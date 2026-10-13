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
import com.example.model.MealType
import com.example.model.ScanDepartment
import com.example.model.UserRole
import com.example.testutil.TestCardCryptoHelper
import com.example.util.CardCryptoManager
import com.example.util.QrUnifiedVerifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
            studentNumber = "LTC-2026-0777",
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
    fun auditOfflineConcurrentMealClaimsAcrossIndependentDevices() = runBlocking {
        val serverA = AuthUser(role = UserRole.MEAL_SERVING_STAFF, name = "Server A", station = "Dining Point 1")
        val serverB = AuthUser(role = UserRole.MEAL_SERVING_STAFF, name = "Server B", station = "Dining Point 2")

        // 1. Both devices operate completely offline and detached
        syncManagerDeviceA.setNetworkConnectivity(false)
        syncManagerDeviceB.setNetworkConnectivity(false)

        // 2. Student scans at Device A for Lunch
        val serveA = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.MEALS,
            currentUser = serverA,
            database = databaseDeviceA,
            syncManager = syncManagerDeviceA,
            mealType = MealType.LUNCH
        )
        assertTrue("Device A must allow meal serving locally", serveA is DepartmentVerificationResult.Success)
        assertTrue((serveA as DepartmentVerificationResult.Success).isAllowed)

        // 3. Immediately afterwards, student goes to Dining Point 2 (Device B) while Device B is still offline
        // Because Device B has not synchronized with Device A, its local database has no knowledge of Device A's serving.
        val serveB = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.MEALS,
            currentUser = serverB,
            database = databaseDeviceB,
            syncManager = syncManagerDeviceB,
            mealType = MealType.LUNCH
        )
        assertTrue("Device B allows meal serving locally because offline devices have no real-time cross-device channel", serveB is DepartmentVerificationResult.Success)

        // Both devices have 1 meal record locally
        assertEquals(1, databaseDeviceA.mealRecordDao().getUnsynced().size)
        assertEquals(1, databaseDeviceB.mealRecordDao().getUnsynced().size)

        // 4. Synchronization to backend
        syncManagerDeviceA.setNetworkConnectivity(true)
        val syncA = syncManagerDeviceA.syncNow()
        assertTrue(syncA.isSuccess)

        syncManagerDeviceB.setNetworkConnectivity(true)
        val syncB = syncManagerDeviceB.syncNow()
        assertTrue(syncB.isSuccess)
    }
}
