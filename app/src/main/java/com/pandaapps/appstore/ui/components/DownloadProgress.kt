package com.pandaapps.appstore.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandaapps.appstore.install.InstallState
import com.pandaapps.appstore.util.Formatters

/**
 * Progress bar + caption for an active install. Determinate (animated) while downloading with a
 * known size, indeterminate for Queued/Verifying/Installing/size-unknown. Renders nothing for
 * states that are not in progress — wrap in AnimatedVisibility to animate it in and out.
 *
 * While downloading, the caption also shows a smoothed speed and time left
 * ("12 MB / 177 MB · about 2 min left · 4.2 MB/s"), ellipsized on narrow screens.
 *
 * @param showCaption show the "12 MB / 177 MB · 7%" / "Verifying…" line under the bar.
 */
@Composable
fun DownloadProgress(
    state: InstallState,
    modifier: Modifier = Modifier,
    showCaption: Boolean = true,
) {
    val bytesPerSecond = rememberDownloadSpeed(state)
    val caption = progressCaption(state, bytesPerSecond) ?: return
    val determinate = state is InstallState.Downloading && state.total > 0
    val color by animateColorAsState(state.color(), label = "progressColor")
    val trackColor = color.copy(alpha = 0.16f)
    val barModifier = Modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(CircleShape)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (determinate) {
            val progress by animateFloatAsState(
                targetValue = (state as InstallState.Downloading).progress,
                animationSpec = tween(durationMillis = 250),
                label = "downloadProgress",
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = barModifier,
                color = color,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else {
            LinearProgressIndicator(
                modifier = barModifier,
                color = color,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
        if (showCaption) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    caption.first,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                caption.second?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
    }
}

/** Weight of the newest sample in the speed moving average; lower is smoother but slower to react. */
private const val SPEED_SMOOTHING = 0.2

/**
 * Smoothed download speed in bytes/s from successive [InstallState.Downloading] updates, or 0
 * while unknown (before the second sample, or outside a download). Resets when bytes go
 * backwards, i.e. a new download started.
 */
@Composable
private fun rememberDownloadSpeed(state: InstallState): Double {
    val bytes = (state as? InstallState.Downloading)?.bytes
    var speed by remember { mutableDoubleStateOf(0.0) }
    // Plain holder, not state: the last sample must not trigger recomposition.
    val last = remember { LongArray(2) { -1L } } // [bytes, elapsedNanos]
    // ponytail: a stalled download sends no updates, so the last speed lingers; add a decay ticker if that misleads.
    LaunchedEffect(bytes) {
        val now = System.nanoTime()
        if (bytes == null || last[0] < 0 || bytes < last[0]) {
            speed = 0.0
        } else {
            val seconds = (now - last[1]) / 1e9
            if (seconds > 0) {
                val instant = (bytes - last[0]) / seconds
                speed = if (speed <= 0.0) instant else speed + SPEED_SMOOTHING * (instant - speed)
            }
        }
        last[0] = bytes ?: -1L
        last[1] = now
    }
    return speed
}

/** (left caption, right caption) or null when [state] has no progress to show. */
private fun progressCaption(state: InstallState, bytesPerSecond: Double): Pair<String, String?>? = when (state) {
    InstallState.Queued -> "Waiting for another install…" to null
    is InstallState.Downloading ->
        listOfNotNull(
            Formatters.progressBytes(state.bytes, state.total),
            Formatters.timeLeft(state.bytes, state.total, bytesPerSecond),
            Formatters.speed(bytesPerSecond),
        ).joinToString(" · ") to
            (if (state.total > 0) Formatters.percent(state.progress) else null)
    InstallState.Verifying -> "Verifying download…" to null
    InstallState.Installing -> "Installing…" to null
    InstallState.PendingUserAction -> "Confirm the install in the system dialog" to null
    InstallState.Idle, InstallState.Success, is InstallState.Failed -> null
}
