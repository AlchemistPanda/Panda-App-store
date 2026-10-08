package com.pandagallery.app.data.compression

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pandagallery.app.data.local.dao.CompressionTaskDao
import com.pandagallery.app.data.local.entity.CompressionTaskEntity
import kotlinx.coroutines.flow.first
import com.pandagallery.app.data.local.dao.CompressionAccuracyRow
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.domain.compression.CalibrationSample
import com.pandagallery.app.domain.compression.CompressionCalibration
import com.pandagallery.app.domain.compression.CompressionEstimator
import com.pandagallery.app.domain.compression.calibrationFrom
import com.pandagallery.app.domain.compression.toEstimatorInput
import com.pandagallery.app.domain.compression.CompressionMediaType
import com.pandagallery.app.domain.compression.CompressionHistoryAnalytics
import com.pandagallery.app.domain.compression.CompressionQueueProgress
import com.pandagallery.app.domain.compression.CompressionTask
import com.pandagallery.app.domain.compression.CompressionTaskStatus
import com.pandagallery.app.domain.compression.OriginalDeleteStatus
import com.pandagallery.app.domain.compression.pendingMoveDeletionTasks
import com.pandagallery.app.domain.compression.toOperationSummary
import com.pandagallery.app.domain.compression.toActiveQueueProgress
import com.pandagallery.app.domain.compression.toStatistics
import com.pandagallery.app.domain.compression.withCurrentOutputSavings
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.CompressionOriginalAction
import com.pandagallery.app.domain.model.ImageFormat
import com.pandagallery.app.domain.model.VideoCodec
import com.pandagallery.app.domain.model.VideoResolution
import com.pandagallery.app.data.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompressionQueueRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CompressionTaskDao,
    private val engine: CompressionEngine,
    private val mediaRepository: MediaRepository,
    private val preferencesDataSource: PreferencesDataSource,
) {
    private val currentHistory = combine(
        dao.observeHistory(),
        mediaRepository.observeCurrentMedia(),
    ) { rows, media ->
        val tasks = rows.map { it.toDomain() }
        CurrentCompressionHistory(
            tasks = tasks,
            existingOutputTaskIds = tasks.existingOutputTaskIds(media),
        )
    }

    val activeTasks: Flow<List<CompressionTask>> = dao.observeActive().map { rows -> rows.map { it.toDomain() } }
    val queueProgress: Flow<CompressionQueueProgress?> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }.toActiveQueueProgress()
    }
    val historyAnalytics: Flow<CompressionHistoryAnalytics> = currentHistory.map { current ->
        val currentTasks = current.tasks.withCurrentOutputSavings(current.existingOutputTaskIds)
        val operations = currentTasks
            .groupBy { it.operationId }
            .values
            .map { it.toOperationSummary() }
            .sortedByDescending { it.completedAt ?: it.createdAt }
        CompressionHistoryAnalytics(
            operations = operations,
            tasks = currentTasks,
            statistics = current.tasks.toStatistics(current.existingOutputTaskIds),
        )
    }

    /** Every task of the current or most recent batch, finished ones included — see [CompressionTaskDao.observeBatch]. */
    val batchTasks: Flow<List<CompressionTaskEntity>> = dao.observeBatch()
    val pendingOriginalDeletions: Flow<List<CompressionTask>> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }.pendingMoveDeletionTasks()
    }

    /**
     * @param targetSizeBytes a hard per-file output budget, as chosen in Compression Studio's
     *   Target Size mode. Null runs the ordinary quality-driven encode.
     * @param remasterDetailLevel strength of the Studio's detail-enhancement pass, 0 when off.
     * @param imageFormat, imageQuality, videoResolution, videoCodec the encode settings for this
     *   batch; null means the current global preference. Either way the resolved values are
     *   snapshotted into each row and the processor encodes with that snapshot, so changing the
     *   preset mid-batch (or resuming after a reboot) can't re-encode the rest of the queue with
     *   settings it was never queued with.
     */
    suspend fun enqueue(
        items: List<MediaItem>,
        action: CompressionOriginalAction,
        targetSizeBytes: Long? = null,
        remasterDetailLevel: Float = 0f,
        imageFormat: ImageFormat? = null,
        imageQuality: Int? = null,
        videoResolution: VideoResolution? = null,
        videoCodec: VideoCodec? = null,
    ): String {
        require(items.isNotEmpty()) { "Choose at least one image or video" }
        val operationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        // Snapshot the uncalibrated prediction now, while the settings that produced it are
        // still current. Scoring it later is what lets calibration improve over time.
        val globalPreferences = preferencesDataSource.userPreferencesFlow.first()
        val preferences = globalPreferences.copy(
            imageFormat = imageFormat ?: globalPreferences.imageFormat,
            imageQuality = imageQuality ?: globalPreferences.imageQuality,
            videoResolution = videoResolution ?: globalPreferences.videoResolution,
            videoCodec = videoCodec ?: globalPreferences.videoCodec,
        )
        val isLossless = preferences.isLosslessImageEncode()
        dao.insertAll(items.mapIndexed { index, item ->
            CompressionTaskEntity(
                id = UUID.randomUUID().toString(),
                operationId = operationId,
                sourceMediaId = item.id,
                sourceUri = item.uri.toString(),
                displayName = item.displayName,
                mimeType = item.mimeType,
                originalBytes = item.size,
                dateTaken = com.pandagallery.app.domain.model.effectiveDateMillis(item.dateTaken, item.dateAdded),
                status = CompressionTaskStatus.QUEUED.name,
                createdAt = now + index,
                predictedBytes = CompressionEstimator.rawEstimate(item.toEstimatorInput(), preferences),
                originalAction = action.name,
                originalDeleteStatus = if (action == CompressionOriginalAction.MOVE) {
                    OriginalDeleteStatus.PENDING.name
                } else {
                    OriginalDeleteStatus.NOT_REQUIRED.name
                },
                isLossless = isLossless && !item.isVideo,
                // Only images have a budgeted or remastered encode path; a video task carrying
                // either would silently ignore it, so don't record a promise that can't be kept.
                targetSizeBytes = targetSizeBytes?.takeIf { !item.isVideo },
                remasterDetailLevel = if (item.isVideo) 0f else remasterDetailLevel,
                imageFormat = preferences.imageFormat.name,
                imageQuality = preferences.imageQuality,
                videoResolution = preferences.videoResolution.name,
                videoCodec = preferences.videoCodec.name,
            )
        })
        scheduleWorker()
        return operationId
    }

    /**
     * Re-enqueues the worker if a batch was mid-flight when the process died, e.g. across a
     * device reboot or when the app is reopened. WorkManager doesn't guarantee resuming a unique
     * one-time work request on its own, and [CompressionQueueProcessor.processQueue] already
     * resets stuck `RUNNING` rows back to `QUEUED` as soon as it runs — this just makes sure it
     * runs.
     *
     * Uses the ordinary request with KEEP: this is called on every app resume, and appending a
     * request each time grew the unique chain with every foregrounding. From a boot broadcast the
     * worker can't become a dataSync foreground service on Android 15, so there it defers itself
     * and posts a "tap to resume" notification instead of running a long batch unprotected —
     * see [CompressionQueueWorker].
     */
    suspend fun resumeAfterBootIfNeeded() {
        if (dao.hasResumableTasks()) scheduleWorker(ExistingWorkPolicy.KEEP)
    }

    /**
     * Correction factors measured from completed work, refreshed as jobs finish.
     */
    val calibration: Flow<CompressionCalibration> = dao.observeCalibrationSamples()
        .map { rows -> calibrationFrom(rows.map(CompressionAccuracyRow::toSample)) }

    suspend fun withCompressionMetadata(items: List<MediaItem>): List<MediaItem> {
        if (items.isEmpty()) return items
        val candidatesByMedia = items.associateWith { item ->
            compressionOutputUriCandidates(item.id, item.isVideo, item.uri.toString())
        }
        val tasksByOutputUri = candidatesByMedia.values
            .flatten()
            .distinct()
            .chunked(800)
            .flatMap { outputUris -> dao.getCompletedByOutputUris(outputUris) }
            .associateBy { it.outputUri }

        return items.map { item ->
            val task = candidatesByMedia.getValue(item)
                .mapNotNull(tasksByOutputUri::get)
                .maxByOrNull { it.completedAt ?: it.createdAt }
            if (task == null) item else item.copy(
                isCompressed = true,
                originalSize = task.originalBytes,
            )
        }
    }

    /**
     * For each original still in [currentMedia] that has a completed compression task, the id of
     * that task's output file — but only when the output is *also* still in [currentMedia], i.e.
     * both copies are live, not just the compression record. Panda Sweep uses this to recognize
     * a compressed copy as a known relationship rather than rediscovering it as a guess from a
     * perceptual-hash comparison.
     */
    suspend fun compressedCopyMediaIds(currentMedia: List<MediaItem>): Map<Long, Long> {
        if (currentMedia.isEmpty()) return emptyMap()
        val liveIds = currentMedia.mapTo(mutableSetOf()) { it.id }
        val tasks = liveIds.toList().chunked(800).flatMap { chunk -> dao.getCompletedForSources(chunk) }
        val result = mutableMapOf<Long, Long>()
        tasks.forEach { task ->
            val outputUri = task.outputUri ?: return@forEach
            val outputId = runCatching { ContentUris.parseId(Uri.parse(outputUri)) }.getOrNull() ?: return@forEach
            if (outputId != task.sourceMediaId && outputId in liveIds) {
                result[task.sourceMediaId] = outputId
            }
        }
        return result
    }

    suspend fun pause(taskId: String) {
        dao.pause(taskId)
        engine.cancelVideo(taskId)
    }

    suspend fun pauseAll() {
        dao.pauseAll()
        engine.cancelCurrentVideo()
    }

    suspend fun resume(taskId: String) {
        dao.resume(taskId)
        scheduleWorker()
    }

    suspend fun resumeAll() {
        dao.resumeAll()
        scheduleWorker()
    }

    suspend fun retry(taskId: String) {
        dao.retry(taskId)
        scheduleWorker()
    }

    suspend fun retryAllFailed() {
        dao.retryAllFailed()
        scheduleWorker()
    }

    suspend fun delete(taskId: String) {
        dao.delete(taskId)
        engine.cancelVideo(taskId)
    }

    suspend fun cancelAllActive() {
        dao.cancelAllActive()
        engine.cancelCurrentVideo()
    }

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun markOriginalDeletionRequested(ids: List<String>) {
        if (ids.isNotEmpty()) dao.setOriginalDeleteStatus(ids, OriginalDeleteStatus.REQUESTED.name)
    }

    suspend fun completeOriginalDeletion(ids: List<String>, approved: Boolean) {
        if (ids.isNotEmpty()) {
            dao.setOriginalDeleteStatus(
                ids,
                if (approved) OriginalDeleteStatus.DELETED.name else OriginalDeleteStatus.DECLINED.name,
            )
            if (approved) runCatching { mediaRepository.syncMediaStore() }
                .onFailure { com.pandagallery.app.data.diagnostics.CrashLog.e("CompressionQueue", "Sync after deleting originals failed", it) }
        }
    }

    /**
     * Batches are deliberately *not* expedited work. Expedited jobs are metered against a per-app quota
     * measured in minutes; a batch that transcodes video runs for far longer than that, and
     * once the quota is gone the platform stops the job — which tears down the foreground
     * service with it, leaving the process as a cached app the OEM is free to freeze and kill
     * mid-batch. A plain one-time request that [CompressionQueueWorker] promotes to the
     * foreground with `setForeground` is the supported shape for long-running work.
     *
     * New work appends (APPEND_OR_REPLACE) so a batch started while another runs is picked up by
     * a follow-on run; a resume only needs *a* worker, so it passes KEEP.
     */
    private fun scheduleWorker(policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE) {
        val request = OneTimeWorkRequestBuilder<CompressionQueueWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CompressionQueueWorker.UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }
}

