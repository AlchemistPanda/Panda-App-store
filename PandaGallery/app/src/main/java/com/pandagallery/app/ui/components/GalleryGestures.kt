package com.pandagallery.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class GridZoomDirection {
    IN,
    OUT,
}

internal class GridZoomAccumulator(
    private val zoomInThreshold: Float = 1.18f,
    private val zoomOutThreshold: Float = 0.82f,
) {
    private var accumulatedZoom = 1f

    fun add(zoomChange: Float): GridZoomDirection? {
        accumulatedZoom *= zoomChange
        val direction = when {
            accumulatedZoom >= zoomInThreshold -> GridZoomDirection.IN
            accumulatedZoom <= zoomOutThreshold -> GridZoomDirection.OUT
            else -> null
        }
        if (direction != null) accumulatedZoom = 1f
        return direction
    }
}

internal fun isCompletedSwipeRight(distancePx: Float, thresholdPx: Float): Boolean =
    distancePx >= thresholdPx

/**
 * Calculates the auto-scroll delta per frame based on proximity to the top or bottom viewport edges.
 */
internal fun calculateAutoScrollDelta(
    currentY: Float,
    viewportHeight: Int,
    thresholdPx: Float,
    maxScrollSpeedPx: Float,
): Float {
    if (viewportHeight <= 0 || thresholdPx <= 0f) return 0f
    return when {
        currentY < thresholdPx -> {
            val ratio = ((thresholdPx - currentY) / thresholdPx).coerceIn(0f, 1f)
            -maxScrollSpeedPx * ratio
        }
        currentY > viewportHeight - thresholdPx -> {
            val ratio = ((currentY - (viewportHeight - thresholdPx)) / thresholdPx).coerceIn(0f, 1f)
            maxScrollSpeedPx * ratio
        }
        else -> 0f
    }
}

/**
 * Calculates a range-based selection set given a start index and current index.
 * If [isSelecting] is true, items in the range are added to [initialSelection].
 * If [isSelecting] is false, items in the range are removed from [initialSelection].
 */
internal fun <T, ID> calculateRangeSelection(
    startIndex: Int,
    currentIndex: Int,
    initialSelection: Set<ID>,
    items: List<T>,
    getItemId: (T) -> ID?,
    isSelecting: Boolean,
): Set<ID> {
    if (startIndex < 0 || currentIndex < 0 || items.isEmpty()) return initialSelection
    val minIdx = minOf(startIndex, currentIndex).coerceIn(0, items.lastIndex)
    val maxIdx = maxOf(startIndex, currentIndex).coerceIn(0, items.lastIndex)
    val rangeIds = (minIdx..maxIdx).mapNotNull { getItemId(items[it]) }.toSet()
    return if (isSelecting) {
        initialSelection + rangeIds
    } else {
        initialSelection - rangeIds
    }
}

/**
 * Returns the index of the grid item under [position], searching exact bounds first,
 * then falling back to the closest item by Euclidean distance.
 */
internal fun LazyGridState.getItemIndexAtPosition(position: Offset): Int? {
    val items = layoutInfo.visibleItemsInfo
    if (items.isEmpty()) return null

    // 1. Exact match within item bounds
    val exact = items.firstOrNull { item ->
        val x = item.offset.x
        val y = item.offset.y
        position.x >= x && position.x <= x + item.size.width &&
            position.y >= y && position.y <= y + item.size.height
    }
    if (exact != null) return exact.index

    // 2. Fallback to nearest visible item
    return items.minByOrNull { item ->
        val centerX = item.offset.x + item.size.width / 2f
        val centerY = item.offset.y + item.size.height / 2f
        val dx = position.x - centerX
        val dy = position.y - centerY
        dx * dx + dy * dy
    }?.index
}

internal fun shouldInitiateDragSelect(
    dx: Float,
    dy: Float,
    touchSlop: Float,
): Boolean {
    val absDx = kotlin.math.abs(dx)
    val absDy = kotlin.math.abs(dy)
    return absDx > touchSlop && absDx > absDy * 1.3f
}

internal fun isVerticalScrollDrag(
    dx: Float,
    dy: Float,
    touchSlop: Float,
): Boolean {
    val absDx = kotlin.math.abs(dx)
    val absDy = kotlin.math.abs(dy)
    return absDy > touchSlop && absDy >= absDx
}

