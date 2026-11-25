package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.SoftwareCardSigner
import com.example.crypto.TrustedIssuerRegistry
import com.example.data.RoomStudentRepository
import com.example.data.local.AppDatabase
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.CardEntity
import com.example.data.local.ExamExceptionEntity
import com.example.data.local.MealRecordEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.data.sync.InMemoryCloudBackend
import com.example.model.AccessStatus
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.MealServingStatus
import com.example.model.MealType
import com.example.model.MealVerificationResult
import com.example.model.StaffPermission
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.model.UserRole
import com.example.testutil.TestCardCryptoHelper
import com.example.ui.MainViewModel
import com.example.util.CardCryptoManager
import com.example.util.SecurityManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoleFeaturesAndBackendSyncTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var cloudBackend: InMemoryCloudBackend
    private lateinit var repository: RoomStudentRepository
    private lateinit var viewModel: MainViewModel

    private lateinit var testKeyPair: KeyPair
    private lateinit var testSigner: SoftwareCardSigner

    private val boardingStudentId = "STU-ROLE-BOARDER"
    private val boardingCardId = "CRD-ROLE-BOARDER"
    private lateinit var boardingQrPayload: String

    private val dayScholarStudentId = "STU-ROLE-DAY"
    private val dayScholarCardId = "CRD-ROLE-DAY"
    private lateinit var dayScholarQrPayload: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecurityManager.resetForTesting(context)
        TrustedIssuerRegistry.initialize(context)
        TrustedIssuerRegistry.clearAll()
        CardCryptoManager.resetForTesting()

        database = AppDatabase.createInMemory(context)
        cloudBackend = InMemoryCloudBackend()
        repository = RoomStudentRepository(database, remoteCloudDataSource = cloudBackend)
        runBlocking { repository.initialize() }
        viewModel = MainViewModel(repository)

        testKeyPair = TestCardCryptoHelper.generateKeyPair()
        testSigner = SoftwareCardSigner(testKeyPair)
        CardCryptoManager.setActiveSigner(testSigner)
        TrustedIssuerRegistry.registerTrustedKey(testSigner.publicKey, "LTC Authority")

        val now = System.currentTimeMillis()
        boardingQrPayload = CardCryptoManager.signCardPayload(
            cardId = boardingCardId,
            validFrom = "2026-01-01",
            validUntil = "2026-12-31"
        )
        dayScholarQrPayload = CardCryptoManager.signCardPayload(
            cardId = dayScholarCardId,
            validFrom = "2026-01-01",
            validUntil = "2026-12-31"
        )

        val boarder = StudentEntity(
            id = boardingStudentId,
            studentNumber = "LTC-2026-8101",
            name = "Sarah Akello",
            firstName = "Sarah",
            lastName = "Akello",
            photoUrl = null,
            accessStatus = AccessStatus.APPROVED.name,
            gradeClass = "Senior 3-East",
            isDayScholar = false,
            dayScholarType = DayScholarStatus.BOARDER.name,
            transportRoute = "Campus Hostel",
            feesStatus = FeeStatus.CLEARED.name,
            outstandingAmount = 0.0,
            gender = "Female",
            avatarColorSeed = 11L,
            guardianName = "Okello John",
            guardianPhone = "+256700111222",
            emergencyContact = "+256700111222",
            homeroomTeacher = "Mr. Otim",
            academicYear = "2026",
            notes = "",
            qrToken = "TOK-B1",
            updatedAt = now,
            isDeleted = false
        )
        val dayScholar = StudentEntity(
            id = dayScholarStudentId,
            studentNumber = "LTC-2026-8102",
            name = "David Okwir",
            firstName = "David",
            lastName = "Okwir",
            photoUrl = null,
            accessStatus = AccessStatus.APPROVED.name,
            gradeClass = "Senior 2-West",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK.name,
            transportRoute = "Main Gate",
            feesStatus = FeeStatus.CLEARED.name,
            outstandingAmount = 0.0,
            gender = "Male",
            avatarColorSeed = 22L,
            guardianName = "Okwir Grace",
            guardianPhone = "+256700333444",
            emergencyContact = "+256700333444",
            homeroomTeacher = "Ms. Atim",
            academicYear = "2026",
            notes = "",
            qrToken = "TOK-D1",
            updatedAt = now,
            isDeleted = false
        )

        val boarderCard = CardEntity(
            id = boardingCardId,
            cardIdentifier = "LTC-CARD-8101",
            studentId = boardingStudentId,
            studentNumber = "LTC-2026-8101",
            qrPayload = boardingQrPayload,
            status = CardStatus.ACTIVE.name,
            issueDate = now,
            activationDate = now,
            deactivationDate = null,
            replacedByCardId = null,
            reason = "Initial issue",
            notes = "",
            updatedAt = now
        )
        val dayCard = CardEntity(
            id = dayScholarCardId,
            cardIdentifier = "LTC-CARD-8102",
            studentId = dayScholarStudentId,
            studentNumber = "LTC-2026-8102",
            qrPayload = dayScholarQrPayload,
            status = CardStatus.ACTIVE.name,
            issueDate = now,
            activationDate = now,
            deactivationDate = null,
            replacedByCardId = null,
            reason = "Initial issue",
            notes = "",
            updatedAt = now
        )

        runBlocking {
            database.studentDao().insertOrUpdateStudents(listOf(boarder, dayScholar))
            database.cardDao().insertOrUpdateCards(listOf(boarderCard, dayCard))
            cloudBackend.pushStudentChanges(listOf(boarder, dayScholar))
            cloudBackend.pushCardChanges(listOf(boarderCard, dayCard))
        }
    }

    @After
    fun tearDown() {
        database.close()
        CardCryptoManager.resetForTesting()
    }

    private suspend fun waitUntil(timeoutMs: Long = 5_000, predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            delay(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition was not satisfied within ${timeoutMs}ms", predicate())
    }

    @Test
    fun administratorWithoutFinanceCannotEditFees_withFinanceSyncsFeeToCloudAndExposesAllCards() = runBlocking {
        // 1. Admin without finance access
        viewModel.loginAs(UserRole.ADMINISTRATOR, "Registry Admin", hasFinancePermission = false)
        assertFalse(viewModel.currentUser.value!!.hasPermission(StaffPermission.MANAGE_FEES))
        assertTrue(viewModel.currentUser.value!!.hasPermission(StaffPermission.EXPORT_DATA))

        // allCardsFlow in RoomStudentRepository exposes issued cards cleanly (and MainViewModel.allCards wraps it)
        val repoCards = repository.allCardsFlow.first { it.size >= 2 }
        assertTrue(repoCards.any { it.id == boardingCardId })
        assertTrue(repoCards.any { it.id == dayScholarCardId })
        assertNotNull(viewModel.allCards)

        // Attempt fee modification without finance permission -> rejected synchronously
        viewModel.updateFeeStatus(boardingStudentId, FeeStatus.OUTSTANDING, 350000.0)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val deniedFeedback = viewModel.userFeedbackMessage.value
        assertNotNull(deniedFeedback)
        assertTrue(deniedFeedback!!.contains("Access Denied", ignoreCase = true))
        assertEquals(FeeStatus.CLEARED.name, database.studentDao().getStudentById(boardingStudentId)!!.feesStatus)

        // Admin can register a student and persist locally + to cloud
        val newStudent = Student(
            id = "STU-ADMIN-NEW",
            studentNumber = "LTC-2026-8999",
            firstName = "Ruth",
            lastName = "Lanyero",
            gradeClass = "Senior 1-A",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            accessStatus = AccessStatus.APPROVED
        )
        var registered = false
        viewModel.registerNewStudent(newStudent) { ok, _ -> registered = ok }
        waitUntil { database.studentDao().getStudentById(newStudent.id) != null && registered }
        assertTrue(registered)

        // 2. Admin WITH explicit finance access updates fees -> updates Room AND remote backend
        viewModel.loginAs(UserRole.ADMINISTRATOR, "Finance Admin", hasFinancePermission = true)
        assertTrue(viewModel.currentUser.value!!.hasPermission(StaffPermission.MANAGE_FEES))
        viewModel.updateFeeStatus(boardingStudentId, FeeStatus.OUTSTANDING, 420000.0)

        waitUntil {
            val local = database.studentDao().getStudentById(boardingStudentId)
            val remote = cloudBackend.fetchAllRemoteStudents().getOrThrow().find { it.id == boardingStudentId }
            local?.feesStatus == FeeStatus.OUTSTANDING.name &&
                local.outstandingAmount == 420000.0 &&
                remote?.feesStatus == FeeStatus.OUTSTANDING.name
        }
        val remoteBoarder = cloudBackend.fetchAllRemoteStudents().getOrThrow().find { it.id == boardingStudentId }!!
        assertEquals(420000.0, remoteBoarder.outstandingAmount, 0.01)
        // Ensure PendingChangeDao outbox count excludes FEE_STATUS rows (keeps legacy Room invariants intact)
        assertEquals(0, database.pendingChangeDao().getPendingCount())
    }

    @Test
    fun headTeacherIsStrictlyReadOnlyAndSyncsGateLogsAndMealsOnlyForPermittedTables() = runBlocking {
        val now = System.currentTimeMillis()
        val remoteLog = ScanLogEntity(
            id = "LOG-REMOTE-HT-01",
            studentId = boardingStudentId,
            studentNumber = "LTC-2026-8101",
            studentName = "Sarah Akello",
            gradeClass = "Senior 3-East",
            cardId = boardingCardId,
            cardIdentifier = "LTC-CARD-8101",
            qrPayload = boardingQrPayload,
            timestamp = now + 5000,
            decision = GateVerificationDecision.APPROVED.name,
            feeStatus = FeeStatus.CLEARED.name,
            cardStatus = CardStatus.ACTIVE.name,
            isDayScholar = false,
            isApproved = true,
            reason = "Fees cleared • Boarding",
            isOfflineDecision = false,
            dataSyncTimestampAtScan = now,
            guardName = "Officer Opio",
            deviceIdentifier = "GATE-ANDROID-1",
            gateLocation = "Main Gate",
            isSyncedToCloud = true
        )
        val remoteMeal = MealRecordEntity(
            id = "MEAL-REMOTE-HT-01",
            studentId = boardingStudentId,
            studentNumber = "LTC-2026-8101",
            studentName = "Sarah Akello",
            gradeClass = "Senior 3-East",
            mealType = MealType.LUNCH.name,
            mealDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)),
            timestamp = now + 5000,
            serverName = "Cook Apio",
            status = MealServingStatus.SERVED.name,
            isSyncedToCloud = true
        )
        val remoteAttendance = AttendanceRecordEntity(
            id = "ATT-REMOTE-SKIP",
            studentId = boardingStudentId,
            studentNumber = "LTC-2026-8101",
            studentName = "Sarah Akello",
            gradeClass = "Senior 3-East",
            subject = "Mathematics",
            sessionName = "Morning",
            lessonDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)),
            timestamp = now,
            teacherName = "Teacher",
            isPresent = true,
            isSyncedToCloud = true
        )
        val remoteException = ExamExceptionEntity(
            id = "EX-REMOTE-SKIP",
            studentId = boardingStudentId,
            studentNumber = "LTC-2026-8101",
            studentName = "Sarah Akello",
            examCode = "UNEB-UCE",
            requestedByBursar = "Bursar",
            recommendationReason = "Pending",
            isApproved = false,
            requestedAt = now,
            isSyncedToCloud = true
        )

        cloudBackend.pushGateLogs(listOf(remoteLog))
        cloudBackend.pushMealRecords(listOf(remoteMeal))
        cloudBackend.pushAttendanceRecords(listOf(remoteAttendance))
        cloudBackend.pushExamExceptions(listOf(remoteException))

        viewModel.loginAs(UserRole.HEAD_TEACHER_MANAGEMENT, "Head Teacher Okello", hasFinancePermission = false)
        assertTrue(viewModel.currentUser.value!!.hasPermission(StaffPermission.EXPORT_DATA))

        // Sync should pull gate logs and meals, while skipping attendance and exam exceptions for Head Teacher
        repository.getSyncManager().syncNow()

        val syncedLogs = database.scanLogDao().getAllLogsFlow().first()
        val syncedMeals = database.mealRecordDao().observeAll().first()
        val syncedAttendance = database.attendanceRecordDao().observeAll().first()
        val syncedExceptions = database.examExceptionDao().observeAll().first()

        assertTrue("Head Teacher must pull remote gate logs", syncedLogs.any { it.id == remoteLog.id })
        assertTrue("Head Teacher must pull remote meal records", syncedMeals.any { it.id == remoteMeal.id })
        assertTrue("V1 Head Teacher must not pull attendance records", syncedAttendance.isEmpty())
        assertTrue("V1 Head Teacher must not pull exam exceptions", syncedExceptions.isEmpty())

        // Write attempts must all be denied
        viewModel.updateFeeStatus(boardingStudentId, FeeStatus.OUTSTANDING, 100000.0)
        viewModel.deleteStudentRecord(boardingStudentId)
        viewModel.handleBarcodeScan(boardingQrPayload, context)
        viewModel.verifyAndServeMeal(boardingQrPayload, context)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        delay(100)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(FeeStatus.CLEARED.name, database.studentDao().getStudentById(boardingStudentId)!!.feesStatus)
        assertFalse(database.studentDao().getStudentById(boardingStudentId)!!.isDeleted)
        assertEquals(1, database.scanLogDao().getAllLogsFlow().first().size)
        assertEquals(1, database.mealRecordDao().observeAll().first().size)
    }

    @Test
    fun issuerAdminActionsRemainFailClosedUntilServerAuthorizationExists() {
        viewModel.loginAs(UserRole.ADMINISTRATOR, "Registry Admin")
        assertTrue(CardCryptoManager.hasIssuerPrivateKey())
        val trustedBefore = TrustedIssuerRegistry.getAllIssuers()
        assertTrue("Test fixture should have one trusted test issuer", trustedBefore.isNotEmpty())

        viewModel.setupAuthoritativeIssuerKey()
        assertFalse("Admin UI must not activate local signing authority", CardCryptoManager.hasIssuerPrivateKey())
        assertEquals("Local setup must not change trusted issuers", trustedBefore, TrustedIssuerRegistry.getAllIssuers())

        val publicKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.public.encoded)
        viewModel.enrollTrustedIssuerPublicKey(publicKeyBase64)
        assertEquals("Client-side public-key enrollment must remain disabled", trustedBefore, TrustedIssuerRegistry.getAllIssuers())

        viewModel.revokeTrustedIssuer("unknown-test-kid")
        assertEquals("Local revocation must not mutate the trust registry", trustedBefore, TrustedIssuerRegistry.getAllIssuers())
    }

    @Test
    fun gateStaffVerifiesQrLogsEntryAndCannotExportOrEditStudentsOrServeMeals() = runBlocking {
        viewModel.loginAs(UserRole.GATE_STAFF, "Guard Ocen")
        val guard = viewModel.currentUser.value!!
        assertFalse(guard.hasPermission(StaffPermission.EXPORT_DATA))
        assertFalse(guard.hasPermission(StaffPermission.MANAGE_STUDENTS))
        assertFalse(guard.hasPermission(StaffPermission.SERVE_MEALS))

        // Use the cleared day-scholar fixture: boarders are intentionally denied
        // at the day-scholar gate by the repository's access policy.
        viewModel.handleBarcodeScan(dayScholarQrPayload, context)
        waitUntil { viewModel.activeScanResult.value != null && database.scanLogDao().getAllLogsFlow().first().isNotEmpty() }
        val result = viewModel.activeScanResult.value
        assertTrue(result is StudentScanResult.Success)
        assertTrue((result as StudentScanResult.Success).isApproved)

        val logs = database.scanLogDao().getAllLogsFlow().first()
        assertEquals(dayScholarStudentId, logs.first().studentId)
        assertEquals("Guard Ocen", logs.first().guardName)

        // Denied meal serving
        viewModel.verifyAndServeMeal(boardingQrPayload, context)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(viewModel.mealScanOutcome.value is MealVerificationResult.StudentNotFound)
        assertTrue(database.mealRecordDao().observeAll().first().isEmpty())
    }

    @Test
    fun mealStaffServesLunchBlocksDoubleServingBlocksDayScholarSupperAndRejectsInvalidQr() = runBlocking {
        viewModel.loginAs(UserRole.MEAL_SERVING_STAFF, "Cook Nighty")
        val cook = viewModel.currentUser.value!!
        assertFalse(cook.hasPermission(StaffPermission.EXPORT_DATA))
        assertFalse(cook.hasPermission(StaffPermission.VERIFY_GATE_ACCESS))

        // 1. Lunch for boarder -> Success & persisted to Room
        viewModel.setActiveMealType(MealType.LUNCH)
        viewModel.verifyAndServeMeal(boardingQrPayload, context)
        waitUntil { viewModel.mealScanOutcome.value != null }

        val firstOutcome = viewModel.mealScanOutcome.value
        assertTrue("Expected MealVerificationResult.Success but got $firstOutcome", firstOutcome is MealVerificationResult.Success)
        assertEquals(boardingStudentId, (firstOutcome as MealVerificationResult.Success).record.studentId)
        viewModel.dismissMealOutcome()

        // 2. Duplicate lunch for same boarder -> BlockedDoubleServing
        viewModel.verifyAndServeMeal(boardingQrPayload, context)
        waitUntil { viewModel.mealScanOutcome.value != null }
        val duplicateOutcome = viewModel.mealScanOutcome.value
        assertTrue("Expected BlockedDoubleServing but got $duplicateOutcome", duplicateOutcome is MealVerificationResult.BlockedDoubleServing)
        viewModel.dismissMealOutcome()

        // 3. Supper (DINNER) for Day Scholar -> BlockedNoClearance
        viewModel.setActiveMealType(MealType.DINNER)
        viewModel.verifyAndServeMeal(dayScholarQrPayload, context)
        waitUntil { viewModel.mealScanOutcome.value != null }
        val daySupperOutcome = viewModel.mealScanOutcome.value
        assertTrue("Expected BlockedNoClearance for Day Scholar supper but got $daySupperOutcome", daySupperOutcome is MealVerificationResult.BlockedNoClearance)
        viewModel.dismissMealOutcome()

        // 4. Bare student ID (tampered/non-V2) -> StudentNotFound (must never silently fail or leave dialog stuck)
        viewModel.verifyAndServeMeal("LTC-2026-8101", context)
        waitUntil { viewModel.mealScanOutcome.value != null }
        val invalidOutcome = viewModel.mealScanOutcome.value
        assertTrue("Expected StudentNotFound for bare student ID but got $invalidOutcome", invalidOutcome is MealVerificationResult.StudentNotFound)
    }
}
