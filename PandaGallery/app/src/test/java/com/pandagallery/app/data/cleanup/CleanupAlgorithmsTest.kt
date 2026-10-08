package com.pandagallery.app.data.cleanup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupAlgorithmsTest {
    @Test fun `exact duplicate groups retain only groups with multiple items`() {
        val candidates = listOf(
            candidate(1, "a"), candidate(2, "a"), candidate(3, "b"), candidate(4, null),
        )
        assertEquals(listOf(listOf(1L, 2L)), exactDuplicateGroups(candidates).map { group -> group.map { it.id } })
    }

    @Test fun `similar groups use hamming distance and do not duplicate membership`() {
        val candidates = listOf(
            candidate(1, perceptual = 0b0000),
            candidate(2, perceptual = 0b0011),
            candidate(3, perceptual = 0b1111),
        )
        val groups = similarMediaGroups(candidates, maximumDistance = 2)
        assertEquals(listOf(listOf(1L, 2L)), groups.map { it.map { item -> item.id } })
    }

    @Test fun `estimated duplicate savings keeps one file per group`() {
        val group = listOf(candidate(1, "a", size = 100), candidate(2, "a", size = 100), candidate(3, "a", size = 100))
        assertTrue(estimatedDuplicateSavings(listOf(group)) == 200L)
    }

    @Test fun `duplicates to trash with LARGEST keeps the largest copy and trashes others`() {
        val group = listOf(
            candidate(id = 1, exact = "hash1", size = 2_000L),
            candidate(id = 2, exact = "hash1", size = 5_000L),
            candidate(id = 3, exact = "hash1", size = 1_000L),
        )
        val doomed = duplicatesToTrash(listOf(group), com.pandagallery.app.domain.model.DuplicateKeepRule.LARGEST)
        assertEquals(setOf(1L, 3L), doomed)
    }

    @Test fun `duplicates to trash with NEWEST keeps the newest taken copy and trashes older copies`() {
        val group = listOf(
            candidate(id = 10, exact = "hash2", takenAt = 100L),
            candidate(id = 20, exact = "hash2", takenAt = 500L),
            candidate(id = 30, exact = "hash2", takenAt = 300L),
        )
        val doomed = duplicatesToTrash(listOf(group), com.pandagallery.app.domain.model.DuplicateKeepRule.NEWEST)
        assertEquals(setOf(10L, 30L), doomed)
    }

    @Test fun `duplicates to trash with OLDEST keeps the oldest taken copy and trashes newer copies`() {
        val group = listOf(
            candidate(id = 10, exact = "hash3", takenAt = 100L),
            candidate(id = 20, exact = "hash3", takenAt = 500L),
            candidate(id = 30, exact = "hash3", takenAt = 300L),
        )
        val doomed = duplicatesToTrash(listOf(group), com.pandagallery.app.domain.model.DuplicateKeepRule.OLDEST)
        assertEquals(setOf(20L, 30L), doomed)
    }

    private fun candidate(
        id: Long,
        exact: String? = null,
        perceptual: Long? = null,
        size: Long = 100,
        takenAt: Long = 0L,
    ) = CleanupCandidate(id, size, exact, perceptual, blurScore = null, takenAt = takenAt)
}
