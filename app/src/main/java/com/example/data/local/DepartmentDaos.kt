package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealRecordDao {
    // Atomic first-write-wins operation for one student/meal/date; -1 means already served.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(record: MealRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: MealRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<MealRecordEntity>)

    @Query("SELECT * FROM meal_records WHERE studentId = :studentId AND mealType = :mealType AND mealDate = :mealDate LIMIT 1")
    suspend fun findServing(studentId: String, mealType: String, mealDate: String): MealRecordEntity?

    @Query("SELECT * FROM meal_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MealRecordEntity>>

    @Query("SELECT * FROM meal_records WHERE mealDate = :mealDate ORDER BY timestamp DESC")
    fun observeByDate(mealDate: String): Flow<List<MealRecordEntity>>

    @Query("SELECT * FROM meal_records WHERE isSyncedToCloud = 0")
    suspend fun getUnsynced(): List<MealRecordEntity>

    @Query("UPDATE meal_records SET isSyncedToCloud = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}

@Dao
interface AttendanceRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: AttendanceRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AttendanceRecordEntity>)

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId AND lessonDate = :lessonDate AND sessionName = :sessionName LIMIT 1")
    suspend fun findAttendance(studentId: String, lessonDate: String, sessionName: String): AttendanceRecordEntity?

    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE lessonDate = :lessonDate AND gradeClass = :gradeClass ORDER BY timestamp DESC")
    fun observeByClassAndDate(gradeClass: String, lessonDate: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE isSyncedToCloud = 0")
    suspend fun getUnsynced(): List<AttendanceRecordEntity>

    @Query("UPDATE attendance_records SET isSyncedToCloud = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}

@Dao
interface ExamExceptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: ExamExceptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ExamExceptionEntity>)

    @Query("SELECT * FROM exam_exceptions WHERE studentId = :studentId AND examCode = :examCode LIMIT 1")
    suspend fun findException(studentId: String, examCode: String): ExamExceptionEntity?

    @Query("SELECT * FROM exam_exceptions ORDER BY requestedAt DESC")
    fun observeAll(): Flow<List<ExamExceptionEntity>>

    @Query("SELECT * FROM exam_exceptions WHERE isSyncedToCloud = 0")
    suspend fun getUnsynced(): List<ExamExceptionEntity>

    @Query("UPDATE exam_exceptions SET isSyncedToCloud = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
