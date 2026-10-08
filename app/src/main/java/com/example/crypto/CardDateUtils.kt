package com.example.crypto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of checking credential validity against current date.
 */
sealed class ValidityCheckResult {
    object Valid : ValidityCheckResult()
    data class NotYetValid(val validFrom: String) : ValidityCheckResult()
    data class Expired(val validUntil: String) : ValidityCheckResult()
    data class MalformedDates(val reason: String) : ValidityCheckResult()
}

/**
 * Robust date formatting, parsing, and validity verification for student ID credentials.
 * Uses standard SimpleDateFormat to ensure 100% compatibility across Android minSdk 24+.
 */
object CardDateUtils {

    const val DATE_FORMAT = "yyyy-MM-dd"
    private val DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun isValidDateFormat(dateStr: String): Boolean {
        val trimmed = dateStr.trim()
        if (!DATE_REGEX.matches(trimmed)) return false
        return try {
            val sdf = SimpleDateFormat(DATE_FORMAT, Locale.US).apply { isLenient = false }
            sdf.parse(trimmed) != null
        } catch (_: Exception) {
            false
        }
    }

    fun parseDateMillis(dateStr: String): Long? {
        val trimmed = dateStr.trim()
        if (!DATE_REGEX.matches(trimmed)) return null
        return try {
            val sdf = SimpleDateFormat(DATE_FORMAT, Locale.US).apply { isLenient = false }
            sdf.parse(trimmed)?.time
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Computes the default (validFrom, validUntil) range.
     * If an academic year like "2026" or "2025/2026" is passed, derives the range accordingly.
     * Defaults to the current calendar year (e.g. "2026-01-01" to "2026-12-31").
     */
    fun getDefaultValidityRange(academicYear: String? = null): Pair<String, String> {
        val currentYearStr = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val (startYear, endYear) = if (!academicYear.isNullOrBlank()) {
            val cleaned = academicYear.trim()
            if (cleaned.contains("/")) {
                val parts = cleaned.split("/")
                val start = parts.getOrNull(0)?.trim() ?: currentYearStr
                val end = parts.getOrNull(1)?.trim() ?: currentYearStr
                Pair(start, end)
            } else if (cleaned.length == 4 && cleaned.all { it.isDigit() }) {
                Pair(cleaned, cleaned)
            } else {
                Pair(currentYearStr, currentYearStr)
            }
        } else {
            Pair(currentYearStr, currentYearStr)
        }

        return Pair("$startYear-01-01", "$endYear-12-31")
    }

    /**
     * Checks if [referenceMillis] falls within [[validFrom], [validUntil]].
     * The validity period extends through the very end of the [validUntil] day (23:59:59.999).
     */
    fun checkValidity(
        validFrom: String,
        validUntil: String,
        referenceMillis: Long = System.currentTimeMillis()
    ): ValidityCheckResult {
        val fromMillis = parseDateMillis(validFrom)
            ?: return ValidityCheckResult.MalformedDates("Invalid validFrom date format: '$validFrom'. Expected 'YYYY-MM-DD'.")
        val untilMillis = parseDateMillis(validUntil)
            ?: return ValidityCheckResult.MalformedDates("Invalid validUntil date format: '$validUntil'. Expected 'YYYY-MM-DD'.")

        if (fromMillis > untilMillis) {
            return ValidityCheckResult.MalformedDates("validFrom ($validFrom) cannot be after validUntil ($validUntil).")
        }

        // Include the entire final day until 23:59:59.999
        val endOfDayMillis = untilMillis + (24 * 60 * 60 * 1000L) - 1

        return when {
            referenceMillis < fromMillis -> ValidityCheckResult.NotYetValid(validFrom)
            referenceMillis > endOfDayMillis -> ValidityCheckResult.Expired(validUntil)
            else -> ValidityCheckResult.Valid
        }
    }
}
