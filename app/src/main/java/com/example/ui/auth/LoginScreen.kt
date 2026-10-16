package com.example.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.model.UserRole

/**
 * Compatibility wrapper retained for callers using the historical LoginScreen name.
 * Authentication and role resolution are delegated to FirebaseStaffLoginScreen.
 */
@Composable
fun LoginScreen(
    onSelectRole: (UserRole, String?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FirebaseStaffLoginScreen(
        onAuthenticated = onSelectRole,
        modifier = modifier
    )
}

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
