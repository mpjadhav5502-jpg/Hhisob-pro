package com.example.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.DailyRegisterRecordEntity
import com.example.domain.DailyRegisterCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RegisterZipRestorePayload(
    val records: List<DailyRegisterRecordEntity>,
    val restoredPhotosCount: Int
)

object RegisterZipBackupManager {
    private const val MANIFEST_NAME = "register_backup.json"
    private const val PHOTOS_FOLDER = "photos/"
    private const val BACKUP_CACHE_DIR = "register_backups"

    fun exportToUri(
        context: Context,
        targetUri: Uri,
        records: List<DailyRegisterRecordEntity>
    ): Result<Int> {
        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                writeZipArchive(out, records)
            } ?: throw IllegalStateException("बॅकअप फाईल लिहिता आली नाही")
            Result.success(records.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportShareableZip(
        context: Context,
        records: List<DailyRegisterRecordEntity>
    ): Result<Pair<File, Uri>> {
        return try {
            val dir = File(context.cacheDir, BACKUP_CACHE_DIR).apply {
                if (!exists()) mkdirs()
            }
            val file = File(dir, "masoor_register_backup_${System.currentTimeMillis()}.zip")
            FileOutputStream(file).use { out ->
                writeZipArchive(out, records)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Result.success(file to uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeZipArchive(
        outputStream: OutputStream,
        records: List<DailyRegisterRecordEntity>
    ) {
        val photosDirNamesUsed = mutableSetOf<String>()
        val recordsJson = JSONArray()

        ZipOutputStream(outputStream).use { zip ->
            for (rec in records) {
                val origFile = File(rec.originalPhotoPath)
                val procFile = File(rec.processedPhotoPath)

                val origEntryName = if (origFile.exists()) {
                    "orig_${rec.recordUuid}_${origFile.name.takeLast(24)}".replace(Regex("[^a-zA-Z0-9._-]"), "_")
                } else ""

                val procEntryName = if (procFile.exists() && procFile.absolutePath != origFile.absolutePath) {
                    "scan_${rec.recordUuid}_${procFile.name.takeLast(24)}".replace(Regex("[^a-zA-Z0-9._-]"), "_")
                } else origEntryName

                if (origEntryName.isNotEmpty() && origFile.exists() && photosDirNamesUsed.add(origEntryName)) {
                    zip.putNextEntry(ZipEntry("$PHOTOS_FOLDER$origEntryName"))
                    FileInputStream(origFile).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                if (procEntryName.isNotEmpty() && procFile.exists() && photosDirNamesUsed.add(procEntryName)) {
                    zip.putNextEntry(ZipEntry("$PHOTOS_FOLDER$procEntryName"))
                    FileInputStream(procFile).use { it.copyTo(zip) }
                    zip.closeEntry()
                }

                recordsJson.put(
                    JSONObject().apply {
                        put("recordUuid", rec.recordUuid)
                        put("registerDate", rec.registerDate)
                        put("registerDateMillis", rec.registerDateMillis)
                        put("origPhotoEntry", origEntryName)
                        put("procPhotoEntry", procEntryName)
                        put("fullMasoorQty", rec.fullMasoorQty)
                        put("halfMasoorQty", rec.halfMasoorQty)
                        put("rotiQty", rec.rotiQty)
                        put("rawTranscript", rec.rawTranscript)
                        put("unclearSummary", rec.unclearSummary)
                        put("notes", rec.notes)
                        put("wasAiRead", rec.wasAiRead)
                        put("createdAtMillis", rec.createdAtMillis)
                        put("updatedAtMillis", rec.updatedAtMillis)
                    }
                )
            }

            val root = JSONObject().apply {
                put("schemaVersion", 1)
                put("exportedAtMillis", System.currentTimeMillis())
                put("records", recordsJson)
            }
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun restoreFromUri(
        context: Context,
        sourceUri: Uri,
        photosTargetDir: File
    ): Result<RegisterZipRestorePayload> {
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                readZipArchive(input, photosTargetDir)
            } ?: throw IllegalStateException("बॅकअप फाईल उघडता आली नाही")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readZipArchive(
        inputStream: InputStream,
        photosTargetDir: File
    ): Result<RegisterZipRestorePayload> {
        return try {
            if (!photosTargetDir.exists()) photosTargetDir.mkdirs()

            var manifestText: String? = null
            val restoredFilesByEntry = mutableMapOf<String, String>()

            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (!entry.isDirectory) {
                        when {
                            entryName == MANIFEST_NAME -> {
                                manifestText = zip.readBytes().toString(Charsets.UTF_8)
                            }
                            entryName.startsWith(PHOTOS_FOLDER) -> {
                                val baseName = File(entryName).name
                                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                if (baseName.isNotBlank()) {
                                    val dest = File(photosTargetDir, baseName)
                                    // Canonical path guard against zip-slip
                                    if (dest.canonicalPath.startsWith(photosTargetDir.canonicalPath)) {
                                        FileOutputStream(dest).use { out -> zip.copyTo(out) }
                                        restoredFilesByEntry[baseName] = dest.absolutePath
                                    }
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val rawJson = manifestText
                ?: throw IllegalArgumentException("वैध रजिस्टर बॅकअप फाईल सापडली नाही ($MANIFEST_NAME गहाळ आहे)")
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("records") ?: JSONArray()
            val records = mutableListOf<DailyRegisterRecordEntity>()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val uuid = obj.optString("recordUuid", "").trim().ifEmpty { UUID.randomUUID().toString() }
                val regDate = obj.optString("registerDate", "").trim()
                val regDateMillis = DailyRegisterCalculator.parseIsoDateToMillis(regDate)
                    ?: obj.optLong("registerDateMillis", System.currentTimeMillis())
                val normalizedDate = DailyRegisterCalculator.formatIsoDate(regDateMillis)

                val fullQty = obj.optInt("fullMasoorQty", 0).coerceAtLeast(0)
                val halfQty = obj.optInt("halfMasoorQty", 0).coerceAtLeast(0)
                val rotiQty = obj.optInt("rotiQty", 0).coerceAtLeast(0)
                val calc = DailyRegisterCalculator.calculate(fullQty, halfQty, rotiQty)

                val origEntry = File(obj.optString("origPhotoEntry", "")).name
                val procEntry = File(obj.optString("procPhotoEntry", "")).name
                val origPath = restoredFilesByEntry[origEntry] ?: restoredFilesByEntry[procEntry] ?: ""
                val procPath = restoredFilesByEntry[procEntry] ?: origPath

                records.add(
                    DailyRegisterRecordEntity(
                        id = 0L,
                        recordUuid = uuid,
                        registerDate = normalizedDate,
                        registerDateMillis = regDateMillis,
                        originalPhotoPath = origPath,
                        processedPhotoPath = procPath,
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
                        rawTranscript = obj.optString("rawTranscript", ""),
                        unclearSummary = obj.optString("unclearSummary", ""),
                        notes = obj.optString("notes", ""),
                        wasAiRead = obj.optBoolean("wasAiRead", false),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis())
                    )
                )
            }

            Result.success(
                RegisterZipRestorePayload(
                    records = records,
                    restoredPhotosCount = restoredFilesByEntry.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
