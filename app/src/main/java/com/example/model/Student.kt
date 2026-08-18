package com.example.model

data class Student(
    val id: String, // e.g. "STU-2026-0001"
    val firstName: String,
    val lastName: String,
    val gradeClass: String, // e.g. "Grade 11-A"
    val isDayScholar: Boolean = true,
    val dayScholarType: DayScholarStatus = DayScholarStatus.DAY_SCHOLAR_BUS,
    val transportRoute: String = "Route 4 (Oakville Express)",
    val feesStatus: FeeStatus = FeeStatus.CLEARED,
    val outstandingAmount: Double = 0.0,
    val gender: String = "Unspecified",
    val avatarColorSeed: Long = 0xFF3B82F6,
    val photoUrl: String? = null,
    val guardianName: String = "Guardian",
    val guardianPhone: String = "+1 (555) 019-2831",
    val emergencyContact: String = "+1 (555) 019-8800",
    val homeroomTeacher: String = "Mrs. Sarah Henderson",
    val academicYear: String = "2025/2026",
    val notes: String = ""
) {
    val fullName: String get() = "$firstName $lastName"

    /**
     * Entry verification policy:
     * Student is approved if they are a registered Day Scholar and their School Fees are CLEARED.
     */
    val isEntryApproved: Boolean get() = (feesStatus == FeeStatus.CLEARED)
}
