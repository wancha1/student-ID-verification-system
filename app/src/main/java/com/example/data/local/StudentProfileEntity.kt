package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.AccessStatus
import com.example.model.Student

/**
 * Room database schema dedicated specifically to storing local student profiles
 * for fast, reliable offline gate verification.
 *
 * Stores the core identity attributes:
 * - id: Machine UUID primary key
 * - studentNumber: Unique school registration number (e.g., "OAK-2026-0001")
 * - name: Full student name
 * - photoUrl: Local or cached photo URL for visual identity confirmation
 * - accessStatus: Current authorization status ("APPROVED", "RESTRICTED_FEES", "RESTRICTED_BOARDER", "SUSPENDED", etc.)
 *
 * Optimized with indices for instant offline QR lookups and gate pass validation.
 */
@Entity(
    tableName = "student_profiles",
    indices = [
        Index(value = ["studentNumber"], unique = true),
        Index(value = ["accessStatus"]),
        Index(value = ["name"]),
        Index(value = ["updatedAt"])
    ]
)
data class StudentProfileEntity(
    @PrimaryKey
    val id: String,
    val studentNumber: String,
    val name: String,
    val photoUrl: String?,
    val accessStatus: String,
    val gradeClass: String = "",
    val isDayScholar: Boolean = true,
    val isFeesCleared: Boolean = true,
    val outstandingAmount: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isAccessApproved: Boolean
        get() = accessStatus.equals(AccessStatus.APPROVED.name, ignoreCase = true)

    companion object {
        fun fromStudent(student: Student): StudentProfileEntity {
            return StudentProfileEntity(
                id = student.id,
                studentNumber = student.studentNumber,
                name = student.name,
                photoUrl = student.photoUrl,
                accessStatus = student.accessStatus.name,
                gradeClass = student.gradeClass,
                isDayScholar = student.isDayScholar,
                isFeesCleared = student.feesStatus == com.example.model.FeeStatus.CLEARED,
                outstandingAmount = student.outstandingAmount,
                updatedAt = student.updatedAt
            )
        }
    }
}
