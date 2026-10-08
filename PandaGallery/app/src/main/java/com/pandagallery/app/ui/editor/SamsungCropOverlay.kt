package com.pandagallery.app.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.pandagallery.app.data.editing.CropAspect
import com.pandagallery.app.data.editing.CropShape
import com.pandagallery.app.data.editing.NormalizedCrop
import kotlin.math.abs

/**
 * Samsung One UI 15.9 Clone Crop Overlay.
 *
 * Faithfully reproduces Samsung Gallery's CropArea and CropAreaPainter:
 * - 4 corner L-brackets (26dp length, 2.5dp stroke, subtle shadow).
 * - 4 center edge grab bars (24dp length, 4dp width).
 * - Proportional fixed-ratio resizing when aspect is locked (1:1, 4:3, 16:9, etc.).
 * - Inside-box dragging (panning).
 * - Rule-of-thirds dynamic alignment grid displayed during touch.
 * - Circular / Oval / Rounded crop preview boundary.
 */
@Composable
fun SamsungCropOverlay(
    crop: NormalizedCrop,
    cropAspect: CropAspect,
    cropShape: CropShape,
    sourceAspect: Float,
    onCommit: (NormalizedCrop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var draft by remember(crop) { mutableStateOf(crop.sanitized()) }
    var activeHandle by remember { mutableIntStateOf(-1) }
    var isTouching by remember { mutableStateOf(false) }

    // Target aspect ratio float (or null for free)
    val targetAspect = remember(cropAspect, sourceAspect) {
        when (cropAspect) {
            CropAspect.FREE -> null
            CropAspect.ORIGINAL, CropAspect.FULL -> sourceAspect.coerceIn(0.2f, 5f)
            CropAspect.SQUARE -> 1.0f
            CropAspect.FOUR_THREE -> 4f / 3f
            CropAspect.THREE_FOUR -> 3f / 4f
            CropAspect.SIXTEEN_NINE -> 16f / 9f
            CropAspect.NINE_SIXTEEN -> 9f / 16f
            CropAspect.TWO_THREE -> 2f / 3f
            CropAspect.THREE_TWO -> 3f / 2f
        }
    }

    Canvas(
        modifier = modifier.pointerInput(cropAspect, sourceAspect) {
            detectDragGestures(
                onDragStart = { position ->
                    isTouching = true
                    val left = draft.left * size.width
                    val top = draft.top * size.height
                    val right = draft.right * size.width
                    val bottom = draft.bottom * size.height
                    val midX = (left + right) / 2f
                    val midY = (top + bottom) / 2f

                    val touchRadius = 42.dp.toPx()
                    val touchRadiusSq = touchRadius * touchRadius

                    // 8 handles + inside:
                    // 0: Top-Left, 1: Top-Edge, 2: Top-Right, 3: Right-Edge,
                    // 4: Bottom-Right, 5: Bottom-Edge, 6: Bottom-Left, 7: Left-Edge, 8: Inside
                    val handlePositions = listOf(
                        Offset(left, top),       // 0
                        Offset(midX, top),       // 1
                        Offset(right, top),      // 2
                        Offset(right, midY),     // 3
                        Offset(right, bottom),   // 4
                        Offset(midX, bottom),    // 5
                        Offset(left, bottom),    // 6
                        Offset(left, midY),      // 7
                    )

                    val nearestIndex = handlePositions.indices.minByOrNull { idx ->
                        val diff = handlePositions[idx] - position
                        diff.x * diff.x + diff.y * diff.y
                    } ?: -1

                    val distSq = if (nearestIndex >= 0) {
                        val diff = handlePositions[nearestIndex] - position
                        diff.x * diff.x + diff.y * diff.y
                    } else Float.MAX_VALUE

                    activeHandle = if (distSq <= touchRadiusSq) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        nearestIndex
                    } else if (position.x in left..right && position.y in top..bottom) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        8 // Inside box pan
                    } else {
                        -1
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    if (activeHandle == -1) return@detectDragGestures

                    val dx = dragAmount.x / size.width
                    val dy = dragAmount.y / size.height
                    val minDim = 0.10f

                    if (activeHandle == 8) {
                        // Inside box pan
                        val width = draft.right - draft.left
                        val height = draft.bottom - draft.top
                        val newLeft = (draft.left + dx).coerceIn(0f, 1f - width)
                        val newTop = (draft.top + dy).coerceIn(0f, 1f - height)
                        draft = draft.copy(
                            left = newLeft,
                            top = newTop,
                            right = newLeft + width,
                            bottom = newTop + height,
                        ).sanitized()
                    } else if (targetAspect == null) {
                        // Free aspect ratio drag
                        val px = (change.position.x / size.width).coerceIn(0f, 1f)
                        val py = (change.position.y / size.height).coerceIn(0f, 1f)
                        draft = when (activeHandle) {
                            0 -> draft.copy(left = px.coerceAtMost(draft.right - minDim), top = py.coerceAtMost(draft.bottom - minDim))
                            1 -> draft.copy(top = py.coerceAtMost(draft.bottom - minDim))
                            2 -> draft.copy(right = px.coerceAtLeast(draft.left + minDim), top = py.coerceAtMost(draft.bottom - minDim))
                            3 -> draft.copy(right = px.coerceAtLeast(draft.left + minDim))
                            4 -> draft.copy(right = px.coerceAtLeast(draft.left + minDim), bottom = py.coerceAtLeast(draft.top + minDim))
                            5 -> draft.copy(bottom = py.coerceAtLeast(draft.top + minDim))
                            6 -> draft.copy(left = px.coerceAtMost(draft.right - minDim), bottom = py.coerceAtLeast(draft.top + minDim))
                            7 -> draft.copy(left = px.coerceAtMost(draft.right - minDim))
                            else -> draft
                        }.sanitized()
                    } else {
                        // Fixed aspect ratio resize (width / height = targetAspect)
                        // Normalized aspect conversion: screen aspect = size.width / size.height
                        val screenAspect = size.width / size.height
                        val normAspect = targetAspect / screenAspect

                        when (activeHandle) {
                            0 -> { // Top-Left
                                val newLeft = (draft.left + dx).coerceIn(0f, draft.right - minDim)
                                val newWidth = draft.right - newLeft
                                val newHeight = newWidth / normAspect
                                val newTop = (draft.bottom - newHeight).coerceIn(0f, draft.bottom - minDim)
                                val finalWidth = (draft.bottom - newTop) * normAspect
                                draft = draft.copy(left = (draft.right - finalWidth).coerceAtLeast(0f), top = newTop)
                            }
                            2 -> { // Top-Right
                                val newRight = (draft.right + dx).coerceIn(draft.left + minDim, 1f)
                                val newWidth = newRight - draft.left
                                val newHeight = newWidth / normAspect
                                val newTop = (draft.bottom - newHeight).coerceIn(0f, draft.bottom - minDim)
                                val finalWidth = (draft.bottom - newTop) * normAspect
                                draft = draft.copy(right = (draft.left + finalWidth).coerceAtMost(1f), top = newTop)
                            }
                            4 -> { // Bottom-Right
                                val newRight = (draft.right + dx).coerceIn(draft.left + minDim, 1f)
                                val newWidth = newRight - draft.left
                                val newHeight = newWidth / normAspect
                                val newBottom = (draft.top + newHeight).coerceIn(draft.top + minDim, 1f)
                                val finalWidth = (newBottom - draft.top) * normAspect
                                draft = draft.copy(right = (draft.left + finalWidth).coerceAtMost(1f), bottom = newBottom)
                            }
                            6 -> { // Bottom-Left
                                val newLeft = (draft.left + dx).coerceIn(0f, draft.right - minDim)
                                val newWidth = draft.right - newLeft
                                val newHeight = newWidth / normAspect
                                val newBottom = (draft.top + newHeight).coerceIn(draft.top + minDim, 1f)
                                val finalWidth = (newBottom - draft.top) * normAspect
                                draft = draft.copy(left = (draft.right - finalWidth).coerceAtLeast(0f), bottom = newBottom)
                            }
                            1, 5 -> { // Top or Bottom Edge
                                val newHeight = (if (activeHandle == 1) draft.bottom - (draft.top + dy) else (draft.bottom + dy) - draft.top).coerceIn(minDim, 1f)
                                val newWidth = (newHeight * normAspect).coerceIn(minDim, 1f)
                                val midX = (draft.left + draft.right) / 2f
                                val halfW = newWidth / 2f
                                val adjLeft = (midX - halfW).coerceAtLeast(0f)
                                val adjRight = (midX + halfW).coerceAtMost(1f)
                                if (activeHandle == 1) {
                                    draft = draft.copy(top = (draft.bottom - newHeight).coerceAtLeast(0f), left = adjLeft, right = adjRight)
                                } else {
                                    draft = draft.copy(bottom = (draft.top + newHeight).coerceAtMost(1f), left = adjLeft, right = adjRight)
                                }
                            }
                            3, 7 -> { // Right or Left Edge
                                val newWidth = (if (activeHandle == 3) (draft.right + dx) - draft.left else draft.right - (draft.left + dx)).coerceIn(minDim, 1f)
                                val newHeight = (newWidth / normAspect).coerceIn(minDim, 1f)
                                val midY = (draft.top + draft.bottom) / 2f
                                val halfH = newHeight / 2f
                                val adjTop = (midY - halfH).coerceAtLeast(0f)
                                val adjBottom = (midY + halfH).coerceAtMost(1f)
                                if (activeHandle == 3) {
                                    draft = draft.copy(right = (draft.left + newWidth).coerceAtMost(1f), top = adjTop, bottom = adjBottom)
                                } else {
                                    draft = draft.copy(left = (draft.right - newWidth).coerceAtLeast(0f), top = adjTop, bottom = adjBottom)
                                }
                            }
                        }
                        draft = draft.sanitized()
                    }
                },
                onDragEnd = {
                    isTouching = false
                    activeHandle = -1
                    onCommit(draft)
                },
                onDragCancel = {
                    isTouching = false
                    activeHandle = -1
                },
            )
        }
    ) {
        val left = draft.left * size.width
        val top = draft.top * size.height
        val right = draft.right * size.width
        val bottom = draft.bottom * size.height
        val boxWidth = right - left
        val boxHeight = bottom - top
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f

        // 1. Semi-transparent backdrop outside crop area
        val shade = Color.Black.copy(alpha = 0.55f)
        drawRect(shade, size = Size(size.width, top.coerceAtLeast(0f)))
        drawRect(shade, topLeft = Offset(0f, bottom), size = Size(size.width, (size.height - bottom).coerceAtLeast(0f)))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left.coerceAtLeast(0f), boxHeight.coerceAtLeast(0f)))
        drawRect(shade, topLeft = Offset(right, top), size = Size((size.width - right).coerceAtLeast(0f), boxHeight.coerceAtLeast(0f)))

        // 2. Crop Shape Outline
        val cropRect = Rect(left, top, right, bottom)
        val strokeWidthPx = 2.dp.toPx()

        when (cropShape) {
            CropShape.RECTANGLE -> {
                drawRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(strokeWidthPx),
                )
            }
            CropShape.OVAL -> {
                drawOval(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(strokeWidthPx),
                )
            }
            CropShape.ROUNDED_RECT -> {
                val cornerRadius = 32.dp.toPx()
                val path = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(cropRect, cornerRadius, cornerRadius))
                }
                drawPath(path, Color.White, style = Stroke(strokeWidthPx))
            }
        }

        // 3. Dynamic Rule-of-Thirds Grid (visible during touch or drag)
        if (isTouching || activeHandle != -1) {
            val gridColor = Color.White.copy(alpha = 0.45f)
            val gridStroke = 0.8.dp.toPx()
            repeat(2) { i ->
                val frac = (i + 1) / 3f
                // Vertical lines
                drawLine(
                    color = gridColor,
                    start = Offset(left + boxWidth * frac, top),
                    end = Offset(left + boxWidth * frac, bottom),
                    strokeWidth = gridStroke,
                )
                // Horizontal lines
                drawLine(
                    color = gridColor,
                    start = Offset(left, top + boxHeight * frac),
                    end = Offset(right, top + boxHeight * frac),
                    strokeWidth = gridStroke,
                )
            }
        }

        // 4. Samsung One UI Handles: 4 Corner L-Brackets + 4 Center Edge Bars
        val bracketLength = 26.dp.toPx().coerceAtMost(boxWidth * 0.4f).coerceAtMost(boxHeight * 0.4f)
        val bracketStroke = 3.dp.toPx()
        val handleColor = Color.White
        val shadowColor = Color.Black.copy(alpha = 0.5f)
        val shadowOffset = 1.dp.toPx()

        fun drawLBracket(cx: Float, cy: Float, dirX: Float, dirY: Float) {
            // Drop shadow
            val shadowPath = Path().apply {
                moveTo(cx + dirX * bracketLength + shadowOffset, cy + shadowOffset)
                lineTo(cx + shadowOffset, cy + shadowOffset)
                lineTo(cx + shadowOffset, cy + dirY * bracketLength + shadowOffset)
            }
            drawPath(shadowPath, shadowColor, style = Stroke(bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Miter))

            // White foreground
            val path = Path().apply {
                moveTo(cx + dirX * bracketLength, cy)
                lineTo(cx, cy)
                lineTo(cx, cy + dirY * bracketLength)
            }
            drawPath(path, handleColor, style = Stroke(bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Miter))
        }

        // 4 Corners:
        drawLBracket(left, top, 1f, 1f)          // Top-Left
        drawLBracket(right, top, -1f, 1f)        // Top-Right
        drawLBracket(left, bottom, 1f, -1f)      // Bottom-Left
        drawLBracket(right, bottom, -1f, -1f)    // Bottom-Right

        // 4 Center Edge Bars (horizontal for top/bottom, vertical for left/right):
        val edgeBarLength = 24.dp.toPx().coerceAtMost(boxWidth * 0.35f)
        val edgeBarStroke = 3.5.dp.toPx()

        fun drawEdgeBar(startX: Float, startY: Float, endX: Float, endY: Float) {
            // Shadow
            drawLine(shadowColor, Offset(startX + shadowOffset, startY + shadowOffset), Offset(endX + shadowOffset, endY + shadowOffset), strokeWidth = edgeBarStroke, cap = StrokeCap.Round)
            // White
            drawLine(handleColor, Offset(startX, startY), Offset(endX, endY), strokeWidth = edgeBarStroke, cap = StrokeCap.Round)
        }

        val halfBar = edgeBarLength / 2f
        drawEdgeBar(midX - halfBar, top, midX + halfBar, top)          // Top Edge
        drawEdgeBar(midX - halfBar, bottom, midX + halfBar, bottom)    // Bottom Edge
        drawEdgeBar(left, midY - halfBar, left, midY + halfBar)        // Left Edge
        drawEdgeBar(right, midY - halfBar, right, midY + halfBar)      // Right Edge
    }
}
