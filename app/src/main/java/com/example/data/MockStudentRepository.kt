package com.example.data

import com.example.model.FeeStatus
import com.example.model.ScanLog
import com.example.model.Student
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MockStudentRepository : StudentRepository {

    private val _students = MutableStateFlow<List<Student>>(StudentDataSamples.createInitialStudents())
    override val studentsFlow: Flow<List<Student>> = _students.asStateFlow()

    private val _scanLogs = MutableStateFlow<List<ScanLog>>(emptyList())
    override val scanLogsFlow: Flow<List<ScanLog>> = _scanLogs.asStateFlow()

    override suspend fun getStudentById(id: String): Student? {
        val trimmed = id.trim()
        return _students.value.firstOrNull {
            it.id.equals(trimmed, ignoreCase = true)
        }
    }

    override suspend fun findStudentByQrCode(rawCode: String): Student? {
        val cleanCode = rawCode.trim()
        return _students.value.firstOrNull {
            it.id.equals(cleanCode, ignoreCase = true)
        }
    }

    override suspend fun updateFeeStatus(
        studentId: String,
        newStatus: FeeStatus,
        outstandingAmount: Double
    ): Result<Unit> {
        var found = false
        _students.update { list ->
            list.map { student ->
                if (student.id.equals(studentId, ignoreCase = true)) {
                    found = true
                    student.copy(
                        feesStatus = newStatus,
                        outstandingAmount = if (newStatus == FeeStatus.CLEARED) 0.0 else if (outstandingAmount > 0) outstandingAmount else 450.0
                    )
                } else {
                    student
                }
            }
        }
        return if (found) Result.success(Unit) else Result.failure(Exception("Student not found"))
    }

    override suspend fun addStudent(student: Student): Result<Unit> {
        val existing = _students.value.firstOrNull { it.id.equals(student.id.trim(), ignoreCase = true) }
        if (existing != null) {
            return Result.failure(Exception("Student with ID '${student.id}' already exists"))
        }
        _students.update { list ->
            listOf(student) + list
        }
        return Result.success(Unit)
    }

    override suspend fun updateStudent(student: Student): Result<Unit> {
        var found = false
        _students.update { list ->
            list.map { existing ->
                if (existing.id.equals(student.id.trim(), ignoreCase = true)) {
                    found = true
                    student
                } else {
                    existing
                }
            }
        }
        return if (found) Result.success(Unit) else Result.failure(Exception("Student not found"))
    }

    override suspend fun deleteStudent(studentId: String): Result<Unit> {
        _students.update { list ->
            list.filterNot { it.id.equals(studentId.trim(), ignoreCase = true) }
        }
        return Result.success(Unit)
    }

    override suspend fun logVerificationScan(log: ScanLog) {
        _scanLogs.update { current ->
            listOf(log) + current
        }
    }

    override suspend fun clearScanLogs() {
        _scanLogs.value = emptyList()
    }

    override suspend fun resetToSampleData() {
        _students.value = StudentDataSamples.createInitialStudents()
        _scanLogs.value = emptyList()
    }

    companion object {
        @Volatile
        private var instance: MockStudentRepository? = null

        fun getInstance(): MockStudentRepository {
            return instance ?: synchronized(this) {
                instance ?: MockStudentRepository().also { instance = it }
            }
        }
    }
}
