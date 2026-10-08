package com.pandagallery.app.data.source

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.pandagallery.app.data.compression.Mp4Timestamps
import com.pandagallery.app.data.media.normalizedAlbumRelativePath
import com.pandagallery.app.data.media.renamedMediaDisplayName
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.UserPreferences
import com.pandagallery.app.domain.model.effectiveDateMillis
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source that queries MediaStore for images and videos.
 * Optimized for speed: queries only essential columns,
 * uses bundle-based pagination, and runs on IO dispatcher.
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    fun observeChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            observer,
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            observer,
        )
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    // Columns to query — minimal set for performance
    private val imageProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.IS_FAVORITE,
        MediaStore.Images.Media.IS_TRASHED,
    )

    private val videoProjection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.MIME_TYPE,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.DATE_TAKEN,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.BUCKET_ID,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.RELATIVE_PATH,
        MediaStore.Video.Media.IS_FAVORITE,
        MediaStore.Video.Media.IS_TRASHED,
    )

    /**
     * Load all images from MediaStore.
     */
    suspend fun loadImages(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val queryArgs = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Images.Media.DATE_ADDED)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }

        contentResolver.query(uri, imageProjection, queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val relativePathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val favoriteCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_FAVORITE)
            val trashedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_TRASHED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(uri, id)

                items.add(
                    MediaItem(
                        id = id,
                        uri = contentUri,
                        displayName = cursor.getString(nameCol) ?: "Unknown",
                        mimeType = cursor.getString(mimeCol) ?: "image/*",
                        size = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModifiedCol),
                        dateTaken = cursor.getLongOrNull(dateTakenCol),
                        duration = null,
                        bucketId = cursor.getLongOrNull(bucketIdCol),
                        bucketName = cursor.getString(bucketNameCol),
                        relativePath = cursor.getString(relativePathCol),
                        isFavorite = cursor.getInt(favoriteCol) == 1,
                        isTrashed = cursor.getInt(trashedCol) == 1,
                    )
                )
            }
        }
        items
    }

    /**
     * Load all videos from MediaStore.
     */
    suspend fun loadVideos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val queryArgs = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Video.Media.DATE_ADDED)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }

        contentResolver.query(uri, videoProjection, queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val relativePathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            val favoriteCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.IS_FAVORITE)
            val trashedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.IS_TRASHED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(uri, id)

                items.add(
                    MediaItem(
                        id = id,
                        uri = contentUri,
                        displayName = cursor.getString(nameCol) ?: "Unknown",
                        mimeType = cursor.getString(mimeCol) ?: "video/*",
                        size = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModifiedCol),
                        dateTaken = cursor.getLongOrNull(dateTakenCol),
                        duration = cursor.getLongOrNull(durationCol),
                        bucketId = cursor.getLongOrNull(bucketIdCol),
                        bucketName = cursor.getString(bucketNameCol),
                        relativePath = cursor.getString(relativePathCol),
                        isFavorite = cursor.getInt(favoriteCol) == 1,
                        isTrashed = cursor.getInt(trashedCol) == 1,
                    )
                )
            }
        }
        items
    }

    /**
     * Load all media (images + videos) sorted by date.
     */
    suspend fun loadAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            val imagesDeferred = async { loadImages() }
            val videosDeferred = async { loadVideos() }
            val allMedia = mutableListOf<MediaItem>()
            allMedia.addAll(imagesDeferred.await())
            allMedia.addAll(videosDeferred.await())
            allMedia.sortByDescending { it.sortDate }
            allMedia
        }
    }

    suspend fun publishCompressedFile(
        source: MediaItem,
        compressedFile: File,
        preferences: UserPreferences,
    ): Uri = withContext(Dispatchers.IO) {
        val isVideo = source.isVideo
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val displayName = compressedName(source.displayName, compressedFile.extension)
        val mimeType = if (isVideo) "video/mp4" else when (compressedFile.extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            else -> "image/webp"
        }
        val relativePath = preferences.compressedFolderPath
            .trim()
            .trim('/')
            .ifBlank { "Pictures/PandaGallery Compressed" } + "/"

        val effectiveDateMillis = effectiveDateMillis(source.dateTaken, source.dateAdded)
        val effectiveDateSeconds = effectiveDateMillis / 1000L

        // Publishing makes MediaProvider scan the file, and for a video the scan takes DATE_TAKEN
        // from the MP4 header rather than from anything inserted below. The muxer wrote the export
        // time there, so it has to carry the original's capture time before the file is shared.
        if (isVideo) runCatching { Mp4Timestamps.setCreationTime(compressedFile, effectiveDateMillis) }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.MediaColumns.DATE_TAKEN, effectiveDateMillis)
            put(MediaStore.MediaColumns.DATE_ADDED, effectiveDateSeconds)
            put(MediaStore.MediaColumns.DATE_MODIFIED, effectiveDateSeconds)
        }

        val destinationUri = contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Unable to create compressed media item")
        try {
            contentResolver.openOutputStream(destinationUri)?.use { output ->
                compressedFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Unable to write compressed media item")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            values.put(MediaStore.MediaColumns.DATE_TAKEN, effectiveDateMillis)
            values.put(MediaStore.MediaColumns.DATE_ADDED, effectiveDateSeconds)
            values.put(MediaStore.MediaColumns.DATE_MODIFIED, effectiveDateSeconds)
            contentResolver.update(destinationUri, values, null, null)

            // Clearing IS_PENDING is what triggers the scan, and the scan's own reading of the
            // file lands after the values above. Set the capture date again now that it has run,
            // for formats whose metadata the scan could not read a date from at all.
            runCatching {
                contentResolver.update(
                    destinationUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, effectiveDateMillis) },
                    null,
                    null,
                )
            }

            // The write above happens on the raw file, whose OS-level lastModified is stamped
            // "now" the moment the stream closes. Any later MediaProvider rescan (FUSE close,
            // directory observer, full media scan) reads that stat and overwrites DATE_MODIFIED
            // right back to today, undoing the ContentValues set above. Stamping the physical
            // file's mtime to the original date is best effort only: on the FUSE-backed shared
            // storage of recent Android versions it is refused, which is why the capture date
            // is also written into the file's own metadata (EXIF offset, MP4 header).
            runCatching {
                contentResolver.query(
                    destinationUri,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (dataIndex >= 0) {
                            cursor.getString(dataIndex)?.let { path ->
                                File(path).setLastModified(effectiveDateMillis)
                            }
                        }
                    }
                }
            }

            destinationUri
        } catch (error: Throwable) {
            contentResolver.delete(destinationUri, null, null)
            throw error
        }
    }

    /**
     * Toggle favorite status in MediaStore (Android 11+).
     */
    suspend fun toggleFavorite(item: MediaItem, favorite: Boolean) = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
        }
        contentResolver.update(item.uri, values, null, null)
    }

    /**
     * Move item to trash in MediaStore (Android 11+).
     */
    suspend fun trashItem(item: MediaItem) = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, 1)
        }
        contentResolver.update(item.uri, values, null, null)
    }

    /**
     * Restore item from trash in MediaStore.
     */
    suspend fun restoreItem(item: MediaItem) = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, 0)
        }
        contentResolver.update(item.uri, values, null, null)
    }

    /**
     * @param onProgress called with how many items are done, so a long copy can show its progress
     *   instead of looking frozen — copying hundreds of photos is minutes of work.
     */
    suspend fun copyToAlbum(
        items: List<MediaItem>,
        relativePath: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val destinationPath = normalizedAlbumRelativePath(relativePath)
                items.countIndexed { index, item ->
                    onProgress(index + 1, items.size)
                    val collection = if (item.isVideo) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    }
                    val destination = contentResolver.insert(collection, ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, destinationPath)
                        item.dateTaken?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }) ?: error("Unable to create ${item.displayName}")
                    try {
                        contentResolver.openInputStream(item.uri)?.use { input ->
                            contentResolver.openOutputStream(destination)?.use(input::copyTo)
                                ?: error("Unable to write ${item.displayName}")
                        } ?: error("Unable to read ${item.displayName}")
                        contentResolver.update(destination, ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }, null, null)
                        true
                    } catch (error: Throwable) {
                        contentResolver.delete(destination, null, null)
                        throw error
                    }
                }
            }
        }

    suspend fun moveToAlbum(
        items: List<MediaItem>,
        relativePath: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val destinationPath = normalizedAlbumRelativePath(relativePath)
                items.countIndexed { index, item ->
                    onProgress(index + 1, items.size)
                    contentResolver.update(item.uri, ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, destinationPath)
                    }, null, null) > 0
                }
            }
        }


    suspend fun renameMedia(item: MediaItem, requestedName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val displayName = renamedMediaDisplayName(item.displayName, requestedName)
                val updated = contentResolver.update(item.uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                }, null, null)
                check(updated > 0) { "Unable to rename ${item.displayName}" }
                displayName
            }
        }

    // Extension to safely get nullable Long from cursor
    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (isNull(columnIndex)) null else getLong(columnIndex)
    }

    private fun compressedName(originalName: String, extension: String): String {
        val cleanExtension = extension.ifBlank { "webp" }
        val baseName = originalName.substringBeforeLast('.', originalName)
        return "${baseName}_compressed.$cleanExtension"
    }
}

/**
 * [Iterable.count] with the item's position, so a per-item callback can report progress without
 * the loop having to keep its own counter.
 */
private inline fun <T> List<T>.countIndexed(predicate: (index: Int, T) -> Boolean): Int {
    var matches = 0
    forEachIndexed { index, item -> if (predicate(index, item)) matches++ }
    return matches
}
