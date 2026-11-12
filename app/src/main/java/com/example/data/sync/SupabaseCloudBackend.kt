package com.example.data.sync

import android.content.Context
import android.net.Uri
import com.example.data.auth.SupabaseClientFactory
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.CardEntity
import com.example.data.local.ExamExceptionEntity
import com.example.data.local.MealRecordEntity
import com.example.data.local.ScanLogEntity
import com.example.data.local.StudentEntity
import com.example.model.FeeStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Supabase is the only production cloud backend for LTC V1.
 *
 * Room remains the offline source of truth on-device. This adapter owns remote
 * roster, private profile, card, gate-log, meal and private-photo operations.
 * Attendance and exam-exception tables are deliberately outside V1 cloud scope.
 */
class SupabaseCloudBackend(
    private val context: Context,
    private val client: SupabaseClient = SupabaseClientFactory.getInstance(context.applicationContext),
    private val schoolId: String = LTC_SCHOOL_ID
) : RemoteCloudDataSource {

    private suspend fun activeRole(): String {
        val user = client.auth.retrieveUserForCurrentSession()
        val membership = client.from("staff_memberships").select {
            filter {
                eq("school_id", schoolId)
                eq("user_id", user.id)
                eq("is_active", true)
            }
        }.decodeSingleOrNull<MembershipRow>()
            ?: throw SecurityException("Active LTC staff membership is required.")
        return membership.role
    }

    override suspend fun fetchStudentsUpdatedSince(timestamp: Long): Result<List<StudentEntity>> = runCatching {
        val role = activeRole()
        val roster = client.from("students").select {
            filter {
                eq("school_id", schoolId)
                gt("updated_at_ms", timestamp)
            }
        }.decodeList<StudentRow>()

        val privateById = if (role == "ADMIN" || role == "HEAD_TEACHER") {
            client.from("student_private").select {
                filter { eq("school_id", schoolId) }
            }.decodeList<StudentPrivateRow>().associateBy { it.studentId }
        } else emptyMap()

        roster.map { row ->
            val private = privateById[row.id]
            StudentEntity(
                id = row.id,
                studentNumber = row.studentNumber,
                name = row.name,
                firstName = row.firstName.orEmpty(),
                lastName = row.lastName.orEmpty(),
                photoUrl = downloadPhotoToCache(row.photoPath, row.id),
                accessStatus = row.accessStatus,
                gradeClass = row.gradeClass,
                isDayScholar = row.isDayScholar,
                dayScholarType = row.dayScholarType ?: "DAY_SCHOLAR_WALK",
                transportRoute = row.transportRoute.orEmpty(),
                feesStatus = row.feeStatus,
                outstandingAmount = private?.outstandingAmount ?: 0.0,
                gender = row.gender ?: "Male",
                avatarColorSeed = row.avatarColorSeed ?: 42L,
                guardianName = private?.guardianName.orEmpty(),
                guardianPhone = private?.guardianPhone.orEmpty(),
                emergencyContact = private?.emergencyContact.orEmpty(),
                homeroomTeacher = row.homeroomTeacher.orEmpty(),
                academicYear = row.academicYear ?: "2026",
                notes = private?.notes.orEmpty(),
                qrToken = row.qrToken.orEmpty(),
                updatedAt = row.updatedAtMs,
                isDeleted = row.isDeleted
            )
        }
    }

    override suspend fun fetchCardsUpdatedSince(timestamp: Long): Result<List<CardEntity>> = runCatching {
        activeRole()
        client.from("cards").select {
            filter {
                eq("school_id", schoolId)
                gt("updated_at_ms", timestamp)
            }
        }.decodeList<CardRow>().map { it.toEntity() }
    }

    override suspend fun fetchAllRemoteStudents(): Result<List<StudentEntity>> =
        fetchStudentsUpdatedSince(-1L)

    override suspend fun fetchAllRemoteCards(): Result<List<CardEntity>> =
        fetchCardsUpdatedSince(-1L)

    override suspend fun fetchAllRemoteMealRecords(): Result<List<MealRecordEntity>> = runCatching {
        activeRole()
        client.from("meals").select {
            filter { eq("school_id", schoolId) }
        }.decodeList<MealRow>().map { it.toEntity() }
    }

    // These modules are intentionally excluded from LTC V1 remote sync.
    override suspend fun fetchAllRemoteAttendanceRecords(): Result<List<AttendanceRecordEntity>> =
        Result.success(emptyList())

    override suspend fun fetchAllRemoteExamExceptions(): Result<List<ExamExceptionEntity>> =
        Result.success(emptyList())

    override suspend fun pushStudentChanges(students: List<StudentEntity>): Result<Unit> = runCatching {
        if (students.isEmpty()) return@runCatching
        val role = activeRole()
        if (role !in setOf("ADMIN", "HEAD_TEACHER")) {
            throw SecurityException("Only school leadership can update student records.")
        }

        for (student in students) {
            val photoPath = uploadPhotoIfLocal(student)
            if (role == "ADMIN") {
                client.from("students").upsert(
                    StudentRow.from(student, schoolId, photoPath)
                ) { onConflict = "school_id,id" }
            }
            client.from("student_private").upsert(
                StudentPrivateRow(
                    schoolId = schoolId,
                    studentId = student.id,
                    guardianName = student.guardianName.takeIf { it.isNotBlank() },
                    guardianPhone = student.guardianPhone.takeIf { it.isNotBlank() },
                    emergencyContact = student.emergencyContact.takeIf { it.isNotBlank() },
                    notes = student.notes.takeIf { it.isNotBlank() },
                    outstandingAmount = student.outstandingAmount,
                    updatedAtMs = student.updatedAt
                )
            ) { onConflict = "school_id,student_id" }
        }
    }

    override suspend fun pushCardChanges(cards: List<CardEntity>): Result<Unit> = runCatching {
        if (cards.isEmpty()) return@runCatching
        if (activeRole() != "ADMIN") throw SecurityException("Only an administrator can manage student cards.")
        cards.forEach { card ->
            client.from("cards").upsert(CardRow.from(card, schoolId)) {
                onConflict = "school_id,id"
            }
        }
    }

    override suspend fun pushGateLogs(logs: List<ScanLogEntity>): Result<Unit> = runCatching {
        if (logs.isEmpty()) return@runCatching
        if (activeRole() !in setOf("ADMIN", "GATE_OFFICER")) {
            throw SecurityException("Only gate staff can upload gate verification records.")
        }
        logs.forEach { log ->
            client.from("gate_logs").upsert(GateLogRow.from(log, schoolId)) {
                onConflict = "school_id,id"
            }
        }
    }

    override suspend fun pushMealRecords(meals: List<MealRecordEntity>): Result<Unit> = runCatching {
        if (meals.isEmpty()) return@runCatching
        if (activeRole() !in setOf("ADMIN", "MEAL_STAFF")) {
            throw SecurityException("Only meal staff can upload meal records.")
        }
        meals.forEach { meal ->
            client.from("meals").upsert(MealRow.from(meal, schoolId)) {
                onConflict = "school_id,id"
            }
        }
    }

    override suspend fun pushAttendanceRecords(attendance: List<AttendanceRecordEntity>): Result<Unit> =
        Result.success(Unit)

    override suspend fun pushExamExceptions(exceptions: List<ExamExceptionEntity>): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateRemoteFeeStatus(
        studentId: String,
        newStatus: FeeStatus,
        outstandingAmount: Double,
        updatedAt: Long
    ): Result<Unit> = runCatching {
        if (activeRole() != "ADMIN") throw SecurityException("Only an administrator can update fee status.")
        client.from("students").update({
            set("fee_status", newStatus.name)
            set("updated_at_ms", updatedAt)
        }) {
            filter {
                eq("school_id", schoolId)
                eq("id", studentId)
            }
        }
        client.from("student_private").upsert(
            StudentPrivateRow(
                schoolId = schoolId,
                studentId = studentId,
                guardianName = null,
                guardianPhone = null,
                emergencyContact = null,
                notes = null,
                outstandingAmount = outstandingAmount,
                updatedAtMs = updatedAt
            )
        ) { onConflict = "school_id,student_id" }
    }

    override suspend fun deleteRemoteStudent(studentId: String, updatedAt: Long): Result<Unit> = runCatching {
        if (activeRole() != "ADMIN") throw SecurityException("Only an administrator can deactivate student records.")
        client.from("students").update({
            set("is_deleted", true)
            set("updated_at_ms", updatedAt)
        }) {
            filter {
                eq("school_id", schoolId)
                eq("id", studentId)
            }
        }
    }

    override suspend fun updateRemoteCard(card: CardEntity): Result<Unit> =
        pushCardChanges(listOf(card))

    override suspend fun refreshStudentPhotos(): Result<Map<String, String>> = runCatching {
        activeRole()
        val rows = client.from("students").select {
            filter {
                eq("school_id", schoolId)
                neq("photo_path", null)
            }
        }.decodeList<StudentRow>()
        rows.mapNotNull { row ->
            val local = downloadPhotoToCache(row.photoPath, row.id)
            if (local.isNullOrBlank()) null else row.id to local
        }.toMap()
    }

    private suspend fun uploadPhotoIfLocal(student: StudentEntity): String? {
        val current = student.photoUrl?.trim().orEmpty()
        if (current.isBlank()) return null
        if (current.startsWith("$schoolId/") && current.endsWith("/photo.jpg")) return current

        val uri = Uri.parse(current)
        val bytes = when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            "file" -> File(uri.path.orEmpty()).takeIf { it.isFile && it.canRead() }?.readBytes()
            null, "" -> File(current).takeIf { it.isFile && it.canRead() }?.readBytes()
            else -> null
        } ?: throw IllegalStateException("Student photo is not readable on this device.")

        require(bytes.isNotEmpty() && bytes.size <= MAX_PHOTO_BYTES) {
            "Student photo must be between 1 byte and 5 MB."
        }
        val path = "$schoolId/${student.id}/photo.jpg"
        client.storage.from(PHOTO_BUCKET).upload(path, bytes, upsert = true)
        return path
    }

    private suspend fun downloadPhotoToCache(path: String?, studentId: String): String? {
        if (path.isNullOrBlank()) return null
        if (!path.startsWith("$schoolId/") || !path.endsWith("/photo.jpg")) return null
        return runCatching {
            val bytes = client.storage.from(PHOTO_BUCKET).downloadAuthenticated(path)
            if (bytes.isEmpty() || bytes.size > MAX_PHOTO_BYTES) return@runCatching null
            val dir = File(context.filesDir, "student_photos")
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, "supabase_${Uri.encode(studentId)}.jpg")
            val temp = File(dir, target.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    @Serializable
    private data class MembershipRow(
        @SerialName("school_id") val schoolId: String,
        @SerialName("user_id") val userId: String,
        val role: String,
        @SerialName("is_active") val isActive: Boolean
    )

    @Serializable
    private data class StudentRow(
        @SerialName("school_id") val schoolId: String,
        val id: String,
        @SerialName("student_number") val studentNumber: String,
        val name: String,
        @SerialName("first_name") val firstName: String? = null,
        @SerialName("last_name") val lastName: String? = null,
        @SerialName("photo_path") val photoPath: String? = null,
        @SerialName("access_status") val accessStatus: String = "APPROVED",
        @SerialName("fee_status") val feeStatus: String = "CLEARED",
        @SerialName("grade_class") val gradeClass: String = "",
        @SerialName("class_name") val className: String? = null,
        val stream: String? = null,
        @SerialName("is_day_scholar") val isDayScholar: Boolean = false,
        @SerialName("day_scholar_type") val dayScholarType: String? = null,
        @SerialName("transport_route") val transportRoute: String? = null,
        val gender: String? = null,
        @SerialName("avatar_color_seed") val avatarColorSeed: Long? = null,
        @SerialName("homeroom_teacher") val homeroomTeacher: String? = null,
        @SerialName("academic_year") val academicYear: String? = null,
        @SerialName("qr_token") val qrToken: String? = null,
        @SerialName("updated_at_ms") val updatedAtMs: Long,
        @SerialName("is_deleted") val isDeleted: Boolean = false
    ) {
        companion object {
            fun from(s: StudentEntity, school: String, photo: String?) = StudentRow(
                schoolId = school, id = s.id, studentNumber = s.studentNumber, name = s.name,
                firstName = s.firstName, lastName = s.lastName, photoPath = photo,
                accessStatus = s.accessStatus, feeStatus = s.feesStatus, gradeClass = s.gradeClass, className = s.gradeClass,
                isDayScholar = s.isDayScholar, dayScholarType = s.dayScholarType,
                transportRoute = s.transportRoute, gender = s.gender,
                avatarColorSeed = s.avatarColorSeed, homeroomTeacher = s.homeroomTeacher,
                academicYear = s.academicYear, qrToken = s.qrToken, updatedAtMs = s.updatedAt,
                isDeleted = s.isDeleted
            )
        }
    }

    @Serializable
    private data class StudentPrivateRow(
        @SerialName("school_id") val schoolId: String,
        @SerialName("student_id") val studentId: String,
        @SerialName("guardian_name") val guardianName: String?,
        @SerialName("guardian_phone") val guardianPhone: String?,
        @SerialName("emergency_contact") val emergencyContact: String?,
        val notes: String?,
        @SerialName("outstanding_amount") val outstandingAmount: Double = 0.0,
        @SerialName("updated_at_ms") val updatedAtMs: Long
    )

    @Serializable
    private data class CardRow(
        @SerialName("school_id") val schoolId: String,
        val id: String,
        @SerialName("card_identifier") val cardIdentifier: String,
        @SerialName("student_id") val studentId: String,
        @SerialName("student_number") val studentNumber: String,
        @SerialName("qr_payload") val qrPayload: String,
        val status: String,
        @SerialName("issue_date_ms") val issueDate: Long,
        @SerialName("activation_date_ms") val activationDate: Long? = null,
        @SerialName("deactivation_date_ms") val deactivationDate: Long? = null,
        @SerialName("replaced_by_card_id") val replacedByCardId: String? = null,
        val reason: String? = null,
        val notes: String? = null,
        @SerialName("updated_at_ms") val updatedAt: Long,
        @SerialName("is_deleted") val isDeleted: Boolean = false
    ) {
        fun toEntity() = CardEntity(id, cardIdentifier, studentId, studentNumber, qrPayload, status,
            issueDate, activationDate ?: 0L, deactivationDate, replacedByCardId, reason, notes.orEmpty(), updatedAt, isDeleted)
        companion object {
            fun from(c: CardEntity, school: String) = CardRow(school, c.id, c.cardIdentifier, c.studentId,
                c.studentNumber, c.qrPayload, c.status, c.issueDate, c.activationDate,
                c.deactivationDate, c.replacedByCardId, c.reason, c.notes, c.updatedAt, c.isDeleted)
        }
    }

    @Serializable
    private data class GateLogRow(
        @SerialName("school_id") val schoolId: String,
        val id: String,
        @SerialName("student_id") val studentId: String?,
        @SerialName("student_number") val studentNumber: String,
        @SerialName("student_name") val studentName: String,
        @SerialName("card_id") val cardId: String?,
        @SerialName("card_identifier") val cardIdentifier: String?,
        @SerialName("qr_payload") val qrPayload: String,
        val decision: String,
        @SerialName("gate_location") val gateLocation: String,
        @SerialName("timestamp_ms") val timestamp: Long,
        @SerialName("fee_status") val feeStatus: String?,
        @SerialName("card_status") val cardStatus: String?,
        @SerialName("is_day_scholar") val isDayScholar: Boolean,
        @SerialName("is_approved") val isApproved: Boolean,
        val reason: String,
        @SerialName("is_offline_decision") val isOfflineDecision: Boolean,
        @SerialName("data_sync_timestamp_at_scan") val dataSyncTimestampAtScan: Long,
        @SerialName("guard_name") val guardName: String,
        @SerialName("device_identifier") val deviceIdentifier: String
    ) {
        companion object {
            fun from(l: ScanLogEntity, school: String) = GateLogRow(
                school, l.id, l.studentId, l.studentNumber ?: "UNKNOWN", l.studentName,
                l.cardId, l.cardIdentifier, l.qrPayload, l.decision, l.gateLocation,
                l.timestamp, l.feeStatus, l.cardStatus, l.isDayScholar, l.isApproved,
                l.reason, l.isOfflineDecision, l.dataSyncTimestampAtScan, l.guardName, l.deviceIdentifier
            )
        }
    }

    @Serializable
    private data class MealRow(
        @SerialName("school_id") val schoolId: String,
        val id: String,
        @SerialName("student_id") val studentId: String,
        @SerialName("student_number") val studentNumber: String,
        @SerialName("student_name") val studentName: String,
        @SerialName("grade_class") val gradeClass: String,
        @SerialName("meal_type") val mealType: String,
        @SerialName("meal_date") val mealDate: String,
        val status: String,
        val notes: String,
        @SerialName("serving_station") val servingStation: String,
        @SerialName("timestamp_ms") val timestamp: Long
    ) {
        fun toEntity() = MealRecordEntity(id, studentId, studentNumber, studentName, gradeClass,
            mealType, mealDate, timestamp, servingStation, status, notes, true)
        companion object {
            fun from(m: MealRecordEntity, school: String) = MealRow(
                school, m.id, m.studentId, m.studentNumber, m.studentName, m.gradeClass,
                m.mealType, m.mealDate, m.status, m.notes, m.serverName, m.timestamp
            )
        }
    }

    private companion object {
        const val LTC_SCHOOL_ID = "ltc_school_01"
        const val PHOTO_BUCKET = "student-photos"
        const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
    }
}
