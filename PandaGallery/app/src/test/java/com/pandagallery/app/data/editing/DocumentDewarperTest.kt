package com.pandagallery.app.data.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class DocumentDewarperTest {

    @Test
    fun `calculateTargetDimensions computes maximum opposing edge lengths`() {
        // Skewed document on a 1000x1500 canvas
        // Top edge: (100, 100) to (900, 150) -> width ~ 801.56
        // Bottom edge: (80, 1400) to (950, 1380) -> width ~ 870.23
        // Left edge: (100, 100) to (80, 1400) -> height ~ 1300.15
        // Right edge: (900, 150) to (950, 1380) -> height ~ 1231.02
        val corners = DocumentCorners(
            topLeft = NormalizedPoint(0.10f, 0.0667f),
            topRight = NormalizedPoint(0.90f, 0.10f),
            bottomRight = NormalizedPoint(0.95f, 0.92f),
            bottomLeft = NormalizedPoint(0.08f, 0.9333f),
        )

        val (targetW, targetH) = DocumentDewarper.calculateTargetDimensions(
            corners = corners,
            sourceWidth = 1000,
            sourceHeight = 1500,
        )

        // Target width should be bottom width ~ 870px
        assertTrue("Width should be max of top and bottom edge (~870)", targetW in 860..880)
        // Target height should be left height ~ 1300px
        assertTrue("Height should be max of left and right edge (~1300)", targetH in 1290..1310)
    }

    @Test
    fun `computePolyPoints outputs matching 8-point source and destination arrays`() {
        val corners = DocumentCorners(
            topLeft = NormalizedPoint(0.1f, 0.2f),
            topRight = NormalizedPoint(0.8f, 0.2f),
            bottomRight = NormalizedPoint(0.8f, 0.9f),
            bottomLeft = NormalizedPoint(0.1f, 0.9f),
        )

        val (src, dst) = DocumentDewarper.computePolyPoints(
            corners = corners,
            sourceWidth = 1000,
            sourceHeight = 2000,
            targetWidth = 700,
            targetHeight = 1400,
        )

        assertEquals(8, src.size)
        assertEquals(8, dst.size)

        // Top-Left src
        assertEquals(100f, src[0], 0.01f)
        assertEquals(400f, src[1], 0.01f)
        // Top-Left dst
        assertEquals(0f, dst[0], 0.01f)
        assertEquals(0f, dst[1], 0.01f)

        // Bottom-Right src
        assertEquals(800f, src[4], 0.01f)
        assertEquals(1800f, src[5], 0.01f)
        // Bottom-Right dst
        assertEquals(700f, dst[4], 0.01f)
        assertEquals(1400f, dst[5], 0.01f)
    }

    @Test
    fun `calculateLuminance conforms to standard Rec 601 weighting`() {
        // Pure White: 255
        val white = DocumentDewarper.calculateLuminance(255, 255, 255)
        assertEquals(255f, white, 0.1f)

        // Pure Black: 0
        val black = DocumentDewarper.calculateLuminance(0, 0, 0)
        assertEquals(0f, black, 0.1f)

        // Primary Green has highest perceptual weight (0.587 * 255 ~ 149.685)
        val green = DocumentDewarper.calculateLuminance(0, 255, 0)
        assertEquals(149.685f, green, 0.1f)

        // Primary Blue has lowest perceptual weight (0.114 * 255 ~ 29.07)
        val blue = DocumentDewarper.calculateLuminance(0, 0, 255)
        assertEquals(29.07f, blue, 0.1f)
    }

    @Test
    fun `detectCornersFromLuminanceGrid snaps to high-contrast paper boundaries`() {
        val width = 100
        val height = 100

        // Create synthetic desk image (dark background = lum 30)
        // with bright rectangular paper sheet (lum 220) from [15..85] on X and [15..85] on Y
        val grid = Array(height) { y ->
            FloatArray(width) { x ->
                if (x in 15..85 && y in 15..85) 220f else 30f
            }
        }

        val corners = DocumentDewarper.detectCornersFromLuminanceGrid(grid, width, height)

        // Top-Left should snap near ~0.15 on both X and Y
        assertTrue("TopLeft X should snap near paper edge (~0.15)", corners.topLeft.x in 0.10f..0.22f)
        assertTrue("TopLeft Y should snap near paper edge (~0.15)", corners.topLeft.y in 0.10f..0.22f)

        // Top-Right should snap near ~0.85 on X, ~0.15 on Y
        assertTrue("TopRight X should snap near paper edge (~0.85)", corners.topRight.x in 0.78f..0.90f)
        assertTrue("TopRight Y should snap near paper edge (~0.15)", corners.topRight.y in 0.10f..0.22f)

        // Bottom-Right should snap near ~0.85 on both X and Y
        assertTrue("BottomRight X should snap near paper edge (~0.85)", corners.bottomRight.x in 0.78f..0.90f)
        assertTrue("BottomRight Y should snap near paper edge (~0.85)", corners.bottomRight.y in 0.78f..0.90f)

        // Bottom-Left should snap near ~0.15 on X, ~0.85 on Y
        assertTrue("BottomLeft X should snap near paper edge (~0.15)", corners.bottomLeft.x in 0.10f..0.22f)
        assertTrue("BottomLeft Y should snap near paper edge (~0.85)", corners.bottomLeft.y in 0.78f..0.90f)
    }

    @Test
    fun `withUpdatedCorner updates corner index and clamps into normalized range`() {
        val initial = DocumentCorners()

        // Update Top-Left with negative out-of-bounds point
        val updatedTl = initial.withUpdatedCorner(0, NormalizedPoint(-0.2f, -0.5f))
        assertEquals(0f, updatedTl.topLeft.x, 0.001f)
        assertEquals(0f, updatedTl.topLeft.y, 0.001f)

        // Update Bottom-Right with positive out-of-bounds point
        val updatedBr = initial.withUpdatedCorner(2, NormalizedPoint(1.5f, 1.2f))
        assertEquals(1f, updatedBr.bottomRight.x, 0.001f)
        assertEquals(1f, updatedBr.bottomRight.y, 0.001f)

        // Verify other corners remain unchanged
        assertEquals(initial.topRight, updatedTl.topRight)
        assertEquals(initial.bottomLeft, updatedTl.bottomLeft)
    }
}
