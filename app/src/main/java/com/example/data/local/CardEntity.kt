package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.Card
import com.example.model.CardStatus

@Entity(
    tableName = "cards",
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["studentNumber"]),
        Index(value = ["status"]),
        Index(value = ["qrPayload"]),
        Index(value = ["cardIdentifier"])
    ]
)
data class CardEntity(
    @PrimaryKey
    val id: String,
    val cardIdentifier: String,
    val studentId: String,
    val studentNumber: String,
    val qrPayload: String,
    val status: String,
    val issueDate: Long,
    val activationDate: Long,
    val deactivationDate: Long?,
    val replacedByCardId: String?,
    val reason: String?,
    val notes: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false
) {
    fun toDomain(): Card {
        val parsedStatus = try {
            CardStatus.valueOf(status)
        } catch (_: Exception) {
            CardStatus.ACTIVE
        }

        return Card(
            id = id,
            cardIdentifier = cardIdentifier,
            studentId = studentId,
            studentNumber = studentNumber,
            qrPayload = qrPayload,
            status = parsedStatus,
            issueDate = issueDate,
            activationDate = activationDate,
            deactivationDate = deactivationDate,
            replacedByCardId = replacedByCardId,
            reason = reason,
            notes = notes,
            updatedAt = updatedAt,
            isDeleted = isDeleted
        )
    }

    companion object {
        fun fromDomain(card: Card): CardEntity {
            return CardEntity(
                id = card.id,
                cardIdentifier = card.cardIdentifier,
                studentId = card.studentId,
                studentNumber = card.studentNumber,
                qrPayload = card.qrPayload,
                status = card.status.name,
                issueDate = card.issueDate,
                activationDate = card.activationDate,
                deactivationDate = card.deactivationDate,
                replacedByCardId = card.replacedByCardId,
                reason = card.reason,
                notes = card.notes,
                updatedAt = card.updatedAt,
                isDeleted = card.isDeleted
            )
        }
    }
}
