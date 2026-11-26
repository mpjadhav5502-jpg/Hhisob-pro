package com.example.data.local

import androidx.room.withTransaction
import com.example.domain.DailyRegisterCalculator
import com.example.media.RegisterPhotoManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class LocalRegisterRepository(
    private val database: AppDatabase
) {
    private val dao = database.dailyRegisterDao()

    val recordsFlow: Flow<List<DailyRegisterRecordEntity>> = dao.observeAllRecords()

    suspend fun getAllRecordsOnce(): List<DailyRegisterRecordEntity> = dao.getAllRecordsOnce()

    suspend fun getById(id: Long): DailyRegisterRecordEntity? = dao.getById(id)

    suspend fun saveNewRecord(
        registerDate: String,
        originalPhotoPath: String,
        processedPhotoPath: String,
        fullMasoorQty: Int,
        halfMasoorQty: Int,
        rotiQty: Int,
        rawTranscript: String,
        unclearSummary: String,
        notes: String,
        wasAiRead: Boolean,
        recordUuid: String = UUID.randomUUID().toString()
    ): Result<DailyRegisterRecordEntity> {
        return try {
            val dateMillis = DailyRegisterCalculator.parseIsoDateToMillis(registerDate)
                ?: throw IllegalArgumentException("कृपया वैध तारीख (YYYY-MM-DD) निवडा")
            require(fullMasoorQty >= 0 && halfMasoorQty >= 0 && rotiQty >= 0) {
                "प्रमाण शून्यापेक्षा कमी असू शकत नाही"
            }
            require(originalPhotoPath.isNotBlank()) {
                "मूळ फोटो जतन केलेला नाही"
            }

            val calc = DailyRegisterCalculator.calculate(fullMasoorQty, halfMasoorQty, rotiQty)
            val now = System.currentTimeMillis()
            val saved = database.withTransaction {
                // Guarantee we never overwrite an existing record accidentally
                val safeUuid = if (dao.getByUuid(recordUuid) == null) {
                    recordUuid
                } else {
                    UUID.randomUUID().toString()
                }
                val entity = DailyRegisterRecordEntity(
                    recordUuid = safeUuid,
                    registerDate = registerDate.trim(),
                    registerDateMillis = dateMillis,
                    originalPhotoPath = originalPhotoPath,
                    processedPhotoPath = processedPhotoPath.ifBlank { originalPhotoPath },
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
                    rawTranscript = rawTranscript.take(4000),
                    unclearSummary = unclearSummary.take(500),
                    notes = notes.trim().take(500),
                    wasAiRead = wasAiRead,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                val rowId = dao.insertRecord(entity)
                entity.copy(id = rowId)
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateExistingRecord(
        recordId: Long,
        registerDate: String,
        fullMasoorQty: Int,
        halfMasoorQty: Int,
        rotiQty: Int,
        notes: String
    ): Result<DailyRegisterRecordEntity> {
        return try {
            val dateMillis = DailyRegisterCalculator.parseIsoDateToMillis(registerDate)
                ?: throw IllegalArgumentException("कृपया वैध तारीख (YYYY-MM-DD) निवडा")
            require(fullMasoorQty >= 0 && halfMasoorQty >= 0 && rotiQty >= 0) {
                "प्रमाण शून्यापेक्षा कमी असू शकत नाही"
            }

            val updated = database.withTransaction {
                val existing = dao.getById(recordId)
                    ?: throw IllegalStateException("नोंद सापडली नाही")
                val calc = DailyRegisterCalculator.calculate(fullMasoorQty, halfMasoorQty, rotiQty)
                val next = existing.copy(
                    registerDate = registerDate.trim(),
                    registerDateMillis = dateMillis,
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
                    notes = notes.trim().take(500),
                    updatedAtMillis = System.currentTimeMillis()
                )
                dao.updateRecord(next)
                next
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRecords(ids: List<Long>): Result<Int> {
        if (ids.isEmpty()) return Result.success(0)
        return try {
            val pathsToDelete = mutableListOf<String>()
            database.withTransaction {
                val records = dao.getByIds(ids)
                records.forEach { rec ->
                    if (rec.originalPhotoPath.isNotBlank()) pathsToDelete.add(rec.originalPhotoPath)
                    if (rec.processedPhotoPath.isNotBlank()) pathsToDelete.add(rec.processedPhotoPath)
                }
                dao.deleteByIds(ids)
            }
            // Only remove photo files after DB transaction succeeds
            val distinctPaths = pathsToDelete.distinct()
            val remainingPaths = dao.getAllRecordsOnce()
                .flatMap { listOf(it.originalPhotoPath, it.processedPhotoPath) }
                .toSet()
            RegisterPhotoManager.deletePhotoFiles(
                distinctPaths.filterNot { it in remainingPaths }
            )
            Result.success(ids.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergeRestoredRecords(records: List<DailyRegisterRecordEntity>): Result<Int> {
        return try {
            var addedCount = 0
            database.withTransaction {
                for (rec in records) {
                    val existing = dao.getByUuid(rec.recordUuid)
                    if (existing == null) {
                        val calc = DailyRegisterCalculator.calculate(
                            rec.fullMasoorQty,
                            rec.halfMasoorQty,
                            rec.rotiQty
                        )
                        dao.insertRecord(
                            rec.copy(
                                id = 0L,
                                fullMasoorRate = calc.fullMasoorRate,
                                fullMasoorIncome = calc.fullMasoorIncome,
                                halfMasoorRate = calc.halfMasoorRate,
                                halfMasoorIncome = calc.halfMasoorIncome,
                                rotiRate = calc.rotiRate,
                                rotiIncome = calc.rotiIncome,
                                totalQuantity = calc.totalQuantity,
                                totalIncome = calc.totalIncome
                            )
                        )
                        addedCount += 1
                    }
                    // Never overwrite existing local records automatically!
                }
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
