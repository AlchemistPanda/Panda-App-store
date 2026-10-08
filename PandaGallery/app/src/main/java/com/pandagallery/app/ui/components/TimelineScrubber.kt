package com.pandagallery.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandagallery.app.domain.model.TimelineItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

private const val MINIMUM_ITEMS_FOR_SCRUBBER = 4
private val THUMB_DEFAULT_HEIGHT = 40.dp
private val THUMB_IDLE_WIDTH = 3.5.dp
private val THUMB_ACTIVE_WIDTH = 8.dp
private val THUMB_END_PADDING = 4.dp
private val THUMB_TOUCH_AREA_WIDTH = 36.dp
private val THUMB_VERTICAL_TOLERANCE = 16.dp

/**
 * Checks if a touch Y position hits the scrollbar thumb with vertical tolerance.
 * Samsung Gallery restricts fast scroll activation strictly to the thumb handle.
 */
internal fun isPointInsideThumb(
    touchY: Float,
    thumbTopY: Float,
    thumbHeightPx: Float,
    verticalTolerancePx: Float,
): Boolean {
    val top = thumbTopY - verticalTolerancePx
    val bottom = thumbTopY + thumbHeightPx + verticalTolerancePx
    return touchY in top..bottom
}

/**
 * Calculates the new thumb top Y position based on pointer Y and the initial touch grab offset,
 * ensuring the thumb tracks the finger without jumping or teleporting.
 */
internal fun calculateScrubberThumbY(
    currentPointerY: Float,
    grabOffsetY: Float,
    maxOffsetPx: Float,
): Float {
    if (maxOffsetPx <= 0f) return 0f
    return (currentPointerY - grabOffsetY).coerceIn(0f, maxOffsetPx)
}

/**
 * Formats a date label for the floating scrubber bubble in Samsung Gallery style.
 * Shows "Today" or "Yesterday" if applicable, otherwise "Month Year" (e.g. "August 2024").
 */
internal fun formatScrubberBubbleDate(headerLabel: String, dateKey: String): String {
    if (headerLabel.equals("Today", ignoreCase = true) || headerLabel.equals("Yesterday", ignoreCase = true)) {
        return headerLabel
    }
    return try {
        val parts = dateKey.split("-")
        if (parts.size >= 2) {
            val year = parts[0]
            val monthInt = parts[1].toIntOrNull() ?: 1
            val monthName = DateFormatSymbols(Locale.getDefault()).months.getOrNull(monthInt - 1)
            if (!monthName.isNullOrBlank()) {
                "$monthName $year"
            } else {
                headerLabel
            }
        } else {
            headerLabel
        }
    } catch (_: Exception) {
        headerLabel
    }
}

/**
 * Samsung Gallery / One UI fast scrollbar and timeline scrubber.
 *
 * Key Behaviors:
 * 1. Does NOT jump on track tap: Touching the right edge outside the thumb never teleports
 *    the gallery or moves the scrollbar. Unconsumed touches pass directly to [LazyGridState]
 *    for fluid normal scrolling with inertia and flinging.
 * 2. Thumb grab without jumping: Only touching directly on the thumb handle engages fast scrolling.
 *    The initial touch grab offset is anchored so the thumb moves smoothly with the finger.
 * 3. Samsung One UI styling: Minimalist 3.5dp capsule when idle, expanding to an 8dp primary pill
 *    when actively dragged, with a floating date bubble displaying Month & Year to the left.
 * 4. Haptic feedback: Subtle tick when grabbing and whenever crossing month/date boundaries.
 * 5. Auto-hide: Thumb appears during grid scrolling and fades out after 1500ms of inactivity.
 */
