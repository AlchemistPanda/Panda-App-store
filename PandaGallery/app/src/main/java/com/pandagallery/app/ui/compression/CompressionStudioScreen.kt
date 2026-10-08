package com.pandagallery.app.ui.compression

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandagallery.app.data.compression.PerceptualQualityEstimator
import com.pandagallery.app.domain.model.CompressionPreset
import com.pandagallery.app.domain.model.ImageFormat
import com.pandagallery.app.domain.model.formatFileSize
import com.pandagallery.app.ui.components.OneUiContainerHigh
import com.pandagallery.app.ui.components.OneUiContainerLow
import com.pandagallery.app.ui.components.OneUiThumbInactive
import com.pandagallery.app.ui.components.OneUiTrackInactive
import com.pandagallery.app.ui.components.PremiumAlertDialog
import com.pandagallery.app.ui.components.SeslSlider
import com.pandagallery.app.ui.compression.components.BeforeAfterSplitSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionStudioScreen(
    mediaId: Long,
    onBack: () -> Unit,
    viewModel: CompressionStudioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showReplaceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onBack()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Compression Studio",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        val preview = state.previewResult
                        if (preview != null) {
                            Text(
                                text = "${formatFileSize(preview.originalSizeBytes)} → ${formatFileSize(preview.compressedSizeBytes)} (-${preview.savingsPercentage}%)",
                                fontSize = 12.sp,
                                color = Color(0xFF80E386),
                                fontWeight = FontWeight.Medium,
                            )
                        } else {
                            Text(
                                text = "Fine-tune visual fidelity & file size",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            val preview = state.previewResult
            val errorMessage = state.errorMessage

            if (!state.isLoading && preview == null && errorMessage != null) {
                // Without this branch a missing item or a failed first preview left the
                // "Analyzing" spinner up forever, with no way out but the system back gesture.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = errorMessage,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onBack) {
                            Text("Back", color = Color.White)
                        }
                        Button(onClick = viewModel::retryPreview) {
                            Text("Retry")
                        }
                    }
                }
            } else if (state.isLoading || preview == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analyzing frame fidelity...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // The slider sits above the drawer instead of under it: drawn full-screen beneath a
                    // ~450dp drawer, its loupe/zoom pills and the centred split handle were covered and
                    // untouchable. The drawer is capped and scrolls, so the image keeps at least 40%.
                    val drawerMaxHeight = maxHeight * 0.6f
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            // Interactive Before/After Split Canvas
                            BeforeAfterSplitSlider(
                                originalBitmap = preview.originalBitmap,
                                compressedBitmap = preview.compressedBitmap,
                                initialSplitFraction = state.splitFraction,
                                leftLabel = "Original",
                                rightLabel = if (state.isRemasterEnhanceEnabled) "Remastered" else "Compressed",
                                onSplitFractionChanged = viewModel::setSplitFraction,
                                modifier = Modifier.fillMaxSize(),
                            )

                            // Updating Preview Shimmer Badge
                            androidx.compose.animation.AnimatedVisibility(
                                visible = state.isGeneratingPreview,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF1E1E1E).copy(alpha = 0.9f),
                                    shadowElevation = 4.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Encoding preview...",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }

                        // One UI 6.1 Floating Control Drawer
                        Surface(
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            color = OneUiContainerHigh,
                            shadowElevation = 16.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = drawerMaxHeight),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                            ) {
                                // Perceptual quality score. It lives at the top of this drawer rather
                                // than floating over the image: it used to be pinned to the bottom of
                                // the screen with a hardcoded 280dp offset, which assumed a drawer
                                // height these controls comfortably exceed, so the one measured number
                                // on the screen was rendered underneath the drawer and never seen.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(preview.qualityScore.grade.colorHex), CircleShape),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${"%.1f".format(preview.qualityScore.matchPercentage)}% Visual Match",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "· ${preview.qualityScore.grade.label}",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                    )
                                    preview.contentComplexity?.let { complexity ->
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "· ${complexity.label}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }

                                // Presets Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    PresetChip(
                                        label = "✨ Smart AI",
                                        isSelected = state.selectedPreset == CompressionPreset.SMART_AUTO,
                                        onClick = { viewModel.setPreset(CompressionPreset.SMART_AUTO) },
                                    )
                                    PresetChip(
                                        label = "Archival (92%)",
                                        isSelected = state.selectedPreset == CompressionPreset.NEAR_LOSSLESS,
                                        onClick = { viewModel.setPreset(CompressionPreset.NEAR_LOSSLESS) },
                                    )
                                    PresetChip(
                                        label = "Lossless",
                                        isSelected = state.selectedPreset == CompressionPreset.LOSSLESS,
                                        onClick = { viewModel.setPreset(CompressionPreset.LOSSLESS) },
                                    )
                                    PresetChip(
                                        label = "High",
                                        isSelected = state.selectedPreset == CompressionPreset.HIGH,
                                        onClick = { viewModel.setPreset(CompressionPreset.HIGH) },
                                    )
                                    PresetChip(
                                        label = "Balanced",
                                        isSelected = state.selectedPreset == CompressionPreset.MEDIUM,
                                        onClick = { viewModel.setPreset(CompressionPreset.MEDIUM) },
                                    )
                                    PresetChip(
                                        label = "Saver",
                                        isSelected = state.selectedPreset == CompressionPreset.LOW,
                                        onClick = { viewModel.setPreset(CompressionPreset.LOW) },
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Target Budget Row. The label sits above its pills rather than beside
                                // them: on a 1080p screen the four pills and the label together overflow
                                // the row, and the last pill collapses to one character per line.
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Target Size",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.7f),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        FormatPill(
                                            label = "Auto/Slider",
                                            isSelected = !state.isTargetSizeMode,
                                            onClick = { viewModel.disableTargetSize() },
                                        )
                                        FormatPill(
                                            label = "1 MB",
                                            isSelected = state.isTargetSizeMode && state.targetSizeBytes == 1_048_576L,
                                            onClick = { viewModel.setTargetSize(1_048_576L) },
                                        )
                                        FormatPill(
                                            label = "2 MB",
                                            isSelected = state.isTargetSizeMode && state.targetSizeBytes == 2_097_152L,
                                            onClick = { viewModel.setTargetSize(2_097_152L) },
                                        )
                                        FormatPill(
                                            label = "5 MB",
                                            isSelected = state.isTargetSizeMode && state.targetSizeBytes == 5_242_880L,
                                            onClick = { viewModel.setTargetSize(5_242_880L) },
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Format Selection & Quality Slider
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Format",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.7f),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        FormatPill(
                                            label = if (state.isAvifSupported) "AVIF" else "AVIF (unsupported)",
                                            isSelected = state.selectedFormat == ImageFormat.AVIF,
                                            onClick = { viewModel.setFormat(ImageFormat.AVIF) },
                                            enabled = state.isAvifSupported,
                                        )
                                        FormatPill(
                                            label = "WebP",
                                            isSelected = state.selectedFormat == ImageFormat.WEBP,
                                            onClick = { viewModel.setFormat(ImageFormat.WEBP) },
                                        )
                                        FormatPill(
                                            label = "JPEG",
                                            isSelected = state.selectedFormat == ImageFormat.JPEG,
                                            onClick = { viewModel.setFormat(ImageFormat.JPEG) },
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val currentTargetBytes = state.targetSizeBytes
                                if (state.isTargetSizeMode && currentTargetBytes != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = OneUiContainerLow,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Speed,
                                                contentDescription = null,
                                                // OneUiContainerLow is dark in both themes.
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Target Mode: Calibrated to fit within ${formatFileSize(currentTargetBytes)}",
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                } else {
                                    // Keyed on the value, not the chip: picking a format after Smart AI
                                    // clears the chip but keeps adaptive quality, which read "-1%".
                                    val qualityLabel = if (state.quality < 0) {
                                        "Quality: AI Adaptive (${preview.contentComplexity?.label ?: "Smart Auto"})"
                                    } else {
                                        "Quality: ${state.quality}%"
                                    }
                                    // SESL Quality Slider
                                    SeslSlider(
                                        label = qualityLabel,
                                        value = if (state.quality < 0) 0.85f else state.quality / 100f,
                                        range = 0.05f..1f,
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        onValueChange = { newVal ->
                                            viewModel.setQuality((newVal * 100).toInt())
                                        },
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Samsung One UI 6.1 AI Remaster Detail Enhancer
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (state.isRemasterEnhanceEnabled) MaterialTheme.colorScheme.surfaceContainerHighest else Color(0xFF1C1C1E),
                                    border = BorderStroke(
                                        1.dp,
                                        if (state.isRemasterEnhanceEnabled) MaterialTheme.colorScheme.outlineVariant else Color.White.copy(alpha = 0.08f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (state.isRemasterEnhanceEnabled) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.1f),
                                                    modifier = Modifier.size(30.dp),
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.AutoAwesome,
                                                            contentDescription = null,
                                                            tint = if (state.isRemasterEnhanceEnabled) MaterialTheme.colorScheme.surface else Color.White.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                }
                                                Column {
                                                    Text(
                                                        text = "✨ AI Remaster Detail Enhancer",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                    )
                                                    Text(
                                                        text = "Shadow recovery, micro-contrast & edge clarity",
                                                        fontSize = 11.sp,
                                                        color = Color.White.copy(alpha = 0.6f),
                                                    )
                                                }
                                            }

                                            Switch(
                                                checked = state.isRemasterEnhanceEnabled,
                                                onCheckedChange = viewModel::toggleRemasterEnhance,
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                    uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                                                ),
                                            )
                                        }

                                        AnimatedVisibility(
                                            visible = state.isRemasterEnhanceEnabled,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut(),
                                        ) {
                                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                                SeslSlider(
                                                    label = "Detail Boost: ${(state.remasterDetailLevel * 100).toInt()}%",
                                                    value = state.remasterDetailLevel,
                                                    range = 0.1f..1f,
                                                    accentColor = MaterialTheme.colorScheme.primary,
                                                    onValueChange = viewModel::setRemasterDetailLevel,
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Action Buttons: Save as Copy vs Replace Original
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = viewModel::saveAsCopy,
                                        enabled = !state.isSaving,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp),
                                    ) {
                                        Text(
                                            text = "Save Copy",
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    Button(
                                        onClick = { showReplaceDialog = true },
                                        enabled = !state.isSaving,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.onSurface,
                                            contentColor = MaterialTheme.colorScheme.surface,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp),
                                    ) {
                                        if (state.isSaving) {
                                            CircularProgressIndicator(
                                                // Must match the button's contentColor; a hardcoded
                                                // white one vanished against the light-on-dark button.
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Text(
                                                text = "Replace Original",
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
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
    }

    if (showReplaceDialog) {
        val preview = state.previewResult
        val savingsStr = if (preview != null) " (saves ${formatFileSize(preview.originalSizeBytes - preview.compressedSizeBytes)})" else ""
        PremiumAlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            title = { Text("Replace Original File?") },
            text = {
                Text(
                    "This will compress the image and free storage$savingsStr. " +
                            "For your safety, the original uncompressed photo will be safely moved to your 30-day Trash bin so you can restore it anytime."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReplaceDialog = false
                        viewModel.replaceOriginal()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Replace & Move to Trash",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReplaceDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun FormatPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (isSelected) null else BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            // Without this the row's last pill is squeezed to a single character per line
            // ("5 / M / B") as soon as the label beside it takes the width.
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
