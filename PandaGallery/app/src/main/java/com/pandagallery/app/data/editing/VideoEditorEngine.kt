package com.pandagallery.app.data.editing

import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
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

@Singleton
@OptIn(UnstableApi::class)
class VideoEditorEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val presetSynthesizer: PresetBgmAudioSynthesizer,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun saveCopy(
        item: com.pandagallery.app.domain.model.MediaItem,
        edit: VideoEditState,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.Main.immediate) {
        val bgmUri = if (!edit.customAudioUri.isNullOrBlank()) {
            android.net.Uri.parse(edit.customAudioUri)
        } else if (edit.backgroundMusic != BackgroundMusicTrack.NONE) {
            presetSynthesizer.getPresetUri(edit.backgroundMusic)
        } else {
            null
        }
        val output = export(item, edit, bgmUri, onProgress)
        try {
            publish(item, output)
        } finally {
            output.delete()
        }
    }

    private suspend fun export(
        item: com.pandagallery.app.domain.model.MediaItem,
        edit: VideoEditState,
        bgmUri: android.net.Uri? = null,
        onProgress: (Int) -> Unit,
    ): File = suspendCancellableCoroutine { continuation ->
        val output = File(context.cacheDir, "edited_${UUID.randomUUID()}.mp4")
        val duration = item.duration ?: edit.trimEndMs
        val range = normalizedTrimRange(edit.trimStartMs, edit.trimEndMs, duration)
        val trimmedDurationMs = (range.last - range.first).coerceAtLeast(500L)

        val mediaItem = MediaItem.Builder()
            .setUri(item.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(range.first)
                    .setEndPositionMs(range.last)
                    .build()
            )
            .build()
        val effects = mutableListOf<Effect>()
        if (edit.rotationDegrees % 360 != 0) {
            effects.add(
                ScaleAndRotateTransformation.Builder().setRotationDegrees(edit.rotationDegrees.toFloat()).build()
            )
        }
        if (edit.speed != VideoSpeed.NORMAL) {
            effects.add(androidx.media3.effect.SpeedChangeEffect(edit.speed.multiplier))
        }

        val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
        if (!edit.mute && edit.speed != VideoSpeed.NORMAL) {
            audioProcessors.add(
                androidx.media3.common.audio.SonicAudioProcessor().apply {
                    setSpeed(edit.speed.multiplier)
                }
            )
        }

        val removeVideoAudio = edit.mute || edit.videoAudioVolume <= 0.05f
        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(removeVideoAudio)
            .setEffects(Effects(audioProcessors, effects))
            .build()
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(output) else output.delete()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    output.delete()
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            })
            .build()
        val progress = ProgressHolder()
        val poller = object : Runnable {
            override fun run() {
                if (!continuation.isActive) return
                if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(progress.progress)
                mainHandler.postDelayed(this, 350)
            }
        }
        continuation.invokeOnCancellation {
            mainHandler.post {
                transformer.cancel()
                output.delete()
            }
        }

        if (bgmUri != null) {
            val audioMediaItem = MediaItem.Builder().setUri(bgmUri).build()
            val audioEditedItem = EditedMediaItem.Builder(audioMediaItem)
                .setDurationUs(trimmedDurationMs * 1_000L)
                .build()
            val videoSequence = androidx.media3.transformer.EditedMediaItemSequence.Builder(edited).build()
            val audioSequence = androidx.media3.transformer.EditedMediaItemSequence.Builder(audioEditedItem)
                .setIsLooping(true)
                .build()
            val composition = Composition.Builder(listOf(videoSequence, audioSequence)).build()
            transformer.start(composition, output.absolutePath)
        } else {
            transformer.start(edited, output.absolutePath)
        }
        mainHandler.post(poller)
    }

    suspend fun exportSlowMo(
        item: com.pandagallery.app.domain.model.MediaItem,
        config: com.pandagallery.app.domain.model.SlowMoExportConfig,
        onProgress: (Int) -> Unit,
    ): com.pandagallery.app.domain.model.SlowMoExportResult = withContext(Dispatchers.IO) {
        val speedEnum = when {
            config.speedMultiplier <= 0.35f -> VideoSpeed.QUARTER
            config.speedMultiplier <= 0.65f -> VideoSpeed.HALF
            config.speedMultiplier >= 1.5f -> VideoSpeed.DOUBLE
            else -> VideoSpeed.NORMAL
        }
        val editState = VideoEditState(
            trimStartMs = config.trimStartMs,
            trimEndMs = config.trimEndMs,
            speed = speedEnum,
            mute = config.muteAudio,
            isInstantSlowMoActive = true,
        )
        try {
            val file = export(item, editState, bgmUri = null, onProgress = onProgress)
            val uri = publishSlowMo(item, file, config.speedMultiplier)
            val origDur = item.duration ?: (config.trimEndMs - config.trimStartMs)
            val outputDuration = config.calculateOutputDurationMs(origDur)
            com.pandagallery.app.domain.model.SlowMoExportResult(
                isSuccess = true,
                destinationUri = uri,
                destinationPath = file.absolutePath,
                originalDurationMs = origDur,
                outputDurationMs = outputDuration,
                bytesWritten = file.length(),
            )
        } catch (e: Exception) {
            com.pandagallery.app.domain.model.SlowMoExportResult(
                isSuccess = false,
                errorMessage = e.message ?: "Failed to export slow-mo video",
            )
        }
    }

    private fun publishSlowMo(
        item: com.pandagallery.app.domain.model.MediaItem,
        output: File,
        speedMultiplier: Float,
    ): android.net.Uri {
        val speedTag = if (speedMultiplier <= 0.35f) "0.25x" else "0.5x"
        val destination = context.contentResolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${item.displayName.substringBeforeLast('.', item.displayName)}_slowmo_${speedTag}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PandaGallery SlowMo/")
                item.dateTaken?.let { put(MediaStore.Video.Media.DATE_TAKEN, it) }
                put(MediaStore.Video.Media.IS_PENDING, 1)
            },
        ) ?: throw IOException("Unable to create slow-mo video entry")
        try {
            context.contentResolver.openOutputStream(destination)?.use { stream ->
                output.inputStream().use { it.copyTo(stream) }
            } ?: throw IOException("Unable to write slow-mo video")
            context.contentResolver.update(destination, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null)
            return destination
        } catch (error: IOException) {
            context.contentResolver.delete(destination, null, null)
            throw error
        }
    }

    private fun publish(item: com.pandagallery.app.domain.model.MediaItem, output: File) {
        val destination = context.contentResolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${item.displayName.substringBeforeLast('.', item.displayName)}_edited.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PandaGallery Edited/")
                item.dateTaken?.let { put(MediaStore.Video.Media.DATE_TAKEN, it) }
                put(MediaStore.Video.Media.IS_PENDING, 1)
            },
        ) ?: throw IOException("Unable to create edited video")
        try {
            context.contentResolver.openOutputStream(destination)?.use { stream -> output.inputStream().use { it.copyTo(stream) } }
                ?: throw IOException("Unable to write edited video")
            context.contentResolver.update(destination, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null)
        } catch (error: IOException) {
            context.contentResolver.delete(destination, null, null)
            throw error
        }
    }
}
