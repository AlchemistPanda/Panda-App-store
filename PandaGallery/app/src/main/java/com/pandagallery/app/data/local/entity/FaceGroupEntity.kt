package com.pandagallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a clustered person group in the People album.
 */
@Entity(
    tableName = "face_groups",
    indices = [Index(value = ["personKey"], unique = true)]
)
data class FaceGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val personKey: String,
    val name: String? = null,
    val coverMediaId: Long? = null,
    val faceCount: Int = 0,
    val clusterId: Int = -1,
    val isPinned: Boolean = false,
    val isDismissed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
