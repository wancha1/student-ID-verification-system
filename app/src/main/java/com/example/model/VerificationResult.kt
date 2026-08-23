package com.example.model

/**
 * The 5 distinct semantic outcomes of a QR scan at the school gate.
 */
enum class GateVerificationDecision(val title: String) {
    APPROVED("ENTRY APPROVED"),
    NOT_APPROVED("ENTRY NOT APPROVED"),
    CARD_INACTIVE("CARD INACTIVE"),
    STUDENT_NOT_FOUND("STUDENT NOT FOUND"),
    INVALID_QR("INVALID QR CODE")
}

/**
 * Rich verification evaluation model returned after scanning and looking up a QR badge.
 */
sealed class StudentScanResult {
    data class Success(
        val student: Student,
        val card: Card?,
        val isApproved: Boolean,
        val reason: String,
        val isOfflineData: Boolean,
        val lastSyncTimestamp: Long
    ) : StudentScanResult()

    data class CardInactive(
        val student: Student,
        val card: Card,
        val cardStatus: CardStatus,
        val reason: String,
        val isOfflineData: Boolean,
        val lastSyncTimestamp: Long
    ) : StudentScanResult()

    data class StudentNotFound(
        val parsedIdentifier: String,
        val reason: String,
        val isOfflineData: Boolean,
        val lastSyncTimestamp: Long
    ) : StudentScanResult()

    data class InvalidQr(
        val rawScannedString: String,
        val errorReason: String
    ) : StudentScanResult()
}
