package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.Student

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["studentNumber"], unique = true),
        Index(value = ["isDeleted"])
    ]
)
data class StudentEntity(
    @PrimaryKey
    val id: String,
    val studentNumber: String,
    val firstName: String,
    val lastName: String,
    val gradeClass: String,
    val isDayScholar: Boolean,
    val dayScholarType: String,
    val transportRoute: String,
    val feesStatus: String,
    val outstandingAmount: Double,
    val gender: String,
    val avatarColorSeed: Long,
    val photoUrl: String?,
    val guardianName: String,
    val guardianPhone: String,
    val emergencyContact: String,
    val homeroomTeacher: String,
    val academicYear: String,
    val notes: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false
) {
    fun toDomain(): Student {
        val parsedFeeStatus = try {
            FeeStatus.valueOf(feesStatus)
        } catch (_: Exception) {
            FeeStatus.OUTSTANDING
        }

        val parsedDayScholarType = try {
            DayScholarStatus.valueOf(dayScholarType)
        } catch (_: Exception) {
            DayScholarStatus.DAY_SCHOLAR_BUS
        }

        return Student(
            id = id,
            studentNumber = studentNumber,
            firstName = firstName,
            lastName = lastName,
            gradeClass = gradeClass,
            isDayScholar = isDayScholar,
            dayScholarType = parsedDayScholarType,
            transportRoute = transportRoute,
            feesStatus = parsedFeeStatus,
            outstandingAmount = outstandingAmount,
            gender = gender,
            avatarColorSeed = avatarColorSeed,
            photoUrl = photoUrl,
            guardianName = guardianName,
            guardianPhone = guardianPhone,
            emergencyContact = emergencyContact,
            homeroomTeacher = homeroomTeacher,
            academicYear = academicYear,
            notes = notes,
            updatedAt = updatedAt,
            isDeleted = isDeleted
        )
    }

    companion object {
        fun fromDomain(student: Student): StudentEntity {
            return StudentEntity(
                id = student.id,
                studentNumber = student.studentNumber,
                firstName = student.firstName,
                lastName = student.lastName,
                gradeClass = student.gradeClass,
                isDayScholar = student.isDayScholar,
                dayScholarType = student.dayScholarType.name,
                transportRoute = student.transportRoute,
                feesStatus = student.feesStatus.name,
                outstandingAmount = student.outstandingAmount,
                gender = student.gender,
                avatarColorSeed = student.avatarColorSeed,
                photoUrl = student.photoUrl,
                guardianName = student.guardianName,
                guardianPhone = student.guardianPhone,
                emergencyContact = student.emergencyContact,
                homeroomTeacher = student.homeroomTeacher,
                academicYear = student.academicYear,
                notes = student.notes,
                updatedAt = student.updatedAt,
                isDeleted = student.isDeleted
            )
        }
    }
}
