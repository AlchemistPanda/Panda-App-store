package com.pandagallery.app.data.local.dao

import androidx.room.*
import com.pandagallery.app.data.local.entity.TrashItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashItemDao {
    @Query("SELECT * FROM trash_items_v2 WHERE isPurged = 0 ORDER BY trashedDate DESC")
    fun observeAllTrashItems(): Flow<List<TrashItemEntity>>

    @Query("SELECT * FROM trash_items_v2 WHERE isPurged = 0 ORDER BY trashedDate DESC")
    suspend fun getAllTrashItems(): List<TrashItemEntity>

    @Query("SELECT * FROM trash_items_v2 WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getTrashItem(mediaId: Long): TrashItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TrashItemEntity>)

    @Query("SELECT * FROM trash_items_v2 WHERE expiryDate <= :currentTime AND isPurged = 0")
    suspend fun getExpiredItems(currentTime: Long = System.currentTimeMillis()): List<TrashItemEntity>

    @Query("UPDATE trash_items_v2 SET isPurged = 1 WHERE mediaId IN (:mediaIds)")
    suspend fun markPurged(mediaIds: List<Long>)

    @Query("DELETE FROM trash_items_v2 WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: Long)

    @Query("DELETE FROM trash_items_v2 WHERE isPurged = 1")
    suspend fun clearPurgedItems()
}
