package com.pandagallery.app.data.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AVIF outputs get their EXIF from a throwaway JPEG's APP1 segment, because ExifInterface
 * cannot save AVIF. These pin down the segment walking that extraction relies on.
 */
class JpegSegmentsTest {

    private val soi = bytes(0xFF, 0xD8)
    private val app0 = bytes(0xFF, 0xE0, 0x00, 0x04, 0x4A, 0x46)
    private val exifPayload = "Exif".toByteArray() + bytes(0x00, 0x00) + bytes(0x4D, 0x4D, 0x00, 0x2A)
    private val app1Exif = bytes(0xFF, 0xE1, 0x00, exifPayload.size + 2) + exifPayload
    private val scan = bytes(0xFF, 0xDA, 0x00, 0x02, 0xFF, 0xD9)

    @Test
    fun `exif payload is the APP1 body from the Exif header on`() {
        val jpeg = soi + app0 + app1Exif + scan
        assertArrayEquals(exifPayload, JpegSegments.exifPayload(jpeg))
    }

    @Test
    fun `no exif segment means no payload`() {
        assertNull(JpegSegments.exifPayload(soi + app0 + scan))
    }

    @Test
    fun `non jpeg input is rejected rather than misread`() {
        assertNull(JpegSegments.exifPayload(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A)))
        assertNull(JpegSegments.withXmp(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A), "<x/>"))
    }

    @Test
    fun `a segment length running past the end is rejected`() {
        val truncated = soi + bytes(0xFF, 0xE1, 0x7F, 0xFF, 0x00)
        assertNull(JpegSegments.exifPayload(truncated))
    }

    @Test
    fun `xmp goes after the existing APPn segments so EXIF stays first`() {
        val jpeg = soi + app1Exif + scan
        val result = JpegSegments.withXmp(jpeg, "<x/>")!!
        val header = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
        val xmpBody = header + "<x/>".toByteArray()
        val expected = soi + app1Exif + bytes(0xFF, 0xE1, 0x00, xmpBody.size + 2) + xmpBody + scan
        assertArrayEquals(expected, result)
        // And the EXIF is still found once the XMP is in.
        assertArrayEquals(exifPayload, JpegSegments.exifPayload(result))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
