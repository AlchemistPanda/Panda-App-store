package com.pandagallery.app.ui.compression

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandagallery.app.domain.compression.CompressionMediaType
import com.pandagallery.app.domain.compression.CompressionOperationStatus
import com.pandagallery.app.domain.compression.CompressionOperationSummary
import com.pandagallery.app.domain.compression.CompressionStatistics
import com.pandagallery.app.domain.compression.CompressionTask
import com.pandagallery.app.domain.compression.CompressionTaskStatus
import com.pandagallery.app.ui.components.AdaptiveSingleLineText
import com.pandagallery.app.ui.components.PremiumAlertDialog
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionManagerScreen(
    onBack: () -> Unit,
    onHistory: () -> Unit,
    viewModel: CompressionManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failedTasks = state.historyTasks.filter { it.status == CompressionTaskStatus.FAILED }
    var showCancelQueueDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BlackTopBar(
                title = "Compression queue",
                onBack = onBack,
                action = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Outlined.History, "Compression history")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // This screen has no bottom bar, so the app-wide floating compression-progress pill
            // (MainActivity) sits just above the nav bar inset rather than above a 96dp-tall bar.
            // A flat 16dp bottom inset let it cover the last card's status row while a batch — the
            // very thing this screen is showing — was running. 96dp matches the clearance the pill
            // gets on screens that do have the bottom bar, so the last card stays fully visible.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SavingsCard(state.statistics) }

            if (failedTasks.isNotEmpty()) {
                item {
                    FailedTasksBannerCard(
                        failedCount = failedTasks.size,
                        onRetryAll = { viewModel.retryAllFailed() },
                        onViewHistory = onHistory,
                    )
                }
            }

            if (state.activeTasks.isEmpty()) {
                if (failedTasks.isEmpty()) {
                    item {
                        SmartStorageRecommendationCard(onGoToGallery = onBack)
                    }
                    item {
                        EmptyState(
                            title = "Queue is idle",
                            body = "Background compression engine is ready. Select photos or videos in your gallery to batch compress and free up storage.",
                        )
                    }
                }
            } else {
                item {
                    QueueControlsCard(
                        activeTasks = state.activeTasks,
                        onPauseAll = { viewModel.pauseAll() },
                        onResumeAll = { viewModel.resumeAll() },
                        onCancelAll = { showCancelQueueDialog = true },
                    )
                }
                items(state.activeTasks, key = { it.id }) { task ->
                    QueueTaskCard(
                        task = task,
                        onPause = { viewModel.pause(task.id) },
                        onResume = { viewModel.resume(task.id) },
                        onDelete = { viewModel.delete(task.id) },
                    )
                }
            }
        }
    }

    if (showCancelQueueDialog) {
        PremiumAlertDialog(
            onDismissRequest = { showCancelQueueDialog = false },
            title = { Text("Cancel Compression Queue?") },
            text = {
                Text(
                    "This will stop active compression and remove all ${state.activeTasks.size} pending tasks from the queue."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelAllActive()
                        showCancelQueueDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Cancel Queue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelQueueDialog = false }) {
                    Text("Keep Running")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionHistoryScreen(
    onBack: () -> Unit,
    viewModel: CompressionManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failedTasks = state.historyTasks.filter { it.status == CompressionTaskStatus.FAILED }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BlackTopBar(
                title = "Compression history",
                onBack = onBack,
                action = {
                    if (state.history.isNotEmpty() || failedTasks.isNotEmpty()) {
                        IconButton(onClick = { showClearHistoryDialog = true }) {
                            Icon(Icons.Outlined.DeleteSweep, "Clear history")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Same floating-pill clearance as CompressionManagerScreen above — this screen is one
            // tap away from it (the History icon) and can be opened while a batch is still running.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SavingsCard(state.statistics) }
            if (failedTasks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("NEEDS ATTENTION (${failedTasks.size})")
                        FilledTonalButton(
                            onClick = { viewModel.retryAllFailed() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Retry all")
                        }
                    }
                }
                items(failedTasks, key = { "failed-${it.id}" }) { task ->
                    FailedTaskCard(
                        task = task,
                        onRetry = { viewModel.retry(task.id) },
                        onDelete = { viewModel.delete(task.id) },
                    )
                }
            }
            if (state.history.isEmpty() && failedTasks.isEmpty()) {
                item { EmptyState("No history yet", "Completed compression operations and their space savings appear here.") }
            } else if (state.history.isNotEmpty()) {
                item { SectionLabel("OPERATIONS") }
                items(state.history, key = { it.operationId }) { OperationCard(it) }
            }
        }
    }

    if (showClearHistoryDialog) {
        PremiumAlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Compression History?") },
            text = { Text("This will permanently clear completed and failed compression records.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlackTopBar(
    title: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit,
) {
    TopAppBar(
        title = {
            AdaptiveSingleLineText(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                minFontSize = MaterialTheme.typography.titleMedium.fontSize,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = { action() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun QueueControlsCard(
    activeTasks: List<CompressionTask>,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val runningOrQueuedCount = activeTasks.count { it.status == CompressionTaskStatus.RUNNING || it.status == CompressionTaskStatus.QUEUED }
    val pausedCount = activeTasks.count { it.status == CompressionTaskStatus.PAUSED }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = "${activeTasks.size} active task${if (activeTasks.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        if (runningOrQueuedCount > 0) append("$runningOrQueuedCount queued/running")
                        if (pausedCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("$pausedCount paused")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (runningOrQueuedCount > 0) {
                    FilledTonalButton(
                        onClick = onPauseAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause all", maxLines = 1)
                    }
                }
                if (pausedCount > 0) {
                    FilledTonalButton(
                        onClick = onResumeAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Resume all", maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = onCancelAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel queue", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FailedTasksBannerCard(
    failedCount: Int,
    onRetryAll: () -> Unit,
    onViewHistory: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "$failedCount conversion${if (failedCount == 1) "" else "s"} failed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "You can retry all failed tasks now or inspect them in history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onViewHistory) {
                    Text("View details")
                }
                FilledTonalButton(
                    onClick = onRetryAll,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry all failed")
                }
            }
        }
    }
}

@Composable
private fun SavingsCard(stats: CompressionStatistics) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Savings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(
                        if (stats.netSavedBytes < 0L) "Storage used by compression" else "Space saved overall",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (stats.savedPercent > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1B5E20).copy(alpha = 0.35f),
                    ) {
                        Text(
                            text = "-${stats.savedPercent}%",
                            color = Color(0xFF80E386),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            // Reporting stats.savedBytes unconditionally showed "0 B" above an after-figure
            // larger than the before-figure, which reads as a broken screen rather than as
            // history from jobs that grew their files.
            Text(
                if (stats.netSavedBytes < 0L) {
                    "+${formatBytes(-stats.netSavedBytes)}"
                } else {
                    formatBytes(stats.savedBytes)
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (stats.netSavedBytes < 0L) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (stats.netSavedBytes < 0L) {
                Text(
                    "These compressed copies take more room than the originals did. " +
                        "Newer jobs keep the original whenever the re-encode comes out no smaller.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stats.originalBytes > 0L) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        val ratio = (stats.outputBytes.toFloat() / stats.originalBytes.toFloat()).coerceIn(0.02f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ratio)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Original: ${formatBytes(stats.originalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Now: ${formatBytes(stats.outputBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                "${stats.completedItems} item${if (stats.completedItems == 1) "" else "s"} compressed across ${stats.totalOperations} batch${if (stats.totalOperations == 1) "" else "es"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun QueueTaskCard(
    task: CompressionTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = task.sourceUri,
                        contentDescription = task.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (task.mediaType == CompressionMediaType.VIDEO) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        task.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        task.status.readableName(),
                        color = if (task.status == CompressionTaskStatus.PAUSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilledTonalIconButton(onClick = if (task.status == CompressionTaskStatus.PAUSED) onResume else onPause) {
                    Icon(
                        if (task.status == CompressionTaskStatus.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        if (task.status == CompressionTaskStatus.PAUSED) "Resume" else "Pause",
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete task") }
            }
            LinearProgressIndicator(
                progress = { task.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatBytes(task.originalBytes), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text("${task.progress}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FailedTaskCard(task: CompressionTask, onRetry: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = task.sourceUri,
                        contentDescription = task.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (task.mediaType == CompressionMediaType.VIDEO) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        task.displayName,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        task.errorMessage ?: "Compression failed",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete failed task") }
                FilledTonalIconButton(onClick = onRetry) { Icon(Icons.Outlined.Refresh, "Retry") }
            }
        }
    }
}

@Composable
private fun OperationCard(operation: CompressionOperationSummary) {
    val percent = if (operation.originalBytes == 0L) 0 else ((operation.savedBytes * 100L) / operation.originalBytes).toInt()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${operation.itemCount} item${if (operation.itemCount == 1) "" else "s"}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(operation.createdAt)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (operation.sampleSourceUris.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    operation.sampleSourceUris.forEach { uri ->
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    if (operation.itemCount > operation.sampleSourceUris.size) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "+${operation.itemCount - operation.sampleSourceUris.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "${operation.imageCount} image${if (operation.imageCount == 1) "" else "s"} · ${operation.videoCount} video${if (operation.videoCount == 1) "" else "s"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Saved ${formatBytes(operation.savedBytes)} ($percent%)",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${formatBytes(operation.originalBytes)} → ${formatBytes(operation.outputBytes)} · ${operation.status.readableName()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}

private fun CompressionTaskStatus.readableName() = name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }

private fun CompressionOperationStatus.readableName() = when (this) {
    CompressionOperationStatus.ACTIVE -> "Active"
    CompressionOperationStatus.COMPLETED -> "Completed"
    CompressionOperationStatus.PARTIALLY_FAILED -> "Partially failed"
    CompressionOperationStatus.FAILED -> "Failed"
}

private fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    return when {
        value >= 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", value / (1024 * 1024 * 1024))
        value >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", value / (1024 * 1024))
        value >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", value / 1024)
        else -> "${value.toLong()} B"
    }
}

@Composable
private fun SmartStorageRecommendationCard(onGoToGallery: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Compress,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Storage Optimizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Samsung One UI Storage Saver",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                // Three claims measured and dropped: "70-80%" (true only for 4K video and large
                // camera originals, not for the already-compressed files most libraries are full
                // of), "camera-original visual fidelity" (the default is a lossy quality-75
                // re-encode), and naming the codec as the savings mechanism (Media3 derives the
                // bitrate from dimensions alone, so HEVC and AV1 buy quality, not size).
                text = "Re-encode large photos and high-resolution video to free up space. " +
                    "Savings are biggest on camera originals and 4K clips; files that are " +
                    "already compressed are kept as they are rather than replaced with " +
                    "something larger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(
                    onClick = onGoToGallery,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Photos to Compress")
                }
            }
        }
    }
}
