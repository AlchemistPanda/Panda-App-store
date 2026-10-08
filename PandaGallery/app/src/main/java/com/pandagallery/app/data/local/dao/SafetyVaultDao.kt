package com.pandagallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pandagallery.app.data.local.entity.SafetyVaultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyVaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SafetyVaultEntity)

    // Newest first: one source can have several backups (compressed twice, or stripped and then
    // compressed), and an unordered LIMIT 1 returned an arbitrary one.
    @Query("SELECT * FROM safety_vault_items WHERE compressedMediaId = :compressedMediaId ORDER BY backedUpAt DESC LIMIT 1")
    suspend fun getByCompressedMediaId(compressedMediaId: Long): SafetyVaultEntity?

    @Query("SELECT * FROM safety_vault_items WHERE compressedMediaId = :compressedMediaId ORDER BY backedUpAt DESC LIMIT 1")
    fun observeByCompressedMediaId(compressedMediaId: Long): Flow<SafetyVaultEntity?>

    @Query("SELECT * FROM safety_vault_items WHERE sourceMediaId = :sourceMediaId ORDER BY backedUpAt DESC LIMIT 1")
    suspend fun getBySourceMediaId(sourceMediaId: Long): SafetyVaultEntity?

    /**
     * Links exactly one backup — the row [backupOriginal][com.pandagallery.app.data.compression.SafetyVaultRepository.backupOriginal]
     * returned — to its output. Keying on sourceMediaId re-pointed every earlier backup of the
     * same source at the newest output, so the earlier copies could no longer be reverted.
     */
    @Query("UPDATE safety_vault_items SET compressedMediaId = :compressedMediaId WHERE id = :id")
    suspend fun linkCompressedMediaById(id: String, compressedMediaId: Long)

    /** Links only the newest backup of [sourceMediaId]; for callers that don't hold the row id. */
    @Query(
        "UPDATE safety_vault_items SET compressedMediaId = :compressedMediaId WHERE id = " +
            "(SELECT id FROM safety_vault_items WHERE sourceMediaId = :sourceMediaId ORDER BY backedUpAt DESC LIMIT 1)"
    )
    suspend fun linkNewestBackup(sourceMediaId: Long, compressedMediaId: Long)

    @Query("DELETE FROM safety_vault_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM safety_vault_items WHERE compressedMediaId = :compressedMediaId")
    suspend fun deleteByCompressedMediaId(compressedMediaId: Long)

    @Query("SELECT * FROM safety_vault_items WHERE expiresAt < :now")
    suspend fun getExpired(now: Long = System.currentTimeMillis()): List<SafetyVaultEntity>

    @Query("SELECT * FROM safety_vault_items")
    suspend fun getAll(): List<SafetyVaultEntity>

    @Query("SELECT localBackupPath FROM safety_vault_items")
    suspend fun getAllBackupPaths(): List<String>

    @Query("DELETE FROM safety_vault_items")
    suspend fun deleteAll()

    /**
     * Re-dates every backup against a retention window the user has just changed. Without this the
     * policy would only ever apply to future compressions, leaving copies already on disk pinned to
     * whatever window was in force when they were made.
     */
    @Query("UPDATE safety_vault_items SET expiresAt = backedUpAt + :retentionMillis")
    suspend fun reapplyRetention(retentionMillis: Long)

    @Query("UPDATE safety_vault_items SET expiresAt = :expiresAt")
    suspend fun setExpiryForAll(expiresAt: Long)

    @Query("SELECT COUNT(*) FROM safety_vault_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(originalBytes), 0) FROM safety_vault_items")
    fun observeTotalBytes(): Flow<Long>
}
