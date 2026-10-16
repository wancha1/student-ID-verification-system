package com.example

import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.CardEntity
import com.example.data.local.ExamExceptionEntity
import com.example.data.local.MealRecordEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.data.sync.InMemoryCloudBackend
import com.example.data.sync.RemoteCloudDataSource
import com.example.model.FeeStatus

/**
 * Keeps remote mutations unavailable so repository outbox tests can inspect
 * durable PENDING/FAILED entries without a successful background sync consuming
 * them between assertions. Reads delegate to an empty in-memory backend.
 */
internal class OutboxPreservingCloudBackend : RemoteCloudDataSource by InMemoryCloudBackend() {
    private fun unavailable(): Result<Unit> =
        Result.failure(IllegalStateException("Offline test backend: remote writes intentionally unavailable"))

    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> = unavailable()
    override suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit> = unavailable()
    override suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit> = unavailable()
    override suspend fun pushMealRecords(meals: List<MealRecordEntity>): Result<Unit> = unavailable()
    override suspend fun pushAttendanceRecords(attendance: List<AttendanceRecordEntity>): Result<Unit> = unavailable()
    override suspend fun pushExamExceptions(exceptions: List<ExamExceptionEntity>): Result<Unit> = unavailable()

    override suspend fun updateRemoteFeeStatus(
        studentId: String,
        newStatus: FeeStatus,
        outstandingAmount: Double,
        updatedAt: Long
    ): Result<Unit> = unavailable()

    override suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit> = unavailable()
    override suspend fun updateRemoteCard(card: CardEntity): Result<Unit> = unavailable()
}
