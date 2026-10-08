package com.pandagallery.app.data.collage

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LocalAudioRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun getDeviceAudios(searchQuery: String = ""): List<CollageAudioTrack> = withContext(Dispatchers.IO) {
        val audios = mutableListOf<CollageAudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
        )

        val selection = if (searchQuery.isBlank()) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 1000"
        } else {
            "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 1000) AND (${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?)"
        }

        val selectionArgs = if (searchQuery.isBlank()) {
            null
        } else {
            val q = "%$searchQuery%"
            arrayOf(q, q)
        }

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn)?.takeIf { it != "<unknown>" } ?: "Unknown Artist"
                    val duration = cursor.getLong(durationColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )

                    audios.add(
                        CollageAudioTrack(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            uri = contentUri,
                            durationMs = duration.coerceAtLeast(0L),
                            isOnline = false,
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore security or query exceptions gracefully
        }
        audios
    }

    suspend fun resolveTrackFromUri(uri: Uri): CollageAudioTrack = withContext(Dispatchers.IO) {
        var title = "Audio Track"
        var artist = "Local File"
        var durationMs = 0L

        // Try getting display name from content resolver
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            title = name.substringBeforeLast(".")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Try extracting metadata with MediaMetadataRetriever
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                if (!metaTitle.isNullOrBlank()) title = metaTitle
                if (!metaArtist.isNullOrBlank()) artist = metaArtist
                metaDuration?.toLongOrNull()?.let { durationMs = it }
            }
        } catch (_: Exception) {}

        CollageAudioTrack(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = artist,
            uri = uri,
            durationMs = durationMs,
            isOnline = false,
        )
    }
}
