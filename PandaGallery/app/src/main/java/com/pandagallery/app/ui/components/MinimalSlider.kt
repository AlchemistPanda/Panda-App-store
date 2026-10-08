package com.pandagallery.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Minimal seek bar
//
// Material's stock Slider is built for a solid surface: a 16dp track and a wide pill thumb that
// together read as a control panel, not as a value. On the glass trays this app floats over
// content that is far too much furniture — the panel ends up looking like two chunky bars stacked
// under the artwork the user is actually editing.
//
// This is the same Material Slider (so gesture handling, keyboard steps and accessibility
// semantics all stay), redressed with a hairline track and a small round thumb. The thumb's
// *layout* box stays a constant 20dp while only the visible dot grows on touch: Material insets
// the track by half the thumb width, so animating the real thumb size would shift the track and
// make the fill jitter under the finger.
// ─────────────────────────────────────────────────────────────────────────────

/** Track height at rest — thin enough to read as a line rather than a bar. */
private val TrackHeight = 3.dp
private val ThumbSlot = 20.dp
private val ThumbRest = 13.dp
private val ThumbActive = 17.dp

/**
 * A thin, iOS-flavoured slider: hairline track, small white thumb, no tick marks.
 *
 * Sized to sit inside a glass tray, where a stock Slider's 16dp track would compete with the
 * content behind the glass.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val dotSize by animateDpAsState(
        targetValue = if (dragged || pressed) ThumbActive else ThumbRest,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "sliderThumb",
    )
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(),
        modifier = modifier,
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span <= 0f) 0f else {
                ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrackHeight)
                    .clip(CircleShape)
                    .background(if (enabled) inactiveColor else inactiveColor.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (enabled) activeColor else activeColor.copy(alpha = 0.38f)),
                )
            }
        },
        thumb = {
            Box(
                modifier = Modifier.size(ThumbSlot),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        // The dot is white in both themes: it sits on the accent-coloured fill, and
                        // a surface-toned thumb disappears into a light glass tray.
                        .shadow(if (enabled) 3.dp else 0.dp, CircleShape)
                        .background(if (enabled) Color.White else Color.White.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.Black.copy(alpha = 0.06f), CircleShape),
                )
            }
        },
    )
}

/**
 * [MinimalSlider] with the label and read-out that a settings row needs: name on the left, value on
 * the right, both in the same muted tone so the accent-coloured fill is the only thing that draws
 * the eye.
 */
@Composable
fun LabeledMinimalSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    valueLabel: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp),
        )
        MinimalSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        if (valueLabel != null) {
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(32.dp),
            )
        }
    }
}
