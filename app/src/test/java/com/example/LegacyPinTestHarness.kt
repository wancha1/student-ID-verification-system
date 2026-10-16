package com.example

import android.content.Context
import com.example.model.UserRole
import com.example.ui.MainViewModel
import com.example.util.SecurityManager

/**
 * Test-only adapter for legacy role-workflow tests.
 *
 * Production authentication is Firebase-backed. This helper deliberately lives
 * under src/test and must never be moved into main source: it lets existing
 * scanner/session tests establish a test session after validating their fixture
 * PINs, without restoring local-PIN authentication to the shipped app.
 */
internal fun MainViewModel.authenticateAndLogin(
    context: Context,
    role: UserRole,
    pin: String,
    customName: String? = null
): Boolean {
    if (!SecurityManager.verifyRolePin(context, role, pin)) {
        return false
    }

    loginAs(
        role = role,
        customName = customName,
        hasFinancePermission = role == UserRole.BURSAR_FINANCE
    )
    return true
}
