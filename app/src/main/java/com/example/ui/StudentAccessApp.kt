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
import com.example.ui.admin.StudentDetailScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.guard.GuardDashboardScreen
import com.example.ui.guard.GuardScannerScreen
import com.example.ui.logs.GateAccessLogsScreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SchoolPrimary

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

    var showLogsScreen by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

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
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isScannerOpen) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    color = if (user.role == UserRole.SECURITY_GUARD) SchoolPrimary else GoldAccent
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (user.role == UserRole.SECURITY_GUARD) Icons.Default.Security else Icons.Default.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Oakridge Student Access",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (user.role == UserRole.SECURITY_GUARD) "Gate Verification • Guard Mode" else "Admin Office • Fees & Records",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        },
                        actions = {
                            // Quick Role Switcher Pill
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .testTag("button_switch_role")
                                    .clickable {
                                        val newRole = if (user.role == UserRole.SECURITY_GUARD) UserRole.ADMINISTRATOR else UserRole.SECURITY_GUARD
                                        viewModel.loginAs(newRole)
                                        showLogsScreen = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Switch Role",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (user.role == UserRole.SECURITY_GUARD) "Switch to Admin" else "Switch to Guard",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp
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
                                        text = { Text("Gate Access Activity Logs") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showLogsScreen = true
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_view_logs")
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
                                        text = { Text("Reset Sample Roster") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.resetDemoData()
                                            showOptionsMenu = false
                                        },
                                        modifier = Modifier.testTag("menu_reset_demo_data")
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
                    .padding(if (isScannerOpen) PaddingValues(0.dp) else paddingValues)
            ) {
                if (showLogsScreen) {
                    GateAccessLogsScreen(
                        scanLogs = scanLogs,
                        onBack = { showLogsScreen = false },
                        onClearLogs = { viewModel.clearLogs() }
                    )
                } else {
                    when (user.role) {
                        UserRole.SECURITY_GUARD -> {
                            if (isScannerOpen) {
                                GuardScannerScreen(
                                    sampleStudents = allStudents,
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
                                    allStudents = allStudents,
                                    scanLogs = scanLogs,
                                    syncInfo = syncInfo,
                                    onOpenScanner = { viewModel.openScanner() },
                                    onSimulateScan = { studentId ->
                                        viewModel.handleBarcodeScan(studentId, context)
                                    },
                                    onDismissScanResult = { viewModel.dismissScanResult() },
                                    onTriggerSync = { viewModel.triggerCloudSync() },
                                    onToggleOnline = { viewModel.toggleNetworkOnline(it) },
                                    onViewAllLogs = { showLogsScreen = true }
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
                                        viewModel.loginAs(UserRole.SECURITY_GUARD)
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
                                    onViewScanLogs = { showLogsScreen = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
