package com.pandagallery.app.ui.viewer.components

import android.graphics.Bitmap
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandagallery.app.data.media.SubjectCutoutResult
import kotlin.math.roundToInt

/**
 * Samsung Gallery 1:1 Image Clipper Lifted Object Overlay.
 *
 * Displays the lifted subject with Samsung recoil spring elevation, drop shadow,
 * shimmering contour glow, and a floating frosted-glass action pill.
 */
@Composable
fun ImageClipperOverlay(
    cutout: SubjectCutoutResult,
    imageScreenRect: RectF,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current

    // Spring elevation scale (lifts off background)
    var isElevated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isElevated = true
    }

    val elevationScale by animateFloatAsState(
        targetValue = if (isElevated) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 380f,
        ),
        label = "clipperElevationScale",
    )

    // Shimmer glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "clipperShimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "clipperShimmerAlpha",
    )

    // Calculate exact screen coordinates for cutout
    val cutoutLeft = imageScreenRect.left + cutout.sourceBounds.left * imageScreenRect.width()
    val cutoutTop = imageScreenRect.top + cutout.sourceBounds.top * imageScreenRect.height()
    val cutoutWidth = cutout.sourceBounds.width() * imageScreenRect.width()
    val cutoutHeight = cutout.sourceBounds.height() * imageScreenRect.height()

    val cutoutWidthDp = with(density) { cutoutWidth.toDp() }
    val cutoutHeightDp = with(density) { cutoutHeight.toDp() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onDismiss()
                },
            ),
    ) {
        // 1. Ambient Background Dimmer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f)),
        )

        // 2. Lifted Cutout Subject with Drop Shadow & Shimmer Glow
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = cutoutLeft.roundToInt(),
                        y = cutoutTop.roundToInt(),
                    )
                }
                .size(cutoutWidthDp, cutoutHeightDp)
                .scale(elevationScale),
        ) {
            // Drop shadow underneath the lifted cutout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 10.dp)
                    .blur(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f)),
            )

            // Shimmer contour glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = shimmerAlpha * 0.4f),
                                    Color.White.copy(alpha = shimmerAlpha * 0.12f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = maxOf(size.width, size.height) * 0.7f,
                            ),
                        )
                    },
            )

            // The crisp, transparent subject bitmap
            Image(
                bitmap = cutout.cutoutBitmap.asImageBitmap(),
                contentDescription = "Lifted Subject",
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 3. Floating Frosted Glass Action Pill (Samsung One UI 6.1 Pill Menu)
        // Position pill directly above the subject (or flip below if too close to top bar)
        val pillYOffset = if (cutoutTop > 140f) {
            cutoutTop - 70f
        } else {
            cutoutTop + cutoutHeight + 20f
        }
        val pillCenterX = cutoutLeft + (cutoutWidth / 2f)

        AnimatedVisibility(
            visible = isElevated,
            enter = fadeIn(tween(200, delayMillis = 100)) + scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
            ),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = (pillCenterX - with(density) { 135.dp.toPx() }).roundToInt().coerceAtLeast(20),
                        y = pillYOffset.roundToInt().coerceAtLeast(80),
                    )
                },
        ) {
            ClipperActionPill(
                onCopy = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onCopy()
                },
                onShare = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onShare()
                },
                onSave = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onSave()
                },
            )
        }
    }
}

/**
 * Authentic Samsung One UI 6.1 Frosted Glass Floating Action Pill.
 */
@Composable
private fun ClipperActionPill(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xEB1E1E24), // Frosted dark obsidian
        shadowElevation = 12.dp,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(28.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ClipperPillButton(
                icon = Icons.Outlined.ContentCopy,
                label = "Copy",
                onClick = onCopy,
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color.White.copy(alpha = 0.15f)),
            )

            ClipperPillButton(
                icon = Icons.Outlined.Share,
                label = "Share",
                onClick = onShare,
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color.White.copy(alpha = 0.15f)),
            )

            ClipperPillButton(
                icon = Icons.Outlined.SaveAlt,
                label = "Save",
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun ClipperPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White,
            ),
        )
    }
}
