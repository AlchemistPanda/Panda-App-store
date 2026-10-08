package com.pandagallery.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaContentFilterTest {
    @Test
    fun `content filters separate images videos gifs and large files`() {
        assertTrue(matchesMediaContentFilter("image/jpeg", 1L, MediaContentFilter.IMAGES))
        assertFalse(matchesMediaContentFilter("image/gif", 1L, MediaContentFilter.IMAGES))
        assertTrue(matchesMediaContentFilter("video/mp4", 1L, MediaContentFilter.VIDEOS))
        assertTrue(matchesMediaContentFilter("image/gif", 1L, MediaContentFilter.GIFS))
        assertTrue(
            matchesMediaContentFilter(
                "image/png",
                60L * 1024L * 1024L,
                MediaContentFilter.LARGE_FILES,
            ),
        )
    }
}
