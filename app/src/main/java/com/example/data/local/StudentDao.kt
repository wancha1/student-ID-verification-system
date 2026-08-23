package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Query("SELECT * FROM students WHERE isDeleted = 0 ORDER BY lastName ASC, firstName ASC")
    fun getAllActiveStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getStudentById(id: String): StudentEntity?

    @Query("SELECT * FROM students WHERE studentNumber = :studentNumber AND isDeleted = 0 LIMIT 1")
    suspend fun getStudentByStudentNumber(studentNumber: String): StudentEntity?

    @Query("SELECT * FROM students WHERE (id = :identifier OR studentNumber = :identifier) AND isDeleted = 0 LIMIT 1")
    suspend fun findStudentByIdOrNumber(identifier: String): StudentEntity?

    @Query("SELECT * FROM students")
    suspend fun getAllStudentsSnapshot(): List<StudentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStudent(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStudents(students: List<StudentEntity>)

    @Query("UPDATE students SET feesStatus = :newStatus, outstandingAmount = :outstandingAmount, updatedAt = :updatedAt WHERE id = :studentId")
    suspend fun updateFeeStatus(studentId: String, newStatus: String, outstandingAmount: Double, updatedAt: Long)

    @Query("UPDATE students SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :studentId")
    suspend fun softDeleteStudent(studentId: String, updatedAt: Long)

    @Query("DELETE FROM students WHERE id = :studentId")
    suspend fun hardDeleteStudent(studentId: String)

    @Query("DELETE FROM students")
    suspend fun clearAllStudents()

    @Query("SELECT COUNT(*) FROM students WHERE isDeleted = 0")
    suspend fun getActiveCount(): Int
}
