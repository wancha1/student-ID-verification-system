package com.example.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.ui.components.VisualQrMatrix
import com.example.ui.theme.SchoolPrimary
import com.example.util.ImageStorageHelper
import java.util.UUID

@Composable
fun StudentFormDialog(
    initialStudent: Student? = null,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    val context = LocalContext.current
    val isEditing = initialStudent != null

    var firstName by remember { mutableStateOf(initialStudent?.firstName ?: "") }
    var lastName by remember { mutableStateOf(initialStudent?.lastName ?: "") }
    var studentNumber by remember { mutableStateOf(initialStudent?.studentNumber ?: "OAK-2026-00${(11..99).random()}") }
    var gradeClass by remember { mutableStateOf(initialStudent?.gradeClass ?: "Senior 3-A") }
    var transportRoute by remember { mutableStateOf(initialStudent?.transportRoute ?: "School Bus #4 (Oakville Express)") }
    var guardianName by remember { mutableStateOf(initialStudent?.guardianName ?: "") }
    var guardianPhone by remember { mutableStateOf(initialStudent?.guardianPhone ?: "+256 772 ") }
    var homeroomTeacher by remember { mutableStateOf(initialStudent?.homeroomTeacher ?: "Ms. Lauren Parker") }
    var notes by remember { mutableStateOf(initialStudent?.notes ?: "") }
    var feeStatus by remember { mutableStateOf(initialStudent?.feesStatus ?: FeeStatus.CLEARED) }
    var outstandingAmount by remember { mutableStateOf(initialStudent?.outstandingAmount?.toString() ?: "0.00") }

    // Passport Photo state from device
    var photoUrl by remember { mutableStateOf(initialStudent?.photoUrl) }

    // Unique QR Code token
    val qrToken by remember {
        mutableStateOf(
            initialStudent?.qrToken ?: UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val localPath = ImageStorageHelper.saveImageUriToInternalStorage(context, uri)
            if (localPath != null) {
                photoUrl = localPath
            }
        }
    }

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ==========================================
                // 1. PASSPORT PHOTO SELECTION FROM DEVICE
                // ==========================================
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PASSPORT PHOTO (DEVICE ATTACHMENT)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SchoolPrimary,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Photo preview
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (photoUrl != null) SchoolPrimary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (photoUrl != null) {
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = "Student Passport Photo",
                                        modifier = Modifier.size(76.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddAPhoto,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "No Photo",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Actions
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("button_pick_passport_photo")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (photoUrl == null) "Select from Device" else "Change Photo",
                                        fontSize = 12.sp
                                    )
                                }

                                if (photoUrl != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF059669).copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF059669),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Device Photo Saved",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF059669)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        IconButton(
                                            onClick = {
                                                ImageStorageHelper.deleteInternalPhoto(context, photoUrl)
                                                photoUrl = null
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove photo",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. UNIQUE QR CODE GENERATION & PREVIEW
                // ==========================================
                val effectiveStudentNumber = studentNumber.trim().uppercase()
                val uniqueQrCodePayload = "OAKRIDGE:STU:$effectiveStudentNumber:$qrToken"

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SchoolPrimary.copy(alpha = 0.05f)
                    ),
                    border = BorderStroke(1.dp, SchoolPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                VisualQrMatrix(
                                    payload = uniqueQrCodePayload,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = SchoolPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ASSIGNED UNIQUE QR CODE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SchoolPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uniqueQrCodePayload,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Unique tamper-resistant identifier for Gate, Requirements & Meals.",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Student Number & Class
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = studentNumber,
                        onValueChange = { if (!isEditing) studentNumber = it.uppercase() },
                        enabled = !isEditing,
                        label = { Text("Student Number") },
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
                            if (outstandingAmount.toDoubleOrNull() == 0.0) outstandingAmount = "450000.00"
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
                        label = { Text("Outstanding Balance (UGX)") },
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
                    if (studentNumber.isBlank()) {
                        errorMessage = "Student Number is required."
                        return@Button
                    }

                    val colorSeed = initialStudent?.avatarColorSeed ?: listOf(
                        0xFF1D4ED8, 0xFF059669, 0xFF7C3AED, 0xFF0891B2, 0xFF4F46E5, 0xFFDB2777
                    ).random()

                    val newStudent = Student(
                        id = initialStudent?.id ?: UUID.randomUUID().toString(),
                        studentNumber = studentNumber.trim().uppercase(),
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        gradeClass = gradeClass.trim(),
                        isDayScholar = true,
                        dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
                        transportRoute = transportRoute.trim(),
                        feesStatus = feeStatus,
                        outstandingAmount = if (feeStatus == FeeStatus.CLEARED) 0.0 else (outstandingAmount.toDoubleOrNull() ?: 450000.0),
                        gender = initialStudent?.gender ?: "Not specified",
                        avatarColorSeed = colorSeed,
                        photoUrl = photoUrl,
                        guardianName = guardianName.trim().ifBlank { "Parent / Guardian" },
                        guardianPhone = guardianPhone.trim().ifBlank { "+256 700 000000" },
                        emergencyContact = initialStudent?.emergencyContact ?: "+256 770 000000",
                        homeroomTeacher = homeroomTeacher.trim().ifBlank { "Unassigned" },
                        notes = notes.trim(),
                        qrToken = qrToken,
                        updatedAt = System.currentTimeMillis()
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

