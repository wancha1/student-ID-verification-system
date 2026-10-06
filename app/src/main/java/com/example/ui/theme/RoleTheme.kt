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
 * Crafted with luxury gradients, ambient glow tints, and distinctive typography headers.
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
            surface = SurfaceLight,
            onSurface = TextPrimaryLight,
            surfaceVariant = SurfaceVariantLight,
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
            surface = SurfaceDark,
            onSurface = TextPrimaryDark,
            surfaceVariant = SurfaceVariantDark,
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
        primaryColor = Color(0xFFD97706),       // Warm Gold
        secondaryColor = Color(0xFFB45309),     // Deep Amber Bronze
        tertiaryColor = Color(0xFF1E293B),      // Slate Obsidian
        accentGlowColor = Color(0xFFFBBF24),    // Radiant Gold
        containerColor = Color(0xFFFEF3C7),     // Pale Champagne
        onContainerColor = Color(0xFF78350F),
        badgeTextColor = Color(0xFF78350F),
        stationBadge = "REGISTRY HQ",
        cardGradient = Brush.horizontalGradient(
            colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF291B07))
        ),
        headerGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF291B07), Color(0xFF111827))
        ),
        darkHeaderGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF3B2506), Color(0xFF0A0F1D))
        ),
        icon = Icons.Default.AdminPanelSettings,
        departmentTagline = "REGISTRY & GOVERNANCE TERMINAL",
        dutyMotto = "Official Student Roster • ID Badge Authority • Security Provisioning",
        quickStatLabel = "Total Enrolled",
        accentBorderColor = Color(0xFFF59E0B)
    )

    // 2. Bursar / Finance: Emerald Teal & Mint Cashmere
    val BursarFinanceTheme = RoleThemeConfig(
        role = UserRole.BURSAR_FINANCE,
        primaryColor = Color(0xFF0D9488),       // Emerald Teal
        secondaryColor = Color(0xFF047857),     // Malachite Green
        tertiaryColor = Color(0xFF065F46),      // Deep Forest
        accentGlowColor = Color(0xFF2DD4BF),    // Luminous Mint
        containerColor = Color(0xFFCCFBF1),     // Pale Mint Mist
        onContainerColor = Color(0xFF115E59),
        badgeTextColor = Color(0xFF065F46),
        stationBadge = "BURSARY DESK",
        cardGradient = Brush.horizontalGradient(
            colors = listOf(Color(0xFF064E3B), Color(0xFF0F766E), Color(0xFF115E59))
        ),
        headerGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF042F2E), Color(0xFF0F172A))
        ),
        darkHeaderGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF032B2A), Color(0xFF06181B))
        ),
        icon = Icons.Default.AccountBalance,
        departmentTagline = "BURSARY & FISCAL CLEARANCE",
        dutyMotto = "Tuition Roster • Ledger Clearance • Revenue Accounting",
        quickStatLabel = "Fees Cleared",
        accentBorderColor = Color(0xFF14B8A6)
    )

    // 3. Gate Staff: Tactical Sapphire & Electric Cobalt
    val GateStaffTheme = RoleThemeConfig(
        role = UserRole.GATE_STAFF,
        primaryColor = Color(0xFF1D4ED8),       // Royal Guard Cobalt
        secondaryColor = Color(0xFF1E3A8A),     // Deep Navy
        tertiaryColor = Color(0xFF0284C7),      // Precision Azure
        accentGlowColor = Color(0xFF60A5FA),    // Electric Azure
        containerColor = Color(0xFFDBEAFE),     // Pale Ice Blue
        onContainerColor = Color(0xFF1E40AF),
        badgeTextColor = Color(0xFF1E3A8A),
        stationBadge = "GATE 1 TURNSTILE",
        cardGradient = Brush.horizontalGradient(
            colors = listOf(Color(0xFF172554), Color(0xFF1E3A8A), Color(0xFF1D4ED8))
        ),
        headerGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF1E3A8A))
        ),
        darkHeaderGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF0A1329), Color(0xFF090E1A))
        ),
        icon = Icons.Default.Security,
        departmentTagline = "PERIMETER & ACCESS CONTROL",
        dutyMotto = "Gate 1 Main Turnstile • QR Telemetry • Boarding & Day Scholar Control",
        quickStatLabel = "Verified Today",
        accentBorderColor = Color(0xFF3B82F6)
    )

    // 4. Meal-Serving Staff: Culinary Ruby & Warm Amber
    val MealServingStaffTheme = RoleThemeConfig(
        role = UserRole.MEAL_SERVING_STAFF,
        primaryColor = Color(0xFFE11D48),       // Dining Ruby
        secondaryColor = Color(0xFFBE123C),     // Gourmet Crimson
        tertiaryColor = Color(0xFFF97316),      // Warm Amber
        accentGlowColor = Color(0xFFFB7185),    // Coral Glow
        containerColor = Color(0xFFFFE4E6),     // Soft Blossom
        onContainerColor = Color(0xFF9F1239),
        badgeTextColor = Color(0xFF881337),
        stationBadge = "DINING HALL",
        cardGradient = Brush.horizontalGradient(
            colors = listOf(Color(0xFF4C0519), Color(0xFF881337), Color(0xFF9F1239))
        ),
        headerGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF4C0519), Color(0xFF1E1B4B))
        ),
        darkHeaderGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF3B0313), Color(0xFF120817))
        ),
        icon = Icons.Default.Restaurant,
        departmentTagline = "DINING HALL & NUTRITION SERVICES",
        dutyMotto = "Dining Hall Turnstile • Meal Session Verification • Anti-Double Serving",
        quickStatLabel = "Meals Served",
        accentBorderColor = Color(0xFFF43F5E)
    )

    // 5. Teachers: Scholarly Indigo & Royal Iris
    val TeachersTheme = RoleThemeConfig(
        role = UserRole.TEACHERS,
        primaryColor = Color(0xFF4F46E5),       // Academic Indigo
        secondaryColor = Color(0xFF4338CA),     // Scholastic Iris
        tertiaryColor = Color(0xFF6366F1),      // Radiant Violet
        accentGlowColor = Color(0xFF818CF8),    // Radiant Lavender
        containerColor = Color(0xFFE0E7FF),     // Pale Periwinkle
        onContainerColor = Color(0xFF3730A3),
        badgeTextColor = Color(0xFF312E81),
        stationBadge = "FACULTY PORTAL",
        cardGradient = Brush.horizontalGradient(
            colors = listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF3730A3))
        ),
        headerGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
        ),
        darkHeaderGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF18153B), Color(0xFF0B0D18))
        ),
        icon = Icons.Default.MenuBook,
        departmentTagline = "ACADEMICS & CLASSROOM ROSTER",
        dutyMotto = "Class Roll-Call • Attendance Verification • Student Directories",
        quickStatLabel = "Class Roster",
        accentBorderColor = Color(0xFF6366F1)
    )

    // 6. Examination Staff: Precision Cyan & Arctic Steel
    val ExaminationStaffTheme = RoleThemeConfig(
        role = UserRole.EXAMINATION_STAFF,
        primaryColor = Color(0xFF0284C7),       // Exams Cyan
        secondaryColor = Color(0xFF0369A1),     // Steel Azure
        tertiaryColor = Color(0xFF0891B2),      // Deep Cyan
        accentGlowColor = Color(0xFF38BDF8),    // Arctic Glow
        containerColor = Color(0xFFE0F2FE),     // Pale Sky
        onContainerColor = Color(0xFF075985),
        badgeTextColor = Color(0xFF0C4A6E),
        stationBadge = "EXAM CONTROL",
        cardGradient = Brush.horizontalGradient(
            colors = listOf(Color(0xFF082F49), Color(0xFF0C4A6E), Color(0xFF0369A1))
        ),
        headerGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF082F49), Color(0xFF0F172A))
        ),
        darkHeaderGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF051F30), Color(0xFF07111C))
        ),
        icon = Icons.Default.FactCheck,
        departmentTagline = "STANDARDS & EXAM CLEARANCE",
        dutyMotto = "UNEB Examination Hall Access • Requirements Compliance • Candidate Slips",
        quickStatLabel = "Exam Cleared",
        accentBorderColor = Color(0xFF0EA5E9)
    )

    // 7. Head Teacher / Management: Sovereign Purple & Radiant Platinum
    val HeadTeacherManagementTheme = RoleThemeConfig(
        role = UserRole.HEAD_TEACHER_MANAGEMENT,
        primaryColor = Color(0xFF7C3AED),       // Sovereign Purple
        secondaryColor = Color(0xFF6D28D9),     // Majestic Violet
        tertiaryColor = Color(0xFF9333EA),      // Imperial Amethyst
        accentGlowColor = Color(0xFFA78BFA),    // Regal Amethyst
        containerColor = Color(0xFFEDE9FE),     // Platinum Lilac
        onContainerColor = Color(0xFF5B21B6),
        badgeTextColor = Color(0xFF4C1D95),
        stationBadge = "EXECUTIVE SUITE",
        cardGradient = Brush.horizontalGradient(
            colors = listOf(Color(0xFF2E1065), Color(0xFF4C1D95), Color(0xFF5B21B6))
        ),
        headerGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF2E1065), Color(0xFF0F172A))
        ),
        darkHeaderGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF230D4F), Color(0xFF0A0717))
        ),
        icon = Icons.Default.SupervisorAccount,
        departmentTagline = "EXECUTIVE OVERSIGHT & BOARD COMMAND",
        dutyMotto = "Institutional Governance • Emergency Override Authority • Campus Intelligence",
        quickStatLabel = "Active Students",
        accentBorderColor = Color(0xFF8B5CF6)
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
