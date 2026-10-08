package com.pandagallery.app.data.export

import android.content.Context
import android.net.Uri
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Outcome of an export, including anything that could not be read. */
data class AlbumExportResult(
    val exported: Int,
    val skipped: Int,
    val totalBytes: Long,
) {
    val hasFailures: Boolean get() = skipped > 0
}

/**
 * Bundles an album into a single zip the user can hand to someone.
 *
 * Originals are stored, not re-compressed: the point of an export is to give somebody the
 * real files. Media is already in compressed formats, so zip's deflate would spend a lot of
 * CPU to save almost nothing — [ZipEntry.STORED] is not used only because it requires
 * pre-computing CRCs, but the deflate level is set to zero for the same reason.
 */
@Singleton
class AlbumExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * @param destination a document URI from `CreateDocument`, already writable.
     * @param onProgress called with (completed, total) as each item finishes.
     * @throws IOException when the destination itself cannot be opened. Individual
     *   unreadable items are counted as skipped rather than aborting the whole export.
     */
    suspend fun exportToZip(
        items: List<MediaItem>,
        destination: Uri,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): AlbumExportResult = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(destination)
            ?: throw IOException("Could not open the chosen destination")

        var exported = 0
        var skipped = 0
        var totalBytes = 0L
        val usedNames = mutableSetOf<String>()

        try {
            ZipOutputStream(output.buffered()).use { zip ->
                zip.setLevel(0)
                items.forEachIndexed { index, item ->
                    coroutineContext.ensureActive()
                    val entryName = uniqueName(item.displayName, usedNames)
                    val copied = runCatching {
                        context.contentResolver.openInputStream(item.uri)?.use { input ->
                            zip.putNextEntry(ZipEntry(entryName))
                            val bytes = input.copyTo(zip)
                            zip.closeEntry()
                            bytes
                        }
                    }.getOrNull()

                    if (copied == null) skipped++ else { exported++; totalBytes += copied }
                    onProgress(index + 1, items.size)
                }
            }
        } catch (cancelled: CancellationException) {
            // A half-written zip is worse than none: the user would try to open it.
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, destination) }
            throw cancelled
        }

        AlbumExportResult(exported = exported, skipped = skipped, totalBytes = totalBytes)
    }

    /**
     * Two photos in one album can share a display name, and a zip with duplicate entry names
     * extracts unpredictably. Disambiguate rather than silently lose one.
     */
    private fun uniqueName(displayName: String, used: MutableSet<String>): String {
        val cleaned = displayName.replace('/', '_').ifBlank { "item" }
        if (used.add(cleaned)) return cleaned
        val stem = cleaned.substringBeforeLast('.', cleaned)
        val extension = cleaned.substringAfterLast('.', "")
        var suffix = 2
        while (true) {
            val candidate = if (extension.isEmpty()) "$stem ($suffix)" else "$stem ($suffix).$extension"
            if (used.add(candidate)) return candidate
            suffix++
        }
    }
}

/** A filename-safe export name for an album, e.g. `Holiday.zip`. */
fun albumExportFileName(albumName: String): String {
    val cleaned = albumName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Album" }
    return "$cleaned.zip"
}
