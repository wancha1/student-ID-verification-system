package com.example.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Manages the authoritative Issuer Signing Key lifecycle within Android Keystore.
 *
 * Operational Model:
 * 1. Explicit Enrollment: Issuer keys are created ONLY via explicit administrator action.
 *    Devices do NOT automatically create signing keys on app startup.
 * 2. Hardware Enclave Protection: Keys are generated and retained within Android Keystore
 *    (StrongBox Keymaster or TEE hardware when available).
 * 3. Non-Exportable: The private key CANNOT be exported, read, or serialized to PKCS#8.
 * 4. Audit & Transparency: Precise hardware-backing status is inspected via [KeyInfo]
 *    and accurately reported.
 */
object KeystoreIssuerManager {

    const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    const val PRIMARY_ISSUER_ALIAS = "ltc_issuer_primary"
    const val ALIAS_PREFIX = "ltc_issuer_"

    private fun getKeyStore(): KeyStore {
        return KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }

    /**
     * Checks if an authoritative Issuer Signing Key currently exists in Android Keystore.
     */
    fun hasIssuerKey(alias: String = PRIMARY_ISSUER_ALIAS): Boolean {
        return try {
            getKeyStore().containsAlias(alias)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an authoritative ECDSA P-256 signing key in Android Keystore.
     *
     * @param alias The stable Keystore alias (defaults to [PRIMARY_ISSUER_ALIAS]).
     * @param preferStrongBox Whether to attempt StrongBox hardware protection on Android 9+ (API 28+).
     * @return [IssuerKeyInfo] detailing the generated key and its verified hardware backing.
     */
    fun generateIssuerKey(
        alias: String = PRIMARY_ISSUER_ALIAS,
        preferStrongBox: Boolean = true
    ): IssuerKeyInfo {
        val keyStore = getKeyStore()

        // If an old key with this alias exists, remove it before generating a fresh one
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }

        // Attempt StrongBox first if requested on supported platform
        var generatedStrongBox = false
        if (preferStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generateKeyInternal(alias, isStrongBox = true)
                generatedStrongBox = true
            } catch (_: Exception) {
                // StrongBox not available on this chipset or failed; fallback to standard TEE / Keystore
                generatedStrongBox = false
            }
        }

        if (!generatedStrongBox) {
            generateKeyInternal(alias, isStrongBox = false)
        }

        val certificate = keyStore.getCertificate(alias)
            ?: throw IllegalStateException("Key generation completed, but certificate was not found in $KEYSTORE_PROVIDER.")
        val publicKey = certificate.publicKey
        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw IllegalStateException("Key generation completed, but private key was inaccessible in $KEYSTORE_PROVIDER.")

        val kid = CardCryptoUtils.computeKeyId(publicKey)
        val (securityLevel, isInsideHardware) = inspectKeySecurity(privateKey)
        val pubKeyBase64 = CardCryptoUtils.encodePublicKeyToBase64(publicKey)

        return IssuerKeyInfo(
            keyId = kid,
            alias = alias,
            securityLevel = securityLevel,
            isInsideSecureHardware = isInsideHardware,
            algorithm = "EC / secp256r1 (P-256)",
            createdAt = System.currentTimeMillis(),
            publicKeyEncodedBase64 = pubKeyBase64
        )
    }

    private fun generateKeyInternal(alias: String, isStrongBox: Boolean) {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(CardCryptoUtils.CURVE_NAME))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

        if (isStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specBuilder.setIsStrongBoxBacked(true)
        }

        kpg.initialize(specBuilder.build())
        kpg.generateKeyPair()
    }

    /**
     * Obtains an active [CardSigner] backed by the enrolled Android Keystore key.
     * Returns null if no issuer key is enrolled on this device.
     */
    fun getSigner(alias: String = PRIMARY_ISSUER_ALIAS): CardSigner? {
        if (!hasIssuerKey(alias)) return null
        return try {
            KeystoreCardSigner(alias)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retrieves the public key for the enrolled issuer without needing the private key.
     */
    fun getIssuerPublicKey(alias: String = PRIMARY_ISSUER_ALIAS): PublicKey? {
        return try {
            val keyStore = getKeyStore()
            val cert = keyStore.getCertificate(alias) ?: return null
            cert.publicKey
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Inspects the security level of the Android Keystore private key using [KeyInfo].
     */
    fun inspectKeySecurity(privateKey: PrivateKey): Pair<KeySecurityLevel, Boolean> {
        return try {
            val factory = KeyFactory.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                KEYSTORE_PROVIDER
            )
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
            val isHardware = keyInfo.isInsideSecureHardware

            val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX_BACKED
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TEE_BACKED
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.SOFTWARE_BACKED
                    else -> if (isHardware) KeySecurityLevel.TEE_BACKED else KeySecurityLevel.SOFTWARE_BACKED
                }
            } else {
                if (isHardware) KeySecurityLevel.TEE_BACKED else KeySecurityLevel.SOFTWARE_BACKED
            }
            Pair(level, isHardware)
        } catch (_: Exception) {
            Pair(KeySecurityLevel.SOFTWARE_BACKED, false)
        }
    }

    /**
     * Deletes an issuer key from Android Keystore.
     */
    fun deleteIssuerKey(alias: String = PRIMARY_ISSUER_ALIAS): Boolean {
        return try {
            val keyStore = getKeyStore()
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
