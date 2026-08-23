package com.example.data

import com.example.model.Card
import com.example.model.FeeStatus
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.model.SyncInfo
import com.example.model.SyncSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for student ID verification & access management.
 * Abstracts the Room local database and cloud synchronization layer.
 */
interface StudentRepository {
    val studentsFlow: Flow<List<Student>>
    val scanLogsFlow: Flow<List<ScanLog>>
    val syncInfoFlow: Flow<SyncInfo>

    suspend fun getStudentById(id: String): Student?
    suspend fun getStudentByStudentNumber(studentNumber: String): Student?

    /**
     * Core Gate Scanning Verification:
     * Parses QR code -> searches local Room DB -> verifies Card lifecycle (ACTIVE vs LOST/REPLACED/DEACTIVATED)
     * -> evaluates fee & day-scholar status -> returns typed result.
     */
    suspend fun verifyStudentByQr(rawQrCode: String): StudentScanResult

    // Student CRUD
    suspend fun updateFeeStatus(studentId: String, newStatus: FeeStatus, outstandingAmount: Double = 0.0): Result<Unit>
    suspend fun addStudent(student: Student): Result<Unit>
    suspend fun updateStudent(student: Student): Result<Unit>
    suspend fun deleteStudent(studentId: String): Result<Unit>

    // Card Lifecycle Management
    fun getCardsForStudentFlow(studentId: String): Flow<List<Card>>
    suspend fun getCardsForStudent(studentId: String): List<Card>
    suspend fun getActiveCardForStudent(studentId: String): Card?
    suspend fun issueCard(studentId: String, customIdentifier: String? = null, reason: String = "Initial issuance"): Result<Card>
    suspend fun reportCardLost(studentId: String, cardId: String, reason: String = "Reported lost by student/guardian"): Result<Card>
    suspend fun issueReplacementCard(studentId: String, oldCardId: String, reason: String = "Replacement card issued"): Result<Card>
    suspend fun deactivateCard(studentId: String, cardId: String, reason: String = "Deactivated by Administrator"): Result<Unit>

    // Audit Logging
    suspend fun logVerificationScan(log: ScanLog)
    suspend fun clearScanLogs()

    // Cloud Sync
    suspend fun syncWithCloud(): Result<SyncSummary>
    fun setNetworkOnline(isOnline: Boolean)

    // Maintenance & Sample Data
    suspend fun resetToSampleData()
    fun getLastSyncTimestamp(): Long
}
