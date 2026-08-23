package com.example.data

import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.model.SyncInfo
import com.example.model.SyncStatus
import com.example.model.SyncSummary
import com.example.util.QrCodeUtils
import com.example.util.QrParseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MockStudentRepository : StudentRepository {

    private val _students = MutableStateFlow<List<Student>>(StudentDataSamples.createInitialStudents())
    override val studentsFlow: Flow<List<Student>> = _students.asStateFlow()

    private val _cards = MutableStateFlow<List<Card>>(StudentDataSamples.createInitialCards(_students.value))
    private val _scanLogs = MutableStateFlow<List<ScanLog>>(StudentDataSamples.createInitialScanLogs())
    override val scanLogsFlow: Flow<List<ScanLog>> = _scanLogs.asStateFlow()

    private var lastSyncTime = System.currentTimeMillis()
    private val _syncInfo = MutableStateFlow(
        SyncInfo(
            status = SyncStatus.SYNCED,
            lastSyncTimestamp = lastSyncTime,
            isOnline = true,
            pendingLogsCount = 0
        )
    )
    override val syncInfoFlow: Flow<SyncInfo> = _syncInfo.asStateFlow()

    override suspend fun getStudentById(id: String): Student? {
        val trimmed = id.trim()
        return _students.value.firstOrNull {
            it.id.equals(trimmed, ignoreCase = true)
        }
    }

    override suspend fun getStudentByStudentNumber(studentNumber: String): Student? {
        val clean = studentNumber.trim()
        return _students.value.firstOrNull {
            it.studentNumber.equals(clean, ignoreCase = true)
        }
    }

    override suspend fun verifyStudentByQr(rawQrCode: String): StudentScanResult {
        val lastSync = lastSyncTime
        val isOffline = !_syncInfo.value.isOnline

        val parseResult = QrCodeUtils.parseQrCode(rawQrCode)
        return when (parseResult) {
            is QrParseResult.Invalid -> {
                StudentScanResult.InvalidQr(
                    rawScannedString = rawQrCode,
                    errorReason = parseResult.reason
                )
            }
            is QrParseResult.ValidStudentNumber -> {
                val student = getStudentByStudentNumber(parseResult.studentNumber)
                if (student != null) {
                    evaluateStudentAndCard(student, isOffline, lastSync)
                } else {
                    StudentScanResult.StudentNotFound(
                        parsedIdentifier = parseResult.studentNumber,
                        reason = "Student Number '${parseResult.studentNumber}' not found in the local gate database.",
                        isOfflineData = isOffline,
                        lastSyncTimestamp = lastSync
                    )
                }
            }
            is QrParseResult.ValidInternalId -> {
                val student = getStudentById(parseResult.internalId)
                if (student != null) {
                    evaluateStudentAndCard(student, isOffline, lastSync)
                } else {
                    StudentScanResult.StudentNotFound(
                        parsedIdentifier = parseResult.internalId,
                        reason = "Student ID '${parseResult.internalId}' not found in the local gate database.",
                        isOfflineData = isOffline,
                        lastSyncTimestamp = lastSync
                    )
                }
            }
        }
    }

    private fun evaluateStudentAndCard(
        student: Student,
        isOffline: Boolean,
        lastSync: Long
    ): StudentScanResult {
        val studentCards = _cards.value.filter { it.studentId == student.id || it.studentNumber == student.studentNumber }
        val activeCard = studentCards.firstOrNull { it.status == CardStatus.ACTIVE }

        if (activeCard == null) {
            val latestCard = studentCards.maxByOrNull { it.issueDate }
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

            return if (latestCard != null) {
                val dateStr = dateFormat.format(Date(latestCard.deactivationDate ?: latestCard.updatedAt))
                val reason = when (latestCard.status) {
                    CardStatus.LOST -> "Card ${latestCard.cardIdentifier} was reported LOST on $dateStr. Access Denied."
                    CardStatus.REPLACED -> "Card ${latestCard.cardIdentifier} was REPLACED on $dateStr. Please use active replacement card."
                    CardStatus.DEACTIVATED -> "Card ${latestCard.cardIdentifier} has been DEACTIVATED (${latestCard.reason ?: "Administrative lock"})."
                    CardStatus.ACTIVE -> "Card status unverified."
                }
                StudentScanResult.CardInactive(
                    student = student,
                    card = latestCard,
                    cardStatus = latestCard.status,
                    reason = reason,
                    isOfflineData = isOffline,
                    lastSyncTimestamp = lastSync
                )
            } else {
                val dummy = Card(
                    cardIdentifier = "NO-CARD",
                    studentId = student.id,
                    studentNumber = student.studentNumber,
                    status = CardStatus.DEACTIVATED,
                    reason = "No physical card registered"
                )
                StudentScanResult.CardInactive(
                    student = student,
                    card = dummy,
                    cardStatus = CardStatus.DEACTIVATED,
                    reason = "No active physical ID card registered for student ${student.studentNumber}.",
                    isOfflineData = isOffline,
                    lastSyncTimestamp = lastSync
                )
            }
        }

        return if (!student.isDayScholar) {
            StudentScanResult.Success(
                student = student,
                card = activeCard,
                isApproved = false,
                reason = "Student is enrolled as a Boarding student and cannot pass Day Scholar gate.",
                isOfflineData = isOffline,
                lastSyncTimestamp = lastSync
            )
        } else if (student.feesStatus == FeeStatus.OUTSTANDING) {
            val formattedAmt = String.format(Locale.US, "%,.0f", student.outstandingAmount)
            StudentScanResult.Success(
                student = student,
                card = activeCard,
                isApproved = false,
                reason = "School fees are outstanding (Balance: UGX $formattedAmt). Direct to Bursar.",
                isOfflineData = isOffline,
                lastSyncTimestamp = lastSync
            )
        } else {
            StudentScanResult.Success(
                student = student,
                card = activeCard,
                isApproved = true,
                reason = "Entry Approved: Fees Cleared & Card Active (${activeCard.cardIdentifier}).",
                isOfflineData = isOffline,
                lastSyncTimestamp = lastSync
            )
        }
    }

    override suspend fun updateFeeStatus(
        studentId: String,
        newStatus: FeeStatus,
        outstandingAmount: Double
    ): Result<Unit> {
        var found = false
        val now = System.currentTimeMillis()
        _students.update { list ->
            list.map { student ->
                if (student.id.equals(studentId, ignoreCase = true) || student.studentNumber.equals(studentId, ignoreCase = true)) {
                    found = true
                    student.copy(
                        feesStatus = newStatus,
                        outstandingAmount = if (newStatus == FeeStatus.CLEARED) 0.0 else if (outstandingAmount > 0) outstandingAmount else 450000.0,
                        updatedAt = now
                    )
                } else {
                    student
                }
            }
        }
        return if (found) Result.success(Unit) else Result.failure(Exception("Student not found"))
    }

    override suspend fun addStudent(student: Student): Result<Unit> {
        val existing = _students.value.firstOrNull {
            it.id.equals(student.id.trim(), ignoreCase = true) ||
                    it.studentNumber.equals(student.studentNumber.trim(), ignoreCase = true)
        }
        if (existing != null) {
            return Result.failure(Exception("Student with number '${student.studentNumber}' already exists"))
        }
        _students.update { list ->
            listOf(student) + list
        }
        // Issue active card for new student
        val firstCard = Card(
            id = UUID.randomUUID().toString(),
            cardIdentifier = "CRD-${student.studentNumber.removePrefix("OAK-")}-01",
            studentId = student.id,
            studentNumber = student.studentNumber,
            qrPayload = "OAKRIDGE:STU:${student.studentNumber}",
            status = CardStatus.ACTIVE,
            reason = "Initial issuance"
        )
        _cards.update { listOf(firstCard) + it }
        return Result.success(Unit)
    }

    override suspend fun updateStudent(student: Student): Result<Unit> {
        var found = false
        _students.update { list ->
            list.map { existing ->
                if (existing.id.equals(student.id.trim(), ignoreCase = true)) {
                    found = true
                    student
                } else {
                    existing
                }
            }
        }
        return if (found) Result.success(Unit) else Result.failure(Exception("Student not found"))
    }

    override suspend fun deleteStudent(studentId: String): Result<Unit> {
        _students.update { list ->
            list.filterNot { it.id.equals(studentId.trim(), ignoreCase = true) }
        }
        _cards.update { list ->
            list.filterNot { it.studentId.equals(studentId.trim(), ignoreCase = true) }
        }
        return Result.success(Unit)
    }

    override fun getCardsForStudentFlow(studentId: String): Flow<List<Card>> {
        return _cards.map { list -> list.filter { it.studentId == studentId } }
    }

    override suspend fun getCardsForStudent(studentId: String): List<Card> {
        return _cards.value.filter { it.studentId == studentId }
    }

    override suspend fun getActiveCardForStudent(studentId: String): Card? {
        return _cards.value.firstOrNull { it.studentId == studentId && it.status == CardStatus.ACTIVE }
    }

    override suspend fun issueCard(
        studentId: String,
        customIdentifier: String?,
        reason: String
    ): Result<Card> {
        val student = getStudentById(studentId) ?: return Result.failure(Exception("Student not found"))
        val now = System.currentTimeMillis()
        val count = _cards.value.count { it.studentId == studentId } + 1
        val cardIdentifier = customIdentifier ?: "CRD-${student.studentNumber.removePrefix("OAK-")}-${String.format(Locale.US, "%02d", count)}"

        val newCard = Card(
            id = UUID.randomUUID().toString(),
            cardIdentifier = cardIdentifier,
            studentId = student.id,
            studentNumber = student.studentNumber,
            status = CardStatus.ACTIVE,
            issueDate = now,
            activationDate = now,
            reason = reason,
            updatedAt = now
        )

        _cards.update { current ->
            val updatedOld = current.map {
                if (it.studentId == studentId && it.status == CardStatus.ACTIVE) {
                    it.copy(status = CardStatus.REPLACED, deactivationDate = now, replacedByCardId = newCard.id)
                } else it
            }
            listOf(newCard) + updatedOld
        }
        return Result.success(newCard)
    }

    override suspend fun reportCardLost(
        studentId: String,
        cardId: String,
        reason: String
    ): Result<Card> {
        var foundCard: Card? = null
        val now = System.currentTimeMillis()
        _cards.update { current ->
            current.map {
                if (it.id == cardId) {
                    val updated = it.copy(status = CardStatus.LOST, deactivationDate = now, reason = reason, updatedAt = now)
                    foundCard = updated
                    updated
                } else it
            }
        }
        return if (foundCard != null) Result.success(foundCard!!) else Result.failure(Exception("Card not found"))
    }

    override suspend fun issueReplacementCard(
        studentId: String,
        oldCardId: String,
        reason: String
    ): Result<Card> {
        val student = getStudentById(studentId) ?: return Result.failure(Exception("Student not found"))
        val now = System.currentTimeMillis()
        val count = _cards.value.count { it.studentId == studentId } + 1
        val newCardIdentifier = "CRD-${student.studentNumber.removePrefix("OAK-")}-${String.format(Locale.US, "%02d", count)}"

        val newCard = Card(
            id = UUID.randomUUID().toString(),
            cardIdentifier = newCardIdentifier,
            studentId = student.id,
            studentNumber = student.studentNumber,
            status = CardStatus.ACTIVE,
            issueDate = now,
            activationDate = now,
            reason = "Replacement card issued (replacing $oldCardId)",
            updatedAt = now
        )

        _cards.update { current ->
            val marked = current.map {
                if (it.id == oldCardId) {
                    it.copy(status = CardStatus.REPLACED, deactivationDate = now, replacedByCardId = newCard.id, reason = reason, updatedAt = now)
                } else it
            }
            listOf(newCard) + marked
        }
        return Result.success(newCard)
    }

    override suspend fun deactivateCard(
        studentId: String,
        cardId: String,
        reason: String
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        _cards.update { current ->
            current.map {
                if (it.id == cardId) {
                    it.copy(status = CardStatus.DEACTIVATED, deactivationDate = now, reason = reason, updatedAt = now)
                } else it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun logVerificationScan(log: ScanLog) {
        _scanLogs.update { current ->
            listOf(log) + current
        }
        if (!_syncInfo.value.isOnline) {
            _syncInfo.update { it.copy(pendingLogsCount = it.pendingLogsCount + 1, status = SyncStatus.OFFLINE) }
        }
    }

    override suspend fun clearScanLogs() {
        _scanLogs.value = emptyList()
        _syncInfo.update { it.copy(pendingLogsCount = 0) }
    }

    override suspend fun syncWithCloud(): Result<SyncSummary> {
        val now = System.currentTimeMillis()
        lastSyncTime = now
        val pendingCount = _syncInfo.value.pendingLogsCount
        _scanLogs.update { list -> list.map { it.copy(isSyncedToCloud = true) } }
        _syncInfo.value = _syncInfo.value.copy(
            status = SyncStatus.SYNCED,
            lastSyncTimestamp = now,
            pendingLogsCount = 0,
            errorMessage = null
        )
        return Result.success(
            SyncSummary(
                studentsDownloaded = 0,
                studentsUpdated = 0,
                logsUploaded = pendingCount,
                timestamp = now
            )
        )
    }

    override fun setNetworkOnline(isOnline: Boolean) {
        _syncInfo.update {
            it.copy(
                isOnline = isOnline,
                status = if (isOnline) SyncStatus.SYNCED else SyncStatus.OFFLINE
            )
        }
    }

    override suspend fun resetToSampleData() {
        _students.value = StudentDataSamples.createInitialStudents()
        _cards.value = StudentDataSamples.createInitialCards(_students.value)
        _scanLogs.value = StudentDataSamples.createInitialScanLogs()
        lastSyncTime = System.currentTimeMillis()
        _syncInfo.value = SyncInfo(
            status = SyncStatus.SYNCED,
            lastSyncTimestamp = lastSyncTime,
            isOnline = true,
            pendingLogsCount = 0
        )
    }

    override fun getLastSyncTimestamp(): Long = lastSyncTime

    companion object {
        @Volatile
        private var instance: MockStudentRepository? = null

        fun getInstance(): MockStudentRepository {
            return instance ?: synchronized(this) {
                instance ?: MockStudentRepository().also { instance = it }
            }
        }
    }
}
