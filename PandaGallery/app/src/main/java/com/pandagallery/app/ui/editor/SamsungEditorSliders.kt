package com.pandagallery.app.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

private val OneUiAccentBlue = Color(0xFF2C6CF5)
private val OneUiTrackBackground = Color(0xFF2C2C2E)
private val OneUiTickColor = Color(0xFF555558)

/**
 * Professional Samsung One UI precision slider.
 * Supports:
 * - Bipolar filling from center (0) for -100..+100 parameters.
 * - Unipolar filling from left (0) for 0..100 parameters.
 * - Central neutral tick indicator for bipolar sliders.
 * - Value badge with smooth animation and one-tap reset.
 * - Tactile haptic feedback when crossing neutral point (0).
 */
@Composable
fun SamsungOneUiSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = OneUiAccentBlue,
    valueFormatter: (Float) -> String = { v ->
        if (range.start < 0f) {
            val intVal = (v * 100).roundToInt()
            if (intVal > 0) "+$intVal" else "$intVal"
        } else {
            "${(v * 100).roundToInt()}%"
        }
    },
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit = { onValueChange(0f) },
) {
    val view = LocalView.current
    val isBipolar = range.start < 0f
    val isModified = if (isBipolar) abs(value) > 0.005f else abs(value - range.start) > 0.005f
    var prevSign by remember { mutableIntStateOf(if (value > 0.02f) 1 else if (value < -0.02f) -1 else 0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        // Top Header: Label on left, Reset & Value Badge on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isModified) Color.White else Color(0xFF9E9EA2),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = label,
                    color = if (isModified) Color.White else Color(0xFFC7C7CC),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = if (isModified) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimatedVisibility(
                    visible = isModified,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(100)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = "Reset",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onReset()
                            },
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isModified) accentColor.copy(alpha = 0.18f) else Color(0xFF222224),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = if (isModified) accentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (isModified) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onReset()
                            }
                        },
                ) {
                    Text(
                        text = valueFormatter(value),
                        color = if (isModified) accentColor else Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // Custom Slider Track Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(range, isBipolar) {
                        fun updateFromPosition(x: Float) {
                            val fraction = (x / size.width).coerceIn(0f, 1f)
                            val rawValue = range.start + fraction * (range.endInclusive - range.start)
                            val finalValue = if (isBipolar && abs(rawValue) < 0.015f) 0f else rawValue
                            val currentSign = if (finalValue > 0.02f) 1 else if (finalValue < -0.02f) -1 else 0
                            if (currentSign != prevSign && (currentSign == 0 || prevSign == 0)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            prevSign = currentSign
                            onValueChange(finalValue)
                        }

                        detectTapGestures { offset ->
                            updateFromPosition(offset.x)
                        }
                    }
                    .pointerInput(range, isBipolar) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            val rawValue = range.start + fraction * (range.endInclusive - range.start)
                            val finalValue = if (isBipolar && abs(rawValue) < 0.015f) 0f else rawValue
                            val currentSign = if (finalValue > 0.02f) 1 else if (finalValue < -0.02f) -1 else 0
                            if (currentSign != prevSign && (currentSign == 0 || prevSign == 0)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            prevSign = currentSign
                            onValueChange(finalValue)
                        }
                    },
            ) {
                val trackHeight = 7.dp.toPx()
                val thumbRadius = 10.dp.toPx()
                val centerY = size.height / 2f
                val paddingX = thumbRadius

                val availableWidth = size.width - paddingX * 2
                val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                val thumbX = paddingX + fraction * availableWidth

                // Inactive Track Background
                drawRoundRect(
                    color = OneUiTrackBackground,
                    topLeft = Offset(paddingX, centerY - trackHeight / 2f),
                    size = Size(availableWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                )

                // Active Fill
                if (isBipolar) {
                    val zeroFraction = (0f - range.start) / (range.endInclusive - range.start)
                    val zeroX = paddingX + zeroFraction * availableWidth

                    if (thumbX > zeroX) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(zeroX, centerY - trackHeight / 2f),
                            size = Size(thumbX - zeroX, trackHeight),
                            cornerRadius = CornerRadius(0f, 0f),
                        )
                    } else if (thumbX < zeroX) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(thumbX, centerY - trackHeight / 2f),
                            size = Size(zeroX - thumbX, trackHeight),
                            cornerRadius = CornerRadius(0f, 0f),
                        )
                    }

                    // Center Neutral Indicator Tick
                    drawCircle(
                        color = if (isModified) Color.White.copy(alpha = 0.8f) else OneUiTickColor,
                        radius = 2.2.dp.toPx(),
                        center = Offset(zeroX, centerY),
                    )
                } else {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(paddingX, centerY - trackHeight / 2f),
                        size = Size((thumbX - paddingX).coerceAtLeast(0f), trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                    )
                }

                // Thumb Outer Shadow / Glow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = thumbRadius + 2.dp.toPx(),
                    center = Offset(thumbX, centerY + 1.dp.toPx()),
                )

                // Thumb White Outer Body
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbX, centerY),
                )

                // Thumb Inner Accent Indicator
                drawCircle(
                    color = if (isModified) accentColor else Color(0xFF333333),
                    radius = thumbRadius * 0.45f,
                    center = Offset(thumbX, centerY),
                )
            }
        }
    }
}

