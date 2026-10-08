package com.pandagallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing Samsung Edge Panel quick shortcuts and actions.
 */
@Entity(tableName = "edge_panel_actions")
data class EdgePanelActionEntity(
    @PrimaryKey
    val actionId: String,
    val label: String,
    val iconResName: String,
    val targetRoute: String,
    val sortOrder: Int = 0,
    val isEnabled: Boolean = true
)
