package com.pandagallery.app.ui.photos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemGridVisibilityTest {

    @Test
    fun `isMediaItemVisible returns true when item key matches a visible item`() {
        val visibleKeys = listOf("media_1001", "media_1002", "media_1003")
        val visibleIndices = listOf(5, 6, 7)

        val isVisible = isMediaItemVisible(
            visibleKeys = visibleKeys,
            visibleIndices = visibleIndices,
            mediaId = 1002L,
            targetIndex = 6,
        )

        assertTrue(isVisible)
    }

    @Test
    fun `isMediaItemVisible returns true when item index matches even if key was not matching`() {
        val visibleKeys = listOf("media_2001", "media_2002")
        val visibleIndices = listOf(10, 11)

        val isVisible = isMediaItemVisible(
            visibleKeys = visibleKeys,
            visibleIndices = visibleIndices,
            mediaId = 9999L,
            targetIndex = 11,
        )

        assertTrue(isVisible)
    }

    @Test
    fun `isMediaItemVisible returns false when target item is not in visible items`() {
        val visibleKeys = listOf("media_10", "media_11", "media_12")
        val visibleIndices = listOf(0, 1, 2)

        val isVisible = isMediaItemVisible(
            visibleKeys = visibleKeys,
            visibleIndices = visibleIndices,
            mediaId = 500L,
            targetIndex = 50,
        )

        assertFalse(isVisible)
    }

    @Test
    fun `isMediaItemVisible returns false when visible items list is empty`() {
        val isVisible = isMediaItemVisible(
            visibleKeys = emptyList(),
            visibleIndices = emptyList(),
            mediaId = 100L,
            targetIndex = 0,
        )

        assertFalse(isVisible)
    }
}
