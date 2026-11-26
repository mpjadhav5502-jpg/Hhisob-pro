package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DailyRegisterRecordEntity
import com.example.data.local.LocalBillingRepository
import com.example.data.local.LocalRegisterRepository
import com.example.data.remote.CloudBackupRepository
import com.example.data.remote.RegisterOcrRepository
import com.example.domain.DailyRegisterCalculator
import com.example.media.RegisterPhotoManager
import com.example.ui.DailyRegisterViewModel
import com.example.ui.MasoorBillingApp
import com.example.ui.MasoorBillingViewModel
import com.example.ui.theme.MasoorBillingTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class DailyRegisterWorkflowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var billingRepo: LocalBillingRepository
    private lateinit var registerRepo: LocalRegisterRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        RegisterPhotoManager.getPhotosDir(context).deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        billingRepo = LocalBillingRepository(db)
        registerRepo = LocalRegisterRepository(db)
    }

    @After
    fun teardown() {
        db.close()
        RegisterPhotoManager.getPhotosDir(context).deleteRecursively()
    }

    private fun createTestImageFile(name: String, width: Int, height: Int, format: Bitmap.CompressFormat): File {
        val file = File(context.cacheDir, name)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        FileOutputStream(file).use { out ->
            bmp.compress(format, 95, out)
        }
        bmp.recycle()
        return file
    }

    @Test
    fun `photo manager supports png and jpg, rotates and crops while preserving original`() {
        val pngFile = createTestImageFile("sample_page.png", 400, 200, Bitmap.CompressFormat.PNG)
        val jpgFile = createTestImageFile("sample_page.jpg", 300, 150, Bitmap.CompressFormat.JPEG)

        val pngResult = RegisterPhotoManager.saveOriginalPhotoFromUri(
            context,
            Uri.fromFile(pngFile),
            "uuid-png-1"
        )
        assertTrue(pngResult.isSuccess)
        val origPngPath = pngResult.getOrThrow()
        assertTrue(origPngPath.endsWith(".png"))
        assertTrue(File(origPngPath).exists())

        val jpgResult = RegisterPhotoManager.saveOriginalPhotoFromUri(
            context,
            Uri.fromFile(jpgFile),
            "uuid-jpg-1"
        )
        assertTrue(jpgResult.isSuccess)
        val origJpgPath = jpgResult.getOrThrow()
        assertTrue(origJpgPath.endsWith(".jpg"))
        assertTrue(File(origJpgPath).exists())

        // Rotate 90 degrees and crop center 50% of the PNG
        val editedResult = RegisterPhotoManager.applyCropAndRotation(
            context = context,
            sourcePath = origPngPath,
            rotationDegrees = 90,
            cropLeftNorm = 0.25f,
            cropTopNorm = 0.25f,
            cropRightNorm = 0.75f,
            cropBottomNorm = 0.75f,
            recordUuid = "uuid-png-1"
        )
        assertTrue(editedResult.isSuccess)
        val editedPath = editedResult.getOrThrow()
        assertNotEquals(origPngPath, editedPath)
        assertTrue(File(editedPath).exists())

        // Original dimensions must remain untouched (400x200), edited bitmap reflects 90° rotate (200x400) -> 50% crop (100x200)
        val origBmp = RegisterPhotoManager.loadBitmapWithExif(origPngPath)!!
        val editedBmp = RegisterPhotoManager.loadBitmapWithExif(editedPath)!!
        assertEquals(400, origBmp.width)
        assertEquals(200, origBmp.height)
        assertEquals(100, editedBmp.width)
        assertEquals(200, editedBmp.height)
        origBmp.recycle()
        editedBmp.recycle()

        // Invalid non-image file must be rejected cleanly
        val badFile = File(context.cacheDir, "not_an_image.txt").apply { writeText("hello") }
        val badResult = RegisterPhotoManager.saveOriginalPhotoFromUri(context, Uri.fromFile(badFile), "uuid-bad")
        assertTrue(badResult.isFailure)
    }

    @Test
    fun `repository persists, edits, merges without overwriting, and safely deletes records and photos`() = runBlocking {
        val photo1 = createTestImageFile("p1.jpg", 200, 200, Bitmap.CompressFormat.JPEG)
        val orig1 = RegisterPhotoManager.saveOriginalPhotoFromFile(context, photo1, "rec-1").getOrThrow()

        val photo2 = createTestImageFile("p2.png", 200, 200, Bitmap.CompressFormat.PNG)
        val orig2 = RegisterPhotoManager.saveOriginalPhotoFromUri(context, Uri.fromFile(photo2), "rec-2").getOrThrow()

        val saved1 = registerRepo.saveNewRecord(
            registerDate = "2026-11-24",
            originalPhotoPath = orig1,
            processedPhotoPath = orig1,
            fullMasoorQty = 3,
            halfMasoorQty = 2,
            rotiQty = 6,
            rawTranscript = "Full 3, Half 2, Roti 6",
            unclearSummary = "",
            notes = "पान १",
            wasAiRead = true,
            recordUuid = "fixed-uuid-1"
        ).getOrThrow()
        assertEquals(490, saved1.totalIncome) // 300 + 100 + 90
        assertEquals(11, saved1.totalQuantity)

        // Attempt to save another record with the SAME recordUuid must NEVER overwrite saved1
        val saved2 = registerRepo.saveNewRecord(
            registerDate = "2026-11-25",
            originalPhotoPath = orig2,
            processedPhotoPath = orig2,
            fullMasoorQty = 1,
            halfMasoorQty = 1,
            rotiQty = 2,
            rawTranscript = "",
            unclearSummary = "",
            notes = "पान २",
            wasAiRead = false,
            recordUuid = "fixed-uuid-1"
        ).getOrThrow()
        assertNotEquals(saved1.recordUuid, saved2.recordUuid)
        assertEquals(2, registerRepo.getAllRecordsOnce().size)

        // Update record 1 quantities and verify recalculation
        val updated1 = registerRepo.updateExistingRecord(
            recordId = saved1.id,
            registerDate = "2026-11-24",
            fullMasoorQty = 4,
            halfMasoorQty = 2,
            rotiQty = 10,
            notes = "दुरुस्त केले"
        ).getOrThrow()
        assertEquals(650, updated1.totalIncome) // 400 + 100 + 150
        assertEquals(16, updated1.totalQuantity)

        // Merge restore payload with 1 existing UUID (must NOT overwrite) and 1 new UUID
        val existingCollision = updated1.copy(fullMasoorQty = 99, totalIncome = 9900)
        val newRestore = DailyRegisterRecordEntity(
            id = 0L,
            recordUuid = UUID.randomUUID().toString(),
            registerDate = "2026-11-20",
            registerDateMillis = DailyRegisterCalculator.parseIsoDateToMillis("2026-11-20")!!,
            originalPhotoPath = "",
            processedPhotoPath = "",
            fullMasoorQty = 2,
            fullMasoorRate = 100,
            fullMasoorIncome = 200,
            halfMasoorQty = 0,
            halfMasoorRate = 50,
            halfMasoorIncome = 0,
            rotiQty = 4,
            rotiRate = 15,
            rotiIncome = 60,
            totalQuantity = 6,
            totalIncome = 260,
            rawTranscript = "",
            unclearSummary = "",
            notes = "रिस्टोअर नोंद",
            wasAiRead = false,
            createdAtMillis = 1700000000000L,
            updatedAtMillis = 1700000000000L
        )
        val mergedCount = registerRepo.mergeRestoredRecords(listOf(existingCollision, newRestore)).getOrThrow()
        assertEquals(1, mergedCount)
        val allAfterMerge = registerRepo.getAllRecordsOnce()
        assertEquals(3, allAfterMerge.size)
        assertEquals(650, registerRepo.getById(saved1.id)!!.totalIncome)

        // Delete ONLY record 1 -> photo1 must be removed from disk while record 2 & photo2 stay untouched
        registerRepo.deleteRecords(listOf(saved1.id)).getOrThrow()
        assertEquals(2, registerRepo.getAllRecordsOnce().size)
        assertFalse(File(orig1).exists())
        assertTrue(File(orig2).exists())
    }

    @Test
    fun `compose photo register workflow uploads photo, calculates report, saves, views reports, and deletes with confirmation`() {
        val firestore = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val posVm = MasoorBillingViewModel(
            localRepo = billingRepo,
            cloudRepo = CloudBackupRepository(firestore),
            localRegisterRepo = registerRepo
        )
        val registerVm = DailyRegisterViewModel(
            localRepo = registerRepo,
            ocrRepo = RegisterOcrRepository()
        )

        composeTestRule.setContent {
            MasoorBillingTheme {
                MasoorBillingApp(
                    viewModel = posVm,
                    dailyRegisterViewModel = registerVm
                )
            }
        }
        composeTestRule.waitForIdle()

        // Switch from POS billing mode to Photo Daily Register mode
        composeTestRule.onNodeWithTag("mode_photo_register").performClick()
        composeTestRule.waitForIdle()

        // Load a valid PNG photo as though picked from Gallery
        val notebookPng = createTestImageFile("notebook_page.png", 360, 480, Bitmap.CompressFormat.PNG)
        registerVm.onGalleryPhotoSelected(context, Uri.fromFile(notebookPng))
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            registerVm.draftState.value.originalPhotoPath != null
        }
        composeTestRule.waitForIdle()

        val originalSavedPath = registerVm.draftState.value.originalPhotoPath!!
        assertTrue(File(originalSavedPath).exists())

        // Rotate photo right and verify processed photo changes while original stays safe
        composeTestRule.onNodeWithTag("photo_rotate_right_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            registerVm.draftState.value.rotationDegrees == 90 && !registerVm.draftState.value.isProcessingPhoto
        }
        composeTestRule.waitForIdle()

        // Trigger AI OCR while offline -> must show clear internet requirement error (not fake OCR)
        registerVm.runAiOcr(isNetworkConnected = false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("AI हस्तलिखित वाचनासाठी इंटरनेट आवश्यक आहे", substring = true)
            .performScrollTo()
            .assertExists()

        // Enter quantities: 2 Full (₹200) + 1 Half (₹50) + 4 Roti (₹60) = 7 items, ₹310
        composeTestRule.onNodeWithTag("register_full_qty_input").performScrollTo().performTextInput("२") // Marathi digit 2!
        composeTestRule.onNodeWithTag("register_half_qty_input").performScrollTo().performTextInput("1")
        composeTestRule.onNodeWithTag("register_roti_qty_input").performScrollTo().performTextInput("4")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("register_report_total_qty").performScrollTo().assertTextContains("7")
        composeTestRule.onNodeWithTag("register_report_total_income").performScrollTo().assertTextContains("310", substring = true)

        // Save permanently
        composeTestRule.onNodeWithTag("save_register_record_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            runBlocking { registerRepo.getAllRecordsOnce().isNotEmpty() }
        }
        composeTestRule.waitForIdle()

        val savedRecords = runBlocking { registerRepo.getAllRecordsOnce() }
        assertEquals(1, savedRecords.size)
        val savedRec = savedRecords.first()
        assertEquals(310, savedRec.totalIncome)
        assertEquals(7, savedRec.totalQuantity)
        assertEquals(originalSavedPath, savedRec.originalPhotoPath)

        // Switch to Reports tab and verify prominent total income = ₹310
        composeTestRule.onNodeWithTag("register_tab_reports").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("register_all_time_income").assertTextContains("310", substring = true)

        // Switch to History tab and delete the record via confirmation dialog
        composeTestRule.onNodeWithTag("register_tab_history").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("delete_register_record_${savedRec.id}").performClick()
        composeTestRule.waitForIdle()

        // Confirmation dialog appears before deleting
        composeTestRule.onNodeWithTag("confirm_delete_register_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            runBlocking { registerRepo.getAllRecordsOnce().isEmpty() }
        }
        composeTestRule.waitForIdle()

        // Both DB row and photo files are deleted cleanly
        assertFalse(File(originalSavedPath).exists())
        composeTestRule.onAllNodesWithText("नोंद हटवायची का?").assertCountEquals(0)
    }
}
