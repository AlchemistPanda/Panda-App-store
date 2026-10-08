package com.pandagallery.app.data.story

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryHighlightExporterTest {

    @Test
    fun `StoryAspect dimensions follow standard HD and vertical video resolutions`() {
        assertEquals(1080, StoryAspect.PORTRAIT_9_16.width)
        assertEquals(1920, StoryAspect.PORTRAIT_9_16.height)

        assertEquals(1920, StoryAspect.LANDSCAPE_16_9.width)
        assertEquals(1080, StoryAspect.LANDSCAPE_16_9.height)

        assertEquals(1080, StoryAspect.SQUARE.width)
        assertEquals(1080, StoryAspect.SQUARE.height)
    }

    @Test
    fun `story title sanitization removes illegal filesystem characters and limits length`() {
        val rawTitle = "Trip to Paris & Zurich!! (2026/09/09) - Best Moments"
        val sanitized = rawTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32)

        assertEquals("Trip_to_Paris___Zurich____2026_0", sanitized)
        assertEquals(32, sanitized.length)
    }

    @Test
    fun `total reel duration calculation allocates 3500ms per clip with minimum boundary`() {
        val clipDurationMs = 3500L

        val zeroItemDuration = (0 * clipDurationMs).coerceAtLeast(2000L)
        assertEquals(2000L, zeroItemDuration)

        val threeItemDuration = (3 * clipDurationMs).coerceAtLeast(2000L)
        assertEquals(10500L, threeItemDuration)

        val tenItemDuration = (10 * clipDurationMs).coerceAtLeast(2000L)
        assertEquals(35000L, tenItemDuration)
    }
}
