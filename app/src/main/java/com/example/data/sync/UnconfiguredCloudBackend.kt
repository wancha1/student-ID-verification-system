package com.example.data.sync

import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.CardEntity
import com.example.data.local.ExamExceptionEntity
import com.example.data.local.MealRecordEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.model.FeeStatus

/** Production fail-closed backend until school-scoped Firestore sync is implemented. */
class UnconfiguredCloudBackend : RemoteCloudDataSource {
    private fun failure(operation: String) = UnsupportedOperationException(
        "Cloud sync is not configured for '" + operation + "'. Local data is retained; no remote sync was reported."
    )
    private fun failed(operation: String): Result<Unit> = Result.failure(failure(operation))
    private fun <T> failedData(operation: String): Result<T> = Result.failure(failure(operation))

    override suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>> = failedData("fetchStudentsUpdatedSince")
    override suspend fun fetchCardsUpdatedSince(timestamp: Long): Result<List<CardEntity>> = failedData("fetchCardsUpdatedSince")
    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> = failed("pushStudentChanges")
    override suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit> = failed("pushCardChanges")
    override suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit> = failed("pushGateLogs")
    override suspend fun pushMealRecords(meals: List<MealRecordEntity>): Result<Unit> = failed("pushMealRecords")
    override suspend fun pushAttendanceRecords(attendance: List<AttendanceRecordEntity>): Result<Unit> = failed("pushAttendanceRecords")
    override suspend fun pushExamExceptions(exceptions: List<ExamExceptionEntity>): Result<Unit> = failed("pushExamExceptions")
    override suspend fun fetchAllRemoteStudents(): Result<List<StudentEntity>> = failedData("fetchAllRemoteStudents")
    override suspend fun fetchAllRemoteCards(): Result<List<CardEntity>> = failedData("fetchAllRemoteCards")
    override suspend fun updateRemoteFeeStatus(studentId: String, newStatus: FeeStatus, outstandingAmount: Double, updatedAt: Long): Result<Unit> = failed("updateRemoteFeeStatus")
    override suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit> = failed("deleteRemoteStudent")
    override suspend fun updateRemoteCard(card: CardEntity): Result<Unit> = failed("updateRemoteCard")
}
