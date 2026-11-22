package com.example.model

/**
 * Access authorization status stored in Room database for offline gate verification.
 */
enum class AccessStatus(
    val label: String,
    val isApproved: Boolean,
    val reasonCode: String
) {
    PENDING_POLICY(
        label = "Policy Pending",
        isApproved = false,
        reasonCode = "POLICY_PENDING"
    ),
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
        /**
         * Legacy signature retained for source compatibility. Entry policy is not inferred
         * from fee or boarding fields until the school formally approves that policy.
         */
        fun evaluate(isDayScholar: Boolean, feeStatus: FeeStatus): AccessStatus = PENDING_POLICY

        fun fromString(value: String?): AccessStatus {
            if (value.isNullOrBlank()) return PENDING_POLICY
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: if (value.equals("CLEARED", ignoreCase = true)) APPROVED else PENDING_POLICY
        }
    }
}
