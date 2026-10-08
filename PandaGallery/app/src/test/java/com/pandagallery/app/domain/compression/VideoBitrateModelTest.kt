package com.pandagallery.app.domain.compression

import com.pandagallery.app.domain.model.CompressionPreset
import com.pandagallery.app.domain.model.UserPreferences
import com.pandagallery.app.domain.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The video model is anchored to what Media3 actually requests of the encoder:
 * `DefaultEncoderFactory.getSuggestedBitrate` = `width * height * frameRate * 0.07 * 2.0`.
 *
 * Measured on device: a 848x478 clip had its encoder configured at 1,702,037 bps, against a
 * predicted 848 * 478 * 30 * 0.14 = 1,702,445. The old model used half that constant and then
 * scaled it down again per codec, leaving it ~3.2x optimistic — which is how the batch sheet
 * came to promise "frees ~12.7 MB (60% reduction)" for a 21.2 MB WhatsApp video that the queue
 * then refused to replace, having produced nothing smaller.
 */
class VideoBitrateModelTest {

    private fun preferences(preset: CompressionPreset) = UserPreferences(
        compressionPreset = preset,
        imageFormat = preset.imageFormat,
        imageQuality = preset.imageQuality,
        videoResolution = preset.videoResolution,
        videoCodec = preset.videoCodec,
    )

    /** The clip used for the on-device measurement. */
    private val whatsAppClip = CompressionEstimator.Input(
        sizeBytes = 22_251_163,
        width = 848,
        height = 478,
        isVideo = true,
        durationMillis = 108_884,
    )

    @Test
    fun `an already-compressed sub-1080p clip is predicted to save nothing`() {
        val estimate = CompressionEstimator.estimate(whatsAppClip, preferences(CompressionPreset.HIGH))

        // The queue kept the original for exactly this file; the prediction has to agree.
        assertEquals(whatsAppClip.sizeBytes, estimate.estimatedBytes)
        assertEquals(0, estimate.savingsPercent)
    }

    @Test
    fun `downscaling 4K still predicts a large saving`() {
        val fourK = CompressionEstimator.Input(
            sizeBytes = 300_000_000,
            width = 3840,
            height = 2160,
            isVideo = true,
            durationMillis = 60_000,
        )
        val estimate = CompressionEstimator.estimate(fourK, preferences(CompressionPreset.HIGH))

        assertTrue(
            "4K down to 1080p should still be worth doing, got ${estimate.savingsPercent}%",
            estimate.savingsPercent > 50,
        )
    }

    @Test
    fun `the model tracks Media3's own bitrate formula`() {
        // 10 seconds of 1920x1080 at the 1080p ceiling: no scaling, so the whole prediction is
        // Media3's suggested video bitrate plus the AAC track.
        val clip = CompressionEstimator.Input(
            sizeBytes = 500_000_000,
            width = 1920,
            height = 1080,
            isVideo = true,
            durationMillis = 10_000,
        )
        val estimate = CompressionEstimator.estimate(
            clip,
            preferences(CompressionPreset.HIGH).copy(videoResolution = VideoResolution.P1080),
        )

        val media3Bitrate = 1920.0 * 1080.0 * 30.0 * 0.07 * 2.0
        val expected = ((media3Bitrate + 128_000.0) * 10.0 / 8.0).toLong()
        assertEquals(expected.toDouble(), estimate.estimatedBytes.toDouble(), expected * 0.01)
    }
}
