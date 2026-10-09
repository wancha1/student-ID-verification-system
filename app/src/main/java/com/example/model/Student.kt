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
    val transportRoute: String = "Route 1 (Lira Main Town)",
    val feesStatus: FeeStatus = FeeStatus.CLEARED,
    val outstandingAmount: Double = 0.0,
    val gender: String = "Unspecified",
    val avatarColorSeed: Long = 0xFF3B82F6,
    val photoUrl: String? = null,
    val accessStatus: AccessStatus = AccessStatus.evaluate(isDayScholar, feesStatus),
    val guardianName: String = "Guardian",
    val guardianPhone: String = "+256 700 000000",
    val emergencyContact: String = "+256 770 000000",
    val homeroomTeacher: String = "Mr. Okello Patrick",
    val academicYear: String = "2025/2026",
    val notes: String = "",
    val qrToken: String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    val fullName: String get() = "$firstName $lastName"
    val name: String get() = fullName

    /**
     * Stable non-sensitive QR identifier token assigned to this specific student.
     * Formatted as "LTC:STU:<studentNumber>:<qrToken>".
     *
     * SECURITY NOTICE: This is a static identifier reference. It does NOT sign any credential
     * and MUST NOT be used as a valid gate entry QR code.
     */
    val uniqueQrCode: String get() = "LTC:STU:${studentNumber.trim().uppercase()}:$qrToken"

    /**
     * Backward-compatible alias for the student identifier token.
     * Reading this property NEVER invokes cryptographic signing.
     */
    val qrPayload: String get() = uniqueQrCode

    /**
     * Entry verification policy:
     * Student is approved if they are a registered Day Scholar and their School Fees are CLEARED.
     */
    val isEntryApproved: Boolean get() = (accessStatus.isApproved && feesStatus == FeeStatus.CLEARED && isDayScholar)
}
