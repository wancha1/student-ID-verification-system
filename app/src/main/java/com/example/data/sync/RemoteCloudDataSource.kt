package com.example.data.sync

import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.CardEntity
import com.example.data.local.ExamExceptionEntity
import com.example.data.local.MealRecordEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.data.local.StudentRequirementEntity
import com.example.model.FeeStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Remote cloud backend abstraction (e.g. Firebase Firestore or remote school cluster).
 * Enables offline-first architecture to push logs and pull latest updates when online.
 */
interface RemoteCloudDataSource {
    suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>>
    suspend fun fetchCardsUpdatedSince(timestamp: Long): Result<List<CardEntity>>
    suspend fun fetchRequirementsUpdatedSince(timestamp: Long): Result<List<StudentRequirementEntity>>
    suspend fun canSyncRequirements(): Boolean = true
    suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit>
    suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit>
    suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit>
    suspend fun pushMealRecords(meals: List<MealRecordEntity>): Result<Unit>
    suspend fun pushAttendanceRecords(attendance: List<AttendanceRecordEntity>): Result<Unit>
    suspend fun pushExamExceptions(exceptions: List<ExamExceptionEntity>): Result<Unit>
    suspend fun pushRequirementRecords(requirements: List<StudentRequirementEntity>): Result<Unit>
    suspend fun fetchAllRemoteStudents(): Result<List<StudentEntity>>
    suspend fun fetchAllRemoteCards(): Result<List<CardEntity>>
    suspend fun updateRemoteFeeStatus(studentId: String, newStatus: FeeStatus, outstandingAmount: Double, updatedAt: Long): Result<Unit>
    suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit>
    suspend fun updateRemoteCard(card: CardEntity): Result<Unit>
}

/**
 * In-memory cloud backend for offline simulation & isolated integration tests.
 */
class InMemoryCloudBackend : RemoteCloudDataSource {
    fun resetRemoteState() {
        remoteStudentsMap.clear()
        remoteCardsMap.clear()
        remoteLogsList.clear()
        remoteMealsList.clear()
        remoteAttendanceList.clear()
        remoteExceptionsList.clear()
        remoteRequirementsMap.clear()
    }

    private val mutex = Mutex()
    private val remoteStudentsMap = mutableMapOf<String, StudentEntity>()
    private val remoteCardsMap = mutableMapOf<String, CardEntity>()
    private val remoteLogsList = mutableListOf<ScanLogEntity>()
    private val remoteMealsList = mutableListOf<MealRecordEntity>()
    private val remoteAttendanceList = mutableListOf<AttendanceRecordEntity>()
    private val remoteExceptionsList = mutableListOf<ExamExceptionEntity>()
    private val remoteRequirementsMap = mutableMapOf<String, StudentRequirementEntity>()

    override suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>> = mutex.withLock {
        delay(50)
        val modified = remoteStudentsMap.values.filter { it.updatedAt > timestamp }
        Result.success(modified)
    }

    override suspend fun fetchCardsUpdatedSince(timestamp: Long): Result<List<CardEntity>> = mutex.withLock {
        delay(50)
        val modified = remoteCardsMap.values.filter { it.updatedAt > timestamp }
        Result.success(modified)
    }

    override suspend fun fetchRequirementsUpdatedSince(timestamp: Long): Result<List<StudentRequirementEntity>> = mutex.withLock {
        delay(50)
        Result.success(remoteRequirementsMap.values.filter { it.updatedAt > timestamp }.map { it.copy(isSyncedToCloud = true) })
    }

    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> = mutex.withLock {
        delay(50)
        students.forEach { student ->
            val existing = remoteStudentsMap[student.id]
            if (existing == null || student.updatedAt >= existing.updatedAt) {
                remoteStudentsMap[student.id] = student
            }
        }
        Result.success(Unit)
    }

    override suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit> = mutex.withLock {
        delay(50)
        cards.forEach { card ->
            val existing = remoteCardsMap[card.id]
            if (existing == null || card.updatedAt >= existing.updatedAt) {
                remoteCardsMap[card.id] = card
            }
        }
        Result.success(Unit)
    }

    override suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit> = mutex.withLock {
        delay(50)
        logs.forEach { log ->
            if (remoteLogsList.none { it.id == log.id }) {
                remoteLogsList.add(log.copy(isSyncedToCloud = true))
            }
        }
        Result.success(Unit)
    }

    override suspend fun pushMealRecords(meals: List<MealRecordEntity>): Result<Unit> = mutex.withLock {
        delay(50)
        meals.forEach { meal ->
            if (remoteMealsList.none { it.id == meal.id }) {
                remoteMealsList.add(meal.copy(isSyncedToCloud = true))
            }
        }
        Result.success(Unit)
    }

    override suspend fun pushAttendanceRecords(attendance: List<AttendanceRecordEntity>): Result<Unit> = mutex.withLock {
        delay(50)
        attendance.forEach { att ->
            if (remoteAttendanceList.none { it.id == att.id }) {
                remoteAttendanceList.add(att.copy(isSyncedToCloud = true))
            }
        }
        Result.success(Unit)
    }

    override suspend fun pushExamExceptions(exceptions: List<ExamExceptionEntity>): Result<Unit> = mutex.withLock {
        delay(50)
        exceptions.forEach { ex ->
            val existing = remoteExceptionsList.indexOfFirst { it.id == ex.id }
            if (existing >= 0) {
                remoteExceptionsList[existing] = ex.copy(isSyncedToCloud = true)
            } else {
                remoteExceptionsList.add(ex.copy(isSyncedToCloud = true))
            }
        }
        Result.success(Unit)
    }

    override suspend fun pushRequirementRecords(requirements: List<StudentRequirementEntity>): Result<Unit> = mutex.withLock {
        delay(50)
        requirements.forEach { requirement ->
            val existing = remoteRequirementsMap[requirement.studentId]
            if (existing == null || requirement.updatedAt >= existing.updatedAt) {
                remoteRequirementsMap[requirement.studentId] = requirement.copy(notes = "", isSyncedToCloud = true)
            }
        }
        Result.success(Unit)
    }

    override suspend fun fetchAllRemoteStudents(): Result<List<StudentEntity>> = mutex.withLock {
        Result.success(remoteStudentsMap.values.toList())
    }

    override suspend fun fetchAllRemoteCards(): Result<List<CardEntity>> = mutex.withLock {
        Result.success(remoteCardsMap.values.toList())
    }

    override suspend fun updateRemoteFeeStatus(
        studentId: String,
        newStatus: FeeStatus,
        outstandingAmount: Double,
        updatedAt: Long
    ): Result<Unit> = mutex.withLock {
        val existing = remoteStudentsMap[studentId]
        if (existing != null) {
            remoteStudentsMap[studentId] = existing.copy(
                feesStatus = newStatus.name,
                outstandingAmount = outstandingAmount,
                updatedAt = updatedAt
            )
        }
        Result.success(Unit)
    }

    override suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit> = mutex.withLock {
        val existing = remoteStudentsMap[studentId]
        if (existing != null) {
            remoteStudentsMap[studentId] = existing.copy(isDeleted = true, updatedAt = updatedAt)
        }
        Result.success(Unit)
    }

    override suspend fun updateRemoteCard(card: CardEntity): Result<Unit> = mutex.withLock {
        remoteCardsMap[card.id] = card
        Result.success(Unit)
    }
}
