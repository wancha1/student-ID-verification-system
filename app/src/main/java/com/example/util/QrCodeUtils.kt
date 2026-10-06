package com.example.util

sealed class QrParseResult {
    /**
     * Authenticated Protocol V2 QR payload: "LTC:V2:<cardId>:<signature>".
     * Syntactically valid V2 payload containing random cardId and Ed25519 signature.
     */
    data class ValidV2Card(
        val cardId: String,
        val signature: String,
        val rawPayload: String
    ) : QrParseResult()

    /**
     * Explicitly isolated legacy format result.
     * Used ONLY by explicit administrative reissue/inspection tools.
     * Strictly rejected from the normal production verification path.
     */
    data class LegacyUnsigned(
        val rawPayload: String,
        val studentNumber: String?,
        val cardIdentifier: String?,
        val formatDescription: String
    ) : QrParseResult()

    data class Invalid(
        val rawString: String,
        val reason: String
    ) : QrParseResult()
}

object QrCodeUtils {

    const val CURRENT_VERSION = "V2"
    const val PREFIX_LTC_V2 = "LTC:V2:"

    // Legacy format prefixes - strictly isolated from the production verification path
    const val PREFIX_LTC_V1 = "LTC:V1:"
    const val PREFIX_LTC_STU = "LTC:STU:"
    const val PREFIX_OAKRIDGE_STU = "OAKRIDGE:STU:"
    const val PREFIX_OAKRIDGE_ID = "OAKRIDGE:ID:"
    const val PREFIX_LTC_ID = "LTC:ID:"

    // Regex for bare student numbers: LTC-2026-0001, OAK-2026-0001, STU-2026-0001
    private val STUDENT_NUMBER_REGEX = Regex("^(LTC|OAK|STU)-[0-9]{4}-[0-9]{3,5}$", RegexOption.IGNORE_CASE)

    // Regex for standard UUIDs (36 characters with hyphens)
    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * Builds an authenticated, versioned, non-sensitive V2 QR payload for Lira Town College.
     * Canonical structure: "LTC:V2:<cardId>:<signatureBase64Url>"
     *
     * Never includes student personal info, fees, guardian contacts, or sensitive data.
     */
    fun createV2Payload(cardIdentifier: String): String {
        return CardCryptoManager.signCardPayload(cardIdentifier)
    }

    /**
     * Builds standardized QR payload.
     * In V2, creates an Ed25519-signed canonical payload for the given [cardIdentifier].
     * If no card identifier is provided, generates a secure random 128-bit card ID and signs it.
     */
    fun createPayload(studentNumber: String, cardIdentifier: String? = null): String {
        val effectiveCardId = if (!cardIdentifier.isNullOrBlank()) {
            cardIdentifier.trim().uppercase()
        } else {
            CardCryptoManager.generateSecureRandomCardId()
        }
        return CardCryptoManager.signCardPayload(effectiveCardId)
    }

    /**
     * Legacy V1 helper strictly for backward-compatible historical testing and migration tools.
     * Must never be emitted for newly issued cards.
     */
    fun createLegacyV1Payload(studentNumber: String, cardIdentifier: String? = null): String {
        val cleanNumber = studentNumber.trim().uppercase()
        return if (!cardIdentifier.isNullOrBlank()) {
            "$PREFIX_LTC_V1$cleanNumber:${cardIdentifier.trim().uppercase()}"
        } else {
            "$PREFIX_LTC_STU$cleanNumber"
        }
    }

