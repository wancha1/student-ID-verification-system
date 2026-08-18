package com.example.ui.admin

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
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
    modifier: Modifier = Modifier
) {
    val totalStudents = allStudents.size
    val clearedCount = allStudents.count { it.feesStatus == FeeStatus.CLEARED }
    val outstandingCount = allStudents.count { it.feesStatus == FeeStatus.OUTSTANDING }
    val totalScans = scanLogs.size

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
            // Admin Header
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(GoldAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin",
                                tint = GoldAccent,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BURSAR & RECORDS OFFICE",
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Student Access Management",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Logged in as ${user.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = onViewScanLogs,
                            modifier = Modifier
                                .testTag("button_admin_view_logs")
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Gate Activity Logs",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // KPI Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total
                    KpiCard(
                        title = "TOTAL",
                        count = totalStudents.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    // Cleared
                    KpiCard(
                        title = "CLEARED",
                        count = clearedCount.toString(),
                        color = ApprovedGreenDark,
                        bgColor = ApprovedGreenLight,
                        modifier = Modifier.weight(1f)
                    )
                    // Outstanding
                    KpiCard(
                        title = "OUTSTANDING",
                        count = outstandingCount.toString(),
                        color = RejectedRedDark,
                        bgColor = RejectedRedLight,
                        modifier = Modifier.weight(1f)
                    )
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

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = feeFilter == FeeFilter.ALL,
                        onClick = { onFilterChange(FeeFilter.ALL) },
                        label = { Text("All ($totalStudents)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("filter_all")
                    )
                    FilterChip(
                        selected = feeFilter == FeeFilter.CLEARED,
                        onClick = { onFilterChange(FeeFilter.CLEARED) },
                        label = { Text("Cleared ($clearedCount)") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ApprovedGreenLight,
                            selectedLabelColor = ApprovedGreenDark
                        ),
                        modifier = Modifier.testTag("filter_cleared")
                    )
                    FilterChip(
                        selected = feeFilter == FeeFilter.OUTSTANDING,
                        onClick = { onFilterChange(FeeFilter.OUTSTANDING) },
                        label = { Text("Outstanding ($outstandingCount)") },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RejectedRedLight,
                            selectedLabelColor = RejectedRedDark
                        ),
                        modifier = Modifier.testTag("filter_outstanding")
                    )
                }
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
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No students match your query",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Try adjusting the search filter or register a new student below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredStudents, key = { it.id }) { student ->
                    val isCleared = student.feesStatus == FeeStatus.CLEARED

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

                                // Quick 1-tap Fee Status Switcher Button
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
                                                fontSize = 10.sp,
                                                color = if (isCleared) ApprovedGreenText else RejectedRedText
                                            )
                                            Text(
                                                text = "Tap to flip",
                                                fontSize = 8.sp,
                                                color = if (isCleared) ApprovedGreenDark else RejectedRedDark
                                            )
                                        }
                                    }
                                }
                            }

                            // Secondary Quick Actions Row
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

        // Floating Action Button to Register New Student
        ExtendedFloatingActionButton(
            onClick = { showAddStudentDialog = true },
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
            text = { Text("Register Student", fontWeight = FontWeight.Bold) },
            containerColor = SchoolPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_register_student")
        )
    }

    // Register Student Modal
    if (showAddStudentDialog) {
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
    if (studentToEdit != null) {
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
    if (studentToDelete != null) {
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
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = count,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}
