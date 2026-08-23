package com.example.data.sync

import com.example.data.local.AppDatabase
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
            status = SyncStatus.SYNCED,
            lastSyncTimestamp = System.currentTimeMillis(),
            isOnline = true,
            pendingLogsCount = 0
        )
    )
    val syncInfo: StateFlow<SyncInfo> = _syncInfo.asStateFlow()

    private var lastSuccessfulSyncTime: Long = System.currentTimeMillis()

    suspend fun initialize() = withContext(ioDispatcher) {
        val savedTimeStr = database.syncMetadataDao().getValue("last_sync_timestamp")
        lastSuccessfulSyncTime = savedTimeStr?.toLongOrNull() ?: System.currentTimeMillis()

        val pendingLogs = database.scanLogDao().getUnsyncedLogsCount()
        _syncInfo.value = _syncInfo.value.copy(
            lastSyncTimestamp = lastSuccessfulSyncTime,
            pendingLogsCount = pendingLogs,
            status = if (_syncInfo.value.isOnline) SyncStatus.SYNCED else SyncStatus.OFFLINE
        )
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
        val pending = database.scanLogDao().getUnsyncedLogsCount()
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
                        logsUploadedCount = unsyncedLogs.size
                    }
                }

                // 2. Fetch modified student records from cloud (since last sync time)
                val fetchStudentsResult = remoteCloudDataSource.fetchStudentsUpdatedSince(lastSuccessfulSyncTime - 5000)
                var studentsDownloadedCount = 0
                var studentsUpdatedCount = 0

                if (fetchStudentsResult.isSuccess) {
                    val remoteStudents = fetchStudentsResult.getOrThrow()
                    studentsDownloadedCount = remoteStudents.size

                    for (remoteStudent in remoteStudents) {
                        val local = database.studentDao().getStudentById(remoteStudent.id)
                        // Conflict resolution: Last-Write-Wins based on updatedAt
                        if (local == null || remoteStudent.updatedAt >= local.updatedAt) {
                            database.studentDao().insertOrUpdateStudent(remoteStudent)
                            studentsUpdatedCount++
                        }
                    }
                }

                // 3. Fetch modified card records from cloud
                val fetchCardsResult = remoteCloudDataSource.fetchCardsUpdatedSince(lastSuccessfulSyncTime - 5000)
                if (fetchCardsResult.isSuccess) {
                    val remoteCards = fetchCardsResult.getOrThrow()
                    for (remoteCard in remoteCards) {
                        val local = database.cardDao().getCardById(remoteCard.id)
                        if (local == null || remoteCard.updatedAt >= local.updatedAt) {
                            database.cardDao().insertOrUpdateCard(remoteCard)
                        }
                    }
                }

                // 4. Update sync metadata
                val now = System.currentTimeMillis()
                lastSuccessfulSyncTime = now
                database.syncMetadataDao().setValue(
                    SyncMetadataEntity("last_sync_timestamp", now.toString(), now)
                )

                val pendingLogsAfter = database.scanLogDao().getUnsyncedLogsCount()
                _syncInfo.value = _syncInfo.value.copy(
                    status = SyncStatus.SYNCED,
                    lastSyncTimestamp = now,
                    pendingLogsCount = pendingLogsAfter,
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
                _syncInfo.value = _syncInfo.value.copy(
                    status = SyncStatus.SYNC_FAILED,
                    errorMessage = e.localizedMessage ?: "Synchronization failed"
                )
                Result.failure(e)
            }
        }
    }

    fun getLastSyncTimestamp(): Long = lastSuccessfulSyncTime

    fun getRemoteCloudBackend(): RemoteCloudDataSource = remoteCloudDataSource
}
