package com.pandaapps.appstore.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Frosted "glass" surface used for list rows and content blocks: a soft vertical gradient over
 * the surface container with a 1 dp specular border that is brighter at the top.
 * Clickable cards get the bouncy press scale.
 *
 * @param accent optional tint blended into the border (e.g. PandaGreen for highlighted rows).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PandaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = Brush.verticalGradient(
        listOf(colors.surfaceContainerHigh.copy(alpha = 0.92f), colors.surfaceContainer.copy(alpha = 0.85f)),
    )
    val top = (accent ?: Color.White).copy(alpha = if (accent != null) 0.45f else 0.10f)
    val border = Brush.verticalGradient(listOf(top, colors.outlineVariant.copy(alpha = 0.25f)))

    val interaction = remember { MutableInteractionSource() }
    var cardModifier = modifier
    if (onClick != null) cardModifier = cardModifier.pressScale(interaction, pressedScale = 0.98f)
    cardModifier = cardModifier
        .clip(shape)
        .background(background)
        .border(1.dp, border, shape)
    if (onClick != null) {
        cardModifier = cardModifier.combinedClickable(
            interactionSource = interaction,
            indication = ripple(),
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
    Column(cardModifier.padding(contentPadding), content = content)
}