@Composable
fun TimelineScrubber(
    gridState: LazyGridState,
    timelineItems: List<TimelineItem>,
    modifier: Modifier = Modifier,
) {
    if (timelineItems.size < MINIMUM_ITEMS_FOR_SCRUBBER) return

    val density = LocalDensity.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val thumbHeightPx = with(density) { THUMB_DEFAULT_HEIGHT.toPx() }

    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }

    val scrollProgress by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            if (total <= 1) return@derivedStateOf 0f

            if (!gridState.canScrollBackward) return@derivedStateOf 0f
            if (!gridState.canScrollForward) return@derivedStateOf 1f

            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf 0f

            val firstItem = visibleItems.first()
            val firstIndex = firstItem.index
            val itemHeight = firstItem.size.height.toFloat()
            val offsetFraction = if (itemHeight > 0f) {
                gridState.firstVisibleItemScrollOffset.toFloat() / itemHeight
            } else {
                0f
            }

            val maxIndex = (total - visibleItems.size).coerceAtLeast(1)
            ((firstIndex + offsetFraction) / maxIndex).coerceIn(0f, 1f)
        }
    }

    val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

    val effectiveFraction by remember {
        derivedStateOf {
            if (isDragging && maxOffsetPx > 0f) {
                (dragOffsetY / maxOffsetPx).coerceIn(0f, 1f)
            } else {
                scrollProgress
            }
        }
    }

    val activeTargetIndex by remember {
        derivedStateOf {
            if (isDragging) {
                (effectiveFraction * (timelineItems.size - 1)).roundToInt().coerceIn(0, timelineItems.size - 1)
            } else {
                gridState.firstVisibleItemIndex.coerceIn(0, timelineItems.size - 1)
            }
        }
    }

    // Scrubber date label matching Samsung Gallery (e.g. "August 2024" or "Today")
    val label by remember(timelineItems, activeTargetIndex) {
        derivedStateOf {
            if (timelineItems.isEmpty()) return@derivedStateOf ""
            val idx = activeTargetIndex.coerceIn(0, timelineItems.size - 1)
            for (i in idx downTo 0) {
                val item = timelineItems.getOrNull(i)
                if (item is TimelineItem.Header) {
                    return@derivedStateOf formatScrubberBubbleDate(item.header.label, item.header.dateKey)
                }
            }
            for (i in idx until timelineItems.size) {
                val item = timelineItems.getOrNull(i)
                if (item is TimelineItem.Header) {
                    return@derivedStateOf formatScrubberBubbleDate(item.header.label, item.header.dateKey)
                }
            }
            ""
        }
    }

    // Samsung SESL tactile micro-haptic tick when crossing month/date boundaries while dragging
    LaunchedEffect(label) {
        if (isDragging && label.isNotEmpty()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    var active by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            active = true
        } else {
            delay(1500)
            active = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) 150 else 250),
        label = "scrubber_alpha",
    )

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) THUMB_ACTIVE_WIDTH else THUMB_IDLE_WIDTH,
        animationSpec = tween(120),
        label = "scrubber_thumb_width",
    )

    val thumbColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
        animationSpec = tween(120),
        label = "scrubber_thumb_color",
    )

    if (!active && alpha <= 0.01f) return

    val currentTotalItems by rememberUpdatedState(timelineItems.size)
    val currentMaxOffsetPx by rememberUpdatedState(maxOffsetPx)
    val currentEffectiveFraction by rememberUpdatedState(effectiveFraction)
    val isScrubberActive by rememberUpdatedState(active)
    val currentAlpha by rememberUpdatedState(alpha)

    var lastScrolledIndex by remember { mutableIntStateOf(-1) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    fun scrollToFraction(fraction: Float) {
        val count = currentTotalItems
        if (count <= 1) return
        val targetIndex = (fraction.coerceIn(0f, 1f) * (count - 1)).roundToInt().coerceIn(0, count - 1)
        if (targetIndex != lastScrolledIndex) {
            lastScrolledIndex = targetIndex
            scrollJob?.cancel()
            scrollJob = scope.launch {
                gridState.scrollToItem(targetIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(THUMB_TOUCH_AREA_WIDTH)
            .padding(vertical = 8.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .alpha(alpha)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val maxOff = currentMaxOffsetPx
                    if (maxOff <= 0f) return@awaitEachGesture

                    // If the scrubber is not visible, do not intercept any touch
                    if (!isScrubberActive || currentAlpha <= 0.05f) {
                        return@awaitEachGesture
                    }

                    val touchY = down.position.y
                    val currentThumbY = currentEffectiveFraction * maxOff
                    val verticalTolerancePx = with(density) { THUMB_VERTICAL_TOLERANCE.toPx() }

                    // Samsung Gallery behavior: ONLY intercept if touch is directly on the thumb handle!
                    // If touch is outside the thumb (e.g. user swiping near the right edge to scroll normally,
                    // or tapping the empty track), DO NOT consume the event and exit immediately.
                    // This allows normal grid scrolling, flinging, and photo tapping to work seamlessly.
                    val isHitThumb = isPointInsideThumb(
                        touchY = touchY,
                        thumbTopY = currentThumbY,
                        thumbHeightPx = thumbHeightPx,
                        verticalTolerancePx = verticalTolerancePx,
                    )

                    if (!isHitThumb) {
                        return@awaitEachGesture
                    }

                    // User touched the thumb handle! Engage fast scroll
                    down.consume()
                    isDragging = true
                    active = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    // Anchor grab offset so thumb stays under finger without jumping
                    val grabOffsetY = (touchY - currentThumbY).coerceIn(0f, thumbHeightPx)
                    dragOffsetY = currentThumbY

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()

                        val newThumbY = calculateScrubberThumbY(
                            currentPointerY = change.position.y,
                            grabOffsetY = grabOffsetY,
                            maxOffsetPx = maxOff,
                        )
                        dragOffsetY = newThumbY
                        val fraction = if (maxOff > 0f) newThumbY / maxOff else 0f
                        scrollToFraction(fraction)
                    }
                    isDragging = false
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            },
    ) {
        val currentThumbY = (effectiveFraction * maxOffsetPx)

        // Samsung Gallery floating date badge displayed to the left of the thumb
        if ((isDragging || (active && isScrolling)) && label.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val badgeHeightPx = with(density) { 32.dp.toPx() }
                        val centeredY = (currentThumbY + (thumbHeightPx - badgeHeightPx) / 2f).toInt()
                        val maxY = (trackHeightPx - badgeHeightPx).toInt().coerceAtLeast(0)
                        IntOffset(
                            x = -with(density) { (THUMB_ACTIVE_WIDTH + 16.dp).toPx() }.toInt(),
                            y = centeredY.coerceIn(0, maxY),
                        )
                    }
                    .wrapContentWidth(align = Alignment.End, unbounded = true),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        // Samsung Gallery sleek capsule thumb handle
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, currentThumbY.toInt()) }
                .padding(end = THUMB_END_PADDING)
                .width(thumbWidth)
                .height(THUMB_DEFAULT_HEIGHT)
                .background(
                    color = thumbColor,
                    shape = CircleShape,
                )
                .semantics { contentDescription = "Scroll through the timeline" },
        )
    }
}
