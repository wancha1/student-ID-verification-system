package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Manages authenticated administrative access and supervisor emergency gate overrides.
 * Eliminates unauthenticated role switching and bypass buttons.
 */
object SecurityManager {

    private const val PREFS_NAME = "ltc_security_prefs"
    private const val KEY_ADMIN_PIN_HASH = "admin_pin_hash"
    private const val KEY_SUPERVISOR_PIN_HASH = "supervisor_pin_hash"

    // Default emergency master PIN for initial pilot configuration: "2026"
    private const val DEFAULT_PIN = "2026"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("ltc_salt_${pin.trim()}".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies if the entered PIN matches the administrator master PIN.
     */
    fun verifyAdminPin(context: Context, enteredPin: String): Boolean {
        val prefs = getPrefs(context)
        val storedHash = prefs.getString(KEY_ADMIN_PIN_HASH, null) ?: hashPin(DEFAULT_PIN)
        return storedHash == hashPin(enteredPin)
    }

    /**
     * Sets or updates the administrator master PIN.
     */
    fun setAdminPin(context: Context, oldPin: String?, newPin: String): Boolean {
        if (newPin.length < 4) return false
        val prefs = getPrefs(context)
        val storedHash = prefs.getString(KEY_ADMIN_PIN_HASH, null) ?: hashPin(DEFAULT_PIN)

        if (oldPin != null && storedHash != hashPin(oldPin)) {
            return false
        }

        prefs.edit().putString(KEY_ADMIN_PIN_HASH, hashPin(newPin)).apply()
        return true
    }

    /**
     * Verifies supervisor PIN for emergency gate access overrides.
     * Allows designated school supervisors / administrators to authorize rejected day scholars.
     */
    fun verifySupervisorOverridePin(context: Context, enteredPin: String): Boolean {
        val prefs = getPrefs(context)
        val storedSupervisorHash = prefs.getString(KEY_SUPERVISOR_PIN_HASH, null)
        val storedAdminHash = prefs.getString(KEY_ADMIN_PIN_HASH, null) ?: hashPin(DEFAULT_PIN)
        val enteredHash = hashPin(enteredPin)

        return enteredHash == storedSupervisorHash || enteredHash == storedAdminHash
    }

    /**
     * Configures a distinct supervisor PIN.
     */
    fun setSupervisorPin(context: Context, newPin: String): Boolean {
        if (newPin.length < 4) return false
        getPrefs(context).edit().putString(KEY_SUPERVISOR_PIN_HASH, hashPin(newPin)).apply()
        return true
    }
}
