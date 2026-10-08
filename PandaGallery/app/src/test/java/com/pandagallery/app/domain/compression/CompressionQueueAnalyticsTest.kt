package com.pandagallery.app.domain.compression

import com.pandagallery.app.domain.model.CompressionOriginalAction
import org.junit.Assert.assertEquals
import org.junit.Test

class CompressionQueueAnalyticsTest {

    @Test
    fun `saved bytes never becomes negative when output is larger`() {
        assertEquals(0L, calculateSavedBytes(originalBytes = 1_000L, outputBytes = 1_200L))
        assertEquals(350L, calculateSavedBytes(originalBytes = 1_000L, outputBytes = 650L))
    }

    @Test
    fun `operation summary aggregates mixed media and completion state`() {
        val tasks = listOf(
            task(id = "image", type = CompressionMediaType.IMAGE, saved = 400L),
            task(id = "video", type = CompressionMediaType.VIDEO, saved = 1_600L),
        )

        val summary = tasks.toOperationSummary()

        assertEquals(2, summary.itemCount)
        assertEquals(1, summary.imageCount)
        assertEquals(1, summary.videoCount)
        assertEquals(2_000L, summary.savedBytes)
        assertEquals(CompressionOperationStatus.COMPLETED, summary.status)
        assertEquals(listOf("content://media/image", "content://media/video"), summary.sampleSourceUris)
    }

    @Test
    fun `operation summary extracts sample source uris capped at four items`() {
        val tasks = (1..6).map { index ->
            task(id = "item$index")
        }

        val summary = tasks.toOperationSummary()

        assertEquals(6, summary.itemCount)
        assertEquals(4, summary.sampleSourceUris.size)
        assertEquals(
            listOf(
                "content://media/item1",
                "content://media/item2",
                "content://media/item3",
                "content://media/item4",
            ),
            summary.sampleSourceUris,
        )
    }

    @Test
    fun `operation summary reports active and failed states`() {
        val active = listOf(
            task(id = "done", status = CompressionTaskStatus.COMPLETED),
            task(id = "running", status = CompressionTaskStatus.RUNNING),
        ).toOperationSummary()
        val failed = listOf(
            task(id = "done", status = CompressionTaskStatus.COMPLETED),
            task(id = "failed", status = CompressionTaskStatus.FAILED),
        ).toOperationSummary()

        assertEquals(CompressionOperationStatus.ACTIVE, active.status)
        assertEquals(CompressionOperationStatus.PARTIALLY_FAILED, failed.status)
    }

    @Test
    fun `move deletion waits for operation and includes only successful originals`() {
        val running = listOf(
            task(id = "done", action = CompressionOriginalAction.MOVE),
            task(id = "running", status = CompressionTaskStatus.RUNNING, action = CompressionOriginalAction.MOVE),
        )
        val terminal = listOf(
            task(id = "done", action = CompressionOriginalAction.MOVE),
            task(id = "failed", status = CompressionTaskStatus.FAILED, action = CompressionOriginalAction.MOVE),
        )

        assertEquals(emptyList<CompressionTask>(), running.pendingMoveDeletionTasks())
        assertEquals(listOf("done"), terminal.pendingMoveDeletionTasks().map { it.id })
    }

    @Test
    fun `copy and already handled move tasks never request deletion`() {
        val tasks = listOf(
            task(id = "copy", action = CompressionOriginalAction.COPY),
            task(
                id = "deleted",
                action = CompressionOriginalAction.MOVE,
                deleteStatus = OriginalDeleteStatus.DELETED,
            ),
        )

        assertEquals(emptyList<CompressionTask>(), tasks.pendingMoveDeletionTasks())
    }

    @Test
    fun `statistics exclude completed tasks whose compressed output was deleted`() {
        val tasks = listOf(
            task(id = "kept", outputUri = "content://media/kept", outputBytes = 600L, saved = 400L),
            task(id = "deleted", outputUri = "content://media/deleted", outputBytes = 500L, saved = 500L),
        )

        val statistics = tasks.toStatistics(setOf("kept"))

        assertEquals(1, statistics.completedItems)
        assertEquals(1, statistics.totalOperations)
        assertEquals(1_000L, statistics.originalBytes)
        assertEquals(600L, statistics.outputBytes)
        assertEquals(400L, statistics.savedBytes)
    }

