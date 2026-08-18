package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.DayScholarStatus
import com.example.model.FeeStatus
import com.example.model.Student
import com.example.ui.components.StudentIdCardView
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
            id = "STU-2026-0001",
            firstName = "Michael",
            lastName = "Adeyemi",
            gradeClass = "Grade 11-A",
            isDayScholar = true,
            dayScholarType = DayScholarStatus.DAY_SCHOLAR_BUS,
            transportRoute = "Bus Route 4 (Oakville Express)",
            feesStatus = FeeStatus.CLEARED
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                StudentIdCardView(student = sampleStudent)
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
