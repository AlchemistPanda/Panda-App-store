package com.pandagallery.app.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pandagallery.app.data.editing.NormalizedPoint
import kotlin.math.atan2

private val OneUiAccent = Color(0xFF2C6CF5)

/**
 * Samsung One UI Transform Bounding Box for Text and Sticker Overlays.
 *
 * Supports:
 * - Translation / Panning
 * - Top Rotation Pin (rotate degrees)
 * - Corner Pinch / Drag Scale (scale factor)
 * - Top-Right Delete Button (X)
 * - Optional Flip or Align Action
 */
@Composable
fun SamsungOverlayBox(
    position: NormalizedPoint,
    rotationDegrees: Float,
    scale: Float,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMove: (NormalizedPoint) -> Unit,
    onRotate: (Float) -> Unit,
    onScale: (Float) -> Unit,
    onDelete: () -> Unit,
    onFlip: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
    ) {
        if (containerSize.width == 0 || containerSize.height == 0) return@Box

        val posX = (position.x * containerSize.width)
        val posY = (position.y * containerSize.height)

        Box(
            modifier = Modifier
                .offset { IntOffset(posX.toInt(), posY.toInt()) }
                .graphicsLayer {
                    this.rotationZ = rotationDegrees
                    this.scaleX = scale
                    this.scaleY = scale
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelect()
                        }
                    )
                }
                .pointerInput(containerSize) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onSelect()
                        val newX = (position.x + dragAmount.x / containerSize.width).coerceIn(0.05f, 0.95f)
                        val newY = (position.y + dragAmount.y / containerSize.height).coerceIn(0.05f, 0.95f)
                        onMove(NormalizedPoint(newX, newY))
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .padding(14.dp)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 1.2.dp,
                                color = OneUiAccent,
                                shape = RoundedCornerShape(8.dp),
                            )
                        } else Modifier
                    ),
            ) {
                content()
            }

            // Samsung Bounding Box Handles when selected
            if (isSelected) {
                // Delete Button (Top-Right)
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF3B30),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onDelete()
                        },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                    )
                }

                // Top Rotation Handle
                Surface(
                    shape = CircleShape,
                    color = OneUiAccent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(24.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newRot = (rotationDegrees + dragAmount.x * 0.8f) % 360f
                                onRotate(newRot)
                            }
                        },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RotateRight,
                        contentDescription = "Rotate",
                        tint = Color.White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                    )
                }

                // Bottom-Right Scale Handle
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, OneUiAccent),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val delta = (dragAmount.x + dragAmount.y) * 0.005f
                                val newScale = (scale + delta).coerceIn(0.4f, 3.5f)
                                onScale(newScale)
                            }
                        },
                ) {}

                // Bottom-Left Flip Handle (if supported)
                if (onFlip != null) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF2C2C2E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onFlip()
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flip,
                            contentDescription = "Flip",
                            tint = Color.White,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}
