package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.crypto.KeystoreIssuerManager
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

        // If this device was previously enrolled as an authoritative Issuer, load its Keystore signer
        if (KeystoreIssuerManager.hasIssuerKey()) {
            KeystoreIssuerManager.getSigner()?.let { signer ->
                // Restore local trust before enabling signing. A previously revoked
                // issuer must not regain signing capability after an app restart.
                val entry = TrustedIssuerRegistry.registerTrustedKey(
                    signer.publicKey,
                    "LTC Local Issuer"
                )
                if (entry.isRevoked) {
                    CardCryptoManager.clearIssuerPrivateKey()
                } else {
                    CardCryptoManager.setActiveSigner(signer)
                }
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
