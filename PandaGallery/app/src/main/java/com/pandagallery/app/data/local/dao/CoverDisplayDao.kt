package com.pandagallery.app.data.local.dao

import androidx.room.*
import com.pandagallery.app.data.local.entity.CoverDisplayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoverDisplayDao {
    @Query("SELECT * FROM cover_display ORDER BY id DESC LIMIT 1")
    fun observeLatestCoverState(): Flow<CoverDisplayEntity?>

    @Query("SELECT * FROM cover_display ORDER BY id DESC LIMIT 1")
    suspend fun getLatestCoverState(): CoverDisplayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: CoverDisplayEntity): Long

    @Query("UPDATE cover_display SET isCoverActive = :isActive, lastSyncTimestamp = :timestamp WHERE id = :id")
    suspend fun updateCoverActive(id: Long, isActive: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM cover_display")
    suspend fun clear()
}
