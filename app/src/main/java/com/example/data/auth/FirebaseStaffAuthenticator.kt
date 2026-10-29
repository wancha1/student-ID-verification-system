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
    private class InvalidFirebaseConfigurationException(message: String) : IllegalStateException(message)

    suspend fun signIn(context: Context, email: String, password: String): AuthenticatedStaff {
        require(email.isNotBlank()) { "Enter your staff email address." }
        require(password.isNotBlank()) { "Enter your password." }

        val app = FirebaseApp.getInstance()
        val auth = FirebaseAuth.getInstance(app)
        val signedInUser = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Firebase Authentication returned no user.")

        try {
            val databaseId = context.getString(R.string.firestore_database_id).trim()
            // The school owner confirmed this exact Firestore database for LTC.
            // Fail closed if local configuration drifts to any other database.
            if (databaseId != AUTHORIZED_FIRESTORE_DATABASE_ID) {
                throw InvalidFirebaseConfigurationException(
                    "LTC Firebase database configuration does not match the authorized school database."
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
        } catch (error: SecurityException) {
            // Explicitly invalid membership must end this local Firebase session.
            auth.signOut()
            throw error
        } catch (error: InvalidFirebaseConfigurationException) {
            // A known database configuration mismatch is not recoverable by retrying this session.
            auth.signOut()
            throw error
        } catch (error: Exception) {
            // Firestore/network failures are not proof that the account is unprovisioned.
            // Keep this device's Firebase session so the user can retry server validation.
            // No app role is granted unless the trusted staff document validates.
            throw error
        }
    }

    /**
     * Restores a Firebase-persisted login only after revalidating the staff profile
     * against the server. A cached profile is never sufficient to restore privileges.
     * Network errors propagate so the caller can keep the login screen visible.
     */
    suspend fun restoreSession(context: Context): AuthenticatedStaff? {
        val app = FirebaseApp.getInstance()
        val auth = FirebaseAuth.getInstance(app)
        val signedInUser = auth.currentUser ?: return null

        val databaseId = context.getString(R.string.firestore_database_id).trim()
        if (databaseId != AUTHORIZED_FIRESTORE_DATABASE_ID) {
            auth.signOut()
            throw IllegalStateException(
                "LTC Firebase database configuration does not match the authorized school database."
            )
        }

        val staffSnapshot = FirebaseFirestore.getInstance(app, databaseId)
            .collection("schools")
            .document(SCHOOL_ID)
            .collection("staff")
            .document(signedInUser.uid)
            .get(Source.SERVER)
            .await()

        if (!staffSnapshot.exists() || staffSnapshot.getBoolean("active") != true) {
            auth.signOut()
            throw SecurityException("This Firebase account is not an active LTC staff account.")
        }

        val rawRole = staffSnapshot.getString("role")
            ?.trim()
            ?.uppercase()
            ?: run {
                auth.signOut()
                throw SecurityException("The LTC staff profile has no role.")
            }
        val role = mapRole(rawRole) ?: run {
            auth.signOut()
            throw SecurityException("The LTC staff profile has an unsupported role: $rawRole.")
        }
        val name = staffSnapshot.getString("name")
            ?: staffSnapshot.getString("displayName")
            ?: signedInUser.displayName
            ?: signedInUser.email
            ?: signedInUser.uid

        return AuthenticatedStaff(
            uid = signedInUser.uid,
            email = signedInUser.email.orEmpty(),
            displayName = name,
            role = role,
            hasExplicitFinanceAccess = role == UserRole.BURSAR_FINANCE ||
                (role in setOf(UserRole.ADMINISTRATOR, UserRole.HEAD_TEACHER_MANAGEMENT) &&
                    staffSnapshot.getBoolean("hasExplicitFinanceAccess") == true)
        )
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
        const val AUTHORIZED_FIRESTORE_DATABASE_ID =
            "ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba"
    }
}
