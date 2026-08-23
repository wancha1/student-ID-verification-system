package com.example.model

import java.util.UUID

/**
 * Immutable audit log for every gate verification attempt.
 * Persisted locally in Room and synchronized to cloud when connected.
 */
data class ScanLog(
    val id: String = UUID.randomUUID().toString(),
    val studentId: String? = null,
    val studentNumber: String? = null,
    val studentName: String,
    val gradeClass: String,
    val cardId: String? = null,
    val cardIdentifier: String? = null,
    val qrPayload: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val decision: GateVerificationDecision = GateVerificationDecision.APPROVED,
    val feeStatus: FeeStatus? = null,
    val cardStatus: CardStatus? = null,
    val isDayScholar: Boolean = true,
    val isApproved: Boolean = true,
    val reason: String = "Normal access",
    val isOfflineDecision: Boolean = false,
    val dataSyncTimestampAtScan: Long = System.currentTimeMillis(),
    val guardName: String = "Officer Daniel Miller",
    val deviceIdentifier: String = "GateTerminal-01 (Handheld)",
    val gateLocation: String = "Gate 1 (Main Entrance)",
    val isSyncedToCloud: Boolean = false
)
