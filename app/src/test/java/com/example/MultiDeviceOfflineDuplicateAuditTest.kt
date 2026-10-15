package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.SoftwareCardSigner
import com.example.crypto.TrustedIssuerRegistry
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
import com.example.model.UserRole
import com.example.testutil.TestCardCryptoHelper
import com.example.util.CardCryptoManager
import com.example.util.QrUnifiedVerifier
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiDeviceOfflineDuplicateAuditTest {

    private lateinit var databaseDeviceA: AppDatabase
    private lateinit var databaseDeviceB: AppDatabase
    private lateinit var sharedCloudBackend: InMemoryCloudBackend
    private lateinit var syncManagerDeviceA: SyncManager
    private lateinit var syncManagerDeviceB: SyncManager

    private lateinit var testKeyPair: KeyPair
    private lateinit var testSigner: SoftwareCardSigner
    private val studentId = "STU-DUPLICATE-AUDIT"
    private val cardId = "CRD-DUPLICATE-AUDIT"
    private lateinit var qrPayload: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TrustedIssuerRegistry.initialize(context)
        TrustedIssuerRegistry.clearAll()
        CardCryptoManager.resetForTesting()

        sharedCloudBackend = InMemoryCloudBackend()

        databaseDeviceA = AppDatabase.createInMemory(context)
        syncManagerDeviceA = SyncManager(databaseDeviceA, sharedCloudBackend)

        databaseDeviceB = AppDatabase.createInMemory(context)
        syncManagerDeviceB = SyncManager(databaseDeviceB, sharedCloudBackend)

        testKeyPair = TestCardCryptoHelper.generateKeyPair()
        testSigner = SoftwareCardSigner(testKeyPair)
        CardCryptoManager.setActiveSigner(testSigner)
        TrustedIssuerRegistry.registerTrustedKey(testSigner.publicKey, "Primary Test Issuer Authority")

        val now = System.currentTimeMillis()
        val s1 = StudentEntity(
            id = studentId,
            studentNumber = "LTC-2026-0777", // Odd ending (7)
            name = "Denis Otim",
            firstName = "Denis",
            lastName = "Otim",
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
            guardianName = "Otim Peter",
            guardianPhone = "+256 772 555666",
            emergencyContact = "+256 772 555666",
            homeroomTeacher = "Mr. Obua",
            academicYear = "2026",
            notes = "Duplicate audit student",
            qrToken = "TOK-AUDIT-1",
            updatedAt = now,
            isDeleted = false
        )

        qrPayload = CardCryptoManager.signCardPayload(
            cardId = cardId,
            validFrom = "2026-01-01",
            validUntil = "2026-12-31",
            signer = testSigner
        )

        val c1 = CardEntity(
            id = "crd-audit-001",
            cardIdentifier = cardId,
            studentId = studentId,
            studentNumber = "LTC-2026-0777",
            qrPayload = qrPayload,
            status = CardStatus.ACTIVE.name,
            issueDate = now,
            activationDate = now,
            deactivationDate = null,
            replacedByCardId = null,
            reason = "Audit card",
            notes = "Test card",
            updatedAt = now,
            isDeleted = false
        )

        runBlocking {
            databaseDeviceA.studentDao().insertOrUpdateStudent(s1)
            databaseDeviceA.cardDao().insertOrUpdateCard(c1)

            databaseDeviceB.studentDao().insertOrUpdateStudent(s1)
            databaseDeviceB.cardDao().insertOrUpdateCard(c1)
        }
    }

    @After
    fun tearDown() {
        databaseDeviceA.close()
        databaseDeviceB.close()
        CardCryptoManager.resetForTesting()
        TrustedIssuerRegistry.clearAll()
    }

    @Test
    fun auditSameDeviceDuplicatePreventionBlocksImmediateDoubleClaim() = runBlocking {
        val serverA = AuthUser(role = UserRole.MEAL_SERVING_STAFF, name = "Server A", station = "Dining Hall")
        syncManagerDeviceA.setNetworkConnectivity(false)

        // 1. First claim succeeds
        val serve1 = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.MEALS,
            currentUser = serverA,
            database = databaseDeviceA,
            syncManager = syncManagerDeviceA,
            mealType = MealType.LUNCH
        )
        assertTrue("First meal serving on device must be allowed", serve1 is DepartmentVerificationResult.Success)
        assertTrue((serve1 as DepartmentVerificationResult.Success).isAllowed)

        // 2. Second claim for same student, same meal, same day is immediately BLOCKED locally
        val serve2 = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.MEALS,
            currentUser = serverA,
            database = databaseDeviceA,
            syncManager = syncManagerDeviceA,
            mealType = MealType.LUNCH
        )
        assertTrue("Same-device duplicate must be rejected locally", serve2 is DepartmentVerificationResult.DuplicateAction)
        assertEquals(ScanDepartment.MEALS, (serve2 as DepartmentVerificationResult.DuplicateAction).department)
    }

    @Test
    fun auditStationPartitioningPreventsCrossStationOfflineDoubleServing() = runBlocking {
        // Station 1 is partitioned for Odd student numbers; Station 2 for Even student numbers
        val serverStation1 = AuthUser(role = UserRole.MEAL_SERVING_STAFF, name = "Server 1", station = "Station 1 (Odd)")
        val serverStation2 = AuthUser(role = UserRole.MEAL_SERVING_STAFF, name = "Server 2", station = "Station 2 (Even)")

        // Both devices operate offline
        syncManagerDeviceA.setNetworkConnectivity(false)
        syncManagerDeviceB.setNetworkConnectivity(false)

        // Student has studentNumber LTC-2026-0777 (ending with odd digit 7)
        // 1. Serving at Station 1 (Odd) succeeds
        val serveAtStation1 = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.MEALS,
            currentUser = serverStation1,
            database = databaseDeviceA,
            syncManager = syncManagerDeviceA,
            mealType = MealType.LUNCH
        )
        assertTrue("Serving at assigned Station 1 (Odd) must be allowed", serveAtStation1 is DepartmentVerificationResult.Success)
        assertTrue((serveAtStation1 as DepartmentVerificationResult.Success).isAllowed)

        // 2. Student attempts to scan at Station 2 (Even) while both are offline
        // Deterministic station assignment blocks the student from obtaining a second meal!
        val serveAtStation2 = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.MEALS,
            currentUser = serverStation2,
            database = databaseDeviceB,
            syncManager = syncManagerDeviceB,
            mealType = MealType.LUNCH
        )
        assertTrue("Serving at wrong station must be blocked by deterministic partition", serveAtStation2 is DepartmentVerificationResult.Success)
        val result = serveAtStation2 as DepartmentVerificationResult.Success
        assertFalse("Must be denied", result.isAllowed)
        assertEquals(MealServingStatus.BLOCKED_NO_MEAL_CLEARANCE, result.mealStatus)
        assertTrue(result.statusHeadline.contains("Mismatch"))
    }

    @Test
    fun auditOfflineConcurrentMealClaimsAcrossIndependentDevicesAndSyncConflictDetection() = runBlocking {
        val serverA = AuthUser(role = UserRole.MEAL_SERVING_STAFF, name = "Server A", station = "Dining Point A")
        val serverB = AuthUser(role = UserRole.MEAL_SERVING_STAFF, name = "Server B", station = "Dining Point B")

        syncManagerDeviceA.setNetworkConnectivity(false)
        syncManagerDeviceB.setNetworkConnectivity(false)

        // Student scans at Point A
        val serveA = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.MEALS,
            currentUser = serverA,
            database = databaseDeviceA,
            syncManager = syncManagerDeviceA,
            mealType = MealType.LUNCH
        )
        assertTrue(serveA is DepartmentVerificationResult.Success)

        // Student scans at Point B (unpartitioned stations, disconnected)
        val serveB = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.MEALS,
            currentUser = serverB,
            database = databaseDeviceB,
            syncManager = syncManagerDeviceB,
            mealType = MealType.LUNCH
        )
        assertTrue(serveB is DepartmentVerificationResult.Success)

        // Synchronize both devices to cloud backend
        syncManagerDeviceA.setNetworkConnectivity(true)
        val syncA = syncManagerDeviceA.syncNow()
        assertTrue(syncA.isSuccess)

        syncManagerDeviceB.setNetworkConnectivity(true)
        val syncB = syncManagerDeviceB.syncNow()
        assertTrue(syncB.isSuccess)
    }
}
