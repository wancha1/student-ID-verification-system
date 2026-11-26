package com.example.ui.meals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.model.MealRecord
import com.example.model.MealType
import com.example.model.MealVerificationResult
import com.example.model.Student
import com.example.ui.components.MultiFormatExportDialog
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.ApprovedGreenText
import com.example.ui.theme.LocalRoleTheme
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberLight
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.RejectedRedText
import com.example.ui.theme.roleGlassBackground
import com.example.util.ExportFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MealsMasterScreen(
    user: AuthUser,
    activeMealType: MealType,
    mealRecords: List<MealRecord>,
    mealScanOutcome: MealVerificationResult?,
    allStudents: List<Student>,
    onSelectMealType: (MealType) -> Unit,
    onOpenScanner: () -> Unit,
    onDismissScanOutcome: () -> Unit,
    onExport: (format: ExportFormat) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    selectedMealsTab: String = "SERVE"
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var historySearchQuery by remember { mutableStateOf("") }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todayRecords = remember(mealRecords, todayStr) {
        mealRecords.filter { it.mealDate == todayStr }
    }
    val currentSessionRecords = remember(todayRecords, activeMealType) {
        todayRecords.filter { it.mealType == activeMealType }
    }

    val filteredRecords = remember(mealRecords, historySearchQuery) {
        if (historySearchQuery.isBlank()) {
            mealRecords
        } else {
            mealRecords.filter {
                it.studentName.contains(historySearchQuery, ignoreCase = true) ||
                    it.studentNumber.contains(historySearchQuery, ignoreCase = true) ||
                    it.gradeClass.contains(historySearchQuery, ignoreCase = true)
            }
        }
    }

    val roleTheme = LocalRoleTheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .roleGlassBackground(roleTheme.primaryColor)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Dining Station Card + Session Toggle + KPIs
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = roleTheme.primaryColor.copy(alpha = 0.12f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Restaurant,
                                            contentDescription = "Dining Services",
                                            tint = roleTheme.primaryColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (readOnly) "Meals Oversight" else "Dining Hall Service",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${user.station} • ${user.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (readOnly) {
                                OutlinedButton(
                                    onClick = { showExportDialog = true },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("button_meals_export"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        // Active Meal Session Segmented Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "ACTIVE MEAL SESSION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.8.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MealType.values().filter { it.isEnabledForServing }.forEach { type ->
                                    val isSelected = activeMealType == type
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) roleTheme.primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) roleTheme.primaryColor else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .then(
                                                if (readOnly) Modifier else Modifier.clickable { onSelectMealType(type) }
                                            )
                                            .testTag("button_meal_session_${type.name.lowercase()}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = type.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = type.timeWindow,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) {
                                                    Color.White.copy(alpha = 0.85f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3 Compact Metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MealStatCard(
                                title = "Today",
                                count = todayRecords.size.toString(),
                                color = roleTheme.primaryColor,
                                modifier = Modifier.weight(1f)
                            )
                            MealStatCard(
                                title = activeMealType.label,
                                count = currentSessionRecords.size.toString(),
                                color = ApprovedGreen,
                                modifier = Modifier.weight(1f)
                            )
                            MealStatCard(
                                title = "Registry",
                                count = allStudents.size.toString(),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2A. Serve Meal Tab Content
            if (!readOnly && selectedMealsTab == "SERVE") {
                item {
                    Text(
                        text = "Recent ${activeMealType.label} Serves (${currentSessionRecords.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (currentSessionRecords.isEmpty()) {
                    item {
                        EmptyMealStateCard(
                            message = "No ${activeMealType.label.lowercase()} serves recorded yet today.",
                            hint = "Tap Scan Student Card below to verify a V2 badge and serve ${activeMealType.label.lowercase()}."
                        )
                    }
                } else {
                    items(currentSessionRecords.take(8), key = { it.id }) { record ->
                        val student = allStudents.find { it.id == record.studentId }
                        MealRecordRow(record = record, student = student)
                    }
                }
            } else {
                // 3B. Serving History / Read-only Oversight
                item {
                    OutlinedTextField(
                        value = historySearchQuery,
                        onValueChange = { historySearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_meals_history_search"),
                        placeholder = { Text("Filter by student name, number, or class") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (filteredRecords.isEmpty()) {
                    item {
                        EmptyMealStateCard(
                            message = if (mealRecords.isEmpty()) "No meal serving records yet" else "No matching records",
                            hint = if (mealRecords.isEmpty()) "Served meals will appear here automatically." else "Try clearing or adjusting your filter."
                        )
                    }
                } else {
                    items(filteredRecords, key = { it.id }) { record ->
                        val student = allStudents.find { it.id == record.studentId }
                        MealRecordRow(record = record, student = student)
                    }
                }
            }
        }

        // 4. Thumb-Zone Sticky Bottom Action Dock (Active Dining Staff only)
        if (!readOnly) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = onOpenScanner,
                            modifier = Modifier
                                .testTag("button_meals_scan_qr")
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = roleTheme.primaryColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "SCAN STUDENT CARD",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        MultiFormatExportDialog(
            datasetTitle = "Dining Hall Meal Serving Records",
            recordCount = mealRecords.size,
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                onExport(format)
                showExportDialog = false
            }
        )
    }

    mealScanOutcome?.let { outcome ->
        MealOutcomeDialog(
            outcome = outcome,
            onDismiss = onDismissScanOutcome
        )
    }
}

@Composable
private fun EmptyMealStateCard(
    message: String,
    hint: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Fastfood,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MealStatCard(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MealRecordRow(
    record: MealRecord,
    student: Student?,
    modifier: Modifier = Modifier
) {
    val timeStr = remember(record.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.US).format(Date(record.timestamp))
    }
    val roleTheme = LocalRoleTheme.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (student != null) {
                StudentAvatar(student = student, size = 42.dp)
            } else {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = roleTheme.primaryColor.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = roleTheme.primaryColor
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.studentName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.studentNumber} • ${record.gradeClass}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = roleTheme.containerColor
                ) {
                    Text(
                        text = record.mealType.label.uppercase(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = roleTheme.badgeTextColor
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MealOutcomeDialog(
    outcome: MealVerificationResult,
    onDismiss: () -> Unit
) {
    when (outcome) {
        is MealVerificationResult.Success -> {
            val record = outcome.record
            val timeStr = remember(record.timestamp) {
                SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(record.timestamp))
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = ApprovedGreen,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "Meal Approved & Served",
                        fontWeight = FontWeight.Bold,
                        color = ApprovedGreen
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = record.studentName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${record.studentNumber} • ${record.gradeClass}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ApprovedGreenLight,
                            border = BorderStroke(1.dp, ApprovedGreen)
                        ) {
                            Text(
                                text = "${record.mealType.label.uppercase()} • $timeStr",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ApprovedGreenText
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ApprovedGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("button_dismiss_meal_success")
                    ) {
                        Text("Next Student")
                    }
                }
            )
        }

        is MealVerificationResult.BlockedDoubleServing -> {
            val student = outcome.student
            val previous = outcome.previousRecord
            val prevTime = remember(previous.timestamp) {
                SimpleDateFormat("hh:mm a", Locale.US).format(Date(previous.timestamp))
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Double Serving Blocked",
                        tint = RejectedRed,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "Double Serving Blocked",
                        fontWeight = FontWeight.Bold,
                        color = RejectedRed
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = student.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${student.studentNumber} • ${student.gradeClass}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RejectedRedLight,
                            border = BorderStroke(1.dp, RejectedRed)
                        ) {
                            Text(
                                text = "Already received ${outcome.mealType.label} today at $prevTime (${previous.serverName}).",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = RejectedRedText
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = RejectedRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("button_dismiss_double_serving")
                    ) {
                        Text("Dismiss")
                    }
                }
            )
        }

        is MealVerificationResult.StudentNotFound -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "Student Not Found",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text("No active student matches code: ${outcome.scannedCode}")
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Dismiss")
                    }
                }
            )
        }

        is MealVerificationResult.PolicyPending -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Meal policy pending",
                        tint = PendingAmber,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "Meal Policy Pending",
                        fontWeight = FontWeight.Bold,
                        color = PendingAmber
                    )
                },
                text = {
                    Text("${outcome.student.fullName} (${outcome.student.studentNumber}): ${outcome.reason}")
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PendingAmber)
                    ) {
                        Text("Dismiss")
                    }
                }
            )
        }

        is MealVerificationResult.BlockedNoClearance -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "No Clearance",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(
                        text = "Meal Clearance Blocked",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text("${outcome.student.fullName} (${outcome.student.studentNumber}): ${outcome.reason}")
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Dismiss")
                    }
                }
            )
        }
    }
}
