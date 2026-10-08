package com.pandagallery.app.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.magnifier
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pandagallery.app.data.editing.DocumentCorners
import com.pandagallery.app.data.editing.NormalizedPoint
import kotlin.math.hypot

/**
 * Samsung One UI 6.1 / One UI 7 Document Scan Interactive Overlay.
 *
 * Provides:
 * - Translucent cyan quadrilateral highlight over the paper area.
 * - 4 interactive draggable corner pins with haptic click feedback.
 * - Proportional quadrilateral edge lines with subtle boundary glow.
 * - Dynamic 4x circular magnifier loupe that pops up above the touch point when dragging,
 *   allowing sub-pixel alignment of corners to receipt/page edges.
 * - Translation support for dragging the entire quadrilateral together.
 */
private val OneUiScanYellow = Color(0xFFFFC700)

@Composable
fun DocumentScanOverlay(
    corners: DocumentCorners,
    onCornersChange: (DocumentCorners) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current

    var draftCorners by remember(corners) { mutableStateOf(corners) }
    var activeHandle by remember { mutableIntStateOf(-1) } // 0..3 for corners, 4 for inside body
    var isDragging by remember { mutableStateOf(false) }
    var dragTouchOffset by remember { mutableStateOf<Offset?>(null) }

    // Pulsing halo animation for active handle
    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    // Loupe offset: floats 85dp above touch point
    val loupeYOffsetPx = with(density) { -85.dp.toPx() }

    // Determine magnifier modifier if dragging a specific corner
    val magnifierModifier = if (isDragging && activeHandle in 0..3 && dragTouchOffset != null) {
        val touch = dragTouchOffset!!
        Modifier.magnifier(
            sourceCenter = { touch },
            magnifierCenter = {
                Offset(touch.x, (touch.y + loupeYOffsetPx).coerceAtLeast(60.dp.value))
            },
            zoom = 2.75f,
            size = DpSize(110.dp, 110.dp),
            cornerRadius = 55.dp,
            elevation = 10.dp,
        )
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .then(magnifierModifier)
            .pointerInput(draftCorners) {
                detectDragGestures(
                    onDragStart = { position ->
                        isDragging = true
                        dragTouchOffset = position
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                        val w = size.width.toFloat()
                        val h = size.height.toFloat()

                        val p0 = Offset(draftCorners.topLeft.x * w, draftCorners.topLeft.y * h)
                        val p1 = Offset(draftCorners.topRight.x * w, draftCorners.topRight.y * h)
                        val p2 = Offset(draftCorners.bottomRight.x * w, draftCorners.bottomRight.y * h)
                        val p3 = Offset(draftCorners.bottomLeft.x * w, draftCorners.bottomLeft.y * h)

                        val cornerOffsets = listOf(p0, p1, p2, p3)
                        val touchRadiusPx = 48.dp.toPx()

                        // Check nearest corner
                        var foundCorner = -1
                        var minDistance = Float.MAX_VALUE
                        cornerOffsets.forEachIndexed { index, cornerPos ->
                            val dist = hypot(cornerPos.x - position.x, cornerPos.y - position.y)
                            if (dist < touchRadiusPx && dist < minDistance) {
                                minDistance = dist
                                foundCorner = index
                            }
                        }

                        activeHandle = if (foundCorner != -1) {
                            foundCorner
                        } else {
                            // Check if inside quadrilateral for body translation
                            if (isPointInsideQuad(position, p0, p1, p2, p3)) 4 else -1
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newTouch = (dragTouchOffset ?: change.position) + dragAmount
                        dragTouchOffset = newTouch

                        val w = size.width.toFloat()
                        val h = size.height.toFloat()

                        if (activeHandle in 0..3) {
                            val normX = (newTouch.x / w).coerceIn(0.01f, 0.99f)
                            val normY = (newTouch.y / h).coerceIn(0.01f, 0.99f)
                            val updated = draftCorners.withUpdatedCorner(
                                activeHandle,
                                NormalizedPoint(normX, normY),
                            )
                            draftCorners = updated
                            onCornersChange(updated)
                        } else if (activeHandle == 4) {
                            // Translate entire quadrilateral
                            val dx = dragAmount.x / w
                            val dy = dragAmount.y / h

                            val minX = minOf(draftCorners.topLeft.x, draftCorners.topRight.x, draftCorners.bottomRight.x, draftCorners.bottomLeft.x)
                            val maxX = maxOf(draftCorners.topLeft.x, draftCorners.topRight.x, draftCorners.bottomRight.x, draftCorners.bottomLeft.x)
                            val minY = minOf(draftCorners.topLeft.y, draftCorners.topRight.y, draftCorners.bottomRight.y, draftCorners.bottomLeft.y)
                            val maxY = maxOf(draftCorners.topLeft.y, draftCorners.topRight.y, draftCorners.bottomRight.y, draftCorners.bottomLeft.y)

                            val clampedDx = dx.coerceIn(-minX, 1f - maxX)
                            val clampedDy = dy.coerceIn(-minY, 1f - maxY)

                            val shifted = DocumentCorners(
                                topLeft = NormalizedPoint(draftCorners.topLeft.x + clampedDx, draftCorners.topLeft.y + clampedDy),
                                topRight = NormalizedPoint(draftCorners.topRight.x + clampedDx, draftCorners.topRight.y + clampedDy),
                                bottomRight = NormalizedPoint(draftCorners.bottomRight.x + clampedDx, draftCorners.bottomRight.y + clampedDy),
                                bottomLeft = NormalizedPoint(draftCorners.bottomLeft.x + clampedDx, draftCorners.bottomLeft.y + clampedDy),
                            )
                            draftCorners = shifted
                            onCornersChange(shifted)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        activeHandle = -1
                        dragTouchOffset = null
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    },
                    onDragCancel = {
                        isDragging = false
                        activeHandle = -1
                        dragTouchOffset = null
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height

        val p0 = Offset(draftCorners.topLeft.x * w, draftCorners.topLeft.y * h)
        val p1 = Offset(draftCorners.topRight.x * w, draftCorners.topRight.y * h)
        val p2 = Offset(draftCorners.bottomRight.x * w, draftCorners.bottomRight.y * h)
        val p3 = Offset(draftCorners.bottomLeft.x * w, draftCorners.bottomLeft.y * h)

        val quadPath = Path().apply {
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }

        // 1. Semi-transparent yellow wash over detected page area
        drawPath(
            path = quadPath,
            color = OneUiScanYellow.copy(alpha = 0.12f),
        )

        // 2. Yellow boundary stroke with subtle drop glow
        drawPath(
            path = quadPath,
            color = OneUiScanYellow.copy(alpha = 0.4f),
            style = Stroke(
                width = 4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawPath(
            path = quadPath,
            color = OneUiScanYellow,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // 3. Draw edge midpoint hash marks
        val mid01 = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
        val mid12 = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        val mid23 = Offset((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f)
        val mid30 = Offset((p3.x + p0.x) / 2f, (p3.y + p0.y) / 2f)
        val midPoints = listOf(mid01, mid12, mid23, mid30)

        midPoints.forEach { mid ->
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = 3.dp.toPx(),
                center = mid,
            )
            drawCircle(
                color = OneUiScanYellow,
                radius = 1.8.dp.toPx(),
                center = mid,
            )
        }

        // 4. Draw 4 Corner Pins
        val cornersList = listOf(p0, p1, p2, p3)
        cornersList.forEachIndexed { index, cornerPos ->
            val isActive = (activeHandle == index)

            if (isActive) {
                // Pulsing glow halo
                drawCircle(
                    color = OneUiScanYellow.copy(alpha = pulseAlpha * 0.4f),
                    radius = 24.dp.toPx(),
                    center = cornerPos,
                )
                // Crosshair indicators
                val armLen = 14.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = Offset(cornerPos.x - armLen, cornerPos.y),
                    end = Offset(cornerPos.x + armLen, cornerPos.y),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawLine(
                    color = Color.White,
                    start = Offset(cornerPos.x, cornerPos.y - armLen),
                    end = Offset(cornerPos.x, cornerPos.y + armLen),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            // Outer yellow border ring
            drawCircle(
                color = OneUiScanYellow,
                radius = if (isActive) 14.dp.toPx() else 11.dp.toPx(),
                center = cornerPos,
            )

            // Inner white core dot
            drawCircle(
                color = Color.White,
                radius = if (isActive) 8.dp.toPx() else 6.5.dp.toPx(),
                center = cornerPos,
            )

            // Center yellow pinpoint
            drawCircle(
                color = OneUiScanYellow,
                radius = 2.5.dp.toPx(),
                center = cornerPos,
            )
        }
    }
}

/**
 * Fast cross-product point-in-polygon test for convex quadrilateral.
 */
private fun isPointInsideQuad(pt: Offset, p0: Offset, p1: Offset, p2: Offset, p3: Offset): Boolean {
    fun sign(p1: Offset, p2: Offset, p3: Offset): Float {
        return (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y)
    }

    val d1 = sign(pt, p0, p1)
    val d2 = sign(pt, p1, p2)
    val d3 = sign(pt, p2, p3)
    val d4 = sign(pt, p3, p0)

    val hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0) || (d4 < 0)
    val hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0) || (d4 > 0)

    return !(hasNeg && hasPos)
}
