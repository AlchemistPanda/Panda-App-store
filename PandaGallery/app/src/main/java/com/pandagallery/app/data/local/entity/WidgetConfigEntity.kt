package com.pandagallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing configuration of home screen gallery widgets.
 */
@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey
    val appWidgetId: Int,
    val widgetType: String = "GRID_3X3", // GRID_3X3, SINGLE_PHOTO, STORY_ALBUM
    val targetAlbumId: Long? = null,
    val refreshIntervalMinutes: Int = 60,
    val showLabels: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)
