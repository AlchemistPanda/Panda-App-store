package com.pandagallery.app.data.gif

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class GifEncoderTest {

    @Test
    fun encoderProducesValidGif89aHeaderAndTrailer() {
        val encoder = GifEncoder()
        val stream = ByteArrayOutputStream()

        encoder.setDelayMs(100)
        encoder.setRepeat(0) // Infinite loop
        assertTrue("Encoder should start", encoder.start(stream))

        // Create a 16x16 test frame with red pixels: 0xFFFF0000
        val redPixels = IntArray(16 * 16) { 0xFFFF0000.toInt() }

        assertTrue("Frame should encode", encoder.addFrame(redPixels, 16, 16))
        assertTrue("Encoder should finish", encoder.finish())

        val bytes = stream.toByteArray()
        assertTrue("GIF should have content", bytes.size > 20)

        // Verify "GIF89a" Magic Header
        val header = String(bytes.copyOfRange(0, 6), Charsets.US_ASCII)
        assertEquals("GIF89a", header)

        // Verify Netscape 2.0 Loop Extension
        val bytesString = String(bytes, Charsets.ISO_8859_1)
        assertTrue("Should contain NETSCAPE2.0 loop marker", bytesString.contains("NETSCAPE2.0"))

        // Verify 0x3B Trailer byte at end
        assertEquals(0x3B.toByte(), bytes.last())
    }

    @Test
    fun multiFrameGifEncodesSuccessfully() {
        val encoder = GifEncoder()
        val stream = ByteArrayOutputStream()

        encoder.setDelayMs(50)
        encoder.setRepeat(0)
        encoder.start(stream)

        val frame1 = IntArray(10 * 10) { 0xFF0000FF.toInt() } // Blue
        val frame2 = IntArray(10 * 10) { 0xFF00FF00.toInt() } // Green
        val frame3 = IntArray(10 * 10) { 0xFFFFFF00.toInt() } // Yellow

        encoder.addFrame(frame1, 10, 10)
        encoder.addFrame(frame2, 10, 10)
        encoder.addFrame(frame3, 10, 10)
        encoder.finish()

        val bytes = stream.toByteArray()
        assertTrue("Multi-frame GIF should have non-trivial size", bytes.size > 100)
        assertEquals(0x3B.toByte(), bytes.last())
    }
}
