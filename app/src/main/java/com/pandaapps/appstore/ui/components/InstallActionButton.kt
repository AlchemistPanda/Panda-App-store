package com.pandaapps.appstore.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.install.InstallState
import com.pandaapps.appstore.install.isBusy
import kotlinx.coroutines.delay

/** What [InstallActionButton] shows for a given status + install state. */
enum class InstallAction { Install, Update, Open, Retry, Confirm, Progress, Done, Unavailable, None }

/** Pure mapping used by [InstallActionButton] (exposed so screens can label things consistently). */
fun installActionFor(status: AppStatus, state: InstallState, canOpen: Boolean): InstallAction = when {
    state == InstallState.PendingUserAction -> InstallAction.Confirm
    state.isBusy -> InstallAction.Progress
    state == InstallState.Success -> InstallAction.Done
    state is InstallState.Failed -> InstallAction.Retry
    else -> when (status) {
        AppStatus.NotInstalled -> InstallAction.Install
        AppStatus.UpdateAvailable -> InstallAction.Update
        AppStatus.UpToDate, AppStatus.InstalledNewer, AppStatus.SignerMismatch ->
            if (canOpen) InstallAction.Open else InstallAction.None
        AppStatus.Incompatible -> InstallAction.Unavailable
    }
}

/**
 * The primary action for an app, driven by [status] and [installState]:
 * Install / Update (Primary) · Open (Tonal) · Retry (Danger) · Confirm (resume system dialog) ·
 * a progress ring with a cancel ✕ while working · a ✓ right after success.
 * Transitions between them are animated.
 *
 * @param onInstall install or update the latest release.
 * @param onRetry retry a failed install (repeats the release it was for); defaults to [onInstall].
 * @param onConfirm reopen the pending system install dialog.
 * @param canOpen the installed app has a launcher activity.
 * @param compact list-row sizing; false for the detail screen's large button.
 */
@Composable
fun InstallActionButton(
    status: AppStatus,
    installState: InstallState,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = onInstall,
    onConfirm: () -> Unit = {},
    canOpen: Boolean = true,
    compact: Boolean = true,
) {
    val action = installActionFor(status, installState, canOpen)
    AnimatedContent(
        targetState = action,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.6f)))
                .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.9f))
        },
        contentAlignment = Alignment.Center,
        modifier = modifier,
        label = "installAction",
    ) { current ->
        when (current) {
            InstallAction.Install ->
                PandaButton("Install", onInstall, icon = Icons.Filled.Download, compact = compact)
            InstallAction.Update ->
                PandaButton("Update", onInstall, icon = Icons.Filled.Upgrade, compact = compact)
            InstallAction.Open ->
                PandaButton(
                    "Open", onOpen, icon = Icons.AutoMirrored.Filled.OpenInNew,
                    style = PandaButtonStyle.Tonal, compact = compact,
                )
            InstallAction.Retry ->
                PandaButton(
                    "Retry", onRetry, icon = Icons.Filled.Refresh,
                    style = PandaButtonStyle.Danger, compact = compact,
                )
            InstallAction.Confirm ->
                PandaButton(
                    "Confirm", onConfirm, icon = Icons.Filled.TouchApp,
                    style = PandaButtonStyle.Tonal, compact = compact,
                )
            InstallAction.Progress ->
                InstallProgressRing(installState, onCancel, size = if (compact) 40.dp else 52.dp)
            InstallAction.Done -> DoneBadge(size = if (compact) 40.dp else 52.dp)
            InstallAction.Unavailable ->
                PandaButton(
                    "Unavailable", {}, style = PandaButtonStyle.Outlined, enabled = false, compact = compact,
                )
            InstallAction.None -> Box(Modifier.size(1.dp))
        }
    }
}

/**
 * Circular progress with a cancel ✕ in the middle (tap to cancel). Determinate while downloading
 * a known size, otherwise spinning. It takes the place of the Install/Update button that started
 * the install, so taps in its first moments ([CANCEL_ARM_DELAY_MILLIS]) are ignored: the second
 * half of a double tap must not cancel what the first half started.
 */
@Composable
fun InstallProgressRing(
    state: InstallState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val color = state.color()
    val interaction = remember { MutableInteractionSource() }
    val cancel = rememberArmedClick(onCancel)
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction, pressedScale = 0.88f)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = cancel)
            .semantics { contentDescription = "Cancel install" },
        contentAlignment = Alignment.Center,
    ) {
        val track = color.copy(alpha = 0.18f)
        if (state is InstallState.Downloading && state.total > 0) {
            val progress by animateFloatAsState(
                targetValue = state.progress,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "ringProgress",
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(size),
                color = color,
                trackColor = track,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(size),
                color = color,
                trackColor = track,
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
            )
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

@Composable
private fun DoneBadge(size: Dp) {
    val color = InstallState.Success.color()
    Box(
        Modifier
            .size(size)
            .background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = "Installed", tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

/** How long a freshly shown cancel control ignores taps. */
const val CANCEL_ARM_DELAY_MILLIS = 600L

/**
 * [onClick], but ignored until [armAfterMillis] after this call first enters the composition.
 * For a control that appears under the user's finger in place of the one they just tapped.
 */
@Composable
fun rememberArmedClick(onClick: () -> Unit, armAfterMillis: Long = CANCEL_ARM_DELAY_MILLIS): () -> Unit {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armAfterMillis) {
        delay(armAfterMillis)
        armed = true
    }
    val currentOnClick by rememberUpdatedState(onClick)
    return remember { { if (armed) currentOnClick() } }
}
