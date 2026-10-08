package com.pandagallery.app.ui.photos

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.pandagallery.app.ui.components.FaceCropTransformation
import com.pandagallery.app.ui.components.LocalHazeState
import com.pandagallery.app.ui.components.PandaModalBottomSheet
import com.pandagallery.app.ui.components.oneUiGlass
import com.pandagallery.app.ui.components.pandaGlass
import com.pandagallery.app.ui.components.pandaGlassStyle
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import coil3.compose.AsyncImage
import com.pandagallery.app.domain.model.CompressionOriginalAction
import com.pandagallery.app.domain.model.formatFileSize
import com.pandagallery.app.domain.search.LabelSuggestion
import com.pandagallery.app.domain.model.AlbumGridDensity
import com.pandagallery.app.domain.model.GridDensity
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.SortOrder
import com.pandagallery.app.domain.model.label
import com.pandagallery.app.domain.model.StoryHighlight
import com.pandagallery.app.domain.model.TimelineItem
import com.pandagallery.app.domain.model.MediaContentFilter
import com.pandagallery.app.domain.model.MediaSearchCriteria
import com.pandagallery.app.domain.model.SearchMediaKind
import com.pandagallery.app.domain.model.zoomIn
import com.pandagallery.app.domain.model.zoomOut
import com.pandagallery.app.ui.components.SamsungShareBottomSheet
import com.pandagallery.app.ui.map.SamsungInteractiveMapSheet
import com.pandagallery.app.ui.components.MediaThumbnail
import com.pandagallery.app.ui.components.AlbumCard
import com.pandagallery.app.ui.components.PandaDropdownMenu
import com.pandagallery.app.ui.components.PremiumAlertDialog
import com.pandagallery.app.ui.components.TimelineScrubber
import com.pandagallery.app.ui.components.TimelineSectionHeader
import com.pandagallery.app.ui.components.SeslCheckbox
import com.pandagallery.app.ui.components.SeslGoToTopButton
import com.pandagallery.app.data.trash.calculateTrashBadge
import com.pandagallery.app.ui.components.AdaptiveSingleLineText
import com.pandagallery.app.ui.components.adaptiveColumnCount
import com.pandagallery.app.ui.components.pinchToResizeGrid
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.core.content.ContextCompat
import com.pandagallery.app.ui.components.swipeRightToNavigate
import com.pandagallery.app.ui.components.dragSelectGrid
import com.pandagallery.app.ui.components.calculateRangeSelection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    onMediaClick: (Long) -> Unit,
    onCreateCollage: (List<Long>) -> Unit = {},
    onCreateGif: (List<Long>) -> Unit = {},
    mediaFilter: MediaFilter = MediaFilter.All,
    title: String = "Photos",
    onBack: (() -> Unit)? = null,
    enableSwipeToAlbums: Boolean = false,
    searchMode: Boolean = false,
    onSearchClick: (() -> Unit)? = null,
    /** Opens straight into selection mode — see [com.pandagallery.app.ui.navigation.SmartGroupRoute]. */
    startInSelectionMode: Boolean = false,
    onSelectionModeChanged: (Boolean) -> Unit = {},
    viewModel: PhotosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isSelectionMode) {
        onSelectionModeChanged(uiState.isSelectionMode)
    }
    val pendingMutation by viewModel.pendingMutation.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()
    val compressionPrompt by viewModel.compressionPrompt.collectAsStateWithLifecycle()
    val trashCompressPrompt by viewModel.trashCompressPrompt.collectAsStateWithLifecycle()
    val albumTransferPrompt by viewModel.albumTransferPrompt.collectAsStateWithLifecycle()
    val renamePrompt by viewModel.renamePrompt.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val swipeThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val mutationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.completePendingMutation(result.resultCode == android.app.Activity.RESULT_OK)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showMediaManagementDialog by remember { mutableStateOf(false) }
    var showNewTransferAlbum by remember { mutableStateOf(false) }
    var showEmptyTrashConfirmation by remember { mutableStateOf(false) }
    var showSearchFilters by remember { mutableStateOf(false) }
    var showEditDateTimeDialog by remember { mutableStateOf(false) }
    var showChangeLocationDialog by remember { mutableStateOf(false) }
    var showShareBottomSheet by remember { mutableStateOf(false) }
    var showInteractiveMapSheet by remember { mutableStateOf(false) }
    val geotaggedPhotos by viewModel.geotaggedPhotos.collectAsStateWithLifecycle()
    var activeStory by remember { mutableStateOf<StoryHighlight?>(null) }
    val mediaManagementLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (MediaStore.canManageMedia(context)) {
            viewModel.moveSelectedToPrivate()
        } else {
            android.widget.Toast.makeText(
                context,
                "Media management access was not enabled",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    var isVisualUserSelectedOnly by remember { mutableStateOf(false) }
    val visualUserSelectedPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            isVisualUserSelectedOnly = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED
        }
        viewModel.refreshFromPull()
    }

    LifecycleResumeEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            isVisualUserSelectedOnly = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(mediaFilter, searchMode) {
        viewModel.setMediaFilter(if (searchMode) MediaFilter.Search else mediaFilter)
        if (startInSelectionMode) viewModel.enterSelectionMode()
    }

    // Back leaves selection mode before it leaves the screen. It matters more now that a grid can
    // open already selecting (a person's photos, reached from "Remove photos…"), where popping
    // straight out would end the visit the user had just started.
    BackHandler(enabled = uiState.isSelectionMode) { viewModel.clearSelection() }

    LaunchedEffect(pendingMutation) {
        pendingMutation?.let { request ->
            try {
                mutationLauncher.launch(
                    IntentSenderRequest.Builder(request.pendingIntent.intentSender).build(),
                )
            } catch (e: Exception) {
                com.pandagallery.app.data.diagnostics.CrashLog.e("PhotosScreen", "Could not show the system confirmation", e)
                viewModel.completePendingMutation(approved = false)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.compressionQueued.collect { count ->
            snackbarHostState.showSnackbar(
                "$count item${if (count == 1) "" else "s"} added to compression queue",
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.galleryMessage.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val personRemoval by viewModel.personRemoval.collectAsStateWithLifecycle()
    LaunchedEffect(personRemoval) {
        val removal = personRemoval ?: return@LaunchedEffect
        val count = removal.mediaIds.size
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = "Removed $count ${if (count == 1) "photo" else "photos"} from $title",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoPersonRemoval()
        else viewModel.clearPersonRemoval()
    }

    val lastTrashOperation by viewModel.lastTrashOperation.collectAsStateWithLifecycle()
    LaunchedEffect(lastTrashOperation) {
        val trashOp = lastTrashOperation ?: return@LaunchedEffect
        val count = trashOp.items.size
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = "Moved $count ${if (count == 1) "photo" else "photos"} to Trash",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoTrash()
        else viewModel.clearTrashOperation()
    }

    LifecycleResumeEffect(Unit) {
        val lastViewedId = viewModel.consumeLastViewedMediaId()
        if (lastViewedId != null) {
            val targetIndex = uiState.timelineItems.indexOfFirst {
                it is TimelineItem.Media && it.item.id == lastViewedId
            }
            if (targetIndex >= 0) {
                val isAlreadyVisible = isMediaItemVisibleInGrid(
                    layoutInfo = gridState.layoutInfo,
                    mediaId = lastViewedId,
                    targetIndex = targetIndex,
                )
                if (!isAlreadyVisible) {
                    screenScope.launch {
                        gridState.scrollToItem(targetIndex)
                    }
                }
            }
        }
        onPauseOrDispose { }
    }

    albumTransferPrompt?.let { prompt ->
        AlbumDestinationScreen(
            prompt = prompt,
            onBack = viewModel::dismissAlbumTransfer,
            onDestinationSelected = viewModel::selectAlbumTransferDestination,
            onConfirm = viewModel::confirmAlbumTransfer,
            onNewAlbum = { showNewTransferAlbum = true },
        )
        if (showNewTransferAlbum) {
            NewDestinationAlbumDialog(
                onDismiss = { showNewTransferAlbum = false },
                onCreate = { name ->
                    viewModel.createAlbumAndTransfer(name)
                    showNewTransferAlbum = false
                },
            )
        }
        return
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Selection mode toolbar or regular toolbar
            AnimatedContent(
                targetState = uiState.isSelectionMode,
                transitionSpec = {
                    fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                },
                label = "toolbar_transition"
            ) { isSelectionMode ->
                if (isSelectionMode) {
                    val allSelected = uiState.selectedItems.size == uiState.mediaItems.size && uiState.mediaItems.isNotEmpty()
                    SelectionTopBar(
                        selectedCount = uiState.selectedItems.size,
                        allSelected = allSelected,
                        onClose = { viewModel.clearSelection() },
                        onToggleSelectAll = {
                            if (allSelected) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAll()
                            }
                        },
                    )
                } else if (onBack != null) {
                    CenterAlignedTopAppBar(
                        title = {
                            if (searchMode) {
                                GallerySearchField(
                                    query = uiState.searchCriteria.query,
                                    onQueryChange = viewModel::updateSearchQuery,
                                    onSearch = viewModel::commitSearchQuery,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                AdaptiveSingleLineText(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Normal,
                                    minFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (searchMode) {
                                BadgedBox(
                                    badge = {
                                        if (uiState.searchCriteria.hasActiveFilters) Badge()
                                    },
                                ) {
                                    IconButton(onClick = { showSearchFilters = true }) {
                                        Icon(Icons.Outlined.Tune, contentDescription = "Search filters")
                                    }
                                }
                            }
                            if (mediaFilter == MediaFilter.Trash && uiState.mediaItems.isNotEmpty()) {
                                IconButton(onClick = { showEmptyTrashConfirmation = true }) {
                                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Empty Trash")
                                }
                            }
                            if (!searchMode) Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "Album actions")
                                }
                                AlbumActionsMenu(
                                    expanded = showSortMenu,
                                    currentSort = uiState.sortOrder,
                                    sortScopeNote = if (uiState.isFolderSortScoped) {
                                        "Sort this folder"
                                    } else {
                                        "Sort every photo grid"
                                    },
                                    onDismiss = { showSortMenu = false },
                                    onSelectItems = {
                                        showSortMenu = false
                                        viewModel.enterSelectionMode()
                                    },
                                    onChangeGrid = {
                                        showSortMenu = false
                                        viewModel.cycleGridDensity()
                                    },
                                    currentFilter = uiState.contentFilter,
                                    onFilterSelected = {
                                        viewModel.setContentFilter(it)
                                        showSortMenu = false
                                    },
                                    onSortSelected = {
                                        viewModel.setSortOrder(it)
                                        showSortMenu = false
                                    },
                                    onOpenMapView = {
                                        showSortMenu = false
                                        viewModel.loadGeotaggedPhotos()
                                        showInteractiveMapSheet = true
                                    },
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                } else {
                    LargeTopAppBar(
                        title = {
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                AdaptiveSingleLineText(
                                    text = title,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp,
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    minFontSize = 18.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (uiState.mediaItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${uiState.mediaItems.size} items",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    )
                                }
                            }
                        },
                        actions = {
                            if (onSearchClick != null) {
                                IconButton(onClick = onSearchClick) {
                                    Icon(
                                        Icons.Outlined.Search,
                                        contentDescription = "Search",
                                    )
                                }
                            }
                            // Grid density
                            IconButton(onClick = { viewModel.cycleGridDensity() }) {
                                Icon(
                                    when (uiState.gridDensity) {
                                        GridDensity.YEAR -> Icons.Outlined.GridView
                                        GridDensity.MONTH -> Icons.Outlined.Apps
                                        GridDensity.NORMAL -> Icons.Outlined.GridView
                                        GridDensity.EXPANDED -> Icons.Outlined.ViewAgenda
                                    },
                                    contentDescription = "Grid density",
                                )
                            }
                            // Sort menu
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = "More options",
                                    )
                                }
                                AlbumActionsMenu(
                                    expanded = showSortMenu,
                                    currentSort = uiState.sortOrder,
                                    sortScopeNote = if (uiState.isFolderSortScoped) {
                                        "Sort this folder"
                                    } else {
                                        "Sort every photo grid"
                                    },
                                    onDismiss = { showSortMenu = false },
                                    onSelectItems = {
                                        showSortMenu = false
                                        viewModel.enterSelectionMode()
                                    },
                                    onChangeGrid = {
                                        showSortMenu = false
                                        viewModel.cycleGridDensity()
                                    },
                                    currentFilter = uiState.contentFilter,
                                    onFilterSelected = {
                                        viewModel.setContentFilter(it)
                                        showSortMenu = false
                                    },
                                    onSortSelected = {
                                        viewModel.setSortOrder(it)
                                        showSortMenu = false
                                    },
                                    onOpenMapView = {
                                        showSortMenu = false
                                        viewModel.loadGeotaggedPhotos()
                                        showInteractiveMapSheet = true
                                    },
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                }
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode) {
                if (mediaFilter == MediaFilter.Trash) {
                    TrashSelectionActionBar(
                        onRestore = viewModel::restoreSelectedItems,
                        onCompressToKeep = viewModel::promptCompressSelectedTrashItems,
                        onDeletePermanently = viewModel::permanentlyDeleteSelectedItems,
                    )
                } else {
                    SelectionActionBar(
                        onCollage = {
                            val selected = uiState.mediaItems.filter { it.id in uiState.selectedItems }
                            when {
                                selected.size < 2 -> screenScope.launch { snackbarHostState.showSnackbar("Select at least 2 items") }
                                selected.size > 6 -> screenScope.launch { snackbarHostState.showSnackbar("You can include up to 6 items") }
                                selected.count(MediaItem::isVideo) > 4 -> screenScope.launch { snackbarHostState.showSnackbar("You can include up to 4 videos") }
                                else -> onCreateCollage(selected.map(MediaItem::id))
                            }
                        },
                        onCreateGif = {
                            val selected = uiState.mediaItems.filter { it.id in uiState.selectedItems }
                            when {
                                selected.size < 2 -> screenScope.launch { snackbarHostState.showSnackbar("Select at least 2 items") }
                                selected.size > 50 -> screenScope.launch { snackbarHostState.showSnackbar("You can include up to 50 items for GIF") }
                                else -> onCreateGif(selected.map(MediaItem::id))
                            }
                        },
                        onShare = { showShareBottomSheet = true },
                        onFavorite = { viewModel.favoriteSelectedItems() },
                        onMove = { viewModel.showAlbumTransfer(AlbumTransferMode.MOVE) },
                        onCopy = { viewModel.showAlbumTransfer(AlbumTransferMode.COPY) },
                        onRotate = { viewModel.rotateSelectedItems() },
                        onRename = viewModel::showRenameSelected,
                        onCompress = { viewModel.compressSelectedItems() },
                        onEditDateTime = { showEditDateTimeDialog = true },
                        onChangeLocation = { showChangeLocationDialog = true },
                    onMoveToPrivate = {
                        if (MediaStore.canManageMedia(context)) {
                            viewModel.moveSelectedToPrivate()
                        } else {
                            showMediaManagementDialog = true
                        }
                    },
                    onDelete = viewModel::trashSelectedItems,
                    onRemoveFromPerson = uiState.personGroupKey?.let {
                        { viewModel.removeSelectedFromPerson() }
                    },
                    onSetPersonCover = uiState.personGroupKey?.takeIf {
                        uiState.selectedItems.size == 1
                    }?.let { { viewModel.setPersonCoverFromSelection() } },
                    primaryActionIsRestore = false,
                    favoriteActionIsRemove = uiState.mediaItems
                        .filter { it.id in uiState.selectedItems }
                        .all { it.isFavorite },
                    showMoveToPrivate = true,
                    )
                }
            }
        },
    ) { padding ->
        // Suggestions can outgrow their usual space when the library has lots of matching
        // labels; cap them at half the screen and scroll internally instead of squeezing
        // (or pushing off-screen) the grid below.
        val discoveryMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
        when {
            uiState.isLoading && uiState.timelineItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = uiState.error ?: "Something went wrong",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        FilledTonalButton(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            uiState.timelineItems.isEmpty() -> {
                if (!uiState.hasLoadedFromCache) {
                    // Still executing initial fast Room cache query (~15ms)
                    // Render clean background surface without flashing empty state text or spinners
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Text(
                                text = if (searchMode && uiState.searchCriteria.query.isBlank() && !uiState.searchCriteria.hasActiveFilters) "Search your gallery" else if (searchMode) "No matches" else "No photos yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (searchMode) "Try changing the text, date, type, size, favorite, or album filters" else "Take some photos to see them here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                            )
                            if (searchMode && uiState.searchCriteria.query.isBlank()) {
                                SearchDiscovery(
                                    history = uiState.searchHistory,
                                    labels = uiState.labelSuggestions,
                                    people = uiState.peopleSuggestions,
                                    onQuery = viewModel::searchFor,
                                    onForget = viewModel::forgetSearchQuery,
                                    onClearHistory = viewModel::clearSearchHistory,
                                    modifier = Modifier
                                        .heightIn(max = discoveryMaxHeight)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Column(modifier = Modifier.padding(padding)) {
                    // Above the grid, not only in the empty state: with a blank query the
                    // grid shows the whole library, so an empty-state-only placement meant
                    // these were unreachable in practice -- exactly the discoverability
                    // problem they exist to solve.
                    if (searchMode && uiState.searchCriteria.query.isBlank()) {
                        SearchDiscovery(
                            history = uiState.searchHistory,
                            labels = uiState.labelSuggestions,
                            people = uiState.peopleSuggestions,
                            onQuery = viewModel::searchFor,
                            onForget = viewModel::forgetSearchQuery,
                            onClearHistory = viewModel::clearSearchHistory,
                            modifier = Modifier
                                .heightIn(max = discoveryMaxHeight)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    transferProgress?.let { progress ->
                        MediaTransferBanner(progress)
                    }
                    if (mediaFilter == MediaFilter.Trash) {
                        val totalTrashBytes = remember(uiState.mediaItems) { uiState.mediaItems.sumOf { it.size } }
                        val potentialSavedBytes = remember(totalTrashBytes) { (totalTrashBytes * 0.65).toLong() }
                        TrashRetentionBanner(
                            retentionDays = uiState.trashRetentionDays,
                            totalItemsCount = uiState.mediaItems.size,
                            totalBytes = totalTrashBytes,
                            potentialSavedBytes = potentialSavedBytes,
                            expiredCount = uiState.trashRemainingDays.count { it.value <= 0 },
                            onDeleteExpired = viewModel::deleteExpiredTrashItems,
                            onEmptyTrash = { showEmptyTrashConfirmation = true },
                            onCompressToKeepAll = viewModel::promptCompressAllTrashItems,
                        )
                    }
                    if (mediaFilter is MediaFilter.Cleanup && !uiState.isSelectionMode) {
                        val redundantCount = remember(uiState.mediaItems) { viewModel.redundantDuplicateCount() }
                        val groupCount = remember(uiState.mediaItems) { viewModel.duplicateGroupCount() }
                        if (redundantCount > 0) {
                            DuplicateSelectionBanner(
                                groupCount = groupCount,
                                redundantCount = redundantCount,
                                onSelectRedundant = viewModel::selectRedundantDuplicates,
                            )
                        }
                    }
                    if (mediaFilter is MediaFilter.All && !searchMode && !uiState.isSelectionMode && uiState.storyHighlights.isNotEmpty()) {
                        StoryHighlightStrip(
                            stories = uiState.storyHighlights,
                            onStoryClick = { story ->
                                activeStory = story
                            },
                        )
                    }
                    if (mediaFilter is MediaFilter.All && !searchMode && !uiState.isSelectionMode) {
                        ShotTypesFilterRow(
                            currentFilter = uiState.contentFilter,
                            onFilterSelected = { filter ->
                                viewModel.setContentFilter(filter)
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = mediaFilter is MediaFilter.All && !searchMode && !uiState.isSelectionMode && uiState.storageOpportunity != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        uiState.storageOpportunity?.let { opp ->
                            StorageOpportunityCard(
                                opportunity = opp,
                                onReviewAndCompress = viewModel::promptStorageOpportunityCompression,
                                onDismiss = viewModel::dismissStorageOpportunity,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isVisualUserSelectedOnly && !uiState.isSelectionMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        text = "Access granted to selected photos and videos",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            visualUserSelectedPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_MEDIA_IMAGES,
                                                    Manifest.permission.READ_MEDIA_VIDEO,
                                                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                                                )
                                            )
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text("Manage", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refreshFromPull,
                ) {
                    MediaTimeline(
                        timelineItems = uiState.timelineItems,
                        gridDensity = uiState.gridDensity,
                        selectedItems = uiState.selectedItems,
                        trashRemainingDays = uiState.trashRemainingDays,
                        isSelectionMode = uiState.isSelectionMode,
                        gridState = gridState,
                        onMediaClick = { item ->
                            if (uiState.isSelectionMode) {
                                viewModel.toggleSelection(item)
                            } else {
                                // Hand the viewer this exact list so swiping stays in context.
                                viewModel.prepareViewerSession()
                                // Hand the viewer this exact list and selection so swiping stays in context
                                viewModel.prepareViewerSession(clickedItem = item)
                                onMediaClick(item.id)
                            }
                        },
                        onExpandClick = { item ->
                            viewModel.prepareViewerSession(clickedItem = item)
                            onMediaClick(item.id)
                        },
                        onToggleSelection = { item ->
                            viewModel.toggleSelection(item)
                        },
                        onMediaLongClick = { item ->
                            viewModel.toggleSelection(item)
                        },
                        onDateSelection = viewModel::toggleDateSelection,
                        onSelectionChange = viewModel::setSelectedItems,
                        modifier = Modifier
                            .pinchToResizeGrid(
                                enabled = !uiState.isSelectionMode,
                                onZoomIn = viewModel::zoomInGrid,
                                onZoomOut = viewModel::zoomOutGrid,
                            )
                            .swipeRightToNavigate(
                                enabled = enableSwipeToAlbums && !uiState.isSelectionMode,
                                thresholdPx = swipeThresholdPx,
                                onSwipeRight = { onBack?.invoke() },
                            ),
                    )
                    }
                }
            }
        }
    }

    if (showSearchFilters) {
        SearchFiltersSheet(
            criteria = uiState.searchCriteria,
            albums = uiState.searchAlbums,
            onDismiss = { showSearchFilters = false },
            onApply = {
                viewModel.updateSearchCriteria(it)
                showSearchFilters = false
            },
        )
    }

    compressionPrompt?.let { prompt ->
        com.pandagallery.app.ui.compression.components.CompressionBatchBottomSheet(
            itemCount = prompt.itemCount,
            estimate = prompt.estimate,
            onDismiss = viewModel::dismissCompressionPrompt,
            onConfirmAction = viewModel::chooseCompressionAction,
        )
    }

    if (showMediaManagementDialog) {
        PremiumAlertDialog(
            onDismissRequest = { showMediaManagementDialog = false },
            icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
            title = { Text("Allow automatic Private moves?") },
            text = {
                Text("Android requires one Media management grant before PandaGallery can remove public originals without asking every time.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMediaManagementDialog = false
                        runCatching {
                            mediaManagementLauncher.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }.onFailure {
                            viewModel.moveSelectedToPrivate()
                        }
                    },
                ) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMediaManagementDialog = false
                        viewModel.moveSelectedToPrivate()
                    },
                ) { Text("Continue with confirmation") }
            },
        )
    }


    renamePrompt?.let { item ->
        RenameMediaDialog(
            currentName = item.displayName,
            onDismiss = viewModel::dismissRename,
            onRename = viewModel::renameSelected,
        )
    }


    if (showEmptyTrashConfirmation) {
        val totalTrashBytes = remember(uiState.mediaItems) { uiState.mediaItems.sumOf { it.size } }
        val totalFormatted = remember(totalTrashBytes) { formatFileSize(totalTrashBytes) }
        PremiumAlertDialog(
            onDismissRequest = { showEmptyTrashConfirmation = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Empty Trash?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("All ${uiState.mediaItems.size} items ($totalFormatted) will be permanently deleted and cannot be recovered.")
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("💡", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                // "up to 75%" was an unconditional promise; the real figure
                                // depends entirely on how compressed the originals already are.
                                text = "Tip: Want to save space without losing photos? " +
                                    "'Compress to Keep' re-encodes them instead of deleting — " +
                                    "biggest gains on large camera photos and 4K video.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyTrashConfirmation = false
                        viewModel.emptyTrash()
                    },
                ) { Text("Delete permanently", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    trashCompressPrompt?.let { prompt ->
        val totalFormatted = remember(prompt.totalBytes) { formatFileSize(prompt.totalBytes) }
        val savedFormatted = remember(prompt.potentialSavedBytes) { formatFileSize(prompt.potentialSavedBytes) }
        PremiumAlertDialog(
            onDismissRequest = viewModel::dismissTrashCompressPrompt,
            icon = {
                Icon(
                    Icons.Outlined.Compress,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(28.dp),
                )
            },
            title = { Text("Compress & Keep?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Restore ${prompt.items.size} items to your library and compress them with Near-Lossless quality to reclaim space while keeping your memories intact.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Current size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(totalFormatted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Estimated space saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("~$savedFormatted (~65%)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmCompressAndRestoreTrash,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Text("Compress & Restore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissTrashCompressPrompt) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showEditDateTimeDialog) {
        val firstSelected = uiState.mediaItems.firstOrNull { it.id in uiState.selectedItems }
        val initialMillis = firstSelected?.dateTaken ?: System.currentTimeMillis()
        EditDateTimeDialog(
            initialDateMillis = initialMillis,
            onDismiss = { showEditDateTimeDialog = false },
            onConfirm = { millis ->
                showEditDateTimeDialog = false
                viewModel.updateSelectedDateTime(millis)
            },
        )
    }

    if (showChangeLocationDialog) {
        ChangeLocationDialog(
            onDismiss = { showChangeLocationDialog = false },
            onConfirm = { lat, lng ->
                showChangeLocationDialog = false
                viewModel.updateSelectedLocation(lat, lng)
            },
        )
    }

    activeStory?.let { story ->
        val storyMediaItems = remember(story, uiState.mediaItems) {
            story.items.ifEmpty {
                val idSet = story.mediaIds.toSet()
                val found = uiState.mediaItems.filter { it.id in idSet }
                found.ifEmpty { uiState.mediaItems.take(10) }
            }
        }
        com.pandagallery.app.ui.story.StoryPlayerDialog(
            title = story.title,
            subtitle = story.subtitle,
            mediaItems = storyMediaItems,
            badge = story.badge,
            dateRangeLabel = story.dateRangeLabel,
            mood = story.mood,
            estimatedSavingsBytes = story.estimatedSavingsBytes,
            onDismiss = { activeStory = null },
            onCreateCollage = { ids ->
                activeStory = null
                onCreateCollage(ids)
            },
            onCompressStory = { items ->
                activeStory = null
                viewModel.compressItems(items)
            },
            onExportReel = { items, storyTitle, soundtrack, onProgress, onSuccess, onError ->
                viewModel.exportStoryReel(
                    title = storyTitle,
                    items = items,
                    soundtrack = soundtrack,
                    onProgress = onProgress,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
        )
    }

    if (showInteractiveMapSheet) {
        SamsungInteractiveMapSheet(
            items = geotaggedPhotos,
            onDismiss = { showInteractiveMapSheet = false },
            onOpenMedia = { mediaId ->
                showInteractiveMapSheet = false
                onMediaClick(mediaId)
            },
            onCompressCluster = { cluster ->
                viewModel.compressCluster(cluster)
            },
            onStripGps = { cluster ->
                viewModel.stripClusterGps(cluster)
            },
        )
    }

    if (showShareBottomSheet) {
        val selected = remember(uiState.mediaItems, uiState.selectedItems) {
            uiState.mediaItems.filter { it.id in uiState.selectedItems }
        }
        if (selected.isNotEmpty()) {
            SamsungShareBottomSheet(
                items = selected,
                initialRemoveLocation = uiState.removeLocationWhenSharing,
                initialConvertHeifRaw = uiState.convertHeifWhenSharing,
                onDismiss = { showShareBottomSheet = false },
                onConfirmShare = { removeLocation, convertHeifRaw ->
                    showShareBottomSheet = false
                    viewModel.shareSelectedItems(
                        removeLocation = removeLocation,
                        convertHeifRaw = convertHeifRaw,
                    )
                },
            )
        } else {
            showShareBottomSheet = false
        }
    }
}

/**
 * Reports a copy or move in progress.
 *
 * Transfers are per-file and can run for minutes on a large selection; without this the screen
 * simply sat there, which reads as a hang rather than as work. Mirrors the compression queue's
 * line so both long operations look the same.
 */
@Composable
private fun MediaTransferBanner(progress: MediaTransferProgress) {
    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .oneUiGlass(shape = shape, state = hazeState, tintAlpha = 0.72f, blurRadius = 32.dp),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = progress.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${(progress.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

/**
 * Trash is only self-cleaning when Android grants MANAGE_MEDIA, so state the window
 * and offer a one-tap clear for anything already past it.
 */
@Composable
private fun TrashRetentionBanner(
    retentionDays: Int,
    totalItemsCount: Int,
    totalBytes: Long,
    potentialSavedBytes: Long,
    expiredCount: Int,
    onDeleteExpired: () -> Unit,
    onEmptyTrash: () -> Unit,
    onCompressToKeepAll: () -> Unit,
) {
    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(20.dp)
    val isExpired = expiredCount > 0
    val totalFormatted = remember(totalBytes) { formatFileSize(totalBytes) }
    val savedFormatted = remember(potentialSavedBytes) { formatFileSize(potentialSavedBytes) }

    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .oneUiGlass(shape = shape, state = hazeState, tintAlpha = 0.70f, blurRadius = 32.dp)
            // The expired state is signalled by warming the panel's edge and a translucent wash,
            // both drawn over the glass rather than instead of it.
            .then(
                if (isExpired) {
                    Modifier
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f), shape)
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), shape)
                } else {
                    Modifier
                }
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isExpired) Icons.Outlined.WarningAmber else Icons.Outlined.DeleteSweep,
                            contentDescription = null,
                            tint = if (isExpired) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = if (totalItemsCount > 0) {
                            "Trash · $totalItemsCount items ($totalFormatted)"
                        } else {
                            "Trash is empty"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isExpired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (isExpired) {
                            "$expiredCount item${if (expiredCount == 1) "" else "s"} past the $retentionDays-day limit"
                        } else {
                            "Items are permanently deleted after $retentionDays days"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                        color = if (isExpired) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isExpired) {
                    FilledTonalButton(
                        onClick = onDeleteExpired,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = "Delete expired",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Superpower quick actions row when items exist in Trash
            if (totalItemsCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Compress to Keep Superpower button
                    FilledTonalButton(
                        onClick = onCompressToKeepAll,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 36.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF00C853).copy(alpha = 0.16f),
                            contentColor = Color(0xFF00E676),
                        ),
                        shape = RoundedCornerShape(17.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Compress,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Compress & Keep (~$savedFormatted)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Empty Trash button
                    OutlinedButton(
                        onClick = onEmptyTrash,
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 0.8.dp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                        ),
                        shape = RoundedCornerShape(17.dp),
                    ) {
                        Text(
                            text = "Empty",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The point of a duplicate finder: keep the largest copy in each group, select the rest.
 */
@Composable
private fun DuplicateSelectionBanner(
    groupCount: Int,
    redundantCount: Int,
    onSelectRedundant: () -> Unit,
) {
    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(18.dp)
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .oneUiGlass(shape = shape, state = hazeState, tintAlpha = 0.70f, blurRadius = 32.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "$groupCount group${if (groupCount == 1) "" else "s"} of copies",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Keeps the largest copy of each",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onSelectRedundant,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.defaultMinSize(minHeight = 34.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                Text(
                    text = "Select $redundantCount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StorageOpportunityCard(
    opportunity: StorageOpportunity,
    onReviewAndCompress: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(20.dp)
    val oneUiBlue = Color(0xFF2C74DE)

    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .oneUiGlass(shape = shape, state = hazeState, tintAlpha = 0.72f, blurRadius = 32.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = oneUiBlue.copy(alpha = 0.16f),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Compress,
                            contentDescription = null,
                            tint = oneUiBlue,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Storage Opportunity",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = oneUiBlue,
                    )
                    Text(
                        text = "Save ~${formatFileSize(opportunity.potentialSavedBytes)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${opportunity.candidateCount} uncompressed photos can be optimized with near-lossless clarity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        text = "Not now",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                FilledTonalButton(
                    onClick = onReviewAndCompress,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = oneUiBlue,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Compress,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Review & Compress",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


@Composable
private fun AlbumActionsMenu(
    expanded: Boolean,
    currentSort: SortOrder,
    /** Says which grids the sort below will change, since that now depends on a setting. */
    sortScopeNote: String,
    onDismiss: () -> Unit,
    onSelectItems: () -> Unit,
    onChangeGrid: () -> Unit,
    currentFilter: MediaContentFilter,
    onFilterSelected: (MediaContentFilter) -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onOpenMapView: (() -> Unit)? = null,
) {
    PandaDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Select items") },
            leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
            onClick = onSelectItems,
        )
        DropdownMenuItem(
            text = { Text("Change grid size") },
            leadingIcon = { Icon(Icons.Outlined.GridView, contentDescription = null) },
            onClick = onChangeGrid,
        )
        if (onOpenMapView != null) {
            DropdownMenuItem(
                text = { Text("Map view") },
                leadingIcon = { Icon(Icons.Outlined.Map, contentDescription = null) },
                onClick = onOpenMapView,
            )
        }
        HorizontalDivider()
        MediaContentFilter.entries.forEach { filter ->
            DropdownMenuItem(
                text = { Text(filter.contentFilterLabel()) },
                leadingIcon = { if (filter == currentFilter) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = { onFilterSelected(filter) },
            )
        }
        HorizontalDivider()
        Text(
            text = sortScopeNote,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SortOrder.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(sort.label) },
                leadingIcon = {
                    if (sort == currentSort) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
                onClick = { onSortSelected(sort) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDestinationScreen(
    prompt: AlbumTransferPrompt,
    onBack: () -> Unit,
    onDestinationSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onNewAlbum: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var gridDensity by rememberSaveable { mutableStateOf(AlbumGridDensity.NORMAL) }
    val sourcePaths = remember(prompt.items) {
        prompt.items.mapNotNull { it.relativePath }.map { it.trim('/') }.toSet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (prompt.mode == AlbumTransferMode.MOVE) "Move to album" else "Copy to album",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Choose a destination",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            TransferConfirmationBar(
                items = prompt.items,
                mode = prompt.mode,
                canConfirm = prompt.selectedDestinationPath != null,
                onCancel = onBack,
                onConfirm = onConfirm,
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(adaptiveColumnCount(gridDensity.columns, maxWidth)),
                modifier = Modifier
                    .fillMaxSize()
                    .pinchToResizeGrid(
                        enabled = true,
                        onZoomIn = { gridDensity = gridDensity.zoomIn() },
                        onZoomOut = { gridDensity = gridDensity.zoomOut() },
                    ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "new_album") {
                    NewAlbumPickerCard(onClick = onNewAlbum)
                }
                items(prompt.albums, key = { it.id }) { album ->
                    val path = album.relativePath ?: "Pictures/${album.name}"
                    val isCurrentMoveDestination = prompt.mode == AlbumTransferMode.MOVE &&
                        sourcePaths.size == 1 && path.trim('/') in sourcePaths
                    AlbumCard(
                        name = album.name,
                        coverUri = album.coverUri,
                        mediaCount = album.mediaCount,
                        onClick = { if (!isCurrentMoveDestination) onDestinationSelected(path) },
                        isSelected = prompt.selectedDestinationPath == path,
                        enabled = !isCurrentMoveDestination,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferConfirmationBar(
    items: List<MediaItem>,
    mode: AlbumTransferMode,
    canConfirm: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = LocalHazeState.current
    val pillShape = RoundedCornerShape(50.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .shadow(
                elevation = 20.dp,
                shape = pillShape,
                ambientColor = Color.Black.copy(alpha = 0.8f),
                spotColor = Color.Black.copy(alpha = 0.9f),
            )
            .oneUiGlass(shape = pillShape, state = hazeState, tintAlpha = 0.50f, blurRadius = 36.dp),
        shape = pillShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .height(60.dp)
                .padding(horizontal = 14.dp)
                .semantics { isTraversalGroup = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectedMediaSummary(
                items = items,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onCancel,
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onConfirm,
                enabled = canConfirm,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (mode == AlbumTransferMode.MOVE) "Move here" else "Copy here",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun SelectedMediaSummary(
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${items.size} ${if (items.size == 1) "item" else "items"} selected"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(width = 42.dp, height = 34.dp)) {
            val context = LocalContext.current
            items.take(3).reversed().forEachIndexed { index, item ->
                val offset = (2 - index).coerceAtLeast(0) * 3
                val thumbRequest = remember(item.uri) {
                    ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(128)
                        .crossfade(false)
                        .memoryCacheKey("${item.uri}_thumb")
                        .placeholderMemoryCacheKey("${item.uri}_thumb")
                        .build()
                }
                AsyncImage(
                    model = thumbRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(start = offset.dp, top = offset.dp)
                        .size(24.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                )
            }
        }
        Text(
            text = "${items.size} selected",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun NewAlbumPickerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = "New album",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        Text(
            text = "Create",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewDestinationAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New album") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                label = { Text("Album name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameMediaDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    val extension = currentName.substringAfterLast('.', "")
    val initialName = if (extension.isBlank()) currentName else currentName.removeSuffix(".$extension")
    var name by remember(currentName) { mutableStateOf(initialName) }
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
        title = { Text("Rename media") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                label = { Text("File name") },
                suffix = if (extension.isBlank()) null else ({ Text(".$extension") }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Timeline grid with section headers that span the full width.
 */
@Composable
fun MediaTimeline(
    timelineItems: List<TimelineItem>,
    gridDensity: GridDensity,
    selectedItems: Set<Long>,
    trashRemainingDays: Map<Long, Int> = emptyMap(),
    isSelectionMode: Boolean,
    onMediaClick: (MediaItem) -> Unit,
    onMediaLongClick: (MediaItem) -> Unit,
    onDateSelection: (String) -> Unit,
    onToggleSelection: (MediaItem) -> Unit = {},
    onSelectionChange: (Set<Long>) -> Unit = {},
    onExpandClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    var dragInitialSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var dragIsSelecting by remember { mutableStateOf(true) }
    val targetThumbnailSize = remember(gridDensity) {
        when (gridDensity) {
            GridDensity.YEAR -> 128
            GridDensity.MONTH -> 256
            GridDensity.NORMAL -> 384
            GridDensity.EXPANDED -> 768
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(adaptiveColumnCount(gridDensity.columns, maxWidth)),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .dragSelectGrid(
                    gridState = gridState,
                    isSelectionMode = isSelectionMode,
                    isSelectableItem = { index ->
                        timelineItems.getOrNull(index) is TimelineItem.Media
                    },
                    onDragStart = { startIdx ->
                        val startMedia = (timelineItems.getOrNull(startIdx) as? TimelineItem.Media)?.item
                        if (startMedia != null) {
                            val initial = selectedItems
                            val selecting = !initial.contains(startMedia.id)
                            dragInitialSelection = initial
                            dragIsSelecting = selecting
                            val updated = if (selecting) initial + startMedia.id else initial - startMedia.id
                            onSelectionChange(updated)
                        }
                    },
                    onDrag = { startIdx, currentIdx ->
                        val updated = calculateRangeSelection(
                            startIndex = startIdx,
                            currentIndex = currentIdx,
                            initialSelection = dragInitialSelection,
                            items = timelineItems,
                            getItemId = { (it as? TimelineItem.Media)?.item?.id },
                            isSelecting = dragIsSelecting,
                        )
                        onSelectionChange(updated)
                    },
                    onDragEnd = {
                        dragInitialSelection = emptySet()
                    },
                ),
            contentPadding = PaddingValues(horizontal = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            items(
                count = timelineItems.size,
                key = { index ->
                    when (val item = timelineItems[index]) {
                        is TimelineItem.Header -> "header_${item.header.dateKey}"
                        is TimelineItem.Media -> "media_${item.item.id}"
                    }
                },
                span = { index ->
                    when (timelineItems[index]) {
                        is TimelineItem.Header -> GridItemSpan(maxLineSpan)
                        is TimelineItem.Media -> GridItemSpan(1)
                    }
                },
                contentType = { index ->
                    when (timelineItems[index]) {
                        is TimelineItem.Header -> "header"
                        is TimelineItem.Media -> "media"
                    }
                },
            ) { index ->
                val itemModifier = if (gridState.isScrollInProgress) {
                    Modifier
                } else {
                    Modifier.animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = null,
                    )
                }
                when (val item = timelineItems[index]) {
                    is TimelineItem.Header -> {
                        TimelineSectionHeader(
                            label = item.header.label,
                            itemCount = item.header.itemCount,
                            selectedCount = if (isSelectionMode) {
                                item.header.itemIds.count(selectedItems::contains)
                            } else {
                                null
                            },
                            onToggleSelection = { onDateSelection(item.header.dateKey) },
                            modifier = itemModifier,
                        )
                    }
                    is TimelineItem.Media -> {
                        MediaThumbnail(
                            item = item.item,
                            isSelected = item.item.id in selectedItems,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleSelection(item.item)
                                } else {
                                    onMediaClick(item.item)
                                }
                            },
                            onLongClick = {
                                if (item.item.id !in selectedItems) {
                                    onMediaLongClick(item.item)
                                }
                            },
                            onToggleSelection = { onToggleSelection(item.item) },
                            onExpandClick = onExpandClick?.let { expand -> { expand(item.item) } },
                            statusLabel = calculateTrashBadge(trashRemainingDays[item.item.id])?.label,
                            targetThumbnailSize = targetThumbnailSize,
                            modifier = itemModifier,
                        )
                    }
                }
            }
        }

        TimelineScrubber(
            gridState = gridState,
            timelineItems = timelineItems,
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        SeslGoToTopButton(
            gridState = gridState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        )
    }
}

/**
 * Selection mode toolbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onToggleSelectAll,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeslCheckbox(
                    checked = allSelected,
                    onCheckedChange = { onToggleSelectAll() },
                    activeColor = MaterialTheme.colorScheme.primary,
                    size = 22.dp,
                )
                Text(
                    text = "All",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        title = {
            Text(
                text = if (selectedCount == 0) "Select items" else "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        actions = {
            TextButton(
                onClick = onClose,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun SelectionActionBar(
    onCollage: () -> Unit,
    onCreateGif: () -> Unit = {},
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRotate: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onMoveToPrivate: () -> Unit,
    onDelete: () -> Unit,
    primaryActionIsRestore: Boolean,
    favoriteActionIsRemove: Boolean,
    showMoveToPrivate: Boolean,
    onEditDateTime: (() -> Unit)? = null,
    onChangeLocation: (() -> Unit)? = null,
    /** Only in a person's photos: takes the selection out of that group. */
    onRemoveFromPerson: (() -> Unit)? = null,
    /** Only in a person's photos, with one photo selected: makes it their cover. */
    onSetPersonCover: (() -> Unit)? = null,
) {
    val hazeState = LocalHazeState.current
    val pillShape = RoundedCornerShape(28.dp)
    var showMoreMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.7f),
                    spotColor = Color.Black.copy(alpha = 0.8f),
                )
                .oneUiGlass(shape = pillShape, state = hazeState, tintAlpha = 0.54f, blurRadius = 36.dp),
            shape = pillShape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics { isTraversalGroup = true },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // If in person's photos: specific contextual option
                onRemoveFromPerson?.let {
                    SelectionAction(
                        label = "Not them",
                        icon = Icons.Outlined.PersonRemove,
                        onClick = it,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Share
                SelectionAction(
                    label = "Share",
                    icon = Icons.Outlined.Share,
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                )

                // Compress: the headline space-saver, so it stays one tap away
                // instead of hiding behind the overflow menu.
                SelectionAction(
                    label = "Compress",
                    icon = Icons.Outlined.Compress,
                    onClick = onCompress,
                    modifier = Modifier.weight(1f),
                )

                // Delete / Restore
                SelectionAction(
                    label = if (primaryActionIsRestore) "Restore" else "Delete",
                    icon = if (primaryActionIsRestore) Icons.Outlined.Restore else Icons.Outlined.Delete,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    tint = if (primaryActionIsRestore) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )

                // More (⋮) with Popover Menu
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    SelectionAction(
                        label = "More",
                        icon = Icons.Outlined.MoreVert,
                        onClick = { showMoreMenu = true },
                    )
                    PandaDropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Create collage") },
                            leadingIcon = { Icon(Icons.Outlined.GridView, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onCollage()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Create GIF") },
                            leadingIcon = { Icon(Icons.Outlined.GifBox, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onCreateGif()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Move to album") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onMove()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy to album") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onCopy()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (favoriteActionIsRemove) "Remove from favorites" else "Add to favorites") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (favoriteActionIsRemove) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (favoriteActionIsRemove) Color(0xFFFF453A) else LocalContentColor.current,
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onFavorite()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Rotate right") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onRotate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onRename()
                            },
                        )
                        if (showMoveToPrivate) {
                            DropdownMenuItem(
                                text = { Text("Move to Private Vault") },
                                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onMoveToPrivate()
                                },
                            )
                        }
                        onSetPersonCover?.let {
                            DropdownMenuItem(
                                text = { Text("Set as cover") },
                                leadingIcon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    it()
                                },
                            )
                        }
                        onEditDateTime?.let {
                            DropdownMenuItem(
                                text = { Text("Edit date and time") },
                                leadingIcon = { Icon(Icons.Outlined.EditCalendar, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    it()
                                },
                            )
                        }
                        onChangeLocation?.let {
                            DropdownMenuItem(
                                text = { Text("Change location") },
                                leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    it()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditDateTimeDialog(
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val cal = remember(initialDateMillis) {
        java.util.Calendar.getInstance().apply {
            timeInMillis = if (initialDateMillis > 0) initialDateMillis else System.currentTimeMillis()
        }
    }
    var year by remember { mutableIntStateOf(cal.get(java.util.Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(cal.get(java.util.Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(cal.get(java.util.Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableIntStateOf(cal.get(java.util.Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(cal.get(java.util.Calendar.MINUTE)) }

    fun calculateMillis(): Long {
        val c = java.util.Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit date and time", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Adjust the capture timestamp for selected items:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Date row: Year, Month, Day
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = year.toString(),
                        onValueChange = { str -> str.filter { it.isDigit() }.take(4).toIntOrNull()?.let { year = it } },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f),
                    )
                    OutlinedTextField(
                        value = month.toString(),
                        onValueChange = { str -> str.filter { it.isDigit() }.take(2).toIntOrNull()?.let { month = it.coerceIn(1, 12) } },
                        label = { Text("Month") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = day.toString(),
                        onValueChange = { str -> str.filter { it.isDigit() }.take(2).toIntOrNull()?.let { day = it.coerceIn(1, 31) } },
                        label = { Text("Day") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Time row: Hour, Minute
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = hour.toString().padStart(2, '0'),
                        onValueChange = { str -> str.filter { it.isDigit() }.take(2).toIntOrNull()?.let { hour = it.coerceIn(0, 23) } },
                        label = { Text("Hour (0-23)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minute.toString().padStart(2, '0'),
                        onValueChange = { str -> str.filter { it.isDigit() }.take(2).toIntOrNull()?.let { minute = it.coerceIn(0, 59) } },
                        label = { Text("Minute (0-59)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Quick Offset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AssistChip(
                        onClick = {
                            val c = java.util.Calendar.getInstance().apply { timeInMillis = calculateMillis(); add(java.util.Calendar.HOUR_OF_DAY, 1) }
                            year = c.get(java.util.Calendar.YEAR)
                            month = c.get(java.util.Calendar.MONTH) + 1
                            day = c.get(java.util.Calendar.DAY_OF_MONTH)
                            hour = c.get(java.util.Calendar.HOUR_OF_DAY)
                            minute = c.get(java.util.Calendar.MINUTE)
                        },
                        label = { Text("+1h") },
                    )
                    AssistChip(
                        onClick = {
                            val c = java.util.Calendar.getInstance().apply { timeInMillis = calculateMillis(); add(java.util.Calendar.HOUR_OF_DAY, -1) }
                            year = c.get(java.util.Calendar.YEAR)
                            month = c.get(java.util.Calendar.MONTH) + 1
                            day = c.get(java.util.Calendar.DAY_OF_MONTH)
                            hour = c.get(java.util.Calendar.HOUR_OF_DAY)
                            minute = c.get(java.util.Calendar.MINUTE)
                        },
                        label = { Text("-1h") },
                    )
                    AssistChip(
                        onClick = {
                            val c = java.util.Calendar.getInstance().apply { timeInMillis = calculateMillis(); add(java.util.Calendar.DAY_OF_MONTH, 1) }
                            year = c.get(java.util.Calendar.YEAR)
                            month = c.get(java.util.Calendar.MONTH) + 1
                            day = c.get(java.util.Calendar.DAY_OF_MONTH)
                            hour = c.get(java.util.Calendar.HOUR_OF_DAY)
                            minute = c.get(java.util.Calendar.MINUTE)
                        },
                        label = { Text("+1d") },
                    )
                    AssistChip(
                        onClick = {
                            val c = java.util.Calendar.getInstance().apply { timeInMillis = calculateMillis(); add(java.util.Calendar.DAY_OF_MONTH, -1) }
                            year = c.get(java.util.Calendar.YEAR)
                            month = c.get(java.util.Calendar.MONTH) + 1
                            day = c.get(java.util.Calendar.DAY_OF_MONTH)
                            hour = c.get(java.util.Calendar.HOUR_OF_DAY)
                            minute = c.get(java.util.Calendar.MINUTE)
                        },
                        label = { Text("-1d") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(calculateMillis()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ChangeLocationDialog(
    onDismiss: () -> Unit,
    onConfirm: (latitude: Double?, longitude: Double?) -> Unit,
) {
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change location", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Enter GPS coordinates for selected items or remove location completely.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude (e.g. 37.7749)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("Longitude (e.g. -122.4194)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onConfirm(null, null) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Outlined.LocationOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove location", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            val lat = latText.toDoubleOrNull()
            val lng = lngText.toDoubleOrNull()
            TextButton(
                enabled = lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0,
                onClick = { onConfirm(lat, lng) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TrashSelectionActionBar(
    onRestore: () -> Unit,
    onCompressToKeep: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    val hazeState = LocalHazeState.current
    val pillShape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.7f),
                    spotColor = Color.Black.copy(alpha = 0.8f),
                )
                .oneUiGlass(shape = pillShape, state = hazeState, tintAlpha = 0.54f, blurRadius = 36.dp),
            shape = pillShape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionAction(
                    label = "Restore",
                    icon = Icons.Outlined.Restore,
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.primary,
                )
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SelectionAction(
                    label = "Compress & Keep",
                    icon = Icons.Outlined.Compress,
                    onClick = onCompressToKeep,
                    modifier = Modifier.weight(1.3f),
                    tint = Color(0xFF00E676),
                )
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SelectionAction(
                    label = "Delete",
                    icon = Icons.Outlined.DeleteForever,
                    onClick = onDeletePermanently,
                    modifier = Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SelectionAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Sort dropdown menu.
 */
@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    currentSort: SortOrder,
    currentFilter: MediaContentFilter,
    onDismiss: () -> Unit,
    onSortSelected: (SortOrder) -> Unit,
    onFilterSelected: (MediaContentFilter) -> Unit,
) {
    PandaDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MediaContentFilter.entries.forEach { filter ->
            DropdownMenuItem(
                text = { Text(filter.contentFilterLabel()) },
                onClick = { onFilterSelected(filter) },
                leadingIcon = {
                    if (filter == currentFilter) Icon(Icons.Filled.Check, contentDescription = null)
                },
            )
        }
        HorizontalDivider()
        SortOrder.entries.forEach { sort ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = sort.label,
                        fontWeight = if (sort == currentSort) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = { onSortSelected(sort) },
                leadingIcon = {
                    if (sort == currentSort) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFiltersSheet(
    criteria: MediaSearchCriteria,
    albums: List<com.pandagallery.app.domain.model.Album>,
    onDismiss: () -> Unit,
    onApply: (MediaSearchCriteria) -> Unit,
) {
    var draft by remember(criteria) { mutableStateOf(criteria) }
    val now = remember { System.currentTimeMillis() }
    PandaModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Search filters", style = MaterialTheme.typography.titleLarge)
            SearchFilterGroup("Media type") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchMediaKind.entries.forEach { kind ->
                        FilterChip(
                            selected = draft.mediaKind == kind,
                            onClick = { draft = draft.copy(mediaKind = kind) },
                            label = { Text(kind.name.lowercase().replaceFirstChar(Char::titlecase)) },
                        )
                    }
                }
            }
            SearchFilterGroup("Date taken") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "Any time" to null,
                        "7 days" to now - 7L * DAY_MILLIS,
                        "30 days" to now - 30L * DAY_MILLIS,
                        "1 year" to now - 365L * DAY_MILLIS,
                    ).forEach { (label, start) ->
                        FilterChip(
                            selected = draft.takenAfter == start,
                            onClick = { draft = draft.copy(takenAfter = start) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            SearchFilterGroup("Minimum size") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Any" to null, "10 MB" to 10L * MB, "50 MB" to 50L * MB, "100 MB" to 100L * MB)
                        .forEach { (label, bytes) ->
                            FilterChip(
                                selected = draft.minimumBytes == bytes,
                                onClick = { draft = draft.copy(minimumBytes = bytes) },
                                label = { Text(label) },
                            )
                        }
                }
            }
            SearchFilterGroup("Album") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = draft.albumPath == null,
                        onClick = { draft = draft.copy(albumPath = null) },
                        label = { Text("All albums") },
                    )
                    albums.forEach { album ->
                        val path = album.relativePath ?: return@forEach
                        FilterChip(
                            selected = draft.albumPath == path,
                            onClick = { draft = draft.copy(albumPath = path) },
                            label = { Text(album.name) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { draft = draft.copy(favoriteOnly = !draft.favoriteOnly) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = draft.favoriteOnly, onCheckedChange = null)
                Text("Favorites only", modifier = Modifier.padding(start = 12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = { draft = MediaSearchCriteria(query = draft.query) }) { Text("Reset") }
                Button(onClick = { onApply(draft) }) { Text("Show results") }
            }
        }
    }
}

@Composable
private fun SearchFilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

private fun MediaContentFilter.contentFilterLabel(): String = when (this) {
    MediaContentFilter.ALL -> "All media"
    MediaContentFilter.IMAGES -> "Images"
    MediaContentFilter.VIDEOS -> "Videos"
    MediaContentFilter.PORTRAITS -> "Portraits"
    MediaContentFilter.RAW -> "RAW"
    MediaContentFilter.SLOW_MO -> "Slow-Mo"
    MediaContentFilter.SCREENSHOTS -> "Screenshots"
    MediaContentFilter.GIFS -> "GIFs"
    MediaContentFilter.LARGE_FILES -> "Large files (50 MB+)"
}

private const val MB = 1024L * 1024L
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

/**
 * The empty search state.
 *
 * Smart search is invisible until someone guesses the right word, so this shows what the
 * on-device index actually found — which teaches the feature and doubles as one-tap
 * browsing — alongside what they searched for before.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchDiscovery(
    history: List<String>,
    labels: List<LabelSuggestion>,
    people: List<PersonSuggestion>,
    onQuery: (String) -> Unit,
    onForget: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty() && labels.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Samsung Shot Types & Categories Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Shot types & categories",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    listOf(
                        Triple("Videos", Icons.Outlined.VideoLibrary, "video"),
                        Triple("Favorites", Icons.Filled.Favorite, "favorite"),
                        Triple("Locations", Icons.Outlined.Place, "locations"),
                        Triple("Screenshots", Icons.Outlined.Screenshot, "screenshot"),
                        Triple("Documents", Icons.Outlined.Description, "document"),
                        Triple("GIFs", Icons.Outlined.GifBox, "gif"),
                        Triple("Compressed", Icons.Outlined.Storage, "compressed"),
                    )
                ) { (title, icon, query) ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .width(88.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onQuery(query) },
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // Samsung Scene Tags
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Scene categories",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    listOf("🌅 Sunsets", "🍲 Food & Drinks", "🐱 Pets", "🏖️ Nature & Beach", "🏛️ Architecture", "🌸 Flowers", "🚗 Vehicles", "📄 Text")
                ) { tag ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                val cleanTag = tag.substringAfter(" ")
                                onQuery(cleanTag)
                            },
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        if (people.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "People",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(people, key = { it.name }) { person ->
                        PersonSearchChip(person = person, onClick = { onQuery(person.name) })
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearHistory) { Text("Clear") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEach { entry ->
                        InputChip(
                            selected = false,
                            onClick = { onQuery(entry) },
                            label = { Text(entry) },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Remove $entry from recent searches",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onForget(entry) },
                                )
                            },
                        )
                    }
                }
            }
        }
        if (labels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Found in your library",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { onQuery(suggestion.label) },
                            label = { Text("${suggestion.label} · ${suggestion.count}") },
                        )
                    }
                }
            }
        }
    }
}

/** A face and a name; tapping it searches for that person by name. */
@Composable
private fun PersonSearchChip(person: PersonSuggestion, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
    ) {
        if (person.faceUri != null) {
            val context = LocalContext.current
            val faceRequest = remember(person.faceUri, person.face) {
                ImageRequest.Builder(context)
                    .data(person.faceUri)
                    .size(192)
                    .crossfade(false)
                    .transformations(
                        FaceCropTransformation(
                            person.face.left,
                            person.face.top,
                            person.face.right,
                            person.face.bottom,
                        ),
                    )
                    .build()
            }
            AsyncImage(
                model = faceRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The search input.
 *
 * Built from a [BasicTextField] rather than an `OutlinedTextField` because this lives in
 * the app bar's title slot: a Material text field brings a 56dp minimum height and its own
 * label/placeholder layout, and inside that constrained slot the placeholder wrapped onto a
 * second line and pushed the outline out of shape. A fixed-height pill is both the correct
 * search-bar affordance and predictable in a space this tight.
 */
@Composable
private fun GallerySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Surface(
        modifier = modifier.height(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search photos",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearch()
                            keyboardController?.hide()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShotTypesFilterRow(
    currentFilter: MediaContentFilter,
    onFilterSelected: (MediaContentFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(MediaContentFilter.entries) { filter ->
            val isSelected = filter == currentFilter
            val icon = when (filter) {
                MediaContentFilter.ALL -> Icons.Outlined.AllInclusive
                MediaContentFilter.IMAGES -> Icons.Outlined.Image
                MediaContentFilter.VIDEOS -> Icons.Outlined.Videocam
                MediaContentFilter.PORTRAITS -> Icons.Outlined.Face
                MediaContentFilter.RAW -> Icons.Outlined.CameraAlt
                MediaContentFilter.SLOW_MO -> Icons.Outlined.SlowMotionVideo
                MediaContentFilter.SCREENSHOTS -> Icons.Outlined.PhoneAndroid
                MediaContentFilter.GIFS -> Icons.Outlined.Gif
                MediaContentFilter.LARGE_FILES -> Icons.Outlined.FolderZip
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.8.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onFilterSelected(filter)
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = filter.contentFilterLabel(),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryHighlightStrip(
    stories: List<StoryHighlight>,
    onStoryClick: (StoryHighlight) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Stories & Memories",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            items(stories, key = { it.id }) { story ->
                StoryHighlightCard(story = story, onClick = { onStoryClick(story) })
            }
        }
    }
}

@Composable
private fun StoryHighlightCard(
    story: StoryHighlight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ken_burns")
    val kenBurnsScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ken_burns_scale",
    )

    Box(
        modifier = modifier
            .width(170.dp)
            .height(230.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick),
    ) {
        if (story.coverUri != null) {
            val context = LocalContext.current
            val storyCoverRequest = remember(story.coverUri) {
                ImageRequest.Builder(context)
                    .data(story.coverUri)
                    .size(512)
                    .crossfade(true)
                    .memoryCacheKey("${story.coverUri}_thumb")
                    .placeholderMemoryCacheKey("${story.coverUri}_thumb")
                    .build()
            }
            AsyncImage(
                model = storyCoverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = kenBurnsScale
                        scaleY = kenBurnsScale
                    },
            )
        }

        // Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f),
                        )
                    )
                )
        )

        // Floating Story Badge
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(10.dp)
                .align(Alignment.TopStart),
        ) {
            Text(
                text = story.badge,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        // Title and Subtitle at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = story.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = story.subtitle,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

/**
 * Checks if the media item (or its target grid index) is currently within
 * any visible row of the lazy grid layout.
 */
internal fun isMediaItemVisible(
    visibleKeys: Collection<Any>,
    visibleIndices: Collection<Int>,
    mediaId: Long,
    targetIndex: Int,
): Boolean {
    val targetKey = "media_$mediaId"
    return targetKey in visibleKeys || targetIndex in visibleIndices
}

internal fun isMediaItemVisibleInGrid(
    layoutInfo: LazyGridLayoutInfo,
    mediaId: Long,
    targetIndex: Int,
): Boolean {
    val targetKey = "media_$mediaId"
    return layoutInfo.visibleItemsInfo.any {
        it.key == targetKey || it.index == targetIndex
    }
}

