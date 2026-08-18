package com.example.model

enum class UserRole(
    val title: String,
    val subtitle: String,
    val defaultUsername: String,
    val badge: String
) {
    SECURITY_GUARD(
        title = "Security Guard",
        subtitle = "Gate 1 - Main Campus Access",
        defaultUsername = "Officer Daniel Miller",
        badge = "GATE ACCESS"
    ),
    ADMINISTRATOR(
        title = "Administrator",
        subtitle = "Bursar & Student Affairs Office",
        defaultUsername = "Admin Margaret Evans",
        badge = "FULL ACCESS"
    )
}

data class AuthUser(
    val role: UserRole,
    val name: String,
    val station: String
)
