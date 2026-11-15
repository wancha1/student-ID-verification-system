package com.example.data.auth

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * Supabase Kotlin 3.5.0 SessionManager backed by [EncryptedSessionVault].
 *
 * encodeDefaults must remain enabled: Supabase's own session-manager guidance
 * warns that omitting default-valued UserSession fields can break restoration.
 */
class EncryptedSupabaseSessionManager(
    private val vault: EncryptedSessionVault,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        vault.save(json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession? {
        val payload = try {
            vault.load()
        } catch (_: EncryptedSessionVault.SessionVaultException) {
            // The vault has cleared an unreadable or invalidated session.
            // Returning null lets Auth start without a restored session.
            return null
        } ?: return null

        return try {
            json.decodeFromString(UserSession.serializer(), payload)
        } catch (_: Exception) {
            vault.clear()
            null
        }
    }

    override suspend fun deleteSession() {
        vault.clear()
    }
}
