package com.example.model

/**
 * Access authorization status stored in Room database for offline gate verification.
 */
enum class AccessStatus(
    val label: String,
    val isApproved: Boolean,
    val reasonCode: String
) {
    APPROVED(
        label = "Access Approved",
        isApproved = true,
        reasonCode = "CLEARED"
    ),
    RESTRICTED_FEES(
        label = "Restricted: Outstanding Fees",
        isApproved = false,
        reasonCode = "FEES_OUTSTANDING"
    ),
    RESTRICTED_BOARDER(
        label = "Restricted: Boarder at Day Gate",
        isApproved = false,
        reasonCode = "BOARDER_RESTRICTION"
    ),
    SUSPENDED(
        label = "Access Suspended",
        isApproved = false,
        reasonCode = "ADMIN_SUSPENSION"
    ),
    INACTIVE(
        label = "Inactive / Withdrawn",
        isApproved = false,
        reasonCode = "INACTIVE_STATUS"
    );

    companion object {
        fun evaluate(isDayScholar: Boolean, feeStatus: FeeStatus): AccessStatus {
            return when {
                !isDayScholar -> RESTRICTED_BOARDER
                feeStatus == FeeStatus.OUTSTANDING -> RESTRICTED_FEES
                else -> APPROVED
            }
        }

        fun fromString(value: String?): AccessStatus {
            if (value.isNullOrBlank()) return APPROVED
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: if (value.equals("CLEARED", ignoreCase = true)) APPROVED else RESTRICTED_FEES
        }
    }
}
