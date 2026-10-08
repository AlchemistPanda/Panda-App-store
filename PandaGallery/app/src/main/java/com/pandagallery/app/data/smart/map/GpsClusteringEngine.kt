package com.pandagallery.app.data.smart.map

import androidx.compose.ui.geometry.Offset
import com.pandagallery.app.domain.model.GeoPoint
import com.pandagallery.app.domain.model.PhotoGpsCluster
import com.pandagallery.app.domain.model.PhotoGpsItem
import kotlin.math.*

/**
 * High-performance spatial clustering and Web Mercator projection engine.
 * Converts GPS coordinates to screen pixel space and dynamically clusters
 * photos at the current viewport zoom level without requiring Google Maps API keys.
 */
object GpsClusteringEngine {

    private const val MERCATOR_MAX_LAT = 85.05112878

    /**
     * Converts Latitude and Longitude to normalized Web Mercator coordinates [0..1, 0..1].
     */
    fun latLngToWorld(lat: Double, lng: Double): Offset {
        val clampedLat = lat.coerceIn(-MERCATOR_MAX_LAT, MERCATOR_MAX_LAT)
        val x = ((lng + 180.0) / 360.0).coerceIn(0.0, 1.0)
        val latRad = Math.toRadians(clampedLat)
        val y = ((1.0 - ln(tan(Math.PI / 4.0 + latRad / 2.0)) / Math.PI) / 2.0).coerceIn(0.0, 1.0)
        return Offset(x.toFloat(), y.toFloat())
    }

    /**
     * Converts normalized Web Mercator coordinates [0..1, 0..1] back to Latitude and Longitude.
     */
    fun worldToLatLng(worldX: Float, worldY: Float): GeoPoint {
        val lng = worldX.toDouble() * 360.0 - 180.0
        val n = Math.PI * (1.0 - 2.0 * worldY.toDouble())
        val latRad = atan(sinh(n))
        val lat = Math.toDegrees(latRad).coerceIn(-MERCATOR_MAX_LAT, MERCATOR_MAX_LAT)
        return GeoPoint(latitude = lat, longitude = lng.coerceIn(-180.0, 180.0))
    }

    /**
     * Projects a geographical coordinate to on-screen pixel space given viewport center and zoom.
     */
    fun latLngToScreen(
        lat: Double,
        lng: Double,
        centerLat: Double,
        centerLng: Double,
        zoom: Float,
        screenWidth: Float,
        screenHeight: Float,
    ): Offset {
        val world = latLngToWorld(lat, lng)
        val centerWorld = latLngToWorld(centerLat, centerLng)
        val scale = 256.0f * 2.0f.pow(zoom)

        val deltaX = (world.x - centerWorld.x) * scale
        val deltaY = (world.y - centerWorld.y) * scale

        return Offset(
            x = screenWidth / 2f + deltaX,
            y = screenHeight / 2f + deltaY,
        )
    }

    /**
     * Converts an on-screen pixel tap or offset back to geographical coordinate.
     */
    fun screenToLatLng(
        screenOffset: Offset,
        centerLat: Double,
        centerLng: Double,
        zoom: Float,
        screenWidth: Float,
        screenHeight: Float,
    ): GeoPoint {
        val centerWorld = latLngToWorld(centerLat, centerLng)
        val scale = 256.0f * 2.0f.pow(zoom)

        val worldX = centerWorld.x + (screenOffset.x - screenWidth / 2f) / scale
        val worldY = centerWorld.y + (screenOffset.y - screenHeight / 2f) / scale

        return worldToLatLng(worldX, worldY)
    }

