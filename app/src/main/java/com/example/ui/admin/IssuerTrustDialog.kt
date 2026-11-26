package com.example.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.TrustedIssuerEntry

@Composable
fun IssuerTrustDialog(
    hasIssuerSigningKey: Boolean,
    activeIssuerKeyId: String?,
    activeIssuerPublicKeyBase64: String?,
    trustedIssuers: List<TrustedIssuerEntry>,
    onGenerateIssuerKey: () -> Unit,
    onEnrollTrustedKey: (base64: String, label: String) -> Unit,
    onRevokeTrustedIssuer: (kid: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var remoteKeyInput by remember { mutableStateOf("") }
    var remoteLabelInput by remember { mutableStateOf("") }
    var statusNotice by remember { mutableStateOf<String?>(null) }
    var revokeTarget by remember { mutableStateOf<TrustedIssuerEntry?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Issuer & Trust Keys",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                statusNotice?.let { notice ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Local Hardware Signing Key Section
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasIssuerSigningKey) Icons.Default.CheckCircle else Icons.Default.Key,
                                contentDescription = null,
                                tint = if (hasIssuerSigningKey) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasIssuerSigningKey) "Issuing Key Active" else "No Issuing Key Enrolled",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (hasIssuerSigningKey && activeIssuerPublicKeyBase64 != null) {
                            Text(
                                text = "Key ID (kid): ${activeIssuerKeyId ?: "Unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Share this public key with Gate and Dining terminals so they verify cards issued from this device offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(
                                            ClipData.newPlainText("LTC Issuer Public Key", activeIssuerPublicKeyBase64)
                                        )
                                        statusNotice = "Issuer public key copied to clipboard."
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("button_copy_issuer_pubkey")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Key", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "LTC Issuer Public Key (${activeIssuerKeyId.orEmpty()})")
                                                putExtra(Intent.EXTRA_TEXT, activeIssuerPublicKeyBase64)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share LTC Issuer Public Key"))
                                        }.onFailure {
                                            statusNotice = "No share app available on this device. Use Copy Key instead."
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("button_share_issuer_pubkey")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text(
                                text = "Enroll a hardware-backed Android Keystore ECDSA P-256 signing key to issue cryptographic student ID cards on this terminal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onGenerateIssuerKey,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("button_enroll_issuer_key")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enroll Authoritative Issuer Key")
                            }
                        }
                    }
                }

                // 2. Enroll Remote Trusted Issuer Key Section
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Trust External Issuing Terminal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = remoteLabelInput,
                            onValueChange = { remoteLabelInput = it },
                            label = { Text("Terminal Label (e.g. Bursar Desk 1)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_trusted_issuer_label")
                        )
                        OutlinedTextField(
                            value = remoteKeyInput,
                            onValueChange = { remoteKeyInput = it },
                            label = { Text("Base64 X.509 Public Key") },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_trusted_issuer_pubkey")
                        )
                        Button(
                            onClick = {
                                val cleanedKey = remoteKeyInput.trim()
                                val cleanedLabel = remoteLabelInput.trim().ifBlank { "Remote LTC Issuer" }
                                if (cleanedKey.isBlank()) {
                                    statusNotice = "Paste an issuer public key before enrolling."
                                    return@Button
                                }
                                onEnrollTrustedKey(cleanedKey, cleanedLabel)
                                remoteKeyInput = ""
                                remoteLabelInput = ""
                                statusNotice = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("button_enroll_trusted_pubkey")
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enroll Trusted Public Key")
                        }
                    }
                }

                // 3. Trusted Registry List
                Text(
                    text = "Trusted Issuer Registry (${trustedIssuers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (trustedIssuers.isEmpty()) {
                    Text(
                        text = "No trusted issuer keys enrolled yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        trustedIssuers.forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "kid: ${entry.kid}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (entry.isRevoked) "REVOKED" else "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (entry.isRevoked) MaterialTheme.colorScheme.error else Color(0xFF059669)
                                        )
                                    }
                                    if (!entry.isRevoked) {
                                        TextButton(
                                            onClick = { revokeTarget = entry },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier.testTag("button_revoke_issuer_${entry.kid}")
                                        ) {
                                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Revoke", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("button_close_issuer_trust")
            ) {
                Text("Done")
            }
        }
    )

    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke Issuer Key?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Cards signed by '${target.label}' (${target.kid}) will immediately be rejected at all scanners on this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRevokeTrustedIssuer(target.kid)
                        revokeTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Revoke Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
