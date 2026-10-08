package com.pandagallery.app.domain.model

import android.net.Uri
import kotlinx.serialization.Serializable

/**
 * Core media item representing an image or video in the gallery.
 * This is the domain model used throughout the app.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val size: Long,             // bytes
    val width: Int,
    val height: Int,
    val dateAdded: Long,        // epoch seconds
    val dateModified: Long,     // epoch seconds
    val dateTaken: Long?,       // epoch millis (from EXIF)
    val duration: Long? = null, // millis (for video)
    val bucketId: Long? = null,
    val bucketName: String? = null,
    val relativePath: String? = null,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isCompressed: Boolean = false,
    val originalSize: Long? = null,  // size before compression
) {
    val mediaType: MediaType
        get() = when {
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("image/gif") -> MediaType.GIF
            else -> MediaType.IMAGE
        }

    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
    val isImage: Boolean get() = mediaType == MediaType.IMAGE || mediaType == MediaType.GIF

    /** Formatted file size (e.g., "3.2 MB") */
    val formattedSize: String
        get() = formatFileSize(size)

    val formattedOriginalSize: String?
        get() = originalSize?.takeIf { it > 0L }?.let(::formatFileSize)

    val compressionPercent: Int?
        get() = calculateCompressionPercent(originalSize, size)

    /** Formatted duration for video (e.g., "3:45") */
    val formattedDuration: String?
        get() = duration?.let {
            val totalSeconds = it / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            "%d:%02d".format(minutes, seconds)
        }

    /** The best date to use for sorting/display */
    val sortDate: Long get() = effectiveDateMillis(dateTaken, dateAdded)

    /** Resolution as string (e.g., "4032 × 3024") */
    val resolution: String get() = "$width × $height"

    /** Compression savings ratio (e.g., "72% saved") */
    val compressionSavings: String?
        get() = compressionPercent?.let { "$it% saved" }
}

/**
 * The single timestamp the app sorts, groups and picks covers by.
 *
 * `dateTaken` is the real capture time, but MediaStore leaves it absent for screenshots,
 * downloads and received media — as `null` on some devices and as a literal `0` on others.
 * Treating `0` as a real date stamps those files as 1970 and buries today's screenshot at the
 * very bottom of the timeline, so both forms have to count as "no capture date". `dateAdded`
 * is always populated and always in seconds, hence the conversion.
 *
 * `SQL_EFFECTIVE_DATE_MILLIS` is the same rule expressed for Room. The two must stay in step:
 * album covers come from query order while grids sort in memory, and any difference between
 * them shows up as an album whose cover is not its first photo.
 */
fun effectiveDateMillis(dateTaken: Long?, dateAddedSeconds: Long): Long =
    dateTaken?.takeIf { it > 0L } ?: (dateAddedSeconds * 1000L)

/** Kotlin's [effectiveDateMillis] as a SQLite expression. See that function for why. */
const val SQL_EFFECTIVE_DATE_MILLIS =
    "(CASE WHEN dateTaken IS NULL OR dateTaken <= 0 THEN dateAdded * 1000 ELSE dateTaken END)"

fun calculateCompressionPercent(originalBytes: Long?, currentBytes: Long): Int? {
    if (originalBytes == null || originalBytes <= 0L) return null
    return (((originalBytes - currentBytes).coerceAtLeast(0L) * 100L) / originalBytes).toInt()
}

fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}

/**
 * Type of media content.
 */
enum class MediaType {
    IMAGE,
    VIDEO,
    GIF,
}

@Serializable
enum class MediaContentFilter {
    ALL,
    IMAGES,
    VIDEOS,
    PORTRAITS,
    RAW,
    SLOW_MO,
    SCREENSHOTS,
    GIFS,
    LARGE_FILES,
}

internal fun filterMedia(items: List<MediaItem>, filter: MediaContentFilter): List<MediaItem> =
    items.filter { it.matchesFilter(filter) }

