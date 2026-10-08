package com.example.crypto

import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

/**
 * Production implementation of [CardSigner] backed by an Android Keystore hardware/TEE entry.
 *
 * Security Guarantee:
 * - The private key NEVER leaves the secure hardware boundary.
 * - Private key bytes are NEVER accessed, encoded, or exported.
 * - Signing operations occur through the Android Keystore JCA provider.
 */
class KeystoreCardSigner(
    val alias: String,
    private val keyStoreProvider: String = "AndroidKeyStore"
) : CardSigner {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(keyStoreProvider).apply {
            load(null)
        }
    }

    override val publicKey: PublicKey by lazy {
        val certificate = keyStore.getCertificate(alias)
            ?: throw IllegalStateException("Certificate for alias '$alias' was not found in $keyStoreProvider.")
        certificate.publicKey
    }

    override val keyId: String by lazy {
        CardCryptoUtils.computeKeyId(publicKey)
    }

    /**
     * Signs the canonical message bytes using the Android Keystore private key.
     * The private key is accessed strictly as an opaque reference and never serialized.
     */
    override fun sign(canonicalMessage: ByteArray): ByteArray {
        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw IllegalStateException("Private signing key for alias '$alias' not found or inaccessible in $keyStoreProvider.")

        val signer = Signature.getInstance(CardCryptoUtils.SIGNATURE_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(canonicalMessage)
        return signer.sign()
    }
}
