package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing the offline-change queue.
 */
@Dao
interface PendingChangeDao {

    @Query("SELECT * FROM pending_changes WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingChangesFlow(): Flow<List<PendingChangeEntity>>

    @Query("SELECT * FROM pending_changes WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingChanges(): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getFailedChanges(): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE changeId = :changeId LIMIT 1")
    suspend fun getChangeById(changeId: String): PendingChangeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueChange(change: PendingChangeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueChanges(changes: List<PendingChangeEntity>)

    @Query("UPDATE pending_changes SET status = :newStatus, lastAttemptAt = :attemptTime WHERE changeId = :changeId")
    suspend fun updateStatus(changeId: String, newStatus: String, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE pending_changes SET status = 'FAILED', retryCount = retryCount + 1, lastError = :error, lastAttemptAt = :attemptTime WHERE changeId = :changeId")
    suspend fun recordFailure(changeId: String, error: String, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE pending_changes SET status = 'SYNCED', lastAttemptAt = :attemptTime WHERE changeId = :changeId")
    suspend fun markSynced(changeId: String, attemptTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM pending_changes WHERE status = 'SYNCED' AND createdAt < :cutoffTime")
    suspend fun purgeSyncedChanges(cutoffTime: Long)

    @Query("DELETE FROM pending_changes WHERE changeId = :changeId")
    suspend fun deleteChange(changeId: String)

    @Query("SELECT COUNT(*) FROM pending_changes WHERE status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_changes WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int
}
