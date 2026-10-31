package com.example.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.model.UserRole
import com.example.ui.theme.LocalRoleTheme
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.model.AuthUser
import com.example.model.FeeStatus
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.SyncInfo
import com.example.model.SyncStatus
import com.example.ui.FeeFilter
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenDark
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedDark
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import com.example.ui.theme.SchoolPrimary

@Composable
fun AdminDashboardScreen(
    user: AuthUser,
    allStudents: List<Student>,
    filteredStudents: List<Student>,
    scanLogs: List<ScanLog>,
    searchQuery: String,
    feeFilter: FeeFilter,
    onSearchChange: (String) -> Unit,
    onFilterChange: (FeeFilter) -> Unit,
    onSelectStudent: (String) -> Unit,
    onQuickToggleFeeStatus: (studentId: String, currentStatus: FeeStatus) -> Unit,
    onAddStudent: (Student) -> Unit,
    onDeleteStudent: (String) -> Unit,
    onEditStudent: (Student) -> Unit,
    onViewScanLogs: () -> Unit,
    onViewGuardianAlerts: () -> Unit = {},
    onViewExeatPasses: () -> Unit = {},
    onViewBatchPrint: () -> Unit = {},
    onExportLogsCsv: () -> Unit = {},
    syncInfo: SyncInfo? = null,
    onTriggerSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalStudents = allStudents.size
    val totalScans = scanLogs.size

    val roleTheme = LocalRoleTheme.current
    val roleColor = roleTheme.primaryColor

    var showAddStudentDialog by remember { mutableStateOf(false) }
    var studentToEdit by remember { mutableStateOf<Student?>(null) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            // Admin / Staff Dynamic Role Header
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1726)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, GoldAccent.copy(alpha = 0.58f), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.2.dp, GoldAccent.copy(alpha = 0.6f))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = roleTheme.icon,
                                    contentDescription = roleTheme.departmentTagline,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = roleTheme.departmentTagline,
                                    color = GoldAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = roleTheme.containerColor
                                ) {
                                    Text(
                                        text = roleTheme.stationBadge,
                                        color = roleTheme.badgeTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = when (user.role) {
                                    UserRole.BURSAR_FINANCE -> "Tuition Roster & Fee Ledger"
                                    UserRole.HEAD_TEACHER_MANAGEMENT -> "Institutional Oversight & Analytics"
                                    UserRole.TEACHERS -> "Classroom Attendance & Roll Call"
                                    else -> "Student Access Management"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Authenticated Officer: ${user.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }

                        if (user.role == UserRole.ADMINISTRATOR) {
                            IconButton(
                                onClick = onViewScanLogs,
                                modifier = Modifier
                                    .testTag("button_admin_view_logs")
                                    .size(42.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Gate Activity Logs",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            syncInfo?.let { info ->
                item {
                    val syncColor = when (info.status) {
                        SyncStatus.SYNCED -> ApprovedGreen
                        SyncStatus.SYNC_FAILED -> RejectedRed
                        SyncStatus.OFFLINE -> Color(0xFFB7791F)
                        SyncStatus.SYNCING -> SchoolPrimary
                        SyncStatus.NEEDS_SYNC -> Color(0xFFB7791F)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = syncColor.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, syncColor.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when (info.status) {
                                            SyncStatus.SYNCED -> "Cloud sync is up to date"
                                            SyncStatus.SYNCING -> "Synchronizing with school database…"
                                            SyncStatus.NEEDS_SYNC -> "Changes waiting to sync"
                                            SyncStatus.OFFLINE -> "Device is offline"
                                            SyncStatus.SYNC_FAILED -> "Cloud synchronization failed"
                                        },
                                        color = syncColor,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (info.pendingLogsCount == 0) "No pending local changes"
                                        else "${info.pendingLogsCount} pending local change(s)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (info.status != SyncStatus.SYNCING) {
                                    TextButton(onClick = onTriggerSync, enabled = info.isOnline) {
                                        Text("Retry sync")
                                    }
                                }
                            }
                            info.errorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Version 1 registry KPI: student enrolment only.
            item {
                KpiCard(
                    title = "REGISTERED STUDENTS",
                    count = totalStudents.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Gate analytics and operational shortcuts are restricted to the Administrator.
            if (user.role == UserRole.ADMINISTRATOR) {
                item {
                    GateAnalyticsCard(
                        students = allStudents,
                        scanLogs = scanLogs
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "QUICK ACCESS",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.6.sp,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HubButton(
                                icon = Icons.Default.History,
                                title = "Gate Logs",
                                onClick = onViewScanLogs,
                                testTag = "button_hub_gate_logs",
                                modifier = Modifier.weight(1f)
                            )
                            HubButton(
                                icon = Icons.Default.Notifications,
                                title = "SMS Alerts",
                                onClick = onViewGuardianAlerts,
                                testTag = "button_hub_sms_alerts",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HubButton(
                                icon = Icons.Default.ConfirmationNumber,
                                title = "Exeat Passes",
                                onClick = onViewExeatPasses,
                                testTag = "button_hub_exeat_passes",
                                modifier = Modifier.weight(1f)
                            )
                            HubButton(
                                icon = Icons.Default.Badge,
                                title = "Print Badges",
                                onClick = onViewBatchPrint,
                                testTag = "button_hub_print_badges",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // Search Field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search by student name, ID, or class") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .testTag("input_admin_search_students")
                        .fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Text(
                    text = "STUDENT DIRECTORY",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Student List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STUDENT ROSTER (${filteredStudents.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Tap row for ID badge & details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Students List
            if (filteredStudents.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (allStudents.isEmpty()) Icons.Default.PersonAdd else Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (allStudents.isEmpty()) "No student records registered yet" else "No students match your query",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (allStudents.isEmpty())
                                    "Tap '+ Register Student' to enroll your first student into the school directory."
                                else
                                    "Try adjusting the search filter or register a new student below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredStudents, key = { it.id }) { student ->
                    Card(
                        onClick = { onSelectStudent(student.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .testTag("student_row_${student.id}")
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .border(
                                1.dp,
                                if (isCleared) MaterialTheme.colorScheme.outlineVariant else RejectedRed.copy(alpha = 0.4f),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudentAvatar(
                                    student = student,
                                    size = 52.dp
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = student.fullName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${student.gradeClass} • ${student.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = student.transportRoute,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

                                // Fee changes are restricted to the Bursar role.
                                if (user.role == UserRole.BURSAR_FINANCE) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCleared) ApprovedGreenLight else RejectedRedLight,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isCleared) ApprovedGreen else RejectedRed
                                    ),
                                    modifier = Modifier
                                        .testTag("toggle_fee_${student.id}")
                                        .clickable {
                                            onQuickToggleFeeStatus(student.id, student.feesStatus)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = "Toggle Status",
                                            tint = if (isCleared) ApprovedGreenDark else RejectedRedDark,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = if (isCleared) "CLEARED" else "OUTSTANDING",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = if (isCleared) ApprovedGreenText else RejectedRedText
                                            )
                                            Text(
                                                text = "Tap to flip",
                                                fontSize = 11.sp,
                                                color = if (isCleared) ApprovedGreenDark else RejectedRedDark
                                            )
                                        }
                                    }
                                }
                            }

                                }

                            // Registry actions are restricted to the Administrator role.
                            if (user.role == UserRole.ADMINISTRATOR) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { studentToEdit = student },
                                    modifier = Modifier.testTag("button_edit_${student.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", fontSize = 12.sp)
                                }

                                TextButton(
                                    onClick = { studentToDelete = student },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.testTag("button_delete_${student.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Only the Administrator can register students.
        if (user.role == UserRole.ADMINISTRATOR) {
        ExtendedFloatingActionButton(
            onClick = { showAddStudentDialog = true },
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
            text = { Text("Register Student", fontWeight = FontWeight.Bold) },
            containerColor = roleTheme.primaryColor,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_register_student")
        )
        }
    }

    // Register Student Modal
    if (user.role == UserRole.ADMINISTRATOR && showAddStudentDialog) {
        StudentFormDialog(
            initialStudent = null,
            onDismiss = { showAddStudentDialog = false },
            onSave = { newStudent ->
                onAddStudent(newStudent)
                showAddStudentDialog = false
            }
        )
    }

    // Edit Student Modal
    if (user.role == UserRole.ADMINISTRATOR && studentToEdit != null) {
        StudentFormDialog(
            initialStudent = studentToEdit,
            onDismiss = { studentToEdit = null },
            onSave = { updatedStudent ->
                onEditStudent(updatedStudent)
                studentToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (user.role == UserRole.ADMINISTRATOR && studentToDelete != null) {
        val s = studentToDelete!!
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            title = { Text("Delete Student Record?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to remove ${s.fullName} (${s.id}) from the day scholar roster? Gate verification for this student ID will be denied.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStudent(s.id)
                        studentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("button_confirm_delete_student")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { studentToDelete = null },
                    modifier = Modifier.testTag("button_cancel_delete_student")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    count: String,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = count,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
private fun HubButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String = "",
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
