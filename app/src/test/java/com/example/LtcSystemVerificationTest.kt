package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.MockStudentRepository
import com.example.data.RoomStudentRepository
import com.example.data.local.AppDatabase
import com.example.data.local.CardEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.data.local.SyncEntityType
import com.example.data.local.SyncOperationType
import com.example.model.AccessStatus
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.StaffPermission
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.model.UserRole
import com.example.ui.MainViewModel
import com.example.util.CardCryptoManager
import com.example.testutil.TestCardCryptoHelper
import com.example.util.QrCodeGenerator
import com.example.util.QrCodeUtils
import com.example.util.QrParseResult
import com.example.util.SecurityManager
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.After
import com.example.crypto.TrustedIssuerRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LtcSystemVerificationTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TrustedIssuerRegistry.initialize(context)
        if (!CardCryptoManager.hasIssuerPrivateKey()) {
            val keyPair = TestCardCryptoHelper.generateKeyPair()
            TestCardCryptoHelper.configureTestKeyPair(keyPair)
        }
    }

    @After
    fun tearDown() {
        // Keep clean state
    }

    // =========================================================================
    // PRIORITY 1: QR CODE LIFECYCLE, LTC MONOGRAM, AND DECODABILITY TESTS
    // =========================================================================

    @Test
    fun testQrPayloadGenerationAndParsing() {
        val studentNum = "LTC-2026-0042"
        val cardId = "CRD-2026-0042-01"

        // 1. Authenticated Protocol V2 payload: LTC:V2:<cardId>:<signature>
        val payloadV2 = QrCodeUtils.createPayload(studentNum, cardId)
        assertTrue("V2 payload must start with LTC:V2:", payloadV2.startsWith("LTC:V2:"))

        val parseResultV2 = QrCodeUtils.parseQrCode(payloadV2)
        assertTrue("Parsing V2 payload must succeed as ValidV2Card", parseResultV2 is QrParseResult.ValidV2Card)
        val validV2 = parseResultV2 as QrParseResult.ValidV2Card
        assertEquals(cardId, validV2.cardId)
        assertTrue("Signature must not be empty", validV2.signature.isNotBlank())
        assertTrue(
            "Cryptographic signature must be authentic against trusted key",
            CardCryptoManager.verifyCardSignature(validV2.cardId, validV2.signature)
        )

        // 2. Reject Legacy LTC:V1 from production verification path
        val legacyV1 = QrCodeUtils.createLegacyV1Payload(studentNum, cardId)
        val parseResultLegacyV1 = QrCodeUtils.parseQrCode(legacyV1)
        assertTrue("LTC:V1 must be rejected from production verification path", parseResultLegacyV1 is QrParseResult.Invalid)

        // Isolated legacy parser extracts metadata for administrative tools
        val isolatedV1 = QrCodeUtils.parseLegacyCardIsolated(legacyV1)
        assertNotNull(isolatedV1)
        assertEquals(studentNum, isolatedV1?.studentNumber)
        assertEquals(cardId, isolatedV1?.cardIdentifier)

        // 3. Reject Legacy LTC:STU and OAKRIDGE from production verification path
        val legacyOakridge = "OAKRIDGE:STU:OAK-2026-0001"
        val parseOakridge = QrCodeUtils.parseQrCode(legacyOakridge)
        assertTrue("OAKRIDGE:STU must be rejected from production verification path", parseOakridge is QrParseResult.Invalid)

        val isolatedOak = QrCodeUtils.parseLegacyCardIsolated(legacyOakridge)
        assertNotNull(isolatedOak)
        assertEquals("OAK-2026-0001", isolatedOak?.studentNumber)

        // 4. Reject Bare student numbers and bare UUIDs
        assertTrue(QrCodeUtils.parseQrCode("LTC-2026-0042") is QrParseResult.Invalid)
        assertTrue(QrCodeUtils.parseQrCode("12345678-1234-1234-1234-123456789abc") is QrParseResult.Invalid)

        // 5. Malformed, empty, and security-violating inputs
        assertTrue(QrCodeUtils.parseQrCode("") is QrParseResult.Invalid)
        assertTrue(QrCodeUtils.parseQrCode("   ") is QrParseResult.Invalid)
        assertTrue(QrCodeUtils.parseQrCode("UNKNOWN_INVALID_BARCODE") is QrParseResult.Invalid)
        assertTrue(QrCodeUtils.parseQrCode("{\"fees\":\"PAID\",\"student\":\"Alice\"}") is QrParseResult.Invalid)
    }

    @Test
    fun testQrCodeBitmapGenerationAndZXingDecodability() {
        val payload = QrCodeUtils.createPayload("LTC-2026-0042", "CRD-2026-0042-01")

        // Generate QR code with centered high-contrast LTC monogram
        val bitmap = QrCodeGenerator.generateQrBitmap(
            content = payload,
            size = 600,
            addLtcLogo = true
        )
        assertNotNull("Generated QR bitmap must not be null", bitmap)
        assertEquals(600, bitmap!!.width)
        assertEquals(600, bitmap.height)

        // Convert Bitmap pixels to ZXing LuminanceSource and decode
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val result = reader.decode(binaryBitmap)

        assertNotNull("ZXing reader must successfully decode QR with centered LTC monogram", result)
        assertEquals("Decoded payload must exactly match original payload", payload, result.text)
    }

    // =========================================================================
    // PRIORITY 2: DATA PERSISTENCE ACROSS APPLICATION/PROCESS RESTARTS
    // =========================================================================

    @Test
    fun testDataPersistenceAcrossDatabaseReopen() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = File(context.filesDir, "test_persistence_verification.db")
            if (dbFile.exists()) dbFile.delete()

            // 1. Initial process session: create database and insert records
            val dbSession1 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                .allowMainThreadQueries()
                .build()

            val studentId = "stu-persistent-01"
            val studentNum = "LTC-2026-0888"
            val photoUrl = "file://${context.filesDir}/student_photos/photo_persisted.jpg"

            val student = Student(
                id = studentId,
                studentNumber = studentNum,
                firstName = "Joshua",
                lastName = "Apio",
                gradeClass = "Senior 4-A",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
                feesStatus = FeeStatus.CLEARED,
                outstandingAmount = 0.0,
                photoUrl = photoUrl
            )
            dbSession1.studentDao().insertOrUpdateStudent(StudentEntity.fromDomain(student))

            val cardId = "card-persistent-01"
            val card = CardEntity(
                id = cardId,
                cardIdentifier = "CRD-2026-0888-01",
                studentId = studentId,
                studentNumber = studentNum,
                qrPayload = QrCodeUtils.createPayload(studentNum, "CRD-2026-0888-01"),
                status = CardStatus.ACTIVE.name,
                issueDate = System.currentTimeMillis(),
                activationDate = System.currentTimeMillis(),
                deactivationDate = null,
                replacedByCardId = null,
                reason = "Initial Pilot Card",
                notes = "Persistent card verification",
                updatedAt = System.currentTimeMillis()
            )
            dbSession1.cardDao().insertOrUpdateCard(card)

            val logId = "log-persistent-01"
            val scanLog = ScanLogEntity(
                id = logId,
                studentId = studentId,
                studentNumber = studentNum,
                studentName = "Joshua Apio",
                gradeClass = "Senior 4-A",
                cardId = cardId,
                cardIdentifier = "CRD-2026-0888-01",
                qrPayload = QrCodeUtils.createPayload(studentNum, "CRD-2026-0888-01"),
                decision = GateVerificationDecision.APPROVED.name,
                feeStatus = FeeStatus.CLEARED.name,
                cardStatus = CardStatus.ACTIVE.name,
                isDayScholar = true,
                isApproved = true,
                reason = "Entry Approved: Fees Cleared",
                timestamp = System.currentTimeMillis(),
                isOfflineDecision = true,
                dataSyncTimestampAtScan = System.currentTimeMillis(),
                guardName = "Gate Guard",
                deviceIdentifier = "Terminal-1",
                gateLocation = "Gate 1",
                isSyncedToCloud = false
            )
            dbSession1.scanLogDao().insertLog(scanLog)

            // Close session (simulating app exit/process death)
            dbSession1.close()

            // 2. Restart session: Reopen the database from persistent file
            val dbSession2 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                .allowMainThreadQueries()
                .build()

            val retrievedStudent = dbSession2.studentDao().getStudentById(studentId)
            assertNotNull("Student record must survive application restart", retrievedStudent)
            assertEquals("Joshua Apio", retrievedStudent?.name)
            assertEquals(studentNum, retrievedStudent?.studentNumber)
            assertEquals(photoUrl, retrievedStudent?.photoUrl)

            val retrievedCard = dbSession2.cardDao().getCardById(cardId)
            assertNotNull("Card record must survive application restart", retrievedCard)
            assertEquals("CRD-2026-0888-01", retrievedCard?.cardIdentifier)
            assertEquals(CardStatus.ACTIVE.name, retrievedCard?.status)

            val retrievedLog = dbSession2.scanLogDao().getScanLogById(logId)
            assertNotNull("Gate scan log must survive application restart", retrievedLog)
            assertEquals(GateVerificationDecision.APPROVED.name, retrievedLog?.decision)

            dbSession2.close()
            dbFile.delete()
        }
    }

    // =========================================================================
    // PRIORITY 3: PRIVILEGE ESCALATION, ACCESS CONTROL, AND EMERGENCY OVERRIDE
    // =========================================================================

    @Test
    fun testRoleBasedAuthorizationGuardsPreventUnauthorizedModifications() {
        runBlocking {
            val repository = MockStudentRepository()
            val targetStudent = Student(
                id = "stu-001",
                studentNumber = "LTC-2026-0001",
                firstName = "Michael",
                lastName = "Kasule",
                gradeClass = "Senior 4-A",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
                feesStatus = FeeStatus.CLEARED,
                outstandingAmount = 0.0,
                accessStatus = AccessStatus.APPROVED
            )
            repository.addStudent(targetStudent)
            val viewModel = MainViewModel(repository)

            // Initial launch requires login
            assertFalse(viewModel.currentUser.value != null && viewModel.currentUser.value?.role == UserRole.ADMINISTRATOR)

            // Log in as Gate Keeper (Guard post)
            viewModel.loginAs(UserRole.GATE_KEEPER)
            assertEquals(UserRole.GATE_KEEPER, viewModel.currentUser.value?.role)

            // 1. Guard attempts to modify fee status -> Blocked!
            viewModel.updateFeeStatus(targetStudent.id, FeeStatus.OUTSTANDING, 500000.0)
            assertTrue(
                "Guard modification of fees must be denied",
                viewModel.userFeedbackMessage.value?.contains("Bursar / Finance") == true
            )

            // 2. Guard attempts to register new student -> Blocked!
            var registerCallbackCalled = false
            var registerSuccess = false
            val newStudent = Student(
                id = "unauthorized-add-01",
                studentNumber = "LTC-2026-9999",
                firstName = "Hacker",
                lastName = "Attempt",
                gradeClass = "Senior 1"
            )
            viewModel.registerNewStudent(newStudent) { success, _ ->
                registerCallbackCalled = true
                registerSuccess = success
            }
            assertTrue(registerCallbackCalled)
            assertFalse("Guard registration of students must be blocked", registerSuccess)

            // 3. Guard attempts to delete student -> Blocked!
            viewModel.deleteStudentRecord(targetStudent.id)
            assertTrue(
                "Guard deletion of students must be denied",
                viewModel.userFeedbackMessage.value?.contains("requires Administrator privileges") == true
            )

            // 4. Authenticate as Administrator without explicit finance clearance -> Fee modification is blocked!
            viewModel.loginAs(UserRole.ADMINISTRATOR, hasFinancePermission = false)
            assertEquals(UserRole.ADMINISTRATOR, viewModel.currentUser.value?.role)

            viewModel.updateFeeStatus(targetStudent.id, FeeStatus.CLEARED, 0.0)
            assertTrue(
                "Administrator without explicit finance clearance must be blocked from fee updates per least privilege",
                viewModel.userFeedbackMessage.value?.contains("Bursar / Finance") == true
            )

            // 5. Authenticate with explicit finance clearance (or as Bursar) -> Allowed!
            viewModel.loginAs(UserRole.ADMINISTRATOR, hasFinancePermission = true)
            viewModel.updateFeeStatus(targetStudent.id, FeeStatus.CLEARED, 0.0)
            assertTrue(
                "Administrator with explicit finance clearance must succeed",
                viewModel.userFeedbackMessage.value?.contains("CLEARED") == true
            )

            // 6. Authenticate as Bursar / Finance directly -> Fee modification allowed, registration blocked
            viewModel.loginAs(UserRole.BURSAR_FINANCE)
            viewModel.updateFeeStatus(targetStudent.id, FeeStatus.OUTSTANDING, 150000.0)
            assertTrue(
                "Bursar modification must succeed",
                viewModel.userFeedbackMessage.value?.contains("OUTSTANDING") == true
            )
            var bursarRegisterCalled = false
            var bursarRegisterSuccess = false
            viewModel.registerNewStudent(newStudent) { success, _ ->
                bursarRegisterCalled = true
                bursarRegisterSuccess = success
            }
            assertTrue(bursarRegisterCalled)
            assertFalse("Bursar must not be permitted to register students (reserved for Admin)", bursarRegisterSuccess)
        }
    }

    @Test
    fun testEmergencySupervisorOverrideWorkflow() {
        runBlocking {
            val repository = MockStudentRepository()
            val outstandingStudent = Student(
                id = "stu-002",
                studentNumber = "LTC-2026-0002",
                firstName = "Sophia",
                lastName = "Nanteza",
                gradeClass = "Senior 3-B",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
                feesStatus = FeeStatus.OUTSTANDING,
                outstandingAmount = 450000.0,
                accessStatus = AccessStatus.RESTRICTED_FEES
            )
            repository.addStudent(outstandingStudent)
            val card = repository.issueCard(outstandingStudent.id, "CRD-0002", "Initial card").getOrThrow()

            val viewModel = MainViewModel(repository)
            val context = ApplicationProvider.getApplicationContext<Context>()
            SecurityManager.configureTestCredentials(context, adminPin = "7891", supervisorPin = "7891")

            viewModel.loginAs(UserRole.GATE_KEEPER)

            // Student with outstanding fees scanned via authentic V2 card
            val studentNumber = outstandingStudent.studentNumber
            viewModel.handleBarcodeScan(card.qrPayload, context)

            val initialScan = viewModel.activeScanResult.value
            assertTrue(initialScan is StudentScanResult.Success)
            assertFalse("Outstanding fee student must initially be denied", (initialScan as StudentScanResult.Success).isApproved)

            // 1. Override with invalid supervisor PIN -> Rejected!
            var overrideSuccess = false
            viewModel.authorizeEmergencyOverride(
                studentId = studentNumber,
                supervisorName = "Mrs. Clara Nambi",
                overridePin = "0000",
                reason = "Guardian promises payment tomorrow",
                context = context
            ) { success, _ -> overrideSuccess = success }
            assertFalse("Override with invalid PIN must fail", overrideSuccess)
            assertFalse("Scan result must remain unapproved after failed override", (viewModel.activeScanResult.value as StudentScanResult.Success).isApproved)

            // 2. Override on invalid / unknown QR code -> Rejected!
            viewModel.handleBarcodeScan("INVALID_MALFORMED_QR_XYZ", context)
            var invalidOverrideSuccess = false
            viewModel.authorizeEmergencyOverride(
                studentId = "unknown-id",
                supervisorName = "Mrs. Clara Nambi",
                overridePin = "7891",
                reason = "Manual bypass attempt",
                context = context
            ) { success, _ -> invalidOverrideSuccess = success }
            assertFalse("Emergency override must NEVER authorize an invalid or unknown QR code", invalidOverrideSuccess)

            // 3. Valid supervisor override on identified student -> Approved & Logged!
            viewModel.handleBarcodeScan(card.qrPayload, context)
            var validOverrideSuccess = false
            viewModel.authorizeEmergencyOverride(
                studentId = studentNumber,
                supervisorName = "Mrs. Clara Nambi",
                overridePin = "7891",
                reason = "Guardian signed payment commitment note with Principal",
                context = context
            ) { success, _ -> validOverrideSuccess = success }

            assertTrue("Emergency override with valid PIN must succeed", validOverrideSuccess)
            val approvedResult = viewModel.activeScanResult.value
            assertTrue(approvedResult is StudentScanResult.Success)
            assertTrue("Scan result must now be Approved", (approvedResult as StudentScanResult.Success).isApproved)
            assertTrue(approvedResult.reason.contains("EMERGENCY SUPERVISOR OVERRIDE"))

            // Verify audit log entry
            val logs = repository.scanLogsFlow.first()
            val latestLog = logs.first()
            assertEquals(GateVerificationDecision.APPROVED, latestLog.decision)
            assertTrue(latestLog.guardName.contains("Mrs. Clara Nambi"))
            assertTrue(latestLog.reason.contains("EMERGENCY SUPERVISOR OVERRIDE"))
        }
    }

    // =========================================================================
    // PRIORITY 6: SUPPORT AT LEAST 3,000 STUDENTS & ENFORCE DB UNIQUENESS
    // =========================================================================

    @Test
    fun testScaleThreeThousandStudentsUniquenessAndLookup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database = AppDatabase.createInMemory(context)

            val totalStudents = 3000
            val studentEntities = ArrayList<StudentEntity>(totalStudents)
            val cardEntities = ArrayList<CardEntity>(totalStudents)
            val now = System.currentTimeMillis()

            for (i in 1..totalStudents) {
                val studentId = "stu-scale-$i"
                val formattedSeq = String.format("%05d", i)
                val studentNumber = "LTC-2026-$formattedSeq"
                val cardIdentifier = "CRD-2026-$formattedSeq-01"
                val qrPayload = QrCodeUtils.createPayload(studentNumber, cardIdentifier)

                studentEntities.add(
                    StudentEntity(
                        id = studentId,
                        studentNumber = studentNumber,
                        name = "Student Test $i",
                        firstName = "Student",
                        lastName = "Test $i",
                        photoUrl = null,
                        accessStatus = AccessStatus.APPROVED.name,
                        gradeClass = "Senior ${(i % 6) + 1}",
                        isDayScholar = (i % 3) != 0, // 2/3 day scholars
                        dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK.name,
                        transportRoute = "Route #${(i % 10) + 1}",
                        feesStatus = if (i % 5 == 0) FeeStatus.OUTSTANDING.name else FeeStatus.CLEARED.name,
                        outstandingAmount = if (i % 5 == 0) 350000.0 else 0.0,
                        gender = if (i % 2 == 0) "Female" else "Male",
                        avatarColorSeed = i.toLong(),
                        guardianName = "Guardian $i",
                        guardianPhone = "+256 772 00$formattedSeq",
                        emergencyContact = "+256 701 00$formattedSeq",
                        homeroomTeacher = "Teacher ${(i % 20) + 1}",
                        academicYear = "2026",
                        notes = "Scale pilot cohort record",
                        updatedAt = now
                    )
                )

                cardEntities.add(
                    CardEntity(
                        id = "crd-scale-$i",
                        cardIdentifier = cardIdentifier,
                        studentId = studentId,
                        studentNumber = studentNumber,
                        qrPayload = qrPayload,
                        status = CardStatus.ACTIVE.name,
                        issueDate = now,
                        activationDate = now,
                        deactivationDate = null,
                        replacedByCardId = null,
                        reason = "Batch issuance",
                        notes = "High-volume card issuance",
                        updatedAt = now
                    )
                )
            }

            // Insert in batches
            val chunkSize = 500
            studentEntities.chunked(chunkSize).forEach { chunk ->
                database.studentDao().insertOrUpdateStudents(chunk)
            }
            cardEntities.chunked(chunkSize).forEach { chunk ->
                database.cardDao().insertOrUpdateCards(chunk)
            }

            // 1. Verify exact count
            val count = database.studentDao().getActiveCount()
            assertEquals(3000, count)

            val cardCount = database.cardDao().getTotalCardsCount()
            assertEquals(3000, cardCount)

            // 2. High performance indexed lookup at arbitrary positions
            val sampleStudent1 = database.studentDao().getStudentByStudentNumber("LTC-2026-00001")
            assertNotNull(sampleStudent1)
            assertEquals("Student Test 1", sampleStudent1?.name)

            val sampleStudent1500 = database.studentDao().getStudentByStudentNumber("LTC-2026-01500")
            assertNotNull(sampleStudent1500)
            assertEquals("Student Test 1500", sampleStudent1500?.name)

            val sampleStudent3000 = database.studentDao().getStudentByStudentNumber("LTC-2026-03000")
            assertNotNull(sampleStudent3000)
            assertEquals("Student Test 3000", sampleStudent3000?.name)

            // Indexed lookup by QR payload
            val expectedPayload = cardEntities[1499].qrPayload
            val sampleCard = database.cardDao().getCardByQrPayload(expectedPayload)
            assertNotNull(sampleCard)
            assertEquals("stu-scale-1500", sampleCard?.studentId)

            // 3. Database uniqueness constraint verification
            val duplicateStudent = StudentEntity(
                id = "duplicate-uuid-9999",
                studentNumber = "LTC-2026-00001", // Duplicate of student #1
                name = "Duplicate Student",
                firstName = "Duplicate",
                lastName = "Student",
                photoUrl = null,
                accessStatus = AccessStatus.APPROVED.name,
                gradeClass = "Senior 1",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK.name,
                transportRoute = "Route 1",
                feesStatus = FeeStatus.CLEARED.name,
                outstandingAmount = 0.0,
                gender = "Male",
                avatarColorSeed = 1L,
                guardianName = "Guardian",
                guardianPhone = "+256 700 000000",
                emergencyContact = "+256 700 000000",
                homeroomTeacher = "Teacher",
                academicYear = "2026",
                notes = "Should fail",
                updatedAt = now
            )

            try {
                database.studentDao().insertStudent(duplicateStudent)
                fail("Inserting duplicate studentNumber must trigger SQLiteConstraintException")
            } catch (e: Exception) {
                assertTrue(
                    "Exception must be SQLiteConstraintException: ${e.javaClass.simpleName}",
                    e is android.database.sqlite.SQLiteConstraintException || e.message?.contains("UNIQUE") == true
                )
            }

            database.close()
        }
    }

    // =========================================================================
    // PRIORITY 7: TRANSACTION ATOMICITY AND DURABLE OFFLINE QUEUE ROLLBACK
    // =========================================================================

    @Test
    fun testSuccessfulOperationsPersistAllExpectedRecordsAndPendingChanges() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database = AppDatabase.createInMemory(context)
            val repo = RoomStudentRepository(database)

            val studentId = "stu-tx-success-01"
            val studentNumber = "LTC-2026-7777"
            val testStudent = Student(
                id = studentId,
                studentNumber = studentNumber,
                firstName = "Brenda",
                lastName = "Acan",
                gradeClass = "Senior 3-A",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
                feesStatus = FeeStatus.CLEARED,
                outstandingAmount = 0.0
            )

            // 1. Verify atomic addStudent (student, profile, initial card, 2 pending changes)
            val addResult = repo.addStudent(testStudent)
            assertTrue("addStudent must succeed", addResult.isSuccess)

            val retrievedStudent = database.studentDao().getStudentById(studentId)
            assertNotNull("Student entity must be persisted", retrievedStudent)
            assertEquals("Brenda Acan", retrievedStudent?.name)

            val retrievedProfile = database.studentProfileDao().getProfileById(studentId)
            assertNotNull("Student profile must be persisted", retrievedProfile)
            assertEquals(AccessStatus.APPROVED.name, retrievedProfile?.accessStatus)

            val cards = database.cardDao().getCardsForStudent(studentId)
            assertEquals("Exactly one initial active card must be issued", 1, cards.size)
            val initialCard = cards.first()
            assertEquals(CardStatus.ACTIVE.name, initialCard.status)

            val pendingChanges1 = database.pendingChangeDao().getPendingChanges()
            assertEquals("Exactly 2 pending changes must be enqueued (STUDENT & CARD)", 2, pendingChanges1.size)
            assertTrue(pendingChanges1.any { it.entityType == SyncEntityType.STUDENT && it.recordId == studentId })
            assertTrue(pendingChanges1.any { it.entityType == SyncEntityType.CARD && it.recordId == initialCard.id })

            // 2. Verify atomic updateFeeStatus
            val updateFeeResult = repo.updateFeeStatus(studentId, FeeStatus.OUTSTANDING, 320000.0)
            assertTrue("updateFeeStatus must succeed", updateFeeResult.isSuccess)

            val updatedStudent = database.studentDao().getStudentById(studentId)
            assertEquals(FeeStatus.OUTSTANDING.name, updatedStudent?.feesStatus)
            assertEquals(320000.0, updatedStudent?.outstandingAmount ?: 0.0, 0.01)
            assertEquals(AccessStatus.RESTRICTED_FEES.name, updatedStudent?.accessStatus)

            val updatedProfile = database.studentProfileDao().getProfileById(studentId)
            assertEquals(AccessStatus.RESTRICTED_FEES.name, updatedProfile?.accessStatus)

            val pendingChanges2 = database.pendingChangeDao().getPendingChanges()
            assertEquals(3, pendingChanges2.size)
            assertTrue(pendingChanges2.any { it.entityType == SyncEntityType.FEE_STATUS && it.recordId == studentId })

            // 3. Verify atomic issueReplacementCard
            val replaceResult = repo.issueReplacementCard(studentId, initialCard.id, "Lost card at gate")
            assertTrue("issueReplacementCard must succeed", replaceResult.isSuccess)

            val reloadedInitialCard = database.cardDao().getCardById(initialCard.id)
            assertEquals("Initial card must be marked REPLACED", CardStatus.REPLACED.name, reloadedInitialCard?.status)

            val allStudentCards = database.cardDao().getCardsForStudent(studentId)
            assertEquals("Student must have 2 total cards now", 2, allStudentCards.size)
            val newActiveCard = allStudentCards.firstOrNull { it.status == CardStatus.ACTIVE.name }
            assertNotNull("New replacement card must be ACTIVE", newActiveCard)

            val pendingChanges3 = database.pendingChangeDao().getPendingChanges()
            assertEquals(5, pendingChanges3.size) // 2 original + 1 fee + 2 replacement (1 status change + 1 create)

            // 4. Verify atomic reportCardLost
            val lostResult = repo.reportCardLost(studentId, newActiveCard!!.id, "Student lost replacement card")
            assertTrue("reportCardLost must succeed", lostResult.isSuccess)
            val lostCard = database.cardDao().getCardById(newActiveCard.id)
            assertEquals(CardStatus.LOST.name, lostCard?.status)

            val pendingChanges4 = database.pendingChangeDao().getPendingChanges()
            assertEquals(6, pendingChanges4.size)

            database.close()
        }
    }

    @Test
    fun testDeliberatelyInjectedFailureMidwayRollsBackAllWritesAndLeavesNoOrphanRecords() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database = AppDatabase.createInMemory(context)
            val repo = RoomStudentRepository(database)

            val studentId = "stu-tx-rollback-01"
            val studentNumber = "LTC-2026-8888"
            val testStudent = Student(
                id = studentId,
                studentNumber = studentNumber,
                firstName = "Patrick",
                lastName = "Ocen",
                gradeClass = "Senior 4-C",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
                feesStatus = FeeStatus.CLEARED,
                outstandingAmount = 0.0
            )

            // -------------------------------------------------------------
            // TEST 1: addStudent fails midway after local writes
            // -------------------------------------------------------------
            repo.testFailureInterceptor = { step ->
                if (step == "addStudent_afterLocalWrites") {
                    throw IllegalStateException("SIMULATED_FAILURE: Crash during addStudent before pending changes")
                }
            }

            val failedAddResult = repo.addStudent(testStudent)
            assertTrue("addStudent must return failure on injected exception", failedAddResult.isFailure)
            val exception = failedAddResult.exceptionOrNull()
            assertNotNull("Failure exception must not be null", exception)
            assertTrue(
                "Exception message must match simulated failure",
                exception?.message?.contains("SIMULATED_FAILURE") == true
            )

            // CRITICAL VERIFICATION: Atomicity rollback must leave ZERO orphan records
            assertNull(
                "Student entity must have rolled back completely",
                database.studentDao().getStudentById(studentId)
            )
            assertNull(
                "Student profile entity must have rolled back completely",
                database.studentProfileDao().getProfileById(studentId)
            )
            assertTrue(
                "Card entities must have rolled back completely (no orphan cards)",
                database.cardDao().getCardsForStudent(studentId).isEmpty()
            )
            assertEquals(
                "Pending changes queue must be completely empty (no orphan queue items)",
                0,
                database.pendingChangeDao().getPendingCount()
            )

            // -------------------------------------------------------------
            // TEST 2: Successful addStudent after clearing interceptor
            // -------------------------------------------------------------
            repo.testFailureInterceptor = null
            val successfulAddResult = repo.addStudent(testStudent)
            assertTrue("addStudent must succeed after clearing failure interceptor", successfulAddResult.isSuccess)
            assertNotNull("Student entity must now exist", database.studentDao().getStudentById(studentId))
            assertNotNull("Student profile must now exist", database.studentProfileDao().getProfileById(studentId))
            assertEquals(1, database.cardDao().getCardsForStudent(studentId).size)
            assertEquals(2, database.pendingChangeDao().getPendingCount())

            // -------------------------------------------------------------
            // TEST 3: updateFeeStatus fails midway after local writes
            // -------------------------------------------------------------
            val prePendingCount = database.pendingChangeDao().getPendingCount()
            repo.testFailureInterceptor = { step ->
                if (step == "updateFeeStatus_afterLocalWrites") {
                    throw RuntimeException("SIMULATED_FAILURE: Crash during updateFeeStatus")
                }
            }

            val failedFeeResult = repo.updateFeeStatus(studentId, FeeStatus.OUTSTANDING, 600000.0)
            assertTrue("updateFeeStatus must return failure on injected exception", failedFeeResult.isFailure)

            // CRITICAL VERIFICATION: Fee status remains CLEARED (not updated in DB)
            val studentAfterFailedFee = database.studentDao().getStudentById(studentId)
            assertEquals("Student feesStatus must remain CLEARED due to rollback", FeeStatus.CLEARED.name, studentAfterFailedFee?.feesStatus)
            assertEquals("Student outstandingAmount must remain 0.0", 0.0, studentAfterFailedFee?.outstandingAmount ?: 0.0, 0.01)

            val profileAfterFailedFee = database.studentProfileDao().getProfileById(studentId)
            assertEquals("Profile accessStatus must remain APPROVED due to rollback", AccessStatus.APPROVED.name, profileAfterFailedFee?.accessStatus)

            assertEquals(
                "No orphan FEE_STATUS change must be recorded in pending queue",
                prePendingCount,
                database.pendingChangeDao().getPendingCount()
            )

            // -------------------------------------------------------------
            // TEST 4: issueReplacementCard fails midway
            // -------------------------------------------------------------
            val initialCard = database.cardDao().getCardsForStudent(studentId).first()
            repo.testFailureInterceptor = { step ->
                if (step == "issueReplacementCard_afterLocalWrites") {
                    throw RuntimeException("SIMULATED_FAILURE: Crash during replacement card issuance")
                }
            }

            val failedReplaceResult = repo.issueReplacementCard(studentId, initialCard.id, "Lost card")
            assertTrue("issueReplacementCard must return failure on injected exception", failedReplaceResult.isFailure)

            // Original card must STILL be ACTIVE (not set to REPLACED because of transaction rollback!)
            val reloadedCard = database.cardDao().getCardById(initialCard.id)
            assertEquals("Original card status must remain ACTIVE due to rollback", CardStatus.ACTIVE.name, reloadedCard?.status)
            assertEquals("No new replacement card should exist in DB", 1, database.cardDao().getCardsForStudent(studentId).size)
            assertEquals("Pending changes count must remain unchanged", prePendingCount, database.pendingChangeDao().getPendingCount())

            database.close()
        }
    }

    // =========================================================================
    // PRIORITY 8: ROOM DATABASE MIGRATION INTEGRITY AND GROUNDED UPGRADE PATHS
    // =========================================================================

    @Test
    fun testMigrationFromVersion4To6PreservesAllDataAndAppliesSchemaChanges() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = File(context.filesDir, "test_migration_v4_to_v6.db")
            if (dbFile.exists()) dbFile.delete()

            // 1. Create native SQLite database strictly matching Version 4 schema
            val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            rawDb.version = 4

            // Version 4 students table (notice: NO qrToken column in Version 4!)
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `students` (
                    `id` TEXT NOT NULL,
                    `studentNumber` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `firstName` TEXT NOT NULL,
                    `lastName` TEXT NOT NULL,
                    `photoUrl` TEXT,
                    `accessStatus` TEXT NOT NULL DEFAULT 'APPROVED',
                    `gradeClass` TEXT NOT NULL,
                    `isDayScholar` INTEGER NOT NULL,
                    `dayScholarType` TEXT NOT NULL,
                    `transportRoute` TEXT NOT NULL,
                    `feesStatus` TEXT NOT NULL,
                    `outstandingAmount` REAL NOT NULL,
                    `gender` TEXT NOT NULL,
                    `avatarColorSeed` INTEGER NOT NULL,
                    `guardianName` TEXT NOT NULL,
                    `guardianPhone` TEXT NOT NULL,
                    `emergencyContact` TEXT NOT NULL,
                    `homeroomTeacher` TEXT NOT NULL,
                    `academicYear` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_students_studentNumber` ON `students` (`studentNumber`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_students_accessStatus` ON `students` (`accessStatus`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_students_name` ON `students` (`name`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_students_isDeleted` ON `students` (`isDeleted`)")

            // Version 4 student_profiles table
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `student_profiles` (
                    `id` TEXT NOT NULL,
                    `studentNumber` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `photoUrl` TEXT,
                    `accessStatus` TEXT NOT NULL,
                    `gradeClass` TEXT NOT NULL,
                    `isDayScholar` INTEGER NOT NULL,
                    `isFeesCleared` INTEGER NOT NULL,
                    `outstandingAmount` REAL NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_student_profiles_studentNumber` ON `student_profiles` (`studentNumber`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_student_profiles_accessStatus` ON `student_profiles` (`accessStatus`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_student_profiles_name` ON `student_profiles` (`name`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_student_profiles_updatedAt` ON `student_profiles` (`updatedAt`)")

            // Version 4 cards table
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cards` (
                    `id` TEXT NOT NULL,
                    `cardIdentifier` TEXT NOT NULL,
                    `studentId` TEXT NOT NULL,
                    `studentNumber` TEXT NOT NULL,
                    `qrPayload` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `issueDate` INTEGER NOT NULL,
                    `activationDate` INTEGER NOT NULL,
                    `deactivationDate` INTEGER,
                    `replacedByCardId` TEXT,
                    `reason` TEXT,
                    `notes` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_studentId` ON `cards` (`studentId`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_studentNumber` ON `cards` (`studentNumber`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_status` ON `cards` (`status`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_qrPayload` ON `cards` (`qrPayload`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_cardIdentifier` ON `cards` (`cardIdentifier`)")

            // Version 4 gate_scan_logs table
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `gate_scan_logs` (
                    `id` TEXT NOT NULL,
                    `studentId` TEXT,
                    `studentNumber` TEXT,
                    `studentName` TEXT NOT NULL,
                    `gradeClass` TEXT NOT NULL,
                    `cardId` TEXT,
                    `cardIdentifier` TEXT,
                    `qrPayload` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `decision` TEXT NOT NULL,
                    `feeStatus` TEXT,
                    `cardStatus` TEXT,
                    `isDayScholar` INTEGER NOT NULL,
                    `isApproved` INTEGER NOT NULL,
                    `reason` TEXT NOT NULL,
                    `isOfflineDecision` INTEGER NOT NULL,
                    `dataSyncTimestampAtScan` INTEGER NOT NULL,
                    `guardName` TEXT NOT NULL,
                    `deviceIdentifier` TEXT NOT NULL,
                    `gateLocation` TEXT NOT NULL,
                    `isSyncedToCloud` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_timestamp` ON `gate_scan_logs` (`timestamp`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_studentId` ON `gate_scan_logs` (`studentId`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_studentNumber` ON `gate_scan_logs` (`studentNumber`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_cardId` ON `gate_scan_logs` (`cardId`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_decision` ON `gate_scan_logs` (`decision`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_isSyncedToCloud` ON `gate_scan_logs` (`isSyncedToCloud`)")

            // Version 4 sync_metadata table
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_metadata` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent()
            )

            // Populate pre-existing Version 4 historical records
            val now = System.currentTimeMillis()
            rawDb.execSQL(
                """
                INSERT INTO `students` (
                    id, studentNumber, name, firstName, lastName, photoUrl, accessStatus,
                    gradeClass, isDayScholar, dayScholarType, transportRoute, feesStatus,
                    outstandingAmount, gender, avatarColorSeed, guardianName, guardianPhone,
                    emergencyContact, homeroomTeacher, academicYear, notes, updatedAt, isDeleted
                ) VALUES (
                    'stu-v4-01', 'LTC-2026-0042', 'Emmanuel Okello', 'Emmanuel', 'Okello',
                    'file:///photos/stu-v4-01.jpg', 'APPROVED', 'Senior 3-A', 1,
                    'DAY_SCHOLAR_BUS', 'Bus Route 1', 'CLEARED', 0.0, 'Male', 12345,
                    'Patrick Okello', '+256 772 123456', '+256 772 123456', 'Mr. Obua Denis',
                    '2026', 'Historical pre-migration student', $now, 0
                )
                """.trimIndent()
            )

            rawDb.execSQL(
                """
                INSERT INTO `student_profiles` (
                    id, studentNumber, name, photoUrl, accessStatus, gradeClass,
                    isDayScholar, isFeesCleared, outstandingAmount, updatedAt
                ) VALUES (
                    'stu-v4-01', 'LTC-2026-0042', 'Emmanuel Okello', 'file:///photos/stu-v4-01.jpg',
                    'APPROVED', 'Senior 3-A', 1, 1, 0.0, $now
                )
                """.trimIndent()
            )

            rawDb.execSQL(
                """
                INSERT INTO `cards` (
                    id, cardIdentifier, studentId, studentNumber, qrPayload, status,
                    issueDate, activationDate, deactivationDate, replacedByCardId, reason,
                    notes, updatedAt, isDeleted
                ) VALUES (
                    'crd-v4-01', 'CRD-0042-01', 'stu-v4-01', 'LTC-2026-0042',
                    'LTC:V1:LTC-2026-0042:CRD-0042-01', 'ACTIVE', $now, $now, NULL, NULL,
                    'Historical pilot badge', 'Active badge', $now, 0
                )
                """.trimIndent()
            )

            rawDb.execSQL(
                """
                INSERT INTO `gate_scan_logs` (
                    id, studentId, studentNumber, studentName, gradeClass, cardId,
                    cardIdentifier, qrPayload, timestamp, decision, feeStatus, cardStatus,
                    isDayScholar, isApproved, reason, isOfflineDecision, dataSyncTimestampAtScan,
                    guardName, deviceIdentifier, gateLocation, isSyncedToCloud
                ) VALUES (
                    'log-v4-01', 'stu-v4-01', 'LTC-2026-0042', 'Emmanuel Okello', 'Senior 3-A',
                    'crd-v4-01', 'CRD-0042-01', 'LTC:V1:LTC-2026-0042:CRD-0042-01', $now,
                    'APPROVED', 'CLEARED', 'ACTIVE', 1, 1, 'Entry Approved', 1, $now,
                    'Gate Guard', 'Terminal-01', 'Main Gate', 0
                )
                """.trimIndent()
            )

            rawDb.close()

            // 2. Open through Room with explicit grounded migrations MIGRATION_4_5 and MIGRATION_5_6
            val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build()

            // 3. Verify all pre-existing records survived unchanged
            val migratedStudent = roomDb.studentDao().getStudentById("stu-v4-01")
            assertNotNull("Student record must survive migration from Version 4", migratedStudent)
            assertEquals("Emmanuel Okello", migratedStudent?.name)
            assertEquals("LTC-2026-0042", migratedStudent?.studentNumber)
            assertEquals(FeeStatus.CLEARED.name, migratedStudent?.feesStatus)
            assertEquals("file:///photos/stu-v4-01.jpg", migratedStudent?.photoUrl)
            // Verify new column qrToken has correct default value
            assertEquals("New qrToken column must have default empty string", "", migratedStudent?.qrToken)

            val migratedProfile = roomDb.studentProfileDao().getProfileById("stu-v4-01")
            assertNotNull("Student profile record must survive migration", migratedProfile)
            assertEquals("Emmanuel Okello", migratedProfile?.name)
            assertEquals("APPROVED", migratedProfile?.accessStatus)

            val migratedCards = roomDb.cardDao().getCardsForStudent("stu-v4-01")
            assertEquals("Card record must survive migration", 1, migratedCards.size)
            assertEquals("CRD-0042-01", migratedCards[0].cardIdentifier)
            assertEquals("ACTIVE", migratedCards[0].status)

            val migratedLog = roomDb.scanLogDao().getScanLogById("log-v4-01")
            assertNotNull("Scan log must survive migration", migratedLog)
            assertEquals("APPROVED", migratedLog?.decision)

            // 4. Verify newly created pending_changes table is fully functional
            val newChange = com.example.data.local.PendingChangeEntity(
                changeId = "change-v6-post-migration-01",
                entityType = SyncEntityType.STUDENT,
                recordId = "stu-v4-01",
                operationType = SyncOperationType.UPDATE,
                createdAt = System.currentTimeMillis()
            )
            roomDb.pendingChangeDao().enqueueChange(newChange)
            val pendingChanges = roomDb.pendingChangeDao().getPendingChanges()
            assertEquals("pending_changes queue must be queryable and contain enqueued change", 1, pendingChanges.size)
            assertEquals("change-v6-post-migration-01", pendingChanges[0].changeId)

            // 5. Close and reopen to verify stability across sessions
            roomDb.close()

            val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build()

            val reopenedStudent = reopenedDb.studentDao().getStudentById("stu-v4-01")
            assertNotNull("Reopened database must retain migrated student", reopenedStudent)
            assertEquals("Emmanuel Okello", reopenedStudent?.name)
            assertEquals(1, reopenedDb.pendingChangeDao().getPendingChanges().size)

            reopenedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun testMigrationFromVersion5To6PreservesQrTokenAndCreatesPendingChangesTable() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = File(context.filesDir, "test_migration_v5_to_v6.db")
            if (dbFile.exists()) dbFile.delete()

            // 1. Create native SQLite database strictly matching Version 5 schema (has qrToken, no pending_changes)
            val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            rawDb.version = 5

            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `students` (
                    `id` TEXT NOT NULL,
                    `studentNumber` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `firstName` TEXT NOT NULL,
                    `lastName` TEXT NOT NULL,
                    `photoUrl` TEXT,
                    `accessStatus` TEXT NOT NULL DEFAULT 'APPROVED',
                    `gradeClass` TEXT NOT NULL,
                    `isDayScholar` INTEGER NOT NULL,
                    `dayScholarType` TEXT NOT NULL,
                    `transportRoute` TEXT NOT NULL,
                    `feesStatus` TEXT NOT NULL,
                    `outstandingAmount` REAL NOT NULL,
                    `gender` TEXT NOT NULL,
                    `avatarColorSeed` INTEGER NOT NULL,
                    `guardianName` TEXT NOT NULL,
                    `guardianPhone` TEXT NOT NULL,
                    `emergencyContact` TEXT NOT NULL,
                    `homeroomTeacher` TEXT NOT NULL,
                    `academicYear` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `qrToken` TEXT NOT NULL DEFAULT '',
                    `updatedAt` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_students_studentNumber` ON `students` (`studentNumber`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_students_accessStatus` ON `students` (`accessStatus`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_students_name` ON `students` (`name`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_students_isDeleted` ON `students` (`isDeleted`)")

            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `student_profiles` (
                    `id` TEXT NOT NULL,
                    `studentNumber` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `photoUrl` TEXT,
                    `accessStatus` TEXT NOT NULL,
                    `gradeClass` TEXT NOT NULL,
                    `isDayScholar` INTEGER NOT NULL,
                    `isFeesCleared` INTEGER NOT NULL,
                    `outstandingAmount` REAL NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_student_profiles_studentNumber` ON `student_profiles` (`studentNumber`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_student_profiles_accessStatus` ON `student_profiles` (`accessStatus`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_student_profiles_name` ON `student_profiles` (`name`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_student_profiles_updatedAt` ON `student_profiles` (`updatedAt`)")

            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cards` (
                    `id` TEXT NOT NULL,
                    `cardIdentifier` TEXT NOT NULL,
                    `studentId` TEXT NOT NULL,
                    `studentNumber` TEXT NOT NULL,
                    `qrPayload` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `issueDate` INTEGER NOT NULL,
                    `activationDate` INTEGER NOT NULL,
                    `deactivationDate` INTEGER,
                    `replacedByCardId` TEXT,
                    `reason` TEXT,
                    `notes` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_studentId` ON `cards` (`studentId`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_studentNumber` ON `cards` (`studentNumber`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_status` ON `cards` (`status`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_qrPayload` ON `cards` (`qrPayload`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_cardIdentifier` ON `cards` (`cardIdentifier`)")

            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `gate_scan_logs` (
                    `id` TEXT NOT NULL,
                    `studentId` TEXT,
                    `studentNumber` TEXT,
                    `studentName` TEXT NOT NULL,
                    `gradeClass` TEXT NOT NULL,
                    `cardId` TEXT,
                    `cardIdentifier` TEXT,
                    `qrPayload` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `decision` TEXT NOT NULL,
                    `feeStatus` TEXT,
                    `cardStatus` TEXT,
                    `isDayScholar` INTEGER NOT NULL,
                    `isApproved` INTEGER NOT NULL,
                    `reason` TEXT NOT NULL,
                    `isOfflineDecision` INTEGER NOT NULL,
                    `dataSyncTimestampAtScan` INTEGER NOT NULL,
                    `guardName` TEXT NOT NULL,
                    `deviceIdentifier` TEXT NOT NULL,
                    `gateLocation` TEXT NOT NULL,
                    `isSyncedToCloud` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_timestamp` ON `gate_scan_logs` (`timestamp`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_studentId` ON `gate_scan_logs` (`studentId`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_studentNumber` ON `gate_scan_logs` (`studentNumber`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_cardId` ON `gate_scan_logs` (`cardId`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_decision` ON `gate_scan_logs` (`decision`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_isSyncedToCloud` ON `gate_scan_logs` (`isSyncedToCloud`)")

            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_metadata` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent()
            )

            // Insert student record with pre-existing populated qrToken in Version 5
            val now = System.currentTimeMillis()
            rawDb.execSQL(
                """
                INSERT INTO `students` (
                    id, studentNumber, name, firstName, lastName, photoUrl, accessStatus,
                    gradeClass, isDayScholar, dayScholarType, transportRoute, feesStatus,
                    outstandingAmount, gender, avatarColorSeed, guardianName, guardianPhone,
                    emergencyContact, homeroomTeacher, academicYear, notes, qrToken, updatedAt, isDeleted
                ) VALUES (
                    'stu-v5-01', 'LTC-2026-0099', 'Sarah Akello', 'Sarah', 'Akello',
                    NULL, 'APPROVED', 'Senior 4-B', 1, 'DAY_SCHOLAR_WALK', 'Walking',
                    'CLEARED', 0.0, 'Female', 9876, 'Mary Akello', '+256 782 234567',
                    '+256 782 234567', 'Ms. Aceng Betty', '2026', 'Existing v5 student',
                    'LTC_SPECIAL_QR_TOKEN_99', $now, 0
                )
                """.trimIndent()
            )
            rawDb.close()

            // 2. Open with Room specifying MIGRATION_5_6
            val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build()

            // 3. Verify student and existing qrToken preserved intact
            val student = roomDb.studentDao().getStudentById("stu-v5-01")
            assertNotNull(student)
            assertEquals("LTC_SPECIAL_QR_TOKEN_99", student?.qrToken)

            // 4. Verify pending_changes table was created cleanly and works
            assertEquals(0, roomDb.pendingChangeDao().getPendingCount())
            val testChange = com.example.data.local.PendingChangeEntity(
                changeId = "chg-v5-to-v6-01",
                entityType = SyncEntityType.CARD,
                recordId = "crd-v5-01",
                operationType = SyncOperationType.CREATE,
                createdAt = now
            )
            roomDb.pendingChangeDao().enqueueChange(testChange)
            assertEquals(1, roomDb.pendingChangeDao().getPendingCount())

            roomDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun testMissingMigrationForUnsupportedVersionsFailsSafelyWithoutDestructiveWipe() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_unsupported_v3.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Create a database claiming user_version = 3 (unsupported historical version)
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.version = 3
        rawDb.execSQL("CREATE TABLE IF NOT EXISTS `sample_user_data` (`id` TEXT PRIMARY KEY, `data` TEXT)")
        rawDb.execSQL("INSERT INTO `sample_user_data` VALUES ('id-123', 'CRITICAL_USER_DATA_THAT_MUST_NOT_BE_DESTROYED')")
        rawDb.close()

        // 2. Attempt to open through Room configured with migrations 4->5 and 5->6 only (no 3->X path)
        var migrationException: Throwable? = null
        try {
            val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build()

            // Trigger database opening and migration verification
            roomDb.openHelper.writableDatabase
            roomDb.close()
        } catch (e: Throwable) {
            migrationException = e
        }

        // 3. Room MUST throw IllegalStateException indicating missing migration path
        assertNotNull("Room must fail when attempting to upgrade from unsupported version without explicit migration", migrationException)
        assertTrue(
            "Exception must be IllegalStateException mentioning missing migration: ${migrationException?.message}",
            migrationException is IllegalStateException && migrationException.message?.contains("migration") == true
        )

        // 4. CRITICAL SAFETY CHECK: Verify pre-existing data was NOT destructively wiped
        val verifyDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        val cursor = verifyDb.rawQuery("SELECT data FROM sample_user_data WHERE id = 'id-123'", null)
        assertTrue("Pre-existing table and data must NOT be destructively dropped on migration failure", cursor.moveToFirst())
        assertEquals("CRITICAL_USER_DATA_THAT_MUST_NOT_BE_DESTROYED", cursor.getString(0))
        cursor.close()
        verifyDb.close()

        dbFile.delete()
    }

    @Test
    fun testMigratedDatabaseSchemaAndIndexesMatchExpectedStructure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_schema_audit.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Create Version 4 SQLite database
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.version = 4
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `students` (
                `id` TEXT NOT NULL,
                `studentNumber` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `firstName` TEXT NOT NULL,
                `lastName` TEXT NOT NULL,
                `photoUrl` TEXT,
                `accessStatus` TEXT NOT NULL DEFAULT 'APPROVED',
                `gradeClass` TEXT NOT NULL,
                `isDayScholar` INTEGER NOT NULL,
                `dayScholarType` TEXT NOT NULL,
                `transportRoute` TEXT NOT NULL,
                `feesStatus` TEXT NOT NULL,
                `outstandingAmount` REAL NOT NULL,
                `gender` TEXT NOT NULL,
                `avatarColorSeed` INTEGER NOT NULL,
                `guardianName` TEXT NOT NULL,
                `guardianPhone` TEXT NOT NULL,
                `emergencyContact` TEXT NOT NULL,
                `homeroomTeacher` TEXT NOT NULL,
                `academicYear` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_students_studentNumber` ON `students` (`studentNumber`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_students_accessStatus` ON `students` (`accessStatus`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_students_name` ON `students` (`name`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_students_isDeleted` ON `students` (`isDeleted`)")

        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `student_profiles` (
                `id` TEXT NOT NULL,
                `studentNumber` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `photoUrl` TEXT,
                `accessStatus` TEXT NOT NULL,
                `gradeClass` TEXT NOT NULL,
                `isDayScholar` INTEGER NOT NULL,
                `isFeesCleared` INTEGER NOT NULL,
                `outstandingAmount` REAL NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_student_profiles_studentNumber` ON `student_profiles` (`studentNumber`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_student_profiles_accessStatus` ON `student_profiles` (`accessStatus`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_student_profiles_name` ON `student_profiles` (`name`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_student_profiles_updatedAt` ON `student_profiles` (`updatedAt`)")

        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cards` (
                `id` TEXT NOT NULL,
                `cardIdentifier` TEXT NOT NULL,
                `studentId` TEXT NOT NULL,
                `studentNumber` TEXT NOT NULL,
                `qrPayload` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `issueDate` INTEGER NOT NULL,
                `activationDate` INTEGER NOT NULL,
                `deactivationDate` INTEGER,
                `replacedByCardId` TEXT,
                `reason` TEXT,
                `notes` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_studentId` ON `cards` (`studentId`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_studentNumber` ON `cards` (`studentNumber`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_status` ON `cards` (`status`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_qrPayload` ON `cards` (`qrPayload`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_cardIdentifier` ON `cards` (`cardIdentifier`)")

        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `gate_scan_logs` (
                `id` TEXT NOT NULL,
                `studentId` TEXT,
                `studentNumber` TEXT,
                `studentName` TEXT NOT NULL,
                `gradeClass` TEXT NOT NULL,
                `cardId` TEXT,
                `cardIdentifier` TEXT,
                `qrPayload` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `decision` TEXT NOT NULL,
                `feeStatus` TEXT,
                `cardStatus` TEXT,
                `isDayScholar` INTEGER NOT NULL,
                `isApproved` INTEGER NOT NULL,
                `reason` TEXT NOT NULL,
                `isOfflineDecision` INTEGER NOT NULL,
                `dataSyncTimestampAtScan` INTEGER NOT NULL,
                `guardName` TEXT NOT NULL,
                `deviceIdentifier` TEXT NOT NULL,
                `gateLocation` TEXT NOT NULL,
                `isSyncedToCloud` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_timestamp` ON `gate_scan_logs` (`timestamp`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_studentId` ON `gate_scan_logs` (`studentId`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_studentNumber` ON `gate_scan_logs` (`studentNumber`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_cardId` ON `gate_scan_logs` (`cardId`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_decision` ON `gate_scan_logs` (`decision`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_scan_logs_isSyncedToCloud` ON `gate_scan_logs` (`isSyncedToCloud`)")

        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_metadata` (
                `key` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`key`)
            )
            """.trimIndent()
        )
        rawDb.close()

        // 2. Perform Room migration to Version 6
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        val db = roomDb.openHelper.readableDatabase

        // 3. Inspect PRAGMA table_info('students') for added qrToken column
        val studentsColumns = mutableMapOf<String, String>()
        val studentsNotnull = mutableMapOf<String, Int>()
        val studentsDefaults = mutableMapOf<String, String?>()
        val studentCursor = db.query("PRAGMA table_info(`students`)")
        while (studentCursor.moveToNext()) {
            val name = studentCursor.getString(1)
            val type = studentCursor.getString(2)
            val notnull = studentCursor.getInt(3)
            val dflt = studentCursor.getString(4)
            studentsColumns[name] = type
            studentsNotnull[name] = notnull
            studentsDefaults[name] = dflt
        }
        studentCursor.close()

        assertTrue("students table must contain qrToken column after migration", studentsColumns.containsKey("qrToken"))
        assertEquals("qrToken column must be of type TEXT", "TEXT", studentsColumns["qrToken"])
        assertEquals("qrToken column must be NOT NULL", 1, studentsNotnull["qrToken"])

        // 4. Inspect PRAGMA table_info('pending_changes')
        val pendingColumns = mutableMapOf<String, String>()
        val pendingCursor = db.query("PRAGMA table_info(`pending_changes`)")
        while (pendingCursor.moveToNext()) {
            val name = pendingCursor.getString(1)
            val type = pendingCursor.getString(2)
            pendingColumns[name] = type
        }
        pendingCursor.close()

        val expectedPendingCols = listOf(
            "changeId", "entityType", "recordId", "operationType",
            "payloadJson", "status", "retryCount", "lastError",
            "createdAt", "lastAttemptAt"
        )
        for (col in expectedPendingCols) {
            assertTrue("pending_changes must contain column $col", pendingColumns.containsKey(col))
        }

        // 5. Inspect PRAGMA index_list('pending_changes')
        val indexList = mutableListOf<String>()
        val indexCursor = db.query("PRAGMA index_list(`pending_changes`)")
        while (indexCursor.moveToNext()) {
            indexList.add(indexCursor.getString(1))
        }
        indexCursor.close()

        assertTrue("Index on status must exist", indexList.contains("index_pending_changes_status"))
        assertTrue("Index on createdAt must exist", indexList.contains("index_pending_changes_createdAt"))
        assertTrue("Index on (entityType, recordId) must exist", indexList.contains("index_pending_changes_entityType_recordId"))

        // 6. Test idempotent execution of MIGRATION_5_6 DDL
        roomDb.openHelper.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS `pending_changes` (`changeId` TEXT PRIMARY KEY)")
        roomDb.openHelper.writableDatabase.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_changes_status` ON `pending_changes` (`status`)")

        roomDb.close()
        dbFile.delete()
    }

    // =========================================================================
    // PRIORITY 9: STAFF AUTHENTICATION & ROLE AUTHORIZATION VERIFICATION
    // =========================================================================

    @Test
    fun testUnprovisionedTerminalRejectsAllAuthenticationAndDisallowsUniversal2026Default() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecurityManager.resetForTesting(context)

        // 1. Verify terminal is unprovisioned
        assertFalse("Unprovisioned terminal must return isProvisioned = false", SecurityManager.isProvisioned(context))

        // 2. CRITICAL: Default master PIN '2026' must NOT work as an unauthenticated backdoor
        assertFalse("Universal default PIN '2026' must be rejected when unprovisioned", SecurityManager.verifyAdminPin(context, "2026"))
        assertFalse("Gate staff access with '2026' must be rejected when unprovisioned", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, "2026"))
        assertFalse("Supervisor override with '2026' must be rejected when unprovisioned", SecurityManager.verifySupervisorOverridePin(context, "2026"))
        assertFalse("Bursar access with '0000' must be rejected when unprovisioned", SecurityManager.verifyRolePin(context, UserRole.BURSAR_FINANCE, "0000"))
    }

    @Test
    fun testStaffProvisioningRoleAuthenticationAndSeparation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecurityManager.resetForTesting(context)

        // 1. First-run administrator provisioning
        val provisionSuccess = SecurityManager.provisionInitialAdmin(context, "Principal Denis", "8822")
        assertTrue("Provisioning initial admin must succeed", provisionSuccess)
        assertTrue("Terminal must now be provisioned", SecurityManager.isProvisioned(context))
        assertEquals("Principal Denis", SecurityManager.getAdminName(context))

        // 2. Valid and invalid PIN verification
        assertTrue("Admin master PIN '8822' must be verified", SecurityManager.verifyAdminPin(context, "8822"))
        assertFalse("Invalid PIN '1234' must be rejected", SecurityManager.verifyAdminPin(context, "1234"))
        assertFalse("Old default PIN '2026' must be rejected", SecurityManager.verifyAdminPin(context, "2026"))

        // 3. Gate Staff: before dedicated role PIN is set, Admin PIN authenticates
        assertTrue("Master PIN can authenticate Gate Staff role", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, "8822"))
        assertFalse("Random PIN must be rejected for Gate Staff", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, "9999"))

        // 4. Configure dedicated Gate Staff PIN
        val setGatePinSuccess = SecurityManager.setRolePin(context, UserRole.GATE_STAFF, "3344")
        assertTrue(setGatePinSuccess)
        assertTrue("Dedicated Gate Staff PIN must authenticate Gate Staff", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, "3344"))

        // 5. Role Isolation: Gate Staff PIN CANNOT access Bursar or Admin roles
        assertFalse("Gate Staff PIN '3344' must NOT authenticate Bursar role", SecurityManager.verifyRolePin(context, UserRole.BURSAR_FINANCE, "3344"))
        assertFalse("Gate Staff PIN '3344' must NOT authenticate Administrator role", SecurityManager.verifyAdminPin(context, "3344"))

        // 6. Supervisor Override PIN configuration and verification
        val setSupPinSuccess = SecurityManager.setSupervisorPin(context, "7711")
        assertTrue(setSupPinSuccess)
        assertTrue("Supervisor PIN must authenticate supervisor override", SecurityManager.verifySupervisorOverridePin(context, "7711"))
        assertFalse("Invalid PIN '0000' must fail supervisor override", SecurityManager.verifySupervisorOverridePin(context, "0000"))
    }

    @Test
    fun testLeastPrivilegeSevenRoleDutyEnforcement() {
        runBlocking {
            val repository = MockStudentRepository()
            val targetStudent = Student(
                id = "sec-stu-01",
                studentNumber = "LTC-2026-0777",
                firstName = "Joan",
                lastName = "Acan",
                gradeClass = "Senior 2-A",
                feesStatus = FeeStatus.OUTSTANDING,
                outstandingAmount = 200000.0,
                accessStatus = AccessStatus.APPROVED
            )
            repository.addStudent(targetStudent)
            val viewModel = MainViewModel(repository)

            // 1. Verify default permissions matrix across all 7 canonical roles
            assertFalse("Administrator must NOT have MANAGE_FEES by default", UserRole.ADMINISTRATOR.defaultPermissions.contains(StaffPermission.MANAGE_FEES))
            assertTrue("Administrator must have MANAGE_STUDENTS", UserRole.ADMINISTRATOR.defaultPermissions.contains(StaffPermission.MANAGE_STUDENTS))
            assertTrue("Administrator must have SYSTEM_CONFIGURATION", UserRole.ADMINISTRATOR.defaultPermissions.contains(StaffPermission.SYSTEM_CONFIGURATION))

            assertTrue("Bursar must have MANAGE_FEES", UserRole.BURSAR_FINANCE.defaultPermissions.contains(StaffPermission.MANAGE_FEES))
            assertFalse("Bursar must NOT have MANAGE_STUDENTS", UserRole.BURSAR_FINANCE.defaultPermissions.contains(StaffPermission.MANAGE_STUDENTS))

            assertTrue("Gate Staff must have VERIFY_GATE_ACCESS", UserRole.GATE_STAFF.defaultPermissions.contains(StaffPermission.VERIFY_GATE_ACCESS))
            assertFalse("Gate Staff must NOT have MANAGE_FEES", UserRole.GATE_STAFF.defaultPermissions.contains(StaffPermission.MANAGE_FEES))

            assertTrue("Meals Staff must have SERVE_MEALS", UserRole.MEAL_SERVING_STAFF.defaultPermissions.contains(StaffPermission.SERVE_MEALS))
            assertFalse("Meals Staff must NOT have MANAGE_FEES", UserRole.MEAL_SERVING_STAFF.defaultPermissions.contains(StaffPermission.MANAGE_FEES))

            assertTrue("Teachers must have VIEW_AUDIT_LOGS", UserRole.TEACHERS.defaultPermissions.contains(StaffPermission.VIEW_AUDIT_LOGS))
            assertFalse("Teachers must NOT have MANAGE_STUDENTS", UserRole.TEACHERS.defaultPermissions.contains(StaffPermission.MANAGE_STUDENTS))

            assertTrue("Exams Staff must have EXAM_CLEARANCE", UserRole.EXAMINATION_STAFF.defaultPermissions.contains(StaffPermission.EXAM_CLEARANCE))
            assertFalse("Exams Staff must NOT have MANAGE_FEES", UserRole.EXAMINATION_STAFF.defaultPermissions.contains(StaffPermission.MANAGE_FEES))

            assertTrue("Head Teacher must have EMERGENCY_OVERRIDE", UserRole.HEAD_TEACHER_MANAGEMENT.defaultPermissions.contains(StaffPermission.EMERGENCY_OVERRIDE))
            assertFalse("Head Teacher must NOT have MANAGE_FEES", UserRole.HEAD_TEACHER_MANAGEMENT.defaultPermissions.contains(StaffPermission.MANAGE_FEES))

            // 2. Unauthenticated session: all sensitive operations blocked
            viewModel.logout()
            assertNull(viewModel.currentUser.value)

            viewModel.updateFeeStatus(targetStudent.id, FeeStatus.CLEARED, 0.0)
            assertTrue("Unauthenticated fee change must be denied", viewModel.userFeedbackMessage.value?.contains("Unauthenticated session") == true)

            viewModel.deleteStudentRecord(targetStudent.id)
            assertTrue("Unauthenticated deletion must be denied", viewModel.userFeedbackMessage.value?.contains("Unauthenticated session") == true)

            // 3. Administrator without explicit finance clearance: student ops allowed, fee change denied
            viewModel.loginAs(UserRole.ADMINISTRATOR, hasFinancePermission = false)
            assertFalse(viewModel.currentUser.value!!.hasPermission(StaffPermission.MANAGE_FEES))
            assertTrue(viewModel.currentUser.value!!.hasPermission(StaffPermission.MANAGE_STUDENTS))

            viewModel.updateFeeStatus(targetStudent.id, FeeStatus.CLEARED, 0.0)
            assertTrue("Admin without explicit finance permission must be blocked from modifying fees", viewModel.userFeedbackMessage.value?.contains("Bursar / Finance") == true)

            // 4. Administrator with explicit finance clearance: fee change allowed
            viewModel.loginAs(UserRole.ADMINISTRATOR, hasFinancePermission = true)
            assertTrue(viewModel.currentUser.value!!.hasPermission(StaffPermission.MANAGE_FEES))

            viewModel.updateFeeStatus(targetStudent.id, FeeStatus.CLEARED, 0.0)
            assertTrue("Admin with explicit finance permission must succeed", viewModel.userFeedbackMessage.value?.contains("CLEARED") == true)

            // 5. Bursar / Finance: fee updates allowed, student registry deletions denied
            viewModel.loginAs(UserRole.BURSAR_FINANCE)
            assertTrue(viewModel.currentUser.value!!.hasPermission(StaffPermission.MANAGE_FEES))
            assertFalse(viewModel.currentUser.value!!.hasPermission(StaffPermission.MANAGE_STUDENTS))

            viewModel.updateFeeStatus(targetStudent.id, FeeStatus.OUTSTANDING, 300000.0)
            assertTrue("Bursar fee update must succeed", viewModel.userFeedbackMessage.value?.contains("OUTSTANDING") == true)

            viewModel.deleteStudentRecord(targetStudent.id)
            assertTrue("Bursar must not be allowed to delete students", viewModel.userFeedbackMessage.value?.contains("Administrator privileges") == true)

            // 6. Gate Staff: fee updates and student deletions denied
            viewModel.loginAs(UserRole.GATE_STAFF)
            viewModel.updateFeeStatus(targetStudent.id, FeeStatus.CLEARED, 0.0)
            assertTrue("Gate Staff cannot modify fees", viewModel.userFeedbackMessage.value?.contains("Bursar / Finance") == true)

            viewModel.deleteStudentRecord(targetStudent.id)
            assertTrue("Gate Staff cannot delete students", viewModel.userFeedbackMessage.value?.contains("Administrator privileges") == true)
        }
    }

    @Test
    fun testUnauthenticatedUsersCannotEnterAnyProtectedDutyModeOrPerformOperations() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            SecurityManager.resetForTesting(context)
            assertFalse(SecurityManager.isProvisioned(context))

            val repository = MockStudentRepository()
            val student = Student(
                id = "sec-unauth-01",
                studentNumber = "LTC-2026-0901",
                firstName = "David",
                lastName = "Okello",
                gradeClass = "Senior 3-C",
                feesStatus = FeeStatus.CLEARED
            )
            repository.addStudent(student)
            val viewModel = MainViewModel(repository)

            // 1. Terminal is unprovisioned: all login attempts must fail
            val unauthLogin = viewModel.authenticateAndLogin(context, UserRole.GATE_STAFF, "1234")
            assertFalse("Unprovisioned login must fail", unauthLogin)
            assertNull("Current user must remain null", viewModel.currentUser.value)

            // 2. Unauthenticated calls to sensitive methods are blocked
            viewModel.handleBarcodeScan("LTC:STU:LTC-2026-0901", context)
            assertTrue("Unauthenticated scan must be denied", viewModel.scanError.value?.contains("Access Denied") == true)

            viewModel.updateFeeStatus(student.id, FeeStatus.OUTSTANDING, 100000.0)
            assertTrue("Unauthenticated fee modification must be denied", viewModel.userFeedbackMessage.value?.contains("Unauthenticated session") == true)

            var regSuccess = false
            viewModel.registerNewStudent(student.copy(id = "new-id", studentNumber = "LTC-2026-0902")) { success, _ ->
                regSuccess = success
            }
            assertFalse("Unauthenticated student registration must be denied", regSuccess)

            viewModel.deleteStudentRecord(student.id)
            assertTrue("Unauthenticated student deletion must be denied", viewModel.userFeedbackMessage.value?.contains("Unauthenticated session") == true)

            viewModel.verifyAndServeMeal("LTC:STU:LTC-2026-0901", context)
            assertTrue("Unauthenticated meal serving must be denied", viewModel.userFeedbackMessage.value?.contains("Unauthenticated session") == true)

            viewModel.toggleRequirementItem(student.id, "uniform")
            assertTrue("Unauthenticated requirements modification must be denied", viewModel.userFeedbackMessage.value?.contains("Unauthenticated session") == true)

            viewModel.clearLogs()
            assertTrue("Unauthenticated clearing logs must be denied", viewModel.userFeedbackMessage.value?.contains("Unauthenticated session") == true)

            viewModel.exportGateLogsCsv(context)
            assertTrue("Unauthenticated export must be denied", viewModel.userFeedbackMessage.value?.contains("Unauthenticated session") == true)
        }
    }

    @Test
    fun testGateKeeperCannotBypassAuthenticationAndRoleSwitchingRequiresCredentials() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            SecurityManager.resetForTesting(context)
            SecurityManager.provisionInitialAdmin(context, "Head Teacher", "9911")
            SecurityManager.setRolePin(context, UserRole.GATE_STAFF, "4422")
            SecurityManager.setRolePin(context, UserRole.BURSAR_FINANCE, "5533")

            val repository = MockStudentRepository()
            val viewModel = MainViewModel(repository)

            // 1. Gate Keeper CANNOT bypass authentication: wrong PINs rejected
            assertFalse("Empty PIN must be rejected", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, ""))
            assertFalse("Short PIN must be rejected", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, "12"))
            assertFalse("Wrong PIN must be rejected", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, "0000"))
            assertFalse("Default 2026 PIN must be rejected", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, "2026"))

            val loginFailed = viewModel.authenticateAndLogin(context, UserRole.GATE_STAFF, "0000")
            assertFalse("Authenticate with wrong PIN must fail", loginFailed)
            assertNull(viewModel.currentUser.value)

            // 2. Gate Keeper authenticates with correct PIN
            val loginSuccess = viewModel.authenticateAndLogin(context, UserRole.GATE_STAFF, "4422", "Officer Ronald")
            assertTrue("Authenticate with correct PIN must succeed", loginSuccess)
            assertEquals(UserRole.GATE_STAFF, viewModel.currentUser.value?.role)
            assertEquals("Officer Ronald", viewModel.currentUser.value?.name)

            // 3. User cannot switch role merely by request: switching to Bursar with wrong PIN is blocked
            val switchFailed = viewModel.authenticateAndLogin(context, UserRole.BURSAR_FINANCE, "4422") // using gate PIN
            assertFalse("Cannot switch to Bursar using Gate Staff PIN", switchFailed)
            // Current role is unchanged
            assertEquals(UserRole.GATE_STAFF, viewModel.currentUser.value?.role)

            // 4. Switching to Bursar with correct Bursar PIN succeeds
            val switchSuccess = viewModel.authenticateAndLogin(context, UserRole.BURSAR_FINANCE, "5533", "Bursar Florence")
            assertTrue("Switching to Bursar with correct PIN succeeds", switchSuccess)
            assertEquals(UserRole.BURSAR_FINANCE, viewModel.currentUser.value?.role)
            assertEquals("Bursar Florence", viewModel.currentUser.value?.name)
        }
    }

    @Test
    fun testTerminalSessionLockingAndBackgroundProtection() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            SecurityManager.resetForTesting(context)
            SecurityManager.provisionInitialAdmin(context, "Admin", "6655")

            val repository = MockStudentRepository()
            val viewModel = MainViewModel(repository)

            // Authenticate session
            viewModel.authenticateAndLogin(context, UserRole.ADMINISTRATOR, "6655")
            assertNotNull(viewModel.currentUser.value)

            // Explicit lock session
            viewModel.lockSession()
            assertNull("Explicit session lock must clear current user", viewModel.currentUser.value)

            // Re-authenticate
            viewModel.authenticateAndLogin(context, UserRole.ADMINISTRATOR, "6655")
            assertNotNull(viewModel.currentUser.value)

            // Background lifecycle lock
            viewModel.lockSessionOnBackground()
            assertNull("Background lifecycle trigger must lock terminal session", viewModel.currentUser.value)
        }
    }

    @Test
    fun testInvalidCredentialsAreRejectedAndDoNotCreateSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecurityManager.resetForTesting(context)
        SecurityManager.provisionInitialAdmin(context, "Administrator", "1234")

        // Invalid credentials tests
        assertFalse("3-digit PIN cannot be set", SecurityManager.setRolePin(context, UserRole.GATE_STAFF, "123"))
        assertFalse("Non-numeric PIN cannot be set", SecurityManager.setRolePin(context, UserRole.GATE_STAFF, "abcd"))
        assertFalse("Wrong PIN rejected", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, "9999"))
        assertFalse("Old default 2026 rejected", SecurityManager.verifyRolePin(context, UserRole.GATE_STAFF, "2026"))
    }
}
