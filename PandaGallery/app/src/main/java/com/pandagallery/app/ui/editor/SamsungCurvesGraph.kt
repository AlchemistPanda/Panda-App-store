package com.pandagallery.app.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandagallery.app.data.editing.CurveChannel
import com.pandagallery.app.data.editing.CurveControlPoint
import com.pandagallery.app.data.editing.CurvePreset

private val OneUiCardBg = Color(0xFF18181A)
private val OneUiBorderColor = Color(0xFF2C2C2E)

/**
 * Interactive 2D Cartesian Spline Graph for Samsung Tone Curves.
 */
@Composable
fun SamsungCurvesGraph(
    selectedChannel: CurveChannel,
    curvePoints: Map<CurveChannel, List<CurveControlPoint>>,
    onChannelChange: (CurveChannel) -> Unit,
    onPointsChange: (CurveChannel, List<CurveControlPoint>) -> Unit,
    onPresetSelect: (CurvePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val channelColor = Color(selectedChannel.colorHex)

    // Current points for selected channel, or default 5-point linear diagonal
    val defaultPoints = remember {
        listOf(
            CurveControlPoint(0f, 0f),
            CurveControlPoint(0.25f, 0.25f),
            CurveControlPoint(0.50f, 0.50f),
            CurveControlPoint(0.75f, 0.75f),
            CurveControlPoint(1.0f, 1.0f),
        )
    }

    val activePoints = curvePoints[selectedChannel]?.takeIf { it.size >= 2 } ?: defaultPoints
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var touchValues by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Channel Selector Chips (RGB, Red, Green, Blue)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurveChannel.entries.forEach { channel ->
                val isSelected = channel == selectedChannel
                val cColor = Color(channel.colorHex)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) cColor.copy(alpha = 0.25f) else OneUiCardBg,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else 0.8.dp,
                        color = if (isSelected) cColor else OneUiBorderColor,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onChannelChange(channel) },
                ) {
                    Text(
                        text = channel.displayName,
                        color = if (isSelected) cColor else Color(0xFFC7C7CC),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    )
                }
            }

            // Reset current channel curve
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPointsChange(selectedChannel, defaultPoints)
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = "Reset Curve",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // 2D Cartesian Spline Graph Box
        Box(
            modifier = Modifier
                .size(width = 240.dp, height = 170.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141416))
                .border(1.dp, OneUiBorderColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .pointerInput(selectedChannel, activePoints) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val w = size.width
                                val h = size.height
                                val touchRad = 28.dp.toPx()
                                val touchRadSq = touchRad * touchRad

                                draggingIndex = activePoints.indices.minByOrNull { idx ->
                                    val pt = activePoints[idx]
                                    val px = pt.x * w
                                    val py = (1f - pt.y) * h
                                    val dx = px - offset.x
                                    val dy = py - offset.y
                                    dx * dx + dy * dy
                                }?.takeIf { idx ->
                                    val pt = activePoints[idx]
                                    val px = pt.x * w
                                    val py = (1f - pt.y) * h
                                    val dx = px - offset.x
                                    val dy = py - offset.y
                                    dx * dx + dy * dy <= touchRadSq
                                } ?: -1

                                if (draggingIndex != -1) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    val pt = activePoints[draggingIndex]
                                    touchValues = ((pt.x * 255).toInt()) to ((pt.y * 255).toInt())
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (draggingIndex == -1) return@detectDragGestures

                                val normX = (change.position.x / size.width).coerceIn(0f, 1f)
                                val normY = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)

                                // Constraint: endpoints fixed on x = 0 and x = 1
                                val clampedX = when (draggingIndex) {
                                    0 -> 0f
                                    activePoints.lastIndex -> 1f
                                    else -> {
                                        val minX = activePoints[draggingIndex - 1].x + 0.05f
                                        val maxX = activePoints[draggingIndex + 1].x - 0.05f
                                        normX.coerceIn(minX, maxX)
                                    }
                                }

                                val updated = activePoints.toMutableList()
                                updated[draggingIndex] = CurveControlPoint(clampedX, normY)
                                onPointsChange(selectedChannel, updated)
                                touchValues = ((clampedX * 255).toInt()) to ((normY * 255).toInt())
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                touchValues = null
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                touchValues = null
                            },
                        )
                    }
            ) {
                val w = size.width
                val h = size.height

                // 1. Coordinate Grid Lines (25%, 50%, 75%)
                val gridColor = Color(0xFF2C2C2E)
                val gridStroke = 0.8.dp.toPx()
                listOf(0.25f, 0.50f, 0.75f).forEach { fraction ->
                    drawLine(gridColor, Offset(w * fraction, 0f), Offset(w * fraction, h), strokeWidth = gridStroke)
                    drawLine(gridColor, Offset(0f, h * fraction), Offset(w, h * fraction), strokeWidth = gridStroke)
                }

                // 2. Linear diagonal baseline (dashed or subtle)
                drawLine(
                    color = Color.White.copy(alpha = 0.18f),
                    start = Offset(0f, h),
                    end = Offset(w, 0f),
                    strokeWidth = 1.dp.toPx(),
                )

                // 3. Render Spline Curve Path
                val curvePath = Path()
                val stepCount = 60
                val sorted = activePoints.sortedBy { it.x }

                for (s in 0..stepCount) {
                    val t = s.toFloat() / stepCount
                    // Find segment
                    val nextIdx = sorted.indexOfFirst { it.x >= t }.let { if (it <= 0) 1 else it }
                    val prev = sorted[nextIdx - 1]
                    val next = sorted[nextIdx]
                    val dx = (next.x - prev.x).coerceAtLeast(0.0001f)
                    val localT = ((t - prev.x) / dx).coerceIn(0f, 1f)
                    val smooth = localT * localT * (3f - 2f * localT)
                    val y = prev.y + (next.y - prev.y) * smooth

                    val px = t * w
                    val py = (1f - y) * h

                    if (s == 0) curvePath.moveTo(px, py) else curvePath.lineTo(px, py)
                }

                drawPath(
                    path = curvePath,
                    color = channelColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )

                // 4. Render Control Points (Circles)
                activePoints.forEachIndexed { index, pt ->
                    val cx = pt.x * w
                    val cy = (1f - pt.y) * h
                    val isDragging = index == draggingIndex

                    // Outer halo if dragging
                    if (isDragging) {
                        drawCircle(
                            color = channelColor.copy(alpha = 0.35f),
                            radius = 12.dp.toPx(),
                            center = Offset(cx, cy),
                        )
                    }

                    // White outer ring
                    drawCircle(
                        color = Color.White,
                        radius = 5.5.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                    // Filled colored center
                    drawCircle(
                        color = channelColor,
                        radius = 4.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                }
            }

            // Real-time In/Out Tooltip Badge
            touchValues?.let { (inVal, outVal) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Color.White.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = "In: $inVal  Out: $outVal",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // Curve Preset Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Presets:",
                color = Color(0xFF8E8E93),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                modifier = Modifier.padding(end = 2.dp),
            )
            CurvePreset.entries.forEach { preset ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OneUiCardBg,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, OneUiBorderColor),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onPresetSelect(preset)
                        },
                ) {
                    Text(
                        text = preset.displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
