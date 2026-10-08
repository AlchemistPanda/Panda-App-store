package com.pandagallery.app.domain.model

import android.net.Uri

/**
 * Geographical latitude and longitude coordinate.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Bounding box for map viewports and geographic clusters.
 */
data class GeoBoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
) {
    val center: GeoPoint
        get() = GeoPoint((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)
}

/**
 * A media item containing valid GPS coordinates.
 */
data class PhotoGpsItem(
    val mediaId: Long,
    val uri: Uri,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val dateTaken: Long? = null,
    val size: Long = 0L,
    val isVideo: Boolean = false,
)

/**
 * Represents a dynamic cluster of geotagged photos grouped by geographical proximity.
 * Mirrors Samsung Gallery 15.9's `clustering_map_bottom_sheet_v2.xml`.
 */
data class PhotoGpsCluster(
    val clusterId: String,
    val center: GeoPoint,
    val title: String,
    val items: List<PhotoGpsItem>,
    val itemCount: Int = items.size,
    val coverItem: PhotoGpsItem = items.first(),
    val totalBytes: Long = items.sumOf { it.size },
) {
    val estimatedVaultSavingsBytes: Long
        get() = (totalBytes * 0.65).toLong()
}

/**
 * Viewport state for the interactive map canvas.
 */
data class MapViewport(
    val centerLat: Double = 20.5937,
    val centerLng: Double = 78.9629,
    val zoom: Float = 3.5f, // 1f = full world, 18f = street level
    val minZoom: Float = 1.2f,
    val maxZoom: Float = 18f,
)
