package com.example.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.UserRole
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SchoolPrimary
import com.example.util.SecurityManager

@Composable
fun LoginScreen(
    onSelectRole: (UserRole, String?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var isProvisioned by remember { mutableStateOf(SecurityManager.isProvisioned(context)) }

    // First-run provisioning dialog state
    var showProvisionDialog by remember { mutableStateOf(false) }
    var provisionAdminName by remember { mutableStateOf("") }
    var provisionAdminPin by remember { mutableStateOf("") }
    var provisionConfirmPin by remember { mutableStateOf("") }
    var provisionError by remember { mutableStateOf<String?>(null) }

    // Duty Mode authentication dialog state
    var showPinDialog by remember { mutableStateOf(false) }
    var rolePendingAuth by remember { mutableStateOf<UserRole?>(null) }
    var enteredPin by remember { mutableStateOf("") }
    var enteredStaffName by remember { mutableStateOf("") }
    var requestFinanceAccess by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Campus Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_school_hero),
                    contentDescription = "Lira Town College Campus",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    SchoolPrimary.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(80.dp)
                        .shadow(8.dp, CircleShape),
                    shape = CircleShape,
                    color = SchoolPrimary,
                    border = androidx.compose.foundation.BorderStroke(3.dp, GoldAccent)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "Lira Town College Crest",
                            tint = GoldAccent,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "LIRA TOWN COLLEGE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Student Access System",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Student QR Identity & Gate Access • Bursar Clearance • Lira, Uganda",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // First-Run Security Setup Callout (if unprovisioned)
            if (!isProvisioned) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("banner_security_provisioning_required")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Initial Security Provisioning Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This terminal is unconfigured. No default PIN or backdoor credentials exist. An authorized Administrator must establish the local security credentials to enable staff access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                provisionAdminName = ""
                                provisionAdminPin = ""
                                provisionConfirmPin = ""
                                provisionError = null
                                showProvisionDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("button_setup_initial_security")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Setup Administrator Security PIN")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Role Selection Container (Seven Target Roles)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AUTHENTICATE STAFF DUTY MODE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Gate Staff (Gate 1 Turnstile)
                DutyRoleCard(
                    role = UserRole.GATE_STAFF,
                    icon = Icons.Default.Security,
                    color = SchoolPrimary,
                    testTag = "button_login_gate_keeper",
                    onClick = {
                        if (!isProvisioned) {
                            showProvisionDialog = true
                        } else {
                            rolePendingAuth = UserRole.GATE_STAFF
                            enteredPin = ""
                            enteredStaffName = "Gate Security Officer"
                            requestFinanceAccess = false
                            pinError = null
                            showPinDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Bursar / Finance (Fee Clearance)
                DutyRoleCard(
                    role = UserRole.BURSAR_FINANCE,
                    icon = Icons.Default.AccountBalance,
                    color = Color(0xFF0D9488),
                    testTag = "button_login_bursar_finance",
                    onClick = {
                        if (!isProvisioned) {
                            showProvisionDialog = true
                        } else {
                            rolePendingAuth = UserRole.BURSAR_FINANCE
                            enteredPin = ""
                            enteredStaffName = "Bursar Officer"
                            requestFinanceAccess = true
                            pinError = null
                            showPinDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Administrator (Student Registry, Cards, System Settings)
                DutyRoleCard(
                    role = UserRole.ADMINISTRATOR,
                    icon = Icons.Default.AdminPanelSettings,
                    color = GoldAccent,
                    testTag = "button_login_administrator",
                    onClick = {
                        if (!isProvisioned) {
                            showProvisionDialog = true
                        } else {
                            rolePendingAuth = UserRole.ADMINISTRATOR
                            enteredPin = ""
                            enteredStaffName = SecurityManager.getAdminName(context)
                            requestFinanceAccess = false
                            pinError = null
                            showPinDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Meal-Serving Staff (Dining Hall)
                DutyRoleCard(
                    role = UserRole.MEAL_SERVING_STAFF,
                    icon = Icons.Default.Restaurant,
                    color = Color(0xFFE11D48),
                    testTag = "button_login_meals_master",
                    onClick = {
                        if (!isProvisioned) {
                            showProvisionDialog = true
                        } else {
                            rolePendingAuth = UserRole.MEAL_SERVING_STAFF
                            enteredPin = ""
                            enteredStaffName = "Catering Staff"
                            requestFinanceAccess = false
                            pinError = null
                            showPinDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Teachers (Classroom Roll Call)
                DutyRoleCard(
                    role = UserRole.TEACHERS,
                    icon = Icons.Default.MenuBook,
                    color = Color(0xFF4F46E5),
                    testTag = "button_login_teachers",
                    onClick = {
                        if (!isProvisioned) {
                            showProvisionDialog = true
                        } else {
                            rolePendingAuth = UserRole.TEACHERS
                            enteredPin = ""
                            enteredStaffName = "Teacher on Duty"
                            requestFinanceAccess = false
                            pinError = null
                            showPinDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 6. Examination Staff (Exam Hall Entry Clearance)
                DutyRoleCard(
                    role = UserRole.EXAMINATION_STAFF,
                    icon = Icons.Default.FactCheck,
                    color = Color(0xFF0284C7),
                    testTag = "button_login_requirements_master",
                    onClick = {
                        if (!isProvisioned) {
                            showProvisionDialog = true
                        } else {
                            rolePendingAuth = UserRole.EXAMINATION_STAFF
                            enteredPin = ""
                            enteredStaffName = "Examination Officer"
                            requestFinanceAccess = false
                            pinError = null
                            showPinDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 7. Head Teacher / Management (Executive Oversight & Gate Overrides)
                DutyRoleCard(
                    role = UserRole.HEAD_TEACHER_MANAGEMENT,
                    icon = Icons.Default.SupervisorAccount,
                    color = Color(0xFF7C3AED),
                    testTag = "button_login_head_teacher",
                    onClick = {
                        if (!isProvisioned) {
                            showProvisionDialog = true
                        } else {
                            rolePendingAuth = UserRole.HEAD_TEACHER_MANAGEMENT
                            enteredPin = ""
                            enteredStaffName = "Head Teacher"
                            requestFinanceAccess = false
                            pinError = null
                            showPinDialog = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Feature Highlights
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeaturePill(icon = Icons.Default.QrCodeScanner, text = "CameraX Scan", modifier = Modifier.weight(1f))
                    FeaturePill(icon = Icons.Default.VerifiedUser, text = "Role Security", modifier = Modifier.weight(1f))
                    FeaturePill(icon = Icons.Default.DirectionsBus, text = "Day Scholars", modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Least-Privilege Security: Each duty role requires verified authentication. Financial modifications are strictly reserved for Bursar clearance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Dialog: First-Run Security Provisioning
        if (showProvisionDialog) {
            AlertDialog(
                onDismissRequest = { showProvisionDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Terminal Security Setup", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Create the Master Administrator credentials to secure this terminal. Choose a secure 4-6 digit numeric PIN.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = provisionAdminName,
                            onValueChange = { provisionAdminName = it },
                            label = { Text("Administrator Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_provision_admin_name")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = provisionAdminPin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    provisionAdminPin = it
                                    provisionError = null
                                }
                            },
                            label = { Text("Master PIN (4-6 digits)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth().testTag("input_provision_admin_pin")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = provisionConfirmPin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    provisionConfirmPin = it
                                    provisionError = null
                                }
                            },
                            label = { Text("Confirm Master PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = provisionError != null,
                            supportingText = provisionError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier.fillMaxWidth().testTag("input_provision_confirm_pin")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (provisionAdminPin.length < 4) {
                                provisionError = "PIN must be at least 4 digits"
                            } else if (provisionAdminPin != provisionConfirmPin) {
                                provisionError = "PINs do not match"
                            } else {
                                val success = SecurityManager.provisionInitialAdmin(
                                    context,
                                    provisionAdminName.ifBlank { "Administrator" },
                                    provisionAdminPin
                                )
                                if (success) {
                                    isProvisioned = true
                                    showProvisionDialog = false
                                } else {
                                    provisionError = "Failed to provision terminal security"
                                }
                            }
                        },
                        modifier = Modifier.testTag("button_confirm_provision")
                    ) {
                        Text("Save & Provision")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showProvisionDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog: Mandatory Role Authentication
        if (showPinDialog && rolePendingAuth != null) {
            val targetRole = rolePendingAuth!!
            AlertDialog(
                onDismissRequest = {
                    showPinDialog = false
                    rolePendingAuth = null
                    enteredPin = ""
                    pinError = null
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${targetRole.title} Authentication", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Enter your verified staff PIN to activate ${targetRole.title} duty mode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = enteredStaffName,
                            onValueChange = { enteredStaffName = it },
                            label = { Text("Staff Full Name (Audit Log Attribution)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_staff_name")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    enteredPin = it
                                    pinError = null
                                }
                            },
                            label = { Text("Staff PIN (4-6 digits)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = pinError != null,
                            supportingText = pinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_staff_pin")
                        )

                        // Least Privilege: Explicit Finance Access Checkbox for Administrator
                        if (targetRole == UserRole.ADMINISTRATOR) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { requestFinanceAccess = !requestFinanceAccess }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = requestFinanceAccess,
                                    onCheckedChange = { requestFinanceAccess = it },
                                    modifier = Modifier.testTag("checkbox_request_finance_access")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Request Explicit Finance / Fee Clearance Permission",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "By default, Administrator role cannot modify fees unless explicitly enabled.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (SecurityManager.verifyRolePin(context, targetRole, enteredPin)) {
                                showPinDialog = false
                                val roleToEnter = targetRole
                                val staffName = enteredStaffName.ifBlank { targetRole.defaultUsername }
                                val financeAccess = if (targetRole == UserRole.BURSAR_FINANCE) true else requestFinanceAccess
                                rolePendingAuth = null
                                enteredPin = ""
                                pinError = null
                                onSelectRole(roleToEnter, staffName, financeAccess)
                            } else {
                                pinError = "Incorrect staff PIN. Access denied."
                            }
                        },
                        modifier = Modifier.testTag("button_confirm_pin")
                    ) {
                        Text("Authenticate")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPinDialog = false
                            rolePendingAuth = null
                            enteredPin = ""
                            pinError = null
                        },
                        modifier = Modifier.testTag("button_cancel_pin")
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// Backward-compatible overload
@Composable
fun LoginScreen(
    onSelectRole: (UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    LoginScreen(
        onSelectRole = { role, _, _ -> onSelectRole(role) },
        modifier = modifier
    )
}

@Composable
private fun DutyRoleCard(
    role: UserRole,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .testTag(testTag)
            .fillMaxWidth()
            .border(
                1.5.dp,
                color.copy(alpha = 0.4f),
                RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = role.title,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = role.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = role.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FeaturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
