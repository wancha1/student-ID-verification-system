package com.example.model

/**
 * Lifecycle status of a physical/digital student access card.
 */
enum class CardStatus(val label: String, val allowsEntry: Boolean) {
    ACTIVE("Active", true),
    LOST("Reported Lost", false),
    REPLACED("Replaced", false),
    DEACTIVATED("Deactivated", false)
}
