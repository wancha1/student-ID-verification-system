package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.StudentEntity
import com.example.data.local.StudentProfileEntity
import com.example.model.AccessStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.Student
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomStudentProfileDatabaseTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveStudentProfile() = runBlocking {
        val studentId = UUID.randomUUID().toString()
        val profile = StudentProfileEntity(
            id = studentId,
            studentNumber = "OAK-2026-0001",
            name = "Michael Kasule",
            photoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb",
            accessStatus = AccessStatus.APPROVED.name,
            gradeClass = "Senior 4-A",
            isDayScholar = true,
            isFeesCleared = true,
            outstandingAmount = 0.0
        )

        database.studentProfileDao().insertOrUpdateProfile(profile)

        val retrieved = database.studentProfileDao().getProfileById(studentId)
        assertNotNull(retrieved)
        assertEquals("Michael Kasule", retrieved?.name)
        assertEquals("OAK-2026-0001", retrieved?.studentNumber)
        assertEquals(AccessStatus.APPROVED.name, retrieved?.accessStatus)
        assertTrue(retrieved!!.isAccessApproved)

        // Offline lookup by student number
        val byNumber = database.studentProfileDao().findProfileForOfflineVerification("OAK-2026-0001")
        assertNotNull(byNumber)
        assertEquals(studentId, byNumber?.id)
    }

    @Test
    fun testUpdateAccessStatusForOfflineVerification() = runBlocking {
        val studentId = UUID.randomUUID().toString()
        val profile = StudentProfileEntity(
            id = studentId,
            studentNumber = "OAK-2026-0002",
            name = "Sophia Nanteza",
            photoUrl = null,
            accessStatus = AccessStatus.RESTRICTED_FEES.name,
            gradeClass = "Senior 3-B",
            isDayScholar = true,
            isFeesCleared = false,
            outstandingAmount = 450000.0
        )

        database.studentProfileDao().insertOrUpdateProfile(profile)

        // Verify restricted initially
        val initial = database.studentProfileDao().getProfileById(studentId)
        assertEquals(AccessStatus.RESTRICTED_FEES.name, initial?.accessStatus)

        // Bursar updates status upon payment
        val now = System.currentTimeMillis()
        database.studentProfileDao().updateAccessStatus(studentId, AccessStatus.APPROVED.name, now)

        val updated = database.studentProfileDao().getProfileById(studentId)
        assertEquals(AccessStatus.APPROVED.name, updated?.accessStatus)
        assertTrue(updated!!.isAccessApproved)
    }

    @Test
    fun testStudentEntityWithAccessStatusAndName() = runBlocking {
        val student = Student(
            id = UUID.randomUUID().toString(),
            studentNumber = "OAK-2026-0003",
            firstName = "Daniel",
            lastName = "Okello",
            gradeClass = "Senior 2-C",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            accessStatus = AccessStatus.APPROVED,
            photoUrl = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6"
        )

        val entity = StudentEntity.fromDomain(student)
        database.studentDao().insertOrUpdateStudent(entity)

        val retrievedEntity = database.studentDao().getStudentById(student.id)
        assertNotNull(retrievedEntity)
        assertEquals("Daniel Okello", retrievedEntity?.name)
        assertEquals(AccessStatus.APPROVED.name, retrievedEntity?.accessStatus)

        val domainStudent = retrievedEntity?.toDomain()
        assertNotNull(domainStudent)
        assertEquals(AccessStatus.APPROVED, domainStudent?.accessStatus)
        assertTrue(domainStudent!!.isEntryApproved)
    }

    @Test
    fun testPendingChangesQueueTracksOfflineOperations() = runBlocking {
        val changeId = UUID.randomUUID().toString()
        val change = com.example.data.local.PendingChangeEntity(
            changeId = changeId,
            entityType = com.example.data.local.SyncEntityType.FEE_STATUS,
            recordId = "stu-001",
            operationType = com.example.data.local.SyncOperationType.STATUS_CHANGE,
            payloadJson = "{\"feesStatus\":\"CLEARED\"}"
        )

        database.pendingChangeDao().enqueueChange(change)

        val pending = database.pendingChangeDao().getPendingChanges()
        assertEquals(1, pending.size)
        assertEquals(changeId, pending[0].changeId)
        assertEquals(com.example.data.local.SyncItemStatus.PENDING, pending[0].status)
        assertEquals(1, database.pendingChangeDao().getPendingCount())

        // Mark as synced
        val now = System.currentTimeMillis()
        database.pendingChangeDao().markSynced(changeId, now)

        assertEquals(0, database.pendingChangeDao().getPendingCount())
        val syncedItem = database.pendingChangeDao().getChangeById(changeId)
        assertNotNull(syncedItem)
        assertEquals(com.example.data.local.SyncItemStatus.SYNCED, syncedItem?.status)
    }

    @Test
    fun testExistingRecordsSurviveRepositoryInitializationWithoutDestructiveWipe() = runBlocking {
        val student = Student(
            id = "test-preserved-id-01",
            studentNumber = "OAK-2026-0001",
            firstName = "Preserved",
            lastName = "Student",
            gradeClass = "Senior 1",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
            feesStatus = FeeStatus.CLEARED,
            outstandingAmount = 0.0,
            accessStatus = AccessStatus.APPROVED
        )

        database.studentDao().insertOrUpdateStudent(StudentEntity.fromDomain(student))

        val repo = com.example.data.RoomStudentRepository(database)
        repo.initialize()

        // Student must NOT be wiped
        val survivor = database.studentDao().getStudentById("test-preserved-id-01")
        assertNotNull("Student with OAK-2026-0001 must not be wiped during initialize()", survivor)
        assertEquals("Preserved Student", survivor?.name)
    }

    @Test
    fun testOfflineMutationsEnqueueDurablePendingChanges() = runBlocking {
        val repo = com.example.data.RoomStudentRepository(database)
        repo.setNetworkOnline(false)

        val student = Student(
            id = "offline-student-100",
            studentNumber = "OAK-2026-0100",
            firstName = "Amina",
            lastName = "Namubiru",
            gradeClass = "Senior 3-A",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            feesStatus = FeeStatus.OUTSTANDING,
            outstandingAmount = 250000.0,
            accessStatus = AccessStatus.RESTRICTED_FEES
        )

        repo.addStudent(student)
        repo.updateFeeStatus(student.id, FeeStatus.CLEARED, 0.0)

        val pending = database.pendingChangeDao().getPendingChanges()
        assertTrue("Pending changes queue must contain offline student and fee mutations", pending.size >= 3)
        assertTrue(pending.any { it.entityType == com.example.data.local.SyncEntityType.STUDENT && it.operationType == com.example.data.local.SyncOperationType.CREATE })
        assertTrue(pending.any { it.entityType == com.example.data.local.SyncEntityType.FEE_STATUS && it.operationType == com.example.data.local.SyncOperationType.STATUS_CHANGE })
    }
}
