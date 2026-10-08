package com.pandagallery.app.data.local.dao

import androidx.room.*
import com.pandagallery.app.data.local.entity.SharedAlbumEntity
import com.pandagallery.app.data.local.entity.SharedLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedAlbumDao {
    @Query("SELECT * FROM shared_album ORDER BY createdAt DESC")
    fun observeAllSharedAlbums(): Flow<List<SharedAlbumEntity>>

    @Query("SELECT * FROM shared_album WHERE id = :id")
    suspend fun getSharedAlbumById(id: Long): SharedAlbumEntity?

    @Query("SELECT * FROM shared_album WHERE albumId = :albumId LIMIT 1")
    suspend fun getSharedAlbumByAlbumId(albumId: Long): SharedAlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSharedAlbum(album: SharedAlbumEntity): Long

    @Delete
    suspend fun deleteSharedAlbum(album: SharedAlbumEntity)

    @Query("SELECT * FROM shared_links WHERE albumId = :albumId LIMIT 1")
    suspend fun getLinkForAlbum(albumId: Long): SharedLinkEntity?

    @Query("SELECT * FROM shared_links WHERE shareToken = :token LIMIT 1")
    suspend fun getLinkByToken(token: String): SharedLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSharedLink(link: SharedLinkEntity)

    @Query("DELETE FROM shared_links WHERE shareToken = :token")
    suspend fun deleteSharedLink(token: String)
}
