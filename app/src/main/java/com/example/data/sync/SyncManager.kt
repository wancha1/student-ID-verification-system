package com.example.data.sync

import com.example.data.local.AppDatabase
import com.example.data.local.PendingChangeEntity
import com.example.data.local.SyncEntityType
import com.example.data.local.SyncOperationType
import com.example.data.local.StudentProfileEntity
import com.example.data.local.SyncMetadataEntity
import com.example.model.SyncInfo
import com.example.model.SyncStatus
import com.example.model.SyncSummary
import com.example.model.FeeStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncManager(
    private val database: AppDatabase,
    private val remoteCloudDataSource: RemoteCloudDataSource = InMemoryCloudBackend(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val syncMutex = Mutex()
    private val _syncInfo = MutableStateFlow(
        SyncInfo(
            status = SyncStatus.SYNCED,
            lastSyncTimestamp = System.currentTimeMillis(),
            isOnline = true,
            pendingLogsCount = 0
        )
    )
    val syncInfo: StateFlow<SyncInfo> = _syncInfo.asStateFlow()

    private var lastSuccessfulSyncTime: Long = 0L

    suspend fun initialize() = withContext(ioDispatcher) {
        val savedTimeStr = database.syncMetadataDao().getValue("last_sync_timestamp")
        lastSuccessfulSyncTime = savedTimeStr?.toLongOrNull() ?: 0L
        val pendingCount = calculatePendingCount()
        _syncInfo.value = _syncInfo.value.copy(
            lastSyncTimestamp = lastSuccessfulSyncTime,
            pendingLogsCount = pendingCount,
            status = if (_syncInfo.value.isOnline) SyncStatus.SYNCED else SyncStatus.OFFLINE
        )
    }

    private suspend fun calculatePendingCount(): Int {
        return database.scanLogDao().getUnsyncedLogsCount() +
                database.mealRecordDao().getUnsynced().size +
                database.attendanceRecordDao().getUnsynced().size +
                database.examExceptionDao().getUnsynced().size +
                database.pendingChangeDao().getPendingCount()
    }

    fun setNetworkConnectivity(isOnline: Boolean) {
        val pending = _syncInfo.value.pendingLogsCount
        _syncInfo.value = _syncInfo.value.copy(
            isOnline = isOnline,
            status = if (isOnline) {
                if (pending > 0) SyncStatus.NEEDS_SYNC else SyncStatus.SYNCED
            } else {
                SyncStatus.OFFLINE
            }
        )
    }

    suspend fun notifyLocalLogAdded() = withContext(ioDispatcher) {
        val pending = calculatePendingCount()
        _syncInfo.value = _syncInfo.value.copy(
            pendingLogsCount = pending,
            status = if (!_syncInfo.value.isOnline) SyncStatus.OFFLINE else SyncStatus.NEEDS_SYNC
        )
        // If online, auto-sync in background
        if (_syncInfo.value.isOnline) {
            syncNow()
        }
    }

    suspend fun syncNow(): Result<SyncSummary> = withContext(ioDispatcher) {
        if (!_syncInfo.value.isOnline) {
            _syncInfo.value = _syncInfo.value.copy(
                status = SyncStatus.OFFLINE,
                errorMessage = "Device is offline. Using local cached data."
            )
            return@withContext Result.failure(IllegalStateException("Cannot sync while offline"))
        }

        syncMutex.withLock {
            _syncInfo.value = _syncInfo.value.copy(status = SyncStatus.SYNCING, errorMessage = null)
            try {
                // 1. Upload unsynced local gate verification logs to cloud
                val unsyncedLogs = database.scanLogDao().getUnsyncedLogs()
                var logsUploadedCount = 0
                if (unsyncedLogs.isNotEmpty()) {
                    val uploadResult = remoteCloudDataSource.pushGateLogs(unsyncedLogs)
                    if (uploadResult.isSuccess) {
                        database.scanLogDao().markLogsAsSynced(unsyncedLogs.map { it.id })
                        logsUploadedCount += unsyncedLogs.size
                    }
                }

                // 2. Upload unsynced meal records
                val unsyncedMeals = database.mealRecordDao().getUnsynced()
                if (unsyncedMeals.isNotEmpty()) {
                    val mealResult = remoteCloudDataSource.pushMealRecords(unsyncedMeals)
                    if (mealResult.isSuccess) {
                        database.mealRecordDao().markSynced(unsyncedMeals.map { it.id })
                        logsUploadedCount += unsyncedMeals.size
                    }
                }

                // 3. Upload unsynced classroom attendance records
                val unsyncedAttendance = database.attendanceRecordDao().getUnsynced()
                if (unsyncedAttendance.isNotEmpty()) {
                    val attResult = remoteCloudDataSource.pushAttendanceRecords(unsyncedAttendance)
                    if (attResult.isSuccess) {
                        database.attendanceRecordDao().markSynced(unsyncedAttendance.map { it.id })
                        logsUploadedCount += unsyncedAttendance.size
                    }
                }

                // 4. Upload unsynced examination exceptions
                val unsyncedExceptions = database.examExceptionDao().getUnsynced()
                if (unsyncedExceptions.isNotEmpty()) {
                    val exResult = remoteCloudDataSource.pushExamExceptions(unsyncedExceptions)
                    if (exResult.isSuccess) {
                        database.examExceptionDao().markSynced(unsyncedExceptions.map { it.id })
                    }
                }

                // 5. Fetch modified student records from cloud (since last sync time)
                val fetchStudentsResult = remoteCloudDataSource.fetchStudentsUpdatedSince((lastSuccessfulSyncTime - 5000).coerceAtLeast(-1L))
                if (fetchStudentsResult.isFailure) {
                    throw fetchStudentsResult.exceptionOrNull() ?: IllegalStateException("Failed to fetch remote students")
                }
                var studentsDownloadedCount = 0
                var studentsUpdatedCount = 0
                run {
                    val remoteStudents = fetchStudentsResult.getOrThrow()
                    studentsDownloadedCount = remoteStudents.size
                    for (remoteStudent in remoteStudents) {
                        val local = database.studentDao().getStudentById(remoteStudent.id)
                        // Conflict resolution: Last-Write-Wins based on updatedAt
                        if (local == null || remoteStudent.updatedAt >= local.updatedAt) {
                            database.studentDao().insertOrUpdateStudent(remoteStudent)
                            database.studentProfileDao().insertOrUpdateProfile(StudentProfileEntity.fromStudent(remoteStudent.toDomain()))
                            studentsUpdatedCount++
                        }
                    }
                }

                // 6. Fetch modified card records from cloud
                val fetchCardsResult = remoteCloudDataSource.fetchCardsUpdatedSince((lastSuccessfulSyncTime - 5000).coerceAtLeast(-1L))
                if (fetchCardsResult.isFailure) {
                    throw fetchCardsResult.exceptionOrNull() ?: IllegalStateException("Failed to fetch remote cards")
                }
                run {
                    val remoteCards = fetchCardsResult.getOrThrow()
                    for (remoteCard in remoteCards) {
                        val local = database.cardDao().getCardById(remoteCard.id)
                        // Revocation is monotonic: an ACTIVE copy must never overwrite a revoked/deleted card.
                        val localIsRevoked = local != null && (local.status in listOf("LOST", "REPLACED", "DEACTIVATED") || local.isDeleted)
                        val remoteIsRevoked = remoteCard.status in listOf("LOST", "REPLACED", "DEACTIVATED") || remoteCard.isDeleted
                        val remoteIsActive = remoteCard.status == "ACTIVE" && !remoteCard.isDeleted
                        if (localIsRevoked && remoteIsActive) continue

                        // A remote revocation wins over a stale local ACTIVE copy, regardless of device clock skew.
                        if (remoteIsRevoked && local != null && local.status == "ACTIVE" && !local.isDeleted) {
                            database.cardDao().insertOrUpdateCard(remoteCard)
                            continue
                        }

                        if (local == null || remoteCard.updatedAt >= local.updatedAt) {
                            database.cardDao().insertOrUpdateCard(remoteCard)
                        }
                    }
                }

                val now = System.currentTimeMillis()

                // 7. Dispatch durable outbox changes. A change is marked SYNCED only
                // after its corresponding remote operation has positively succeeded.
                val pendingChanges = database.pendingChangeDao().getPendingChanges()
                for (change in pendingChanges) {
                    val dispatchResult = dispatchPendingChange(change)
                    if (dispatchResult.isSuccess) {
                        database.pendingChangeDao().markSynced(change.changeId, now)
                    } else {
                        database.pendingChangeDao().recordFailure(
                            change.changeId,
                            dispatchResult.exceptionOrNull()?.localizedMessage ?: "Remote dispatch failed",
                            now
                        )
                    }
                }

                // 8. Only advance the sync watermark when all local outbox/log records
                // are confirmed uploaded. Failed items remain visible and retryable.
                val pendingAfter = calculatePendingCount()
                if (pendingAfter == 0) {
                    lastSuccessfulSyncTime = now
                    database.syncMetadataDao().setValue(
                        SyncMetadataEntity("last_sync_timestamp", now.toString(), now)
                    )
                    _syncInfo.value = _syncInfo.value.copy(
                        status = SyncStatus.SYNCED,
                        lastSyncTimestamp = now,
                        pendingLogsCount = 0,
                        errorMessage = null
                    )
                } else {
                    val message = "$pendingAfter local change(s) remain unsynchronized; they will be retried."
                    _syncInfo.value = _syncInfo.value.copy(
                        status = SyncStatus.SYNC_FAILED,
                        pendingLogsCount = pendingAfter,
                        errorMessage = message
                    )
                    return@withLock Result.failure(IllegalStateException(message))
                }

                val summary = SyncSummary(
                    studentsDownloaded = studentsDownloadedCount,
                    studentsUpdated = studentsUpdatedCount,
                    logsUploaded = logsUploadedCount,
                    timestamp = now
                )
                Result.success(summary)
            } catch (e: Exception) {
                _syncInfo.value = _syncInfo.value.copy(
                    status = SyncStatus.SYNC_FAILED,
                    errorMessage = e.localizedMessage ?: "Synchronization failed"
                )
                Result.failure(e)
            }
        }
    }

    private suspend fun dispatchPendingChange(change: PendingChangeEntity): Result<Unit> {
        return when (change.entityType) {
            SyncEntityType.STUDENT -> {
                if (change.operationType == SyncOperationType.DELETE) {
                    remoteCloudDataSource.deleteRemoteStudent(change.recordId, change.createdAt)
                } else {
                    val student = database.studentDao().getStudentById(change.recordId)
                        ?: return Result.failure(IllegalStateException("Queued student ${change.recordId} is missing locally"))
                    remoteCloudDataSource.pushStudentChanges(listOf(student))
                }
            }

            SyncEntityType.CARD -> {
                if (change.operationType == SyncOperationType.DELETE) {
                    Result.failure(IllegalArgumentException("Card deletion is not a supported sync operation"))
                } else {
                    val card = database.cardDao().getCardById(change.recordId)
                        ?: return Result.failure(IllegalStateException("Queued card ${change.recordId} is missing locally"))
                    // Upsert the full current local state; this is idempotent and also
                    // handles a retry where the remote card has not been created yet.
                    remoteCloudDataSource.pushCardChanges(listOf(card))
                }
            }

            SyncEntityType.FEE_STATUS -> {
                val student = database.studentDao().getStudentById(change.recordId)
                    ?: return Result.failure(IllegalStateException("Queued fee update has no local student ${change.recordId}"))
                val feeStatus = try {
                    FeeStatus.valueOf(student.feesStatus)
                } catch (e: Exception) {
                    return Result.failure(IllegalStateException("Unknown fee status '${student.feesStatus}' for ${change.recordId}", e))
                }
                remoteCloudDataSource.updateRemoteFeeStatus(
                    studentId = student.id,
                    newStatus = feeStatus,
                    outstandingAmount = student.outstandingAmount,
                    updatedAt = student.updatedAt
                )
            }

            SyncEntityType.SCAN_LOG -> {
                val log = database.scanLogDao().getScanLogById(change.recordId)
                    ?: return Result.failure(IllegalStateException("Queued scan log ${change.recordId} is missing locally"))
                remoteCloudDataSource.pushGateLogs(listOf(log))
            }

            else -> Result.failure(IllegalArgumentException("Unsupported queued entity type '${change.entityType}'"))
        }
    }

    fun getLastSyncTimestamp(): Long = lastSuccessfulSyncTime

    fun getRemoteCloudBackend(): RemoteCloudDataSource = remoteCloudDataSource
}