internal fun Modifier.pinchToResizeGrid(
    enabled: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val view = LocalView.current
        val coroutineScope = rememberCoroutineScope()
        val scaleAnim = remember { Animatable(1f) }
        var focalPoint by remember { mutableStateOf(TransformOrigin.Center) }
        val currentOnZoomIn by rememberUpdatedState(onZoomIn)
        val currentOnZoomOut by rememberUpdatedState(onZoomOut)

        this
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                transformOrigin = focalPoint
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val accumulator = GridZoomAccumulator()
                    var currentScale = 1f
                    var triggeredDirection: GridZoomDirection? = null

                    do {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        if (pressedChanges.size >= 2) {
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val sizeWidth = size.width.toFloat()
                            val sizeHeight = size.height.toFloat()
                            if (sizeWidth > 0f && sizeHeight > 0f) {
                                focalPoint = TransformOrigin(
                                    pivotFractionX = (centroid.x / sizeWidth).coerceIn(0f, 1f),
                                    pivotFractionY = (centroid.y / sizeHeight).coerceIn(0f, 1f),
                                )
                            }
                            val zoomDelta = event.calculateZoom()
                            currentScale *= zoomDelta
                            val dir = accumulator.add(zoomDelta)
                            if (dir != null && triggeredDirection == null) {
                                triggeredDirection = dir
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                                } else {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }

                            val clampedTargetScale = when {
                                currentScale > 1.30f -> 1.30f + (currentScale - 1.30f) * 0.2f
                                currentScale < 0.75f -> 0.75f - (0.75f - currentScale) * 0.2f
                                else -> currentScale
                            }

                            coroutineScope.launch {
                                scaleAnim.snapTo(clampedTargetScale)
                            }

                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (triggeredDirection != null) {
                        when (triggeredDirection) {
                            GridZoomDirection.IN -> currentOnZoomIn()
                            GridZoomDirection.OUT -> currentOnZoomOut()
                        }
                    }

                    coroutineScope.launch {
                        scaleAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.75f,
                                stiffness = 350f,
                            ),
                        )
                    }
                }
            }
    }
}


internal fun Modifier.swipeRightToNavigate(
    enabled: Boolean,
    thresholdPx: Float,
    onSwipeRight: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(thresholdPx, onSwipeRight) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onHorizontalDrag = { _, dragAmount -> distance += dragAmount },
            onDragEnd = {
                if (isCompletedSwipeRight(distance, thresholdPx)) onSwipeRight()
                distance = 0f
            },
            onDragCancel = { distance = 0f },
        )
    }
}

/**
 * Adds Samsung Gallery / Google Photos-style drag-to-multi-select gesture to a [LazyVerticalGrid].
 *
 * - When not in selection mode: long-pressing an item starts selection mode with haptic feedback
 *   and enables continuous dragging across items.
 * - When in selection mode: touching and dragging past touch slop immediately sweeps across items to select/deselect.
 * - Auto-scrolls smoothly near the top and bottom of the grid viewport while continuing selection.
 */
