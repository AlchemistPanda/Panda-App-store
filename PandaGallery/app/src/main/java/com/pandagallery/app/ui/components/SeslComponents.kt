package com.pandagallery.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

// One UI Standard Palette Tokens
val OneUiBlue = Color(0xFF2C6CF5) // Samsung One UI 6.1 / 7 system accent
val OneUiTrackInactive = Color(0xFF3A3A3D)
val OneUiThumbInactive = Color(0xFF9E9EA2)
// Pinned-dark panel tones for chrome that must stay dark in both themes (compression studio,
// media overlays). Kept in step with the AMOLED ladder in `theme/Color.kt` — at the old
// #121212 / #1E1E1E these read as grey cards once the surrounding ground went true black.
val OneUiContainerLow = Color(0xFF0B0B0D)
val OneUiContainerHigh = Color(0xFF16161A)

/**
 * Authentic Samsung One UI SESL Switch / Toggle.
 * Features:
 * - Capsule track (50dp x 28dp) with rounded edges.
 * - Smooth spring recoil thumb translation with medium bounce.
 * - Dynamic color transitions matching Samsung One UI 6.1 / 7.
 * - Micro-haptic tactile feedback on toggle.
 */
@Composable
fun SeslSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeTrackColor: Color = OneUiBlue,
    inactiveTrackColor: Color = OneUiTrackInactive,
    thumbColor: Color = Color.White,
) {
    val view = LocalView.current
    val trackWidth = 50.dp
    val trackHeight = 28.dp
    val thumbSize = 22.dp
    val padding = 3.dp

    val trackColor by animateColorAsState(
        targetValue = if (checked) activeTrackColor else inactiveTrackColor,
        animationSpec = tween(durationMillis = 200),
        label = "SeslSwitchTrackColor",
    )

    val thumbOffsetFraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "SeslSwitchThumbOffset",
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) trackColor else trackColor.copy(alpha = 0.5f))
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.toggleable(
                        value = checked,
                        onValueChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onCheckedChange(it)
                        },
                        role = Role.Switch,
                        interactionSource = interactionSource,
                        indication = null,
                    )
                } else Modifier
            )
            .padding(padding),
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxTravel = trackWidth - trackHeight // 50 - 28 = 22dp
        Box(
            modifier = Modifier
                .offset(x = maxTravel * thumbOffsetFraction)
                .size(thumbSize)
                .shadow(
                    elevation = if (checked) 4.dp else 2.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.6f),
                )
                .background(if (enabled) thumbColor else thumbColor.copy(alpha = 0.6f), CircleShape)
        )
    }
}

/**
 * Authentic Samsung One UI SESL Circular Checkbox.
 * Features:
 * - Circular outline boundary (24dp).
 * - Smooth vector stroke checkmark animation when checked.
 * - Samsung One UI active fill and tactile click haptics.
 */
@Composable
fun SeslCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = OneUiBlue,
    inactiveBorderColor: Color = Color.White.copy(alpha = 0.7f),
    size: Dp = 24.dp,
    /** Renders a dash instead of a tick, for "some but not all of this group is selected". */
    indeterminate: Boolean = false,
) {
    val view = LocalView.current
    val progress by animateFloatAsState(
        targetValue = if (checked || indeterminate) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "SeslCheckboxProgress",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onCheckedChange(!checked)
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.toPx() / 2f
            val center = Offset(radius, radius)

            if (progress > 0f) {
                // Active Solid Fill
                drawCircle(
                    color = activeColor,
                    radius = radius * progress,
                    center = center,
                )

                // White checkmark, or a dash for the partial state
                val checkPath = Path().apply {
                    if (indeterminate && !checked) {
                        moveTo(radius * 0.6f, radius)
                        lineTo(radius * 1.4f, radius)
                    } else {
                        val startX = radius * 0.65f
                        val startY = radius * 1.0f
                        val midX = radius * 0.9f
                        val midY = radius * 1.25f
                        val endX = radius * 1.4f
                        val endY = radius * 0.72f

                        moveTo(startX, startY)
                        lineTo(midX, midY)
                        lineTo(endX, endY)
                    }
                }

                drawPath(
                    path = checkPath,
                    color = Color.White,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            } else {
                // Inactive Border
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = inactiveBorderColor,
                    radius = radius - 1.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * Authentic Samsung One UI SESL Precision Slider.
 * Features:
 * - Bipolar filling from center (0) for -100..+100 parameters.
 * - Unipolar filling from left (0) for 0..100 parameters.
 * - Central neutral tick indicator for bipolar sliders.
 * - Value badge with smooth animation and one-tap reset.
 * - Magnetic 0°/0% snap with haptic feedback tick.
 */
@Composable
fun SeslSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = OneUiBlue,
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
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        // Header Row: Label & Icon on left, Reset & Value Badge on right
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
                    enter = fadeIn(tween(100)) + scaleIn(tween(100)),
                    exit = fadeOut(tween(100)) + scaleOut(tween(100)),
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
                    color = OneUiTrackInactive,
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
                        color = if (isModified) Color.White.copy(alpha = 0.8f) else Color(0xFF555558),
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
 * Authentic Samsung One UI Reachability Header.
 * Implements the 60/40 Reachability Rule:
 * Large bold title (34sp) on initial display or downward pull, with optional subtitle.
 */
@Composable
fun SeslReachabilityHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
            if (action != null) {
                action()
            }
        }
    }
}

/**
 * Authentic Samsung One UI Pill Action Button.
 */
@Composable
fun SeslPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = Color.White,
    contentColor: Color = Color.Black,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val view = LocalView.current
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.4f),
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(50.dp))
            .clickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                color = contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
