package com.example.data.sync

import com.example.data.local.AppDatabase
import com.example.data.local.StudentProfileEntity
import com.example.data.local.SyncEntityType
import com.example.data.local.SyncOperationType
import com.example.data.local.SyncMetadataEntity
import com.example.model.SyncInfo
import com.example.model.SyncStatus
import com.example.model.SyncSummary
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
            status = if (remoteCloudDataSource is UnconfiguredCloudBackend) SyncStatus.SYNC_FAILED else SyncStatus.SYNCED,
            lastSyncTimestamp = 0L,
            isOnline = true,
            pendingLogsCount = 0,
            errorMessage = if (remoteCloudDataSource is UnconfiguredCloudBackend) {
                "Cloud sync is not configured. Data remains local on this device."
            } else null
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
            status = when {
                !_syncInfo.value.isOnline -> SyncStatus.OFFLINE
                remoteCloudDataSource is UnconfiguredCloudBackend -> SyncStatus.SYNC_FAILED
                pendingCount > 0 -> SyncStatus.NEEDS_SYNC
                else -> SyncStatus.SYNCED
            },
            errorMessage = if (remoteCloudDataSource is UnconfiguredCloudBackend) {
                "Cloud sync is not configured. Data remains local on this device."
            } else null
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
        val backendUnconfigured = remoteCloudDataSource is UnconfiguredCloudBackend
        _syncInfo.value = _syncInfo.value.copy(
            isOnline = isOnline,
            status = when {
                !isOnline -> SyncStatus.OFFLINE
                backendUnconfigured -> SyncStatus.SYNC_FAILED
                pending > 0 -> SyncStatus.NEEDS_SYNC
                else -> SyncStatus.SYNCED
            },
            errorMessage = when {
                !isOnline -> "Device is offline. Local changes remain queued."
                backendUnconfigured -> "Cloud sync is not configured. Data remains local on this device."
                pending > 0 -> "Local changes are waiting to sync."
                else -> null
            }
        )
    }

    suspend fun refreshPendingState() = withContext(ioDispatcher) {
        val pending = calculatePendingCount()
        val online = _syncInfo.value.isOnline
        val backendUnconfigured = remoteCloudDataSource is UnconfiguredCloudBackend
        _syncInfo.value = _syncInfo.value.copy(
            pendingLogsCount = pending,
            status = when {
                !online -> SyncStatus.OFFLINE
                backendUnconfigured -> SyncStatus.SYNC_FAILED
                pending > 0 -> SyncStatus.NEEDS_SYNC
                else -> SyncStatus.SYNCED
            },
            errorMessage = when {
                !online -> "Device is offline. Local changes remain queued."
                backendUnconfigured -> "Cloud sync is not configured. Data remains local on this device."
                pending > 0 -> "Local changes are waiting to sync."
                else -> null
            }
        )
    }

    suspend fun notifyLocalLogAdded() = withContext(ioDispatcher) {
        refreshPendingState()
        // If online, auto-sync in background.
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
                    uploadResult.getOrThrow()
                    database.scanLogDao().markLogsAsSynced(unsyncedLogs.map { it.id })
                    logsUploadedCount += unsyncedLogs.size
                }

                // 2. Upload unsynced meal records
                val unsyncedMeals = database.mealRecordDao().getUnsynced()
                if (unsyncedMeals.isNotEmpty()) {
                    val mealResult = remoteCloudDataSource.pushMealRecords(unsyncedMeals)
                    mealResult.getOrThrow()
                    database.mealRecordDao().markSynced(unsyncedMeals.map { it.id })
                    logsUploadedCount += unsyncedMeals.size
                }

                // 3. Upload unsynced classroom attendance records
                val unsyncedAttendance = database.attendanceRecordDao().getUnsynced()
                if (unsyncedAttendance.isNotEmpty()) {
                    val attResult = remoteCloudDataSource.pushAttendanceRecords(unsyncedAttendance)
                    attResult.getOrThrow()
                    database.attendanceRecordDao().markSynced(unsyncedAttendance.map { it.id })
                    logsUploadedCount += unsyncedAttendance.size
                }

                // 4. Upload unsynced examination exceptions
                val unsyncedExceptions = database.examExceptionDao().getUnsynced()
                if (unsyncedExceptions.isNotEmpty()) {
                    val exResult = remoteCloudDataSource.pushExamExceptions(unsyncedExceptions)
                    exResult.getOrThrow()
                    database.examExceptionDao().markSynced(unsyncedExceptions.map { it.id })
                }

                // 5. Fetch modified student records from cloud (since last sync time)
                val fetchStudentsResult = remoteCloudDataSource.fetchStudentsUpdatedSince(lastSuccessfulSyncTime - 5000)
                val remoteStudents = fetchStudentsResult.getOrThrow()
                val studentsDownloadedCount = remoteStudents.size
                var studentsUpdatedCount = 0
                for (remoteStudent in remoteStudents) {
                    // Include local tombstones in conflict resolution so an older remote record
                    // cannot resurrect a student deleted while this device was offline.
                    val local = database.studentDao().getStudentByIdIncludingDeleted(remoteStudent.id)
                    if (local == null || remoteStudent.updatedAt >= local.updatedAt) {
                        database.studentDao().insertOrUpdateStudent(remoteStudent)
                        if (remoteStudent.isDeleted) {
                            database.studentProfileDao().deleteProfileById(remoteStudent.id)
                        } else {
                            database.studentProfileDao().insertOrUpdateProfile(
                                StudentProfileEntity.fromStudent(remoteStudent.toDomain())
                            )
                        }
                        studentsUpdatedCount++
                    }
                }

                // 6. Fetch modified card records from cloud
                val fetchCardsResult = remoteCloudDataSource.fetchCardsUpdatedSince(lastSuccessfulSyncTime - 5000)
                val remoteCards = fetchCardsResult.getOrThrow()
                for (remoteCard in remoteCards) {
                    val local = database.cardDao().getCardById(remoteCard.id)
                    if (local == null || remoteCard.updatedAt >= local.updatedAt) {
                        database.cardDao().insertOrUpdateCard(remoteCard)
                    }
                }

                val now = System.currentTimeMillis()

                // Drain the durable mutation queue only after each remote operation succeeds.
                // Failed items stay in Room and are included in the next retry.
                val pendingChanges = database.pendingChangeDao().getPendingChanges()
                val queueFailures = mutableListOf<String>()
                for (change in pendingChanges) {
                    try {
                        when (change.entityType) {
                            SyncEntityType.STUDENT -> {
                                if (change.operationType == SyncOperationType.DELETE) {
                                    remoteCloudDataSource
                                        .deleteRemoteStudent(change.recordId, change.createdAt)
                                        .getOrThrow()
                                } else {
                                    val student = database.studentDao().getStudentByIdIncludingDeleted(change.recordId)
                                        ?: throw NoSuchElementException(
                                            "Queued student " + change.recordId + " no longer exists locally."
                                        )
                                    // A later soft-delete supersedes any older create/update in the queue.
                                    if (!student.isDeleted) {
                                        remoteCloudDataSource.pushStudentChanges(listOf(student)).getOrThrow()
                                    }
                                }
                            }

                            SyncEntityType.FEE_STATUS -> {
                                val student = database.studentDao().getStudentByIdIncludingDeleted(change.recordId)
                                    ?: throw NoSuchElementException(
                                        "Queued fee-status student " + change.recordId + " no longer exists locally."
                                    )
                                // Do not publish a stale fee update after a queued student deletion.
                                if (!student.isDeleted) {
                                    remoteCloudDataSource.pushStudentChanges(listOf(student)).getOrThrow()
                                }
                            }

                            SyncEntityType.CARD -> {
                                val card = database.cardDao().getCardById(change.recordId)
                                    ?: throw NoSuchElementException(
                                        "Queued card " + change.recordId + " no longer exists locally."
                                    )
                                remoteCloudDataSource.pushCardChanges(listOf(card)).getOrThrow()
                            }

                            else -> throw UnsupportedOperationException(
                                "No sync handler exists for queued entity type '" + change.entityType + "'."
                            )
                        }
                        database.pendingChangeDao().markSynced(change.changeId)
                    } catch (e: Exception) {
                        val message = e.localizedMessage ?: e.javaClass.simpleName
                        database.pendingChangeDao().recordFailure(change.changeId, message)
                        queueFailures += change.entityType + "/" + change.recordId + ": " + message
                    }
                }
                if (queueFailures.isNotEmpty()) {
                    throw IllegalStateException(
                        "Could not sync " + queueFailures.size + " queued change(s): " +
                            queueFailures.joinToString("; ")
                    )
                }

                // 8. Update sync metadata
                lastSuccessfulSyncTime = now
                database.syncMetadataDao().setValue(
                    SyncMetadataEntity("last_sync_timestamp", now.toString(), now)
                )

                val pendingAfter = calculatePendingCount()
                _syncInfo.value = _syncInfo.value.copy(
                    status = SyncStatus.SYNCED,
                    lastSyncTimestamp = now,
                    pendingLogsCount = pendingAfter,
                    errorMessage = null
                )

                val summary = SyncSummary(
                    studentsDownloaded = studentsDownloadedCount,
                    studentsUpdated = studentsUpdatedCount,
                    logsUploaded = logsUploadedCount,
                    timestamp = now
                )
                Result.success(summary)
            } catch (e: Exception) {
                val pendingAfterFailure = calculatePendingCount()
                _syncInfo.value = _syncInfo.value.copy(
                    status = SyncStatus.SYNC_FAILED,
                    pendingLogsCount = pendingAfterFailure,
                    errorMessage = e.localizedMessage ?: "Synchronization failed"
                )
                Result.failure(e)
            }
        }
    }

    fun getLastSyncTimestamp(): Long = lastSuccessfulSyncTime

    fun getRemoteCloudBackend(): RemoteCloudDataSource = remoteCloudDataSource
}
