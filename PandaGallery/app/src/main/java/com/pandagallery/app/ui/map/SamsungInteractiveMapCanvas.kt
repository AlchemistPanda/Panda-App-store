package com.pandagallery.app.ui.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pandagallery.app.data.smart.map.GpsClusteringEngine
import com.pandagallery.app.domain.model.GeoPoint
import com.pandagallery.app.domain.model.MapViewport
import com.pandagallery.app.domain.model.PhotoGpsCluster
import com.pandagallery.app.domain.model.PhotoGpsItem
import kotlin.math.roundToInt

val MapOceanDark = Color(0xFF0B0F17)
val MapLandmassDark = Color(0xFF151D2A)
val MapBorderDark = Color(0xFF222E42)
val MapGridlineDark = Color(0xFF192233)
val MapCyanAccent = Color(0xFF00C4B4)

/**
 * High-performance vector map canvas with dark AMOLED styling, fluid pan/zoom,
 * and Samsung One UI photo cluster pins.
 */
@Composable
fun SamsungInteractiveMapCanvas(
    items: List<PhotoGpsItem>,
    initialCenter: GeoPoint? = null,
    initialZoom: Float = 4.0f,
    selectedCluster: PhotoGpsCluster? = null,
    onClusterSelected: (PhotoGpsCluster) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var centerLat by remember { mutableDoubleStateOf(initialCenter?.latitude ?: (items.firstOrNull()?.latitude ?: 20.5937)) }
    var centerLng by remember { mutableDoubleStateOf(initialCenter?.longitude ?: (items.firstOrNull()?.longitude ?: 78.9629)) }
    var zoom by remember { mutableFloatStateOf(initialZoom.coerceIn(1.5f, 16f)) }

    LaunchedEffect(initialCenter) {
        if (initialCenter != null) {
            centerLat = initialCenter.latitude
            centerLng = initialCenter.longitude
            zoom = 8.5f
        }
    }

    val clusters = remember(items, centerLat, centerLng, zoom, canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            GpsClusteringEngine.clusterPhotos(
                items = items,
                centerLat = centerLat,
                centerLng = centerLng,
                zoom = zoom,
                screenWidth = canvasSize.width.toFloat(),
                screenHeight = canvasSize.height.toFloat(),
                clusterRadiusPx = 72f,
            )
        } else emptyList()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapOceanDark)
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val newZoom = (zoom * gestureZoom).coerceIn(1.5f, 16f)
                    zoom = newZoom

                    // Pan offset in geographical coordinates
                    val deltaLat = -(pan.y / (256f * Math.pow(2.0, newZoom.toDouble())) * 140.0)
                    val deltaLng = -(pan.x / (256f * Math.pow(2.0, newZoom.toDouble())) * 360.0)

                    centerLat = (centerLat + deltaLat).coerceIn(-80.0, 80.0)
                    centerLng = (centerLng + deltaLng).coerceIn(-180.0, 180.0)
                }
            }
            .pointerInput(canvasSize) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val tappedGeo = GpsClusteringEngine.screenToLatLng(
                            screenOffset = tapOffset,
                            centerLat = centerLat,
                            centerLng = centerLng,
                            zoom = zoom,
                            screenWidth = canvasSize.width.toFloat(),
                            screenHeight = canvasSize.height.toFloat(),
                        )
                        centerLat = tappedGeo.latitude
                        centerLng = tappedGeo.longitude
                        zoom = (zoom + 1.5f).coerceAtMost(16f)
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                )
            },
    ) {
        // Base AMOLED Vector Map Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMapGrid(centerLat, centerLng, zoom)
            drawWorldLandmasses(centerLat, centerLng, zoom)
        }

        // Render Samsung Photo Cluster Pins as Overlay Composables
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            clusters.forEach { cluster ->
                val screenPos = GpsClusteringEngine.latLngToScreen(
                    lat = cluster.center.latitude,
                    lng = cluster.center.longitude,
                    centerLat = centerLat,
                    centerLng = centerLng,
                    zoom = zoom,
                    screenWidth = canvasSize.width.toFloat(),
                    screenHeight = canvasSize.height.toFloat(),
                )

                // Only render if on screen with margin
                if (screenPos.x in -60f..(canvasSize.width + 60f) && screenPos.y in -60f..(canvasSize.height + 60f)) {
                    val isSelected = selectedCluster?.clusterId == cluster.clusterId

                    SamsungPhotoPin(
                        cluster = cluster,
                        isSelected = isSelected,
                        screenPos = screenPos,
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            centerLat = cluster.center.latitude
                            centerLng = cluster.center.longitude
                            onClusterSelected(cluster)
                        },
                    )
                }
            }
        }

        // Floating Map Controls (Zoom In, Zoom Out, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF181C24).copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                shadowElevation = 8.dp,
            ) {
                Column {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            zoom = (zoom + 1.0f).coerceAtMost(16f)
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Zoom In", tint = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(0.6.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                            .align(Alignment.CenterHorizontally)
                    )
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            zoom = (zoom - 1.0f).coerceAtLeast(1.5f)
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Zoom Out", tint = Color.White)
                    }
                }
            }

            // Recenter / Fit All Pins
            Surface(
                shape = CircleShape,
                color = Color(0xFF181C24).copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                shadowElevation = 8.dp,
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        if (items.isNotEmpty()) {
                            val avgLat = items.map { it.latitude }.average()
                            val avgLng = items.map { it.longitude }.average()
                            centerLat = avgLat
                            centerLng = avgLng
                            zoom = 4.5f
                        }
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MyLocation,
                        contentDescription = "Fit Pins",
                        tint = MapCyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom Left Zoom Level Badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.6f),
            border = androidx.compose.foundation.BorderStroke(0.6.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = "%.1fx".format(zoom),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Signature Samsung Gallery Photo Pin:
 * Avatar bubble with photo thumbnail, glowing cyan border, stem indicator,
 * and count badge.
 */
@Composable
private fun BoxScope.SamsungPhotoPin(
    cluster: PhotoGpsCluster,
    isSelected: Boolean,
    screenPos: Offset,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "PinPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "GlowAlpha",
    )

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (screenPos.x - 26.dp.toPx()).roundToInt(),
                    y = (screenPos.y - 54.dp.toPx()).roundToInt(),
                )
            }
            .clickable { onClick() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(-4.dp),
        ) {
            // Pin Head with Photo Thumbnail and Count Pill
            Box(
                contentAlignment = Alignment.TopEnd,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E2430),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.5.dp else 1.8.dp,
                        color = if (isSelected) MapCyanAccent.copy(alpha = glowAlpha) else Color.White,
                    ),
                    shadowElevation = if (isSelected) 12.dp else 6.dp,
                    modifier = Modifier.size(52.dp),
                ) {
                    val request = remember(cluster.coverItem.uri) {
                        ImageRequest.Builder(context)
                            .data(cluster.coverItem.uri)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = cluster.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Counter Badge Pill (if more than 1 item)
                if (cluster.itemCount > 1) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MapCyanAccent,
                        shadowElevation = 4.dp,
                        modifier = Modifier.offset(x = 6.dp, y = (-4).dp),
                    ) {
                        Text(
                            text = cluster.itemCount.toString(),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            // Downward Pointer Stem
            Canvas(modifier = Modifier.size(12.dp, 8.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, color = if (isSelected) MapCyanAccent else Color.White)
            }
        }
    }
}

