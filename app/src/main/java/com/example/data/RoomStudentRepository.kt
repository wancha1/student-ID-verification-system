package com.example.data

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.CardEntity
import com.example.data.local.PendingChangeEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.data.local.StudentProfileEntity
import com.example.data.local.SyncEntityType
import com.example.data.local.SyncOperationType
import com.example.data.sync.InMemoryCloudBackend
import com.example.data.sync.RemoteCloudDataSource
import com.example.data.sync.SyncManager
import com.example.model.AccessStatus
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
import com.example.util.CardCryptoManager
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

    private val _guardianNotifications = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.model.GuardianNotification>>(emptyList())
    override val guardianNotificationsFlow: Flow<List<com.example.model.GuardianNotification>> = _guardianNotifications

    private val _exeatPasses = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.model.ExeatPass>>(emptyList())
    override val exeatPassesFlow: Flow<List<com.example.model.ExeatPass>> = _exeatPasses

    override val syncInfoFlow: Flow<SyncInfo> = syncManager.syncInfo

    val pendingChangesFlow: Flow<List<PendingChangeEntity>> = database.pendingChangeDao().getPendingChangesFlow()
    val pendingCountFlow: Flow<Int> = database.pendingChangeDao().getPendingCountFlow()

    internal var testFailureInterceptor: ((operation: String) -> Unit)? = null

    suspend fun initialize() = withContext(ioDispatcher) {
        syncManager.initialize()
        seedInitialDataIfEmpty()
    }

    override suspend fun getStudentById(id: String): Student? = withContext(ioDispatcher) {
        database.studentDao().getStudentById(id)?.toDomain()
    }

    override suspend fun getStudentByStudentNumber(studentNumber: String): Student? = withContext(ioDispatcher) {
        database.studentDao().getStudentByStudentNumber(studentNumber.trim().uppercase())?.toDomain()
    }

    override suspend fun getStudentByCardIdentifier(cardIdentifier: String): Student? = withContext(ioDispatcher) {
        val cleanCardId = cardIdentifier.trim().uppercase()
        val cardEntity = database.cardDao().getCardByIdentifier(cleanCardId)
            ?: database.cardDao().getCardById(cleanCardId)
            ?: return@withContext null

        val studentEntity = database.studentDao().getStudentById(cardEntity.studentId)
            ?: database.studentDao().getStudentByStudentNumber(cardEntity.studentNumber)
            ?: return@withContext null

        if (studentEntity.isDeleted) return@withContext null
        studentEntity.toDomain()
    }

    /**
     * Complete Hierarchical Gate Verification Decision Tree:
     * 1. QR valid format? -> NO: INVALID QR CODE
     * 2. Cryptographic signature authentic? -> NO: INVALID QR CODE
     * 3. QR recognized (Exact Card record found)? -> NO: STUDENT NOT FOUND / UNREGISTERED CARD
     * 4. Card active? -> NO: CARD INACTIVE (LOST, REPLACED, DEACTIVATED) - NO fallback to another card!
     * 5. Associated student active and not deleted? -> NO: STUDENT NOT FOUND
     * 6. Student day scholar eligible? -> NO: NOT APPROVED
     * 7. Fees cleared? -> NO: NOT APPROVED (OUTSTANDING)
     * 8. Everything valid? -> YES: ENTRY APPROVED
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
            is QrParseResult.LegacyUnsigned -> {
                StudentScanResult.InvalidQr(
                    rawScannedString = rawQrCode,
                    errorReason = "Rejected: Legacy unsigned QR badge format (${parseResult.formatDescription}) is not permitted on the production gate verification path. Reissue to authenticated V2 badge required."
                )
            }
            is QrParseResult.ValidV2Card -> {
                // 1. Cryptographically verify signature using trusted public key
                val isAuthentic = CardCryptoManager.verifyCardSignature(
                    cardId = parseResult.cardId,
                    signatureBase64Url = parseResult.signature
                )
                if (!isAuthentic) {
                    return@withContext StudentScanResult.InvalidQr(
                        rawScannedString = rawQrCode,
                        errorReason = "Cryptographic signature verification failed for card '${parseResult.cardId}'. Potential forgery, tampering, or invalid issuer key."
                    )
                }

                // 2. Query exact card record corresponding to the scanned cardId
                val cardEntity = database.cardDao().getCardByIdentifier(parseResult.cardId)
                    ?: database.cardDao().getCardById(parseResult.cardId)

                if (cardEntity == null) {
                    return@withContext StudentScanResult.StudentNotFound(
                        parsedIdentifier = parseResult.cardId,
                        reason = "Card '${parseResult.cardId}' is cryptographically authentic but not registered in the school database.",
                        isOfflineData = isOffline,
                        lastSyncTimestamp = lastSync
                    )
                }

                val card = cardEntity.toDomain()

                // 3. Card MUST be ACTIVE. Absolutely NO fallback to another active card!
                if (card.status != CardStatus.ACTIVE) {
                    val studentEntity = database.studentDao().getStudentById(card.studentId)
                        ?: database.studentDao().getStudentByStudentNumber(card.studentNumber)
                    val student = studentEntity?.toDomain() ?: Student(
                        id = card.studentId,
                        studentNumber = card.studentNumber,
                        firstName = "Student",
                        lastName = "Cardholder",
                        gradeClass = "Unknown"
                    )

                    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
                    val dateStr = dateFormat.format(Date(card.deactivationDate ?: card.updatedAt))
                    val reason = when (card.status) {
                        CardStatus.LOST -> "Card ${card.cardIdentifier} was reported LOST on $dateStr. Access Denied."
                        CardStatus.REPLACED -> "Card ${card.cardIdentifier} was REPLACED on $dateStr. Access Denied. Please present newly issued active card."
                        CardStatus.DEACTIVATED -> "Card ${card.cardIdentifier} has been DEACTIVATED (${card.reason ?: "Administrative lock"}). Access Denied."
                        CardStatus.ACTIVE -> "Card status unverified."
                    }
                    return@withContext StudentScanResult.CardInactive(
                        student = student,
                        card = card,
                        cardStatus = card.status,
                        reason = reason,
                        isOfflineData = isOffline,
                        lastSyncTimestamp = lastSync
                    )
                }

                // 4. Retrieve associated student and ensure active/not deleted
                val studentEntity = database.studentDao().getStudentById(card.studentId)
                    ?: database.studentDao().getStudentByStudentNumber(card.studentNumber)

                if (studentEntity == null || studentEntity.isDeleted) {
                    return@withContext StudentScanResult.StudentNotFound(
                        parsedIdentifier = parseResult.cardId,
                        reason = "Associated student record (${card.studentNumber}) was not found or has been deactivated/deleted.",
                        isOfflineData = isOffline,
                        lastSyncTimestamp = lastSync
                    )
                }

                val student = studentEntity.toDomain()

                // 5. Evaluate fee and day-scholar gate access rules
                if (!student.isDayScholar) {
                    StudentScanResult.Success(
                        student = student,
                        card = card,
                        isApproved = false,
                        reason = "Student is enrolled as a Boarding student and cannot pass Day Scholar gate.",
                        isOfflineData = isOffline,
                        lastSyncTimestamp = lastSync
                    )
                } else if (student.feesStatus == FeeStatus.OUTSTANDING) {
                    val formattedAmt = String.format(Locale.US, "%,.0f", student.outstandingAmount)
                    StudentScanResult.Success(
                        student = student,
                        card = card,
                        isApproved = false,
                        reason = "School fees are outstanding (Balance: UGX $formattedAmt). Direct to Bursar.",
                        isOfflineData = isOffline,
                        lastSyncTimestamp = lastSync
                    )
                } else {
                    StudentScanResult.Success(
                        student = student,
                        card = card,
                        isApproved = true,
                        reason = "Entry Approved: Authentic V2 Card (${card.cardIdentifier}) verified & Fees Cleared.",
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
        lastSync: Long,
        scannedCardIdentifier: String? = null
    ): StudentScanResult {
        // Retrieve cards for this student
        val cards = database.cardDao().getCardsForStudent(student.id).map { it.toDomain() }

        // If a specific card was scanned, check its exact status first
        if (scannedCardIdentifier != null) {
            val specificCard = database.cardDao().getCardByIdentifier(scannedCardIdentifier)?.toDomain()
                ?: cards.firstOrNull { it.cardIdentifier.equals(scannedCardIdentifier, ignoreCase = true) }

            if (specificCard != null && specificCard.status != CardStatus.ACTIVE) {
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
                val dateStr = dateFormat.format(Date(specificCard.deactivationDate ?: specificCard.updatedAt))
                val reason = when (specificCard.status) {
                    CardStatus.LOST -> "Card ${specificCard.cardIdentifier} was reported LOST on $dateStr. Access Denied."
                    CardStatus.REPLACED -> "Card ${specificCard.cardIdentifier} was REPLACED on $dateStr. Please present the newly issued active card."
                    CardStatus.DEACTIVATED -> "Card ${specificCard.cardIdentifier} has been DEACTIVATED (${specificCard.reason ?: "Administrative lock"})."
                    CardStatus.ACTIVE -> "Card status unverified."
                }

                return StudentScanResult.CardInactive(
                    student = student,
                    card = specificCard,
                    cardStatus = specificCard.status,
                    reason = reason,
                    isOfflineData = isOffline,
                    lastSyncTimestamp = lastSync
                )
            }
        }

        val activeCard = if (scannedCardIdentifier != null) {
            val specificCard = database.cardDao().getCardByIdentifier(scannedCardIdentifier)?.toDomain()
            if (specificCard?.status == CardStatus.ACTIVE) specificCard else cards.firstOrNull { it.status == CardStatus.ACTIVE }
        } else {
            cards.firstOrNull { it.status == CardStatus.ACTIVE }
        }

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
            val student = database.studentDao().getStudentById(studentId)?.toDomain()
            val newAccessStatus = if (student != null) {
                AccessStatus.evaluate(student.isDayScholar, newStatus)
            } else {
                if (newStatus == FeeStatus.CLEARED) AccessStatus.APPROVED else AccessStatus.RESTRICTED_FEES
            }

            database.withTransaction {
                database.studentDao().updateFeeStatus(
                    studentId = studentId,
                    newStatus = newStatus.name,
                    outstandingAmount = outstandingAmount,
                    updatedAt = now
                )
                database.studentDao().updateAccessStatus(
                    studentId = studentId,
                    newStatus = newAccessStatus.name,
                    updatedAt = now
                )
                database.studentProfileDao().updateAccessStatus(
                    studentId = studentId,
                    newStatus = newAccessStatus.name,
                    updatedAt = now
                )

                testFailureInterceptor?.invoke("updateFeeStatus_afterLocalWrites")

                // Durable offline tracking: Record fee status mutation in pending queue
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.FEE_STATUS,
                        recordId = studentId,
                        operationType = SyncOperationType.STATUS_CHANGE,
                        payloadJson = "{\"feesStatus\":\"${newStatus.name}\",\"outstandingAmount\":$outstandingAmount}",
                        createdAt = now
                    )
                )
            }

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

        // Automatically issue first active card for the new student
        val cleanStudentNum = student.studentNumber.removePrefix("LTC-").removePrefix("OAK-")
        val cardId = "CRD-$cleanStudentNum-01"
        val firstCard = Card(
            id = UUID.randomUUID().toString(),
            cardIdentifier = cardId,
            studentId = student.id,
            studentNumber = student.studentNumber,
            qrPayload = QrCodeUtils.createPayload(student.studentNumber, cardId),
            status = CardStatus.ACTIVE,
            issueDate = now,
            activationDate = now,
            reason = "Initial enrollment card issuance",
            updatedAt = now
        )
        val cardEntity = CardEntity.fromDomain(firstCard)

        try {
            database.withTransaction {
                database.studentDao().insertOrUpdateStudent(entity)
                database.studentProfileDao().insertOrUpdateProfile(StudentProfileEntity.fromStudent(studentWithTimestamp))
                database.cardDao().insertOrUpdateCard(cardEntity)

                testFailureInterceptor?.invoke("addStudent_afterLocalWrites")

                // Durable offline tracking: Record student and initial card creation
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.STUDENT,
                        recordId = student.id,
                        operationType = SyncOperationType.CREATE,
                        createdAt = now
                    )
                )
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.CARD,
                        recordId = firstCard.id,
                        operationType = SyncOperationType.CREATE,
                        createdAt = now
                    )
                )
            }

            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.pushStudentChanges(listOf(entity))
                remoteCloudDataSource.pushCardChanges(listOf(cardEntity))
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
            database.withTransaction {
                database.studentDao().insertOrUpdateStudent(entity)
                database.studentProfileDao().insertOrUpdateProfile(StudentProfileEntity.fromStudent(studentWithTimestamp))

                testFailureInterceptor?.invoke("updateStudent_afterLocalWrites")

                // Durable offline tracking: Record student update
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.STUDENT,
                        recordId = student.id,
                        operationType = SyncOperationType.UPDATE,
                        createdAt = now
                    )
                )
            }

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
            database.withTransaction {
                database.studentDao().softDeleteStudent(studentId, now)
                database.studentProfileDao().deleteProfileById(studentId)

                testFailureInterceptor?.invoke("deleteStudent_afterLocalWrites")

                // Durable offline tracking: Record soft deletion
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.STUDENT,
                        recordId = studentId,
                        operationType = SyncOperationType.DELETE,
                        createdAt = now
                    )
                )
            }

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
        val cardIdentifier = customIdentifier?.trim()?.uppercase() ?: CardCryptoManager.generateSecureRandomCardId()
        val signedPayload = CardCryptoManager.signCardPayload(cardIdentifier)

        val newCard = Card(
            id = UUID.randomUUID().toString(),
            cardIdentifier = cardIdentifier,
            studentId = student.id,
            studentNumber = student.studentNumber,
            qrPayload = signedPayload,
            status = CardStatus.ACTIVE,
            issueDate = now,
            activationDate = now,
            reason = reason,
            updatedAt = now
        )

        try {
            database.withTransaction {
                // Mark previous active cards as REPLACED
                database.cardDao().markActiveCardsReplaced(
                    studentId = student.id,
                    newCardId = newCard.id,
                    deactivationDate = now,
                    reason = "Replaced by new card $cardIdentifier",
                    updatedAt = now
                )
                database.cardDao().insertOrUpdateCard(CardEntity.fromDomain(newCard))

                testFailureInterceptor?.invoke("issueCard_afterLocalWrites")

                // Durable offline tracking: Record newly issued card
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.CARD,
                        recordId = newCard.id,
                        operationType = SyncOperationType.CREATE,
                        createdAt = now
                    )
                )
            }

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
            database.withTransaction {
                database.cardDao().insertOrUpdateCard(updated)

                testFailureInterceptor?.invoke("reportCardLost_afterLocalWrites")

                // Durable offline tracking: Record lost card status change
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.CARD,
                        recordId = cardId,
                        operationType = SyncOperationType.STATUS_CHANGE,
                        payloadJson = "{\"status\":\"LOST\",\"reason\":\"$reason\"}",
                        createdAt = now
                    )
                )
            }

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
        val newCardIdentifier = CardCryptoManager.generateSecureRandomCardId()
        val signedPayload = CardCryptoManager.signCardPayload(newCardIdentifier)

        val newCard = Card(
            id = UUID.randomUUID().toString(),
            cardIdentifier = newCardIdentifier,
            studentId = student.id,
            studentNumber = student.studentNumber,
            qrPayload = signedPayload,
            status = CardStatus.ACTIVE,
            issueDate = now,
            activationDate = now,
            reason = "Replacement card issued (replacing $oldCardId: $reason)",
            notes = "Issued on ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(now))}",
            updatedAt = now
        )

        try {
            database.withTransaction {
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

                testFailureInterceptor?.invoke("issueReplacementCard_afterLocalWrites")

                // Durable offline tracking: Record replacement card and previous card status change
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.CARD,
                        recordId = oldCardId,
                        operationType = SyncOperationType.STATUS_CHANGE,
                        payloadJson = "{\"status\":\"REPLACED\",\"replacedBy\":\"${newCard.id}\"}",
                        createdAt = now
                    )
                )
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.CARD,
                        recordId = newCard.id,
                        operationType = SyncOperationType.CREATE,
                        createdAt = now
                    )
                )
            }

            if (syncManager.syncInfo.value.isOnline) {
                remoteCloudDataSource.pushCardChanges(listOf(CardEntity.fromDomain(newCard)))
            }
            Result.success(newCard)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reissueCardToSecureV2(studentId: String): Result<Card> = withContext(ioDispatcher) {
        val student = database.studentDao().getStudentById(studentId)
            ?: return@withContext Result.failure(NoSuchElementException("Student $studentId not found"))

        val now = System.currentTimeMillis()
        val newCardIdentifier = CardCryptoManager.generateSecureRandomCardId()
        val signedPayload = CardCryptoManager.signCardPayload(newCardIdentifier)

        val newCard = Card(
            id = UUID.randomUUID().toString(),
            cardIdentifier = newCardIdentifier,
            studentId = student.id,
            studentNumber = student.studentNumber,
            qrPayload = signedPayload,
            status = CardStatus.ACTIVE,
            issueDate = now,
            activationDate = now,
            reason = "Reissued to cryptographically authentic V2 card",
            notes = "Issued with Ed25519 digital signature",
            updatedAt = now
        )

        try {
            database.withTransaction {
                // Mark previous active cards as REPLACED
                database.cardDao().markActiveCardsReplaced(
                    studentId = student.id,
                    newCardId = newCard.id,
                    deactivationDate = now,
                    reason = "Upgraded to secure V2 card $newCardIdentifier",
                    updatedAt = now
                )
                database.cardDao().insertOrUpdateCard(CardEntity.fromDomain(newCard))

                // Durable offline tracking
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.CARD,
                        recordId = newCard.id,
                        operationType = SyncOperationType.CREATE,
                        createdAt = now
                    )
                )
            }

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
            database.withTransaction {
                database.cardDao().insertOrUpdateCard(updated)

                testFailureInterceptor?.invoke("deactivateCard_afterLocalWrites")

                // Durable offline tracking: Record card deactivation
                database.pendingChangeDao().enqueueChange(
                    PendingChangeEntity(
                        changeId = UUID.randomUUID().toString(),
                        entityType = SyncEntityType.CARD,
                        recordId = cardId,
                        operationType = SyncOperationType.STATUS_CHANGE,
                        payloadJson = "{\"status\":\"DEACTIVATED\",\"reason\":\"$reason\"}",
                        createdAt = now
                    )
                )
            }

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

    override suspend fun issueExeatPass(pass: com.example.model.ExeatPass): Result<Unit> = withContext(ioDispatcher) {
        _exeatPasses.value = listOf(pass) + _exeatPasses.value
        val notif = com.example.model.GuardianNotification(
            studentId = pass.studentId,
            studentNumber = pass.studentNumber,
            studentName = pass.studentName,
            guardianName = "Guardian of ${pass.studentName}",
            guardianPhone = pass.guardianContact,
            type = com.example.model.NotificationType.EXEAT_PASS_ISSUED,
            message = "🎫 OFFICIAL EXEAT PASS: ${pass.passNumber} issued for ${pass.studentName} (${pass.reason.name.replace("_", " ")}). Destination: ${pass.destination}.",
            timestamp = System.currentTimeMillis(),
            isDelivered = true
        )
        _guardianNotifications.value = listOf(notif) + _guardianNotifications.value
        Result.success(Unit)
    }

    override suspend fun markExeatPassUsed(passId: String): Result<Unit> = withContext(ioDispatcher) {
        _exeatPasses.value = _exeatPasses.value.map {
            if (it.id == passId) it.copy(status = com.example.model.ExeatStatus.USED) else it
        }
        Result.success(Unit)
    }

    override suspend fun sendGuardianNotification(notification: com.example.model.GuardianNotification): Result<Unit> = withContext(ioDispatcher) {
        _guardianNotifications.value = listOf(notification) + _guardianNotifications.value
        Result.success(Unit)
    }

    override suspend fun resetToSampleData() = withContext(ioDispatcher) {
        clearAllData()
        seedInitialDataIfEmpty()
    }

    suspend fun clearAllData() = withContext(ioDispatcher) {
        database.studentDao().clearAllStudents()
        database.studentProfileDao().clearAllProfiles()
        database.cardDao().clearAllCards()
        database.scanLogDao().clearAllLogs()
        _guardianNotifications.value = emptyList()
        _exeatPasses.value = emptyList()

        if (remoteCloudDataSource is InMemoryCloudBackend) {
            remoteCloudDataSource.resetRemoteState()
        }
        syncManager.initialize()
    }

    override fun getLastSyncTimestamp(): Long = syncManager.getLastSyncTimestamp()

    fun getSyncManager(): SyncManager = syncManager

    override suspend fun allocateNextStudentNumber(year: Int): String = withContext(ioDispatcher) {
        val prefix = "LTC-$year-"
        val lastNumber = database.studentDao().getLastStudentNumber("$prefix%")
        val nextSeq = if (lastNumber != null && lastNumber.startsWith(prefix)) {
            val suffix = lastNumber.removePrefix(prefix).takeWhile { it.isDigit() }
            (suffix.toIntOrNull() ?: 0) + 1
        } else {
            val count = database.studentDao().getActiveCount()
            count + 1
        }
        String.format(Locale.US, "LTC-%d-%04d", year, nextSeq)
    }

    private suspend fun seedInitialDataIfEmpty() {
        if (database.studentDao().getActiveCount() > 0) return
        val now = System.currentTimeMillis()
        val initialStudents = listOf(
            Student(
                id = "stu-ltc-001",
                studentNumber = "LTC-2026-0001",
                firstName = "Emmanuel",
                lastName = "Okello",
                gradeClass = "Senior 3-A",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
                transportRoute = "Bus #1 - Lira Main Line",
                feesStatus = FeeStatus.CLEARED,
                outstandingAmount = 0.0,
                gender = "Male",
                avatarColorSeed = 0xFF1E3A8A,
                guardianName = "Okello Patrick",
                guardianPhone = "+256 772 123456",
                emergencyContact = "+256 772 123456",
                homeroomTeacher = "Mr. Obua Denis",
                academicYear = "2026",
                notes = "Student council representative. All term supplies verified.",
                qrToken = "LTC0001TOK",
                updatedAt = now
            ),
            Student(
                id = "stu-ltc-002",
                studentNumber = "LTC-2026-0002",
                firstName = "Sarah",
                lastName = "Akello",
                gradeClass = "Senior 4-B",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
                transportRoute = "Bicycle / Walking - Junior Quarters",
                feesStatus = FeeStatus.CLEARED,
                outstandingAmount = 0.0,
                gender = "Female",
                avatarColorSeed = 0xFF0D9488,
                guardianName = "Akello Mary",
                guardianPhone = "+256 782 234567",
                emergencyContact = "+256 782 234567",
                homeroomTeacher = "Ms. Aceng Betty",
                academicYear = "2026",
                notes = "Science club president. Cleared for laboratory access.",
                qrToken = "LTC0002TOK",
                updatedAt = now
            ),
            Student(
                id = "stu-ltc-003",
                studentNumber = "LTC-2026-0003",
                firstName = "Moses",
                lastName = "Opio",
                gradeClass = "Senior 2-C",
                isDayScholar = false,
                dayScholarType = DayScholarStatus.BOARDER,
                transportRoute = "Boarder - Oyam House",
                feesStatus = FeeStatus.OUTSTANDING,
                outstandingAmount = 380000.0,
                gender = "Male",
                avatarColorSeed = 0xFFD97706,
                guardianName = "Opio David",
                guardianPhone = "+256 701 345678",
                emergencyContact = "+256 701 345678",
                homeroomTeacher = "Mr. Ogwal Francis",
                academicYear = "2026",
                notes = "Bursar hold for Term 1 balance. Supervisor override permitted if guardian confirms deposit.",
                qrToken = "LTC0003TOK",
                updatedAt = now
            ),
            Student(
                id = "stu-ltc-004",
                studentNumber = "LTC-2026-0004",
                firstName = "Harriet",
                lastName = "Adongo",
                gradeClass = "Senior 1-A",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_PRIVATE,
                transportRoute = "Private Drop-off - Lira Town Centre",
                feesStatus = FeeStatus.CLEARED,
                outstandingAmount = 0.0,
                gender = "Female",
                avatarColorSeed = 0xFF7C3AED,
                guardianName = "Adongo Grace",
                guardianPhone = "+256 752 456789",
                emergencyContact = "+256 752 456789",
                homeroomTeacher = "Mrs. Atim Stella",
                academicYear = "2026",
                notes = "Prefect for library affairs. Authorized for late gate exit with exeat slip.",
                qrToken = "LTC0004TOK",
                updatedAt = now
            )
        )

        database.withTransaction {
            for (stu in initialStudents) {
                val entity = StudentEntity.fromDomain(stu)
                val profile = StudentProfileEntity.fromStudent(stu)
                val cardIdentifier = "CRD-${stu.studentNumber.removePrefix("LTC-")}-01"
                val card = Card(
                    id = UUID.randomUUID().toString(),
                    cardIdentifier = cardIdentifier,
                    studentId = stu.id,
                    studentNumber = stu.studentNumber,
                    qrPayload = QrCodeUtils.createPayload(stu.studentNumber, cardIdentifier),
                    status = CardStatus.ACTIVE,
                    issueDate = now,
                    activationDate = now,
                    reason = "Initial enrollment card issuance",
                    updatedAt = now
                )
                database.studentDao().insertOrUpdateStudent(entity)
                database.studentProfileDao().insertOrUpdateProfile(profile)
                database.cardDao().insertOrUpdateCard(CardEntity.fromDomain(card))
            }
        }
    }

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
