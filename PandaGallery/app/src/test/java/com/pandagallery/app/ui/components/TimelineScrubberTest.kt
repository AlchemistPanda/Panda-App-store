package com.pandagallery.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineScrubberTest {

    @Test
    fun `isPointInsideThumb returns true when touch lands on thumb`() {
        val thumbTop = 200f
        val thumbHeight = 100f
        val tolerance = 40f

        // Exact top and bottom boundaries
        assertTrue(isPointInsideThumb(touchY = 200f, thumbTopY = thumbTop, thumbHeightPx = thumbHeight, verticalTolerancePx = tolerance))
        assertTrue(isPointInsideThumb(touchY = 250f, thumbTopY = thumbTop, thumbHeightPx = thumbHeight, verticalTolerancePx = tolerance))
        assertTrue(isPointInsideThumb(touchY = 300f, thumbTopY = thumbTop, thumbHeightPx = thumbHeight, verticalTolerancePx = tolerance))

        // Within tolerance padding
        assertTrue(isPointInsideThumb(touchY = 165f, thumbTopY = thumbTop, thumbHeightPx = thumbHeight, verticalTolerancePx = tolerance))
        assertTrue(isPointInsideThumb(touchY = 335f, thumbTopY = thumbTop, thumbHeightPx = thumbHeight, verticalTolerancePx = tolerance))
    }

    @Test
    fun `isPointInsideThumb returns false when touch is outside thumb`() {
        val thumbTop = 500f
        val thumbHeight = 100f
        val tolerance = 40f

        // Far above (e.g. user touching right edge near top)
        assertFalse(isPointInsideThumb(touchY = 100f, thumbTopY = thumbTop, thumbHeightPx = thumbHeight, verticalTolerancePx = tolerance))
        assertFalse(isPointInsideThumb(touchY = 450f, thumbTopY = thumbTop, thumbHeightPx = thumbHeight, verticalTolerancePx = tolerance))

        // Far below (e.g. user touching right edge near bottom)
        assertFalse(isPointInsideThumb(touchY = 650f, thumbTopY = thumbTop, thumbHeightPx = thumbHeight, verticalTolerancePx = tolerance))
        assertFalse(isPointInsideThumb(touchY = 1200f, thumbTopY = thumbTop, thumbHeightPx = thumbHeight, verticalTolerancePx = tolerance))
    }

    @Test
    fun `calculateScrubberThumbY anchors to grab offset without jumping`() {
        val maxOffset = 1000f
        val currentThumbY = 400f
        val touchY = 425f
        val grabOffsetY = touchY - currentThumbY // 25f

        // Initial position before moving finger
        val initialY = calculateScrubberThumbY(currentPointerY = touchY, grabOffsetY = grabOffsetY, maxOffsetPx = maxOffset)
        assertEquals(400f, initialY, 0.001f)

        // Dragging down by 50px
        val movedDownY = calculateScrubberThumbY(currentPointerY = touchY + 50f, grabOffsetY = grabOffsetY, maxOffsetPx = maxOffset)
        assertEquals(450f, movedDownY, 0.001f)

        // Dragging up by 100px
        val movedUpY = calculateScrubberThumbY(currentPointerY = touchY - 100f, grabOffsetY = grabOffsetY, maxOffsetPx = maxOffset)
        assertEquals(300f, movedUpY, 0.001f)

        // Clamping to top and bottom bounds
        val overTopY = calculateScrubberThumbY(currentPointerY = -50f, grabOffsetY = grabOffsetY, maxOffsetPx = maxOffset)
        assertEquals(0f, overTopY, 0.001f)

        val overBottomY = calculateScrubberThumbY(currentPointerY = 2000f, grabOffsetY = grabOffsetY, maxOffsetPx = maxOffset)
        assertEquals(1000f, overBottomY, 0.001f)
    }

    @Test
    fun `formatScrubberBubbleDate formats Today and Yesterday cleanly`() {
        assertEquals("Today", formatScrubberBubbleDate(headerLabel = "Today", dateKey = "2024-09-02"))
        assertEquals("Yesterday", formatScrubberBubbleDate(headerLabel = "Yesterday", dateKey = "2024-09-01"))
    }

    @Test
    fun `formatScrubberBubbleDate formats month and year from date key`() {
        val formatted = formatScrubberBubbleDate(headerLabel = "August 15, 2024", dateKey = "2024-08-15")
        assertTrue(formatted.contains("2024"))
        assertTrue(formatted.contains("August") || formatted.contains("Aug"))
    }
}
