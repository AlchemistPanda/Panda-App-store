package com.pandagallery.app.data.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.LinkedList
import java.util.Queue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Result representing an extracted subject cutout from a photo.
 */
data class SubjectCutoutResult(
    val cutoutBitmap: Bitmap,
    val sourceBounds: RectF, // Normalized 0..1 bounding box in original image
    val imageWidth: Int,
    val imageHeight: Int,
)

/**
 * On-device Samsung Image Clipper engine.
 *
 * Uses Google ML Kit Neural Segmentation for human subjects/portraits, with a smart
 * edge-aware color-saliency contour flood fallback for pets, objects, and products.
 */
object SubjectSegmentationHelper {

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }

    /**
     * Extracts a cutout of the subject tapped at normalized coordinates ([touchXFraction], [touchYFraction]).
     * Returns null if no coherent subject or boundary could be identified at the touch location.
     */
    suspend fun extractSubjectCutout(
        sourceBitmap: Bitmap,
        touchXFraction: Float,
        touchYFraction: Float,
    ): SubjectCutoutResult? = withContext(Dispatchers.Default) {
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        if (width <= 0 || height <= 0) return@withContext null

        val touchPxX = (touchXFraction * width).toInt().coerceIn(0, width - 1)
        val touchPxY = (touchYFraction * height).toInt().coerceIn(0, height - 1)

        // 1. Try ML Kit Neural Segmenter first
        val mlKitResult = runMlKitSegmentation(sourceBitmap, touchPxX, touchPxY)
        if (mlKitResult != null) return@withContext mlKitResult

        // 2. Fallback to smart edge-aware contour flood (for pets, food, objects)
        runSaliencyFloodCutout(sourceBitmap, touchPxX, touchPxY)
    }

    private fun runMlKitSegmentation(
        sourceBitmap: Bitmap,
        touchX: Int,
        touchY: Int,
    ): SubjectCutoutResult? {
        return runCatching {
            val inputImage = InputImage.fromBitmap(sourceBitmap, 0)
            val mask = Tasks.await(segmenter.process(inputImage))
            val maskWidth = mask.width
            val maskHeight = mask.height
            val floatBuffer: FloatBuffer = mask.buffer.asFloatBuffer()

            // Scale touch coordinates to mask resolution if different
            val maskTouchX = ((touchX.toFloat() / sourceBitmap.width) * maskWidth).toInt().coerceIn(0, maskWidth - 1)
            val maskTouchY = ((touchY.toFloat() / sourceBitmap.height) * maskHeight).toInt().coerceIn(0, maskHeight - 1)

            val touchConfidence = floatBuffer.get(maskTouchY * maskWidth + maskTouchX)
            // If the touch point has low confidence of being a person, let fallback handle it
            if (touchConfidence < 0.45f) {
                return null
            }

            // Find connected component of the subject around the touch point
            var minX = maskTouchX
            var maxX = maskTouchX
            var minY = maskTouchY
            var maxY = maskTouchY

            val visited = BooleanArray(maskWidth * maskHeight)
            val queue: Queue<Int> = LinkedList()
            val startIdx = maskTouchY * maskWidth + maskTouchX
            queue.add(startIdx)
            visited[startIdx] = true

            var pixelCount = 0
            while (!queue.isEmpty()) {
                val idx = queue.poll() ?: break
                val px = idx % maskWidth
                val py = idx / maskWidth
                pixelCount++

                minX = min(minX, px)
                maxX = max(maxX, px)
                minY = min(minY, py)
                maxY = max(maxY, py)

                val neighbors = arrayOf(
                    if (px > 0) idx - 1 else -1,
                    if (px < maskWidth - 1) idx + 1 else -1,
                    if (py > 0) idx - maskWidth else -1,
                    if (py < maskHeight - 1) idx + maskWidth else -1,
                )

                for (nIdx in neighbors) {
                    if (nIdx >= 0 && !visited[nIdx]) {
                        visited[nIdx] = true
                        val conf = floatBuffer.get(nIdx)
                        if (conf >= 0.50f) {
                            queue.add(nIdx)
                        }
                    }
                }
            }

            if (pixelCount < 100) return null

            // Map mask bounds to source image coords
            val scaleX = sourceBitmap.width.toFloat() / maskWidth
            val scaleY = sourceBitmap.height.toFloat() / maskHeight

            val srcMinX = (minX * scaleX).toInt().coerceIn(0, sourceBitmap.width - 1)
            val srcMaxX = ((maxX + 1) * scaleX).toInt().coerceIn(0, sourceBitmap.width)
            val srcMinY = (minY * scaleY).toInt().coerceIn(0, sourceBitmap.height - 1)
            val srcMaxY = ((maxY + 1) * scaleY).toInt().coerceIn(0, sourceBitmap.height)

            val cropW = srcMaxX - srcMinX
            val cropH = srcMaxY - srcMinY
            if (cropW <= 0 || cropH <= 0) return null

            val cutout = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            val srcPixels = IntArray(cropW)
            val outPixels = IntArray(cropW)

            for (y in 0 until cropH) {
                val origY = srcMinY + y
                sourceBitmap.getPixels(srcPixels, 0, cropW, srcMinX, origY, cropW, 1)

                val maskY = (origY / scaleY).toInt().coerceIn(0, maskHeight - 1)
                for (x in 0 until cropW) {
                    val origX = srcMinX + x
                    val maskX = (origX / scaleX).toInt().coerceIn(0, maskWidth - 1)
                    val maskIdx = maskY * maskWidth + maskX
                    val conf = floatBuffer.get(maskIdx)

                    if (visited[maskIdx] && conf >= 0.40f) {
                        // Smooth alpha edge transition
                        val alpha = if (conf >= 0.70f) 255 else ((conf - 0.40f) / 0.30f * 255).toInt()
                        val color = srcPixels[x]
                        outPixels[x] = (alpha shl 24) or (color and 0x00FFFFFF)
                    } else {
                        outPixels[x] = 0 // Transparent background
                    }
                }
                cutout.setPixels(outPixels, 0, cropW, 0, y, cropW, 1)
            }

            SubjectCutoutResult(
                cutoutBitmap = cutout,
                sourceBounds = RectF(
                    srcMinX.toFloat() / sourceBitmap.width,
                    srcMinY.toFloat() / sourceBitmap.height,
                    srcMaxX.toFloat() / sourceBitmap.width,
                    srcMaxY.toFloat() / sourceBitmap.height,
                ),
                imageWidth = sourceBitmap.width,
                imageHeight = sourceBitmap.height,
            )
        }.getOrNull()
    }

    /**
     * Fallback for pets, objects, and food: color and gradient-aware flood fill around touch point.
     */
    private fun runSaliencyFloodCutout(
        sourceBitmap: Bitmap,
        touchX: Int,
        touchY: Int,
    ): SubjectCutoutResult? {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        // Downsample for rapid processing if large
        val maxDim = 600
        val scale = if (max(width, height) > maxDim) {
            max(width, height).toFloat() / maxDim
        } else 1.0f

        val procW = (width / scale).toInt().coerceAtLeast(1)
        val procH = (height / scale).toInt().coerceAtLeast(1)
        val procBitmap = Bitmap.createScaledBitmap(sourceBitmap, procW, procH, true)

        val seedX = (touchX / scale).toInt().coerceIn(0, procW - 1)
        val seedY = (touchY / scale).toInt().coerceIn(0, procH - 1)
        val seedColor = procBitmap.getPixel(seedX, seedY)

        val seedR = Color.red(seedColor)
        val seedG = Color.green(seedColor)
        val seedB = Color.blue(seedColor)

        val visited = BooleanArray(procW * procH)
        val mask = BooleanArray(procW * procH)
        val queue: Queue<Int> = LinkedList()
        val startIdx = seedY * procW + seedX
        queue.add(startIdx)
        visited[startIdx] = true

        var minX = seedX
        var maxX = seedX
        var minY = seedY
        var maxY = seedY

        val colorThreshold = 42
        var count = 0
        val maxPixels = (procW * procH * 0.75).toInt()

        while (!queue.isEmpty() && count < maxPixels) {
            val idx = queue.poll() ?: break
            val px = idx % procW
            val py = idx / procW
            mask[idx] = true
            count++

            minX = min(minX, px)
            maxX = max(maxX, px)
            minY = min(minY, py)
            maxY = max(maxY, py)

            val neighbors = arrayOf(
                if (px > 0) idx - 1 else -1,
                if (px < procW - 1) idx + 1 else -1,
                if (py > 0) idx - procW else -1,
                if (py < procH - 1) idx + procW else -1,
            )

            for (nIdx in neighbors) {
                if (nIdx >= 0 && !visited[nIdx]) {
                    visited[nIdx] = true
                    val nx = nIdx % procW
                    val ny = nIdx / procW
                    val c = procBitmap.getPixel(nx, ny)
                    val rDiff = abs(Color.red(c) - seedR)
                    val gDiff = abs(Color.green(c) - seedG)
                    val bDiff = abs(Color.blue(c) - seedB)

                    if (rDiff <= colorThreshold && gDiff <= colorThreshold && bDiff <= colorThreshold) {
                        queue.add(nIdx)
                    }
                }
            }
        }

        if (count < 80) return null

        val srcMinX = (minX * scale).toInt().coerceIn(0, width - 1)
        val srcMaxX = ((maxX + 1) * scale).toInt().coerceIn(0, width)
        val srcMinY = (minY * scale).toInt().coerceIn(0, height - 1)
        val srcMaxY = ((maxY + 1) * scale).toInt().coerceIn(0, height)

        val cropW = srcMaxX - srcMinX
        val cropH = srcMaxY - srcMinY
        if (cropW <= 0 || cropH <= 0) return null

        val cutout = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(cropW)
        val outPixels = IntArray(cropW)

        for (y in 0 until cropH) {
            val origY = srcMinY + y
            sourceBitmap.getPixels(srcPixels, 0, cropW, srcMinX, origY, cropW, 1)
            val pY = (origY / scale).toInt().coerceIn(0, procH - 1)

            for (x in 0 until cropW) {
                val origX = srcMinX + x
                val pX = (origX / scale).toInt().coerceIn(0, procW - 1)
                val isForeground = mask[pY * procW + pX]

                if (isForeground) {
                    outPixels[x] = srcPixels[x]
                } else {
                    outPixels[x] = 0
                }
            }
            cutout.setPixels(outPixels, 0, cropW, 0, y, cropW, 1)
        }

        return SubjectCutoutResult(
            cutoutBitmap = cutout,
            sourceBounds = RectF(
                srcMinX.toFloat() / width,
                srcMinY.toFloat() / height,
                srcMaxX.toFloat() / width,
                srcMaxY.toFloat() / height,
            ),
            imageWidth = width,
            imageHeight = height,
        )
    }

    /**
     * Saves transparent PNG sticker cutout directly into MediaStore under Pictures/PandaGallery/Stickers.
     */
    suspend fun saveCutoutToGallery(
        context: Context,
        cutoutBitmap: Bitmap,
        baseName: String = "Sticker",
    ): Uri? = withContext(Dispatchers.IO) {
        val fileName = "${baseName}_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PandaGallery/Stickers")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                cutoutBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: error("Cannot write to sticker uri")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * Copies transparent PNG sticker directly to Android system clipboard.
     */
    suspend fun copyCutoutToClipboard(
        context: Context,
        cutoutBitmap: Bitmap,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cacheFile = File(context.cacheDir, "clipped_sticker_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { out ->
                cutoutBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile,
            )

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(context.contentResolver, "Sticker", uri)
            clipboard.setPrimaryClip(clip)
            true
        }.getOrDefault(false)
    }

    /**
     * Shares transparent PNG sticker via Android system share sheet.
     */
    suspend fun shareCutout(
        context: Context,
        cutoutBitmap: Bitmap,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val cacheFile = File(context.cacheDir, "share_sticker_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use { out ->
                cutoutBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile,
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share sticker").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
