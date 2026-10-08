package com.pandagallery.app.data.gif

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, self-contained standard GIF89a encoder for Android.
 *
 * Capabilities:
 * - Netscape 2.0 application extension for seamless infinite looping in all gallery and social apps.
 * - Accurate per-frame delay timing in hundredths of a second.
 * - Median-cut color quantization with 256-color palette extraction.
 * - LZW compression for standard GIF compliance.
 * - Multi-frame animation streaming.
 */
class GifEncoder {

    private var outputStream: OutputStream? = null
    private var width: Int = 0
    private var height: Int = 0
    private var isFirstFrame: Boolean = true
    private var repeatCount: Int = 0 // 0 = infinite loop
    private var delayCentiseconds: Int = 10 // 10cs = 100ms = 10 fps default

    /**
     * Sets the delay between frames in milliseconds.
     */
    fun setDelayMs(delayMs: Int) {
        delayCentiseconds = max(2, delayMs / 10)
    }

    /**
     * Sets the repeat count (0 = infinite loop).
     */
    fun setRepeat(repeat: Int) {
        repeatCount = max(0, repeat)
    }

    /**
     * Initializes the GIF stream on the specified output stream.
     */
    fun start(os: OutputStream): Boolean {
        outputStream = os
        isFirstFrame = true
        return true
    }

    /**
     * Encodes and appends a Bitmap frame to the GIF stream.
     */
    fun addFrame(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return addFrame(pixels, w, h)
    }

    /**
     * Encodes and appends a raw ARGB pixel array frame to the GIF stream.
     * Useful for direct pixel buffers and test execution without Android Bitmap dependencies.
     */
    fun addFrame(pixels: IntArray, w: Int, h: Int): Boolean {
        val os = outputStream ?: return false

        if (isFirstFrame) {
            width = w
            height = h
            writeHeader(os)
            writeLogicalScreenDescriptor(os, w, h)
            writeNetscapeExtension(os)
            isFirstFrame = false
        }

        // 1. Quantize 24-bit ARGB pixels to 256-color palette + indexed pixels
        val (palette, indexedPixels) = quantizePixels(pixels, w, h)

        // 2. Write Graphic Control Extension (frame timing & disposal)
        writeGraphicControlExtension(os, delayCentiseconds)

        // 3. Write Image Descriptor & Local Color Table
        writeImageDescriptor(os, w, h)
        writeColorTable(os, palette)

        // 4. Write LZW Encoded Pixels
        writeLzwImageData(os, indexedPixels, 8)

        return true
    }

    /**
     * Finalizes the GIF stream by writing the GIF trailer byte (0x3B).
     */
    fun finish(): Boolean {
        val os = outputStream ?: return false
        try {
            os.write(0x3B) // GIF Trailer
            os.flush()
            return true
        } catch (_: Exception) {
            return false
        } finally {
            outputStream = null
        }
    }

    // --- Protocol Writing Helpers ---

    private fun writeHeader(os: OutputStream) {
        os.write("GIF89a".toByteArray(Charsets.US_ASCII))
    }

    private fun writeLogicalScreenDescriptor(os: OutputStream, w: Int, h: Int) {
        writeShort(os, w)
        writeShort(os, h)
        // Packed Fields: No Global Color Table, 8 bits color resolution
        os.write(0x70)
        os.write(0) // Background color index
        os.write(0) // Pixel aspect ratio
    }

    private fun writeNetscapeExtension(os: OutputStream) {
        os.write(0x21) // Extension introducer
        os.write(0xFF) // Application extension label
        os.write(11)   // Block size
        os.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        os.write(3)    // Sub-block size
        os.write(1)    // Sub-block ID
        writeShort(os, repeatCount) // Loop count (0 = infinite)
        os.write(0)    // Block terminator
    }

    private fun writeGraphicControlExtension(os: OutputStream, delayCs: Int) {
        os.write(0x21) // Extension introducer
        os.write(0xF9) // Graphic control label
        os.write(4)    // Block size
        // Packed fields: Disposal method 1 (Do not dispose / leave in place)
        os.write(0x04)
        writeShort(os, delayCs)
        os.write(0)    // Transparent color index (unused)
        os.write(0)    // Block terminator
    }

