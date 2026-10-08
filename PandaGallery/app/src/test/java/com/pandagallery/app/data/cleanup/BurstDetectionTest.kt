package com.pandagallery.app.data.cleanup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstDetectionTest {
    private val base = 1_700_000_000_000L

    private fun shot(
        id: Long,
        offsetMillis: Long,
        hash: Long = 0b1010L,
        blur: Double = 200.0,
        size: Long = 3_000_000,
    ) = CleanupCandidate(
        id = id,
        size = size,
        exactHash = null,
        perceptualHash = hash,
        blurScore = blur,
        takenAt = base + offsetMillis,
    )

    @Test
    fun `rapid near-identical shots form one burst`() {
        val groups = burstGroups(listOf(shot(1, 0), shot(2, 400), shot(3, 900)))

        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L, 3L), groups.single().map { it.id })
    }

    @Test
    fun `a gap in time ends the burst`() {
        val groups = burstGroups(listOf(shot(1, 0), shot(2, 400), shot(3, 60_000), shot(4, 60_400)))

        assertEquals(2, groups.size)
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), groups.map { group -> group.map { it.id } })
    }

    /**
     * The distinction from plain similarity matching: two sunsets a year apart look alike
     * but are not a burst, and offering to delete one of them would be wrong.
     */
    @Test
    fun `similar photos taken far apart are not a burst`() {
        val groups = burstGroups(listOf(shot(1, 0), shot(2, 365L * 24 * 60 * 60 * 1000)))

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `visually different shots taken back to back are not a burst`() {
        val groups = burstGroups(
            listOf(shot(1, 0, hash = 0L), shot(2, 300, hash = -1L))
        )

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `a lone shot is not a burst`() {
        assertTrue(burstGroups(listOf(shot(1, 0))).isEmpty())
    }

    @Test
    fun `photos with no timestamp are skipped`() {
        val undated = shot(1, 0).copy(takenAt = 0L)
        val alsoUndated = shot(2, 300).copy(takenAt = 0L)

        assertTrue(burstGroups(listOf(undated, alsoUndated)).isEmpty())
    }

    /**
     * The key difference from duplicate handling, which keeps the largest file. Burst frames
     * are near-identical in size, so sharpness is the only signal that matters.
     */
    @Test
    fun `the sharpest frame is kept, not the largest`() {
        val groups = burstGroups(
            listOf(
                shot(1, 0, blur = 50.0, size = 9_000_000),
                shot(2, 300, blur = 400.0, size = 2_000_000),
                shot(3, 600, blur = 90.0, size = 5_000_000),
            )
        )

        assertEquals(setOf(1L, 3L), redundantBurstIds(groups))
    }

    @Test
    fun `savings count every frame except the keeper`() {
        val groups = burstGroups(
            listOf(
                shot(1, 0, blur = 400.0, size = 1_000_000),
                shot(2, 300, blur = 100.0, size = 2_000_000),
                shot(3, 600, blur = 90.0, size = 3_000_000),
            )
        )

        assertEquals(5_000_000L, estimatedBurstSavings(groups))
    }

    @Test
    fun `grouping is deterministic regardless of input order`() {
        val shots = listOf(shot(1, 0), shot(2, 400), shot(3, 60_000), shot(4, 60_400))

        assertEquals(
            burstGroups(shots).map { group -> group.map { it.id } },
            burstGroups(shots.reversed()).map { group -> group.map { it.id } },
        )
    }
}
