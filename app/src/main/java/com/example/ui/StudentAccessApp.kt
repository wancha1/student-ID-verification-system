package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.FeeStatus
import com.example.model.StaffPermission
import com.example.model.UserRole
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.BatchPrintIdCardsDialog
import com.example.ui.admin.HeadTeacherDashboardScreen
import com.example.ui.admin.StudentDetailScreen
import com.example.ui.auth.SupabaseStaffLoginScreen
import com.example.ui.attendance.AttendanceMasterScreen
import com.example.ui.exeat.ExeatPassScreen
import com.example.ui.notifications.GuardianAlertsScreen
import com.example.ui.requirements.RequirementsMasterScreen
import com.example.ui.components.MultiFormatExportDialog
import com.example.ui.guard.GuardDashboardScreen
import com.example.ui.guard.GuardScannerScreen
import com.example.ui.logs.GateAccessLogsScreen
import com.example.ui.meals.MealsMasterScreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalRoleTheme
import com.example.ui.theme.Navy900
import com.example.ui.theme.RoleThemeWrapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAccessApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.provideFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allStudents by viewModel.allStudents.collectAsStateWithLifecycle()
    val allCards by viewModel.allCards.collectAsStateWithLifecycle()
    val filteredStudents by viewModel.filteredStudents.collectAsStateWithLifecycle()
    val scanLogs by viewModel.scanLogs.collectAsStateWithLifecycle()
    val syncInfo by viewModel.syncInfo.collectAsStateWithLifecycle()
    val guardianNotifications by viewModel.guardianNotifications.collectAsStateWithLifecycle()
    val exeatPasses by viewModel.exeatPasses.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val selectedAttendanceSubject by viewModel.selectedAttendanceSubject.collectAsStateWithLifecycle()
    val selectedAttendanceSession by viewModel.selectedAttendanceSession.collectAsStateWithLifecycle()
    val requirementsList by viewModel.requirementsList.collectAsStateWithLifecycle()
    val requirementsSearchQuery by viewModel.requirementsSearchQuery.collectAsStateWithLifecycle()

    val activeScanResult by viewModel.activeScanResult.collectAsStateWithLifecycle()
    val activeScannedStudent by viewModel.currentScannedStudent.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val isScannerOpen by viewModel.isScannerOpen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val feeFilter by viewModel.feeFilter.collectAsStateWithLifecycle()
    val selectedStudentDetail by viewModel.selectedStudentDetail.collectAsStateWithLifecycle()
    val selectedStudentCards by viewModel.selectedStudentCards.collectAsStateWithLifecycle()
    val userFeedbackMessage by viewModel.userFeedbackMessage.collectAsStateWithLifecycle()

    val activeMealType by viewModel.activeMealType.collectAsStateWithLifecycle()
    val mealRecords by viewModel.mealRecords.collectAsStateWithLifecycle()
    val mealScanOutcome by viewModel.mealScanOutcome.collectAsStateWithLifecycle()
    val isMealScannerOpen by viewModel.isMealScannerOpen.collectAsStateWithLifecycle()

    var showLogsScreen by remember { mutableStateOf(false) }
    var showGuardianAlertsScreen by remember { mutableStateOf(false) }
    var showExeatScreen by remember { mutableStateOf(false) }
    var showAttendanceScreen by remember { mutableStateOf(false) }
    var showRequirementsScreen by remember { mutableStateOf(false) }
    var showMealsScreen by remember { mutableStateOf(false) }
    var showHeadStudentsScreen by remember { mutableStateOf(false) }
    var mealsTab by remember { mutableStateOf("SERVE") }
    var showBatchPrintDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showAllExportsDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var exportDatasetTarget by remember { mutableStateOf("STUDENTS") }

    val isAnyScannerOpen = isScannerOpen || isMealScannerOpen
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.restoreSupabaseSession(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.restoreSupabaseSession(context)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(userFeedbackMessage) {
        userFeedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearFeedbackMessage()
        }
    }

    RoleThemeWrapper(role = currentUser?.role) {
        val user = currentUser
        if (user == null) {
            SupabaseStaffLoginScreen(
                onAuthenticated = { role, name, hasFinance ->
                    showLogsScreen = false
                    showMealsScreen = false
                    showHeadStudentsScreen = false
                    mealsTab = "SERVE"
                    viewModel.loginAs(role, name, hasFinance)
                    viewModel.syncInBackground()
                },
                modifier = modifier
            )
        } else {
            val roleTheme = LocalRoleTheme.current
            val roleColor = roleTheme.primaryColor
            val isSubScreenOpen = isAnyScannerOpen || showLogsScreen || showGuardianAlertsScreen ||
                showExeatScreen || showAttendanceScreen || showRequirementsScreen ||
                showMealsScreen || (selectedStudentDetail != null)

            BackHandler(enabled = showSignOutConfirm) { showSignOutConfirm = false }
            BackHandler(enabled = showBatchPrintDialog) { showBatchPrintDialog = false }
            BackHandler(enabled = showAllExportsDialog) { showAllExportsDialog = false }
            BackHandler(enabled = showLogsScreen) { showLogsScreen = false }
            BackHandler(enabled = showGuardianAlertsScreen) { showGuardianAlertsScreen = false }
            BackHandler(enabled = showExeatScreen) { showExeatScreen = false }
            BackHandler(enabled = showAttendanceScreen) { showAttendanceScreen = false }
            BackHandler(enabled = showRequirementsScreen) { showRequirementsScreen = false }
            BackHandler(enabled = showMealsScreen) { showMealsScreen = false }
            BackHandler(enabled = showHeadStudentsScreen && selectedStudentDetail == null) { showHeadStudentsScreen = false }
            BackHandler(enabled = selectedStudentDetail != null) { viewModel.selectStudentForDetail(null) }
            BackHandler(enabled = isScannerOpen) { viewModel.closeScanner() }
            BackHandler(enabled = isMealScannerOpen) { viewModel.closeMealScanner() }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (!isSubScreenOpen) {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.1f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = roleTheme.icon,
                                                contentDescription = user.role.title,
                                                tint = GoldAccent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = "Lira Town College",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${user.role.title} • ${user.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.78f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { viewModel.lockSession() },
                                    modifier = Modifier.testTag("button_lock_session")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Lock Terminal",
                                        tint = Color.White.copy(alpha = 0.9f)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showOptionsMenu = true },
                                        modifier = Modifier.testTag("button_top_menu")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More options",
                                            tint = Color.White
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showOptionsMenu,
                                        onDismissRequest = { showOptionsMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Sync with Server") },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                            onClick = {
                                                viewModel.triggerCloudSync()
                                                showOptionsMenu = false
                                            },
                                            modifier = Modifier.testTag("menu_trigger_sync")
                                        )

                                        if (user.hasPermission(StaffPermission.EXPORT_DATA)) {
                                            DropdownMenuItem(
                                                text = { Text("Export Records") },
                                                leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                                onClick = {
                                                    exportDatasetTarget = when {
                                                        showLogsScreen -> "GATE_LOGS"
                                                        showMealsScreen -> "MEALS"
                                                        else -> "STUDENTS"
                                                    }
                                                    showAllExportsDialog = true
                                                    showOptionsMenu = false
                                                },
                                                modifier = Modifier.testTag("menu_multi_export")
                                            )
                                        }

                                        if (user.role == UserRole.ADMINISTRATOR || user.role == UserRole.HEAD_TEACHER_MANAGEMENT) {
                                            DropdownMenuItem(
                                                text = { Text("Guardian WhatsApp Alerts") },
                                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                                onClick = {
                                                    showGuardianAlertsScreen = true
                                                    showExeatScreen = false
                                                    showAttendanceScreen = false
                                                    showRequirementsScreen = false
                                                    showLogsScreen = false
                                                    showMealsScreen = false
                                                    showOptionsMenu = false
                                                },
                                                modifier = Modifier.testTag("menu_view_guardian_alerts")
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Exeat & Leave Passes") },
                                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                                onClick = {
                                                    showExeatScreen = true
                                                    showGuardianAlertsScreen = false
                                                    showAttendanceScreen = false
                                                    showRequirementsScreen = false
                                                    showLogsScreen = false
                                                    showMealsScreen = false
                                                    showOptionsMenu = false
                                                },
                                                modifier = Modifier.testTag("menu_view_exeat_passes")
                                            )
                                        }
                                        if (user.role == UserRole.GATE_STAFF) {
                                            DropdownMenuItem(
                                                text = { Text("Exeat Gate") },
                                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                                onClick = {
                                                    showExeatScreen = true
                                                    showGuardianAlertsScreen = false
                                                    showAttendanceScreen = false
                                                    showRequirementsScreen = false
                                                    showLogsScreen = false
                                                    showMealsScreen = false
                                                    showOptionsMenu = false
                                                },
                                                modifier = Modifier.testTag("menu_gate_exeat")
                                            )
                                        }
                                        if (user.role == UserRole.HEAD_TEACHER_MANAGEMENT) {
                                            DropdownMenuItem(
                                                text = { Text("Attendance Review") },
                                                leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                                                onClick = {
                                                    showAttendanceScreen = true
                                                    showRequirementsScreen = false
                                                    showGuardianAlertsScreen = false
                                                    showExeatScreen = false
                                                    showLogsScreen = false
                                                    showMealsScreen = false
                                                    showOptionsMenu = false
                                                },
                                                modifier = Modifier.testTag("menu_view_attendance")
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Requirements Review") },
                                                leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                                onClick = {
                                                    showRequirementsScreen = true
                                                    showAttendanceScreen = false
                                                    showGuardianAlertsScreen = false
                                                    showExeatScreen = false
                                                    showLogsScreen = false
                                                    showMealsScreen = false
                                                    showOptionsMenu = false
                                                },
                                                modifier = Modifier.testTag("menu_view_requirements")
                                            )
                                        }

                                        if (user.role == UserRole.ADMINISTRATOR) {
                                            DropdownMenuItem(
                                                text = { Text("Batch ID Badges") },
                                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                                onClick = {
                                                    showBatchPrintDialog = true
                                                    showOptionsMenu = false
                                                },
                                                modifier = Modifier.testTag("menu_batch_print")
                                            )
                                        }

                                        HorizontalDivider()

                                        DropdownMenuItem(
                                            text = { Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ExitToApp,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                showOptionsMenu = false
                                                showSignOutConfirm = true
                                            },
                                            modifier = Modifier.testTag("menu_logout")
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Navy900,
                                titleContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                    }
                },
                bottomBar = {
                    if (!isAnyScannerOpen && selectedStudentDetail == null &&
                        !showGuardianAlertsScreen && !showExeatScreen &&
                        !showAttendanceScreen && !showRequirementsScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.testTag("main_navigation_bar")
                        ) {
                            val navColors = NavigationBarItemDefaults.colors(
                                indicatorColor = roleColor.copy(alpha = 0.14f),
                                selectedIconColor = roleColor,
                                selectedTextColor = roleColor
                            )

                            when (user.role) {
                                UserRole.ADMINISTRATOR -> {
                                    val isDashboard = !showLogsScreen
                                    NavigationBarItem(
                                        selected = isDashboard,
                                        onClick = { showLogsScreen = false },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.School,
                                                contentDescription = "Students"
                                            )
                                        },
                                        label = { Text("Students") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_students")
                                    )
                                    NavigationBarItem(
                                        selected = showLogsScreen,
                                        onClick = { showLogsScreen = true },
                                        icon = { Icon(Icons.Default.History, contentDescription = "Gate Logs") },
                                        label = { Text("Gate Logs") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_logs")
                                    )
                                    NavigationBarItem(
                                        selected = showBatchPrintDialog,
                                        onClick = { showBatchPrintDialog = true },
                                        icon = { Icon(Icons.Default.Badge, contentDescription = "ID Badges") },
                                        label = { Text("ID Badges") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_badges")
                                    )
                                }

                                UserRole.HEAD_TEACHER_MANAGEMENT -> {
                                    NavigationBarItem(
                                        selected = !showLogsScreen && !showMealsScreen && !showHeadStudentsScreen,
                                        onClick = {
                                            showLogsScreen = false
                                            showMealsScreen = false
                                            showHeadStudentsScreen = false
                                        },
                                        icon = { Icon(Icons.Default.SupervisorAccount, contentDescription = "Overview") },
                                        label = { Text("Overview") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_leadership")
                                    )
                                    NavigationBarItem(
                                        selected = showHeadStudentsScreen && !showLogsScreen && !showMealsScreen,
                                        onClick = {
                                            showHeadStudentsScreen = true
                                            showLogsScreen = false
                                            showMealsScreen = false
                                        },
                                        icon = { Icon(Icons.Default.School, contentDescription = "Students") },
                                        label = { Text("Students") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_head_students")
                                    )
                                    NavigationBarItem(
                                        selected = showLogsScreen,
                                        onClick = {
                                            showLogsScreen = true
                                            showMealsScreen = false
                                            showHeadStudentsScreen = false
                                        },
                                        icon = { Icon(Icons.Default.History, contentDescription = "Gate activity") },
                                        label = { Text("Gate") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_head_gate")
                                    )
                                    NavigationBarItem(
                                        selected = showMealsScreen,
                                        onClick = {
                                            showMealsScreen = true
                                            showLogsScreen = false
                                            showHeadStudentsScreen = false
                                        },
                                        icon = { Icon(Icons.Default.Restaurant, contentDescription = "Meal reports") },
                                        label = { Text("Meals") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_head_meals")
                                    )
                                }

                                UserRole.GATE_STAFF -> {
                                    NavigationBarItem(
                                        selected = !showLogsScreen,
                                        onClick = { showLogsScreen = false },
                                        icon = { Icon(Icons.Default.Security, contentDescription = "Turnstile") },
                                        label = { Text("Turnstile") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_turnstile")
                                    )
                                    NavigationBarItem(
                                        selected = showLogsScreen,
                                        onClick = { showLogsScreen = true },
                                        icon = { Icon(Icons.Default.History, contentDescription = "Gate Logs") },
                                        label = { Text("Gate Logs") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_logs")
                                    )
                                }

                                UserRole.MEAL_SERVING_STAFF -> {
                                    NavigationBarItem(
                                        selected = mealsTab == "SERVE",
                                        onClick = { mealsTab = "SERVE" },
                                        icon = { Icon(Icons.Default.Restaurant, contentDescription = "Serve Meals") },
                                        label = { Text("Serve") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_dining")
                                    )
                                    NavigationBarItem(
                                        selected = mealsTab == "HISTORY",
                                        onClick = { mealsTab = "HISTORY" },
                                        icon = { Icon(Icons.Default.History, contentDescription = "Serving Log") },
                                        label = { Text("Serving Log") },
                                        colors = navColors,
                                        modifier = Modifier.testTag("nav_item_meal_history")
                                    )
                                }

                                else -> Unit
                            }
                        }
                    }
                },
                modifier = modifier
            ) { paddingValues ->
                val contentPadding = if (isAnyScannerOpen) PaddingValues(0.dp) else paddingValues
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .consumeWindowInsets(contentPadding)
                ) {
                    if (showLogsScreen) {
                        GateAccessLogsScreen(
                            scanLogs = scanLogs,
                            onBack = { showLogsScreen = false },
                            onClearLogs = null
                        )
                    } else if (showGuardianAlertsScreen) {
                        GuardianAlertsScreen(
                            notifications = guardianNotifications,
                            onBack = { showGuardianAlertsScreen = false },
                            onSendCustomAlert = { studentName, phone, message ->
                                viewModel.sendCustomGuardianAlert(studentName, phone, message)
                            },
                            readOnly = user.role == UserRole.HEAD_TEACHER_MANAGEMENT
                        )
                    } else if (showExeatScreen) {
                        // Issuance is an Admin action; gate departure is a Gate Staff action.
                        ExeatPassScreen(
                            exeatPasses = exeatPasses,
                            students = allStudents,
                            onBack = { showExeatScreen = false },
                            onIssueExeatPass = { pass -> viewModel.issueExeatPass(pass) },
                            onMarkExeatUsed = { passId -> viewModel.markExeatUsed(passId) },
                            readOnly = user.role == UserRole.HEAD_TEACHER_MANAGEMENT,
                            canIssue = user.role == UserRole.ADMINISTRATOR,
                            canMarkUsed = user.role == UserRole.GATE_STAFF
                        )
                    } else if (showAttendanceScreen && user.role == UserRole.HEAD_TEACHER_MANAGEMENT) {
                        AttendanceMasterScreen(
                            user = user,
                            attendanceRecords = attendanceRecords,
                            selectedSubject = selectedAttendanceSubject,
                            selectedSession = selectedAttendanceSession,
                            onSubjectChange = { viewModel.setAttendanceSubject(it) },
                            onSessionChange = { viewModel.setAttendanceSession(it) },
                            onOpenScanner = { },
                            onManualLookup = { },
                            onCorrectAttendance = { _, _ -> },
                            readOnly = true
                        )
                    } else if (showRequirementsScreen && user.role == UserRole.HEAD_TEACHER_MANAGEMENT) {
                        RequirementsMasterScreen(
                            user = user,
                            requirementsList = requirementsList,
                            allStudents = allStudents,
                            searchQuery = requirementsSearchQuery,
                            onSearchQueryChange = { viewModel.setRequirementsSearchQuery(it) },
                            onToggleRequirement = { _, _ -> },
                            onMarkAllCleared = { },
                            onUpdateNotes = { _, _ -> },
                            onOpenScanner = { },
                            onExport = { format -> viewModel.exportDataset("REQUIREMENTS", format, context) },
                            readOnly = true
                        )
                    } else if (showMealsScreen && user.role == UserRole.HEAD_TEACHER_MANAGEMENT) {
                        MealsMasterScreen(
                            user = user,
                            activeMealType = activeMealType,
                            mealRecords = mealRecords,
                            mealScanOutcome = null,
                            allStudents = allStudents,
                            onSelectMealType = { viewModel.setActiveMealType(it) },
                            onOpenScanner = { },
                            onDismissScanOutcome = { },
                            onExport = { format -> viewModel.exportDataset("MEALS", format, context) },
                            readOnly = true,
                            selectedMealsTab = "HISTORY"
                        )
                    } else {
                        when (user.role) {
                            UserRole.GATE_STAFF -> {
                                if (isScannerOpen) {
                                    GuardScannerScreen(
                                        onBarcodeDetected = { rawCode ->
                                            viewModel.handleBarcodeScan(rawCode, context)
                                        },
                                        onCloseScanner = { viewModel.closeScanner() }
                                    )
                                } else {
                                    GuardDashboardScreen(
                                        user = user,
                                        activeScanResult = activeScanResult,
                                        activeScannedStudent = activeScannedStudent,
                                        scanError = scanError,
                                        scanLogs = scanLogs,
                                        syncInfo = syncInfo,
                                        onOpenScanner = { viewModel.openScanner() },
                                        onDismissScanResult = { viewModel.dismissScanResult() },
                                        onTriggerSync = { viewModel.triggerCloudSync() },
                                        onToggleOnline = { viewModel.toggleNetworkOnline(it) },
                                        onViewAllLogs = { showLogsScreen = true }
                                    )
                                }
                            }

                            UserRole.HEAD_TEACHER_MANAGEMENT -> {
                                if (selectedStudentDetail != null) {
                                    StudentDetailScreen(
                                        student = selectedStudentDetail!!,
                                        cards = selectedStudentCards,
                                        onBack = { viewModel.selectStudentForDetail(null) },
                                        onUpdateFeeStatus = { _, _ -> },
                                        canManageStudentRecords = false,
                                        canManageFees = false,
                                        onUpdateStudentDetails = { },
                                        onDeleteStudent = { }
                                    )
                                } else if (showHeadStudentsScreen) {
                                    AdminDashboardScreen(
                                        user = user,
                                        allStudents = allStudents,
                                        filteredStudents = filteredStudents,
                                        scanLogs = scanLogs,
                                        syncInfo = syncInfo,
                                        onTriggerSync = { viewModel.triggerCloudSync() },
                                        searchQuery = searchQuery,
                                        feeFilter = feeFilter,
                                        onSearchChange = { viewModel.setSearchQuery(it) },
                                        onFilterChange = { viewModel.setFeeFilter(it) },
                                        onSelectStudent = { studentId ->
                                            viewModel.selectStudentForDetail(studentId)
                                        },
                                        onQuickToggleFeeStatus = { _, _ -> },
                                        onAddStudent = { }
                                    )
                                } else {
                                    HeadTeacherDashboardScreen(
                                        user = user,
                                        students = allStudents,
                                        mealRecords = mealRecords,
                                        gateLogs = scanLogs,
                                        onOpenStudents = { showHeadStudentsScreen = true },
                                        onOpenGateLogs = { showLogsScreen = true },
                                        onOpenMeals = { showMealsScreen = true }
                                    )
                                }
                            }

                            UserRole.ADMINISTRATOR -> {
                                if (selectedStudentDetail != null) {
                                    StudentDetailScreen(
                                        student = selectedStudentDetail!!,
                                        cards = selectedStudentCards,
                                        onBack = { viewModel.selectStudentForDetail(null) },
                                        onUpdateFeeStatus = { newStatus, amount ->
                                            viewModel.updateFeeStatus(selectedStudentDetail!!.id, newStatus, amount)
                                        },
                                        canManageStudentRecords = true,
                                        canManageFees = user.hasPermission(StaffPermission.MANAGE_FEES),
                                        onUpdateStudentDetails = { updated ->
                                            viewModel.updateStudentDetails(updated)
                                        },
                                        onDeleteStudent = { studentId ->
                                            viewModel.deleteStudentRecord(studentId)
                                            viewModel.selectStudentForDetail(null)
                                        },
                                        onReportCardLost = { studentId, cardId, reason ->
                                            viewModel.reportCardLost(studentId, cardId, reason)
                                        },
                                        onIssueReplacementCard = { studentId, oldCardId, reason ->
                                            viewModel.issueReplacementCard(studentId, oldCardId, reason)
                                        },
                                        onDeactivateCard = { studentId, cardId, reason ->
                                            viewModel.deactivateCard(studentId, cardId, reason)
                                        },
                                        onIssueNewCard = { studentId, reason ->
                                            viewModel.issueNewActiveCard(studentId, reason)
                                        }
                                    )
                                } else {
                                    AdminDashboardScreen(
                                        user = user,
                                        allStudents = allStudents,
                                        filteredStudents = filteredStudents,
                                        scanLogs = scanLogs,
                                        syncInfo = syncInfo,
                                        onTriggerSync = { viewModel.triggerCloudSync() },
                                        searchQuery = searchQuery,
                                        feeFilter = feeFilter,
                                        onSearchChange = { viewModel.setSearchQuery(it) },
                                        onFilterChange = { viewModel.setFeeFilter(it) },
                                        onSelectStudent = { studentId ->
                                            viewModel.selectStudentForDetail(studentId)
                                        },
                                        onQuickToggleFeeStatus = { studentId, currentStatus ->
                                            val nextStatus = if (currentStatus == FeeStatus.CLEARED) FeeStatus.OUTSTANDING else FeeStatus.CLEARED
                                            viewModel.updateFeeStatus(studentId, nextStatus)
                                        },
                                        onAddStudent = { newStudent ->
                                            viewModel.registerNewStudent(newStudent)
                                        }
                                    )
                                }
                            }

                            UserRole.MEAL_SERVING_STAFF -> {
                                if (isMealScannerOpen) {
                                    GuardScannerScreen(
                                        onBarcodeDetected = { rawCode ->
                                            viewModel.verifyAndServeMeal(rawCode, context)
                                        },
                                        onCloseScanner = { viewModel.closeMealScanner() },
                                        screenTitle = "MEAL QR VERIFICATION",
                                        cameraPermissionMessage = "Camera access is needed to scan student ID cards and record lunch or supper service."
                                    )
                                } else {
                                    MealsMasterScreen(
                                        user = user,
                                        activeMealType = activeMealType,
                                        mealRecords = mealRecords,
                                        mealScanOutcome = mealScanOutcome,
                                        allStudents = allStudents,
                                        onSelectMealType = { viewModel.setActiveMealType(it) },
                                        onOpenScanner = { viewModel.openMealScanner() },
                                        onDismissScanOutcome = { viewModel.dismissMealOutcome() },
                                        onExport = { format ->
                                            viewModel.exportDataset("MEALS", format, context)
                                        },
                                        selectedMealsTab = mealsTab
                                    )
                                }
                            }

                            else -> Unit
                        }
                    }

                    if (showBatchPrintDialog) {
                        BatchPrintIdCardsDialog(
                            students = allStudents,
                            cards = allCards,
                            onDismiss = { showBatchPrintDialog = false }
                        )
                    }

                    if (showAllExportsDialog) {
                        MultiFormatExportDialog(
                            datasetTitle = when (exportDatasetTarget) {
                                "GATE_LOGS" -> "Gate Access Verification Logs"
                                "MEALS" -> "Dining Hall Meal Serving Records"
                                else -> "Complete Student Registry & Fee Roster"
                            },
                            recordCount = when (exportDatasetTarget) {
                                "GATE_LOGS" -> scanLogs.size
                                "MEALS" -> mealRecords.size
                                else -> allStudents.size
                            },
                            onDismiss = { showAllExportsDialog = false },
                            onExport = { format ->
                                viewModel.exportDataset(exportDatasetTarget, format, context)
                                showAllExportsDialog = false
                            }
                        )
                    }

                    if (showSignOutConfirm) {
                        AlertDialog(
                            onDismissRequest = { showSignOutConfirm = false },
                            title = { Text("Sign Out?", fontWeight = FontWeight.Bold) },
                            text = {
                                Text("You will return to the staff sign-in screen and clear your active session on this device.")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showSignOutConfirm = false
                                        showLogsScreen = false
                                        showMealsScreen = false
                                        showHeadStudentsScreen = false
                                        mealsTab = "SERVE"
                                        viewModel.logout()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.testTag("button_confirm_sign_out")
                                ) {
                                    Text("Sign Out")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSignOutConfirm = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