/**
 * Draws geographic meridians and parallels.
 */
private fun DrawScope.drawMapGrid(centerLat: Double, centerLng: Double, zoom: Float) {
    val stepDeg = when {
        zoom < 3.0f -> 45.0
        zoom < 6.0f -> 20.0
        zoom < 9.0f -> 10.0
        else -> 2.0
    }

    var lat = -80.0
    while (lat <= 80.0) {
        val y = GpsClusteringEngine.latLngToScreen(lat, 0.0, centerLat, centerLng, zoom, size.width, size.height).y
        if (y in 0f..size.height) {
            drawLine(
                color = MapGridlineDark,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.8f,
            )
        }
        lat += stepDeg
    }

    var lng = -180.0
    while (lng <= 180.0) {
        val x = GpsClusteringEngine.latLngToScreen(0.0, lng, centerLat, centerLng, zoom, size.width, size.height).x
        if (x in 0f..size.width) {
            drawLine(
                color = MapGridlineDark,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.8f,
            )
        }
        lng += stepDeg
    }
}

/**
 * Draws vectorized world landmasses for high-aesthetic offline map visualization.
 */
private fun DrawScope.drawWorldLandmasses(centerLat: Double, centerLng: Double, zoom: Float) {
    for (polygon in CONTINENTAL_POLYGONS) {
        val path = Path()
        var first = true

        for (geo in polygon) {
            val screenPos = GpsClusteringEngine.latLngToScreen(
                lat = geo.first,
                lng = geo.second,
                centerLat = centerLat,
                centerLng = centerLng,
                zoom = zoom,
                screenWidth = size.width,
                screenHeight = size.height,
            )
            if (first) {
                path.moveTo(screenPos.x, screenPos.y)
                first = false
            } else {
                path.lineTo(screenPos.x, screenPos.y)
            }
        }
        path.close()

        drawPath(path, color = MapLandmassDark, style = Fill)
        drawPath(path, color = MapBorderDark, style = Stroke(width = 1.2f))
    }
}

