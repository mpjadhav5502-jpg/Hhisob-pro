package com.example

import com.example.backup.RegisterZipBackupManager
import com.example.data.local.DailyRegisterRecordEntity
import com.example.data.remote.RegisterOcrRepository
import com.example.domain.DailyRegisterCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyRegisterUnitTest {

    @Test
    fun `fixed rates always calculate 100, 50, 15 and sum total quantity and income`() {
        val calc = DailyRegisterCalculator.calculate(
            fullMasoorQty = 2,
            halfMasoorQty = 1,
            rotiQty = 4
        )
        assertEquals(100, calc.fullMasoorRate)
        assertEquals(200, calc.fullMasoorIncome)
        assertEquals(50, calc.halfMasoorRate)
        assertEquals(50, calc.halfMasoorIncome)
        assertEquals(15, calc.rotiRate)
        assertEquals(60, calc.rotiIncome)
        assertEquals(7, calc.totalQuantity)
        assertEquals(310, calc.totalIncome)
    }

    @Test
    fun `negative quantities clamp safely to zero`() {
        val calc = DailyRegisterCalculator.calculate(
            fullMasoorQty = -3,
            halfMasoorQty = -1,
            rotiQty = 5
        )
        assertEquals(0, calc.fullMasoorQty)
        assertEquals(0, calc.halfMasoorQty)
        assertEquals(5, calc.rotiQty)
        assertEquals(5, calc.totalQuantity)
        assertEquals(75, calc.totalIncome)
    }

    @Test
    fun `marathi devanagari numerals normalize cleanly to ascii digits`() {
        assertEquals("12", DailyRegisterCalculator.normalizeQuantityInput("१२"))
        assertEquals("05", DailyRegisterCalculator.normalizeQuantityInput("०५"))
        assertEquals("250", DailyRegisterCalculator.normalizeQuantityInput("२५abc०"))
        assertEquals("", DailyRegisterCalculator.normalizeQuantityInput("अस्पष्ट?"))
    }

    @Test
    fun `iso date parsing validates format and formats display date`() {
        val millis = DailyRegisterCalculator.parseIsoDateToMillis("2026-11-25")
        assertNotNull(millis)
        assertEquals("2026-11-25", DailyRegisterCalculator.formatIsoDate(millis!!))
        assertEquals("2026-11", DailyRegisterCalculator.formatMonthKey("2026-11-25"))
        assertNull(DailyRegisterCalculator.parseIsoDateToMillis("25/11/2026"))
        assertNull(DailyRegisterCalculator.parseIsoDateToMillis("2026-13-45"))
    }

    @Test
    fun `ocr parser parses clear extraction accurately`() {
        val json = """
            {
              "detectedDate": "2026-11-25",
              "dateUnclear": false,
              "dateRawText": "२५/११/२६",
              "fullMasoorQty": 14,
              "fullMasoorUnclear": false,
              "fullMasoorRawText": "फुल आखा मसूर - १४",
              "halfMasoorQty": 9,
              "halfMasoorUnclear": false,
              "halfMasoorRawText": "हाफ पक्का - ९",
              "rotiQty": 38,
              "rotiUnclear": false,
              "rotiRawText": "रोटी - ३८",
              "rawTranscript": "दि. २५/११/२६\nफुल आखा मसूर १४\nहाफ पक्का ९\nरोटी ३८",
              "overallWarning": ""
            }
        """.trimIndent()

        val parsed = RegisterOcrRepository.parseExtractionJson(json)
        assertEquals("2026-11-25", parsed.detectedDate)
        assertFalse(parsed.dateUnclear)
        assertEquals(14, parsed.fullMasoorQty)
        assertEquals(9, parsed.halfMasoorQty)
        assertEquals(38, parsed.rotiQty)
        assertFalse(parsed.hasAnyUnclearOrMissing)
    }

    @Test
    fun `ocr parser preserves null and unclear flags instead of guessing missing numbers`() {
        val json = """
            {
              "detectedDate": "unclear",
              "dateUnclear": true,
              "dateRawText": "२?/११",
              "fullMasoorQty": 10,
              "fullMasoorUnclear": false,
              "fullMasoorRawText": "Full 10",
              "halfMasoorQty": null,
              "halfMasoorUnclear": true,
              "halfMasoorRawText": "हाफ - खाडाखोड",
              "rotiQty": null,
              "rotiUnclear": true,
              "rotiRawText": "रोटी - अस्पष्ट",
              "rawTranscript": "Full 10, Half ?, Roti smudged",
              "overallWarning": "हाफ मसूर व रोटीची संख्या अस्पष्ट आहे"
            }
        """.trimIndent()

        val parsed = RegisterOcrRepository.parseExtractionJson(json)
        assertNull(parsed.detectedDate)
        assertTrue(parsed.dateUnclear)
        assertEquals(10, parsed.fullMasoorQty)
        assertFalse(parsed.fullMasoorUnclear)
        assertNull(parsed.halfMasoorQty)
        assertTrue(parsed.halfMasoorUnclear)
        assertNull(parsed.rotiQty)
        assertTrue(parsed.rotiUnclear)
        assertTrue(parsed.hasAnyUnclearOrMissing)
    }

    @Test
    fun `zip backup writes and restores register records and photos without data loss`() {
        val tempRoot = Files.createTempDirectory("register_zip_test").toFile()
        try {
            val sourceDir = File(tempRoot, "source_photos").apply { mkdirs() }
            val restoreDir = File(tempRoot, "restored_photos").apply { mkdirs() }

            val origFile = File(sourceDir, "orig_uuid1.png").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
            }
            val procFile = File(sourceDir, "scan_uuid1.jpg").apply {
                writeBytes(byteArrayOf(9, 10, 11, 12, 13))
            }

            val rec = DailyRegisterRecordEntity(
                id = 1L,
                recordUuid = "uuid-abc-123",
                registerDate = "2026-11-25",
                registerDateMillis = DailyRegisterCalculator.parseIsoDateToMillis("2026-11-25")!!,
                originalPhotoPath = origFile.absolutePath,
                processedPhotoPath = procFile.absolutePath,
                fullMasoorQty = 5,
                fullMasoorRate = 100,
                fullMasoorIncome = 500,
                halfMasoorQty = 3,
                halfMasoorRate = 50,
                halfMasoorIncome = 150,
                rotiQty = 10,
                rotiRate = 15,
                rotiIncome = 150,
                totalQuantity = 18,
                totalIncome = 800,
                rawTranscript = "Full 5, Half 3, Roti 10",
                unclearSummary = "",
                notes = "सकाळ रजिस्टर",
                wasAiRead = true,
                createdAtMillis = 1700000000000L,
                updatedAtMillis = 1700000001000L
            )

            val baos = ByteArrayOutputStream()
            RegisterZipBackupManager.writeZipArchive(baos, listOf(rec))

            val payload = RegisterZipBackupManager.readZipArchive(
                ByteArrayInputStream(baos.toByteArray()),
                restoreDir
            ).getOrThrow()

            assertEquals(1, payload.records.size)
            assertEquals(2, payload.restoredPhotosCount)

            val restored = payload.records.first()
            assertEquals("uuid-abc-123", restored.recordUuid)
            assertEquals("2026-11-25", restored.registerDate)
            assertEquals(5, restored.fullMasoorQty)
            assertEquals(3, restored.halfMasoorQty)
            assertEquals(10, restored.rotiQty)
            assertEquals(18, restored.totalQuantity)
            assertEquals(800, restored.totalIncome)
            assertTrue(File(restored.originalPhotoPath).exists())
            assertTrue(File(restored.processedPhotoPath).exists())
            assertTrue(File(restored.originalPhotoPath).readBytes().contentEquals(origFile.readBytes()))
            assertTrue(File(restored.processedPhotoPath).readBytes().contentEquals(procFile.readBytes()))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
