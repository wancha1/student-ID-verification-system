package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.AttendanceRecord
import com.example.model.ExamException
import com.example.model.MealRecord
import com.example.model.MealServingStatus
import com.example.model.MealType

@Entity(
    tableName = "meal_records",
    indices = [
        Index(value = ["studentId", "mealType", "mealDate"], unique = true),
        Index(value = ["mealDate"]),
        Index(value = ["isSyncedToCloud"])
    ]
)
data class MealRecordEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val gradeClass: String,
    val mealType: String,
    val mealDate: String,
    val timestamp: Long,
    val serverName: String,
    val status: String,
    val notes: String = "",
    val isSyncedToCloud: Boolean = false
) {
    fun toDomain(): MealRecord = MealRecord(
        id = id,
        studentId = studentId,
        studentNumber = studentNumber,
        studentName = studentName,
        gradeClass = gradeClass,
        mealType = try { MealType.valueOf(mealType) } catch (_: Exception) { MealType.LUNCH },
        mealDate = mealDate,
        timestamp = timestamp,
        serverName = serverName,
        status = try { MealServingStatus.valueOf(status) } catch (_: Exception) { MealServingStatus.SERVED },
        notes = notes
    )

    companion object {
        fun fromDomain(m: MealRecord, isSynced: Boolean = false): MealRecordEntity = MealRecordEntity(
            id = m.id,
            studentId = m.studentId,
            studentNumber = m.studentNumber,
            studentName = m.studentName,
            gradeClass = m.gradeClass,
            mealType = m.mealType.name,
            mealDate = m.mealDate,
            timestamp = m.timestamp,
            serverName = m.serverName,
            status = m.status.name,
            notes = m.notes,
            isSyncedToCloud = isSynced
        )
    }
}

@Entity(
    tableName = "attendance_records",
    indices = [
        Index(value = ["studentId", "lessonDate", "sessionName"], unique = true),
        Index(value = ["gradeClass", "lessonDate"]),
        Index(value = ["isSyncedToCloud"])
    ]
)
data class AttendanceRecordEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val gradeClass: String,
    val subject: String,
    val sessionName: String,
    val lessonDate: String,
    val timestamp: Long,
    val teacherName: String,
    val isPresent: Boolean,
    val notes: String = "",
    val isCorrection: Boolean = false,
    val correctedBy: String? = null,
    val isSyncedToCloud: Boolean = false
) {
    fun toDomain(): AttendanceRecord = AttendanceRecord(
        id = id,
        studentId = studentId,
        studentNumber = studentNumber,
        studentName = studentName,
        gradeClass = gradeClass,
        subject = subject,
        sessionName = sessionName,
        lessonDate = lessonDate,
        timestamp = timestamp,
        teacherName = teacherName,
        isPresent = isPresent,
        notes = notes,
        isCorrection = isCorrection,
        correctedBy = correctedBy
    )

    companion object {
        fun fromDomain(a: AttendanceRecord, isSynced: Boolean = false): AttendanceRecordEntity = AttendanceRecordEntity(
            id = a.id,
            studentId = a.studentId,
            studentNumber = a.studentNumber,
            studentName = a.studentName,
            gradeClass = a.gradeClass,
            subject = a.subject,
            sessionName = a.sessionName,
            lessonDate = a.lessonDate,
            timestamp = a.timestamp,
            teacherName = a.teacherName,
            isPresent = a.isPresent,
            notes = a.notes,
            isCorrection = a.isCorrection,
            correctedBy = a.correctedBy,
            isSyncedToCloud = isSynced
        )
    }
}

@Entity(
    tableName = "exam_exceptions",
    indices = [
        Index(value = ["studentId", "examCode"], unique = true),
        Index(value = ["isApproved"]),
        Index(value = ["isSyncedToCloud"])
    ]
)
data class ExamExceptionEntity(
    @PrimaryKey
    val id: String,
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val examCode: String,
    val requestedByBursar: String,
    val recommendationReason: String,
    val isApproved: Boolean,
    val approvedByHeadTeacher: String? = null,
    val recommendedByUid: String = "",
    val approvedByUid: String? = null,
    val requestedAt: Long,
    val approvedAt: Long? = null,
    val isSyncedToCloud: Boolean = false
) {
    fun toDomain(): ExamException = ExamException(
        id = id,
        studentId = studentId,
        studentNumber = studentNumber,
        studentName = studentName,
        examCode = examCode,
        requestedByBursar = requestedByBursar,
        recommendationReason = recommendationReason,
        isApproved = isApproved,
        approvedByHeadTeacher = approvedByHeadTeacher,
        requestedAt = requestedAt,
        approvedAt = approvedAt
    )

    companion object {
        fun fromDomain(e: ExamException, isSynced: Boolean = false): ExamExceptionEntity = ExamExceptionEntity(
            id = e.id,
            studentId = e.studentId,
            studentNumber = e.studentNumber,
            studentName = e.studentName,
            examCode = e.examCode,
            requestedByBursar = e.requestedByBursar,
            recommendationReason = e.recommendationReason,
            isApproved = e.isApproved,
            approvedByHeadTeacher = e.approvedByHeadTeacher,
            requestedAt = e.requestedAt,
            approvedAt = e.approvedAt,
            isSyncedToCloud = isSynced
        )
    }
}
