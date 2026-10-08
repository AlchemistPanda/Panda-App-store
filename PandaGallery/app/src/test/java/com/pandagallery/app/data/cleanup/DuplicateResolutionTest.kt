package com.pandagallery.app.data.cleanup

import com.pandagallery.app.domain.model.DuplicateKeepRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateResolutionTest {
    private val base = 1_700_000_000_000L

    private fun copy(id: Long, size: Long, takenAt: Long) = CleanupCandidate(
        id = id,
        size = size,
        exactHash = "same",
        perceptualHash = 0L,
        blurScore = 100.0,
        takenAt = takenAt,
    )

    private val group = listOf(
        copy(1, size = 5_000_000, takenAt = base),
        copy(2, size = 9_000_000, takenAt = base + 1_000),
        copy(3, size = 7_000_000, takenAt = base + 2_000),
    )

    @Test
    fun `largest keeps the biggest file`() {
        assertEquals(setOf(1L, 3L), duplicatesToTrash(listOf(group), DuplicateKeepRule.LARGEST))
    }

    @Test
    fun `newest keeps the most recent copy`() {
        assertEquals(setOf(1L, 2L), duplicatesToTrash(listOf(group), DuplicateKeepRule.NEWEST))
    }

    @Test
    fun `oldest keeps the original`() {
        assertEquals(setOf(2L, 3L), duplicatesToTrash(listOf(group), DuplicateKeepRule.OLDEST))
    }

    @Test
    fun `every group keeps exactly one copy`() {
        val groups = listOf(group, group.map { it.copy(id = it.id + 100) })

        DuplicateKeepRule.entries.forEach { rule ->
            val trashed = duplicatesToTrash(groups, rule)
            assertEquals("$rule kept the wrong number", 4, trashed.size)
        }
    }

    /**
     * This runs unattended on a schedule. A tie broken differently between runs would make
     * the outcome impossible to predict or explain.
     */
    @Test
    fun `ties are broken deterministically`() {
        val identical = listOf(
            copy(7, size = 1_000, takenAt = base),
            copy(3, size = 1_000, takenAt = base),
            copy(5, size = 1_000, takenAt = base),
        )

        DuplicateKeepRule.entries.forEach { rule ->
            // Lowest id wins every tie, so id 3 is the survivor each time.
            assertEquals(setOf(5L, 7L), duplicatesToTrash(listOf(identical), rule))
        }
    }

    @Test
    fun `nothing is trashed when there are no duplicates`() {
        assertTrue(duplicatesToTrash(emptyList(), DuplicateKeepRule.LARGEST).isEmpty())
    }
}
