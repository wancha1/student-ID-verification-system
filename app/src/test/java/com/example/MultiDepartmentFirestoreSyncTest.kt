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
import com.example.model.GateDirection
import com.example.model.MealType
import com.example.model.ScanDepartment
import com.example.model.SyncStatus
import com.example.model.UserRole
import com.example.testutil.TestCardCryptoHelper
import com.example.util.CardCryptoManager
import com.example.util.QrUnifiedVerifier
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
class MultiDepartmentFirestoreSyncTest {

    private lateinit var databaseDeviceA: AppDatabase
    private lateinit var databaseDeviceB: AppDatabase
    private lateinit var sharedCloudBackend: InMemoryCloudBackend
    private lateinit var syncManagerDeviceA: SyncManager
    private lateinit var syncManagerDeviceB: SyncManager

    private lateinit var testKeyPair: KeyPair
    private lateinit var testSigner: SoftwareCardSigner
    private val studentId = "STU-SYNC-001"
    private val cardId = "CRD-SYNC-001"
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
            studentNumber = "LTC-2026-0500",
            name = "Emmanuel Okello",
            firstName = "Emmanuel",
            lastName = "Okello",
            photoUrl = null,
            accessStatus = AccessStatus.APPROVED.name,
            gradeClass = "Senior 4-B",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK.name,
            transportRoute = "Main Gate Line",
            feesStatus = FeeStatus.CLEARED.name,
            outstandingAmount = 0.0,
            gender = "Male",
            avatarColorSeed = 100L,
            guardianName = "Okello George",
            guardianPhone = "+256 772 888999",
            emergencyContact = "+256 772 888999",
            homeroomTeacher = "Mr. Obua",
            academicYear = "2026",
            notes = "Sync test student",
            qrToken = "TOK-SYNC-1",
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
            id = "crd-sync-db-001",
            cardIdentifier = cardId,
            studentId = studentId,
            studentNumber = "LTC-2026-0500",
            qrPayload = qrPayload,
            status = CardStatus.ACTIVE.name,
            issueDate = now,
            activationDate = now,
            deactivationDate = null,
            replacedByCardId = null,
            reason = "Sync card",
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
    fun test01_OfflinePersistence_And_AutomaticRecoveryOnReconnect() = runBlocking {
        val guard = AuthUser(role = UserRole.GATE_STAFF, name = "Guard John", station = "Gate 1")

        // 1. Device A goes offline
        syncManagerDeviceA.setNetworkConnectivity(false)
        assertEquals(SyncStatus.OFFLINE, syncManagerDeviceA.syncInfo.value.status)

        // 2. Scan QR while offline
        val scanResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.GATE,
            currentUser = guard,
            database = databaseDeviceA,
            syncManager = syncManagerDeviceA,
            gateDirection = GateDirection.ENTRY
        )
        assertTrue(scanResult is DepartmentVerificationResult.Success)
        assertTrue((scanResult as DepartmentVerificationResult.Success).isOffline)

        // 3. Confirm scan is persisted locally in Room and flagged unsynced
        val unsyncedLogs = databaseDeviceA.scanLogDao().getUnsyncedLogs()
        assertEquals(1, unsyncedLogs.size)

        // 4. Reconnect network connectivity
        syncManagerDeviceA.setNetworkConnectivity(true)
        val syncResult = syncManagerDeviceA.syncNow()
        assertTrue(syncResult.isSuccess)

        // 5. Verify local outbox marked synced
        assertEquals(0, databaseDeviceA.scanLogDao().getUnsyncedLogs().size)
        assertEquals(SyncStatus.SYNCED, syncManagerDeviceA.syncInfo.value.status)
    }

    @Test
    fun test02_MultiDepartmentEvents_SyncToCloudBackend() = runBlocking {
        val mealStaff = AuthUser(role = UserRole.MEAL_SERVING_STAFF, name = "Chef Mary", station = "Cafeteria")
        val teacher = AuthUser(role = UserRole.TEACHERS, name = "Tr. Paul", station = "Class S4")

        // Record a meal on Device A
        val mealResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.MEALS,
            currentUser = mealStaff,
            database = databaseDeviceA,
            syncManager = syncManagerDeviceA,
            mealType = MealType.LUNCH
        )
        assertTrue(mealResult is DepartmentVerificationResult.Success)

        // Record attendance on Device A
        val attResult = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.ATTENDANCE,
            currentUser = teacher,
            database = databaseDeviceA,
            syncManager = syncManagerDeviceA,
            attendanceSubject = "Mathematics",
            attendanceSessionName = "Morning Lesson 1"
        )
        assertTrue(attResult is DepartmentVerificationResult.Success)

        // Sync Device A to cloud
        val syncA = syncManagerDeviceA.syncNow()
        assertTrue(syncA.isSuccess)
        assertEquals(0, databaseDeviceA.mealRecordDao().getUnsynced().size)
        assertEquals(0, databaseDeviceA.attendanceRecordDao().getUnsynced().size)
    }
}
