package com.pandagallery.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pandagallery.app.ui.albums.AlbumsScreen
import com.pandagallery.app.ui.collections.CollectionsScreen
import com.pandagallery.app.ui.collage.CollageScreen
import com.pandagallery.app.ui.gif.GifMakerScreen
import com.pandagallery.app.ui.compression.CompressionHistoryScreen
import com.pandagallery.app.ui.compression.CompressionManagerScreen
import com.pandagallery.app.ui.compression.CompressionStudioScreen
import com.pandagallery.app.domain.model.SmartCollection
import com.pandagallery.app.ui.photos.MediaFilter
import com.pandagallery.app.ui.photos.PhotosScreen
import com.pandagallery.app.ui.settings.SettingsScreen
import com.pandagallery.app.ui.viewer.ViewerScreen
import com.pandagallery.app.ui.vault.PrivateVaultScreen
import com.pandagallery.app.ui.cleanup.CleanupScreen
import com.pandagallery.app.ui.editor.EditorScreen
import com.pandagallery.app.ui.organize.SmartOrganizerScreen
import com.pandagallery.app.ui.people.PeopleScreen
import com.pandagallery.app.ui.cover.CoverViewerScreen
import com.pandagallery.app.ui.cover.CoverViewerViewModel
import com.pandagallery.app.ui.story.StoryScreen
import com.pandagallery.app.ui.story.StoryViewModel
import com.pandagallery.app.ui.search.SearchScreen
import com.pandagallery.app.ui.search.SearchViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

