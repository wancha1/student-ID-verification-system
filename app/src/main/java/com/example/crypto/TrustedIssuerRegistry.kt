package com.example.crypto

import android.content.Context
import android.content.SharedPreferences
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Metadata record for a trusted card-issuing authority public key.
 */
data class TrustedIssuerEntry(
    val kid: String,
    val publicKeyBase64: String,
    val label: String,
    val enrolledAt: Long,
    val isRevoked: Boolean = false
)

/**
 * Persistent registry of authoritative issuer public keys installed on verification devices.
 *
 * Security Guarantee:
 * - Stores PUBLIC verification keys ONLY.
 * - Possesses ZERO signing or private key capabilities.
 * - Supports key rotation and multiple concurrent valid issuer keys.
 * - Allows explicit revocation of retired or compromised issuer keys without database restructuring.
 * - Backed by persistent SharedPreferences so registrations survive process termination and device reboot.
 */
object TrustedIssuerRegistry {

    private const val PREFS_NAME = "ltc_trusted_issuer_registry"
    private const val KEY_PREFIX_PUBKEY = "issuer_pubkey_"
    private const val KEY_PREFIX_LABEL = "issuer_label_"
    private const val KEY_PREFIX_ENROLLED_AT = "issuer_enrolled_at_"
    private const val KEY_PREFIX_REVOKED = "issuer_revoked_"
    private const val KEY_ISSUER_KIDS = "enrolled_issuer_kids"

    @Volatile
    private var appContext: Context? = null

