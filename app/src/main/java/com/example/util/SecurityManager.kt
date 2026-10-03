package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.model.UserRole
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages staff authentication, role credential verification, and supervisor gate overrides.
 * Strictly eliminates hardcoded default PINs (e.g. "2026"), universal backdoors, and unauthenticated role switching.
 */
object SecurityManager {

    private const val PREFS_NAME = "ltc_security_prefs"
    private const val KEY_SALT = "crypto_salt"
    private const val KEY_ADMIN_PIN_HASH = "admin_pin_hash"
    private const val KEY_ADMIN_NAME = "admin_staff_name"
    private const val KEY_SUPERVISOR_PIN_HASH = "supervisor_pin_hash"
    private const val KEY_ROLE_PIN_PREFIX = "role_pin_hash_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retrieves or generates a cryptographically secure random per-installation salt.
     */
    private fun getOrCreateSalt(context: Context): String {
        val prefs = getPrefs(context)
        var salt = prefs.getString(KEY_SALT, null)
        if (salt.isNullOrBlank()) {
            val random = SecureRandom()
            val saltBytes = ByteArray(16)
            random.nextBytes(saltBytes)
            salt = saltBytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_SALT, salt).apply()
        }
        return salt
    }

    /**
     * Computes SHA-256 hash using the installation-specific salt.
     */
    private fun hashPin(salt: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "ltc_v2_${salt}_${pin.trim()}"
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Checks if the device has completed initial staff security provisioning.
     * Returns true ONLY if an administrator PIN has been established.
     * No default PIN exists.
     */
    fun isProvisioned(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_ADMIN_PIN_HASH)
    }

    /**
     * Provisions the initial Master Administrator credentials during first-run setup.
     */
    fun provisionInitialAdmin(context: Context, adminName: String, adminPin: String): Boolean {
        if (adminPin.length < 4 || !adminPin.all { it.isDigit() }) return false
        val cleanName = adminName.trim().ifBlank { "System Administrator" }
        val salt = getOrCreateSalt(context)
        val hash = hashPin(salt, adminPin)

        getPrefs(context).edit()
            .putString(KEY_ADMIN_PIN_HASH, hash)
            .putString(KEY_ADMIN_NAME, cleanName)
            .apply()
        return true
    }

    /**
     * Gets the provisioned administrator name, if available.
     */
    fun getAdminName(context: Context): String {
        return getPrefs(context).getString(KEY_ADMIN_NAME, "System Administrator") ?: "System Administrator"
    }

    /**
     * Verifies PIN for entering a specific staff duty role.
     * Every role requires authentication.
     * If a role has a dedicated PIN set, it checks against that PIN.
     * If not, the Master Administrator PIN authenticates the role.
     */
    fun verifyRolePin(context: Context, role: UserRole, enteredPin: String): Boolean {
        if (!isProvisioned(context)) return false
        val prefs = getPrefs(context)
        val salt = getOrCreateSalt(context)
        val enteredHash = hashPin(salt, enteredPin)

        val rolePinKey = KEY_ROLE_PIN_PREFIX + role.name
        val storedRoleHash = prefs.getString(rolePinKey, null)
        val storedAdminHash = prefs.getString(KEY_ADMIN_PIN_HASH, null)

        return (storedRoleHash != null && enteredHash == storedRoleHash) ||
               (storedAdminHash != null && enteredHash == storedAdminHash)
    }

    /**
     * Verifies if the entered PIN matches the administrator master PIN.
     */
    fun verifyAdminPin(context: Context, enteredPin: String): Boolean {
        if (!isProvisioned(context)) return false
        val prefs = getPrefs(context)
        val salt = getOrCreateSalt(context)
        val storedHash = prefs.getString(KEY_ADMIN_PIN_HASH, null) ?: return false
        return storedHash == hashPin(salt, enteredPin)
    }

    /**
     * Sets or updates the administrator master PIN.
     */
    fun setAdminPin(context: Context, oldPin: String?, newPin: String): Boolean {
        if (newPin.length < 4 || !newPin.all { it.isDigit() }) return false
        val prefs = getPrefs(context)
        val salt = getOrCreateSalt(context)
        val storedHash = prefs.getString(KEY_ADMIN_PIN_HASH, null)

        if (storedHash != null && oldPin != null) {
            if (storedHash != hashPin(salt, oldPin)) return false
        } else if (storedHash != null && oldPin == null) {
            return false // Must provide current PIN to change
        }

        prefs.edit().putString(KEY_ADMIN_PIN_HASH, hashPin(salt, newPin)).apply()
        return true
    }

    /**
     * Configures a distinct PIN for a specific duty role (e.g. Gate Staff, Bursar, Meals).
     */
    fun setRolePin(context: Context, role: UserRole, newPin: String): Boolean {
        if (newPin.length < 4 || !newPin.all { it.isDigit() }) return false
        val salt = getOrCreateSalt(context)
        val key = KEY_ROLE_PIN_PREFIX + role.name
        getPrefs(context).edit().putString(key, hashPin(salt, newPin)).apply()
        return true
    }

    /**
     * Verifies supervisor PIN for emergency gate access overrides.
     * Allows designated school supervisors / administrators to authorize rejected access.
     */
    fun verifySupervisorOverridePin(context: Context, enteredPin: String): Boolean {
        if (!isProvisioned(context)) return false
        val prefs = getPrefs(context)
        val salt = getOrCreateSalt(context)
        val storedSupervisorHash = prefs.getString(KEY_SUPERVISOR_PIN_HASH, null)
        val storedAdminHash = prefs.getString(KEY_ADMIN_PIN_HASH, null)
        val enteredHash = hashPin(salt, enteredPin)

        return (storedSupervisorHash != null && enteredHash == storedSupervisorHash) ||
               (storedAdminHash != null && enteredHash == storedAdminHash)
    }

    /**
     * Configures a distinct supervisor override PIN.
     */
    fun setSupervisorPin(context: Context, newPin: String): Boolean {
        if (newPin.length < 4 || !newPin.all { it.isDigit() }) return false
        val salt = getOrCreateSalt(context)
        getPrefs(context).edit().putString(KEY_SUPERVISOR_PIN_HASH, hashPin(salt, newPin)).apply()
        return true
    }

    /**
     * Test-only helper: sets up isolated credentials for deterministic automated testing.
     */
    fun configureTestCredentials(
        context: Context,
        adminPin: String = "1234",
        adminName: String = "Test Administrator",
        supervisorPin: String? = null,
        rolePins: Map<UserRole, String> = emptyMap()
    ) {
        val prefs = getPrefs(context)
        val salt = getOrCreateSalt(context)
        val editor = prefs.edit()
        editor.putString(KEY_ADMIN_PIN_HASH, hashPin(salt, adminPin))
        editor.putString(KEY_ADMIN_NAME, adminName)
        if (supervisorPin != null) {
            editor.putString(KEY_SUPERVISOR_PIN_HASH, hashPin(salt, supervisorPin))
        }
        for ((role, pin) in rolePins) {
            editor.putString(KEY_ROLE_PIN_PREFIX + role.name, hashPin(salt, pin))
        }
        editor.apply()
    }

    /**
     * Test-only helper: resets security prefs to an unprovisioned state.
     */
    fun resetForTesting(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
