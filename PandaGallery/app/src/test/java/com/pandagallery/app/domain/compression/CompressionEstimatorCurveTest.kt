package com.pandagallery.app.domain.compression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bits-per-pixel anchors are measured points on a convex rate curve. Interpolating between
 * them with a straight line sits above the real curve, which inflated the predicted output for
 * exactly the presets that fall between anchors — Smart Auto (resolved to ~quality 80) and
 * Near-Lossless (92). Inflated enough, the prediction clamps to the original size and the UI
 * reports "no meaningful saving" for work that does in fact save.
 */
class CompressionEstimatorCurveTest {

    @Test
    fun `measured anchors are returned exactly`() {
        assertEquals(0.06, CompressionEstimator.webpBitsPerPixel(10), 1e-9)
        assertEquals(0.18, CompressionEstimator.webpBitsPerPixel(30), 1e-9)
        assertEquals(0.26, CompressionEstimator.webpBitsPerPixel(50), 1e-9)
        assertEquals(0.38, CompressionEstimator.webpBitsPerPixel(75), 1e-9)
        assertEquals(0.85, CompressionEstimator.webpBitsPerPixel(90), 1e-9)
        assertEquals(2.00, CompressionEstimator.webpBitsPerPixel(100), 1e-9)
    }

    @Test
    fun `curve stays strictly increasing across its whole range`() {
        var previous = 0.0
        for (quality in 1..100) {
            val bpp = CompressionEstimator.webpBitsPerPixel(quality)
            assertTrue("quality $quality should not cost fewer bits than ${quality - 1}", bpp >= previous)
            previous = bpp
        }
    }

    @Test
    fun `interpolated points sit below the straight line between their anchors`() {
        // Quality 80 lies a third of the way from the 75 anchor to the 90 one.
        val low = CompressionEstimator.webpBitsPerPixel(75)
        val high = CompressionEstimator.webpBitsPerPixel(90)
        val straightLine = low + (high - low) / 3.0
        val actual = CompressionEstimator.webpBitsPerPixel(80)
        assertTrue("quality 80 should be below the chord ($actual vs $straightLine)", actual < straightLine)
        assertTrue("but still above the lower anchor", actual > low)
    }

    @Test
    fun `values outside the anchor range are held at the ends`() {
        assertEquals(
            CompressionEstimator.webpBitsPerPixel(1),
            CompressionEstimator.webpBitsPerPixel(9),
            1e-9,
        )
        // Clamping, not extrapolation, past the top anchor.
        assertEquals(2.00, CompressionEstimator.webpBitsPerPixel(120), 1e-9)
    }
}
