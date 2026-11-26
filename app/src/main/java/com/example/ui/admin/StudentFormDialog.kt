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
import androidx.compose.material.icons.filled.Crop
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import com.example.model.AccessStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.ui.components.PhotoEditorDialog
import com.example.ui.theme.SchoolPrimary
import com.example.util.ImageStorageHelper
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentFormDialog(
    initialStudent: Student? = null,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    val context = LocalContext.current
    val isEditing = initialStudent != null
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    var firstName by remember { mutableStateOf(initialStudent?.firstName ?: "") }
    var lastName by remember { mutableStateOf(initialStudent?.lastName ?: "") }
    var studentNumber by remember { mutableStateOf(initialStudent?.studentNumber ?: "") }
    var gradeClass by remember { mutableStateOf(initialStudent?.gradeClass ?: "") }
    var dayScholarType by remember {
        mutableStateOf(
            initialStudent?.dayScholarType ?: if (initialStudent?.isDayScholar == false) {
                DayScholarStatus.BOARDER
            } else {
                DayScholarStatus.DAY_SCHOLAR_BUS
            }
        )
    }
    var transportRoute by remember { mutableStateOf(initialStudent?.transportRoute ?: "") }
    var guardianName by remember { mutableStateOf(initialStudent?.guardianName ?: "") }
    var guardianPhone by remember { mutableStateOf(initialStudent?.guardianPhone ?: "") }
    var homeroomTeacher by remember { mutableStateOf(initialStudent?.homeroomTeacher ?: "") }
    var academicYear by remember { mutableStateOf(initialStudent?.academicYear?.ifBlank { currentYear.toString() } ?: currentYear.toString()) }
    var notes by remember { mutableStateOf(initialStudent?.notes ?: "") }

    // Passport Photo state from device
    var photoUrl by remember { mutableStateOf(initialStudent?.photoUrl) }
    var showPhotoEditor by remember { mutableStateOf(false) }
    var pendingPhotoUriOrPath by remember { mutableStateOf<String?>(null) }

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
            pendingPhotoUriOrPath = localPath ?: uri.toString()
            showPhotoEditor = true
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Student Details" else "Register New Student",
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
                            // Enlarged Passport Photo preview (3:4 ratio)
                            Box(
                                modifier = Modifier
                                    .size(width = 84.dp, height = 104.dp)
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
                                        modifier = Modifier.size(width = 84.dp, height = 104.dp),
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
                                    OutlinedButton(
                                        onClick = {
                                            pendingPhotoUriOrPath = photoUrl
                                            showPhotoEditor = true
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("button_edit_passport_photo")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Crop,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit / Crop Photo", fontSize = 12.sp)
                                    }

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
                                                    text = "Photo Saved",
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
                // 2. SIGNED V2 CREDENTIAL NOTICE
                // ==========================================
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
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = SchoolPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEditing) "ACTIVE STUDENT CREDENTIAL" else "CRYPTOGRAPHIC V2 BADGE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SchoolPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isEditing) {
                                    "Print or replace the signed QR ID badge from the student's profile screen."
                                } else {
                                    "A hardware-signed ECDSA P-256 QR credential will be generated automatically if an Issuer Signing Key is enrolled."
                                },
                                fontSize = 10.5.sp,
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
                        placeholder = { Text("LTC-$currentYear-0001") },
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
                        placeholder = { Text("Senior 3-A") },
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

                // Residency / Enrollment Category
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ENROLLMENT CATEGORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SchoolPrimary,
                        letterSpacing = 0.5.sp
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DayScholarStatus.entries.forEach { statusOption ->
                            FilterChip(
                                selected = dayScholarType == statusOption,
                                onClick = { dayScholarType = statusOption },
                                label = { Text(statusOption.label, fontSize = 11.sp) },
                                modifier = Modifier.testTag("chip_residency_${statusOption.name.lowercase()}")
                            )
                        }
                    }
                }

                // Transport Route or Dormitory
                OutlinedTextField(
                    value = transportRoute,
                    onValueChange = { transportRoute = it },
                    label = {
                        Text(
                            if (dayScholarType.isDayScholar) "Day Scholar Route / Transport" else "Dormitory / Boarding House"
                        )
                    },
                    placeholder = {
                        Text(if (dayScholarType.isDayScholar) "e.g. Lira Main Town" else "e.g. Nile Dormitory")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DirectionsBus, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .testTag("input_form_route")
                        .fillMaxWidth()
                )

                // Homeroom Teacher & Academic Year
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = homeroomTeacher,
                        onValueChange = { homeroomTeacher = it },
                        label = { Text("Class Teacher") },
                        leadingIcon = {
                            Icon(Icons.Default.School, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .testTag("input_form_teacher")
                            .weight(1.4f)
                    )

                    OutlinedTextField(
                        value = academicYear,
                        onValueChange = { academicYear = it },
                        label = { Text("Academic Year") },
                        singleLine = true,
                        modifier = Modifier
                            .testTag("input_form_academic_year")
                            .weight(1f)
                    )
                }

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

                    val feeStatus = initialStudent?.feesStatus ?: FeeStatus.CLEARED
                    val resolvedIsDayScholar = dayScholarType.isDayScholar
                    val newStudent = Student(
                        id = initialStudent?.id ?: UUID.randomUUID().toString(),
                        studentNumber = studentNumber.trim().uppercase(),
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        gradeClass = gradeClass.trim().ifBlank { "Unassigned" },
                        isDayScholar = resolvedIsDayScholar,
                        dayScholarType = dayScholarType,
                        transportRoute = transportRoute.trim().ifBlank {
                            if (resolvedIsDayScholar) "Local / General" else "Boarding House"
                        },
                        feesStatus = feeStatus,
                        outstandingAmount = initialStudent?.outstandingAmount ?: 0.0,
                        accessStatus = AccessStatus.evaluate(resolvedIsDayScholar, feeStatus),
                        gender = initialStudent?.gender ?: "Not specified",
                        avatarColorSeed = colorSeed,
                        photoUrl = photoUrl,
                        guardianName = guardianName.trim(),
                        guardianPhone = guardianPhone.trim(),
                        emergencyContact = initialStudent?.emergencyContact ?: "",
                        homeroomTeacher = homeroomTeacher.trim(),
                        academicYear = academicYear.trim().ifBlank { currentYear.toString() },
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

    if (showPhotoEditor && pendingPhotoUriOrPath != null) {
        PhotoEditorDialog(
            imageUriOrPath = pendingPhotoUriOrPath!!,
            onDismiss = {
                showPhotoEditor = false
                pendingPhotoUriOrPath = null
            },
            onPhotoSaved = { savedPath ->
                photoUrl = savedPath
                showPhotoEditor = false
                pendingPhotoUriOrPath = null
            }
        )
    }
}

