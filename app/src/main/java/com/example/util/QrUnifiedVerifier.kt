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
                // Direction-based event
                val directionLabel = if (gateDirection == GateDirection.ENTRY) "ENTRY" else "EXIT"
                val eventId = UUID.randomUUID().toString()
                val logEntity = ScanLogEntity(
                    id = eventId,
                    studentId = student.id,
                    studentNumber = student.studentNumber,
                    studentName = student.fullName,
                    gradeClass = student.gradeClass,
                    cardId = card.id,
                    cardIdentifier = card.cardIdentifier,
                    qrPayload = card.qrPayload,
                    timestamp = now,
                    decision = "APPROVED",
                    feeStatus = student.feesStatus.name,
                    cardStatus = card.status.name,
                    isDayScholar = student.isDayScholar,
                    isApproved = true,
                    reason = "Gate $directionLabel authorized for ${student.fullName} (${if (student.isDayScholar) "Day Scholar" else "Boarder"})",
                    isOfflineDecision = isOffline,
                    dataSyncTimestampAtScan = lastSync,
                    guardName = currentUser.name,
                    deviceIdentifier = currentUser.station,
                    gateLocation = "Gate 1 (Main Entrance)",
                    isSyncedToCloud = false
                )
                database.scanLogDao().insertScanLog(logEntity)
                syncManager.notifyLocalLogAdded()

                return@withContext DepartmentVerificationResult.Success(
                    student = student,
                    card = card,
                    department = ScanDepartment.GATE,
                    isAllowed = true,
                    statusHeadline = "Gate $directionLabel Cleared",
                    statusDetail = "Student ${student.fullName} (${student.gradeClass}) recorded for $directionLabel at ${currentUser.station}.",
                    isOffline = isOffline,
                    syncTimestamp = lastSync,
                    gateDirection = gateDirection
                )
            }

            ScanDepartment.MEALS -> {
                // Rule 1: Day scholars are eligible for LUNCH only; boarders for LUNCH and DINNER
                val isEligible = if (student.isDayScholar) {
                    mealType == MealType.LUNCH
                } else {
                    mealType == MealType.LUNCH || mealType == MealType.DINNER || mealType == MealType.BREAKFAST || mealType == MealType.SPECIAL_SNACK
                }

                if (!isEligible) {
                    val msg = if (student.isDayScholar) {
                        "Ineligible: Day scholar students are entitled to Lunch only. Not eligible for ${mealType.displayName}."
                    } else {
                        "Ineligible for ${mealType.displayName}."
                    }
                    return@withContext DepartmentVerificationResult.Success(
                        student = student,
                        card = card,
                        department = ScanDepartment.MEALS,
                        isAllowed = false,
                        statusHeadline = "Meal Serving Ineligible",
                        statusDetail = msg,
                        isOffline = isOffline,
                        syncTimestamp = lastSync,
                        mealType = mealType,
                        mealStatus = MealServingStatus.BLOCKED_NO_MEAL_CLEARANCE
                    )
                }

                // Rule 2: Prevent duplicate serving for same student, meal period, and service date
                val existingServing = database.mealRecordDao().findServing(student.id, mealType.name, todayStr)
                if (existingServing != null) {
                    val prevTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(existingServing.timestamp))
                    return@withContext DepartmentVerificationResult.DuplicateAction(
                        student = student,
                        department = ScanDepartment.MEALS,
                        message = "DUPLICATE MEAL CLAIM: ${student.fullName} was already served ${mealType.displayName} today ($todayStr) at $prevTime by ${existingServing.serverName}.",
                        existingEventTimestamp = existingServing.timestamp
                    )
                }

                // Record meal serving event
                val mealId = UUID.randomUUID().toString()
                val mealRecord = MealRecord(
                    id = mealId,
                    studentId = student.id,
                    studentNumber = student.studentNumber,
                    studentName = student.fullName,
                    gradeClass = student.gradeClass,
                    mealType = mealType,
                    mealDate = todayStr,
                    timestamp = now,
                    serverName = currentUser.name,
                    status = MealServingStatus.SERVED,
                    notes = if (student.isDayScholar) "Day Scholar" else "Boarding Student"
                )
                database.mealRecordDao().insertOrUpdate(MealRecordEntity.fromDomain(mealRecord, isSynced = false))
                syncManager.notifyLocalLogAdded()

                return@withContext DepartmentVerificationResult.Success(
                    student = student,
                    card = card,
                    department = ScanDepartment.MEALS,
                    isAllowed = true,
                    statusHeadline = "${mealType.displayName} Served",
                    statusDetail = "${mealType.displayName} served to ${student.fullName} (${student.gradeClass}).",
                    isOffline = isOffline,
                    syncTimestamp = lastSync,
                    mealType = mealType,
                    mealStatus = MealServingStatus.SERVED
                )
            }

            ScanDepartment.EXAMINATIONS -> {
                // School exam clearance policy:
                // Full clearance requires Fees status CLEARED.
                // If OUTSTANDING, check if an authorized exception has been approved by Head Teacher.
                val existingException = database.examExceptionDao().findException(student.id, examCode)
                val isExceptionApproved = existingException?.isApproved == true

                val (isAllowed, examStatus, headline, detail) = when {
                    student.feesStatus == FeeStatus.CLEARED -> {
                        Tuple4(
                            true,
                            ExamClearanceStatus.CLEARED_FULL,
                            "Exam Hall Cleared (Financial Clearance)",
                            "Student has full financial clearance for $examCode."
                        )
                    }
                    isExceptionApproved -> {
                        Tuple4(
                            true,
                            ExamClearanceStatus.CLEARED_SPECIAL_EXCEPTION,
                            "Exam Hall Cleared (Special Exception)",
                            "Approved special exception by ${existingException?.approvedByHeadTeacher ?: "Head Teacher"} for $examCode."
                        )
                    }
                    existingException != null && !existingException.isApproved -> {
                        Tuple4(
                            false,
                            ExamClearanceStatus.PENDING_BURSAR_RECOMMENDED,
                            "Exam Clearance Pending Approval",
                            "Bursar recommended exception (${existingException.recommendationReason}), awaiting Head Teacher authorization."
                        )
                    }
                    else -> {
                        Tuple4(
                            false,
                            ExamClearanceStatus.BLOCKED_FEES,
                            "Exam Clearance Blocked (Fees Outstanding)",
                            "Outstanding fee balance. Bursar exception recommendation required."
                        )
                    }
                }

                return@withContext DepartmentVerificationResult.Success(
                    student = student,
                    card = card,
                    department = ScanDepartment.EXAMINATIONS,
                    isAllowed = isAllowed,
                    statusHeadline = headline,
                    statusDetail = detail,
                    isOffline = isOffline,
                    syncTimestamp = lastSync,
                    examStatus = examStatus
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
