package com.example

import com.example.data.local.CardEntity
import com.example.model.CardStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CardEntityFailClosedTest {
    @Test
    fun unknownCardStatusDoesNotBecomeActive() {
        val entity = CardEntity(
            id = "card-unknown-status",
            cardIdentifier = "CRD-UNKNOWN",
            studentId = "student-1",
            studentNumber = "LTC-001",
            qrPayload = "LTC:V2:invalid",
            status = "UNRECOGNIZED_STATUS",
            issueDate = 0L,
            activationDate = 0L,
            deactivationDate = null,
            replacedByCardId = null,
            reason = null,
            notes = "",
            updatedAt = 1L,
            isDeleted = false
        )

        assertEquals(CardStatus.DEACTIVATED, entity.toDomain().status)
    }
}
