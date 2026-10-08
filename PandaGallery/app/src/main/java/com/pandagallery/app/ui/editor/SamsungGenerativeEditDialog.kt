package com.pandagallery.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pandagallery.app.data.editing.GenerativeEditEngine
import com.pandagallery.app.domain.model.GenerativeEditConfig
import com.pandagallery.app.domain.model.GenerativeEditMode
import com.pandagallery.app.domain.model.GenerativeEditResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

val OneUiGalaxyAiPurple = Color(0xFF8B5CF6)
val OneUiGalaxyAiCyan = Color(0xFF00C4B4)

/**
 * Signature Samsung One UI 6.1 / One UI 7 AI Photo Assist & Generative Edit Dialog.
 * Provides on-device generative horizon straightening, perspective keystone tilt,
 * and boundary outpainting with 14-day Safety Vault protection.
 */
@Composable
fun SamsungGenerativeEditDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onSaveToVault: (Bitmap) -> Unit,
    onSaveAsCopy: (Bitmap) -> Unit,
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var config by remember { mutableStateOf(GenerativeEditConfig()) }
    var activeMode by remember { mutableStateOf(GenerativeEditMode.STRAIGHTEN) }

    var isGenerating by remember { mutableStateOf(false) }
    var generateProgress by remember { mutableFloatStateOf(0f) }
    var generatedResult by remember { mutableStateOf<GenerativeEditResult?>(null) }
    var isHoldingCompareOriginal by remember { mutableStateOf(false) }

    // Live interactive preview bitmap
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Update real-time preview on config change
    LaunchedEffect(config, sourceBitmap) {
        if (generatedResult != null) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val preview = GenerativeEditEngine.generateInteractivePreview(sourceBitmap, config, maxDimension = 800)
            previewBitmap = preview
        }
    }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0F17))
                .systemBarsPadding(),
        ) {
            // Main Content Column
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                GenerativeTopBar(
                    hasModifications = config.hasModifications,
                    onReset = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        config = GenerativeEditConfig()
                        generatedResult = null
                    },
                    onClose = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onDismiss()
                    },
                )

                // Center Image Preview & Alignment Grid Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val displayBitmap = when {
                        isHoldingCompareOriginal -> sourceBitmap
                        generatedResult != null -> generatedResult!!.outputBitmap
                        previewBitmap != null -> previewBitmap!!
                        else -> sourceBitmap
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF141A24)),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = displayBitmap.asImageBitmap(),
                            contentDescription = "Generative Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Alignment Horizon Grid Overlay (only visible during manual tweaking)
                        if (generatedResult == null && config.hasModifications) {
                            GenerativeHorizonGridOverlay()
                        }

                        // Status Badge / Readout
                        if (config.hasModifications && !isHoldingCompareOriginal) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = OneUiGalaxyAiCyan,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (activeMode) {
                                            GenerativeEditMode.STRAIGHTEN -> "Horizon: %+.1f°".format(config.rotationDegrees)
                                            GenerativeEditMode.PERSPECTIVE -> "Tilt: ↕%+.0f°  ↔%+.0f°".format(config.verticalTilt, config.horizontalTilt)
                                            GenerativeEditMode.EXPAND -> "Expand: +%.0f%%".format((config.expandTopPct + config.expandBottomPct) * 100f)
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        // Hold to Compare Original Button (when generated or modified)
                        if (config.hasModifications || generatedResult != null) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1E2430).copy(alpha = 0.9f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                                isHoldingCompareOriginal = true
                                                tryAwaitRelease()
                                                isHoldingCompareOriginal = false
                                            }
                                        )
                                    },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Compare,
                                    contentDescription = "Hold to compare",
                                    tint = if (isHoldingCompareOriginal) OneUiGalaxyAiCyan else Color.White,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(22.dp),
                                )
                            }
                        }

                        // Generating Shimmer Progress Overlay
                        if (isGenerating) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    CircularProgressIndicator(
                                        progress = { generateProgress },
                                        color = OneUiGalaxyAiCyan,
                                        trackColor = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(52.dp),
                                    )
                                    Text(
                                        text = "Synthesizing generative boundaries...",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Control Deck & Sliders
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color(0xFF141A24),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        // Segmented Mode Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0B0F17), RoundedCornerShape(14.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            GenerativeTabItem(
                                title = "Straighten",
                                icon = Icons.Outlined.RotateRight,
                                isSelected = activeMode == GenerativeEditMode.STRAIGHTEN,
                                onClick = {
                                    activeMode = GenerativeEditMode.STRAIGHTEN
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            GenerativeTabItem(
                                title = "Perspective",
                                icon = Icons.Outlined.Transform,
                                isSelected = activeMode == GenerativeEditMode.PERSPECTIVE,
                                onClick = {
                                    activeMode = GenerativeEditMode.PERSPECTIVE
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            GenerativeTabItem(
                                title = "Expand",
                                icon = Icons.Outlined.AspectRatio,
                                isSelected = activeMode == GenerativeEditMode.EXPAND,
                                onClick = {
                                    activeMode = GenerativeEditMode.EXPAND
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Mode Specific Controls
                        when (activeMode) {
                            GenerativeEditMode.STRAIGHTEN -> {
                                StraightenControlSection(
                                    angle = config.rotationDegrees,
                                    onAngleChange = { newAngle ->
                                        if (abs(newAngle) < 0.2f && config.rotationDegrees != 0f) {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                            config = config.copy(rotationDegrees = 0f)
                                        } else {
                                            config = config.copy(rotationDegrees = (newAngle * 10f).roundToInt() / 10f)
                                        }
                                        generatedResult = null
                                    },
                                )
                            }
                            GenerativeEditMode.PERSPECTIVE -> {
                                PerspectiveControlSection(
                                    verticalTilt = config.verticalTilt,
                                    horizontalTilt = config.horizontalTilt,
                                    onVerticalChange = {
                                        config = config.copy(verticalTilt = it.roundToInt().toFloat())
                                        generatedResult = null
                                    },
                                    onHorizontalChange = {
                                        config = config.copy(horizontalTilt = it.roundToInt().toFloat())
                                        generatedResult = null
                                    },
                                )
                            }
                            GenerativeEditMode.EXPAND -> {
                                ExpandControlSection(
                                    expandPct = config.expandTopPct,
                                    onExpandChange = { pct ->
                                        config = config.copy(
                                            expandLeftPct = pct,
                                            expandTopPct = pct,
                                            expandRightPct = pct,
                                            expandBottomPct = pct,
                                        )
                                        generatedResult = null
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action Buttons Row
                        if (generatedResult == null) {
                            // "✨ Generate" Pill Button
                            Button(
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    isGenerating = true
                                    generateProgress = 0f
                                    coroutineScope.launch {
                                        val result = GenerativeEditEngine.transformAndSynthesize(
                                            source = sourceBitmap,
                                            config = config,
                                            onProgress = { generateProgress = it },
                                        )
                                        generatedResult = result
                                        isGenerating = false
                                    }
                                },
                                enabled = config.hasModifications && !isGenerating,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = if (config.hasModifications) {
                                                listOf(OneUiGalaxyAiPurple, OneUiGalaxyAiCyan)
                                            } else {
                                                listOf(Color(0xFF2A3140), Color(0xFF2A3140))
                                            }
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                    ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Generate Fill",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } else {
                            // "Save to Safety Vault" & "Save Copy" Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                        generatedResult?.let { onSaveToVault(it.outputBitmap) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OneUiGalaxyAiCyan,
                                        contentColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp),
                                ) {
                                    Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Save to Vault",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                        generatedResult?.let { onSaveAsCopy(it.outputBitmap) }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp),
                                ) {
                                    Text(
                                        text = "Save Copy",
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerativeTopBar(
    hasModifications: Boolean,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = OneUiGalaxyAiPurple,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Generative edit",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        TextButton(
            onClick = onReset,
            enabled = hasModifications,
        ) {
            Text(
                text = "Reset",
                color = if (hasModifications) OneUiGalaxyAiCyan else Color.White.copy(alpha = 0.3f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GenerativeTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF222B38) else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, OneUiGalaxyAiCyan.copy(alpha = 0.4f)) else null,
        modifier = modifier.clickable { onClick() },
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) OneUiGalaxyAiCyan else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun StraightenControlSection(
    angle: Float,
    onAngleChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Horizon Angle", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            Text(
                text = "%+.1f°".format(angle),
                color = if (angle == 0f) Color.White.copy(alpha = 0.6f) else OneUiGalaxyAiCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = angle,
            onValueChange = onAngleChange,
            valueRange = -30f..30f,
            colors = SliderDefaults.colors(
                thumbColor = OneUiGalaxyAiCyan,
                activeTrackColor = OneUiGalaxyAiCyan,
                inactiveTrackColor = Color(0xFF222B38),
            ),
        )

        // Quick angle presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(-5f, -1f, 0f, 1f, 5f).forEach { step ->
                TextButton(
                    onClick = { onAngleChange(step) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (step == 0f) "Center" else "%+.0f°".format(step),
                        color = if (angle == step) OneUiGalaxyAiCyan else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PerspectiveControlSection(
    verticalTilt: Float,
    horizontalTilt: Float,
    onVerticalChange: (Float) -> Unit,
    onHorizontalChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Vertical Pitch Tilt
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Vertical Tilt (Pitch)", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text("%+.0f°".format(verticalTilt), color = OneUiGalaxyAiCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Slider(
                value = verticalTilt,
                onValueChange = onVerticalChange,
                valueRange = -25f..25f,
                colors = SliderDefaults.colors(thumbColor = OneUiGalaxyAiCyan, activeTrackColor = OneUiGalaxyAiCyan),
            )
        }

        // Horizontal Yaw Tilt
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Horizontal Tilt (Yaw)", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text("%+.0f°".format(horizontalTilt), color = OneUiGalaxyAiCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Slider(
                value = horizontalTilt,
                onValueChange = onHorizontalChange,
                valueRange = -25f..25f,
                colors = SliderDefaults.colors(thumbColor = OneUiGalaxyAiCyan, activeTrackColor = OneUiGalaxyAiCyan),
            )
        }
    }
}

@Composable
private fun ExpandControlSection(
    expandPct: Float,
    onExpandChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Canvas Extension", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            Text("+%.0f%%".format(expandPct * 100f), color = OneUiGalaxyAiCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = expandPct,
            onValueChange = onExpandChange,
            valueRange = 0f..0.35f,
            colors = SliderDefaults.colors(
                thumbColor = OneUiGalaxyAiCyan,
                activeTrackColor = OneUiGalaxyAiCyan,
                inactiveTrackColor = Color(0xFF222B38),
            ),
        )
    }
}

@Composable
private fun GenerativeHorizonGridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Subtle Rule of Thirds Grid
        val gridColor = Color.White.copy(alpha = 0.2f)
        val stroke = 0.8f

        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), stroke)
        drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), stroke)
        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), stroke)
        drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), stroke)

        // Center Horizon Leveler Axis (Glowing Cyan)
        drawLine(
            color = OneUiGalaxyAiCyan.copy(alpha = 0.7f),
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 1.4f,
        )
    }
}
