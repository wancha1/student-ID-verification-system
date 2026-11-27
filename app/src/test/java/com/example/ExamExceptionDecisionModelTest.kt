package com.example

import com.example.data.local.ExamExceptionEntity
import com.example.model.ExamException
import com.example.model.ExamExceptionDecisionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamExceptionDecisionModelTest {

    @Test
    fun rejectedDecisionSurvivesRoomDomainRoundTrip() {
        val request = ExamException(
            id = "exam-rejected-1",
            studentId = "student-1",
            studentNumber = "SYN-001",
            studentName = "Synthetic Student",
            examCode = "UACE-MOCK",
            requestedByBursar = "Synthetic Bursar",
            recommendationReason = "Outstanding balance",
            isApproved = false,
            requestedAt = 1_700_000_000_000,
            decisionStatus = ExamExceptionDecisionStatus.REJECTED,
            decisionReason = "Supporting evidence was insufficient",
            updatedAt = 1_700_000_000_500
        )

        val entity = ExamExceptionEntity.fromDomain(request, isSynced = true)
        val restored = entity.toDomain()

        assertFalse(entity.isApproved)
        assertEquals("REJECTED", entity.decisionStatus)
        assertEquals(ExamExceptionDecisionStatus.REJECTED, restored.decisionStatus)
        assertEquals("Supporting evidence was insufficient", restored.decisionReason)
        assertEquals(1_700_000_000_500, restored.updatedAt)
        assertTrue(entity.isSyncedToCloud)
    }

    @Test
    fun legacyApprovedRowsMapToApprovedDecision() {
        val legacy = ExamExceptionEntity(
            id = "exam-approved-legacy",
            studentId = "student-1",
            studentNumber = "SYN-001",
            studentName = "Synthetic Student",
            examCode = "UACE-MOCK",
            requestedByBursar = "Synthetic Bursar",
            recommendationReason = "Fee arrangement",
            isApproved = true,
            approvedByHeadTeacher = "Head Teacher",
            requestedAt = 1_700_000_000_000,
            approvedAt = 1_700_000_000_500
        )

        assertEquals("APPROVED", legacy.decisionStatus)
        assertEquals(ExamExceptionDecisionStatus.APPROVED, legacy.toDomain().decisionStatus)
    }

    @Test
    fun pendingRequestRemainsPendingByDefault() {
        val pending = ExamException(
            studentId = "student-1",
            studentNumber = "SYN-001",
            studentName = "Synthetic Student",
            examCode = "UCE-MOCK",
            requestedByBursar = "Synthetic Bursar",
            recommendationReason = "Payment plan requested"
        )

        val entity = ExamExceptionEntity.fromDomain(pending)

        assertEquals("PENDING", entity.decisionStatus)
        assertFalse(entity.isApproved)
    }
}