    // In-memory cache for ultra-fast offline lookups at the gate
    private val keyCache = ConcurrentHashMap<String, PublicKey>()
    private val entryCache = ConcurrentHashMap<String, TrustedIssuerEntry>()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadAllFromStorage()
    }

    private fun getPrefs(): SharedPreferences? {
        return appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Enrolls or updates an authoritative issuer public key in the trusted registry.
     */
    fun registerTrustedKey(
        publicKey: PublicKey,
        label: String = "Authoritative LTC Issuer",
        enrolledAt: Long = System.currentTimeMillis()
    ): TrustedIssuerEntry {
        val kid = CardCryptoUtils.computeKeyId(publicKey)
        val base64 = CardCryptoUtils.encodePublicKeyToBase64(publicKey)
        val entry = TrustedIssuerEntry(
            kid = kid,
            publicKeyBase64 = base64,
            label = label,
            enrolledAt = enrolledAt,
            isRevoked = false
        )

        keyCache[kid] = publicKey
        entryCache[kid] = entry
        persistEntry(entry)
        return entry
    }

    /**
     * Enrolls an issuer public key from its X.509 Base64 representation.
     */
    fun registerTrustedKeyBase64(
        publicKeyBase64: String,
        label: String = "Enrolled Issuer",
        enrolledAt: Long = System.currentTimeMillis()
    ): TrustedIssuerEntry {
        val publicKey = CardCryptoUtils.decodePublicKeyFromBase64(publicKeyBase64)
        return registerTrustedKey(publicKey, label, enrolledAt)
    }

    /**
     * Retrieves the trusted public key for the specified key identifier.
     * Returns null if the kid is unknown or if the key has been revoked.
     */
    fun getPublicKey(kid: String): PublicKey? {
        val cleanKid = kid.trim().lowercase()
        val entry = entryCache[cleanKid] ?: loadEntryFromStorage(cleanKid) ?: return null
        if (entry.isRevoked) {
            return null // Revoked keys cannot verify credentials
        }

        val cached = keyCache[cleanKid]
        if (cached != null) return cached

        return try {
            val key = CardCryptoUtils.decodePublicKeyFromBase64(entry.publicKeyBase64)
            keyCache[cleanKid] = key
            key
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the full metadata entry for the specified kid, including revocation status.
     */
    fun getIssuerEntry(kid: String): TrustedIssuerEntry? {
        val cleanKid = kid.trim().lowercase()
        return entryCache[cleanKid] ?: loadEntryFromStorage(cleanKid)
    }

    /**
     * Checks if the key identifier corresponds to an active, non-revoked trusted issuer.
     */
    fun isTrusted(kid: String): Boolean {
        val cleanKid = kid.trim().lowercase()
        val entry = getIssuerEntry(cleanKid) ?: return false
        return !entry.isRevoked
    }

    /**
     * Revokes an issuer key. Cards signed by this key will immediately be rejected.
     */
    fun revokeIssuer(kid: String): Boolean {
        val cleanKid = kid.trim().lowercase()
        val existing = getIssuerEntry(cleanKid) ?: return false
        val updated = existing.copy(isRevoked = true)
        entryCache[cleanKid] = updated
        keyCache.remove(cleanKid)
        persistEntry(updated)
        return true
    }

    /**
     * Removes an issuer key from the registry completely.
     */
    fun removeIssuer(kid: String): Boolean {
        val cleanKid = kid.trim().lowercase()
        entryCache.remove(cleanKid)
        keyCache.remove(cleanKid)
        val prefs = getPrefs() ?: return true
        val kids = prefs.getStringSet(KEY_ISSUER_KIDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        kids.remove(cleanKid)
        prefs.edit()
            .putStringSet(KEY_ISSUER_KIDS, kids)
            .remove("$KEY_PREFIX_PUBKEY$cleanKid")
            .remove("$KEY_PREFIX_LABEL$cleanKid")
            .remove("$KEY_PREFIX_ENROLLED_AT$cleanKid")
            .remove("$KEY_PREFIX_REVOKED$cleanKid")
            .apply()
        return true
    }

    /**
     * Lists all enrolled issuer keys.
     */
    fun getAllIssuers(): List<TrustedIssuerEntry> {
        val kids = getEnrolledKids()
        return kids.mapNotNull { getIssuerEntry(it) }
    }

    /**
     * Clears all trusted issuers (for test isolation and clean resetting).
     */
    fun clearAll() {
        keyCache.clear()
        entryCache.clear()
        val prefs = getPrefs()
        prefs?.edit()?.clear()?.apply()
    }

    private fun persistEntry(entry: TrustedIssuerEntry) {
        val prefs = getPrefs() ?: return
        val kids = prefs.getStringSet(KEY_ISSUER_KIDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        kids.add(entry.kid)

        prefs.edit()
            .putStringSet(KEY_ISSUER_KIDS, kids)
            .putString("$KEY_PREFIX_PUBKEY${entry.kid}", entry.publicKeyBase64)
            .putString("$KEY_PREFIX_LABEL${entry.kid}", entry.label)
            .putLong("$KEY_PREFIX_ENROLLED_AT${entry.kid}", entry.enrolledAt)
            .putBoolean("$KEY_PREFIX_REVOKED${entry.kid}", entry.isRevoked)
            .apply()
    }

    private fun loadEntryFromStorage(kid: String): TrustedIssuerEntry? {
        val prefs = getPrefs() ?: return null
        val pubKey = prefs.getString("$KEY_PREFIX_PUBKEY$kid", null) ?: return null
        val label = prefs.getString("$KEY_PREFIX_LABEL$kid", "Enrolled Issuer") ?: "Enrolled Issuer"
        val enrolledAt = prefs.getLong("$KEY_PREFIX_ENROLLED_AT$kid", System.currentTimeMillis())
        val isRevoked = prefs.getBoolean("$KEY_PREFIX_REVOKED$kid", false)

        val entry = TrustedIssuerEntry(
            kid = kid,
            publicKeyBase64 = pubKey,
            label = label,
            enrolledAt = enrolledAt,
            isRevoked = isRevoked
        )
        entryCache[kid] = entry
        return entry
    }

    private fun loadAllFromStorage() {
        val kids = getEnrolledKids()
        kids.forEach { kid ->
            loadEntryFromStorage(kid)
        }
    }

    private fun getEnrolledKids(): Set<String> {
        val storedKids = getPrefs()?.getStringSet(KEY_ISSUER_KIDS, emptySet()) ?: emptySet()
        val memoryKids = entryCache.keys.toSet()
        return storedKids + memoryKids
    }
}
