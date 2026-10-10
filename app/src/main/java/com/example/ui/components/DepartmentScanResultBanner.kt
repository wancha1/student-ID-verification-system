package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DepartmentVerificationResult
import com.example.model.ExamClearanceStatus
import com.example.model.GateDirection
import com.example.model.ScanDepartment
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import com.example.ui.theme.SchoolPrimary
import com.example.ui.theme.WarningYellow
import com.example.ui.theme.WarningYellowLight
import com.example.ui.theme.WarningYellowText

@Composable
fun DepartmentScanResultBanner(
    result: DepartmentVerificationResult?,
    onDismiss: () -> Unit,
    onRecommendException: ((studentId: String, studentNumber: String, studentName: String) -> Unit)? = null,
    onApproveException: ((studentId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = result != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (result == null) return@AnimatedVisibility

        when (result) {
            is DepartmentVerificationResult.Success -> {
                val isSuccess = result.isAllowed
                val bgColor = if (isSuccess) ApprovedGreenLight else RejectedRedLight
                val borderColor = if (isSuccess) ApprovedGreen else RejectedRed
                val iconColor = if (isSuccess) ApprovedGreenText else RejectedRedText

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(borderColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.statusHeadline,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = iconColor
                                )
                                Text(
                                    text = "${result.department.title} • ${if (result.student.isDayScholar) "Day Scholar" else "Boarder"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = iconColor)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = borderColor.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Student Information
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "STUDENT NAME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = result.student.fullName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "REG NUMBER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = result.student.studentNumber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.statusDetail,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Examination Actions (Bursar Recommendation / Head Teacher Approval)
                        if (result.department == ScanDepartment.EXAMINATIONS) {
                            if (result.examStatus == ExamClearanceStatus.BLOCKED_FEES && onRecommendException != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        onRecommendException(
                                            result.student.id,
                                            result.student.studentNumber,
                                            result.student.fullName
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SchoolPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Bursar: Recommend Exam Exception")
                                }
                            } else if (result.examStatus == ExamClearanceStatus.PENDING_BURSAR_RECOMMENDED && onApproveException != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { onApproveException(result.student.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ApprovedGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Head Teacher: Approve Exam Exception")
                                }
                            }
                        }
                    }
                }
            }

            is DepartmentVerificationResult.DuplicateAction -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningYellowLight),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(WarningYellow))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningYellowText, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Duplicate Scan Blocked", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = WarningYellowText)
                                Text(text = "${result.department.title} • ${result.student.fullName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = WarningYellowText)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = result.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            is DepartmentVerificationResult.PermissionDenied -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RejectedRedLight),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RejectedRed))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = RejectedRedText, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Permission Denied", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RejectedRedText)
                                Text(text = "Requires ${result.requiredPermission.name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = RejectedRedText)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = result.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            is DepartmentVerificationResult.CardInactive -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RejectedRedLight),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RejectedRed))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = RejectedRedText, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Card ${result.cardStatus.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RejectedRedText)
                                Text(text = "Card ID: ${result.card.cardIdentifier}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = RejectedRedText)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = result.reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            is DepartmentVerificationResult.InvalidCredential -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RejectedRedLight),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RejectedRed))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = RejectedRedText, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Invalid QR Credential", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RejectedRedText)
                                Text(text = "Security verification failed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = RejectedRedText)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = result.errorReason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            is DepartmentVerificationResult.StudentNotFound -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RejectedRedLight),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RejectedRed))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = RejectedRedText, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Student Not Found", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = RejectedRedText)
                                Text(text = "Card: ${result.cardId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = RejectedRedText)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = result.reason, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
