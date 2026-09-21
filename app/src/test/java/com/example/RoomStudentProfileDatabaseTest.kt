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
}
