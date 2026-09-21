package com.example.model

import java.util.UUID

enum class ExeatStatus {
    ACTIVE,
    USED,
    EXPIRED,
    CANCELLED
}

enum class ExeatReason {
    MEDICAL_CLINIC,
    WEEKEND_HOME_LEAVE,
    OFFICIAL_SCHOOL_REPRESENTATION,
    PARENTAL_EMERGENCY,
    SPECIAL_PERMISSION
}

data class ExeatPass(
    val id: String = UUID.randomUUID().toString(),
    val passNumber: String = "EXT-${(1000..9999).random()}",
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val gradeClass: String,
    val reason: ExeatReason = ExeatReason.MEDICAL_CLINIC,
    val destination: String = "Oakridge Health Clinic / Home",
    val issuedBy: String = "Deputy Principal • Mrs. Clara Nambi",
    val validFrom: Long = System.currentTimeMillis(),
    val validUntil: Long = System.currentTimeMillis() + (8 * 3600 * 1000L), // 8 hours default
    val status: ExeatStatus = ExeatStatus.ACTIVE,
    val guardianContact: String = "+256 772 349112",
    val guardianApprovalConfirmed: Boolean = true,
    val qrPayload: String = "OAKRIDGE:EXEAT:$passNumber"
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > validUntil || status == ExeatStatus.EXPIRED

    val isValidForDeparture: Boolean
        get() = status == ExeatStatus.ACTIVE && !isExpired
}
