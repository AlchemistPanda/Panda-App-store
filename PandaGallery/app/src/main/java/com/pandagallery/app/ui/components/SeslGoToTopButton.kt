package com.pandagallery.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Authentic Samsung One UI "Go To Top" floating button.
 * Matches Samsung Gallery's SeslGoToTopController:
 * - Appears when scrolling down past the threshold (firstVisibleItemIndex > thresholdItems).
 * - Remains visible while scrolling and for [autoHideDelayMs] after scrolling stops.
 * - Smoothly scrolls to the top with haptic tactile confirmation on tap.
 * - Circular shape with frosted container and subtle outline.
 */
@Composable
fun SeslGoToTopButton(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    thresholdItems: Int = 16,
    autoHideDelayMs: Long = 2500L,
    buttonSize: Dp = 44.dp,
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.isScrollInProgress) {
        if (gridState.firstVisibleItemIndex > thresholdItems) {
            isVisible = true
            if (!gridState.isScrollInProgress) {
                delay(autoHideDelayMs)
                isVisible = false
            }
        } else {
            isVisible = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(160)) + scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            initialScale = 0.75f,
        ),
        exit = fadeOut(tween(160)) + scaleOut(
            animationSpec = tween(160),
            targetScale = 0.75f,
        ),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(buttonSize)
                .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f))
                .border(
                    width = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    shape = CircleShape,
                )
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = buttonSize / 2),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch {
                            gridState.animateScrollToItem(0)
                        }
                    },
                )
                .semantics { contentDescription = "Scroll to top" },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
