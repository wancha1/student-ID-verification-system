package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.SoftwareCardSigner
import com.example.crypto.TrustedIssuerRegistry
import com.example.data.RoomStudentRepository
import com.example.data.local.AppDatabase
import com.example.data.local.CardEntity
import com.example.data.local.StudentEntity
import com.example.data.sync.InMemoryCloudBackend
import com.example.data.sync.SyncManager
import com.example.model.AccessStatus
import com.example.model.AuthUser
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.DepartmentVerificationResult
import com.example.model.ExamClearanceStatus
import com.example.model.FeeStatus
import com.example.model.GateDirection
import com.example.model.MealServingStatus
import com.example.model.MealType
import com.example.model.ScanDepartment
import com.example.model.StaffPermission
import com.example.model.UserRole
import com.example.testutil.TestCardCryptoHelper
import com.example.util.CardCryptoManager
import com.example.util.QrUnifiedVerifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiDepartmentScanningTest {

    private lateinit var database: AppDatabase
    private lateinit var syncManager: SyncManager
    private lateinit var testKeyPair: KeyPair
    private lateinit var testSigner: SoftwareCardSigner

    private val dayScholarStudentId = "STU-DAY-001"
    private val boarderStudentId = "STU-BRD-002"
    private val dayScholarCardId = "CRD-DAY-001"
    private val boarderCardId = "CRD-BRD-002"

    private lateinit var dayScholarQrPayload: String
    private lateinit var boarderQrPayload: String

    private val validFrom = "2026-01-01"
    private val validUntil = "2026-12-31"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TrustedIssuerRegistry.initialize(context)
        TrustedIssuerRegistry.clearAll()
        CardCryptoManager.resetForTesting()

        database = AppDatabase.createInMemory(context)
        syncManager = SyncManager(database, InMemoryCloudBackend())

        testKeyPair = TestCardCryptoHelper.generateKeyPair()
        testSigner = SoftwareCardSigner(testKeyPair)
        CardCryptoManager.setActiveSigner(testSigner)
        TrustedIssuerRegistry.registerTrustedKey(testSigner.publicKey, "Primary Test Issuer Authority")

        val now = System.currentTimeMillis()

        runBlocking {
            val dayStudent = StudentEntity(
                id = dayScholarStudentId,
                studentNumber = "LTC-2026-0001",
                name = "Daniel Okello",
                firstName = "Daniel",
                lastName = "Okello",
                photoUrl = null,
                accessStatus = AccessStatus.APPROVED.name,
                gradeClass = "Senior 4-A",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK.name,
                transportRoute = "Main Gate Line",
                feesStatus = FeeStatus.CLEARED.name,
                outstandingAmount = 0.0,
                gender = "Male",
                avatarColorSeed = 100L,
                guardianName = "John Okello",
                guardianPhone = "+256 772 111222",
                emergencyContact = "+256 772 111222",
                homeroomTeacher = "Mr. Obua",
                academicYear = "2026",
                notes = "Day scholar student",
                qrToken = "TOK-DAY-1",
                updatedAt = now,
                isDeleted = false
            )
            database.studentDao().insertOrUpdateStudent(dayStudent)

            val boarderStudent = StudentEntity(
                id = boarderStudentId,
                studentNumber = "LTC-2026-0002",
                name = "Sarah Achieng",
                firstName = "Sarah",
                lastName = "Achieng",
                photoUrl = null,
                accessStatus = AccessStatus.APPROVED.name,
                gradeClass = "Senior 5-Science",
                isDayScholar = false,
                dayScholarType = DayScholarStatus.BOARDER.name,
                transportRoute = "Boarding Wing",
                feesStatus = FeeStatus.OUTSTANDING.name,
                outstandingAmount = 250000.0,
                gender = "Female",
                avatarColorSeed = 200L,
                guardianName = "Grace Achieng",
                guardianPhone = "+256 772 333444",
                emergencyContact = "+256 772 333444",
                homeroomTeacher = "Ms. Akello",
                academicYear = "2026",
                notes = "Boarding student",
                qrToken = "TOK-BRD-2",
                updatedAt = now,
                isDeleted = false
            )
            database.studentDao().insertOrUpdateStudent(boarderStudent)

            dayScholarQrPayload = CardCryptoManager.signCardPayload(
                cardId = dayScholarCardId,
                validFrom = validFrom,
                validUntil = validUntil,
                signer = testSigner
            )

            boarderQrPayload = CardCryptoManager.signCardPayload(
                cardId = boarderCardId,
                validFrom = validFrom,
                validUntil = validUntil,
                signer = testSigner
            )

            val c1 = CardEntity(
                id = "crd-db-001",
                cardIdentifier = dayScholarCardId,
                studentId = dayScholarStudentId,
                studentNumber = "LTC-2026-0001",
                qrPayload = dayScholarQrPayload,
                status = CardStatus.ACTIVE.name,
                issueDate = now,
                activationDate = now,
                deactivationDate = null,
                replacedByCardId = null,
                reason = "Primary card",
                notes = "Day scholar card",
                updatedAt = now,
                isDeleted = false
            )
            database.cardDao().insertOrUpdateCard(c1)

            val c2 = CardEntity(
                id = "crd-db-002",
                cardIdentifier = boarderCardId,
                studentId = boarderStudentId,
                studentNumber = "LTC-2026-0002",
                qrPayload = boarderQrPayload,
                status = CardStatus.ACTIVE.name,
                issueDate = now,
                activationDate = now,
                deactivationDate = null,
                replacedByCardId = null,
                reason = "Primary card",
                notes = "Boarder card",
                updatedAt = now,
                isDeleted = false
            )
            database.cardDao().insertOrUpdateCard(c2)
        }
    }

    @After
    fun tearDown() {
        database.close()
        CardCryptoManager.resetForTesting()
        TrustedIssuerRegistry.clearAll()
    }

    @Test
    fun test01_GateWorkflow_EntryAndExitRecording() = runBlocking {
        val gateGuard = AuthUser(
            role = UserRole.GATE_STAFF,
            name = "Officer James",
            station = "Main Gate 1"
        )

        // 1. Entry scan
        val entryResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.GATE,
            currentUser = gateGuard,
            database = database,
            syncManager = syncManager,
            gateDirection = GateDirection.ENTRY
        )

        assertTrue(entryResult is DepartmentVerificationResult.Success)
        val successEntry = entryResult as DepartmentVerificationResult.Success
        assertTrue(successEntry.isAllowed)
        assertEquals(GateDirection.ENTRY, successEntry.gateDirection)

        // 2. Exit scan
        val exitResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.GATE,
            currentUser = gateGuard,
            database = database,
            syncManager = syncManager,
            gateDirection = GateDirection.EXIT
        )

        assertTrue(exitResult is DepartmentVerificationResult.Success)
        val successExit = exitResult as DepartmentVerificationResult.Success
        assertTrue(successExit.isAllowed)
        assertEquals(GateDirection.EXIT, successExit.gateDirection)

        // 3. Verify logs in database
        val logs = database.scanLogDao().getAllLogsFlow().first()
        assertEquals(2, logs.size)
    }

    @Test
    fun test02_MealsWorkflow_DayScholarLunchOnly_BoarderLunchAndSupper() = runBlocking {
        val mealStaff = AuthUser(
            role = UserRole.MEAL_SERVING_STAFF,
            name = "Chef Mary",
            station = "Main Dining Hall"
        )

        // Day scholar: LUNCH is eligible
        val dayLunchResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.MEALS,
            currentUser = mealStaff,
            database = database,
            syncManager = syncManager,
            mealType = MealType.LUNCH
        )
        assertTrue(dayLunchResult is DepartmentVerificationResult.Success)
        assertTrue((dayLunchResult as DepartmentVerificationResult.Success).isAllowed)

        // Day scholar: DINNER (Supper) is NOT eligible
        val dayDinnerResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.MEALS,
            currentUser = mealStaff,
            database = database,
            syncManager = syncManager,
            mealType = MealType.DINNER
        )
        assertTrue(dayDinnerResult is DepartmentVerificationResult.Success)
        assertFalse((dayDinnerResult as DepartmentVerificationResult.Success).isAllowed)
        assertEquals(MealServingStatus.BLOCKED_NO_MEAL_CLEARANCE, dayDinnerResult.mealStatus)

        // Boarder: LUNCH and DINNER both eligible
        val boarderLunch = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = boarderQrPayload,
            department = ScanDepartment.MEALS,
            currentUser = mealStaff,
            database = database,
            syncManager = syncManager,
            mealType = MealType.LUNCH
        )
        assertTrue(boarderLunch is DepartmentVerificationResult.Success)
        assertTrue((boarderLunch as DepartmentVerificationResult.Success).isAllowed)

        val boarderDinner = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = boarderQrPayload,
            department = ScanDepartment.MEALS,
            currentUser = mealStaff,
            database = database,
            syncManager = syncManager,
            mealType = MealType.DINNER
        )
        assertTrue(boarderDinner is DepartmentVerificationResult.Success)
        assertTrue((boarderDinner as DepartmentVerificationResult.Success).isAllowed)
    }

    @Test
    fun test03_MealsWorkflow_DuplicateMealPrevention() = runBlocking {
        val mealStaff = AuthUser(
            role = UserRole.MEAL_SERVING_STAFF,
            name = "Chef Mary",
            station = "Main Dining Hall"
        )

        // First serving
        val firstServe = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.MEALS,
            currentUser = mealStaff,
            database = database,
            syncManager = syncManager,
            mealType = MealType.LUNCH
        )
        assertTrue(firstServe is DepartmentVerificationResult.Success)

        // Immediate duplicate attempt for same meal & date
        val duplicateServe = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.MEALS,
            currentUser = mealStaff,
            database = database,
            syncManager = syncManager,
            mealType = MealType.LUNCH
        )
        assertTrue("Duplicate meal must be blocked", duplicateServe is DepartmentVerificationResult.DuplicateAction)
    }

    @Test
    fun test04_ClassroomAttendance_RollCallAndDuplicatePrevention() = runBlocking {
        val teacher = AuthUser(
            role = UserRole.TEACHERS,
            name = "Tr. Paul Okot",
            station = "Senior 4 Classroom"
        )

        // Mark present in Lesson 1
        val att1 = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.ATTENDANCE,
            currentUser = teacher,
            database = database,
            syncManager = syncManager,
            attendanceSubject = "Mathematics",
            attendanceSessionName = "Morning Lesson 1"
        )
        assertTrue(att1 is DepartmentVerificationResult.Success)
        assertTrue((att1 as DepartmentVerificationResult.Success).isAllowed)

        // Duplicate scan in the same session and lesson date
        val attDup = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.ATTENDANCE,
            currentUser = teacher,
            database = database,
            syncManager = syncManager,
            attendanceSubject = "Mathematics",
            attendanceSessionName = "Morning Lesson 1"
        )
        assertTrue("Duplicate attendance must be blocked", attDup is DepartmentVerificationResult.DuplicateAction)

        // Distinct session on the same day is permitted
        val att2 = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.ATTENDANCE,
            currentUser = teacher,
            database = database,
            syncManager = syncManager,
            attendanceSubject = "Physics",
            attendanceSessionName = "Afternoon Lesson 4"
        )
        assertTrue(att2 is DepartmentVerificationResult.Success)
        assertTrue((att2 as DepartmentVerificationResult.Success).isAllowed)
    }

    @Test
    fun test05_Examinations_ClearanceAndAuthorizedExceptionApproval() = runBlocking {
        val examOfficer = AuthUser(
            role = UserRole.EXAMINATION_STAFF,
            name = "Invigilator Jane",
            station = "Main Hall"
        )

        // 1. Cleared student is allowed immediately
        val clearedStudentResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.EXAMINATIONS,
            currentUser = examOfficer,
            database = database,
            syncManager = syncManager,
            examCode = "UNEB-UCE-2026"
        )
        assertTrue(clearedStudentResult is DepartmentVerificationResult.Success)
        assertEquals(ExamClearanceStatus.CLEARED_FULL, (clearedStudentResult as DepartmentVerificationResult.Success).examStatus)

        // 2. Outstanding fees student is blocked
        val blockedResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = boarderQrPayload,
            department = ScanDepartment.EXAMINATIONS,
            currentUser = examOfficer,
            database = database,
            syncManager = syncManager,
            examCode = "UNEB-UCE-2026"
        )
        assertTrue(blockedResult is DepartmentVerificationResult.Success)
        assertFalse((blockedResult as DepartmentVerificationResult.Success).isAllowed)
        assertEquals(ExamClearanceStatus.BLOCKED_FEES, blockedResult.examStatus)

        // 3. Bursar recommends an exception
        database.examExceptionDao().insertOrUpdate(
            com.example.data.local.ExamExceptionEntity(
                id = UUID.randomUUID().toString(),
                studentId = boarderStudentId,
                studentNumber = "LTC-2026-0002",
                studentName = "Sarah Achieng",
                examCode = "UNEB-UCE-2026",
                requestedByBursar = "Bursar Richard",
                recommendationReason = "Parent committed promissory note to settle balance before Term ends",
                isApproved = false,
                requestedAt = System.currentTimeMillis()
            )
        )

        // 4. While unapproved, exam officer sees PENDING_BURSAR_RECOMMENDED
        val pendingResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = boarderQrPayload,
            department = ScanDepartment.EXAMINATIONS,
            currentUser = examOfficer,
            database = database,
            syncManager = syncManager,
            examCode = "UNEB-UCE-2026"
        )
        assertTrue(pendingResult is DepartmentVerificationResult.Success)
        assertFalse((pendingResult as DepartmentVerificationResult.Success).isAllowed)
        assertEquals(ExamClearanceStatus.PENDING_BURSAR_RECOMMENDED, (pendingResult as DepartmentVerificationResult.Success).examStatus)

        // 5. Authorized Head Teacher approves exception
        val existingEx = database.examExceptionDao().findException(boarderStudentId, "UNEB-UCE-2026")!!
        database.examExceptionDao().insertOrUpdate(
            existingEx.copy(
                isApproved = true,
                approvedByHeadTeacher = "Head Teacher Dr. Otim",
                approvedAt = System.currentTimeMillis()
            )
        )

        // 6. Now exam officer scans same badge -> CLEARED_SPECIAL_EXCEPTION
        val approvedResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = boarderQrPayload,
            department = ScanDepartment.EXAMINATIONS,
            currentUser = examOfficer,
            database = database,
            syncManager = syncManager,
            examCode = "UNEB-UCE-2026"
        )
        assertTrue(approvedResult is DepartmentVerificationResult.Success)
        assertTrue((approvedResult as DepartmentVerificationResult.Success).isAllowed)
        assertEquals(ExamClearanceStatus.CLEARED_SPECIAL_EXCEPTION, (approvedResult as DepartmentVerificationResult.Success).examStatus)
    }

    @Test
    fun test06_PermissionDenied_RoleMismatch() = runBlocking {
        // Teacher tries to scan for gate entry
        val teacher = AuthUser(
            role = UserRole.TEACHERS,
            name = "Tr. Paul Okot",
            station = "Classroom"
        )

        val deniedGate = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.GATE,
            currentUser = teacher,
            database = database,
            syncManager = syncManager
        )
        assertTrue(deniedGate is DepartmentVerificationResult.PermissionDenied)
        assertEquals(StaffPermission.VERIFY_GATE_ACCESS, (deniedGate as DepartmentVerificationResult.PermissionDenied).requiredPermission)

        // Gate guard tries to scan for dining hall meals
        val gateGuard = AuthUser(
            role = UserRole.GATE_STAFF,
            name = "Guard James",
            station = "Gate"
        )
        val deniedMeals = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = dayScholarQrPayload,
            department = ScanDepartment.MEALS,
            currentUser = gateGuard,
            database = database,
            syncManager = syncManager
        )
        assertTrue(deniedMeals is DepartmentVerificationResult.PermissionDenied)
        assertEquals(StaffPermission.SERVE_MEALS, (deniedMeals as DepartmentVerificationResult.PermissionDenied).requiredPermission)
    }

    @Test
    fun test07_InvalidQr_Rejection() = runBlocking {
        val gateGuard = AuthUser(
            role = UserRole.GATE_STAFF,
            name = "Guard James",
            station = "Gate"
        )

        // Bare student ID is rejected
        val bareIdResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = "LTC-2026-0001",
            department = ScanDepartment.GATE,
            currentUser = gateGuard,
            database = database,
            syncManager = syncManager
        )
        assertTrue(bareIdResult is DepartmentVerificationResult.InvalidCredential)

        // Unsigned legacy format is rejected
        val legacyResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = "LTC:STU:LTC-2026-0001:TOK-DAY-1",
            department = ScanDepartment.GATE,
            currentUser = gateGuard,
            database = database,
            syncManager = syncManager
        )
        assertTrue(legacyResult is DepartmentVerificationResult.InvalidCredential)
    }
}
