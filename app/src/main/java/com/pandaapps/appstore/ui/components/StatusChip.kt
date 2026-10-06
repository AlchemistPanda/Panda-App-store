package com.pandaapps.appstore.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.install.InstallState
import com.pandaapps.appstore.install.isBusy
import com.pandaapps.appstore.ui.theme.statusColors
import com.pandaapps.appstore.util.Formatters

/** Short user-facing label for a catalog status. */
val AppStatus.label: String
    get() = when (this) {
        AppStatus.NotInstalled -> "Not installed"
        AppStatus.UpToDate -> "Up to date"
        AppStatus.UpdateAvailable -> "Update available"
        AppStatus.InstalledNewer -> "Newer installed"
        AppStatus.SignerMismatch -> "Different key"
        AppStatus.Incompatible -> "Incompatible"
    }

/** Short label for a non-idle install state, or null for [InstallState.Idle]. */
val InstallState.label: String?
    get() = when (this) {
        InstallState.Idle -> null
        InstallState.Queued -> "Queued"
        is InstallState.Downloading -> if (total > 0) "Downloading ${Formatters.percent(progress)}" else "Downloading"
        InstallState.Verifying -> "Verifying"
        InstallState.Installing -> "Installing"
        InstallState.PendingUserAction -> "Waiting for you"
        InstallState.Success -> "Installed"
        is InstallState.Failed -> "Failed"
    }

/** Brand colour for a catalog status. */
@Composable
fun AppStatus.color(): Color {
    val colors = MaterialTheme.statusColors
    return when (this) {
        AppStatus.UpToDate -> colors.success
        AppStatus.UpdateAvailable -> colors.downloading
        AppStatus.NotInstalled -> colors.neutral
        AppStatus.InstalledNewer -> colors.warning
        AppStatus.SignerMismatch, AppStatus.Incompatible -> colors.error
    }
}

/** Brand colour for an install state ([InstallState.Idle] → neutral). */
@Composable
fun InstallState.color(): Color {
    val colors = MaterialTheme.statusColors
    return when (this) {
        InstallState.Idle -> colors.neutral
        InstallState.Queued, InstallState.Verifying -> colors.neutral
        is InstallState.Downloading, InstallState.Installing -> colors.downloading
        InstallState.PendingUserAction -> colors.warning
        InstallState.Success -> colors.success
        is InstallState.Failed -> colors.error
    }
}

/**
 * Pill showing the app's status; while an install is active the install state takes over
 * (with a pulsing dot). Colour and text animate between states.
 */
@Composable
fun StatusChip(
    status: AppStatus,
    modifier: Modifier = Modifier,
    installState: InstallState = InstallState.Idle,
) {
    val activeLabel = installState.label
    val text = activeLabel ?: status.label
    val target = if (activeLabel != null) installState.color() else status.color()
    val color by animateColorAsState(target, label = "statusChipColor")
    val pulsing = installState.isBusy

    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = color, pulsing = pulsing)
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            // Only animate when the kind of label changes, not on every progress percent.
            contentKey = { it.substringBefore(' ') },
            label = "statusChipText",
        ) { label ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
    }
}

/** Small status dot; [pulsing] breathes it (1100 ms sine, brand "live indicator"). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, pulsing: Boolean = false) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "statusDot")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "statusDotAlpha",
        )
        value
    } else {
        1f
    }
    Box(
        modifier
            .size(7.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(color, CircleShape),
    )
}
