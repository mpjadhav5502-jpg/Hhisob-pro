package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.BillingCalculator

@Entity(tableName = "shop_settings")
data class ShopSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "हॉटेल आखा मसूर पॅलेस",
    val shopSubtitle: String = "शुद्ध व चविष्ट अस्सल मराठी चव",
    val shopPhone: String = "",
    val shopAddress: String = "",
    val fullMasoorPrice: Int = BillingCalculator.DEFAULT_FULL_MASOOR_PRICE,
    val halfMasoorPrice: Int = BillingCalculator.DEFAULT_HALF_MASOOR_PRICE,
    val rotiPrice: Int = BillingCalculator.DEFAULT_ROTI_PRICE,
    val nextBillNumber: Int = 1,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val lastBackupMillis: Long = 0L
)

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["mobile"]),
        Index(value = ["name"])
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val mobile: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bills",
    indices = [
        Index(value = ["billNumber"], unique = true),
        Index(value = ["billTimestampMillis"]),
        Index(value = ["customerId"]),
        Index(value = ["customerMobile"])
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val billNumber: String,
    val customerId: Long,
    val customerName: String,
    val customerMobile: String,
    val billTimestampMillis: Long,
    val fullMasoorQty: Int,
    val fullMasoorPrice: Int,
    val halfMasoorQty: Int,
    val halfMasoorPrice: Int,
    val rotiQty: Int,
    val rotiPrice: Int,
    val grandTotal: Int,
    val cashPaid: Int,
    val onlinePaid: Int,
    val onlineConfirmed: Boolean,
    val totalReceived: Int,
    val pendingAmount: Int,
    val shopNameSnapshot: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "daily_register_records",
    indices = [
        Index(value = ["recordUuid"], unique = true),
        Index(value = ["registerDate"]),
        Index(value = ["registerDateMillis"])
    ]
)
data class DailyRegisterRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recordUuid: String,
    val registerDate: String,
    val registerDateMillis: Long,
    val originalPhotoPath: String,
    val processedPhotoPath: String,
    val fullMasoorQty: Int,
    val fullMasoorRate: Int = 100,
    val fullMasoorIncome: Int,
    val halfMasoorQty: Int,
    val halfMasoorRate: Int = 50,
    val halfMasoorIncome: Int,
    val rotiQty: Int,
    val rotiRate: Int = 15,
    val rotiIncome: Int,
    val totalQuantity: Int,
    val totalIncome: Int,
    val rawTranscript: String = "",
    val unclearSummary: String = "",
    val notes: String = "",
    val wasAiRead: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
