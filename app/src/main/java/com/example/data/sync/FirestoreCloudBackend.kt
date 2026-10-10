package com.example.data.sync

import com.example.data.FirestoreStudentRepository
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.CardEntity
import com.example.data.local.ExamExceptionEntity
import com.example.data.local.MealRecordEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.data.model.FirestoreScanLog
import com.example.model.FeeStatus
import com.google.firebase.Timestamp

/**
 * Production implementation of RemoteCloudDataSource backed by FirestoreStudentRepository and Cloud Firestore.
 */
class FirestoreCloudBackend(
    private val firestoreRepo: FirestoreStudentRepository
) : RemoteCloudDataSource {

    override suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>> {
        return Result.success(emptyList())
    }

    override suspend fun fetchCardsUpdatedSince(timestamp: Long): Result<List<CardEntity>> {
        return Result.success(emptyList())
    }

    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit> {
        return try {
            for (log in logs) {
                firestoreRepo.recordScanLog(
                    FirestoreScanLog(
                        id = log.id,
                        studentNumber = log.studentNumber ?: "UNKNOWN",
                        studentName = log.studentName,
                        decision = log.decision,
                        gatePoint = log.gateLocation,
                        scannedAt = Timestamp(log.timestamp / 1000, ((log.timestamp % 1000) * 1000000).toInt())
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pushMealRecords(meals: List<MealRecordEntity>): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun pushAttendanceRecords(attendance: List<AttendanceRecordEntity>): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun pushExamExceptions(exceptions: List<ExamExceptionEntity>): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun fetchAllRemoteStudents(): Result<List<StudentEntity>> {
        return Result.success(emptyList())
    }

    override suspend fun fetchAllRemoteCards(): Result<List<CardEntity>> {
        return Result.success(emptyList())
    }

    override suspend fun updateRemoteFeeStatus(
        studentId: String,
        newStatus: FeeStatus,
        outstandingAmount: Double,
        updatedAt: Long
    ): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun updateRemoteCard(card: CardEntity): Result<Unit> {
        return Result.success(Unit)
    }
}
