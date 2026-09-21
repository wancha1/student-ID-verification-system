package com.example.ui

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.FeeStatus
import com.example.model.UserRole
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.AdminScanLogsScreen
import com.example.ui.admin.BatchPrintIdCardsDialog
import com.example.ui.admin.StudentDetailScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.exeat.ExeatPassScreen
import com.example.ui.guard.GuardDashboardScreen
import com.example.ui.guard.GuardScannerScreen
import com.example.ui.logs.GateAccessLogsScreen
import com.example.ui.notifications.GuardianAlertsScreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SchoolPrimary
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TableChart
import com.example.ui.components.MultiFormatExportDialog
import com.example.ui.meals.MealsMasterScreen
import com.example.ui.requirements.RequirementsMasterScreen
import com.example.util.ExportFormat

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

    val isAnyScannerOpen = isScannerOpen || isMealScannerOpen || isRequirementScannerOpen

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userFeedbackMessage) {
        userFeedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearFeedbackMessage()
        }
    }

    // Top Level Container
    val user = currentUser
    if (user == null) {
        LoginScreen(
            onSelectRole = { role -> viewModel.loginAs(role) },
            modifier = modifier
        )
    } else {
        val roleColor = when (user.role) {
            UserRole.GATE_KEEPER, UserRole.SECURITY_GUARD -> SchoolPrimary
            UserRole.REQUIREMENTS_MASTER -> Color(0xFF0284C7)
            UserRole.ADMINISTRATOR -> GoldAccent
            UserRole.MEALS_MASTER -> Color(0xFFE11D48)
        }

        val roleIcon = when (user.role) {
            UserRole.GATE_KEEPER, UserRole.SECURITY_GUARD -> Icons.Default.Security
            UserRole.REQUIREMENTS_MASTER -> Icons.Default.FactCheck
            UserRole.ADMINISTRATOR -> Icons.Default.AdminPanelSettings
            UserRole.MEALS_MASTER -> Icons.Default.Restaurant
        }

        val roleTitle = when (user.role) {
            UserRole.GATE_KEEPER, UserRole.SECURITY_GUARD -> "Gate Keeper"
            UserRole.REQUIREMENTS_MASTER -> "Requirements Master"
            UserRole.ADMINISTRATOR -> "Administrator"
            UserRole.MEALS_MASTER -> "Meals Master"
        }

        val roleSubtitle = when (user.role) {
            UserRole.GATE_KEEPER, UserRole.SECURITY_GUARD -> "Turnstile Gate 1 • Access Verification"
            UserRole.REQUIREMENTS_MASTER -> "Checklist Clearance • Student Affairs"
            UserRole.ADMINISTRATOR -> "Bursar & Records • Fees & Registry"
            UserRole.MEALS_MASTER -> "Dining Hall Turnstile • Food Service"
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isAnyScannerOpen) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    shape = CircleShape,
                                    color = roleColor
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = roleIcon,
                                            contentDescription = roleTitle,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Oakridge Academy",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$roleTitle • $roleSubtitle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        },
                        actions = {
                            // Quick Role Switcher Pill with Dropdown
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = roleColor.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, roleColor.copy(alpha = 0.35f)),
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
                                            contentDescription = "Switch Role",
                                            tint = roleColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = roleTitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = roleColor,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showRoleSwitchMenu,
                                    onDismissRequest = { showRoleSwitchMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Gate Keeper (Turnstile)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Security, contentDescription = null, tint = SchoolPrimary)
                                        },
                                        onClick = {
                                            viewModel.loginAs(UserRole.GATE_KEEPER)
                                            showLogsScreen = false
                                            showGuardianAlertsScreen = false
                                            showExeatScreen = false
                                            showRoleSwitchMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Requirements Master (Checklist)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.FactCheck, contentDescription = null, tint = Color(0xFF0284C7))
                                        },
                                        onClick = {
                                            viewModel.loginAs(UserRole.REQUIREMENTS_MASTER)
                                            showLogsScreen = false
                                            showGuardianAlertsScreen = false
                                            showExeatScreen = false
                                            showRoleSwitchMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Administrator (Registry & Fees)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = GoldAccent)
                                        },
                                        onClick = {
                                            viewModel.loginAs(UserRole.ADMINISTRATOR)
                                            showLogsScreen = false
                                            showGuardianAlertsScreen = false
                                            showExeatScreen = false
                                            showRoleSwitchMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Meals Master (Dining Hall)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color(0xFFE11D48))
                                        },
                                        onClick = {
                                            viewModel.loginAs(UserRole.MEALS_MASTER)
                                            showLogsScreen = false
                                            showGuardianAlertsScreen = false
                                            showExeatScreen = false
                                            showRoleSwitchMenu = false
                                        }
                                    )
                                }
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
                                        text = { Text("Switch / Log Out") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ExitToApp,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.logout()
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_logout")
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
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
                        UserRole.GATE_KEEPER, UserRole.SECURITY_GUARD -> {
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

                        UserRole.REQUIREMENTS_MASTER -> {
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

                        UserRole.ADMINISTRATOR -> {
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
                                        viewModel.loginAs(UserRole.GATE_KEEPER)
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

                        UserRole.MEALS_MASTER -> {
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
