package com.example.ui.logs

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.ui.components.GateDecisionBadge
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean and simple log screen to view recent gate access activity,
 * including timestamps, student names, verification outcomes, and details using a LazyColumn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateAccessLogsScreen(
    scanLogs: List<ScanLog>,
    onBack: () -> Unit,
    onClearLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedLogForDetails by remember { mutableStateOf<ScanLog?>(null) }

    val filteredLogs = remember(scanLogs, selectedFilter) {
        when (selectedFilter) {
            "APPROVED" -> scanLogs.filter { it.decision == GateVerificationDecision.APPROVED }
            "DENIED" -> scanLogs.filter { it.decision != GateVerificationDecision.APPROVED }
            else -> scanLogs
        }
    }

    val approvedCount = scanLogs.count { it.decision == GateVerificationDecision.APPROVED }
    val deniedCount = scanLogs.count { it.decision != GateVerificationDecision.APPROVED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Gate Access Activity",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${scanLogs.size} total entries recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("button_back_gate_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (onClearLogs != null && scanLogs.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("button_clear_gate_logs")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear logs",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.testTag("screen_gate_access_logs")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Filter Pills Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${scanLogs.size})") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("filter_chip_all")
                )

                FilterChip(
                    selected = selectedFilter == "APPROVED",
                    onClick = { selectedFilter = "APPROVED" },
                    label = { Text("Approved ($approvedCount)") },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ApprovedGreenLight,
                        selectedLabelColor = ApprovedGreenText
                    ),
                    modifier = Modifier.testTag("filter_chip_approved")
                )

                FilterChip(
                    selected = selectedFilter == "DENIED",
                    onClick = { selectedFilter = "DENIED" },
                    label = { Text("Denied ($deniedCount)") },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RejectedRedLight,
                        selectedLabelColor = RejectedRedText
                    ),
                    modifier = Modifier.testTag("filter_chip_denied")
                )
            }

            // LazyColumn of Gate Activity Logs
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (scanLogs.isEmpty()) "No Gate Access Activity Yet" else "No matching logs found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (scanLogs.isEmpty())
                                "When student cards are scanned at the gate terminal, real-time activity with timestamps and student names will be displayed here."
                            else
                                "Change the filter to view other gate entries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("lazy_column_gate_logs"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredLogs,
                        key = { log -> log.id }
                    ) { log ->
                        GateLogItemCard(
                            log = log,
                            onClick = { selectedLogForDetails = log }
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedLogForDetails?.let { log ->
        val fullDateFormat = remember { SimpleDateFormat("EEEE, MMMM d, yyyy • hh:mm:ss a", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { selectedLogForDetails = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gate Access Detail", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GateDecisionBadge(decision = log.decision, isLarge = true)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Student Name:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = log.studentName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Student Number:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = log.studentNumber ?: log.studentId ?: "N/A",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    if (log.cardIdentifier != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Card Identifier:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = log.cardIdentifier,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Timestamp:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fullDateFormat.format(Date(log.timestamp)),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Security Guard & Location:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${log.guardName} • ${log.gateLocation}",
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Access Verification Reason:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = log.reason,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLogForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear Logs Confirmation
    if (showClearDialog && onClearLogs != null) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Gate Access Logs?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all recent gate access logs from this terminal.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLogs()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("button_confirm_clear_gate_logs")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Individual log item card with student name, timestamp, decision badge, and guard details.
 */
@Composable
fun GateLogItemCard(
    log: ScanLog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isApproved = log.decision == GateVerificationDecision.APPROVED
    val timeFormatter = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val formattedTime = remember(log.timestamp) { timeFormatter.format(Date(log.timestamp)) }
    val formattedDate = remember(log.timestamp) { dateFormatter.format(Date(log.timestamp)) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("gate_log_item_${log.id}")
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon Indicator
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isApproved) ApprovedGreenLight else RejectedRedLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (log.decision) {
                        GateVerificationDecision.APPROVED -> Icons.Default.CheckCircle
                        GateVerificationDecision.NOT_APPROVED -> Icons.Default.Close
                        GateVerificationDecision.CARD_INACTIVE -> Icons.Default.Block
                        GateVerificationDecision.STUDENT_NOT_FOUND -> Icons.Default.Error
                        GateVerificationDecision.INVALID_QR -> Icons.Default.QrCodeScanner
                    },
                    contentDescription = null,
                    tint = if (isApproved) ApprovedGreen else RejectedRed,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Student Name and Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.studentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.studentNumber ?: log.studentId ?: "Unknown",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )

                    if (log.gradeClass.isNotBlank() && log.gradeClass != "Unknown") {
                        Text(
                            text = " • ${log.gradeClass}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Guard: ${log.guardName} • ${log.gateLocation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.5.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Timestamp and Outcome Badge
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                GateDecisionBadge(decision = log.decision)

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
