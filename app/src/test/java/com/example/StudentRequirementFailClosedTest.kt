package com.example

import com.example.model.StudentRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StudentRequirementFailClosedTest {

    @Test
    fun newRequirementRecordStartsWithNoItemsCleared() {
        val requirement = StudentRequirement(
            studentId = "synthetic-student-1",
            studentNumber = "TEST-001",
            studentName = "Synthetic Student",
            gradeClass = "Test Class"
        )

        assertEquals(0, requirement.clearedCount)
        assertEquals(0, requirement.compliancePercentage)
        assertFalse(requirement.isFullyCleared)
    }

    @Test
    fun explicitlyClearedItemsAreCountedOnlyAfterAnExplicitUpdate() {
        val requirement = StudentRequirement(
            studentId = "synthetic-student-2",
            studentNumber = "TEST-002",
            studentName = "Synthetic Student",
            gradeClass = "Test Class",
            uniformComplete = true
        )

        assertEquals(1, requirement.clearedCount)
        assertEquals(14, requirement.compliancePercentage)
        assertFalse(requirement.isFullyCleared)
    }
}
