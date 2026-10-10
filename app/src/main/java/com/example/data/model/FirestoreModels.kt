package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "GATE_OFFICER",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreStudent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val gradeClass: String = "",
    val isDayScholar: Boolean = true,
    val feesStatus: String = "CLEARED",
    val outstandingAmount: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreCard(
    @DocumentId val id: String = "",
    val userId: String = "",
    val cardIdentifier: String = "",
    val studentId: String = "",
    val studentNumber: String = "",
    val qrPayload: String = "",
    val status: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreScanLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentNumber: String = "",
    val studentName: String = "",
    val decision: String = "",
    val gatePoint: String = "Main Gate",
    @ServerTimestamp val scannedAt: Timestamp? = null
)
