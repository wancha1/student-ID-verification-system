package com.example.crypto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
 * Robust, deterministic date formatting, parsing, and validity verification for student ID credentials.
 *
 * Explicit Timezone Policy:
 * All credential date calculations use the institutional timezone "Africa/Kampala" (UTC+3, Lira, Uganda).
 * This guarantees consistent date semantics across all issuer terminals and gate verifier devices
 * regardless of local system or emulator timezones.
 *
 * Stated date representation: "YYYY-MM-DD".
 * The validity period begins at 00:00:00.000 Kampala time on [validFrom]
 * and includes the entire stated day through 23:59:59.999 Kampala time on [validUntil].
 */
object CardDateUtils {
    const val DATE_FORMAT = "yyyy-MM-dd"
    const val TIMEZONE_ID = "Africa/Kampala"
    val INSTITUTIONAL_TIMEZONE: TimeZone = TimeZone.getTimeZone(TIMEZONE_ID)

    private val DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    private fun getSdf(): SimpleDateFormat {
        return SimpleDateFormat(DATE_FORMAT, Locale.US).apply {
            isLenient = false
            timeZone = INSTITUTIONAL_TIMEZONE
        }
    }

    fun isValidDateFormat(dateStr: String): Boolean {
        val trimmed = dateStr.trim()
        if (!DATE_REGEX.matches(trimmed)) return false
        return try {
            getSdf().parse(trimmed) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Parses the given "YYYY-MM-DD" string into epoch milliseconds corresponding to
     * 00:00:00.000 in the explicit "Africa/Kampala" timezone.
     */
    fun parseDateMillis(dateStr: String): Long? {
        val trimmed = dateStr.trim()
        if (!DATE_REGEX.matches(trimmed)) return null
        return try {
            getSdf().parse(trimmed)?.time
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Computes the default (validFrom, validUntil) range in "YYYY-MM-DD" canonical format.
     * Uses the current year in the explicit "Africa/Kampala" timezone.
     */
    fun getDefaultValidityRange(academicYear: String? = null): Pair<String, String> {
        val currentYearStr = SimpleDateFormat("yyyy", Locale.US).apply {
            timeZone = INSTITUTIONAL_TIMEZONE
        }.format(Date())

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
     *
     * Boundary Semantics:
     * - The period starts exactly at 00:00:00.000 on [validFrom] (Kampala time).
     * - The validity period extends through the very end of the [validUntil] day:
     *   exactly 23:59:59.999 (Kampala time), i.e. (start-of-day + 24 hours - 1 ms).
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

        // Include the entire final day until 23:59:59.999 in Africa/Kampala
        val endOfDayMillis = untilMillis + (24 * 60 * 60 * 1000L) - 1

        return when {
            referenceMillis < fromMillis -> ValidityCheckResult.NotYetValid(validFrom)
            referenceMillis > endOfDayMillis -> ValidityCheckResult.Expired(validUntil)
            else -> ValidityCheckResult.Valid
        }
    }
}
