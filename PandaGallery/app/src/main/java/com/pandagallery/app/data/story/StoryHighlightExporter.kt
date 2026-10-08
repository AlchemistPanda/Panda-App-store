package com.pandagallery.app.data.story

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.pandagallery.app.data.editing.BackgroundMusicTrack
import com.pandagallery.app.data.editing.PresetBgmAudioSynthesizer
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class StoryAspect(val width: Int, val height: Int) {
    PORTRAIT_9_16(1080, 1920),
    LANDSCAPE_16_9(1920, 1080),
    SQUARE(1080, 1080),
}

/**
 * Flagship Samsung Gallery Story / Memory highlight reel exporter.
 * Assembles multiple photos and video clips into a 1080p MP4 highlight video
 * with cinematic pacing and background music soundtrack via Media3 Transformer.
 */
@Singleton
@OptIn(UnstableApi::class)
class StoryHighlightExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presetSynthesizer: PresetBgmAudioSynthesizer,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun exportHighlightReel(
        title: String,
        mediaItems: List<MediaItem>,
        soundtrack: BackgroundMusicTrack = BackgroundMusicTrack.ACOUSTIC_BREEZE,
        customAudioUri: Uri? = null,
        aspect: StoryAspect = StoryAspect.PORTRAIT_9_16,
        clipDurationMs: Long = 3500L,
        onProgress: (Int) -> Unit = {},
    ): Uri = withContext(Dispatchers.Main.immediate) {
        if (mediaItems.isEmpty()) throw IllegalArgumentException("No media items provided for highlight reel")

        val effectiveAudioUri = if (customAudioUri != null) {
            customAudioUri
        } else if (soundtrack != BackgroundMusicTrack.NONE) {
            presetSynthesizer.getPresetUri(soundtrack)
        } else {
            null
        }

        val tempOutputFile = File(context.cacheDir, "story_${UUID.randomUUID()}.mp4")
        try {
            renderReel(
                mediaItems = mediaItems,
                audioUri = effectiveAudioUri,
                outputFile = tempOutputFile,
                aspect = aspect,
                clipDurationMs = clipDurationMs,
                onProgress = onProgress,
            )
            publishStoryReel(title, tempOutputFile)
        } finally {
            tempOutputFile.delete()
        }
    }

    private suspend fun renderReel(
        mediaItems: List<MediaItem>,
        audioUri: Uri?,
        outputFile: File,
        aspect: StoryAspect,
        clipDurationMs: Long,
        onProgress: (Int) -> Unit,
    ): File = suspendCancellableCoroutine { continuation ->
        val presentation: List<Effect> = listOf(
            Presentation.createForWidthAndHeight(
                aspect.width,
                aspect.height,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
            )
        )

        val totalDurationMs = (mediaItems.size * clipDurationMs).coerceAtLeast(2000L)

        // 1. Build video sequence
        val editedVideoItems = mediaItems.map { item ->
            val builder = Media3Item.Builder().setUri(item.uri)
            if (item.isVideo) {
                builder.setClippingConfiguration(
                    Media3Item.ClippingConfiguration.Builder()
                        .setStartPositionMs(0L)
                        .setEndPositionMs(clipDurationMs.coerceAtMost(item.duration ?: clipDurationMs))
                        .build()
                )
            } else {
                builder.setImageDurationMs(clipDurationMs)
            }

            EditedMediaItem.Builder(builder.build())
                .setDurationUs(clipDurationMs * 1000L)
                .setFrameRate(30)
                .setRemoveAudio(true) // Highlights use BGM soundtrack
                .setEffects(Effects(emptyList(), presentation))
                .build()
        }

        val videoSequence = EditedMediaItemSequence.Builder(editedVideoItems).build()
        val sequences = mutableListOf(videoSequence)

        // 2. Build background music sequence if present
        if (audioUri != null) {
            val audioMediaItem = Media3Item.Builder().setUri(audioUri).build()
            val audioItem = EditedMediaItem.Builder(audioMediaItem)
                .setDurationUs(totalDurationMs * 1000L)
                .build()
            val audioSequence = EditedMediaItemSequence.Builder(audioItem)
                .setIsLooping(true)
                .build()
            sequences.add(audioSequence)
        }

        val composition = Composition.Builder(sequences).build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(outputFile) else outputFile.delete()
                }

                override fun onError(
                    comp: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    outputFile.delete()
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            })
            .build()

        val progress = ProgressHolder()
        val poller = object : Runnable {
            override fun run() {
                if (!continuation.isActive) return
                if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progress.progress)
                }
                mainHandler.postDelayed(this, 350)
            }
        }

        continuation.invokeOnCancellation {
            mainHandler.post {
                transformer.cancel()
                outputFile.delete()
            }
        }

        transformer.start(composition, outputFile.absolutePath)
        mainHandler.post(poller)
    }

    private fun publishStoryReel(title: String, file: File): Uri {
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32)
        val displayName = "Story_${sanitizedTitle}_Highlight.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PandaGallery Stories/")
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val destination = context.contentResolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: throw IOException("Unable to create MediaStore video entry for Story Highlight Reel")

        try {
            context.contentResolver.openOutputStream(destination)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("Unable to write story video stream")

            context.contentResolver.update(
                destination,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null,
            )
            return destination
        } catch (e: Exception) {
            context.contentResolver.delete(destination, null, null)
            throw e
        }
    }
}