    private fun writeImageDescriptor(os: OutputStream, w: Int, h: Int) {
        os.write(0x2C) // Image separator
        writeShort(os, 0) // Left
        writeShort(os, 0) // Top
        writeShort(os, w) // Width
        writeShort(os, h) // Height
        // Packed fields: Local Color Table Present (0x80), Color table size = 256 (0x07) -> 0x87
        os.write(0x87)
    }

    private fun writeColorTable(os: OutputStream, palette: IntArray) {
        val entryCount = 256
        for (i in 0 until entryCount) {
            val color = if (i < palette.size) palette[i] else 0
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            os.write(r)
            os.write(g)
            os.write(b)
        }
    }

    private fun writeShort(os: OutputStream, value: Int) {
        os.write(value and 0xFF)
        os.write((value shr 8) and 0xFF)
    }

    // --- Color Quantization (Median Cut) ---

    private fun quantizePixels(pixels: IntArray, w: Int, h: Int): Pair<IntArray, ByteArray> {
        val total = w * h

        // Subsample for fast palette building (max 10,000 samples)
        val step = max(1, total / 10000)
        val sampleList = ArrayList<Int>(total / step + 1)
        for (i in 0 until total step step) {
            sampleList.add(pixels[i])
        }

        // Build 256-color palette via Median Cut
        val palette = medianCutPalette(sampleList, 256)

        // Map each pixel to nearest palette index
        val indexed = ByteArray(total)
        // Palette lookup cache for speed (5-5-5 RGB reduction)
        val cache = IntArray(32768) { -1 }

        for (i in 0 until total) {
            val c = pixels[i]
            val r5 = ((c shr 16) and 0xFF) shr 3
            val g5 = ((c shr 8) and 0xFF) shr 3
            val b5 = (c and 0xFF) shr 3
            val key = (r5 shl 10) or (g5 shl 5) or b5

            var bestIdx = cache[key]
            if (bestIdx == -1) {
                bestIdx = findNearestColor(c, palette)
                cache[key] = bestIdx
            }
            indexed[i] = bestIdx.toByte()
        }

        return Pair(palette, indexed)
    }

    private fun medianCutPalette(colors: List<Int>, maxColors: Int): IntArray {
        if (colors.isEmpty()) return IntArray(maxColors)

        class ColorBox(val colors: MutableList<Int>) {
            var minR = 255; var maxR = 0
            var minG = 255; var maxG = 0
            var minB = 255; var maxB = 0

            init {
                colors.forEach { c ->
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    if (r < minR) minR = r; if (r > maxR) maxR = r
                    if (g < minG) minG = g; if (g > maxG) maxG = g
                    if (b < minB) minB = b; if (b > maxB) maxB = b
                }
            }

            fun longestDimension(): Int {
                val dr = maxR - minR
                val dg = maxG - minG
                val db = maxB - minB
                return if (dr >= dg && dr >= db) 0 else if (dg >= dr && dg >= db) 1 else 2
            }

            fun averageColor(): Int {
                if (colors.isEmpty()) return 0
                var sumR = 0L; var sumG = 0L; var sumB = 0L
                colors.forEach { c ->
                    sumR += (c shr 16) and 0xFF
                    sumG += (c shr 8) and 0xFF
                    sumB += c and 0xFF
                }
                val n = colors.size.toLong()
                return ((sumR / n).toInt() shl 16) or ((sumG / n).toInt() shl 8) or (sumB / n).toInt()
            }
        }

        val boxes = ArrayList<ColorBox>()
        boxes.add(ColorBox(colors.toMutableList()))

        while (boxes.size < maxColors) {
            // Pick box with highest volume / color spread
            val splitCandidate = boxes.filter { it.colors.size >= 2 }.maxByOrNull { box ->
                val dr = box.maxR - box.minR
                val dg = box.maxG - box.minG
                val db = box.maxB - box.minB
                max(dr, max(dg, db))
            } ?: break

            boxes.remove(splitCandidate)
            val dim = splitCandidate.longestDimension()

            splitCandidate.colors.sortWith { c1, c2 ->
                val v1 = when (dim) {
                    0 -> (c1 shr 16) and 0xFF
                    1 -> (c1 shr 8) and 0xFF
                    else -> c1 and 0xFF
                }
                val v2 = when (dim) {
                    0 -> (c2 shr 16) and 0xFF
                    1 -> (c2 shr 8) and 0xFF
                    else -> c2 and 0xFF
                }
                v1.compareTo(v2)
            }

            val mid = splitCandidate.colors.size / 2
            val box1 = ColorBox(splitCandidate.colors.subList(0, mid).toMutableList())
            val box2 = ColorBox(splitCandidate.colors.subList(mid, splitCandidate.colors.size).toMutableList())
            boxes.add(box1)
            boxes.add(box2)
        }

        val palette = IntArray(maxColors)
        boxes.forEachIndexed { index, box ->
            if (index < maxColors) {
                palette[index] = box.averageColor()
            }
        }
        return palette
    }

