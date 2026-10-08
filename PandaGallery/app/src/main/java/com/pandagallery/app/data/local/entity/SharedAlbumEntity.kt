package com.pandagallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a shared album that can be accessed via a generated link or QR code.
 */
@Entity(tableName = "shared_album")
data class SharedAlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val albumId: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null, // nullable for never-expiring links
    val shareToken: String, // short token used in URLs / QR codes
    val memberCount: Int = 1,
    val isPublic: Boolean = false
)

/**
 * Metadata for links and QR codes created for shared albums.
 */
@Entity(tableName = "shared_links")
data class SharedLinkEntity(
    @PrimaryKey
    val shareToken: String,
    val albumId: Long,
    val shareUrl: String,
    val qrCodePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
)
