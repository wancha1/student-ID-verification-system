package com.example.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted-at-rest storage primitive for opaque Supabase Auth session payloads.
 *
 * This class deliberately does not depend on the Supabase SDK's SessionManager API.
 * The SDK adapter should call [save], [load], and [clear] after its exact versioned
 * interface has been verified. Never pass passwords or log session contents here.
 */
class EncryptedSessionVault(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun save(sessionPayload: String) {
        require(sessionPayload.isNotBlank()) { "Session payload cannot be blank." }

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(sessionPayload.toByteArray(Charsets.UTF_8))
            val packed = cipher.iv + ciphertext
            val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)

            check(preferences.edit().putString(SESSION_KEY, encoded).commit()) {
                "Could not persist the encrypted session."
            }
        } catch (error: Exception) {
            throw SessionVaultException("Could not securely save the authentication session.", error)
        }
    }

    @Synchronized
    fun load(): String? {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null

        try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_LENGTH) { "Encrypted session payload is malformed." }

            val iv = packed.copyOfRange(0, IV_LENGTH)
            val ciphertext = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (error: Exception) {
            // A lost/invalidated Keystore key makes the ciphertext unusable. Remove both
            // pieces so the app can require a fresh sign-in instead of looping forever.
            clearInternal()
            throw SessionVaultException(
                "The saved session could not be decrypted. Please sign in again.",
                error
            )
        }
    }

    @Synchronized
    fun clear() {
        clearInternal()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private fun getExistingKey(): SecretKey {
        return loadKeyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw GeneralSecurityException("Session encryption key is missing.")
    }

    private fun clearInternal() {
        preferences.edit().remove(SESSION_KEY).commit()
        try {
            loadKeyStore().deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // Session data is already removed; a later save can recreate the key.
        }
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    class SessionVaultException(message: String, cause: Throwable) :
        IllegalStateException(message, cause)

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "ltc_supabase_auth_session_v1"
        const val PREFERENCES_NAME = "ltc_secure_auth_session"
        const val SESSION_KEY = "encrypted_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
