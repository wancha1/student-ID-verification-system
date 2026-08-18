package com.example.model

data class ScanLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val studentId: String,
    val studentName: String,
    val gradeClass: String,
    val timestamp: Long = System.currentTimeMillis(),
    val feeStatus: FeeStatus,
    val isDayScholar: Boolean,
    val isApproved: Boolean,
    val guardName: String = "Officer Daniel Miller",
    val gateLocation: String = "Gate 1 (Main Entrance)"
)
