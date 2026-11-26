package com.example

import com.example.domain.BillPaymentStatus
import com.example.domain.BillingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingCalculatorTest {

    @Test
    fun `prompt scenario calculates 2 Full Masoor, 1 Half Masoor, 4 Roti, Cash 200, Online 50, Pending 60`() {
        val result = BillingCalculator.calculate(
            fullMasoorQty = 2,
            fullMasoorPrice = BillingCalculator.DEFAULT_FULL_MASOOR_PRICE, // 100
            halfMasoorQty = 1,
            halfMasoorPrice = BillingCalculator.DEFAULT_HALF_MASOOR_PRICE, // 50
            rotiQty = 4,
            rotiPrice = BillingCalculator.DEFAULT_ROTI_PRICE, // 15
            cashInput = 200,
            onlineInput = 50,
            onlineConfirmed = true
        )

        assertEquals(200, result.fullMasoorTotal)
        assertEquals(50, result.halfMasoorTotal)
        assertEquals(60, result.rotiTotal)
        assertEquals(310, result.grandTotal)
        assertEquals(200, result.effectiveCashReceived)
        assertEquals(50, result.effectiveOnlineReceived)
        assertEquals(250, result.totalReceived)
        assertEquals(60, result.pendingAmount)
        assertEquals(BillPaymentStatus.PARTIAL, result.status)
        assertTrue(result.canSave)
        assertNull(result.validationError)
    }

    @Test
    fun `unconfirmed online payment is not counted as received and blocks save`() {
        val result = BillingCalculator.calculate(
            fullMasoorQty = 2,
            fullMasoorPrice = 100,
            halfMasoorQty = 1,
            halfMasoorPrice = 50,
            rotiQty = 4,
            rotiPrice = 15,
            cashInput = 200,
            onlineInput = 50,
            onlineConfirmed = false
        )

        assertEquals(310, result.grandTotal)
        assertEquals(200, result.effectiveCashReceived)
        assertEquals(0, result.effectiveOnlineReceived)
        assertEquals(200, result.totalReceived)
        assertEquals(110, result.pendingAmount)
        assertTrue(result.hasUnconfirmedOnlineEntry)
        assertFalse(result.canSave)
        assertNotNull(result.validationError)
    }

    @Test
    fun `overpayment beyond grand total is rejected`() {
        val result = BillingCalculator.calculate(
            fullMasoorQty = 1,
            fullMasoorPrice = 100,
            halfMasoorQty = 0,
            halfMasoorPrice = 50,
            rotiQty = 0,
            rotiPrice = 15,
            cashInput = 80,
            onlineInput = 50,
            onlineConfirmed = true
        )

        assertEquals(100, result.grandTotal)
        assertTrue(result.isOverpaid)
        assertFalse(result.canSave)
        assertNotNull(result.validationError)
    }

    @Test
    fun `negative payment inputs are rejected`() {
        val result = BillingCalculator.calculate(
            fullMasoorQty = 1,
            fullMasoorPrice = 100,
            halfMasoorQty = 0,
            halfMasoorPrice = 50,
            rotiQty = 0,
            rotiPrice = 15,
            cashInput = -10,
            onlineInput = 0,
            onlineConfirmed = false
        )

        assertFalse(result.canSave)
        assertNotNull(result.validationError)
        assertNull(BillingCalculator.parseNonNegativeIntOrNull("-10"))
    }

    @Test
    fun `mobile validation and bill number formatting work properly`() {
        assertTrue(BillingCalculator.isValidMobileOrEmpty(""))
        assertTrue(BillingCalculator.isValidMobileOrEmpty("9876543210"))
        assertFalse(BillingCalculator.isValidMobileOrEmpty("12345"))
        assertFalse(BillingCalculator.isValidMobileOrEmpty("1234567890"))
        assertEquals("MB-0001", BillingCalculator.formatBillNumber(1))
        assertEquals("MB-0042", BillingCalculator.formatBillNumber(42))
    }
}
