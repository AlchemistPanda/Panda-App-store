package com.pandagallery.app.data.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MotionPhotoHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun findMp4StartIndex_returnsCorrectOffset_whenFtypPresent() {
        val jpegHeader = ByteArray(2000) { 0xFF.toByte() }
        val mp4Size = byteArrayOf(0x00, 0x00, 0x04, 0x00) // 1024 bytes
        val ftyp = byteArrayOf(0x66, 0x74, 0x79, 0x70) // "ftyp"
        val mp4Payload = ByteArray(1020) { 0xAA.toByte() }

        val syntheticMotionPhoto = jpegHeader + mp4Size + ftyp + mp4Payload
        val expectedOffset = jpegHeader.size

        val foundOffset = MotionPhotoHelper.findMp4StartIndex(syntheticMotionPhoto)
        assertEquals(expectedOffset, foundOffset)
    }

    @Test
    fun findMp4StartIndex_returnsNegativeOne_whenNoFtypPresent() {
        val regularJpeg = ByteArray(3000) { 0xEE.toByte() }
        val foundOffset = MotionPhotoHelper.findMp4StartIndex(regularJpeg)
        assertEquals(-1, foundOffset)
    }

    @Test
    fun findMp4StartIndex_returnsNegativeOne_whenTooSmall() {
        val smallFile = ByteArray(500) { 0x66.toByte() }
        val foundOffset = MotionPhotoHelper.findMp4StartIndex(smallFile)
        assertEquals(-1, foundOffset)
    }

    @Test
    fun appendMotionVideo_writesXmpDescriptorAndAppendsClip() = runBlocking {
        // SOI, a JFIF APP0, then start-of-scan and EOI: the shape Bitmap.compress writes.
        val app0 = byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x4A, 0x46)
        val scan = byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02, 0xFF.toByte(), 0xD9.toByte())
        val still = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + app0 + scan
        val targetFile = tempFolder.newFile("test_still.jpg")
        targetFile.writeBytes(still)

        val videoBytes = byteArrayOf(10, 20, 30, 40)
        val success = MotionPhotoHelper.appendMotionVideo(targetFile, videoBytes)

        assertTrue(success)
        val resultBytes = targetFile.readBytes()
        // The clip sits at the very end, so lengths counted back from the end point at it.
        assertArrayEquals(videoBytes, resultBytes.copyOfRange(resultBytes.size - videoBytes.size, resultBytes.size))
        // The still keeps its own segments, with the XMP APP1 inserted after APP0.
        assertArrayEquals(still.copyOfRange(0, 8), resultBytes.copyOfRange(0, 8))
        assertEquals(0xE1, resultBytes[9].toInt() and 0xFF)
        val text = String(resultBytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("GCamera:MotionPhoto=\"1\""))
        assertTrue(text.contains("GCamera:MicroVideoOffset=\"4\""))
        assertTrue(text.contains("Item:Semantic=\"MotionPhoto\" Item:Length=\"4\""))
    }

    @Test
    fun appendMotionVideo_refusesNonJpeg_andLeavesFileUntouched() = runBlocking {
        val targetFile = tempFolder.newFile("not_a_jpeg.webp")
        targetFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        assertFalse(MotionPhotoHelper.appendMotionVideo(targetFile, byteArrayOf(10, 20)))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), targetFile.readBytes())
    }

    @Test
    fun findMp4StartIndex_ignoresFtypAtFileStart_forPlainHeic() {
        // A plain HEIC opens with its own ftyp box; that is the photo, not an appended clip.
        val heic = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70) + ByteArray(4000)
        assertEquals(-1, MotionPhotoHelper.findMp4StartIndex(heic))
    }

    @Test
    fun appendMotionVideo_returnsFalse_forNonExistentFile() = runBlocking {
        val missingFile = File(tempFolder.root, "non_existent.jpg")
        val success = MotionPhotoHelper.appendMotionVideo(missingFile, byteArrayOf(1, 2))
        assertFalse(success)
    }
}