/** WebP at quality 100 is encoded as true lossless WebP (the Lossless preset). */
internal fun com.pandagallery.app.domain.model.UserPreferences.isLosslessImageEncode(): Boolean =
    imageFormat == ImageFormat.WEBP && imageQuality >= 100

internal fun compressionOutputUriCandidates(
    mediaId: Long,
    isVideo: Boolean,
    currentUri: String,
): Set<String> {
    val collection = if (isVideo) "video" else "images"
    return setOf(
        currentUri,
        "content://media/external/$collection/media/$mediaId",
        "content://media/external_primary/$collection/media/$mediaId",
    )
}

private data class CurrentCompressionHistory(
    val tasks: List<CompressionTask>,
    val existingOutputTaskIds: Set<String>,
)

private data class MediaStoreOutputKey(
    val id: Long,
    val type: CompressionMediaType,
)

private fun List<CompressionTask>.existingOutputTaskIds(media: List<MediaItem>): Set<String> {
    val availableOutputs = media.filterNot { it.isTrashed }.mapTo(mutableSetOf()) { item ->
        MediaStoreOutputKey(
            id = item.id,
            type = if (item.isVideo) CompressionMediaType.VIDEO else CompressionMediaType.IMAGE,
        )
    }
    return mapNotNullTo(mutableSetOf()) { task ->
        val outputId = task.outputUri?.let { rawUri ->
            runCatching { ContentUris.parseId(Uri.parse(rawUri)) }.getOrNull()
        }
        val outputKey = outputId?.let { MediaStoreOutputKey(it, task.mediaType) }
        task.id.takeIf { outputKey in availableOutputs }
    }
}

internal fun CompressionTaskEntity.toDomain() = CompressionTask(
    id = id,
    operationId = operationId,
    sourceUri = sourceUri,
    displayName = displayName,
    mimeType = mimeType,
    mediaType = if (mimeType.startsWith("video/")) CompressionMediaType.VIDEO else CompressionMediaType.IMAGE,
    originalBytes = originalBytes,
    status = CompressionTaskStatus.valueOf(status),
    progress = progress,
    outputUri = outputUri,
    outputBytes = outputBytes,
    savedBytes = savedBytes,
    errorMessage = errorMessage,
    createdAt = createdAt,
    startedAt = startedAt,
    completedAt = completedAt,
    originalAction = CompressionOriginalAction.valueOf(originalAction),
    originalDeleteStatus = OriginalDeleteStatus.valueOf(originalDeleteStatus),
)

private fun CompressionAccuracyRow.toSample() = CalibrationSample(
    isVideo = mimeType.startsWith("video/"),
    predictedBytes = predictedBytes,
    actualBytes = outputBytes,
)
