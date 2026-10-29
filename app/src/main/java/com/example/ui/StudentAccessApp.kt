package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.FeeStatus
import com.example.model.UserRole
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.AdminScanLogsScreen
import com.example.ui.admin.BatchPrintIdCardsDialog
import com.example.ui.admin.StudentDetailScreen
import com.example.ui.auth.FirebaseStaffLoginScreen
import com.example.ui.exeat.ExeatPassScreen
import com.example.ui.guard.GuardDashboardScreen
import com.example.ui.guard.GuardScannerScreen
import com.example.ui.logs.GateAccessLogsScreen
import com.example.ui.notifications.GuardianAlertsScreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LocalRoleTheme
import com.example.ui.theme.RoleThemeWrapper
import com.example.ui.theme.RoleThemes
import com.example.ui.theme.SchoolPrimary
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TableChart
import com.example.ui.components.MultiFormatExportDialog
import com.example.ui.attendance.AttendanceMasterScreen
import com.example.ui.meals.MealsMasterScreen
import com.example.ui.requirements.RequirementsMasterScreen
import com.example.util.ExportFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.util.SecurityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAccessApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.provideFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allStudents by viewModel.allStudents.collectAsStateWithLifecycle()
    val filteredStudents by viewModel.filteredStudents.collectAsStateWithLifecycle()
    val scanLogs by viewModel.scanLogs.collectAsStateWithLifecycle()
    val syncInfo by viewModel.syncInfo.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val selectedAttendanceSubject by viewModel.selectedAttendanceSubject.collectAsStateWithLifecycle()
    val selectedAttendanceSession by viewModel.selectedAttendanceSession.collectAsStateWithLifecycle()
    val departmentScanResult by viewModel.departmentScanResult.collectAsStateWithLifecycle()

    val activeScanResult by viewModel.activeScanResult.collectAsStateWithLifecycle()
    val activeScannedStudent by viewModel.currentScannedStudent.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val isScannerOpen by viewModel.isScannerOpen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val feeFilter by viewModel.feeFilter.collectAsStateWithLifecycle()
    val selectedStudentDetail by viewModel.selectedStudentDetail.collectAsStateWithLifecycle()
    val selectedStudentCards by viewModel.selectedStudentCards.collectAsStateWithLifecycle()
    val userFeedbackMessage by viewModel.userFeedbackMessage.collectAsStateWithLifecycle()
    val guardianNotifications by viewModel.guardianNotifications.collectAsStateWithLifecycle()
    val exeatPasses by viewModel.exeatPasses.collectAsStateWithLifecycle()

    val activeMealType by viewModel.activeMealType.collectAsStateWithLifecycle()
    val mealRecords by viewModel.mealRecords.collectAsStateWithLifecycle()
    val mealScanOutcome by viewModel.mealScanOutcome.collectAsStateWithLifecycle()
    val isMealScannerOpen by viewModel.isMealScannerOpen.collectAsStateWithLifecycle()
    val requirementsList by viewModel.requirementsList.collectAsStateWithLifecycle()
    val requirementsSearchQuery by viewModel.requirementsSearchQuery.collectAsStateWithLifecycle()
    val isRequirementScannerOpen by viewModel.isRequirementScannerOpen.collectAsStateWithLifecycle()

    var showLogsScreen by remember { mutableStateOf(false) }
    var showGuardianAlertsScreen by remember { mutableStateOf(false) }
    var showExeatScreen by remember { mutableStateOf(false) }
    var showBatchPrintDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showRoleSwitchMenu by remember { mutableStateOf(false) }
    var showAllExportsDialog by remember { mutableStateOf(false) }
    var exportDatasetTarget by remember { mutableStateOf("STUDENTS") }
    var showAuthDialogForRole by remember { mutableStateOf<UserRole?>(null) }
    var switchRolePin by remember { mutableStateOf("") }
    var switchRolePinError by remember { mutableStateOf<String?>(null) }
    var switchRoleRequestFinance by remember { mutableStateOf(false) }

    val isAnyScannerOpen = isScannerOpen || isMealScannerOpen || isRequirementScannerOpen

    val snackbarHostState = remember { SnackbarHostState() }

    // Firebase credentials persist per device. Revalidate staff membership on
    // app start/resume, and retry after a transient server/network validation failure.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.restoreFirebaseSession(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.restoreFirebaseSession(context)
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

    // Top Level Container with Role Dynamic Theme
    RoleThemeWrapper(role = currentUser?.role) {
        val user = currentUser
        if (user == null) {
            FirebaseStaffLoginScreen(
                onAuthenticated = { role, name, hasFinance -> viewModel.loginAs(role, name, hasFinance) },
                modifier = modifier
            )
        } else {
            val roleTheme = LocalRoleTheme.current
            val roleColor = roleTheme.primaryColor
            val roleIcon = roleTheme.icon
            val roleTitle = user.role.title
            val roleSubtitle = user.role.subtitle
            val isSubScreenOpen = isAnyScannerOpen || showLogsScreen || showGuardianAlertsScreen || showExeatScreen || (selectedStudentDetail != null)

            // Systematic Android Back Navigation Handling
            BackHandler(enabled = showAuthDialogForRole != null) {
                showAuthDialogForRole = null
                switchRolePin = ""
                switchRolePinError = null
                switchRoleRequestFinance = false
            }
            BackHandler(enabled = showBatchPrintDialog) {
                showBatchPrintDialog = false
            }
            BackHandler(enabled = showAllExportsDialog) {
                showAllExportsDialog = false
            }
            BackHandler(enabled = showLogsScreen) {
                showLogsScreen = false
            }
            BackHandler(enabled = showGuardianAlertsScreen) {
                showGuardianAlertsScreen = false
            }
            BackHandler(enabled = showExeatScreen) {
                showExeatScreen = false
            }
            BackHandler(enabled = selectedStudentDetail != null) {
                viewModel.selectStudentForDetail(null)
            }
            BackHandler(enabled = isScannerOpen) {
                viewModel.closeScanner()
            }
            BackHandler(enabled = isMealScannerOpen) {
                viewModel.closeMealScanner()
            }
            BackHandler(enabled = isRequirementScannerOpen) {
                viewModel.closeRequirementScanner()
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (!isSubScreenOpen) {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(42.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF15283D),
                                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.9f)),
                                        shadowElevation = 4.dp
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = roleIcon,
                                                contentDescription = roleTitle,
                                                tint = GoldAccent,
                                                modifier = Modifier.size(23.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "LIRA TOWN COLLEGE",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            letterSpacing = 1.1.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = roleTheme.containerColor
                                            ) {
                                                Text(
                                                    text = roleTheme.stationBadge,
                                                    color = roleTheme.badgeTextColor,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    letterSpacing = 0.4.sp,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$roleTitle • ${user.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.78f),
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            },
                            actions = {
                                // Quick Role Switcher Pill with Dropdown
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, roleTheme.accentGlowColor.copy(alpha = 0.65f)),
                                        modifier = Modifier
                                            .testTag("button_switch_role")
                                            .clickable { showRoleSwitchMenu = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SwapHoriz,
                                                contentDescription = "Change staff account",
                                                tint = GoldAccent,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }

                                DropdownMenu(
                                    expanded = showRoleSwitchMenu,
                                    onDismissRequest = { showRoleSwitchMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Gate Staff (Turnstile)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Security, contentDescription = null, tint = SchoolPrimary)
                                        },
                                        onClick = {
                                            showRoleSwitchMenu = false
                                            showAuthDialogForRole = UserRole.GATE_STAFF
                                            switchRolePin = ""
                                            switchRolePinError = null
                                            switchRoleRequestFinance = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Bursar / Finance (Fees)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFF0D9488))
                                        },
                                        onClick = {
                                            showRoleSwitchMenu = false
                                            showAuthDialogForRole = UserRole.BURSAR_FINANCE
                                            switchRolePin = ""
                                            switchRolePinError = null
                                            switchRoleRequestFinance = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Administrator (Registry)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = GoldAccent)
                                        },
                                        onClick = {
                                            showRoleSwitchMenu = false
                                            showAuthDialogForRole = UserRole.ADMINISTRATOR
                                            switchRolePin = ""
                                            switchRolePinError = null
                                            switchRoleRequestFinance = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Meal-Serving Staff (Dining Hall)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color(0xFFE11D48))
                                        },
                                        onClick = {
                                            showRoleSwitchMenu = false
                                            showAuthDialogForRole = UserRole.MEAL_SERVING_STAFF
                                            switchRolePin = ""
                                            switchRolePinError = null
                                            switchRoleRequestFinance = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Teachers (Classroom Roll Call)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF4F46E5))
                                        },
                                        onClick = {
                                            showRoleSwitchMenu = false
                                            showAuthDialogForRole = UserRole.TEACHERS
                                            switchRolePin = ""
                                            switchRolePinError = null
                                            switchRoleRequestFinance = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Examination Staff (Clearance)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color(0xFF0284C7))
                                        },
                                        onClick = {
                                            showRoleSwitchMenu = false
                                            showAuthDialogForRole = UserRole.EXAMINATION_STAFF
                                            switchRolePin = ""
                                            switchRolePinError = null
                                            switchRoleRequestFinance = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Head Teacher / Management") },
                                        leadingIcon = {
                                            Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = Color(0xFF7C3AED))
                                        },
                                        onClick = {
                                            showRoleSwitchMenu = false
                                            showAuthDialogForRole = UserRole.HEAD_TEACHER_MANAGEMENT
                                            switchRolePin = ""
                                            switchRolePinError = null
                                            switchRoleRequestFinance = false
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.lockSession() },
                                modifier = Modifier.testTag("button_lock_session")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Terminal",
                                    tint = GoldAccent
                                )
                            }

                            // Overflow Menu
                            Box {
                                IconButton(
                                    onClick = { showOptionsMenu = true },
                                    modifier = Modifier.testTag("button_top_menu")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options"
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Multi-Format Data Downloads (Excel, Word...)") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.TableChart,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            exportDatasetTarget = when (user.role) {
                                                UserRole.MEALS_MASTER -> "MEALS"
                                                UserRole.REQUIREMENTS_MASTER -> "REQUIREMENTS"
                                                UserRole.GATE_KEEPER, UserRole.SECURITY_GUARD -> "GATE_LOGS"
                                                else -> "STUDENTS"
                                            }
                                            showAllExportsDialog = true
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_multi_export")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Gate Access Logs") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showLogsScreen = true
                                            showGuardianAlertsScreen = false
                                            showExeatScreen = false
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_view_logs")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Guardian SMS & Alerts") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showGuardianAlertsScreen = true
                                            showLogsScreen = false
                                            showExeatScreen = false
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_view_alerts")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Exeat & Gate Leave Passes") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.AdminPanelSettings,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showExeatScreen = true
                                            showLogsScreen = false
                                            showGuardianAlertsScreen = false
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_view_exeats")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Batch ID Badges Studio") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Badge,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showBatchPrintDialog = true
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_batch_print")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export Gate Logs (CSV)") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.FileDownload,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.exportGateLogsCsv(context)
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_export_csv")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Attendance Summary") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Assessment,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.exportAttendanceSummaryReport(context)
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_export_summary")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sync with Central Server") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.triggerCloudSync()
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_trigger_sync")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear Gate Logs") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.clearLogs()
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_clear_logs")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Lock Terminal / Log Out") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.lockSession()
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_logout")
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF0B1726),
                            titleContentColor = Color.White,
                            actionIconContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                }
            },
            bottomBar = {
                if (!isAnyScannerOpen && selectedStudentDetail == null) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.testTag("main_navigation_bar")
                    ) {
                        when (user.role) {
                            UserRole.TEACHERS -> {
                                NavigationBarItem(
                                    selected = true,
                                    onClick = { showAllExportsDialog = false },
                                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Attendance") },
                                    label = { Text("Attendance", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_attendance")
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = {
                                        exportDatasetTarget = "ATTENDANCE"
                                        showAllExportsDialog = true
                                    },
                                    icon = { Icon(Icons.Default.TableChart, contentDescription = "Export") },
                                    label = { Text("Export Roll", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_export")
                                )
                            }
                        UserRole.ADMINISTRATOR, UserRole.BURSAR_FINANCE, UserRole.HEAD_TEACHER_MANAGEMENT -> {
                                val isDashboard = !showLogsScreen && !showGuardianAlertsScreen && !showExeatScreen
                                NavigationBarItem(
                                    selected = isDashboard,
                                    onClick = {
                                        showLogsScreen = false
                                        showGuardianAlertsScreen = false
                                        showExeatScreen = false
                                    },
                                    icon = { Icon(Icons.Default.School, contentDescription = "Students") },
                                    label = { Text("Students", fontSize = 11.sp, fontWeight = if (isDashboard) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_students")
                                )
                                NavigationBarItem(
                                    selected = showLogsScreen,
                                    onClick = {
                                        showLogsScreen = true
                                        showGuardianAlertsScreen = false
                                        showExeatScreen = false
                                    },
                                    icon = { Icon(Icons.Default.History, contentDescription = "Gate Logs") },
                                    label = { Text("Gate Logs", fontSize = 11.sp, fontWeight = if (showLogsScreen) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_logs")
                                )
                                NavigationBarItem(
                                    selected = showExeatScreen,
                                    onClick = {
                                        showExeatScreen = true
                                        showLogsScreen = false
                                        showGuardianAlertsScreen = false
                                    },
                                    icon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = "Exeat Passes") },
                                    label = { Text("Exeat Passes", fontSize = 11.sp, fontWeight = if (showExeatScreen) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_exeat")
                                )
                                NavigationBarItem(
                                    selected = showGuardianAlertsScreen,
                                    onClick = {
                                        showGuardianAlertsScreen = true
                                        showLogsScreen = false
                                        showExeatScreen = false
                                    },
                                    icon = { Icon(Icons.Default.Notifications, contentDescription = "SMS Alerts") },
                                    label = { Text("SMS Alerts", fontSize = 11.sp, fontWeight = if (showGuardianAlertsScreen) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_alerts")
                                )
                            }

                            UserRole.GATE_STAFF -> {
                                val isGateDashboard = !showLogsScreen && !showExeatScreen
                                NavigationBarItem(
                                    selected = isGateDashboard,
                                    onClick = {
                                        showLogsScreen = false
                                        showExeatScreen = false
                                    },
                                    icon = { Icon(Icons.Default.Security, contentDescription = "Turnstile") },
                                    label = { Text("Turnstile", fontSize = 11.sp, fontWeight = if (isGateDashboard) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_turnstile")
                                )
                                NavigationBarItem(
                                    selected = showLogsScreen,
                                    onClick = {
                                        showLogsScreen = true
                                        showExeatScreen = false
                                    },
                                    icon = { Icon(Icons.Default.History, contentDescription = "Gate Logs") },
                                    label = { Text("Gate Logs", fontSize = 11.sp, fontWeight = if (showLogsScreen) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_logs")
                                )
                                NavigationBarItem(
                                    selected = showExeatScreen,
                                    onClick = {
                                        showExeatScreen = true
                                        showLogsScreen = false
                                    },
                                    icon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = "Exeat Passes") },
                                    label = { Text("Exeat Passes", fontSize = 11.sp, fontWeight = if (showExeatScreen) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_exeat")
                                )
                            }

                            UserRole.MEAL_SERVING_STAFF -> {
                                NavigationBarItem(
                                    selected = true,
                                    onClick = { showAllExportsDialog = false },
                                    icon = { Icon(Icons.Default.Restaurant, contentDescription = "Dining Hall") },
                                    label = { Text("Dining Hall", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_dining")
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = {
                                        exportDatasetTarget = "MEALS"
                                        showAllExportsDialog = true
                                    },
                                    icon = { Icon(Icons.Default.TableChart, contentDescription = "Export") },
                                    label = { Text("Export Log", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_export")
                                )
                            }

                            UserRole.EXAMINATION_STAFF -> {
                                NavigationBarItem(
                                    selected = true,
                                    onClick = { showAllExportsDialog = false },
                                    icon = { Icon(Icons.Default.FactCheck, contentDescription = "Clearance") },
                                    label = { Text("Requirements", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_requirements")
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = {
                                        exportDatasetTarget = "REQUIREMENTS"
                                        showAllExportsDialog = true
                                    },
                                    icon = { Icon(Icons.Default.TableChart, contentDescription = "Export") },
                                    label = { Text("Export Clearances", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = roleColor.copy(alpha = 0.2f),
                                        selectedIconColor = roleColor,
                                        selectedTextColor = roleColor
                                    ),
                                    modifier = Modifier.testTag("nav_item_export")
                                )
                            }
                        }
                    }
                }
            },
            modifier = modifier
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isAnyScannerOpen) PaddingValues(0.dp) else paddingValues)
            ) {
                if (showLogsScreen) {
                    GateAccessLogsScreen(
                        scanLogs = scanLogs,
                        onBack = { showLogsScreen = false },
                        onClearLogs = { viewModel.clearLogs() }
                    )
                } else if (showGuardianAlertsScreen) {
                    GuardianAlertsScreen(
                        notifications = guardianNotifications,
                        onBack = { showGuardianAlertsScreen = false },
                        onSendCustomAlert = { studentName, phone, message ->
                            viewModel.sendCustomGuardianAlert(studentName, phone, message)
                        }
                    )
                } else if (showExeatScreen) {
                    ExeatPassScreen(
                        exeatPasses = exeatPasses,
                        students = allStudents,
                        onBack = { showExeatScreen = false },
                        onIssueExeatPass = { pass ->
                            viewModel.issueExeatPass(pass)
                        },
                        onMarkExeatUsed = { passId ->
                            viewModel.markExeatUsed(passId)
                        }
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
                                    onManualLookup = { studentId ->
                                        viewModel.handleBarcodeScan(studentId, context)
                                    },
                                    onDismissScanResult = { viewModel.dismissScanResult() },
                                    onTriggerSync = { viewModel.triggerCloudSync() },
                                    onToggleOnline = { viewModel.toggleNetworkOnline(it) },
                                    onViewAllLogs = { showLogsScreen = true }
                                )
                            }
                        }

                        UserRole.EXAMINATION_STAFF -> {
                            if (isRequirementScannerOpen) {
                                GuardScannerScreen(
                                    onBarcodeDetected = { rawCode ->
                                        viewModel.handleRequirementBarcodeScan(rawCode)
                                    },
                                    onCloseScanner = { viewModel.closeRequirementScanner() }
                                )
                            } else {
                                RequirementsMasterScreen(
                                    user = user,
                                    requirementsList = requirementsList,
                                    allStudents = allStudents,
                                    searchQuery = requirementsSearchQuery,
                                    onSearchQueryChange = { viewModel.setRequirementsSearchQuery(it) },
                                    onToggleRequirement = { studentId, itemKey ->
                                        viewModel.toggleRequirementItem(studentId, itemKey)
                                    },
                                    onMarkAllCleared = { studentId ->
                                        viewModel.markAllRequirementsCleared(studentId)
                                    },
                                    onUpdateNotes = { studentId, notes ->
                                        viewModel.updateRequirementNotes(studentId, notes)
                                    },
                                    onOpenScanner = { viewModel.openRequirementScanner() },
                                    onExport = { format ->
                                        viewModel.exportDataset("REQUIREMENTS", format, context)
                                    }
                                )
                            }
                        }

                        UserRole.TEACHERS -> {
                            if (isScannerOpen) {
                                GuardScannerScreen(
                                    onBarcodeDetected = { rawCode ->
                                        viewModel.scanUnifiedQr(rawCode, context)
                                    },
                                    onCloseScanner = { viewModel.closeScanner() }
                                )
                            } else {
                                AttendanceMasterScreen(
                                    user = user,
                                    attendanceRecords = attendanceRecords,
                                    selectedSubject = selectedAttendanceSubject,
                                    selectedSession = selectedAttendanceSession,
                                    onSubjectChange = { viewModel.setAttendanceSubject(it) },
                                    onSessionChange = { viewModel.setAttendanceSession(it) },
                                    onOpenScanner = {
                                        viewModel.selectDepartment(com.example.model.ScanDepartment.ATTENDANCE)
                                        viewModel.openScanner()
                                    },
                                    onManualLookup = { studentNo ->
                                        viewModel.scanUnifiedQr(studentNo, context)
                                    },
                                    onCorrectAttendance = { recordId, note ->
                                        // attendance audit note
                                    }
                                )
                            }
                        }
                        UserRole.ADMINISTRATOR, UserRole.BURSAR_FINANCE, UserRole.HEAD_TEACHER_MANAGEMENT -> {
                            if (selectedStudentDetail != null) {
                                StudentDetailScreen(
                                    student = selectedStudentDetail!!,
                                    cards = selectedStudentCards,
                                    onBack = { viewModel.selectStudentForDetail(null) },
                                    onUpdateFeeStatus = { newStatus, amount ->
                                        viewModel.updateFeeStatus(selectedStudentDetail!!.id, newStatus, amount)
                                    },
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
                                    },
                                    onTestScanAsGuard = { studentId ->
                                        viewModel.handleBarcodeScan(studentId, context)
                                        viewModel.selectStudentForDetail(null)
                                    }
                                )
                            } else {
                                AdminDashboardScreen(
                                    user = user,
                                    allStudents = allStudents,
                                    filteredStudents = filteredStudents,
                                    scanLogs = scanLogs,
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
                                    },
                                    onDeleteStudent = { studentId ->
                                        viewModel.deleteStudentRecord(studentId)
                                    },
                                    onEditStudent = { updated ->
                                        viewModel.updateStudentDetails(updated)
                                    },
                                    onViewScanLogs = { showLogsScreen = true },
                                    onViewGuardianAlerts = { showGuardianAlertsScreen = true },
                                    onViewExeatPasses = { showExeatScreen = true },
                                    onViewBatchPrint = { showBatchPrintDialog = true },
                                    onExportLogsCsv = { viewModel.exportGateLogsCsv(context) }
                                )
                            }
                        }

                        UserRole.MEAL_SERVING_STAFF -> {
                            if (isMealScannerOpen) {
                                GuardScannerScreen(
                                    onBarcodeDetected = { rawCode ->
                                        viewModel.verifyAndServeMeal(rawCode, context)
                                    },
                                    onCloseScanner = { viewModel.closeMealScanner() }
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
                                    onManualServe = { studentNumber ->
                                        viewModel.verifyAndServeMeal(studentNumber, context)
                                    },
                                    onDismissScanOutcome = { viewModel.dismissMealOutcome() },
                                    onExport = { format ->
                                        viewModel.exportDataset("MEALS", format, context)
                                    }
                                )
                            }
                        }
                    }
                }

                // Batch Print Modal
                if (showBatchPrintDialog) {
                    BatchPrintIdCardsDialog(
                        students = allStudents,
                        onDismiss = { showBatchPrintDialog = false }
                    )
                }

                // Privileged Role Switch Authentication Dialog
                if (showAuthDialogForRole != null) {
                    val targetRole = showAuthDialogForRole!!
                    val targetTheme = RoleThemes.getThemeForRole(targetRole)
                    AlertDialog(
                        onDismissRequest = {
                            showAuthDialogForRole = null
                            switchRolePin = ""
                            switchRolePinError = null
                            switchRoleRequestFinance = false
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = targetTheme.primaryColor
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = targetTheme.icon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("Change staff account", fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Text(
                                "Duty roles are assigned by the school administrator. To use a different role, sign out and sign in with the staff account provisioned for that role."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.logout()
                                    showLogsScreen = false
                                    showGuardianAlertsScreen = false
                                    showExeatScreen = false
                                    showAuthDialogForRole = null
                                    showRoleSwitchMenu = false
                                    switchRolePin = ""
                                    switchRolePinError = null
                                    switchRoleRequestFinance = false
                                },
                                modifier = Modifier.testTag("button_confirm_role_switch")
                            ) {
                                Text("Sign out")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showAuthDialogForRole = null
                                    switchRolePin = ""
                                    switchRolePinError = null
                                    switchRoleRequestFinance = false
                                },
                                modifier = Modifier.testTag("button_cancel_role_switch")
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Multi-Format Export Dialog
                if (showAllExportsDialog) {
                    MultiFormatExportDialog(
                        datasetTitle = when (exportDatasetTarget) {
                            "GATE_LOGS" -> "Gate Access Verification Logs"
                            "MEALS" -> "Dining Hall Meal Serving Records"
                            "REQUIREMENTS" -> "Student Term Requirements Checklist"
                            else -> "Complete Student Registry & Fee Roster"
                        },
                        recordCount = when (exportDatasetTarget) {
                            "GATE_LOGS" -> scanLogs.size
                            "MEALS" -> mealRecords.size
                            "REQUIREMENTS" -> requirementsList.size
                            else -> allStudents.size
                        },
                        onDismiss = { showAllExportsDialog = false },
                        onExport = { format ->
                            viewModel.exportDataset(exportDatasetTarget, format, context)
                            showAllExportsDialog = false
                        }
                    )
                }
            }
        }
    }
}
}

