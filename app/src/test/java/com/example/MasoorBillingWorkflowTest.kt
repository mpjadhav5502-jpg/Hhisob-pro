package com.example

import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.LocalBillingRepository
import com.example.data.remote.CloudBackupRepository
import com.example.domain.BillingCalculator
import com.example.pdf.BillPdfGenerator
import com.example.ui.MasoorBillingApp
import com.example.ui.MasoorBillingViewModel
import com.example.ui.theme.MasoorBillingTheme
import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class MasoorBillingWorkflowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var localRepo: LocalBillingRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        localRepo = LocalBillingRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `offline billing repository persists bill, prevents duplicates, settles balance, and renders receipt`() = runBlocking {
        val defaults = localRepo.ensureDefaultsInitialized()
        assertEquals(100, defaults.fullMasoorPrice)
        assertEquals(50, defaults.halfMasoorPrice)
        assertEquals(15, defaults.rotiPrice)

        // Reject unconfirmed online payment
        val unconfirmedResult = localRepo.saveBill(
            customerNameRaw = "रमेश पाटील",
            customerMobileRaw = "9876543210",
            fullMasoorQty = 2,
            halfMasoorQty = 1,
            rotiQty = 4,
            cashInput = 200,
            onlineInput = 50,
            onlineConfirmed = false
        )
        assertTrue(unconfirmedResult.isFailure)

        // Save valid bill with confirmed online payment
        val saveResult = localRepo.saveBill(
            customerNameRaw = "रमेश पाटील",
            customerMobileRaw = "9876543210",
            fullMasoorQty = 2,
            halfMasoorQty = 1,
            rotiQty = 4,
            cashInput = 200,
            onlineInput = 50,
            onlineConfirmed = true
        )
        assertTrue(saveResult.isSuccess)
        val savedBill = saveResult.getOrThrow()
        assertEquals("MB-0001", savedBill.billNumber)
        assertEquals(310, savedBill.grandTotal)
        assertEquals(200, savedBill.cashPaid)
        assertEquals(50, savedBill.onlinePaid)
        assertEquals(250, savedBill.totalReceived)
        assertEquals(60, savedBill.pendingAmount)

        // Second bill automatically increments sequence and never duplicates MB-0001
        val secondSave = localRepo.saveBill(
            customerNameRaw = "सुरेश जाधव",
            customerMobileRaw = "",
            fullMasoorQty = 1,
            halfMasoorQty = 0,
            rotiQty = 2,
            cashInput = 130,
            onlineInput = 0,
            onlineConfirmed = false
        ).getOrThrow()
        assertEquals("MB-0002", secondSave.billNumber)
        assertEquals(130, secondSave.grandTotal)
        assertEquals(0, secondSave.pendingAmount)

        // Verify receipt page canvas drawing succeeds cleanly
        val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        BillPdfGenerator.drawBillPage(canvas, 595, 842, savedBill, defaults)
        assertEquals(595, bitmap.width)

        // Settle remaining ₹60 balance
        val settled = localRepo.settlePendingPayment(
            billId = savedBill.id,
            addCash = 60,
            addOnline = 0,
            onlineConfirmed = false
        ).getOrThrow()
        assertEquals(310, settled.totalReceived)
        assertEquals(0, settled.pendingAmount)

        // Edit prices in settings and confirm historical bills retain original price snapshot
        localRepo.updateSettings(
            shopName = "हॉटेल आखा मसूर पॅलेस",
            shopSubtitle = "कोल्हापूर",
            shopPhone = "9999999999",
            shopAddress = "कोल्हापूर",
            fullMasoorPrice = 120,
            halfMasoorPrice = 60,
            rotiPrice = 20
        ).getOrThrow()

        val thirdBill = localRepo.saveBill(
            customerNameRaw = "रोख ग्राहक",
            customerMobileRaw = "",
            fullMasoorQty = 1,
            halfMasoorQty = 0,
            rotiQty = 1,
            cashInput = 140,
            onlineInput = 0,
            onlineConfirmed = false
        ).getOrThrow()
        assertEquals(140, thirdBill.grandTotal)

        val allBills = localRepo.billsFlow.first()
        assertEquals(3, allBills.size)
        assertEquals(310, allBills.first { it.billNumber == "MB-0001" }.grandTotal)
    }

    @Test
    fun `compose billing UI computes exact 310 grand total and 60 pending balance`() {
        val firestore = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
        val vm = MasoorBillingViewModel(
            localRepo = localRepo,
            cloudRepo = CloudBackupRepository(firestore)
        )

        composeTestRule.setContent {
            MasoorBillingTheme {
                MasoorBillingApp(viewModel = vm)
            }
        }
        composeTestRule.waitForIdle()

        // Enter customer name & mobile
        composeTestRule.onNodeWithTag("customer_name_input").performScrollTo().performTextInput("रमेश पाटील")
        composeTestRule.onNodeWithTag("customer_mobile_input").performScrollTo().performTextInput("9876543210")

        // 2 Full Akha Masoor, 1 Half Pakka Masoor, 4 Roti
        composeTestRule.onNodeWithTag("full_masoor_plus").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("full_masoor_plus").performClick()
        composeTestRule.onNodeWithTag("half_masoor_plus").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("roti_plus").performScrollTo()
        repeat(4) {
            composeTestRule.onNodeWithTag("roti_plus").performClick()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("grand_total_text").performScrollTo().assertTextContains("310", substring = true)

        // Enter Cash = 200, Online = 50, manually confirm Online
        composeTestRule.onNodeWithTag("cash_payment_input").performScrollTo().performTextInput("200")
        composeTestRule.onNodeWithTag("online_payment_input").performScrollTo().performTextInput("50")
        composeTestRule.waitForIdle()

        // Before confirmation: pending should be 110 (310 - 200)
        composeTestRule.onNodeWithTag("pending_balance_text").performScrollTo().assertTextContains("110", substring = true)

        // Confirm online payment manually
        composeTestRule.onNodeWithTag("online_confirm_checkbox").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // After confirmation: received = 250, pending = 60
        composeTestRule.onNodeWithTag("total_received_text").performScrollTo().assertTextContains("250", substring = true)
        composeTestRule.onNodeWithTag("pending_balance_text").performScrollTo().assertTextContains("60", substring = true)

        // Save bill
        composeTestRule.onNodeWithTag("save_bill_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify persisted in Room
        runBlocking {
            val savedBills = localRepo.billsFlow.first()
            assertEquals(1, savedBills.size)
            assertEquals(310, savedBills.first().grandTotal)
            assertEquals(60, savedBills.first().pendingAmount)
        }
    }
}
