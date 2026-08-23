package com.example.model

import java.util.UUID

/**
 * Physical/Digital Card Entity distinct from Student identity.
 *
 * @property id Unique immutable Card UUID.
 * @property cardIdentifier Human-friendly reference e.g. "CARD-001" or "CRD-2026-0042-01".
 * @property studentId Foreign key linking to Student.id (UUID).
 * @property studentNumber Human-readable student number for display (e.g. "OAK-2026-0042").
 * @property qrPayload Barcode content formatted as "OAKRIDGE:STU:<studentNumber>" or specific card token.
 * @property status Current lifecycle state: ACTIVE, LOST, REPLACED, DEACTIVATED.
 * @property issueDate Milliseconds timestamp of when the card was physically printed/issued.
 * @property activationDate Milliseconds timestamp of when the card was activated for gate entry.
 * @property deactivationDate Milliseconds timestamp when marked LOST, REPLACED, or DEACTIVATED.
 * @property replacedByCardId Reference to the replacement card ID if replaced.
 * @property reason Note explaining loss, deactivation, or replacement reason.
 */
data class Card(
    val id: String = UUID.randomUUID().toString(),
    val cardIdentifier: String,
    val studentId: String,
    val studentNumber: String,
    val qrPayload: String = "OAKRIDGE:STU:$studentNumber",
    val status: CardStatus = CardStatus.ACTIVE,
    val issueDate: Long = System.currentTimeMillis(),
    val activationDate: Long = System.currentTimeMillis(),
    val deactivationDate: Long? = null,
    val replacedByCardId: String? = null,
    val reason: String? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
) {
    val isActive: Boolean get() = status == CardStatus.ACTIVE
}
