package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
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
 * Cohesive role accent tokens built on top of the unified LTC Neutral & Navy system.
 * Keeps every role immediately recognizable without jarring neon color shifts.
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
            secondaryContainer = SurfaceVariantLight,
            onSecondaryContainer = TextPrimaryLight,
            tertiary = tertiaryColor,
            onTertiary = Color.White,
            background = BgLight,
            onBackground = TextPrimaryLight,
            surface = SurfaceLight,
            onSurface = TextPrimaryLight,
            surfaceVariant = SurfaceVariantLight,
            onSurfaceVariant = TextSecondaryLight,
            error = RejectedRed,
            onError = Color.White,
            outline = Color(0xFF94A3B8),
            outlineVariant = DividerLight
        )
    }

    fun darkColorScheme(): ColorScheme {
        return darkColorScheme(
            primary = accentGlowColor,
            onPrimary = Navy950,
            primaryContainer = secondaryColor,
            onPrimaryContainer = containerColor,
            secondary = accentGlowColor.copy(alpha = 0.85f),
            onSecondary = Navy950,
            secondaryContainer = SurfaceVariantDark,
            onSecondaryContainer = TextPrimaryDark,
            tertiary = GoldBright,
            onTertiary = Navy950,
            background = BgDark,
            onBackground = TextPrimaryDark,
            surface = SurfaceDark,
            onSurface = TextPrimaryDark,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = TextSecondaryDark,
            error = Color(0xFFF87171),
            onError = Navy950,
            outline = Color(0xFF475569),
            outlineVariant = DividerDark
        )
    }
}

object RoleThemes {

    val AdministratorTheme = RoleThemeConfig(
        role = UserRole.ADMINISTRATOR,
        primaryColor = Color(0xFF0F2942),
        secondaryColor = Color(0xFF1D4368),
        tertiaryColor = GoldAccent,
        accentGlowColor = Color(0xFF60A5FA),
        containerColor = Color(0xFFFEF3C7),
        onContainerColor = Color(0xFF78350F),
        badgeTextColor = Color(0xFF78350F),
        stationBadge = "ADMIN REGISTRY",
        cardGradient = Brush.horizontalGradient(listOf(Navy900, Navy800)),
        headerGradient = Brush.verticalGradient(listOf(Navy900, Navy950)),
        darkHeaderGradient = Brush.verticalGradient(listOf(Navy900, Navy950)),
        icon = Icons.Default.AdminPanelSettings,
        departmentTagline = "REGISTRY & GOVERNANCE",
        dutyMotto = "Student Roster • ID Cards • Access Logs",
        quickStatLabel = "Registered",
        accentBorderColor = GoldAccent
    )

    val BursarFinanceTheme = RoleThemeConfig(
        role = UserRole.BURSAR_FINANCE,
        primaryColor = Color(0xFF0F766E),
        secondaryColor = Color(0xFF115E59),
        tertiaryColor = GoldAccent,
        accentGlowColor = Color(0xFF2DD4BF),
        containerColor = Color(0xFFCCFBF1),
        onContainerColor = Color(0xFF115E59),
        badgeTextColor = Color(0xFF115E59),
        stationBadge = "BURSARY DESK",
        cardGradient = Brush.horizontalGradient(listOf(Color(0xFF0F766E), Color(0xFF134E4A))),
        headerGradient = Brush.verticalGradient(listOf(Color(0xFF134E4A), Navy950)),
        darkHeaderGradient = Brush.verticalGradient(listOf(Color(0xFF134E4A), Navy950)),
        icon = Icons.Default.AccountBalance,
        departmentTagline = "FINANCE & FEE CLEARANCE",
        dutyMotto = "Tuition Roster • Ledger Clearance",
        quickStatLabel = "Fees Cleared",
        accentBorderColor = Color(0xFF0D9488)
    )

    val GateStaffTheme = RoleThemeConfig(
        role = UserRole.GATE_STAFF,
        primaryColor = Color(0xFF1D4ED8),
        secondaryColor = Color(0xFF1E3A8A),
        tertiaryColor = GoldAccent,
        accentGlowColor = Color(0xFF60A5FA),
        containerColor = Color(0xFFDBEAFE),
        onContainerColor = Color(0xFF1E3A8A),
        badgeTextColor = Color(0xFF1E3A8A),
        stationBadge = "GATE 1",
        cardGradient = Brush.horizontalGradient(listOf(Navy900, Color(0xFF1E3A8A))),
        headerGradient = Brush.verticalGradient(listOf(Navy900, Color(0xFF1E3A8A))),
        darkHeaderGradient = Brush.verticalGradient(listOf(Navy950, Navy900)),
        icon = Icons.Default.Security,
        departmentTagline = "GATE ACCESS CONTROL",
        dutyMotto = "QR Verification • Entry Telemetry",
        quickStatLabel = "Verified Today",
        accentBorderColor = Color(0xFF2563EB)
    )

