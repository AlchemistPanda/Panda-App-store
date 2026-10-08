package com.pandagallery.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.pandagallery.app.data.local.entity.AlbumEntity
import com.pandagallery.app.data.local.entity.MediaEntity
import com.pandagallery.app.data.local.entity.TrashEntity
import com.pandagallery.app.data.local.entity.PrivateMediaEntity
import com.pandagallery.app.data.local.entity.PrivateFolderEntity
import com.pandagallery.app.domain.model.SQL_EFFECTIVE_DATE_MILLIS
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // ============================================
    // Media Items
    // ============================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaEntity)

    @Update
    suspend fun update(item: MediaEntity)

    @Delete
    suspend fun delete(item: MediaEntity)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT id FROM media_items")
    suspend fun getAllIds(): List<Long>

    /**
     * Reconciles the cache with what MediaStore reported, without wiping and rebuilding the
     * whole table on every sync — most calls here are a content-observer nudge for one changed
     * file, and a full library `deleteAll()` + `insertAll()` on every one of those was rewriting
     * the entire cache (and, at 100k+ rows, risking a very large single transaction) for what is
     * usually a one-row change. Only ids no longer present get deleted; everything else is a
     * REPLACE upsert via [insertAll]. Deletes are chunked — [ids] can be small, but the stale-id
     * list computed from the *existing* table has no such guarantee, and SQLite caps how many
     * bound parameters a single statement can take.
     */
    @Transaction
    suspend fun replaceAll(items: List<MediaEntity>) {
        val existingSnapshot = getSyncSnapshots().associateBy { it.id }
        val incomingIds = items.mapTo(mutableSetOf()) { it.id }
        val staleIds = existingSnapshot.keys.filterNot { it in incomingIds }
        if (staleIds.isNotEmpty()) {
            staleIds.chunked(900).forEach { chunk -> deleteByIds(chunk) }
        }
        val itemsToUpsert = items.filter { item ->
            val existing = existingSnapshot[item.id] ?: return@filter true
            existing.dateModified != item.dateModified ||
                existing.size != item.size ||
                existing.isFavorite != item.isFavorite ||
                existing.isTrashed != item.isTrashed ||
                existing.isCompressed != item.isCompressed ||
                existing.originalSize != item.originalSize ||
                // A move or rename changes none of the fields above (MediaProvider keeps the
                // file's dates), so without these the cache kept showing moved items in their
                // old album indefinitely.
                existing.bucketId != item.bucketId ||
                existing.relativePath != item.relativePath ||
                existing.displayName != item.displayName
        }
        if (itemsToUpsert.isNotEmpty()) {
            insertAll(itemsToUpsert)
        }
    }

    /**
     * App-local state that MediaStore knows nothing about. Read before a sync so a
     * refresh doesn't wipe trash bookkeeping or compression badges.
     */
    @Query("SELECT id, isTrashed, isCompressed, originalSize FROM media_items")
    suspend fun getLocalMediaFlags(): List<LocalMediaFlags>

    @Query("SELECT id, dateModified, size, isFavorite, isTrashed, isCompressed, originalSize, bucketId, relativePath, displayName FROM media_items")
    suspend fun getSyncSnapshots(): List<MediaSyncSnapshot>

    @Query("SELECT * FROM media_items WHERE isTrashed = 0 ORDER BY $SQL_EFFECTIVE_DATE_MILLIS DESC, id DESC")
    fun getAllMediaPaged(): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media_items WHERE isTrashed = 0 ORDER BY $SQL_EFFECTIVE_DATE_MILLIS DESC, id DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaById(id: Long): MediaEntity?

    @Query("SELECT * FROM media_items WHERE bucketId = :bucketId AND isTrashed = 0 ORDER BY $SQL_EFFECTIVE_DATE_MILLIS DESC, id DESC")
    fun getMediaByBucket(bucketId: Long): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE bucketId = :bucketId AND isTrashed = 0 ORDER BY $SQL_EFFECTIVE_DATE_MILLIS DESC, id DESC")
    fun getMediaByBucketPaged(bucketId: Long): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY $SQL_EFFECTIVE_DATE_MILLIS DESC, id DESC")
    fun getFavorites(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY $SQL_EFFECTIVE_DATE_MILLIS DESC, id DESC")
    fun getFavoritesPaged(): PagingSource<Int, MediaEntity>

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE media_items SET isTrashed = :isTrashed WHERE id = :id")
    suspend fun setTrashed(id: Long, isTrashed: Boolean)

    @Query("UPDATE media_items SET isCompressed = 1, originalSize = :originalSize WHERE id = :id")
    suspend fun setCompressed(id: Long, originalSize: Long)

    @Query("SELECT * FROM media_items WHERE isTrashed = 1 ORDER BY dateModified DESC")
    fun getTrashedMedia(): Flow<List<MediaEntity>>

    @Query("SELECT COUNT(*) FROM media_items WHERE isTrashed = 0")
    fun getMediaCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE isFavorite = 1 AND isTrashed = 0")
    fun getFavoritesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE isTrashed = 1")
    fun getTrashedCount(): Flow<Int>

    @Query("""
        SELECT * FROM media_items 
        WHERE isTrashed = 0 
        AND (displayName LIKE '%' || :query || '%' OR bucketName LIKE '%' || :query || '%')
        ORDER BY $SQL_EFFECTIVE_DATE_MILLIS DESC, id DESC
    """)
    fun searchMedia(query: String): Flow<List<MediaEntity>>

    // ============================================
    // Albums — aggregated from MediaStore buckets
    // ============================================

    @Query("SELECT * FROM media_items WHERE bucketId = :bucketId AND isTrashed = 0 ORDER BY $SQL_EFFECTIVE_DATE_MILLIS DESC, id DESC LIMIT 1")
    suspend fun getFirstItemInBucket(bucketId: Long): MediaEntity?

    @Query("""
        SELECT bucketId, bucketName, COUNT(*) as count,
            MAX($SQL_EFFECTIVE_DATE_MILLIS) as lastDate
        FROM media_items 
        WHERE bucketId IS NOT NULL AND isTrashed = 0
        GROUP BY bucketId 
        ORDER BY lastDate DESC
    """)
    fun getAlbumBuckets(): Flow<List<AlbumBucket>>

    // ============================================
    // Album Entities (user-managed)
    // ============================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Delete
    suspend fun deleteAlbum(album: AlbumEntity)

    @Query("SELECT * FROM albums ORDER BY lastModified DESC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE isHidden = 0 ORDER BY lastModified DESC")
    fun getVisibleAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbumById(id: Long): AlbumEntity?

    // ============================================
    // Trash
    // ============================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashEntity)

    @Delete
    suspend fun deleteTrashItem(item: TrashEntity)

    @Query("SELECT * FROM trash_items ORDER BY trashedDate DESC")
    fun getTrashItems(): Flow<List<TrashEntity>>

    @Query("DELETE FROM trash_items WHERE expiryDate < :currentTime")
    suspend fun deleteExpiredTrash(currentTime: Long)

    @Query("SELECT * FROM trash_items WHERE expiryDate <= :currentTime ORDER BY expiryDate")
    suspend fun getExpiredTrash(currentTime: Long): List<TrashEntity>

    @Query("SELECT * FROM trash_items WHERE mediaId = :mediaId")
    suspend fun getTrashItem(mediaId: Long): TrashEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivateMedia(items: List<PrivateMediaEntity>)

    @Query("SELECT * FROM private_media ORDER BY dateTaken DESC")
    fun getPrivateMedia(): Flow<List<PrivateMediaEntity>>

    /** One-shot list for maintenance passes such as re-encrypting the vault. */
    @Query("SELECT * FROM private_media")
    suspend fun getAllPrivateMedia(): List<PrivateMediaEntity>

    @Query("SELECT * FROM private_media WHERE id = :id")
    suspend fun getPrivateMediaById(id: String): PrivateMediaEntity?

    @Query("SELECT COUNT(*) FROM private_media")
    fun getPrivateMediaCount(): Flow<Int>

    @Query("SELECT * FROM private_media WHERE id IN (:ids)")
    suspend fun getPrivateMediaByIds(ids: List<String>): List<PrivateMediaEntity>

    @Query("DELETE FROM private_media WHERE id IN (:ids)")
    suspend fun deletePrivateMedia(ids: List<String>)

    @Query("UPDATE private_media SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun movePrivateMedia(ids: List<String>, folderId: String?)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPrivateFolder(folder: PrivateFolderEntity)

    @Query("SELECT * FROM private_folders ORDER BY name COLLATE NOCASE")
    fun getPrivateFolders(): Flow<List<PrivateFolderEntity>>

    @Query("SELECT COUNT(*) FROM private_folders WHERE name = :name COLLATE NOCASE")
    suspend fun getPrivateFolderNameCount(name: String): Int

    @Query("SELECT COUNT(*) FROM private_media WHERE folderId = :folderId")
    suspend fun getPrivateFolderItemCount(folderId: String): Int

    @Query("DELETE FROM private_folders WHERE id = :folderId")
    suspend fun deletePrivateFolder(folderId: String)
}

/**
 * Projection for album bucket aggregation query.
 */
data class AlbumBucket(
    val bucketId: Long?,
    val bucketName: String?,
    val count: Int,
    val lastDate: Long?,
)

/** Projection of the columns Room owns rather than MediaStore. */
data class LocalMediaFlags(
    val id: Long,
    val isTrashed: Boolean,
    val isCompressed: Boolean,
    val originalSize: Long?,
)

/** Projection for diffing sync items to avoid unnecessary Room invalidations. */
data class MediaSyncSnapshot(
    val id: Long,
    val dateModified: Long,
    val size: Long,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val isCompressed: Boolean,
    val originalSize: Long?,
    val bucketId: Long?,
    val relativePath: String?,
    val displayName: String,
)
