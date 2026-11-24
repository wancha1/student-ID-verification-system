package com.example.model

data class StudentRequirement(
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val gradeClass: String,
    val uniformComplete: Boolean = false,
    val sportsKitComplete: Boolean = false,
    val textbooksSubmitted: Boolean = false,
    val medicalFormSigned: Boolean = false,
    val schoolIdIssued: Boolean = false,
    val rulesAgreementSigned: Boolean = false,
    val busPassCleared: Boolean = false,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalItems: Int get() = 7

    val clearedCount: Int
        get() {
            var count = 0
            if (uniformComplete) count++
            if (sportsKitComplete) count++
            if (textbooksSubmitted) count++
            if (medicalFormSigned) count++
            if (schoolIdIssued) count++
            if (rulesAgreementSigned) count++
            if (busPassCleared) count++
            return count
        }

    val compliancePercentage: Int
        get() = (clearedCount * 100) / totalItems

    val isFullyCleared: Boolean
        get() = clearedCount == totalItems
}
