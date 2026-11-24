package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StudentRepository
import com.example.data.auth.SupabaseClientFactory
import com.example.data.auth.SupabaseStaffAuthenticator
import com.example.model.AuthUser
import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.ExeatPass
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.GuardianNotification
import com.example.model.NotificationType
import com.example.model.ScanDepartment
import com.example.model.GateDirection
import com.example.model.DepartmentVerificationResult
import com.example.model.AttendanceRecord
import com.example.model.ExamException
import com.example.model.ExamClearanceStatus
import com.example.data.local.MealRecordEntity
import com.example.data.database
import com.example.data.syncManagerInstance
import com.example.util.QrUnifiedVerifier
import com.example.model.MealRecord
import com.example.model.MealServingStatus
import com.example.model.MealType
import com.example.model.MealVerificationResult
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentRequirement
import com.example.model.StudentScanResult
import com.example.model.StaffPermission
import com.example.model.SyncInfo
import com.example.model.SyncStatus
import com.example.model.UserRole
import com.example.crypto.KeystoreIssuerManager
import com.example.crypto.CardCryptoUtils
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.example.crypto.TrustedIssuerRegistry
import com.example.crypto.TrustedIssuerEntry
import com.example.util.CardCryptoManager
import com.example.util.ExportFormat
import com.example.util.ExportManager
import com.example.util.ExportUtils
import com.example.util.FeedbackHelper
import com.example.util.QrCodeUtils
import com.example.util.QrParseResult
import com.example.util.SecurityManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FeeFilter { ALL, CLEARED, OUTSTANDING }

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: StudentRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val VERSION_ONE_ROLES = setOf(
            UserRole.ADMINISTRATOR,
            UserRole.HEAD_TEACHER_MANAGEMENT,
            UserRole.GATE_STAFF,
        UserRole.MEAL_SERVING_STAFF
    )

    // Supabase sessions are restored from the encrypted Android Keystore vault.
    // Every restore revalidates the user's active LTC membership against Postgres RLS.
    private var sessionRestoreStarted = false
    private var supabaseAuthenticator: SupabaseStaffAuthenticator? = null
    private val _currentUser = MutableStateFlow<AuthUser?>(null)

    fun restoreSupabaseSession(context: Context) {
        if (sessionRestoreStarted || _currentUser.value != null) return
        sessionRestoreStarted = true
        viewModelScope.launch {
            try {
                val authenticator = SupabaseStaffAuthenticator(
                    SupabaseClientFactory.getInstance(context.applicationContext)
                )
                supabaseAuthenticator = authenticator
                val staff = authenticator.restoreSession()
                if (staff != null && _currentUser.value == null) {
                    loginAs(staff.role, staff.displayName, staff.hasExplicitFinanceAccess)
                }
            } catch (_: Exception) {
                // Fail closed: network, configuration or membership errors never grant a role.
                // Keep the login screen visible so the staff member can retry.
            } finally {
                sessionRestoreStarted = false
            }
        }
    }
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

    // Multi-Department Unified Scanner State
    private val _selectedDepartment = MutableStateFlow(ScanDepartment.GATE)
    val selectedDepartment: StateFlow<ScanDepartment> = _selectedDepartment.asStateFlow()

    private val _departmentScanResult = MutableStateFlow<DepartmentVerificationResult?>(null)
    val departmentScanResult: StateFlow<DepartmentVerificationResult?> = _departmentScanResult.asStateFlow()

    private val _gateDirection = MutableStateFlow(GateDirection.ENTRY)
    val gateDirection: StateFlow<GateDirection> = _gateDirection.asStateFlow()

    private val _selectedAttendanceSubject = MutableStateFlow("Mathematics")
    val selectedAttendanceSubject: StateFlow<String> = _selectedAttendanceSubject.asStateFlow()

    private val _selectedAttendanceSession = MutableStateFlow("Morning Lesson 1")
    val selectedAttendanceSession: StateFlow<String> = _selectedAttendanceSession.asStateFlow()

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords.asStateFlow()

    private val _examExceptions = MutableStateFlow<List<ExamException>>(emptyList())
    val examExceptions: StateFlow<List<ExamException>> = _examExceptions.asStateFlow()

    private val _activeMealType = MutableStateFlow(MealType.LUNCH)
    val activeMealType: StateFlow<MealType> = _activeMealType.asStateFlow()

    private val _mealRecords = MutableStateFlow<List<MealRecord>>(emptyList())
    val mealRecords: StateFlow<List<MealRecord>> = _mealRecords.asStateFlow()

    private val _mealScanOutcome = MutableStateFlow<MealVerificationResult?>(null)
    val mealScanOutcome: StateFlow<MealVerificationResult?> = _mealScanOutcome.asStateFlow()

    private val _isMealScannerOpen = MutableStateFlow(false)
    val isMealScannerOpen: StateFlow<Boolean> = _isMealScannerOpen.asStateFlow()

    init {
        // Operational history is sourced from Room, not in-memory demo lists.
        repository.database?.let { db ->
            viewModelScope.launch {
                db.mealRecordDao().observeAll().collect { rows ->
                    _mealRecords.value = rows.map { it.toDomain() }
                }
            }
            viewModelScope.launch {
                db.attendanceRecordDao().observeAll().collect { rows ->
                    _attendanceRecords.value = rows.map { it.toDomain() }
                }
            }
            viewModelScope.launch {
                db.examExceptionDao().observeAll().collect { rows ->
                    _examExceptions.value = rows.map { it.toDomain() }
                }
            }
        }
    }


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
                // Requirements are independent records. Fees and boarding status
                // are not evidence that a uniform, textbook, form, ID or bus pass
                // has been physically checked or issued.
                notes = student.notes
            )
        }.filter {
            if (query.isBlank()) true
            else it.studentName.contains(query, ignoreCase = true) ||
                 it.studentNumber.contains(query, ignoreCase = true) ||
                 it.gradeClass.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loginAs(role: UserRole, customName: String? = null, hasFinancePermission: Boolean = false) {
        if (role !in VERSION_ONE_ROLES) {
            _currentUser.value = null
            _userFeedbackMessage.value = "This staff role is not enabled in Version 1."
            return
        }
        _currentUser.value = AuthUser(
            role = role,
            name = customName ?: role.defaultUsername,
            station = role.subtitle,
            hasExplicitFinanceAccess = hasFinancePermission
        )
        dismissScanResult()
        refreshTrustedIssuerKeys()
    }

    /**
     * Authenticates staff against the provisioned terminal credentials before opening a duty session.
     * Failed attempts leave the existing session unchanged.
     */
    fun authenticateAndLogin(
        context: Context,
        role: UserRole,
        pin: String,
        customName: String? = null
    ): Boolean {
        if (role !in VERSION_ONE_ROLES) {
            _userFeedbackMessage.value = "This staff role is not enabled in Version 1."
            return false
        }
        if (!SecurityManager.isProvisioned(context)) {
            _userFeedbackMessage.value = "Login unavailable: this terminal has not been provisioned by an administrator."
            return false
        }
        if (!SecurityManager.verifyRolePin(context, role, pin)) {
            _userFeedbackMessage.value = "Login failed: incorrect PIN for ${role.subtitle}."
            return false
        }

        val resolvedName = when {
            !customName.isNullOrBlank() -> customName.trim()
            role == UserRole.ADMINISTRATOR -> SecurityManager.getAdminName(context)
            else -> role.defaultUsername
        }
        val user = AuthUser(
            role = role,
            name = resolvedName,
            station = role.subtitle,
            hasExplicitFinanceAccess = role == UserRole.BURSAR_FINANCE
        )
        _currentUser.value = user
        SecurityManager.saveActiveSession(context, user)
        dismissScanResult()
        _userFeedbackMessage.value = null
        return true
    }


    /**
     * Locks terminal session. Unlocks only after entering valid staff credentials.
     */
    fun lockSession() {
        _currentUser.value = null
        dismissScanResult()
    }

    var lockOnBackgroundEnabled = true

    fun lockSessionOnBackground() {
        if (lockOnBackgroundEnabled && _currentUser.value != null) {
            lockSession()
        }
    }

    fun logout() {
        lockSession()
        // Explicit logout revokes the Supabase session and clears its encrypted refresh token.
        viewModelScope.launch {
            runCatching {
                val authenticator = supabaseAuthenticator ?: appContext?.let {
                    SupabaseStaffAuthenticator(SupabaseClientFactory.getInstance(it))
                }
                authenticator?.signOut()
            }
            // Clear any legacy Firebase session left on an upgraded installation.
        }
    }

    fun openScanner() {
        _scanError.value = null
        _isScannerOpen.value = true
    }

    fun closeScanner() {
        _isScannerOpen.value = false
    }

    private val gateScanThrottle = GateScanThrottle(cooldownMs = 2_000L)
    private val departmentScanThrottle = GateScanThrottle(cooldownMs = 2_000L)

    fun handleBarcodeScan(rawCode: String, context: Context? = null) {
        if (!requirePermission(StaffPermission.VERIFY_GATE_ACCESS, "Gate verification scanning")) {
            _scanError.value = "Access Denied: Gate verification scanning requires Gate Staff authorization."
            return
        }
        if (!gateScanThrottle.tryBegin(rawCode)) return
        val scanJob = viewModelScope.launch {
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
                            decision = when { scanResult.isPolicyPending -> GateVerificationDecision.POLICY_PENDING; scanResult.isApproved -> GateVerificationDecision.APPROVED; else -> GateVerificationDecision.NOT_APPROVED },
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
                        if (!scanResult.isPolicyPending) FeedbackHelper.playFeedback(ctx, scanResult.isApproved)
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

            // Approved students get a brief acknowledgement, then the scanner resumes.
            // Denials and verification errors remain visible until an officer dismisses them.
            if (scanResult is StudentScanResult.Success && scanResult.isApproved) {
                delay(1_500L)
                dismissScanResult()
                openScanner()
            }
        }
        scanJob.invokeOnCompletion { gateScanThrottle.finish() }
    }

    /**
     * Manual gate fallback is deliberately separate from QR verification. It looks up
     * an enrolled student by admission number and records the officer's reason in the
     * existing persistent scan audit log.
     */
    fun handleManualGateLookup(studentNumber: String, reason: String, context: Context? = null) {
        if (!requirePermission(StaffPermission.VERIFY_GATE_ACCESS, "Manual gate verification")) return
        if (reason.isBlank()) {
            _userFeedbackMessage.value = "A reason is required for manual verification."
            return
        }
        viewModelScope.launch {
            val student = repository.getStudentByStudentNumber(studentNumber.trim())
            val guard = _currentUser.value?.name ?: "Security Guard"
            if (student == null) {
                _currentScannedStudent.value = null
                _currentScannedCard.value = null
                _scanError.value = "No enrolled student matches that admission number."
                _activeScanResult.value = StudentScanResult.StudentNotFound(
                    parsedIdentifier = studentNumber.trim(),
                    reason = "Manual lookup: student record not found.",
                    isOfflineData = !syncInfo.value.isOnline,
                    lastSyncTimestamp = syncInfo.value.lastSyncTimestamp
                )
                repository.logVerificationScan(
                    ScanLog(
                        studentId = null,
                        studentNumber = studentNumber.trim(),
                        studentName = "Manual lookup - no matching student",
                        gradeClass = "Unknown",
                        qrPayload = "MANUAL_ENTRY",
                        decision = GateVerificationDecision.STUDENT_NOT_FOUND,
                        feeStatus = null,
                        cardStatus = null,
                        isDayScholar = false,
                        isApproved = false,
                        reason = "MANUAL_ENTRY: ${reason.trim()} | Student not found",
                        isOfflineDecision = !syncInfo.value.isOnline,
                        dataSyncTimestampAtScan = syncInfo.value.lastSyncTimestamp,
                        guardName = guard,
                        deviceIdentifier = "GateTerminal-01",
                        gateLocation = "Gate 1 (Main Entrance)"
                    )
                )
                context?.let { FeedbackHelper.playFeedback(it, false) }
                return@launch
            }

            // A manual admission-number lookup is not proof of possession of a valid
            // card and must never grant entry automatically. Hold for the school's
            // approved manual-exception procedure; do not infer fee policy here.
            val approved = false
            val resultReason = "Student identity matched. Entry decision requires an authorised school exception."
            _currentScannedStudent.value = student
            _currentScannedCard.value = null
            _scanError.value = if (approved) null else resultReason
            _activeScanResult.value = StudentScanResult.Success(
                student = student,
                card = null,
                isApproved = approved,
                reason = resultReason,
                isOfflineData = !syncInfo.value.isOnline,
                lastSyncTimestamp = syncInfo.value.lastSyncTimestamp
            )
            repository.logVerificationScan(
                ScanLog(
                    studentId = student.id,
                    studentNumber = student.studentNumber,
                    studentName = student.fullName,
                    gradeClass = student.gradeClass,
                    cardId = null,
                    cardIdentifier = null,
                    qrPayload = "MANUAL_ENTRY",
                    decision = if (approved) GateVerificationDecision.APPROVED else GateVerificationDecision.NOT_APPROVED,
                    feeStatus = null,
                    cardStatus = null,
                    isDayScholar = student.isDayScholar,
                    isApproved = approved,
                    reason = "MANUAL_ENTRY: ${reason.trim()} | $resultReason",
                    isOfflineDecision = !syncInfo.value.isOnline,
                    dataSyncTimestampAtScan = syncInfo.value.lastSyncTimestamp,
                    guardName = guard,
                    deviceIdentifier = "GateTerminal-01",
                    gateLocation = "Gate 1 (Main Entrance)"
                )
            )
            context?.let { FeedbackHelper.playFeedback(it, approved) }
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
                // Refresh public issuer keys after the data sync so gate terminals
                // learn newly enrolled or revoked issuers without reinstalling the app.
                refreshTrustedIssuerKeys()
                val summary = result.getOrThrow()
                _userFeedbackMessage.value = "Synced with central server! ${summary.logsUploaded} logs uploaded, ${summary.studentsUpdated} records updated."
            } else {
                _userFeedbackMessage.value = "Sync failed: ${result.exceptionOrNull()?.message ?: "Check connection"}"
            }
        }
    }

    fun toggleNetworkOnline(isOnline: Boolean) {
        repository.setNetworkOnline(isOnline)
        if (isOnline) {
            _userFeedbackMessage.value = "Network connected. Synchronizing with the school database…"
            triggerCloudSync()
        } else {
            _userFeedbackMessage.value = "Offline mode enabled. Verification continues using the local cache."
        }
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

    fun requirePermission(permission: StaffPermission, actionName: String): Boolean {
        val user = _currentUser.value
        if (user == null) {
            _userFeedbackMessage.value = "Access Denied: Unauthenticated session. Please authenticate first."
            return false
        }
        if (!user.hasPermission(permission)) {
            val roleMsg = when (permission) {
                StaffPermission.MANAGE_FEES -> "Access Denied: $actionName requires explicit finance authorization on an Admin account."
                StaffPermission.MANAGE_STUDENTS -> "Access Denied: $actionName requires Administrator privileges."
                StaffPermission.VERIFY_GATE_ACCESS -> "Access Denied: $actionName requires Gate Staff authorization."
                StaffPermission.SERVE_MEALS -> "Access Denied: $actionName requires Meal-Serving Staff authorization."
                StaffPermission.EXAM_CLEARANCE -> "Access Denied: $actionName requires Examination Staff authorization."
                StaffPermission.SYSTEM_CONFIGURATION -> "Access Denied: $actionName requires Administrator privileges."
                else -> "Access Denied: Insufficient staff permissions for $actionName."
            }
            _userFeedbackMessage.value = roleMsg
            return false
        }
        return true
    }

    fun requireAdminRole(actionName: String): Boolean {
        return requirePermission(StaffPermission.MANAGE_STUDENTS, actionName)
    }

    fun requireFinanceRole(actionName: String): Boolean {
        return requirePermission(StaffPermission.MANAGE_FEES, actionName)
    }

    fun updateFeeStatus(studentId: String, newStatus: FeeStatus, outstandingAmount: Double = 0.0) {
        if (!requireFinanceRole("Modifying student fee status")) return
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
        if (!requireAdminRole("Registering new students")) {
            onComplete(false, "Access Denied: Administrator role required.")
            return
        }
        viewModelScope.launch {
            val result = repository.addStudent(student)
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Failed to add student"
                _userFeedbackMessage.value = msg
                onComplete(false, msg)
                return@launch
            }

            // A successful Room write is not proof that Firestore accepted the record.
            // Attempt a sync before claiming cloud registration, and keep the local
            // record queued for retry if network/rules reject the upload.
            val syncResult = repository.syncWithCloud()
            if (syncResult.isSuccess) {
                val msg = "Student ${student.fullName} (${student.studentNumber}) and active ID card are saved and synchronized."
                _userFeedbackMessage.value = msg
                onComplete(true, msg)
            } else {
                val detail = syncResult.exceptionOrNull()?.localizedMessage
                    ?.takeIf { it.isNotBlank() }
                val msg = buildString {
                    append("Student ${student.fullName} (${student.studentNumber}) is saved on this device, but cloud sync is pending. ")
                    append("It may not appear on other phones until synchronization succeeds.")
                    if (detail != null) append(" Details: ").append(detail)
                }
                _userFeedbackMessage.value = msg
                // Local registration succeeded; preserve the record and allow the UI
                // to close the form while clearly disclosing that cloud sync is pending.
                onComplete(true, msg)
            }
        }
    }

    fun registerImportedStudents(students: List<Student>, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        if (!requireAdminRole("Importing roster students")) {
            onComplete(false, "Access Denied: Administrator role required.")
            return
        }
        if (students.isEmpty()) {
            onComplete(false, "No valid students were selected for import.")
            return
        }
        viewModelScope.launch {
            val result = repository.addStudentsWithoutCards(students)
            if (result.isFailure) {
                val message = result.exceptionOrNull()?.localizedMessage ?: "Failed to import student roster"
                _userFeedbackMessage.value = message
                onComplete(false, message)
                return@launch
            }
            val syncResult = repository.syncWithCloud()
            val message = if (syncResult.isSuccess) {
                "${students.size} student record(s) imported and synchronized. No ID cards were issued."
            } else {
                "${students.size} student record(s) saved locally; cloud sync is pending. No ID cards were issued."
            }
            _userFeedbackMessage.value = message
            onComplete(true, message)
        }
    }

    fun updateStudentDetails(student: Student, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        if (!requireAdminRole("Updating student records")) {
            onComplete(false, "Access Denied: Administrator role required.")
            return
        }
        viewModelScope.launch {
            val result = repository.updateStudent(student)
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Failed to update student"
                _userFeedbackMessage.value = msg
                onComplete(false, msg)
                return@launch
            }

            if (_currentScannedStudent.value?.id.equals(student.id, ignoreCase = true) ||
                _currentScannedStudent.value?.studentNumber.equals(student.studentNumber, ignoreCase = true)
            ) {
                _currentScannedStudent.value = student
            }

            val syncResult = repository.syncWithCloud()
            if (syncResult.isSuccess) {
                val msg = "Updated ${student.fullName} and synchronized the change to the school database."
                _userFeedbackMessage.value = msg
                onComplete(true, msg)
            } else {
                val detail = syncResult.exceptionOrNull()?.localizedMessage
                val msg = "Updated ${student.fullName} on this device. Cloud sync is pending and will retry." +
                    (detail?.takeIf { it.isNotBlank() }?.let { " Details: $it" } ?: "")
                _userFeedbackMessage.value = msg
                onComplete(true, msg)
            }
        }
    }

    fun deleteStudentRecord(studentId: String) {
        if (!requireAdminRole("Deleting student records")) return
        viewModelScope.launch {
            val student = repository.getStudentById(studentId) ?: repository.getStudentByStudentNumber(studentId)
            val deleteResult = repository.deleteStudent(studentId)
            if (deleteResult.isFailure) {
                _userFeedbackMessage.value = "Could not deactivate student: ${deleteResult.exceptionOrNull()?.localizedMessage ?: "local database error"}"
                return@launch
            }

            if (_selectedStudentId.value.equals(studentId, ignoreCase = true)) {
                _selectedStudentId.value = null
            }

            val syncResult = repository.syncWithCloud()
            val name = student?.fullName ?: studentId
            _userFeedbackMessage.value = if (syncResult.isSuccess) {
                "Deactivated $name and synchronized the change to the school database."
            } else {
                "Deactivated $name on this device. Cloud sync is pending and will retry. " +
                    (syncResult.exceptionOrNull()?.localizedMessage ?: "")
            }
        }
    }

    // Card Lifecycle Actions
    fun reportCardLost(studentId: String, cardId: String, reason: String = "Reported lost by student/guardian") {
        if (!requireAdminRole("Reporting or updating card status")) return
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
        if (!requireAdminRole("Issuing replacement cards")) return
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
        if (!requireAdminRole("Deactivating student cards")) return
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
        if (!requireAdminRole("Issuing new student cards")) return
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

    /**
     * Authenticated Supervisor Emergency Gate Override.
     * Allows authorized supervisors to admit a student with outstanding fees/issues,
     * while strictly recording the action in the audit log and preventing unknown card bypasses.
     */
    fun authorizeEmergencyOverride(
        studentId: String,
        supervisorName: String,
        overridePin: String,
        reason: String,
        context: Context,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val authenticatedSupervisor = _currentUser.value
        if (authenticatedSupervisor == null ||
            authenticatedSupervisor.role !in setOf(
                UserRole.ADMINISTRATOR,
                UserRole.HEAD_TEACHER_MANAGEMENT
            )
        ) {
            val err = "Emergency override requires an authenticated Administrator or Head Teacher session."
            _userFeedbackMessage.value = err
            onResult(false, err)
            return
        }

        if (!com.example.util.SecurityManager.verifySupervisorOverridePin(context, overridePin)) {
            val err = "Invalid supervisor PIN. Emergency override denied."
            _userFeedbackMessage.value = err
            onResult(false, err)
            return
        }

        val currentScan = _activeScanResult.value
        // A supervisor may override a decision only for the exact student whose
        // valid card was just scanned. Never let a valid scan for Student A be
        // reused to approve Student B.
        val scannedStudent = when (currentScan) {
            is StudentScanResult.Success -> currentScan.student
            is StudentScanResult.CardInactive -> {
                val err = "Security restriction: Inactive, revoked, or replaced cards cannot be overridden."
                _userFeedbackMessage.value = err
                onResult(false, err)
                return
            }
            else -> null
        }
        if (scannedStudent == null) {
            val err = "Security restriction: Emergency override cannot authorize unknown or invalid QR cards."
            _userFeedbackMessage.value = err
            onResult(false, err)
            return
        }
        if (!scannedStudent.id.equals(studentId, ignoreCase = true) &&
            !scannedStudent.studentNumber.equals(studentId, ignoreCase = true)
        ) {
            val err = "Security restriction: Override target must match the student identified by the current scan."
            _userFeedbackMessage.value = err
            onResult(false, err)
            return
        }

        val scannedCard = (currentScan as? StudentScanResult.Success)?.card ?: run {
            val err = "Security restriction: A verified student card is required for an emergency override."
            _userFeedbackMessage.value = err
            onResult(false, err)
            return
        }

        viewModelScope.launch {
            val student = repository.getStudentById(scannedStudent.id)
                ?: repository.getStudentByStudentNumber(scannedStudent.studentNumber)
            if (student == null) {
                val err = "Student not found in school registry."
                _userFeedbackMessage.value = err
                onResult(false, err)
                return@launch
            }

            // The displayed name is not an authority source. Attribute the action
            // to the authenticated session identity so a caller cannot forge the audit trail.
            val authenticatedName = authenticatedSupervisor.name
            val overrideReason = "EMERGENCY SUPERVISOR OVERRIDE: $reason (Authorized by $authenticatedName)"

            // Record this explicit emergency override in the persistent audit log
            repository.logVerificationScan(
                ScanLog(
                    studentId = student.id,
                    studentNumber = student.studentNumber,
                    studentName = student.fullName,
                    gradeClass = student.gradeClass,
                    cardId = scannedCard.id,
                    cardIdentifier = scannedCard.cardIdentifier,
                    qrPayload = scannedCard.qrPayload,
                    decision = GateVerificationDecision.APPROVED,
                    feeStatus = student.feesStatus,
                    cardStatus = scannedCard.status,
                    isDayScholar = student.isDayScholar,
                    isApproved = true,
                    reason = overrideReason,
                    isOfflineDecision = true,
                    guardName = "$authenticatedName (Supervisor Override)",
                    deviceIdentifier = "GateTerminal-01",
                    gateLocation = "Gate 1 (Main Entrance)"
                )
            )

            // Update active scan result to Approved with supervisor override attribution
            _activeScanResult.value = StudentScanResult.Success(
                student = student,
                card = scannedCard,
                isApproved = true,
                reason = overrideReason,
                isOfflineData = true,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            com.example.util.FeedbackHelper.playFeedback(context, true)
            _userFeedbackMessage.value = "Emergency gate access authorized by supervisor $authenticatedName."
            onResult(true, "Emergency override authorized.")
        }
    }

    fun issueExeatPass(pass: ExeatPass) {
        if (!requirePermission(StaffPermission.MANAGE_STUDENTS, "Issuing exeat passes")) return
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
        if (!requirePermission(StaffPermission.VERIFY_GATE_ACCESS, "Processing exeat gate exit")) return
        viewModelScope.launch {
            val result = repository.markExeatPassUsed(passId)
            if (result.isSuccess) {
                _userFeedbackMessage.value = "Exeat Pass marked as USED. Gate exit recorded."
            }
        }
    }

    fun sendCustomGuardianAlert(studentName: String, guardianPhone: String, message: String): Boolean {
        if (!requirePermission(StaffPermission.MANAGE_STUDENTS, "Preparing guardian WhatsApp messages")) return false
        // This only authorizes the UI to open WhatsApp with a prefilled message.
        // It does not send the message or prove delivery.
        _userFeedbackMessage.value =
            "WhatsApp message prepared for $guardianPhone. Review it in WhatsApp and tap Send; LTC cannot confirm delivery."
        return true
    }

    fun exportGateLogsCsv(context: Context) {
        if (!requirePermission(StaffPermission.EXPORT_DATA, "Exporting gate logs")) return
        val logs = scanLogs.value
        if (logs.isEmpty()) {
            _userFeedbackMessage.value = "No gate scan records to export."
            return
        }
        val csv = ExportUtils.generateGateLogsCsv(logs)
        ExportUtils.shareData(
            context = context,
            content = csv,
            subject = "LTC Gate Verification Logs (CSV)",
            isCsv = true
        )
        _userFeedbackMessage.value = "Exported ${logs.size} log records to CSV share sheet."
    }

    fun exportAttendanceSummaryReport(context: Context) {
        if (!requirePermission(StaffPermission.EXPORT_DATA, "Exporting attendance summary")) return
        val report = ExportUtils.generateAttendanceSummaryReport(
            allStudents = allStudents.value,
            scanLogs = scanLogs.value
        )
        ExportUtils.shareData(
            context = context,
            content = report,
            subject = "LTC Gate Attendance Summary Report",
            isCsv = false
        )
        _userFeedbackMessage.value = "Gate Attendance Summary Report opened in share sheet."
    }

    // ==========================================
    // MEALS MASTER ACTIONS
    // ==========================================
    fun selectMealType(mealType: MealType) {
        if (!mealType.isEnabledForServing) {
            _userFeedbackMessage.value = "${mealType.label} is disabled. Only lunch and supper are available."
            return
        }
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

    /**
     * Manual meal lookup is identity assistance only until the school approves
     * and configures meal eligibility rules. It must not record a serving.
     */
    fun verifyAndServeMealManually(studentNumber: String, reason: String, context: Context? = null) {
        val user = _currentUser.value
        if (user == null || !user.hasPermission(StaffPermission.SERVE_MEALS)) {
            _userFeedbackMessage.value = "Access Denied: Meal staff authorization is required."
            return
        }
        if (reason.isBlank()) {
            _userFeedbackMessage.value = "A reason is required for manual meal lookup."
            return
        }

        viewModelScope.launch {
            val student = repository.getStudentByStudentNumber(studentNumber.trim())
            if (student == null) {
                _mealScanOutcome.value = MealVerificationResult.StudentNotFound(studentNumber.trim())
                FeedbackHelper.playDeniedSoundAndHaptic(context)
                return@launch
            }

            _mealScanOutcome.value = MealVerificationResult.BlockedNoClearance(
                student,
                "Meal policy is not approved/configured. No meal was recorded. Follow the school's approved manual procedure. Lookup reason: ${reason.trim()}"
            )
            _userFeedbackMessage.value = "Student identity found. Meal eligibility remains pending school policy; no serving was recorded."
            FeedbackHelper.playWarningSoundAndHaptic(context)
        }
    }

    fun verifyAndServeMeal(rawCode: String, context: Context? = null) {
        val selectedMeal = _activeMealType.value
        if (!selectedMeal.isEnabledForServing) {
            _userFeedbackMessage.value = "${selectedMeal.label} is disabled. Only lunch and supper can be served."
            FeedbackHelper.playDeniedSoundAndHaptic(context)
            return
        }
        val user = _currentUser.value
        if (user == null || !user.hasPermission(StaffPermission.SERVE_MEALS)) {
            _userFeedbackMessage.value = "Access Denied: Unauthenticated session. Meal Staff authorization is required."
            _mealScanOutcome.value = MealVerificationResult.StudentNotFound(rawCode)
            FeedbackHelper.playDeniedSoundAndHaptic(context)
            return
        }

        // Production path uses the shared verifier, which validates the exact signed card,
        // checks its lifecycle status, enforces meal permissions, and persists the event to Room.
        if (repository.database != null && repository.syncManagerInstance != null) {
            _selectedDepartment.value = ScanDepartment.MEALS
            scanUnifiedQr(rawCode, context)
            return
        }

        // Test/demo repositories have no Room database. Do not bypass the
        // unconfigured meal policy in this fallback path either.
        viewModelScope.launch {
            when (val verification = repository.verifyStudentByQr(rawCode)) {
                is StudentScanResult.Success -> {
                    _mealScanOutcome.value = MealVerificationResult.BlockedNoClearance(
                        verification.student,
                        "Meal policy is not approved/configured. No meal was recorded. Follow the school's approved manual procedure."
                    )
                    FeedbackHelper.playWarningSoundAndHaptic(context)
                }
                else -> {
                    _mealScanOutcome.value = MealVerificationResult.StudentNotFound(rawCode)
                    FeedbackHelper.playDeniedSoundAndHaptic(context)
                }
            }
        }
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
        if (!requirePermission(StaffPermission.EXAM_CLEARANCE, "Scanning requirements QR")) return
        _isRequirementScannerOpen.value = false
        viewModelScope.launch {
            // Do not block the UI thread while checking the card, and never trust
            // a signature alone: the repository also checks dates and lifecycle state.
            val student = (repository.verifyStudentByQr(rawCode)
                as? StudentScanResult.Success)?.student
            if (student != null) {
                _scannedRequirementStudentId.value = student.id
                _requirementsSearchQuery.value = student.studentNumber
                _userFeedbackMessage.value = "Pulled up requirements record for ${student.fullName}."
            } else {
                _userFeedbackMessage.value = "Student not found for scanned QR: $rawCode"
            }
        }
    }

    fun toggleRequirementItem(studentId: String, itemKey: String) {
        if (!requirePermission(StaffPermission.EXAM_CLEARANCE, "Modifying requirement checklist")) return
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

    fun updateRequirementNotes(studentId: String, notes: String) {
        if (!requirePermission(StaffPermission.EXAM_CLEARANCE, "Updating requirement notes")) return
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
        if (!requirePermission(StaffPermission.EXPORT_DATA, "Exporting dataset ($datasetType)")) return
        when (datasetType.uppercase()) {
            "STUDENTS" -> {
                val list = allStudents.value
                val content = ExportManager.generateStudentsExport(list, format)
                ExportManager.downloadAndShare(
                    context = context,
                    content = content,
                    baseFileName = "LTC_Student_Registry",
                    format = format,
                    subject = "Lira Town College (LTC) - Student Registry Directory"
                )
                _userFeedbackMessage.value = "Exported ${list.size} student records as ${format.label}."
            }
            "GATE_LOGS" -> {
                val logs = scanLogs.value
                val content = ExportManager.generateGateLogsExport(logs, format)
                ExportManager.downloadAndShare(
                    context = context,
                    content = content,
                    baseFileName = "LTC_Gate_Verification_Logs",
                    format = format,
                    subject = "Lira Town College (LTC) - Gate Verification Logs"
                )
                _userFeedbackMessage.value = "Exported ${logs.size} gate logs as ${format.label}."
            }
            "MEALS" -> {
                val meals = mealRecords.value
                val content = ExportManager.generateMealsExport(meals, format)
                ExportManager.downloadAndShare(
                    context = context,
                    content = content,
                    baseFileName = "LTC_Dining_Hall_Logs",
                    format = format,
                    subject = "Lira Town College (LTC) - Dining Hall Access Log"
                )
                _userFeedbackMessage.value = "Exported ${meals.size} meal serving logs as ${format.label}."
            }
            "REQUIREMENTS" -> {
                val reqs = requirementsList.value
                val content = ExportManager.generateRequirementsExport(reqs, format)
                ExportManager.downloadAndShare(
                    context = context,
                    content = content,
                    baseFileName = "LTC_Requirements_Clearance",
                    format = format,
                    subject = "Lira Town College (LTC) - Requirements Compliance Report"
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
        if (!requirePermission(StaffPermission.SYSTEM_CONFIGURATION, "Clearing gate activity audit logs")) return
        viewModelScope.launch {
            repository.clearScanLogs()
            _userFeedbackMessage.value = "Gate activity audit logs cleared."
        }
    }

    fun resetDemoData() {
        if (!requirePermission(StaffPermission.SYSTEM_CONFIGURATION, "Resetting local records")) return
        viewModelScope.launch {
            repository.resetToSampleData()
            _userFeedbackMessage.value = "All local records cleared."
        }
    }

    // Cryptographic Issuer & Verifier Authority Management
    fun setupAuthoritativeIssuerKey(preferStrongBox: Boolean = true) {
        if (!requireAdminRole("Enrolling Issuer Signing Key")) return
        try {
            if (!KeystoreIssuerManager.hasIssuerKey()) {
                KeystoreIssuerManager.generateIssuerKey(preferStrongBox = preferStrongBox)
            }
            val signer = KeystoreIssuerManager.getSigner()
                ?: throw IllegalStateException("The enrolled Android Keystore signing key could not be loaded.")
            val entry = TrustedIssuerRegistry.registerTrustedKey(
                signer.publicKey,
                "LTC Authoritative Issuer"
            )
            if (entry.isRevoked) {
                CardCryptoManager.clearIssuerPrivateKey()
                throw IllegalStateException(
                    "This issuer key is revoked. Do not issue cards with it; complete an explicit key-rotation procedure first."
                )
            }
            CardCryptoManager.setActiveSigner(signer)
            _userFeedbackMessage.value = "Issuer key created locally. Publishing its public verification key to the LTC cloud…"
            val context = appContext
                ?: throw IllegalStateException("App context unavailable; public-key distribution cannot be started.")
            viewModelScope.launch {
                try {
                    SupabaseClientFactory.getInstance(context.applicationContext)
                        .from("trusted_issuers")
                        .upsert(
                            TrustedIssuerCloudRow(
                                schoolId = "ltc_school_01",
                                keyId = entry.kid,
                                publicKeyBase64 = entry.publicKeyBase64,
                                label = entry.label,
                                enrolledAtMs = entry.enrolledAt,
                                isRevoked = entry.isRevoked
                            )
                        ) { onConflict = "school_id,key_id" }
                    _userFeedbackMessage.value = "Issuer key published. Other LTC staff devices will trust it after their next successful sync."
                } catch (e: Exception) {
                    // Keep the private key in Android Keystore for retry, but disable
                    // signing until its public key is confirmed in the cloud registry.
                    CardCryptoManager.clearIssuerPrivateKey()
                    _userFeedbackMessage.value = "Issuer key exists on this device, but cloud publication failed: ${e.localizedMessage ?: "unknown error"}. Signing has been disabled; retry publication before issuing cards."
                }
            }
        } catch (e: Exception) {
            CardCryptoManager.clearIssuerPrivateKey()
            _userFeedbackMessage.value = "Failed to enroll issuer key: ${e.message}"
        }
    }

    private fun refreshTrustedIssuerKeys() {
        val context = appContext ?: return
        viewModelScope.launch {
            try {
                val rows = SupabaseClientFactory.getInstance(context.applicationContext)
                    .from("trusted_issuers")
                    .select {
                        filter { eq("school_id", "ltc_school_01") }
                    }
                    .decodeList<TrustedIssuerCloudRow>()
                rows.forEach { row ->
                    if (row.isRevoked) {
                        TrustedIssuerRegistry.revokeIssuer(row.keyId)
                        if (KeystoreIssuerManager.getSigner()?.keyId?.equals(row.keyId, ignoreCase = true) == true) {
                            CardCryptoManager.clearIssuerPrivateKey()
                        }
                    } else {
                        TrustedIssuerRegistry.registerTrustedKeyBase64(
                            row.publicKeyBase64,
                            row.label,
                            row.enrolledAtMs
                        )
                    }
                }
            } catch (e: Exception) {
                _userFeedbackMessage.value = "Issuer trust list could not be refreshed: ${e.localizedMessage ?: "check connection"}"
            }
        }
    }

    fun enrollTrustedIssuerPublicKey(base64: String, label: String = "Remote Issuer") {
        if (!requireAdminRole("Enrolling trusted public key")) return
        try {
            val entry = TrustedIssuerRegistry.registerTrustedKeyBase64(base64, label)
            _userFeedbackMessage.value = "Trusted Issuer enrolled! kid: ${entry.kid} (${entry.label})"
        } catch (e: Exception) {
            _userFeedbackMessage.value = "Failed to enroll public key: ${e.message}"
        }
    }

    fun revokeTrustedIssuer(kid: String) {
        if (!requireAdminRole("Revoking trusted issuer key")) return
        val existing = TrustedIssuerRegistry.getIssuerEntry(kid)
        val success = TrustedIssuerRegistry.revokeIssuer(kid)
        if (success && KeystoreIssuerManager.getSigner()?.keyId?.equals(kid, ignoreCase = true) == true) {
            CardCryptoManager.clearIssuerPrivateKey()
        }
        if (success && existing != null) {
            val context = appContext
            if (context != null) {
                viewModelScope.launch {
                    try {
                        SupabaseClientFactory.getInstance(context.applicationContext)
                            .from("trusted_issuers")
                            .upsert(
                                TrustedIssuerCloudRow(
                                    schoolId = "ltc_school_01",
                                    keyId = existing.kid,
                                    publicKeyBase64 = existing.publicKeyBase64,
                                    label = existing.label,
                                    enrolledAtMs = existing.enrolledAt,
                                    isRevoked = true
                                )
                            ) { onConflict = "school_id,key_id" }
                        _userFeedbackMessage.value = "Issuer key revoked in the LTC cloud. Devices will reject its cards after their next successful sync."
                    } catch (e: Exception) {
                        _userFeedbackMessage.value = "Local key revoked, but cloud revocation failed: ${e.localizedMessage ?: "unknown error"}"
                    }
                }
            } else {
                _userFeedbackMessage.value = "Issuer key revoked on this device."
            }
        } else {
            _userFeedbackMessage.value = "Key '$kid' not found in trusted registry."
        }
    }


    fun selectDepartment(dept: ScanDepartment) {
        _selectedDepartment.value = dept
    }

    fun setGateDirection(direction: GateDirection) {
        _gateDirection.value = direction
    }

    fun setAttendanceSubject(subject: String) {
        _selectedAttendanceSubject.value = subject
    }

    fun setAttendanceSession(session: String) {
        _selectedAttendanceSession.value = session
    }

    fun dismissDepartmentScanResult() {
        _departmentScanResult.value = null
    }

    fun scanUnifiedQr(rawCode: String, context: Context? = null) {
        val user = _currentUser.value ?: run {
            _userFeedbackMessage.value = "Access Denied: Unauthenticated staff session."
            return
        }

        if (!departmentScanThrottle.tryBegin(rawCode)) return
        val scanJob = viewModelScope.launch {
            _isScannerOpen.value = false
            _isMealScannerOpen.value = false
            _isRequirementScannerOpen.value = false

            val db = repository.database
            val syncMgr = repository.syncManagerInstance

            if (db != null && syncMgr != null) {
                val result = QrUnifiedVerifier.verifyAndProcessScan(
                    rawQrCode = rawCode,
                    department = _selectedDepartment.value,
                    currentUser = user,
                    database = db,
                    syncManager = syncMgr,
                    gateDirection = _gateDirection.value,
                    mealType = _activeMealType.value,
                    examCode = "UNEB-UCE-2026",
                    attendanceSubject = _selectedAttendanceSubject.value,
                    attendanceSessionName = _selectedAttendanceSession.value
                )
                _departmentScanResult.value = result

                // Also update legacy state flows for backward compatibility with existing screens
                when (result) {
                    is DepartmentVerificationResult.Success -> {
                        _currentScannedStudent.value = result.student
                        _currentScannedCard.value = result.card
                        _scanError.value = null
                        if (result.department == ScanDepartment.MEALS) {
                            if (result.isPolicyPending || result.mealStatus != MealServingStatus.SERVED) {
                                // Never translate an unresolved or blocked policy result into a
                                // successful meal-serving outcome for legacy screens.
                                _mealScanOutcome.value = MealVerificationResult.BlockedNoClearance(
                                    student = result.student,
                                    reason = result.statusDetail
                                )
                            } else {
                                val newMeal = MealRecord(
                                    studentId = result.student.id,
                                    studentNumber = result.student.studentNumber,
                                    studentName = result.student.fullName,
                                    gradeClass = result.student.gradeClass,
                                    mealType = result.mealType ?: MealType.LUNCH,
                                    timestamp = System.currentTimeMillis(),
                                    serverName = user.name,
                                    status = MealServingStatus.SERVED
                                )
                                _mealScanOutcome.value = MealVerificationResult.Success(newMeal)
                            }
                        } else if (result.department == ScanDepartment.ATTENDANCE) {
                            val newAtt = AttendanceRecord(
                                studentId = result.student.id,
                                studentNumber = result.student.studentNumber,
                                studentName = result.student.fullName,
                                gradeClass = result.student.gradeClass,
                                subject = result.attendanceSubject ?: "Mathematics",
                                sessionName = result.attendanceSession ?: "Session",
                                lessonDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                                teacherName = user.name
                            )
                        }
                        context?.let {
                            if (result.isPolicyPending) FeedbackHelper.playWarningSoundAndHaptic(it)
                            else FeedbackHelper.playFeedback(it, result.isAllowed)
                        }
                    }
                    is DepartmentVerificationResult.DuplicateAction -> {
                        _scanError.value = result.message
                        context?.let { FeedbackHelper.playWarningSoundAndHaptic(it) }
                    }
                    is DepartmentVerificationResult.PermissionDenied -> {
                        _scanError.value = result.message
                        context?.let { FeedbackHelper.playDeniedSoundAndHaptic(it) }
                    }
                    is DepartmentVerificationResult.CardInactive -> {
                        _scanError.value = result.reason
                        context?.let { FeedbackHelper.playFeedback(it, false) }
                    }
                    is DepartmentVerificationResult.InvalidCredential -> {
                        _scanError.value = result.errorReason
                        context?.let { FeedbackHelper.playFeedback(it, false) }
                    }
                    is DepartmentVerificationResult.StudentNotFound -> {
                        _scanError.value = result.reason
                        context?.let { FeedbackHelper.playFeedback(it, false) }
                    }
                }
            } else {
                // Fallback for mock repository
                handleBarcodeScan(rawCode, context)
            }
        }
        scanJob.invokeOnCompletion { departmentScanThrottle.finish() }
    }

    fun recommendExamException(studentId: String, studentNumber: String, studentName: String, reason: String = "Financial clearance pending bursar review") {
        if (!requirePermission(StaffPermission.MANAGE_FEES, "Recommending exam exception")) return
        viewModelScope.launch {
            val db = repository.database
            if (db != null) {
                val ex = ExamException(
                    studentId = studentId,
                    studentNumber = studentNumber,
                    studentName = studentName,
                    examCode = "UNEB-UCE-2026",
                    requestedByBursar = _currentUser.value?.name ?: "Bursar",
                    recommendationReason = reason,
                    isApproved = false
                )
                db.examExceptionDao().insertOrUpdate(com.example.data.local.ExamExceptionEntity.fromDomain(ex, isSynced = false))
                repository.syncManagerInstance?.notifyLocalLogAdded()
                _examExceptions.value = listOf(ex) + _examExceptions.value
                _userFeedbackMessage.value = "Bursar exception recommended for $studentName. Awaiting Head Teacher approval."
            }
        }
    }

    fun approveExamException(studentId: String, examCode: String = "UNEB-UCE-2026") {
        if (!requirePermission(StaffPermission.EMERGENCY_OVERRIDE, "Approving exam exception")) {
            _userFeedbackMessage.value = "Access Denied: Only Head Teacher / Executive Management can approve examination exceptions."
            return
        }
        viewModelScope.launch {
            val db = repository.database
            if (db != null) {
                val existing = db.examExceptionDao().findException(studentId, examCode)
                if (existing != null) {
                    val approved = existing.copy(
                        isApproved = true,
                        approvedByHeadTeacher = _currentUser.value?.name ?: "Head Teacher",
                        approvedAt = System.currentTimeMillis()
                    )
                    db.examExceptionDao().insertOrUpdate(approved)
                    repository.syncManagerInstance?.notifyLocalLogAdded()
                    _userFeedbackMessage.value = "Exam exception APPROVED by Head Teacher for ${existing.studentName}!"
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            context: Context
        ): androidx.lifecycle.ViewModelProvider.Factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = com.example.data.RoomStudentRepository.getInstance(context.applicationContext)
                return MainViewModel(repo, context.applicationContext) as T
            }
        }
    }
}




@Serializable
private data class TrustedIssuerCloudRow(
    @SerialName("school_id") val schoolId: String,
    @SerialName("key_id") val keyId: String,
    @SerialName("public_key_base64") val publicKeyBase64: String,
    val label: String,
    @SerialName("enrolled_at_ms") val enrolledAtMs: Long,
    @SerialName("is_revoked") val isRevoked: Boolean
)
