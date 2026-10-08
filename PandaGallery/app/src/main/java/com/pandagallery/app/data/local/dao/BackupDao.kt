package com.pandagallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pandagallery.app.data.local.entity.BackupRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BackupRecordEntity)

    @Query("SELECT * FROM backup_records WHERE destinationRootUri = :rootUri AND mediaId = :mediaId")
    suspend fun get(rootUri: String, mediaId: Long): BackupRecordEntity?

    @Query("SELECT * FROM backup_records WHERE destinationRootUri = :rootUri ORDER BY backedUpAt DESC")
    suspend fun getAll(rootUri: String): List<BackupRecordEntity>

    @Query("SELECT COUNT(*) FROM backup_records WHERE destinationRootUri = :rootUri")
    fun observeCount(rootUri: String): Flow<Int>
}
