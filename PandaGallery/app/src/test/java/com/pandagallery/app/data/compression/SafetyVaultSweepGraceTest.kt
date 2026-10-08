package com.pandagallery.app.data.compression

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyVaultSweepGraceTest {
    private val now = 1_000_000_000_000L

    @Test
    fun `a file written moments ago is kept even though no row names it yet`() {
        // A backup renamed into place after the sweep took its referenced snapshot keeps the
        // write time of its last byte, which is recent, so the sweep must leave it alone.
        assertFalse(isPastSweepGrace(lastModifiedMs = now - 2_000L, nowMs = now))
    }

    @Test
    fun `a file exactly at the grace window is swept`() {
        assertTrue(isPastSweepGrace(lastModifiedMs = now - VAULT_SWEEP_GRACE_MS, nowMs = now))
    }

    @Test
    fun `a file one millisecond inside the grace window is kept`() {
        assertFalse(isPastSweepGrace(lastModifiedMs = now - VAULT_SWEEP_GRACE_MS + 1, nowMs = now))
    }

    @Test
    fun `a file untouched for days is swept`() {
        assertTrue(isPastSweepGrace(lastModifiedMs = now - 3L * 24 * 60 * 60 * 1000, nowMs = now))
    }

    @Test
    fun `a file stamped in the future by clock skew is kept`() {
        assertFalse(isPastSweepGrace(lastModifiedMs = now + 60_000L, nowMs = now))
    }
}
