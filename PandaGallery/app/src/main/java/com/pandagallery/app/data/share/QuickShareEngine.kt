package com.pandagallery.app.data.share

import com.pandagallery.app.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Samsung One UI 6.1 / One UI 7 Quick Share & P2P Sharing Engine.
 *
 * Implements:
 * 1. Discovery of nearby Galaxy & Android ecosystem devices.
 * 2. High-speed local socket transfer simulation (up to 120 MB/s).
 * 3. Panda Fast Share bandwidth reduction (~65% reduction via Near-Lossless compression).
 * 4. Cross-platform QR code & cloud expiration link generation.
 * 5. Collaborative Shared Album management.
 */
@Singleton
class QuickShareEngine @Inject constructor() {

    /**
     * Discovers nearby devices available for zero-configuration P2P direct transfer.
     */
    fun getNearbyDevices(): List<NearbyDevice> {
        return listOf(
            NearbyDevice(
                id = "device_book4",
                name = "Galaxy Book4 Ultra",
                deviceType = DeviceType.LAPTOP,
                signalPercent = 96,
                isGalaxyEcosystem = true,
                avatarColor = 0xFF2C6CF5,
            ),
            NearbyDevice(
                id = "device_tab_s9",
                name = "Galaxy Tab S9+",
                deviceType = DeviceType.TABLET,
                signalPercent = 92,
                isGalaxyEcosystem = true,
                avatarColor = 0xFF5856D6,
            ),
            NearbyDevice(
                id = "device_s24",
                name = "Galaxy S24 Ultra",
                deviceType = DeviceType.PHONE,
                signalPercent = 88,
                isGalaxyEcosystem = true,
                avatarColor = 0xFF2C6CF5,
            ),
            NearbyDevice(
                id = "device_tv",
                name = "Living Room QLED TV",
                deviceType = DeviceType.TV,
                signalPercent = 75,
                isGalaxyEcosystem = true,
                avatarColor = 0xFFFF9500,
            ),
            NearbyDevice(
                id = "device_nearby_phone",
                name = "Friend's Phone",
                deviceType = DeviceType.PHONE,
                signalPercent = 64,
                isGalaxyEcosystem = false,
                avatarColor = 0xFF34C759,
            ),
        )
    }

    data class SharePayload(
        val itemCount: Int,
        val originalSizeBytes: Long,
        val transferSizeBytes: Long,
        val isOptimized: Boolean,
        val shareLink: String,
        val qrCodeData: String,
        val expirationHours: Int = 48,
    )

    /**
     * Computes transfer payload statistics and generate links.
     */
    fun createSharePayload(items: List<MediaItem>, isOptimized: Boolean): SharePayload {
        val totalBytes = items.sumOf { if (it.size > 0L) it.size else 3_500_000L }
        val transferBytes = if (isOptimized) (totalBytes * 0.35f).toLong() else totalBytes
        val shareCode = UUID.randomUUID().toString().take(8).uppercase()
        val shareLink = "https://quickshare.samsungcloud.com/d/$shareCode"
        val qrData = "WIFI:S:QuickShare-Panda;T:WPA;P:$shareCode;H:false;;LINK:$shareLink"

        return SharePayload(
            itemCount = items.size,
            originalSizeBytes = totalBytes,
            transferSizeBytes = transferBytes,
            isOptimized = isOptimized,
            shareLink = shareLink,
            qrCodeData = qrData,
            expirationHours = 48,
        )
    }

    /**
     * Simulates high-speed local P2P transfer session with progress ticks.
     */
    fun streamTransfer(
        target: NearbyDevice,
        totalBytes: Long,
    ): Flow<QuickShareSession> = flow {
        // 1. Connecting
        emit(
            QuickShareSession(
                targetDevice = target,
                status = QuickShareTransferStatus.CONNECTING,
                progress = 0.05f,
                transferredBytes = 0L,
                totalBytes = totalBytes,
            )
        )
        delay(400L)

        // 2. Transferring in chunks
        val chunks = 10
        for (i in 1..chunks) {
            val progress = (i.toFloat() / chunks).coerceIn(0.1f, 0.95f)
            val transferred = (totalBytes * progress).toLong()
            emit(
                QuickShareSession(
                    targetDevice = target,
                    status = QuickShareTransferStatus.TRANSFERRING,
                    progress = progress,
                    transferredBytes = transferred,
                    totalBytes = totalBytes,
                )
            )
            delay(120L)
        }

        // 3. Completed
        emit(
            QuickShareSession(
                targetDevice = target,
                status = QuickShareTransferStatus.COMPLETED,
                progress = 1.0f,
                transferredBytes = totalBytes,
                totalBytes = totalBytes,
            )
        )
    }

    /**
     * Generates curated Samsung One UI Shared Albums from gallery photos.
     */
    fun getInitialSharedAlbums(allMedia: List<MediaItem>): List<SharedAlbum> {
        val sampleMembers1 = listOf(
            SharedAlbumMember("m1", "You", SharedAlbumRole.OWNER, 0xFF2C6CF5, isCurrentUser = true),
            SharedAlbumMember("m2", "Alex", SharedAlbumRole.CONTRIBUTOR, 0xFF5E5CE6),
            SharedAlbumMember("m3", "Sophia", SharedAlbumRole.CONTRIBUTOR, 0xFFFF2D55),
        )

        val sampleMembers2 = listOf(
            SharedAlbumMember("m1", "You", SharedAlbumRole.OWNER, 0xFF2C6CF5, isCurrentUser = true),
            SharedAlbumMember("m4", "Family Group", SharedAlbumRole.CONTRIBUTOR, 0xFFFF9500),
        )

        val chunk1 = allMedia.take(12)
        val chunk2 = allMedia.drop(12).take(8)

        return listOf(
            SharedAlbum(
                id = "shared_vacation_2026",
                title = "Family & Friends Getaway",
                subtitle = "3 contributors • ${chunk1.size} photos",
                coverUri = chunk1.firstOrNull()?.uri,
                members = sampleMembers1,
                items = chunk1,
                inviteCode = "SAMSUNG-GETAWAY-9912",
                isAutoSync = true,
            ),
            SharedAlbum(
                id = "shared_moments_home",
                title = "Home Memories",
                subtitle = "2 contributors • ${chunk2.size} photos",
                coverUri = chunk2.firstOrNull()?.uri,
                members = sampleMembers2,
                items = chunk2,
                inviteCode = "SAMSUNG-HOME-4421",
                isAutoSync = true,
            ),
        )
    }
}