    private fun findNearestColor(color: Int, palette: IntArray): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        var bestDist = Int.MAX_VALUE
        var bestIdx = 0

        for (i in palette.indices) {
            val pc = palette[i]
            val pr = (pc shr 16) and 0xFF
            val pg = (pc shr 8) and 0xFF
            val pb = pc and 0xFF

            // Weighted Euclidean distance (human eye is more sensitive to green)
            val dr = r - pr
            val dg = g - pg
            val db = b - pb
            val dist = dr * dr * 2 + dg * dg * 4 + db * db * 3
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
                if (dist == 0) break
            }
        }
        return bestIdx
    }

    // --- LZW Compression ---

    private fun writeLzwImageData(os: OutputStream, pixels: ByteArray, initCodeSize: Int) {
        os.write(initCodeSize)

        val clearCode = 1 shl initCodeSize
        val eoiCode = clearCode + 1
        var nextCode = eoiCode + 1
        var codeSize = initCodeSize + 1
        var codeMask = (1 shl codeSize) - 1

        val dict = HashMap<Int, Int>(5003)

        fun resetDict() {
            dict.clear()
            for (i in 0 until clearCode) {
                dict[i] = i
            }
            codeSize = initCodeSize + 1
            codeMask = (1 shl codeSize) - 1
            nextCode = eoiCode + 1
        }

        resetDict()

        // Bit packing accumulator
        var curAccum = 0
        var curBits = 0
        val packet = ByteArray(256)
        var packetLen = 0

        fun emitCode(code: Int) {
            curAccum = curAccum or (code shl curBits)
            curBits += codeSize

            while (curBits >= 8) {
                packet[packetLen++] = (curAccum and 0xFF).toByte()
                curAccum = curAccum shr 8
                curBits -= 8

                if (packetLen >= 254) {
                    os.write(packetLen)
                    os.write(packet, 0, packetLen)
                    packetLen = 0
                }
            }

            if (nextCode > codeMask && codeSize < 12) {
                codeSize++
                codeMask = (1 shl codeSize) - 1
            }
        }

        fun flushBits() {
            if (curBits > 0) {
                packet[packetLen++] = (curAccum and 0xFF).toByte()
                curAccum = 0
                curBits = 0
            }
            if (packetLen > 0) {
                os.write(packetLen)
                os.write(packet, 0, packetLen)
                packetLen = 0
            }
        }

        emitCode(clearCode)

        var prefix = (pixels[0].toInt() and 0xFF)

        for (i in 1 until pixels.size) {
            val suffix = (pixels[i].toInt() and 0xFF)
            val key = (prefix shl 8) or suffix

            val found = dict[key]
            if (found != null) {
                prefix = found
            } else {
                emitCode(prefix)
                if (nextCode < 4096) {
                    dict[key] = nextCode++
                } else {
                    emitCode(clearCode)
                    resetDict()
                }
                prefix = suffix
            }
        }

        emitCode(prefix)
        emitCode(eoiCode)
        flushBits()
        os.write(0) // Data sub-block terminator
    }
}
