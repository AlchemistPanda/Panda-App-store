package com.pandagallery.app.data.repository

import com.pandagallery.app.data.diagnostics.CrashLog
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.local.dao.MediaDao
import com.pandagallery.app.data.local.entity.AlbumEntity
import com.pandagallery.app.data.local.entity.MediaEntity
import com.pandagallery.app.data.local.entity.TrashEntity
import com.pandagallery.app.data.media.newAlbumRelativePath
import com.pandagallery.app.data.metadata.MediaMetadataRepository.Companion.EXIF_TAGS_TO_PRESERVE
import com.pandagallery.app.data.metadata.MediaMetadataRepository.Companion.GPS_TAGS
import com.pandagallery.app.data.source.MediaStoreDataSource
import com.pandagallery.app.domain.model.Album
import com.pandagallery.app.domain.model.resolveAlbumCover
import com.pandagallery.app.domain.model.effectiveDateMillis
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preserved original timestamps and updated physical dimensions for media items
 * that were rotated in place, preventing them from jumping to "Today" or being marked
 * as latest modified/created.
 */
data class PreservedMediaDates(
    val dateAdded: Long,
    val dateModified: Long,
    val dateTaken: Long?,
    val width: Int,
    val height: Int,
    val size: Long,
)

/**
 * Unified repository managing media library, caching, and state.
 * Syncs MediaStore data into Room database on initial launch.
 * Subsequent reads come from Room for instant display.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val mediaDao: MediaDao,
    private val preferencesDataSource: PreferencesDataSource,
) {
    /**
     * Cache of rotated media items' original dates and updated geometry, protecting
     * them against MediaStore's scanner setting dateModified/dateTaken to the current time.
     */
    private val preservedRotatedDates = ConcurrentHashMap<Long, PreservedMediaDates>()

    private val sharingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * One live query of the whole library, shared by every screen that observes it.
     *
     * Each collector of a Room flow runs its own full-table query on every invalidation and
     * holds its own copy of the result. With ten-odd screens observing the library, a large
     * library, and a compression batch invalidating the table once per published file, those
     * copies filled the 256 MB heap and the app died with an OutOfMemoryError mid-batch.
     * `replayExpirationMillis = 0` drops the cached list once nobody observes it, so a later
     * subscriber never starts from a stale library.
     */
    private val sharedMediaEntities: Flow<List<MediaEntity>> = mediaDao.getAllMedia()
        .shareIn(sharingScope, SharingStarted.WhileSubscribed(5_000, 0), replay = 1)

    private val sharedMedia: Flow<List<MediaItem>> = sharedMediaEntities
        .map { entities -> entities.map { it.toDomain() } }
        .distinctUntilChanged()
        .shareIn(sharingScope, SharingStarted.WhileSubscribed(5_000, 0), replay = 1)

    /**
     * Several observers react to the same MediaStore notification. Each full sync reads the
     * whole library, so they run one at a time, and a caller skips its sync when one that
     * started after its request has already finished — that one saw every change it would have.
     */
    private val syncMutex = Mutex()
    @Volatile private var lastSyncStartedAt = 0L

    /**
     * Sync MediaStore data into Room cache.
     * Call on app start and when ContentObserver detects changes.
     */
    suspend fun syncMediaStore() {
        val requestedAt = System.nanoTime()
        syncMutex.withLock {
            if (lastSyncStartedAt > requestedAt) return
            lastSyncStartedAt = System.nanoTime()
            syncMediaStoreLocked()
        }
    }

    private suspend fun syncMediaStoreLocked() = withContext(Dispatchers.IO) {
        val allMedia = mediaStoreDataSource.loadAllMedia()
        // MediaStore doesn't know about our trash bookkeeping or compression history,
        // so carry those columns across instead of letting the rewrite drop them.
        val localFlags = mediaDao.getLocalMediaFlags().associateBy { it.id }
        val entities = allMedia.map { item ->
            val entity = item.toEntity()
            val preserved = preservedRotatedDates[item.id]
            val entityWithPreservedDates = if (preserved != null) {
                entity.copy(
                    dateAdded = preserved.dateAdded,
                    dateModified = preserved.dateModified,
                    dateTaken = preserved.dateTaken,
                    width = preserved.width,
                    height = preserved.height,
                    size = preserved.size,
                )
            } else entity
            val local = localFlags[item.id] ?: return@map entityWithPreservedDates
            // MediaStore is the authority for isTrashed — the PendingIntent flow already wrote
            // the change there, so Room must mirror it rather than OR-ing in a stale local flag.
            // isCompressed is reset when the file's current size equals its pre-compression
            // originalSize, meaning the compressed copy was deleted and this is the original.
            entityWithPreservedDates.copy(
                isTrashed = entityWithPreservedDates.isTrashed,
                isCompressed = local.isCompressed && local.originalSize?.let { entityWithPreservedDates.size != it } != true,
                originalSize = entityWithPreservedDates.originalSize ?: local.originalSize,
            )
        }
        val existingSnapshots = mediaDao.getSyncSnapshots().associateBy { it.id }
        val modifiedItems = allMedia.filter { item ->
            val existing = existingSnapshots[item.id] ?: return@filter false
            existing.dateModified != item.dateModified || existing.size != item.size
        }
        if (modifiedItems.isNotEmpty()) {
            runCatching {
                val imageLoader = coil3.SingletonImageLoader.get(context)
                modifiedItems.forEach { item ->
                    imageLoader.diskCache?.remove("${item.uri}_thumb")
                    imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key("${item.uri}_thumb"))
                }
            }
        }
        mediaDao.replaceAll(entities)
    }

    /** Emits whenever images or videos change on the device, so screens can re-sync. */
    fun mediaStoreChanges(): Flow<Unit> = mediaStoreDataSource.observeChanges()

    /**
     * Get all media as a reactive Flow from Room cache.
     */
    fun getAllMedia(): Flow<List<MediaItem>> = sharedMedia

    /**
     * A fresh read of the library for one-shot use. Prefer this to `getAllMedia().first()`:
     * the shared flow can lag a write by a moment, so right after a sync it may still replay
     * the previous list.
     */
    suspend fun getAllMediaSnapshot(): List<MediaItem> =
        mediaDao.getAllMedia().first().map { it.toDomain() }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun observeCurrentMedia(): Flow<List<MediaItem>> =
        mediaStoreDataSource.observeChanges()
            // A published file notifies several times (insert, then pending cleared, then
            // metadata); one sync per burst is enough.
            .debounce(750L)
            .mapLatest { runCatching { syncMediaStore() } }
            .flatMapLatest { getAllMedia() }

    /**
     * Get PagingSource for infinite scroll.
     */
    fun getAllMediaPaged() = mediaDao.getAllMediaPaged()

    /**
     * Get media for a specific album/bucket.
     */
    fun getMediaByBucket(bucketId: Long): Flow<List<MediaItem>> {
        return mediaDao.getMediaByBucket(bucketId).map { entities ->
            entities.map { it.toDomain() }
        }.distinctUntilChanged()
    }

    fun getMediaByBucketPaged(bucketId: Long) = mediaDao.getMediaByBucketPaged(bucketId)

    /**
     * Get all albums (from bucket aggregation), with cover URI populated.
     */
    fun getAlbums(includeHidden: Boolean = false): Flow<List<Album>> {
        return combine(
            mediaDao.getAlbumBuckets(),
            mediaDao.getAllAlbums(),
            sharedMediaEntities,
            preferencesDataSource.userPreferencesFlow,
        ) { buckets, customAlbums, media, preferences ->
            // One pass over the cached media instead of a per-bucket cover query.
            val covers = media.groupBy { it.bucketId }
            val mediaStoreAlbums = buckets.mapNotNull { bucket ->
                if (bucket.bucketId == null || bucket.bucketName == null) return@mapNotNull null
                val bucketMedia = covers[bucket.bucketId].orEmpty()
                val newestItem = newestInAlbum(bucketMedia)
                val base = Album(
                    id = bucket.bucketId,
                    name = bucket.bucketName,
                    coverUri = newestItem?.uri?.let(Uri::parse),
                    mediaCount = bucket.count,
                    // Summed from the media already grouped above rather than with another
                    // query — the rows are in hand either way.
                    sizeBytes = bucketMedia.sumOf { it.size },
                    lastModified = bucket.lastDate ?: 0L,
                    relativePath = newestItem?.relativePath?.trimEnd('/'),
                    groupName = preferences.albumGroupOverrides[bucket.bucketId],
                )
                val metadata = customAlbums.firstOrNull { album ->
                    album.id == base.id || album.relativePath?.trimEnd('/') == base.relativePath
                }
                if (metadata == null) base else base.copy(
                    coverUri = resolveAlbumCover(
                        customCoverUri = metadata.coverUri,
                        availableUris = bucketMedia.mapTo(hashSetOf()) { it.uri },
                        automaticCoverUri = newestItem?.uri,
                    )?.let(Uri::parse),
                    customCoverUri = metadata.coverUri?.let(Uri::parse),
                    isHidden = metadata.isHidden,
                    isPinned = metadata.isPinned,
                    isLocked = metadata.isLocked,
                    groupName = preferences.albumGroupOverrides[base.id],
                )
            }
            val mediaStorePaths = mediaStoreAlbums.mapNotNull { it.relativePath ?: it.name }.toSet()
            val emptyCustomAlbums = customAlbums
                .filter { album -> album.relativePath !in mediaStorePaths }
                .map { album ->
                    Album(
                        id = album.id,
                        name = album.name,
                        coverUri = album.coverUri?.let(Uri::parse),
                        customCoverUri = album.coverUri?.let(Uri::parse),
                        mediaCount = 0,
                        lastModified = album.lastModified,
                        relativePath = album.relativePath,
                        isHidden = album.isHidden,
                        isPinned = album.isPinned,
                        isLocked = album.isLocked,
                        isPrivate = album.isPrivate,
                        isMemoriesVault = album.isMemoriesVault,
                        groupName = preferences.albumGroupOverrides[album.id],
                    )
                }
            (emptyCustomAlbums + mediaStoreAlbums)
                .filter { includeHidden || !it.isHidden }
                .sortedWith(compareByDescending<Album> { it.isPinned }.thenByDescending { it.lastModified })
        }.flowOn(Dispatchers.Default).distinctUntilChanged()
    }

    suspend fun updateAlbumGroup(albumId: Long, groupName: String?) =
        preferencesDataSource.updateAlbumGroup(albumId, groupName)

    suspend fun updateAlbumsGroup(albumIds: Set<Long>, groupName: String?) =
        preferencesDataSource.updateAlbumsGroup(albumIds, groupName)

    suspend fun renameAlbumGroup(oldName: String, newName: String) =
        preferencesDataSource.renameAlbumGroup(oldName, newName)

    suspend fun dissolveAlbumGroup(groupName: String) =
        preferencesDataSource.dissolveAlbumGroup(groupName)

    suspend fun setAlbumPinned(album: Album, pinned: Boolean) = withContext(Dispatchers.IO) {
        mediaDao.insertAlbum(album.toMetadataEntity(isPinned = pinned))
    }

    suspend fun setAlbumHidden(album: Album, hidden: Boolean) = withContext(Dispatchers.IO) {
        mediaDao.insertAlbum(album.toMetadataEntity(isHidden = hidden))
    }

    suspend fun batchSetAlbumHidden(albums: List<Album>, hidden: Boolean) = withContext(Dispatchers.IO) {
        albums.forEach { album ->
            mediaDao.insertAlbum(album.toMetadataEntity(isHidden = hidden))
        }
    }

    suspend fun mergeAlbums(
        sourceAlbums: List<Album>,
        targetAlbum: Album,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val destinationPath = targetAlbum.relativePath ?: newAlbumRelativePath(targetAlbum.name)
            val itemsToMove = mutableListOf<MediaItem>()
            for (source in sourceAlbums) {
                if (source.id != targetAlbum.id) {
                    val items = getMediaByBucket(source.id).first()
                    itemsToMove.addAll(items)
                }
            }
            if (itemsToMove.isEmpty()) return@runCatching 0
            val movedCount = mediaStoreDataSource.moveToAlbum(itemsToMove, destinationPath, onProgress).getOrThrow()
            for (source in sourceAlbums) {
                if (source.id != targetAlbum.id) {
                    mediaDao.getAlbumById(source.id)?.let { mediaDao.deleteAlbum(it) }
                }
            }
            syncMediaStore()
            movedCount
        }
    }

    suspend fun setAlbumLocked(album: Album, locked: Boolean) = withContext(Dispatchers.IO) {
        mediaDao.insertAlbum(album.toMetadataEntity(isLocked = locked))
    }

    /**
     * Unlocks every folder when no folder PIN exists.
     *
     * A locked flag with no PIN behind it is a dead end, not a lock: there is nothing to prove, so
     * the folder can be neither opened nor unlocked, and the wrong guesses it collects push the
     * user into a lockout on a PIN that does not exist. That state is reachable simply by removing
     * the folder PIN in Settings while a folder is still locked, so it is reconciled here rather
     * than trusted not to happen.
     */
    suspend fun clearFolderLocksWithoutPin() = withContext(Dispatchers.IO) {
        if (preferencesDataSource.folderLockPinHash() != null) return@withContext
        mediaDao.getAllAlbums().first()
            .filter { it.isLocked }
            .forEach { mediaDao.insertAlbum(it.copy(isLocked = false)) }
    }

    suspend fun setAlbumCover(album: Album, coverUri: Uri) = withContext(Dispatchers.IO) {
        mediaDao.insertAlbum(album.toMetadataEntity(coverUri = coverUri.toString()))
    }

    /** Returns the album to picking its own cover from its most recent item. */
    suspend fun clearAlbumCover(album: Album) = withContext(Dispatchers.IO) {
        mediaDao.insertAlbum(album.toMetadataEntity(coverUri = null))
    }

    suspend fun createAlbum(name: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaStorePath = newAlbumRelativePath(name)
            val relativePath = mediaStorePath.trimEnd('/')
            val cleanName = relativePath.substringAfterLast('/')
            mediaDao.insertAlbum(
                AlbumEntity(
                    id = stableAlbumId(relativePath),
                    name = cleanName,
                    coverUri = null,
                    relativePath = relativePath,
                    lastModified = System.currentTimeMillis(),
                )
            )
            mediaStorePath
        }
    }

    suspend fun copyToAlbum(
        items: List<MediaItem>,
        relativePath: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Int> =
        mediaStoreDataSource.copyToAlbum(items, relativePath, onProgress).also { result ->
            // The files are already in place; a failed refresh must not turn that into a crash.
            if (result.isSuccess) runCatching { syncMediaStore() }
                .onFailure { CrashLog.e("MediaRepository", "Sync after copyToAlbum failed", it) }
        }

    suspend fun moveToAlbum(
        items: List<MediaItem>,
        relativePath: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Int> =
        mediaStoreDataSource.moveToAlbum(items, relativePath, onProgress).also { result ->
            // The files are already in place; a failed refresh must not turn that into a crash.
            if (result.isSuccess) runCatching { syncMediaStore() }
                .onFailure { CrashLog.e("MediaRepository", "Sync after moveToAlbum failed", it) }
        }

    suspend fun renameMedia(item: MediaItem, requestedName: String): Result<String> =
        mediaStoreDataSource.renameMedia(item, requestedName).also { result ->
            if (result.isSuccess) syncMediaStore()
        }

    suspend fun renameAlbum(album: Album, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val destinationPath = newAlbumRelativePath(newName)
            val items = getMediaByBucket(album.id).first()
            if (items.isNotEmpty()) {
                mediaStoreDataSource.moveToAlbum(items, destinationPath).getOrThrow()
                mediaDao.getAlbumById(album.id)?.let { mediaDao.deleteAlbum(it) }
                syncMediaStore()
            } else {
                mediaDao.getAlbumById(album.id)?.let { old ->
                    mediaDao.deleteAlbum(old)
                    val storedPath = destinationPath.trimEnd('/')
                    mediaDao.insertAlbum(
                        old.copy(
                            id = stableAlbumId(storedPath),
                            name = storedPath.substringAfterLast('/'),
                            relativePath = storedPath,
                            lastModified = System.currentTimeMillis(),
                        ),
                    )
                } ?: error("Album no longer exists")
            }
        }
    }

    suspend fun deleteEmptyAlbum(albumId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val album = mediaDao.getAlbumById(albumId) ?: error("Only empty albums can be deleted directly")
            mediaDao.deleteAlbum(album)
        }
    }

    /**
     * Get favorites.
     */
    fun getFavorites(): Flow<List<MediaItem>> {
        return mediaDao.getFavorites().map { entities ->
            entities.map { it.toDomain() }
        }.distinctUntilChanged()
    }

    fun getFavoritesPaged() = mediaDao.getFavoritesPaged()

    /**
     * Toggle favorite status.
     */
    suspend fun toggleFavorite(item: MediaItem) {
        val newFavorite = !item.isFavorite
        mediaDao.setFavorite(item.id, newFavorite)
    }

    suspend fun setFavorite(items: List<MediaItem>, favorite: Boolean) {
        items.forEach { mediaDao.setFavorite(it.id, favorite) }
    }

    /**
     * Move item to trash.
     */
    suspend fun trashItem(item: MediaItem, retentionDays: Int = 30) {
        val now = System.currentTimeMillis()
        mediaDao.setTrashed(item.id, true)
        mediaDao.insertTrashItem(
            TrashEntity(
                mediaId = item.id,
                originalUri = item.uri.toString(),
                originalPath = item.relativePath,
                trashedDate = now,
                expiryDate = now + retentionDays.coerceIn(1, 90) * 24L * 60L * 60L * 1000L,
            )
        )
    }

    suspend fun markItemsTrashed(items: List<MediaItem>) {
        val retentionDays = preferencesDataSource.userPreferencesFlow.first().trashRetentionDays
        items.forEach { trashItem(it, retentionDays) }
    }

    /**
     * Restore item from trash.
     */
    suspend fun restoreFromTrash(item: MediaItem) {
        mediaDao.setTrashed(item.id, false)
        mediaDao.getTrashItem(item.id)?.let { mediaDao.deleteTrashItem(it) }
    }

    suspend fun markItemsRestored(items: List<MediaItem>) {
        items.forEach { restoreFromTrash(it) }
    }

    /**
     * Get trashed media.
     */
    fun getTrashedMedia(): Flow<List<MediaItem>> {
        return mediaDao.getTrashedMedia().map { entities ->
            entities.map { it.toDomain() }
        }.distinctUntilChanged()
    }

    fun getTrashExpiryDates(): Flow<Map<Long, Long>> = mediaDao.getTrashItems().map { items ->
        items.associate { it.mediaId to it.expiryDate }
    }

    suspend fun permanentlyDelete(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            mediaDao.getTrashItem(item.id)?.let { mediaDao.deleteTrashItem(it) }
            mediaDao.deleteById(item.id)
        }
    }

    /**
     * Search media by name or bucket name.
     */
    fun searchMedia(query: String): Flow<List<MediaItem>> {
        return mediaDao.searchMedia(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get a single media item by ID.
     * Falls back to MediaStore query if not in Room cache yet.
     */
    suspend fun getMediaById(id: Long): MediaItem? {
        // Try Room cache first (fast)
        val cached = mediaDao.getMediaById(id)?.toDomain()
        if (cached != null) return cached
        // Fall back to MediaStore scan (covers first-launch before sync)
        return mediaStoreDataSource.loadAllMedia().firstOrNull { it.id == id }
    }

    /**
     * Clean up expired trash items.
     */
    suspend fun cleanExpiredTrash() {
        mediaDao.deleteExpiredTrash(System.currentTimeMillis())
    }

    fun getMediaCount(): Flow<Int> = mediaDao.getMediaCount()
    fun getFavoritesCount(): Flow<Int> = mediaDao.getFavoritesCount()
    fun getTrashedCount(): Flow<Int> = mediaDao.getTrashedCount()

    suspend fun removeMedia(id: Long) = mediaDao.deleteById(id)

    suspend fun compressionPreferences(): UserPreferences =
        preferencesDataSource.userPreferencesFlow.first()

    /**
     * Rotates the given image items by [degrees] clockwise in place one by one sequentially.
     * Automatically filters to images only (omitting videos).
     * Preserves all EXIF metadata and capture timestamps.
     */
    suspend fun rotateImages(
        items: List<MediaItem>,
        degrees: Float = 90f,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val imageItems = items.filter { it.isImage && !it.isVideo }
            var rotatedCount = 0
            val total = imageItems.size

            val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getDefault()
            }

            for (item in imageItems) {
                val originalDateAdded = item.dateAdded
                val originalDateModified = item.dateModified
                val originalDateTaken = item.dateTaken?.takeIf { it > 0L } ?: (originalDateAdded * 1000L)
                val formattedOriginalDate = sdf.format(java.util.Date(originalDateTaken))

                // Read EXIF tags from original file before modifying
                val exifTags = mutableMapOf<String, String>()
                runCatching {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        val sourceExif = ExifInterface(stream)
                        for (tag in EXIF_TAGS_TO_PRESERVE) {
                            sourceExif.getAttribute(tag)?.let { exifTags[tag] = it }
                        }
                    }
                }

                // Ensure capture and modification dates in EXIF match original timestamps
                if (exifTags[ExifInterface.TAG_DATETIME_ORIGINAL] == null) {
                    exifTags[ExifInterface.TAG_DATETIME_ORIGINAL] = formattedOriginalDate
                }
                if (exifTags[ExifInterface.TAG_DATETIME_DIGITIZED] == null) {
                    exifTags[ExifInterface.TAG_DATETIME_DIGITIZED] = formattedOriginalDate
                }
                if (exifTags[ExifInterface.TAG_DATETIME] == null) {
                    exifTags[ExifInterface.TAG_DATETIME] = formattedOriginalDate
                }

                // Decode bitmap upright
                val bitmap = try {
                    val source = ImageDecoder.createSource(context.contentResolver, item.uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } catch (t: Throwable) {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    } ?: throw IOException("Could not decode image at ${item.uri}")
                }

                // Rotate bitmap
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotatedBitmap !== bitmap) {
                    bitmap.recycle()
                }

                val format = when {
                    item.mimeType.contains("png", true) -> Bitmap.CompressFormat.PNG
                    item.mimeType.contains("webp", true) -> Bitmap.CompressFormat.WEBP_LOSSY
                    else -> Bitmap.CompressFormat.JPEG
                }
                val ext = when {
                    item.mimeType.contains("png", true) -> "png"
                    item.mimeType.contains("webp", true) -> "webp"
                    else -> "jpg"
                }

                val tempFile = File(context.cacheDir, "rotate_${UUID.randomUUID()}.$ext")
                try {
                    FileOutputStream(tempFile).use { out ->
                        check(rotatedBitmap.compress(format, 95, out)) { "Failed to compress rotated image" }
                    }

                    // Restore EXIF tags to tempFile
                    runCatching {
                        val destExif = ExifInterface(tempFile.absolutePath)
                        for ((tag, value) in exifTags) {
                            destExif.setAttribute(tag, value)
                        }
                        destExif.setAttribute(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL.toString(),
                        )
                        // Write the rotated dimensions so EXIF-based tools report the
                        // correct resolution instead of the pre-rotation one.
                        destExif.setAttribute(
                            ExifInterface.TAG_IMAGE_WIDTH,
                            rotatedBitmap.width.toString(),
                        )
                        destExif.setAttribute(
                            ExifInterface.TAG_IMAGE_LENGTH,
                            rotatedBitmap.height.toString(),
                        )
                        destExif.saveAttributes()
                    }

                    val newFileSize = tempFile.length()

                    // Overwrite original URI
                    context.contentResolver.openOutputStream(item.uri, "wt")?.use { output ->
                        tempFile.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Cannot open output stream for ${item.uri}")

                    // 1. Immediately update MediaStore so it preserves original created/modified dates
                    val values = ContentValues().apply {
                        if (originalDateModified > 0L) {
                            put(MediaStore.MediaColumns.DATE_MODIFIED, originalDateModified)
                        }
                        if (originalDateAdded > 0L) {
                            put(MediaStore.MediaColumns.DATE_ADDED, originalDateAdded)
                        }
                        if (originalDateTaken > 0L) {
                            put(MediaStore.MediaColumns.DATE_TAKEN, originalDateTaken)
                        }
                        put(MediaStore.MediaColumns.WIDTH, rotatedBitmap.width)
                        put(MediaStore.MediaColumns.HEIGHT, rotatedBitmap.height)
                    }
                    runCatching {
                        context.contentResolver.update(item.uri, values, null, null)
                    }

                    // 2. Restore filesystem lastModified timestamp on physical file
                    val filePath = runCatching {
                        context.contentResolver.query(
                            item.uri,
                            arrayOf(MediaStore.MediaColumns.DATA),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val col = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                                if (col >= 0) cursor.getString(col) else null
                            } else null
                        }
                    }.getOrNull()

                    val diskFile = filePath?.let { File(it) }?.takeIf { it.exists() } ?: run {
                        val rel = item.relativePath?.trim('/') ?: ""
                        val f = File(
                            Environment.getExternalStorageDirectory(),
                            if (rel.isNotEmpty()) "$rel/${item.displayName}" else item.displayName
                        )
                        f.takeIf { it.exists() }
                    }

                    diskFile?.let { file ->
                        val targetMillis = if (originalDateModified > 0L) originalDateModified * 1000L else originalDateTaken
                        runCatching { file.setLastModified(targetMillis) }
                    }

                    // 3. Clear Coil caches for this URI
                    runCatching {
                        val imageLoader = coil3.SingletonImageLoader.get(context)
                        imageLoader.diskCache?.remove(item.uri.toString())
                        imageLoader.diskCache?.remove("${item.uri}_thumb")
                        imageLoader.diskCache?.remove("${item.uri}_thumb_${item.dateModified}")
                        imageLoader.diskCache?.remove("${item.uri}_thumb_${item.width}x${item.height}")
                        imageLoader.diskCache?.remove("${item.uri}_thumb_${rotatedBitmap.width}x${rotatedBitmap.height}")
                        imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key(item.uri.toString()))
                        imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key("${item.uri}_thumb"))
                        imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key("${item.uri}_thumb_${item.dateModified}"))
                        imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key("${item.uri}_thumb_${item.width}x${item.height}"))
                        imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key("${item.uri}_thumb_${rotatedBitmap.width}x${rotatedBitmap.height}"))
                    }

                    // 4. Save preserved dates and rotated geometry
                    preservedRotatedDates[item.id] = PreservedMediaDates(
                        dateAdded = originalDateAdded,
                        dateModified = originalDateModified,
                        dateTaken = item.dateTaken,
                        width = rotatedBitmap.width,
                        height = rotatedBitmap.height,
                        size = if (newFileSize > 0L) newFileSize else item.size,
                    )

                    rotatedCount++
                    onProgress?.invoke(rotatedCount, total)
                } finally {
                    tempFile.delete()
                    rotatedBitmap.recycle()
                }
            }

            if (rotatedCount > 0) {
                syncMediaStore()
            }
            rotatedCount
        }
    }

    /**
     * Batch updates the capture date & time for [items].
     * If [shiftOffsetMillis] is provided, each item's date is adjusted by that relative offset.
     * Otherwise, all items are set directly to [targetDateTimeMillis].
     * Updates EXIF for images, MediaStore DATE_TAKEN, and Room cache.
     */
    suspend fun updateDateTime(
        items: List<MediaItem>,
        targetDateTimeMillis: Long,
        shiftOffsetMillis: Long? = null,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var count = 0
            val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getDefault()
            }
            for (item in items) {
                val baseTime = item.dateTaken ?: (item.dateAdded * 1000L)
                val newTime = if (shiftOffsetMillis != null) {
                    baseTime + shiftOffsetMillis
                } else {
                    targetDateTimeMillis
                }
                // 1. Update EXIF if image
                if (item.isImage) {
                    try {
                        context.contentResolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
                            val exif = ExifInterface(pfd.fileDescriptor)
                            val formatted = sdf.format(java.util.Date(newTime))
                            exif.setAttribute(ExifInterface.TAG_DATETIME, formatted)
                            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formatted)
                            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formatted)
                            exif.saveAttributes()
                        }
                    } catch (_: Exception) {}
                }
                // 2. Update MediaStore DATE_TAKEN
                try {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DATE_TAKEN, newTime)
                    }
                    context.contentResolver.update(item.uri, values, null, null)
                } catch (_: Exception) {}

                // 3. Update Room DAO
                mediaDao.getMediaById(item.id)?.let { entity ->
                    mediaDao.insert(entity.copy(dateTaken = newTime))
                }
                count++
            }
            if (count > 0) syncMediaStore()
            count
        }
    }

    /**
     * Batch updates GPS coordinates for [items].
     * If [latitude] and [longitude] are provided, writes them to EXIF.
     * If null, clears GPS tags from EXIF (matching Samsung Gallery 'Remove location').
     */
    suspend fun updateLocation(
        items: List<MediaItem>,
        latitude: Double?,
        longitude: Double?,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var count = 0
            for (item in items) {
                if (item.isImage) {
                    try {
                        context.contentResolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
                            val exif = ExifInterface(pfd.fileDescriptor)
                            if (latitude != null && longitude != null) {
                                exif.setLatLong(latitude, longitude)
                            } else {
                                GPS_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
                            }
                            exif.saveAttributes()
                        }
                    } catch (_: Exception) {}
                }
                count++
            }
            if (count > 0) syncMediaStore()
            count
        }
    }
}

