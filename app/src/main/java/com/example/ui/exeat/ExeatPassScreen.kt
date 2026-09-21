package com.example.ui.exeat

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.model.ExeatPass
import com.example.model.ExeatReason
import com.example.model.ExeatStatus
import com.example.model.Student
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.SchoolPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExeatPassScreen(
    exeatPasses: List<ExeatPass>,
    students: List<Student>,
    onBack: () -> Unit,
    onIssueExeatPass: (ExeatPass) -> Unit,
    onMarkExeatUsed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ACTIVE") }
    var showIssueDialog by remember { mutableStateOf(false) }
    var selectedPassForDetail by remember { mutableStateOf<ExeatPass?>(null) }

    val filteredPasses = remember(exeatPasses, selectedFilter) {
        when (selectedFilter) {
            "ACTIVE" -> exeatPasses.filter { it.status == ExeatStatus.ACTIVE && !it.isExpired }
            "USED" -> exeatPasses.filter { it.status == ExeatStatus.USED }
            "EXPIRED" -> exeatPasses.filter { it.isExpired || it.status == ExeatStatus.EXPIRED }
            else -> exeatPasses
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gate Exeat & Leave Passes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${exeatPasses.count { it.status == ExeatStatus.ACTIVE }} active passes authorized",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("button_back_exeat")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showIssueDialog = true },
                containerColor = SchoolPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_issue_exeat")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Issue Exeat Pass", fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier.testTag("screen_exeat_passes")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ACTIVE",
                    onClick = { selectedFilter = "ACTIVE" },
                    label = { Text("Active Passes (${exeatPasses.count { it.status == ExeatStatus.ACTIVE && !it.isExpired }})") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("chip_exeat_active")
                )
                FilterChip(
                    selected = selectedFilter == "USED",
                    onClick = { selectedFilter = "USED" },
                    label = { Text("Used / Departed") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("chip_exeat_used")
                )
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${exeatPasses.size})") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("chip_exeat_all")
                )
            }

            if (filteredPasses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Exeat Passes Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Issue an official exeat pass for boarding students or special day-scholar leaves.",
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
                        .testTag("lazy_column_exeat_passes"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = filteredPasses,
                        key = { it.id }
                    ) { pass ->
                        ExeatPassItemCard(
                            pass = pass,
                            onClick = { selectedPassForDetail = pass },
                            onMarkUsed = { onMarkExeatUsed(pass.id) }
                        )
                    }
                }
            }
        }
    }

    // Exeat Detail Dialog
    selectedPassForDetail?.let { pass ->
        val fullDateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy • hh:mm a", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { selectedPassForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ConfirmationNumber, contentDescription = null, tint = GoldAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Official Exeat Pass: ${pass.passNumber}", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Student: ${pass.studentName} (${pass.studentNumber})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Class: ${pass.gradeClass}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reason: ${pass.reason.name.replace("_", " ")}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = SchoolPrimary
                    )
                    Text(
                        text = "Destination: ${pass.destination}",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Authorized by: ${pass.issuedBy}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Valid Until: ${fullDateFormat.format(Date(pass.validUntil))}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pass.isExpired) RejectedRed else ApprovedGreen
                    )
                    Text(
                        text = "Guardian Contact: ${pass.guardianContact} (Confirmed)",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pass Verification Payload: ${pass.qrPayload}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                if (pass.status == ExeatStatus.ACTIVE && !pass.isExpired) {
                    Button(
                        onClick = {
                            onMarkExeatUsed(pass.id)
                            selectedPassForDetail = null
                        }
                    ) {
                        Text("Approve Gate Departure")
                    }
                } else {
                    TextButton(onClick = { selectedPassForDetail = null }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (pass.status == ExeatStatus.ACTIVE && !pass.isExpired) {
                    TextButton(onClick = { selectedPassForDetail = null }) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }

    // Issue New Exeat Pass Dialog
    if (showIssueDialog) {
        var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
        var expandedStudentDropdown by remember { mutableStateOf(false) }
        var selectedReason by remember { mutableStateOf(ExeatReason.MEDICAL_CLINIC) }
        var destinationInput by remember { mutableStateOf("Entebbe Grade B Hospital") }
        var durationHours by remember { mutableStateOf("6") }
        var guardianConfirmed by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showIssueDialog = false },
            title = { Text("Issue Student Exeat Pass", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Student Selector Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedStudentDropdown,
                        onExpandedChange = { expandedStudentDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = selectedStudent?.fullName ?: "Select Student",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Student") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStudentDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedStudentDropdown,
                            onDismissRequest = { expandedStudentDropdown = false }
                        ) {
                            students.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.fullName} (${s.studentNumber}) - ${s.gradeClass}") },
                                    onClick = {
                                        selectedStudent = s
                                        expandedStudentDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Reason Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExeatReason.values().take(3).forEach { r ->
                            FilterChip(
                                selected = selectedReason == r,
                                onClick = { selectedReason = r },
                                label = { Text(r.name.split("_").first(), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = destinationInput,
                        onValueChange = { destinationInput = it },
                        label = { Text("Destination / Clinic") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = durationHours,
                        onValueChange = { durationHours = it },
                        label = { Text("Validity Duration (Hours)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Guardian Approval Verified", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = guardianConfirmed,
                            onCheckedChange = { guardianConfirmed = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val stu = selectedStudent
                        if (stu != null) {
                            val hours = durationHours.toLongOrNull() ?: 6L
                            val newPass = ExeatPass(
                                studentId = stu.id,
                                studentNumber = stu.studentNumber,
                                studentName = stu.fullName,
                                gradeClass = stu.gradeClass,
                                reason = selectedReason,
                                destination = destinationInput,
                                validFrom = System.currentTimeMillis(),
                                validUntil = System.currentTimeMillis() + (hours * 3600 * 1000L),
                                status = ExeatStatus.ACTIVE,
                                guardianContact = stu.guardianPhone,
                                guardianApprovalConfirmed = guardianConfirmed
                            )
                            onIssueExeatPass(newPass)
                            showIssueDialog = false
                        }
                    },
                    modifier = Modifier.testTag("button_confirm_issue_exeat")
                ) {
                    Text("Issue Pass")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ExeatPassItemCard(
    pass: ExeatPass,
    onClick: () -> Unit,
    onMarkUsed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val isAvailable = pass.status == ExeatStatus.ACTIVE && !pass.isExpired

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (isAvailable) GoldAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(14.dp)
            )
            .testTag("exeat_item_${pass.id}")
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
                    .background(if (isAvailable) GoldAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (pass.reason) {
                        ExeatReason.MEDICAL_CLINIC -> Icons.Default.MedicalServices
                        else -> Icons.Default.ConfirmationNumber
                    },
                    contentDescription = null,
                    tint = if (isAvailable) GoldAccent else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pass.studentName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (pass.status) {
                            ExeatStatus.ACTIVE -> if (pass.isExpired) RejectedRed.copy(alpha = 0.15f) else ApprovedGreenLight
                            ExeatStatus.USED -> MaterialTheme.colorScheme.surfaceVariant
                            ExeatStatus.EXPIRED -> RejectedRed.copy(alpha = 0.15f)
                            ExeatStatus.CANCELLED -> RejectedRed.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = if (pass.status == ExeatStatus.ACTIVE && pass.isExpired) "EXPIRED" else pass.status.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (isAvailable) ApprovedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${pass.passNumber} • ${pass.reason.name.replace("_", " ")}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Destination: ${pass.destination} • Valid until ${timeFormat.format(Date(pass.validUntil))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