    @Test
    fun `history keeps deleted output task but removes its current savings`() {
        val tasks = listOf(
            task(id = "kept", outputUri = "content://media/kept", outputBytes = 600L, saved = 400L),
            task(id = "deleted", outputUri = "content://media/deleted", outputBytes = 500L, saved = 500L),
        )

        val summary = tasks.withCurrentOutputSavings(setOf("kept")).toOperationSummary()

        assertEquals(2, summary.itemCount)
        assertEquals(400L, summary.savedBytes)
        assertEquals(600L, summary.outputBytes)
    }

    @Test
    fun `queue progress includes completed items from active operations`() {
        val tasks = listOf(
            task(id = "completed", status = CompressionTaskStatus.COMPLETED),
            task(id = "running", status = CompressionTaskStatus.RUNNING, progress = 40),
            task(id = "queued", status = CompressionTaskStatus.QUEUED),
        )

        assertEquals(47, tasks.toActiveQueueProgress()?.percent)
    }

    @Test
    fun `queue progress is absent when every operation is terminal`() {
        val tasks = listOf(
            task(id = "completed", status = CompressionTaskStatus.COMPLETED),
            task(id = "failed", status = CompressionTaskStatus.FAILED),
        )

        assertEquals(null, tasks.toActiveQueueProgress())
    }

    @Test
    fun `active queue accurately detects paused and running tasks`() {
        val mixedTasks = listOf(
            task(id = "running", status = CompressionTaskStatus.RUNNING),
            task(id = "paused", status = CompressionTaskStatus.PAUSED),
            task(id = "queued", status = CompressionTaskStatus.QUEUED),
        )

        val hasRunningOrQueued = mixedTasks.any {
            it.status == CompressionTaskStatus.RUNNING || it.status == CompressionTaskStatus.QUEUED
        }
        val hasPaused = mixedTasks.any { it.status == CompressionTaskStatus.PAUSED }

        assertEquals(true, hasRunningOrQueued)
        assertEquals(true, hasPaused)
    }

    @Test
    fun `failed tasks are accurately filtered for individual and bulk retry`() {
        val historyTasks = listOf(
            task(id = "done1", status = CompressionTaskStatus.COMPLETED),
            task(id = "failed1", status = CompressionTaskStatus.FAILED),
            task(id = "failed2", status = CompressionTaskStatus.FAILED),
            task(id = "done2", status = CompressionTaskStatus.COMPLETED),
        )

        val failedOnly = historyTasks.filter { it.status == CompressionTaskStatus.FAILED }

        assertEquals(2, failedOnly.size)
        assertEquals(listOf("failed1", "failed2"), failedOnly.map { it.id })
    }

    private fun task(
        id: String,
        type: CompressionMediaType = CompressionMediaType.IMAGE,
        status: CompressionTaskStatus = CompressionTaskStatus.COMPLETED,
        progress: Int = 0,
        saved: Long = 0L,
        outputUri: String? = null,
        outputBytes: Long? = null,
        action: CompressionOriginalAction = CompressionOriginalAction.COPY,
        deleteStatus: OriginalDeleteStatus = if (action == CompressionOriginalAction.MOVE) {
            OriginalDeleteStatus.PENDING
        } else {
            OriginalDeleteStatus.NOT_REQUIRED
        },
    ) = CompressionTask(
        id = id,
        operationId = "operation",
        sourceUri = "content://media/$id",
        displayName = id,
        mimeType = if (type == CompressionMediaType.VIDEO) "video/mp4" else "image/jpeg",
        mediaType = type,
        originalBytes = 1_000L,
        status = status,
        progress = progress,
        outputUri = outputUri,
        outputBytes = outputBytes,
        savedBytes = saved,
        createdAt = 10L,
        originalAction = action,
        originalDeleteStatus = deleteStatus,
    )
}