internal fun MediaItem.matchesFilter(filter: MediaContentFilter): Boolean = when (filter) {
    MediaContentFilter.ALL -> true
    MediaContentFilter.IMAGES -> mimeType.startsWith("image/") && mimeType != "image/gif"
    MediaContentFilter.VIDEOS -> mimeType.startsWith("video/")
    MediaContentFilter.PORTRAITS -> displayName.contains("portrait", ignoreCase = true) ||
            (bucketName?.contains("portrait", ignoreCase = true) == true)
    MediaContentFilter.RAW -> mimeType in listOf("image/x-adobe-dng", "image/x-canon-cr2", "image/x-nikon-nef", "image/x-sony-arw") ||
            displayName.endsWith(".dng", ignoreCase = true) || displayName.endsWith(".raw", ignoreCase = true)
    MediaContentFilter.SLOW_MO -> isVideo && (displayName.contains("slow", ignoreCase = true) || displayName.contains("slomo", ignoreCase = true))
    MediaContentFilter.SCREENSHOTS -> (bucketName?.contains("screenshot", ignoreCase = true) == true) ||
            (relativePath?.contains("screenshot", ignoreCase = true) == true) ||
            displayName.contains("screenshot", ignoreCase = true)
    MediaContentFilter.GIFS -> mimeType == "image/gif"
    MediaContentFilter.LARGE_FILES -> size >= 50L * 1024L * 1024L
}

internal fun matchesMediaContentFilter(
    mimeType: String,
    size: Long,
    filter: MediaContentFilter,
): Boolean = when (filter) {
    MediaContentFilter.ALL -> true
    MediaContentFilter.IMAGES -> mimeType.startsWith("image/") && mimeType != "image/gif"
    MediaContentFilter.VIDEOS -> mimeType.startsWith("video/")
    MediaContentFilter.PORTRAITS -> false
    MediaContentFilter.RAW -> mimeType in listOf("image/x-adobe-dng", "image/x-canon-cr2", "image/x-nikon-nef", "image/x-sony-arw")
    MediaContentFilter.SLOW_MO -> mimeType.startsWith("video/")
    MediaContentFilter.SCREENSHOTS -> false
    MediaContentFilter.GIFS -> mimeType == "image/gif"
    MediaContentFilter.LARGE_FILES -> size >= 50L * 1024L * 1024L
}

/**
 * Represents an album/folder in the gallery.
 */
data class Album(
    val id: Long,
    val name: String,
    /**
     * The cover actually shown. Either the user's pick or, failing that, the album's most
     * recent item — so this is a display value and must never be written back as a choice.
     */
    val coverUri: Uri?,
    val mediaCount: Int,
    val lastModified: Long,
    /** Bytes held by this album's media, for the optional size line on the card. */
    val sizeBytes: Long = 0L,
    val relativePath: String? = null,
    val isHidden: Boolean = false,
    val isPinned: Boolean = false,
    /**
     * Requires device authentication before opening. The files are not moved or encrypted —
     * this guards the in-app path only, which the UI states plainly so nobody mistakes it
     * for the Private Album.
     */
    val isLocked: Boolean = false,
    val isPrivate: Boolean = false,
    val isMemoriesVault: Boolean = false,
    /**
     * The cover the user explicitly chose, or null when the album picks its own.
     *
     * Kept separate from [coverUri] because persisting the effective cover would silently
     * freeze an automatic one the first time any other album property was written.
     */
    val customCoverUri: Uri? = null,
    val groupName: String? = null,
) {
    val hasCustomCover: Boolean get() = customCoverUri != null
}

/**
 * Picks the cover to display.
 *
 * A chosen cover only wins while the photo behind it still exists — otherwise deleting that
 * one photo would leave the album showing a blank tile forever, with nothing to indicate
 * why. Falling back to the automatic cover makes that self-healing.
 */
