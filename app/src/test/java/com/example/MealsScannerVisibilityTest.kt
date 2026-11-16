package com.example

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.model.AuthUser
import com.example.model.MealType
import com.example.model.UserRole
import com.example.ui.meals.MealsMasterScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MealsScannerVisibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mealStaffSeesProminentQrScanAction() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MealsMasterScreen(
                    user = AuthUser(
                        role = UserRole.MEAL_SERVING_STAFF,
                        name = "Meal Officer",
                        station = "Dining Hall"
                    ),
                    activeMealType = MealType.LUNCH,
                    mealRecords = emptyList(),
                    mealScanOutcome = null,
                    allStudents = emptyList(),
                    onSelectMealType = {},
                    onOpenScanner = {},
                    onManualServe = {},
                    onDismissScanOutcome = {},
                    onExport = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("button_meals_scan_qr")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun readOnlyManagementViewDoesNotExposeMealServingScanner() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MealsMasterScreen(
                    user = AuthUser(
                        role = UserRole.HEAD_TEACHER_MANAGEMENT,
                        name = "Head Teacher",
                        station = "Head Teacher's Office"
                    ),
                    activeMealType = MealType.LUNCH,
                    mealRecords = emptyList(),
                    mealScanOutcome = null,
                    allStudents = emptyList(),
                    onSelectMealType = {},
                    onOpenScanner = {},
                    onManualServe = {},
                    onDismissScanOutcome = {},
                    onExport = {},
                    readOnly = true
                )
            }
        }

        composeTestRule.onNodeWithTag("button_meals_scan_qr")
            .assertDoesNotExist()
    }
}
