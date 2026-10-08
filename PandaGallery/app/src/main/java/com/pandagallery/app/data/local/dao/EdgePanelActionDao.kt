package com.pandagallery.app.data.local.dao

import androidx.room.*
import com.pandagallery.app.data.local.entity.EdgePanelActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EdgePanelActionDao {
    @Query("SELECT * FROM edge_panel_actions WHERE isEnabled = 1 ORDER BY sortOrder ASC")
    fun observeEnabledActions(): Flow<List<EdgePanelActionEntity>>

    @Query("SELECT * FROM edge_panel_actions ORDER BY sortOrder ASC")
    suspend fun getAllActions(): List<EdgePanelActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(action: EdgePanelActionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(actions: List<EdgePanelActionEntity>)

    @Query("UPDATE edge_panel_actions SET isEnabled = :isEnabled WHERE actionId = :actionId")
    suspend fun updateEnabled(actionId: String, isEnabled: Boolean)

    @Query("DELETE FROM edge_panel_actions WHERE actionId = :actionId")
    suspend fun deleteAction(actionId: String)
}
