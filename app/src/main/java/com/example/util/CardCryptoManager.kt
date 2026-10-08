package com.example.util

import com.example.crypto.CardCryptoUtils
import com.example.crypto.CardDateUtils
import com.example.crypto.CardSigner
import com.example.crypto.KeystoreIssuerManager
import com.example.crypto.SoftwareCardSigner
import com.example.crypto.TrustedIssuerRegistry
import java.security.KeyPair
import java.security.PublicKey

/**
 * High-level cryptographic coordinator for the Lira Town College (LTC)
 * Student QR Identity System (Protocol V2 Authenticated Trust Model).
 *
 * Separation of Responsibilities:
 * 1. ISSUER DEVICE:
 *    - Holds an active [CardSigner] (backed by Android Keystore hardware via [KeystoreIssuerManager]).
 *    - Private keys are NEVER exportable, NEVER stored in Room, SharedPreferences, or plain memory.
 *    - Performs authoritative card issuance and replacement signing.
 * 2. VERIFIER / GATE DEVICE:
 *    - Holds [TrustedIssuerRegistry] containing trusted issuer PUBLIC keys only.
 *    - Possesses ZERO private keys or signing capabilities.
 *    - Does NOT automatically generate signing keys.
 *    - Validates cards offline by verifying ECDSA P-256 signatures against the canonical message.
 */
object CardCryptoManager {

    const val PROTOCOL_VERSION = "V2"
    const val PREFIX_LTC_V2 = "LTC:V2:"

    @Volatile
    private var activeSigner: CardSigner? = null

    /**
     * Checks if this terminal holds private signing capability.
     * Gate terminals should report false.
     */
    fun hasIssuerPrivateKey(): Boolean = activeSigner != null

    /**
     * Explicitly sets the active signer for authoritative operations.
     * In production, this is configured during issuer enrollment.
     */
    fun setActiveSigner(signer: CardSigner?) {
        activeSigner = signer
    }

    /**
     * Gate-only device hardening: removes any active signing capability completely.
     * After this call, the device can strictly only VERIFY cards.
     */
    fun clearIssuerPrivateKey() {
        activeSigner = null
    }

    /**
     * Generates a cryptographically random card identifier with at least 128 bits of entropy.
     * Guaranteed NOT to be derived from student number, name, date, counter, or device identifier.
     * Format: "CRD-" followed by 32 uppercase hexadecimal characters (16 random bytes).
     */
    fun generateSecureRandomCardId(): String {
        return CardCryptoUtils.generateSecureRandomCardId()
    }

    /**
     * Deterministic canonical byte representation of the security-sensitive fields.
     * Format: "LTC-V2|<kid>|<cardId>|<validFrom>|<validUntil>" encoded in UTF-8.
     */
    fun getCanonicalMessageBytes(
        kid: String,
        cardId: String,
        validFrom: String,
        validUntil: String
    ): ByteArray {
        return CardCryptoUtils.buildCanonicalMessage(
            kid = kid.trim().lowercase(),
            cardId = cardId.trim().uppercase(),
            validFrom = validFrom.trim(),
            validUntil = validUntil.trim()
        )
    }

    /**
     * Compatibility helper: builds canonical message using active signer's kid
     * and default validity dates if available.
     */
    fun getCanonicalMessageBytes(cardId: String): ByteArray {
        val kid = activeSigner?.keyId ?: "0000000000000000"
        val (from, until) = CardDateUtils.getDefaultValidityRange()
        return getCanonicalMessageBytes(kid, cardId, from, until)
    }

    /**
     * Signs a card credential using the active [CardSigner] and formats the canonical V2 QR payload.
     *
     * Payload structure: "LTC:V2:<kid>:<cardId>:<validFrom>:<validUntil>:<signatureBase64Url>"
     *
     * @throws IllegalStateException if this device has no active issuer signing capability.
     */
    fun signCardPayload(
        cardId: String,
        validFrom: String? = null,
        validUntil: String? = null,
        signer: CardSigner? = null
    ): String {
        val effectiveSigner = signer ?: activeSigner
            ?: throw IllegalStateException(
                "Cannot sign card payload: Device is operating in Gate / Verifier mode with no Issuer Signing Key configured. " +
                "Card issuance requires an authoritative Issuer device."
            )

        val cleanCardId = cardId.trim().uppercase()
        require(cleanCardId.isNotBlank()) { "Card ID must not be blank" }

        val (defaultFrom, defaultUntil) = CardDateUtils.getDefaultValidityRange()
        val effectiveFrom = if (!validFrom.isNullOrBlank()) validFrom.trim() else defaultFrom
        val effectiveUntil = if (!validUntil.isNullOrBlank()) validUntil.trim() else defaultUntil

        val canonicalBytes = CardCryptoUtils.buildCanonicalMessage(
            kid = effectiveSigner.keyId,
            cardId = cleanCardId,
            validFrom = effectiveFrom,
            validUntil = effectiveUntil
        )

        val rawSignature = effectiveSigner.sign(canonicalBytes)
        val signatureBase64Url = CardCryptoUtils.encodeBase64UrlNoPadding(rawSignature)

        return CardCryptoUtils.buildV2QrPayload(
            kid = effectiveSigner.keyId,
            cardId = cleanCardId,
            validFrom = effectiveFrom,
            validUntil = effectiveUntil,
            signatureBase64Url = signatureBase64Url
        )
    }

