package com.pandagallery.app.ui.viewer.video

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.pandagallery.app.ui.viewer.formatTime
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SeekDirection {
    BACKWARD, FORWARD
}

enum class ActiveMeterType {
    BRIGHTNESS, VOLUME
}

private enum class DragGestureType {
    NONE, BRIGHTNESS, VOLUME, SEEK, DISMISS, DETAILS, HORIZONTAL_PAGER
}

/**
 * Samsung Gallery 1:1 Gesture & Overlay System for Video Playback:
 * - Swipe up/down on left half: Adjust brightness
 * - Swipe up/down on right half: Adjust volume
 * - Swipe left/right across video: Passes through unconsumed to HorizontalPager for seamless next/prev photo & video navigation
 * - Double tap left / right: Seek ±10s with ripple animations
 * - Press & hold: Instant Slow-Mo (0.25x speed)
 * - Single tap: Toggle controls
 * - Swipe down from top edge: Dismiss
 * - Swipe up from bottom edge: Details sheet
 */
@Composable
fun SamsungVideoGesturesOverlay(
    player: ExoPlayer?,
    onToggleControls: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = player?.isPlaying == true,
    activeSeekRipple: SeekDirection? = null,
    isSlowMoActive: Boolean = false,
    slowMoSpeedMultiplier: Float = 0.25f,
    onSlowMoStart: (() -> Unit)? = null,
    onSlowMoEnd: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeSeekingChanged: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    val topMarginPx = with(density) { 88.dp.toPx() }
    val bottomMarginPx = with(density) { 88.dp.toPx() }
    val dismissThresholdPx = with(density) { 110.dp.toPx() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val scope = rememberCoroutineScope()

    var localSeekRipple by remember { mutableStateOf(activeSeekRipple) }
    var seekRippleJob by remember { mutableStateOf<Job?>(null) }

    var activeMeter by remember { mutableStateOf<ActiveMeterType?>(null) }
    var activeMeterJob by remember { mutableStateOf<Job?>(null) }

    // Seeking HUD state
    var isSeekingBySwipe by remember { mutableStateOf(false) }
    var swipeTargetPosition by remember { mutableLongStateOf(0L) }
    var swipeDeltaMs by remember { mutableLongStateOf(0L) }
    var swipeDuration by remember { mutableLongStateOf(0L) }
    var seekHudHideJob by remember { mutableStateOf<Job?>(null) }

    // Preserve initial window brightness to revert back to system default/previous value
    val initialWindowBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
    var hasOverriddenBrightness by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose {
            if (hasOverriddenBrightness) {
                activity?.window?.let { win ->
                    win.attributes = win.attributes.apply {
                        screenBrightness = initialWindowBrightness
                    }
                }
            }
        }
    }

    var brightnessFraction by remember {
        val currentWinBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        if (currentWinBrightness in 0.01f..1.0f) {
            mutableFloatStateOf(currentWinBrightness)
        } else {
            val sysBrightness = try {
                android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (e: Exception) {
                0.5f
            }
            mutableFloatStateOf(sysBrightness.coerceIn(0.01f, 1.0f))
        }
    }

    var volumeFraction by remember {
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val currVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVol / 2)
        mutableFloatStateOf((currVol.toFloat() / maxVol.coerceAtLeast(1)).coerceIn(0f, 1f))
    }

    val brightnessIcon = when {
        brightnessFraction < 0.33f -> Icons.Outlined.BrightnessLow
        brightnessFraction < 0.66f -> Icons.Outlined.BrightnessMedium
        else -> Icons.Outlined.BrightnessHigh
    }

    val volumeIcon = when {
        volumeFraction <= 0.01f -> Icons.AutoMirrored.Outlined.VolumeMute
        volumeFraction < 0.5f -> Icons.AutoMirrored.Outlined.VolumeDown
        else -> Icons.AutoMirrored.Outlined.VolumeUp
    }

    // Safety watchdog: ensure seek HUD never remains stuck indefinitely even if gesture is cancelled abruptly
    LaunchedEffect(isSeekingBySwipe) {
        if (isSeekingBySwipe) {
            delay(1200)
            isSeekingBySwipe = false
            onSwipeSeekingChanged?.invoke(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var slowMoStarted = false
                        val slowMoJob = scope.launch {
                            delay(350)
                            if (player != null && onSlowMoStart != null) {
                                slowMoStarted = true
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSlowMoStart()
                            }
                        }
                        tryAwaitRelease()
                        slowMoJob.cancel()
                        if (slowMoStarted && onSlowMoEnd != null) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSlowMoEnd()
                        }
                    },
                    onTap = {
                        onToggleControls()
                    },
                    onDoubleTap = { offset ->
                        val isLeft = offset.x < size.width / 2f
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (isLeft) {
                            onSeekRelative(-10_000L)
                            localSeekRipple = SeekDirection.BACKWARD
                        } else {
                            onSeekRelative(+10_000L)
                            localSeekRipple = SeekDirection.FORWARD
                        }
                        seekRippleJob?.cancel()
                        seekRippleJob = scope.launch {
                            delay(650)
                            localSeekRipple = null
                        }
                    },
                )
            }
            .pointerInput(player) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    val initialPositionMs = player?.currentPosition ?: 0L
                    val playerDuration = (player?.duration ?: 0L).coerceAtLeast(0L)
                    var gestureType = DragGestureType.NONE
                    var totalDx = 0f
                    var totalDy = 0f

                    try {
                        do {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            if (pressedChanges.size == 1) {
                                val change = pressedChanges.first()
                                val posChange = change.positionChange()
                                totalDx += posChange.x
                                totalDy += posChange.y

                                // Resolve drag gesture once movement exceeds touchSlop
                                if (gestureType == DragGestureType.NONE) {
                                    if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                        if (abs(totalDy) >= abs(totalDx)) {
                                            // Vertical Drag:
                                            if (startPos.y < topMarginPx && totalDy > 0 && onDismissRequest != null) {
                                                gestureType = DragGestureType.DISMISS
                                            } else if (onSwipeUp != null && startPos.y > (size.height - bottomMarginPx) && totalDy < 0 && abs(totalDy) >= abs(totalDx) * 1.5f) {
                                                gestureType = DragGestureType.DETAILS
                                            } else if (startPos.y in topMarginPx..(size.height - bottomMarginPx)) {
                                                // Brightness on left half, Volume on right half
                                                val isLeft = startPos.x < size.width / 2f
                                                gestureType = if (isLeft) DragGestureType.BRIGHTNESS else DragGestureType.VOLUME
                                                activeMeterJob?.cancel()
                                                activeMeter = if (isLeft) ActiveMeterType.BRIGHTNESS else ActiveMeterType.VOLUME
                                            } else {
                                                gestureType = DragGestureType.NONE
                                            }
                                        } else {
                                            // Horizontal Drag:
                                            // Paging between media items is handled by HorizontalPager!
                                            // Do not consume horizontal drag events so HorizontalPager can navigate effortlessly.
                                            gestureType = DragGestureType.HORIZONTAL_PAGER
                                        }
                                    }
                                }

                                when (gestureType) {
                                    DragGestureType.DISMISS, DragGestureType.DETAILS -> {
                                        change.consume()
                                    }
                                    DragGestureType.BRIGHTNESS -> {
                                        change.consume()
                                        hasOverriddenBrightness = true
                                        val dragDelta = -posChange.y / (size.height.toFloat() * 0.70f)
                                        brightnessFraction = (brightnessFraction + dragDelta).coerceIn(0.01f, 1.0f)
                                        activity?.window?.let { win ->
                                            win.attributes = win.attributes.apply { screenBrightness = brightnessFraction }
                                        }
                                    }
                                    DragGestureType.VOLUME -> {
                                        change.consume()
                                        val dragDelta = -posChange.y / (size.height.toFloat() * 0.70f)
                                        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                                        if (maxVol > 0) {
                                            volumeFraction = (volumeFraction + dragDelta).coerceIn(0f, 1f)
                                            val newVolInt = (volumeFraction * maxVol).roundToInt().coerceIn(0, maxVol)
                                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolInt, 0)
                                        }
                                    }
                                    DragGestureType.SEEK -> {
                                        change.consume()
                                        if (playerDuration > 0L) {
                                            // 1 screen width sweeps through roughly 90 seconds (or duration/2)
                                            val sweepWindow = playerDuration.coerceIn(30_000L, 90_000L)
                                            val delta = ((totalDx / size.width.toFloat()) * sweepWindow).toLong()
                                            val target = (initialPositionMs + delta).coerceIn(0L, playerDuration)
                                            swipeDeltaMs = delta
                                            swipeTargetPosition = target
                                            player?.seekTo(target)
                                        }
                                    }
                                    DragGestureType.HORIZONTAL_PAGER, DragGestureType.NONE -> {
                                        // Touch within slop or horizontal drag for next/prev media:
                                        // DO NOT consume, allowing HorizontalPager to receive the gesture natively!
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        // Gesture ended / finger lifted / cancelled
                        when (gestureType) {
                            DragGestureType.DISMISS -> {
                                if (totalDy > dismissThresholdPx) {
                                    onDismissRequest?.invoke()
                                }
                            }
                            DragGestureType.DETAILS -> {
                                if (-totalDy > dismissThresholdPx) {
                                    onSwipeUp?.invoke()
                                }
                            }
                            DragGestureType.BRIGHTNESS, DragGestureType.VOLUME -> {
                                activeMeterJob?.cancel()
                                activeMeterJob = scope.launch {
                                    delay(1200)
                                    activeMeter = null
                                }
                            }
                            DragGestureType.SEEK -> {
                                onSwipeSeekingChanged?.invoke(false)
                                seekHudHideJob?.cancel()
                                seekHudHideJob = scope.launch {
                                    delay(600)
                                    isSeekingBySwipe = false
                                }
                            }
                            DragGestureType.HORIZONTAL_PAGER, DragGestureType.NONE -> {
                                if (isSeekingBySwipe) {
                                    onSwipeSeekingChanged?.invoke(false)
                                    seekHudHideJob?.cancel()
                                    seekHudHideJob = scope.launch {
                                        delay(600)
                                        isSeekingBySwipe = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Instant Slow-Mo HUD
        SamsungInstantSlowMoOverlay(
            isSlowMoActive = isSlowMoActive,
            speedMultiplier = slowMoSpeedMultiplier,
        )

        // Seeking HUD (Center screen pill with target time, delta, and progress bar)
        AnimatedVisibility(
            visible = isSeekingBySwipe && swipeDuration > 0L,
            enter = fadeIn(tween(120)) + scaleIn(tween(150), initialScale = 0.85f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.95f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            SamsungSeekHud(
                targetPosition = swipeTargetPosition,
                duration = swipeDuration,
                deltaMs = swipeDeltaMs,
            )
        }

        // Animated double-tap seek ripple (Backward)
        val rippleToDisplay = activeSeekRipple ?: localSeekRipple
        AnimatedVisibility(
            visible = rippleToDisplay == SeekDirection.BACKWARD,
            enter = fadeIn(tween(100)) + scaleIn(tween(150), initialScale = 0.8f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.1f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp),
        ) {
            SeekRippleBadge(
                direction = SeekDirection.BACKWARD,
                label = "-10 sec",
            )
        }

        // Animated double-tap seek ripple (Forward)
        AnimatedVisibility(
            visible = rippleToDisplay == SeekDirection.FORWARD,
            enter = fadeIn(tween(100)) + scaleIn(tween(150), initialScale = 0.8f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.1f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp),
        ) {
            SeekRippleBadge(
                direction = SeekDirection.FORWARD,
                label = "+10 sec",
            )
        }

        // Left vertical meter HUD (Brightness)
        AnimatedVisibility(
            visible = activeMeter == ActiveMeterType.BRIGHTNESS,
            enter = fadeIn(tween(120)) + scaleIn(tween(150), initialScale = 0.85f),
            exit = fadeOut(tween(350)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp),
        ) {
            SamsungVerticalMeterHud(
                icon = brightnessIcon,
                fraction = brightnessFraction,
                percentage = (brightnessFraction * 100).roundToInt(),
                label = "Brightness",
            )
        }

        // Right vertical meter HUD (Volume)
        AnimatedVisibility(
            visible = activeMeter == ActiveMeterType.VOLUME,
            enter = fadeIn(tween(120)) + scaleIn(tween(150), initialScale = 0.85f),
            exit = fadeOut(tween(350)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp),
        ) {
            SamsungVerticalMeterHud(
                icon = volumeIcon,
                fraction = volumeFraction,
                percentage = (volumeFraction * 100).roundToInt(),
                label = "Volume",
            )
        }
    }
}

/**
 * Backwards-compatible visual-only overload.
 */
@Composable
fun SamsungVideoGesturesOverlay(
    activeSeekRipple: SeekDirection?,
    modifier: Modifier = Modifier,
    isSlowMoActive: Boolean = false,
    slowMoSpeedMultiplier: Float = 0.25f,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        SamsungInstantSlowMoOverlay(
            isSlowMoActive = isSlowMoActive,
            speedMultiplier = slowMoSpeedMultiplier,
        )

        AnimatedVisibility(
            visible = activeSeekRipple == SeekDirection.BACKWARD,
            enter = fadeIn(tween(100)) + scaleIn(tween(150), initialScale = 0.8f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.1f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp),
        ) {
            SeekRippleBadge(
                direction = SeekDirection.BACKWARD,
                label = "-10 sec",
            )
        }

        AnimatedVisibility(
            visible = activeSeekRipple == SeekDirection.FORWARD,
            enter = fadeIn(tween(100)) + scaleIn(tween(150), initialScale = 0.8f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.1f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp),
        ) {
            SeekRippleBadge(
                direction = SeekDirection.FORWARD,
                label = "+10 sec",
            )
        }
    }
}

/**
 * Samsung One UI Swipe-to-Seek Center Screen HUD:
 * Displays target time, seek delta, direction icon, and miniature timeline progress bar.
 */
@Composable
fun SamsungSeekHud(
    targetPosition: Long,
    duration: Long,
    deltaMs: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE141414),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
        shadowElevation = 10.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (deltaMs >= 0) Icons.Outlined.FastForward else Icons.Outlined.FastRewind,
                    contentDescription = null,
                    tint = if (deltaMs >= 0) Color(0xFF38BDF8) else Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "${formatTime(targetPosition)} / ${formatTime(duration)}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            val deltaSeconds = (deltaMs / 1000).toInt()
            val sign = if (deltaSeconds >= 0) "+" else ""
            Text(
                text = "$sign${deltaSeconds}s",
                color = if (deltaSeconds >= 0) Color(0xFF38BDF8) else Color(0xFFFF9800),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )

            // Miniature progress track
            val fraction = if (duration > 0L) (targetPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(if (deltaMs >= 0) Color(0xFF38BDF8) else Color(0xFFFF9800)),
                )
            }
        }
    }
}

/**
 * Animated double-tap seek ripple pill with circular semi-transparent background.
 */
@Composable
private fun SeekRippleBadge(
    direction: SeekDirection,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color.Black.copy(alpha = 0.65f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = Modifier.padding(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (direction == SeekDirection.BACKWARD) {
                Icon(
                    imageVector = Icons.Outlined.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            if (direction == SeekDirection.FORWARD) {
                Icon(
                    imageVector = Icons.Outlined.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Samsung One UI Floating Vertical Meter HUD (for Brightness & Volume):
 * Sleek glassmorphic rounded capsule with icon at top, vertical progress track,
 * and percentage value at bottom.
 */
@Composable
private fun SamsungVerticalMeterHud(
    icon: ImageVector,
    fraction: Float,
    percentage: Int,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF141414).copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier
            .width(52.dp)
            .height(180.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )

            // Vertical Track
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .weight(1f)
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction.coerceIn(0f, 1f))
                        .background(Color.White),
                )
            }

            Text(
                text = "$percentage%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
