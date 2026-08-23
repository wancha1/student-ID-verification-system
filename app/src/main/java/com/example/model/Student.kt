package com.example.model

import java.util.UUID

/**
 * Core Student domain model.
 *
 * @property id Immutable, collision-resistant machine UUID.
 * @property studentNumber Human-readable school registration number (e.g. "OAK-2026-0001").
 */
data class Student(
    val id: String = UUID.randomUUID().toString(),
    val studentNumber: String,
    val firstName: String,
    val lastName: String,
    val gradeClass: String,
    val isDayScholar: Boolean = true,
    val dayScholarType: DayScholarStatus = DayScholarStatus.DAY_SCHOLAR_BUS,
    val transportRoute: String = "Route 4 (Oakville Express)",
    val feesStatus: FeeStatus = FeeStatus.CLEARED,
    val outstandingAmount: Double = 0.0,
    val gender: String = "Unspecified",
    val avatarColorSeed: Long = 0xFF3B82F6,
    val photoUrl: String? = null,
    val guardianName: String = "Guardian",
    val guardianPhone: String = "+256 700 000000",
    val emergencyContact: String = "+256 770 000000",
    val homeroomTeacher: String = "Mrs. Sarah Henderson",
    val academicYear: String = "2025/2026",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    val fullName: String get() = "$firstName $lastName"

    /**
     * Standardized non-sensitive QR payload.
     * Encodes ONLY a non-sensitive student pointer (never financial or personal info).
     */
    val qrPayload: String get() = "OAKRIDGE:STU:$studentNumber"

    /**
     * Entry verification policy:
     * Student is approved if they are a registered Day Scholar and their School Fees are CLEARED.
     */
    val isEntryApproved: Boolean get() = (feesStatus == FeeStatus.CLEARED && isDayScholar)
}
