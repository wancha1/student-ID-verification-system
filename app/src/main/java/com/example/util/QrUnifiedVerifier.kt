package com.example.util

import com.example.crypto.CardDateUtils
import com.example.crypto.TrustedIssuerRegistry
import com.example.crypto.ValidityCheckResult
import com.example.data.local.AppDatabase
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.MealRecordEntity
import com.example.data.local.ScanLogEntity
import com.example.data.sync.SyncManager
import com.example.model.AttendanceRecord
import com.example.model.AuthUser
import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.DepartmentVerificationResult
import com.example.model.ExamClearanceStatus
import com.example.model.FeeStatus
import com.example.model.GateDirection
import com.example.model.MealRecord
import com.example.model.MealServingStatus
import com.example.model.MealType
import com.example.model.ScanDepartment
import com.example.model.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Single shared scanning and verification pipeline for all departments at Lira Town College.
 *
 * Pipeline sequence:
 * 1. Scan QR
 * 2. Verify P-256 cryptographic signature
 * 3. Validate credential format and dates (institutional Uganda/Kampala timezone)
 * 4. Look up exact card ID in database (no fallback)
 * 5. Verify card status (ACTIVE) and student status (ACTIVE / not deleted)
 * 6. Authorize the signed-in staff member's requested action and department
 * 7. Apply department-specific business rules
 * 8. Return department verification result
 * 9. Persist event to Room local database and trigger sync
 */
object QrUnifiedVerifier {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun verifyAndProcessScan(
        rawQrCode: String,
        department: ScanDepartment,
        currentUser: AuthUser,
        database: AppDatabase,
        syncManager: SyncManager,
        // Context parameters for department actions:
        gateDirection: GateDirection = GateDirection.ENTRY,
        mealType: MealType = MealType.LUNCH,
        examCode: String = "UNEB-UCE-2026",
        attendanceSubject: String = "Mathematics",
        attendanceSessionName: String = "Morning Lesson 1"
    ): DepartmentVerificationResult = withContext(Dispatchers.IO) {
        val lastSync = syncManager.getLastSyncTimestamp()
        val isOffline = !syncManager.syncInfo.value.isOnline

        // Step 0: Permission Check
        if (!currentUser.hasPermission(department.requiredPermission)) {
            return@withContext DepartmentVerificationResult.PermissionDenied(
                department = department,
                requiredPermission = department.requiredPermission,
                message = "Access Denied: ${department.title} scanning requires ${department.requiredPermission.name} authorization for role ${currentUser.role.title}."
            )
        }

        // Step 1: Parse QR
        val parseResult = QrCodeUtils.parseQrCode(rawQrCode)
        if (parseResult !is QrParseResult.ValidV2Card) {
            val errorReason = when (parseResult) {
                is QrParseResult.LegacyUnsigned -> "Rejected: Legacy unsigned QR format (${parseResult.formatDescription}) is prohibited. Authenticated V2 badge required."
                is QrParseResult.Invalid -> parseResult.reason
                else -> "Unrecognized QR format."
            }
            return@withContext DepartmentVerificationResult.InvalidCredential(
                rawCode = rawQrCode,
                errorReason = errorReason
            )
        }

        // Step 2 & 3: Validate credential format and date boundaries
        when (val dateCheck = CardDateUtils.checkValidity(parseResult.validFrom, parseResult.validUntil)) {
            is ValidityCheckResult.NotYetValid -> {
                return@withContext DepartmentVerificationResult.InvalidCredential(
                    rawCode = rawQrCode,
                    errorReason = "Card '${parseResult.cardId}' is not yet valid (valid from ${dateCheck.validFrom}). Access Denied."
                )
            }
            is ValidityCheckResult.Expired -> {
                return@withContext DepartmentVerificationResult.InvalidCredential(
                    rawCode = rawQrCode,
                    errorReason = "Card '${parseResult.cardId}' expired on ${dateCheck.validUntil}. Access Denied. Please report to administration for renewal."
                )
            }
            is ValidityCheckResult.MalformedDates -> {
                return@withContext DepartmentVerificationResult.InvalidCredential(
                    rawCode = rawQrCode,
                    errorReason = "Card '${parseResult.cardId}' has invalid dates: ${dateCheck.reason}"
                )
            }
            is ValidityCheckResult.Valid -> { /* Valid range */ }
        }

        // Step 4: Cryptographically verify signature
        val isAuthentic = CardCryptoManager.verifyCardSignature(
            kid = parseResult.kid,
            cardId = parseResult.cardId,
            validFrom = parseResult.validFrom,
            validUntil = parseResult.validUntil,
            signatureBase64Url = parseResult.signature
        )
        if (!isAuthentic) {
            val isUntrustedIssuer = !TrustedIssuerRegistry.isTrusted(parseResult.kid)
            val errorReason = if (isUntrustedIssuer) {
                "Untrusted issuer key '${parseResult.kid}'. Card was issued by an unrecognized or revoked key authority."
            } else {
                "Cryptographic signature verification failed for card '${parseResult.cardId}'. Potential forgery, tampering, or invalid signature."
            }
            return@withContext DepartmentVerificationResult.InvalidCredential(
                rawCode = rawQrCode,
                errorReason = errorReason
            )
        }

        // Step 5: Look up exact card ID
        val cardEntity = database.cardDao().getCardByIdentifier(parseResult.cardId)
            ?: database.cardDao().getCardById(parseResult.cardId)
        if (cardEntity == null) {
            return@withContext DepartmentVerificationResult.StudentNotFound(
                cardId = parseResult.cardId,
                reason = "Card '${parseResult.cardId}' is cryptographically authentic but not registered in the school database."
            )
        }
        val card = cardEntity.toDomain()

        // Step 6: Verify card status
        if (card.status != CardStatus.ACTIVE) {
            val studentEntity = database.studentDao().getStudentById(card.studentId)
                ?: database.studentDao().getStudentByStudentNumber(card.studentNumber)
            val student = studentEntity?.toDomain()
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(card.deactivationDate ?: card.updatedAt))
            val reason = when (card.status) {
                CardStatus.LOST -> "Card ${card.cardIdentifier} was reported LOST on $dateStr. Access Denied."
                CardStatus.REPLACED -> "Card ${card.cardIdentifier} was REPLACED on $dateStr. Access Denied. Please present newly issued active card."
                CardStatus.DEACTIVATED -> "Card ${card.cardIdentifier} has been DEACTIVATED. Access Denied."
                CardStatus.ACTIVE -> "Card status unverified."
            }
            return@withContext DepartmentVerificationResult.CardInactive(
                student = student,
                card = card,
                cardStatus = card.status,
                reason = reason
            )
        }

