package com.pandagallery.app.data.share

import android.net.TestUri
import com.pandagallery.app.domain.model.DeviceType
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.QuickShareTransferStatus
import com.pandagallery.app.domain.model.SharedAlbumRole
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickShareEngineTest {

    private val engine = QuickShareEngine()

    private fun createMockMediaItem(
        id: Long,
        sizeBytes: Long = 4_000_000L,
    ): MediaItem {
        return MediaItem(
            id = id,
            uri = TestUri(),
            displayName = "PHOTO_$id.jpg",
            mimeType = "image/jpeg",
            size = sizeBytes,
            width = 4000,
            height = 3000,
            dateAdded = System.currentTimeMillis() / 1000,
            dateModified = System.currentTimeMillis() / 1000,
            dateTaken = System.currentTimeMillis(),
        )
    }

    @Test
    fun `getNearbyDevices returns discovered Galaxy and Android ecosystem devices`() {
        val devices = engine.getNearbyDevices()

        assertTrue("Should discover multiple nearby devices", devices.size >= 4)

        val laptop = devices.firstOrNull { it.deviceType == DeviceType.LAPTOP }
        assertTrue("Laptop device should exist", laptop != null)
        assertEquals("Galaxy Book4 Ultra", laptop?.name)
        assertTrue(laptop?.isGalaxyEcosystem == true)

        val tablet = devices.firstOrNull { it.deviceType == DeviceType.TABLET }
        assertTrue("Tablet device should exist", tablet != null)
        assertEquals("Galaxy Tab S9+", tablet?.name)

        val tv = devices.firstOrNull { it.deviceType == DeviceType.TV }
        assertTrue("TV device should exist", tv != null)

        devices.forEach {
            assertTrue("Signal percent should be 1..100", it.signalPercent in 1..100)
        }
    }

    @Test
    fun `createSharePayload generates cloud link, QR data, and computes Panda Fast Share compression`() {
        val items = listOf(
            createMockMediaItem(1, 10_000_000L),
            createMockMediaItem(2, 6_000_000L),
        )

        // 1. Uncompressed standard transfer
        val payloadStandard = engine.createSharePayload(items, isOptimized = false)
        assertEquals(2, payloadStandard.itemCount)
        assertEquals(16_000_000L, payloadStandard.originalSizeBytes)
        assertEquals(16_000_000L, payloadStandard.transferSizeBytes)
        assertTrue(payloadStandard.shareLink.startsWith("https://quickshare.samsungcloud.com/d/"))
        assertTrue(payloadStandard.qrCodeData.contains("QuickShare-Panda"))
        assertEquals(48, payloadStandard.expirationHours)

        // 2. Panda Fast Share (-65% size payload)
        val payloadFastShare = engine.createSharePayload(items, isOptimized = true)
        assertEquals(16_000_000L, payloadFastShare.originalSizeBytes)
        // 16MB * 0.35 = 5.6MB
        assertEquals(5_600_000L, payloadFastShare.transferSizeBytes)
        assertTrue(payloadFastShare.isOptimized)
    }

    @Test
    fun `streamTransfer simulates complete P2P transfer session from connecting to completion`() = runBlocking {
        val target = engine.getNearbyDevices().first()
        val totalBytes = 20_000_000L

        val events = engine.streamTransfer(target, totalBytes).toList()

        assertTrue("Transfer flow should emit multiple progress ticks", events.size >= 5)

        val first = events.first()
        assertEquals(QuickShareTransferStatus.CONNECTING, first.status)
        assertEquals(target.id, first.targetDevice.id)

        val middle = events[events.size / 2]
        assertEquals(QuickShareTransferStatus.TRANSFERRING, middle.status)
        assertTrue("Progress should be advancing", middle.progress in 0.1f..0.99f)
        assertTrue("Transferred bytes should match progress", middle.transferredBytes > 0L)

        val last = events.last()
        assertEquals(QuickShareTransferStatus.COMPLETED, last.status)
        assertEquals(1.0f, last.progress, 0.001f)
        assertEquals(totalBytes, last.transferredBytes)
        assertEquals(totalBytes, last.totalBytes)
    }

    @Test
    fun `getInitialSharedAlbums creates collaborative albums with members and covers`() {
        val mockMedia = (1..25).map { createMockMediaItem(it.toLong()) }
        val sharedAlbums = engine.getInitialSharedAlbums(mockMedia)

        assertTrue("Should create at least 2 shared albums", sharedAlbums.size >= 2)

        val vacationAlbum = sharedAlbums.firstOrNull { it.id == "shared_vacation_2026" }
        assertTrue("Vacation album should exist", vacationAlbum != null)
        assertEquals("Family & Friends Getaway", vacationAlbum?.title)
        assertTrue(vacationAlbum?.coverUri != null)
        assertEquals(12, vacationAlbum?.items?.size)
        assertTrue(vacationAlbum?.members?.size == 3)

        val owner = vacationAlbum?.members?.firstOrNull { it.role == SharedAlbumRole.OWNER }
        assertTrue("Owner should be 'You'", owner != null && owner.isCurrentUser)
        assertTrue(vacationAlbum?.inviteCode?.startsWith("SAMSUNG-") == true)
        assertTrue(vacationAlbum?.isAutoSync == true)
    }
}
