package com.pandagallery.app.data.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSharePreparerTest {
    @Test
    fun `modern image formats are converted for compatible sharing`() {
        assertTrue(requiresJpegShareConversion("image/avif"))
        assertTrue(requiresJpegShareConversion("image/webp"))
        assertTrue(requiresJpegShareConversion("image/heic"))
    }

    @Test
    fun `jpeg png and videos keep their original representation`() {
        assertFalse(requiresJpegShareConversion("image/jpeg"))
        assertFalse(requiresJpegShareConversion("image/png"))
        assertFalse(requiresJpegShareConversion("video/mp4"))
    }

    @Test
    fun `mixed images use wildcard image mime type`() {
        assertEquals("image/*", sharingMimeType(listOf("image/jpeg", "image/png")))
        assertEquals("*/*", sharingMimeType(listOf("image/jpeg", "video/mp4")))
    }

    @Test
    fun `heif and raw format detection works accurately`() {
        assertTrue(isHeifFormat("image/heif"))
        assertTrue(isHeifFormat("image/heic"))
        assertFalse(isHeifFormat("image/jpeg"))

        assertTrue(isRawFormat("image/x-adobe-dng"))
        assertTrue(isRawFormat("image/x-canon-cr2"))
        assertTrue(isRawFormat("image/x-nikon-nef"))
        assertTrue(isRawFormat("image/x-sony-arw"))
        assertTrue(isRawFormat("image/octet-stream", "photo.dng"))
        assertTrue(isRawFormat("image/octet-stream", "photo.RAW"))
        assertFalse(isRawFormat("image/jpeg", "photo.jpg"))
    }
}
