package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveDateTest {
    @Test
    fun `a real capture date wins`() {
        assertEquals(1_700_000_000_000L, effectiveDateMillis(1_700_000_000_000L, 1_600_000_000L))
    }

    @Test
    fun `a missing capture date falls back to when the file arrived`() {
        assertEquals(1_600_000_000_000L, effectiveDateMillis(null, 1_600_000_000L))
    }

    /**
     * The bug this guards: MediaStore reports `dateTaken = 0` rather than null for screenshots,
     * downloads and received media on many devices. Treating 0 as a real date stamped them 1970,
     * which buried today's screenshot at the bottom of the timeline and stopped it from ever
     * being picked as its album's cover.
     */
    @Test
    fun `a zero capture date counts as missing, not as 1970`() {
        assertEquals(1_600_000_000_000L, effectiveDateMillis(0L, 1_600_000_000L))
        assertEquals(1_600_000_000_000L, effectiveDateMillis(-1L, 1_600_000_000L))
    }

    /** `dateAdded` is in seconds and everything downstream compares milliseconds. */
    @Test
    fun `the fallback is converted to milliseconds`() {
        assertEquals(5_000L, effectiveDateMillis(null, 5L))
    }
}
