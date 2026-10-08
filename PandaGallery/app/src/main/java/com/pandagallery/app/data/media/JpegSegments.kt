package com.pandagallery.app.data.media

/**
 * Just enough JPEG marker walking to read and add APPn metadata segments, with no Android
 * dependencies so it can be unit tested.
 */
internal object JpegSegments {

    private const val MARKER_PREFIX = 0xFF
    private const val SOI = 0xD8
    private const val APP1 = 0xE1
    private val EXIF_HEADER = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) // "Exif\0\0"
    private val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes.u8(0) == MARKER_PREFIX && bytes.u8(1) == SOI

    /**
     * The APP1 EXIF payload (starting at "Exif\0\0", then the TIFF header) — the form
     * `AvifWriter.addExifData` takes — or null when there is none.
     */
    fun exifPayload(jpeg: ByteArray): ByteArray? {
        val offsets = leadingAppSegments(jpeg) ?: return null
        for (position in offsets.dropLast(1)) {
            if (jpeg.u8(position + 1) == APP1 && jpeg.startsWith(EXIF_HEADER, position + 4)) {
                return jpeg.copyOfRange(position + 4, position + 2 + jpeg.u16(position + 2))
            }
        }
        return null
    }

    /**
     * [jpeg] with an XMP APP1 segment carrying [xmp] inserted after the existing APPn segments
     * (so EXIF stays first, where readers expect it). Null when [jpeg] is not a JPEG or the
     * packet is too large for one segment.
     */
    fun withXmp(jpeg: ByteArray, xmp: String): ByteArray? {
        val insertAt = leadingAppSegments(jpeg)?.last() ?: return null
        val payload = XMP_HEADER + xmp.toByteArray(Charsets.UTF_8)
        val length = payload.size + 2
        if (length > 0xFFFF) return null
        val segment = byteArrayOf(
            MARKER_PREFIX.toByte(),
            APP1.toByte(),
            (length shr 8).toByte(),
            length.toByte(),
        ) + payload
        return jpeg.copyOfRange(0, insertAt) + segment + jpeg.copyOfRange(insertAt, jpeg.size)
    }

    /**
     * Offsets of each APPn segment after SOI, ending with the offset just past the last one.
     * Null when [jpeg] is not a JPEG or a segment length runs off the end.
     */
    private fun leadingAppSegments(jpeg: ByteArray): List<Int>? {
        if (!isJpeg(jpeg)) return null
        val offsets = mutableListOf<Int>()
        var position = 2
        while (position + 4 <= jpeg.size && jpeg.u8(position) == MARKER_PREFIX && jpeg.u8(position + 1) in 0xE0..0xEF) {
            offsets += position
            val length = jpeg.u16(position + 2)
            if (length < 2 || position + 2 + length > jpeg.size) return null
            position += 2 + length
        }
        offsets += position
        return offsets
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
    private fun ByteArray.u16(index: Int): Int = (u8(index) shl 8) or u8(index + 1)
    private fun ByteArray.startsWith(prefix: ByteArray, at: Int): Boolean =
        at + prefix.size <= size && prefix.indices.all { this[at + it] == prefix[it] }
}
