package com.example.data.auth

import android.content.Context
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Creates the LTC Supabase client with encrypted Android session persistence.
 *
 * The publishable key is a public client key, not a service-role secret.
 * Database access is controlled by authenticated requests and Supabase RLS.
 */
object SupabaseClientFactory {

    fun create(context: Context): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL.trim()
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

        require(url.startsWith("https://") && url.endsWith(".supabase.co")) {
            "Supabase URL is missing or invalid. Configure SUPABASE_URL in the local environment."
        }
        require(
            publishableKey.isNotBlank() &&
                publishableKey != "YOUR_SUPABASE_PUBLISHABLE_KEY"
        ) {
            "Supabase publishable key is not configured. Add it to the local environment; never use a service-role key in the Android app."
        }

        val secureSessionManager = EncryptedSupabaseSessionManager(
            vault = EncryptedSessionVault(context.applicationContext)
        )

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = publishableKey
        ) {
            install(Auth) {
                sessionManager = secureSessionManager
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Storage)
        }
    }
}
