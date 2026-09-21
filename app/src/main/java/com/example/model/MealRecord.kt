package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class MealType(
    val displayName: String,
    val operatingHours: String,
    val iconName: String
) {
    BREAKFAST("Breakfast", "06:30 - 08:30", "BakeryDining"),
    LUNCH("Lunch", "12:00 - 14:00", "Restaurant"),
    DINNER("Dinner", "18:00 - 20:00", "DinnerDining"),
    SPECIAL_SNACK("Evening Tea", "16:00 - 17:00", "Coffee");

    val label: String get() = displayName
    val timeWindow: String get() = operatingHours
}

enum class MealServingStatus {
    SERVED,
    BLOCKED_DOUBLE_SERVING,
    BLOCKED_NO_MEAL_CLEARANCE
}

data class MealRecord(
    val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val gradeClass: String,
    val mealType: MealType,
    val mealDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val timestamp: Long = System.currentTimeMillis(),
    val serverName: String = "Chef Jackson Omondi",
    val status: MealServingStatus = MealServingStatus.SERVED,
    val notes: String = ""
)

sealed class MealVerificationResult {
    data class Success(val record: MealRecord) : MealVerificationResult()
    data class BlockedDoubleServing(
        val student: Student,
        val mealType: MealType,
        val previousRecord: MealRecord
    ) : MealVerificationResult()
    data class StudentNotFound(val rawCode: String) : MealVerificationResult() {
        val scannedCode: String get() = rawCode
    }
    data class BlockedNoClearance(val student: Student, val reason: String) : MealVerificationResult()
}
