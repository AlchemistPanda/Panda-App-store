package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The quality setting has one value that is not a quality: [ADAPTIVE_IMAGE_QUALITY].
 *
 * Every path that writes the setting back — Settings' quality dialog, and Compression Studio's
 * Save Copy / Replace Original — used to run it through `coerceIn(1, 100)`, which turned the
 * Smart Auto preset into quality 1. Selecting "Smart Auto" and then merely opening the quality
 * dialog was enough to leave every subsequent compression encoding at the worst setting the
 * encoder offers.
 */
class CompressionQualitySettingTest {

    @Test
    fun `adaptive sentinel survives normalisation`() {
        assertEquals(ADAPTIVE_IMAGE_QUALITY, normalizeImageQuality(ADAPTIVE_IMAGE_QUALITY))
    }

    @Test
    fun `ordinary qualities are kept as chosen`() {
        assertEquals(1, normalizeImageQuality(1))
        assertEquals(75, normalizeImageQuality(75))
        assertEquals(100, normalizeImageQuality(100))
    }

    @Test
    fun `out of range values are clamped rather than passed to the encoder`() {
        assertEquals(1, normalizeImageQuality(0))
        assertEquals(1, normalizeImageQuality(-2))
        assertEquals(100, normalizeImageQuality(140))
    }

    @Test
    fun `smart auto preset round-trips through the setting`() {
        val stored = normalizeImageQuality(CompressionPreset.SMART_AUTO.imageQuality)
        assertEquals(CompressionPreset.SMART_AUTO.imageQuality, stored)
        // The engine reads a negative quality as "classify the content and pick"; anything
        // clamped into range would instead be taken literally.
        assertEquals(true, stored < 0)
    }

    @Test
    fun `every other preset stores a real encoder quality`() {
        CompressionPreset.entries
            .filter { it != CompressionPreset.SMART_AUTO }
            .forEach { preset ->
                assertEquals(
                    "${preset.name} should store its quality unchanged",
                    preset.imageQuality,
                    normalizeImageQuality(preset.imageQuality),
                )
                assertEquals("${preset.name} must be in encoder range", true, preset.imageQuality in 1..100)
            }
    }
}
