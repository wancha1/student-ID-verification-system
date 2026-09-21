package com.example.model

data class StudentRequirement(
    val studentId: String,
    val studentNumber: String,
    val studentName: String,
    val gradeClass: String,
    val uniformComplete: Boolean = true,
    val sportsKitComplete: Boolean = true,
    val textbooksSubmitted: Boolean = true,
    val medicalFormSigned: Boolean = true,
    val schoolIdIssued: Boolean = true,
    val rulesAgreementSigned: Boolean = true,
    val busPassCleared: Boolean = true,
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
