package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.jan.supabase.handleDeeplinks
import com.example.data.auth.SupabaseClientFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.crypto.KeystoreIssuerManager
import com.example.crypto.TrustedIssuerRegistry
import com.example.ui.StudentAccessApp
import com.example.ui.theme.MyApplicationTheme
import com.example.util.CardCryptoManager

class MainActivity : ComponentActivity() {
    companion object {
        var passwordRecoveryRequested by mutableStateOf(false)
            private set

        fun dismissPasswordRecovery() {
            passwordRecoveryRequested = false
        }
    }

    private fun handleAuthCallback(callbackIntent: android.content.Intent?) {
        val callback = callbackIntent?.data ?: return
        if (callback.scheme == "ltcstudentaccess" && callback.host == "auth-callback") {
            passwordRecoveryRequested = true
            runCatching { SupabaseClientFactory.getInstance(applicationContext).handleDeeplinks(callbackIntent) }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthCallback(intent)

        // Initialize persistent trusted issuer registry for offline verification
        TrustedIssuerRegistry.initialize(applicationContext)

        // If this device was previously enrolled as an authoritative Issuer, load its Keystore signer
        if (KeystoreIssuerManager.hasIssuerKey()) {
            KeystoreIssuerManager.getSigner()?.let { signer ->
                CardCryptoManager.setActiveSigner(signer)
            }
        }

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StudentAccessApp()
                }
            }
        }
    }
}
