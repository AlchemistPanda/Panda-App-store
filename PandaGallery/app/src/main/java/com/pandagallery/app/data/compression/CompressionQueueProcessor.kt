package com.pandagallery.app.data.compression

import android.content.Context
import android.net.Uri
import android.util.Log
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.local.dao.CompressionTaskDao
import com.pandagallery.app.data.local.entity.CompressionTaskEntity
import com.pandagallery.app.data.local.entity.SafetyVaultEntity
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.data.source.MediaStoreDataSource
import com.pandagallery.app.domain.compression.CompressionTaskStatus
import com.pandagallery.app.domain.compression.calculateSavedBytes
import com.pandagallery.app.domain.model.ImageFormat
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.UserPreferences
import com.pandagallery.app.domain.model.VideoCodec
import com.pandagallery.app.domain.model.VideoResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class CompressionTaskProgress(
    val completedCount: Int,
    val totalCount: Int,
    val currentFileName: String?,
    val currentItemPercent: Int,
    val savedBytesTotal: Long = 0L,
    val isThermalThrottled: Boolean = false,
    val thermalDescription: String = "Optimal",
)

@Singleton
class CompressionQueueProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CompressionTaskDao,
    private val engine: CompressionEngine,
    private val preferencesDataSource: PreferencesDataSource,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val mediaRepository: MediaRepository,
    private val safetyVaultRepository: SafetyVaultRepository,
    private val thermalMonitor: ThermalThrottlingMonitor,
) {
    suspend fun processQueue(
        onProgressUpdate: (suspend (progress: CompressionTaskProgress) -> Unit)? = null,
    ) {
        dao.recoverInterruptedTasks()
        // Nothing is in flight now, so anything left in the engine's work dir is an export a
        // killed or cancelled run never got to delete — possibly GBs of 4K video.
        engine.deleteStaleWorkFiles()
        val preferences = preferencesDataSource.userPreferencesFlow.first()
        val requestedConcurrency = preferences.compressionConcurrency.coerceIn(1, 10)
        val publishedAny = AtomicBoolean(false)
        val initialActiveCount = dao.countActive()
        val completedCounter = AtomicInteger(0)
        val savedBytesAccumulator = AtomicLong(0L)
        val activeTasksProgress = ConcurrentHashMap<String, Pair<String, Int>>()

        val initialThermal = thermalMonitor.refreshThermalState()
        onProgressUpdate?.invoke(
            CompressionTaskProgress(
                completedCount = 0,
                totalCount = initialActiveCount,
                currentFileName = null,
                currentItemPercent = 0,
                savedBytesTotal = 0L,
                isThermalThrottled = initialThermal.isThrottled,
                thermalDescription = initialThermal.statusDescription,
            )
        )

        coroutineScope {
            val workers = (0 until requestedConcurrency).map { workerIndex ->
                launch {
                    while (true) {
                        // Dynamically throttle workers if device is heating up
                        thermalMonitor.applyCooldownIfNeeded { dao.countActive() > 0 }
                        val currentThermal = thermalMonitor.refreshThermalState()
                        val maxAllowedConcurrency = thermalMonitor.effectiveConcurrency(requestedConcurrency)

                        if (workerIndex >= maxAllowedConcurrency) {
                            // Active concurrency is thermally clamped; pace higher worker indices
                            delay(600L)
                            if (dao.countActive() == 0) break
                            continue
                        }

                        val task = dao.claimNextQueuedTask(System.currentTimeMillis())
                        if (task == null) {
                            // Nothing claimable *right now* doesn't mean the batch is over:
                            // the remaining rows may be held by sibling workers, or the user
                            // may be appending more from the gallery. Exiting here ends
                            // processQueue, which ends doWork, which tears down the foreground
                            // service mid-batch — so wait for the queue to actually drain.
                            if (dao.countActive() == 0) break
                            delay(400L)
                            continue
                        }
                        val currentDone = completedCounter.get()
                        val currentActive = dao.countActive()
                        // The claimed task is RUNNING, so countActive already includes it.
                        val totalInBatch = currentDone + currentActive
                        activeTasksProgress[task.id] = task.displayName to 0

                        onProgressUpdate?.invoke(
                            CompressionTaskProgress(
                                completedCount = currentDone,
                                totalCount = totalInBatch,
                                currentFileName = task.displayName,
                                currentItemPercent = 0,
                                savedBytesTotal = savedBytesAccumulator.get(),
                                isThermalThrottled = currentThermal.isThrottled,
                                thermalDescription = currentThermal.statusDescription,
                            )
                        )

                        val result = processTask(task) { itemPercent ->
                            activeTasksProgress[task.id] = task.displayName to itemPercent
                            val activeItem = activeTasksProgress.values.firstOrNull()
                            val liveThermal = thermalMonitor.thermalState.value
                            onProgressUpdate?.invoke(
                                CompressionTaskProgress(
                                    completedCount = completedCounter.get(),
                                    totalCount = totalInBatch,
                                    currentFileName = activeItem?.first ?: task.displayName,
                                    currentItemPercent = activeItem?.second ?: itemPercent,
                                    savedBytesTotal = savedBytesAccumulator.get(),
                                    isThermalThrottled = liveThermal.isThrottled,
                                    thermalDescription = liveThermal.statusDescription,
                                )
                            )
                        }

                        activeTasksProgress.remove(task.id)
                        if (result.published) {
                            publishedAny.set(true)
                            savedBytesAccumulator.addAndGet(result.savedBytes)
                        }

                        // A paused or deleted task isn't done: counting it showed "3 of 3 (100%)"
                        // with paused rows still waiting, and overshot once they were resumed.
                        val updatedDone = if (result.finished) completedCounter.incrementAndGet() else completedCounter.get()
                        val remainingActive = dao.countActive()
                        val nextItem = activeTasksProgress.values.firstOrNull()
                        val postThermal = thermalMonitor.refreshThermalState()

                        onProgressUpdate?.invoke(
                            CompressionTaskProgress(
                                completedCount = updatedDone,
                                totalCount = updatedDone + remainingActive,
                                currentFileName = nextItem?.first,
                                currentItemPercent = nextItem?.second ?: 0,
                                savedBytesTotal = savedBytesAccumulator.get(),
                                isThermalThrottled = postThermal.isThrottled,
                                thermalDescription = postThermal.statusDescription,
                            )
                        )
                    }
                }
            }
            workers.joinAll()
        }

        if (publishedAny.get()) mediaRepository.syncMediaStore()
    }

    /**
     * Whether any batch work is left, paused included, so the worker reports a paused batch as paused.
     * A worker that finds nothing returns before it promotes to foreground: a follow-on run that starts
     * after the batch has drained would otherwise post "Compression paused" for an empty queue.
     */
    suspend fun hasWork(): Boolean = dao.hasUnfinishedTasks()

    /**
     * The start-of-batch sweep of [processQueue], for a run that has nothing to do: an export a killed run
     * left behind would otherwise sit until the next batch starts. With no unfinished rows no encode is in flight.
     */
    suspend fun sweepStaleWorkIfIdle() {
        // ponytail: a failed sweep only leaves the file for the next batch start, so it must not fail the worker
        if (!hasWork()) runCatching { engine.deleteStaleWorkFiles() }
    }

    private companion object {
        const val TAG = "CompressionQueue"

        /**
         * A re-encode has to beat the original by at least 5% to be worth keeping. Below that the
         * duplicate costs more storage than the saving is worth, and at or above 1.0 it costs
         * storage to make the file bigger.
         */
        const val KEEP_ORIGINAL_RATIO = 0.95
    }

    private data class ProcessTaskResult(
        /** The row reached COMPLETED or FAILED (not paused or deleted mid-run). */
        val finished: Boolean,
        val published: Boolean,
        val savedBytes: Long,
    )

    private suspend fun processTask(
        task: CompressionTaskEntity,
        onItemProgress: (suspend (Int) -> Unit)? = null,
    ): ProcessTaskResult =
        coroutineScope {
            val preferences = task.encodeSettings(preferencesDataSource.userPreferencesFlow.first())
            val source = task.toMediaItem()

            if (shouldKeepOriginalUnencoded(task, preferences)) {
                dao.completeWithoutReplacing(task.id, task.originalBytes, System.currentTimeMillis())
                onItemProgress?.invoke(100)
                return@coroutineScope ProcessTaskResult(finished = true, published = false, savedBytes = 0L)
            }

            var temporaryFile: File? = null
            // The MOVE backup taken for this run, until its completion is recorded. The finally block
            // discards it if the run ends first; see the backup step below.
            var uncompletedBackup: SafetyVaultEntity? = null
            // The MediaStore copy this run published, until its completion is recorded. The finally block
            // deletes it if the run ends first, so a retry publishes one copy rather than two.
            var publishedOutput: Uri? = null
            val progressEvents = MutableSharedFlow<Int>(
                extraBufferCapacity = 32,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
            // Both helpers are children of this scope, so an exception escaping either (e.g.
            // SQLiteFullException from updateProgress when output fills the disk) used to cancel
            // the whole batch and leave every in-flight row RUNNING. Progress is cosmetic: log it.
            val progressWriter = launch {
                progressEvents.collectLatest { pct ->
                    val coerced = pct.coerceIn(0, 99)
                    try {
                        dao.updateProgress(task.id, coerced)
                        onItemProgress?.invoke(coerced)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Log.w(TAG, "Progress update for ${task.displayName} failed", error)
                    }
                }
            }
            // runCatching keeps an encode failure in the Result instead of failing this scope.
            val encode = async {
                runCatching {
                    if (task.mimeType.startsWith("video/")) {
                        engine.compressVideo(
                            videoUri = source.uri,
                            preferences = preferences,
                            taskId = task.id,
                        ) { progressEvents.tryEmit(it.toInt()) }
                    } else {
                        val targetSizeBytes = task.targetSizeBytes
                        if (targetSizeBytes != null && targetSizeBytes > 0L) {
                            engine.compressImageToTargetSize(
                                imageUri = source.uri,
                                targetSizeBytes = targetSizeBytes,
                                preferences = preferences,
                                remasterDetailLevel = task.remasterDetailLevel,
                                replacesOriginal = task.originalAction == "MOVE",
                                onProgress = { progressEvents.tryEmit(it) },
                            )
                        } else {
                            engine.compressImage(
                                imageUri = source.uri,
                                preferences = preferences,
                                remasterDetailLevel = task.remasterDetailLevel,
                                replacesOriginal = task.originalAction == "MOVE",
                                onProgress = { progressEvents.tryEmit(it) },
                            )
                        }
                    }
                }
            }
            // Pause/delete cancels the encode coroutine itself, not just a registered video
            // export: a task claimed by a worker can sit RUNNING for minutes waiting on the video
            // or image gate, where engine.cancelVideo found nothing to cancel and the full
            // transcode then ran anyway. Cancelling the coroutine also aborts the gate wait,
            // image encodes, and the gap between fallback tiers.
            val stateWatcher = launch {
                try {
                    dao.observeById(task.id).collect { current ->
                        if (current == null || current.status == CompressionTaskStatus.PAUSED.name) {
                            encode.cancel()
                            engine.cancelVideo(task.id)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Log.w(TAG, "Watching ${task.displayName} for pause/delete failed", error)
                }
            }

            try {
                val encoded = try {
                    encode.await()
                } catch (stopped: CancellationException) {
                    ensureActive() // the batch itself is being stopped: propagate
                    // The engine deletes its own output when cancelled (see ensureActive() before it returns),
                    // so nothing is left behind for deleteStaleWorkFiles() to sweep.
                    return@coroutineScope stoppedOrFailed(task, "Compression was stopped")
                }
                val output = encoded.getOrThrow()
                temporaryFile = output

                val current = dao.getById(task.id)
                if (current == null || current.status != CompressionTaskStatus.RUNNING.name) {
                    return@coroutineScope ProcessTaskResult(finished = false, published = false, savedBytes = 0L)
                }
                val outputBytes = output.length()
                require(outputBytes > 0L) { "Compression produced an empty file" }

                // Re-encoding something already well compressed — a previous output of this very
                // queue, or media that arrived optimised — usually produces a *larger* file. That
                // was being published anyway, so each pass added a duplicate and the library grew
                // instead of shrinking. Keep the original instead; nothing is backed up, nothing
                // is published, and the original is never offered for deletion.
                if (task.originalBytes > 0L && outputBytes >= task.originalBytes * KEEP_ORIGINAL_RATIO) {
                    dao.completeWithoutReplacing(
                        id = task.id,
                        originalBytes = task.originalBytes,
                        completedAt = System.currentTimeMillis(),
                    )
                    onItemProgress?.invoke(100)
                    return@coroutineScope ProcessTaskResult(finished = true, published = false, savedBytes = 0L)
                }

                // MOVE ends with the original offered for deletion, on the promise that the vault
                // holds a copy to revert to. With the vault on, a failed backup (disk full mid-copy
                // of a large video, source unreadable) must therefore stop the replacement rather
                // than be ignored. With the vault switched off the user has opted out of that
                // net — the original still goes to the system trash, not straight to deletion — so
                // MOVE proceeds as before.
                val backup = if (task.originalAction == "MOVE") {
                    safetyVaultRepository.backupOriginal(
                        sourceUri = source.uri,
                        sourceMediaId = task.sourceMediaId,
                        displayName = task.displayName,
                        mimeType = task.mimeType,
                        originalBytes = task.originalBytes,
                        dateTaken = task.dateTaken,
                    ).also { backup ->
                        if (backup == null && preferences.safetyVaultEnabled) {
                            error("Couldn't back up the original to the Safety Vault (low storage?), so it was kept unchanged")
                        }
                    }
                } else {
                    null
                }
                // Nothing owns this backup until the completion is recorded. If the run fails or is
                // stopped before then, the finally block discards it: the original is still in place,
                // so the copy would only hold space the batch was meant to free.
                uncompletedBackup = backup

                // Publish, link and complete as one unit: a stop between publishing and recording
                // the completion left the row RUNNING, so the next run re-encoded, re-backed-up
                // and re-published it — two compressed copies and two vault backups of one photo.
                // ponytail: process death between publishing and recording the completion can still duplicate on retry (a failed completion write is cleaned up in finally); upgrade = record outputUri before clearing IS_PENDING and treat rows with one as done on recovery
                val savedBytes = calculateSavedBytes(task.originalBytes, outputBytes)
                val recorded = withContext(NonCancellable) {
                    val outputUri = mediaStoreDataSource.publishCompressedFile(source, output, preferences)
                    publishedOutput = outputUri
                    if (backup != null) {
                        runCatching {
                            val publishedId = android.content.ContentUris.parseId(outputUri)
                            if (publishedId > 0) safetyVaultRepository.linkCompressedMedia(backup.id, publishedId)
                        }
                    }
                    // Recorded only while the row is still RUNNING: a pause or delete that landed during the
                    // encode or publish holds. An unrecorded run keeps the row's status and drops its copies below.
                    dao.complete(
                        id = task.id,
                        outputUri = outputUri.toString(),
                        outputBytes = outputBytes,
                        savedBytes = savedBytes,
                        completedAt = System.currentTimeMillis(),
                    ) > 0
                }
                if (!recorded) {
                    // published = true so the MediaStore sync runs after the copy is removed in finally.
                    return@coroutineScope ProcessTaskResult(finished = false, published = true, savedBytes = 0L)
                }
                uncompletedBackup = null
                publishedOutput = null
                onItemProgress?.invoke(100)
                ProcessTaskResult(finished = true, published = true, savedBytes = savedBytes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                com.pandagallery.app.data.diagnostics.CrashLog.e(
                    "CompressionQueue",
                    "Compressing ${task.displayName} (${task.mimeType}, ${task.originalBytes / 1024} KB) failed",
                    error,
                )
                stoppedOrFailed(task, error.message?.take(300) ?: "Compression failed")
            } finally {
                // A copy published by this run whose completion was never recorded is removed, so the
                // retry publishes one copy and the discarded backup no longer points at a live item.
                // The app created the item, so the delete needs no prompt. Best effort: a failed delete
                // leaves the copy visible, as before this guard.
                publishedOutput?.let { orphan -> runCatching { context.contentResolver.delete(orphan, null, null) } }
                // Only a backup that no completed row claimed is still set here, and it runs in
                // NonCancellable, so a stopped batch releases it too.
                uncompletedBackup?.let { safetyVaultRepository.discardBackup(it) }
                stateWatcher.cancel()
                progressWriter.cancel()
                engine.cancelVideo(task.id)
                temporaryFile?.delete()
            }
        }

    /**
     * Fails the row if it is still RUNNING; a paused or deleted row is left as the user set it.
     * An encode can also end because the user stopped it, which surfaces as an error or a
     * cancellation — the row's status is what says which it was.
     */
    private suspend fun stoppedOrFailed(task: CompressionTaskEntity, message: String): ProcessTaskResult =
        withContext(NonCancellable) {
            val current = dao.getById(task.id)
            val stillRunning = current?.status == CompressionTaskStatus.RUNNING.name
            if (stillRunning) dao.fail(task.id, message, System.currentTimeMillis())
            ProcessTaskResult(finished = stillRunning, published = false, savedBytes = 0L)
        }

    /**
     * Sources a re-encode can only damage or never improve, completed as "kept original" without
     * decoding them at all:
     * - animated GIF / WebP: the decoder reads the first frame only, so the "compressed" copy
     *   was a still image and MOVE then binned the animation;
     * - Lossless onto an already-lossy JPEG/HEIC/HEIF: lossless WebP of a camera photo is almost
     *   always bigger, which was discovered only after a slow full-resolution encode;
     * - video under Lossless: there is no lossless video path, so it would be a lossy transcode
     *   replacing the original under a "Lossless" label.
     */
    private fun shouldKeepOriginalUnencoded(task: CompressionTaskEntity, preferences: UserPreferences): Boolean {
        val mime = task.mimeType.lowercase(Locale.US)
        if (mime == "image/gif") return true
        if (mime == "image/webp" && isAnimatedWebp(Uri.parse(task.sourceUri))) return true
        // ponytail: animated AVIF/HEIF sequences aren't detected; add an ImageDecoder isAnimated probe if they show up
        // Target Size is a budgeted encode, not a lossless one: never skip it.
        if (task.targetSizeBytes != null) return false
        if (mime.startsWith("video/")) {
            // Only the video side of the Lossless preset (ORIGINAL resolution) has no lossless path;
            // a hand-tuned video resolution still needs its transcode.
            return preferences.isLosslessImageEncode() && preferences.videoResolution == VideoResolution.ORIGINAL
        }
        return preferences.isLosslessImageEncode() && mime in LOSSY_PHOTO_MIME_TYPES
    }

    private fun isAnimatedWebp(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(WEBP_HEADER_BYTES)
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) break
                read += n
            }
            read == header.size && isAnimatedWebpHeader(header)
        } ?: false
    }.getOrDefault(false)
}

/** Overlays the encode settings snapshotted on the row (see [CompressionTaskEntity.imageFormat]). */
internal fun CompressionTaskEntity.encodeSettings(current: UserPreferences): UserPreferences = current.copy(
    imageFormat = imageFormat?.let { runCatching { ImageFormat.valueOf(it) }.getOrNull() } ?: current.imageFormat,
    imageQuality = imageQuality ?: current.imageQuality,
    videoResolution = videoResolution?.let { runCatching { VideoResolution.valueOf(it) }.getOrNull() } ?: current.videoResolution,
    videoCodec = videoCodec?.let { runCatching { VideoCodec.valueOf(it) }.getOrNull() } ?: current.videoCodec,
)

private const val WEBP_HEADER_BYTES = 21

/** `RIFF....WEBPVP8X` with the animation flag (bit 1 of the VP8X flags byte) set. */
internal fun isAnimatedWebpHeader(header: ByteArray): Boolean {
    if (header.size < WEBP_HEADER_BYTES) return false
    fun ascii(offset: Int, text: String) = text.indices.all { header[offset + it] == text[it].code.toByte() }
    return ascii(0, "RIFF") && ascii(8, "WEBP") && ascii(12, "VP8X") && (header[20].toInt() and 0x02) != 0
}

private val LOSSY_PHOTO_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/heic", "image/heif")

private fun CompressionTaskEntity.toMediaItem(): MediaItem {
    val effectiveDate = dateTaken?.takeIf { it > 0L } ?: createdAt
    val effectiveSeconds = effectiveDate / 1_000L
    return MediaItem(
        id = sourceMediaId,
        uri = Uri.parse(sourceUri),
        displayName = displayName,
        mimeType = mimeType,
        size = originalBytes,
        width = 0,
        height = 0,
        dateAdded = effectiveSeconds,
        dateModified = effectiveSeconds,
        dateTaken = effectiveDate,
    )
}

