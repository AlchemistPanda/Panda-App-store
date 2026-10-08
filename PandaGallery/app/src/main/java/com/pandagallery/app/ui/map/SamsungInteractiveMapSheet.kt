package com.pandagallery.app.ui.map

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pandagallery.app.domain.model.GeoPoint
import com.pandagallery.app.domain.model.PhotoGpsCluster
import com.pandagallery.app.domain.model.PhotoGpsItem
import com.pandagallery.app.domain.model.formatFileSize

/**
 * Fullscreen Samsung One UI Interactive Map View with bottom sliding media drawer
 * (mimicking Samsung Gallery 15.9's `clustering_map_bottom_sheet_v2.xml`).
 */
@Composable
fun SamsungInteractiveMapSheet(
    items: List<PhotoGpsItem>,
    initialCenter: GeoPoint? = null,
    onDismiss: () -> Unit,
    onOpenMedia: (Long) -> Unit,
    onCompressCluster: (PhotoGpsCluster) -> Unit,
    onStripGps: ((PhotoGpsCluster) -> Unit)? = null,
) {
    val view = LocalView.current
    var selectedCluster by remember { mutableStateOf<PhotoGpsCluster?>(null) }
    var showCompressConfirmDialog by remember { mutableStateOf<PhotoGpsCluster?>(null) }
    var showStripConfirmDialog by remember { mutableStateOf<PhotoGpsCluster?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
        ) {
            // Interactive Vector Map Canvas
            SamsungInteractiveMapCanvas(
                items = items,
                initialCenter = initialCenter,
                selectedCluster = selectedCluster,
                onClusterSelected = { cluster ->
                    selectedCluster = cluster
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Top Header Bar
            Surface(
                color = Color(0xFF0B0F17).copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            onDismiss()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "Map View",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${items.size} geotagged photo${if (items.size == 1) "" else "s"}",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                        )
                    }

                    if (selectedCluster != null) {
                        IconButton(
                            onClick = {
                                selectedCluster = null
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOff,
                                contentDescription = "Deselect Cluster",
                                tint = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            // Bottom Sliding Cluster Card (Samsung `clustering_map_bottom_sheet_v2.xml`)
            AnimatedVisibility(
                visible = selectedCluster != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                selectedCluster?.let { cluster ->
                    SamsungClusterMediaCard(
                        cluster = cluster,
                        onOpenMedia = { mediaId ->
                            onOpenMedia(mediaId)
                            onDismiss()
                        },
                        onCompressClick = {
                            showCompressConfirmDialog = cluster
                        },
                        onStripGpsClick = {
                            showStripConfirmDialog = cluster
                        },
                    )
                }
            }

            // Confirmation: Compress Cluster to Vault Dialog
            showCompressConfirmDialog?.let { cluster ->
                AlertDialog(
                    onDismissRequest = { showCompressConfirmDialog = null },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.ElectricBolt,
                            contentDescription = null,
                            tint = MapCyanAccent,
                        )
                    },
                    title = {
                        Text(
                            text = "Compress Location Photos",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Compress all ${cluster.itemCount} photos taken in ${cluster.title}?",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E2430),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Estimated Space Savings: ~${formatFileSize(cluster.estimatedVaultSavingsBytes)} (-65%)",
                                        color = MapCyanAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Original full-resolution photos are protected in Panda's 14-day Safety Vault and can be restored at any time.",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val target = cluster
                                showCompressConfirmDialog = null
                                onCompressCluster(target)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MapCyanAccent,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text("Compress to Vault", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCompressConfirmDialog = null }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                        }
                    },
                    containerColor = Color(0xFF161B24),
                )
            }

            // Confirmation: Strip GPS Dialog
            showStripConfirmDialog?.let { cluster ->
                AlertDialog(
                    onDismissRequest = { showStripConfirmDialog = null },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.LocationOff,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                        )
                    },
                    title = {
                        Text(
                            text = "Strip GPS Metadata",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        Text(
                            text = "Remove EXIF location data from ${cluster.itemCount} photo${if (cluster.itemCount == 1) "" else "s"} in ${cluster.title}? This cannot be undone.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val target = cluster
                                showStripConfirmDialog = null
                                onStripGps?.invoke(target)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B6B),
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Strip Location", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStripConfirmDialog = null }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                        }
                    },
                    containerColor = Color(0xFF161B24),
                )
            }
        }
    }
}

/**
 * Bottom sliding cluster info sheet with horizontal photo carousel and 1-tap actions.
 */
@Composable
private fun SamsungClusterMediaCard(
    cluster: PhotoGpsCluster,
    onOpenMedia: (Long) -> Unit,
    onCompressClick: () -> Unit,
    onStripGpsClick: () -> Unit,
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xFF161B24).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 20.dp),
        ) {
            // Drag Handle Bar
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape)
                    .align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Cluster Title & Statistics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MapCyanAccent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cluster.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${cluster.itemCount} photo${if (cluster.itemCount == 1) "" else "s"} • ${formatFileSize(cluster.totalBytes)}",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizontal Photo Carousel
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(cluster.items, key = { it.mediaId }) { photo ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF222B38),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .size(76.dp)
                            .clickable { onOpenMedia(photo.mediaId) },
                    ) {
                        val request = remember(photo.uri) {
                            ImageRequest.Builder(context)
                                .data(photo.uri)
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = request,
                            contentDescription = photo.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1-Tap Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1-Tap Compress to Vault Button
                Button(
                    onClick = onCompressClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MapCyanAccent,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ElectricBolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Compress to Vault",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }

                // Strip GPS Button
                OutlinedButton(
                    onClick = onStripGpsClick,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Strip GPS",
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
