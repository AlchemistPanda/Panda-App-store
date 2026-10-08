package com.pandagallery.app.domain.compression

import com.pandagallery.app.domain.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class CompressionSizingTest {

    /** A fake encoder whose output grows with quality: 10 KB per quality point. */
    private fun fakeEncode(quality: Int) = ByteArray(quality * 10_000)

    @Test
    fun `budget search picks the highest quality that fits`() {
        val budget = 500_000L
        val result = encodeWithinBudget(budgetBytes = budget, encode = ::fakeEncode)
        assertTrue(result.bytes.size <= budget)
        assertEquals(50, result.quality)
        assertEquals(500_000, result.bytes.size)
    }

    @Test
    fun `when nothing fits the smallest attempt is kept, not the first`() {
        val budget = 1_000L
        val tried = mutableListOf<Int>()
        val result = encodeWithinBudget(budgetBytes = budget) { quality ->
            tried += quality
            fakeEncode(quality)
        }
        assertFalse(result.bytes.size <= budget)
        // The search runs all the way down, so the floor of the range is actually tried.
        assertEquals(15, result.quality)
        assertEquals(tried.min(), result.quality)
        assertTrue("searched in ${tried.size} encodes", tried.size <= 7)
    }

    @Test
    fun `budget search reports every attempt`() {
        var attempts = 0
        encodeWithinBudget(budgetBytes = 300_000L, onAttempt = { attempts = it }, encode = ::fakeEncode)
        assertTrue(attempts in 1..7)
    }

    @Test
    fun `preview bytes scale with pixel count, not its square root`() {
        // 4000x3000 previewed at 1000x750 is 16x the pixels; sqrt would have said 4x.
        val scale = previewByteScale(4000, 3000, 1000, 750)
        assertEquals(16.0.pow(0.9), scale, 1e-9)
        assertTrue(scale > 10.0)
    }

    @Test
    fun `preview scale extrapolates to the job's capped decode, not the raw source`() {
        // 16000x12000 is decoded by the job at 4000x3000, same as the case above.
        assertEquals(previewByteScale(4000, 3000, 1000, 750), previewByteScale(16000, 12000, 1000, 750), 1e-9)
    }

    @Test
    fun `preview at full size, or unknown sizes, scale by one`() {
        assertEquals(1.0, previewByteScale(1000, 750, 1000, 750), 0.0)
        assertEquals(1.0, previewByteScale(0, 0, 1000, 750), 0.0)
    }

    @Test
    fun `decode sample size halves until both edges fit the cap`() {
        assertEquals(1, decodeSampleSize(6000, 4000))
        assertEquals(2, decodeSampleSize(8160, 6120))
        assertEquals(4, decodeSampleSize(16000, 12000))
        assertEquals(4, decodeSampleSize(4000, 3000, maxEdge = 1920))
    }

    @Test
    fun `portrait video at the setting is left alone, like landscape`() {
        assertNull(videoScaledDimensions(VideoResolution.P1080, 1080, 1920))
        assertNull(videoScaledDimensions(VideoResolution.P1080, 1920, 1080))
    }

    @Test
    fun `the setting caps the short edge in either orientation`() {
        assertEquals(1080 to 1920, videoScaledDimensions(VideoResolution.P1080, 2160, 3840))
        assertEquals(1920 to 1080, videoScaledDimensions(VideoResolution.P1080, 3840, 2160))
        assertEquals(720 to 1280, videoScaledDimensions(VideoResolution.P720, 1080, 1920))
        assertEquals(1280 to 720, videoScaledDimensions(VideoResolution.P720, 1920, 1080))
    }

    @Test
    fun `scaled edges are always even`() {
        // 1080x1350 (4:5) at 720p is 720x900 exactly; 1001x1777 at 480p needs rounding.
        assertEquals(720 to 900, videoScaledDimensions(VideoResolution.P720, 1080, 1350))
        val (width, height) = videoScaledDimensions(VideoResolution.P480, 1001, 1777)!!
        assertEquals(0, width % 2)
        assertEquals(0, height % 2)
    }

    @Test
    fun `never scales up, and original or unreadable sizes are untouched`() {
        assertNull(videoScaledDimensions(VideoResolution.P1080, 848, 474))
        assertNull(videoScaledDimensions(VideoResolution.ORIGINAL, 3840, 2160))
        assertNull(videoScaledDimensions(VideoResolution.P720, 0, 0))
    }
}
