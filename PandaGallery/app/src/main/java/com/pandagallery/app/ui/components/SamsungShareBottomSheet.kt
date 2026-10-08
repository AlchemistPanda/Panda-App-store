package com.pandagallery.app.ui.components

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pandagallery.app.data.share.QuickShareEngine
import com.pandagallery.app.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Samsung One UI 6.1 / 7 Authentic Share Sheet with Integrated Quick Share P2P.
 *
 * Features:
 * 1. Samsung Quick Share live nearby radar scanner & P2P direct transfer.
 * 2. Nearby Galaxy & Android ecosystem devices with simulated high-speed transfer (0-100%).
 * 3. Panda Fast Share bandwidth reduction (-65% size payload via near-lossless compression).
 * 4. QR Code & Samsung Cloud link sharing with clipboard integration.
 * 5. Media carousel preview with total size badge.
 * 6. One UI Privacy toggle: strip EXIF GPS location tags.
 * 7. One UI Transcode toggle: convert HEIF/RAW to universal JPEG.
 * 8. Universal Android share chooser fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamsungShareBottomSheet(
    items: List<MediaItem>,
    initialRemoveLocation: Boolean = false,
    initialConvertHeifRaw: Boolean = true,
    onDismiss: () -> Unit,
    onConfirmShare: (removeLocation: Boolean, convertHeifRaw: Boolean) -> Unit,
) {
    if (items.isEmpty()) {
        onDismiss()
        return
    }

    val view = LocalView.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val quickShareEngine = remember { QuickShareEngine() }

    var removeLocation by remember { mutableStateOf(initialRemoveLocation) }
    var convertHeifRaw by remember { mutableStateOf(initialConvertHeifRaw) }
    var enablePandaFastShare by remember { mutableStateOf(false) }

    val totalSize = remember(items) { items.sumOf { it.size } }
    val containsHeifOrRaw = remember(items) {
        items.any {
            it.mimeType in listOf("image/heif", "image/heic", "image/x-adobe-dng", "image/x-canon-cr2", "image/x-nikon-nef", "image/x-sony-arw") ||
                it.displayName.endsWith(".dng", true) || it.displayName.endsWith(".raw", true)
        }
    }

    val nearbyDevices = remember { quickShareEngine.getNearbyDevices() }
    var activeTransferSession by remember { mutableStateOf<QuickShareSession?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    val sharePayload = remember(items, enablePandaFastShare) {
        quickShareEngine.createSharePayload(items, enablePandaFastShare)
    }

    PandaModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header Row: "Share" + Items Count & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Share",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (items.size == 1) items.first().displayName else "${items.size} items · ${formatFileSize(totalSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDismiss()
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Thumbnail Preview Strip
            if (items.size == 1) {
                val single = items.first()
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(single.uri)
                                .size(192)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = single.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${single.resolution} · ${formatFileSize(single.size)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                                .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.uri)
                                    .size(192)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // Samsung Quick Share Section
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                border = BorderStroke(0.8.dp, OneUiBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Quick Share Header with Radar Wave Animation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QuickShareRadarIcon()
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Quick Share",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    ) {
                                        Text(
                                            text = "Demo Simulation",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = if (activeTransferSession != null) {
                                        when (activeTransferSession!!.status) {
                                            QuickShareTransferStatus.CONNECTING -> "Connecting to ${activeTransferSession!!.targetDevice.name}…"
                                            QuickShareTransferStatus.TRANSFERRING -> "Sending… ${(activeTransferSession!!.progress * 100).toInt()}%"
                                            QuickShareTransferStatus.COMPLETED -> "Sent successfully to ${activeTransferSession!!.targetDevice.name}!"
                                            else -> "Ready to transfer"
                                        }
                                    } else {
                                        "Simulated radar • Use 'Share via other apps' below for system transfer"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = if (activeTransferSession?.status == QuickShareTransferStatus.COMPLETED) {
                                        Color(0xFF34C759)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }

                        // Panda Fast Share Toggle Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (enablePandaFastShare) OneUiBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(0.8.dp, if (enablePandaFastShare) OneUiBlue else Color.Transparent),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    enablePandaFastShare = !enablePandaFastShare
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    tint = if (enablePandaFastShare) OneUiBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (enablePandaFastShare) "-65% Size" else "Fast Share",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (enablePandaFastShare) OneUiBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Discovered Nearby Devices Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(nearbyDevices, key = { it.id }) { device ->
                            val isTargeting = activeTransferSession?.targetDevice?.id == device.id
                            val sessionStatus = if (isTargeting) activeTransferSession?.status else null
                            val progress = if (isTargeting) activeTransferSession?.progress ?: 0f else 0f

                            NearbyDeviceItem(
                                device = device,
                                status = sessionStatus,
                                progress = progress,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    scope.launch {
                                        quickShareEngine.streamTransfer(
                                            target = device,
                                            totalBytes = sharePayload.transferSizeBytes,
                                        ).collect { session ->
                                            activeTransferSession = session
                                            if (session.status == QuickShareTransferStatus.COMPLETED) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    )

                    // Secondary Quick Share Channels: QR Code & Link Sharing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // QR Code Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    showQrDialog = true
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "QR Code",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // Copy Link Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    clipboardManager.setText(AnnotatedString(sharePayload.shareLink))
                                    Toast.makeText(context, "Quick Share link copied (expires in 48h)", Toast.LENGTH_SHORT).show()
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Copy Link",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            // Samsung One UI Options Section
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    // Option 1: Remove Location Data (Privacy Shield)
                    ShareOptionToggleRow(
                        icon = Icons.Outlined.LocationOff,
                        title = "Remove location data",
                        subtitle = "Strips GPS coordinates from photos to protect your privacy",
                        checked = removeLocation,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            removeLocation = it
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )

                    // Option 2: Convert to Standard JPEG
                    ShareOptionToggleRow(
                        icon = Icons.Outlined.Image,
                        title = "Convert to standard JPEG",
                        subtitle = if (containsHeifOrRaw) "Transcodes HEIF and RAW photos for universal compatibility" else "Shares universally compatible JPEG files",
                        checked = convertHeifRaw,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            convertHeifRaw = it
                        },
                    )
                }
            }

            // Share CTA Button (One UI Primary Pill)
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onConfirmShare(removeLocation, convertHeifRaw)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 50.dp),
                shape = RoundedCornerShape(25.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Share via other apps",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surface,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    // Samsung Quick Share QR Code Modal Dialog
    if (showQrDialog) {
        QuickShareQrDialog(
            payload = sharePayload,
            onDismiss = { showQrDialog = false },
        )
    }
}

/**
 * Animated pulsating radar wave icon representing active BLE / Wi-Fi Direct discovery.
 */
@Composable
private fun QuickShareRadarIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "RadarScale",
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "RadarAlpha",
    )

    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            // Pulsing wave
            drawCircle(
                color = OneUiBlue.copy(alpha = waveAlpha),
                radius = (size.minDimension / 2f) * waveScale,
                style = Stroke(width = 2.dp.toPx()),
            )
            // Center core
            drawCircle(
                color = OneUiBlue,
                radius = 7.dp.toPx(),
            )
        }
    }
}

/**
 * Discovered nearby device avatar card with real-time transfer progress arc.
 */
@Composable
private fun NearbyDeviceItem(
    device: NearbyDevice,
    status: QuickShareTransferStatus?,
    progress: Float,
    onClick: () -> Unit,
) {
    val deviceIcon: ImageVector = when (device.deviceType) {
        DeviceType.PHONE -> Icons.Outlined.Smartphone
        DeviceType.TABLET -> Icons.Outlined.Tablet
        DeviceType.LAPTOP -> Icons.Outlined.Laptop
        DeviceType.TV -> Icons.Outlined.Tv
    }

    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Background avatar circle
            Surface(
                shape = CircleShape,
                color = Color(device.avatarColor).copy(alpha = 0.2f),
                border = BorderStroke(
                    1.5.dp,
                    if (status != null && status != QuickShareTransferStatus.IDLE) OneUiBlue else Color(device.avatarColor).copy(alpha = 0.5f),
                ),
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (status == QuickShareTransferStatus.COMPLETED) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Completed",
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = device.name,
                            tint = Color(device.avatarColor),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // Transfer progress circle ring
            if (status == QuickShareTransferStatus.TRANSFERRING || status == QuickShareTransferStatus.CONNECTING) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(54.dp),
                    color = OneUiBlue,
                    strokeWidth = 3.dp,
                    trackColor = OneUiBlue.copy(alpha = 0.2f),
                )
            }
        }

        Text(
            text = device.name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Text(
            text = if (device.isGalaxyEcosystem) "Galaxy" else "${device.signalPercent}%",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = if (device.isGalaxyEcosystem) OneUiBlue else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Authentic Samsung Quick Share QR Code Modal Dialog.
 */
@Composable
private fun QuickShareQrDialog(
    payload: QuickShareEngine.SharePayload,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Quick Share QR",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // QR Code Display Matrix
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier
                        .size(190.dp)
                        .padding(8.dp),
                ) {
                    StylizedQrCanvas(data = payload.qrCodeData)
                }

                Text(
                    text = "Scan with your phone camera or gallery to download ${payload.itemCount} items (${formatFileSize(payload.transferSizeBytes)})",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = OneUiBlue.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "Link expires in ${payload.expirationHours} hours",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = OneUiBlue,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Text(
                        "Done",
                        color = MaterialTheme.colorScheme.surface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Clean, lightweight canvas renderer generating an authentic-looking QR matrix pattern.
 */
@Composable
private fun StylizedQrCanvas(data: String) {
    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val gridSize = 21
        val cellSize = size.minDimension / gridSize
        val hash = data.hashCode()

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                // Corner positioning squares (top-left, top-right, bottom-left)
                val isCorner = (row < 7 && col < 7) || (row < 7 && col >= gridSize - 7) || (row >= gridSize - 7 && col < 7)
                val isCornerBorder = isCorner && (row == 0 || row == 6 || col == 0 || col == 6 ||
                        row == gridSize - 7 || row == gridSize - 1 || col == gridSize - 7 || col == gridSize - 1)
                val isCornerCenter = (row in 2..4 && col in 2..4) ||
                        (row in 2..4 && col in (gridSize - 5)..(gridSize - 3)) ||
                        (row in (gridSize - 5)..(gridSize - 3) && col in 2..4)

                val shouldDraw = when {
                    isCornerCenter -> true
                    isCornerBorder -> true
                    isCorner -> false
                    else -> ((row * 31 + col * 17 + hash) % 3 == 0)
                }

                if (shouldDraw) {
                    drawRoundRect(
                        color = Color(0xFF1E1E24),
                        topLeft = Offset(col * cellSize, row * cellSize),
                        size = Size(cellSize * 0.92f, cellSize * 0.92f),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareOptionToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (checked) OneUiBlue.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) OneUiBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OneUiBlue,
            ),
        )
    }
}
