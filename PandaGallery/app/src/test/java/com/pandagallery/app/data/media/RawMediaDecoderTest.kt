package com.pandagallery.app.data.media

import com.pandagallery.app.data.raw.RawMediaDecoder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawMediaDecoderTest {

    @Test
    fun isRawFormat_identifiesProfessionalRawExtensions() {
        assertTrue(RawMediaDecoder.isRaw("photo.dng"))
        assertTrue(RawMediaDecoder.isRaw("photo.DNG"))
        assertTrue(RawMediaDecoder.isRaw("canon_shot.CR2"))
        assertTrue(RawMediaDecoder.isRaw("nikon_shot.nef"))
        assertTrue(RawMediaDecoder.isRaw("sony_shot.arw"))

        assertFalse(RawMediaDecoder.isRaw("photo.jpg"))
        assertFalse(RawMediaDecoder.isRaw("photo.png"))
        assertFalse(RawMediaDecoder.isRaw("clip.mp4"))
    }

    @Test
    fun isNextGenFormat_identifiesHeicAndAvif() {
        assertTrue(RawMediaDecoder.isNextGen("apple_shot.HEIC"))
        assertTrue(RawMediaDecoder.isNextGen("burst.heif"))
        assertTrue(RawMediaDecoder.isNextGen("optimized.avif"))

        assertFalse(RawMediaDecoder.isNextGen("photo.jpg"))
        assertFalse(RawMediaDecoder.isNextGen("photo.dng"))
    }
}
