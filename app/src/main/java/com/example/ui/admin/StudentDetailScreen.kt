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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.ui.components.DayScholarBadge
import com.example.ui.components.FeeStatusBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.components.StudentIdCardView
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenDark
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedDark
import com.example.ui.theme.RejectedRedLight

@Composable
fun StudentDetailScreen(
    student: Student,
    onBack: () -> Unit,
    onUpdateFeeStatus: (FeeStatus, Double) -> Unit,
    onUpdateStudentDetails: (Student) -> Unit,
    onDeleteStudent: (String) -> Unit,
    onTestScanAsGuard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var outstandingAmountInput by remember(student.outstandingAmount) {
        mutableStateOf(if (student.outstandingAmount > 0) student.outstandingAmount.toString() else "450.00")
    }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isCleared = student.feesStatus == FeeStatus.CLEARED

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
    ) {
        // Navigation Top Bar
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
                            .testTag("button_back_from_detail")
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to list"
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Student Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = student.id,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Edit & Delete Icons
                Row {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.testTag("button_detail_edit_student")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Student Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("button_detail_delete_student")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Record",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // STUDENT HEADER CARD
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudentAvatar(
                            student = student,
                            size = 72.dp
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.fullName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${student.gradeClass} • ${student.academicYear}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            DayScholarBadge(
                                status = student.dayScholarType,
                                route = student.transportRoute
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }

        // FEES STATUS MANAGEMENT SECTION (CORE ADMIN FEATURE)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .testTag("card_fee_management")
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        if (isCleared) ApprovedGreen.copy(alpha = 0.5f) else RejectedRed.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FEES STATUS CONTROL",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        FeeStatusBadge(status = student.feesStatus)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Toggle fee status below to instantly update security guard gate verification outcome:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2-Button Segmented Status Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // CLEARED OPTION
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCleared) ApprovedGreen else ApprovedGreenLight.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isCleared) 2.dp else 1.dp,
                                ApprovedGreen
                            ),
                            modifier = Modifier
                                .testTag("button_status_cleared")
                                .weight(1f)
                                .clickable {
                                    onUpdateFeeStatus(FeeStatus.CLEARED, 0.0)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Cleared",
                                    tint = if (isCleared) Color.White else ApprovedGreenDark,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "CLEARED",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = if (isCleared) Color.White else ApprovedGreenDark,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Entry Approved",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCleared) Color.White.copy(alpha = 0.9f) else ApprovedGreenDark
                                )
                            }
                        }

                        // OUTSTANDING OPTION
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (!isCleared) RejectedRed else RejectedRedLight.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                if (!isCleared) 2.dp else 1.dp,
                                RejectedRed
                            ),
                            modifier = Modifier
                                .testTag("button_status_outstanding")
                                .weight(1f)
                                .clickable {
                                    val amt = outstandingAmountInput.toDoubleOrNull() ?: 450.0
                                    onUpdateFeeStatus(FeeStatus.OUTSTANDING, amt)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = "Outstanding",
                                    tint = if (!isCleared) Color.White else RejectedRedDark,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "OUTSTANDING",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = if (!isCleared) Color.White else RejectedRedDark,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Entry Not Approved",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (!isCleared) Color.White.copy(alpha = 0.9f) else RejectedRedDark
                                )
                            }
                        }
                    }

                    if (!isCleared) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = outstandingAmountInput,
                                onValueChange = { outstandingAmountInput = it },
                                label = { Text("Outstanding Amount ($)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .testTag("input_outstanding_amount")
                                    .weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val amt = outstandingAmountInput.toDoubleOrNull() ?: 0.0
                                    onUpdateFeeStatus(FeeStatus.OUTSTANDING, amt)
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Propagation Indicator
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCleared) ApprovedGreenLight else RejectedRedLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isCleared) ApprovedGreenDark else RejectedRedDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCleared)
                                    "Live sync active: Security guards scanning QR '${student.id}' will see ENTRY APPROVED."
                                else
                                    "Live sync active: Security guards scanning QR '${student.id}' will see ENTRY NOT APPROVED.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isCleared) ApprovedGreenDark else RejectedRedDark
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }

        // PRINTABLE DIGITAL ID CARD WITH GENERATED QR CODE
        item {
            Text(
                text = "STUDENT DIGITAL ID & BARCODE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            StudentIdCardView(
                student = student,
                showQrCode = true
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // QUICK ACTION: TEST IN SECURITY GUARD SCANNER
        item {
            Button(
                onClick = { onTestScanAsGuard(student.id) },
                modifier = Modifier
                    .testTag("button_test_as_guard")
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Test QR Scan in Security Guard Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }

        // GUARDIAN & HOMEROOM DETAILS
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Contact & School Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DetailRow(
                        label = "Guardian Name",
                        value = student.guardianName
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DetailRow(
                        label = "Guardian Phone",
                        value = student.guardianPhone
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DetailRow(
                        label = "Homeroom Teacher",
                        value = student.homeroomTeacher
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DetailRow(
                        label = "Transport Route",
                        value = student.transportRoute
                    )

                    if (student.notes.isNotBlank()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        DetailRow(
                            label = "Admin Notes",
                            value = student.notes
                        )
                    }
                }
            }
        }
    }

    // Edit Modal
    if (showEditDialog) {
        StudentFormDialog(
            initialStudent = student,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                onUpdateStudentDetails(updated)
                showEditDialog = false
            }
        )
    }

    // Delete Confirmation Modal
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Student Record?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${student.fullName} (${student.id})? Gate verification will be denied for this ID.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStudent(student.id)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("button_detail_confirm_delete")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.testTag("button_detail_cancel_delete")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
