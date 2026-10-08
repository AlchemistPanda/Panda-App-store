package com.pandagallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for dual-display (cover screen) state on foldable devices.
 */
@Entity(tableName = "cover_display")
data class CoverDisplayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val isCoverActive: Boolean = false,
    val currentMediaId: Long? = null,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)