@Composable
fun PandaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = PicturesRoute,
    onTopLevelContextBarChanged: (Boolean) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(tween(110)) + slideInHorizontally(tween(170)) { it / 4 }
        },
        exitTransition = {
            fadeOut(tween(110))
        },
        popEnterTransition = {
            fadeIn(tween(110))
        },
        popExitTransition = {
            fadeOut(tween(110)) + slideOutHorizontally(tween(170)) { it / 4 }
        },
    ) {
        // ============================================
        // Bottom Nav Destinations
        // ============================================

        composable<PicturesRoute>(
            enterTransition = { fadeIn(tween(90)) },
            exitTransition = { fadeOut(tween(90)) },
        ) {
            PhotosScreen(
                onMediaClick = { mediaId -> navController.navigate(ViewerRoute(mediaId, sourceRoute = "photos")) },
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateGif = { navController.navigate(GifMakerRoute(it)) },
                mediaFilter = MediaFilter.All,
                title = "Pictures",
                onSearchClick = { navController.navigate(SearchRoute) },
                onSelectionModeChanged = onTopLevelContextBarChanged,
            )
        }

        composable<AlbumsRoute>(
            enterTransition = { fadeIn(tween(90)) },
            exitTransition = { fadeOut(tween(90)) },
        ) {
            AlbumsScreen(
                onAlbumClick = { bucketId, name ->
                    navController.navigate(AlbumDetailRoute(bucketId, name))
                },
                onMenuClick = { navController.navigate(SettingsRoute) },
                onSearchMedia = { navController.navigate(SearchRoute) },
                onCreateCollage = { navController.navigate(CollageRoute()) },
                onVideosClick = { navController.navigate(VideosRoute) },
                onFavoritesClick = { navController.navigate(FavoritesRoute) },
                onSelectionModeChanged = onTopLevelContextBarChanged,
            )
        }

        composable<CollectionsRoute>(
            enterTransition = { fadeIn(tween(90)) },
            exitTransition = { fadeOut(tween(90)) },
        ) {
            CollectionsScreen(
                onFavoritesClick = { navController.navigate(FavoritesRoute) },
                onVideosClick = { navController.navigate(VideosRoute) },
                onRecentClick = { navController.navigate(RecentRoute) },
                onTrashClick = { navController.navigate(TrashRoute) },
                onSmartCollectionClick = { navController.navigate(SmartCollectionRoute(it)) },
                onCleanupClick = { navController.navigate(CleanupRoute) },
                onPeopleClick = { navController.navigate(PeopleRoute) },
                onSmartOrganizerClick = { navController.navigate(SmartOrganizerRoute) },
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateStory = { navController.navigate(StoryCreatorRoute) },
            )
        }

        composable<SettingsRoute>(
            enterTransition = { fadeIn(tween(90)) },
            exitTransition = { fadeOut(tween(90)) },
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPrivateFolderClick = { navController.navigate(PrivateVaultRoute) },
                onCompressionQueueClick = { navController.navigate(CompressionManagerRoute) },
                onCoverViewerClick = { navController.navigate(CoverViewerRoute) },
            )
        }

        composable<CompressionManagerRoute> {
            CompressionManagerScreen(
                onBack = { navController.popBackStack() },
                onHistory = { navController.navigate(CompressionHistoryRoute) },
            )
        }

        composable<CompressionHistoryRoute> {
            CompressionHistoryScreen(onBack = { navController.popBackStack() })
        }

        composable<CompressionStudioRoute>(
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(tween(180))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(160))
            },
            popEnterTransition = {
                fadeIn(tween(150))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(160))
            },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<CompressionStudioRoute>()
            CompressionStudioScreen(
                mediaId = route.mediaId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<CoverViewerRoute> {
            val viewModel: CoverViewerViewModel = hiltViewModel()
            val mediaList by viewModel.mediaList.collectAsStateWithLifecycle()
            val coverState by viewModel.coverState.collectAsStateWithLifecycle()
            CoverViewerScreen(
                mediaList = mediaList,
                onClose = { navController.popBackStack() },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onCoverActiveChanged = { viewModel.setCoverActive(it) }
            )
        }

        composable<StoryCreatorRoute> {
            val viewModel: StoryViewModel = hiltViewModel()
            StoryScreen(
                storyEngine = viewModel.storyEngine,
                mediaDao = viewModel.mediaDao,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCreateCollage = { ids -> navController.navigate(CollageRoute(ids)) },
            )
        }

        // ============================================
        // Detail Screens
        // ============================================

        composable<ViewerRoute>(
            enterTransition = {
                scaleIn(
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f,
                    ),
                    initialScale = 0.88f,
                ) + fadeIn(tween(150, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                scaleOut(
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f,
                    ),
                    targetScale = 0.88f,
                ) + fadeOut(tween(140, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                fadeIn(tween(150))
            },
            popExitTransition = {
                scaleOut(
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f,
                    ),
                    targetScale = 0.88f,
                ) + fadeOut(tween(140, easing = FastOutSlowInEasing))
            },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ViewerRoute>()
            ViewerScreen(
                mediaId = route.mediaId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(EditorRoute(it)) },
                onOpenCompressionStudio = { navController.navigate(CompressionStudioRoute(it)) },
            )
        }

        composable<ExternalViewerRoute>(
            enterTransition = {
                scaleIn(
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f,
                    ),
                    initialScale = 0.88f,
                ) + fadeIn(tween(150, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                scaleOut(
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f,
                    ),
                    targetScale = 0.88f,
                ) + fadeOut(tween(140, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                fadeIn(tween(150))
            },
            popExitTransition = {
                scaleOut(
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f,
                    ),
                    targetScale = 0.88f,
                ) + fadeOut(tween(140, easing = FastOutSlowInEasing))
            },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ExternalViewerRoute>()
            ViewerScreen(
                mediaId = null,
                externalUri = route.uri,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(EditorRoute(mediaId = -1L, uriString = route.uri)) },
                onOpenCompressionStudio = { navController.navigate(CompressionStudioRoute(it)) },
            )
        }

        composable<EditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditorRoute>()
            EditorScreen(
                mediaId = route.mediaId,
                uriString = route.uriString,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable<CollageRoute> {
            CollageScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable<GifMakerRoute> {
            GifMakerScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable<AlbumDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AlbumDetailRoute>()
            PhotosScreen(
                onMediaClick = { mediaId ->
                    navController.navigate(ViewerRoute(mediaId))
                },
                mediaFilter = MediaFilter.Bucket(route.bucketId),
                title = route.albumName,
                onBack = { navController.popBackStack() },
                onSearchClick = { navController.navigate(SearchRoute) },
                enableSwipeToAlbums = true,
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateGif = { navController.navigate(GifMakerRoute(it)) },
            )
        }

        composable<FavoritesRoute> {
            PhotosScreen(
                onMediaClick = { mediaId ->
                    navController.navigate(ViewerRoute(mediaId))
                },
                mediaFilter = MediaFilter.Favorites,
                title = "Favorites",
                onBack = { navController.popBackStack() },
                onSearchClick = { navController.navigate(SearchRoute) },
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateGif = { navController.navigate(GifMakerRoute(it)) },
            )
        }

        composable<VideosRoute> {
            PhotosScreen(
                onMediaClick = { mediaId -> navController.navigate(ViewerRoute(mediaId)) },
                mediaFilter = MediaFilter.Videos,
                title = "Videos",
                onBack = { navController.popBackStack() },
                onSearchClick = { navController.navigate(SearchRoute) },
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateGif = { navController.navigate(GifMakerRoute(it)) },
            )
        }

        composable<RecentRoute> {
            PhotosScreen(
                onMediaClick = { mediaId -> navController.navigate(ViewerRoute(mediaId)) },
                mediaFilter = MediaFilter.Collection(SmartCollection.RECENTLY_ADDED),
                title = "Recent",
                onBack = { navController.popBackStack() },
                onSearchClick = { navController.navigate(SearchRoute) },
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateGif = { navController.navigate(GifMakerRoute(it)) },
            )
        }

        composable<TrashRoute> {
            PhotosScreen(
                onMediaClick = { mediaId ->
                    navController.navigate(ViewerRoute(mediaId))
                },
                mediaFilter = MediaFilter.Trash,
                title = "Trash",
                onBack = { navController.popBackStack() },
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateGif = { navController.navigate(GifMakerRoute(it)) },
            )
        }

        composable<SearchRoute> {
            val searchViewModel: SearchViewModel = hiltViewModel()
            SearchScreen(
                viewModel = searchViewModel,
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaId -> navController.navigate(ViewerRoute(mediaId)) },
                onOpenPeople = { navController.navigate(PeopleRoute) },
                onOpenMapView = {
                    navController.navigate(SmartCollectionRoute(SmartCollection.LOCATIONS))
                },
            )
        }

        composable<SmartCollectionRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SmartCollectionRoute>()
            PhotosScreen(
                onMediaClick = { mediaId -> navController.navigate(ViewerRoute(mediaId)) },
                mediaFilter = MediaFilter.Collection(route.collection),
                title = route.collection.collectionTitle(),
                onBack = { navController.popBackStack() },
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateGif = { navController.navigate(GifMakerRoute(it)) },
            )
        }

        composable<CleanupRoute> {
            CleanupScreen(
                onBack = { navController.popBackStack() },
                onReview = { navController.navigate(CleanupReviewRoute(it)) },
            )
        }

        composable<SmartOrganizerRoute> {
            SmartOrganizerScreen(
                onBack = { navController.popBackStack() },
                onOpenGroup = { type, key, title -> navController.navigate(SmartGroupRoute(type, key, title)) },
                onOpenPeople = { navController.navigate(PeopleRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }

        composable<PeopleRoute> {
            PeopleScreen(
                onBack = { navController.popBackStack() },
                onOpenPerson = { type, key, title, startInSelection ->
                    navController.navigate(SmartGroupRoute(type, key, title, startInSelection))
                },
            )
        }

        composable<SmartGroupRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SmartGroupRoute>()
            PhotosScreen(
                onMediaClick = { mediaId -> navController.navigate(ViewerRoute(mediaId)) },
                mediaFilter = MediaFilter.SmartGroup(route.type, route.key),
                title = route.title,
                onBack = { navController.popBackStack() },
                startInSelectionMode = route.startInSelection,
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateGif = { navController.navigate(GifMakerRoute(it)) },
            )
        }

        composable<CleanupReviewRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<CleanupReviewRoute>()
            PhotosScreen(
                onMediaClick = { mediaId -> navController.navigate(ViewerRoute(mediaId)) },
                mediaFilter = MediaFilter.Cleanup(route.category),
                title = route.category.cleanupTitle(),
                onBack = { navController.popBackStack() },
                onCreateCollage = { navController.navigate(CollageRoute(it)) },
                onCreateGif = { navController.navigate(GifMakerRoute(it)) },
            )
        }

        composable<PrivateVaultRoute> {
            PrivateVaultScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun com.pandagallery.app.domain.model.SmartCollection.collectionTitle(): String = when (this) {
    com.pandagallery.app.domain.model.SmartCollection.SCREENSHOTS -> "Screenshots"
    com.pandagallery.app.domain.model.SmartCollection.GIFS -> "GIFs"
    com.pandagallery.app.domain.model.SmartCollection.DOCUMENTS -> "Documents"
    com.pandagallery.app.domain.model.SmartCollection.DOWNLOADS -> "Downloads"
    com.pandagallery.app.domain.model.SmartCollection.LARGE_FILES -> "Large files"
    com.pandagallery.app.domain.model.SmartCollection.RECENTLY_ADDED -> "Recently added"
    com.pandagallery.app.domain.model.SmartCollection.LOCATIONS -> "Locations"
}

private fun com.pandagallery.app.data.cleanup.CleanupCategory.cleanupTitle(): String = when (this) {
    com.pandagallery.app.data.cleanup.CleanupCategory.EXACT_DUPLICATES -> "Exact duplicates"
    com.pandagallery.app.data.cleanup.CleanupCategory.SIMILAR -> "Similar photos"
    com.pandagallery.app.data.cleanup.CleanupCategory.BURSTS -> "Burst sequences"
    com.pandagallery.app.data.cleanup.CleanupCategory.BLURRY -> "Possibly blurry"
    com.pandagallery.app.data.cleanup.CleanupCategory.SCREENSHOTS -> "Screenshots"
    com.pandagallery.app.data.cleanup.CleanupCategory.DOCUMENTS -> "Documents"
    com.pandagallery.app.data.cleanup.CleanupCategory.LARGE_FILES -> "Large files"
    com.pandagallery.app.data.cleanup.CleanupCategory.COMPRESSED_BACKED_UP -> "Compressed & backed up"
}
