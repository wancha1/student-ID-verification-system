package com.example.model

import java.util.UUID

/**
 * Domain model for a classroom attendance session roll-call.
 */
data class AttendanceRecord(
    val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val gradeClass: String,
    val subject: String,
    val sessionName: String,
    val lessonDate: String,
    val timestamp: Long = System.currentTimeMillis(),
    val teacherName: String,
    val isPresent: Boolean = true,
    val notes: String = "",
    val isCorrection: Boolean = false,
    val correctedBy: String? = null
)

/**
 * Domain model for an Examination exception review request.
 */
data class ExamException(
    val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val examCode: String,
    val requestedByBursar: String,
    val recommendationReason: String,
    val isApproved: Boolean = false,
    val approvedByHeadTeacher: String? = null,
    val requestedAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null
)