internal fun resolveAlbumCover(
    customCoverUri: String?,
    availableUris: Set<String>,
    automaticCoverUri: String?,
): String? = customCoverUri?.takeIf { it in availableUris } ?: automaticCoverUri

@Serializable
enum class AlbumSortOrder {
    NEWEST,
    OLDEST,
    NAME_ASC,
    NAME_DESC,
    ITEM_COUNT,
}

/**
 * Applies a temporary reveal of hidden albums.
 *
 * [revealHidden] is a session-only view state, never a change to an album's stored hidden
 * flag — revealing shows them for the moment, it does not un-hide them.
 */
internal fun visibleAlbums(albums: List<Album>, revealHidden: Boolean): List<Album> =
    if (revealHidden) albums else albums.filterNot(Album::isHidden)

internal fun sortAlbums(albums: List<Album>, order: AlbumSortOrder): List<Album> {
    val sorted = when (order) {
        AlbumSortOrder.NEWEST -> albums.sortedByDescending { it.lastModified }
        AlbumSortOrder.OLDEST -> albums.sortedBy { it.lastModified }
        AlbumSortOrder.NAME_ASC -> albums.sortedBy { it.name.lowercase() }
        AlbumSortOrder.NAME_DESC -> albums.sortedByDescending { it.name.lowercase() }
        AlbumSortOrder.ITEM_COUNT -> albums.sortedByDescending { it.mediaCount }
    }
    return sorted.sortedByDescending { it.isPinned }
}

/**
 * High-level display item for the root albums grid.
 * Samsung Gallery One UI 6.1 clusters albums belonging to a group into an [AlbumDisplayItem.Group]
 * card with 4-quadrant preview, leaving ungrouped albums as [AlbumDisplayItem.Single].
 */
sealed interface AlbumDisplayItem {
    val id: String
    val isPinned: Boolean
    val lastModified: Long

    data class Single(val album: Album) : AlbumDisplayItem {
        override val id: String get() = "album_${album.id}"
        override val isPinned: Boolean get() = album.isPinned
        override val lastModified: Long get() = album.lastModified
    }

    data class Group(
        val name: String,
        val albums: List<Album>,
        val coverUris: List<Uri>,
        val totalMediaCount: Int,
        val totalSizeBytes: Long,
        override val isPinned: Boolean = false,
        override val lastModified: Long = 0L,
    ) : AlbumDisplayItem {
        override val id: String get() = "group_$name"
    }
}

/**
 * Clusters a list of albums into display items: groups and standalone single albums.
 */
fun clusterAlbumsForDisplay(
    albums: List<Album>,
    sortOrder: AlbumSortOrder,
): List<AlbumDisplayItem> {
    val grouped = albums.groupBy { it.groupName }
    val result = mutableListOf<AlbumDisplayItem>()

    // Add groups
    grouped.forEach { (groupName, groupAlbums) ->
        if (groupName != null) {
            val covers = groupAlbums.mapNotNull { it.coverUri }.distinct().take(4)
            val totalMediaCount = groupAlbums.sumOf { it.mediaCount }
            val totalSize = groupAlbums.sumOf { it.sizeBytes }
            val isPinned = groupAlbums.any { it.isPinned }
            val lastModified = groupAlbums.maxOfOrNull { it.lastModified } ?: 0L
            result.add(
                AlbumDisplayItem.Group(
                    name = groupName,
                    albums = sortAlbums(groupAlbums, sortOrder),
                    coverUris = covers,
                    totalMediaCount = totalMediaCount,
                    totalSizeBytes = totalSize,
                    isPinned = isPinned,
                    lastModified = lastModified,
                )
            )
        }
    }

    // Add ungrouped albums
    grouped[null]?.forEach { album ->
        result.add(AlbumDisplayItem.Single(album))
    }

    // Sort items respecting order and pinning
    val sorted = when (sortOrder) {
        AlbumSortOrder.NEWEST -> result.sortedByDescending { it.lastModified }
        AlbumSortOrder.OLDEST -> result.sortedBy { it.lastModified }
        AlbumSortOrder.NAME_ASC -> result.sortedBy {
            when (it) {
                is AlbumDisplayItem.Single -> it.album.name.lowercase()
                is AlbumDisplayItem.Group -> it.name.lowercase()
            }
        }
        AlbumSortOrder.NAME_DESC -> result.sortedByDescending {
            when (it) {
                is AlbumDisplayItem.Single -> it.album.name.lowercase()
                is AlbumDisplayItem.Group -> it.name.lowercase()
            }
        }
        AlbumSortOrder.ITEM_COUNT -> result.sortedByDescending {
            when (it) {
                is AlbumDisplayItem.Single -> it.album.mediaCount
                is AlbumDisplayItem.Group -> it.totalMediaCount
            }
        }
    }
    return sorted.sortedByDescending { it.isPinned }
}

