package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MockStudentRepository
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog
import com.example.model.Student
import com.example.model.StudentScanResult
import com.example.model.UserRole
import com.example.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var repository: MockStudentRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        repository = MockStudentRepository.getInstance()
        runBlocking {
            repository.resetToSampleData()
            repository.clearScanLogs()

            val s1 = Student(
                id = "stu-001",
                studentNumber = "OAK-2026-0001",
                firstName = "Michael",
                lastName = "Kasule",
                gradeClass = "Senior 4-A",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
                feesStatus = FeeStatus.CLEARED,
                outstandingAmount = 0.0,
                accessStatus = com.example.model.AccessStatus.APPROVED
            )
            val s2 = Student(
                id = "stu-002",
                studentNumber = "OAK-2026-0002",
                firstName = "Sophia",
                lastName = "Nanteza",
                gradeClass = "Senior 3-B",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
                feesStatus = FeeStatus.OUTSTANDING,
                outstandingAmount = 450000.0,
                accessStatus = com.example.model.AccessStatus.RESTRICTED_FEES
            )
            repository.addStudent(s1)
            repository.addStudent(s2)
            repository.issueCard(s1.id, "CARD-0001", "Initial card")
            repository.issueCard(s2.id, "CARD-0002", "Initial card")
        }
        viewModel = MainViewModel(repository)
    }

    @Test
    fun testAppNameString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LTC Student Access", appName)
    }

    @Test
    fun testStudentQrLookup() = runBlocking {
        val student = repository.getStudentByStudentNumber("OAK-2026-0001")!!
        val card = repository.getActiveCardForStudent(student.id)!!

        // Legacy format must be rejected in V2 production path
        val legacyScan = repository.verifyStudentByQr("OAKRIDGE:STU:OAK-2026-0001")
        assertTrue("Legacy OAKRIDGE format must be rejected", legacyScan is StudentScanResult.InvalidQr)

        // Authenticated V2 payload scan
        val scanResult = repository.verifyStudentByQr(card.qrPayload)
        assertTrue(scanResult is StudentScanResult.Success)
        val verifiedStudent = (scanResult as StudentScanResult.Success).student
        assertNotNull(verifiedStudent)
        assertEquals("Michael", verifiedStudent.firstName)
        assertEquals("Senior 4-A", verifiedStudent.gradeClass)
        assertTrue(verifiedStudent.isDayScholar)
        assertEquals(FeeStatus.CLEARED, verifiedStudent.feesStatus)
        assertTrue("Fees cleared student must be approved for entry", scanResult.isApproved)
    }

    @Test
    fun testOutstandingStudentEntryNotApproved() = runBlocking {
        val student = repository.getStudentByStudentNumber("OAK-2026-0002")!!
        val card = repository.getActiveCardForStudent(student.id)!!

        val scanResult = repository.verifyStudentByQr(card.qrPayload)
        assertTrue(scanResult is StudentScanResult.Success)
        val success = scanResult as StudentScanResult.Success
        assertEquals("Sophia", success.student.firstName)
        assertEquals(FeeStatus.OUTSTANDING, success.student.feesStatus)
        assertFalse("Outstanding fee student must NOT be approved for entry", success.isApproved)
    }

    @Test
    fun testCardLifecycle_ReportLostAndReplacement() = runBlocking {
        val studentNumber = "OAK-2026-0001"
        val student = repository.getStudentByStudentNumber(studentNumber)
        assertNotNull(student)

        val activeCard = repository.getActiveCardForStudent(student!!.id)
        assertNotNull(activeCard)
        assertEquals(CardStatus.ACTIVE, activeCard!!.status)

        // 1. Initial scan is approved with authentic V2 card
        val scanResult1 = repository.verifyStudentByQr(activeCard.qrPayload)
        assertTrue(scanResult1 is StudentScanResult.Success && scanResult1.isApproved)

        // 2. Report card lost
        val reportResult = repository.reportCardLost(student.id, activeCard.id, "Student lost wallet on campus")
        assertTrue(reportResult.isSuccess)

        // 3. Scan now returns CardInactive (Denied entry) - NO fallback to any other card!
        val scanResult2 = repository.verifyStudentByQr(activeCard.qrPayload)
        assertTrue("Scanning lost card must yield CardInactive", scanResult2 is StudentScanResult.CardInactive)
        val inactiveResult = scanResult2 as StudentScanResult.CardInactive
        assertEquals(CardStatus.LOST, inactiveResult.cardStatus)

        // 4. Issue replacement card
        val replacementResult = repository.issueReplacementCard(student.id, activeCard.id, "Badge replacement")
        assertTrue(replacementResult.isSuccess)
        val newCard = replacementResult.getOrThrow()
        assertEquals(CardStatus.ACTIVE, newCard.status)

        // 5. Scan succeeds with newly issued replacement card
        val scanResult3 = repository.verifyStudentByQr(newCard.qrPayload)
        assertTrue(scanResult3 is StudentScanResult.Success && scanResult3.isApproved)

        // 6. Old lost card remains inactive and cannot gain access
        val oldCardRescan = repository.verifyStudentByQr(activeCard.qrPayload)
        assertTrue(oldCardRescan is StudentScanResult.CardInactive)
    }

    @Test
    fun testFeeStatusUpdateImmediatelyAffectsApproval() = runBlocking {
        val studentNumber = "OAK-2026-0002"
        val initialStudent = repository.getStudentByStudentNumber(studentNumber)
        assertNotNull(initialStudent)
        assertFalse(initialStudent!!.isEntryApproved)

        val card = repository.getActiveCardForStudent(initialStudent.id)!!

        // Admin clears the student's fees
        val updateResult = repository.updateFeeStatus(initialStudent.id, FeeStatus.CLEARED)
        assertTrue(updateResult.isSuccess)

        // Immediate scan by guard returns approved!
        val scanResult1 = repository.verifyStudentByQr(card.qrPayload)
        assertTrue(scanResult1 is StudentScanResult.Success && scanResult1.isApproved)

        // Admin sets fees back to outstanding
        repository.updateFeeStatus(initialStudent.id, FeeStatus.OUTSTANDING, 480000.0)
        val scanResult2 = repository.verifyStudentByQr(card.qrPayload)
        assertTrue(scanResult2 is StudentScanResult.Success && !scanResult2.isApproved)
    }

    @Test
    fun testAdminAddAndRemoveStudent() = runBlocking {
        val newStudent = Student(
            id = UUID.randomUUID().toString(),
            studentNumber = "OAK-2026-0099",
            firstName = "Lucas",
            lastName = "Vance",
            gradeClass = "Senior 3-C",
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            guardianName = "Patricia Vance",
            guardianPhone = "+256 772 349112",
            homeroomTeacher = "Mr. Henderson",
            transportRoute = "North Gate • Route #5"
        )

        val addResult = repository.addStudent(newStudent)
        assertTrue(addResult.isSuccess)

        val card = repository.getActiveCardForStudent(newStudent.id)!!

        val scanResult = repository.verifyStudentByQr(card.qrPayload)
        assertTrue(scanResult is StudentScanResult.Success)
        val retrieved = (scanResult as StudentScanResult.Success).student
        assertEquals("Lucas", retrieved.firstName)
        assertTrue(scanResult.isApproved)

        // Delete student
        val deleteResult = repository.deleteStudent(newStudent.id)
        assertTrue(deleteResult.isSuccess)

        val afterDelete = repository.verifyStudentByQr(card.qrPayload)
        assertTrue(afterDelete is StudentScanResult.StudentNotFound)
    }

    @Test
    fun testGuardScanWorkflowAndAuditLogging() = runBlocking {
        viewModel.loginAs(UserRole.SECURITY_GUARD)

        val s1 = repository.getStudentByStudentNumber("OAK-2026-0001")!!
        val card1 = repository.getActiveCardForStudent(s1.id)!!

        val s2 = repository.getStudentByStudentNumber("OAK-2026-0002")!!
        val card2 = repository.getActiveCardForStudent(s2.id)!!

        // Scan cleared student
        viewModel.handleBarcodeScan(card1.qrPayload)
        val clearedResult = viewModel.activeScanResult.value
        assertNotNull(clearedResult)
        assertTrue(clearedResult is StudentScanResult.Success && clearedResult.isApproved)
        assertNull(viewModel.scanError.value)

        // Scan outstanding student
        viewModel.handleBarcodeScan(card2.qrPayload)
        val outstandingResult = viewModel.activeScanResult.value
        assertNotNull(outstandingResult)
        assertTrue(outstandingResult is StudentScanResult.Success && !outstandingResult.isApproved)

        // Scan invalid barcode format
        viewModel.handleBarcodeScan("NON_SCHOOL_BARCODE_XYZ")
        val invalidResult = viewModel.activeScanResult.value
        assertNotNull(invalidResult)
        assertTrue(invalidResult is StudentScanResult.InvalidQr)

        // Verify logs contain all scans (newest first)
        val logs = repository.scanLogsFlow.first()
        assertEquals(3, logs.size)
        assertEquals(GateVerificationDecision.INVALID_QR, logs[0].decision)
        assertEquals(GateVerificationDecision.NOT_APPROVED, logs[1].decision)
        assertEquals(GateVerificationDecision.APPROVED, logs[2].decision)
    }
}
