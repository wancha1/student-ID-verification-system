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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SyncAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.ui.components.PrintableStudentIdCard
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenDark
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedDark
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudentDetailScreen(
    student: Student,
    cards: List<StudentCard> = emptyList(),
    onBack: () -> Unit,
    onUpdateFeeStatus: (FeeStatus, Double) -> Unit,
    onUpdateStudentDetails: (Student) -> Unit,
    onDeleteStudent: (String) -> Unit,
    onReportCardLost: (studentId: String, cardId: String, reason: String) -> Unit = { _, _, _ -> },
    onIssueReplacementCard: (studentId: String, oldCardId: String, reason: String) -> Unit = { _, _, _ -> },
    onDeactivateCard: (studentId: String, cardId: String, reason: String) -> Unit = { _, _, _ -> },
    onIssueNewCard: (studentId: String, reason: String) -> Unit = { _, _ -> },
    onTestScanAsGuard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDigitalCardDialog by remember { mutableStateOf(false) }
    var cardToReportLost by remember { mutableStateOf<StudentCard?>(null) }
    var cardToReplace by remember { mutableStateOf<StudentCard?>(null) }
    var cardToDeactivate by remember { mutableStateOf<StudentCard?>(null) }
    var showIssueNewCardDialog by remember { mutableStateOf(false) }

    var outstandingAmountInput by remember(student.outstandingAmount) {
        mutableStateOf(if (student.outstandingAmount > 0) student.outstandingAmount.toString() else "450000.00")
    }

    val isCleared = student.feesStatus == FeeStatus.CLEARED
    val activeCard = cards.firstOrNull { it.status == CardStatus.ACTIVE }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
    ) {
        // TOP NAVIGATION BAR
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
                            text = student.studentNumber,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Edit & Delete Icons
                Row {
                    IconButton(
                        onClick = { showDigitalCardDialog = true },
                        modifier = Modifier.testTag("button_detail_print_card")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print ID Card",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

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

        // PHYSICAL ID CARD LIFECYCLE MANAGEMENT SECTION (STUDENT ≠ CARD ≠ SCAN)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .testTag("card_id_card_management")
                    .fillMaxWidth()
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PHYSICAL ID CARD LIFECYCLE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (activeCard != null) {
                            CardStatusBadge(status = activeCard.status)
                        } else {
                            CardStatusBadge(status = CardStatus.DEACTIVATED)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (activeCard != null) {
                        // Active Card Overview Banner
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "ACTIVE CARD IDENTIFIER",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = activeCard.cardIdentifier,
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "ISSUED ON",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = dateFormat.format(Date(activeCard.issueDate)),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "QR Payload: ${activeCard.qrPayload}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Card Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { cardToReportLost = activeCard },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("button_report_lost"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE11D48))
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Report Lost", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { cardToReplace = activeCard },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("button_issue_replacement"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Replace Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDigitalCardDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("button_view_digital_badge")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Printable Badge", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { cardToDeactivate = activeCard },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("button_deactivate_card"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64748B))
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Deactivate", fontSize = 12.sp)
                            }
                        }
                    } else {
                        // No active card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "No Active ID Card Registered",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF78350F)
                                    )
                                    Text(
                                        text = "Gate scanners will deny access until a physical card is issued.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showIssueNewCardDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("button_issue_new_card"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddCard, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Issue & Activate Physical ID Card", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Card Audit History
                    if (cards.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Card Issuance History (${cards.size} records)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        cards.forEach { c ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = c.cardIdentifier,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Issued: ${dateFormat.format(Date(c.issueDate))} ${c.reason?.let { "• $it" } ?: ""}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    CardStatusBadge(status = c.status)
                                }
                            }
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

                        FeeStatusBadge(feeStatus = student.feesStatus)
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
                                    val amt = outstandingAmountInput.toDoubleOrNull() ?: 450000.0
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
                                label = { Text("Outstanding Amount (UGX)") },
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
                                    "Status updated: Security guards scanning QR badge (${student.studentNumber}) will evaluate to ENTRY APPROVED."
                                else
                                    "Status updated: Security guards scanning QR badge (${student.studentNumber}) will evaluate to ENTRY NOT APPROVED.",
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
                text = "STUDENT DIGITAL ID & SECURE BARCODE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            PrintableStudentIdCard(
                student = student,
                card = activeCard
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // QUICK ACTION: TEST IN SECURITY GUARD SCANNER
        item {
            Button(
                onClick = { onTestScanAsGuard(student.qrPayload) },
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
                        label = "Internal UUID",
                        value = student.id
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DetailRow(
                        label = "Student Number",
                        value = student.studentNumber
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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

    // Digital ID Printable Dialog
    if (showDigitalCardDialog) {
        DigitalIdCardDialog(
            student = student,
            card = activeCard,
            onDismiss = { showDigitalCardDialog = false },
            onTestScan = onTestScanAsGuard
        )
    }

    // Report Lost Dialog
    cardToReportLost?.let { card ->
        var reasonText by remember { mutableStateOf("Student reported card lost on campus") }
        AlertDialog(
            onDismissRequest = { cardToReportLost = null },
            title = { Text("Report Card Lost", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Reporting ${card.cardIdentifier} as LOST will immediately deny gate entry with this card.")
                    Spacer(modifier = Modifier.height(12.dp))
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    modifier = Modifier.testTag("button_confirm_report_lost")
                ) {
                    Text("Confirm Lost")
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToReportLost = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Issue Replacement Dialog
    cardToReplace?.let { card ->
        var reasonText by remember { mutableStateOf("Replaced lost/damaged badge") }
        AlertDialog(
            onDismissRequest = { cardToReplace = null },
            title = { Text("Issue Replacement Card", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This will deactivate card ${card.cardIdentifier} and issue a newly incremented active card.")
                    Spacer(modifier = Modifier.height(12.dp))
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
                    modifier = Modifier.testTag("button_confirm_replace_card")
                ) {
                    Text("Issue Replacement")
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToReplace = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Deactivate Dialog
    cardToDeactivate?.let { card ->
        var reasonText by remember { mutableStateOf("Deactivated by Administrator") }
        AlertDialog(
            onDismissRequest = { cardToDeactivate = null },
            title = { Text("Deactivate Card?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Deactivating ${card.cardIdentifier} will block gate scans with this card.")
                    Spacer(modifier = Modifier.height(12.dp))
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Deactivate")
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToDeactivate = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Issue New Card Dialog
    if (showIssueNewCardDialog) {
        var reasonText by remember { mutableStateOf("New card issuance") }
        AlertDialog(
            onDismissRequest = { showIssueNewCardDialog = false },
            title = { Text("Issue Physical ID Card", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Generate and activate a new physical ID badge for ${student.fullName} (${student.studentNumber}).")
                    Spacer(modifier = Modifier.height(12.dp))
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
                    }
                ) {
                    Text("Issue Card")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueNewCardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Student Modal
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
            text = { Text("Are you sure you want to delete ${student.fullName} (${student.studentNumber})? Gate verification will be denied for this ID.") },
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
