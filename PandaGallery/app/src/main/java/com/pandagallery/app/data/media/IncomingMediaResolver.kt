package com.pandagallery.app.data.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Turns a URI handed over by another app into something the gallery can actually show.
 *
 * Only some apps pass a MediaStore URI. WhatsApp serves chat attachments — including the
 * images people send as documents — from its own provider, `content://com.whatsapp.provider.media/…`,
 * whose last path segment is a message key rather than a MediaStore row id. Reading an id
 * straight off such a URI either fails outright (the "isn't in this device's gallery" dead
 * end) or, worse, succeeds with a number belonging to an unrelated photo.
 *
 * The file behind those URIs is usually indexed anyway — WhatsApp keeps its media under
 * `Android/media/com.whatsapp/…`, which MediaStore scans — so the work is matching the
 * foreign URI back to its MediaStore row. That is attempted from the most precise signal to
 * the loosest: the media provider itself, a document id, a filesystem path, then name and
 * size. Whatever is still unmatched (a chat cache under `Android/data`, a `.nomedia`
 * folder) is shown straight from its URI as a read-only external item instead of refused.
 */
@Singleton
class IncomingMediaResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed interface Resolution {
        /** The URI belongs to a photo or video MediaStore already knows about. */
        data class InGallery(val mediaId: Long) : Resolution

        /** Readable, but outside the gallery — shown on its own, without library actions. */
        data class External(val uri: Uri) : Resolution

