package com.example.ui.notifications

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GuardianNotification
import com.example.model.NotificationChannel
import com.example.model.NotificationType
import com.example.ui.theme.ApprovedGreen
import com.example.ui.theme.ApprovedGreenLight
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RejectedRed
import com.example.ui.theme.RejectedRedLight
import com.example.ui.theme.SchoolPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianAlertsScreen(
    notifications: List<GuardianNotification>,
    onBack: () -> Unit,
    onSendCustomAlert: (studentName: String, guardianPhone: String, message: String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var launchError by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedNotificationForPreview by remember { mutableStateOf<GuardianNotification?>(null) }
    var showSendCustomDialog by remember { mutableStateOf(false) }

    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "ARRIVAL" -> notifications.filter { it.type == NotificationType.ARRIVAL }
            "DENIED" -> notifications.filter { it.type == NotificationType.DENIED_ACCESS }
            "EXEAT" -> notifications.filter { it.type == NotificationType.EXEAT_PASS_ISSUED }
            else -> notifications
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Guardian WhatsApp Alerts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${notifications.size} recorded alert notices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("button_back_alerts")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSendCustomDialog = true },
                containerColor = SchoolPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_send_guardian_whatsapp")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message on WhatsApp", fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier.testTag("screen_guardian_alerts")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "WhatsApp handoff: review the prepared message in WhatsApp and tap Send. LTC cannot confirm delivery from this screen.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${notifications.size})") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("chip_alert_all")
                )
                FilterChip(
                    selected = selectedFilter == "ARRIVAL",
                    onClick = { selectedFilter = "ARRIVAL" },
                    label = { Text("Arrivals") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("chip_alert_arrival")
                )
                FilterChip(
                    selected = selectedFilter == "DENIED",
                    onClick = { selectedFilter = "DENIED" },
                    label = { Text("Denials / Flagged") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("chip_alert_denied")
                )
                FilterChip(
                    selected = selectedFilter == "EXEAT",
                    onClick = { selectedFilter = "EXEAT" },
                    label = { Text("Exeat Passes") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("chip_alert_exeat")
                )
            }

            if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Guardian Alerts Recorded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Guardian alert records appear here. To contact a guardian, prepare a WhatsApp message and send it from WhatsApp.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .testTag("lazy_column_alerts"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = filteredNotifications,
                        key = { it.id }
                    ) { item ->
                        GuardianAlertItemCard(
                            notification = item,
                            onClick = { selectedNotificationForPreview = item }
                        )
                    }
                }
            }
        }
    }

    // Detail Preview Modal (Phone SMS Bubble style)
    selectedNotificationForPreview?.let { notif ->
        val timeFormat = remember { SimpleDateFormat("EEEE, MMMM d, yyyy • hh:mm a", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { selectedNotificationForPreview = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        tint = SchoolPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardian Alert Preview", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // SMS Bubble Box
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = SchoolPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = notif.guardianPhone,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SchoolPrimary
                                    )
                                }
                                Text(
                                    text = if (notif.isDelivered) "DELIVERY CONFIRMED" else "NOT CONFIRMED",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    color = if (notif.isDelivered) ApprovedGreen else MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = notif.message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (notif.isDelivered) {
                            "Delivery confirmed for ${notif.guardianName}."
                        } else {
                            "This record does not confirm that a message was sent or delivered. Check WhatsApp directly."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (notif.isDelivered) ApprovedGreen else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Recipient: ${notif.guardianName} (Guardian of ${notif.studentName})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Student ID: ${notif.studentNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Timestamp: ${timeFormat.format(Date(notif.timestamp))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNotificationForPreview = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Compose WhatsApp message handoff dialog
    if (showSendCustomDialog) {
        var studentNameInput by remember { mutableStateOf("") }
        var phoneInput by remember { mutableStateOf("+256 ") }
        var messageInput by remember { mutableStateOf("Dear Parent, this is an official gate security update from Lira Town College (LTC).") }

        AlertDialog(
            onDismissRequest = { showSendCustomDialog = false },
            title = { Text("Message Guardian on WhatsApp", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = studentNameInput,
                        onValueChange = { studentNameInput = it },
                        label = { Text("Student Name") },
                        placeholder = { Text("e.g. Michael Adeyemi") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_sms_student_name")
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Guardian Phone Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_sms_phone")
                    )
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        label = { Text("WhatsApp Message") },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_sms_message")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val digits = phoneInput.filter { it.isDigit() }
                        val internationalNumber = when {
                            digits.startsWith("256") -> digits
                            digits.startsWith("0") -> "256" + digits.drop(1)
                            else -> digits
                        }
                        if (studentNameInput.isNotBlank() && internationalNumber.length >= 10 && messageInput.isNotBlank()) {
                            val preparedMessage = "Dear Parent/Guardian, regarding ${studentNameInput.trim()}:\n\n${messageInput.trim()}\n\nLira Town College (LTC)"
                            if (onSendCustomAlert(studentNameInput.trim(), internationalNumber, preparedMessage)) {
                                try {
                                    val whatsappUri = Uri.parse(
                                        "https://wa.me/$internationalNumber?text=${Uri.encode(preparedMessage)}"
                                    )
                                    context.startActivity(Intent(Intent.ACTION_VIEW, whatsappUri))
                                    launchError = null
                                    showSendCustomDialog = false
                                } catch (_: ActivityNotFoundException) {
                                    launchError = "Could not open WhatsApp. Please install WhatsApp or open it manually."
                                } catch (_: Exception) {
                                    launchError = "Could not open WhatsApp. Check the guardian's number and try again."
                                }
                            }
                        } else {
                            launchError = "Enter a student name, valid guardian number, and message."
                        }
                    },
                    modifier = Modifier.testTag("button_confirm_send_whatsapp")
                ) {
                    Text("Open WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendCustomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    launchError?.let { error ->
        AlertDialog(
            onDismissRequest = { launchError = null },
            title = { Text("WhatsApp not opened") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { launchError = null }) { Text("OK") }
            }
        )
    }
}

@Composable
fun GuardianAlertItemCard(
    notification: GuardianNotification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    val (icon, tintColor, bgColor) = when (notification.type) {
        NotificationType.ARRIVAL -> Triple(Icons.Default.CheckCircle, ApprovedGreen, ApprovedGreenLight)
        NotificationType.DENIED_ACCESS -> Triple(Icons.Default.Block, RejectedRed, RejectedRedLight)
        NotificationType.EXEAT_PASS_ISSUED -> Triple(Icons.Default.ConfirmationNumber, GoldAccent, GoldAccent.copy(alpha = 0.15f))
        NotificationType.FEE_REMINDER -> Triple(Icons.Default.CreditCard, GoldAccent, GoldAccent.copy(alpha = 0.15f))
        else -> Triple(Icons.Default.Message, SchoolPrimary, SchoolPrimary.copy(alpha = 0.15f))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .testTag("alert_item_${notification.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.studentName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${timeFormatter.format(Date(notification.timestamp))} • ${dateFormatter.format(Date(notification.timestamp))}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "To: ${notification.guardianName} (${notification.guardianPhone})",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }
        }
    }
}
