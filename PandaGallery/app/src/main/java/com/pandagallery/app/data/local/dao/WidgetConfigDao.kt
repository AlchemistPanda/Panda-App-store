package com.pandagallery.app.data.local.dao

import androidx.room.*
import com.pandagallery.app.data.local.entity.WidgetConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_configs")
    fun observeAllWidgetConfigs(): Flow<List<WidgetConfigEntity>>

    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :appWidgetId LIMIT 1")
    suspend fun getConfigForWidget(appWidgetId: Int): WidgetConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: WidgetConfigEntity)

    @Query("DELETE FROM widget_configs WHERE appWidgetId = :appWidgetId")
    suspend fun deleteWidgetConfig(appWidgetId: Int)
}
