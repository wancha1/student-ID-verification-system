package com.example.ui

/**
 * Prevents CameraX frame callbacks from starting overlapping verifications and
 * suppresses repeat reads of the same badge for a short cooldown.
 *
 * The clock is injectable so the behaviour can be tested without sleeping.
 */
internal class GateScanThrottle(
    private val cooldownMs: Long = 2_000L,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private var inFlight = false
    private var lastCode: String? = null
    private var lastAcceptedAtMs = Long.MIN_VALUE

    @Synchronized
    fun tryBegin(rawCode: String): Boolean {
        val now = nowMs()
        if (inFlight) return false
        if (rawCode == lastCode && lastAcceptedAtMs != Long.MIN_VALUE &&
            now - lastAcceptedAtMs < cooldownMs
        ) return false

        inFlight = true
        lastCode = rawCode
        lastAcceptedAtMs = now
        return true
    }

    @Synchronized
    fun finish() {
        inFlight = false
    }
}
