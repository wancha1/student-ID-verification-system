package com.example.model

/**
 * Granular permissions for staff operations in the LTC Student QR Identity System.
 * Enforces least-privilege role-based access control.
 */
enum class StaffPermission {
    MANAGE_STUDENTS,       // Register, update, delete student records; issue/revoke cards
    MANAGE_FEES,           // Update fee clearance status and outstanding balances (Bursar)
    VERIFY_GATE_ACCESS,    // QR barcode gate scanning and day scholar pass checks
    SERVE_MEALS,           // Dining hall meal issuance and double-serving prevention
    EXAM_CLEARANCE,        // Requirements / examination hall clearance checklists
    EMERGENCY_OVERRIDE,    // Supervisor authorization for rejected access
    VIEW_AUDIT_LOGS,       // View gate logs, attendance records, audit trail
    EXPORT_DATA,           // Export reports in Excel, Word, CSV, JSON
    SYSTEM_CONFIGURATION   // Manage security PINs, staff provisioning, system setup
}

/**
 * The 7 canonical staff duty roles at Lira Town College.
 */
enum class UserRole(
    val title: String,
    val subtitle: String,
    val defaultUsername: String,
    val badge: String
) {
    ADMINISTRATOR(
        title = "Administrator",
        subtitle = "Principal's Office & Registry Administration",
        defaultUsername = "System Administrator",
        badge = "ADMIN"
    ),
    BURSAR_FINANCE(
        title = "Bursar / Finance",
        subtitle = "Bursar & Accounts Office • School Fees",
        defaultUsername = "Bursar Officer",
        badge = "FINANCE"
    ),
    GATE_STAFF(
        title = "Gate Staff",
        subtitle = "Gate 1 - Main Campus Access & Security",
        defaultUsername = "Gate Security Officer",
        badge = "GATE ACCESS"
    ),
    MEAL_SERVING_STAFF(
        title = "Meal-Serving Staff",
        subtitle = "Main Dining Hall & Cafeteria Food Service",
        defaultUsername = "Catering Staff",
        badge = "MEALS"
    ),
    TEACHERS(
        title = "Teachers",
        subtitle = "Academics & Classroom Roll-Call Roster",
        defaultUsername = "Teacher on Duty",
        badge = "TEACHER"
    ),
    EXAMINATION_STAFF(
        title = "Examination Staff",
        subtitle = "Examination Hall Entry & Requirements Clearance",
        defaultUsername = "Examination Officer",
        badge = "EXAMS"
    ),
    HEAD_TEACHER_MANAGEMENT(
        title = "Head Teacher / Management",
        subtitle = "Head Teacher & Board Executive Oversight",
        defaultUsername = "Head Teacher",
        badge = "MANAGEMENT"
    );

    /**
     * Default permissions assigned to this duty role.
     * NOTE: Per least-privilege requirements, ADMINISTRATOR does NOT automatically receive
     * MANAGE_FEES unless finance permission is explicitly granted.
     */
    val defaultPermissions: Set<StaffPermission>
        get() = when (this) {
            ADMINISTRATOR -> setOf(
                StaffPermission.MANAGE_STUDENTS,
                StaffPermission.VERIFY_GATE_ACCESS,
                StaffPermission.VIEW_AUDIT_LOGS,
                StaffPermission.EXPORT_DATA,
                StaffPermission.SYSTEM_CONFIGURATION
            )
            BURSAR_FINANCE -> setOf(
                StaffPermission.MANAGE_FEES,
                StaffPermission.VIEW_AUDIT_LOGS,
                StaffPermission.EXPORT_DATA
            )
            GATE_STAFF -> setOf(
                StaffPermission.VERIFY_GATE_ACCESS,
                StaffPermission.VIEW_AUDIT_LOGS
            )
            MEAL_SERVING_STAFF -> setOf(
                StaffPermission.SERVE_MEALS,
                StaffPermission.VIEW_AUDIT_LOGS
            )
            TEACHERS -> setOf(
                StaffPermission.VIEW_AUDIT_LOGS
            )
            EXAMINATION_STAFF -> setOf(
                StaffPermission.EXAM_CLEARANCE,
                StaffPermission.VIEW_AUDIT_LOGS
            )
            HEAD_TEACHER_MANAGEMENT -> setOf(
                StaffPermission.VIEW_AUDIT_LOGS,
                StaffPermission.EMERGENCY_OVERRIDE,
                StaffPermission.EXPORT_DATA
            )
        }

    companion object {
        // Backward-compatible aliases for legacy references
        val GATE_KEEPER get() = GATE_STAFF
        val SECURITY_GUARD get() = GATE_STAFF
        val ADMIN get() = ADMINISTRATOR
        val REQUIREMENTS_MASTER get() = EXAMINATION_STAFF
        val MEALS_MASTER get() = MEAL_SERVING_STAFF
    }
}

/**
 * Authenticated staff session representation.
 */
data class AuthUser(
    val role: UserRole,
    val name: String,
    val station: String,
    val permissions: Set<StaffPermission> = role.defaultPermissions,
    val hasExplicitFinanceAccess: Boolean = false
) {
    /**
     * Verifies if this staff user has the requested permission.
     * Administrator only has MANAGE_FEES if hasExplicitFinanceAccess is true.
     */
    fun hasPermission(permission: StaffPermission): Boolean {
        if (permission == StaffPermission.MANAGE_FEES && hasExplicitFinanceAccess) {
            return true
        }
        return permissions.contains(permission)
    }
}

