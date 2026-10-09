package com.example.util

import com.example.crypto.CardCryptoUtils
import com.example.crypto.CardDateUtils

sealed class QrParseResult {
    /**
     * Authenticated Protocol V2 QR payload: "LTC:V2:<kid>:<cardId>:<validFrom>:<validUntil>:<signature>".
     * Syntactically valid V2 payload containing deterministic kid, random cardId, validFrom, validUntil, and ECDSA signature.
     */
    data class ValidV2Card(
        override val kid: String,
        override val cardId: String,
        override val validFrom: String,
        override val validUntil: String,
        override val signature: String,
        val canonicalMessage: String,
        val rawPayload: String
    ) : QrParseResult(), QrCodeUtils.ValidV2CardData

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

    interface ValidV2CardData {
        val kid: String
        val cardId: String
        val validFrom: String
        val validUntil: String
        val signature: String
    }

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

    // Regex for valid 16-hex key ID (kid)
    private val KID_REGEX = Regex("^[0-9a-fA-F]{16}$")

    /**
     * Builds an authenticated, versioned, non-sensitive V2 QR payload for Lira Town College.
     * Canonical structure: "LTC:V2:<kid>:<cardId>:<validFrom>:<validUntil>:<signatureBase64Url>"
     *
     * Never includes student personal info, fees, guardian contacts, or sensitive data.
     */
    fun createV2Payload(
        cardIdentifier: String,
        validFrom: String? = null,
        validUntil: String? = null
    ): String {
        return CardCryptoManager.signCardPayload(cardIdentifier, validFrom, validUntil)
    }

    /**
     * Explicitly signs and builds an authoritative V2 QR payload for [cardIdentifier].
     * Requires the active issuer signer to be configured in [CardCryptoManager].
     *
     * @throws IllegalStateException if the device does not possess authoritative issuer signing capability.
     */
    fun createPayload(
        studentNumber: String,
        cardIdentifier: String? = null,
        validFrom: String? = null,
        validUntil: String? = null
    ): String {
        val effectiveCardId = if (!cardIdentifier.isNullOrBlank()) {
            cardIdentifier.trim().uppercase()
        } else {
            CardCryptoManager.generateSecureRandomCardId()
        }
        return CardCryptoManager.signCardPayload(effectiveCardId, validFrom, validUntil)
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
     * - ONLY canonical "LTC:V2:<kid>:<cardId>:<validFrom>:<validUntil>:<signature>" payloads are accepted.
     * - Bare student numbers, bare UUIDs, OAKRIDGE:* legacy formats, and unsigned V1 payloads
     *   are explicitly rejected.
     */
    fun parseQrCode(rawScannedText: String): QrParseResult {
        val trimmed = rawScannedText.trim()
        if (trimmed.isBlank()) {
            return QrParseResult.Invalid(trimmed, "QR code content is empty or unreadable.")
        }

        // 1. Strict Protocol V2 Check: "LTC:V2:<kid>:<cardId>:<validFrom>:<validUntil>:<signature>"
        if (trimmed.startsWith(PREFIX_LTC_V2, ignoreCase = true)) {
            val parts = trimmed.split(":")
            if (parts.size != 7) {
                return QrParseResult.Invalid(
                    trimmed,
                    "Malformed V2 QR syntax. Expected 'LTC:V2:<kid>:<cardId>:<validFrom>:<validUntil>:<signature>' with exactly 7 segments."
                )
            }
            if (!parts[0].equals("LTC", ignoreCase = true) || !parts[1].equals("V2", ignoreCase = true)) {
                return QrParseResult.Invalid(trimmed, "Invalid protocol header. Expected 'LTC:V2'.")
            }

            val kid = parts[2].trim().lowercase()
            val cardId = parts[3].trim().uppercase()
            val validFrom = parts[4].trim()
            val validUntil = parts[5].trim()
            val signature = parts[6].trim()

            if (kid.isBlank() || cardId.isBlank() || validFrom.isBlank() || validUntil.isBlank() || signature.isBlank()) {
                return QrParseResult.Invalid(trimmed, "Malformed V2 QR code: one or more segments are empty.")
            }

            // Key ID must be 16 hexadecimal characters
            if (!KID_REGEX.matches(kid)) {
                return QrParseResult.Invalid(trimmed, "Malformed key identifier (kid) in V2 QR code. Expected 16 hexadecimal characters.")
            }

            // Card ID must contain only valid safe characters
            if (cardId.length < 6 || !cardId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                return QrParseResult.Invalid(trimmed, "Malformed card identifier character set in V2 QR code.")
            }

            // Validity dates must match YYYY-MM-DD
            if (!CardDateUtils.isValidDateFormat(validFrom) || !CardDateUtils.isValidDateFormat(validUntil)) {
                return QrParseResult.Invalid(trimmed, "Malformed date format in V2 QR code. Expected 'YYYY-MM-DD'.")
            }

            val canonical = "${CardCryptoUtils.CANONICAL_PREFIX}|$kid|$cardId|$validFrom|$validUntil"

            return QrParseResult.ValidV2Card(
                kid = kid,
                cardId = cardId,
                validFrom = validFrom,
                validUntil = validUntil,
                signature = signature,
                canonicalMessage = canonical,
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
            "Invalid QR format. Expected cryptographically signed Lira Town College badge ('LTC:V2:<kid>:<cardId>:<validFrom>:<validUntil>:<signature>')."
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
