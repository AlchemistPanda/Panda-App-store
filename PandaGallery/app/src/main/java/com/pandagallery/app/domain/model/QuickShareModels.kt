package com.pandagallery.app.domain.model

import android.net.Uri

/**
 * Types of nearby devices discovered by Samsung Quick Share.
 */
enum class DeviceType(val displayName: String) {
    PHONE("Phone"),
    TABLET("Tablet"),
    LAPTOP("Laptop"),
    TV("Smart TV"),
}

/**
 * Status of a local P2P Quick Share transfer.
 */
enum class QuickShareTransferStatus {
    IDLE,
    CONNECTING,
    TRANSFERRING,
    COMPLETED,
    FAILED,
}

/**
 * A nearby device discovered over Bluetooth LE & Wi-Fi Direct.
 */
data class NearbyDevice(
    val id: String,
    val name: String,
    val deviceType: DeviceType,
    val signalPercent: Int = 85,
    val isGalaxyEcosystem: Boolean = true,
    val avatarColor: Long = 0xFF2C6CF5,
)

/**
 * Quick Share transfer progress session.
 */
data class QuickShareSession(
    val targetDevice: NearbyDevice,
    val status: QuickShareTransferStatus = QuickShareTransferStatus.IDLE,
    val progress: Float = 0f, // 0.0f..1.0f
    val transferredBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
)

/**
 * Member roles in a Samsung Shared Album.
 */
enum class SharedAlbumRole(val displayName: String) {
    OWNER("Owner"),
    CONTRIBUTOR("Contributor"),
    VIEWER("Viewer"),
}

/**
 * Member in a collaborative Shared Album.
 */
data class SharedAlbumMember(
    val id: String,
    val name: String,
    val role: SharedAlbumRole = SharedAlbumRole.CONTRIBUTOR,
    val avatarColor: Long = 0xFF5E5CE6,
    val isCurrentUser: Boolean = false,
)

/**
 * Interactive emoji reaction on a photo in a Shared Album.
 */
data class SharedPhotoReaction(
    val emoji: String,
    val count: Int,
    val userReacted: Boolean = false,
)

/**
 * User comment or note in a collaborative Shared Album.
 */
data class SharedAlbumComment(
    val id: String,
    val author: String,
    val avatarColor: Long = 0xFF2C6CF5,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Collaborative Shared Album modeled after Samsung One UI Shared Albums.
 */
data class SharedAlbum(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUri: Uri?,
    val members: List<SharedAlbumMember>,
    val items: List<MediaItem>,
    val inviteCode: String = "SAMSUNG-SHARE-8821",
    val isAutoSync: Boolean = true,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val comments: List<SharedAlbumComment> = emptyList(),
) {
    val memberCount: Int get() = members.size
    val itemCount: Int get() = items.size
}
