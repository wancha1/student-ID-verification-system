package com.example.crypto

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * In-memory software implementation of [CardSigner] for unit tests, Robolectric suites,
 * and isolated test harnesses.
 *
 * NOTE: Production devices MUST use [KeystoreCardSigner] to ensure non-exportable hardware backing.
 */
class SoftwareCardSigner(
    val keyPair: KeyPair
) : CardSigner {

    override val publicKey: PublicKey = keyPair.public

    override val keyId: String = CardCryptoUtils.computeKeyId(keyPair.public)

    override fun sign(canonicalMessage: ByteArray): ByteArray {
        val signer = Signature.getInstance(CardCryptoUtils.SIGNATURE_ALGORITHM)
        signer.initSign(keyPair.private)
        signer.update(canonicalMessage)
        return signer.sign()
    }

    companion object {
        /**
         * Generates a standard EC P-256 (secp256r1) key pair using standard JCA providers.
         */
        fun generateP256KeyPair(): KeyPair {
            val kpg = KeyPairGenerator.getInstance(CardCryptoUtils.KEY_ALGORITHM_EC)
            kpg.initialize(ECGenParameterSpec(CardCryptoUtils.CURVE_NAME))
            return kpg.generateKeyPair()
        }
    }
}
