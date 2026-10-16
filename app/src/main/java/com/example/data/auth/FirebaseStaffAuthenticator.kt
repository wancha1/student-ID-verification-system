package com.example.data.auth

import android.content.Context
import com.example.R
import com.example.model.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * Authenticates staff with Firebase Authentication and derives all permissions
 * from the trusted school staff document. A client-supplied role is never trusted.
 */
data class AuthenticatedStaff(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val hasExplicitFinanceAccess: Boolean
)

class FirebaseStaffAuthenticator {
    suspend fun signIn(context: Context, email: String, password: String): AuthenticatedStaff {
        require(email.isNotBlank()) { "Enter your staff email address." }
        require(password.isNotBlank()) { "Enter your password." }

        val app = FirebaseApp.getInstance()
        val auth = FirebaseAuth.getInstance(app)
        val signedInUser = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Firebase Authentication returned no user.")

        try {
            val databaseId = context.getString(R.string.firestore_database_id).trim()
            // The checked-in resource currently contains the AI Studio/Oakridge database ID.
            // Refuse LTC staff sign-in until the owner supplies the confirmed LTC database ID.
            if (databaseId.isBlank() ||
                databaseId.contains("oakridge", ignoreCase = true) ||
                databaseId.startsWith("UNCONFIGURED_", ignoreCase = true)) {
                throw IllegalStateException(
                    "LTC Firebase is not configured. Set firestore_database_id to the confirmed LTC Firestore database ID before staff sign-in."
                )
            }
            val db = FirebaseFirestore.getInstance(app, databaseId)
            val staffSnapshot = db.collection("schools")
                .document(SCHOOL_ID)
                .collection("staff")
                .document(signedInUser.uid)
                .get(Source.SERVER)
                .await()

            if (!staffSnapshot.exists()) {
                throw SecurityException("This Firebase account is not provisioned as LTC staff.")
            }
            if (staffSnapshot.getBoolean("active") != true) {
                throw SecurityException("This LTC staff account is inactive.")
            }

            val rawRole = staffSnapshot.getString("role")
                ?.trim()
                ?.uppercase()
                ?: throw SecurityException("The LTC staff profile has no role.")
            val role = mapRole(rawRole)
                ?: throw SecurityException("The LTC staff profile has an unsupported role: $rawRole.")

            val name = staffSnapshot.getString("name")
                ?: staffSnapshot.getString("displayName")
                ?: signedInUser.displayName
                ?: signedInUser.email
                ?: signedInUser.uid

            return AuthenticatedStaff(
                uid = signedInUser.uid,
                email = signedInUser.email ?: email.trim(),
                displayName = name,
                role = role,
                hasExplicitFinanceAccess = role == UserRole.BURSAR_FINANCE ||
                    (role in setOf(UserRole.ADMINISTRATOR, UserRole.HEAD_TEACHER_MANAGEMENT) &&
                        staffSnapshot.getBoolean("hasExplicitFinanceAccess") == true)
            )
        } catch (error: Exception) {
            // Never leave an authenticated but unprovisioned account active in the client.
            auth.signOut()
            throw error
        }
    }

    private fun mapRole(role: String): UserRole? = when (role) {
        "ADMIN" -> UserRole.ADMINISTRATOR
        "HEAD_TEACHER" -> UserRole.HEAD_TEACHER_MANAGEMENT
        "BURSAR" -> UserRole.BURSAR_FINANCE
        "GATE_OFFICER", "SECURITY_HEAD" -> UserRole.GATE_STAFF
        "MATRON_PATRON", "MEAL_STAFF" -> UserRole.MEAL_SERVING_STAFF
        "DIRECTOR_OF_STUDIES", "TEACHER" -> UserRole.TEACHERS
        "EXAM_STAFF" -> UserRole.EXAMINATION_STAFF
        else -> null
    }

    companion object {
        const val SCHOOL_ID = "ltc_school_01"
    }
}
