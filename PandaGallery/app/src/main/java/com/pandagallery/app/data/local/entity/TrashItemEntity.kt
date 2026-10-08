package com.pandagallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for enhanced 30-day Trash Bin management with auto-purge metadata.
 */
@Entity(
    tableName = "trash_items_v2",
    indices = [Index(value = ["expiryDate"]), Index(value = ["trashedDate"])]
)
data class TrashItemEntity(
    @PrimaryKey
    val mediaId: Long,
    val originalUri: String,
    val originalPath: String?,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val trashedDate: Long = System.currentTimeMillis(),
    val expiryDate: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L), // 30 days
    val isPurged: Boolean = false
)
