package com.pandagallery.app.ui.viewer.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import com.pandagallery.app.domain.model.formatFileSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandagallery.app.data.media.MotionPhotoHelper
import com.pandagallery.app.domain.compression.CompressionEstimator
import com.pandagallery.app.domain.model.CompressionOriginalAction
import com.pandagallery.app.domain.model.CompressionPreset
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.UserPreferences
import com.pandagallery.app.domain.model.matchedCompressionPreset
import com.pandagallery.app.ui.components.OneUiContainerHigh
import com.pandagallery.app.ui.components.PandaModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartOptimizeBottomSheet(
    item: MediaItem,
    /** Null until loaded; the estimates and the Safety Vault wording both come from it. */
    preferences: UserPreferences?,
    onDismiss: () -> Unit,
    onConfirmAction: (preset: CompressionPreset, remasterDetailLevel: Float, action: CompressionOriginalAction) -> Unit,
    onOpenStudio: () -> Unit,
    onStripMotionPhoto: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // Start from the user's own settings, so a default confirm doesn't override them with MEDIUM.
    var selectedPreset by remember(preferences != null) {
        mutableStateOf(preferences?.matchedCompressionPreset ?: CompressionPreset.MEDIUM)
    }
    var enableAiRemaster by remember { mutableStateOf(false) }
    var motionVideoRange by remember(item.uri) { mutableStateOf<Pair<Int, Int>?>(null) }
    val remasterDetailLevel = if (enableAiRemaster) SMART_OPTIMIZE_REMASTER_LEVEL else 0f

    // One estimate per preset, from the same model the rest of the app uses, so the hero card and
    // the chips describe the preset actually picked. They used to fall back to a made-up "~70%"
    // (and fixed "-50/-70/-85%" chips) for any file, including already-small ones that save nothing.
    val presetEstimates: Map<CompressionPreset, CompressionEstimator.Estimate> = remember(item, preferences) {
        if (preferences == null) return@remember emptyMap()
        val input = CompressionEstimator.Input(
            sizeBytes = item.size,
            width = item.width,
            height = item.height,
            isVideo = item.isVideo,
            durationMillis = item.duration,
        )
        CompressionPreset.entries.associateWith { preset ->
            CompressionEstimator.estimate(
                input,
                preferences.copy(
                    imageFormat = preset.imageFormat,
                    imageQuality = preset.imageQuality,
                    videoResolution = preset.videoResolution,
                    videoCodec = preset.videoCodec,
                ),
            )
        }
    }
    val estimate = presetEstimates[selectedPreset]
    // Vault off means a Replace has no in-app undo; null (not loaded yet) promises nothing specific.
    val vaultEnabled = preferences?.safetyVaultEnabled
    val retentionDays = preferences?.safetyVaultRetentionDays

    LaunchedEffect(item.uri) {
        if (item.isImage) {
            motionVideoRange = MotionPhotoHelper.getMotionVideoRange(context, item.uri)
        }
    }

    PandaModalBottomSheet(
        onDismissRequest = onDismiss,
        // This sheet draws its text and icons as literal white rather than through the scheme, so
        // the glass is pinned dark — a light-theme panel here would be white-on-white.
        tint = Color.Black,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    // Unweighted against the "14d Safe Vault" badge, "Smart Storage Optimizer"
                    // was long enough to push their combined width past the sheet's, which
                    // squeezed the badge's Text to near-zero and wrapped it one letter per line.
                    // weight(1f) makes this block the one that yields, via the title's ellipsis.
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Smart Storage Optimizer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "One UI Archival Compression",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // With the vault off the badge takes the warning palette: green "safe" styling next to
                // a "Vault off" label would signal the opposite of what the label says.
                val vaultOff = vaultEnabled == false
                val badgeTint = if (vaultOff) Color(0xFFFFB74D) else Color(0xFF80E386)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (vaultOff) Color(0x33FFA726) else Color(0xFF1B382B),
                    border = BorderStroke(1.dp, if (vaultOff) Color(0x66FFA726) else Color(0xFF2E592F))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (vaultOff) Icons.Outlined.WarningAmber else Icons.Outlined.Check,
                            contentDescription = null,
                            tint = badgeTint,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = when {
                                vaultOff -> "Vault off"
                                retentionDays != null && retentionDays > 0 -> "${retentionDays}d Safe Vault"
                                else -> "Safe Vault"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTint
                        )
                    }
                }
            }

            // Size Comparison Hero Card
            val predictedBytes = estimate?.estimatedBytes

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF18181B),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Size",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = formatFileSize(item.size),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.Compress,
                            contentDescription = null,
                            tint = Color(0xFF80E386),
                            modifier = Modifier.size(20.dp)
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Predicted Size",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = predictedBytes?.let { "~${formatFileSize(it)}" } ?: "—",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80E386)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF80E386).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = when {
                                    estimate == null -> "Estimate unavailable"
                                    estimate.savingsPercent <= 0 -> "No meaningful saving expected"
                                    else -> "Saves ~${formatFileSize(estimate.savedBytes)} (${estimate.savingsPercent}%)"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80E386),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Samsung Motion Photo 1-Tap Strip Card
            motionVideoRange?.let { (_, lengthBytes) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF241C12),
                    border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LayersClear,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "🎬 Motion Photo Detected",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFBBF24).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = formatFileSize(lengthBytes.toLong()),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "An embedded micro-video is taking up ${formatFileSize(lengthBytes.toLong())}. You can delete just the video clip in ~10ms while keeping 100% of the original photo bitstream intact.",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            lineHeight = 16.sp
                        )

                        Button(
                            onClick = {
                                onDismiss()
                                onStripMotionPhoto?.invoke()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD97706),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                        ) {
                            Text(
                                text = "⚡ Delete Motion Clip (${formatFileSize(lengthBytes.toLong())} saved)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Quality Presets
            Text(
                text = "Compression Profile",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val displayPresets = listOf(
                    CompressionPreset.HIGH,
                    CompressionPreset.MEDIUM,
                    CompressionPreset.LOW
                )
                displayPresets.forEach { preset ->
                    val selected = preset == selectedPreset
                    val label = when (preset) {
                        CompressionPreset.HIGH -> "High"
                        CompressionPreset.MEDIUM -> "Balanced"
                        CompressionPreset.LOW -> "Max Space"
                        CompressionPreset.SMART_AUTO -> "AI Auto"
                        CompressionPreset.NEAR_LOSSLESS -> "Archival"
                        CompressionPreset.LOSSLESS -> "Lossless"
                    }
                    val percent = presetEstimates[preset]?.savingsPercent
                        ?.let { if (it > 0) "-$it%" else "~0%" } ?: "—"

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = if (selected) null else BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedPreset = preset }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = percent,
                                fontSize = 11.sp,
                                color = if (selected) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // AI Remaster Detail Pop Option
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (enableAiRemaster) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    1.dp,
                    if (enableAiRemaster) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = if (enableAiRemaster) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "✨ AI Remaster Detail Pop",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Sharpen edges & dynamic shadow recovery",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Switch(
                        checked = enableAiRemaster,
                        onCheckedChange = { enableAiRemaster = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                        )
                    )
                }
            }

            // Reversible Safety Net card, worded from the user's actual vault settings
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            vaultEnabled == false ->
                                "Safety Vault is off: on Replace, the original is not backed up here and cannot be reverted from Details."
                            retentionDays != null && retentionDays > 0 ->
                                "$retentionDays-Day Safety Net: Original will be preserved in your local vault and can be reverted from Details."
                            retentionDays == 0 ->
                                "Safety Net: Original will be preserved in your local vault until you clear it, and can be reverted from Details."
                            else ->
                                "Safety Net: Original will be preserved in your local vault and can be reverted from Details."
                        },
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )
                }
            }

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onDismiss()
                        onConfirmAction(selectedPreset, remasterDetailLevel, CompressionOriginalAction.MOVE)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = "Optimize & Replace",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onConfirmAction(selectedPreset, remasterDetailLevel, CompressionOriginalAction.COPY)
                        },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(
                            text = "Save as Copy",
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (item.isImage) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenStudio()
                            },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Studio View",
                                fontSize = 13.sp,
                                color = Color.White,
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

/** Same default strength the Compression Studio's Detail Boost slider starts at. */
private const val SMART_OPTIMIZE_REMASTER_LEVEL = 0.5f
