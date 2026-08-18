package com.example.data

import com.example.model.FeeStatus
import com.example.model.ScanLog
import com.example.model.Student
import kotlinx.coroutines.flow.Flow

/**
 * Clean repository interface for student ID verification & fee management.
 * Can be swapped seamlessly for a backend (e.g. Supabase, Firebase, or REST API).
 */
interface StudentRepository {
    val studentsFlow: Flow<List<Student>>
    val scanLogsFlow: Flow<List<ScanLog>>

    suspend fun getStudentById(id: String): Student?
    suspend fun findStudentByQrCode(rawCode: String): Student?
    suspend fun updateFeeStatus(studentId: String, newStatus: FeeStatus, outstandingAmount: Double = 0.0): Result<Unit>
    suspend fun addStudent(student: Student): Result<Unit>
    suspend fun updateStudent(student: Student): Result<Unit>
    suspend fun deleteStudent(studentId: String): Result<Unit>
    suspend fun logVerificationScan(log: ScanLog)
    suspend fun clearScanLogs()
    suspend fun resetToSampleData()
}
