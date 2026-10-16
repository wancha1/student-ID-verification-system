package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.StudentRequirement

/**
 * Durable local snapshot of a student's requirements checklist.
 * The checklist is stored independently from transient Compose/ViewModel state.
 */
@Entity(tableName = "student_requirements")
data class StudentRequirementEntity(
    @PrimaryKey val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val gradeClass: String,
    val uniformComplete: Boolean,
    val sportsKitComplete: Boolean,
    val textbooksSubmitted: Boolean,
    val medicalFormSigned: Boolean,
    val schoolIdIssued: Boolean,
    val rulesAgreementSigned: Boolean,
    val busPassCleared: Boolean,
    val notes: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val isSyncedToCloud: Boolean = false
) {
    fun toDomain() = StudentRequirement(
        studentId = studentId,
        studentNumber = studentNumber,
        studentName = studentName,
        gradeClass = gradeClass,
        uniformComplete = uniformComplete,
        sportsKitComplete = sportsKitComplete,
        textbooksSubmitted = textbooksSubmitted,
        medicalFormSigned = medicalFormSigned,
        schoolIdIssued = schoolIdIssued,
        rulesAgreementSigned = rulesAgreementSigned,
        busPassCleared = busPassCleared,
        notes = notes,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(value: StudentRequirement) = StudentRequirementEntity(
            studentId = value.studentId,
            studentNumber = value.studentNumber,
            studentName = value.studentName,
            gradeClass = value.gradeClass,
            uniformComplete = value.uniformComplete,
            sportsKitComplete = value.sportsKitComplete,
            textbooksSubmitted = value.textbooksSubmitted,
            medicalFormSigned = value.medicalFormSigned,
            schoolIdIssued = value.schoolIdIssued,
            rulesAgreementSigned = value.rulesAgreementSigned,
            busPassCleared = value.busPassCleared,
            notes = value.notes,
            updatedAt = value.updatedAt
        )
    }
}
