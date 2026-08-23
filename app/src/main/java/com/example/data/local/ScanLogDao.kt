package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanLogDao {

    @Query("SELECT * FROM gate_scan_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<ScanLogEntity>>

    @Query("SELECT * FROM gate_scan_logs WHERE isSyncedToCloud = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedLogs(): List<ScanLogEntity>

    @Query("SELECT COUNT(*) FROM gate_scan_logs WHERE isSyncedToCloud = 0")
    fun getUnsyncedLogsCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM gate_scan_logs WHERE isSyncedToCloud = 0")
    suspend fun getUnsyncedLogsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ScanLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<ScanLogEntity>)

    @Query("UPDATE gate_scan_logs SET isSyncedToCloud = 1 WHERE id IN (:logIds)")
    suspend fun markLogsAsSynced(logIds: List<String>)

    @Query("DELETE FROM gate_scan_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM gate_scan_logs")
    suspend fun getTotalLogsCount(): Int
}
