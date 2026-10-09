package com.example.testutil

import com.example.crypto.CardSigner
import com.example.crypto.SoftwareCardSigner
import com.example.crypto.TrustedIssuerRegistry
import com.example.util.CardCryptoManager
import java.security.KeyPair

/**
 * Isolated test-only helper to configure in-memory software signing for unit and Robolectric tests.
 * This class resides strictly in the test source set and is never compiled into production APKs.
 */
object TestCardCryptoHelper {
    fun generateKeyPair(): KeyPair {
        return SoftwareCardSigner.generateP256KeyPair()
    }

    fun configureTestKeyPair(keyPair: KeyPair): CardSigner {
        val signer = SoftwareCardSigner(keyPair)
        CardCryptoManager.setActiveSigner(signer)
        TrustedIssuerRegistry.registerTrustedKey(signer.publicKey, "Test Issuer Authority")
        return signer
    }
}
