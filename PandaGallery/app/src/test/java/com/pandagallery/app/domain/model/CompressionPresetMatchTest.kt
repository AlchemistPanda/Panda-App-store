package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompressionPresetMatchTest {

    private fun preferencesFor(preset: CompressionPreset) = UserPreferences(
        compressionPreset = preset,
        imageFormat = preset.imageFormat,
        imageQuality = preset.imageQuality,
        videoResolution = preset.videoResolution,
        videoCodec = preset.videoCodec,
    )

    @Test
    fun `settings straight from a preset report that preset`() {
        CompressionPreset.entries.forEach { preset ->
            assertEquals(preset, preferencesFor(preset).matchedCompressionPreset)
        }
    }

    @Test
    fun `hand-tuning image quality away from the preset reports no preset`() {
        val tuned = preferencesFor(CompressionPreset.MEDIUM).copy(imageQuality = 90)
        assertNull(tuned.matchedCompressionPreset)
    }

    @Test
    fun `hand-tuning video resolution away from the preset reports no preset`() {
        val tuned = preferencesFor(CompressionPreset.HIGH).copy(videoResolution = VideoResolution.ORIGINAL)
        assertNull(tuned.matchedCompressionPreset)
    }

    @Test
    fun `a stale stored preset does not override the actual values`() {
        // The stored preset still says LOW, but every value matches HIGH — the values win.
        val stale = preferencesFor(CompressionPreset.HIGH).copy(compressionPreset = CompressionPreset.LOW)
        assertEquals(CompressionPreset.HIGH, stale.matchedCompressionPreset)
    }

    @Test
    fun `tuning back onto a preset's values re-reports that preset`() {
        val roundTrip = preferencesFor(CompressionPreset.LOW)
            .copy(imageQuality = 90)
            .copy(imageQuality = CompressionPreset.LOW.imageQuality)
        assertEquals(CompressionPreset.LOW, roundTrip.matchedCompressionPreset)
    }

    @Test
    fun `default preferences report HIGH preset with JPEG format and 1080p resolution`() {
        val defaultPrefs = UserPreferences()
        assertEquals(CompressionPreset.HIGH, defaultPrefs.compressionPreset)
        assertEquals(ImageFormat.JPEG, defaultPrefs.imageFormat)
        assertEquals(75, defaultPrefs.imageQuality)
        assertEquals(VideoResolution.P1080, defaultPrefs.videoResolution)
        assertEquals(CompressionPreset.HIGH, defaultPrefs.matchedCompressionPreset)
    }

    @Test
    fun `HIGH preset matches JPEG format and 1080p video resolution`() {
        assertEquals(ImageFormat.JPEG, CompressionPreset.HIGH.imageFormat)
        assertEquals(75, CompressionPreset.HIGH.imageQuality)
        assertEquals(VideoResolution.P1080, CompressionPreset.HIGH.videoResolution)
    }

    @Test
    fun `MEDIUM preset matches JPEG format and 1080p video resolution`() {
        assertEquals(ImageFormat.JPEG, CompressionPreset.MEDIUM.imageFormat)
        assertEquals(50, CompressionPreset.MEDIUM.imageQuality)
        assertEquals(VideoResolution.P1080, CompressionPreset.MEDIUM.videoResolution)
    }
}