    /**
     * Compatibility overload for legacy test callers or callers without validity arguments.
     */
    fun signCardPayload(cardId: String, privateKeyOrSigner: Any?): String {
        return when (privateKeyOrSigner) {
            is CardSigner -> signCardPayload(cardId = cardId, signer = privateKeyOrSigner)
            is KeyPair -> {
                val signer = SoftwareCardSigner(privateKeyOrSigner)
                signCardPayload(cardId = cardId, signer = signer)
            }
            else -> signCardPayload(cardId = cardId)
        }
    }

    /**
     * Cryptographically verifies that a V2 card payload was signed by an authoritative issuer.
     * Operates completely offline using the trusted public key registered for [kid].
     */
    fun verifyCardSignature(
        kid: String,
        cardId: String,
        validFrom: String,
        validUntil: String,
        signatureBase64Url: String,
        publicKey: PublicKey? = null
    ): Boolean {
        val cleanKid = kid.trim().lowercase()
        val cleanCardId = cardId.trim().uppercase()
        if (cleanKid.isBlank() || cleanCardId.isBlank() || signatureBase64Url.isBlank()) return false

        val verifierKey = publicKey ?: TrustedIssuerRegistry.getPublicKey(cleanKid) ?: return false

        val canonicalBytes = CardCryptoUtils.buildCanonicalMessage(
            kid = cleanKid,
            cardId = cleanCardId,
            validFrom = validFrom.trim(),
            validUntil = validUntil.trim()
        )

        return try {
            val signatureBytes = CardCryptoUtils.decodeBase64Url(signatureBase64Url)
            CardCryptoUtils.verifyEcdsaSignature(verifierKey, canonicalBytes, signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies signature from a parsed [QrParseResult.ValidV2Card].
     */
    fun verifyCardSignature(validV2: QrCodeUtils.ValidV2CardData): Boolean {
        return verifyCardSignature(
            kid = validV2.kid,
            cardId = validV2.cardId,
            validFrom = validV2.validFrom,
            validUntil = validV2.validUntil,
            signatureBase64Url = validV2.signature
        )
    }

    /**
     * Compatibility overload for calls passing (cardId, signatureBase64Url, optionalPublicKey).
     */
    fun verifyCardSignature(
        cardId: String,
        signatureBase64Url: String,
        publicKey: PublicKey? = null
    ): Boolean {
        // If an explicit publicKey is passed:
        if (publicKey != null) {
            val kid = CardCryptoUtils.computeKeyId(publicKey)
            val (from, until) = CardDateUtils.getDefaultValidityRange()
            return verifyCardSignature(
                kid = kid,
                cardId = cardId,
                validFrom = from,
                validUntil = until,
                signatureBase64Url = signatureBase64Url,
                publicKey = publicKey
            )
        }

        // Try looking up in all active trusted issuers if kid was omitted
        val issuers = TrustedIssuerRegistry.getAllIssuers().filter { !it.isRevoked }
        val (from, until) = CardDateUtils.getDefaultValidityRange()
        for (issuer in issuers) {
            val key = TrustedIssuerRegistry.getPublicKey(issuer.kid) ?: continue
            val valid = verifyCardSignature(
                kid = issuer.kid,
                cardId = cardId,
                validFrom = from,
                validUntil = until,
                signatureBase64Url = signatureBase64Url,
                publicKey = key
            )
            if (valid) return true
        }

        // Also check if activeSigner public key matches (e.g. in test environment)
        val signer = activeSigner
        if (signer != null) {
            return verifyCardSignature(
                kid = signer.keyId,
                cardId = cardId,
                validFrom = from,
                validUntil = until,
                signatureBase64Url = signatureBase64Url,
                publicKey = signer.publicKey
            )
        }

        return false
    }

    // =========================================================================
    // Test & Bootstrap Helpers
    // =========================================================================

    /**
     * Configures a software test signer and enrolls its public key into the trusted registry.
     */
    fun configureTestKeyPair(keyPair: KeyPair): CardSigner {
        val signer = SoftwareCardSigner(keyPair)
        activeSigner = signer
        TrustedIssuerRegistry.registerTrustedKey(signer.publicKey, "Test Issuer Authority")
        return signer
    }

    /**
     * Generates a standard EC P-256 key pair for test execution.
     */
    fun generateKeyPair(): KeyPair {
        return SoftwareCardSigner.generateP256KeyPair()
    }

    /**
     * Resets state for unit/Robolectric test isolation.
     */
    fun resetForTesting() {
        activeSigner = null
        TrustedIssuerRegistry.clearAll()
    }

    /**
     * Exports an EC public key to Base64 (X.509 format).
     */
    fun exportPublicKeyBase64(publicKey: PublicKey): String {
        return CardCryptoUtils.encodePublicKeyToBase64(publicKey)
    }

    /**
     * Imports an EC public key from Base64 (X.509 format).
     */
    fun importPublicKeyBase64(base64String: String): PublicKey {
        return CardCryptoUtils.decodePublicKeyFromBase64(base64String)
    }
}
