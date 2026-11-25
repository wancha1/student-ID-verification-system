package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Card as StudentCard
import com.example.model.CardStatus
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenDark
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberLight
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedDark
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VerificationResultDisplay(
    scanResult: StudentScanResult,
    onScanNext: () -> Unit,
    onDismiss: () -> Unit = onScanNext,
    modifier: Modifier = Modifier
) {
    when (scanResult) {
        is StudentScanResult.Success -> {
            StudentSuccessVerificationView(
                student = scanResult.student,
                card = scanResult.card,
                isApproved = scanResult.isApproved,
                reason = scanResult.reason,
                isOffline = scanResult.isOfflineData,
                lastSyncTimestamp = scanResult.lastSyncTimestamp,
                onScanNext = onScanNext,
                onDismiss = onDismiss,
                modifier = modifier
            )
        }
        is StudentScanResult.CardInactive -> {
            CardInactiveVerificationView(
                student = scanResult.student,
                card = scanResult.card,
                cardStatus = scanResult.cardStatus,
                reason = scanResult.reason,
                isOffline = scanResult.isOfflineData,
                lastSyncTimestamp = scanResult.lastSyncTimestamp,
                onScanNext = onScanNext,
                onDismiss = onDismiss,
                modifier = modifier
            )
        }
        is StudentScanResult.StudentNotFound -> {
            StudentNotFoundVerificationView(
                parsedIdentifier = scanResult.parsedIdentifier,
                reason = scanResult.reason,
                isOffline = scanResult.isOfflineData,
                onScanNext = onScanNext,
                onDismiss = onDismiss,
                modifier = modifier
            )
        }
        is StudentScanResult.InvalidQr -> {
            InvalidQrVerificationView(
                rawString = scanResult.rawScannedString,
                errorReason = scanResult.errorReason,
                onScanNext = onScanNext,
                onDismiss = onDismiss,
                modifier = modifier
            )
        }
    }
}

