package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.Card
import com.example.model.CardStatus
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.ui.components.PrintableStudentIdCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun student_card_screenshot() {
        val sampleStudent = Student(
            id = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            studentNumber = "OAK-2026-0001",
            firstName = "Michael",
            lastName = "Adeyemi",
            gradeClass = "Senior 4-A",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            transportRoute = "Bus Route 4 (Oakville Express)",
            feesStatus = FeeStatus.CLEARED
        )

        val sampleCard = Card(
            id = "crd-001",
            studentId = sampleStudent.id,
            studentNumber = sampleStudent.studentNumber,
            cardIdentifier = "CRD-2026-0001-01",
            qrPayload = sampleStudent.qrPayload,
            status = CardStatus.ACTIVE
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                PrintableStudentIdCard(
                    student = sampleStudent,
                    card = sampleCard
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
