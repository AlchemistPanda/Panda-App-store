package com.pandagallery.app.data.compression

import java.io.File
import java.io.RandomAccessFile

/**
 * Rewrites the creation/modification times an MP4 carries in its `mvhd`, `tkhd` and `mdhd` boxes.
 *
 * MediaProvider derives a video's DATE_TAKEN from the `mvhd` creation time every time it scans
 * the file, and the muxer stamps that with the moment the export finished. So a compressed copy
 * of a May clip scanned in as "today" no matter what ContentValues were inserted with it, and
 * sorted to the top of the timeline. Writing the original capture time into the file itself is
 * the only thing a later rescan cannot undo.
 *
 * The fields are fixed-width and patched in place: no box changes size, so nothing else in the
 * file has to move.
 */
object Mp4Timestamps {

    /** Seconds between the MP4 epoch (1904-01-01 UTC) and the Unix epoch. */
    private const val MP4_EPOCH_OFFSET_SECONDS = 2_082_844_800L

    private val CONTAINERS = setOf("moov", "trak", "mdia")
    private val TIMESTAMPED = setOf("mvhd", "tkhd", "mdhd")

    /** @return how many boxes were patched; 0 means the file carried no `moov` we could find. */
    fun setCreationTime(file: File, epochMillis: Long): Int {
        if (epochMillis <= 0L) return 0
        val mp4Seconds = epochMillis / 1_000L + MP4_EPOCH_OFFSET_SECONDS
        return RandomAccessFile(file, "rw").use { raf -> patchBoxes(raf, 0L, raf.length(), mp4Seconds) }
    }

    private fun patchBoxes(raf: RandomAccessFile, start: Long, end: Long, mp4Seconds: Long): Int {
        var patched = 0
        var position = start
        while (position + 8 <= end) {
            raf.seek(position)
            var size = raf.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also(raf::readFully).toString(Charsets.US_ASCII)
            var headerSize = 8L
            when (size) {
                1L -> {
                    size = raf.readLong()
                    headerSize = 16L
                }
                0L -> size = end - position
            }
            if (size < headerSize || position + size > end) break

            val bodyStart = position + headerSize
            when (type) {
                in CONTAINERS -> patched += patchBoxes(raf, bodyStart, position + size, mp4Seconds)
                in TIMESTAMPED -> if (patchFullBox(raf, bodyStart, position + size, mp4Seconds)) patched++
            }
            position += size
        }
        return patched
    }

    /**
     * All three boxes open with the same FullBox header followed by creation and modification
     * times: 32-bit each in version 0, 64-bit in version 1.
     */
    private fun patchFullBox(raf: RandomAccessFile, bodyStart: Long, boxEnd: Long, mp4Seconds: Long): Boolean {
        raf.seek(bodyStart)
        return when (raf.readUnsignedByte()) {
            0 -> {
                if (bodyStart + 12 > boxEnd || mp4Seconds > 0xFFFFFFFFL) return false
                raf.seek(bodyStart + 4)
                raf.writeInt(mp4Seconds.toInt())
                raf.writeInt(mp4Seconds.toInt())
                true
            }
            1 -> {
                if (bodyStart + 20 > boxEnd) return false
                raf.seek(bodyStart + 4)
                raf.writeLong(mp4Seconds)
                raf.writeLong(mp4Seconds)
                true
            }
            else -> false
        }
    }
}
