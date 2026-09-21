package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for local student profiles stored for offline gate verification.
 */
@Dao
interface StudentProfileDao {

    @Query("SELECT * FROM student_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<StudentProfileEntity>>

    @Query("SELECT * FROM student_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): StudentProfileEntity?

    @Query("SELECT * FROM student_profiles WHERE studentNumber = :studentNumber LIMIT 1")
    suspend fun getProfileByStudentNumber(studentNumber: String): StudentProfileEntity?

    @Query("SELECT * FROM student_profiles WHERE (id = :identifier OR studentNumber = :identifier) LIMIT 1")
    suspend fun findProfileForOfflineVerification(identifier: String): StudentProfileEntity?

    @Query("SELECT * FROM student_profiles WHERE accessStatus = :status ORDER BY name ASC")
    fun getProfilesByAccessStatus(status: String): Flow<List<StudentProfileEntity>>

    @Query("SELECT * FROM student_profiles WHERE accessStatus = 'APPROVED' ORDER BY name ASC")
    fun getApprovedProfiles(): Flow<List<StudentProfileEntity>>

    @Query("SELECT * FROM student_profiles WHERE name LIKE '%' || :query || '%' OR studentNumber LIKE '%' || :query || '%'")
    fun searchProfiles(query: String): Flow<List<StudentProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: StudentProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfiles(profiles: List<StudentProfileEntity>)

    @Query("UPDATE student_profiles SET accessStatus = :newStatus, updatedAt = :updatedAt WHERE id = :studentId")
    suspend fun updateAccessStatus(studentId: String, newStatus: String, updatedAt: Long)

    @Query("DELETE FROM student_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)

    @Query("DELETE FROM student_profiles")
    suspend fun clearAllProfiles()

    @Query("SELECT COUNT(*) FROM student_profiles")
    suspend fun getProfileCount(): Int

    @Query("SELECT COUNT(*) FROM student_profiles WHERE accessStatus = 'APPROVED'")
    suspend fun getApprovedCount(): Int
}
