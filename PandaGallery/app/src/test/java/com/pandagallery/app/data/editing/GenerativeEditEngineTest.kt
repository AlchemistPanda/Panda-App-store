package com.pandagallery.app.data.editing

import com.pandagallery.app.domain.model.GenerativeEditConfig
import com.pandagallery.app.domain.model.GenerativeEditMode
import com.pandagallery.app.domain.model.GenerativeEditResult
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

/**
 * Pure JVM unit tests for GenerativeEditEngine supporting logic.
 *
 * Note: Android graphics classes (Bitmap, Canvas, Matrix, Camera) are stubbed stubs
 * on the JVM test runner and cannot be instantiated without Robolectric.
 * These tests cover the pure-Kotlin data-model and configuration logic that
 * underpins GenerativeEditEngine, following the same no-Robolectric pattern
 * used throughout the PandaGallery test suite.
 */
class GenerativeEditEngineTest {

    // ──────────────────────────────────────────────────────────────────────────
    // GenerativeEditConfig model tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun config_defaultValues_hasNoModifications() {
        val config = GenerativeEditConfig()
        assertFalse("Default config must report no modifications", config.hasModifications)
    }

    @Test
    fun config_withRotation_hasModifications() {
        val config = GenerativeEditConfig(rotationDegrees = 5f)
        assertTrue("Config with rotation must report hasModifications=true", config.hasModifications)
    }

    @Test
    fun config_withVerticalTilt_hasModifications() {
        val config = GenerativeEditConfig(verticalTilt = 10f)
        assertTrue("Config with verticalTilt must report hasModifications=true", config.hasModifications)
    }

    @Test
    fun config_withHorizontalTilt_hasModifications() {
        val config = GenerativeEditConfig(horizontalTilt = -5f)
        assertTrue("Config with horizontalTilt must report hasModifications=true", config.hasModifications)
    }

    @Test
    fun config_withExpandLeft_hasModifications() {
        val config = GenerativeEditConfig(expandLeftPct = 0.1f)
        assertTrue("Config with expandLeftPct must report hasModifications=true", config.hasModifications)
    }

    @Test
    fun config_withExpandTop_hasModifications() {
        val config = GenerativeEditConfig(expandTopPct = 0.05f)
        assertTrue("Config with expandTopPct must report hasModifications=true", config.hasModifications)
    }

    @Test
    fun config_withExpandRight_hasModifications() {
        val config = GenerativeEditConfig(expandRightPct = 0.2f)
        assertTrue("Config with expandRightPct must report hasModifications=true", config.hasModifications)
    }

    @Test
    fun config_withExpandBottom_hasModifications() {
        val config = GenerativeEditConfig(expandBottomPct = 0.15f)
        assertTrue("Config with expandBottomPct must report hasModifications=true", config.hasModifications)
    }

    @Test
    fun config_withAllZeroExpansions_noModifications() {
        val config = GenerativeEditConfig(
            expandLeftPct = 0f, expandTopPct = 0f,
            expandRightPct = 0f, expandBottomPct = 0f,
        )
        assertFalse("All-zero expand must not report modifications", config.hasModifications)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GenerativeEditMode enum tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun mode_allEntriesPresent() {
        val modes = GenerativeEditMode.entries
        assertTrue("STRAIGHTEN must be present", modes.contains(GenerativeEditMode.STRAIGHTEN))
        assertTrue("PERSPECTIVE must be present", modes.contains(GenerativeEditMode.PERSPECTIVE))
        assertTrue("EXPAND must be present", modes.contains(GenerativeEditMode.EXPAND))
    }

    @Test
    fun mode_entriesCount_isThree() {
        assertEquals("GenerativeEditMode must have exactly 3 entries", 3, GenerativeEditMode.entries.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Dimension-math helper tests (the pure-Kotlin arithmetic inside computeCompositeTransform)
    // Validates the boundary-expansion padding formula independently of Android.graphics.Matrix
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Simulates the expansion padding math from computeCompositeTransform without
     * instantiating any Android graphics class.
     */
    private fun simulateExpandedDimensions(
        baseW: Float,
        baseH: Float,
        expandLeftPct: Float,
        expandTopPct: Float,
        expandRightPct: Float,
        expandBottomPct: Float,
    ): Pair<Int, Int> {
        val padLeft = baseW * expandLeftPct
        val padTop = baseH * expandTopPct
        val padRight = baseW * expandRightPct
        val padBottom = baseH * expandBottomPct
        val finalW = (baseW + padLeft + padRight).roundToInt().coerceAtLeast(1)
        val finalH = (baseH + padTop + padBottom).roundToInt().coerceAtLeast(1)
        return finalW to finalH
    }

    @Test
    fun expandPadding_noExpansion_preservesDimensions() {
        val (w, h) = simulateExpandedDimensions(400f, 300f, 0f, 0f, 0f, 0f)
        assertEquals("Width without expansion", 400, w)
        assertEquals("Height without expansion", 300, h)
    }

    @Test
    fun expandPadding_10pctLeftRight_addsCorrectWidth() {
        // 10% left + 10% right on 400px base → adds 80px
        val (w, h) = simulateExpandedDimensions(400f, 300f, 0.10f, 0f, 0.10f, 0f)
        val expectedW = (400 + 40 + 40)
        assertTrue(
            "Expected width ~$expectedW, got $w (±2 tolerance)",
            abs(w - expectedW) <= 2,
        )
        assertEquals("Height must be unchanged", 300, h)
    }

    @Test
    fun expandPadding_10pctTopBottom_addsCorrectHeight() {
        // 10% top + 10% bottom on 300px base → adds 60px
        val (w, h) = simulateExpandedDimensions(400f, 300f, 0f, 0.10f, 0f, 0.10f)
        val expectedH = (300 + 30 + 30)
        assertTrue(
            "Expected height ~$expectedH, got $h (±2 tolerance)",
            abs(h - expectedH) <= 2,
        )
        assertEquals("Width must be unchanged", 400, w)
    }

    @Test
    fun expandPadding_50pctAllSides_doublesEachDimension() {
        // 50% on all sides doubles both dimensions
        val (w, h) = simulateExpandedDimensions(200f, 100f, 0.5f, 0.5f, 0.5f, 0.5f)
        // Expected: 200 + 100 + 100 = 400 width; 100 + 50 + 50 = 200 height
        assertTrue("Width should double to ~400, got $w", abs(w - 400) <= 2)
        assertTrue("Height should double to ~200, got $h", abs(h - 200) <= 2)
    }

    @Test
    fun expandPadding_smallSource_neverGoesNegative() {
        val (w, h) = simulateExpandedDimensions(1f, 1f, 0f, 0f, 0f, 0f)
        assertTrue("Width must be >= 1, got $w", w >= 1)
        assertTrue("Height must be >= 1, got $h", h >= 1)
    }

    @Test
    fun expandPadding_asymmetric_addsCorrectSides() {
        // 20% left, 0% right on a 500px wide image → adds 100px left only
        val (w, _) = simulateExpandedDimensions(500f, 400f, 0.20f, 0f, 0f, 0f)
        assertTrue("Asymmetric left pad: expected ~600, got $w", abs(w - 600) <= 2)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Horizon rotation bounding-box math (standalone Kotlin geometry)
    // Validates the corner-rotation formula without Android.graphics.Matrix
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Pure Kotlin rotation of a single point (px, py) around center (cx, cy) by [angleDeg].
     * Mirrors what GenerativeEditEngine.computeCompositeTransform does via Matrix.postRotate.
     */
    private fun rotatePoint(px: Float, py: Float, cx: Float, cy: Float, angleDeg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        val dx = px - cx
        val dy = py - cy
        return (cx + dx * cos - dy * sin) to (cy + dx * sin + dy * cos)
    }

    private fun rotatedBoundingBox(srcW: Int, srcH: Int, angleDeg: Float): Pair<Int, Int> {
        val cx = srcW / 2f
        val cy = srcH / 2f
        val corners = listOf(
            rotatePoint(0f, 0f, cx, cy, angleDeg),
            rotatePoint(srcW.toFloat(), 0f, cx, cy, angleDeg),
            rotatePoint(srcW.toFloat(), srcH.toFloat(), cx, cy, angleDeg),
            rotatePoint(0f, srcH.toFloat(), cx, cy, angleDeg),
        )
        val minX = corners.minOf { it.first }
        val maxX = corners.maxOf { it.first }
        val minY = corners.minOf { it.second }
        val maxY = corners.maxOf { it.second }
        val w = (maxX - minX).roundToInt().coerceAtLeast(1)
        val h = (maxY - minY).roundToInt().coerceAtLeast(1)
        return w to h
    }

    @Test
    fun rotationBBox_zero_preservesDimensions() {
        val (w, h) = rotatedBoundingBox(400, 300, 0f)
        assertEquals("0° rotation must preserve width", 400, w)
        assertEquals("0° rotation must preserve height", 300, h)
    }

    @Test
    fun rotationBBox_90degrees_swapsDimensions() {
        val (w, h) = rotatedBoundingBox(400, 300, 90f)
        // After a 90° rotation, width and height must be swapped
        assertTrue("90° rotation must swap: expected ~300 width, got $w", abs(w - 300) <= 2)
        assertTrue("90° rotation must swap: expected ~400 height, got $h", abs(h - 400) <= 2)
    }

    @Test
    fun rotationBBox_negativeAngle_sameAsPositive() {
        val (w1, h1) = rotatedBoundingBox(600, 400, 20f)
        val (w2, h2) = rotatedBoundingBox(600, 400, -20f)
        assertEquals("Positive and negative 20° bounding box widths must match", w1, w2)
        assertEquals("Positive and negative 20° bounding box heights must match", h1, h2)
    }

    @Test
    fun rotationBBox_canvasMustContainSourceDiagonal() {
        val side = 200
        val originalDiagonal = sqrt((side * side + side * side).toDouble())
        val (w, h) = rotatedBoundingBox(side, side, 45f)
        val outputDiagonal = sqrt((w * w + h * h).toDouble())
        assertTrue(
            "Output diagonal ($outputDiagonal) must be >= source diagonal ($originalDiagonal)",
            outputDiagonal >= originalDiagonal - 1.0,
        )
    }

    @Test
    fun rotationBBox_monotonicGrowth_from0to45Degrees() {
        val sizes = listOf(0f, 5f, 10f, 15f, 20f, 30f, 45f).map { angle ->
            val (w, h) = rotatedBoundingBox(600, 400, angle)
            w + h
        }
        for (i in 1 until sizes.size) {
            assertTrue(
                "Bounding box at ${i * 5}° (${sizes[i]}) should be >= at ${(i - 1) * 5}° (${sizes[i - 1]})",
                sizes[i] >= sizes[i - 1],
            )
        }
    }
}
