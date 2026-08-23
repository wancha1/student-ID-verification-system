package com.example.data

import com.example.data.local.AppDatabase
import com.example.data.local.CardEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.data.sync.InMemoryCloudBackend
import com.example.data.sync.RemoteCloudDataSource
import com.example.data.sync.SyncManager
import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.model.SyncInfo
import com.example.model.SyncSummary
import com.example.util.QrCodeUtils
import com.example.util.QrParseResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class RoomStudentRepository(
    private val database: AppDatabase,
    private val remoteCloudDataSource: RemoteCloudDataSource = InMemoryCloudBackend(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : StudentRepository {

    private val syncManager = SyncManager(database, remoteCloudDataSource, ioDispatcher)

    override val studentsFlow: Flow<List<Student>> = database.studentDao().getAllActiveStudents()
        .map { entities -> entities.map { it.toDomain() } }

    override val scanLogsFlow: Flow<List<ScanLog>> = database.scanLogDao().getAllLogsFlow()
        .map { entities -> entities.map { it.toDomain() } }

    override val syncInfoFlow: Flow<SyncInfo> = syncManager.syncInfo

    suspend fun initialize() = withContext(ioDispatcher) {
        // Pre-populate Room with initial sample data if DB is empty
        val count = database.studentDao().getActiveCount()
        if (count == 0) {
            resetToSampleData()
        }
        syncManager.initialize()
    }

    override suspend fun getStudentById(id: String): Student? = withContext(ioDispatcher) {
        database.studentDao().getStudentById(id)?.toDomain()
    }

    override suspend fun getStudentByStudentNumber(studentNumber: String): Student? = withContext(ioDispatcher) {
        database.studentDao().getStudentByStudentNumber(studentNumber.trim().uppercase())?.toDomain()
    }

    /**
     * Complete Hierarchical Gate Verification Decision Tree:
     * 1. QR valid format? -> NO: INVALID QR CODE
     * 2. QR recognized (Student found)? -> NO: STUDENT NOT FOUND
     * 3. Card active? -> NO: CARD INACTIVE (LOST, REPLACED, DEACTIVATED)
     * 4. Student day scholar eligible? -> NO: NOT APPROVED
     * 5. Fees cleared? -> NO: NOT APPROVED (OUTSTANDING)
     * 6. Everything valid? -> YES: ENTRY APPROVED
     */
    override suspend fun verifyStudentByQr(rawQrCode: String): StudentScanResult = withContext(ioDispatcher) {
        val lastSync = syncManager.getLastSyncTimestamp()
        val isOffline = !syncManager.syncInfo.value.isOnline

        val parseResult = QrCodeUtils.parseQrCode(rawQrCode)
        when (parseResult) {
            is QrParseResult.Invalid -> {
                StudentScanResult.InvalidQr(
                    rawScannedString = rawQrCode,
                    errorReason = parseResult.reason
                )
            }
            is QrParseResult.ValidStudentNumber -> {
                val student = database.studentDao().getStudentByStudentNumber(parseResult.studentNumber)?.toDomain()
                if (student != null) {
                    evaluateStudentAndCardAccess(student, isOffline, lastSync)
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
                val student = database.studentDao().getStudentById(parseResult.internalId)?.toDomain()
                if (student != null) {
                    evaluateStudentAndCardAccess(student, isOffline, lastSync)
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

    private suspend fun evaluateStudentAndCardAccess(
        student: Student,
        isOffline: Boolean,
        lastSync: Long
    ): StudentScanResult {
        // Retrieve cards for this student
        val cards = database.cardDao().getCardsForStudent(student.id).map { it.toDomain() }
        val activeCard = cards.firstOrNull { it.status == CardStatus.ACTIVE }

        // If no active card, check inactive card status
        if (activeCard == null) {
            val latestCard = cards.maxByOrNull { it.issueDate }
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

            return if (latestCard != null) {
                val dateStr = dateFormat.format(Date(latestCard.deactivationDate ?: latestCard.updatedAt))
                val reason = when (latestCard.status) {
                    CardStatus.LOST -> "Card ${latestCard.cardIdentifier} was reported LOST on $dateStr. Access Denied."
                    CardStatus.REPLACED -> "Card ${latestCard.cardIdentifier} was REPLACED on $dateStr. Please present the newly issued active card."
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
                // No card record found for this registered student
                val dummyCard = Card(
                    cardIdentifier = "NO-CARD",
                    studentId = student.id,
                    studentNumber = student.studentNumber,
                    status = CardStatus.DEACTIVATED,
                    reason = "No physical card ever issued"
                )
                StudentScanResult.CardInactive(
                    student = student,
                    card = dummyCard,
                    cardStatus = CardStatus.DEACTIVATED,
                    reason = "No active physical ID card is registered for student ${student.studentNumber}. Direct to Administration.",
                    isOfflineData = isOffline,
                    lastSyncTimestamp = lastSync
                )
            }
        }

        // Active Card exists: Check student gate authorization criteria
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
    ): Result<Unit> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        try {
            database.studentDao().updateFeeStatus(
                studentId = studentId,
                newStatus = newStatus.name,
                outstandingAmount = outstandingAmount,
                updatedAt = now
            )

            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.updateRemoteFeeStatus(studentId, newStatus, outstandingAmount, now)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addStudent(student: Student): Result<Unit> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val studentWithTimestamp = student.copy(updatedAt = now)
        val entity = StudentEntity.fromDomain(studentWithTimestamp)

        try {
            database.studentDao().insertOrUpdateStudent(entity)

            // Automatically issue first active card for the new student
            val firstCard = Card(
                id = UUID.randomUUID().toString(),
                cardIdentifier = "CRD-${student.studentNumber.removePrefix("OAK-")}-01",
                studentId = student.id,
                studentNumber = student.studentNumber,
                qrPayload = "OAKRIDGE:STU:${student.studentNumber}",
                status = CardStatus.ACTIVE,
                issueDate = now,
                activationDate = now,
                reason = "Initial enrollment card issuance",
                updatedAt = now
            )
            database.cardDao().insertOrUpdateCard(CardEntity.fromDomain(firstCard))

            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.pushStudentChanges(listOf(entity))
                remoteCloudDataSource.pushCardChanges(listOf(CardEntity.fromDomain(firstCard)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateStudent(student: Student): Result<Unit> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val studentWithTimestamp = student.copy(updatedAt = now)
        val entity = StudentEntity.fromDomain(studentWithTimestamp)

        try {
            database.studentDao().insertOrUpdateStudent(entity)
            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.pushStudentChanges(listOf(entity))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteStudent(studentId: String): Result<Unit> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        try {
            database.studentDao().softDeleteStudent(studentId, now)
            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.deleteRemoteStudent(studentId, now)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Card Lifecycle Management
    override fun getCardsForStudentFlow(studentId: String): Flow<List<Card>> {
        return database.cardDao().getCardsForStudentFlow(studentId)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getCardsForStudent(studentId: String): List<Card> = withContext(ioDispatcher) {
        database.cardDao().getCardsForStudent(studentId).map { it.toDomain() }
    }

    override suspend fun getActiveCardForStudent(studentId: String): Card? = withContext(ioDispatcher) {
        database.cardDao().getActiveCardForStudent(studentId)?.toDomain()
    }

    override suspend fun issueCard(
        studentId: String,
        customIdentifier: String?,
        reason: String
    ): Result<Card> = withContext(ioDispatcher) {
        val student = database.studentDao().getStudentById(studentId)
            ?: return@withContext Result.failure(NoSuchElementException("Student $studentId not found"))

        val now = System.currentTimeMillis()
        val existingCards = database.cardDao().getCardsForStudent(studentId)
        val seqNumber = existingCards.size + 1
        val formattedSeq = String.format(Locale.US, "%02d", seqNumber)
        val cardIdentifier = customIdentifier ?: "CRD-${student.studentNumber.removePrefix("OAK-")}-$formattedSeq"

        val newCard = Card(
            id = UUID.randomUUID().toString(),
            cardIdentifier = cardIdentifier,
            studentId = student.id,
            studentNumber = student.studentNumber,
            qrPayload = "OAKRIDGE:STU:${student.studentNumber}",
            status = CardStatus.ACTIVE,
            issueDate = now,
            activationDate = now,
            reason = reason,
            updatedAt = now
        )

        try {
            // Mark previous active cards as REPLACED
            database.cardDao().markActiveCardsReplaced(
                studentId = student.id,
                newCardId = newCard.id,
                deactivationDate = now,
                reason = "Replaced by new card $cardIdentifier",
                updatedAt = now
            )
            database.cardDao().insertOrUpdateCard(CardEntity.fromDomain(newCard))

            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.pushCardChanges(listOf(CardEntity.fromDomain(newCard)))
            }
            Result.success(newCard)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reportCardLost(
        studentId: String,
        cardId: String,
        reason: String
    ): Result<Card> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val cardEntity = database.cardDao().getCardById(cardId)
            ?: return@withContext Result.failure(NoSuchElementException("Card $cardId not found"))

        val updated = cardEntity.copy(
            status = CardStatus.LOST.name,
            deactivationDate = now,
            reason = reason,
            updatedAt = now
        )

        try {
            database.cardDao().insertOrUpdateCard(updated)
            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.updateRemoteCard(updated)
            }
            Result.success(updated.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun issueReplacementCard(
        studentId: String,
        oldCardId: String,
        reason: String
    ): Result<Card> = withContext(ioDispatcher) {
        val student = database.studentDao().getStudentById(studentId)
            ?: return@withContext Result.failure(NoSuchElementException("Student $studentId not found"))

        val now = System.currentTimeMillis()
        val existingCards = database.cardDao().getCardsForStudent(studentId)
        val seqNumber = existingCards.size + 1
        val formattedSeq = String.format(Locale.US, "%02d", seqNumber)
        val newCardIdentifier = "CRD-${student.studentNumber.removePrefix("OAK-")}-$formattedSeq"

        val newCard = Card(
            id = UUID.randomUUID().toString(),
            cardIdentifier = newCardIdentifier,
            studentId = student.id,
            studentNumber = student.studentNumber,
            qrPayload = "OAKRIDGE:STU:${student.studentNumber}",
            status = CardStatus.ACTIVE,
            issueDate = now,
            activationDate = now,
            reason = "Replacement card issued (replacing $oldCardId: $reason)",
            notes = "Issued on ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(now))}",
            updatedAt = now
        )

        try {
            // Update old card status to REPLACED or LOST
            database.cardDao().updateCardStatus(
                cardId = oldCardId,
                newStatus = CardStatus.REPLACED.name,
                deactivationDate = now,
                replacedByCardId = newCard.id,
                reason = reason,
                updatedAt = now
            )

            database.cardDao().insertOrUpdateCard(CardEntity.fromDomain(newCard))

            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.pushCardChanges(listOf(CardEntity.fromDomain(newCard)))
            }
            Result.success(newCard)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deactivateCard(
        studentId: String,
        cardId: String,
        reason: String
    ): Result<Unit> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val cardEntity = database.cardDao().getCardById(cardId)
            ?: return@withContext Result.failure(NoSuchElementException("Card $cardId not found"))

        val updated = cardEntity.copy(
            status = CardStatus.DEACTIVATED.name,
            deactivationDate = now,
            reason = reason,
            updatedAt = now
        )

        try {
            database.cardDao().insertOrUpdateCard(updated)
            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.updateRemoteCard(updated)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logVerificationScan(log: ScanLog) = withContext(ioDispatcher) {
        val entity = ScanLogEntity.fromDomain(log)
        database.scanLogDao().insertLog(entity)
        syncManager.notifyLocalLogAdded()
    }

    override suspend fun clearScanLogs() = withContext(ioDispatcher) {
        database.scanLogDao().clearAllLogs()
    }

    override suspend fun syncWithCloud(): Result<SyncSummary> = withContext(ioDispatcher) {
        syncManager.syncNow()
    }

    override fun setNetworkOnline(isOnline: Boolean) {
        syncManager.setNetworkConnectivity(isOnline)
    }

    override suspend fun resetToSampleData() = withContext(ioDispatcher) {
        val sampleStudents = StudentDataSamples.createInitialStudents()
        val sampleCards = StudentDataSamples.createInitialCards(sampleStudents)
        val sampleLogs = StudentDataSamples.createInitialScanLogs()

        database.studentDao().clearAllStudents()
        database.cardDao().clearAllCards()
        database.scanLogDao().clearAllLogs()

        database.studentDao().insertOrUpdateStudents(sampleStudents.map { StudentEntity.fromDomain(it) })
        database.cardDao().insertOrUpdateCards(sampleCards.map { CardEntity.fromDomain(it) })
        database.scanLogDao().insertLogs(sampleLogs.map { ScanLogEntity.fromDomain(it) })

        if (remoteCloudDataSource is InMemoryCloudBackend) {
            remoteCloudDataSource.resetRemoteState()
        }
        syncManager.initialize()
    }

    override fun getLastSyncTimestamp(): Long = syncManager.getLastSyncTimestamp()

    fun getSyncManager(): SyncManager = syncManager

    companion object {
        @Volatile
        private var instance: RoomStudentRepository? = null

        fun getInstance(context: android.content.Context): RoomStudentRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val db = AppDatabase.getInstance(context.applicationContext)
                    RoomStudentRepository(db).also { repo ->
                        instance = repo
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            repo.initialize()
                        }
                    }
                }
            }
        }
    }
}
