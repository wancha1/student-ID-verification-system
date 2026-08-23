package com.example.model

enum class SyncStatus {
    SYNCED,
    OFFLINE,
    SYNCING,
    SYNC_FAILED,
    NEEDS_SYNC
}

data class SyncInfo(
    val status: SyncStatus = SyncStatus.SYNCED,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val pendingLogsCount: Int = 0,
    val errorMessage: String? = null
)

data class SyncSummary(
    val studentsDownloaded: Int,
    val studentsUpdated: Int,
    val logsUploaded: Int,
    val timestamp: Long = System.currentTimeMillis()
)
