package com.pandagallery.app.ui.search

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.pandagallery.app.domain.model.MediaItem

/**
 * Shot types representing Samsung Gallery 15.9 search category chips.
 */
enum class SearchShotType(
    val title: String,
    val emoji: String,
    val icon: ImageVector,
) {
    VIDEOS("Videos", "🎬", Icons.Outlined.Videocam),
    PORTRAITS("Portraits", "📷", Icons.Outlined.CameraAlt),
    RAW("RAW", "📸", Icons.Outlined.PhotoCamera),
    SLOW_MO("Slow-Mo", "⏱️", Icons.Outlined.SlowMotionVideo),
    MOTION_PHOTOS("Motion Photos", "💫", Icons.Outlined.MotionPhotosOn),
    SCREENSHOTS("Screenshots", "📱", Icons.Outlined.CropFree),
    FAVORITES("Favorites", "❤️", Icons.Outlined.Favorite);

    fun matches(item: MediaItem): Boolean = when (this) {
        VIDEOS -> item.isVideo
        PORTRAITS -> item.isImage && (
            item.displayName.contains("portrait", ignoreCase = true) ||
            item.relativePath?.contains("portrait", ignoreCase = true) == true ||
            item.bucketName?.contains("portrait", ignoreCase = true) == true
        )
        RAW -> item.mimeType.contains("dng", ignoreCase = true) ||
            item.mimeType.contains("raw", ignoreCase = true) ||
            item.displayName.endsWith(".dng", ignoreCase = true) ||
            item.displayName.endsWith(".cr2", ignoreCase = true) ||
            item.displayName.endsWith(".arw", ignoreCase = true) ||
            item.displayName.endsWith(".nef", ignoreCase = true)
        SLOW_MO -> item.displayName.contains("slow", ignoreCase = true) ||
            item.displayName.contains("slomo", ignoreCase = true) ||
            item.relativePath?.contains("slow", ignoreCase = true) == true
        MOTION_PHOTOS -> item.displayName.contains("motion", ignoreCase = true) ||
            item.relativePath?.contains("motion", ignoreCase = true) == true
        SCREENSHOTS -> item.bucketName?.contains("screenshot", ignoreCase = true) == true ||
            item.relativePath?.contains("screenshot", ignoreCase = true) == true ||
            item.displayName.contains("screenshot", ignoreCase = true)
        FAVORITES -> item.isFavorite
    }
}

/**
 * AI-detected offline scene & object tags with friendly emoji icons.
 */
data class SearchSceneCategory(
    val label: String,
    val emoji: String,
    val count: Int,
)

/**
 * Summarized location for the Locations section on the Search Hub.
 */
data class SearchLocationSummary(
    val name: String,
    val count: Int,
    val lat: Double,
    val lng: Double,
)

/**
 * Person or Pet cluster representation for the People & Pets carousel.
 */
data class SearchPersonSummary(
    val key: String,
    val name: String?,
    val fallbackTitle: String,
    val photoCount: Int,
    val faceUri: Uri?,
    val isPet: Boolean = false,
    val petEmoji: String? = null,
) {
    val displayName: String get() = name ?: fallbackTitle
}

/**
 * Complete UI state for the Samsung One UI Search Hub screen.
 */
data class SearchHubUiState(
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val peopleAndPets: List<SearchPersonSummary> = emptyList(),
    val shotTypes: List<SearchShotType> = SearchShotType.values().toList(),
    val topLocations: List<SearchLocationSummary> = emptyList(),
    val totalGpsClusters: Int = 0,
    val sceneTags: List<SearchSceneCategory> = emptyList(),
    val selectedShotType: SearchShotType? = null,
    val searchResults: List<MediaItem> = emptyList(),
    val isSearching: Boolean = false,
    val selectedMediaIds: Set<Long> = emptySet(),
) {
    val isSelectionMode: Boolean get() = selectedMediaIds.isNotEmpty()
    val isHubEmptyQuery: Boolean get() = query.isBlank() && selectedShotType == null
}
