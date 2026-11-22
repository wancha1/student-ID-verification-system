package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CardSigner
import com.example.crypto.CardCryptoUtils
import com.example.crypto.TrustedIssuerRegistry
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Ephemeral software-backed issuer for local/Robolectric tests only. */
internal object TestIssuerSignerFactory {
    fun create(): CardSigner {
        val generator = KeyPairGenerator.getInstance(CardCryptoUtils.KEY_ALGORITHM_EC)
        generator.initialize(ECGenParameterSpec(CardCryptoUtils.CURVE_NAME))
        val keyPair = generator.generateKeyPair()

        // Production signing now correctly rejects keys absent from the trusted
        // issuer registry. Test signers represent enrolled authorities, so register
        // each disposable public key before tests attempt to issue credentials.
        val context = ApplicationProvider.getApplicationContext<Context>()
        TrustedIssuerRegistry.initialize(context)
        TrustedIssuerRegistry.registerTrustedKey(keyPair.public, "Disposable test issuer")

        return object : CardSigner {
            override val keyId: String = CardCryptoUtils.computeKeyId(keyPair.public)
            override val publicKey = keyPair.public
            override fun sign(canonicalMessage: ByteArray): ByteArray =
                Signature.getInstance(CardCryptoUtils.SIGNATURE_ALGORITHM).run {
                    initSign(keyPair.private)
                    update(canonicalMessage)
                    sign()
                }
        }
    }
}
