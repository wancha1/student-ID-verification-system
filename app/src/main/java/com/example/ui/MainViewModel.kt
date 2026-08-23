package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MockStudentRepository
import com.example.data.StudentRepository
import com.example.model.AuthUser
import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.model.SyncInfo
import com.example.model.SyncStatus
import com.example.model.UserRole
import com.example.util.FeedbackHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeeFilter { ALL, CLEARED, OUTSTANDING }

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: StudentRepository = MockStudentRepository.getInstance()
) : ViewModel() {

    // Current Authenticated User
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

    // Gate verification audit logs
    val scanLogs: StateFlow<List<ScanLog>> = repository.scanLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Synchronization state & freshness
    val syncInfo: StateFlow<SyncInfo> = repository.syncInfoFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SyncInfo(
                status = SyncStatus.SYNCED,
                lastSyncTimestamp = System.currentTimeMillis(),
                isOnline = true,
                pendingLogsCount = 0
            )
        )

    // Guard Scan State
    private val _activeScanResult = MutableStateFlow<StudentScanResult?>(null)
    val activeScanResult: StateFlow<StudentScanResult?> = _activeScanResult.asStateFlow()

    private val _currentScannedStudent = MutableStateFlow<Student?>(null)
    val currentScannedStudent: StateFlow<Student?> = _currentScannedStudent.asStateFlow()

    private val _currentScannedCard = MutableStateFlow<Card?>(null)
    val currentScannedCard: StateFlow<Card?> = _currentScannedCard.asStateFlow()

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
                student.studentNumber.contains(query, ignoreCase = true) ||
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
        else students.firstOrNull { it.id.equals(selectedId, ignoreCase = true) || it.studentNumber.equals(selectedId, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Cards associated with the currently selected student
    val selectedStudentCards: StateFlow<List<Card>> = _selectedStudentId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getCardsForStudentFlow(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User feedback notifications
    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage: StateFlow<String?> = _userFeedbackMessage.asStateFlow()

    fun loginAs(role: UserRole, customName: String? = null) {
        _currentUser.value = AuthUser(
            role = role,
            name = customName ?: role.defaultUsername,
            station = role.subtitle
        )
        dismissScanResult()
    }

    fun logout() {
        _currentUser.value = null
        dismissScanResult()
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
            _isScannerOpen.value = false
            val scanResult = repository.verifyStudentByQr(rawCode)
            _activeScanResult.value = scanResult

            val guard = _currentUser.value?.name ?: "Security Guard"

            when (scanResult) {
                is StudentScanResult.Success -> {
                    val student = scanResult.student
                    val card = scanResult.card
                    _currentScannedStudent.value = student
                    _currentScannedCard.value = card
                    _scanError.value = null

                    // Record gate activity log
                    repository.logVerificationScan(
                        ScanLog(
                            studentId = student.id,
                            studentNumber = student.studentNumber,
                            studentName = student.fullName,
                            gradeClass = student.gradeClass,
                            cardId = card?.id,
                            cardIdentifier = card?.cardIdentifier ?: "CRD-UNKNOWN",
                            qrPayload = card?.qrPayload ?: rawCode,
                            decision = if (scanResult.isApproved) GateVerificationDecision.APPROVED else GateVerificationDecision.NOT_APPROVED,
                            feeStatus = student.feesStatus,
                            cardStatus = card?.status ?: CardStatus.ACTIVE,
                            isDayScholar = student.isDayScholar,
                            isApproved = scanResult.isApproved,
                            reason = scanResult.reason,
                            isOfflineDecision = scanResult.isOfflineData,
                            dataSyncTimestampAtScan = scanResult.lastSyncTimestamp,
                            guardName = guard,
                            deviceIdentifier = "GateTerminal-01",
                            gateLocation = "Gate 1 (Main Entrance)"
                        )
                    )

                    context?.let { ctx ->
                        FeedbackHelper.playFeedback(ctx, scanResult.isApproved)
                    }
                }

                is StudentScanResult.CardInactive -> {
                    val student = scanResult.student
                    val card = scanResult.card
                    _currentScannedStudent.value = student
                    _currentScannedCard.value = card
                    _scanError.value = scanResult.reason

                    // Record audit log for inactive card scan
                    repository.logVerificationScan(
                        ScanLog(
                            studentId = student.id,
                            studentNumber = student.studentNumber,
                            studentName = student.fullName,
                            gradeClass = student.gradeClass,
                            cardId = card.id,
                            cardIdentifier = card.cardIdentifier,
                            qrPayload = card.qrPayload,
                            decision = GateVerificationDecision.CARD_INACTIVE,
                            feeStatus = student.feesStatus,
                            cardStatus = scanResult.cardStatus,
                            isDayScholar = student.isDayScholar,
                            isApproved = false,
                            reason = scanResult.reason,
                            isOfflineDecision = scanResult.isOfflineData,
                            dataSyncTimestampAtScan = scanResult.lastSyncTimestamp,
                            guardName = guard,
                            deviceIdentifier = "GateTerminal-01",
                            gateLocation = "Gate 1 (Main Entrance)"
                        )
                    )

                    context?.let { ctx ->
                        FeedbackHelper.playFeedback(ctx, false)
                    }
                }

                is StudentScanResult.StudentNotFound -> {
                    _currentScannedStudent.value = null
                    _currentScannedCard.value = null
                    _scanError.value = scanResult.reason

                    repository.logVerificationScan(
                        ScanLog(
                            studentId = null,
                            studentNumber = scanResult.parsedIdentifier,
                            studentName = "Unregistered (${scanResult.parsedIdentifier})",
                            gradeClass = "Unknown",
                            cardId = null,
                            cardIdentifier = null,
                            qrPayload = rawCode,
                            decision = GateVerificationDecision.STUDENT_NOT_FOUND,
                            feeStatus = null,
                            cardStatus = null,
                            isDayScholar = false,
                            isApproved = false,
                            reason = scanResult.reason,
                            isOfflineDecision = scanResult.isOfflineData,
                            dataSyncTimestampAtScan = scanResult.lastSyncTimestamp,
                            guardName = guard,
                            deviceIdentifier = "GateTerminal-01",
                            gateLocation = "Gate 1 (Main Entrance)"
                        )
                    )

                    context?.let { ctx ->
                        FeedbackHelper.playFeedback(ctx, false)
                    }
                }

                is StudentScanResult.InvalidQr -> {
                    _currentScannedStudent.value = null
                    _currentScannedCard.value = null
                    _scanError.value = scanResult.errorReason

                    repository.logVerificationScan(
                        ScanLog(
                            studentId = null,
                            studentNumber = null,
                            studentName = "Corrupt / Invalid Badge",
                            gradeClass = "Unknown",
                            cardId = null,
                            cardIdentifier = null,
                            qrPayload = rawCode,
                            decision = GateVerificationDecision.INVALID_QR,
                            feeStatus = null,
                            cardStatus = null,
                            isDayScholar = false,
                            isApproved = false,
                            reason = scanResult.errorReason,
                            isOfflineDecision = !syncInfo.value.isOnline,
                            dataSyncTimestampAtScan = syncInfo.value.lastSyncTimestamp,
                            guardName = guard,
                            deviceIdentifier = "GateTerminal-01",
                            gateLocation = "Gate 1 (Main Entrance)"
                        )
                    )

                    context?.let { ctx ->
                        FeedbackHelper.playFeedback(ctx, false)
                    }
                }
            }
        }
    }

    fun dismissScanResult() {
        _currentScannedStudent.value = null
        _currentScannedCard.value = null
        _activeScanResult.value = null
        _scanError.value = null
        _isScannerOpen.value = false
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            val result = repository.syncWithCloud()
            if (result.isSuccess) {
                val summary = result.getOrThrow()
                _userFeedbackMessage.value = "Synced with central server! ${summary.logsUploaded} logs uploaded, ${summary.studentsUpdated} records updated."
            } else {
                _userFeedbackMessage.value = "Sync failed: ${result.exceptionOrNull()?.message ?: "Check connection"}"
            }
        }
    }

    fun toggleNetworkOnline(isOnline: Boolean) {
        repository.setNetworkOnline(isOnline)
        _userFeedbackMessage.value = if (isOnline) "Network connected. Synchronized with school central database." else "Offline Mode enabled. Guard verification is 100% active from local cache."
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
                val student = repository.getStudentById(studentId) ?: repository.getStudentByStudentNumber(studentId)
                val statusText = if (newStatus == FeeStatus.CLEARED) "CLEARED" else "OUTSTANDING"
                _userFeedbackMessage.value = "Fees for ${student?.fullName ?: studentId} marked $statusText. Gate scanner updated immediately!"

                if (_currentScannedStudent.value?.id.equals(studentId, ignoreCase = true) ||
                    _currentScannedStudent.value?.studentNumber.equals(studentId, ignoreCase = true)
                ) {
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
                _userFeedbackMessage.value = "Student ${student.fullName} (${student.studentNumber}) and Active ID Card created successfully!"
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
                if (_currentScannedStudent.value?.id.equals(student.id, ignoreCase = true) ||
                    _currentScannedStudent.value?.studentNumber.equals(student.studentNumber, ignoreCase = true)
                ) {
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
            val student = repository.getStudentById(studentId) ?: repository.getStudentByStudentNumber(studentId)
            repository.deleteStudent(studentId)
            _userFeedbackMessage.value = "Removed student ${student?.fullName ?: studentId}."
            if (_selectedStudentId.value.equals(studentId, ignoreCase = true)) {
                _selectedStudentId.value = null
            }
        }
    }

    // Card Lifecycle Actions
    fun reportCardLost(studentId: String, cardId: String, reason: String = "Reported lost by student/guardian") {
        viewModelScope.launch {
            val result = repository.reportCardLost(studentId, cardId, reason)
            if (result.isSuccess) {
                val card = result.getOrThrow()
                _userFeedbackMessage.value = "Card ${card.cardIdentifier} marked LOST. Gate scanners will immediately deny entry with this card."
            } else {
                _userFeedbackMessage.value = "Failed to report card lost: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun issueReplacementCard(studentId: String, oldCardId: String, reason: String = "Lost card replacement") {
        viewModelScope.launch {
            val result = repository.issueReplacementCard(studentId, oldCardId, reason)
            if (result.isSuccess) {
                val newCard = result.getOrThrow()
                _userFeedbackMessage.value = "New Card ${newCard.cardIdentifier} issued & activated! Previous card was deactivated."
            } else {
                _userFeedbackMessage.value = "Failed to issue replacement card: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deactivateCard(studentId: String, cardId: String, reason: String = "Deactivated by Administrator") {
        viewModelScope.launch {
            val result = repository.deactivateCard(studentId, cardId, reason)
            if (result.isSuccess) {
                _userFeedbackMessage.value = "Card has been deactivated. Entry with this badge is now blocked."
            } else {
                _userFeedbackMessage.value = "Failed to deactivate card: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun issueNewActiveCard(studentId: String, reason: String = "Manual card issuance") {
        viewModelScope.launch {
            val result = repository.issueCard(studentId, null, reason)
            if (result.isSuccess) {
                val card = result.getOrThrow()
                _userFeedbackMessage.value = "New active Card ${card.cardIdentifier} issued!"
            } else {
                _userFeedbackMessage.value = "Failed to issue card: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearFeedbackMessage() {
        _userFeedbackMessage.value = null
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearScanLogs()
            _userFeedbackMessage.value = "Gate activity audit logs cleared."
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetToSampleData()
            _userFeedbackMessage.value = "Database reset to initial sample student roster and card records."
        }
    }

    companion object {
        fun provideFactory(
            context: Context
        ): androidx.lifecycle.ViewModelProvider.Factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = com.example.data.RoomStudentRepository.getInstance(context.applicationContext)
                return MainViewModel(repo) as T
            }
        }
    }
}