internal fun Modifier.dragSelectGrid(
    gridState: LazyGridState,
    isSelectionMode: Boolean,
    isSelectableItem: (Int) -> Boolean = { true },
    onDragStart: (startIndex: Int) -> Unit,
    onDrag: (startIndex: Int, currentIndex: Int) -> Unit,
    onDragEnd: () -> Unit,
    enabled: Boolean = true,
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val view = LocalView.current
        val haptic = LocalHapticFeedback.current
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val autoScrollThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
        val maxScrollSpeedPx = remember(density) { with(density) { 22.dp.toPx() } }

        var autoScrollJob by remember { mutableStateOf<Job?>(null) }
        val currentPointerPos = remember { mutableStateOf<Offset?>(null) }
        val lastReportedIndex = remember { mutableStateOf<Int?>(null) }

        fun performTickHaptic() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        fun startAutoScroll(startIdx: Int) {
            autoScrollJob?.cancel()
            autoScrollJob = coroutineScope.launch {
                while (isActive) {
                    val pos = currentPointerPos.value
                    val viewportHeight = gridState.layoutInfo.viewportSize.height
                    if (pos != null && viewportHeight > 0) {
                        val delta = calculateAutoScrollDelta(
                            currentY = pos.y,
                            viewportHeight = viewportHeight,
                            thresholdPx = autoScrollThresholdPx,
                            maxScrollSpeedPx = maxScrollSpeedPx,
                        )
                        if (delta != 0f) {
                            gridState.scrollBy(delta)
                            val newIdx = gridState.getItemIndexAtPosition(pos)
                            if (newIdx != null && isSelectableItem(newIdx) && newIdx != lastReportedIndex.value) {
                                lastReportedIndex.value = newIdx
                                performTickHaptic()
                                onDrag(startIdx, newIdx)
                            }
                        }
                    }
                    withFrameMillis { }
                }
            }
        }

        fun stopAutoScroll() {
            autoScrollJob?.cancel()
            autoScrollJob = null
            currentPointerPos.value = null
            lastReportedIndex.value = null
        }

        DisposableEffect(Unit) {
            onDispose {
                autoScrollJob?.cancel()
            }
        }

        val currentIsSelectionMode by rememberUpdatedState(isSelectionMode)
        val currentIsSelectableItem by rememberUpdatedState(isSelectableItem)
        val currentOnDragStart by rememberUpdatedState(onDragStart)
        val currentOnDrag by rememberUpdatedState(onDrag)
        val currentOnDragEnd by rememberUpdatedState(onDragEnd)

        pointerInput(gridState) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

                if (currentEvent.changes.count { it.pressed } > 1) {
                    do {
                        val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                    } while (ev.changes.any { it.pressed })
                    return@awaitEachGesture
                }

                val downPos = down.position
                val startIdx = gridState.getItemIndexAtPosition(downPos)
                if (startIdx == null || !currentIsSelectableItem(startIdx)) {
                    do {
                        val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                    } while (ev.changes.any { it.pressed })
                    return@awaitEachGesture
                }

                val touchSlop = viewConfiguration.touchSlop
                var isDragging = false
                var dragStarted = false

                try {
                    if (currentIsSelectionMode) {
                        // In Samsung Gallery (RecyclerView.java mIsLongPressMultiSelection):
                        // Vertical drags scroll the grid normally so users can browse their library in selection mode.
                        // Drag selection is engaged either by long-pressing an item OR dragging horizontally across columns.
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (event.changes.count { it.pressed } > 1) {
                                do {
                                    val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                                } while (ev.changes.any { it.pressed })
                                break
                            }

                            if (!change.pressed) {
                                if (isDragging) change.consume()
                                break
                            }

                            val currentPos = change.position
                            val dx = currentPos.x - downPos.x
                            val dy = currentPos.y - downPos.y
                            val elapsed = change.uptimeMillis - down.uptimeMillis

                            if (!isDragging) {
                                if (elapsed >= longPressTimeout && (currentPos - downPos).getDistance() <= touchSlop * 2f) {
                                    // Held in place: Long press initiated drag-selection!
                                    isDragging = true
                                    dragStarted = true
                                    lastReportedIndex.value = startIdx
                                    currentPointerPos.value = currentPos
                                    change.consume()
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    currentOnDragStart(startIdx)
                                    startAutoScroll(startIdx)
                                } else if (shouldInitiateDragSelect(dx, dy, touchSlop)) {
                                    // Dragged horizontally across columns: start drag selection
                                    isDragging = true
                                    dragStarted = true
                                    lastReportedIndex.value = startIdx
                                    currentPointerPos.value = currentPos
                                    change.consume()
                                    performTickHaptic()
                                    currentOnDragStart(startIdx)
                                    startAutoScroll(startIdx)
                                } else if (isVerticalScrollDrag(dx, dy, touchSlop)) {
                                    // User is scrolling the grid vertically!
                                    // Drain the rest of this gesture without consuming so LazyVerticalGrid scrolls smoothly.
                                    do {
                                        val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    } while (ev.changes.any { it.pressed })
                                    break
                                }
                            } else {
                                change.consume()
                                currentPointerPos.value = currentPos
                                val currentIdx = gridState.getItemIndexAtPosition(currentPos)
                                if (currentIdx != null && currentIsSelectableItem(currentIdx) && currentIdx != lastReportedIndex.value) {
                                    lastReportedIndex.value = currentIdx
                                    performTickHaptic()
                                    currentOnDrag(startIdx, currentIdx)
                                }
                            }
                        }
                    } else {
                        // Normal mode: wait for long press
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress != null && currentIsSelectableItem(startIdx)) {
                            isDragging = true
                            dragStarted = true
                            lastReportedIndex.value = startIdx
                            currentPointerPos.value = longPress.position
                            longPress.consume()
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            currentOnDragStart(startIdx)
                            startAutoScroll(startIdx)

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (event.changes.count { it.pressed } > 1) {
                                    do {
                                        val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    } while (ev.changes.any { it.pressed })
                                    break
                                }

                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }

                                change.consume()
                                val currentPos = change.position
                                currentPointerPos.value = currentPos
                                val currentIdx = gridState.getItemIndexAtPosition(currentPos)
                                if (currentIdx != null && currentIsSelectableItem(currentIdx) && currentIdx != lastReportedIndex.value) {
                                    lastReportedIndex.value = currentIdx
                                    performTickHaptic()
                                    currentOnDrag(startIdx, currentIdx)
                                }
                            }
                        }
                    }
                } finally {
                    if (dragStarted) {
                        view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                        currentOnDragEnd()
                    }
                    stopAutoScroll()
                }
            }
        }
    }
}

/**
 * 4 cardinal directions for viewer swipe navigation.
 * No diagonal or corner gestures are left unhandled.
 */
enum class SwipeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

/**
 * Resolves a swipe displacement vector into strictly one of four orthogonal directions:
 * LEFT, RIGHT, UP (TOP), or DOWN.
 *
 * Diagonal swipes towards corners are deterministically resolved by dominant axis:
 * - If horizontal travel dominates or equals vertical (|dx| >= |dy|), it resolves to LEFT or RIGHT.
 * - If vertical travel dominates (|dy| > |dx|), it resolves to UP or DOWN.
 *
 * Returns null only while within touchSlop to allow taps to be detected without accidental drags.
 */
fun resolveSwipeDirection(
    dx: Float,
    dy: Float,
    touchSlop: Float,
): SwipeDirection? {
    val absDx = kotlin.math.abs(dx)
    val absDy = kotlin.math.abs(dy)
    if (absDx < touchSlop && absDy < touchSlop) return null
    return if (absDx >= absDy) {
        if (dx < 0f) SwipeDirection.LEFT else SwipeDirection.RIGHT
    } else {
        if (dy < 0f) SwipeDirection.UP else SwipeDirection.DOWN
    }
}