/**
 * Generates a stable album ID from a relative path that:
 * - Uses SHA-256 for a collision-resistant hash (vs 32-bit hashCode)
 * - Lives in the negative Long space so it can never collide with MediaStore's
 *   always-positive BUCKET_ID values
 */
private fun stableAlbumId(relativePath: String): Long {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(relativePath.lowercase().toByteArray())
    val raw = ByteBuffer.wrap(bytes, 0, 8).long
    return if (raw == Long.MIN_VALUE) -1L else -raw.absoluteValue
}

/**
 * The item an album shows when the user has not chosen a cover: the most recently captured one.
 *
 * Deliberately independent of how albums or grids are sorted — a cover that followed the sort
 * order would change every time the user switched to Name A–Z, and the album a user recognises
 * by its picture would stop being recognisable. Picked here rather than trusted from query order
 * so the rule survives any later change to the DAO's ordering, with the id breaking exact date
 * ties so the same photo wins on every emission.
 */
internal fun newestInAlbum(items: List<MediaEntity>): MediaEntity? =
    items.maxWithOrNull(
        compareBy({ effectiveDateMillis(it.dateTaken, it.dateAdded) }, { it.id }),
    )

private fun Album.toMetadataEntity(
    isPinned: Boolean = this.isPinned,
    isHidden: Boolean = this.isHidden,
    isLocked: Boolean = this.isLocked,
    // Defaults to the explicit choice, not the displayed cover. Using the latter meant
    // pinning or hiding an album froze whatever it happened to be showing at the time,
    // so its cover silently stopped following new photos.
    coverUri: String? = this.customCoverUri?.toString(),
) = AlbumEntity(
    id = id,
    name = name,
    coverUri = coverUri,
    relativePath = relativePath,
    isHidden = isHidden,
    isPinned = isPinned,
    isLocked = isLocked,
    isPrivate = isPrivate,
    isMemoriesVault = isMemoriesVault,
    lastModified = lastModified,
)

// ============================================
// Mapping extensions
// ============================================

private fun MediaItem.toEntity() = MediaEntity(
    id = id,
    uri = uri.toString(),
    displayName = displayName,
    mimeType = mimeType,
    size = size,
    width = width,
    height = height,
    dateAdded = dateAdded,
    dateModified = dateModified,
    dateTaken = dateTaken,
    duration = duration,
    bucketId = bucketId,
    bucketName = bucketName,
    relativePath = relativePath,
    isFavorite = isFavorite,
    isTrashed = isTrashed,
    isCompressed = isCompressed,
    originalSize = originalSize,
)

fun MediaEntity.toDomain() = MediaItem(
    id = id,
    uri = Uri.parse(uri),
    displayName = displayName,
    mimeType = mimeType,
    size = size,
    width = width,
    height = height,
    dateAdded = dateAdded,
    dateModified = dateModified,
    dateTaken = dateTaken,
    duration = duration,
    bucketId = bucketId,
    bucketName = bucketName,
    relativePath = relativePath,
    isFavorite = isFavorite,
    isTrashed = isTrashed,
    isCompressed = isCompressed,
    originalSize = originalSize,
)
