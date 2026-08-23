package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.CardStatus
import com.example.model.FeeStatus
import com.example.model.GateVerificationDecision
import com.example.model.ScanLog

@Entity(
    tableName = "gate_scan_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["studentId"]),
        Index(value = ["studentNumber"]),
        Index(value = ["cardId"]),
        Index(value = ["decision"]),
        Index(value = ["isSyncedToCloud"])
    ]
)
data class ScanLogEntity(
    @PrimaryKey
    val id: String,
    val studentId: String?,
    val studentNumber: String?,
    val studentName: String,
    val gradeClass: String,
    val cardId: String?,
    val cardIdentifier: String?,
    val qrPayload: String,
    val timestamp: Long,
    val decision: String,
    val feeStatus: String?,
    val cardStatus: String?,
    val isDayScholar: Boolean,
    val isApproved: Boolean,
    val reason: String,
    val isOfflineDecision: Boolean,
    val dataSyncTimestampAtScan: Long,
    val guardName: String,
    val deviceIdentifier: String,
    val gateLocation: String,
    val isSyncedToCloud: Boolean
) {
    fun toDomain(): ScanLog {
        val parsedDecision = try {
            GateVerificationDecision.valueOf(decision)
        } catch (_: Exception) {
            if (isApproved) GateVerificationDecision.APPROVED else GateVerificationDecision.NOT_APPROVED
        }

        val parsedFeeStatus = feeStatus?.let {
            try {
                FeeStatus.valueOf(it)
            } catch (_: Exception) {
                null
            }
        }

        val parsedCardStatus = cardStatus?.let {
            try {
                CardStatus.valueOf(it)
            } catch (_: Exception) {
                null
            }
        }

        return ScanLog(
            id = id,
            studentId = studentId,
            studentNumber = studentNumber,
            studentName = studentName,
            gradeClass = gradeClass,
            cardId = cardId,
            cardIdentifier = cardIdentifier,
            qrPayload = qrPayload,
            timestamp = timestamp,
            decision = parsedDecision,
            feeStatus = parsedFeeStatus,
            cardStatus = parsedCardStatus,
            isDayScholar = isDayScholar,
            isApproved = isApproved,
            reason = reason,
            isOfflineDecision = isOfflineDecision,
            dataSyncTimestampAtScan = dataSyncTimestampAtScan,
            guardName = guardName,
            deviceIdentifier = deviceIdentifier,
            gateLocation = gateLocation,
            isSyncedToCloud = isSyncedToCloud
        )
    }

    companion object {
        fun fromDomain(log: ScanLog): ScanLogEntity {
            return ScanLogEntity(
                id = log.id,
                studentId = log.studentId,
                studentNumber = log.studentNumber,
                studentName = log.studentName,
                gradeClass = log.gradeClass,
                cardId = log.cardId,
                cardIdentifier = log.cardIdentifier,
                qrPayload = log.qrPayload,
                timestamp = log.timestamp,
                decision = log.decision.name,
                feeStatus = log.feeStatus?.name,
                cardStatus = log.cardStatus?.name,
                isDayScholar = log.isDayScholar,
                isApproved = log.isApproved,
                reason = log.reason,
                isOfflineDecision = log.isOfflineDecision,
                dataSyncTimestampAtScan = log.dataSyncTimestampAtScan,
                guardName = log.guardName,
                deviceIdentifier = log.deviceIdentifier,
                gateLocation = log.gateLocation,
                isSyncedToCloud = log.isSyncedToCloud
            )
        }
    }
}
