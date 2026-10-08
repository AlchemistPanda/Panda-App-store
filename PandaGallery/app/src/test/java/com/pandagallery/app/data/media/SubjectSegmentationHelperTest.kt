package com.pandagallery.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectSegmentationHelperTest {

    data class NormalizedBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    data class ScreenRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * Projects a normalized cutout bounding box (0..1) onto the image's displayed screen bounds.
     */
    private fun projectCutoutToScreen(
        bounds: NormalizedBounds,
        screenRect: ScreenRect,
    ): ScreenRect {
        val cutoutLeft = screenRect.left + bounds.left * screenRect.width
        val cutoutTop = screenRect.top + bounds.top * screenRect.height
        val cutoutWidth = bounds.width * screenRect.width
        val cutoutHeight = bounds.height * screenRect.height
        return ScreenRect(
            left = cutoutLeft,
            top = cutoutTop,
            right = cutoutLeft + cutoutWidth,
            bottom = cutoutTop + cutoutHeight,
        )
    }

    /**
     * Converts a screen touch coordinate into normalized [0..1] image fractions.
     */
    private fun screenTouchToNormalizedFraction(
        touchX: Float,
        touchY: Float,
        screenRect: ScreenRect,
    ): Pair<Float, Float>? {
        if (touchX < screenRect.left || touchX > screenRect.right ||
            touchY < screenRect.top || touchY > screenRect.bottom
        ) {
            return null
        }
        val xFraction = ((touchX - screenRect.left) / screenRect.width).coerceIn(0f, 1f)
        val yFraction = ((touchY - screenRect.top) / screenRect.height).coerceIn(0f, 1f)
        return Pair(xFraction, yFraction)
    }

    @Test
    fun projectCutoutToScreen_mapsSubjectAccurately_inCenteredFitBounds() {
        // Screen bounds where an image is centered letterboxed
        val screenRect = ScreenRect(left = 0f, top = 200f, right = 1080f, bottom = 1640f)
        assertEquals(1080f, screenRect.width, 0.001f)
        assertEquals(1440f, screenRect.height, 0.001f)

        // Subject located centrally in the photo: 25%..75% X, 20%..80% Y
        val bounds = NormalizedBounds(left = 0.25f, top = 0.20f, right = 0.75f, bottom = 0.80f)
        val projected = projectCutoutToScreen(bounds, screenRect)

        assertEquals(270f, projected.left, 0.01f)
        assertEquals(488f, projected.top, 0.01f)
        assertEquals(540f, projected.width, 0.01f)
        assertEquals(864f, projected.height, 0.01f)
    }

    @Test
    fun screenTouchToNormalizedFraction_computesPrecisePoint_whenInsideImage() {
        val screenRect = ScreenRect(left = 100f, top = 200f, right = 900f, bottom = 1400f)
        // Midpoint touch
        val midTouch = screenTouchToNormalizedFraction(500f, 800f, screenRect)
        assertNotNull(midTouch)
        assertEquals(0.5f, midTouch!!.first, 0.001f)
        assertEquals(0.5f, midTouch.second, 0.001f)

        // Top-left touch
        val tlTouch = screenTouchToNormalizedFraction(100f, 200f, screenRect)
        assertNotNull(tlTouch)
        assertEquals(0f, tlTouch!!.first, 0.001f)
        assertEquals(0f, tlTouch.second, 0.001f)

        // Bottom-right touch
        val brTouch = screenTouchToNormalizedFraction(900f, 1400f, screenRect)
        assertNotNull(brTouch)
        assertEquals(1f, brTouch!!.first, 0.001f)
        assertEquals(1f, brTouch.second, 0.001f)
    }

    @Test
    fun screenTouchToNormalizedFraction_returnsNull_whenTouchIsOnLetterboxBars() {
        val screenRect = ScreenRect(left = 100f, top = 200f, right = 900f, bottom = 1400f)

        // Tapping in black margin above image
        val topMarginTouch = screenTouchToNormalizedFraction(500f, 50f, screenRect)
        assertNull(topMarginTouch)

        // Tapping in black margin to the right of image
        val rightMarginTouch = screenTouchToNormalizedFraction(950f, 800f, screenRect)
        assertNull(rightMarginTouch)
    }

    private fun assertNotNull(actual: Any?) {
        assertTrue(actual != null)
    }

    private fun assertNull(actual: Any?) {
        assertTrue(actual == null)
    }
}
