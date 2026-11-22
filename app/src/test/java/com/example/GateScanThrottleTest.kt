package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GateScanThrottleTest {
    @Test
    fun rejectsOverlappingFrameCallbacks() {
        var now = 10_000L
        val throttle = GateScanThrottle(nowMs = { now })

        assertTrue(throttle.tryBegin("badge-A"))
        assertFalse(throttle.tryBegin("badge-A"))
        assertFalse(throttle.tryBegin("badge-B"))

        throttle.finish()
        assertTrue(throttle.tryBegin("badge-B"))
    }

    @Test
    fun suppressesSameBadgeDuringCooldown() {
        var now = 10_000L
        val throttle = GateScanThrottle(cooldownMs = 2_000L, nowMs = { now })

        assertTrue(throttle.tryBegin("badge-A"))
        throttle.finish()

        now += 1_999L
        assertFalse(throttle.tryBegin("badge-A"))

        now += 1L
        assertTrue(throttle.tryBegin("badge-A"))
    }

    @Test
    fun allowsSameBadgeAfterCooldownAndDifferentBadgeImmediately() {
        var now = 10_000L
        val throttle = GateScanThrottle(cooldownMs = 2_000L, nowMs = { now })

        assertTrue(throttle.tryBegin("badge-A"))
        throttle.finish()
        assertTrue(throttle.tryBegin("badge-B"))
        throttle.finish()

        now += 2_000L
        assertTrue(throttle.tryBegin("badge-A"))
    }
}
