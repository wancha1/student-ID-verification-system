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
import com.example.model.FeeStatus
import com.example.model.MealServingStatus
import com.example.model.MealType
import com.example.model.ScanDepartment
import com.example.model.StudentScanResult
import com.example.model.UserRole
import com.example.testutil.TestCardCryptoHelper
import com.example.ui.MainViewModel
import com.example.util.CardCryptoManager
import com.example.util.QrUnifiedVerifier
import com.example.util.SecurityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoleScannerRoutingTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var cloudBackend: InMemoryCloudBackend
    private lateinit var syncManager: SyncManager
    private lateinit var repository: RoomStudentRepository
    private lateinit var viewModel: MainViewModel

    private lateinit var testKeyPair: KeyPair
    private lateinit var testSigner: SoftwareCardSigner
    private val studentId = "STU-ROUTING-01"
    private val cardId = "CRD-ROUTING-01"
    private lateinit var qrPayload: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecurityManager.resetForTesting(context)
        TrustedIssuerRegistry.initialize(context)
        TrustedIssuerRegistry.clearAll()
        CardCryptoManager.resetForTesting()

        SecurityManager.provisionInitialAdmin(context, "System Admin", "1234")
        SecurityManager.setRolePin(context, UserRole.GATE_STAFF, "2222")
        SecurityManager.setRolePin(context, UserRole.MEAL_SERVING_STAFF, "3333")
        SecurityManager.setRolePin(context, UserRole.TEACHERS, "4444")
        SecurityManager.setRolePin(context, UserRole.EXAMINATION_STAFF, "5555")

        database = AppDatabase.createInMemory(context)
        cloudBackend = InMemoryCloudBackend()
        syncManager = SyncManager(database, cloudBackend)
        repository = RoomStudentRepository(database, remoteCloudDataSource = cloudBackend)
        viewModel = MainViewModel(repository)

        testKeyPair = TestCardCryptoHelper.generateKeyPair()
        testSigner = SoftwareCardSigner(testKeyPair)
        CardCryptoManager.setActiveSigner(testSigner)
        TrustedIssuerRegistry.registerTrustedKey(testSigner.publicKey, "Authority")

        val now = System.currentTimeMillis()
        val student = StudentEntity(
            id = studentId,
            studentNumber = "LTC-2026-9001",
            name = "Grace Auma",
            firstName = "Grace",
            lastName = "Auma",
            photoUrl = null,
            accessStatus = AccessStatus.APPROVED.name,
            gradeClass = "Senior 4-Sci",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK.name,
            transportRoute = "North Gate",
            feesStatus = FeeStatus.CLEARED.name,
            outstandingAmount = 0.0,
            gender = "Female",
            avatarColorSeed = 42L,
            guardianName = "Auma Betty",
            guardianPhone = "+256 701 112233",
            emergencyContact = "+256 701 112233",
            homeroomTeacher = "Teacher Akello",
            academicYear = "2026",
            notes = "Routing test student",
            qrToken = "TOK-ROUTING",
            updatedAt = now,
            isDeleted = false
        )

        qrPayload = CardCryptoManager.signCardPayload(
            cardId = cardId,
            validFrom = "2026-01-01",
            validUntil = "2026-12-31",
            signer = testSigner
        )

        val card = CardEntity(
            id = "crd-routing-01",
            cardIdentifier = cardId,
            studentId = studentId,
            studentNumber = "LTC-2026-9001",
            qrPayload = qrPayload,
            status = CardStatus.ACTIVE.name,
            issueDate = now,
            activationDate = now,
            deactivationDate = null,
            replacedByCardId = null,
            reason = "Active card",
            notes = "Test card",
            updatedAt = now,
            isDeleted = false
        )

        runBlocking {
            database.studentDao().insertOrUpdateStudent(student)
            database.cardDao().insertOrUpdateCard(card)
        }
    }

    @After
    fun tearDown() {
        database.close()
        CardCryptoManager.resetForTesting()
        TrustedIssuerRegistry.clearAll()
        SecurityManager.resetForTesting(context)
    }

    @Test
    fun testGateStaffScannerFlowAndBackHandling() = runBlocking {
        // Authenticate as Gate Staff
        val authed = viewModel.authenticateAndLogin(context, UserRole.GATE_STAFF, "2222")
        assertTrue("Gate staff should authenticate with valid PIN", authed)
        assertEquals(UserRole.GATE_STAFF, viewModel.currentUser.value?.role)

        // Open scanner
        viewModel.openScanner()
        assertTrue("Scanner should be open", viewModel.isScannerOpen.value)

        // Close scanner (simulating BackHandler)
        viewModel.closeScanner()
        assertFalse("Scanner should be closed after Back navigation", viewModel.isScannerOpen.value)
    }

    @Test
    fun testMealStaffScannerFlow() = runBlocking {
        val authed = viewModel.authenticateAndLogin(context, UserRole.MEAL_SERVING_STAFF, "3333")
        assertTrue("Meal staff should authenticate", authed)

        viewModel.openMealScanner()
        assertTrue("Meal scanner should be open", viewModel.isMealScannerOpen.value)

        viewModel.closeMealScanner()
        assertFalse("Meal scanner should close on Back", viewModel.isMealScannerOpen.value)
    }

    @Test
    fun testExaminationStaffScannerFlow() = runBlocking {
        val authed = viewModel.authenticateAndLogin(context, UserRole.EXAMINATION_STAFF, "5555")
        assertTrue("Exam staff should authenticate", authed)

        viewModel.openRequirementScanner()
        assertTrue("Requirement scanner should be open", viewModel.isRequirementScannerOpen.value)

        viewModel.closeRequirementScanner()
        assertFalse("Requirement scanner should close on Back", viewModel.isRequirementScannerOpen.value)
    }

    @Test
    fun testTeachersScannerFlow() = runBlocking {
        val authed = viewModel.authenticateAndLogin(context, UserRole.TEACHERS, "4444")
        assertTrue("Teacher should authenticate", authed)

        viewModel.selectDepartment(ScanDepartment.ATTENDANCE)
        viewModel.openScanner()
        assertTrue("Attendance scanner should be open", viewModel.isScannerOpen.value)

        viewModel.closeScanner()
        assertFalse("Attendance scanner should close on Back", viewModel.isScannerOpen.value)
    }

    @Test
    fun testSessionLockAndPersistence() {
        viewModel.loginAs(UserRole.BURSAR_FINANCE, "Bursar Florence")
        assertEquals(UserRole.BURSAR_FINANCE, viewModel.currentUser.value?.role)

        // Save session
        SecurityManager.saveActiveSession(context, viewModel.currentUser.value)

        // Restore in a fresh environment
        val restored = SecurityManager.getActiveSession(context)
        assertNotNull("Session must be restored across restarts", restored)
        assertEquals(UserRole.BURSAR_FINANCE, restored?.role)
        assertEquals("Bursar Florence", restored?.name)

        // Lock session / logout
        viewModel.lockSession()
        assertNull("Current user should be null after locking", viewModel.currentUser.value)
        SecurityManager.clearActiveSession(context)
        assertNull("Restored session should be null after logout", SecurityManager.getActiveSession(context))
    }
}
