package com.example.util

sealed class QrParseResult {
    data class ValidStudentNumber(val studentNumber: String) : QrParseResult()
    data class ValidInternalId(val internalId: String) : QrParseResult()
    data class Invalid(val rawString: String, val reason: String) : QrParseResult()
}

object QrCodeUtils {

    private const val PREFIX_STUDENT_NUMBER = "OAKRIDGE:STU:"
    private const val PREFIX_INTERNAL_ID = "OAKRIDGE:ID:"

    // Regex pattern for human-readable student numbers like OAK-2026-0001, STU-2026-0001, OAK-2025-1234
    private val STUDENT_NUMBER_REGEX = Regex("^(OAK|STU)-[0-9]{4}-[0-9]{3,5}$", RegexOption.IGNORE_CASE)

    // Regex pattern for UUIDs (36 characters with hyphens)
    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * Builds standard non-sensitive QR payload for a student.
     * Contains only an immutable student number pointer (no personal or financial details).
     */
    fun createPayload(studentNumber: String): String {
        return "$PREFIX_STUDENT_NUMBER${studentNumber.trim().uppercase()}"
    }

    /**
     * Parses and validates raw scanned QR content.
     */
    fun parseQrCode(rawScannedText: String): QrParseResult {
        val trimmed = rawScannedText.trim()
        if (trimmed.isBlank()) {
            return QrParseResult.Invalid(trimmed, "QR code content is empty or unreadable.")
        }

        // 1. Standard prefix with Student Number: OAKRIDGE:STU:OAK-2026-0001
        if (trimmed.startsWith(PREFIX_STUDENT_NUMBER, ignoreCase = true)) {
            val num = trimmed.substring(PREFIX_STUDENT_NUMBER.length).trim().uppercase()
            if (num.isBlank()) {
                return QrParseResult.Invalid(trimmed, "Malformed student number in QR code.")
            }
            return QrParseResult.ValidStudentNumber(num)
        }

        // 2. Standard prefix with Internal UUID: OAKRIDGE:ID:<uuid>
        if (trimmed.startsWith(PREFIX_INTERNAL_ID, ignoreCase = true)) {
            val id = trimmed.substring(PREFIX_INTERNAL_ID.length).trim()
            if (id.isBlank()) {
                return QrParseResult.Invalid(trimmed, "Malformed internal ID in QR code.")
            }
            return QrParseResult.ValidInternalId(id)
        }

        // 3. Direct student number match (e.g. OAK-2026-0001 or STU-2026-0001)
        if (STUDENT_NUMBER_REGEX.matches(trimmed)) {
            return QrParseResult.ValidStudentNumber(trimmed.uppercase())
        }

        // 4. Direct UUID match
        if (UUID_REGEX.matches(trimmed)) {
            return QrParseResult.ValidInternalId(trimmed)
        }

        // Reject sensitive JSON or non-standard payloads
        if (trimmed.startsWith("{") || trimmed.contains("\"fees\"") || trimmed.contains("\"phone\"")) {
            return QrParseResult.Invalid(
                trimmed,
                "Security violation: QR encodes raw personal/financial data instead of a secured pointer."
            )
        }

        return QrParseResult.Invalid(
            trimmed,
            "Invalid QR format. Expected Oakridge badge format (e.g. 'OAKRIDGE:STU:OAK-2026-0001')."
        )
    }
}
