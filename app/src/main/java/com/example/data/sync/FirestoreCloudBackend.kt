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

    private fun unsupported(operation: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Firestore operation '" + operation + "' is not implemented. No data was synced."))

    private fun <T> unsupportedData(operation: String): Result<T> =
        Result.failure(UnsupportedOperationException("Firestore operation '" + operation + "' is not implemented. No data was synced."))

    override suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>> = unsupportedData("fetchStudentsUpdatedSince")
    override suspend fun fetchCardsUpdatedSince(timestamp: Long): Result<List<CardEntity>> = unsupportedData("fetchCardsUpdatedSince")
    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> = unsupported("pushStudentChanges")
    override suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit> = unsupported("pushCardChanges")
    override suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit> = unsupported("pushGateLogs")
    override suspend fun pushMealRecords(meals: List<MealRecordEntity>): Result<Unit> = unsupported("pushMealRecords")
    override suspend fun pushAttendanceRecords(attendance: List<AttendanceRecordEntity>): Result<Unit> = unsupported("pushAttendanceRecords")
    override suspend fun pushExamExceptions(exceptions: List<ExamExceptionEntity>): Result<Unit> = unsupported("pushExamExceptions")
    override suspend fun fetchAllRemoteStudents(): Result<List<StudentEntity>> = unsupportedData("fetchAllRemoteStudents")
    override suspend fun fetchAllRemoteCards(): Result<List<CardEntity>> = unsupportedData("fetchAllRemoteCards")
    override suspend fun updateRemoteFeeStatus(studentId: String, newStatus: FeeStatus, outstandingAmount: Double, updatedAt: Long): Result<Unit> = unsupported("updateRemoteFeeStatus")
    override suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit> = unsupported("deleteRemoteStudent")
    override suspend fun updateRemoteCard(card: CardEntity): Result<Unit> = unsupported("updateRemoteCard")
}
