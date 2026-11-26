package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopSettingsDao {
    @Query("SELECT * FROM shop_settings WHERE id = 1")
    fun observeSettings(): Flow<ShopSettingsEntity?>

    @Query("SELECT * FROM shop_settings WHERE id = 1")
    suspend fun getSettingsOnce(): ShopSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: ShopSettingsEntity)

    @Query("UPDATE shop_settings SET nextBillNumber = :nextNumber, updatedAtMillis = :updatedAt WHERE id = 1")
    suspend fun updateNextBillNumber(nextNumber: Int, updatedAt: Long)

    @Query("UPDATE shop_settings SET lastBackupMillis = :backupMillis WHERE id = 1")
    suspend fun updateLastBackupMillis(backupMillis: Long)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY updatedAtMillis DESC, name ASC")
    fun observeAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY updatedAtMillis DESC, name ASC")
    suspend fun getAllCustomersOnce(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE mobile = :mobile LIMIT 1")
    suspend fun findByMobile(mobile: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND mobile = :mobile LIMIT 1")
    suspend fun findByNameAndMobile(name: String, mobile: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY billTimestampMillis DESC, id DESC")
    fun observeAllBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills ORDER BY billTimestampMillis DESC, id DESC")
    suspend fun getAllBillsOnce(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBillById(id: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE billNumber = :billNumber LIMIT 1")
    suspend fun getBillByNumber(billNumber: String): BillEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBill(bill: BillEntity): Long

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBillFromBackup(bill: BillEntity): Long
}

@Dao
interface DailyRegisterDao {
    @Query("SELECT * FROM daily_register_records ORDER BY registerDate DESC, createdAtMillis DESC, id DESC")
    fun observeAllRecords(): Flow<List<DailyRegisterRecordEntity>>

    @Query("SELECT * FROM daily_register_records ORDER BY registerDate DESC, createdAtMillis DESC, id DESC")
    suspend fun getAllRecordsOnce(): List<DailyRegisterRecordEntity>

    @Query("SELECT * FROM daily_register_records WHERE id = :id")
    suspend fun getById(id: Long): DailyRegisterRecordEntity?

    @Query("SELECT * FROM daily_register_records WHERE recordUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): DailyRegisterRecordEntity?

    @Query("SELECT * FROM daily_register_records WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<DailyRegisterRecordEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(record: DailyRegisterRecordEntity): Long

    @Update
    suspend fun updateRecord(record: DailyRegisterRecordEntity)

    @Query("DELETE FROM daily_register_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
