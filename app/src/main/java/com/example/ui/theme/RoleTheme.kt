package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.UserRole

/**
 * Premium design tokens and unique color palette configurations for each LTC duty role.
 * Scholink V1: one consistent navy/teal system across every role. Role identity comes from labels and permissions, not competing palettes.
 */
data class RoleThemeConfig(
    val role: UserRole,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
    val accentGlowColor: Color,
    val containerColor: Color,
    val onContainerColor: Color,
    val badgeTextColor: Color,
    val stationBadge: String,
    val cardGradient: Brush,
    val headerGradient: Brush,
    val darkHeaderGradient: Brush,
    val icon: ImageVector,
    val departmentTagline: String,
    val dutyMotto: String,
    val quickStatLabel: String,
    val accentBorderColor: Color
) {
    fun lightColorScheme(): ColorScheme {
        return lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = containerColor,
            onPrimaryContainer = onContainerColor,
            secondary = secondaryColor,
            onSecondary = Color.White,
            secondaryContainer = containerColor.copy(alpha = 0.55f),
            onSecondaryContainer = onContainerColor,
            tertiary = tertiaryColor,
            onTertiary = Color.White,
            background = BgLight,
            onBackground = TextPrimaryLight,
            surface = Color.White,
            onSurface = TextPrimaryLight,
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = TextSecondaryLight,
            error = RejectedRed,
            onError = Color.White,
            outline = accentBorderColor.copy(alpha = 0.5f),
            outlineVariant = accentBorderColor.copy(alpha = 0.2f)
        )
    }

    fun darkColorScheme(): ColorScheme {
        return darkColorScheme(
            primary = accentGlowColor,
            onPrimary = Color(0xFF0F172A),
            primaryContainer = secondaryColor,
            onPrimaryContainer = containerColor,
            secondary = accentGlowColor.copy(alpha = 0.85f),
            onSecondary = Color(0xFF0F172A),
            secondaryContainer = Color(0xFF1E293B),
            onSecondaryContainer = Color(0xFFF1F5F9),
            tertiary = tertiaryColor,
            onTertiary = Color(0xFF0F172A),
            background = BgDark,
            onBackground = TextPrimaryDark,
            surface = Color(0xFF17283D),
            onSurface = TextPrimaryDark,
            surfaceVariant = Color(0xFF24364B),
            onSurfaceVariant = TextSecondaryDark,
            error = Color(0xFFEF4444),
            onError = Color.White,
            outline = accentGlowColor.copy(alpha = 0.5f),
            outlineVariant = accentGlowColor.copy(alpha = 0.25f)
        )
    }
}

object RoleThemes {

