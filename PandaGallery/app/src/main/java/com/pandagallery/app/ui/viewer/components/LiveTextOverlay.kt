package com.pandagallery.app.ui.viewer.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandagallery.app.data.smart.LiveTextRecognizer
import com.pandagallery.app.data.smart.RecognizedTextLine
import com.pandagallery.app.data.smart.RecognizedTextResult
import com.pandagallery.app.data.smart.SmartAction
import com.pandagallery.app.data.smart.SmartActionClassifier
import com.pandagallery.app.data.smart.SmartActionType
import com.pandagallery.app.data.smart.TextGeometryProjector

/**
 * Authentic Samsung One UI 6.1 / One UI 7 Interactive Live Text OCR Overlay.
 *
 * Features:
 * - Dims the underlying photo slightly to focus attention on recognized typography.
 * - Renders translucent golden highlighting bounding boxes over every detected line of text.
 * - Interactive tap selection: tapping any word or line illuminates it with Samsung vibrant blue
 *   selection fill and bounding pins.
 * - Floating frosted contextual action pill (Copy, Select All, Share, Search, Call, Open URL, Email)
 *   hovering dynamically above the user's selected text.
 * - Bottom One UI floating capsule controls ("Select all", "Copy all", and Dismiss).
 */
@Composable
fun LiveTextOverlay(
    textResult: RecognizedTextResult,
    scale: Float = 1f,
    panOffset: Offset = Offset.Zero,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    BackHandler { onDismiss() }

    // Selection state: null means no specific line is selected (all are highlighted as tap targets).
    // An empty list means "Select All" is active.
    var selectedLines by remember { mutableStateOf<Set<RecognizedTextLine>>(emptySet()) }
    var isSelectAllActive by remember { mutableStateOf(false) }

    val effectiveSelectedLines = remember(selectedLines, isSelectAllActive, textResult) {
        if (isSelectAllActive) textResult.allLines.toSet() else selectedLines
    }

    val selectedText = remember(effectiveSelectedLines) {
        effectiveSelectedLines.joinToString("\n") { it.text }
    }

    val smartActions = remember(selectedText) {
        if (selectedText.isNotBlank()) SmartActionClassifier.classify(selectedText) else emptyList()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(textResult, scale, panOffset) {
                detectTapGestures { offset ->
                    val hitLine = TextGeometryProjector.findLineAt(
                        touchX = offset.x,
                        touchY = offset.y,
                        lines = textResult.allLines,
                        imageWidth = textResult.imageWidth,
                        imageHeight = textResult.imageHeight,
                        viewWidth = size.width.toFloat(),
                        viewHeight = size.height.toFloat(),
                        scale = scale,
                        panX = panOffset.x,
                        panY = panOffset.y,
                        touchSlopPx = 18f,
                    )

                    if (hitLine != null) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        isSelectAllActive = false
                        selectedLines = if (selectedLines.contains(hitLine)) {
                            selectedLines - hitLine
                        } else {
                            selectedLines + hitLine
                        }
                    } else {
                        // Tapped empty space
                        if (selectedLines.isNotEmpty() || isSelectAllActive) {
                            selectedLines = emptySet()
                            isSelectAllActive = false
                        } else {
                            onDismiss()
                        }
                    }
                }
            },
    ) {
        val viewWidth = constraints.maxWidth.toFloat()
        val viewHeight = constraints.maxHeight.toFloat()

        // 1. Draw glowing translucent bounding boxes on canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Ambient photo dimmer
            drawRect(color = Color(0x40000000))

            textResult.allLines.forEach { line ->
                val box = line.boundingBox ?: return@forEach
                val projected = TextGeometryProjector.projectToScreen(
                    imageRect = box,
                    imageWidth = textResult.imageWidth,
                    imageHeight = textResult.imageHeight,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    scale = scale,
                    panX = panOffset.x,
                    panY = panOffset.y,
                )

                val isSelected = effectiveSelectedLines.contains(line)
                val rectLeft = projected.left
                val rectTop = projected.top
                val rectWidth = projected.width
                val rectHeight = projected.height
                val cornerRadiusPx = 5.dp.toPx()

                if (isSelected) {
                    // Samsung selection blue fill and glowing outline
                    drawRoundRect(
                        color = Color(0x752C6CF5),
                        topLeft = Offset(rectLeft, rectTop),
                        size = Size(rectWidth, rectHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    )
                    drawRoundRect(
                        color = Color(0xFF2C6CF5),
                        topLeft = Offset(rectLeft, rectTop),
                        size = Size(rectWidth, rectHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = Stroke(width = 1.8f.dp.toPx()),
                    )
                } else {
                    // Samsung soft golden translucent target highlight
                    drawRoundRect(
                        color = Color(0x2EFFFFFF),
                        topLeft = Offset(rectLeft, rectTop),
                        size = Size(rectWidth, rectHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    )
                    drawRoundRect(
                        color = Color(0x99FFD600),
                        topLeft = Offset(rectLeft, rectTop),
                        size = Size(rectWidth, rectHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = Stroke(width = 0.8f.dp.toPx()),
                    )
                }
            }
        }

        // 2. Floating Contextual Action Pill (anchored above the first selected line)
        val anchorLine = effectiveSelectedLines.firstOrNull()
        if (anchorLine != null && selectedText.isNotBlank()) {
            val anchorBox = anchorLine.boundingBox
            val projected = if (anchorBox != null) {
                TextGeometryProjector.projectToScreen(
                    imageRect = anchorBox,
                    imageWidth = textResult.imageWidth,
                    imageHeight = textResult.imageHeight,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    scale = scale,
                    panX = panOffset.x,
                    panY = panOffset.y,
                )
            } else {
                com.pandagallery.app.data.smart.ScreenBoundingBox(viewWidth / 2f, viewHeight / 2f, viewWidth / 2f, viewHeight / 2f)
            }

            // Position pill ~56dp above bounding box, or flip below if too close to top
            val pillOffsetY = with(density) {
                val candidateY = (projected.top - 62.dp.toPx())
                if (candidateY < 72.dp.toPx()) {
                    (projected.bottom + 12.dp.toPx()).toInt()
                } else {
                    candidateY.toInt()
                }
            }
            val pillOffsetX = with(density) {
                val center = (projected.left + projected.right) / 2f
                (center - 140.dp.toPx()).coerceIn(16.dp.toPx(), (viewWidth - 296.dp.toPx())).toInt()
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(pillOffsetX, pillOffsetY) }
                    .padding(horizontal = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xEE1C1C1E),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Copy Action
                        ContextualActionButton(
                            icon = Icons.Outlined.ContentCopy,
                            label = "Copy",
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                copyToClipboard(context, selectedText)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                        )

                        // Select All Action
                        ContextualActionButton(
                            icon = Icons.Outlined.SelectAll,
                            label = "Select all",
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                isSelectAllActive = true
                            },
                        )

                        // Share Action
                        ContextualActionButton(
                            icon = Icons.Outlined.Share,
                            label = "Share",
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, selectedText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share text"))
                            },
                        )

                        // Web Search Action
                        ContextualActionButton(
                            icon = Icons.Outlined.Search,
                            label = "Search",
                            onClick = {
                                val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra("query", selectedText)
                                }
                                try {
                                    context.startActivity(searchIntent)
                                } catch (e: Exception) {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(selectedText)}"))
                                    context.startActivity(browserIntent)
                                }
                            },
                        )

                        // Smart Contextual Action Chips (Call, URL, Email)
                        smartActions.forEach { action ->
                            VerticalDivider(
                                color = Color.White.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(horizontal = 2.dp),
                            )
                            val icon = when (action.actionType) {
                                SmartActionType.CALL -> Icons.Outlined.Call
                                SmartActionType.URL -> Icons.Outlined.Language
                                SmartActionType.EMAIL -> Icons.Outlined.Email
                                SmartActionType.MAP -> Icons.Outlined.Language
                            }
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.targetUri))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open: ${action.targetUri}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(icon, contentDescription = action.label, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(action.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Samsung One UI Bottom Floating Capsule Control Bar
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xDD1C1C1E),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Select All toggle button
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        isSelectAllActive = !isSelectAllActive
                        if (!isSelectAllActive) selectedLines = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isSelectAllActive) Color(0xFF2C6CF5) else Color.White,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSelectAllActive) "Deselect" else "Select all",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }

                VerticalDivider(
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.height(20.dp),
                )

                // Copy All button
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        copyToClipboard(context, textResult.fullText)
                        Toast.makeText(context, "Copied all text (${textResult.allLines.size} lines)", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy all",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }

                VerticalDivider(
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.height(20.dp),
                )

                // Close / Done button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close Live Text",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextualActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Recognized Text", text)
    clipboard.setPrimaryClip(clip)
}
