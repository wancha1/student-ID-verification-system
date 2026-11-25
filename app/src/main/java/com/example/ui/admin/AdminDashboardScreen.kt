package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuthUser
import com.example.model.FeeStatus
import com.example.model.ScanLog
import com.example.model.StaffPermission
import com.example.model.Student
import com.example.model.SyncInfo
import com.example.model.SyncStatus
import com.example.model.UserRole
import com.example.ui.FeeFilter
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.GoldBright
import com.example.ui.theme.LocalRoleTheme
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.Navy900
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import com.example.ui.theme.roleGlassBackground

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
    syncInfo: SyncInfo? = null,
    onTriggerSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalStudents = allStudents.size
    val clearedCount = allStudents.count { it.feesStatus == FeeStatus.CLEARED }
    val roleTheme = LocalRoleTheme.current

    var showAddStudentDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .roleGlassBackground(roleTheme.primaryColor)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 14.dp,
                bottom = if (user.role == UserRole.ADMINISTRATOR) 96.dp else 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Unified Station Header Card (with optional inline sync strip)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = Navy900,
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (syncInfo == null) 18.dp else 0.dp,
                                bottomEnd = if (syncInfo == null) 18.dp else 0.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = roleTheme.icon,
                                            contentDescription = roleTheme.departmentTagline,
                                            tint = GoldBright,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = roleTheme.stationBadge,
                                        color = GoldBright,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = when (user.role) {
                                            UserRole.ADMINISTRATOR -> "Student Registry"
                                            else -> "Student Roster (Read-Only)"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Officer: ${user.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        syncInfo?.let { info ->
                            val syncColor = when (info.status) {
                                SyncStatus.SYNCED -> ApprovedGreen
                                SyncStatus.SYNC_FAILED -> RejectedRed
                                SyncStatus.OFFLINE, SyncStatus.NEEDS_SYNC -> PendingAmber
                                SyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
                            }
                            val syncIcon = when (info.status) {
                                SyncStatus.SYNCED -> Icons.Default.CloudDone
                                SyncStatus.OFFLINE -> Icons.Default.CloudOff
                                SyncStatus.SYNC_FAILED -> Icons.Default.SyncProblem
                                SyncStatus.SYNCING, SyncStatus.NEEDS_SYNC -> Icons.Default.CloudSync
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = syncIcon,
                                    contentDescription = null,
                                    tint = syncColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when (info.status) {
                                            SyncStatus.SYNCED -> "Cloud sync up to date"
                                            SyncStatus.SYNCING -> "Synchronizing…"
                                            SyncStatus.NEEDS_SYNC -> "${info.pendingLogsCount} change(s) pending"
                                            SyncStatus.OFFLINE -> "Device offline"
                                            SyncStatus.SYNC_FAILED -> "Sync warning"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = syncColor,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    info.errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                                        Text(
                                            text = err,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (info.status != SyncStatus.SYNCING) {
                                    TextButton(
                                        onClick = onTriggerSync,
                                        enabled = info.isOnline,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Sync")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Concise 2-Metric Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "REGISTERED",
                        count = totalStudents.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (user.role == UserRole.ADMINISTRATOR) {
                        KpiCard(
                            title = "GATE SCANS",
                            count = scanLogs.size.toString(),
                            color = ApprovedGreen,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        KpiCard(
                            title = "FEES CLEARED",
                            count = clearedCount.toString(),
                            color = ApprovedGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Gate & Fee Pulse (Administrator)
            if (user.role == UserRole.ADMINISTRATOR) {
                item {
                    GateAnalyticsCard(
                        students = allStudents,
                        scanLogs = scanLogs
                    )
                }
            }

            // 4. Search + One-Tap Fee Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search name, student no., or class") },
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
                                        contentDescription = "Clear search"
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeeFilterChip(
                            label = "All ($totalStudents)",
                            selected = feeFilter == FeeFilter.ALL,
                            onClick = { onFilterChange(FeeFilter.ALL) },
                            testTag = "filter_students_all"
                        )
                        FeeFilterChip(
                            label = "Cleared ($clearedCount)",
                            selected = feeFilter == FeeFilter.CLEARED,
                            onClick = { onFilterChange(FeeFilter.CLEARED) },
                            selectedContainer = ApprovedGreenLight,
                            selectedLabel = ApprovedGreenText,
                            testTag = "filter_students_cleared"
                        )
                        FeeFilterChip(
                            label = "Owing (${(totalStudents - clearedCount).coerceAtLeast(0)})",
                            selected = feeFilter == FeeFilter.OUTSTANDING,
                            onClick = { onFilterChange(FeeFilter.OUTSTANDING) },
                            selectedContainer = RejectedRedLight,
                            selectedLabel = RejectedRedText,
                            testTag = "filter_students_outstanding"
                        )
                    }
                }
            }

            // 5. Roster Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Student Directory (${filteredStudents.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (user.role == UserRole.ADMINISTRATOR) "Tap row to manage" else "Tap row to view",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 6. Student Directory Rows
            if (filteredStudents.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (allStudents.isEmpty()) Icons.Default.PersonAdd else Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (allStudents.isEmpty()) "No student records yet" else "No matching students",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (allStudents.isEmpty()) {
                                    if (user.role == UserRole.ADMINISTRATOR) {
                                        "Tap Register Student below to add your first student."
                                    } else {
                                        "Sync with server to load student records."
                                    }
                                } else {
                                    "Try clearing your search or switching fee filters."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredStudents, key = { it.id }) { student ->
                    val isCleared = student.feesStatus == FeeStatus.CLEARED
                    Card(
                        onClick = { onSelectStudent(student.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (isCleared) MaterialTheme.colorScheme.outlineVariant else RejectedRed.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .testTag("student_row_${student.id}")
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StudentAvatar(student = student, size = 46.dp)

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${student.gradeClass} • ${student.studentNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = MonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            if (user.hasPermission(StaffPermission.MANAGE_FEES)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCleared) ApprovedGreenLight else RejectedRedLight,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCleared) ApprovedGreen else RejectedRed
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onQuickToggleFeeStatus(student.id, student.feesStatus)
                                        }
                                        .testTag("toggle_fee_${student.id}")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isCleared) "CLEARED" else "OWING",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCleared) ApprovedGreenText else RejectedRedText
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCleared) ApprovedGreenLight else RejectedRedLight
                                ) {
                                    Text(
                                        text = if (isCleared) "CLEARED" else "OWING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCleared) ApprovedGreenText else RejectedRedText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Open student profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Thumb-friendly primary creation action (Administrator only)
        if (user.role == UserRole.ADMINISTRATOR) {
            ExtendedFloatingActionButton(
                onClick = { showAddStudentDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Register Student", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("fab_register_student")
            )
        }
    }

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
}

@Composable
private fun FeeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    selectedContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedLabel: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedContainer,
            selectedLabelColor = selectedLabel
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
    )
}

@Composable
private fun KpiCard(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.6.sp
            )
            Text(
                text = count,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