internal fun bulkFavoriteTarget(currentValues: List<Boolean>): Boolean =
    currentValues.isEmpty() || !currentValues.all { it }

/**
 * A date-grouped section header for the timeline view.
 */
data class TimelineHeader(
    val label: String,     // "Today", "Yesterday", "August 3, 2026"
    val dateKey: String,   // "2026-08-05" for grouping
    val itemCount: Int,
    val itemIds: Set<Long>,
)

/**
 * Represents a timeline item — either a header or media.
 */
sealed class TimelineItem {
    data class Header(val header: TimelineHeader) : TimelineItem()
    data class Media(val item: MediaItem) : TimelineItem()
}

/**
 * Sort options for media and albums.
 */
@Serializable
enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
}

/**
 * Whether a grid sorted this way can carry date section headers.
 *
 * Only a date order keeps each day in one contiguous run. Under Name or Size a day reappears
 * further down the list, which gave two "August 18" sections with other days between them — each
 * claiming the whole day's count and selecting the whole day's photos — and, because the grid keys
 * sections by date, a duplicate key that crashed it outright. Those orders get a plain grid.
 */
fun sortSupportsDateSections(order: SortOrder): Boolean =
    order == SortOrder.DATE_DESC || order == SortOrder.DATE_ASC

/**
 * Whether opening this folder should ask for the folder PIN.
 *
 * The `hasFolderLockPin` term is what keeps the lock answerable. Removing the folder PIN while a
 * folder was still flagged locked used to leave that folder unopenable *and* unlockable: every
 * guess was compared against a PIN that no longer existed, so it failed and counted toward a
 * lockout. A lock only means something while there is a PIN behind it.
 */
fun folderNeedsPin(
    isLocked: Boolean,
    hasFolderLockPin: Boolean,
    isUnlockedThisSession: Boolean,
): Boolean = isLocked && hasFolderLockPin && !isUnlockedThisSession

/** How a media sort order reads in menus and in Settings. */
val SortOrder.label: String
    get() = when (this) {
        SortOrder.DATE_DESC -> "Newest first"
        SortOrder.DATE_ASC -> "Oldest first"
        SortOrder.NAME_ASC -> "Name (A-Z)"
        SortOrder.NAME_DESC -> "Name (Z-A)"
        SortOrder.SIZE_DESC -> "Largest first"
        SortOrder.SIZE_ASC -> "Smallest first"
    }

/** How an album sort order reads in menus and in Settings. */
val AlbumSortOrder.label: String
    get() = when (this) {
        AlbumSortOrder.NEWEST -> "Newest albums"
        AlbumSortOrder.OLDEST -> "Oldest albums"
        AlbumSortOrder.NAME_ASC -> "Name (A-Z)"
        AlbumSortOrder.NAME_DESC -> "Name (Z-A)"
        AlbumSortOrder.ITEM_COUNT -> "Most items"
    }

/**
 * Which sort a media grid uses.
 *
 * A folder keeps its own choice only while per-folder sort is switched on; everything else —
 * the timeline, favourites, search results, collections — always follows the library-wide
 * default from Settings. Turning the toggle off leaves the stored overrides untouched rather
 * than discarding them, so flipping it back restores what each folder was set to.
 */
