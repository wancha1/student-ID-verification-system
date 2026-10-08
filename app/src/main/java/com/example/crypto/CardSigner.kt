package com.example.crypto

import java.security.PublicKey

/**
 * Security level of the cryptographic key material as reported by Android Keystore / KeyInfo.
 */
enum class KeySecurityLevel {
    STRONGBOX_BACKED,
    TEE_BACKED,
    SOFTWARE_BACKED,
    UNKNOWN
}

/**
 * Diagnostic and audit information about an enrolled Issuer Key.
 *
 * CRITICAL: Under no circumstances does this structure contain or expose private key bytes.
 */
data class IssuerKeyInfo(
    val keyId: String,
    val alias: String,
    val securityLevel: KeySecurityLevel,
    val isInsideSecureHardware: Boolean,
    val algorithm: String = "EC / secp256r1 (P-256)",
    val createdAt: Long = System.currentTimeMillis(),
    val publicKeyEncodedBase64: String
)

/**
 * Authoritative cryptographic signing abstraction for Lira Town College student credentials.
 *
 * Implementing classes sign canonical messages without exposing or exporting private key bytes.
 */
interface CardSigner {
    /**
     * Deterministic 16-hex key identifier: SHA-256(publicKeyEncoded)[0..7].
     */
    val keyId: String

    /**
     * Public verification key corresponding to this signing key.
     */
    val publicKey: PublicKey

    /**
     * Signs the canonical UTF-8 byte array using ECDSA with P-256 (SHA256withECDSA).
     *
     * @param canonicalMessage The domain-separated byte sequence to sign.
     * @return The raw DER-encoded ECDSA signature bytes.
     */
    fun sign(canonicalMessage: ByteArray): ByteArray
}
