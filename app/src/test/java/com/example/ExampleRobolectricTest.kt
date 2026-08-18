package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MockStudentRepository
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.ScanLog
import com.example.model.Student
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
        val student = repository.findStudentByQrCode("STU-2026-0001")
        assertNotNull(student)
        assertEquals("Michael", student?.firstName)
        assertEquals("Grade 11-A", student?.gradeClass)
        assertTrue(student?.isDayScholar == true)
        assertEquals(FeeStatus.CLEARED, student?.feesStatus)
        assertTrue("Fees cleared student must be approved for entry", student?.isEntryApproved == true)
    }

    @Test
    fun testOutstandingStudentEntryNotApproved() = runBlocking {
        val student = repository.findStudentByQrCode("STU-2026-0002")
        assertNotNull(student)
        assertEquals("Sophia", student?.firstName)
        assertEquals(FeeStatus.OUTSTANDING, student?.feesStatus)
        assertFalse("Outstanding fee student must NOT be approved for entry", student?.isEntryApproved == true)
    }

    @Test
    fun testFeeStatusUpdateImmediatelyAffectsApproval() = runBlocking {
        val studentId = "STU-2026-0002"
        var student = repository.findStudentByQrCode(studentId)
        assertFalse(student!!.isEntryApproved)

        // Admin clears the student's fees
        val updateResult = repository.updateFeeStatus(studentId, FeeStatus.CLEARED)
        assertTrue(updateResult.isSuccess)

        // Immediate scan by guard returns approved!
        student = repository.findStudentByQrCode(studentId)
        assertEquals(FeeStatus.CLEARED, student?.feesStatus)
        assertTrue("Student must be approved immediately after admin clears fees", student?.isEntryApproved == true)

        // Admin sets fees back to outstanding
        repository.updateFeeStatus(studentId, FeeStatus.OUTSTANDING, 480.0)
        student = repository.findStudentByQrCode(studentId)
        assertEquals(FeeStatus.OUTSTANDING, student?.feesStatus)
        assertFalse("Student must NOT be approved immediately after admin sets to outstanding", student?.isEntryApproved == true)
    }

    @Test
    fun testAdminAddAndRemoveStudent() = runBlocking {
        val newStudent = Student(
            id = "STU-2026-0099",
            firstName = "Lucas",
            lastName = "Vance",
            gradeClass = "Grade 10-C",
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            guardianName = "Patricia Vance",
            guardianPhone = "+1 (555) 349-1122",
            homeroomTeacher = "Mr. Henderson",
            transportRoute = "North Gate • Route #5"
        )

        val addResult = repository.addStudent(newStudent)
        assertTrue(addResult.isSuccess)

        val retrieved = repository.findStudentByQrCode("STU-2026-0099")
        assertNotNull(retrieved)
        assertEquals("Lucas", retrieved?.firstName)
        assertTrue(retrieved?.isEntryApproved == true)

        // Delete student
        val deleteResult = repository.deleteStudent("STU-2026-0099")
        assertTrue(deleteResult.isSuccess)

        val afterDelete = repository.findStudentByQrCode("STU-2026-0099")
        assertNull(afterDelete)
    }

    @Test
    fun testGuardScanWorkflowAndAuditLogging() = runBlocking {
        viewModel.loginAs(UserRole.SECURITY_GUARD)

        // Scan cleared student
        viewModel.handleBarcodeScan("STU-2026-0001")
        val clearedScanned = viewModel.currentScannedStudent.value
        assertNotNull(clearedScanned)
        assertTrue(clearedScanned?.isEntryApproved == true)
        assertNull(viewModel.scanError.value)

        // Scan outstanding student
        viewModel.handleBarcodeScan("STU-2026-0002")
        val outstandingScanned = viewModel.currentScannedStudent.value
        assertNotNull(outstandingScanned)
        assertFalse(outstandingScanned?.isEntryApproved == true)

        // Scan invalid barcode
        viewModel.handleBarcodeScan("UNKNOWN-BARCODE-XYZ")
        assertNull(viewModel.currentScannedStudent.value)
        assertNotNull(viewModel.scanError.value)
        assertTrue(viewModel.scanError.value?.contains("UNKNOWN-BARCODE-XYZ") == true)

        // Verify logs contain all scans (newest first)
        val logs = repository.scanLogsFlow.first()
        assertEquals(3, logs.size)
        assertFalse(logs[0].isApproved) // UNKNOWN-BARCODE-XYZ (newest)
        assertFalse(logs[1].isApproved) // STU-2026-0002
        assertTrue(logs[2].isApproved)  // STU-2026-0001
    }
}