    val MealServingStaffTheme = RoleThemeConfig(
        role = UserRole.MEAL_SERVING_STAFF,
        primaryColor = Color(0xFFB45309),
        secondaryColor = Color(0xFF92400E),
        tertiaryColor = Navy900,
        accentGlowColor = Color(0xFFFBBF24),
        containerColor = Color(0xFFFEF3C7),
        onContainerColor = Color(0xFF78350F),
        badgeTextColor = Color(0xFF78350F),
        stationBadge = "DINING HALL",
        cardGradient = Brush.horizontalGradient(listOf(Navy900, Color(0xFF78350F))),
        headerGradient = Brush.verticalGradient(listOf(Color(0xFF78350F), Navy950)),
        darkHeaderGradient = Brush.verticalGradient(listOf(Color(0xFF451A03), Navy950)),
        icon = Icons.Default.Restaurant,
        departmentTagline = "DINING SERVICES",
        dutyMotto = "Lunch & Supper Verification",
        quickStatLabel = "Served Today",
        accentBorderColor = Color(0xFFD97706)
    )

    val TeachersTheme = RoleThemeConfig(
        role = UserRole.TEACHERS,
        primaryColor = Color(0xFF3730A3),
        secondaryColor = Color(0xFF312E81),
        tertiaryColor = GoldAccent,
        accentGlowColor = Color(0xFF818CF8),
        containerColor = Color(0xFFE0E7FF),
        onContainerColor = Color(0xFF312E81),
        badgeTextColor = Color(0xFF312E81),
        stationBadge = "FACULTY",
        cardGradient = Brush.horizontalGradient(listOf(Navy900, Color(0xFF312E81))),
        headerGradient = Brush.verticalGradient(listOf(Color(0xFF312E81), Navy950)),
        darkHeaderGradient = Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Navy950)),
        icon = Icons.AutoMirrored.Filled.MenuBook,
        departmentTagline = "CLASSROOM ROSTER",
        dutyMotto = "Roll-Call & Attendance",
        quickStatLabel = "Class Roster",
        accentBorderColor = Color(0xFF4F46E5)
    )

    val ExaminationStaffTheme = RoleThemeConfig(
        role = UserRole.EXAMINATION_STAFF,
        primaryColor = Color(0xFF0369A1),
        secondaryColor = Color(0xFF075985),
        tertiaryColor = GoldAccent,
        accentGlowColor = Color(0xFF38BDF8),
        containerColor = Color(0xFFE0F2FE),
        onContainerColor = Color(0xFF075985),
        badgeTextColor = Color(0xFF075985),
        stationBadge = "EXAM CONTROL",
        cardGradient = Brush.horizontalGradient(listOf(Navy900, Color(0xFF075985))),
        headerGradient = Brush.verticalGradient(listOf(Color(0xFF075985), Navy950)),
        darkHeaderGradient = Brush.verticalGradient(listOf(Color(0xFF082F49), Navy950)),
        icon = Icons.AutoMirrored.Filled.FactCheck,
        departmentTagline = "EXAM CLEARANCE",
        dutyMotto = "Candidate Verification",
        quickStatLabel = "Cleared",
        accentBorderColor = Color(0xFF0284C7)
    )

    val HeadTeacherManagementTheme = RoleThemeConfig(
        role = UserRole.HEAD_TEACHER_MANAGEMENT,
        primaryColor = Color(0xFF0F2942),
        secondaryColor = Color(0xFF1E3A5F),
        tertiaryColor = GoldAccent,
        accentGlowColor = GoldBright,
        containerColor = Color(0xFFFEF3C7),
        onContainerColor = Color(0xFF78350F),
        badgeTextColor = Color(0xFF78350F),
        stationBadge = "LEADERSHIP",
        cardGradient = Brush.horizontalGradient(listOf(Navy900, Navy800)),
        headerGradient = Brush.verticalGradient(listOf(Navy900, Navy950)),
        darkHeaderGradient = Brush.verticalGradient(listOf(Navy900, Navy950)),
        icon = Icons.Default.SupervisorAccount,
        departmentTagline = "SCHOOL LEADERSHIP",
        dutyMotto = "Operational Oversight",
        quickStatLabel = "Active Students",
        accentBorderColor = GoldAccent
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
