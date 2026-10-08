package com.pandagallery.app.domain.model

data class MediaSearchCriteria(
    val query: String = "",
    val mediaKind: SearchMediaKind = SearchMediaKind.ALL,
    val minimumBytes: Long? = null,
    val maximumBytes: Long? = null,
    val favoriteOnly: Boolean = false,
    val takenAfter: Long? = null,
    val takenBefore: Long? = null,
    val albumPath: String? = null,
) {
    val hasActiveFilters: Boolean
        get() = mediaKind != SearchMediaKind.ALL || minimumBytes != null || maximumBytes != null ||
            favoriteOnly || takenAfter != null || takenBefore != null || albumPath != null

    internal fun matches(media: SearchableMedia): Boolean {
        val normalizedQuery = query.trim()
        val isLocationQuery = normalizedQuery.equals("location", ignoreCase = true) || normalizedQuery.equals("locations", ignoreCase = true)
        // The literal path stays as a fallback so videos and not-yet-indexed photos remain
        // findable by name; anything the on-device index matched arrives as smartMatch.
        if (normalizedQuery.isNotEmpty() && !media.smartMatch &&
            (!isLocationQuery || !media.hasLocation) &&
            listOfNotNull(media.displayName, media.bucketName, media.relativePath)
                .none { it.contains(normalizedQuery, ignoreCase = true) }
        ) return false
        if (mediaKind == SearchMediaKind.IMAGES && !media.mimeType.startsWith("image/")) return false
        if (mediaKind == SearchMediaKind.VIDEOS && !media.mimeType.startsWith("video/")) return false
        if (mediaKind == SearchMediaKind.GIFS && media.mimeType != "image/gif") return false
        if (minimumBytes != null && media.size < minimumBytes) return false
        if (maximumBytes != null && media.size > maximumBytes) return false
        if (favoriteOnly && !media.isFavorite) return false
        if (takenAfter != null && media.sortDate < takenAfter) return false
        if (takenBefore != null && media.sortDate > takenBefore) return false
        if (albumPath != null && media.relativePath?.trim('/') != albumPath.trim('/')) return false
        return true
    }
}

enum class SearchMediaKind { ALL, IMAGES, VIDEOS, GIFS }

internal data class SearchableMedia(
    val displayName: String,
    val bucketName: String?,
    val relativePath: String?,
    val mimeType: String,
    val size: Long,
    val sortDate: Long,
    val isFavorite: Boolean,
    val hasLocation: Boolean = false,
    /** True when the on-device full-text index matched this photo for the current query. */
    val smartMatch: Boolean = false,
)

internal fun filterMediaForSearch(
    items: List<MediaItem>,
    criteria: MediaSearchCriteria,
    smartMatchIds: Set<Long> = emptySet(),
): List<MediaItem> =
    items.filter { item ->
        val hasLocation = item.isImage && (
            item.bucketName?.contains("Camera", ignoreCase = true) == true ||
            item.bucketName?.contains("DCIM", ignoreCase = true) == true ||
            item.relativePath?.contains("Camera", ignoreCase = true) == true ||
            item.relativePath?.contains("DCIM", ignoreCase = true) == true
        )
        criteria.matches(
            SearchableMedia(
                displayName = item.displayName,
                bucketName = item.bucketName,
                relativePath = item.relativePath,
                mimeType = item.mimeType,
                size = item.size,
                sortDate = item.sortDate,
                isFavorite = item.isFavorite,
                hasLocation = hasLocation,
                smartMatch = item.id in smartMatchIds,
            )
        )
    }

internal fun filterCollection(items: List<MediaItem>, collection: SmartCollection): List<MediaItem> = when (collection) {
    SmartCollection.SCREENSHOTS -> items.filter { it.bucketName?.contains("screenshot", true) == true || it.relativePath?.contains("screenshot", true) == true }
    SmartCollection.GIFS -> items.filter { it.mediaType == MediaType.GIF }
    SmartCollection.DOCUMENTS -> items.filter { item ->
        val path = item.relativePath.orEmpty()
        path.contains("document", true) || path.contains("receipt", true) || item.displayName.contains("scan", true)
    }
    SmartCollection.DOWNLOADS -> items.filter { it.relativePath?.contains("download", true) == true || it.bucketName?.contains("download", true) == true }
    SmartCollection.LARGE_FILES -> items.filter { it.size >= 50L * 1024L * 1024L }
    SmartCollection.RECENTLY_ADDED -> {
        val cutoffSeconds = System.currentTimeMillis() / 1000L - 30L * 24L * 60L * 60L
        items.filter { it.dateAdded >= cutoffSeconds }
    }
    SmartCollection.LOCATIONS -> items.filter { item ->
        item.isImage && (
            item.bucketName?.contains("Camera", ignoreCase = true) == true ||
            item.bucketName?.contains("DCIM", ignoreCase = true) == true ||
            item.relativePath?.contains("Camera", ignoreCase = true) == true ||
            item.relativePath?.contains("DCIM", ignoreCase = true) == true
        )
    }
}

@kotlinx.serialization.Serializable
enum class SmartCollection { SCREENSHOTS, GIFS, DOCUMENTS, DOWNLOADS, LARGE_FILES, RECENTLY_ADDED, LOCATIONS }
