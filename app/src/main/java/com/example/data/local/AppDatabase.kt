package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShopSettingsEntity::class,
        CustomerEntity::class,
        BillEntity::class,
        DailyRegisterRecordEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shopSettingsDao(): ShopSettingsDao
    abstract fun customerDao(): CustomerDao
    abstract fun billDao(): BillDao
    abstract fun dailyRegisterDao(): DailyRegisterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_register_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recordUuid` TEXT NOT NULL,
                        `registerDate` TEXT NOT NULL,
                        `registerDateMillis` INTEGER NOT NULL,
                        `originalPhotoPath` TEXT NOT NULL,
                        `processedPhotoPath` TEXT NOT NULL,
                        `fullMasoorQty` INTEGER NOT NULL,
                        `fullMasoorRate` INTEGER NOT NULL,
                        `fullMasoorIncome` INTEGER NOT NULL,
                        `halfMasoorQty` INTEGER NOT NULL,
                        `halfMasoorRate` INTEGER NOT NULL,
                        `halfMasoorIncome` INTEGER NOT NULL,
                        `rotiQty` INTEGER NOT NULL,
                        `rotiRate` INTEGER NOT NULL,
                        `rotiIncome` INTEGER NOT NULL,
                        `totalQuantity` INTEGER NOT NULL,
                        `totalIncome` INTEGER NOT NULL,
                        `rawTranscript` TEXT NOT NULL,
                        `unclearSummary` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `wasAiRead` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_register_records_recordUuid` ON `daily_register_records` (`recordUuid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_register_records_registerDate` ON `daily_register_records` (`registerDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_register_records_registerDateMillis` ON `daily_register_records` (`registerDateMillis`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "masoor_billing.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