    /**
     * Strict production QR parser.
     * Enforces the V2 Trust Model:
     * - ONLY canonical "LTC:V2:<cardId>:<signature>" payloads are accepted for verification.
     * - Bare student numbers, bare UUIDs, OAKRIDGE:* legacy formats, and unsigned V1 payloads
     *   are explicitly rejected.
     */
    fun parseQrCode(rawScannedText: String): QrParseResult {
        val trimmed = rawScannedText.trim()
        if (trimmed.isBlank()) {
            return QrParseResult.Invalid(trimmed, "QR code content is empty or unreadable.")
        }

        // 1. Strict Protocol V2 Check: "LTC:V2:<cardId>:<signature>"
        if (trimmed.startsWith(PREFIX_LTC_V2, ignoreCase = true)) {
            val parts = trimmed.split(":")
            if (parts.size != 4) {
                return QrParseResult.Invalid(
                    trimmed,
                    "Malformed V2 QR syntax. Expected 'LTC:V2:<cardId>:<signature>' with exactly 4 segments."
                )
            }
            if (!parts[0].equals("LTC", ignoreCase = true) || !parts[1].equals("V2", ignoreCase = true)) {
                return QrParseResult.Invalid(trimmed, "Invalid protocol header. Expected 'LTC:V2'.")
            }

            val cardId = parts[2].trim().uppercase()
            val signature = parts[3].trim()

            if (cardId.isBlank() || signature.isBlank()) {
                return QrParseResult.Invalid(trimmed, "Malformed V2 QR code: card identifier or signature segment is empty.")
            }

            // Card ID must contain only valid safe characters
            if (cardId.length < 6 || !cardId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                return QrParseResult.Invalid(trimmed, "Malformed card identifier character set in V2 QR code.")
            }

            return QrParseResult.ValidV2Card(
                cardId = cardId,
                signature = signature,
                rawPayload = trimmed
            )
        }

        // 2. Reject Legacy Unsigned LTC V1
        if (trimmed.startsWith(PREFIX_LTC_V1, ignoreCase = true)) {
            return QrParseResult.Invalid(
                trimmed,
                "Rejected: Legacy unsigned LTC V1 badge format is not accepted in production verification path. Reissue to signed V2 badge required."
            )
        }

        // 3. Reject Legacy LTC:STU prefix
        if (trimmed.startsWith(PREFIX_LTC_STU, ignoreCase = true)) {
            return QrParseResult.Invalid(
                trimmed,
                "Rejected: Legacy unsigned LTC:STU format is not accepted in production verification path."
            )
        }

        // 4. Reject Legacy OAKRIDGE prefixes
        if (trimmed.startsWith(PREFIX_OAKRIDGE_STU, ignoreCase = true) || trimmed.startsWith(PREFIX_OAKRIDGE_ID, ignoreCase = true)) {
            return QrParseResult.Invalid(
                trimmed,
                "Rejected: Legacy OAKRIDGE format is not accepted in production verification path."
            )
        }

        // 5. Reject Internal ID prefix
        if (trimmed.startsWith(PREFIX_LTC_ID, ignoreCase = true)) {
            return QrParseResult.Invalid(
                trimmed,
                "Rejected: Internal database UUID format is not accepted in production verification path."
            )
        }

        // 6. Reject Bare Student Numbers
        if (STUDENT_NUMBER_REGEX.matches(trimmed)) {
            return QrParseResult.Invalid(
                trimmed,
                "Rejected: Bare student numbers are not accepted as production QR identifiers. Cryptographically signed V2 badge required."
            )
        }

        // 7. Reject Bare UUIDs
        if (UUID_REGEX.matches(trimmed)) {
            return QrParseResult.Invalid(
                trimmed,
                "Rejected: Bare UUIDs are not accepted as production QR identifiers. Cryptographically signed V2 badge required."
            )
        }

        // 8. Reject raw JSON / personal info payloads
        if (trimmed.startsWith("{") || trimmed.contains("\"fees\"") || trimmed.contains("\"phone\"")) {
            return QrParseResult.Invalid(
                trimmed,
                "Security violation: QR encodes raw personal or financial data instead of a signed card pointer."
            )
        }

        return QrParseResult.Invalid(
            trimmed,
            "Invalid QR format. Expected cryptographically signed Lira Town College badge ('LTC:V2:<cardId>:<signature>')."
        )
    }

    /**
     * Explicitly isolated legacy parser for administrative migration and card reissue tools.
     * Strictly separated from the production gate verification path.
     */
    fun parseLegacyCardIsolated(rawScannedText: String): QrParseResult.LegacyUnsigned? {
        val trimmed = rawScannedText.trim()
        if (trimmed.startsWith(PREFIX_LTC_V1, ignoreCase = true)) {
            val content = trimmed.substring(PREFIX_LTC_V1.length).trim()
            val parts = content.split(":")
            val studentNumber = parts.getOrNull(0)?.trim()?.uppercase()
            val cardId = parts.getOrNull(1)?.trim()?.uppercase()
            return QrParseResult.LegacyUnsigned(
                rawPayload = trimmed,
                studentNumber = studentNumber,
                cardIdentifier = cardId,
                formatDescription = "LTC V1 Legacy Payload"
            )
        }
        if (trimmed.startsWith(PREFIX_LTC_STU, ignoreCase = true)) {
            val payload = trimmed.substring(PREFIX_LTC_STU.length).trim()
            val parts = payload.split(":")
            return QrParseResult.LegacyUnsigned(
                rawPayload = trimmed,
                studentNumber = parts.getOrNull(0)?.trim()?.uppercase(),
                cardIdentifier = null,
                formatDescription = "LTC STU Legacy Payload"
            )
        }
        if (trimmed.startsWith(PREFIX_OAKRIDGE_STU, ignoreCase = true)) {
            val payload = trimmed.substring(PREFIX_OAKRIDGE_STU.length).trim()
            val parts = payload.split(":")
            return QrParseResult.LegacyUnsigned(
                rawPayload = trimmed,
                studentNumber = parts.getOrNull(0)?.trim()?.uppercase(),
                cardIdentifier = null,
                formatDescription = "Oakridge STU Legacy Payload"
            )
        }
        if (STUDENT_NUMBER_REGEX.matches(trimmed)) {
            return QrParseResult.LegacyUnsigned(
                rawPayload = trimmed,
                studentNumber = trimmed.uppercase(),
                cardIdentifier = null,
                formatDescription = "Bare Student Number"
            )
        }
        return null
    }
}
