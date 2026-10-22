package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.crypto.SoftwareCardSigner
import com.example.crypto.TrustedIssuerRegistry
import com.example.data.RoomStudentRepository
import com.example.data.local.AppDatabase
import com.example.data.local.MealRecordEntity
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.ExamExceptionEntity
import com.example.data.local.CardEntity
import com.example.data.sync.FirestoreCloudBackend
import com.example.data.sync.SyncManager
import com.example.model.AccessStatus
import com.example.model.AuthUser
import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.DepartmentVerificationResult
import com.example.model.FeeStatus
import com.example.model.GateDirection
import com.example.model.ScanDepartment
import com.example.model.Student
import com.example.model.UserRole
import com.example.testutil.TestCardCryptoHelper
import com.example.util.CardCryptoManager
import com.example.util.QrUnifiedVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class MultiDeviceFirestoreSyncTest : FirestoreEmulatorTestBase() {

    private lateinit var context: Context
    private val schoolId = "ltc_school_01"
    private lateinit var deviceADatabase: AppDatabase
    private lateinit var deviceBDatabase: AppDatabase

    private lateinit var testKeyPair: KeyPair
    private lateinit var testSigner: SoftwareCardSigner

    @Before
    override fun setUpFirebase() {
        super.setUpFirebase()
        context = ApplicationProvider.getApplicationContext()
        CardCryptoManager.resetForTesting()
        TrustedIssuerRegistry.initialize(context)
        TrustedIssuerRegistry.clearAll()

        testKeyPair = TestCardCryptoHelper.generateKeyPair()
        testSigner = SoftwareCardSigner(testKeyPair)
        CardCryptoManager.setActiveSigner(testSigner)
        TrustedIssuerRegistry.registerTrustedKey(testSigner.publicKey, "Authority")

        deviceADatabase = AppDatabase.createInMemory(context)
        deviceBDatabase = AppDatabase.createInMemory(context)
    }

    @After
    override fun tearDownFirebase() {
        super.tearDownFirebase()
        deviceADatabase.close()
        deviceBDatabase.close()
        CardCryptoManager.resetForTesting()
        TrustedIssuerRegistry.clearAll()
    }

    private suspend fun signInAs(email: String, role: String): String {
        val uid = signInTestUser(email)

        // Provision roster membership through the test-only emulator owner bypass.
        // Production rules intentionally prohibit client-side self-enrollment.
        provisionStaffForTest(uid, role, email)

        return uid
    }

    @Test
    fun testAdminRegistersStudentOnDeviceA_GateStaffSyncsAndVerifiesOnDeviceB() = runBlocking {
        // 1. Device A: Admin signs in
        val adminUid = signInAs("admin_${UUID.randomUUID().toString().take(6)}@ltc.ac.ug", "ADMIN")
        val cloudBackendA = FirestoreCloudBackend(firestore, auth, schoolId)
        val syncManagerA = SyncManager(deviceADatabase, cloudBackendA)
        val repoA = RoomStudentRepository(deviceADatabase, remoteCloudDataSource = cloudBackendA)

        val studentId = "STU-CROSS-SYNC-01"
        val cardId = "CRD-CROSS-SYNC-01"
        val qrPayload = CardCryptoManager.signCardPayload(
            cardId = cardId,
            validFrom = "2026-01-01",
            validUntil = "2026-12-31",
            signer = testSigner
        )

        val student = Student(
            id = studentId,
            studentNumber = "LTC-2026-5500",
            firstName = "Emmanuel",
            lastName = "Ocen",
            gradeClass = "Senior 4-Sci",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            accessStatus = AccessStatus.APPROVED
        )

        // Device A: Admin adds student & pushes to cloud
        repoA.addStudent(student)
        val card = Card(
            id = "crd-sync-01",
            cardIdentifier = cardId,
            studentId = studentId,
            studentNumber = "LTC-2026-5500",
            qrPayload = qrPayload,
            status = CardStatus.ACTIVE,
            issueDate = System.currentTimeMillis(),
            activationDate = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        deviceADatabase.cardDao().insertOrUpdateCard(CardEntity.fromDomain(card))
        cloudBackendA.pushCardChanges(listOf(CardEntity.fromDomain(card)))

        // 2. Device B: Gate Staff signs in (different account, no private signing key!)
        val gateUid = signInAs("gate_${UUID.randomUUID().toString().take(6)}@ltc.ac.ug", "GATE_OFFICER")
        val cloudBackendB = FirestoreCloudBackend(firestore, auth, schoolId)
        val syncManagerB = SyncManager(deviceBDatabase, cloudBackendB)
        val repoB = RoomStudentRepository(deviceBDatabase, remoteCloudDataSource = cloudBackendB)

        // Device B initially has no students in Room
        val initialStudents = deviceBDatabase.studentDao().getAllStudentsSnapshot()
        assertTrue("Device B initially empty", initialStudents.isEmpty())

        // Device B synchronizes from Firestore
        syncManagerB.syncNow()

        // Device B now has the student cached locally
        val syncedStudent = deviceBDatabase.studentDao().getStudentById(studentId)
        assertNotNull("Device B must have retrieved student from Cloud Firestore", syncedStudent)
        assertEquals("LTC-2026-5500", syncedStudent?.studentNumber)

        val syncedCard = deviceBDatabase.cardDao().getCardByIdentifier(cardId)
        assertNotNull("Device B must have retrieved active card record", syncedCard)

        // Device B verifies QR code using ONLY trusted public key & local Room database
        val gateUser = AuthUser(
            role = UserRole.GATE_STAFF,
            name = "Gate Officer Okello",
            station = "Main Gate 1"
        )

        val verification = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.GATE,
            currentUser = gateUser,
            database = deviceBDatabase,
            syncManager = syncManagerB,
            gateDirection = GateDirection.ENTRY
        )

        assertTrue("Gate verification on Device B must be approved", verification is DepartmentVerificationResult.Success)
    }

    @Test
    fun operationalRecordsUploadedOnOneDeviceArePulledToAnother() = runBlocking {
        signInAs("admin_ops_${UUID.randomUUID().toString().take(6)}@ltc.ac.ug", "ADMIN")
        val backend = FirestoreCloudBackend(firestore, auth, schoolId)

        val meal = MealRecordEntity(
            id = "MEAL-SYNC-${UUID.randomUUID().toString().take(8)}",
            studentId = "STU-OPS-01",
            studentNumber = "LTC-OPS-01",
            studentName = "Test Student",
            gradeClass = "Senior 4",
            mealType = "LUNCH",
            mealDate = "2026-10-22",
            timestamp = System.currentTimeMillis(),
            serverName = "Kitchen",
            status = "SERVED",
            isSyncedToCloud = false
        )
        val attendance = AttendanceRecordEntity(
            id = "ATT-SYNC-${UUID.randomUUID().toString().take(8)}",
            studentId = "STU-OPS-01",
            studentNumber = "LTC-OPS-01",
            studentName = "Test Student",
            gradeClass = "Senior 4",
            subject = "Mathematics",
            sessionName = "Period 1",
            lessonDate = "2026-10-22",
            timestamp = System.currentTimeMillis(),
            teacherName = "Test Teacher",
            isPresent = true
        )
        val exception = ExamExceptionEntity(
            id = "EXAM-SYNC-${UUID.randomUUID().toString().take(8)}",
            studentId = "STU-OPS-01",
            studentNumber = "LTC-OPS-01",
            studentName = "Test Student",
            examCode = "UACE-MATH-01",
            requestedByBursar = "Test Bursar",
            recommendationReason = "Test synchronization record",
            isApproved = false,
            requestedAt = System.currentTimeMillis()
        )

        assertTrue(backend.pushMealRecords(listOf(meal)).isSuccess)
        assertTrue(backend.pushAttendanceRecords(listOf(attendance)).isSuccess)
        assertTrue(backend.pushExamExceptions(listOf(exception)).isSuccess)

        val managerOnDeviceB = SyncManager(deviceBDatabase, backend)
        val syncResult = managerOnDeviceB.syncNow()
        assertTrue("Operational records should sync without failure: ${syncResult.exceptionOrNull()}", syncResult.isSuccess)

        assertEquals(meal.id, deviceBDatabase.mealRecordDao().observeAll().first().single().id)
        assertEquals(attendance.id, deviceBDatabase.attendanceRecordDao().observeAll().first().single().id)
        assertEquals(exception.id, deviceBDatabase.examExceptionDao().observeAll().first().single().id)
    }

    @Test
    fun malformedRemoteStudentFailsSyncInsteadOfBeingSilentlyDropped() = runBlocking {
        signInAs("admin_malformed_${UUID.randomUUID().toString().take(6)}@ltc.ac.ug", "ADMIN")
        val studentId = "STU-MALFORMED-${UUID.randomUUID().toString().take(8)}"
        firestore.collection("schools")
            .document(schoolId)
            .collection("students")
            .document(studentId)
            .set(
                mapOf(
                    "id" to studentId,
                    "studentNumber" to "LTC-2026-9999",
                    "name" to 42,
                    "updatedAt" to -1L,
                    "isDeleted" to false
                )
            )
            .await()

        val malformedCardId = "CARD-MALFORMED-${UUID.randomUUID().toString().take(8)}"
        firestore.collection("schools")
            .document(schoolId)
            .collection("cards")
            .document(malformedCardId)
            .set(
                mapOf(
                    "id" to malformedCardId,
                    "cardIdentifier" to 42,
                    "status" to "ACTIVE",
                    "updatedAt" to -1L,
                    "isDeleted" to false
                )
            )
            .await()

        val backend = FirestoreCloudBackend(firestore, auth, schoolId)
        val studentsResult = backend.fetchStudentsUpdatedSince(-5L)
        val cardsResult = backend.fetchCardsUpdatedSince(-5L)

        assertTrue(
            "A malformed student must fail the pull so the sync watermark is not advanced",
            studentsResult.isFailure
        )
        assertTrue(
            "A malformed card must fail the pull so the sync watermark is not advanced",
            cardsResult.isFailure
        )
    }

    @Test
    fun testRevokedCardRejectedOnDeviceBAfterSync() = runBlocking {
        // Device A deactivates/replaces card
        val adminUid = signInAs("admin_rev_${UUID.randomUUID().toString().take(6)}@ltc.ac.ug", "ADMIN")
        val cloudBackendA = FirestoreCloudBackend(firestore, auth, schoolId)
        val repoA = RoomStudentRepository(deviceADatabase, remoteCloudDataSource = cloudBackendA)

        val studentId = "STU-REV-01"
        val cardId = "CRD-REV-01"
        val qrPayload = CardCryptoManager.signCardPayload(
            cardId = cardId,
            validFrom = "2026-01-01",
            validUntil = "2026-12-31",
            signer = testSigner
        )

        val student = Student(
            id = studentId,
            studentNumber = "LTC-2026-6600",
            firstName = "Sarah",
            lastName = "Apio",
            gradeClass = "Senior 3-A",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            accessStatus = AccessStatus.APPROVED
        )

        repoA.addStudent(student)
        val card = Card(
            id = "crd-rev-entity",
            cardIdentifier = cardId,
            studentId = studentId,
            studentNumber = "LTC-2026-6600",
            qrPayload = qrPayload,
            status = CardStatus.LOST, // Marked LOST
            issueDate = System.currentTimeMillis(),
            activationDate = System.currentTimeMillis(),
            deactivationDate = System.currentTimeMillis(),
            reason = "Reported lost by student",
            updatedAt = System.currentTimeMillis()
        )
        deviceADatabase.cardDao().insertOrUpdateCard(CardEntity.fromDomain(card))
        cloudBackendA.pushCardChanges(listOf(CardEntity.fromDomain(card)))

        // Device B syncs
        val gateUid = signInAs("gate_rev_${UUID.randomUUID().toString().take(6)}@ltc.ac.ug", "GATE_OFFICER")
        val cloudBackendB = FirestoreCloudBackend(firestore, auth, schoolId)
        val syncManagerB = SyncManager(deviceBDatabase, cloudBackendB)
        syncManagerB.syncNow()

        val gateUser = AuthUser(
            role = UserRole.GATE_STAFF,
            name = "Gate Officer Okello",
            station = "Main Gate 1"
        )

        // Device B scans the lost card
        val verification = QrUnifiedVerifier.verifyAndProcessScan(
            rawQrCode = qrPayload,
            department = ScanDepartment.GATE,
            currentUser = gateUser,
            database = deviceBDatabase,
            syncManager = syncManagerB,
            gateDirection = GateDirection.ENTRY
        )

        assertFalse("Lost card MUST be rejected after cloud synchronization", verification is DepartmentVerificationResult.Success)
    }
}
