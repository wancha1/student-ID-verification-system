package com.example.ui.admin

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Card as StudentCard
import com.example.model.CardStatus
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.ui.components.CardStatusBadge
import com.example.ui.components.DayScholarBadge
import com.example.ui.components.DigitalIdCardDialog
import com.example.ui.components.FeeStatusBadge
import com.example.ui.components.PhotoEditorDialog
import com.example.ui.components.PrintableStudentIdCard
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenDark
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.Navy900
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberLight
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedDark
import com.example.ui.theme.RejectedRedLight
import com.example.util.ImageStorageHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    student: Student,
    cards: List<StudentCard> = emptyList(),
    onBack: () -> Unit,
    onUpdateFeeStatus: (FeeStatus, Double) -> Unit,
    canManageStudentRecords: Boolean = true,
    canManageFees: Boolean = false,
    onUpdateStudentDetails: (Student) -> Unit,
    onDeleteStudent: (String) -> Unit,
    onReportCardLost: (studentId: String, cardId: String, reason: String) -> Unit = { _, _, _ -> },
    onIssueReplacementCard: (studentId: String, oldCardId: String, reason: String) -> Unit = { _, _, _ -> },
    onDeactivateCard: (studentId: String, cardId: String, reason: String) -> Unit = { _, _, _ -> },
    onIssueNewCard: (studentId: String, reason: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDigitalCardDialog by remember { mutableStateOf(false) }
    var cardToReportLost by remember { mutableStateOf<StudentCard?>(null) }
    var cardToReplace by remember { mutableStateOf<StudentCard?>(null) }
    var cardToDeactivate by remember { mutableStateOf<StudentCard?>(null) }
    var showIssueNewCardDialog by remember { mutableStateOf(false) }
    var showPhotoEditor by remember { mutableStateOf(false) }
    var pendingPhotoUriOrPath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val localPath = ImageStorageHelper.saveImageUriToInternalStorage(context, uri)
            pendingPhotoUriOrPath = localPath ?: uri.toString()
            showPhotoEditor = true
        }
    }

    var outstandingAmountInput by remember(student.outstandingAmount) {
        mutableStateOf(if (student.outstandingAmount > 0) student.outstandingAmount.toString() else "450000.00")
    }

    val isCleared = student.feesStatus == FeeStatus.CLEARED
    val activeCard = cards.firstOrNull { it.status == CardStatus.ACTIVE }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("button_back_from_detail")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to list"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Student Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = student.studentNumber,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFontFamily,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (canManageStudentRecords) {
                        IconButton(
                            onClick = { showDigitalCardDialog = true },
                            modifier = Modifier.testTag("button_detail_print_card")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Print ID Card"
                            )
                        }
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("button_detail_edit_student")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Student Info"
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("button_detail_delete_student")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Record",
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Identity Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            StudentAvatar(student = student, size = 84.dp)
                            if (canManageStudentRecords) {
                                IconButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        .testTag("button_change_student_photo")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Edit Student Photo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = student.fullName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${student.gradeClass} • ${student.academicYear}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            DayScholarBadge(
                                status = student.dayScholarType,
                                route = student.transportRoute
                            )
                            if (!canManageFees) {
                                FeeStatusBadge(feeStatus = student.feesStatus)
                            }
                        }
                    }
                }
            }

            // 2. Physical ID Card Lifecycle (Administrator)
            if (canManageStudentRecords) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .testTag("card_id_card_management")
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "ID Card Lifecycle",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                CardStatusBadge(status = activeCard?.status ?: CardStatus.DEACTIVATED)
                            }

                            Spacer(Modifier.height(12.dp))

                            if (activeCard != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "CARD IDENTIFIER",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = activeCard.cardIdentifier,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontFamily = MonoFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "ISSUED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = dateFormat.format(Date(activeCard.issueDate)),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "QR: ${activeCard.qrPayload}",
                                            fontFamily = MonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { cardToReportLost = activeCard },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("button_report_lost"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RejectedRed)
                                    ) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Report Lost", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = { cardToReplace = activeCard },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("button_issue_replacement"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Replace", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showDigitalCardDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("button_view_digital_badge"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Print Badge", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { cardToDeactivate = activeCard },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("button_deactivate_card"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Deactivate", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PendingAmberLight,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = PendingAmber,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "No Active ID Card",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color(0xFF78350F)
                                            )
                                            Text(
                                                text = "Gate scans will deny entry until a card is issued.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF92400E)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = { showIssueNewCardDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("button_issue_new_card"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.AddCard, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Issue Physical ID Card", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (cards.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(10.dp))

                                Text(
                                    text = "Issuance History (${cards.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))

                                cards.forEach { c ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = c.cardIdentifier,
                                                    fontFamily = MonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    text = "${dateFormat.format(Date(c.issueDate))}${c.reason?.let { " • $it" } ?: ""}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            CardStatusBadge(status = c.status)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Fees Status Control (Bursar / Authorized Admin)
            if (canManageFees) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.5.dp,
                            if (isCleared) ApprovedGreen.copy(alpha = 0.45f) else RejectedRed.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .testTag("card_fee_management")
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fees Status Control",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                FeeStatusBadge(feeStatus = student.feesStatus)
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCleared) ApprovedGreen else ApprovedGreenLight.copy(alpha = 0.55f),
                                    border = BorderStroke(if (isCleared) 2.dp else 1.dp, ApprovedGreen),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 68.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onUpdateFeeStatus(FeeStatus.CLEARED, 0.0) }
                                        .testTag("button_status_cleared")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Cleared",
                                            tint = if (isCleared) Color.White else ApprovedGreenDark,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "CLEARED",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isCleared) Color.White else ApprovedGreenDark
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (!isCleared) RejectedRed else RejectedRedLight.copy(alpha = 0.55f),
                                    border = BorderStroke(if (!isCleared) 2.dp else 1.dp, RejectedRed),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 68.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            val amt = outstandingAmountInput.toDoubleOrNull() ?: 450000.0
                                            onUpdateFeeStatus(FeeStatus.OUTSTANDING, amt)
                                        }
                                        .testTag("button_status_outstanding")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = "Outstanding",
                                            tint = if (!isCleared) Color.White else RejectedRedDark,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "OUTSTANDING",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (!isCleared) Color.White else RejectedRedDark
                                        )
                                    }
                                }
                            }

                            if (!isCleared) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = outstandingAmountInput,
                                        onValueChange = { outstandingAmountInput = it },
                                        label = { Text("Balance (UGX)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .testTag("input_outstanding_amount")
                                            .weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val amt = outstandingAmountInput.toDoubleOrNull() ?: 0.0
                                            onUpdateFeeStatus(FeeStatus.OUTSTANDING, amt)
                                        },
                                        modifier = Modifier.height(56.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Save")
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isCleared) {
                                            "Gate verification will evaluate to ENTRY APPROVED."
                                        } else {
                                            "Gate verification will evaluate to ENTRY NOT APPROVED."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCleared) ApprovedGreenDark else RejectedRedDark
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Digital ID Preview (Administrator)
            if (canManageStudentRecords) {
                item {
                    PrintableStudentIdCard(
                        student = student,
                        card = activeCard
                    )
                }
            }

            // 5. Contact & School Details
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Contact & School Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))

                        DetailRow(label = "Internal ID", value = student.id, mono = true)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        DetailRow(label = "Student Number", value = student.studentNumber, mono = true)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        DetailRow(label = "Guardian Name", value = student.guardianName)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        DetailRow(label = "Guardian Phone", value = student.guardianPhone, mono = true)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        DetailRow(label = "Homeroom Teacher", value = student.homeroomTeacher)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        DetailRow(label = "Transport Route", value = student.transportRoute)

                        if (student.notes.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            DetailRow(label = "Admin Notes", value = student.notes)
                        }
                    }
                }
            }
        }
    }

    if (showDigitalCardDialog) {
        DigitalIdCardDialog(
            student = student,
            card = activeCard,
            onDismiss = { showDigitalCardDialog = false }
        )
    }

    cardToReportLost?.let { card ->
        var reasonText by remember { mutableStateOf("Student reported card lost on campus") }
        AlertDialog(
            onDismissRequest = { cardToReportLost = null },
            title = { Text("Report Card Lost", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Reporting ${card.cardIdentifier} as LOST will immediately deny gate entry with this card.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Reason / Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportCardLost(student.id, card.id, reasonText)
                        cardToReportLost = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RejectedRed),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("button_confirm_report_lost")
                ) {
                    Text("Confirm Lost")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { cardToReportLost = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    cardToReplace?.let { card ->
        var reasonText by remember { mutableStateOf("Replaced lost/damaged badge") }
        AlertDialog(
            onDismissRequest = { cardToReplace = null },
            title = { Text("Issue Replacement Card", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This will deactivate card ${card.cardIdentifier} and issue a newly incremented active card.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Reason for Replacement") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onIssueReplacementCard(student.id, card.id, reasonText)
                        cardToReplace = null
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("button_confirm_replace_card")
                ) {
                    Text("Issue Replacement")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { cardToReplace = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    cardToDeactivate?.let { card ->
        var reasonText by remember { mutableStateOf("Deactivated by Administrator") }
        AlertDialog(
            onDismissRequest = { cardToDeactivate = null },
            title = { Text("Deactivate Card?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Deactivating ${card.cardIdentifier} will block gate scans with this card.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Deactivation Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeactivateCard(student.id, card.id, reasonText)
                        cardToDeactivate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Deactivate")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { cardToDeactivate = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showIssueNewCardDialog) {
        var reasonText by remember { mutableStateOf("New card issuance") }
        AlertDialog(
            onDismissRequest = { showIssueNewCardDialog = false },
            title = { Text("Issue Physical ID Card", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Generate and activate a new physical ID badge for ${student.fullName} (${student.studentNumber}).")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Issuance Reason / Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onIssueNewCard(student.id, reasonText)
                        showIssueNewCardDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Issue Card")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showIssueNewCardDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Student Record?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete ${student.fullName} (${student.studentNumber})? Gate verification will be denied for this ID.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStudent(student.id)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("button_detail_confirm_delete")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("button_detail_cancel_delete")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPhotoEditor && pendingPhotoUriOrPath != null) {
        PhotoEditorDialog(
            imageUriOrPath = pendingPhotoUriOrPath!!,
            onDismiss = {
                showPhotoEditor = false
                pendingPhotoUriOrPath = null
            },
            onPhotoSaved = { savedPath ->
                onUpdateStudentDetails(
                    student.copy(
                        photoUrl = savedPath,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                showPhotoEditor = false
                pendingPhotoUriOrPath = null
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    mono: Boolean = false
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
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) MonoFontFamily else null,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
