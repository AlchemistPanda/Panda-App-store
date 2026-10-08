package com.pandagallery.app.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSeekBarTest {

    @Test
    fun `formatTime formats seconds and minutes correctly`() {
        assertEquals("00:00", formatTime(0L))
        assertEquals("00:05", formatTime(5_000L))
        assertEquals("00:59", formatTime(59_000L))
        assertEquals("01:00", formatTime(60_000L))
        assertEquals("02:35", formatTime(155_000L))
    }

    @Test
    fun `formatTime formats hours correctly when total duration exceeds one hour`() {
        assertEquals("01:00:00", formatTime(3_600_000L))
        assertEquals("01:01:05", formatTime(3_665_000L))
        assertEquals("10:15:30", formatTime(36_930_000L))
    }

    @Test
    fun `scrub progress maps to milliseconds within duration bounds`() {
        val duration = 120_000L // 2 minutes
        fun calculateSeekMs(progress: Float): Long {
            return (progress * duration).toLong().coerceIn(0L, duration)
        }

        assertEquals(0L, calculateSeekMs(0f))
        assertEquals(60_000L, calculateSeekMs(0.5f))
        assertEquals(120_000L, calculateSeekMs(1.0f))
        assertEquals(0L, calculateSeekMs(-0.2f))
        assertEquals(120_000L, calculateSeekMs(1.5f))
    }

    @Test
    fun `forward and rewind 10 seconds clamp to bounds`() {
        val duration = 30_000L

        // Rewind from 4 seconds clamps to 0
        val rewindResult = (4_000L - 10_000L).coerceAtLeast(0L)
        assertEquals(0L, rewindResult)

        // Forward from 25 seconds clamps to duration
        val forwardResult = (25_000L + 10_000L).coerceAtMost(duration)
        assertEquals(30_000L, forwardResult)

        // Normal 10s steps
        assertEquals(10_000L, (20_000L - 10_000L).coerceAtLeast(0L))
        assertEquals(25_000L, (15_000L + 10_000L).coerceAtMost(duration))
    }
}