        // Step 7: Verify student record
        val studentEntity = database.studentDao().getStudentById(card.studentId)
            ?: database.studentDao().getStudentByStudentNumber(card.studentNumber)
        if (studentEntity == null || studentEntity.isDeleted) {
            return@withContext DepartmentVerificationResult.StudentNotFound(
                cardId = parseResult.cardId,
                reason = "Associated student record (${card.studentNumber}) was not found or has been deactivated/deleted."
            )
        }
        val student = studentEntity.toDomain()

        val todayStr = dateFormat.format(Date())
        val now = System.currentTimeMillis()

        // Step 8: Apply Department Workflows
        when (department) {
            ScanDepartment.GATE -> {
                // Neither entry nor exit is authorized until LTC approves and configures
                // direction-aware gate policy. A valid identity is not movement permission.
                val directionLabel = gateDirection.name
                val decisionReason =
                    "Identity and active card verified. $directionLabel movement is not authorized by this app until LTC approves the gate policy."
                val logEntity = ScanLogEntity(
                    id = UUID.randomUUID().toString(),
                    studentId = student.id,
                    studentNumber = student.studentNumber,
                    studentName = student.fullName,
                    gradeClass = student.gradeClass,
                    cardId = card.id,
                    cardIdentifier = card.cardIdentifier,
                    qrPayload = card.qrPayload,
                    timestamp = now,
                    decision = "POLICY_PENDING",
                    feeStatus = student.feesStatus.name,
                    cardStatus = card.status.name,
                    isDayScholar = student.isDayScholar,
                    isApproved = false,
                    reason = decisionReason,
                    isOfflineDecision = isOffline,
                    dataSyncTimestampAtScan = lastSync,
                    guardName = currentUser.name,
                    deviceIdentifier = currentUser.station,
                    gateLocation = currentUser.station,
                    isSyncedToCloud = false
                )
                database.scanLogDao().insertScanLog(logEntity)
                syncManager.notifyLocalLogAdded()

                return@withContext DepartmentVerificationResult.Success(
                    student = student,
                    card = card,
                    department = ScanDepartment.GATE,
                    isAllowed = false,
                    statusHeadline = "$directionLabel POLICY DECISION PENDING",
                    statusDetail = "Identity and active card verified. No movement authorization was issued. Follow the school's approved procedure; this scan is recorded for audit only.",
                    isOffline = isOffline,
                    syncTimestamp = lastSync,
                    gateDirection = gateDirection,
                    isPolicyPending = true
                )
            }

            ScanDepartment.MEALS -> {
                // Meal entitlement is school policy. Do not infer it from boarding status,
                // meal type or station naming before LTC approves and configures the rules.
                return@withContext DepartmentVerificationResult.Success(
                    student = student,
                    card = card,
                    department = ScanDepartment.MEALS,
                    isAllowed = false,
                    statusHeadline = "MEAL POLICY DECISION PENDING",
                    statusDetail = "Identity and active card verified. No meal was recorded as served. Follow the school's approved manual procedure until meal eligibility rules are configured.",
                    isOffline = isOffline,
                    syncTimestamp = lastSync,
                    mealType = mealType,
                    mealStatus = MealServingStatus.POLICY_PENDING,
                    isPolicyPending = true
                )
            }

            ScanDepartment.EXAMINATIONS -> {
                // Fees, required documents and exceptions are not sufficient on their own
                // to authorize exam admission without an approved school clearance policy.
                return@withContext DepartmentVerificationResult.Success(
                    student = student,
                    card = card,
                    department = ScanDepartment.EXAMINATIONS,
                    isAllowed = false,
                    statusHeadline = "EXAM CLEARANCE POLICY PENDING",
                    statusDetail = "Identity and active card verified. No examination admission decision was issued. Refer to the approved manual clearance process until school policy is configured.",
                    isOffline = isOffline,
                    syncTimestamp = lastSync,
                    examStatus = ExamClearanceStatus.POLICY_PENDING,
                    isPolicyPending = true
                )
            }

            ScanDepartment.ATTENDANCE -> {
                // Class and session context
                // Prevent duplicate attendance for the same student, sessionName, and lessonDate
                val existingAttendance = database.attendanceRecordDao().findAttendance(
                    studentId = student.id,
                    lessonDate = todayStr,
                    sessionName = attendanceSessionName
                )
                if (existingAttendance != null) {
                    val prevTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(existingAttendance.timestamp))
                    return@withContext DepartmentVerificationResult.DuplicateAction(
                        student = student,
                        department = ScanDepartment.ATTENDANCE,
                        message = "DUPLICATE ATTENDANCE: ${student.fullName} was already marked present for $attendanceSubject ($attendanceSessionName) at $prevTime.",
                        existingEventTimestamp = existingAttendance.timestamp
                    )
                }

                val attId = UUID.randomUUID().toString()
                val record = AttendanceRecord(
                    id = attId,
                    studentId = student.id,
                    studentNumber = student.studentNumber,
                    studentName = student.fullName,
                    gradeClass = student.gradeClass,
                    subject = attendanceSubject,
                    sessionName = attendanceSessionName,
                    lessonDate = todayStr,
                    timestamp = now,
                    teacherName = currentUser.name,
                    isPresent = true,
                    notes = "Marked via QR Badge"
                )
                database.attendanceRecordDao().insertOrUpdate(AttendanceRecordEntity.fromDomain(record, isSynced = false))
                syncManager.notifyLocalLogAdded()

                return@withContext DepartmentVerificationResult.Success(
                    student = student,
                    card = card,
                    department = ScanDepartment.ATTENDANCE,
                    isAllowed = true,
                    statusHeadline = "Present in Class",
                    statusDetail = "${student.fullName} marked present for $attendanceSubject - $attendanceSessionName.",
                    isOffline = isOffline,
                    syncTimestamp = lastSync,
                    attendanceSubject = attendanceSubject,
                    attendanceSession = attendanceSessionName
                )
            }
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
