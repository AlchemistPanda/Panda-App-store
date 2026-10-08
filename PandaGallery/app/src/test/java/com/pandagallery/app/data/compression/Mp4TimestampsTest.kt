package com.pandagallery.app.data.compression

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

class Mp4TimestampsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val mp4Epoch = 2_082_844_800L
    private val may2026 = 1_779_721_306_000L

    private fun box(type: String, body: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply {
            writeInt(8 + body.size)
            writeBytes(type)
            write(body)
        }
    }.toByteArray()

    /** A FullBox whose creation/modification fields hold [stamp], followed by filler. */
    private fun timedBox(type: String, version: Int, stamp: Long): ByteArray = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply {
            writeByte(version)
            write(ByteArray(3))
            if (version == 1) {
                writeLong(stamp); writeLong(stamp)
            } else {
                writeInt(stamp.toInt()); writeInt(stamp.toInt())
            }
            write(ByteArray(12))
        }
    }.toByteArray().let { box(type, it) }

    private fun readStamp(file: File, bodyOffset: Long, version: Int): Pair<Long, Long> =
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(bodyOffset + 4)
            if (version == 1) raf.readLong() to raf.readLong()
            else (raf.readInt().toLong() and 0xFFFFFFFFL) to (raf.readInt().toLong() and 0xFFFFFFFFL)
        }

    @Test
    fun `patches movie, track and media headers nested in moov after mdat`() {
        val now = 1_790_000_000L + mp4Epoch
        val ftyp = box("ftyp", "isom0000".toByteArray())
        val mdat = box("mdat", ByteArray(64) { 7 })
        val mvhd = timedBox("mvhd", 0, now)
        val mdhd = timedBox("mdhd", 1, now)
        val tkhd = timedBox("tkhd", 0, now)
        val trak = box("trak", tkhd + box("mdia", mdhd))
        val moov = box("moov", mvhd + trak)
        val file = tmp.newFile("clip.mp4").apply { writeBytes(ftyp + mdat + moov) }

        val patched = Mp4Timestamps.setCreationTime(file, may2026)

        assertEquals(3, patched)
        val expected = may2026 / 1000 + mp4Epoch
        val mvhdBody = (ftyp.size + mdat.size + 8 + 8).toLong()
        assertEquals(expected to expected, readStamp(file, mvhdBody, 0))
        val tkhdBody = mvhdBody - 8 + mvhd.size + 8 + 8
        assertEquals(expected to expected, readStamp(file, tkhdBody, 0))
        val mdhdBody = tkhdBody - 8 + tkhd.size + 8 + 8
        assertEquals(expected to expected, readStamp(file, mdhdBody, 1))
        // Nothing outside the timestamp fields moved.
        assertEquals(ftyp.size + mdat.size + moov.size.toLong(), file.length())
        assertEquals(7, file.readBytes()[ftyp.size + 8].toInt())
    }

    @Test
    fun `leaves a file without moov untouched`() {
        val bytes = box("ftyp", "isom0000".toByteArray()) + box("mdat", ByteArray(16))
        val file = tmp.newFile("broken.mp4").apply { writeBytes(bytes) }

        assertEquals(0, Mp4Timestamps.setCreationTime(file, may2026))
        assertEquals(bytes.toList(), file.readBytes().toList())
    }
}
