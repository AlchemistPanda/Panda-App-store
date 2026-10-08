package com.pandagallery.app.domain.compression

import com.pandagallery.app.domain.model.CompressionPreset
import com.pandagallery.app.domain.model.ImageFormat
import com.pandagallery.app.domain.model.UserPreferences
import com.pandagallery.app.domain.model.VideoCodec
import com.pandagallery.app.domain.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionEstimatorTest {

    private val twelveMegapixelPhoto = CompressionEstimator.Input(
        sizeBytes = 5L * 1024 * 1024, // 5 MB
        width = 4032,
        height = 3024,
        isVideo = false,
    )

    private fun preferences(preset: CompressionPreset) = UserPreferences(
        compressionPreset = preset,
        imageFormat = preset.imageFormat,
        imageQuality = preset.imageQuality,
        videoResolution = preset.videoResolution,
        videoCodec = preset.videoCodec,
    )

    @Test
    fun `lower quality presets predict smaller output`() {
        val low = CompressionEstimator.estimate(twelveMegapixelPhoto, preferences(CompressionPreset.LOW))
        val medium = CompressionEstimator.estimate(twelveMegapixelPhoto, preferences(CompressionPreset.MEDIUM))
        val high = CompressionEstimator.estimate(twelveMegapixelPhoto, preferences(CompressionPreset.HIGH))

        assertTrue(low.estimatedBytes < medium.estimatedBytes)
        assertTrue(medium.estimatedBytes < high.estimatedBytes)
    }

    /**
     * Guards the shape of the model rather than an exact byte count. A 12MP phone photo at
     * WebP q50 lands around 1.5-2.5 MB in practice; if a change pushes the prediction
     * outside that, the numbers shown to users have stopped being credible.
     */
    @Test
    fun `a typical phone photo predicts a plausible size`() {
        val estimate = CompressionEstimator.estimate(twelveMegapixelPhoto, preferences(CompressionPreset.MEDIUM))
        val megabytes = estimate.estimatedBytes / (1024.0 * 1024.0)

        // Measured on real 12MP camera originals: WebP q50 lands around 0.1–0.3 bits per pixel,
        // i.e. a few hundred KB — not the 1.2–2.6 MB the old, five-times-too-high curve claimed.
        assertTrue("got ${"%.2f".format(megabytes)} MB", megabytes in 0.15..0.8)
    }

    /**
     * What the encoder actually achieves on 12MP originals, measured across a real library:
     * 91–97% at LOW, 88–95% at MEDIUM, 83–90% at HIGH. The ranges below bracket that.
     *
     * The older expectations (85 / 70 / 50) came from the mis-calibrated curve rather than from
     * the encoder, and were what made every preset report "no meaningful saving" on already-
     * compressed photos. If this test starts failing, measure before changing the numbers.
     */
    @Test
    fun `predicted savings track the advertised preset figures`() {
        fun savingsFor(preset: CompressionPreset) =
            CompressionEstimator.estimate(twelveMegapixelPhoto, preferences(preset)).savingsPercent

        assertTrue("LOW was ${savingsFor(CompressionPreset.LOW)}%", savingsFor(CompressionPreset.LOW) in 88..98)
        assertTrue("MEDIUM was ${savingsFor(CompressionPreset.MEDIUM)}%", savingsFor(CompressionPreset.MEDIUM) in 84..96)
        assertTrue("HIGH was ${savingsFor(CompressionPreset.HIGH)}%", savingsFor(CompressionPreset.HIGH) in 78..94)
        assertTrue("NEAR_LOSSLESS was ${savingsFor(CompressionPreset.NEAR_LOSSLESS)}%", savingsFor(CompressionPreset.NEAR_LOSSLESS) in 60..90)
    }

    @Test
    fun `estimate never exceeds the original size`() {
        // An already-tiny thumbnail would "compress" to something larger.
        val tiny = CompressionEstimator.Input(sizeBytes = 8_000, width = 4032, height = 3024, isVideo = false)
        val estimate = CompressionEstimator.estimate(tiny, preferences(CompressionPreset.HIGH))

        assertEquals(tiny.sizeBytes, estimate.estimatedBytes)
        assertEquals(0, estimate.savingsPercent)
    }

    @Test
    fun `AVIF predicts smaller than WEBP which predicts smaller than JPEG`() {
        val base = preferences(CompressionPreset.MEDIUM)
        val avif = CompressionEstimator.estimate(twelveMegapixelPhoto, base.copy(imageFormat = ImageFormat.AVIF))
        val webp = CompressionEstimator.estimate(twelveMegapixelPhoto, base.copy(imageFormat = ImageFormat.WEBP))
        val jpeg = CompressionEstimator.estimate(twelveMegapixelPhoto, base.copy(imageFormat = ImageFormat.JPEG))

        assertTrue(avif.estimatedBytes < webp.estimatedBytes)
        assertTrue(webp.estimatedBytes < jpeg.estimatedBytes)
    }

    @Test
    fun `oversized images are estimated at their decoded size`() {
        // The engine halves dimensions until the long edge fits MAX_DECODE_EDGE (6144), so a
        // 16000px image is encoded at 4000px and must not be predicted as if it were full size.
        assertEquals(4000L * 3000L, CompressionEstimator.decodedPixels(16000, 12000))
        assertEquals(4032L * 3024L, CompressionEstimator.decodedPixels(4032, 3024))
        // 24 MP stays whole: the old 4096 assumption predicted a quarter of the real output.
        assertEquals(6000L * 4000L, CompressionEstimator.decodedPixels(6000, 4000))
    }

    @Test
    fun `bits per pixel rises with quality and is clamped at the ends`() {
        assertTrue(CompressionEstimator.webpBitsPerPixel(30) < CompressionEstimator.webpBitsPerPixel(50))
        assertTrue(CompressionEstimator.webpBitsPerPixel(50) < CompressionEstimator.webpBitsPerPixel(75))
        assertEquals(
            CompressionEstimator.webpBitsPerPixel(1),
            CompressionEstimator.webpBitsPerPixel(5),
            0.0001,
        )
    }

    // ------------------------------------------------------------------------ video

    private val oneMinute1080p = CompressionEstimator.Input(
        sizeBytes = 120L * 1024 * 1024,
        width = 1920,
        height = 1080,
        isVideo = true,
        durationMillis = 60_000,
    )

    @Test
    fun `downscaling video predicts a smaller file`() {
        val base = preferences(CompressionPreset.MEDIUM)
        val p480 = CompressionEstimator.estimate(oneMinute1080p, base.copy(videoResolution = VideoResolution.P480))
        val p720 = CompressionEstimator.estimate(oneMinute1080p, base.copy(videoResolution = VideoResolution.P720))
        val p1080 = CompressionEstimator.estimate(oneMinute1080p, base.copy(videoResolution = VideoResolution.P1080))

        assertTrue(p480.estimatedBytes < p720.estimatedBytes)
        assertTrue(p720.estimatedBytes < p1080.estimatedBytes)
    }

    /**
     * The codec does not change the predicted size, because it does not change the requested
     * bitrate: `DefaultEncoderFactory.getSuggestedBitrate` is derived from width, height and
     * frame rate alone. H.265 and AV1 spend the same budget on looking better rather than on
     * being smaller, so predicting them as smaller promised savings nothing would deliver.
     */
    @Test
    fun `codec choice does not change the predicted size`() {
        val base = preferences(CompressionPreset.MEDIUM)
        val h264 = CompressionEstimator.estimate(oneMinute1080p, base.copy(videoCodec = VideoCodec.H264))
        val h265 = CompressionEstimator.estimate(oneMinute1080p, base.copy(videoCodec = VideoCodec.H265))
        val av1 = CompressionEstimator.estimate(oneMinute1080p, base.copy(videoCodec = VideoCodec.AV1))

        assertEquals(h264.estimatedBytes, h265.estimatedBytes)
        assertEquals(h264.estimatedBytes, av1.estimatedBytes)
    }

    @Test
    fun `video is never upscaled beyond its source resolution`() {
        val small = oneMinute1080p.copy(width = 640, height = 480)
        val base = preferences(CompressionPreset.MEDIUM)
        val to720 = CompressionEstimator.estimate(small, base.copy(videoResolution = VideoResolution.P720))
        val original = CompressionEstimator.estimate(small, base.copy(videoResolution = VideoResolution.ORIGINAL))

        assertEquals(original.estimatedBytes, to720.estimatedBytes)
    }

    @Test
    fun `a video with unknown duration falls back to its current size`() {
        val unknown = oneMinute1080p.copy(durationMillis = null)
        val estimate = CompressionEstimator.estimate(unknown, preferences(CompressionPreset.MEDIUM))

        assertEquals(unknown.sizeBytes, estimate.estimatedBytes)
    }

    // ------------------------------------------------------------------------ batches

    @Test
    fun `a batch sums its members`() {
        val batch = CompressionEstimator.estimate(
            listOf(twelveMegapixelPhoto, twelveMegapixelPhoto, oneMinute1080p),
            preferences(CompressionPreset.MEDIUM),
        )

        assertEquals(3, batch.itemCount)
        assertEquals(twelveMegapixelPhoto.sizeBytes * 2 + oneMinute1080p.sizeBytes, batch.originalBytes)
        assertTrue(batch.estimatedBytes < batch.originalBytes)
    }

    @Test
    fun `the range brackets the point estimate and never exceeds the original`() {
        val estimate = CompressionEstimator.estimate(twelveMegapixelPhoto, preferences(CompressionPreset.MEDIUM))

        assertTrue(estimate.lowBytes <= estimate.estimatedBytes)
        assertTrue(estimate.estimatedBytes <= estimate.highBytes)
        assertTrue(estimate.highBytes <= estimate.originalBytes)
    }

    @Test
    fun `an empty selection is not meaningful`() {
        val estimate = CompressionEstimator.estimate(emptyList(), preferences(CompressionPreset.MEDIUM))

        assertEquals(CompressionEstimator.Estimate.Empty, estimate)
        assertFalse(estimate.isMeaningful)
    }

    @Test
    fun `summary reads as an approximation`() {
        val estimate = CompressionEstimator.estimate(twelveMegapixelPhoto, preferences(CompressionPreset.MEDIUM))

        assertTrue(estimate.summary, estimate.summary.contains("→ ~"))
        assertTrue(estimate.summary, estimate.summary.startsWith("5.0 MB"))
        assertTrue(estimate.summaryWithSavings, estimate.summaryWithSavings.contains("saves ~"))
    }

    @Test
    fun `summary says so plainly when there is nothing to save`() {
        val tiny = CompressionEstimator.Input(sizeBytes = 8_000, width = 4032, height = 3024, isVideo = false)
        val estimate = CompressionEstimator.estimate(tiny, preferences(CompressionPreset.HIGH))

        assertTrue(estimate.summaryWithSavings, estimate.summaryWithSavings.contains("no meaningful saving"))
    }

    @Test
    fun `smart auto preset produces realistic estimate`() {
        val estimate = CompressionEstimator.estimate(twelveMegapixelPhoto, preferences(CompressionPreset.SMART_AUTO))
        assertTrue(estimate.estimatedBytes > 0L)
        assertTrue("Estimated bytes should be smaller than original", estimate.estimatedBytes < twelveMegapixelPhoto.sizeBytes)
        assertTrue("Smart auto estimate should be marked meaningful", estimate.isMeaningful)
    }
}
