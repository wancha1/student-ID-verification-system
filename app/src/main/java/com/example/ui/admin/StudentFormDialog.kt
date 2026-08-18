package com.example.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.RejectedRed

@Composable
fun StudentFormDialog(
    initialStudent: Student? = null,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    val isEditing = initialStudent != null

    var firstName by remember { mutableStateOf(initialStudent?.firstName ?: "") }
    var lastName by remember { mutableStateOf(initialStudent?.lastName ?: "") }
    var studentId by remember { mutableStateOf(initialStudent?.id ?: "STU-2026-00${(11..99).random()}") }
    var gradeClass by remember { mutableStateOf(initialStudent?.gradeClass ?: "Grade 11-A") }
    var transportRoute by remember { mutableStateOf(initialStudent?.transportRoute ?: "Bus Route 4 (Oakville Express)") }
    var guardianName by remember { mutableStateOf(initialStudent?.guardianName ?: "") }
    var guardianPhone by remember { mutableStateOf(initialStudent?.guardianPhone ?: "+1 (555) ") }
    var homeroomTeacher by remember { mutableStateOf(initialStudent?.homeroomTeacher ?: "Mr. Kenneth Ross") }
    var notes by remember { mutableStateOf(initialStudent?.notes ?: "") }
    var feeStatus by remember { mutableStateOf(initialStudent?.feesStatus ?: FeeStatus.CLEARED) }
    var outstandingAmount by remember { mutableStateOf(initialStudent?.outstandingAmount?.toString() ?: "0.00") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Student Details" else "Register New Day Scholar",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ID & Class
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = studentId,
                        onValueChange = { if (!isEditing) studentId = it.uppercase() },
                        enabled = !isEditing,
                        label = { Text("Student ID") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null)
                        },
                        modifier = Modifier
                            .testTag("input_form_student_id")
                            .weight(1f)
                    )

                    OutlinedTextField(
                        value = gradeClass,
                        onValueChange = { gradeClass = it },
                        label = { Text("Grade / Class") },
                        singleLine = true,
                        modifier = Modifier
                            .testTag("input_form_grade_class")
                            .weight(1f)
                    )
                }

                // First & Last Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("First Name") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier
                            .testTag("input_form_first_name")
                            .weight(1f)
                    )

                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Last Name") },
                        singleLine = true,
                        modifier = Modifier
                            .testTag("input_form_last_name")
                            .weight(1f)
                    )
                }

                // Fee Status Selector
                Text(
                    text = "FEES & GATE ACCESS PERMISSION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = feeStatus == FeeStatus.CLEARED,
                        onClick = {
                            feeStatus = FeeStatus.CLEARED
                            outstandingAmount = "0.00"
                        },
                        label = { Text("CLEARED (Access OK)") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("form_fee_cleared")
                    )

                    FilterChip(
                        selected = feeStatus == FeeStatus.OUTSTANDING,
                        onClick = {
                            feeStatus = FeeStatus.OUTSTANDING
                            if (outstandingAmount.toDoubleOrNull() == 0.0) outstandingAmount = "450.00"
                        },
                        label = { Text("OUTSTANDING") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("form_fee_outstanding")
                    )
                }

                if (feeStatus == FeeStatus.OUTSTANDING) {
                    OutlinedTextField(
                        value = outstandingAmount,
                        onValueChange = { outstandingAmount = it },
                        label = { Text("Outstanding Balance ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .testTag("input_form_outstanding_amount")
                            .fillMaxWidth()
                    )
                }

                // Transport Route
                OutlinedTextField(
                    value = transportRoute,
                    onValueChange = { transportRoute = it },
                    label = { Text("Day Scholar Route / Transport") },
                    leadingIcon = {
                        Icon(Icons.Default.DirectionsBus, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .testTag("input_form_route")
                        .fillMaxWidth()
                )

                // Homeroom Teacher
                OutlinedTextField(
                    value = homeroomTeacher,
                    onValueChange = { homeroomTeacher = it },
                    label = { Text("Homeroom Teacher") },
                    leadingIcon = {
                        Icon(Icons.Default.School, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .testTag("input_form_teacher")
                        .fillMaxWidth()
                )

                // Guardian Name & Phone
                OutlinedTextField(
                    value = guardianName,
                    onValueChange = { guardianName = it },
                    label = { Text("Guardian Full Name") },
                    singleLine = true,
                    modifier = Modifier
                        .testTag("input_form_guardian_name")
                        .fillMaxWidth()
                )

                OutlinedTextField(
                    value = guardianPhone,
                    onValueChange = { guardianPhone = it },
                    label = { Text("Guardian Phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .testTag("input_form_guardian_phone")
                        .fillMaxWidth()
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Administrative Notes (Optional)") },
                    maxLines = 2,
                    modifier = Modifier
                        .testTag("input_form_notes")
                        .fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (firstName.isBlank() || lastName.isBlank()) {
                        errorMessage = "First and Last Name are required."
                        return@Button
                    }
                    if (studentId.isBlank()) {
                        errorMessage = "Student ID is required."
                        return@Button
                    }

                    val colorSeed = initialStudent?.avatarColorSeed ?: listOf(
                        0xFF1D4ED8, 0xFF059669, 0xFF7C3AED, 0xFF0891B2, 0xFF4F46E5, 0xFFDB2777
                    ).random()

                    val newStudent = Student(
                        id = studentId.trim(),
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        gradeClass = gradeClass.trim(),
                        isDayScholar = true,
                        dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
                        transportRoute = transportRoute.trim(),
                        feesStatus = feeStatus,
                        outstandingAmount = if (feeStatus == FeeStatus.CLEARED) 0.0 else (outstandingAmount.toDoubleOrNull() ?: 450.0),
                        gender = initialStudent?.gender ?: "Not specified",
                        avatarColorSeed = colorSeed,
                        photoUrl = initialStudent?.photoUrl,
                        guardianName = guardianName.trim().ifBlank { "Parent / Guardian" },
                        guardianPhone = guardianPhone.trim().ifBlank { "+1 (555) 000-0000" },
                        emergencyContact = initialStudent?.emergencyContact ?: "+1 (555) 000-0001",
                        homeroomTeacher = homeroomTeacher.trim().ifBlank { "Unassigned" },
                        notes = notes.trim()
                    )
                    onSave(newStudent)
                },
                modifier = Modifier.testTag("button_form_save_student")
            ) {
                Text(if (isEditing) "Save Changes" else "Register Student")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("button_form_cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}
