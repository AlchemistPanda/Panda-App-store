package com.pandagallery.app.data.local.dao

import androidx.room.*
import com.pandagallery.app.data.local.entity.SecureFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecureFolderDao {
    @Query("SELECT * FROM secure_folder_items ORDER BY addedAt DESC")
    fun observeAllSecureItems(): Flow<List<SecureFolderEntity>>

    @Query("SELECT * FROM secure_folder_items WHERE folderId = :folderId ORDER BY addedAt DESC")
    fun observeItemsByFolder(folderId: String): Flow<List<SecureFolderEntity>>

    @Query("SELECT * FROM secure_folder_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): SecureFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SecureFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SecureFolderEntity>)

    @Delete
    suspend fun deleteItem(item: SecureFolderEntity)

    @Query("DELETE FROM secure_folder_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
