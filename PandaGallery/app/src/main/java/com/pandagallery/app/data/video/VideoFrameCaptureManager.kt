package com.pandagallery.app.data.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.pandagallery.app.data.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class VideoFrameCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
) {

    /**
     * Extracts the frame at [positionMs] from the given [videoUri] and saves it
     * to MediaStore under `Pictures/PandaGallery Captures/`.
     *
     * @return The [Uri] of the saved JPEG image.
     */
    suspend fun captureFrame(videoUri: Uri, positionMs: Long): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
            } catch (e: Exception) {
                throw IOException("Failed to load video dataSource for $videoUri", e)
            }

            val timeUs = max(0L, positionMs) * 1000L
            val frameBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw IOException("Unable to extract video frame at ${positionMs}ms")

            try {
                saveFrameBitmap(frameBitmap, positionMs)
            } finally {
                frameBitmap.recycle()
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Extracts [frameCount] frames evenly spaced over [durationMs] starting at [startMs].
     * Caller is responsible for recycling returned bitmaps.
     */
    suspend fun extractClipFrames(
        videoUri: Uri,
        startMs: Long,
        durationMs: Long,
        frameCount: Int = 12,
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        val frames = mutableListOf<Bitmap>()
        try {
            val intervalMs = if (frameCount > 1) durationMs / (frameCount - 1) else durationMs
            for (i in 0 until frameCount) {
                val targetMs = startMs + (i * intervalMs)
                val timeUs = max(0L, targetMs) * 1000L
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    frames.add(frame)
                }
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
        frames
    }

    private suspend fun saveFrameBitmap(bitmap: Bitmap, positionMs: Long): Uri {
        val timestamp = System.currentTimeMillis()
        val displayName = "Capture_${timestamp}_${positionMs}ms.jpg"
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PandaGallery Captures/")
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val destination = context.contentResolver.insert(collection, values)
            ?: throw IOException("Unable to create MediaStore entry for captured frame")

        try {
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            } ?: throw IOException("Unable to open output stream for captured frame")

            context.contentResolver.update(destination, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)

            mediaRepository.syncMediaStore()
            return destination
        } catch (e: Exception) {
            context.contentResolver.delete(destination, null, null)
            throw e
        }
    }

    companion object {
        fun generateCaptureDisplayName(timestamp: Long, positionMs: Long): String =
            "Capture_${timestamp}_${positionMs}ms.jpg"

        fun calculateTargetPosition(currentMs: Long, deltaMs: Long, durationMs: Long): Long =
            (currentMs + deltaMs).coerceIn(0L, max(0L, durationMs))
    }
}
