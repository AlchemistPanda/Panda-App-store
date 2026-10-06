package com.pandaapps.appstore.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Visual weight of a [PandaButton]. */
enum class PandaButtonStyle {
    /** PandaGreen fill — the one primary action on a screen or row. */
    Primary,

    /** Soft tonal fill — secondary actions (Open). */
    Tonal,

    /** Outline only — tertiary actions (Uninstall, Cancel). */
    Outlined,

    /** Error-red fill — destructive or "Retry" actions. */
    Danger,

    /** No container — inline/low emphasis. */
    Text,
}

/**
 * Scales the element down while [interactionSource] is pressed and springs back with a little
 * overshoot (the brand's "bouncy" press). Share the same [interactionSource] with the clickable.
 */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.92f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The brand's bouncy button (GrabButton): Material 3 button + spring press-scale.
 *
 * @param text label; kept to one line.
 * @param icon optional leading icon.
 * @param style visual weight, see [PandaButtonStyle].
 * @param compact smaller paddings/min height for list rows.
 */
@Composable
fun PandaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: PandaButtonStyle = PandaButtonStyle.Primary,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val padding = if (compact) PaddingValues(horizontal = 16.dp, vertical = 6.dp) else ButtonDefaults.ContentPadding
    val minHeight = if (compact) 36.dp else 44.dp
    val scaled = modifier
        .pressScale(interactionSource)
        .defaultMinSize(minHeight = minHeight)
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            }
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
    }
    when (style) {
        PandaButtonStyle.Primary -> Button(
            onClick = onClick, modifier = scaled, enabled = enabled, shape = CircleShape,
            contentPadding = padding, interactionSource = interactionSource,
        ) { content() }

        PandaButtonStyle.Danger -> Button(
            onClick = onClick, modifier = scaled, enabled = enabled, shape = CircleShape,
            contentPadding = padding, interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { content() }

        PandaButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick, modifier = scaled, enabled = enabled, shape = CircleShape,
            contentPadding = padding, interactionSource = interactionSource,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { content() }

        PandaButtonStyle.Outlined -> OutlinedButton(
            onClick = onClick, modifier = scaled, enabled = enabled, shape = CircleShape,
            contentPadding = padding, interactionSource = interactionSource,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) { content() }

        PandaButtonStyle.Text -> TextButton(
            onClick = onClick, modifier = scaled, enabled = enabled, shape = CircleShape,
            contentPadding = padding, interactionSource = interactionSource,
        ) { content() }
    }
}
