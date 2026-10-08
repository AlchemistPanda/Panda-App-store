package com.pandagallery.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
internal fun PremiumAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        val shape = OneUiGlass.DialogShape
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                // Blur, top-lit hairline and sheen all come from here, so the Surface itself stays
                // transparent — an opaque fill over the blur is what made this dialog read as a
                // flat grey card. The tint is denser than the app's chrome uses because a dialog
                // carries body text, which needs a settled backdrop rather than a suggestive one.
                .oneUiGlass(shape = shape, tintAlpha = 0.72f),
            shape = shape,
            color = Color.Transparent,
            // Never derived. `Color.Transparent` is not a scheme role, so `contentColorFor` cannot
            // resolve it and falls back to `LocalContentColor` — Material's `Color.Black` default
            // here, because dialogs are composed as siblings of each screen's Scaffold and so sit
            // outside any Surface that would have provided a content color. Every Text in every
            // dialog that did not name its own color then rendered black on near-black glass.
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (icon != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) { icon() }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                androidx.compose.material3.ProvideTextStyle(
                    value = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    content = title,
                )
                if (text != null) {
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.ProvideTextStyle(
                        value = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        content = text,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
internal fun PremiumDialogOption(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.64f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (destructive) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (destructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = contentColor,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.76f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A single-choice row for pickers (theme, sort order, quality, …) — a checkmark-in-circle
 * indicator instead of a stock RadioButton, matching [PremiumDialogOption]'s icon-chip language.
 */
@Composable
internal fun PandaSelectableOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            // Unselected reads as an empty ring. Filling it with a container tone instead left it
            // indistinguishable from the panel behind it, so the row looked like plain text with no
            // hint that it was selectable.
            border = if (selected) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A hand-drawn pill toggle replacing the stock [androidx.compose.material3.Switch] — wider
 * track, no thumb icon/dot, and a soft shadow under the thumb for a bit of physical depth.
 */
@Composable
internal fun PandaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SeslSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        activeTrackColor = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The app's overflow / context menu: a One UI glass panel rather than the stock filled one.
 *
 * Material's [DropdownMenu] paints `containerColor` opaquely over everything behind it, so the
 * container is transparent and [Modifier.oneUiGlass] draws the panel — blur, top-lit hairline,
 * specular sheen. The hairline is what makes the menu readable at all on the true-black ground:
 * with surface tones sitting within a few levels of #000000 there is no fill contrast to rely on.
 *
 * `shadowElevation` stays at zero. A Material shadow renders as a dark halo, which is invisible
 * against black and, worse, would sit between the blur and the content behind it.
 */
@Composable
internal fun PandaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = OneUiGlass.MenuShape
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.oneUiGlass(shape = shape, tintAlpha = 0.66f, blurRadius = 32.dp),
        shape = shape,
        containerColor = Color.Transparent,
        // Never derived — see the contrast note on [PremiumAlertDialog]; a transparent container
        // has no `contentColorFor` mapping and would fall back to Material's black default.
        shadowElevation = 0.dp,
        content = {
            // The menu's own `contentColor` is not exposed, and it is derived from the now
            // transparent `containerColor` — which has no `contentColorFor` mapping and resolves
            // to Material's black default. Providing it here keeps items legible on the glass.
            val columnScope = this
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                columnScope.content()
            }
        },
    )
}
