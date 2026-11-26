package com.example.domain

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BillPaymentStatus {
    PAID,
    PARTIAL,
    UNPAID
}

data class BillCalculation(
    val fullMasoorTotal: Int,
    val halfMasoorTotal: Int,
    val rotiTotal: Int,
    val grandTotal: Int,
    val enteredCash: Int,
    val enteredOnline: Int,
    val effectiveCashReceived: Int,
    val effectiveOnlineReceived: Int,
    val totalReceived: Int,
    val pendingAmount: Int,
    val status: BillPaymentStatus,
    val hasUnconfirmedOnlineEntry: Boolean,
    val isOverpaid: Boolean,
    val validationError: String?
) {
    val canSave: Boolean
        get() = validationError == null && grandTotal > 0 && !hasUnconfirmedOnlineEntry && !isOverpaid
}

object BillingCalculator {
    const val DEFAULT_FULL_MASOOR_PRICE = 100
    const val DEFAULT_HALF_MASOOR_PRICE = 50
    const val DEFAULT_ROTI_PRICE = 15

    private val indianLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()

    fun calculate(
        fullMasoorQty: Int,
        fullMasoorPrice: Int,
        halfMasoorQty: Int,
        halfMasoorPrice: Int,
        rotiQty: Int,
        rotiPrice: Int,
        cashInput: Int,
        onlineInput: Int,
        onlineConfirmed: Boolean
    ): BillCalculation {
        val safeFullQty = fullMasoorQty.coerceAtLeast(0)
        val safeHalfQty = halfMasoorQty.coerceAtLeast(0)
        val safeRotiQty = rotiQty.coerceAtLeast(0)

        val safeFullPrice = fullMasoorPrice.coerceAtLeast(1)
        val safeHalfPrice = halfMasoorPrice.coerceAtLeast(1)
        val safeRotiPrice = rotiPrice.coerceAtLeast(1)

        val fullTotal = safeFullQty * safeFullPrice
        val halfTotal = safeHalfQty * safeHalfPrice
        val rotiTotal = safeRotiQty * safeRotiPrice
        val grandTotal = fullTotal + halfTotal + rotiTotal

        val safeCash = cashInput.coerceAtLeast(0)
        val safeOnline = onlineInput.coerceAtLeast(0)

        // Reliability invariant: NEVER mark online payment as received unless manually confirmed.
        val hasUnconfirmedOnline = safeOnline > 0 && !onlineConfirmed
        val effectiveOnline = if (onlineConfirmed) safeOnline else 0
        val totalEntered = safeCash + safeOnline
        val isOverpaid = grandTotal > 0 && totalEntered > grandTotal

        val totalReceived = (safeCash + effectiveOnline).coerceAtMost(grandTotal)
        val pendingAmount = (grandTotal - totalReceived).coerceAtLeast(0)

        val status = when {
            grandTotal == 0 -> BillPaymentStatus.UNPAID
            pendingAmount == 0 -> BillPaymentStatus.PAID
            totalReceived > 0 -> BillPaymentStatus.PARTIAL
            else -> BillPaymentStatus.UNPAID
        }

        val error = when {
            cashInput < 0 || onlineInput < 0 -> "रक्कम शून्यापेक्षा कमी असू शकत नाही"
            grandTotal == 0 -> "कृपया किमान एक पदार्थ निवडा"
            isOverpaid -> "जमा केलेली रक्कम एकूण बिलापेक्षा (₹$grandTotal) जास्त असू शकत नाही"
            hasUnconfirmedOnline -> "कृपया ऑनलाईन पेमेंट (₹$safeOnline) मिळाल्याची मॅन्युअल खात्री करा"
            else -> null
        }

        return BillCalculation(
            fullMasoorTotal = fullTotal,
            halfMasoorTotal = halfTotal,
            rotiTotal = rotiTotal,
            grandTotal = grandTotal,
            enteredCash = safeCash,
            enteredOnline = safeOnline,
            effectiveCashReceived = safeCash.coerceAtMost(grandTotal),
            effectiveOnlineReceived = effectiveOnline.coerceAtMost((grandTotal - safeCash).coerceAtLeast(0)),
            totalReceived = totalReceived,
            pendingAmount = pendingAmount,
            status = status,
            hasUnconfirmedOnlineEntry = hasUnconfirmedOnline,
            isOverpaid = isOverpaid,
            validationError = error
        )
    }

    fun parseNonNegativeIntOrNull(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0
        if (!trimmed.all { it.isDigit() }) return null
        return trimmed.toIntOrNull()?.takeIf { it >= 0 }
    }

    fun isValidMobileOrEmpty(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return true
        return trimmed.length == 10 && trimmed.all { it.isDigit() } && trimmed.first() in '6'..'9'
    }

    fun formatBillNumber(sequence: Int): String {
        val safeSeq = sequence.coerceAtLeast(1)
        return String.format(Locale.US, "MB-%04d", safeSeq)
    }

    fun extractBillSequence(billNumber: String): Int {
        val digits = billNumber.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    fun formatInr(amount: Int): String {
        val formatter = NumberFormat.getIntegerInstance(indianLocale)
        return "₹${formatter.format(amount)}"
    }

    fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", indianLocale)
        return sdf.format(Date(millis))
    }

    fun formatDateOnly(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", indianLocale)
        return sdf.format(Date(millis))
    }
}
