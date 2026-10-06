package com.example.util

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Manages Ed25519 cryptographic key generation, card signing, and offline signature verification
 * for the Lira Town College (LTC) Student QR Identity System (Protocol V2).
 *
 * Trust Model:
 * 1. PRIVATE SIGNING KEY:
 *    - Held exclusively on authoritative administrative / card-issuing stations.
 *    - Never embedded in QR codes, gate turnstiles, or plain SharedPreferences.
 *    - Gate devices strip this key using [clearIssuerPrivateKey] so physical theft of a scanner
 *      yields zero card-forgery capability.
 * 2. PUBLIC VERIFICATION KEY:
 *    - Deployed to gate scanning devices for offline signature verification.
 *    - Verification requires no internet connection or backend server.
 * 3. CANONICAL V2 MESSAGE:
 *    - Deterministic byte sequence: UTF-8 encoding of "LTC:V2:<cardId>".
 *    - Domain-separated protocol prefix prevents cross-protocol signature reuse.
 */
object CardCryptoManager {

    const val PROTOCOL_VERSION = "V2"
    const val PREFIX_LTC_V2 = "LTC:V2:"
    private const val ALGORITHM_ED25519 = "Ed25519"
    private const val EXPECTED_SIGNATURE_LENGTH = 64

    private val secureRandom = SecureRandom()

    // Key storage
    @Volatile
    private var activeIssuerPrivateKey: PrivateKey? = null

    @Volatile
    private var activeVerificationPublicKey: PublicKey? = null

    init {
        // Initialize an active key pair for authoritative operations
        initializeDefaultAuthorityKeys()
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
     * Deterministic, unambiguous byte representation of the canonical message to be signed.
     * Format: "LTC:V2:<cardId>" encoded in UTF-8.
     */
    fun getCanonicalMessageBytes(cardId: String): ByteArray {
        val cleanCardId = cardId.trim().uppercase()
        return "$PREFIX_LTC_V2$cleanCardId".toByteArray(Charsets.UTF_8)
    }

    /**
     * Generates a fresh Ed25519 key pair using standard Java Cryptography Architecture.
     */
    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(ALGORITHM_ED25519)
        return kpg.generateKeyPair()
    }

    /**
     * Signs a card identifier using the private key and formats the canonical V2 QR payload.
     * Payload structure: "LTC:V2:<cardId>:<signatureBase64Url>"
     *
     * @throws IllegalStateException if no private signing key is configured on this terminal.
     */
    fun signCardPayload(cardId: String, privateKey: PrivateKey? = null): String {
        val signingKey = privateKey ?: activeIssuerPrivateKey
            ?: throw IllegalStateException("Cannot sign card payload: Private signing key is not installed on this terminal.")

        val cleanCardId = cardId.trim().uppercase()
        require(cleanCardId.isNotBlank()) { "Card ID must not be blank" }

        val canonicalBytes = getCanonicalMessageBytes(cleanCardId)
        val signer = Signature.getInstance(ALGORITHM_ED25519)
        signer.initSign(signingKey)
        signer.update(canonicalBytes)
        val rawSignature = signer.sign()

        val signatureBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature)
        return "$PREFIX_LTC_V2$cleanCardId:$signatureBase64Url"
    }

    /**
     * Cryptographically verifies that a V2 card payload was signed by the authoritative issuer.
     * Operates completely offline using the public verification key.
     *
     * @return true if the signature is authentic and untampered; false otherwise.
     */
    fun verifyCardSignature(
        cardId: String,
        signatureBase64Url: String,
        publicKey: PublicKey? = null
    ): Boolean {
        val verifierKey = publicKey ?: activeVerificationPublicKey ?: return false
        val cleanCardId = cardId.trim().uppercase()
        if (cleanCardId.isBlank() || signatureBase64Url.isBlank()) return false

        return try {
            val signatureBytes = Base64.getUrlDecoder().decode(signatureBase64Url)
            if (signatureBytes.size != EXPECTED_SIGNATURE_LENGTH) {
                return false
            }

            val canonicalBytes = getCanonicalMessageBytes(cleanCardId)
            val verifier = Signature.getInstance(ALGORITHM_ED25519)
            verifier.initVerify(verifierKey)
            verifier.update(canonicalBytes)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            // Malformed base64, incorrect format, or signature exceptions return false without crashing
            false
        }
    }

    /**
     * Checks if this terminal holds private signing capability.
     */
    fun hasIssuerPrivateKey(): Boolean = activeIssuerPrivateKey != null

    /**
     * Configures the trusted public verification key on this device.
     */
    fun setVerificationPublicKey(publicKey: PublicKey) {
        activeVerificationPublicKey = publicKey
    }

    /**
     * Retrieves the current trusted public verification key.
     */
    fun getVerificationPublicKey(): PublicKey? = activeVerificationPublicKey

    /**
     * Configures the private card signing key on an authoritative administrator terminal.
     */
    fun setIssuerPrivateKey(privateKey: PrivateKey) {
        activeIssuerPrivateKey = privateKey
    }

    /**
     * Gate-only device hardening: removes the private signing key completely.
     * After this call, the terminal can ONLY verify cards; it cannot forge or issue cards.
     */
    fun clearIssuerPrivateKey() {
        activeIssuerPrivateKey = null
    }

    /**
     * Test-only configuration: installs an isolated test key pair.
     */
    fun configureTestKeyPair(keyPair: KeyPair) {
        activeIssuerPrivateKey = keyPair.private
        activeVerificationPublicKey = keyPair.public
    }

    /**
     * Resets keys to a freshly generated default key pair for test isolation.
     */
    fun resetForTesting() {
        initializeDefaultAuthorityKeys()
    }

    /**
     * Exports a public key to Base64 (X.509 format) for distribution to gate terminals.
     */
    fun exportPublicKeyBase64(publicKey: PublicKey): String {
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    /**
     * Imports a public key from Base64 (X.509 format).
     */
    fun importPublicKeyBase64(base64String: String): PublicKey {
        val bytes = Base64.getDecoder().decode(base64String.trim())
        val keyFactory = KeyFactory.getInstance(ALGORITHM_ED25519)
        return keyFactory.generatePublic(X509EncodedKeySpec(bytes))
    }

    /**
     * Exports a private key to Base64 (PKCS#8 format).
     */
    fun exportPrivateKeyBase64(privateKey: PrivateKey): String {
        return Base64.getEncoder().encodeToString(privateKey.encoded)
    }

    /**
     * Imports a private key from Base64 (PKCS#8 format).
     */
    fun importPrivateKeyBase64(base64String: String): PrivateKey {
        val bytes = Base64.getDecoder().decode(base64String.trim())
        val keyFactory = KeyFactory.getInstance(ALGORITHM_ED25519)
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    private fun initializeDefaultAuthorityKeys() {
        val keyPair = generateKeyPair()
        activeIssuerPrivateKey = keyPair.private
        activeVerificationPublicKey = keyPair.public
    }
}