        /** Nothing readable behind the URI; the grant may already have lapsed. */
        data object Unreadable : Resolution
    }

    suspend fun resolve(uri: Uri): Resolution = withContext(Dispatchers.IO) {
        val mediaId = mediaProviderId(uri)
            ?: translatedMediaId(uri)
            ?: documentMediaId(uri)
            ?: filePathId(uri)
            ?: nameAndSizeId(uri)

        when {
            mediaId != null -> Resolution.InGallery(mediaId)
            isReadable(uri) -> Resolution.External(uri)
            else -> Resolution.Unreadable
        }
    }

    /**
     * Describes a URI the gallery does not own well enough for the viewer to display it.
     *
     * The id is synthetic and negative so it can never collide with a MediaStore row, and
     * `bucketId` stays null, which already disables the album-scoped actions downstream.
     */
    suspend fun describe(uri: Uri): MediaItem? = withContext(Dispatchers.IO) {
        if (!isReadable(uri)) return@withContext null
        val metadata = openableMetadata(uri)
        val name = metadata?.name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Shared item"
        val mimeType = context.contentResolver.getType(uri)
            ?: mimeTypeFromName(name)
            ?: "image/*"
        val modifiedMillis = metadata?.lastModifiedMillis ?: System.currentTimeMillis()
        val bounds = if (mimeType.startsWith("video/")) null else imageBounds(uri)

        MediaItem(
            id = -(uri.toString().hashCode().toLong().absoluteValue.coerceAtLeast(1L)),
            uri = uri,
            displayName = name,
            mimeType = mimeType,
            size = metadata?.size ?: 0L,
            width = bounds?.first ?: 0,
            height = bounds?.second ?: 0,
            dateAdded = modifiedMillis / 1000L,
            dateModified = modifiedMillis / 1000L,
            dateTaken = modifiedMillis,
        )
    }

    // ── Matching strategies, most precise first ──────────────────────────────────────

    /** `content://media/external/images/media/42` and friends. */
    private fun mediaProviderId(uri: Uri): Long? {
        if (!uri.authority.equals(MediaStore.AUTHORITY, ignoreCase = true)) return null
        val id = uri.lastPathSegment?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        return id.takeIf(::isVisualMedia)
    }

    /**
     * Document and Downloads URIs that MediaProvider itself backs can be translated to
     * their `content://media/…` form, which the step above then reads an id from.
     */
    private fun translatedMediaId(uri: Uri): Long? {
        val translated = runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull() ?: return null
        if (translated == uri) return null
        return mediaProviderId(translated)
    }

    /** Document ids of the form `image:42` / `video:42` from the media documents provider. */
    private fun documentMediaId(uri: Uri): Long? {
        val (type, value) = documentIdParts(uri) ?: return null
        return when (type.lowercase()) {
            "image", "video" -> value.toLongOrNull()?.takeIf(::isVisualMedia)
            else -> null
        }
    }

    /** A `file://` URI, or a document id naming a path on the primary volume. */
    private fun filePathId(uri: Uri): Long? {
        val path = filePath(uri) ?: return null
        return queryFirstId(
            selection = "${MediaStore.Files.FileColumns.DATA} = ? AND $VISUAL_MEDIA_SELECTION",
            arguments = arrayOf(path),
        )
    }

    /**
     * The fallback that catches third-party providers: ask the URI what the file is called
     * and how big it is, then look for that file in MediaStore. Size is matched first so
     * two chats holding a same-named photo cannot be confused for each other; a name-only
     * match takes the most recently modified candidate.
     */
    private fun nameAndSizeId(uri: Uri): Long? {
        val metadata = openableMetadata(uri) ?: return null
        val name = metadata.name?.takeIf { it.isNotBlank() } ?: return null
        val size = metadata.size?.takeIf { it > 0L }
        val byNameAndSize = size?.let {
            queryFirstId(
                selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.Files.FileColumns.SIZE} = ? AND $VISUAL_MEDIA_SELECTION",
                arguments = arrayOf(name, it.toString()),
            )
        }
        return byNameAndSize ?: queryFirstId(
            selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND $VISUAL_MEDIA_SELECTION",
            arguments = arrayOf(name),
        )
    }

    // ── Plumbing ────────────────────────────────────────────────────────────────────

    private fun isVisualMedia(id: Long): Boolean = queryFirstId(
        selection = "${MediaStore.Files.FileColumns._ID} = ? AND $VISUAL_MEDIA_SELECTION",
        arguments = arrayOf(id.toString()),
    ) != null

    /**
     * Queries the files table, which spans both images and videos so a single lookup covers
     * either. Trashed rows are included: an item on its way out is still the item the other
     * app asked us to open.
     */
    private fun queryFirstId(selection: String, arguments: Array<String>): Long? {
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arguments)
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED),
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }
        return runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                arrayOf(MediaStore.Files.FileColumns._ID),
                queryArgs,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    private fun filePath(uri: Uri): String? = when {
        uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) -> uri.path
        uri.authority == EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY -> primaryVolumeDocumentPath(uri)
        else -> null
    }

    private fun primaryVolumeDocumentPath(uri: Uri): String? {
        val (volume, relativePath) = documentIdParts(uri) ?: return null
        if (!volume.equals("primary", ignoreCase = true)) return null
        return "${Environment.getExternalStorageDirectory()}/$relativePath"
    }

    private fun documentIdParts(uri: Uri): Pair<String, String>? {
        if (!runCatching { DocumentsContract.isDocumentUri(context, uri) }.getOrDefault(false)) return null
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val parts = documentId.split(':', limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return parts[0] to parts[1]
    }

    private fun openableMetadata(uri: Uri): OpenableMetadata? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            OpenableMetadata(
                name = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME),
                size = cursor.longOrNull(OpenableColumns.SIZE),
                lastModifiedMillis = cursor.longOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            )
        }
    }.getOrNull()
        // Providers are free to reject columns they do not carry; retry with the essentials.
        ?: runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                OpenableMetadata(
                    name = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME),
                    size = cursor.longOrNull(OpenableColumns.SIZE),
                    lastModifiedMillis = cursor.longOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                )
            }
        }.getOrNull()

    private fun isReadable(uri: Uri): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun imageBounds(uri: Uri): Pair<Int, Int>? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else {
                null
            }
        }
    }.getOrNull()

    private fun mimeTypeFromName(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private data class OpenableMetadata(
        val name: String?,
        val size: Long?,
        val lastModifiedMillis: Long?,
    )

    private companion object {
        const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"
        val VISUAL_MEDIA_SELECTION =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN " +
                "(${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
    }
}

private fun android.database.Cursor.stringOrNull(column: String): String? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun android.database.Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}