fun resolveMediaSort(
    bucketId: Long?,
    defaultSort: SortOrder,
    folderOverrides: Map<Long, SortOrder>,
    perFolderSortEnabled: Boolean,
): SortOrder {
    if (!perFolderSortEnabled || bucketId == null) return defaultSort
    return folderOverrides[bucketId] ?: defaultSort
}

/**
 * Encodes the per-folder sort overrides for DataStore, which stores sets of strings.
 *
 * Kept as `bucketId:SORT_NAME` pairs rather than serialised JSON so a preference written by a
 * newer version stays readable, and so a single unparseable entry costs one folder's override
 * instead of all of them (see [decodeFolderSortOverrides]).
 */
fun encodeFolderSortOverrides(overrides: Map<Long, SortOrder>): Set<String> =
    overrides.mapTo(linkedSetOf()) { (bucketId, order) -> "$bucketId:${order.name}" }

/** Reads back [encodeFolderSortOverrides], dropping entries this version cannot understand. */
fun decodeFolderSortOverrides(raw: Set<String>): Map<Long, SortOrder> =
    raw.mapNotNull { entry ->
        val bucketId = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val order = SortOrder.entries.firstOrNull { it.name == entry.substringAfter(':', "") }
            ?: return@mapNotNull null
        bucketId to order
    }.toMap()

fun encodeAlbumGroupOverrides(overrides: Map<Long, String>): Set<String> =
    overrides.mapTo(linkedSetOf()) { (albumId, groupName) -> "$albumId:$groupName" }

fun decodeAlbumGroupOverrides(raw: Set<String>): Map<Long, String> =
    raw.mapNotNull { entry ->
        val albumId = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val groupName = entry.substringAfter(':', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        albumId to groupName
    }.toMap()

/**
 * Grid layout density (number of columns) matching Samsung One UI 4-tier grid.
 */
@Serializable
enum class GridDensity(val columns: Int) {
    YEAR(8),
    MONTH(5),
    NORMAL(3),
    EXPANDED(1);

    companion object {
        val COMPACT get() = MONTH
        val LARGE get() = EXPANDED
    }
}

internal fun GridDensity.zoomIn(): GridDensity = when (this) {
    GridDensity.YEAR -> GridDensity.MONTH
    GridDensity.MONTH -> GridDensity.NORMAL
    GridDensity.NORMAL -> GridDensity.EXPANDED
    GridDensity.EXPANDED -> GridDensity.EXPANDED
}

internal fun GridDensity.zoomOut(): GridDensity = when (this) {
    GridDensity.EXPANDED -> GridDensity.NORMAL
    GridDensity.NORMAL -> GridDensity.MONTH
    GridDensity.MONTH -> GridDensity.YEAR
    GridDensity.YEAR -> GridDensity.YEAR
}

@Serializable
enum class AlbumGridDensity(val columns: Int) {
    LIST(1),
    LARGE(2),
    NORMAL(3),
    COMPACT(4),
}

internal fun AlbumGridDensity.zoomIn(): AlbumGridDensity = when (this) {
    AlbumGridDensity.COMPACT -> AlbumGridDensity.NORMAL
    AlbumGridDensity.NORMAL -> AlbumGridDensity.LARGE
    AlbumGridDensity.LARGE -> AlbumGridDensity.LIST
    AlbumGridDensity.LIST -> AlbumGridDensity.LIST
}

internal fun AlbumGridDensity.zoomOut(): AlbumGridDensity = when (this) {
    AlbumGridDensity.LIST -> AlbumGridDensity.LARGE
    AlbumGridDensity.LARGE -> AlbumGridDensity.NORMAL
    AlbumGridDensity.NORMAL -> AlbumGridDensity.COMPACT
    AlbumGridDensity.COMPACT -> AlbumGridDensity.COMPACT
}