@Composable
fun VerificationResultDisplay(
    student: Student,
    onScanNext: () -> Unit,
    onDismiss: () -> Unit = onScanNext,
    modifier: Modifier = Modifier
) {
    StudentSuccessVerificationView(
        student = student,
        card = null,
        isApproved = student.isEntryApproved,
        reason = "",
        isOffline = false,
        lastSyncTimestamp = System.currentTimeMillis(),
        onScanNext = onScanNext,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
private fun VerificationScreenScaffold(
    onScanNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            body()
        }

        // Pinned Thumb-Zone Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onScanNext,
                        modifier = Modifier
                            .testTag("button_scan_next_student")
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("SCAN NEXT STUDENT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .testTag("button_back_to_gate_dashboard")
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Return to Gate Dashboard", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun DecisionHeroBanner(
    testTag: String,
    containerColor: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    freshnessLabel: String?,
    isOffline: Boolean
) {
    Surface(
        modifier = Modifier
            .testTag(testTag)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (freshnessLabel != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = freshnessLabel,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentSuccessVerificationView(
    student: Student,
    card: StudentCard?,
    isApproved: Boolean,
    reason: String,
    isOffline: Boolean,
    lastSyncTimestamp: Long,
    onScanNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTimeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
    val lastSyncStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastSyncTimestamp))

    VerificationScreenScaffold(
        onScanNext = onScanNext,
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        DecisionHeroBanner(
            testTag = if (isApproved) "banner_entry_approved" else "banner_entry_not_approved",
            containerColor = if (isApproved) ApprovedGreen else RejectedRed,
            icon = if (isApproved) Icons.Default.CheckCircle else Icons.Default.Block,
            title = if (isApproved) "ENTRY APPROVED" else "ENTRY NOT APPROVED",
            subtitle = reason.ifBlank {
                if (isApproved) "School fees cleared • Access granted" else "School fees outstanding • Entry restricted"
            },
            freshnessLabel = if (isOffline) "Offline cache (Synced $lastSyncStr)" else "Live verified ($currentTimeStr)",
            isOffline = isOffline
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudentAvatar(student = student, size = 72.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.fullName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = student.gradeClass,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (card != null) "${student.studentNumber} • ${card.cardIdentifier}" else student.studentNumber,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isApproved) ApprovedGreenLight else RejectedRedLight,
                        border = BorderStroke(1.dp, if (isApproved) ApprovedGreen else RejectedRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "FEES STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isApproved) ApprovedGreenDark else RejectedRedDark
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isApproved) "CLEARED" else "OUTSTANDING",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isApproved) ApprovedGreenText else RejectedRedText
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "SCHOLAR STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (student.isDayScholar) "DAY SCHOLAR" else "BOARDER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isApproved) Icons.Default.DirectionsBus else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (isApproved) MaterialTheme.colorScheme.primary else RejectedRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isApproved) "Access Instruction" else "Action Required",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Text(
                            text = if (isApproved) {
                                if (student.homeroomTeacher.isNotBlank()) {
                                    "Allow entry through gate. Homeroom: ${student.homeroomTeacher}."
                                } else {
                                    "Allow entry through gate."
                                }
                            } else {
                                reason.ifBlank { "Hold at gate and direct student to Administration." }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (student.guardianName.isNotBlank() || student.guardianPhone.isNotBlank()) {
                            val guardianSummary = buildString {
                                append("Guardian: ")
                                append(student.guardianName.ifBlank { "On file" })
                                if (student.guardianPhone.isNotBlank()) {
                                    append(" (${student.guardianPhone})")
                                }
                            }
                            Text(
                                text = guardianSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Gate Guard View • Read-only student record",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun CardInactiveVerificationView(
    student: Student,
    card: StudentCard,
    cardStatus: CardStatus,
    reason: String,
    isOffline: Boolean,
    lastSyncTimestamp: Long,
    onScanNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTimeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
    val lastSyncStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastSyncTimestamp))

    VerificationScreenScaffold(
        onScanNext = onScanNext,
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        DecisionHeroBanner(
            testTag = "banner_card_inactive",
            containerColor = when (cardStatus) {
                CardStatus.LOST -> RejectedRed
                CardStatus.REPLACED, CardStatus.ACTIVE -> PendingAmber
                CardStatus.DEACTIVATED -> Color(0xFF475569)
            },
            icon = Icons.Default.Cancel,
            title = "CARD INACTIVE",
            subtitle = when (cardStatus) {
                CardStatus.LOST -> "Card Reported Lost • Do Not Admit"
                CardStatus.REPLACED -> "Old Badge Presented • Replaced"
                CardStatus.DEACTIVATED -> "Card Deactivated by Admin"
                CardStatus.ACTIVE -> "Card Inactive"
            },
            freshnessLabel = if (isOffline) "Offline evaluation (Synced $lastSyncStr)" else "Live evaluation ($currentTimeStr)",
            isOffline = isOffline
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudentAvatar(student = student, size = 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.fullName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${student.gradeClass} • ${student.studentNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        CardStatusBadge(status = cardStatus)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Text(
                    text = "Card ID: ${card.cardIdentifier}",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RejectedRedLight,
                    border = BorderStroke(1.dp, RejectedRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RejectedRedDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (cardStatus) {
                                CardStatus.LOST -> "Do NOT admit. Confiscate lost badge and escort student to Administration."
                                CardStatus.REPLACED -> "Do NOT admit with this old badge. Request student's replacement card."
                                CardStatus.DEACTIVATED -> "Card deactivated. Direct student to Administration desk."
                                CardStatus.ACTIVE -> "Direct student to Administration desk."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = RejectedRedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentNotFoundVerificationView(
    parsedIdentifier: String,
    reason: String,
    isOffline: Boolean,
    onScanNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    VerificationScreenScaffold(
        onScanNext = onScanNext,
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        DecisionHeroBanner(
            testTag = "banner_student_not_found",
            containerColor = PendingAmber,
            icon = Icons.Default.PersonOff,
            title = "STUDENT NOT FOUND",
            subtitle = "No matching student record found",
            freshnessLabel = null,
            isOffline = isOffline
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Identifier: $parsedIdentifier",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PendingAmberLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isOffline)
                            "Student may be newly enrolled. Connect online to sync or escort student to Administration."
                        else
                            "Not registered for the current academic year. Escort student to Administration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF78350F),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InvalidQrVerificationView(
    rawString: String,
    errorReason: String,
    onScanNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    VerificationScreenScaffold(
        onScanNext = onScanNext,
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        DecisionHeroBanner(
            testTag = "banner_invalid_qr",
            containerColor = Color(0xFF475569),
            icon = Icons.Default.ErrorOutline,
            title = "INVALID QR CODE",
            subtitle = "Unrecognized or tampered badge payload",
            freshnessLabel = null,
            isOffline = false
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Payload: \"$rawString\"",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = errorReason,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Ensure the student presents an official signed Lira Town College ID card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
