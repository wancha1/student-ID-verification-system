package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MockStudentRepository
import com.example.data.StudentRepository
import com.example.model.AuthUser
import com.example.model.FeeStatus
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.UserRole
import com.example.util.FeedbackHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeeFilter { ALL, CLEARED, OUTSTANDING }

class MainViewModel(
    private val repository: StudentRepository = MockStudentRepository.getInstance()
) : ViewModel() {

    // Current Authenticated User (Null means on login screen)
    private val _currentUser = MutableStateFlow<AuthUser?>(
        AuthUser(
            role = UserRole.SECURITY_GUARD,
            name = UserRole.SECURITY_GUARD.defaultUsername,
            station = UserRole.SECURITY_GUARD.subtitle
        )
    )
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    // Students list from repository
    val allStudents: StateFlow<List<Student>> = repository.studentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Gate verification logs
    val scanLogs: StateFlow<List<ScanLog>> = repository.scanLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Guard Scan States
    private val _currentScannedStudent = MutableStateFlow<Student?>(null)
    val currentScannedStudent: StateFlow<Student?> = _currentScannedStudent.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _isScannerOpen = MutableStateFlow(false)
    val isScannerOpen: StateFlow<Boolean> = _isScannerOpen.asStateFlow()

    // Admin Search & Filter States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _feeFilter = MutableStateFlow(FeeFilter.ALL)
    val feeFilter: StateFlow<FeeFilter> = _feeFilter.asStateFlow()

    private val _selectedStudentId = MutableStateFlow<String?>(null)

    // Derived filtered students for Admin list
    val filteredStudents: StateFlow<List<Student>> = combine(
        allStudents,
        _searchQuery,
        _feeFilter
    ) { students, query, filter ->
        students.filter { student ->
            val matchesQuery = query.isBlank() ||
                student.fullName.contains(query, ignoreCase = true) ||
                student.id.contains(query, ignoreCase = true) ||
                student.gradeClass.contains(query, ignoreCase = true) ||
                student.transportRoute.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                FeeFilter.ALL -> true
                FeeFilter.CLEARED -> student.feesStatus == FeeStatus.CLEARED
                FeeFilter.OUTSTANDING -> student.feesStatus == FeeStatus.OUTSTANDING
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected student detail (reactively updated if repo changes)
    val selectedStudentDetail: StateFlow<Student?> = combine(
        allStudents,
        _selectedStudentId
    ) { students, selectedId ->
        if (selectedId == null) null
        else students.firstOrNull { it.id.equals(selectedId, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Notification toast / snackbar
    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage: StateFlow<String?> = _userFeedbackMessage.asStateFlow()

    fun loginAs(role: UserRole, customName: String? = null) {
        _currentUser.value = AuthUser(
            role = role,
            name = customName ?: role.defaultUsername,
            station = role.subtitle
        )
        // Reset scan state on role change
        _currentScannedStudent.value = null
        _scanError.value = null
        _isScannerOpen.value = false
    }

    fun logout() {
        _currentUser.value = null
        _currentScannedStudent.value = null
        _scanError.value = null
        _isScannerOpen.value = false
    }

    fun openScanner() {
        _scanError.value = null
        _isScannerOpen.value = true
    }

    fun closeScanner() {
        _isScannerOpen.value = false
    }

    fun handleBarcodeScan(rawCode: String, context: Context? = null) {
        viewModelScope.launch {
            val trimmedCode = rawCode.trim()
            val student = repository.findStudentByQrCode(trimmedCode)
            _isScannerOpen.value = false

            if (student != null) {
                _currentScannedStudent.value = student
                _scanError.value = null

                // Record verification log
                repository.logVerificationScan(
                    ScanLog(
                        studentId = student.id,
                        studentName = student.fullName,
                        gradeClass = student.gradeClass,
                        feeStatus = student.feesStatus,
                        isDayScholar = student.isDayScholar,
                        isApproved = student.isEntryApproved,
                        guardName = _currentUser.value?.name ?: "Security Guard",
                        gateLocation = "Gate 1 (Main Entrance)"
                    )
                )

                // Sound & Haptic cues
                context?.let { ctx ->
                    FeedbackHelper.playFeedback(ctx, student.isEntryApproved)
                }
            } else {
                _currentScannedStudent.value = null
                _scanError.value = "Unrecognized QR Code: '$trimmedCode'. No student found in the roster with this ID."

                // Record security audit for unrecognized attempt
                repository.logVerificationScan(
                    ScanLog(
                        studentId = trimmedCode.ifBlank { "UNKNOWN-ID" },
                        studentName = "Unrecognized / Invalid Badge",
                        gradeClass = "Unknown",
                        feeStatus = FeeStatus.OUTSTANDING,
                        isDayScholar = false,
                        isApproved = false,
                        guardName = _currentUser.value?.name ?: "Security Guard",
                        gateLocation = "Gate 1 (Main Entrance)"
                    )
                )

                context?.let { ctx ->
                    FeedbackHelper.playFeedback(ctx, false)
                }
            }
        }
    }

    fun dismissScanResult() {
        _currentScannedStudent.value = null
        _scanError.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFeeFilter(filter: FeeFilter) {
        _feeFilter.value = filter
    }

    fun selectStudentForDetail(studentId: String?) {
        _selectedStudentId.value = studentId
    }

    fun updateFeeStatus(studentId: String, newStatus: FeeStatus, outstandingAmount: Double = 0.0) {
        viewModelScope.launch {
            val result = repository.updateFeeStatus(studentId, newStatus, outstandingAmount)
            if (result.isSuccess) {
                val student = repository.getStudentById(studentId)
                val statusText = if (newStatus == FeeStatus.CLEARED) "CLEARED" else "OUTSTANDING"
                _userFeedbackMessage.value = "Fees for ${student?.fullName ?: studentId} updated to $statusText. Gate scanners will immediately reflect this!"

                // If currently viewing scanned result of this student, keep it in sync
                if (_currentScannedStudent.value?.id.equals(studentId, ignoreCase = true)) {
                    _currentScannedStudent.value = student
                }
            } else {
                _userFeedbackMessage.value = "Failed to update fees status."
            }
        }
    }

    fun registerNewStudent(student: Student, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = repository.addStudent(student)
            if (result.isSuccess) {
                _userFeedbackMessage.value = "Student ${student.fullName} (${student.id}) registered successfully!"
                onComplete(true, "Student registered successfully")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Failed to add student"
                _userFeedbackMessage.value = msg
                onComplete(false, msg)
            }
        }
    }

    fun updateStudentDetails(student: Student, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = repository.updateStudent(student)
            if (result.isSuccess) {
                _userFeedbackMessage.value = "Updated details for ${student.fullName}."
                if (_currentScannedStudent.value?.id.equals(student.id, ignoreCase = true)) {
                    _currentScannedStudent.value = student
                }
                onComplete(true, "Student updated successfully")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Failed to update student"
                _userFeedbackMessage.value = msg
                onComplete(false, msg)
            }
        }
    }

    fun deleteStudentRecord(studentId: String) {
        viewModelScope.launch {
            val student = repository.getStudentById(studentId)
            repository.deleteStudent(studentId)
            _userFeedbackMessage.value = "Removed student ${student?.fullName ?: studentId}."
            if (_selectedStudentId.value.equals(studentId, ignoreCase = true)) {
                _selectedStudentId.value = null
            }
        }
    }

    fun clearFeedbackMessage() {
        _userFeedbackMessage.value = null
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearScanLogs()
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetToSampleData()
            _userFeedbackMessage.value = "Database reset to initial sample student roster."
        }
    }
}
