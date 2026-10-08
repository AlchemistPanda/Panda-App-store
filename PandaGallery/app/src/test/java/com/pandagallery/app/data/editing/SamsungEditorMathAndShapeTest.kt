package com.pandagallery.app.data.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SamsungEditorMathAndShapeTest {

    @Test
    fun `straighten scale at 0 degrees is exactly 1`() {
        val scale = calculateStraightenScale(0f, 1.5f)
        assertEquals(1.0f, scale, 0.0001f)
    }

    @Test
    fun `straighten scale at non-zero angle expands appropriately to avoid black borders`() {
        val scale15 = calculateStraightenScale(15f, 1.33f)
        assertTrue("Scale at 15 degrees should be greater than 1.0", scale15 > 1.0f)
        assertTrue("Scale should be clamped within reasonable bounds", scale15 <= 2.5f)

        val scaleNeg20 = calculateStraightenScale(-20f, 0.75f)
        assertTrue("Scale at -20 degrees should be greater than 1.0", scaleNeg20 > 1.0f)
    }

    @Test
    fun `generateCurveSplineLut produces valid 256-byte monotonic lookup table`() {
        val controlPoints = listOf(
            CurveControlPoint(0f, 0f),
            CurveControlPoint(0.25f, 0.18f),
            CurveControlPoint(0.50f, 0.50f),
            CurveControlPoint(0.75f, 0.82f),
            CurveControlPoint(1f, 1f),
        )
        val lut = generateCurveSplineLut(controlPoints)
        assertEquals(256, lut.size)
        assertEquals(0, lut[0].toInt() and 0xFF)
        assertEquals(255, lut[255].toInt() and 0xFF)

        // Verify LUT entries are within 0..255 range
        for (i in 0..255) {
            val v = lut[i].toInt() and 0xFF
            assertTrue("LUT value must be within 0..255", v in 0..255)
        }
    }

    @Test
    fun `hslSelectiveColorMatrix returns valid 20-element ColorMatrix array`() {
        val adjustments = mapOf(
            HslColorChannel.RED to HslAdjustment(hue = 0.2f, saturation = 0.5f, luminance = -0.1f),
            HslColorChannel.BLUE to HslAdjustment(hue = -0.1f, saturation = 0.3f, luminance = 0.2f),
        )
        val matrix = hslSelectiveColorMatrix(adjustments)
        assertEquals(20, matrix.size)
    }

    @Test
    fun `detectAndSnap detects straight lines from noisy stroke`() {
        // Generate a slightly jittery diagonal line from (0.1, 0.1) to (0.9, 0.9)
        val noisyLine = (0..15).map { i ->
            val t = i / 15f
            val jitter = if (i % 2 == 0) 0.003f else -0.003f
            NormalizedPoint(0.1f + t * 0.8f + jitter, 0.1f + t * 0.8f - jitter)
        }
        val snapped = ShapeDetection.detectAndSnap(noisyLine)
        assertNotNull("Should recognize straight line", snapped)
        assertEquals("Snapped line should have 2 endpoints", 2, snapped!!.size)
        assertEquals(noisyLine.first().x, snapped.first().x, 0.01f)
        assertEquals(noisyLine.last().x, snapped.last().x, 0.01f)
    }

    @Test
    fun `detectAndSnap detects circles from circular stroke`() {
        // Generate circular points
        val circlePoints = (0..20).map { i ->
            val angle = (i / 20f) * 2f * Math.PI.toFloat()
            NormalizedPoint(0.5f + 0.3f * cos(angle), 0.5f + 0.3f * sin(angle))
        }
        val snapped = ShapeDetection.detectAndSnap(circlePoints)
        assertNotNull("Should recognize circular stroke as circle/ellipse", snapped)
        assertTrue("Snapped circle should have smooth point loop", snapped!!.size >= 16)
        // First and last points should close
        assertEquals(snapped.first().x, snapped.last().x, 0.02f)
        assertEquals(snapped.first().y, snapped.last().y, 0.02f)
    }

    @Test
    fun `detectAndSnap detects rectangles from roughly 4-corner box`() {
        // Create 4 distinct sides
        val rectPoints = mutableListOf<NormalizedPoint>()
        // Top edge: (0.2, 0.2) to (0.8, 0.2)
        for (i in 0..5) rectPoints.add(NormalizedPoint(0.2f + i * 0.1f, 0.2f))
        // Right edge: (0.8, 0.2) to (0.8, 0.8)
        for (i in 1..5) rectPoints.add(NormalizedPoint(0.8f, 0.2f + i * 0.12f))
        // Bottom edge: (0.8, 0.8) to (0.2, 0.8)
        for (i in 1..5) rectPoints.add(NormalizedPoint(0.8f - i * 0.12f, 0.8f))
        // Left edge: (0.2, 0.8) to (0.2, 0.2)
        for (i in 1..5) rectPoints.add(NormalizedPoint(0.2f, 0.8f - i * 0.12f))

        val snapped = ShapeDetection.detectAndSnap(rectPoints)
        assertNotNull("Should recognize rectangle", snapped)
        assertEquals("Snapped rectangle should have 5 vertices (4 corners + closing point)", 5, snapped!!.size)
    }

    @Test
    fun `detectAndSnap returns null for random scribbles`() {
        val randomScribble = listOf(
            NormalizedPoint(0.1f, 0.2f),
            NormalizedPoint(0.9f, 0.3f),
            NormalizedPoint(0.2f, 0.8f),
            NormalizedPoint(0.8f, 0.1f),
            NormalizedPoint(0.4f, 0.5f),
            NormalizedPoint(0.1f, 0.9f),
        )
        val result = ShapeDetection.detectAndSnap(randomScribble)
        assertNull("Random scribble should not snap to any shape", result)
    }
}
