package com.pandagallery.app.domain.compression

import com.pandagallery.app.domain.model.CompressionOriginalAction
import kotlin.math.roundToInt

enum class CompressionTaskStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

enum class CompressionMediaType { IMAGE, VIDEO }

enum class CompressionOperationStatus { ACTIVE, COMPLETED, PARTIALLY_FAILED, FAILED }

enum class OriginalDeleteStatus { NOT_REQUIRED, PENDING, REQUESTED, DELETED, DECLINED }

data class CompressionTask(
    val id: String,
    val operationId: String,
    val sourceUri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: CompressionMediaType,
    val originalBytes: Long,
    val status: CompressionTaskStatus,
    val progress: Int = 0,
    val outputUri: String? = null,
    val outputBytes: Long? = null,
    val savedBytes: Long = 0,
    val errorMessage: String? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val originalAction: CompressionOriginalAction = CompressionOriginalAction.COPY,
    val originalDeleteStatus: OriginalDeleteStatus = OriginalDeleteStatus.NOT_REQUIRED,
)

data class CompressionOperationSummary(
    val operationId: String,
    val itemCount: Int,
    val imageCount: Int,
    val videoCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val originalBytes: Long,
    val outputBytes: Long,
    val savedBytes: Long,
    val createdAt: Long,
    val completedAt: Long?,
    val status: CompressionOperationStatus,
    val sampleSourceUris: List<String> = emptyList(),
)

data class CompressionStatistics(
    val totalOperations: Int = 0,
    val completedItems: Int = 0,
    val originalBytes: Long = 0,
    val outputBytes: Long = 0,
    val savedBytes: Long = 0,
) {
    val savedPercent: Int
        get() = if (originalBytes <= 0L) 0 else ((savedBytes * 100L) / originalBytes).toInt()

    /**
     * What the compressed copies actually cost, which is not the same as [savedBytes].
     *
     * [savedBytes] is a sum of per-task figures that were each floored at zero, so a job whose
     * output came out *larger* than its source contributes 0 rather than a loss. Summed up, that
     * reads as "saved 0 B" next to an after-figure bigger than the before-figure. Negative here
     * means the history on screen has cost storage rather than freed it.
     */
    val netSavedBytes: Long get() = originalBytes - outputBytes
}

data class CompressionHistoryAnalytics(
    val operations: List<CompressionOperationSummary>,
    val tasks: List<CompressionTask>,
    val statistics: CompressionStatistics,
)

data class CompressionQueueProgress(val percent: Int) {
    val fraction: Float get() = percent / 100f
}

fun calculateSavedBytes(originalBytes: Long, outputBytes: Long): Long =
    (originalBytes - outputBytes).coerceAtLeast(0L)

fun List<CompressionTask>.toStatistics(existingOutputTaskIds: Set<String>): CompressionStatistics {
    val completed = filter {
        it.status == CompressionTaskStatus.COMPLETED && it.id in existingOutputTaskIds
    }
    return CompressionStatistics(
        totalOperations = completed.map { it.operationId }.distinct().size,
        completedItems = completed.size,
        originalBytes = completed.sumOf { it.originalBytes },
        outputBytes = completed.sumOf { it.outputBytes ?: 0L },
        savedBytes = completed.sumOf { it.savedBytes },
    )
}

fun List<CompressionTask>.withCurrentOutputSavings(existingOutputTaskIds: Set<String>): List<CompressionTask> =
    map { task ->
        when {
            task.status != CompressionTaskStatus.COMPLETED -> task
            // Nothing was ever published — the re-encode came out no smaller, so the queue kept
            // the original. It still occupies its full size, which is what "after" has to show;
            // zeroing it here is what produced summaries like "64.2 MB → 0 B".
            task.outputUri == null -> task.copy(outputBytes = task.originalBytes, savedBytes = 0L)
            // The compressed copy has since been deleted, so it costs nothing now.
            task.id !in existingOutputTaskIds -> task.copy(outputBytes = 0L, savedBytes = 0L)
            else -> task
        }
    }

fun List<CompressionTask>.toActiveQueueProgress(): CompressionQueueProgress? {
    val activeOperationTasks = groupBy { it.operationId }
        .values
        .filter { operation ->
            operation.any {
                it.status == CompressionTaskStatus.QUEUED ||
                    it.status == CompressionTaskStatus.RUNNING ||
                    it.status == CompressionTaskStatus.PAUSED
            }
        }
        .flatten()
    if (activeOperationTasks.isEmpty()) return null

    val percent = activeOperationTasks
        .map { task ->
            when (task.status) {
                CompressionTaskStatus.COMPLETED, CompressionTaskStatus.FAILED -> 100
                else -> task.progress.coerceIn(0, 100)
            }
        }
        .average()
        .roundToInt()
    return CompressionQueueProgress(percent)
}

fun List<CompressionTask>.toOperationSummary(): CompressionOperationSummary {
    require(isNotEmpty()) { "An operation must contain at least one compression task" }
    val completed = count { it.status == CompressionTaskStatus.COMPLETED }
    val failed = count { it.status == CompressionTaskStatus.FAILED }
    val status = when {
        any { it.status == CompressionTaskStatus.QUEUED || it.status == CompressionTaskStatus.RUNNING || it.status == CompressionTaskStatus.PAUSED } -> CompressionOperationStatus.ACTIVE
        failed == size -> CompressionOperationStatus.FAILED
        failed > 0 -> CompressionOperationStatus.PARTIALLY_FAILED
        else -> CompressionOperationStatus.COMPLETED
    }
    return CompressionOperationSummary(
        operationId = first().operationId,
        itemCount = size,
        imageCount = count { it.mediaType == CompressionMediaType.IMAGE },
        videoCount = count { it.mediaType == CompressionMediaType.VIDEO },
        completedCount = completed,
        failedCount = failed,
        originalBytes = sumOf { it.originalBytes },
        outputBytes = sumOf { it.outputBytes ?: 0L },
        savedBytes = sumOf { it.savedBytes },
        createdAt = minOf { it.createdAt },
        completedAt = mapNotNull { it.completedAt }.maxOrNull(),
        status = status,
        sampleSourceUris = map { it.sourceUri }.take(4),
    )
}

fun List<CompressionTask>.pendingMoveDeletionTasks(): List<CompressionTask> =
    groupBy { it.operationId }
        .values
        .filter { operation ->
            operation.isNotEmpty() && operation.all {
                it.status == CompressionTaskStatus.COMPLETED || it.status == CompressionTaskStatus.FAILED
            }
        }
        .flatten()
        .filter {
            it.status == CompressionTaskStatus.COMPLETED &&
                it.originalAction == CompressionOriginalAction.MOVE &&
                it.originalDeleteStatus == OriginalDeleteStatus.PENDING
        }
