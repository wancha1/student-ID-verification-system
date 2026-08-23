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
        }
        viewModel = MainViewModel(repository)
    }

    @Test
    fun testAppNameString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Oakridge Student Access", appName)
    }

    @Test
    fun testStudentQrLookup() = runBlocking {
        val scanResult = repository.verifyStudentByQr("OAKRIDGE:STU:OAK-2026-0001")
        assertTrue(scanResult is StudentScanResult.Success)
        val student = (scanResult as StudentScanResult.Success).student
        assertNotNull(student)
        assertEquals("Michael", student.firstName)
        assertEquals("Senior 4-A", student.gradeClass)
        assertTrue(student.isDayScholar)
        assertEquals(FeeStatus.CLEARED, student.feesStatus)
        assertTrue("Fees cleared student must be approved for entry", scanResult.isApproved)
    }

    @Test
    fun testOutstandingStudentEntryNotApproved() = runBlocking {
        val scanResult = repository.verifyStudentByQr("OAKRIDGE:STU:OAK-2026-0002")
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

        // 1. Initial scan is approved
        val scanResult1 = repository.verifyStudentByQr("OAKRIDGE:STU:$studentNumber")
        assertTrue(scanResult1 is StudentScanResult.Success && scanResult1.isApproved)

        // 2. Retrieve active card and report lost
        val activeCard = repository.getActiveCardForStudent(student!!.id)
        assertNotNull(activeCard)
        assertEquals(CardStatus.ACTIVE, activeCard!!.status)

        val reportResult = repository.reportCardLost(student.id, activeCard.id, "Student lost wallet on campus")
        assertTrue(reportResult.isSuccess)

        // 3. Scan now returns CardInactive (Denied entry)
        val scanResult2 = repository.verifyStudentByQr("OAKRIDGE:STU:$studentNumber")
        assertTrue("Scanning lost card must yield CardInactive", scanResult2 is StudentScanResult.CardInactive)
        val inactiveResult = scanResult2 as StudentScanResult.CardInactive
        assertEquals(CardStatus.LOST, inactiveResult.cardStatus)

        // 4. Issue replacement card
        val replacementResult = repository.issueReplacementCard(student.id, activeCard.id, "Badge replacement")
        assertTrue(replacementResult.isSuccess)
        val newCard = replacementResult.getOrThrow()
        assertEquals(CardStatus.ACTIVE, newCard.status)

        // 5. Scan now succeeds again with new card
        val scanResult3 = repository.verifyStudentByQr("OAKRIDGE:STU:$studentNumber")
        assertTrue(scanResult3 is StudentScanResult.Success && scanResult3.isApproved)
    }

    @Test
    fun testFeeStatusUpdateImmediatelyAffectsApproval() = runBlocking {
        val studentNumber = "OAK-2026-0002"
        val initialStudent = repository.getStudentByStudentNumber(studentNumber)
        assertNotNull(initialStudent)
        assertFalse(initialStudent!!.isEntryApproved)

        // Admin clears the student's fees
        val updateResult = repository.updateFeeStatus(initialStudent.id, FeeStatus.CLEARED)
        assertTrue(updateResult.isSuccess)

        // Immediate scan by guard returns approved!
        val scanResult1 = repository.verifyStudentByQr("OAKRIDGE:STU:$studentNumber")
        assertTrue(scanResult1 is StudentScanResult.Success && scanResult1.isApproved)

        // Admin sets fees back to outstanding
        repository.updateFeeStatus(initialStudent.id, FeeStatus.OUTSTANDING, 480000.0)
        val scanResult2 = repository.verifyStudentByQr("OAKRIDGE:STU:$studentNumber")
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

        val scanResult = repository.verifyStudentByQr("OAKRIDGE:STU:OAK-2026-0099")
        assertTrue(scanResult is StudentScanResult.Success)
        val retrieved = (scanResult as StudentScanResult.Success).student
        assertEquals("Lucas", retrieved.firstName)
        assertTrue(scanResult.isApproved)

        // Delete student
        val deleteResult = repository.deleteStudent(newStudent.id)
        assertTrue(deleteResult.isSuccess)

        val afterDelete = repository.verifyStudentByQr("OAKRIDGE:STU:OAK-2026-0099")
        assertTrue(afterDelete is StudentScanResult.StudentNotFound)
    }

    @Test
    fun testGuardScanWorkflowAndAuditLogging() = runBlocking {
        viewModel.loginAs(UserRole.SECURITY_GUARD)

        // Scan cleared student
        viewModel.handleBarcodeScan("OAKRIDGE:STU:OAK-2026-0001")
        val clearedResult = viewModel.activeScanResult.value
        assertNotNull(clearedResult)
        assertTrue(clearedResult is StudentScanResult.Success && clearedResult.isApproved)
        assertNull(viewModel.scanError.value)

        // Scan outstanding student
        viewModel.handleBarcodeScan("OAKRIDGE:STU:OAK-2026-0002")
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
