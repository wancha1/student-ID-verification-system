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
import com.example.data.local.StudentRequirementEntity
import com.example.model.FeeStatus
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
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
            requireConfiguredLtcDatabaseId(context)
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

    companion object {
        // Must match app/src/main/res/values/firebase_applet_config.xml and firebase.json.
        const val FIRESTORE_DATABASE_ID = "unconfigured-ltc-firestore-database-id"
    }

    override suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>> = try {
        val snapshot = getSchoolRef()
            .collection("students")
            .whereGreaterThan("updatedAt", timestamp)
            .get()
            .await()

        val privateByStudentId = if (canReadPrivateStudentData()) {
            getSchoolRef().collection("student_private").get().await()
                .documents.associateBy { it.id }
        } else emptyMap()

        val list = snapshot.documents.mapNotNull { doc ->
            try {
                val privateDoc = privateByStudentId[doc.id]
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
                    outstandingAmount = (doc.getDouble("outstandingAmount") ?: (doc.getLong("outstandingAmount")?.toDouble()) ?: 0.0),
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
                null
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

    override suspend fun fetchRequirementsUpdatedSince(timestamp: Long): Result<List<StudentRequirementEntity>> = try {
        val snapshot = getSchoolRef()
            .collection("requirements")
            .whereGreaterThan("updatedAt", timestamp)
            .get()
            .await()
        Result.success(snapshot.documents.mapNotNull { doc ->
            try {
                StudentRequirementEntity(
                    studentId = doc.getString("studentId") ?: doc.id,
                    studentNumber = doc.getString("studentNumber") ?: "",
                    studentName = doc.getString("studentName") ?: "",
                    gradeClass = doc.getString("gradeClass") ?: "",
                    uniformComplete = doc.getBoolean("uniformComplete") ?: false,
                    sportsKitComplete = doc.getBoolean("sportsKitComplete") ?: false,
                    textbooksSubmitted = doc.getBoolean("textbooksSubmitted") ?: false,
                    medicalFormSigned = doc.getBoolean("medicalFormSigned") ?: false,
                    schoolIdIssued = doc.getBoolean("schoolIdIssued") ?: false,
                    rulesAgreementSigned = doc.getBoolean("rulesAgreementSigned") ?: false,
                    busPassCleared = doc.getBoolean("busPassCleared") ?: false,
                    notes = "",
                    updatedAt = doc.getLong("updatedAt") ?: 0L,
                    isSyncedToCloud = true
                )
            } catch (_: Exception) {
                null
            }
        })
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> = try {
        val batch = db.batch()
        val canWritePrivate = canManageStudentPrivateData()
        for (student in students) {
            val docRef = getSchoolRef().collection("students").document(student.id)
            val data = hashMapOf(
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
            batch.set(docRef, data)
            if (canWritePrivate) {
                val privateRef = getSchoolRef().collection("student_private").document(student.id)
                batch.set(privateRef, mapOf(
                    "studentId" to student.id,
                    "guardianName" to student.guardianName,
                    "guardianPhone" to student.guardianPhone,
                    "emergencyContact" to student.emergencyContact,
                    "notes" to student.notes,
                    "updatedAt" to student.updatedAt
                ))
            }
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun canReadPrivateStudentData(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val staff = getSchoolRef().collection("staff").document(uid).get().await()
        if (staff.getBoolean("active") != true) return false
        val role = staff.getString("role")?.uppercase() ?: return false
        return role in setOf("ADMIN", "HEAD_TEACHER")
    }

    private suspend fun canManageStudentPrivateData(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val staff = getSchoolRef().collection("staff").document(uid).get().await()
        return staff.getBoolean("active") == true &&
            staff.getString("role")?.uppercase() in setOf("ADMIN", "HEAD_TEACHER")
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

    override suspend fun pushRequirementRecords(requirements: List<StudentRequirementEntity>): Result<Unit> = try {
        val batch = db.batch()
        for (requirement in requirements) {
            val docRef = getSchoolRef().collection("requirements").document(requirement.studentId)
            // Free-text notes are deliberately excluded from shared cloud documents.
            val data = mapOf(
                "studentId" to requirement.studentId,
                "studentNumber" to requirement.studentNumber,
                "studentName" to requirement.studentName,
                "gradeClass" to requirement.gradeClass,
                "uniformComplete" to requirement.uniformComplete,
                "sportsKitComplete" to requirement.sportsKitComplete,
                "textbooksSubmitted" to requirement.textbooksSubmitted,
                "medicalFormSigned" to requirement.medicalFormSigned,
                "schoolIdIssued" to requirement.schoolIdIssued,
                "rulesAgreementSigned" to requirement.rulesAgreementSigned,
                "busPassCleared" to requirement.busPassCleared,
                "updatedAt" to requirement.updatedAt
            )
            batch.set(docRef, data)
        }
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun pushExamExceptions(exceptions: List<ExamExceptionEntity>): Result<Unit> = try {
        val batch = db.batch()
        for (ex in exceptions) {
            val docRef = getSchoolRef().collection("exam_exceptions").document(ex.id)
            val data = hashMapOf(
                "id" to ex.id,
                "studentId" to ex.studentId,
                "examCode" to ex.examCode,
                "recommendedBy" to ex.requestedByBursar,
                "recommendedByUid" to ex.recommendedByUid,
                "isApproved" to ex.isApproved,
                "approvedBy" to (ex.approvedByHeadTeacher ?: ""),
                "approvedByUid" to (ex.approvedByUid ?: ""),
                // Server time is authoritative for the cloud audit event; the local Room
                // timestamp remains available for offline UI/history.
                "approvedAt" to if (ex.isApproved) FieldValue.serverTimestamp() else null
            )
            batch.set(docRef, data)
        }
        batch.commit().await()
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

private fun requireConfiguredLtcDatabaseId(context: Context): String {
    val databaseId = context.getString(R.string.firestore_database_id).trim()
    require(databaseId.isNotBlank() &&
        !databaseId.contains("oakridge", ignoreCase = true) &&
        !databaseId.contains("unconfigured", ignoreCase = true)) {
        "LTC Firebase is not configured. Set firestore_database_id to the confirmed LTC Firestore database ID."
    }
    return databaseId
}
