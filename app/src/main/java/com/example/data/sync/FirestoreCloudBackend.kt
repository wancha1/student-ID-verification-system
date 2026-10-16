package com.example.data.sync

import android.content.Context
import com.example.R
import com.example.data.FirestoreStudentRepository
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.CardEntity
import com.example.data.local.ExamExceptionEntity
import com.example.data.local.MealRecordEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.model.FeeStatus
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

/**
 * Production implementation of RemoteCloudDataSource backed by Cloud Firestore.
 * Supports school-scoped multi-device synchronization for Lira Town College.
 */
class FirestoreCloudBackend(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        FirebaseApp.getInstance(),
        FIRESTORE_DATABASE_ID
    ),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(FirebaseApp.getInstance()),
    private val schoolId: String = "ltc_school_01"
) : RemoteCloudDataSource {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            context.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance(FirebaseApp.getInstance()),
        "ltc_school_01"
    )

    constructor(firestoreRepo: FirestoreStudentRepository) : this(
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FIRESTORE_DATABASE_ID),
        FirebaseAuth.getInstance(FirebaseApp.getInstance()),
        "ltc_school_01"
    )

    private fun getSchoolRef() = db.collection("schools").document(schoolId)

    private suspend fun isStudentPrivacyStaff(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val staff = getSchoolRef().collection("staff").document(uid).get().await()
        return staff.getBoolean("active") == true &&
            staff.getString("role") in setOf("ADMIN", "HEAD_TEACHER")
    }

    companion object {
        // Must match app/src/main/res/values/firebase_applet_config.xml and firebase.json.
        const val FIRESTORE_DATABASE_ID = "ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba"
    }

    override suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>> = try {
        val snapshot = getSchoolRef()
            .collection("students")
            .whereGreaterThan("updatedAt", timestamp)
            .get()
            .await()
        val canReadPrivate = isStudentPrivacyStaff()
        val list = mutableListOf<StudentEntity>()
        for (doc in snapshot.documents) {
            try {
                val privateDoc = if (canReadPrivate) {
                    getSchoolRef().collection("student_private").document(doc.id).get().await()
                } else null
                list += StudentEntity(
                    id = doc.getString("id") ?: doc.id,
                    studentNumber = doc.getString("studentNumber") ?: "",
                    name = doc.getString("name") ?: "${doc.getString("firstName") ?: ""} ${doc.getString("lastName") ?: ""}".trim(),
                    firstName = doc.getString("firstName") ?: "",
                    lastName = doc.getString("lastName") ?: "",
                    photoUrl = doc.getString("photoUrl"),
                    accessStatus = doc.getString("accessStatus") ?: "APPROVED",
                    gradeClass = doc.getString("gradeClass") ?: "",
                    isDayScholar = doc.getBoolean("isDayScholar") ?: true,
                    dayScholarType = doc.getString("dayScholarType") ?: "DAY_SCHOLAR_WALK",
                    transportRoute = doc.getString("transportRoute") ?: "",
                    feesStatus = doc.getString("feesStatus") ?: "CLEARED",
                    outstandingAmount = doc.getDouble("outstandingAmount") ?: (doc.getLong("outstandingAmount")?.toDouble() ?: 0.0),
                    gender = doc.getString("gender") ?: "Male",
                    avatarColorSeed = doc.getLong("avatarColorSeed") ?: 42L,
                    guardianName = privateDoc?.getString("guardianName") ?: "",
                    guardianPhone = privateDoc?.getString("guardianPhone") ?: "",
                    emergencyContact = privateDoc?.getString("emergencyContact") ?: "",
                    homeroomTeacher = doc.getString("homeroomTeacher") ?: "",
                    academicYear = doc.getString("academicYear") ?: "2026",
                    notes = privateDoc?.getString("notes") ?: "",
                    qrToken = doc.getString("qrToken") ?: "",
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                    isDeleted = doc.getBoolean("isDeleted") ?: false
                )
            } catch (_: Exception) {
                // Skip malformed records; Firestore permission failures propagate from the query above.
            }
        }
        Result.success(list)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun fetchCardsUpdatedSince(timestamp: Long): Result<List<CardEntity>> = try {
        val snapshot = getSchoolRef()
            .collection("cards")
            .whereGreaterThan("updatedAt", timestamp)
            .get()
            .await()

        val list = snapshot.documents.mapNotNull { doc ->
            try {
                CardEntity(
                    id = doc.getString("id") ?: doc.id,
                    cardIdentifier = doc.getString("cardIdentifier") ?: "",
                    studentId = doc.getString("studentId") ?: "",
                    studentNumber = doc.getString("studentNumber") ?: "",
                    qrPayload = doc.getString("qrPayload") ?: "",
                    status = doc.getString("status") ?: "ACTIVE",
                    issueDate = doc.getLong("issueDate") ?: 0L,
                    activationDate = doc.getLong("activationDate") ?: 0L,
                    deactivationDate = doc.getLong("deactivationDate"),
                    replacedByCardId = doc.getString("replacedByCardId"),
                    reason = doc.getString("reason"),
                    notes = doc.getString("notes") ?: "",
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                    isDeleted = doc.getBoolean("isDeleted") ?: false
                )
            } catch (_: Exception) {
                null
            }
        }
        Result.success(list)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> = try {
        val batch = db.batch()
        val canWritePrivate = isStudentPrivacyStaff()
        for (student in students) {
            val docRef = getSchoolRef().collection("students").document(student.id)
            val data = hashMapOf<String, Any>(
                "id" to student.id,
                "studentNumber" to student.studentNumber,
                "name" to student.name,
                "firstName" to student.firstName,
                "lastName" to student.lastName,
                "photoUrl" to (student.photoUrl ?: ""),
                "accessStatus" to student.accessStatus,
                "gradeClass" to student.gradeClass,
                "isDayScholar" to student.isDayScholar,
                "dayScholarType" to student.dayScholarType,
                "transportRoute" to student.transportRoute,
                "feesStatus" to student.feesStatus,
                "outstandingAmount" to student.outstandingAmount,
                "gender" to student.gender,
                "avatarColorSeed" to student.avatarColorSeed,
                "homeroomTeacher" to student.homeroomTeacher,
                "academicYear" to student.academicYear,
                "qrToken" to student.qrToken,
                "updatedAt" to student.updatedAt,
                "isDeleted" to student.isDeleted
            )
            if (canWritePrivate) {
                // Remove legacy sensitive fields from the shared student document.
                data["guardianName"] = FieldValue.delete()
                data["guardianPhone"] = FieldValue.delete()
                data["emergencyContact"] = FieldValue.delete()
                data["notes"] = FieldValue.delete()
                val privateRef = getSchoolRef().collection("student_private").document(student.id)
                batch.set(
                    privateRef,
                    mapOf(
                        "studentId" to student.id,
                        "guardianName" to student.guardianName,
                        "guardianPhone" to student.guardianPhone,
                        "emergencyContact" to student.emergencyContact,
                        "notes" to student.notes,
                        "updatedAt" to student.updatedAt
                    ),
                    SetOptions.merge()
                )
            }
            batch.set(docRef, data, SetOptions.merge())
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit> = try {
        val batch = db.batch()
        for (card in cards) {
            val docRef = getSchoolRef().collection("cards").document(card.id)
            val data = hashMapOf(
                "id" to card.id,
                "cardIdentifier" to card.cardIdentifier,
                "studentId" to card.studentId,
                "studentNumber" to card.studentNumber,
                "qrPayload" to card.qrPayload,
                "status" to card.status,
                "issueDate" to card.issueDate,
                "activationDate" to card.activationDate,
                "deactivationDate" to (card.deactivationDate ?: 0L),
                "replacedByCardId" to (card.replacedByCardId ?: ""),
                "reason" to (card.reason ?: ""),
                "notes" to (card.notes ?: ""),
                "updatedAt" to card.updatedAt,
                "isDeleted" to card.isDeleted
            )
            batch.set(docRef, data)
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit> = try {
        val batch = db.batch()
        for (log in logs) {
            val docRef = getSchoolRef().collection("gate_logs").document(log.id)
            val data = hashMapOf(
                "id" to log.id,
                "studentNumber" to (log.studentNumber ?: "UNKNOWN"),
                "studentName" to log.studentName,
                "decision" to log.decision,
                "gateLocation" to log.gateLocation,
                "timestamp" to log.timestamp
            )
            batch.set(docRef, data)
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushMealRecords(meals: List<MealRecordEntity>): Result<Unit> = try {
        val batch = db.batch()
        for (meal in meals) {
            val docRef = getSchoolRef().collection("meals").document(meal.id)
            val data = hashMapOf(
                "id" to meal.id,
                "studentId" to meal.studentId,
                "mealType" to meal.mealType,
                "servingStation" to meal.serverName,
                "timestamp" to meal.timestamp
            )
            batch.set(docRef, data)
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushAttendanceRecords(attendance: List<AttendanceRecordEntity>): Result<Unit> = try {
        val batch = db.batch()
        for (att in attendance) {
            val docRef = getSchoolRef().collection("attendance").document(att.id)
            val data = hashMapOf(
                "id" to att.id,
                "studentId" to att.studentId,
                "subject" to att.subject,
                "sessionName" to att.sessionName,
                "status" to if (att.isPresent) "PRESENT" else "ABSENT",
                "timestamp" to att.timestamp
            )
            batch.set(docRef, data)
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushExamExceptions(exceptions: List<ExamExceptionEntity>): Result<Unit> = try {
        val uid = auth.currentUser?.uid
            ?: throw SecurityException("A signed-in staff identity is required to sync exam exceptions.")
        val staff = getSchoolRef().collection("staff").document(uid).get().await()
        if (staff.getBoolean("active") != true) {
            throw SecurityException("Inactive or unprovisioned staff cannot sync exam exceptions.")
        }
        val authenticatedName = staff.getString("name")
            ?: staff.getString("displayName")
            ?: uid

        for (exception in exceptions) {
            val docRef = getSchoolRef().collection("exam_exceptions").document(exception.id)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                // Do not fabricate an approval or attribute an offline recommendation to
                // whichever device happens to sync it. The original recommendation must
                // first be created by the authenticated Bursar/Finance account.
                if (exception.isApproved) {
                    throw IllegalStateException(
                        "Cannot sync approved exam exception ${exception.id}: its authenticated recommendation is missing. Sync the Bursar recommendation first."
                    )
                }
                docRef.set(
                    mapOf(
                        "id" to exception.id,
                        "studentId" to exception.studentId,
                        "examCode" to exception.examCode,
                        "recommendedBy" to authenticatedName,
                        "recommendedByUid" to uid,
                        "isApproved" to false
                    )
                ).await()
            } else if (exception.isApproved && existing.getBoolean("isApproved") != true) {
                // Only the current authenticated leadership account can complete this transition;
                // Firestore rules independently enforce the allowed role and immutable identity.
                docRef.update(
                    mapOf(
                        "isApproved" to true,
                        "approvedBy" to authenticatedName,
                        "approvedByUid" to uid,
                        "approvedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()
            }
            // Existing pending records are left unchanged on retry; approved records cannot be downgraded.
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun fetchAllRemoteStudents(): Result<List<StudentEntity>> {
        return fetchStudentsUpdatedSince(-1L)
    }

    override suspend fun fetchAllRemoteCards(): Result<List<CardEntity>> {
        return fetchCardsUpdatedSince(-1L)
    }

    override suspend fun updateRemoteFeeStatus(
        studentId: String,
        newStatus: FeeStatus,
        outstandingAmount: Double,
        updatedAt: Long
    ): Result<Unit> = try {
        val docRef = getSchoolRef().collection("students").document(studentId)
        docRef.update(
            mapOf(
                "feesStatus" to newStatus.name,
                "outstandingAmount" to outstandingAmount,
                "updatedAt" to updatedAt
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit> = try {
        val docRef = getSchoolRef().collection("students").document(studentId)
        docRef.update(
            mapOf(
                "isDeleted" to true,
                "updatedAt" to updatedAt
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateRemoteCard(card: CardEntity): Result<Unit> = try {
        val docRef = getSchoolRef().collection("cards").document(card.id)
        docRef.update(
            mapOf(
                "status" to card.status,
                "deactivationDate" to (card.deactivationDate ?: 0L),
                "replacedByCardId" to (card.replacedByCardId ?: ""),
                "reason" to (card.reason ?: ""),
                "notes" to (card.notes ?: ""),
                "updatedAt" to card.updatedAt,
                "isDeleted" to card.isDeleted
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