/**
 * Simplified world continental contours in geographic (Lat, Lng) coordinates.
 */
private val CONTINENTAL_POLYGONS = listOf(
    // North America
    listOf(
        70.0 to -165.0, 72.0 to -130.0, 60.0 to -85.0, 50.0 to -55.0,
        30.0 to -80.0, 25.0 to -80.0, 20.0 to -105.0, 32.0 to -117.0,
        48.0 to -125.0, 60.0 to -140.0, 65.0 to -168.0
    ),
    // South America
    listOf(
        12.0 to -72.0, -5.0 to -35.0, -22.0 to -41.0, -55.0 to -65.0,
        -50.0 to -75.0, -18.0 to -70.0, -4.0 to -81.0, 9.0 to -78.0
    ),
    // Europe & Asia (Eurasia)
    listOf(
        71.0 to 28.0, 68.0 to 50.0, 70.0 to 90.0, 76.0 to 140.0,
        65.0 to 175.0, 45.0 to 142.0, 35.0 to 130.0, 22.0 to 115.0,
        10.0 to 105.0, 8.0 to 77.0, 25.0 to 65.0, 15.0 to 50.0,
        30.0 to 32.0, 36.0 to -5.0, 44.0 to -9.0, 58.0 to 5.0, 60.0 to 25.0
    ),
    // Africa
    listOf(
        35.0 to -6.0, 32.0 to 32.0, 12.0 to 51.0, -12.0 to 40.0,
        -34.0 to 18.0, -34.0 to 26.0, -15.0 to 12.0, 5.0 to 2.0,
        5.0 to -10.0, 15.0 to -17.0, 28.0 to -13.0
    ),
    // Australia
    listOf(
        -12.0 to 132.0, -12.0 to 142.0, -28.0 to 153.0, -38.0 to 145.0,
        -32.0 to 115.0, -22.0 to 114.0, -15.0 to 124.0
    ),
    // Indian Subcontinent Detail Contour
    listOf(
        35.0 to 74.0, 31.0 to 79.0, 27.0 to 88.0, 22.0 to 92.0,
        16.0 to 82.0, 8.0 to 77.5, 15.0 to 73.5, 23.0 to 68.5, 28.0 to 70.0
    )
)
