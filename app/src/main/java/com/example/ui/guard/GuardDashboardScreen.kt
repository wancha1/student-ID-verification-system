package com.example.ui.guard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuthUser
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.model.SyncInfo
import com.example.model.SyncStatus
import com.example.ui.components.FeeStatusBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.components.VerificationResultDisplay
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import com.example.ui.theme.SchoolPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GuardDashboardScreen(
    user: AuthUser,
    activeScanResult: StudentScanResult?,
    activeScannedStudent: Student?,
    scanError: String?,
    allStudents: List<Student>,
    scanLogs: List<ScanLog>,
    syncInfo: SyncInfo,
    onOpenScanner: () -> Unit,
    onSimulateScan: (String) -> Unit,
    onDismissScanResult: () -> Unit,
    onTriggerSync: () -> Unit,
    onToggleOnline: (Boolean) -> Unit,
    onViewAllLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualIdText by remember { mutableStateOf("") }

    // If a student verification is active, display the full-bleed Verification result view
    if (activeScanResult != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    VerificationResultDisplay(
                        scanResult = activeScanResult,
                        onScanNext = {
                            onDismissScanResult()
                            onOpenScanner()
                        },
                        onDismiss = onDismissScanResult
                    )
                }
            }
        }
        return
    }

    // Main Guard Gate Dashboard
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // 1. SYNC STATUS & OFFLINE FRESHNESS BANNER
        item {
            SyncStatusHeaderCard(
                syncInfo = syncInfo,
                onTriggerSync = onTriggerSync,
                onToggleOnline = onToggleOnline
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. GATE & GUARD STATION BANNER
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SchoolPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Guard Badge",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SECURITY ACCESS DESK",
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = user.station,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Officer on duty: ${user.name}",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ApprovedGreenLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ApprovedGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACTIVE",
                                color = ApprovedGreenText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. PRIMARY ACTION: SCAN BADGE BUTTON
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Gate Access Verification",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Scan student QR badge to evaluate fees and day-scholar gate permissions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onOpenScanner,
                        modifier = Modifier
                            .testTag("button_open_qr_scanner")
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "LAUNCH QR SCANNER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showManualInputDialog = true },
                        modifier = Modifier
                            .testTag("button_manual_id_lookup")
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Manual Student No. Lookup",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // 4. QUICK SIMULATION & TEST CARDS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Field Testing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tap to simulate scan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(allStudents) { student ->
                    Card(
                        modifier = Modifier
                            .testTag("button_simulate_scan_${student.studentNumber}")
                            .width(170.dp)
                            .clickable { onSimulateScan(student.qrPayload) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StudentAvatar(student = student, size = 36.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = student.firstName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = student.studentNumber,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            FeeStatusBadge(feeStatus = student.feesStatus)
                        }
                    }
                }

                // Unregistered test student card
                item {
                    Card(
                        modifier = Modifier
                            .testTag("button_simulate_scan_unregistered")
                            .width(170.dp)
                            .clickable { onSimulateScan("OAKRIDGE:STU:OAK-2026-9999") },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Test: Unregistered",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "OAK-2026-9999",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFB45309)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tests NOT FOUND",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF78350F)
                            )
                        }
                    }
                }

                // Invalid QR test card
                item {
                    Card(
                        modifier = Modifier
                            .testTag("button_simulate_scan_invalid")
                            .width(170.dp)
                            .clickable { onSimulateScan("NON_SCHOOL_BARCODE_XYZ") },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Test: Corrupt QR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "Invalid format",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tests INVALID QR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 5. RECENT GATE SCAN LOGS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onViewAllLogs() }
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Recent Gate Scans",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${scanLogs.size} recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onViewAllLogs,
                        modifier = Modifier.testTag("button_guard_view_all_logs")
                    ) {
                        Text(
                            text = "View All",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (scanLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No gate scans recorded yet today.\nScan student badges to begin entry verification.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(scanLogs.take(8)) { log ->
                val timeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(log.timestamp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (log.isApproved) ApprovedGreenLight else RejectedRedLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (log.isApproved) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (log.isApproved) ApprovedGreen else RejectedRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.studentName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${log.studentNumber ?: "Badge"} • $timeStr",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (log.isApproved) ApprovedGreenLight else RejectedRedLight
                        ) {
                            Text(
                                text = if (log.isApproved) "APPROVED" else "DENIED",
                                color = if (log.isApproved) ApprovedGreenText else RejectedRedText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Manual ID Entry Dialog
    if (showManualInputDialog) {
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = { Text("Manual Student Verification") },
            text = {
                Column {
                    Text(
                        text = "Enter the student registration number (e.g. OAK-2026-0001):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = manualIdText,
                        onValueChange = { manualIdText = it.uppercase() },
                        placeholder = { Text("OAK-2026-0001") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_manual_student_id")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = manualIdText.trim()
                        if (input.isNotBlank()) {
                            showManualInputDialog = false
                            onSimulateScan(input)
                        }
                    },
                    modifier = Modifier.testTag("button_confirm_manual_lookup")
                ) {
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SyncStatusHeaderCard(
    syncInfo: SyncInfo,
    onTriggerSync: () -> Unit,
    onToggleOnline: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val lastSyncStr = timeFormatter.format(Date(syncInfo.lastSyncTimestamp))

    val (bgColor, borderColor, icon, statusTitle, statusSubtitle) = when (syncInfo.status) {
        SyncStatus.SYNCED -> {
            Quint(
                ApprovedGreenLight,
                ApprovedGreen.copy(alpha = 0.4f),
                Icons.Default.CloudDone,
                "Synced with Central Database",
                "Last synchronized at $lastSyncStr • Fully up to date"
            )
        }
        SyncStatus.OFFLINE -> {
            Quint(
                Color(0xFFFEF3C7), // Warm amber
                Color(0xFFF59E0B),
                Icons.Default.CloudOff,
                "Offline Mode Active",
                "Using local cached data from $lastSyncStr (${syncInfo.pendingLogsCount} logs queued)"
            )
        }
        SyncStatus.SYNCING -> {
            Quint(
                Color(0xFFEFF6FF), // Soft Blue
                Color(0xFF3B82F6),
                Icons.Default.CloudSync,
                "Synchronizing Data...",
                "Connecting to central school server..."
            )
        }
        SyncStatus.SYNC_FAILED -> {
            Quint(
                RejectedRedLight,
                RejectedRed.copy(alpha = 0.4f),
                Icons.Default.SyncProblem,
                "Sync Warning",
                "Could not reach cloud • Using local data ($lastSyncStr)"
            )
        }
        SyncStatus.NEEDS_SYNC -> {
            Quint(
                Color(0xFFEFF6FF),
                Color(0xFF3B82F6),
                Icons.Default.CloudSync,
                "Changes Pending Upload",
                "${syncInfo.pendingLogsCount} gate logs awaiting upload"
            )
        }
    }

    Card(
        modifier = modifier
            .testTag("card_sync_status")
            .fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Sync Status",
                tint = if (syncInfo.status == SyncStatus.OFFLINE) Color(0xFFB45309) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusSubtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sync Now icon button
            IconButton(
                onClick = onTriggerSync,
                modifier = Modifier
                    .testTag("button_trigger_sync")
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Now",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Online / Offline toggle switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = if (syncInfo.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = if (syncInfo.isOnline) "Online" else "Offline",
                    tint = if (syncInfo.isOnline) ApprovedGreen else Color(0xFFB45309),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = syncInfo.isOnline,
                    onCheckedChange = onToggleOnline,
                    modifier = Modifier.testTag("switch_network_online"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ApprovedGreen,
                        checkedTrackColor = ApprovedGreenLight
                    )
                )
            }
        }
    }
}

private data class Quint<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
