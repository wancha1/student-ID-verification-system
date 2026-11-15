package com.example.model

/**
 * The four department scan workflows supported by LTC At The Centre, plus Admin oversight.
 */
enum class ScanDepartment(
    val title: String,
    val subtitle: String,
    val requiredPermission: StaffPermission,
    val badge: String
) {
    GATE(
        title = "Gate Access",
        subtitle = "Main Campus Gate • Entry & Exit Control",
        requiredPermission = StaffPermission.VERIFY_GATE_ACCESS,
        badge = "GATE"
    ),
    MEALS(
        title = "Dining Hall Meals",
        subtitle = "Food Service • Lunch & Supper Distribution",
        requiredPermission = StaffPermission.RECORD_MEALS,
        badge = "MEALS"
    ),
    EXAMINATIONS(
        title = "Examination Hall",
        subtitle = "Hall Entry Clearance • Policy & Authorized Exceptions",
        requiredPermission = StaffPermission.MANAGE_EXAM_CLEARANCE,
        badge = "EXAMS"
    ),
    ATTENDANCE(
        title = "Classroom Attendance",
        subtitle = "Class Roster • Subject Lesson Roll-Call",
        requiredPermission = StaffPermission.MARK_ATTENDANCE,
        badge = "ATTENDANCE"
    );

    companion object {
        fun defaultForRole(role: UserRole): ScanDepartment = when (role) {
            UserRole.GATE_STAFF -> GATE
            UserRole.MEAL_SERVING_STAFF -> MEALS
            UserRole.EXAMINATION_STAFF -> EXAMINATIONS
            UserRole.TEACHERS -> ATTENDANCE
            UserRole.BURSAR_FINANCE -> GATE
            UserRole.HEAD_TEACHER_MANAGEMENT -> GATE
            UserRole.ADMINISTRATOR -> GATE
        }
    }
}

/**
 * Gate movement direction.
 */
enum class GateDirection(val label: String) {
    ENTRY("Entry into School"),
    EXIT("Exit out of School")
}

/**
 * Examination clearance status according to configured policy.
 */
enum class ExamClearanceStatus {
    CLEARED_FULL,               // Meets school fee and requirements policy
    CLEARED_SPECIAL_EXCEPTION,  // Approved exception by authorized Head Teacher
    PENDING_BURSAR_RECOMMENDED, // Bursar recommended exception, awaiting Head Teacher approval
    BLOCKED_FEES,               // Outstanding balance without approved exception
    BLOCKED_REQUIREMENTS        // Mandatory items missing
}

/**
 * Unified verification result produced by the shared cryptographic QR pipeline.
 */
sealed class DepartmentVerificationResult {
    data class Success(
        val student: Student,
        val card: Card?,
        val department: ScanDepartment,
        val isAllowed: Boolean,
        val statusHeadline: String,
        val statusDetail: String,
        val isOffline: Boolean,
        val syncTimestamp: Long,
        // Department-specific payloads:
        val gateDirection: GateDirection? = null,
        val mealType: MealType? = null,
        val mealStatus: MealServingStatus? = null,
        val examStatus: ExamClearanceStatus? = null,
        val attendanceSubject: String? = null,
        val attendanceSession: String? = null
    ) : DepartmentVerificationResult()

    data class PermissionDenied(
        val department: ScanDepartment,
        val requiredPermission: StaffPermission,
        val message: String
    ) : DepartmentVerificationResult()

    data class DuplicateAction(
        val student: Student,
        val department: ScanDepartment,
        val message: String,
        val existingEventTimestamp: Long? = null
    ) : DepartmentVerificationResult()

    data class CardInactive(
        val student: Student?,
        val card: Card,
        val cardStatus: CardStatus,
        val reason: String
    ) : DepartmentVerificationResult()

    data class StudentNotFound(
        val cardId: String,
        val reason: String
    ) : DepartmentVerificationResult()

    data class InvalidCredential(
        val rawCode: String,
        val errorReason: String
    ) : DepartmentVerificationResult()
}
