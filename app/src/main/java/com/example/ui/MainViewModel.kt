package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MockStudentRepository
import com.example.data.StudentRepository
import com.example.model.AuthUser
import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.ExeatPass
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.GuardianNotification
import com.example.model.NotificationType
import com.example.model.MealRecord
import com.example.model.MealServingStatus
import com.example.model.MealType
import com.example.model.MealVerificationResult
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentRequirement
import com.example.model.StudentScanResult
import com.example.model.SyncInfo
import com.example.model.SyncStatus
import com.example.model.UserRole
import com.example.util.ExportFormat
import com.example.util.ExportManager
import com.example.util.ExportUtils
import com.example.util.FeedbackHelper
import com.example.util.QrCodeUtils
import com.example.util.QrParseResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    // Current Authenticated User (Defaults to Gate Keeper)
    private val _currentUser = MutableStateFlow<AuthUser?>(
        AuthUser(
            role = UserRole.GATE_KEEPER,
            name = UserRole.GATE_KEEPER.defaultUsername,
            station = UserRole.GATE_KEEPER.subtitle
        )
    )
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    // Students list from repository
    val allStudents: StateFlow<List<Student>> = repository.studentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Gate verification audit logs
    val scanLogs: StateFlow<List<ScanLog>> = repository.scanLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Guardian SMS / Notification dispatch history
    val guardianNotifications: StateFlow<List<GuardianNotification>> = repository.guardianNotificationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Student Exeat and Gate passes
    val exeatPasses: StateFlow<List<ExeatPass>> = repository.exeatPassesFlow
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

    // ==========================================
    // MEALS MASTER STATE & ACCESS CONTROL
    // ==========================================
    private val _activeMealType = MutableStateFlow(MealType.LUNCH)
    val activeMealType: StateFlow<MealType> = _activeMealType.asStateFlow()

    private val _mealRecords = MutableStateFlow<List<MealRecord>>(createInitialSampleMeals())
    val mealRecords: StateFlow<List<MealRecord>> = _mealRecords.asStateFlow()

    private val _mealScanOutcome = MutableStateFlow<MealVerificationResult?>(null)
    val mealScanOutcome: StateFlow<MealVerificationResult?> = _mealScanOutcome.asStateFlow()

    private val _isMealScannerOpen = MutableStateFlow(false)
    val isMealScannerOpen: StateFlow<Boolean> = _isMealScannerOpen.asStateFlow()

    // ==========================================
    // REQUIREMENTS MASTER STATE & CHECKLIST
    // ==========================================
    private val _requirementsOverrides = MutableStateFlow<Map<String, StudentRequirement>>(emptyMap())
    private val _requirementsSearchQuery = MutableStateFlow("")
    val requirementsSearchQuery: StateFlow<String> = _requirementsSearchQuery.asStateFlow()

    private val _isRequirementScannerOpen = MutableStateFlow(false)
    val isRequirementScannerOpen: StateFlow<Boolean> = _isRequirementScannerOpen.asStateFlow()

    private val _scannedRequirementStudentId = MutableStateFlow<String?>(null)
    val scannedRequirementStudentId: StateFlow<String?> = _scannedRequirementStudentId.asStateFlow()

    val requirementsList: StateFlow<List<StudentRequirement>> = combine(
        allStudents,
        _requirementsOverrides,
        _requirementsSearchQuery
    ) { students, overrides, query ->
        students.map { student ->
            overrides[student.id] ?: StudentRequirement(
                studentId = student.id,
                studentNumber = student.studentNumber,
                studentName = student.fullName,
                gradeClass = student.gradeClass,
                uniformComplete = student.feesStatus == FeeStatus.CLEARED,
                sportsKitComplete = true,
                textbooksSubmitted = student.feesStatus == FeeStatus.CLEARED,
                medicalFormSigned = true,
                schoolIdIssued = true,
                rulesAgreementSigned = true,
                busPassCleared = student.isDayScholar,
                notes = student.notes
            )
        }.filter {
            if (query.isBlank()) true
            else it.studentName.contains(query, ignoreCase = true) ||
                 it.studentNumber.contains(query, ignoreCase = true) ||
                 it.gradeClass.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun issueExeatPass(pass: ExeatPass) {
        viewModelScope.launch {
            val result = repository.issueExeatPass(pass)
            if (result.isSuccess) {
                _userFeedbackMessage.value = "Exeat Pass ${pass.passNumber} issued for ${pass.studentName}."
            } else {
                _userFeedbackMessage.value = "Failed to issue exeat pass: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun markExeatUsed(passId: String) {
        viewModelScope.launch {
            val result = repository.markExeatPassUsed(passId)
            if (result.isSuccess) {
                _userFeedbackMessage.value = "Exeat Pass marked as USED. Gate exit recorded."
            }
        }
    }

    fun sendCustomGuardianAlert(studentName: String, guardianPhone: String, message: String) {
        viewModelScope.launch {
            val notif = GuardianNotification(
                studentId = "manual-dispatch",
                studentNumber = "MANUAL",
                studentName = studentName,
                guardianName = "Guardian of $studentName",
                guardianPhone = guardianPhone,
                type = NotificationType.ARRIVAL,
                message = message,
                timestamp = System.currentTimeMillis(),
                isDelivered = true
            )
            repository.sendGuardianNotification(notif)
            _userFeedbackMessage.value = "SMS Alert dispatched to $guardianPhone!"
        }
    }

    fun exportGateLogsCsv(context: Context) {
        val logs = scanLogs.value
        if (logs.isEmpty()) {
            _userFeedbackMessage.value = "No gate scan records to export."
            return
        }
        val csv = ExportUtils.generateGateLogsCsv(logs)
        ExportUtils.shareData(
            context = context,
            content = csv,
            subject = "Oakridge Gate Verification Logs (CSV)",
            isCsv = true
        )
        _userFeedbackMessage.value = "Exported ${logs.size} log records to CSV share sheet."
    }

    fun exportAttendanceSummaryReport(context: Context) {
        val report = ExportUtils.generateAttendanceSummaryReport(
            allStudents = allStudents.value,
            scanLogs = scanLogs.value
        )
        ExportUtils.shareData(
            context = context,
            content = report,
            subject = "Oakridge Gate Attendance Summary Report",
            isCsv = false
        )
        _userFeedbackMessage.value = "Gate Attendance Summary Report opened in share sheet."
    }

    // ==========================================
    // MEALS MASTER ACTIONS
    // ==========================================
    fun selectMealType(mealType: MealType) {
        _activeMealType.value = mealType
    }

    fun setActiveMealType(mealType: MealType) {
        selectMealType(mealType)
    }

    fun openMealScanner() {
        _isMealScannerOpen.value = true
    }

    fun closeMealScanner() {
        _isMealScannerOpen.value = false
    }

    fun dismissMealScanOutcome() {
        _mealScanOutcome.value = null
    }

    fun dismissMealOutcome() {
        dismissMealScanOutcome()
    }

    fun verifyAndServeMeal(rawCode: String, context: Context? = null) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val parsed = QrCodeUtils.parseQrCode(rawCode)
        val students = allStudents.value

        val student = when (parsed) {
            is QrParseResult.ValidStudentNumber -> {
                students.find { it.studentNumber.equals(parsed.studentNumber, ignoreCase = true) }
            }
            is QrParseResult.ValidInternalId -> {
                students.find { it.id.equals(parsed.internalId, ignoreCase = true) }
            }
            is QrParseResult.Invalid -> {
                students.find {
                    it.studentNumber.equals(rawCode.trim(), ignoreCase = true) ||
                    it.uniqueQrCode.equals(rawCode.trim(), ignoreCase = true)
                }
            }
        }

        if (student == null) {
            _mealScanOutcome.value = MealVerificationResult.StudentNotFound(rawCode)
            FeedbackHelper.playDeniedSoundAndHaptic(context)
            return
        }

        val activeMeal = _activeMealType.value

        // Check for double-serving on same date and same meal session
        val previousRecord = _mealRecords.value.find {
            it.studentId == student.id &&
            it.mealType == activeMeal &&
            it.mealDate == todayStr &&
            it.status == MealServingStatus.SERVED
        }

        if (previousRecord != null) {
            _mealScanOutcome.value = MealVerificationResult.BlockedDoubleServing(student, activeMeal, previousRecord)
            FeedbackHelper.playWarningSoundAndHaptic(context)
            return
        }

        // Meal Approved & Served
        val newRecord = MealRecord(
            studentId = student.id,
            studentNumber = student.studentNumber,
            studentName = student.fullName,
            gradeClass = student.gradeClass,
            mealType = activeMeal,
            mealDate = todayStr,
            timestamp = System.currentTimeMillis(),
            serverName = _currentUser.value?.name ?: "Chef Jackson Omondi",
            status = MealServingStatus.SERVED
        )

        _mealRecords.value = listOf(newRecord) + _mealRecords.value
        _mealScanOutcome.value = MealVerificationResult.Success(newRecord)
        FeedbackHelper.playApprovedSoundAndHaptic(context)
    }

    // ==========================================
    // REQUIREMENTS MASTER ACTIONS
    // ==========================================
    fun openRequirementScanner() {
        _isRequirementScannerOpen.value = true
    }

    fun closeRequirementScanner() {
        _isRequirementScannerOpen.value = false
    }

    fun setRequirementsSearchQuery(query: String) {
        _requirementsSearchQuery.value = query
    }

    fun selectRequirementStudent(studentId: String?) {
        _scannedRequirementStudentId.value = studentId
    }

    fun handleRequirementBarcodeScan(rawCode: String) {
        _isRequirementScannerOpen.value = false
        val parsed = QrCodeUtils.parseQrCode(rawCode)
        val students = allStudents.value
        val student = when (parsed) {
            is QrParseResult.ValidStudentNumber -> students.find { it.studentNumber.equals(parsed.studentNumber, ignoreCase = true) }
            is QrParseResult.ValidInternalId -> students.find { it.id.equals(parsed.internalId, ignoreCase = true) }
            is QrParseResult.Invalid -> students.find { it.studentNumber.equals(rawCode.trim(), ignoreCase = true) }
        }
        if (student != null) {
            _scannedRequirementStudentId.value = student.id
            _requirementsSearchQuery.value = student.studentNumber
            _userFeedbackMessage.value = "Pulled up requirements record for ${student.fullName}."
        } else {
            _userFeedbackMessage.value = "Student not found for scanned QR: $rawCode"
        }
    }

    fun toggleRequirementItem(studentId: String, itemKey: String) {
        val current = requirementsList.value.find { it.studentId == studentId } ?: return
        val updated = when (itemKey) {
            "uniform" -> current.copy(uniformComplete = !current.uniformComplete, updatedAt = System.currentTimeMillis())
            "sports" -> current.copy(sportsKitComplete = !current.sportsKitComplete, updatedAt = System.currentTimeMillis())
            "textbooks" -> current.copy(textbooksSubmitted = !current.textbooksSubmitted, updatedAt = System.currentTimeMillis())
            "medical" -> current.copy(medicalFormSigned = !current.medicalFormSigned, updatedAt = System.currentTimeMillis())
            "idCard" -> current.copy(schoolIdIssued = !current.schoolIdIssued, updatedAt = System.currentTimeMillis())
            "rules" -> current.copy(rulesAgreementSigned = !current.rulesAgreementSigned, updatedAt = System.currentTimeMillis())
            "busPass" -> current.copy(busPassCleared = !current.busPassCleared, updatedAt = System.currentTimeMillis())
            else -> current
        }
        _requirementsOverrides.value = _requirementsOverrides.value + (studentId to updated)
    }

    fun markAllRequirementsCleared(studentId: String) {
        val current = requirementsList.value.find { it.studentId == studentId } ?: return
        val updated = current.copy(
            uniformComplete = true,
            sportsKitComplete = true,
            textbooksSubmitted = true,
            medicalFormSigned = true,
            schoolIdIssued = true,
            rulesAgreementSigned = true,
            busPassCleared = true,
            updatedAt = System.currentTimeMillis()
        )
        _requirementsOverrides.value = _requirementsOverrides.value + (studentId to updated)
        _userFeedbackMessage.value = "All items marked CLEARED for ${current.studentName}!"
    }

    fun updateRequirementNotes(studentId: String, notes: String) {
        val current = requirementsList.value.find { it.studentId == studentId } ?: return
        val updated = current.copy(notes = notes, updatedAt = System.currentTimeMillis())
        _requirementsOverrides.value = _requirementsOverrides.value + (studentId to updated)
    }

    // ==========================================
    // MULTI-FORMAT EXPORT DISPATCHER (Excel, Word, CSV, Text)
    // ==========================================
    fun exportDataset(datasetType: String, format: ExportFormat, context: Context) {
        exportDataset(context, datasetType, format)
    }

    fun exportDataset(context: Context, datasetType: String, format: ExportFormat) {
        when (datasetType.uppercase()) {
            "STUDENTS" -> {
                val list = allStudents.value
                val content = ExportManager.generateStudentsExport(list, format)
                ExportManager.downloadAndShare(
                    context = context,
                    content = content,
                    baseFileName = "Oakridge_Student_Registry",
                    format = format,
                    subject = "Oakridge Academy - Student Registry Directory"
                )
                _userFeedbackMessage.value = "Exported ${list.size} student records as ${format.label}."
            }
            "GATE_LOGS" -> {
                val logs = scanLogs.value
                val content = ExportManager.generateGateLogsExport(logs, format)
                ExportManager.downloadAndShare(
                    context = context,
                    content = content,
                    baseFileName = "Oakridge_Gate_Verification_Logs",
                    format = format,
                    subject = "Oakridge Academy - Gate Verification Logs"
                )
                _userFeedbackMessage.value = "Exported ${logs.size} gate logs as ${format.label}."
            }
            "MEALS" -> {
                val meals = mealRecords.value
                val content = ExportManager.generateMealsExport(meals, format)
                ExportManager.downloadAndShare(
                    context = context,
                    content = content,
                    baseFileName = "Oakridge_Dining_Hall_Logs",
                    format = format,
                    subject = "Oakridge Academy - Dining Hall Access Log"
                )
                _userFeedbackMessage.value = "Exported ${meals.size} meal serving logs as ${format.label}."
            }
            "REQUIREMENTS" -> {
                val reqs = requirementsList.value
                val content = ExportManager.generateRequirementsExport(reqs, format)
                ExportManager.downloadAndShare(
                    context = context,
                    content = content,
                    baseFileName = "Oakridge_Requirements_Clearance",
                    format = format,
                    subject = "Oakridge Academy - Requirements Compliance Report"
                )
                _userFeedbackMessage.value = "Exported ${reqs.size} requirements records as ${format.label}."
            }
        }
    }

    private fun createInitialSampleMeals(): List<MealRecord> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val now = System.currentTimeMillis()
        return listOf(
            MealRecord(
                studentId = "stu-001",
                studentNumber = "OAK-2026-0001",
                studentName = "Kato Alex",
                gradeClass = "Senior 3-A",
                mealType = MealType.LUNCH,
                mealDate = today,
                timestamp = now - (18 * 60 * 1000),
                serverName = "Chef Jackson Omondi",
                status = MealServingStatus.SERVED
            ),
            MealRecord(
                studentId = "stu-002",
                studentNumber = "OAK-2026-0002",
                studentName = "Nalwadda Grace",
                gradeClass = "Senior 4-B",
                mealType = MealType.LUNCH,
                mealDate = today,
                timestamp = now - (32 * 60 * 1000),
                serverName = "Chef Jackson Omondi",
                status = MealServingStatus.SERVED
            ),
            MealRecord(
                studentId = "stu-004",
                studentNumber = "OAK-2026-0004",
                studentName = "Mukasa David",
                gradeClass = "Senior 1-A",
                mealType = MealType.BREAKFAST,
                mealDate = today,
                timestamp = now - (4 * 3600 * 1000),
                serverName = "Chef Jackson Omondi",
                status = MealServingStatus.SERVED
            )
        )
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
            _userFeedbackMessage.value = "All local records cleared."
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
