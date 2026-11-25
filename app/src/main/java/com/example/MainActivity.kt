package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.crypto.TrustedIssuerRegistry
import com.example.ui.StudentAccessApp
import com.example.ui.theme.MyApplicationTheme
import com.example.util.CardCryptoManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize persistent trusted issuer registry for offline verification
        TrustedIssuerRegistry.initialize(applicationContext)

        // Fail closed: a Keystore key alone is not proof of current server authorization.
        // Issuer signing remains disabled until this device's school-scoped enrollment
        // is confirmed by the backend on this authenticated session.
        CardCryptoManager.clearIssuerPrivateKey()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StudentAccessApp()
                }
            }
        }
    }
}
