package com.example.util

sealed class QrParseResult {
    data class ValidStudentNumber(
        val studentNumber: String,
        val cardIdentifier: String? = null,
        val token: String? = null
    ) : QrParseResult()

    data class ValidInternalId(
        val internalId: String
    ) : QrParseResult()

    data class Invalid(
        val rawString: String,
        val reason: String
    ) : QrParseResult()
}

object QrCodeUtils {

    const val CURRENT_VERSION = "V1"
    const val PREFIX_LTC_V1 = "LTC:V1:"
    const val PREFIX_LTC_STU = "LTC:STU:"
    const val PREFIX_OAKRIDGE_STU = "OAKRIDGE:STU:"
    const val PREFIX_OAKRIDGE_ID = "OAKRIDGE:ID:"

    // Regex pattern for human-readable student numbers: LTC-2026-0001, OAK-2026-0001, STU-2026-0001
    private val STUDENT_NUMBER_REGEX = Regex("^(LTC|OAK|STU)-[0-9]{4}-[0-9]{3,5}$", RegexOption.IGNORE_CASE)

    // Regex pattern for standard UUIDs (36 characters with hyphens)
    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * Builds standardized, versioned, non-sensitive QR payload for Lira Town College.
     * Encodes student registration number + card identifier/token.
     * Example: "LTC:V1:LTC-2026-0001:CRD-0001-01"
     */
    fun createPayload(studentNumber: String, cardIdentifier: String? = null): String {
        val cleanNumber = studentNumber.trim().uppercase()
        return if (!cardIdentifier.isNullOrBlank()) {
            "$PREFIX_LTC_V1$cleanNumber:${cardIdentifier.trim().uppercase()}"
        } else {
            "$PREFIX_LTC_STU$cleanNumber"
        }
    }

    /**
     * Parses and validates raw scanned QR content.
     * Supports both modern LTC:V1 / LTC:STU and backward-compatible OAKRIDGE formats.
     */
    fun parseQrCode(rawScannedText: String): QrParseResult {
        val trimmed = rawScannedText.trim()
        if (trimmed.isBlank()) {
            return QrParseResult.Invalid(trimmed, "QR code content is empty or unreadable.")
        }

        // 1. Modern Versioned Format: LTC:V1:<studentNumber>:<cardIdentifierOrToken>
        if (trimmed.startsWith(PREFIX_LTC_V1, ignoreCase = true)) {
            val content = trimmed.substring(PREFIX_LTC_V1.length).trim()
            val parts = content.split(":")
            val studentNumber = parts.getOrNull(0)?.trim()?.uppercase() ?: ""
            val cardIdOrToken = parts.getOrNull(1)?.trim()?.uppercase()

            if (studentNumber.isBlank()) {
                return QrParseResult.Invalid(trimmed, "Malformed student number in LTC V1 QR code.")
            }
            return QrParseResult.ValidStudentNumber(
                studentNumber = studentNumber,
                cardIdentifier = cardIdOrToken,
                token = parts.getOrNull(2)?.trim()?.uppercase() ?: cardIdOrToken
            )
        }

        // 2. Standard LTC prefix: LTC:STU:<studentNumber> (with optional token)
        if (trimmed.startsWith(PREFIX_LTC_STU, ignoreCase = true)) {
            val payload = trimmed.substring(PREFIX_LTC_STU.length).trim()
            val parts = payload.split(":")
            val studentNumber = parts.getOrNull(0)?.trim()?.uppercase() ?: ""
            val token = parts.getOrNull(1)?.trim()?.uppercase()

            if (studentNumber.isBlank()) {
                return QrParseResult.Invalid(trimmed, "Malformed student number in LTC QR code.")
            }
            return QrParseResult.ValidStudentNumber(
                studentNumber = studentNumber,
                token = token
            )
        }

        // 3. Backward-compatible Oakridge prefix: OAKRIDGE:STU:<studentNumber>[:token]
        if (trimmed.startsWith(PREFIX_OAKRIDGE_STU, ignoreCase = true)) {
            val payload = trimmed.substring(PREFIX_OAKRIDGE_STU.length).trim()
            val parts = payload.split(":")
            val num = parts.getOrNull(0)?.trim()?.uppercase() ?: ""
            val token = parts.getOrNull(1)?.trim()?.uppercase()

            if (num.isBlank()) {
                return QrParseResult.Invalid(trimmed, "Malformed student number in QR code.")
            }
            return QrParseResult.ValidStudentNumber(
                studentNumber = num,
                token = token
            )
        }

        // 4. Internal UUID prefix: OAKRIDGE:ID:<uuid> or LTC:ID:<uuid>
        if (trimmed.startsWith(PREFIX_OAKRIDGE_ID, ignoreCase = true) || trimmed.startsWith("LTC:ID:", ignoreCase = true)) {
            val prefixLen = if (trimmed.startsWith("LTC:ID:", ignoreCase = true)) 7 else PREFIX_OAKRIDGE_ID.length
            val id = trimmed.substring(prefixLen).trim()
            if (id.isBlank()) {
                return QrParseResult.Invalid(trimmed, "Malformed internal ID in QR code.")
            }
            return QrParseResult.ValidInternalId(id)
        }

        // 5. Direct student number match (e.g. LTC-2026-0001, OAK-2026-0001, STU-2026-0001)
        if (STUDENT_NUMBER_REGEX.matches(trimmed)) {
            return QrParseResult.ValidStudentNumber(
                studentNumber = trimmed.uppercase()
            )
        }

        // 6. Direct UUID match
        if (UUID_REGEX.matches(trimmed)) {
            return QrParseResult.ValidInternalId(trimmed)
        }

        // Reject sensitive JSON or raw unencrypted personal/financial payloads
        if (trimmed.startsWith("{") || trimmed.contains("\"fees\"") || trimmed.contains("\"phone\"")) {
            return QrParseResult.Invalid(
                trimmed,
                "Security violation: QR encodes raw personal or financial data instead of a secured pointer."
            )
        }

        return QrParseResult.Invalid(
            trimmed,
            "Invalid QR format. Expected Lira Town College badge format (e.g. 'LTC:V1:LTC-2026-0001:CRD-0001-01')."
        )
    }
}
