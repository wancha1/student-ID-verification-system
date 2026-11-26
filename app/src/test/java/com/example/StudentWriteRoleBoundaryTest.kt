package com.example.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentWriteRoleBoundaryTest {

    @Test
    fun administratorCanWriteStudentRegistry() {
        assertTrue(canWriteStudentRecords("ADMIN"))
    }

    @Test
    fun headTeacherCannotWriteStudentRegistry() {
        assertFalse(canWriteStudentRecords("HEAD_TEACHER"))
    }

    @Test
    fun operationalAndLegacyRolesCannotWriteStudentRegistry() {
        listOf(
            "GATE_OFFICER",
            "MEAL_STAFF",
            "BURSAR",
            "TEACHER",
            "EXAM_STAFF",
            "UNKNOWN"
        ).forEach { role ->
            assertFalse("$role must not write student records", canWriteStudentRecords(role))
        }
    }
}
