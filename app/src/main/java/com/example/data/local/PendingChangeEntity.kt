package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Supported entity types for durable offline-change queue.
 */
object SyncEntityType {
    const val STUDENT = "STUDENT"
    const val CARD = "CARD"
    const val SCAN_LOG = "SCAN_LOG"
    const val FEE_STATUS = "FEE_STATUS"
}

/**
 * Supported operation types for offline sync queue.
 */
object SyncOperationType {
    const val CREATE = "CREATE"
    const val UPDATE = "UPDATE"
    const val DELETE = "DELETE"
    const val STATUS_CHANGE = "STATUS_CHANGE"
}

/**
 * Status of pending sync items.
 */
object SyncItemStatus {
    const val PENDING = "PENDING"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}

/**
 * Room entity tracking every offline mutation durably on-device.
 * Guarantees that updates made while disconnected will be sent to the central backend upon reconnection.
 */
@Entity(
    tableName = "pending_changes",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
        Index(value = ["entityType", "recordId"])
    ]
)
data class PendingChangeEntity(
    @PrimaryKey
    val changeId: String,                    // Unique UUID for this specific change operation (Idempotency Key)
    val entityType: String,                  // "STUDENT", "CARD", "SCAN_LOG", "FEE_STATUS"
    val recordId: String,                    // Primary key / ID of the affected record
    val operationType: String,               // "CREATE", "UPDATE", "DELETE", "STATUS_CHANGE"
    val payloadJson: String? = null,         // Serialized payload or mutation delta
    val status: String = SyncItemStatus.PENDING, // "PENDING", "IN_FLIGHT", "SYNCED", "FAILED"
    val retryCount: Int = 0,                 // Number of failed sync attempts
    val lastError: String? = null,           // Last error message or failure description
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null
)
