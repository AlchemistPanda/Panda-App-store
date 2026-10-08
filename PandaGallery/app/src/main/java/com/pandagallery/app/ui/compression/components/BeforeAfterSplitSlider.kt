package com.pandagallery.app.ui.compression.components

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Samsung Remaster-style interactive Before/After split comparison canvas (`remaster_viewer_handler_layout.xml`).
 *
 * Synchronously renders the original and compressed frames with:
 * - Real-time vertical draggable split divider with screen-space clipping
 * - Synchronized multi-touch pan and pinch-to-zoom up to 8x for micro-detail pixel peeping
 * - Magnetic 50% center snap with Samsung SESL tactile haptics
 * - Floating frosted glass "Original" vs "Compressed" badges
 * - Floating "Zoom: X • Reset" pill for rapid 1-tap reset
 */
@Composable
fun BeforeAfterSplitSlider(
    originalBitmap: Bitmap,
    compressedBitmap: Bitmap,
    modifier: Modifier = Modifier,
    initialSplitFraction: Float = 0.5f,
    leftLabel: String = "Original",
    rightLabel: String = "Compressed",
    onSplitFractionChanged: (Float) -> Unit = {},
) {
    val view = LocalView.current
    var splitFraction by remember { mutableFloatStateOf(initialSplitFraction.coerceIn(0.05f, 0.95f)) }
    var handleYFraction by remember { mutableFloatStateOf(0.5f) }
    var isDraggingHandle by remember { mutableStateOf(false) }
    var loupePinned by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var lastHapticFraction by remember { mutableFloatStateOf(initialSplitFraction) }

    val isLoupeActive = isDraggingHandle || loupePinned
    val loupeAnimProgress by animateFloatAsState(
        targetValue = if (isLoupeActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
        label = "LoupeAnim",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (zoomScale > 1.1f) {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        } else {
                            zoomScale = 3f
                            val widthPx = size.width.toFloat()
                            val heightPx = size.height.toFloat()
                            val centerX = widthPx / 2f
                            val centerY = heightPx / 2f
                            val maxPanX = (widthPx * (3f - 1f)) / 2f
                            val maxPanY = (heightPx * (3f - 1f)) / 2f
                            panOffset = Offset(
                                x = ((centerX - tapOffset.x) * 2f).coerceIn(-maxPanX, maxPanX),
                                y = ((centerY - tapOffset.y) * 2f).coerceIn(-maxPanY, maxPanY),
                            )
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                )
            }
            .pointerInput(Unit) {
                // Multi-touch pan & pinch-to-zoom up to 8x
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (zoomScale * zoom).coerceIn(1f, 8f)
                    zoomScale = newScale
                    if (newScale > 1.01f) {
                        val widthPx = size.width.toFloat()
                        val heightPx = size.height.toFloat()
                        val maxPanX = (widthPx * (newScale - 1f)) / 2f
                        val maxPanY = (heightPx * (newScale - 1f)) / 2f
                        panOffset = Offset(
                            x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY),
                        )
                    } else {
                        panOffset = Offset.Zero
                    }
                }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val dividerX = widthPx * splitFraction

        val origImage = remember(originalBitmap) { originalBitmap.asImageBitmap() }
        val compImage = remember(compressedBitmap) { compressedBitmap.asImageBitmap() }

        // Render the split canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Calculate destination rect to aspect-fit bitmap into viewport
            val bWidth = originalBitmap.width.toFloat()
            val bHeight = originalBitmap.height.toFloat()
            val scaleFactor = minOf(canvasWidth / bWidth, canvasHeight / bHeight)
            val fittedW = bWidth * scaleFactor
            val fittedH = bHeight * scaleFactor
            val fitLeft = (canvasWidth - fittedW) / 2f
            val fitTop = (canvasHeight - fittedH) / 2f

            val dstSize = IntSize(fittedW.roundToInt(), fittedH.roundToInt())
            val dstOffset = IntOffset(fitLeft.roundToInt(), fitTop.roundToInt())
            val pivot = Offset(canvasWidth / 2f, canvasHeight / 2f)

            // 1. Draw Left Side (Original) clipped to left of divider in screen space
            clipRect(left = 0f, top = 0f, right = dividerX, bottom = canvasHeight) {
                translate(left = panOffset.x, top = panOffset.y) {
                    scale(scale = zoomScale, pivot = pivot) {
                        drawImage(
                            image = origImage,
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }

            // 2. Draw Right Side (Compressed) clipped to right of divider in screen space
            clipRect(left = dividerX, top = 0f, right = canvasWidth, bottom = canvasHeight) {
                translate(left = panOffset.x, top = panOffset.y) {
                    scale(scale = zoomScale, pivot = pivot) {
                        drawImage(
                            image = compImage,
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }

            // 3. Draw vertical divider bar line
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(dividerX, 0f),
                end = Offset(dividerX, canvasHeight),
                strokeWidth = 2.dp.toPx(),
            )

            // 4. Draw 4x Magnifier Loupe with Split Comparison
            if (loupeAnimProgress > 0.01f) {
                val loupeRadius = 70.dp.toPx() * loupeAnimProgress
                val inspectPoint = Offset(dividerX, heightPx * handleYFraction)

                // Position loupe vertically above the inspection handle if space permits, else below
                val preferredLoupeY = inspectPoint.y - 110.dp.toPx()
                val loupeCenterY = if (preferredLoupeY - loupeRadius < 20.dp.toPx()) {
                    inspectPoint.y + 110.dp.toPx()
                } else {
                    preferredLoupeY
                }.coerceIn(loupeRadius + 16.dp.toPx(), canvasHeight - loupeRadius - 16.dp.toPx())

                val loupeCenterX = dividerX.coerceIn(
                    loupeRadius + 16.dp.toPx(),
                    canvasWidth - loupeRadius - 16.dp.toPx(),
                )
                val loupeCenter = Offset(loupeCenterX, loupeCenterY)

                val loupePath = Path().apply {
                    addOval(
                        Rect(
                            left = loupeCenter.x - loupeRadius,
                            top = loupeCenter.y - loupeRadius,
                            right = loupeCenter.x + loupeRadius,
                            bottom = loupeCenter.y + loupeRadius,
                        )
                    )
                }

                // Render 4x magnified contents clipped to circle
                clipPath(loupePath) {
                    // Dark base
                    drawRect(Color.Black)

                    // Left half of loupe (Original) - clipped to screen split line dividerX
                    clipRect(left = 0f, top = 0f, right = dividerX, bottom = canvasHeight) {
                        translate(
                            left = loupeCenter.x - inspectPoint.x,
                            top = loupeCenter.y - inspectPoint.y,
                        ) {
                            scale(scale = 4f, pivot = inspectPoint) {
                                translate(left = panOffset.x, top = panOffset.y) {
                                    scale(scale = zoomScale, pivot = pivot) {
                                        drawImage(
                                            image = origImage,
                                            dstOffset = dstOffset,
                                            dstSize = dstSize,
                                            filterQuality = FilterQuality.None, // Crisp raw pixel sampling
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right half of loupe (Compressed) - clipped to screen split line dividerX
                    clipRect(left = dividerX, top = 0f, right = canvasWidth, bottom = canvasHeight) {
                        translate(
                            left = loupeCenter.x - inspectPoint.x,
                            top = loupeCenter.y - inspectPoint.y,
                        ) {
                            scale(scale = 4f, pivot = inspectPoint) {
                                translate(left = panOffset.x, top = panOffset.y) {
                                    scale(scale = zoomScale, pivot = pivot) {
                                        drawImage(
                                            image = compImage,
                                            dstOffset = dstOffset,
                                            dstSize = dstSize,
                                            filterQuality = FilterQuality.None, // Crisp raw pixel sampling
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Split line continuing through the loupe
                    drawLine(
                        color = Color.White.copy(alpha = 0.95f),
                        start = Offset(dividerX, loupeCenter.y - loupeRadius),
                        end = Offset(dividerX, loupeCenter.y + loupeRadius),
                        strokeWidth = 2.dp.toPx(),
                    )

                    // Subtle inspect crosshair at center of loupe
                    val reticleSize = 6.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(loupeCenter.x - reticleSize, loupeCenter.y),
                        end = Offset(loupeCenter.x + reticleSize, loupeCenter.y),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(loupeCenter.x, loupeCenter.y - reticleSize),
                        end = Offset(loupeCenter.x, loupeCenter.y + reticleSize),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }

                // Loupe Border Rings
                // Drop shadow ring
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f * loupeAnimProgress),
                    radius = loupeRadius + 2.dp.toPx(),
                    center = loupeCenter,
                    style = Stroke(width = 4.dp.toPx()),
                )
                // White outer ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f * loupeAnimProgress),
                    radius = loupeRadius,
                    center = loupeCenter,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
                // One UI subtle inner ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f * loupeAnimProgress),
                    radius = loupeRadius - 1.5.dp.toPx(),
                    center = loupeCenter,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        // Floating Badges: Left and Right labels
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 20.dp),
        ) {
            SplitBadge(text = leftLabel.uppercase(), isOriginal = true)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 20.dp),
        ) {
            SplitBadge(text = rightLabel.uppercase(), isOriginal = false)
        }

        // Floating Bottom Controls: 4x Loupe toggle and Zoom Reset
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (loupePinned) Color.White else Color(0xDD1C1C1E),
                border = BorderStroke(1.dp, if (loupePinned) Color.White else Color.White.copy(alpha = 0.25f)),
                shadowElevation = 6.dp,
                modifier = Modifier.clickable {
                    loupePinned = !loupePinned
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "4x Loupe",
                        tint = if (loupePinned) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (loupePinned) "4x Loupe ON" else "4x Loupe",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (loupePinned) Color.Black else Color.White,
                    )
                }
            }

            // Zoom Reset Floating Pill
            AnimatedVisibility(
                visible = zoomScale > 1.05f,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xDD1C1C1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1fx", zoomScale)} • Reset",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // Center Draggable Handle Pill
        val density = LocalDensity.current
        val handleSizeDp = 44.dp
        val handleHalfPx = with(density) { (handleSizeDp / 2).toPx() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (dividerX - handleHalfPx).roundToInt(),
                        y = (heightPx * handleYFraction - handleHalfPx).roundToInt(),
                    )
                }
                .size(handleSizeDp)
                .pointerInput(widthPx) {
                    detectDragGestures(
                        onDragStart = {
                            isDraggingHandle = true
                        },
                        onDragEnd = {
                            isDraggingHandle = false
                        },
                        onDragCancel = {
                            isDraggingHandle = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (widthPx * splitFraction + dragAmount.x).coerceIn(widthPx * 0.05f, widthPx * 0.95f)
                            var newFraction = newX / widthPx

                            val newY = (heightPx * handleYFraction + dragAmount.y).coerceIn(heightPx * 0.15f, heightPx * 0.85f)
                            handleYFraction = newY / heightPx

                            // Magnetic snap to 50% center with tactile haptic tick
                            if (abs(newFraction - 0.5f) < 0.035f) {
                                newFraction = 0.5f
                                if (abs(lastHapticFraction - 0.5f) >= 0.035f) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            lastHapticFraction = newFraction
                            splitFraction = newFraction
                            onSplitFractionChanged(newFraction)
                        }
                    )
                }
                .background(Color(0xFF2C2C2E).copy(alpha = 0.85f), CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = "Slide to compare",
                        tint = Color(0xFF1C1C1E),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitBadge(text: String, isOriginal: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E1E).copy(alpha = 0.75f),
        contentColor = if (isOriginal) Color(0xFFE0E0E0) else Color(0xFF80E386),
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}
