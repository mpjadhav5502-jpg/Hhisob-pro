package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.BillEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.DailyRegisterRecordEntity
import com.example.data.local.ShopSettingsEntity
import com.example.domain.DailyRegisterCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class BackupSummary(
    val customersCount: Int,
    val billsCount: Int,
    val registerRecordsCount: Int,
    val timestampMillis: Long
)

data class CloudBackupPayload(
    val settings: ShopSettingsEntity?,
    val customers: List<CustomerEntity>,
    val bills: List<BillEntity>,
    val registerRecords: List<DailyRegisterRecordEntity>
)

class CloudBackupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("क्लाउड बॅकअपसाठी कृपया Google ने साइन इन करा")
    }

    suspend fun backupToCloud(
        settings: ShopSettingsEntity,
        customers: List<CustomerEntity>,
        bills: List<BillEntity>,
        registerRecords: List<DailyRegisterRecordEntity> = emptyList()
    ): Result<BackupSummary> {
        var currentPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)

            // 1. Settings document ("shop")
            val settingsRef = userDoc.collection("settings").document("shop")
            currentPath = settingsRef.path
            val settingsExists = settingsRef.get().await().exists()
            val settingsFields = mutableMapOf<String, Any>(
                "shopName" to settings.shopName.take(80),
                "shopSubtitle" to settings.shopSubtitle.take(120),
                "shopPhone" to settings.shopPhone.take(20),
                "shopAddress" to settings.shopAddress.take(160),
                "fullMasoorPrice" to settings.fullMasoorPrice.coerceIn(1, 100000),
                "halfMasoorPrice" to settings.halfMasoorPrice.coerceIn(1, 100000),
                "rotiPrice" to settings.rotiPrice.coerceIn(1, 100000),
                "nextBillNumber" to settings.nextBillNumber.coerceAtLeast(1),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (settingsExists) {
                settingsRef.update(settingsFields).await()
            } else {
                settingsFields["userId"] = uid
                settingsFields["createdAt"] = FieldValue.serverTimestamp()
                settingsRef.set(settingsFields).await()
            }

            // 2. Customers documents
            for (customer in customers) {
                val docId = "cust_${customer.id}"
                val custRef = userDoc.collection("customers").document(docId)
                currentPath = custRef.path
                val custExists = custRef.get().await().exists()
                val custFields = mutableMapOf<String, Any>(
                    "localId" to customer.id,
                    "name" to customer.name.take(80),
                    "mobile" to customer.mobile.take(15),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (custExists) {
                    custRef.update(custFields).await()
                } else {
                    custFields["userId"] = uid
                    custFields["createdAt"] = FieldValue.serverTimestamp()
                    custRef.set(custFields).await()
                }
            }

            // 3. Bills documents (keyed by unique billNumber matching ^[a-zA-Z0-9_\-]+$)
            for (bill in bills) {
                val docId = bill.billNumber.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val billRef = userDoc.collection("bills").document(docId)
                currentPath = billRef.path
                val billExists = billRef.get().await().exists()
                val billFields = mutableMapOf<String, Any>(
                    "customerName" to bill.customerName.take(80),
                    "customerMobile" to bill.customerMobile.take(15),
                    "billTimestampMillis" to bill.billTimestampMillis,
                    "fullMasoorQty" to bill.fullMasoorQty,
                    "fullMasoorPrice" to bill.fullMasoorPrice,
                    "halfMasoorQty" to bill.halfMasoorQty,
                    "halfMasoorPrice" to bill.halfMasoorPrice,
                    "rotiQty" to bill.rotiQty,
                    "rotiPrice" to bill.rotiPrice,
                    "grandTotal" to bill.grandTotal,
                    "cashPaid" to bill.cashPaid,
                    "onlinePaid" to bill.onlinePaid,
                    "onlineConfirmed" to bill.onlineConfirmed,
                    "totalReceived" to bill.totalReceived,
                    "pendingAmount" to bill.pendingAmount,
                    "shopNameSnapshot" to bill.shopNameSnapshot.take(80),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (billExists) {
                    billRef.update(billFields).await()
                } else {
                    billFields["userId"] = uid
                    billFields["billNumber"] = bill.billNumber.take(32)
                    billFields["createdAt"] = FieldValue.serverTimestamp()
                    billRef.set(billFields).await()
                }
            }

            // 4. Daily Register records
            for (record in registerRecords) {
                val safeDocId = record.recordUuid.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
                val regRef = userDoc.collection("register_records").document(safeDocId)
                currentPath = regRef.path
                val regExists = regRef.get().await().exists()
                val calc = DailyRegisterCalculator.calculate(
                    record.fullMasoorQty,
                    record.halfMasoorQty,
                    record.rotiQty
                )
                val regFields = mutableMapOf<String, Any>(
                    "registerDate" to record.registerDate.take(10),
                    "registerDateMillis" to record.registerDateMillis.coerceAtLeast(1L),
                    "fullMasoorQty" to calc.fullMasoorQty,
                    "fullMasoorRate" to calc.fullMasoorRate,
                    "fullMasoorIncome" to calc.fullMasoorIncome,
                    "halfMasoorQty" to calc.halfMasoorQty,
                    "halfMasoorRate" to calc.halfMasoorRate,
                    "halfMasoorIncome" to calc.halfMasoorIncome,
                    "rotiQty" to calc.rotiQty,
                    "rotiRate" to calc.rotiRate,
                    "rotiIncome" to calc.rotiIncome,
                    "totalQuantity" to calc.totalQuantity,
                    "totalIncome" to calc.totalIncome,
                    "rawTranscript" to record.rawTranscript.take(4000),
                    "unclearSummary" to record.unclearSummary.take(500),
                    "notes" to record.notes.take(500),
                    "wasAiRead" to record.wasAiRead,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (regExists) {
                    regRef.update(regFields).await()
                } else {
                    regFields["userId"] = uid
                    regFields["recordUuid"] = record.recordUuid.take(64)
                    regFields["createdAt"] = FieldValue.serverTimestamp()
                    regRef.set(regFields).await()
                }
            }

            Result.success(
                BackupSummary(
                    customersCount = customers.size,
                    billsCount = bills.size,
                    registerRecordsCount = registerRecords.size,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun fetchBackupFromCloud(): Result<CloudBackupPayload> {
        var currentPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)

            val settingsPath = "users/$uid/settings/shop"
            currentPath = settingsPath
            val settingsDoc = userDoc.collection("settings").document("shop").get().await()
            val remoteSettings = if (settingsDoc.exists()) {
                val data = settingsDoc.data ?: emptyMap()
                ShopSettingsEntity(
                    id = 1,
                    shopName = (data["shopName"] as? String).orEmpty().ifBlank { "हॉटेल आखा मसूर पॅलेस" },
                    shopSubtitle = (data["shopSubtitle"] as? String).orEmpty(),
                    shopPhone = (data["shopPhone"] as? String).orEmpty(),
                    shopAddress = (data["shopAddress"] as? String).orEmpty(),
                    fullMasoorPrice = (data["fullMasoorPrice"] as? Number)?.toInt() ?: 100,
                    halfMasoorPrice = (data["halfMasoorPrice"] as? Number)?.toInt() ?: 50,
                    rotiPrice = (data["rotiPrice"] as? Number)?.toInt() ?: 15,
                    nextBillNumber = (data["nextBillNumber"] as? Number)?.toInt() ?: 1,
                    updatedAtMillis = System.currentTimeMillis()
                )
            } else {
                null
            }

            currentPath = "users/$uid/customers"
            val customerSnap = userDoc.collection("customers").get().await()
            val remoteCustomers = customerSnap.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val name = (data["name"] as? String).orEmpty()
                if (name.isBlank()) return@mapNotNull null
                CustomerEntity(
                    id = 0L,
                    name = name,
                    mobile = (data["mobile"] as? String).orEmpty(),
                    createdAtMillis = System.currentTimeMillis(),
                    updatedAtMillis = System.currentTimeMillis()
                )
            }

            currentPath = "users/$uid/bills"
            val billSnap = userDoc.collection("bills").get().await()
            val remoteBills = billSnap.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val billNo = (data["billNumber"] as? String).orEmpty()
                val custName = (data["customerName"] as? String).orEmpty()
                if (billNo.isBlank() || custName.isBlank()) return@mapNotNull null
                BillEntity(
                    id = 0L,
                    billNumber = billNo,
                    customerId = 0L,
                    customerName = custName,
                    customerMobile = (data["customerMobile"] as? String).orEmpty(),
                    billTimestampMillis = (data["billTimestampMillis"] as? Number)?.toLong()
                        ?: System.currentTimeMillis(),
                    fullMasoorQty = (data["fullMasoorQty"] as? Number)?.toInt() ?: 0,
                    fullMasoorPrice = (data["fullMasoorPrice"] as? Number)?.toInt() ?: 100,
                    halfMasoorQty = (data["halfMasoorQty"] as? Number)?.toInt() ?: 0,
                    halfMasoorPrice = (data["halfMasoorPrice"] as? Number)?.toInt() ?: 50,
                    rotiQty = (data["rotiQty"] as? Number)?.toInt() ?: 0,
                    rotiPrice = (data["rotiPrice"] as? Number)?.toInt() ?: 15,
                    grandTotal = (data["grandTotal"] as? Number)?.toInt() ?: 0,
                    cashPaid = (data["cashPaid"] as? Number)?.toInt() ?: 0,
                    onlinePaid = (data["onlinePaid"] as? Number)?.toInt() ?: 0,
                    onlineConfirmed = (data["onlineConfirmed"] as? Boolean) ?: false,
                    totalReceived = (data["totalReceived"] as? Number)?.toInt() ?: 0,
                    pendingAmount = (data["pendingAmount"] as? Number)?.toInt() ?: 0,
                    shopNameSnapshot = (data["shopNameSnapshot"] as? String).orEmpty()
                        .ifBlank { "हॉटेल आखा मसूर पॅलेस" },
                    createdAtMillis = System.currentTimeMillis(),
                    updatedAtMillis = System.currentTimeMillis()
                )
            }

            currentPath = "users/$uid/register_records"
            val regSnap = userDoc.collection("register_records").get().await()
            val remoteRegisterRecords = regSnap.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val uuid = (data["recordUuid"] as? String).orEmpty()
                val regDate = (data["registerDate"] as? String).orEmpty()
                if (uuid.isBlank() || regDate.isBlank()) return@mapNotNull null
                val fullQty = (data["fullMasoorQty"] as? Number)?.toInt() ?: 0
                val halfQty = (data["halfMasoorQty"] as? Number)?.toInt() ?: 0
                val rotiQty = (data["rotiQty"] as? Number)?.toInt() ?: 0
                val calc = DailyRegisterCalculator.calculate(fullQty, halfQty, rotiQty)
                DailyRegisterRecordEntity(
                    id = 0L,
                    recordUuid = uuid,
                    registerDate = regDate,
                    registerDateMillis = (data["registerDateMillis"] as? Number)?.toLong()
                        ?: DailyRegisterCalculator.parseIsoDateToMillis(regDate)
                        ?: System.currentTimeMillis(),
                    originalPhotoPath = "",
                    processedPhotoPath = "",
                    fullMasoorQty = calc.fullMasoorQty,
                    fullMasoorRate = calc.fullMasoorRate,
                    fullMasoorIncome = calc.fullMasoorIncome,
                    halfMasoorQty = calc.halfMasoorQty,
                    halfMasoorRate = calc.halfMasoorRate,
                    halfMasoorIncome = calc.halfMasoorIncome,
                    rotiQty = calc.rotiQty,
                    rotiRate = calc.rotiRate,
                    rotiIncome = calc.rotiIncome,
                    totalQuantity = calc.totalQuantity,
                    totalIncome = calc.totalIncome,
                    rawTranscript = (data["rawTranscript"] as? String).orEmpty(),
                    unclearSummary = (data["unclearSummary"] as? String).orEmpty(),
                    notes = (data["notes"] as? String).orEmpty(),
                    wasAiRead = (data["wasAiRead"] as? Boolean) ?: false,
                    createdAtMillis = System.currentTimeMillis(),
                    updatedAtMillis = System.currentTimeMillis()
                )
            }

            Result.success(
                CloudBackupPayload(
                    settings = remoteSettings,
                    customers = remoteCustomers,
                    bills = remoteBills,
                    registerRecords = remoteRegisterRecords
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, currentPath)
            Result.failure(e)
        }
    }
}
