package com.pandagallery.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import com.pandagallery.app.domain.model.SmartCollection
import com.pandagallery.app.data.cleanup.CleanupCategory
import com.pandagallery.app.data.smart.SmartGroupType

/**
 * Top-level navigation destinations for the bottom nav bar.
 */
enum class TopLevelDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    PICTURES(
        label = "Pictures",
        selectedIcon = Icons.Filled.Image,
        unselectedIcon = Icons.Outlined.Image,
    ),
    ALBUMS(
        label = "Albums",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
    ),
    COLLECTIONS(
        label = "Stories",
        selectedIcon = Icons.Filled.CollectionsBookmark,
        unselectedIcon = Icons.Outlined.CollectionsBookmark,
    ),
    MENU(
        label = "Menu",
        selectedIcon = Icons.Filled.Menu,
        unselectedIcon = Icons.Outlined.Menu,
    ),
}

// Type-safe navigation routes
@Serializable object PicturesRoute
@Serializable object AlbumsRoute
@Serializable object CollectionsRoute
@Serializable data class AlbumDetailRoute(val bucketId: Long, val albumName: String)
@Serializable data class ViewerRoute(val mediaId: Long, val sourceRoute: String = "photos")

/**
 * The viewer opened on a URI another app handed over that MediaStore has no row for, so
 * there is no id to route on and no library the item can be paged through.
 */
@Serializable data class ExternalViewerRoute(val uri: String)
@Serializable object FavoritesRoute
@Serializable object VideosRoute
@Serializable object RecentRoute
@Serializable object TrashRoute
@Serializable object SearchRoute
@Serializable data class SmartCollectionRoute(val collection: SmartCollection)
@Serializable object CleanupRoute
@Serializable object SmartOrganizerRoute
@Serializable object PeopleRoute
/**
 * A smart group's photos. [startInSelection] opens the grid already in selection mode, which is
 * how "Remove photos" and "Change cover" on the People screen get the user straight to picking
 * the photos they mean instead of leaving them to find the select toggle.
 */
@Serializable data class SmartGroupRoute(
    val type: SmartGroupType,
    val key: String,
    val title: String,
    val startInSelection: Boolean = false,
)
@Serializable data class CleanupReviewRoute(val category: CleanupCategory)
@Serializable data class EditorRoute(val mediaId: Long = -1L, val uriString: String? = null)
@Serializable data class CollageRoute(val mediaIds: List<Long> = emptyList())
@Serializable data class GifMakerRoute(val mediaIds: List<Long> = emptyList())
@Serializable object PrivateVaultRoute
@Serializable object SettingsRoute
@Serializable object CompressionManagerRoute
@Serializable object CompressionHistoryRoute
@Serializable data class CompressionStudioRoute(val mediaId: Long)
@Serializable object CoverViewerRoute
@Serializable object StoryCreatorRoute