/**
 * Samsung Gallery Straighten Rotary Dial.
 * Features:
 * - Linear horizontal ruler dial (-45° to +45°).
 * - Hash marks at 1°, 5°, and major numbers at 15°.
 * - Fixed center needle in One UI Blue / Accent.
 * - Magnetic 0° center snap with haptic feedback.
 */
@Composable
fun SamsungRulerDialSlider(
    angleDegrees: Float,
    modifier: Modifier = Modifier,
    minAngle: Float = -45f,
    maxAngle: Float = 45f,
    onAngleChange: (Float) -> Unit,
    onReset: () -> Unit = { onAngleChange(0f) },
) {
    val view = LocalView.current
    val isModified = abs(angleDegrees) > 0.05f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Value Badge & Reset
        Row(
            modifier = Modifier.padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isModified) OneUiAccentBlue.copy(alpha = 0.18f) else Color(0xFF1E1E20),
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.8.dp,
                    color = if (isModified) OneUiAccentBlue.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (isModified) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onReset()
                        }
                    },
            ) {
                Text(
                    text = String.format("%.1f°", angleDegrees),
                    color = if (isModified) OneUiAccentBlue else Color(0xFFC7C7CC),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (isModified) {
                Text(
                    text = "Reset",
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onReset()
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        // Interactive Ruler Dial
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(minAngle, maxAngle) {
                        var lastHapticDeg = angleDegrees.roundToInt()
                        detectDragGestures(
                            onDragStart = {
                                lastHapticDeg = angleDegrees.roundToInt()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // Drag sensitivity: 1px = ~0.22 degrees
                                val deltaDegrees = -dragAmount.x * 0.22f
                                var newAngle = (angleDegrees + deltaDegrees).coerceIn(minAngle, maxAngle)
                                // Magnetic snap to 0° within 0.4°
                                if (abs(newAngle) < 0.4f) {
                                    if (angleDegrees != 0f) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    }
                                    newAngle = 0f
                                    lastHapticDeg = 0
                                } else {
                                    val currentDeg = newAngle.roundToInt()
                                    if (currentDeg != lastHapticDeg) {
                                        lastHapticDeg = currentDeg
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                                onAngleChange(newAngle)
                            },
                        )
                    },
            ) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val pxPerDegree = 9.dp.toPx()

                // Draw edge gradient fading
                val fadeWidth = size.width * 0.2f

                // Draw tick marks
                val startDeg = (angleDegrees - (centerX / pxPerDegree)).toInt() - 2
                val endDeg = (angleDegrees + (centerX / pxPerDegree)).toInt() + 2

                for (deg in startDeg..endDeg) {
                    if (deg < minAngle || deg > maxAngle) continue

                    val x = centerX + (deg - angleDegrees) * pxPerDegree
                    if (x < 0 || x > size.width) continue

                    // Calculate distance from center for opacity falloff
                    val distFromCenter = abs(x - centerX) / (size.width / 2f)
                    val alpha = (1f - distFromCenter * distFromCenter).coerceIn(0.1f, 1f)

                    when {
                        deg % 15 == 0 -> {
                            // Major tick with degree label
                            drawLine(
                                color = Color.White.copy(alpha = 0.85f * alpha),
                                start = Offset(x, centerY - 10.dp.toPx()),
                                end = Offset(x, centerY + 10.dp.toPx()),
                                strokeWidth = 1.8.dp.toPx(),
                            )
                        }
                        deg % 5 == 0 -> {
                            // Medium tick
                            drawLine(
                                color = Color.White.copy(alpha = 0.55f * alpha),
                                start = Offset(x, centerY - 6.dp.toPx()),
                                end = Offset(x, centerY + 6.dp.toPx()),
                                strokeWidth = 1.2.dp.toPx(),
                            )
                        }
                        else -> {
                            // Small tick
                            drawLine(
                                color = Color.White.copy(alpha = 0.28f * alpha),
                                start = Offset(x, centerY - 3.5.dp.toPx()),
                                end = Offset(x, centerY + 3.5.dp.toPx()),
                                strokeWidth = 0.9.dp.toPx(),
                            )
                        }
                    }
                }

                // Fixed Center Indicator Needle (Samsung One UI Blue)
                drawLine(
                    color = OneUiAccentBlue,
                    start = Offset(centerX, centerY - 14.dp.toPx()),
                    end = Offset(centerX, centerY + 14.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx(),
                )

                drawCircle(
                    color = OneUiAccentBlue,
                    radius = 2.5.dp.toPx(),
                    center = Offset(centerX, centerY + 16.dp.toPx()),
                )
            }
        }
    }
}
