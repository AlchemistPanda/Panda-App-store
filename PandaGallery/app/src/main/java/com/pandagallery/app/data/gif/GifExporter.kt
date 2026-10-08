package com.pandagallery.app.data.gif

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class GifDirection(val label: String) {
    FORWARD("Forward"),
    REVERSE("Reverse"),
    BOOMERANG("Boomerang"),
}

enum class GifAspect(val label: String, val widthRatio: Int, val heightRatio: Int) {
    ORIGINAL("Original", 0, 0),
    SQUARE("1:1", 1, 1),
    PORTRAIT_3_4("3:4", 3, 4),
    PORTRAIT_9_16("9:16", 9, 16),
    LANDSCAPE_16_9("16:9", 16, 9),
}

data class GifConfiguration(
    val speed: Float = 1.0f,
    val direction: GifDirection = GifDirection.FORWARD,
    val aspect: GifAspect = GifAspect.ORIGINAL,
    val maxDimension: Int = 640,
    val optimizeCompression: Boolean = true,
)

@Singleton
class GifExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
) {

    suspend fun exportGif(
        items: List<MediaItem>,
        configuration: GifConfiguration,
        onProgress: (Int) -> Unit = {},
    ): Uri = withContext(Dispatchers.IO) {
        if (items.isEmpty()) throw IllegalArgumentException("No media items provided for GIF export")

        // 1. Build sequence according to Direction
        val orderedItems = when (configuration.direction) {
            GifDirection.FORWARD -> items
            GifDirection.REVERSE -> items.reversed()
            GifDirection.BOOMERANG -> {
                if (items.size > 2) {
                    items + items.subList(1, items.size - 1).reversed()
                } else {
                    items + items.reversed()
                }
            }
        }

        // 2. Prepare MediaStore destination
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val timestamp = System.currentTimeMillis()
        val displayName = "GIF_${timestamp}.gif"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PandaGallery GIFs/")
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val destination = context.contentResolver.insert(collection, values)
            ?: throw IOException("Unable to create MediaStore GIF entry")

        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                val encoder = GifEncoder()
                // Base delay at 1.0x is 100ms (10 fps). Speed adjusts delay: 0.5x -> 200ms, 2.0x -> 50ms
                val frameDelayMs = (100f / configuration.speed.coerceIn(0.25f, 4.0f)).roundToInt().coerceIn(30, 400)
                encoder.setDelayMs(frameDelayMs)
                encoder.setRepeat(0) // Infinite loop
                encoder.start(output)

                val totalFrames = orderedItems.size
                var targetW = 0
                var targetH = 0

                orderedItems.forEachIndexed { index, item ->
                    val frameBitmap = decodeAndCropFrame(
                        context = context,
                        item = item,
                        aspect = configuration.aspect,
                        maxDim = configuration.maxDimension,
                        forcedWidth = if (index > 0) targetW else 0,
                        forcedHeight = if (index > 0) targetH else 0,
                    )

                    if (index == 0) {
                        targetW = frameBitmap.width
                        targetH = frameBitmap.height
                    }

                    encoder.addFrame(frameBitmap)
                    frameBitmap.recycle()

                    val progressPercent = ((index + 1).toFloat() / totalFrames * 100).toInt().coerceIn(0, 99)
                    onProgress(progressPercent)
                }

                encoder.finish()
            } ?: throw IOException("Unable to open output stream for GIF")

            // Clear IS_PENDING
            context.contentResolver.update(destination, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)

            mediaRepository.syncMediaStore()
            onProgress(100)

            destination
        } catch (error: Exception) {
            context.contentResolver.delete(destination, null, null)
            throw error
        }
    }

    private fun decodeAndCropFrame(
        context: Context,
        item: MediaItem,
        aspect: GifAspect,
        maxDim: Int,
        forcedWidth: Int = 0,
        forcedHeight: Int = 0,
    ): Bitmap {
        val raw = if (item.isVideo) {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, item.uri)
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: throw IOException("Unable to extract video frame for ${item.displayName}")
                val w = frame.width
                val h = frame.height
                val scale = maxDim.toFloat() / max(w, h).coerceAtLeast(1)
                if (scale < 1.0f) {
                    val targetW = (w * scale).toInt().coerceAtLeast(100)
                    val targetH = (h * scale).toInt().coerceAtLeast(100)
                    val scaled = Bitmap.createScaledBitmap(frame, targetW, targetH, true)
                    if (scaled !== frame) frame.recycle()
                    scaled
                } else {
                    frame
                }
            }
        } else {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val scale = maxDim.toFloat() / max(w, h).coerceAtLeast(1)
                if (scale < 1.0f) {
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(100), (h * scale).toInt().coerceAtLeast(100))
                }
            }
        }

        // If dimensions were established by frame 0, conform to it
        if (forcedWidth > 0 && forcedHeight > 0) {
            val conformed = Bitmap.createScaledBitmap(raw, forcedWidth, forcedHeight, true)
            if (conformed !== raw) raw.recycle()
            return conformed
        }

        // Otherwise crop to desired aspect
        if (aspect == GifAspect.ORIGINAL || aspect.widthRatio == 0) {
            val scale = maxDim.toFloat() / max(raw.width, raw.height)
            return if (scale < 1f) {
                val scaled = Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
                if (scaled !== raw) raw.recycle()
                scaled
            } else raw
        }

        val targetRatio = aspect.widthRatio.toFloat() / aspect.heightRatio.toFloat()
        val currentRatio = raw.width.toFloat() / raw.height.toFloat()

        val cropW: Int
        val cropH: Int
        if (currentRatio > targetRatio) {
            cropH = raw.height
            cropW = (raw.height * targetRatio).toInt()
        } else {
            cropW = raw.width
            cropH = (raw.width / targetRatio).toInt()
        }

        val cropX = max(0, (raw.width - cropW) / 2)
        val cropY = max(0, (raw.height - cropH) / 2)

        val cropped = Bitmap.createBitmap(raw, cropX, cropY, cropW.coerceAtMost(raw.width - cropX), cropH.coerceAtMost(raw.height - cropY))
        if (cropped !== raw) raw.recycle()

        val finalScale = maxDim.toFloat() / max(cropped.width, cropped.height)
        return if (finalScale < 1f) {
            val scaled = Bitmap.createScaledBitmap(cropped, (cropped.width * finalScale).toInt(), (cropped.height * finalScale).toInt(), true)
            if (scaled !== cropped) cropped.recycle()
            scaled
        } else cropped
    }
}