    // 1. Administrator: Imperial Gold & Obsidian Slate
    val AdministratorTheme = RoleThemeConfig(
        role = UserRole.ADMINISTRATOR,
        primaryColor = Color(0xFF142B4A),
        secondaryColor = Color(0xFF0F9D91),
        tertiaryColor = Color(0xFF08766E),
        accentGlowColor = Color(0xFF0F9D91),
        containerColor = Color(0xFFE6F4F3),
        onContainerColor = Color(0xFF142B4A),
        badgeTextColor = Color(0xFF08766E),
        stationBadge = "REGISTRY HQ",
        cardGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0F9D91))),
        headerGradient = Brush.verticalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0B1F33))),
        darkHeaderGradient = Brush.verticalGradient(colors = listOf(Color(0xFF0B1F33), Color(0xFF081522))),
        icon = Icons.Default.AdminPanelSettings,
        departmentTagline = "REGISTRY & GOVERNANCE TERMINAL",
        dutyMotto = "Official Student Roster • ID Badge Authority • Security Provisioning",
        quickStatLabel = "Total Enrolled",
        accentBorderColor = Color(0xFF0F9D91)
    )

    // 2. Bursar / Finance: Emerald Teal & Mint Cashmere
    val BursarFinanceTheme = RoleThemeConfig(
        role = UserRole.BURSAR_FINANCE,
        primaryColor = Color(0xFF142B4A),
        secondaryColor = Color(0xFF0F9D91),
        tertiaryColor = Color(0xFF08766E),
        accentGlowColor = Color(0xFF0F9D91),
        containerColor = Color(0xFFE6F4F3),
        onContainerColor = Color(0xFF142B4A),
        badgeTextColor = Color(0xFF08766E),
        stationBadge = "BURSARY DESK",
        cardGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0F9D91))),
        headerGradient = Brush.verticalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0B1F33))),
        darkHeaderGradient = Brush.verticalGradient(colors = listOf(Color(0xFF0B1F33), Color(0xFF081522))),
        icon = Icons.Default.AccountBalance,
        departmentTagline = "BURSARY & FISCAL CLEARANCE",
        dutyMotto = "Tuition Roster • Ledger Clearance • Revenue Accounting",
        quickStatLabel = "Fees Cleared",
        accentBorderColor = Color(0xFF0F9D91)
    )

    // 3. Gate Staff: Tactical Sapphire & Electric Cobalt
    val GateStaffTheme = RoleThemeConfig(
        role = UserRole.GATE_STAFF,
        primaryColor = Color(0xFF142B4A),
        secondaryColor = Color(0xFF0F9D91),
        tertiaryColor = Color(0xFF08766E),
        accentGlowColor = Color(0xFF0F9D91),
        containerColor = Color(0xFFE6F4F3),
        onContainerColor = Color(0xFF142B4A),
        badgeTextColor = Color(0xFF08766E),
        stationBadge = "GATE 1 TURNSTILE",
        cardGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0F9D91))),
        headerGradient = Brush.verticalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0B1F33))),
        darkHeaderGradient = Brush.verticalGradient(colors = listOf(Color(0xFF0B1F33), Color(0xFF081522))),
        icon = Icons.Default.Security,
        departmentTagline = "PERIMETER & ACCESS CONTROL",
        dutyMotto = "Gate 1 Main Turnstile • QR Telemetry • Boarding & Day Scholar Control",
        quickStatLabel = "Verified Today",
        accentBorderColor = Color(0xFF0F9D91)
    )

    // 4. Meal-Serving Staff: Culinary Ruby & Warm Amber
    val MealServingStaffTheme = RoleThemeConfig(
        role = UserRole.MEAL_SERVING_STAFF,
        primaryColor = Color(0xFF142B4A),
        secondaryColor = Color(0xFF0F9D91),
        tertiaryColor = Color(0xFF08766E),
        accentGlowColor = Color(0xFF0F9D91),
        containerColor = Color(0xFFE6F4F3),
        onContainerColor = Color(0xFF142B4A),
        badgeTextColor = Color(0xFF08766E),
        stationBadge = "DINING HALL",
        cardGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0F9D91))),
        headerGradient = Brush.verticalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0B1F33))),
        darkHeaderGradient = Brush.verticalGradient(colors = listOf(Color(0xFF0B1F33), Color(0xFF081522))),
        icon = Icons.Default.Restaurant,
        departmentTagline = "DINING HALL & NUTRITION SERVICES",
        dutyMotto = "Dining Hall Turnstile • Meal Session Verification • Anti-Double Serving",
        quickStatLabel = "Meals Served",
        accentBorderColor = Color(0xFF0F9D91)
    )

    // 5. Teachers: Scholarly Indigo & Royal Iris
    val TeachersTheme = RoleThemeConfig(
        role = UserRole.TEACHERS,
        primaryColor = Color(0xFF142B4A),
        secondaryColor = Color(0xFF0F9D91),
        tertiaryColor = Color(0xFF08766E),
        accentGlowColor = Color(0xFF0F9D91),
        containerColor = Color(0xFFE6F4F3),
        onContainerColor = Color(0xFF142B4A),
        badgeTextColor = Color(0xFF08766E),
        stationBadge = "FACULTY PORTAL",
        cardGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0F9D91))),
        headerGradient = Brush.verticalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0B1F33))),
        darkHeaderGradient = Brush.verticalGradient(colors = listOf(Color(0xFF0B1F33), Color(0xFF081522))),
        icon = Icons.Default.MenuBook,
        departmentTagline = "ACADEMICS & CLASSROOM ROSTER",
        dutyMotto = "Class Roll-Call • Attendance Verification • Student Directories",
        quickStatLabel = "Class Roster",
        accentBorderColor = Color(0xFF0F9D91)
    )

    // 6. Examination Staff: Precision Cyan & Arctic Steel
    val ExaminationStaffTheme = RoleThemeConfig(
        role = UserRole.EXAMINATION_STAFF,
        primaryColor = Color(0xFF142B4A),
        secondaryColor = Color(0xFF0F9D91),
        tertiaryColor = Color(0xFF08766E),
        accentGlowColor = Color(0xFF0F9D91),
        containerColor = Color(0xFFE6F4F3),
        onContainerColor = Color(0xFF142B4A),
        badgeTextColor = Color(0xFF08766E),
        stationBadge = "EXAM CONTROL",
        cardGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0F9D91))),
        headerGradient = Brush.verticalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0B1F33))),
        darkHeaderGradient = Brush.verticalGradient(colors = listOf(Color(0xFF0B1F33), Color(0xFF081522))),
        icon = Icons.Default.FactCheck,
        departmentTagline = "STANDARDS & EXAM CLEARANCE",
        dutyMotto = "UNEB Examination Hall Access • Requirements Compliance • Candidate Slips",
        quickStatLabel = "Exam Cleared",
        accentBorderColor = Color(0xFF0F9D91)
    )

    // 7. Head Teacher / Management: Sovereign Purple & Radiant Platinum
    val HeadTeacherManagementTheme = RoleThemeConfig(
        role = UserRole.HEAD_TEACHER_MANAGEMENT,
        primaryColor = Color(0xFF142B4A),
        secondaryColor = Color(0xFF0F9D91),
        tertiaryColor = Color(0xFF08766E),
        accentGlowColor = Color(0xFF0F9D91),
        containerColor = Color(0xFFE6F4F3),
        onContainerColor = Color(0xFF142B4A),
        badgeTextColor = Color(0xFF08766E),
        stationBadge = "EXECUTIVE SUITE",
        cardGradient = Brush.horizontalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0F9D91))),
        headerGradient = Brush.verticalGradient(colors = listOf(Color(0xFF142B4A), Color(0xFF0B1F33))),
        darkHeaderGradient = Brush.verticalGradient(colors = listOf(Color(0xFF0B1F33), Color(0xFF081522))),
        icon = Icons.Default.SupervisorAccount,
        departmentTagline = "EXECUTIVE OVERSIGHT & BOARD COMMAND",
        dutyMotto = "Institutional Governance • Emergency Override Authority • Campus Intelligence",
        quickStatLabel = "Active Students",
        accentBorderColor = Color(0xFF0F9D91)
    )

    fun getThemeForRole(role: UserRole): RoleThemeConfig {
        return when (role) {
            UserRole.ADMINISTRATOR -> AdministratorTheme
            UserRole.BURSAR_FINANCE -> BursarFinanceTheme
            UserRole.GATE_STAFF -> GateStaffTheme
            UserRole.MEAL_SERVING_STAFF -> MealServingStaffTheme
            UserRole.TEACHERS -> TeachersTheme
            UserRole.EXAMINATION_STAFF -> ExaminationStaffTheme
            UserRole.HEAD_TEACHER_MANAGEMENT -> HeadTeacherManagementTheme
        }
    }
}

val LocalRoleTheme = staticCompositionLocalOf { RoleThemes.AdministratorTheme }

@Composable
fun RoleThemeWrapper(
    role: UserRole?,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val themeConfig = if (role != null) {
        RoleThemes.getThemeForRole(role)
    } else {
        RoleThemes.AdministratorTheme
    }

    val colorScheme = if (role != null) {
        if (darkTheme) themeConfig.darkColorScheme() else themeConfig.lightColorScheme()
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }

    CompositionLocalProvider(LocalRoleTheme provides themeConfig) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
