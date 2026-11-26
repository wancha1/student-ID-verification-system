package com.example.ui.logs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.ui.components.GateDecisionBadge
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.Navy900
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberLight
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import com.example.util.ExportUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            "DENIED" -> scanLogs.filter { it.decision != GateVerificationDecision.APPROVED && it.decision != GateVerificationDecision.POLICY_PENDING }
            "PENDING" -> scanLogs.filter { it.decision == GateVerificationDecision.POLICY_PENDING }
            else -> scanLogs
        }
    }

    val approvedCount = remember(scanLogs) { scanLogs.count { it.decision == GateVerificationDecision.APPROVED } }
    val deniedCount = remember(scanLogs) { scanLogs.count { it.decision != GateVerificationDecision.APPROVED && it.decision != GateVerificationDecision.POLICY_PENDING } }
    val pendingCount = remember(scanLogs) { scanLogs.count { it.decision == GateVerificationDecision.POLICY_PENDING } }
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Gate Access Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${scanLogs.size} total entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("button_back_gate_logs")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (scanLogs.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val csv = ExportUtils.generateGateLogsCsv(scanLogs)
                                ExportUtils.shareData(
                                    context = context,
                                    content = csv,
                                    subject = "LTC Gate Scan History (CSV)",
                                    isCsv = true
                                )
                            },
                            modifier = Modifier.testTag("button_export_csv_logs")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export CSV"
                            )
                        }
                    }
                    if (onClearLogs != null && scanLogs.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("button_clear_gate_logs")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear logs",
                                tint = Color(0xFFFCA5A5)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy900,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${scanLogs.size})") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("filter_chip_all")
                )

                FilterChip(
                    selected = selectedFilter == "APPROVED",
                    onClick = { selectedFilter = "APPROVED" },
                    label = { Text("Approved ($approvedCount)") },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ApprovedGreenLight,
                        selectedLabelColor = ApprovedGreenText
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("filter_chip_approved")
                )

                FilterChip(
                    selected = selectedFilter == "DENIED",
                    onClick = { selectedFilter = "DENIED" },
                    label = { Text("Denied ($deniedCount)") },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RejectedRedLight,
                        selectedLabelColor = RejectedRedText
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("filter_chip_denied")
                )

                FilterChip(
                    selected = selectedFilter == "PENDING",
                    onClick = { selectedFilter = "PENDING" },
                    label = { Text("Pending ($pendingCount)") },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PendingAmberLight,
                        selectedLabelColor = PendingAmber
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("filter_chip_pending")
                )
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (scanLogs.isEmpty()) "No Gate Activity Yet" else "No matching logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (scanLogs.isEmpty()) {
                                "Scanned student cards at the gate will appear here in real time."
                            } else {
                                "Try selecting a different filter above."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
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
                    items(items = filteredLogs, key = { it.id }) { log ->
                        GateLogItemCard(
                            log = log,
                            onClick = { selectedLogForDetails = log }
                        )
                    }
                }
            }
        }
    }

    selectedLogForDetails?.let { log ->
        val fullDateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy • hh:mm:ss a", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { selectedLogForDetails = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Gate Access Detail", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GateDecisionBadge(decision = log.decision, isLarge = true)
                    Spacer(Modifier.height(14.dp))

                    LogDetailLine(label = "Student Name", value = log.studentName)
                    Spacer(Modifier.height(8.dp))
                    LogDetailLine(
                        label = "Student Number",
                        value = log.studentNumber ?: log.studentId ?: "N/A",
                        mono = true
                    )
                    if (log.cardIdentifier != null) {
                        Spacer(Modifier.height(8.dp))
                        LogDetailLine(
                            label = "Card Identifier",
                            value = log.cardIdentifier,
                            mono = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LogDetailLine(label = "Timestamp", value = fullDateFormat.format(Date(log.timestamp)))
                    Spacer(Modifier.height(8.dp))
                    LogDetailLine(label = "Officer & Gate", value = "${log.guardName} • ${log.gateLocation}")
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    LogDetailLine(label = "Verification Reason", value = log.reason)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedLogForDetails = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close")
                }
            }
        )
    }

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
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("button_confirm_clear_gate_logs")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LogDetailLine(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = if (mono) MonoFontFamily else null,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun GateLogItemCard(
    log: ScanLog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isApproved = log.decision == GateVerificationDecision.APPROVED
    val isPending = log.decision == GateVerificationDecision.POLICY_PENDING
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { timeFormatter.format(Date(log.timestamp)) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("gate_log_item_${log.id}")
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(when { isPending -> PendingAmberLight; isApproved -> ApprovedGreenLight; else -> RejectedRedLight }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (log.decision) {
                        GateVerificationDecision.APPROVED -> Icons.Default.CheckCircle
                        GateVerificationDecision.NOT_APPROVED -> Icons.Default.Close
                        GateVerificationDecision.CARD_INACTIVE -> Icons.Default.Block
                        GateVerificationDecision.STUDENT_NOT_FOUND -> Icons.Default.Error
                        GateVerificationDecision.INVALID_QR -> Icons.Default.QrCodeScanner
                        GateVerificationDecision.POLICY_PENDING -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = when { isPending -> PendingAmber; isApproved -> ApprovedGreen; else -> RejectedRed },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.studentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(log.studentNumber ?: log.studentId ?: "Unknown")
                        if (log.gradeClass.isNotBlank() && log.gradeClass != "Unknown") {
                            append(" • ${log.gradeClass}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${log.guardName} • ${log.gateLocation}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                GateDecisionBadge(decision = log.decision)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
