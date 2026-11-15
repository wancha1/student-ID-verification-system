package com.example.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.example.model.UserRole

data class AuthenticatedStaff(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val hasExplicitFinanceAccess: Boolean
)

/**
 * Supabase staff authentication for LTC V1.
 *
 * The authenticated user's ID comes from Supabase Auth. Role and active status
 * are read only from the protected staff_memberships table; user metadata and
 * client-provided role values are never trusted.
 */
class SupabaseStaffAuthenticator(
    private val client: SupabaseClient
) {

    suspend fun signIn(email: String, password: String): AuthenticatedStaff {
        require(email.isNotBlank()) { "Enter your staff email address." }
        require(password.isNotBlank()) { "Enter your password." }

        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }

        return try {
            validateCurrentMembership()
        } catch (error: SecurityException) {
            // Invalid or inactive membership must not leave a signed-in session.
            runCatching { client.auth.signOut() }
            throw error
        }
    }

    /**
     * Waits for encrypted session restoration, then revalidates membership online.
     * A cached role is never sufficient to restore access.
     */
    suspend fun restoreSession(): AuthenticatedStaff? {
        val status = client.auth.sessionStatus.first {
            it !is SessionStatus.Initializing
        }
        if (status !is SessionStatus.Authenticated) return null

        return try {
            validateCurrentMembership()
        } catch (error: SecurityException) {
            runCatching { client.auth.signOut() }
            throw error
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    private suspend fun validateCurrentMembership(): AuthenticatedStaff {
        val user = client.auth.retrieveUserForCurrentSession()
        val membership = client
            .from("staff_memberships")
            .select {
                filter {
                    eq("school_id", LTC_SCHOOL_ID)
                    eq("user_id", user.id)
                }
            }
            .decodeSingleOrNull<StaffMembership>()
            ?: throw SecurityException(
                "This account is not provisioned as staff for Lira Town College."
            )

        if (membership.schoolId != LTC_SCHOOL_ID || membership.userId != user.id) {
            throw SecurityException("The returned staff membership does not match this authenticated account.")
        }

        if (!membership.isActive) {
            throw SecurityException("This Lira Town College staff account is inactive.")
        }

        val role = when (membership.role.trim().uppercase()) {
            "ADMIN" -> UserRole.ADMINISTRATOR
            "HEAD_TEACHER" -> UserRole.HEAD_TEACHER_MANAGEMENT
            "BURSAR" -> UserRole.BURSAR_FINANCE
            "TEACHER", "DIRECTOR_OF_STUDIES" -> UserRole.TEACHERS
            "EXAM_STAFF" -> UserRole.EXAMINATION_STAFF
            "GATE_OFFICER", "SECURITY_HEAD" -> UserRole.GATE_STAFF
            "MEAL_STAFF" -> UserRole.MEAL_SERVING_STAFF
            else -> throw SecurityException(
                "The LTC staff profile has an unsupported role."
            )
        }

        return AuthenticatedStaff(
            uid = user.id,
            email = user.email ?: membership.email.orEmpty(),
            displayName = membership.fullName.ifBlank {
                user.email ?: "LTC Staff"
            },
            role = role,
            hasExplicitFinanceAccess = false
        )
    }

    @Serializable
    private data class StaffMembership(
        @SerialName("school_id") val schoolId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("full_name") val fullName: String,
        val email: String? = null,
        val role: String,
        @SerialName("is_active") val isActive: Boolean
    )

    private companion object {
        const val LTC_SCHOOL_ID = "ltc_school_01"
    }
}
