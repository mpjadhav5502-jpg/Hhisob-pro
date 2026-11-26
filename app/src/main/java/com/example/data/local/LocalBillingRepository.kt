package com.example.data.local

import androidx.room.withTransaction
import com.example.domain.BillingCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max

class LocalBillingRepository(
    private val database: AppDatabase
) {
    private val settingsDao = database.shopSettingsDao()
    private val customerDao = database.customerDao()
    private val billDao = database.billDao()

    val settingsFlow: Flow<ShopSettingsEntity> = settingsDao.observeSettings()
        .map { it ?: ShopSettingsEntity() }

    val customersFlow: Flow<List<CustomerEntity>> = customerDao.observeAllCustomers()

    val billsFlow: Flow<List<BillEntity>> = billDao.observeAllBills()

    suspend fun ensureDefaultsInitialized(): ShopSettingsEntity {
        val existing = settingsDao.getSettingsOnce()
        if (existing != null) return existing
        val defaults = ShopSettingsEntity()
        settingsDao.upsertSettings(defaults)
        return defaults
    }

    suspend fun saveBill(
        customerNameRaw: String,
        customerMobileRaw: String,
        fullMasoorQty: Int,
        halfMasoorQty: Int,
        rotiQty: Int,
        cashInput: Int,
        onlineInput: Int,
        onlineConfirmed: Boolean,
        billTimestampMillis: Long = System.currentTimeMillis()
    ): Result<BillEntity> {
        return try {
            val cleanName = customerNameRaw.trim()
            val cleanMobile = customerMobileRaw.trim()
            require(cleanName.isNotEmpty()) { "कृपया ग्राहकाचे नाव टाका" }
            require(cleanName.length <= 80) { "ग्राहकाचे नाव ८० अक्षरांपेक्षा लहान असावे" }
            require(BillingCalculator.isValidMobileOrEmpty(cleanMobile)) {
                "कृपया १० अंकी वैध मोबाईल नंबर टाका"
            }

            val savedBill = database.withTransaction {
                val settings = ensureDefaultsInitialized()
                val calc = BillingCalculator.calculate(
                    fullMasoorQty = fullMasoorQty,
                    fullMasoorPrice = settings.fullMasoorPrice,
                    halfMasoorQty = halfMasoorQty,
                    halfMasoorPrice = settings.halfMasoorPrice,
                    rotiQty = rotiQty,
                    rotiPrice = settings.rotiPrice,
                    cashInput = cashInput,
                    onlineInput = onlineInput,
                    onlineConfirmed = onlineConfirmed
                )
                require(calc.canSave) {
                    calc.validationError ?: "बिल तपशील तपासा"
                }

                // Prevent pending credit on anonymous walk-in without phone if user wants, or allow with name
                // Ensure unique sequential bill number even after restore or restart
                val existingBills = billDao.getAllBillsOnce()
                val maxExistingSeq = existingBills.maxOfOrNull {
                    BillingCalculator.extractBillSequence(it.billNumber)
                } ?: 0
                var seq = max(settings.nextBillNumber, maxExistingSeq + 1)
                var billNo = BillingCalculator.formatBillNumber(seq)
                while (billDao.getBillByNumber(billNo) != null) {
                    seq += 1
                    billNo = BillingCalculator.formatBillNumber(seq)
                }

                val now = System.currentTimeMillis()
                // Upsert or match customer
                val matchedCustomer = if (cleanMobile.isNotEmpty()) {
                    customerDao.findByMobile(cleanMobile)
                } else {
                    customerDao.findByNameAndMobile(cleanName, "")
                }

                val customerId = if (matchedCustomer != null) {
                    customerDao.updateCustomer(
                        matchedCustomer.copy(
                            name = cleanName,
                            mobile = cleanMobile.ifEmpty { matchedCustomer.mobile },
                            updatedAtMillis = now
                        )
                    )
                    matchedCustomer.id
                } else {
                    customerDao.insertCustomer(
                        CustomerEntity(
                            name = cleanName,
                            mobile = cleanMobile,
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                    )
                }

                val bill = BillEntity(
                    billNumber = billNo,
                    customerId = customerId,
                    customerName = cleanName,
                    customerMobile = cleanMobile,
                    billTimestampMillis = billTimestampMillis,
                    fullMasoorQty = fullMasoorQty,
                    fullMasoorPrice = settings.fullMasoorPrice,
                    halfMasoorQty = halfMasoorQty,
                    halfMasoorPrice = settings.halfMasoorPrice,
                    rotiQty = rotiQty,
                    rotiPrice = settings.rotiPrice,
                    grandTotal = calc.grandTotal,
                    cashPaid = calc.effectiveCashReceived,
                    onlinePaid = calc.effectiveOnlineReceived,
                    onlineConfirmed = onlineConfirmed && calc.effectiveOnlineReceived > 0,
                    totalReceived = calc.totalReceived,
                    pendingAmount = calc.pendingAmount,
                    shopNameSnapshot = settings.shopName,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )

                val rowId = billDao.insertBill(bill)
                settingsDao.updateNextBillNumber(seq + 1, now)
                bill.copy(id = rowId)
            }
            Result.success(savedBill)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun settlePendingPayment(
        billId: Long,
        addCash: Int,
        addOnline: Int,
        onlineConfirmed: Boolean
    ): Result<BillEntity> {
        return try {
            require(addCash >= 0 && addOnline >= 0) { "रक्कम शून्यापेक्षा कमी असू शकत नाही" }
            require(addCash + addOnline > 0) { "कृपया जमा रक्कम टाका" }
            if (addOnline > 0) {
                require(onlineConfirmed) { "कृपया ऑनलाईन पेमेंट मिळाल्याची मॅन्युअल खात्री करा" }
            }

            val updated = database.withTransaction {
                val bill = billDao.getBillById(billId)
                    ?: throw IllegalStateException("बिल सापडले नाही")
                require(bill.pendingAmount > 0) { "या बिलावर कोणतीही रक्कम बाकी नाही" }
                val paymentTotal = addCash + addOnline
                require(paymentTotal <= bill.pendingAmount) {
                    "जमा रक्कम बाकी रकमेपेक्षा (₹${bill.pendingAmount}) जास्त असू शकत नाही"
                }

                val newCash = bill.cashPaid + addCash
                val newOnline = bill.onlinePaid + addOnline
                val newReceived = bill.totalReceived + paymentTotal
                val newPending = (bill.grandTotal - newReceived).coerceAtLeast(0)
                val updatedBill = bill.copy(
                    cashPaid = newCash,
                    onlinePaid = newOnline,
                    onlineConfirmed = bill.onlineConfirmed || (addOnline > 0 && onlineConfirmed),
                    totalReceived = newReceived,
                    pendingAmount = newPending,
                    updatedAtMillis = System.currentTimeMillis()
                )
                billDao.updateBill(updatedBill)
                updatedBill
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveCustomer(nameRaw: String, mobileRaw: String): Result<CustomerEntity> {
        return try {
            val cleanName = nameRaw.trim()
            val cleanMobile = mobileRaw.trim()
            require(cleanName.isNotEmpty()) { "कृपया ग्राहकाचे नाव टाका" }
            require(cleanName.length <= 80) { "नाव ८० अक्षरांपेक्षा लहान असावे" }
            require(BillingCalculator.isValidMobileOrEmpty(cleanMobile)) {
                "कृपया १० अंकी वैध मोबाईल नंबर टाका"
            }

            val now = System.currentTimeMillis()
            val saved = database.withTransaction {
                val existing = if (cleanMobile.isNotEmpty()) {
                    customerDao.findByMobile(cleanMobile)
                } else {
                    customerDao.findByNameAndMobile(cleanName, "")
                }
                if (existing != null) {
                    val updated = existing.copy(
                        name = cleanName,
                        mobile = cleanMobile.ifEmpty { existing.mobile },
                        updatedAtMillis = now
                    )
                    customerDao.updateCustomer(updated)
                    updated
                } else {
                    val entity = CustomerEntity(
                        name = cleanName,
                        mobile = cleanMobile,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                    val id = customerDao.insertCustomer(entity)
                    entity.copy(id = id)
                }
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSettings(
        shopName: String,
        shopSubtitle: String,
        shopPhone: String,
        shopAddress: String,
        fullMasoorPrice: Int,
        halfMasoorPrice: Int,
        rotiPrice: Int
    ): Result<ShopSettingsEntity> {
        return try {
            val cleanShop = shopName.trim()
            require(cleanShop.isNotEmpty()) { "कृपया हॉटेलचे नाव टाका" }
            require(fullMasoorPrice in 1..100000) { "फुल आखा मसूर किंमत ₹1 ते ₹1,00,000 दरम्यान असावी" }
            require(halfMasoorPrice in 1..100000) { "हाफ पक्का मसूर किंमत ₹1 ते ₹1,00,000 दरम्यान असावी" }
            require(rotiPrice in 1..100000) { "रोटी किंमत ₹1 ते ₹1,00,000 दरम्यान असावी" }

            val current = ensureDefaultsInitialized()
            val updated = current.copy(
                shopName = cleanShop.take(80),
                shopSubtitle = shopSubtitle.trim().take(120),
                shopPhone = shopPhone.trim().take(20),
                shopAddress = shopAddress.trim().take(160),
                fullMasoorPrice = fullMasoorPrice,
                halfMasoorPrice = halfMasoorPrice,
                rotiPrice = rotiPrice,
                updatedAtMillis = System.currentTimeMillis()
            )
            settingsDao.upsertSettings(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetDefaultPrices(): Result<ShopSettingsEntity> {
        val current = ensureDefaultsInitialized()
        return updateSettings(
            shopName = current.shopName,
            shopSubtitle = current.shopSubtitle,
            shopPhone = current.shopPhone,
            shopAddress = current.shopAddress,
            fullMasoorPrice = BillingCalculator.DEFAULT_FULL_MASOOR_PRICE,
            halfMasoorPrice = BillingCalculator.DEFAULT_HALF_MASOOR_PRICE,
            rotiPrice = BillingCalculator.DEFAULT_ROTI_PRICE
        )
    }

    suspend fun getAllDataForBackup(): Triple<ShopSettingsEntity, List<CustomerEntity>, List<BillEntity>> {
        val settings = ensureDefaultsInitialized()
        val customers = customerDao.getAllCustomersOnce()
        val bills = billDao.getAllBillsOnce()
        return Triple(settings, customers, bills)
    }

    suspend fun markBackupCompleted(timestampMillis: Long = System.currentTimeMillis()) {
        ensureDefaultsInitialized()
        settingsDao.updateLastBackupMillis(timestampMillis)
    }

    suspend fun restoreFromBackup(
        remoteSettings: ShopSettingsEntity?,
        remoteCustomers: List<CustomerEntity>,
        remoteBills: List<BillEntity>
    ): Result<Int> {
        return try {
            var restoredBillsCount = 0
            database.withTransaction {
                val localSettings = ensureDefaultsInitialized()
                val customerMapByKey = mutableMapOf<String, Long>()

                for (rc in remoteCustomers) {
                    val key = if (rc.mobile.isNotBlank()) "M:${rc.mobile}" else "N:${rc.name.trim().lowercase()}"
                    val existing = if (rc.mobile.isNotBlank()) {
                        customerDao.findByMobile(rc.mobile)
                    } else {
                        customerDao.findByNameAndMobile(rc.name, "")
                    }
                    val localId = if (existing != null) {
                        customerDao.updateCustomer(
                            existing.copy(
                                name = rc.name,
                                mobile = rc.mobile.ifBlank { existing.mobile },
                                updatedAtMillis = max(existing.updatedAtMillis, rc.updatedAtMillis)
                            )
                        )
                        existing.id
                    } else {
                        customerDao.insertCustomer(rc.copy(id = 0L))
                    }
                    customerMapByKey[key] = localId
                }

                var maxSeq = localSettings.nextBillNumber - 1
                for (rb in remoteBills) {
                    val existingBill = billDao.getBillByNumber(rb.billNumber)
                    val key = if (rb.customerMobile.isNotBlank()) "M:${rb.customerMobile}" else "N:${rb.customerName.trim().lowercase()}"
                    var cid = customerMapByKey[key] ?: 0L
                    if (cid == 0L) {
                        val existingCust = if (rb.customerMobile.isNotBlank()) {
                            customerDao.findByMobile(rb.customerMobile)
                        } else {
                            customerDao.findByNameAndMobile(rb.customerName, "")
                        }
                        cid = existingCust?.id ?: customerDao.insertCustomer(
                            CustomerEntity(
                                name = rb.customerName,
                                mobile = rb.customerMobile,
                                createdAtMillis = rb.billTimestampMillis,
                                updatedAtMillis = rb.billTimestampMillis
                            )
                        )
                        customerMapByKey[key] = cid
                    }

                    if (existingBill == null) {
                        billDao.upsertBillFromBackup(rb.copy(id = 0L, customerId = cid))
                        restoredBillsCount += 1
                    } else if (rb.updatedAtMillis > existingBill.updatedAtMillis) {
                        billDao.updateBill(rb.copy(id = existingBill.id, customerId = cid))
                    }
                    maxSeq = max(maxSeq, BillingCalculator.extractBillSequence(rb.billNumber))
                }

                val targetSettings = (remoteSettings ?: localSettings).copy(
                    id = 1,
                    nextBillNumber = max(
                        max(localSettings.nextBillNumber, remoteSettings?.nextBillNumber ?: 1),
                        maxSeq + 1
                    ),
                    updatedAtMillis = System.currentTimeMillis()
                )
                settingsDao.upsertSettings(targetSettings)
            }
            Result.success(restoredBillsCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
