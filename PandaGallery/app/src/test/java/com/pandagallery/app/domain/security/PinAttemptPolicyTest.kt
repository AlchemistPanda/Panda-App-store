package com.pandagallery.app.domain.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinAttemptPolicyTest {
    @Test
    fun `early mistakes cost nothing`() {
        for (attempts in 0 until PIN_ATTEMPTS_BEFORE_BACKOFF) {
            assertEquals("attempt $attempts", 0L, pinLockoutMillis(attempts))
        }
    }

    @Test
    fun `waits grow with repeated wrong guesses`() {
        assertEquals(30_000L, pinLockoutMillis(5))
        assertEquals(30_000L, pinLockoutMillis(7))
        assertEquals(2 * 60_000L, pinLockoutMillis(8))
        assertEquals(10 * 60_000L, pinLockoutMillis(11))
        assertEquals(30 * 60_000L, pinLockoutMillis(14))
        assertEquals(30 * 60_000L, pinLockoutMillis(500))
    }

    @Test
    fun `the wait counts down and then clears`() {
        val lastFailure = 1_000_000L
        assertEquals(30_000L, pinLockoutRemainingMillis(5, lastFailure, lastFailure))
        assertEquals(20_000L, pinLockoutRemainingMillis(5, lastFailure, lastFailure + 10_000L))
        assertEquals(0L, pinLockoutRemainingMillis(5, lastFailure, lastFailure + 30_000L))
        assertEquals(0L, pinLockoutRemainingMillis(5, lastFailure, lastFailure + 60_000L))
    }

    /**
     * Winding the clock back — a timezone change, or someone doing it deliberately — must not
     * shorten a lockout, since the whole point is that guesses cost real time.
     */
    @Test
    fun `a clock moved backwards does not shorten the wait`() {
        val lastFailure = 1_000_000L
        assertEquals(30_000L, pinLockoutRemainingMillis(5, lastFailure, lastFailure - 60_000L))
    }

    @Test
    fun `no failures means no wait`() {
        assertEquals(0L, pinLockoutRemainingMillis(0, 0L, 5_000L))
        assertEquals(0L, pinLockoutRemainingMillis(5, 0L, 5_000L))
    }

    @Test
    fun `the user can be warned before the next wait`() {
        assertEquals(5, pinAttemptsRemainingBeforeLockout(0))
        assertEquals(1, pinAttemptsRemainingBeforeLockout(4))
        assertEquals(3, pinAttemptsRemainingBeforeLockout(5))
        assertEquals(0, pinAttemptsRemainingBeforeLockout(14))
    }

    @Test
    fun `waits read as plain english`() {
        assertEquals("1 second", formatPinLockout(1))
        assertEquals("30 seconds", formatPinLockout(30_000))
        assertEquals("1 minute", formatPinLockout(60_000))
        assertEquals("2 minutes", formatPinLockout(2 * 60_000))
        assertEquals("10 minutes", formatPinLockout(10 * 60_000))
    }
}
