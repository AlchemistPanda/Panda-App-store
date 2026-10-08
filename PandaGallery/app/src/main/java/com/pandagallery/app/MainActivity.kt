package com.pandagallery.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.isSystemInDarkTheme
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.data.compression.ThermalThrottlingMonitor
import com.pandagallery.app.data.compression.ThermalState
import com.pandagallery.app.data.compression.toDomain
import com.pandagallery.app.data.media.IncomingMediaResolver
import com.pandagallery.app.domain.compression.CompressionTask
import com.pandagallery.app.domain.compression.CompressionQueueProgress
import com.pandagallery.app.domain.model.ThemeMode
import com.pandagallery.app.domain.model.UserPreferences
import com.pandagallery.app.domain.model.CompressionStatusPlacement
import com.pandagallery.app.ui.components.LocalHazeState
import com.pandagallery.app.service.EdgePanelService
import com.pandagallery.app.ui.components.PandaModalBottomSheet
import com.pandagallery.app.ui.components.oneUiGlass
import com.pandagallery.app.ui.components.pandaGlass
import com.pandagallery.app.ui.components.pandaGlassStyle
import com.pandagallery.app.ui.compression.components.BatchQueueBottomSheet
import com.pandagallery.app.ui.navigation.*
import com.pandagallery.app.ui.theme.PandaGalleryTheme
import com.pandagallery.app.ui.update.StartupUpdatePrompt
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    @Inject
    lateinit var preferencesDataSource: PreferencesDataSource

    @Inject
    lateinit var compressionQueueRepository: CompressionQueueRepository

    @Inject
    lateinit var thermalThrottlingMonitor: ThermalThrottlingMonitor

    @Inject
    lateinit var incomingMediaResolver: IncomingMediaResolver

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val hasImages = permissions[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                    permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            val hasVisualUserSelected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    permissions[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
            if (hasImages || hasVisualUserSelected) {
                // Permissions granted (full or partial selection), UI will handle refresh
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestPermissions()
        applyLaunchRequest(intent)
        publishShortcuts()

        setContent {
            val preferences by preferencesDataSource.userPreferencesFlow
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            LaunchedEffect(preferences.appIcon) {
                com.pandagallery.app.util.AppIconManager.changeAppIcon(this@MainActivity, preferences.appIcon)
            }

            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            PandaGalleryTheme(
                darkTheme = darkTheme,
                dynamicColor = preferences.useDynamicColors
            ) {
                PandaGalleryMainApp(
                    compressionQueueRepository = compressionQueueRepository,
                    thermalThrottlingMonitor = thermalThrottlingMonitor,
                    userPreferences = preferences,
                    launchRequest = launchRequest,
                    onLaunchRequestConsumed = { launchRequest = null },
                )
                StartupUpdatePrompt()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchRequest(intent)
    }

    /**
     * Maps launcher shortcuts, widget taps and incoming VIEW/EDIT intents from other apps
     * onto a destination. Without the VIEW branch the manifest advertises PandaGallery as a
     * photo viewer but drops the URI and lands on the album list.
     *
     * Handing-over apps rarely pass a MediaStore URI — WhatsApp serves attachments from its
     * own provider — so the URI is matched back to its gallery row off the main thread, and
     * shown on its own when it has no row to match.
     */
    private fun applyLaunchRequest(intent: Intent) {
        val targetRoute = intent.getStringExtra("target_route")
        launchRequest = when {
            targetRoute == "favorites" || intent.action == EdgePanelService.ACTION_EDGE_OPEN_FAVORITES -> LaunchRequest.Favorites
            targetRoute == "stories" || intent.action == EdgePanelService.ACTION_EDGE_OPEN_STORIES -> LaunchRequest.Stories
            intent.action == ACTION_SEARCH -> LaunchRequest.Search
            intent.action == ACTION_PRIVATE -> LaunchRequest.PrivateVault
            intent.action == ACTION_CLEANUP -> LaunchRequest.Cleanup
            intent.action == ACTION_MEMORIES -> LaunchRequest.Memories
            intent.action == ACTION_COMPRESSION -> LaunchRequest.Compression
            intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT -> {
                val uri = intent.incomingMediaUri()
                if (uri == null) {
                    LaunchRequest.Unsupported()
                } else {
                    resolveIncomingMedia(uri, editing = intent.action == Intent.ACTION_EDIT)
                    // Resolution touches MediaStore, so the destination arrives moments later.
                    null
                }
            }
            else -> null
        }
    }

    private fun resolveIncomingMedia(uri: android.net.Uri, editing: Boolean) {
        lifecycleScope.launch {
            launchRequest = when (val resolution = incomingMediaResolver.resolve(uri)) {
                is IncomingMediaResolver.Resolution.InGallery ->
                    if (editing) {
                        LaunchRequest.EditMedia(resolution.mediaId)
                    } else {
                        LaunchRequest.ViewMedia(resolution.mediaId)
                    }
                // The editor writes back through MediaStore, so an item with no row there can
                // still be viewed, just not edited.
                is IncomingMediaResolver.Resolution.External ->
                    LaunchRequest.ViewExternalMedia(resolution.uri.toString())
                IncomingMediaResolver.Resolution.Unreadable ->
                    LaunchRequest.Unsupported("Couldn't open this file — the app that shared it may have removed it")
            }
        }
    }

    /**
     * The URI to open. Most apps set the intent data; some attach it as clip data instead,
     * and a few send it as a stream extra even for VIEW.
     */
    private fun Intent.incomingMediaUri(): android.net.Uri? = data
        ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        ?: @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM)

    private fun publishShortcuts() {
        val manager = getSystemService(ShortcutManager::class.java)
        manager.dynamicShortcuts = listOf(
            shortcut("search", "Search photos", ACTION_SEARCH, R.drawable.ic_shortcut_search),
            shortcut("private", "Private folder", ACTION_PRIVATE, R.drawable.ic_shortcut_lock),
            shortcut("cleanup", "Panda Sweep", ACTION_CLEANUP, R.drawable.ic_shortcut_cleanup),
        )
    }

    private fun shortcut(id: String, label: String, action: String, iconRes: Int): ShortcutInfo =
        ShortcutInfo.Builder(this, id)
            .setShortLabel(label)
            .setIcon(Icon.createWithResource(this, iconRes))
            .setIntent(Intent(this, MainActivity::class.java).setAction(action))
            .build()

    companion object {
        const val ACTION_SEARCH = "com.pandagallery.app.SEARCH"
        const val ACTION_PRIVATE = "com.pandagallery.app.PRIVATE"
        const val ACTION_CLEANUP = "com.pandagallery.app.CLEANUP"
        const val ACTION_MEMORIES = "com.pandagallery.app.MEMORIES"
        const val ACTION_COMPRESSION = "com.pandagallery.app.COMPRESSION"
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasImages = checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasVideos = checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasVisualUserSelected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

            if (!hasImages && !hasVisualUserSelected) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            }
            if (!hasVideos && !hasVisualUserSelected) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            requestPermissions.launch(permissions.toTypedArray())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Menu sheet item model
// ─────────────────────────────────────────────────────────────────────────────
private data class MenuSheetItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** A destination requested from outside the app — a shortcut, the widget, or another app. */
sealed interface LaunchRequest {
    data object Search : LaunchRequest
    data object PrivateVault : LaunchRequest
    data object Cleanup : LaunchRequest
    data object Memories : LaunchRequest
    data object Compression : LaunchRequest
    data object Favorites : LaunchRequest
    data object Stories : LaunchRequest
    data class ViewMedia(val mediaId: Long) : LaunchRequest
    data class EditMedia(val mediaId: Long) : LaunchRequest

    /** A readable image or video that MediaStore has no row for — a chat cache, say. */
    data class ViewExternalMedia(val uri: String) : LaunchRequest
    data class Unsupported(
        val reason: String = "Couldn't open this file",
    ) : LaunchRequest
}

// ─────────────────────────────────────────────────────────────────────────────
// Root app composable
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PandaGalleryMainApp(
    compressionQueueRepository: CompressionQueueRepository,
    thermalThrottlingMonitor: ThermalThrottlingMonitor,
    userPreferences: UserPreferences = UserPreferences(),
    launchRequest: LaunchRequest? = null,
    onLaunchRequestConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(launchRequest) {
        val request = launchRequest ?: return@LaunchedEffect
        val route: Any? = when (request) {
            LaunchRequest.Search -> SearchRoute
            LaunchRequest.PrivateVault -> PrivateVaultRoute
            LaunchRequest.Cleanup -> CleanupRoute
            LaunchRequest.Memories -> SmartOrganizerRoute
            LaunchRequest.Compression -> CompressionManagerRoute
            LaunchRequest.Favorites -> FavoritesRoute
            LaunchRequest.Stories -> CollectionsRoute
            is LaunchRequest.ViewMedia -> ViewerRoute(request.mediaId)
            is LaunchRequest.EditMedia -> EditorRoute(request.mediaId)
            is LaunchRequest.ViewExternalMedia -> ExternalViewerRoute(request.uri)
            is LaunchRequest.Unsupported -> null
        }
        if (route != null) {
            navController.navigate(route) { launchSingleTop = true }
        } else if (request is LaunchRequest.Unsupported) {
            android.widget.Toast
                .makeText(context, request.reason, android.widget.Toast.LENGTH_LONG)
                .show()
        }
        onLaunchRequestConsumed()
    }
    val pendingOriginalDeletions by compressionQueueRepository.pendingOriginalDeletions
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val compressionProgress by compressionQueueRepository.queueProgress
        .collectAsStateWithLifecycle(initialValue = null)
    // The whole current batch, finished rows included: the sheet's "k of N processed" and
    // bytes-saved line used to be fed only the still-active rows, so it read "0 of N" forever
    // while N shrank as tasks finished.
    val batchTasksFlow = remember(compressionQueueRepository) {
        compressionQueueRepository.batchTasks.map { rows -> rows.map { it.toDomain() } }
    }
    val batchTasks by batchTasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val thermalState by thermalThrottlingMonitor.thermalState
        .collectAsStateWithLifecycle()
    var showBatchQueueSheet by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        scope.launch {
            compressionQueueRepository.resumeAfterBootIfNeeded()
        }
        onPauseOrDispose { }
    }

    var launchedDeleteIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeMoveOperation by remember { mutableStateOf<List<CompressionTask>>(emptyList()) }
    val originalDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        val ids = if (approved) launchedDeleteIds else activeMoveOperation.map { it.id }
        scope.launch {
            compressionQueueRepository.completeOriginalDeletion(ids, approved)
            launchedDeleteIds = emptyList()
            activeMoveOperation = emptyList()
        }
    }

    LifecycleResumeEffect(pendingOriginalDeletions, launchedDeleteIds) {
        if (launchedDeleteIds.isNotEmpty()) {
            return@LifecycleResumeEffect onPauseOrDispose { }
        }
        val operation = pendingOriginalDeletions
            .groupBy { it.operationId }
            .values
            .firstOrNull()
            .orEmpty()
        if (operation.isNotEmpty()) {
            val chunk = operation.take(100)
            try {
                val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    MediaStore.createTrashRequest(
                        context.contentResolver,
                        chunk.map { android.net.Uri.parse(it.sourceUri) },
                        true,
                    )
                } else {
                    MediaStore.createDeleteRequest(
                        context.contentResolver,
                        chunk.map { android.net.Uri.parse(it.sourceUri) },
                    )
                }
                activeMoveOperation = operation
                launchedDeleteIds = chunk.map { it.id }
                originalDeleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                // SecurityException / IllegalArgumentException from MediaProvider, or the
                // confirmation activity failing to launch.
                com.pandagallery.app.data.diagnostics.CrashLog.e("MainActivity", "Delete-originals request failed", e)
                scope.launch {
                    compressionQueueRepository.completeOriginalDeletion(
                        operation.map { it.id },
                        approved = false,
                    )
                }
            }
        }
        onPauseOrDispose { }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Controls the Samsung-style menu bottom sheet
    var showMenuSheet by remember { mutableStateOf(false) }
    var hasTopLevelContextBar by remember { mutableStateOf(false) }

    val showBottomBar = remember(currentDestination, hasTopLevelContextBar, userPreferences.showPicturesTab) {
        !hasTopLevelContextBar && (currentDestination?.let { dest ->
            (userPreferences.showPicturesTab && dest.hasRoute<PicturesRoute>()) ||
            dest.hasRoute<AlbumsRoute>() ||
            dest.hasRoute<CollectionsRoute>()
        } ?: true)
    }

    val dynamicStartDestination = remember(userPreferences.showPicturesTab) {
        if (userPreferences.showPicturesTab) PicturesRoute else AlbumsRoute
    }

    BackHandler(enabled = !userPreferences.showPicturesTab && currentDestination?.hasRoute<AlbumsRoute>() == true) {
        (context as? Activity)?.finish()
    }

    LaunchedEffect(userPreferences.showPicturesTab) {
        if (!userPreferences.showPicturesTab && currentDestination?.hasRoute<PicturesRoute>() == true) {
            navController.navigate(AlbumsRoute) {
                popUpTo(PicturesRoute) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    val hazeState = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides hazeState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PandaNavHost(
            navController = navController,
            startDestination = dynamicStartDestination,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            onTopLevelContextBarChanged = { hasTopLevelContextBar = it },
        )
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(animationSpec = tween(140)) { it } + fadeIn(animationSpec = tween(140)),
            exit = slideOutVertically(animationSpec = tween(140)) { it } + fadeOut(animationSpec = tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            SamsungBottomPanel(
                currentDestination = currentDestination,
                hazeState = hazeState,
                showPicturesTab = userPreferences.showPicturesTab,
                onDestinationClick = { destination ->
                    when (destination) {
                        TopLevelDestination.MENU -> {
                            // Show bottom sheet — Samsung Gallery style
                            showMenuSheet = true
                        }
                        else -> {
                            val route = when (destination) {
                                TopLevelDestination.PICTURES -> PicturesRoute
                                TopLevelDestination.ALBUMS -> AlbumsRoute
                                TopLevelDestination.COLLECTIONS -> CollectionsRoute
                                else -> return@SamsungBottomPanel
                            }
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
            )
        }
        val activeCompressionProgress = compressionProgress
        var lastNonNullCompressionProgress by remember { mutableStateOf<CompressionQueueProgress?>(null) }
        if (activeCompressionProgress != null) {
            lastNonNullCompressionProgress = activeCompressionProgress
        }
        val progressToDisplay = activeCompressionProgress ?: lastNonNullCompressionProgress

        when (userPreferences.compressionStatusPlacement) {
            CompressionStatusPlacement.ABOVE_BOTTOM_BAR -> {
                val bottomPadding by animateDpAsState(
                    targetValue = if (showBottomBar) 96.dp else 24.dp,
                    animationSpec = tween(180),
                    label = "compression_bottom_padding",
                )
                AnimatedVisibility(
                    visible = activeCompressionProgress != null,
                    enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(220)),
                    exit = slideOutVertically(animationSpec = tween(220)) { it } + fadeOut(animationSpec = tween(220)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = bottomPadding)
                        .fillMaxWidth(),
                ) {
                    if (progressToDisplay != null) {
                        CompressionProgressLine(
                            progress = progressToDisplay,
                            thermalState = thermalState,
                            hazeState = hazeState,
                            onClick = { showBatchQueueSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            CompressionStatusPlacement.BELOW_TOP_BAR -> {
                AnimatedVisibility(
                    visible = activeCompressionProgress != null,
                    enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(animationSpec = tween(220)),
                    exit = slideOutVertically(animationSpec = tween(220)) { -it } + fadeOut(animationSpec = tween(220)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 58.dp)
                        .fillMaxWidth(),
                ) {
                    if (progressToDisplay != null) {
                        CompressionProgressLine(
                            progress = progressToDisplay,
                            thermalState = thermalState,
                            hazeState = hazeState,
                            onClick = { showBatchQueueSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            CompressionStatusPlacement.COMPACT_ISLAND -> {
                AnimatedVisibility(
                    visible = activeCompressionProgress != null,
                    enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(animationSpec = tween(220)),
                    exit = slideOutVertically(animationSpec = tween(220)) { -it } + fadeOut(animationSpec = tween(220)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 6.dp)
                        .wrapContentSize(),
                ) {
                    if (progressToDisplay != null) {
                        CompactCompressionIsland(
                            progress = progressToDisplay,
                            thermalState = thermalState,
                            hazeState = hazeState,
                            onClick = { showBatchQueueSheet = true },
                        )
                    }
                }
            }
        }
    }

    // ── Samsung Gallery-style Menu Bottom Sheet ───────────────────────────────
    if (showMenuSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        // Eight items, so the grid lands on exactly two full rows of four. Panda Sweep and
        // Compress Studio are not here because the Storage Intelligence Hub card directly above
        // already carries both as chips; Recent is reachable from the shortcut row on the photos
        // screen. Adding a ninth item starts a third row with one orphan in it.
        val menuItems = listOf(
            MenuSheetItem("Videos", Icons.Outlined.PlayCircle) {
                showMenuSheet = false
                navController.navigate(VideosRoute)
            },
            MenuSheetItem("Favorites", Icons.Outlined.FavoriteBorder) {
                showMenuSheet = false
                navController.navigate(FavoritesRoute)
            },
            MenuSheetItem("Memories", Icons.Outlined.AutoAwesome) {
                showMenuSheet = false
                navController.navigate(SmartOrganizerRoute)
            },
            MenuSheetItem("People", Icons.Outlined.Face) {
                showMenuSheet = false
                navController.navigate(PeopleRoute)
            },
            MenuSheetItem("Trash", Icons.Outlined.Delete) {
                showMenuSheet = false
                navController.navigate(TrashRoute)
            },
            MenuSheetItem("Private Album", Icons.Outlined.Lock) {
                showMenuSheet = false
                navController.navigate(PrivateVaultRoute)
            },
            MenuSheetItem("Compress Queue", Icons.Outlined.ManageHistory) {
                showMenuSheet = false
                navController.navigate(CompressionManagerRoute)
            },
            MenuSheetItem("Settings", Icons.Outlined.Settings) {
                showMenuSheet = false
                navController.navigate(SettingsRoute)
            },
        )

        PandaModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Samsung Gallery Storage Intelligence Hub Summary Card
                StorageHubSummaryCard(
                    onOpenPandaSweep = {
                        showMenuSheet = false
                        navController.navigate(CleanupRoute)
                    },
                    onOpenCompression = {
                        showMenuSheet = false
                        navController.navigate(CompressionManagerRoute)
                    },
                )

                // 4-column balanced grid matching Samsung Gallery One UI 6.1 / 7
                menuItems.chunked(MENU_COLUMNS).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { item ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                SamsungMenuSheetItem(
                                    label = item.label,
                                    icon = item.icon,
                                    onClick = item.onClick,
                                )
                            }
                        }
                        repeat(MENU_COLUMNS - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showBatchQueueSheet && (compressionProgress != null || batchTasks.isNotEmpty())) {
        BatchQueueBottomSheet(
            progress = compressionProgress,
            batchTasks = batchTasks,
            thermalState = thermalState,
            onPauseAll = { scope.launch { compressionQueueRepository.pauseAll() } },
            onResumeAll = { scope.launch { compressionQueueRepository.resumeAll() } },
            onCancelAll = { scope.launch { compressionQueueRepository.cancelAllActive() } },
            onDismiss = { showBatchQueueSheet = false },
        )
    }
    }
}

/** Menu sheet columns; rows are padded to this so items keep a constant width. */
private const val MENU_COLUMNS = 4

@Composable
private fun CompressionProgressLine(
    progress: CompressionQueueProgress,
    thermalState: ThermalState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
) {
    val isThrottled = thermalState.isThrottled
    val pillShape = RoundedCornerShape(50.dp)
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 250),
        label = "compression_progress",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = 16.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.5f),
            )
            .oneUiGlass(state = hazeState, shape = pillShape, tintAlpha = 0.42f, blurRadius = 36.dp)
            .then(
                // The throttled state is signalled by warming the panel's own edge, so it keeps
                // the hairline oneUiGlass already drew rather than replacing it.
                if (isThrottled) Modifier.border(0.8.dp, Color(0x99FFA726), pillShape) else Modifier
            ),
        shape = pillShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isThrottled) Icons.Outlined.Thermostat else Icons.Outlined.Compress,
                contentDescription = null,
                tint = if (isThrottled) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isThrottled) "Cooling device (Pacing)…" else "Compressing photos…",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (isThrottled) Color(0xFFFFB74D) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${progress.percent}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isThrottled) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .width(60.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp)),
                color = if (isThrottled) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            )
        }
    }
}

@Composable
private fun CompactCompressionIsland(
    progress: CompressionQueueProgress,
    thermalState: ThermalState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
) {
    val isThrottled = thermalState.isThrottled
    val pillShape = RoundedCornerShape(50.dp)
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 250),
        label = "compact_compression_progress",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(vertical = 4.dp)
            .shadow(
                elevation = 16.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.5f),
            )
            .oneUiGlass(state = hazeState, shape = pillShape, tintAlpha = 0.48f, blurRadius = 36.dp)
            .then(
                // The throttled state is signalled by warming the panel's own edge, so it keeps
                // the hairline oneUiGlass already drew rather than replacing it.
                if (isThrottled) Modifier.border(0.8.dp, Color(0x99FFA726), pillShape) else Modifier
            ),
        shape = pillShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (isThrottled) Icons.Outlined.Thermostat else Icons.Outlined.Compress,
                contentDescription = null,
                tint = if (isThrottled) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${progress.percent}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isThrottled) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isThrottled) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Samsung Gallery Storage Intelligence Hub Summary Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StorageHubSummaryCard(
    onOpenPandaSweep: () -> Unit,
    onOpenCompression: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = RoundedCornerShape(20.dp),
        // Translucent, not near-opaque: this card sits *on* the menu sheet's glass, so a 0.85
        // fill covered the blur it is supposed to be resting on and flattened the whole sheet.
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Compress,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Storage Intelligence Hub",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Content-Adaptive Engine · SSIM ≥ 0.96",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Both entries open a storage tool, so they get matching weight and styling —
                // a tonal and a filled button side by side read as "one of these is the real
                // action" — and a 48dp height so they clear the minimum touch target.
                StorageHubAction(
                    label = "Panda Sweep",
                    icon = Icons.Outlined.CleaningServices,
                    onClick = onOpenPandaSweep,
                    modifier = Modifier.weight(1f),
                )
                StorageHubAction(
                    label = "Compress Studio",
                    icon = Icons.Outlined.Storage,
                    onClick = onOpenCompression,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One of the two equally-weighted storage tools in the hub card. */
@Composable
private fun StorageHubAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    // An illuminated accent chip rather than a `FilledTonalButton`. The stock tonal button fills
    // with `secondaryContainer`, which on a wallpaper-derived scheme lands on a flat, desaturated
    // block — the only opaque slab on a sheet that is otherwise glass, so it read as plastic
    // dropped onto the panel. A top-lit accent wash with a brighter hairline picks up the same
    // lighting model as the glass around it without paying for a third nested blur pass.
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .background(
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.14f)),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.20f)),
                ),
                shape = shape,
            ),
        shape = shape,
        color = Color.Transparent,
        contentColor = accent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            // 12.5sp with tight padding, not labelLarge: these chips are half the card wide, which
            // is 144dp on a 360dp-wide screen, and "Compress Studio" ellipsised to "Compress St…"
            // at 13sp even before the icon was accounted for. A clipped destination name is worse
            // than a slightly smaller one.
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Menu sheet grid item — large circle button + label below
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SamsungMenuSheetItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        // Always reserve both lines. Without it a one-word label made its cell shorter than a
        // wrapping one ("Private Album", "Compress Queue"), so the icon circles in the grid sat
        // at different heights from row to row.
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 13.5.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating glass pill bottom navigation bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SamsungBottomPanel(
    currentDestination: androidx.navigation.NavDestination?,
    hazeState: HazeState,
    showPicturesTab: Boolean = false,
    onDestinationClick: (TopLevelDestination) -> Unit,
) {
    val pillShape = RoundedCornerShape(50.dp)

    val visibleDestinations = remember(showPicturesTab) {
        if (showPicturesTab) {
            TopLevelDestination.entries
        } else {
            TopLevelDestination.entries.filter { it != TopLevelDestination.PICTURES }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(
                elevation = 20.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = 0.8f),
                spotColor = Color.Black.copy(alpha = 0.9f),
            )
            .oneUiGlass(shape = pillShape, state = hazeState, tintAlpha = 0.45f, blurRadius = 36.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        visibleDestinations.forEach { destination ->
            val isSelected = currentDestination?.let { dest ->
                when (destination) {
                    TopLevelDestination.PICTURES -> dest.hasRoute<PicturesRoute>()
                    TopLevelDestination.ALBUMS -> dest.hasRoute<AlbumsRoute>()
                    TopLevelDestination.COLLECTIONS -> dest.hasRoute<CollectionsRoute>()
                    TopLevelDestination.MENU -> false // Menu is never a "selected" nav destination
                }
            } ?: false
            SamsungBottomItem(
                label = destination.label,
                icon = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                selected = isSelected,
                onClick = { onDestinationClick(destination) },
            )
        }
    }
}

@Composable
private fun RowScope.SamsungBottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val selectedPillShape = RoundedCornerShape(22.dp)
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(140),
        label = "navPillAlpha",
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(selectedPillShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Inner highlight pill with smooth animated appearance
        if (pillAlpha > 0f) {
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .height(44.dp)
                    .graphicsLayer { alpha = pillAlpha }
                    .clip(selectedPillShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            val contentColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(120),
                label = "navContentColor",
            )
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
