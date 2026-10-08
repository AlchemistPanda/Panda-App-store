package com.pandagallery.app.data.compression

import com.pandagallery.app.data.local.entity.CompressionTaskEntity
import com.pandagallery.app.domain.model.ImageFormat
import com.pandagallery.app.domain.model.UserPreferences
import com.pandagallery.app.domain.model.VideoCodec
import com.pandagallery.app.domain.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionTaskSnapshotTest {
    private fun task(
        imageFormat: String? = null,
        imageQuality: Int? = null,
        videoResolution: String? = null,
        videoCodec: String? = null,
    ) = CompressionTaskEntity(
        id = "t",
        operationId = "op",
        sourceMediaId = 1L,
        sourceUri = "content://media/external/images/media/1",
        displayName = "a.jpg",
        mimeType = "image/jpeg",
        originalBytes = 1_000L,
        status = "QUEUED",
        createdAt = 0L,
        imageFormat = imageFormat,
        imageQuality = imageQuality,
        videoResolution = videoResolution,
        videoCodec = videoCodec,
    )

    @Test
    fun `snapshotted settings win over preferences changed after enqueue`() {
        val changedSince = UserPreferences(
            imageFormat = ImageFormat.JPEG,
            imageQuality = 30,
            videoResolution = VideoResolution.P480,
            videoCodec = VideoCodec.H264,
        )
        val effective = task("WEBP", 100, "ORIGINAL", "H265").encodeSettings(changedSince)

        assertEquals(ImageFormat.WEBP, effective.imageFormat)
        assertEquals(100, effective.imageQuality)
        assertEquals(VideoResolution.ORIGINAL, effective.videoResolution)
        assertEquals(VideoCodec.H265, effective.videoCodec)
        assertTrue(effective.isLosslessImageEncode())
    }

    @Test
    fun `rows from before the snapshot columns fall back to current preferences`() {
        val current = UserPreferences(imageFormat = ImageFormat.AVIF, imageQuality = 55)
        val effective = task().encodeSettings(current)

        assertEquals(ImageFormat.AVIF, effective.imageFormat)
        assertEquals(55, effective.imageQuality)
        assertFalse(effective.isLosslessImageEncode())
    }

    @Test
    fun `unknown enum names fall back instead of crashing the task`() {
        val current = UserPreferences(imageFormat = ImageFormat.JPEG)
        assertEquals(ImageFormat.JPEG, task(imageFormat = "HEIC_FUTURE").encodeSettings(current).imageFormat)
    }

    private fun webpHeader(chunk: String, flags: Int): ByteArray {
        val bytes = ByteArray(21)
        "RIFF".forEachIndexed { i, c -> bytes[i] = c.code.toByte() }
        "WEBP".forEachIndexed { i, c -> bytes[8 + i] = c.code.toByte() }
        chunk.forEachIndexed { i, c -> bytes[12 + i] = c.code.toByte() }
        bytes[20] = flags.toByte()
        return bytes
    }

    @Test
    fun `animated webp is detected from the VP8X animation flag`() {
        assertTrue(isAnimatedWebpHeader(webpHeader("VP8X", 0x02)))
        assertTrue(isAnimatedWebpHeader(webpHeader("VP8X", 0x12)))
    }

    @Test
    fun `still webp is not treated as animated`() {
        assertFalse(isAnimatedWebpHeader(webpHeader("VP8X", 0x10))) // alpha only
        assertFalse(isAnimatedWebpHeader(webpHeader("VP8 ", 0x02))) // simple lossy, no flags byte
        assertFalse(isAnimatedWebpHeader(webpHeader("VP8L", 0x02))) // simple lossless
        assertFalse(isAnimatedWebpHeader(ByteArray(8)))
    }
}
