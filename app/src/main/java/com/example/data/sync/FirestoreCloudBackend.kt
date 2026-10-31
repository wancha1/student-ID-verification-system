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
import com.google.firebase.firestore.FieldPath
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
            requireAuthorizedLtcDatabaseId(context)
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

    private suspend fun currentActiveStaffRole(): String? {
        val uid = auth.currentUser?.uid ?: return null
        val staff = getSchoolRef().collection("staff").document(uid).get().await()
        return if (staff.getBoolean("active") == true) staff.getString("role") else null
    }

    private suspend fun isStudentPrivacyStaff(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val staff = getSchoolRef().collection("staff").document(uid).get().await()
        return staff.getBoolean("active") == true &&
            staff.getString("role") in setOf("ADMIN", "HEAD_TEACHER")
    }

    companion object {
        private const val MAX_BATCH_WRITES = 400

        // Must match app/src/main/res/values/firebase_applet_config.xml and firebase.json.
        const val FIRESTORE_DATABASE_ID = "ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba"
    }

    override suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>> = try {
        val snapshot = getSchoolRef()
            .collection("students")
            .whereGreaterThan("updatedAt", timestamp)
            .get()
            .await()

        // Fetch private records in bounded document-ID batches instead of issuing one
        // network request per student. Non-leadership staff never query this collection.
        val canReadPrivate = isStudentPrivacyStaff()
        val privateById = if (canReadPrivate && snapshot.documents.isNotEmpty()) {
            snapshot.documents.map { it.id }.distinct().chunked(30).flatMap { ids ->
                getSchoolRef().collection("student_private")
                    .whereIn(FieldPath.documentId(), ids)
                    .get()
                    .await()
                    .documents
            }.associateBy { it.id }
        } else {
            emptyMap()
        }

        // Do not silently skip malformed records: a failed pull must keep the sync
        // cycle retryable instead of advancing the watermark past missing data.
        val list = snapshot.documents.map { doc ->
            val privateDoc = privateById[doc.id]
            StudentEntity(
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
                outstandingAmount = doc.getDouble("outstandingAmount")
                    ?: (doc.getLong("outstandingAmount")?.toDouble() ?: 0.0),
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

        val list = snapshot.documents.map { doc ->
            CardEntity(
                    id = doc.getString("id") ?: doc.id,
                    cardIdentifier = doc.getString("cardIdentifier") ?: "",
                    studentId = doc.getString("studentId") ?: "",
                    studentNumber = doc.getString("studentNumber") ?: "",
                    qrPayload = doc.getString("qrPayload") ?: "",
                    status = doc.getString("status") ?: "DEACTIVATED",
                    issueDate = doc.getLong("issueDate") ?: 0L,
                    activationDate = doc.getLong("activationDate") ?: 0L,
                    deactivationDate = doc.getLong("deactivationDate"),
                    replacedByCardId = doc.getString("replacedByCardId"),
                    reason = doc.getString("reason"),
                    notes = doc.getString("notes") ?: "",
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                    isDeleted = doc.getBoolean("isDeleted") ?: false
                )
        }
        Result.success(list)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> = try {
        if (students.isEmpty()) return Result.success(Unit)
        val role = currentActiveStaffRole()
            ?: throw SecurityException("Active staff sign-in is required to sync student records.")
        val canWriteShared = role == "ADMIN"
        val canWritePrivate = role in setOf("ADMIN", "HEAD_TEACHER")
        if (!canWriteShared && !canWritePrivate) {
            throw SecurityException("Only Admin or Head Teacher can synchronize permitted student information.")
        }

        // Admin writes the shared roster and private profile. Head Teacher writes
        // private profiles only; Firestore rules keep the shared roster Admin-only.
        for (chunk in students.chunked(200)) {
            val batch = db.batch()
            for (student in chunk) {
                if (canWriteShared) {
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
                        "transportRoute" to (student.transportRoute ?: ""),
                        "gender" to student.gender,
                        "avatarColorSeed" to student.avatarColorSeed,
                        "homeroomTeacher" to student.homeroomTeacher,
                        "academicYear" to student.academicYear,
                        "qrToken" to student.qrToken,
                        "updatedAt" to student.updatedAt,
                        "isDeleted" to student.isDeleted
                    )
                    // Explicitly remove retired finance and private fields from the shared roster.
                    data["feesStatus"] = FieldValue.delete()
                    data["outstandingAmount"] = FieldValue.delete()
                    data["guardianName"] = FieldValue.delete()
                    data["guardianPhone"] = FieldValue.delete()
                    data["emergencyContact"] = FieldValue.delete()
                    data["notes"] = FieldValue.delete()
                    batch.set(docRef, data, SetOptions.merge())
                }

                if (canWritePrivate) {
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
            }
            batch.commit().await()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit> = try {
        if (cards.isEmpty()) return Result.success(Unit)
        if (currentActiveStaffRole() != "ADMIN") {
            throw SecurityException("Only the Administrator can synchronize ID-card changes.")
        }
        // Firestore batches have a 500-write ceiling. Keep headroom for future
        // metadata writes and ensure a large offline backlog cannot fail as one batch.
        for (chunk in cards.chunked(MAX_BATCH_WRITES)) {
            val batch = db.batch()
            for (card in chunk) {
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
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit> = try {
        if (logs.isEmpty()) return Result.success(Unit)
        if (currentActiveStaffRole() !in setOf("ADMIN", "GATE_OFFICER")) {
            throw SecurityException("Only Admin or Gate Officer can synchronize gate logs.")
        }
        for (chunk in logs.chunked(MAX_BATCH_WRITES)) {
            val batch = db.batch()
            for (log in chunk) {
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
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushMealRecords(meals: List<MealRecordEntity>): Result<Unit> = try {
        if (meals.isEmpty()) return Result.success(Unit)
        if (currentActiveStaffRole() !in setOf("ADMIN", "MEAL_STAFF")) {
            throw SecurityException("Only Admin or Meal Staff can synchronize meal records.")
        }
        for (chunk in meals.chunked(MAX_BATCH_WRITES)) {
            val batch = db.batch()
            for (meal in chunk) {
                val docRef = getSchoolRef().collection("meals").document(meal.id)
                // Persist the full operational record, not only identifiers. Without
                // date/status/class fields, the cloud audit cannot reconstruct the local meal log.
                val data = hashMapOf(
                    "id" to meal.id,
                    "studentId" to meal.studentId,
                    "studentNumber" to meal.studentNumber,
                    "studentName" to meal.studentName,
                    "gradeClass" to meal.gradeClass,
                    "mealType" to meal.mealType,
                    "mealDate" to meal.mealDate,
                    "status" to meal.status,
                    "notes" to meal.notes,
                    "servingStation" to meal.serverName,
                    "timestamp" to meal.timestamp
                )
                batch.set(docRef, data)
            }
            batch.commit().await()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushAttendanceRecords(attendance: List<AttendanceRecordEntity>): Result<Unit> = try {
        for (chunk in attendance.chunked(MAX_BATCH_WRITES)) {
            val batch = db.batch()
            for (att in chunk) {
                val docRef = getSchoolRef().collection("attendance").document(att.id)
                // Preserve the fields needed to audit attendance and corrections
                // across devices, rather than uploading a lossy summary.
                val data = hashMapOf(
                    "id" to att.id,
                    "studentId" to att.studentId,
                    "studentNumber" to att.studentNumber,
                    "studentName" to att.studentName,
                    "gradeClass" to att.gradeClass,
                    "subject" to att.subject,
                    "sessionName" to att.sessionName,
                    "lessonDate" to att.lessonDate,
                    "status" to if (att.isPresent) "PRESENT" else "ABSENT",
                    "isPresent" to att.isPresent,
                    "teacherName" to att.teacherName,
                    "notes" to att.notes,
                    "isCorrection" to att.isCorrection,
                    "correctedBy" to (att.correctedBy ?: ""),
                    "timestamp" to att.timestamp
                )
                batch.set(docRef, data)
            }
            batch.commit().await()
        }
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
                        "recommendationReason" to exception.recommendationReason,
                        "requestedAt" to exception.requestedAt,
                        "isApproved" to false
                    )
                ).await()
            } else if (exception.isApproved && existing.getBoolean("isApproved") != true) {
                docRef.update(
                    mapOf(
                        "isApproved" to true,
                        "approvedBy" to authenticatedName,
                        "approvedByUid" to uid,
                        "approvedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            // Existing pending records are idempotent on retry; approved records are never downgraded.
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun fetchAllRemoteMealRecords(): Result<List<MealRecordEntity>> {
        return try {
            val role = currentActiveStaffRole()
                ?: throw SecurityException("Active staff sign-in is required to sync meal records.")
            if (role !in setOf("ADMIN", "HEAD_TEACHER", "MEAL_STAFF")) {
                return Result.success(emptyList())
            }
            val snapshot = getSchoolRef().collection("meals").get().await()
            Result.success(snapshot.documents.map { doc ->
                MealRecordEntity(
                    id = doc.getString("id") ?: doc.id,
                    studentId = doc.getString("studentId") ?: "",
                    studentNumber = doc.getString("studentNumber") ?: "",
                    studentName = doc.getString("studentName") ?: "",
                    gradeClass = doc.getString("gradeClass") ?: "",
                    mealType = doc.getString("mealType") ?: "LUNCH",
                    mealDate = doc.getString("mealDate") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    serverName = doc.getString("servingStation") ?: "",
                    status = doc.getString("status") ?: "SERVED",
                    notes = doc.getString("notes") ?: "",
                    isSyncedToCloud = true
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchAllRemoteAttendanceRecords(): Result<List<AttendanceRecordEntity>> {
        return try {
            val role = currentActiveStaffRole()
                ?: throw SecurityException("Active staff sign-in is required to sync attendance records.")
            if (role !in setOf("ADMIN", "HEAD_TEACHER")) {
                return Result.success(emptyList())
            }
            val snapshot = getSchoolRef().collection("attendance").get().await()
            Result.success(snapshot.documents.map { doc ->
                val present = doc.getBoolean("isPresent") ?: (doc.getString("status") == "PRESENT")
                AttendanceRecordEntity(
                    id = doc.getString("id") ?: doc.id,
                    studentId = doc.getString("studentId") ?: "",
                    studentNumber = doc.getString("studentNumber") ?: "",
                    studentName = doc.getString("studentName") ?: "",
                    gradeClass = doc.getString("gradeClass") ?: "",
                    subject = doc.getString("subject") ?: "",
                    sessionName = doc.getString("sessionName") ?: "",
                    lessonDate = doc.getString("lessonDate") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    teacherName = doc.getString("teacherName") ?: "",
                    isPresent = present,
                    notes = doc.getString("notes") ?: "",
                    isCorrection = doc.getBoolean("isCorrection") ?: false,
                    correctedBy = doc.getString("correctedBy")?.takeIf { it.isNotBlank() },
                    isSyncedToCloud = true
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchAllRemoteExamExceptions(): Result<List<ExamExceptionEntity>> {
        return try {
            val role = currentActiveStaffRole()
                ?: throw SecurityException("Active staff sign-in is required to sync exam exceptions.")
            if (role !in setOf("ADMIN", "HEAD_TEACHER")) {
                return Result.success(emptyList())
            }
            val snapshot = getSchoolRef().collection("exam_exceptions").get().await()
            Result.success(snapshot.documents.map { doc ->
                val approvedAtValue = doc.get("approvedAt")
                val approvedAt = when (approvedAtValue) {
                    is Long -> approvedAtValue
                    is com.google.firebase.Timestamp -> approvedAtValue.toDate().time
                    else -> null
                }
                ExamExceptionEntity(
                    id = doc.getString("id") ?: doc.id,
                    studentId = doc.getString("studentId") ?: "",
                    studentNumber = doc.getString("studentNumber") ?: "",
                    studentName = doc.getString("studentName") ?: "",
                    examCode = doc.getString("examCode") ?: "",
                    requestedByBursar = doc.getString("recommendedBy") ?: "",
                    recommendationReason = doc.getString("recommendationReason") ?: "",
                    isApproved = doc.getBoolean("isApproved") ?: false,
                    approvedByHeadTeacher = doc.getString("approvedBy"),
                    requestedAt = doc.getLong("requestedAt") ?: 0L,
                    approvedAt = approvedAt,
                    isSyncedToCloud = true
                )
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
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

private fun requireAuthorizedLtcDatabaseId(context: Context): String {
    val databaseId = context.getString(R.string.firestore_database_id).trim()
    require(databaseId == FirestoreCloudBackend.FIRESTORE_DATABASE_ID) {
        "LTC Firebase database configuration does not match the owner-authorized school database."
    }
    return databaseId
}