    /**
     * Clusters a list of geotagged photos on-screen based on pixel distance threshold.
     * Nearby photos merge into clusters; distant photos remain individual pins.
     */
    fun clusterPhotos(
        items: List<PhotoGpsItem>,
        centerLat: Double,
        centerLng: Double,
        zoom: Float,
        screenWidth: Float,
        screenHeight: Float,
        clusterRadiusPx: Float = 64f,
    ): List<PhotoGpsCluster> {
        if (items.isEmpty()) return emptyList()

        val mapped = items.map { item ->
            val screenPos = latLngToScreen(
                lat = item.latitude,
                lng = item.longitude,
                centerLat = centerLat,
                centerLng = centerLng,
                zoom = zoom,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
            item to screenPos
        }

        val clusters = mutableListOf<MutableList<Pair<PhotoGpsItem, Offset>>>()

        for (itemWithPos in mapped) {
            val (_, pos) = itemWithPos
            var matchedCluster: MutableList<Pair<PhotoGpsItem, Offset>>? = null

            for (cluster in clusters) {
                // Check distance against cluster center
                val avgX = cluster.map { it.second.x }.average().toFloat()
                val avgY = cluster.map { it.second.y }.average().toFloat()
                val dist = hypot(pos.x - avgX, pos.y - avgY)

                if (dist <= clusterRadiusPx) {
                    matchedCluster = cluster
                    break
                }
            }

            if (matchedCluster != null) {
                matchedCluster.add(itemWithPos)
            } else {
                clusters.add(mutableListOf(itemWithPos))
            }
        }

        return clusters.mapIndexed { index, clusterItems ->
            val photoItems = clusterItems.map { it.first }
            val avgLat = photoItems.map { it.latitude }.average()
            val avgLng = photoItems.map { it.longitude }.average()
            val cover = photoItems.maxByOrNull { it.dateTaken ?: 0L } ?: photoItems.first()
            val title = resolveLocationName(avgLat, avgLng)

            PhotoGpsCluster(
                clusterId = "cluster_$index",
                center = GeoPoint(avgLat, avgLng),
                title = title,
                items = photoItems,
                itemCount = photoItems.size,
                coverItem = cover,
            )
        }
    }

    /**
     * Resolves location name based on geographic proximity to major global cities and regions.
     */
    fun resolveLocationName(latitude: Double, longitude: Double): String {
        var closestCity: String? = null
        var minDistanceKm = Double.MAX_VALUE

        for (city in KNOWN_CITIES) {
            val dist = haversineDistanceKm(latitude, longitude, city.lat, city.lng)
            if (dist < minDistanceKm) {
                minDistanceKm = dist
                closestCity = city.name
            }
        }

        return if (closestCity != null && minDistanceKm <= 65.0) {
            closestCity
        } else {
            val latDir = if (latitude >= 0) "N" else "S"
            val lngDir = if (longitude >= 0) "E" else "W"
            String.format(java.util.Locale.US, "%.2f° %s, %.2f° %s", abs(latitude), latDir, abs(longitude), lngDir)
        }
    }

    /**
     * Computes great-circle distance between two GPS coordinates in kilometers.
     */
    fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    private data class CityInfo(val name: String, val lat: Double, val lng: Double)

    private val KNOWN_CITIES = listOf(
        CityInfo("Bengaluru, India", 12.9716, 77.5946),
        CityInfo("Mumbai, India", 19.0760, 72.8777),
        CityInfo("Delhi, India", 28.6139, 77.2090),
        CityInfo("Goa, India", 15.2993, 74.1240),
        CityInfo("Hyderabad, India", 17.3850, 78.4867),
        CityInfo("Chennai, India", 13.0827, 80.2707),
        CityInfo("Kolkata, India", 22.5726, 88.3639),
        CityInfo("Jaipur, India", 26.9124, 75.7873),
        CityInfo("Paris, France", 48.8566, 2.3522),
        CityInfo("London, UK", 51.5074, -0.1278),
        CityInfo("New York, USA", 40.7128, -74.0060),
        CityInfo("San Francisco, USA", 37.7749, -122.4194),
        CityInfo("Los Angeles, USA", 34.0522, -118.2437),
        CityInfo("Chicago, USA", 41.8781, -87.6298),
        CityInfo("Tokyo, Japan", 35.6762, 139.6503),
        CityInfo("Kyoto, Japan", 35.0116, 135.7681),
        CityInfo("Sydney, Australia", -33.8688, 151.2093),
        CityInfo("Melbourne, Australia", -37.8136, 144.9631),
        CityInfo("Dubai, UAE", 25.2048, 55.2708),
        CityInfo("Singapore", 1.3521, 103.8198),
        CityInfo("Rome, Italy", 41.9028, 12.4964),
        CityInfo("Berlin, Germany", 52.5200, 13.4050),
        CityInfo("Amsterdam, Netherlands", 52.3676, 4.9041),
        CityInfo("Barcelona, Spain", 41.3851, 2.1734),
        CityInfo("Toronto, Canada", 43.6532, -79.3832),
        CityInfo("Seoul, South Korea", 37.5665, 126.9780),
        CityInfo("Bangkok, Thailand", 13.7563, 100.5018),
    )
}
