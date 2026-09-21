package com.example.model

import java.util.UUID

enum class NotificationType {
    ARRIVAL,
    DEPARTURE,
    DENIED_ACCESS,
    FEE_REMINDER,
    EXEAT_PASS_ISSUED,
    CARD_REPLACED
}

enum class NotificationChannel {
    SMS,
    WHATSAPP,
    EMAIL
}

data class GuardianNotification(
    val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val guardianName: String,
    val guardianPhone: String,
    val type: NotificationType,
    val channel: NotificationChannel = NotificationChannel.SMS,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isDelivered: Boolean = true,
    val gateLocation: String = "Gate 1 (Main Entrance)"
)
