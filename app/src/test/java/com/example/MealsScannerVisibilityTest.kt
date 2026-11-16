package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.model.AuthUser
import com.example.model.MealType
import com.example.model.UserRole
import com.example.ui.meals.MealsMasterScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
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
    fun mealStaffSeesProminentQrScanActionAndOpensScanner() {
        var scannerOpened = false
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
                    onOpenScanner = { scannerOpened = true },
                    onManualServe = {},
                    onDismissScanOutcome = {},
                    onExport = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("button_meals_scan_qr")
            .assertExists()
            .assertIsDisplayed()
            .performClick()
        assertTrue("Meal scan action must open the scanner", scannerOpened)
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
