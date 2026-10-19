package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.RoomStudentRepository
import com.example.data.local.AppDatabase
import com.example.data.sync.InMemoryCloudBackend
import com.example.model.AccessStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.SyncStatus
import com.example.model.Student
import com.example.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkRecoverySyncTest {

    @Test
    fun reconnectingNetworkAutomaticallyDispatchesDurableOutbox() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.createInMemory(context)
        try {
            val repository = RoomStudentRepository(database, InMemoryCloudBackend())
            repository.initialize()
            repository.setNetworkOnline(false)

            val student = Student(
                id = "STU-NETWORK-RECOVERY",
                studentNumber = "LTC-2026-9898",
                firstName = "Grace",
                lastName = "Auma",
                gradeClass = "Senior 4",
                isDayScholar = true,
                dayScholarType = DayScholarStatus.DAY_SCHOLAR_WALK,
                feesStatus = FeeStatus.CLEARED,
                outstandingAmount = 0.0,
                accessStatus = AccessStatus.APPROVED
            )
            assertTrue(repository.addStudent(student).isSuccess)
            assertTrue(repository.pendingCountFlow.first() > 0)

            val viewModel = MainViewModel(repository)
            viewModel.toggleNetworkOnline(true)

            withTimeout(10_000) {
                repository.pendingCountFlow.first { it == 0 }
                repository.syncInfoFlow.first { it.status == SyncStatus.SYNCED }
            }
            assertEquals(SyncStatus.SYNCED, repository.syncInfoFlow.first().status)
        } finally {
            database.close()
        }
    }
}
