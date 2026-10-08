package com.pandagallery.app.ui.compression.components

import com.pandagallery.app.domain.compression.CompressionMediaType
import com.pandagallery.app.domain.compression.CompressionTask
import com.pandagallery.app.domain.compression.CompressionTaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchQueueFinishedTest {

    @Test
    fun `an empty batch is not finished because its rows have not loaded yet`() {
        assertFalse(emptyList<CompressionTask>().isBatchFinished())
    }

    @Test
    fun `a batch of completed and failed rows is finished`() {
        val batch = listOf(
            task("a", CompressionTaskStatus.COMPLETED),
            task("b", CompressionTaskStatus.FAILED),
        )
        assertTrue(batch.isBatchFinished())
    }

    @Test
    fun `any queued, running or paused row keeps the batch open`() {
        val done = task("done", CompressionTaskStatus.COMPLETED)
        listOf(
            CompressionTaskStatus.QUEUED,
            CompressionTaskStatus.RUNNING,
            CompressionTaskStatus.PAUSED,
        ).forEach { status ->
            assertFalse("$status should not count as finished", listOf(done, task("open", status)).isBatchFinished())
        }
    }

    private fun task(id: String, status: CompressionTaskStatus) = CompressionTask(
        id = id,
        operationId = "operation",
        sourceUri = "content://media/$id",
        displayName = id,
        mimeType = "image/jpeg",
        mediaType = CompressionMediaType.IMAGE,
        originalBytes = 1_000L,
        status = status,
        createdAt = 0L,
    )
}
