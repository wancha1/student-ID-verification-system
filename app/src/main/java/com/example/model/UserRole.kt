package com.example.model

enum class UserRole(
    val title: String,
    val subtitle: String,
    val defaultUsername: String,
    val badge: String
) {
    GATE_KEEPER(
        title = "Gate Keeper",
        subtitle = "Gate 1 - Main Campus Access & Security",
        defaultUsername = "Officer Daniel Miller",
        badge = "GATE ACCESS"
    ),
    REQUIREMENTS_MASTER(
        title = "Requirements Master",
        subtitle = "Student Supplies, Uniform & Clearance Office",
        defaultUsername = "Master Samuel Kigozi",
        badge = "REQUIREMENTS"
    ),
    ADMINISTRATOR(
        title = "Admin",
        subtitle = "Bursar & Principal's Administration Office",
        defaultUsername = "Admin Margaret Evans",
        badge = "ADMIN PORTAL"
    ),
    MEALS_MASTER(
        title = "Meals Master",
        subtitle = "Main Dining Hall & Cafeteria Access",
        defaultUsername = "Chef Jackson Omondi",
        badge = "MEALS TERMINAL"
    );

    companion object {
        // Aliases for backward compatibility
        val SECURITY_GUARD get() = GATE_KEEPER
        val ADMIN get() = ADMINISTRATOR
    }
}

data class AuthUser(
    val role: UserRole,
    val name: String,
    val station: String
)
