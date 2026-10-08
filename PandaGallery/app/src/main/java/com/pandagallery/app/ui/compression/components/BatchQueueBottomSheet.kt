package com.pandagallery.app.ui.compression.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandagallery.app.data.compression.DeviceThermalLevel
import com.pandagallery.app.data.compression.ThermalState
import com.pandagallery.app.domain.compression.CompressionQueueProgress
import com.pandagallery.app.domain.compression.CompressionTask
import com.pandagallery.app.domain.compression.CompressionTaskStatus
import com.pandagallery.app.domain.model.formatFileSize
import com.pandagallery.app.ui.components.OneUiContainerHigh
import com.pandagallery.app.ui.components.PandaModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchQueueBottomSheet(
    progress: CompressionQueueProgress?,
    /** Every task of the current batch, finished and failed ones included, so the counts are real. */
    batchTasks: List<CompressionTask>,
    thermalState: ThermalState,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val total = batchTasks.size
    // "Processed" counts failures too: they are done, and leaving them out would stall the count
    // short of N on a batch that has finished.
    val completed = batchTasks.count {
        it.status == CompressionTaskStatus.COMPLETED || it.status == CompressionTaskStatus.FAILED
    }
    val savedBytes = batchTasks.filter { it.status == CompressionTaskStatus.COMPLETED }.sumOf { it.savedBytes }
    val percent = progress?.percent ?: if (total > 0) ((completed * 100) / total) else 0
    val isAnyRunning = batchTasks.any { it.status == CompressionTaskStatus.RUNNING }
    val isAnyPaused = batchTasks.any { it.status == CompressionTaskStatus.PAUSED }
    // Once nothing is left to run, the sheet becomes a summary: the title, the bytes line and the
    // buttons all change, so a finished batch no longer looks like it is still going.
    val isFinished = batchTasks.isBatchFinished()
    // A finished batch in which no row succeeded: "Batch complete" at 100% would read as a success.
    val nothingSucceeded = isFinished &&
        batchTasks.none { it.status == CompressionTaskStatus.COMPLETED } &&
        batchTasks.any { it.status == CompressionTaskStatus.FAILED }

    PandaModalBottomSheet(
        onDismissRequest = onDismiss,
        // Hard-coded white content — see the note on SmartOptimizeBottomSheet.
        tint = Color.Black,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
        ) {
            // Title & Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Compress,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = "Batch Compression Queue",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "$completed of $total processed • ${if (savedBytes > 0) formatFileSize(savedBytes) + " saved" else if (isFinished) "Nothing saved" else "In progress"}",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Progress Bar & Percentage
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF282828), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            nothingSucceeded -> "Batch failed"
                            isFinished -> "Batch complete"
                            isAnyPaused && !isAnyRunning -> "Queue Paused"
                            else -> "Processing Batch…"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAnyPaused && !isAnyRunning) Color(0xFFFFB74D) else Color.White,
                    )
                    Text(
                        text = "$percent%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Spacer(Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isAnyPaused && !isAnyRunning) Color(0xFFFFB74D) else Color.White,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                )

                val activeItem = batchTasks.firstOrNull { it.status == CompressionTaskStatus.RUNNING }
                if (activeItem != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Active: ${activeItem.displayName}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }

            if (!isFinished) {
                Spacer(Modifier.height(14.dp))

                // Thermal Guard Status Card
                val isThrottled = thermalState.isThrottled
                val cardBg = if (isThrottled) Color(0x33FFA726) else Color(0x2280E386)
                val cardBorder = if (isThrottled) Color(0x66FFA726) else Color(0x4480E386)
                val iconTint = if (isThrottled) Color(0xFFFFB74D) else Color(0xFF80E386)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg, RoundedCornerShape(14.dp))
                        .border(BorderStroke(0.8.dp, cardBorder), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (isThrottled) Icons.Outlined.Thermostat else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isThrottled) "Thermal Guard: ${thermalState.statusDescription}" else "Device Temperature: Optimal",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            text = if (isThrottled) {
                                val temp = thermalState.batteryTempC?.let { "$it°C" } ?: "Warm"
                                "Battery $temp. Pacing threads to maintain 120fps display fluidity."
                            } else {
                                val temp = thermalState.batteryTempC?.let { " ($it°C)" } ?: ""
                                "Running at peak efficiency$temp."
                            },
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons: Pause / Resume / Cancel while the batch runs, and a single Close once it
            // has finished, so a done batch no longer offers controls that do nothing.
            if (isFinished) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        text = "Close",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isAnyPaused && !isAnyRunning) {
                        Button(
                            onClick = onResumeAll,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Resume All",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onPauseAll,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) {
                            Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Pause",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onCancelAll()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * True once no task of the batch is still queued, running or paused. An empty list is not finished:
 * it means the rows have not loaded yet, so the live progress should decide what the sheet shows.
 */
internal fun List<CompressionTask>.isBatchFinished(): Boolean =
    isNotEmpty() && none {
        it.status == CompressionTaskStatus.QUEUED ||
            it.status == CompressionTaskStatus.RUNNING ||
            it.status == CompressionTaskStatus.PAUSED
    }
