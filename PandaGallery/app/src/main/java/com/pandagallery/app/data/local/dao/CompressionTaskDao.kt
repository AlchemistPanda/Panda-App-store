package com.pandagallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pandagallery.app.data.local.entity.CompressionTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompressionTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<CompressionTaskEntity>)

    @Query("SELECT * FROM compression_tasks ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CompressionTaskEntity>>

    /**
     * Finished jobs that recorded a prediction, newest first.
     *
     * Bounded so calibration reflects recent behaviour: settings and camera change, and a
     * job from a year ago is not evidence about what this device does today.
     *
     * Only rows whose size the estimator actually modelled count: the prediction is always the
     * quality-driven encode, so Target Size jobs (size set by the budget), Remaster jobs (the
     * sharpening pass inflates output) and lossless jobs are left out, as are rows completed
     * without replacing (`outputUri IS NULL`), whose outputBytes is the original's size rather
     * than anything the encoder produced.
     */
    @Query(
        "SELECT mimeType, predictedBytes, outputBytes FROM compression_tasks " +
            "WHERE status = 'COMPLETED' AND predictedBytes IS NOT NULL AND outputBytes IS NOT NULL " +
            "AND outputUri IS NOT NULL AND isLossless = 0 AND targetSizeBytes IS NULL " +
            "AND remasterDetailLevel <= 0 " +
            "ORDER BY COALESCE(completedAt, createdAt) DESC LIMIT :limit"
    )
    fun observeCalibrationSamples(limit: Int = 200): Flow<List<CompressionAccuracyRow>>

    /**
     * Every task of the batch in flight — all operations that still have queued or running work —
     * plus the most recent operation, so a finished batch stays visible with its completed and
     * failed rows ("k of N processed", bytes saved) until the next one starts.
     */
    @Query(
        "SELECT * FROM compression_tasks WHERE operationId IN (" +
            "SELECT operationId FROM compression_tasks WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED') " +
            "UNION SELECT operationId FROM (SELECT operationId FROM compression_tasks ORDER BY createdAt DESC LIMIT 1)" +
            ") ORDER BY createdAt ASC"
    )
    fun observeBatch(): Flow<List<CompressionTaskEntity>>

    @Query("SELECT * FROM compression_tasks WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<CompressionTaskEntity>>

    /** Whether a batch was mid-flight and needs its worker re-enqueued, e.g. after a reboot. */
    @Query("SELECT EXISTS(SELECT 1 FROM compression_tasks WHERE status IN ('QUEUED', 'RUNNING'))")
    suspend fun hasResumableTasks(): Boolean

    /** Whether any work is left, paused included: a paused batch is still one the worker must report. */
    @Query("SELECT EXISTS(SELECT 1 FROM compression_tasks WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED'))")
    suspend fun hasUnfinishedTasks(): Boolean

    @Query("SELECT COUNT(*) FROM compression_tasks WHERE status IN ('QUEUED', 'RUNNING')")
    suspend fun countActive(): Int

    @Query("SELECT * FROM compression_tasks WHERE status IN ('COMPLETED', 'FAILED') ORDER BY COALESCE(completedAt, createdAt) DESC")
    fun observeHistory(): Flow<List<CompressionTaskEntity>>

    @Query("SELECT * FROM compression_tasks WHERE id = :id")
    fun observeById(id: String): Flow<CompressionTaskEntity?>

    @Query("SELECT * FROM compression_tasks WHERE id = :id")
    suspend fun getById(id: String): CompressionTaskEntity?

    @Query("SELECT * FROM compression_tasks WHERE status = 'COMPLETED' AND outputUri IN (:outputUris)")
    suspend fun getCompletedByOutputUris(outputUris: List<String>): List<CompressionTaskEntity>

    @Query("SELECT * FROM compression_tasks WHERE status = 'COMPLETED' AND sourceMediaId IN (:mediaIds)")
    suspend fun getCompletedForSources(mediaIds: List<Long>): List<CompressionTaskEntity>

    @Query("SELECT * FROM compression_tasks WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): CompressionTaskEntity?

    @Transaction
    suspend fun claimNextQueuedTask(startedAt: Long): CompressionTaskEntity? {
        while (true) {
            val next = nextQueued() ?: return null
            val updated = markRunning(next.id, startedAt)
            if (updated > 0) {
                return next.copy(status = "RUNNING", startedAt = startedAt)
            }
        }
    }

    @Query("UPDATE compression_tasks SET status = 'QUEUED', progress = 0, startedAt = NULL WHERE status = 'RUNNING'")
    suspend fun recoverInterruptedTasks()

    @Query("UPDATE compression_tasks SET status = 'RUNNING', progress = 0, errorMessage = NULL, startedAt = :startedAt WHERE id = :id AND status = 'QUEUED'")
    suspend fun markRunning(id: String, startedAt: Long): Int

    @Query("UPDATE compression_tasks SET progress = :progress WHERE id = :id AND status = 'RUNNING'")
    suspend fun updateProgress(id: String, progress: Int)

    @Query("UPDATE compression_tasks SET status = 'PAUSED' WHERE id = :id AND status IN ('QUEUED', 'RUNNING')")
    suspend fun pause(id: String)

    @Query("UPDATE compression_tasks SET status = 'PAUSED' WHERE status IN ('QUEUED', 'RUNNING')")
    suspend fun pauseAll()

    @Query("UPDATE compression_tasks SET status = 'QUEUED', errorMessage = NULL WHERE id = :id AND status = 'PAUSED'")
    suspend fun resume(id: String)

    @Query("UPDATE compression_tasks SET status = 'QUEUED', errorMessage = NULL WHERE status = 'PAUSED'")
    suspend fun resumeAll()

    @Query("UPDATE compression_tasks SET status = 'QUEUED', progress = 0, errorMessage = NULL, startedAt = NULL, completedAt = NULL WHERE id = :id AND status = 'FAILED'")
    suspend fun retry(id: String)

    @Query("UPDATE compression_tasks SET status = 'QUEUED', progress = 0, errorMessage = NULL, startedAt = NULL, completedAt = NULL WHERE status = 'FAILED'")
    suspend fun retryAllFailed()

    /**
     * Returns the rows updated. Only a RUNNING task completes, so a pause or delete that landed during
     * the encode or publish is not overwritten; 0 tells the caller the task was not finished.
     */
    @Query("UPDATE compression_tasks SET status = 'COMPLETED', progress = 100, outputUri = :outputUri, outputBytes = :outputBytes, savedBytes = :savedBytes, errorMessage = NULL, completedAt = :completedAt WHERE id = :id AND status = 'RUNNING'")
    suspend fun complete(id: String, outputUri: String, outputBytes: Long, savedBytes: Long, completedAt: Long): Int

    /**
     * Finishes a task that produced nothing worth keeping — the re-encode came out no smaller
     * than the source, so the original stays and no duplicate is published.
     *
     * `originalDeleteStatus` is forced to `NOT_REQUIRED` because there is no compressed copy to
     * replace the original with; leaving it `PENDING` would offer to bin a file that was never
     * actually replaced (see `pendingMoveDeletionTasks`).
     */
    @Query(
        "UPDATE compression_tasks SET status = 'COMPLETED', progress = 100, outputUri = NULL, " +
            "outputBytes = :originalBytes, savedBytes = 0, errorMessage = NULL, " +
            "originalDeleteStatus = 'NOT_REQUIRED', completedAt = :completedAt WHERE id = :id"
    )
    suspend fun completeWithoutReplacing(id: String, originalBytes: Long, completedAt: Long)

    @Query("UPDATE compression_tasks SET status = 'FAILED', errorMessage = :message, completedAt = :completedAt WHERE id = :id")
    suspend fun fail(id: String, message: String, completedAt: Long)

    @Query("DELETE FROM compression_tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM compression_tasks WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED')")
    suspend fun cancelAllActive()

    @Query("DELETE FROM compression_tasks WHERE status IN ('COMPLETED', 'FAILED')")
    suspend fun clearHistory()

    @Query("UPDATE compression_tasks SET originalDeleteStatus = :status WHERE id IN (:ids)")
    suspend fun setOriginalDeleteStatus(ids: List<String>, status: String)
}

/** A completed job's prediction alongside what it actually produced. */
data class CompressionAccuracyRow(
    val mimeType: String,
    val predictedBytes: Long,
    val outputBytes: Long,
)
