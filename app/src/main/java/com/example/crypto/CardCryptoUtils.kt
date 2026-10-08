package com.example.crypto

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Low-level cryptographic primitives and formatting utilities for the Lira Town College
 * Student QR Identity Protocol V2 (ECDSA P-256 / secp256r1 Trust Architecture).
 */
object CardCryptoUtils {

    const val PROTOCOL_VERSION = "V2"
    const val QR_HEADER = "LTC:V2"
    const val CANONICAL_PREFIX = "LTC-V2"
    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    const val KEY_ALGORITHM_EC = "EC"
    const val CURVE_NAME = "secp256r1"

    private val secureRandom = SecureRandom()

    /**
     * Computes a deterministic 16-character hexadecimal identifier from an EC public key.
     * Specification: First 8 bytes (64 bits) of SHA-256(publicKey.encoded) formatted as lowercase hex.
     */
    fun computeKeyId(publicKey: PublicKey): String {
        return computeKeyId(publicKey.encoded)
    }

    /**
     * Computes a deterministic 16-character hexadecimal identifier from X.509 encoded public key bytes.
     */
    fun computeKeyId(publicKeyEncoded: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyEncoded)
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a cryptographically random card identifier with at least 128 bits of entropy.
     * Guaranteed NOT to be derived from student number, name, date, counter, or device identifier.
     * Format: "CRD-" followed by 32 uppercase hexadecimal characters (16 random bytes).
     */
    fun generateSecureRandomCardId(): String {
        val randomBytes = ByteArray(16) // Exactly 128 bits of cryptographic entropy
        secureRandom.nextBytes(randomBytes)
        val hex = randomBytes.joinToString("") { "%02X".format(it) }
        return "CRD-$hex"
    }

    /**
     * Constructs the exact canonical byte array that the issuer signs and the verifier authenticates.
     *
     * Format: "LTC-V2|<kid>|<cardId>|<validFrom>|<validUntil>"
     * Encoding: UTF-8
     *
     * Strict canonical ordering and pipe delimiters prevent field collision and delimiter injection.
     */
    fun buildCanonicalMessage(
        kid: String,
        cardId: String,
        validFrom: String,
        validUntil: String
    ): ByteArray {
        val canonicalString = "$CANONICAL_PREFIX|$kid|$cardId|$validFrom|$validUntil"
        return canonicalString.toByteArray(Charsets.UTF_8)
    }

    /**
     * Constructs the full V2 QR payload string.
     * Format: "LTC:V2:<kid>:<cardId>:<validFrom>:<validUntil>:<signatureBase64Url>"
     */
    fun buildV2QrPayload(
        kid: String,
        cardId: String,
        validFrom: String,
        validUntil: String,
        signatureBase64Url: String
    ): String {
        return "$QR_HEADER:$kid:$cardId:$validFrom:$validUntil:$signatureBase64Url"
    }

    /**
     * Cryptographically verifies an ECDSA P-256 signature against the canonical message.
     */
    fun verifyEcdsaSignature(
        publicKey: PublicKey,
        canonicalMessage: ByteArray,
        signatureBytes: ByteArray
    ): Boolean {
        return try {
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(canonicalMessage)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encodes bytes to URL-safe Base64 without trailing '=' padding characters.
     */
    fun encodeBase64UrlNoPadding(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            ).trim()
        } catch (_: Throwable) {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }

    /**
     * Decodes URL-safe Base64 strings.
     * Throws IllegalArgumentException if input is malformed.
     */
    fun decodeBase64Url(base64String: String): ByteArray {
        val trimmed = base64String.trim()
        return try {
            android.util.Base64.decode(trimmed, android.util.Base64.URL_SAFE)
        } catch (_: Throwable) {
            java.util.Base64.getUrlDecoder().decode(trimmed)
        }
    }

    /**
     * Exports an EC public key to standard Base64 representation (X.509 format).
     */
    fun encodePublicKeyToBase64(publicKey: PublicKey): String {
        return try {
            android.util.Base64.encodeToString(
                publicKey.encoded,
                android.util.Base64.NO_WRAP
            ).trim()
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(publicKey.encoded)
        }
    }

    /**
     * Decodes an EC public key from standard Base64 (X.509 format).
     */
    fun decodePublicKeyFromBase64(base64String: String): PublicKey {
        val bytes = try {
            android.util.Base64.decode(base64String.trim(), android.util.Base64.DEFAULT)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(base64String.trim())
        }
        val spec = X509EncodedKeySpec(bytes)
        val factory = KeyFactory.getInstance(KEY_ALGORITHM_EC)
        return factory.generatePublic(spec)
    }

    /**
     * Decodes an EC public key from raw X.509 DER bytes.
     */
    fun decodePublicKeyFromBytes(bytes: ByteArray): PublicKey {
        val spec = X509EncodedKeySpec(bytes)
        val factory = KeyFactory.getInstance(KEY_ALGORITHM_EC)
        return factory.generatePublic(spec)
    }
}
