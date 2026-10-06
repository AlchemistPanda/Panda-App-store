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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.pandaapps.appstore.install.InstallState
import com.pandaapps.appstore.util.Formatters

/**
 * Progress bar + caption for an active install. Determinate (animated) while downloading with a
 * known size, indeterminate for Queued/Verifying/Installing/size-unknown. Renders nothing for
 * states that are not in progress — wrap in AnimatedVisibility to animate it in and out.
 *
 * @param showCaption show the "12 MB / 177 MB · 7%" / "Verifying…" line under the bar.
 */
@Composable
fun DownloadProgress(
    state: InstallState,
    modifier: Modifier = Modifier,
    showCaption: Boolean = true,
) {
    val caption = progressCaption(state) ?: return
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    caption.first,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                caption.second?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }
    }
}

/** (left caption, right caption) or null when [state] has no progress to show. */
private fun progressCaption(state: InstallState): Pair<String, String?>? = when (state) {
    InstallState.Queued -> "Waiting for another install…" to null
    is InstallState.Downloading ->
        Formatters.progressBytes(state.bytes, state.total) to
            (if (state.total > 0) Formatters.percent(state.progress) else null)
    InstallState.Verifying -> "Verifying download…" to null
    InstallState.Installing -> "Installing…" to null
    InstallState.PendingUserAction -> "Confirm the install in the system dialog" to null
    InstallState.Idle, InstallState.Success, is InstallState.Failed -> null
}
