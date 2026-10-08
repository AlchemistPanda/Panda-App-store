package com.pandagallery.app.data.editing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Samsung One UI 6.1 / One UI 7 Document Scan & Perspective Dewarp Engine.
 *
 * Implements:
 * 1. 4-Corner homography perspective rectification via [Matrix.setPolyToPoly].
 * 2. Adaptive paper whitening and ink contrast enhancement ([DocumentEnhancementMode.CLEAN]).
 * 3. Photocopier-style high-contrast binarization ([DocumentEnhancementMode.BLACK_AND_WHITE]).
 * 4. Gradient-ray edge auto-detection for document corners.
 */
object DocumentDewarper {

    /**
     * Calculates the rectified target width and height based on Euclidean distances
     * between the 4 corner pins.
     */
    fun calculateTargetDimensions(
        corners: DocumentCorners,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Pair<Int, Int> {
        val w = sourceWidth.toFloat()
        val h = sourceHeight.toFloat()

        val p0x = corners.topLeft.x * w
        val p0y = corners.topLeft.y * h

        val p1x = corners.topRight.x * w
        val p1y = corners.topRight.y * h

        val p2x = corners.bottomRight.x * w
        val p2y = corners.bottomRight.y * h

        val p3x = corners.bottomLeft.x * w
        val p3y = corners.bottomLeft.y * h

        val topWidth = hypot(p1x - p0x, p1y - p0y)
        val bottomWidth = hypot(p2x - p3x, p2y - p3y)
        val leftHeight = hypot(p3x - p0x, p3y - p0y)
        val rightHeight = hypot(p2x - p1x, p2y - p1y)

        val targetW = max(topWidth, bottomWidth).roundToInt().coerceIn(64, 8192)
        val targetH = max(leftHeight, rightHeight).roundToInt().coerceIn(64, 8192)

        return Pair(targetW, targetH)
    }

    /**
     * Computes the 8-element float array for source quadrilateral and destination rectangle.
     */
    fun computePolyPoints(
        corners: DocumentCorners,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Pair<FloatArray, FloatArray> {
        val w = sourceWidth.toFloat()
        val h = sourceHeight.toFloat()

        val src = floatArrayOf(
            corners.topLeft.x * w, corners.topLeft.y * h,
            corners.topRight.x * w, corners.topRight.y * h,
            corners.bottomRight.x * w, corners.bottomRight.y * h,
            corners.bottomLeft.x * w, corners.bottomLeft.y * h,
        )

        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat(),
        )

        return Pair(src, dst)
    }

    /**
     * Rectifies a perspective-skewed document into a flat rectangular bitmap
     * and optionally applies document enhancement.
     */
    fun dewarp(
        source: Bitmap,
        corners: DocumentCorners,
        mode: DocumentEnhancementMode = DocumentEnhancementMode.ORIGINAL,
    ): Bitmap {
        val (targetW, targetH) = calculateTargetDimensions(corners, source.width, source.height)
        val (src, dst) = computePolyPoints(corners, source.width, source.height, targetW, targetH)

        val matrix = Matrix().apply {
            setPolyToPoly(src, 0, dst, 0, 4)
        }

        val targetBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.concat(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return when (mode) {
            DocumentEnhancementMode.ORIGINAL -> targetBitmap
            DocumentEnhancementMode.CLEAN -> {
                val enhanced = applyCleanFilter(targetBitmap)
                if (enhanced !== targetBitmap) targetBitmap.recycle()
                enhanced
            }
            DocumentEnhancementMode.BLACK_AND_WHITE -> {
                val bw = applyBlackAndWhiteFilter(targetBitmap)
                if (bw !== targetBitmap) targetBitmap.recycle()
                bw
            }
        }
    }

    /**
     * Adaptive background normalization:
     * Whitens shadowed or yellowish paper while darkening ink for maximum readability.
     */
    fun applyCleanFilter(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Compute coarse 16x16 grid of local paper luminance
        val gridCols = 16
        val gridRows = 16
        val localBgLuminance = Array(gridRows) { FloatArray(gridCols) }

        val blockW = max(1, width / gridCols)
        val blockH = max(1, height / gridRows)

        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                val startX = gx * blockW
                val startY = gy * blockH
                val endX = min(width, startX + blockW)
                val endY = min(height, startY + blockH)

                var maxLum = 0f
                var sumLum = 0f
                var count = 0

                var y = startY
                while (y < endY) {
                    var x = startX
                    while (x < endX) {
                        val pixel = pixels[y * width + x]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val lum = calculateLuminance(r, g, b)
                        if (lum > maxLum) maxLum = lum
                        sumLum += lum
                        count++
                        x += 4 // Step 4 for speed
                    }
                    y += 4
                }

                // Use the 85th percentile proxy (blend between mean and max)
                val avgLum = if (count > 0) sumLum / count else 200f
                localBgLuminance[gy][gx] = max(140f, (avgLum * 0.35f + maxLum * 0.65f))
            }
        }

        // Process pixels using interpolated local paper background
        val outputPixels = IntArray(pixels.size)
        for (y in 0 until height) {
            val gyFraction = (y.toFloat() / height) * (gridRows - 1)
            val gy0 = gyFraction.toInt().coerceIn(0, gridRows - 1)
            val gy1 = (gy0 + 1).coerceIn(0, gridRows - 1)
            val fy = gyFraction - gy0

            for (x in 0 until width) {
                val gxFraction = (x.toFloat() / width) * (gridCols - 1)
                val gx0 = gxFraction.toInt().coerceIn(0, gridCols - 1)
                val gx1 = (gx0 + 1).coerceIn(0, gridCols - 1)
                val fx = gxFraction - gx0

                val bgTop = localBgLuminance[gy0][gx0] * (1f - fx) + localBgLuminance[gy0][gx1] * fx
                val bgBottom = localBgLuminance[gy1][gx0] * (1f - fx) + localBgLuminance[gy1][gx1] * fx
                val localBg = bgTop * (1f - fy) + bgBottom * fy

                val pixel = pixels[y * width + x]
                val a = (pixel shr 24) and 0xFF
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val lum = calculateLuminance(r, g, b)
                val paperThreshold = localBg * 0.85f

                val (outR, outG, outB) = if (lum >= paperThreshold) {
                    // Paper background: gently lift towards pure white
                    val t = ((lum - paperThreshold) / max(1f, localBg - paperThreshold)).coerceIn(0f, 1f)
                    val factor = 1f + t * 0.45f
                    Triple(
                        min(255, (r * factor).toInt()),
                        min(255, (g * factor).toInt()),
                        min(255, (b * factor).toInt()),
                    )
                } else {
                    // Text/Ink foreground: boost contrast by darkening
                    val contrastRatio = (lum / paperThreshold).coerceIn(0f, 1f).pow(1.35f)
                    Triple(
                        (r * contrastRatio).toInt().coerceIn(0, 255),
                        (g * contrastRatio).toInt().coerceIn(0, 255),
                        (b * contrastRatio).toInt().coerceIn(0, 255),
                    )
                }

                outputPixels[y * width + x] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Photocopier binarization: Crisp black ink on clean white background.
     */
    fun applyBlackAndWhiteFilter(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gridCols = 16
        val gridRows = 16
        val localThreshold = Array(gridRows) { FloatArray(gridCols) }

        val blockW = max(1, width / gridCols)
        val blockH = max(1, height / gridRows)

        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                val startX = gx * blockW
                val startY = gy * blockH
                val endX = min(width, startX + blockW)
                val endY = min(height, startY + blockH)

                var sumLum = 0f
                var count = 0

                var y = startY
                while (y < endY) {
                    var x = startX
                    while (x < endX) {
                        val pixel = pixels[y * width + x]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        sumLum += calculateLuminance(r, g, b)
                        count++
                        x += 4
                    }
                    y += 4
                }

                val avgLum = if (count > 0) sumLum / count else 180f
                localThreshold[gy][gx] = avgLum * 0.88f
            }
        }

        val outputPixels = IntArray(pixels.size)
        for (y in 0 until height) {
            val gyFraction = (y.toFloat() / height) * (gridRows - 1)
            val gy0 = gyFraction.toInt().coerceIn(0, gridRows - 1)
            val gy1 = (gy0 + 1).coerceIn(0, gridRows - 1)
            val fy = gyFraction - gy0

            for (x in 0 until width) {
                val gxFraction = (x.toFloat() / width) * (gridCols - 1)
                val gx0 = gxFraction.toInt().coerceIn(0, gridCols - 1)
                val gx1 = (gx0 + 1).coerceIn(0, gridCols - 1)
                val fx = gxFraction - gx0

                val thTop = localThreshold[gy0][gx0] * (1f - fx) + localThreshold[gy0][gx1] * fx
                val thBottom = localThreshold[gy1][gx0] * (1f - fx) + localThreshold[gy1][gx1] * fx
                val threshold = thTop * (1f - fy) + thBottom * fy

                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = calculateLuminance(r, g, b)

                val bwVal = if (lum >= threshold) 0xFF else 0x00
                outputPixels[y * width + x] = (0xFF shl 24) or (bwVal shl 16) or (bwVal shl 8) or bwVal
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Standard Rec. 601 perceived luminance.
     */
    fun calculateLuminance(r: Int, g: Int, b: Int): Float {
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /**
     * Automated document boundary detection.
     * Scans gradient rays from image center towards corners to find paper boundary contrast.
     */
    fun detectDocumentCorners(bitmap: Bitmap): DocumentCorners {
        val sampleW = 160
        val sampleH = 120
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)

        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaled !== bitmap) scaled.recycle()

        val lumGrid = Array(sampleH) { y ->
            FloatArray(sampleW) { x ->
                val p = pixels[y * sampleW + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                calculateLuminance(r, g, b)
            }
        }

        return detectCornersFromLuminanceGrid(lumGrid, sampleW, sampleH)
    }

    /**
     * Core ray-cast edge search on a 2D luminance grid. Exposed for deterministic unit testing.
     */
    fun detectCornersFromLuminanceGrid(
        lumGrid: Array<FloatArray>,
        width: Int,
        height: Int,
    ): DocumentCorners {
        val centerX = width * 0.5f
        val centerY = height * 0.5f

        // Search rays toward each of the 4 corners
        val tl = findEdgeAlongRay(lumGrid, width, height, centerX, centerY, width * 0.05f, height * 0.05f)
            ?: NormalizedPoint(0.08f, 0.08f)

        val tr = findEdgeAlongRay(lumGrid, width, height, centerX, centerY, width * 0.95f, height * 0.05f)
            ?: NormalizedPoint(0.92f, 0.08f)

        val br = findEdgeAlongRay(lumGrid, width, height, centerX, centerY, width * 0.95f, height * 0.95f)
            ?: NormalizedPoint(0.92f, 0.92f)

        val bl = findEdgeAlongRay(lumGrid, width, height, centerX, centerY, width * 0.05f, height * 0.95f)
            ?: NormalizedPoint(0.08f, 0.92f)

        return DocumentCorners(
            topLeft = tl,
            topRight = tr,
            bottomRight = br,
            bottomLeft = bl,
        )
    }

    private fun findEdgeAlongRay(
        lumGrid: Array<FloatArray>,
        width: Int,
        height: Int,
        startX: Float,
        startY: Float,
        targetX: Float,
        targetY: Float,
    ): NormalizedPoint? {
        val steps = 60
        var prevLum = lumGrid[startY.toInt().coerceIn(0, height - 1)][startX.toInt().coerceIn(0, width - 1)]
        var maxGradient = 0f
        var bestNormPoint: NormalizedPoint? = null

        // Scan from 25% along the ray out to 95%
        for (i in (steps * 0.25f).toInt()..steps) {
            val t = i.toFloat() / steps
            val curX = (startX + (targetX - startX) * t).toInt().coerceIn(0, width - 1)
            val curY = (startY + (targetY - startY) * t).toInt().coerceIn(0, height - 1)

            val curLum = lumGrid[curY][curX]
            val grad = kotlin.math.abs(curLum - prevLum)

            if (grad > maxGradient && grad > 25f) {
                maxGradient = grad
                bestNormPoint = NormalizedPoint(
                    curX.toFloat() / width,
                    curY.toFloat() / height,
                )
            }
            prevLum = curLum
        }

        return bestNormPoint
    }
}
