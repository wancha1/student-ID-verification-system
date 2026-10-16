package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentRequirementDao {
    @Query("SELECT * FROM student_requirements ORDER BY studentName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<StudentRequirementEntity>>

    @Query("SELECT * FROM student_requirements WHERE studentId = :studentId LIMIT 1")
    suspend fun getByStudentId(studentId: String): StudentRequirementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(requirement: StudentRequirementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(requirements: List<StudentRequirementEntity>)

    @Query("DELETE FROM student_requirements WHERE studentId = :studentId")
    suspend fun deleteByStudentId(studentId: String)
}
