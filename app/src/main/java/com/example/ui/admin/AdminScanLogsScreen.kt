package com.example.ui.admin

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.theme.ApprovedGreenDark
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedDark
import com.example.ui.theme.RejectedRedLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DecisionFilter(val label: String) {
    ALL("All"),
    APPROVED("Approved"),
    NOT_APPROVED("Not Approved"),
    CARD_INACTIVE("Card Inactive"),
    NOT_FOUND("Not Found"),
    INVALID_QR("Invalid QR")
}

@Composable
fun AdminScanLogsScreen(
    scanLogs: List<ScanLog>,
    onBack: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedDecisionFilter by remember { mutableStateOf(DecisionFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLogForDetail by remember { mutableStateOf<ScanLog?>(null) }

    val totalCount = scanLogs.size
    val approvedCount = scanLogs.count { it.decision == GateVerificationDecision.APPROVED }
    val deniedCount = scanLogs.count { it.decision == GateVerificationDecision.NOT_APPROVED }
    val cardInactiveCount = scanLogs.count { it.decision == GateVerificationDecision.CARD_INACTIVE }
    val notFoundCount = scanLogs.count { it.decision == GateVerificationDecision.STUDENT_NOT_FOUND }
    val invalidQrCount = scanLogs.count { it.decision == GateVerificationDecision.INVALID_QR }

    val approvalRate = if (totalCount > 0) (approvedCount * 100 / totalCount) else 100

    val filteredLogs = scanLogs.filter { log ->
        val matchesFilter = when (selectedDecisionFilter) {
            DecisionFilter.ALL -> true
            DecisionFilter.APPROVED -> log.decision == GateVerificationDecision.APPROVED
            DecisionFilter.NOT_APPROVED -> log.decision == GateVerificationDecision.NOT_APPROVED
            DecisionFilter.CARD_INACTIVE -> log.decision == GateVerificationDecision.CARD_INACTIVE
            DecisionFilter.NOT_FOUND -> log.decision == GateVerificationDecision.STUDENT_NOT_FOUND
            DecisionFilter.INVALID_QR -> log.decision == GateVerificationDecision.INVALID_QR
        }

        val query = searchQuery.trim()
        val matchesSearch = query.isBlank() ||
            log.studentName.contains(query, ignoreCase = true) ||
            (log.studentNumber?.contains(query, ignoreCase = true) == true) ||
            (log.cardIdentifier?.contains(query, ignoreCase = true) == true) ||
            log.guardName.contains(query, ignoreCase = true) ||
            log.gateLocation.contains(query, ignoreCase = true) ||
            log.reason.contains(query, ignoreCase = true)

        matchesFilter && matchesSearch
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("button_back_from_logs")
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Gate Activity Audit Logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$totalCount total scans recorded locally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (scanLogs.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.testTag("button_clear_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear logs",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Metrics Summary Row
        if (totalCount > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = "TOTAL SCANS",
                        value = totalCount.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "APPROVED",
                        value = "$approvedCount ($approvalRate%)",
                        color = ApprovedGreenDark,
                        bgColor = ApprovedGreenLight,
                        modifier = Modifier.weight(1.2f)
                    )
                    MetricBox(
                        title = "CARD INACTIVE",
                        value = cardInactiveCount.toString(),
                        color = Color(0xFFB45309),
                        bgColor = Color(0xFFFEF3C7),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "DENIED",
                        value = deniedCount.toString(),
                        color = RejectedRedDark,
                        bgColor = RejectedRedLight,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            // Search input field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by student, card ID, guard, gate...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .testTag("input_search_logs")
                        .fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }

            // Horizontal Filter Chips
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedDecisionFilter == DecisionFilter.ALL,
                            onClick = { selectedDecisionFilter = DecisionFilter.ALL },
                            label = { Text("All ($totalCount)") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("filter_logs_all")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedDecisionFilter == DecisionFilter.APPROVED,
                            onClick = { selectedDecisionFilter = DecisionFilter.APPROVED },
                            label = { Text("Approved ($approvedCount)") },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ApprovedGreenLight,
                                selectedLabelColor = ApprovedGreenDark
                            ),
                            modifier = Modifier.testTag("filter_logs_approved")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedDecisionFilter == DecisionFilter.CARD_INACTIVE,
                            onClick = { selectedDecisionFilter = DecisionFilter.CARD_INACTIVE },
                            label = { Text("Card Inactive ($cardInactiveCount)") },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFEF3C7),
                                selectedLabelColor = Color(0xFF92400E)
                            ),
                            modifier = Modifier.testTag("filter_logs_card_inactive")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedDecisionFilter == DecisionFilter.NOT_APPROVED,
                            onClick = { selectedDecisionFilter = DecisionFilter.NOT_APPROVED },
                            label = { Text("Not Approved ($deniedCount)") },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RejectedRedLight,
                                selectedLabelColor = RejectedRedDark
                            ),
                            modifier = Modifier.testTag("filter_logs_not_approved")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedDecisionFilter == DecisionFilter.NOT_FOUND,
                            onClick = { selectedDecisionFilter = DecisionFilter.NOT_FOUND },
                            label = { Text("Not Found ($notFoundCount)") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("filter_logs_not_found")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedDecisionFilter == DecisionFilter.INVALID_QR,
                            onClick = { selectedDecisionFilter = DecisionFilter.INVALID_QR },
                            label = { Text("Invalid QR ($invalidQrCount)") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("filter_logs_invalid_qr")
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (scanLogs.isEmpty()) "No Gate Activity Recorded" else "No Logs Match Selected Filter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (scanLogs.isEmpty())
                                "When security guards scan student QR badges at the gate, verification records will appear here in real time."
                            else
                                "Adjust search query or switch filter to 'All' to view other records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { log ->
                val timeFormatted = SimpleDateFormat("hh:mm:ss a, MMM d", Locale.getDefault()).format(Date(log.timestamp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable { selectedLogForDetail = log }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    when (log.decision) {
                                        GateVerificationDecision.APPROVED -> ApprovedGreenLight
                                        GateVerificationDecision.NOT_APPROVED -> RejectedRedLight
                                        GateVerificationDecision.CARD_INACTIVE -> Color(0xFFFEF3C7)
                                        GateVerificationDecision.STUDENT_NOT_FOUND -> Color(0xFFFFFBEB)
                                        GateVerificationDecision.INVALID_QR -> Color(0xFFF3F4F6)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (log.decision) {
                                    GateVerificationDecision.APPROVED -> Icons.Default.CheckCircle
                                    GateVerificationDecision.NOT_APPROVED -> Icons.Default.Error
                                    GateVerificationDecision.CARD_INACTIVE -> Icons.Default.Block
                                    GateVerificationDecision.STUDENT_NOT_FOUND -> Icons.Default.Help
                                    GateVerificationDecision.INVALID_QR -> Icons.Default.QrCodeScanner
                                },
                                contentDescription = null,
                                tint = when (log.decision) {
                                    GateVerificationDecision.APPROVED -> ApprovedGreen
                                    GateVerificationDecision.NOT_APPROVED -> RejectedRed
                                    GateVerificationDecision.CARD_INACTIVE -> Color(0xFFB45309)
                                    GateVerificationDecision.STUDENT_NOT_FOUND -> Color(0xFFD97706)
                                    GateVerificationDecision.INVALID_QR -> Color(0xFF6B7280)
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.studentName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = log.studentNumber ?: log.studentId ?: "N/A",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                if (log.cardIdentifier != null) {
                                    Text(
                                        text = " • ${log.cardIdentifier}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                            Text(
                                text = "Guard: ${log.guardName} • ${log.gateLocation}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            GateDecisionBadge(decision = log.decision)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = timeFormatted,
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Detail Audit Log Modal
    selectedLogForDetail?.let { log ->
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US)
        AlertDialog(
            onDismissRequest = { selectedLogForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gate Audit Record", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GateDecisionBadge(decision = log.decision, isLarge = true)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Student Name: ${log.studentName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Student Number: ${log.studentNumber ?: "None"}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("Internal UUID: ${log.studentId ?: "None"}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Gray)
                    Text("Card ID: ${log.cardIdentifier ?: "None"}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("Scan Timestamp: ${dateFormat.format(Date(log.timestamp))}", fontSize = 12.sp)
                    Text("Guard: ${log.guardName}", fontSize = 12.sp)
                    Text("Gate: ${log.gateLocation} (${log.deviceIdentifier ?: "Terminal-01"})", fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Verification Reason:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(log.reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (log.isOfflineDecision) Icons.Default.CloudOff else Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (log.isOfflineDecision) Color(0xFFD97706) else ApprovedGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (log.isOfflineDecision) "Evaluated from offline gate cache" else "Live evaluated",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLogForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Gate Activity Logs?") },
            text = { Text("This will permanently remove all recent scan verification records from this local gate terminal.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLogs()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("button_confirm_clear_logs")
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

@Composable
private fun MetricBox(
    title: String,
    value: String,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 0.4.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}
