package com.example.data.sync

import com.example.data.StudentDataSamples
import com.example.data.local.CardEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.model.FeeStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Remote cloud backend abstraction (e.g. PostgreSQL, Supabase, or Firebase endpoint).
 * Enables offline-first architecture to push logs and pull latest updates when online.
 */
interface RemoteCloudDataSource {
    suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>>
    suspend fun fetchCardsUpdatedSince(timestamp: Long): Result<List<CardEntity>>
    suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit>
    suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit>
    suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit>
    suspend fun fetchAllRemoteStudents(): Result<List<StudentEntity>>
    suspend fun fetchAllRemoteCards(): Result<List<CardEntity>>
    suspend fun updateRemoteFeeStatus(studentId: String, newStatus: FeeStatus, outstandingAmount: Double, updatedAt: Long): Result<Unit>
    suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit>
    suspend fun updateRemoteCard(card: CardEntity): Result<Unit>
}

/**
 * Simulated central Cloud Backend (acts like a remote Supabase / PostgreSQL server).
 * Holds authoritative state so multi-terminal sync and conflict scenarios can be simulated & tested.
 */
class InMemoryCloudBackend : RemoteCloudDataSource {

    private val mutex = Mutex()
    private val remoteStudentsMap = mutableMapOf<String, StudentEntity>()
    private val remoteCardsMap = mutableMapOf<String, CardEntity>()
    private val remoteLogsList = mutableListOf<ScanLogEntity>()

    init {
        // Initialize central database with sample student and card dataset
        val sampleStudents = StudentDataSamples.createInitialStudents()
        sampleStudents.forEach { student ->
            remoteStudentsMap[student.id] = StudentEntity.fromDomain(student)
        }
        StudentDataSamples.createInitialCards(sampleStudents).forEach { card ->
            remoteCardsMap[card.id] = CardEntity.fromDomain(card)
        }
    }

    override suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>> = mutex.withLock {
        delay(100)
        val modified = remoteStudentsMap.values.filter { it.updatedAt > timestamp }
        Result.success(modified)
    }

    override suspend fun fetchCardsUpdatedSince(timestamp: Long): Result<List<CardEntity>> = mutex.withLock {
        delay(100)
        val modified = remoteCardsMap.values.filter { it.updatedAt > timestamp }
        Result.success(modified)
    }

    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> = mutex.withLock {
        delay(100)
        students.forEach { student ->
            val existing = remoteStudentsMap[student.id]
            if (existing == null || student.updatedAt >= existing.updatedAt) {
                remoteStudentsMap[student.id] = student
            }
        }
        Result.success(Unit)
    }

    override suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit> = mutex.withLock {
        delay(100)
        cards.forEach { card ->
            val existing = remoteCardsMap[card.id]
            if (existing == null || card.updatedAt >= existing.updatedAt) {
                remoteCardsMap[card.id] = card
            }
        }
        Result.success(Unit)
    }

    override suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit> = mutex.withLock {
        delay(80)
        logs.forEach { log ->
            if (remoteLogsList.none { it.id == log.id }) {
                remoteLogsList.add(log.copy(isSyncedToCloud = true))
            }
        }
        Result.success(Unit)
    }

    override suspend fun fetchAllRemoteStudents(): Result<List<StudentEntity>> = mutex.withLock {
        delay(100)
        Result.success(remoteStudentsMap.values.toList())
    }

    override suspend fun fetchAllRemoteCards(): Result<List<CardEntity>> = mutex.withLock {
        delay(100)
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
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Student $studentId not found in cloud backend"))
        }
    }

    override suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit> = mutex.withLock {
        val existing = remoteStudentsMap[studentId]
        if (existing != null) {
            remoteStudentsMap[studentId] = existing.copy(
                isDeleted = true,
                updatedAt = updatedAt
            )
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Student $studentId not found in cloud backend"))
        }
    }

    override suspend fun updateRemoteCard(card: CardEntity): Result<Unit> = mutex.withLock {
        val existing = remoteCardsMap[card.id]
        if (existing == null || card.updatedAt >= existing.updatedAt) {
            remoteCardsMap[card.id] = card
        }
        Result.success(Unit)
    }

    suspend fun resetRemoteState() = mutex.withLock {
        remoteStudentsMap.clear()
        remoteCardsMap.clear()
        remoteLogsList.clear()
        val sampleStudents = StudentDataSamples.createInitialStudents()
        sampleStudents.forEach { student ->
            remoteStudentsMap[student.id] = StudentEntity.fromDomain(student)
        }
        StudentDataSamples.createInitialCards(sampleStudents).forEach { card ->
            remoteCardsMap[card.id] = CardEntity.fromDomain(card)
        }
    }
}
