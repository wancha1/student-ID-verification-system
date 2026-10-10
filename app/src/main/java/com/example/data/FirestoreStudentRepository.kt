package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.FirestoreCard
import com.example.data.model.FirestoreScanLog
import com.example.data.model.FirestoreStudent
import com.example.data.model.FirestoreUser
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreStudentRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing cloud Firestore.")
    }

    suspend fun saveUserProfile(displayName: String, email: String, role: String = "GATE_OFFICER"): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "role" to role,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addStudent(student: FirestoreStudent): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("students")
        val docId = if (student.id.isNotBlank()) student.id else colRef.document().id
        val docRef = colRef.document(docId)
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "studentNumber" to student.studentNumber,
                    "firstName" to student.firstName,
                    "lastName" to student.lastName,
                    "gradeClass" to student.gradeClass,
                    "isDayScholar" to student.isDayScholar,
                    "feesStatus" to student.feesStatus,
                    "outstandingAmount" to student.outstandingAmount,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun addCard(card: FirestoreCard): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("cards")
        val docId = if (card.id.isNotBlank()) card.id else colRef.document().id
        val docRef = colRef.document(docId)
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "cardIdentifier" to card.cardIdentifier,
                    "studentId" to card.studentId,
                    "studentNumber" to card.studentNumber,
                    "qrPayload" to card.qrPayload,
                    "status" to card.status,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordScanLog(log: FirestoreScanLog): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("scan_logs")
        val docId = if (log.id.isNotBlank()) log.id else colRef.document().id
        val docRef = colRef.document(docId)
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "studentNumber" to log.studentNumber,
                    "studentName" to log.studentName,
                    "decision" to log.decision,
                    "gatePoint" to log.gatePoint,
                    "scannedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    fun observeStudents(): Flow<List<FirestoreStudent>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("students")
        emitAll(
            colRef.snapshots().map { snapshot ->
                snapshot.toObjects(FirestoreStudent::class.java)
            }
        )
    }

    fun observeScanLogs(): Flow<List<FirestoreScanLog>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("scan_logs")
        emitAll(
            colRef.snapshots().map { snapshot ->
                snapshot.toObjects(FirestoreScanLog::class.java)
            }
        )
    }
}
