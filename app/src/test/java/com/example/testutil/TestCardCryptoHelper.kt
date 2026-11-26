package com.example.testutil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CardCryptoUtils
import com.example.crypto.CardSigner
import com.example.crypto.SoftwareCardSigner
import com.example.crypto.TrustedIssuerRegistry
import com.example.util.CardCryptoManager
import java.security.KeyPair
import java.security.PublicKey

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
        seedTrustedIssuer(signer.publicKey, "Test Issuer Authority")
        return signer
    }

    /**
     * Clears test-only issuer fixtures. No production registry wipe API exists.
     */
    fun clearTrustedIssuerFixtures() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("ltc_trusted_issuer_registry", Context.MODE_PRIVATE)
        check(prefs.edit().clear().commit()) { "Could not clear test issuer fixtures" }
        TrustedIssuerRegistry.initialize(context)
    }

    /**
     * Seeds a server-provisioned trust record for tests. This helper is test-source
     * only; production code has no local enrollment path.
     */
    fun seedTrustedIssuer(publicKey: PublicKey, label: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val kid = CardCryptoUtils.computeKeyId(publicKey)
        val publicKeyBase64 = CardCryptoUtils.encodePublicKeyToBase64(publicKey)
        val prefs = context.getSharedPreferences("ltc_trusted_issuer_registry", Context.MODE_PRIVATE)
        val kids = prefs.getStringSet("enrolled_issuer_kids", emptySet())?.toMutableSet() ?: mutableSetOf()
        kids.add(kid)
        check(
            prefs.edit()
                .putStringSet("enrolled_issuer_kids", kids)
                .putString("issuer_pubkey_$kid", publicKeyBase64)
                .putString("issuer_label_$kid", label)
                .putLong("issuer_enrolled_at_$kid", System.currentTimeMillis())
                .putBoolean("issuer_revoked_$kid", false)
                .commit()
        ) { "Could not seed test issuer trust record" }
        TrustedIssuerRegistry.initialize(context)
    }
}
