package com.pandagallery.app.data.smart.map

import android.net.TestUri
import com.pandagallery.app.domain.model.GeoPoint
import com.pandagallery.app.domain.model.PhotoGpsItem
import org.junit.Assert.*
import org.junit.Test

class GpsClusteringEngineTest {

    @Test
    fun testWebMercatorProjectionRoundTrip() {
        val testLocations = listOf(
            GeoPoint(12.9716, 77.5946),   // Bengaluru
            GeoPoint(48.8566, 2.3522),     // Paris
            GeoPoint(40.7128, -74.0060),   // New York
            GeoPoint(35.6762, 139.6503),   // Tokyo
            GeoPoint(-33.8688, 151.2093),  // Sydney
        )

        for (loc in testLocations) {
            val world = GpsClusteringEngine.latLngToWorld(loc.latitude, loc.longitude)
            assertTrue("World X must be in [0..1], was ${world.x}", world.x in 0f..1f)
            assertTrue("World Y must be in [0..1], was ${world.y}", world.y in 0f..1f)

            val recovered = GpsClusteringEngine.worldToLatLng(world.x, world.y)
            assertEquals("Latitude round-trip mismatch", loc.latitude, recovered.latitude, 0.001)
            assertEquals("Longitude round-trip mismatch", loc.longitude, recovered.longitude, 0.001)
        }
    }

    @Test
    fun testLatLngToScreenAndInverse() {
        val centerLat = 12.9716
        val centerLng = 77.5946
        val zoom = 6.0f
        val screenWidth = 1080f
        val screenHeight = 2400f

        val targetLat = 13.0827 // Chennai
        val targetLng = 80.2707

        val screenPos = GpsClusteringEngine.latLngToScreen(
            lat = targetLat,
            lng = targetLng,
            centerLat = centerLat,
            centerLng = centerLng,
            zoom = zoom,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )

        val recovered = GpsClusteringEngine.screenToLatLng(
            screenOffset = screenPos,
            centerLat = centerLat,
            centerLng = centerLng,
            zoom = zoom,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )

        assertEquals("Screen projection latitude mismatch", targetLat, recovered.latitude, 0.01)
        assertEquals("Screen projection longitude mismatch", targetLng, recovered.longitude, 0.01)
    }

    @Test
    fun testHaversineDistanceKm() {
        // Distance between Paris and London is approximately 343 km
        val dist = GpsClusteringEngine.haversineDistanceKm(
            lat1 = 48.8566, lon1 = 2.3522, // Paris
            lat2 = 51.5074, lon2 = -0.1278, // London
        )
        assertEquals(343.0, dist, 10.0)
    }

    @Test
    fun testResolveLocationName() {
        // Close to Bengaluru
        val nameBengaluru = GpsClusteringEngine.resolveLocationName(12.98, 77.60)
        assertEquals("Bengaluru, India", nameBengaluru)

        // Close to Paris
        val nameParis = GpsClusteringEngine.resolveLocationName(48.86, 2.34)
        assertEquals("Paris, France", nameParis)

        // Remote middle of Indian Ocean
        val nameRemote = GpsClusteringEngine.resolveLocationName(-10.0, 75.0)
        assertTrue("Expected coordinate string, got: $nameRemote", nameRemote.contains("10.00° S") && nameRemote.contains("75.00° E"))
    }

    @Test
    fun testSpatialClusteringMergesNearbyPhotos() {
        val dummyUri = TestUri()
        val items = listOf(
            PhotoGpsItem(1L, dummyUri, "photo1.jpg", 12.9716, 77.5946, size = 10_000_000L),
            PhotoGpsItem(2L, dummyUri, "photo2.jpg", 12.9720, 77.5950, size = 15_000_000L),
            PhotoGpsItem(3L, dummyUri, "photo3.jpg", 12.9710, 77.5940, size = 20_000_000L),
            PhotoGpsItem(4L, dummyUri, "photo4.jpg", 48.8566, 2.3522, size = 30_000_000L), // Paris
        )

        // Zoomed out to world view
        val clusters = GpsClusteringEngine.clusterPhotos(
            items = items,
            centerLat = 20.0,
            centerLng = 50.0,
            zoom = 2.5f,
            screenWidth = 1080f,
            screenHeight = 2400f,
            clusterRadiusPx = 72f,
        )

        // The 3 Bengaluru photos should merge into 1 cluster, and Paris remains its own cluster
        assertEquals(2, clusters.size)

        val bengaluruCluster = clusters.firstOrNull { it.items.any { item -> item.mediaId == 1L } }
        assertNotNull(bengaluruCluster)
        assertEquals(3, bengaluruCluster!!.itemCount)
        assertEquals(45_000_000L, bengaluruCluster.totalBytes)
        // 65% estimated savings
        assertEquals((45_000_000L * 0.65).toLong(), bengaluruCluster.estimatedVaultSavingsBytes)
    }

    @Test
    fun testEmptyItemsClusteringReturnsEmpty() {
        val clusters = GpsClusteringEngine.clusterPhotos(
            items = emptyList(),
            centerLat = 0.0,
            centerLng = 0.0,
            zoom = 4.0f,
            screenWidth = 1080f,
            screenHeight = 1920f,
        )
        assertTrue(clusters.isEmpty())
    }
}
