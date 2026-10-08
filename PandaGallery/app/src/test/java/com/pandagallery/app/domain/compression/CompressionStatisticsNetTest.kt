package com.pandagallery.app.domain.compression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CompressionStatistics.savedBytes] is a sum of per-task savings that were each floored at
 * zero, so a job whose output came out larger than its source contributes nothing rather than a
 * loss. On the Compression Queue screen that produced "Space saved overall: 0 B" directly above
 * "Original: 293.6 MB / Now: 373.0 MB" — a figure and a bar that contradict each other.
 */
class CompressionStatisticsNetTest {

    @Test
    fun `net saving is positive for a batch that shrank`() {
        val stats = CompressionStatistics(
            totalOperations = 1,
            completedItems = 2,
            originalBytes = 10_000,
            outputBytes = 4_000,
            savedBytes = 6_000,
        )
        assertEquals(6_000L, stats.netSavedBytes)
        assertEquals(60, stats.savedPercent)
    }

    @Test
    fun `net saving is negative when the copies cost more than the originals`() {
        val stats = CompressionStatistics(
            totalOperations = 3,
            completedItems = 3,
            originalBytes = 293_600_000,
            outputBytes = 373_000_000,
            // Every task was floored at zero, so this hides the loss entirely.
            savedBytes = 0,
        )
        assertEquals(0L, stats.savedBytes)
        assertTrue("the loss has to be visible somewhere", stats.netSavedBytes < 0L)
        assertEquals(-79_400_000L, stats.netSavedBytes)
    }

    @Test
    fun `empty history reports no change rather than a loss`() {
        assertEquals(0L, CompressionStatistics().netSavedBytes)
        assertEquals(0, CompressionStatistics().savedPercent)
    }
}
