package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyRegisterCalculation(
    val fullMasoorQty: Int,
    val fullMasoorRate: Int,
    val fullMasoorIncome: Int,
    val halfMasoorQty: Int,
    val halfMasoorRate: Int,
    val halfMasoorIncome: Int,
    val rotiQty: Int,
    val rotiRate: Int,
    val rotiIncome: Int,
    val totalQuantity: Int,
    val totalIncome: Int
)

object DailyRegisterCalculator {
    const val FIXED_FULL_MASOOR_RATE = 100
    const val FIXED_HALF_MASOOR_RATE = 50
    const val FIXED_ROTI_RATE = 15

    private val isoLocale = Locale.US
    private val indianLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
    private val marathiLocale = Locale.Builder().setLanguage("mr").setRegion("IN").build()

    fun calculate(
        fullMasoorQty: Int,
        halfMasoorQty: Int,
        rotiQty: Int
    ): DailyRegisterCalculation {
        val safeFull = fullMasoorQty.coerceAtLeast(0)
        val safeHalf = halfMasoorQty.coerceAtLeast(0)
        val safeRoti = rotiQty.coerceAtLeast(0)

        val fullIncome = safeFull * FIXED_FULL_MASOOR_RATE
        val halfIncome = safeHalf * FIXED_HALF_MASOOR_RATE
        val rotiIncome = safeRoti * FIXED_ROTI_RATE

        return DailyRegisterCalculation(
            fullMasoorQty = safeFull,
            fullMasoorRate = FIXED_FULL_MASOOR_RATE,
            fullMasoorIncome = fullIncome,
            halfMasoorQty = safeHalf,
            halfMasoorRate = FIXED_HALF_MASOOR_RATE,
            halfMasoorIncome = halfIncome,
            rotiQty = safeRoti,
            rotiRate = FIXED_ROTI_RATE,
            rotiIncome = rotiIncome,
            totalQuantity = safeFull + safeHalf + safeRoti,
            totalIncome = fullIncome + halfIncome + rotiIncome
        )
    }

    /**
     * Converts Devanagari numerals (०-९) into ASCII digits (0-9) and strips non-digits.
     */
    fun normalizeQuantityInput(raw: String): String {
        val mapped = buildString(raw.length) {
            for (ch in raw) {
                when (ch) {
                    in '0'..'9' -> append(ch)
                    in '०'..'९' -> append(('0'.code + (ch.code - '०'.code)).toChar())
                }
            }
        }
        return mapped.take(6)
    }

    fun formatIsoDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", isoLocale)
        return sdf.format(Date(millis))
    }

    fun parseIsoDateToMillis(isoDate: String): Long? {
        val trimmed = isoDate.trim()
        if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(trimmed)) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", isoLocale).apply {
                isLenient = false
            }
            val parsed = sdf.parse(trimmed) ?: return null
            // Normalize to noon local time to prevent timezone day-shift
            val cal = Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    fun formatDisplayDate(isoDate: String): String {
        val millis = parseIsoDateToMillis(isoDate) ?: return isoDate
        val sdf = SimpleDateFormat("dd MMM yyyy", indianLocale)
        return sdf.format(Date(millis))
    }

    fun formatMonthKey(isoDate: String): String {
        return if (isoDate.length >= 7) isoDate.substring(0, 7) else formatIsoDate(System.currentTimeMillis()).substring(0, 7)
    }

    fun formatMonthLabel(monthOffset: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
        val sdf = SimpleDateFormat("MMMM yyyy", marathiLocale)
        return sdf.format(cal.time)
    }

    fun getMonthKeyForOffset(monthOffset: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
        val sdf = SimpleDateFormat("yyyy-MM", isoLocale)
        return sdf.format(cal.time)
    }
}
