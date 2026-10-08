package com.pandagallery.app.ui.albums

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.pandagallery.app.domain.model.MediaItem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.pandagallery.app.domain.model.AlbumDisplayItem
import com.pandagallery.app.domain.model.clusterAlbumsForDisplay
import com.pandagallery.app.ui.components.AlbumCard
import com.pandagallery.app.ui.components.AlbumGroupCard
import com.pandagallery.app.ui.components.AlbumGroupListRow
import com.pandagallery.app.ui.components.AlbumListRow
import com.pandagallery.app.ui.components.SeslGoToTopButton
import com.pandagallery.app.ui.components.SeslReachabilityHeader
import com.pandagallery.app.ui.components.adaptiveColumnCount
import com.pandagallery.app.ui.components.PandaDropdownMenu
import com.pandagallery.app.ui.components.PremiumAlertDialog
import com.pandagallery.app.data.export.albumExportFileName
import com.pandagallery.app.domain.model.AlbumGridDensity
import com.pandagallery.app.domain.model.zoomIn
import com.pandagallery.app.domain.model.zoomOut
import com.pandagallery.app.ui.components.pinchToResizeGrid
import com.pandagallery.app.domain.model.Album
import com.pandagallery.app.data.security.PinVerification
import com.pandagallery.app.domain.model.AlbumSortOrder
import com.pandagallery.app.domain.model.formatFileSize
import com.pandagallery.app.domain.model.folderNeedsPin
import com.pandagallery.app.ui.security.PinSetupDialog
import com.pandagallery.app.ui.security.rememberPinLockoutMessage
import com.pandagallery.app.ui.security.PinVerifyDialog
import com.pandagallery.app.ui.security.wrongPinMessage
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.draw.shadow
import com.pandagallery.app.domain.model.SharedAlbum
import com.pandagallery.app.domain.model.SharedAlbumMember
import com.pandagallery.app.domain.model.label
import com.pandagallery.app.ui.photos.SelectionTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onAlbumClick: (Long, String) -> Unit,
    onMenuClick: () -> Unit,
    onSearchMedia: () -> Unit,
    onCreateCollage: () -> Unit,
    onVideosClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onSelectionModeChanged: (Boolean) -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingMutation by viewModel.pendingMutation.collectAsStateWithLifecycle()
    val compressionPrompt by viewModel.compressionPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // What makes the reveal temporary: leaving the screen or backgrounding the app puts
    // hidden albums back out of sight, so a reveal can never outlive the moment it was for.
    val pendingExport by viewModel.pendingExport.collectAsStateWithLifecycle()
    // Locks are per-session: opening a locked album asks once, then stays open until the
    // screen is left, matching how the Private Album behaves.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var unlockedAlbumIds by remember { mutableStateOf(emptySet<Long>()) }
    var albumAwaitingUnlock by remember { mutableStateOf<Album?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        if (destination == null) viewModel.cancelExport() else viewModel.exportTo(destination)
    }

    LaunchedEffect(pendingExport) {
        pendingExport?.let { exportLauncher.launch(albumExportFileName(it.album.name)) }
    }

    var pinError by remember { mutableStateOf<String?>(null) }
    // Set when locking a folder finds no PIN yet: the PIN has to exist before the lock means
    // anything, so choosing one is part of locking the first folder.
    var albumAwaitingNewPin by remember { mutableStateOf<Album?>(null) }
    var albumAwaitingLockRemoval by remember { mutableStateOf<Album?>(null) }

    val lockoutMessage = rememberPinLockoutMessage(
        active = albumAwaitingUnlock != null || albumAwaitingLockRemoval != null,
        remainingMillis = viewModel::folderLockoutRemainingMillis,
    )

    /** The fingerprint shortcut past the folder PIN, offered only when the user turned it on. */
    fun unlockWithBiometrics(album: Album) {
        val activity = context as? androidx.fragment.app.FragmentActivity ?: return
        androidx.biometric.BiometricPrompt(
            activity,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: androidx.biometric.BiometricPrompt.AuthenticationResult,
                ) {
                    unlockedAlbumIds = unlockedAlbumIds + album.id
                    albumAwaitingUnlock = null
                    pinError = null
                    onAlbumClick(album.id, album.name)
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) = Unit

                override fun onAuthenticationFailed() = Unit
            },
        ).authenticate(
            androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock ${album.name}")
                .setSubtitle("Or cancel to enter your folder PIN")
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
                .setNegativeButtonText("Use PIN")
                .build()
        )
    }

    albumAwaitingUnlock?.let { album ->
        if (uiState.hasFolderLockPin) {
            PinVerifyDialog(
                title = "Unlock ${album.name}",
                message = "Enter your folder lock PIN.",
                error = pinError,
                lockoutMessage = lockoutMessage,
                onSubmit = { pin ->
                    scope.launch {
                        when (val result = viewModel.verifyFolderPin(pin)) {
                            PinVerification.Success -> {
                                unlockedAlbumIds = unlockedAlbumIds + album.id
                                albumAwaitingUnlock = null
                                pinError = null
                                onAlbumClick(album.id, album.name)
                            }
                            is PinVerification.Wrong -> pinError = wrongPinMessage(result)
                            is PinVerification.LockedOut -> pinError = null
                        }
                    }
                },
                onDismiss = { albumAwaitingUnlock = null; pinError = null },
                onUseBiometrics = if (uiState.folderLockBiometricsEnabled) {
                    { unlockWithBiometrics(album) }
                } else {
                    null
                },
            )
        } else {
            // A folder marked locked while no PIN exists — possible for anyone upgrading from the
            // device-lock version. Opening it sets the PIN rather than refusing forever.
            PinSetupDialog(
                title = "Set folder lock PIN",
                onPinChosen = { pin ->
                    scope.launch {
                        viewModel.setFolderPin(pin)
                        unlockedAlbumIds = unlockedAlbumIds + album.id
                        albumAwaitingUnlock = null
                        onAlbumClick(album.id, album.name)
                    }
                },
                onDismiss = { albumAwaitingUnlock = null },
            )
        }
    }

    albumAwaitingLockRemoval?.let { album ->
        PinVerifyDialog(
            title = "Unlock ${album.name}",
            message = "Enter your folder lock PIN to remove the lock from this folder.",
            error = pinError,
            lockoutMessage = lockoutMessage,
            onSubmit = { pin ->
                scope.launch {
                    when (val result = viewModel.verifyFolderPin(pin)) {
                        PinVerification.Success -> {
                            viewModel.setLocked(album, false)
                            albumAwaitingLockRemoval = null
                            pinError = null
                        }
                        is PinVerification.Wrong -> pinError = wrongPinMessage(result)
                        is PinVerification.LockedOut -> pinError = null
                    }
                }
            },
            onDismiss = { albumAwaitingLockRemoval = null; pinError = null },
        )
    }

    albumAwaitingNewPin?.let { album ->
        PinSetupDialog(
            title = "Set folder lock PIN",
            onPinChosen = { pin ->
                scope.launch {
                    viewModel.setFolderPin(pin)
                    viewModel.setLocked(album, true)
                    albumAwaitingNewPin = null
                }
            },
            onDismiss = { albumAwaitingNewPin = null },
        )
    }

    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            viewModel.concealHiddenAlbums()
            // Re-lock on the way out; an unlock should not outlive the visit.
            unlockedAlbumIds = emptySet()
        }
    }
    var showCreatePlusMenu by remember { mutableStateOf(false) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateSharedAlbumDialog by remember { mutableStateOf(false) }
    var showMoveToGroupDialog by remember { mutableStateOf(false) }
    var renameGroupName by remember { mutableStateOf<String?>(null) }
    var dissolveGroupName by remember { mutableStateOf<String?>(null) }
    var showHideAlbumsSheet by remember { mutableStateOf(false) }
    var showMergeAlbumsDialog by remember { mutableStateOf(false) }
    var activeSharedAlbum by remember { mutableStateOf<SharedAlbum?>(null) }
    var pendingAddPhotosAlbumId by remember { mutableStateOf<String?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        val albumId = pendingAddPhotosAlbumId
        if (albumId != null && uris.isNotEmpty()) {
            val newItems = uris.mapIndexed { idx, uri ->
                val id = System.currentTimeMillis() + idx
                MediaItem(
                    id = id,
                    uri = uri,
                    displayName = uri.lastPathSegment ?: "Photo_${id}.jpg",
                    mimeType = "image/*",
                    size = 0L,
                    width = 0,
                    height = 0,
                    dateAdded = System.currentTimeMillis() / 1000L,
                    dateModified = System.currentTimeMillis() / 1000L,
                    dateTaken = System.currentTimeMillis(),
                )
            }
            viewModel.addPhotosToSharedAlbum(albumId, newItems)
        }
        pendingAddPhotosAlbumId = null
    }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAlbumIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val isSelectionMode = selectedAlbumIds.isNotEmpty()
    val selectedAlbums = remember(selectedAlbumIds, uiState.albums) {
        uiState.albums.filter { it.id in selectedAlbumIds }
    }
    var renameAlbum by remember { mutableStateOf<Album?>(null) }
    var deleteAlbums by remember { mutableStateOf<List<Album>?>(null) }
    var showAlbumMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val mutationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.completePendingMutation(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(pendingMutation) {
        pendingMutation?.let { pending ->
            try {
                mutationLauncher.launch(IntentSenderRequest.Builder(pending.pendingIntent.intentSender).build())
            } catch (e: Exception) {
                com.pandagallery.app.data.diagnostics.CrashLog.e("AlbumsScreen", "Could not show the system confirmation", e)
                viewModel.completePendingMutation(approved = false)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }
    val filteredAlbums = remember(uiState.albums, searchQuery) {
        if (searchQuery.isBlank()) uiState.albums
        else uiState.albums.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>("All") }
    val availableGroups = remember(uiState.albums) {
        val customGroups = uiState.albums.mapNotNull { it.groupName }.distinct()
        if (customGroups.isEmpty()) listOf("All", "Camera", "Screenshots", "Downloads")
        else listOf("All") + customGroups
    }
    val isInsideGroup = selectedGroup != null && selectedGroup != "All"
    val groupFilteredAlbums = remember(filteredAlbums, selectedGroup) {
        when (selectedGroup) {
            null, "All" -> filteredAlbums
            "Camera" -> filteredAlbums.filter { it.name.contains("Camera", ignoreCase = true) || it.name.contains("DCIM", ignoreCase = true) }
            "Screenshots" -> filteredAlbums.filter { it.name.contains("Screenshot", ignoreCase = true) }
            "Downloads" -> filteredAlbums.filter { it.name.contains("Download", ignoreCase = true) }
            else -> filteredAlbums.filter { it.groupName == selectedGroup }
        }
    }
    val rootDisplayItems = remember(filteredAlbums, uiState.sortOrder) {
        clusterAlbumsForDisplay(filteredAlbums, uiState.sortOrder)
    }
    val hierarchyItems = remember(rootDisplayItems, uiState.expandedGroupNames, uiState.isHierarchyView) {
        if (!uiState.isHierarchyView) {
            emptyList()
        } else {
            val list = mutableListOf<HierarchyDisplayItem>()
            for (item in rootDisplayItems) {
                when (item) {
                    is AlbumDisplayItem.Group -> {
                        val isExpanded = item.name in uiState.expandedGroupNames
                        list.add(HierarchyDisplayItem.GroupHeader(item, isExpanded))
                        if (isExpanded) {
                            for (album in item.albums) {
                                list.add(HierarchyDisplayItem.GroupNestedAlbum(album, item.name))
                            }
                        }
                    }
                    is AlbumDisplayItem.Single -> {
                        list.add(HierarchyDisplayItem.StandaloneAlbum(item.album))
                    }
                }
            }
            list
        }
    }
    BackHandler(enabled = isSelectionMode) { selectedAlbumIds = emptySet() }
    BackHandler(enabled = !isSelectionMode && isInsideGroup) { selectedGroup = "All" }
    LaunchedEffect(isSelectionMode) {
        onSelectionModeChanged(isSelectionMode)
    }
    DisposableEffect(Unit) {
        onDispose { onSelectionModeChanged(false) }
    }

    val albumsGridState = rememberLazyGridState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                val currentPool = if (isInsideGroup) groupFilteredAlbums else filteredAlbums
                val allSelected = currentPool.isNotEmpty() && selectedAlbumIds.size == currentPool.size
                SelectionTopBar(
                    selectedCount = selectedAlbumIds.size,
                    allSelected = allSelected,
                    onClose = { selectedAlbumIds = emptySet() },
                    onToggleSelectAll = {
                        selectedAlbumIds = if (allSelected) {
                            emptySet()
                        } else {
                            currentPool.map { it.id }.toSet()
                        }
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        if (isInsideGroup) {
                            IconButton(onClick = { selectedGroup = "All" }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to albums",
                                )
                            }
                        }
                    },
                    title = {
                        if (isInsideGroup) {
                            Text(
                                text = selectedGroup ?: "Group",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            AnimatedVisibility(
                                visible = albumsGridState.firstVisibleItemIndex > 0,
                                enter = fadeIn(tween(160)),
                                exit = fadeOut(tween(160)),
                            ) {
                                Text(
                                    text = "Albums",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    },
                    actions = {
                        if (isInsideGroup) {
                            val isCustomGroup = selectedGroup !in listOf("Camera", "Screenshots", "Downloads")
                            if (isCustomGroup) {
                                IconButton(onClick = { selectedGroup?.let { viewModel.compressGroup(it) } }) {
                                    Icon(Icons.Outlined.Compress, contentDescription = "Compress group")
                                }
                            }
                            IconButton(onClick = {
                                viewModel.setGridDensity(
                                    if (uiState.gridDensity == AlbumGridDensity.LIST) AlbumGridDensity.NORMAL
                                    else AlbumGridDensity.LIST
                                )
                            }) {
                                Icon(
                                    imageVector = if (uiState.gridDensity == AlbumGridDensity.LIST) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                                    contentDescription = "Toggle view mode",
                                )
                            }
                            IconButton(onClick = onSearchMedia) {
                                Icon(Icons.Outlined.Search, contentDescription = "Search media")
                            }
                            Box {
                                IconButton(onClick = { showAlbumMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "Group options")
                                }
                                PandaDropdownMenu(expanded = showAlbumMenu, onDismissRequest = { showAlbumMenu = false }) {
                                    if (isCustomGroup) {
                                        DropdownMenuItem(
                                            text = { Text("Compress group") },
                                            leadingIcon = { Icon(Icons.Outlined.Compress, contentDescription = null) },
                                            onClick = {
                                                showAlbumMenu = false
                                                selectedGroup?.let { viewModel.compressGroup(it) }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename group") },
                                            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                                            onClick = {
                                                val grp = selectedGroup
                                                showAlbumMenu = false
                                                renameGroupName = grp
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Dissolve group") },
                                            leadingIcon = { Icon(Icons.Outlined.FolderOff, contentDescription = null) },
                                            onClick = {
                                                val grp = selectedGroup
                                                showAlbumMenu = false
                                                dissolveGroupName = grp
                                            },
                                        )
                                        HorizontalDivider()
                                    }
                                    AlbumSortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.label) },
                                            leadingIcon = {
                                                if (order == uiState.sortOrder) Icon(Icons.Filled.Check, contentDescription = null)
                                            },
                                            onClick = {
                                                viewModel.setSortOrder(order)
                                                showAlbumMenu = false
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                        onClick = {
                                            showAlbumMenu = false
                                            onMenuClick()
                                        },
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = {
                                viewModel.setGridDensity(
                                    if (uiState.gridDensity == AlbumGridDensity.LIST) AlbumGridDensity.NORMAL
                                    else AlbumGridDensity.LIST
                                )
                            }) {
                                Icon(
                                    imageVector = if (uiState.gridDensity == AlbumGridDensity.LIST) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                                    contentDescription = "Toggle view mode",
                                )
                            }
                            Box {
                                IconButton(onClick = { showCreatePlusMenu = true }) {
                                    Icon(Icons.Outlined.Add, contentDescription = "Create album or group")
                                }
                                PandaDropdownMenu(expanded = showCreatePlusMenu, onDismissRequest = { showCreatePlusMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Album") },
                                        leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                        onClick = {
                                            showCreatePlusMenu = false
                                            showCreateAlbumDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Group") },
                                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                                        onClick = {
                                            showCreatePlusMenu = false
                                            showCreateGroupDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Shared album") },
                                        leadingIcon = { Icon(Icons.Outlined.People, contentDescription = null) },
                                        onClick = {
                                            showCreatePlusMenu = false
                                            showCreateSharedAlbumDialog = true
                                        },
                                    )
                                }
                            }
                            IconButton(onClick = onSearchMedia) {
                                Icon(Icons.Outlined.Search, contentDescription = "Search media")
                            }
                            Box {
                                IconButton(onClick = { showAlbumMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "Album options")
                                }
                                PandaDropdownMenu(expanded = showAlbumMenu, onDismissRequest = { showAlbumMenu = false }) {
                                    AlbumSortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.label) },
                                            leadingIcon = {
                                                if (order == uiState.sortOrder) Icon(Icons.Filled.Check, contentDescription = null)
                                            },
                                            onClick = {
                                                viewModel.setSortOrder(order)
                                                showAlbumMenu = false
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Create collage") },
                                        leadingIcon = { Icon(Icons.Outlined.GridView, contentDescription = null) },
                                        onClick = {
                                            showAlbumMenu = false
                                            onCreateCollage()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Search albums") },
                                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                        onClick = {
                                            showAlbumMenu = false
                                            showSearchDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Hide or unhide albums") },
                                        leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                                        onClick = {
                                            showAlbumMenu = false
                                            showHideAlbumsSheet = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (uiState.isHierarchyView) "View style: Clustered cards" else "View style: Collapsible hierarchy") },
                                        leadingIcon = { Icon(if (uiState.isHierarchyView) Icons.Outlined.Dashboard else Icons.Outlined.FolderCopy, contentDescription = null) },
                                        onClick = {
                                            showAlbumMenu = false
                                            viewModel.setHierarchyView(!uiState.isHierarchyView)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when {
                                                    uiState.isRevealingHidden -> "Hide hidden albums again"
                                                    uiState.hiddenAlbums.isEmpty() -> "No hidden albums"
                                                    else -> "Show hidden albums (${uiState.hiddenAlbums.size})"
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (uiState.isRevealingHidden) Icons.Outlined.VisibilityOff
                                                else Icons.Outlined.Visibility,
                                                contentDescription = null,
                                            )
                                        },
                                        enabled = uiState.isRevealingHidden || uiState.hiddenAlbums.isNotEmpty(),
                                        onClick = {
                                            showAlbumMenu = false
                                            viewModel.setHiddenAlbumsRevealed(!uiState.isRevealingHidden)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                        onClick = {
                                            showAlbumMenu = false
                                            onMenuClick()
                                        },
                                    )
                                }
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = {
            if (isSelectionMode && selectedAlbums.isNotEmpty()) {
                AlbumSelectionActionBar(
                    selectedAlbums = selectedAlbums,
                    isInsideGroup = isInsideGroup,
                    onUngroup = {
                        viewModel.moveAlbumsToGroup(selectedAlbumIds, null)
                        selectedAlbumIds = emptySet()
                    },
                    onPin = {
                        selectedAlbums.forEach { viewModel.togglePinned(it) }
                        selectedAlbumIds = emptySet()
                    },
                    onCover = {
                        selectedAlbums.firstOrNull()?.let { viewModel.loadCoverChoices(it) }
                        selectedAlbumIds = emptySet()
                    },
                    onHide = {
                        val anyHidden = selectedAlbums.any { it.isHidden }
                        selectedAlbums.forEach { viewModel.setHidden(it, !anyHidden) }
                        selectedAlbumIds = emptySet()
                    },
                    onRename = {
                        selectedAlbums.firstOrNull()?.let { renameAlbum = it }
                        selectedAlbumIds = emptySet()
                    },
                    onLock = {
                        // The selection can outlive its albums (emptied by a batch move or trash).
                        val first = selectedAlbums.firstOrNull()
                        when {
                            first == null -> Unit
                            first.isLocked && !uiState.hasFolderLockPin ->
                                selectedAlbums.forEach { viewModel.setLocked(it, false) }
                            first.isLocked -> albumAwaitingLockRemoval = first
                            !uiState.hasFolderLockPin -> albumAwaitingNewPin = first
                            else -> selectedAlbums.forEach { viewModel.setLocked(it, true) }
                        }
                        selectedAlbumIds = emptySet()
                    },
                    onExport = {
                        selectedAlbums.firstOrNull()?.let { viewModel.prepareExport(it) }
                        selectedAlbumIds = emptySet()
                    },
                    onCompress = {
                        viewModel.compressAlbums(selectedAlbums)
                        selectedAlbumIds = emptySet()
                    },
                    onMoveToGroup = {
                        showMoveToGroupDialog = true
                    },
                    onMerge = {
                        showMergeAlbumsDialog = true
                    },
                    onDelete = {
                        deleteAlbums = selectedAlbums
                        selectedAlbumIds = emptySet()
                    },
                )
            }
        },
    ) { padding ->
        if (uiState.isLoading && uiState.albums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding),
            ) {
                LazyVerticalGrid(
                    state = albumsGridState,
                    columns = GridCells.Fixed(adaptiveColumnCount(uiState.gridDensity.columns, maxWidth)),
                    modifier = Modifier
                        .fillMaxSize()
                        .pinchToResizeGrid(
                            enabled = !isSelectionMode,
                            onZoomIn = { viewModel.setGridDensity(uiState.gridDensity.zoomIn()) },
                            onZoomOut = { viewModel.setGridDensity(uiState.gridDensity.zoomOut()) },
                        ),
                    contentPadding = PaddingValues(start = 10.dp, top = 0.dp, end = 10.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                uiState.exportProgress?.let { progress ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Exporting album… ${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (uiState.isRevealingHidden) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Showing ${uiState.hiddenAlbums.size} hidden " +
                                        if (uiState.hiddenAlbums.size == 1) "album" else "albums",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.setHiddenAlbumsRevealed(false) }) {
                                    Text("Hide again")
                                }
                            }
                        }
                    }
                }

                if (isInsideGroup) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SeslReachabilityHeader(
                            title = selectedGroup ?: "",
                            subtitle = "${groupFilteredAlbums.size} albums • ${groupFilteredAlbums.sumOf { it.mediaCount }} items",
                            modifier = Modifier.padding(horizontal = 0.dp, vertical = 2.dp),
                        )
                    }

                    if (groupFilteredAlbums.isNotEmpty()) {
                        items(
                            items = groupFilteredAlbums,
                            key = { it.id },
                        ) { album ->
                            if (uiState.gridDensity == AlbumGridDensity.LIST) {
                                AlbumListRow(
                                    name = album.name,
                                    coverUri = album.coverUri,
                                    mediaCount = album.mediaCount,
                                    sizeLabel = album.sizeBytes
                                        .takeIf { uiState.showAlbumSize && it > 0L }
                                        ?.let(::formatFileSize),
                                    onClick = {
                                        when {
                                            isSelectionMode -> {
                                                selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                    selectedAlbumIds - album.id
                                                } else {
                                                    selectedAlbumIds + album.id
                                                }
                                            }
                                            folderNeedsPin(
                                                isLocked = album.isLocked,
                                                hasFolderLockPin = uiState.hasFolderLockPin,
                                                isUnlockedThisSession = album.id in unlockedAlbumIds,
                                            ) -> albumAwaitingUnlock = album
                                            else -> onAlbumClick(album.id, album.name)
                                        }
                                    },
                                    onLongClick = {
                                        selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                            selectedAlbumIds - album.id
                                        } else {
                                            selectedAlbumIds + album.id
                                        }
                                    },
                                    modifier = Modifier.animateItem(),
                                    isSelected = album.id in selectedAlbumIds,
                                    isPinned = album.isPinned,
                                    isHidden = album.isHidden,
                                    isLocked = album.isLocked && uiState.hasFolderLockPin,
                                )
                            } else {
                                AlbumCard(
                                    name = album.name,
                                    coverUri = album.coverUri,
                                    mediaCount = album.mediaCount,
                                    sizeLabel = album.sizeBytes
                                        .takeIf { uiState.showAlbumSize && it > 0L }
                                        ?.let(::formatFileSize),
                                    onClick = {
                                        when {
                                            isSelectionMode -> {
                                                selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                    selectedAlbumIds - album.id
                                                } else {
                                                    selectedAlbumIds + album.id
                                                }
                                            }
                                            folderNeedsPin(
                                                isLocked = album.isLocked,
                                                hasFolderLockPin = uiState.hasFolderLockPin,
                                                isUnlockedThisSession = album.id in unlockedAlbumIds,
                                            ) -> albumAwaitingUnlock = album
                                            else -> onAlbumClick(album.id, album.name)
                                        }
                                    },
                                    onLongClick = {
                                        selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                            selectedAlbumIds - album.id
                                        } else {
                                            selectedAlbumIds + album.id
                                        }
                                    },
                                    modifier = Modifier.animateItem(),
                                    isSelected = album.id in selectedAlbumIds,
                                    isPinned = album.isPinned,
                                    isHidden = album.isHidden,
                                    isLocked = album.isLocked && uiState.hasFolderLockPin,
                                )
                            }
                        }
                    }
                } else {
                    // Samsung One UI Smart Category Shortcuts
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SamsungSmartCategoryBar(
                            categories = uiState.smartCategories,
                            onVideosClick = onVideosClick,
                            onFavoritesClick = onFavoritesClick,
                            onCollagesClick = {
                                val collageAlbum = uiState.albums.firstOrNull { it.name.equals("Collages", true) || it.name.equals("Collage", true) }
                                if (collageAlbum != null) onAlbumClick(collageAlbum.id, collageAlbum.name) else onCreateCollage()
                            },
                            onScreenshotsClick = {
                                val scAlbum = uiState.albums.firstOrNull { it.name.equals("Screenshots", true) }
                                if (scAlbum != null) onAlbumClick(scAlbum.id, scAlbum.name)
                            },
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Samsung One UI Segmented Switch: My Albums | Shared Albums
                            SamsungAlbumSegmentedSwitch(
                                selectedTab = uiState.selectedTab,
                                onSelectTab = { viewModel.selectAlbumTab(it) },
                                myAlbumsCount = filteredAlbums.size,
                                sharedAlbumsCount = uiState.sharedAlbums.size,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )

                            if (uiState.selectedTab == AlbumTab.MY_ALBUMS) {
                                SeslReachabilityHeader(
                                    title = "Albums",
                                    subtitle = "${filteredAlbums.size} albums",
                                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 2.dp),
                                )
                                // Samsung Group Filter Chips Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    val view = LocalView.current
                                    availableGroups.forEach { group ->
                                        val isSelected = group == selectedGroup
                                        val chipBg by animateColorAsState(
                                            targetValue = if (isSelected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                                            },
                                            animationSpec = tween(150),
                                            label = "chipBg",
                                        )
                                        val chipTextColor by animateColorAsState(
                                            targetValue = if (isSelected) {
                                                MaterialTheme.colorScheme.surface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            animationSpec = tween(150),
                                            label = "chipTextColor",
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = chipBg,
                                            border = if (!isSelected) {
                                                BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                            } else null,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable {
                                                    if (!isSelected) {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        selectedGroup = group
                                                    }
                                                },
                                        ) {
                                            Text(
                                                text = group,
                                                color = chipTextColor,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                ),
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            } else {
                                SeslReachabilityHeader(
                                    title = "Shared Albums",
                                    subtitle = "${uiState.sharedAlbums.size} collaborative albums",
                                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    if (uiState.selectedTab == AlbumTab.MY_ALBUMS) {
                    if (uiState.isHierarchyView && hierarchyItems.isNotEmpty()) {
                        items(
                            items = hierarchyItems,
                            key = { it.id },
                            span = { item ->
                                when (item) {
                                    is HierarchyDisplayItem.GroupHeader -> GridItemSpan(maxLineSpan)
                                    else -> GridItemSpan(1)
                                }
                            },
                        ) { hierarchyItem ->
                            when (hierarchyItem) {
                                is HierarchyDisplayItem.GroupHeader -> {
                                    AlbumGroupAccordionHeader(
                                        name = hierarchyItem.group.name,
                                        albumCount = hierarchyItem.group.albums.size,
                                        mediaCount = hierarchyItem.group.totalMediaCount,
                                        isExpanded = hierarchyItem.isExpanded,
                                        onToggleExpand = { viewModel.toggleGroupExpanded(hierarchyItem.group.name) },
                                        onDrillDown = { selectedGroup = hierarchyItem.group.name },
                                        onRename = { renameGroupName = hierarchyItem.group.name },
                                        onDissolve = { dissolveGroupName = hierarchyItem.group.name },
                                        onCompress = { viewModel.compressGroup(hierarchyItem.group.name) },
                                    )
                                }
                                is HierarchyDisplayItem.GroupNestedAlbum -> {
                                    val album = hierarchyItem.album
                                    if (uiState.gridDensity == AlbumGridDensity.LIST) {
                                        AlbumListRow(
                                            name = album.name,
                                            coverUri = album.coverUri,
                                            mediaCount = album.mediaCount,
                                            sizeLabel = album.sizeBytes
                                                .takeIf { uiState.showAlbumSize && it > 0L }
                                                ?.let(::formatFileSize),
                                            onClick = {
                                                when {
                                                    isSelectionMode -> {
                                                        selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                            selectedAlbumIds - album.id
                                                        } else {
                                                            selectedAlbumIds + album.id
                                                        }
                                                    }
                                                    folderNeedsPin(
                                                        isLocked = album.isLocked,
                                                        hasFolderLockPin = uiState.hasFolderLockPin,
                                                        isUnlockedThisSession = album.id in unlockedAlbumIds,
                                                    ) -> albumAwaitingUnlock = album
                                                    else -> onAlbumClick(album.id, album.name)
                                                }
                                            },
                                            onLongClick = {
                                                selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                    selectedAlbumIds - album.id
                                                } else {
                                                    selectedAlbumIds + album.id
                                                }
                                            },
                                            modifier = Modifier.animateItem().padding(start = 12.dp),
                                            isSelected = album.id in selectedAlbumIds,
                                            isPinned = album.isPinned,
                                            isHidden = album.isHidden,
                                            isLocked = album.isLocked && uiState.hasFolderLockPin,
                                        )
                                    } else {
                                        AlbumCard(
                                            name = album.name,
                                            coverUri = album.coverUri,
                                            mediaCount = album.mediaCount,
                                            sizeLabel = album.sizeBytes
                                                .takeIf { uiState.showAlbumSize && it > 0L }
                                                ?.let(::formatFileSize),
                                            onClick = {
                                                when {
                                                    isSelectionMode -> {
                                                        selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                            selectedAlbumIds - album.id
                                                        } else {
                                                            selectedAlbumIds + album.id
                                                        }
                                                    }
                                                    folderNeedsPin(
                                                        isLocked = album.isLocked,
                                                        hasFolderLockPin = uiState.hasFolderLockPin,
                                                        isUnlockedThisSession = album.id in unlockedAlbumIds,
                                                    ) -> albumAwaitingUnlock = album
                                                    else -> onAlbumClick(album.id, album.name)
                                                }
                                            },
                                            onLongClick = {
                                                selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                    selectedAlbumIds - album.id
                                                } else {
                                                    selectedAlbumIds + album.id
                                                }
                                            },
                                            modifier = Modifier.animateItem(),
                                            isSelected = album.id in selectedAlbumIds,
                                            isPinned = album.isPinned,
                                            isHidden = album.isHidden,
                                            isLocked = album.isLocked && uiState.hasFolderLockPin,
                                        )
                                    }
                                }
                                is HierarchyDisplayItem.StandaloneAlbum -> {
                                    val album = hierarchyItem.album
                                    if (uiState.gridDensity == AlbumGridDensity.LIST) {
                                        AlbumListRow(
                                            name = album.name,
                                            coverUri = album.coverUri,
                                            mediaCount = album.mediaCount,
                                            sizeLabel = album.sizeBytes
                                                .takeIf { uiState.showAlbumSize && it > 0L }
                                                ?.let(::formatFileSize),
                                            onClick = {
                                                when {
                                                    isSelectionMode -> {
                                                        selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                            selectedAlbumIds - album.id
                                                        } else {
                                                            selectedAlbumIds + album.id
                                                        }
                                                    }
                                                    folderNeedsPin(
                                                        isLocked = album.isLocked,
                                                        hasFolderLockPin = uiState.hasFolderLockPin,
                                                        isUnlockedThisSession = album.id in unlockedAlbumIds,
                                                    ) -> albumAwaitingUnlock = album
                                                    else -> onAlbumClick(album.id, album.name)
                                                }
                                            },
                                            onLongClick = {
                                                selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                    selectedAlbumIds - album.id
                                                } else {
                                                    selectedAlbumIds + album.id
                                                }
                                            },
                                            modifier = Modifier.animateItem(),
                                            isSelected = album.id in selectedAlbumIds,
                                            isPinned = album.isPinned,
                                            isHidden = album.isHidden,
                                            isLocked = album.isLocked && uiState.hasFolderLockPin,
                                        )
                                    } else {
                                        AlbumCard(
                                            name = album.name,
                                            coverUri = album.coverUri,
                                            mediaCount = album.mediaCount,
                                            sizeLabel = album.sizeBytes
                                                .takeIf { uiState.showAlbumSize && it > 0L }
                                                ?.let(::formatFileSize),
                                            onClick = {
                                                when {
                                                    isSelectionMode -> {
                                                        selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                            selectedAlbumIds - album.id
                                                        } else {
                                                            selectedAlbumIds + album.id
                                                        }
                                                    }
                                                    folderNeedsPin(
                                                        isLocked = album.isLocked,
                                                        hasFolderLockPin = uiState.hasFolderLockPin,
                                                        isUnlockedThisSession = album.id in unlockedAlbumIds,
                                                    ) -> albumAwaitingUnlock = album
                                                    else -> onAlbumClick(album.id, album.name)
                                                }
                                            },
                                            onLongClick = {
                                                selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                    selectedAlbumIds - album.id
                                                } else {
                                                    selectedAlbumIds + album.id
                                                }
                                            },
                                            modifier = Modifier.animateItem(),
                                            isSelected = album.id in selectedAlbumIds,
                                            isPinned = album.isPinned,
                                            isHidden = album.isHidden,
                                            isLocked = album.isLocked && uiState.hasFolderLockPin,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (rootDisplayItems.isNotEmpty()) {
                        items(
                            items = rootDisplayItems,
                            key = { it.id },
                        ) { displayItem ->
                            when (displayItem) {
                                is AlbumDisplayItem.Group -> {
                                    val groupAlbumIds = remember(displayItem.albums) { displayItem.albums.map { it.id }.toSet() }
                                    val isGroupSelected = groupAlbumIds.isNotEmpty() && groupAlbumIds.all { it in selectedAlbumIds }
                                    if (uiState.gridDensity == AlbumGridDensity.LIST) {
                                        AlbumGroupListRow(
                                            name = displayItem.name,
                                            coverUris = displayItem.coverUris,
                                            albumCount = displayItem.albums.size,
                                            mediaCount = displayItem.totalMediaCount,
                                            sizeLabel = displayItem.totalSizeBytes
                                                .takeIf { uiState.showAlbumSize && it > 0L }
                                                ?.let(::formatFileSize),
                                            isSelected = isGroupSelected,
                                            isPinned = displayItem.isPinned,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedAlbumIds = if (isGroupSelected) {
                                                        selectedAlbumIds - groupAlbumIds
                                                    } else {
                                                        selectedAlbumIds + groupAlbumIds
                                                    }
                                                } else {
                                                    selectedGroup = displayItem.name
                                                }
                                            },
                                            onLongClick = {
                                                selectedAlbumIds = if (isGroupSelected) {
                                                    selectedAlbumIds - groupAlbumIds
                                                } else {
                                                    selectedAlbumIds + groupAlbumIds
                                                }
                                            },
                                            modifier = Modifier.animateItem(),
                                        )
                                    } else {
                                        AlbumGroupCard(
                                            name = displayItem.name,
                                            coverUris = displayItem.coverUris,
                                            albumCount = displayItem.albums.size,
                                            mediaCount = displayItem.totalMediaCount,
                                            sizeLabel = displayItem.totalSizeBytes
                                                .takeIf { uiState.showAlbumSize && it > 0L }
                                                ?.let(::formatFileSize),
                                            isSelected = isGroupSelected,
                                            isPinned = displayItem.isPinned,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedAlbumIds = if (isGroupSelected) {
                                                        selectedAlbumIds - groupAlbumIds
                                                    } else {
                                                        selectedAlbumIds + groupAlbumIds
                                                    }
                                                } else {
                                                    selectedGroup = displayItem.name
                                                }
                                            },
                                            onLongClick = {
                                                selectedAlbumIds = if (isGroupSelected) {
                                                    selectedAlbumIds - groupAlbumIds
                                                } else {
                                                    selectedAlbumIds + groupAlbumIds
                                                }
                                            },
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                }
                                is AlbumDisplayItem.Single -> {
                                    val album = displayItem.album
                                    if (uiState.gridDensity == AlbumGridDensity.LIST) {
                                        AlbumListRow(
                                            name = album.name,
                                            coverUri = album.coverUri,
                                            mediaCount = album.mediaCount,
                                            sizeLabel = album.sizeBytes
                                                .takeIf { uiState.showAlbumSize && it > 0L }
                                                ?.let(::formatFileSize),
                                            onClick = {
                                                when {
                                                    isSelectionMode -> {
                                                        selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                            selectedAlbumIds - album.id
                                                        } else {
                                                            selectedAlbumIds + album.id
                                                        }
                                                    }
                                                    folderNeedsPin(
                                                        isLocked = album.isLocked,
                                                        hasFolderLockPin = uiState.hasFolderLockPin,
                                                        isUnlockedThisSession = album.id in unlockedAlbumIds,
                                                    ) -> albumAwaitingUnlock = album
                                                    else -> onAlbumClick(album.id, album.name)
                                                }
                                            },
                                            onLongClick = {
                                                selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                    selectedAlbumIds - album.id
                                                } else {
                                                    selectedAlbumIds + album.id
                                                }
                                            },
                                            modifier = Modifier.animateItem(),
                                            isSelected = album.id in selectedAlbumIds,
                                            isPinned = album.isPinned,
                                            isHidden = album.isHidden,
                                            isLocked = album.isLocked && uiState.hasFolderLockPin,
                                        )
                                    } else {
                                        AlbumCard(
                                            name = album.name,
                                            coverUri = album.coverUri,
                                            mediaCount = album.mediaCount,
                                            sizeLabel = album.sizeBytes
                                                .takeIf { uiState.showAlbumSize && it > 0L }
                                                ?.let(::formatFileSize),
                                            onClick = {
                                                when {
                                                    isSelectionMode -> {
                                                        selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                            selectedAlbumIds - album.id
                                                        } else {
                                                            selectedAlbumIds + album.id
                                                        }
                                                    }
                                                    folderNeedsPin(
                                                        isLocked = album.isLocked,
                                                        hasFolderLockPin = uiState.hasFolderLockPin,
                                                        isUnlockedThisSession = album.id in unlockedAlbumIds,
                                                    ) -> albumAwaitingUnlock = album
                                                    else -> onAlbumClick(album.id, album.name)
                                                }
                                            },
                                            onLongClick = {
                                                selectedAlbumIds = if (album.id in selectedAlbumIds) {
                                                    selectedAlbumIds - album.id
                                                } else {
                                                    selectedAlbumIds + album.id
                                                }
                                            },
                                            modifier = Modifier.animateItem(),
                                            isSelected = album.id in selectedAlbumIds,
                                            isPinned = album.isPinned,
                                            isHidden = album.isHidden,
                                            isLocked = album.isLocked && uiState.hasFolderLockPin,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                                Text(
                                    text = if (searchQuery.isBlank()) "No albums yet" else "No albums match \"$searchQuery\"",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (searchQuery.isBlank()) {
                                    FilledTonalButton(onClick = { showCreateAlbumDialog = true }) {
                                        Text("Create album")
                                    }
                                } else {
                                    TextButton(onClick = { searchQuery = "" }) { Text("Clear search") }
                                }
                            }
                        }
                    }
                }
            } else {
                // Shared Albums Tab
                if (uiState.sharedAlbums.isNotEmpty()) {
                    items(
                        items = uiState.sharedAlbums,
                        key = { it.id },
                        span = { GridItemSpan(maxLineSpan) },
                    ) { sharedAlbum ->
                        SharedAlbumCard(
                            album = sharedAlbum,
                            onInviteLink = {
                                val shareLink = "https://quickshare.samsungcloud.com/d/${sharedAlbum.inviteCode}"
                                clipboardManager.setText(AnnotatedString(shareLink))
                                Toast.makeText(context, "Quick Share invite link copied", Toast.LENGTH_SHORT).show()
                            },
                            onCompress = {
                                viewModel.compressSharedAlbum(sharedAlbum)
                            },
                            onClick = {
                                activeSharedAlbum = sharedAlbum
                            },
                        )
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.People,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                                Text(
                                    text = "No shared albums yet",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "Create a shared album to share photos with friends and family over Quick Share",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                                FilledTonalButton(onClick = { showCreateSharedAlbumDialog = true }) {
                                    Text("Create shared album")
                                }
                            }
                        }
                    }
                }
            }
                }
            }

            SeslGoToTopButton(
                gridState = albumsGridState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp),
            )
        }
    }
}

    if (showCreateAlbumDialog) {
        CreateAlbumDialog(
            onDismiss = { showCreateAlbumDialog = false },
            onCreate = {
                viewModel.createAlbum(it)
                showCreateAlbumDialog = false
            },
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name ->
                showCreateGroupDialog = false
                viewModel.createGroup(name, selectedAlbumIds)
                selectedAlbumIds = emptySet()
            },
        )
    }

    if (showCreateSharedAlbumDialog) {
        CreateSharedAlbumDialog(
            onDismiss = { showCreateSharedAlbumDialog = false },
            onCreate = { title ->
                showCreateSharedAlbumDialog = false
                viewModel.createSharedAlbum(title)
            },
        )
    }

    if (showMoveToGroupDialog) {
        val existingCustomGroups = remember(uiState.albums) {
            uiState.albums.mapNotNull { it.groupName }.distinct()
        }
        MoveToGroupDialog(
            groups = existingCustomGroups,
            onDismiss = { showMoveToGroupDialog = false },
            onSelectGroup = { groupName ->
                showMoveToGroupDialog = false
                viewModel.moveAlbumsToGroup(selectedAlbumIds, groupName)
                selectedAlbumIds = emptySet()
            },
            onRemoveFromGroup = {
                showMoveToGroupDialog = false
                viewModel.moveAlbumsToGroup(selectedAlbumIds, null)
                selectedAlbumIds = emptySet()
            },
            onCreateNewGroup = {
                showMoveToGroupDialog = false
                showCreateGroupDialog = true
            },
        )
    }

    renameGroupName?.let { groupName ->
        RenameGroupDialog(
            currentName = groupName,
            onDismiss = { renameGroupName = null },
            onRename = { newName ->
                renameGroupName = null
                viewModel.renameGroup(groupName, newName)
                if (selectedGroup == groupName) {
                    selectedGroup = newName
                }
            },
        )
    }

    dissolveGroupName?.let { groupName ->
        PremiumAlertDialog(
            onDismissRequest = { dissolveGroupName = null },
            title = { Text("Dissolve group?") },
            text = { Text("Albums in \"$groupName\" will be ungrouped. No albums or media will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val grp = groupName
                        dissolveGroupName = null
                        viewModel.dissolveGroup(grp)
                        if (selectedGroup == grp) {
                            selectedGroup = "All"
                        }
                    },
                ) {
                    Text("Dissolve", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dissolveGroupName = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showHideAlbumsSheet) {
        HideAlbumsBottomSheet(
            allAlbums = uiState.albums + uiState.hiddenAlbums,
            onToggleHide = { album, isHidden -> viewModel.setHidden(album, isHidden) },
            onBatchSetHidden = { albums, hidden -> viewModel.batchSetAlbumHidden(albums, hidden) },
            onDismiss = { showHideAlbumsSheet = false },
        )
    }

    if (showMergeAlbumsDialog) {
        MergeAlbumsDialog(
            sourceAlbums = selectedAlbums,
            availableAlbums = uiState.albums,
            onMerge = { sources, target ->
                viewModel.mergeAlbums(sources, target)
                selectedAlbumIds = emptySet()
            },
            onDismiss = { showMergeAlbumsDialog = false },
        )
    }

    val currentSharedAlbum = activeSharedAlbum?.let { active ->
        uiState.sharedAlbums.find { it.id == active.id } ?: active
    }
    currentSharedAlbum?.let { shared ->
        SharedAlbumDetailsDialog(
            album = shared,
            onAddComment = { albumId, text ->
                viewModel.addCommentToSharedAlbum(albumId, text)
            },
            onAddPhotos = { albumId ->
                pendingAddPhotosAlbumId = albumId
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onDismiss = { activeSharedAlbum = null },
        )
    }

    if (showSearchDialog) {
        PremiumAlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search albums") },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text("Album name") },
                )
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { searchQuery = ""; showSearchDialog = false }) {
                    Text("Clear")
                }
            },
        )
    }


    renameAlbum?.let { album ->
        RenameAlbumDialog(
            album = album,
            onDismiss = { renameAlbum = null },
            onRename = { name ->
                viewModel.renameAlbum(album, name)
                renameAlbum = null
            },
        )
    }

    deleteAlbums?.let { albums ->
        val isSingle = albums.size == 1
        val first = albums.first()
        val totalCount = albums.sumOf { it.mediaCount }
        PremiumAlertDialog(
            onDismissRequest = { deleteAlbums = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(if (isSingle) "Delete ${first.name}?" else "Delete ${albums.size} albums?") },
            text = {
                Text(
                    if (totalCount == 0) "The empty ${if (isSingle) "album" else "albums"} will be removed."
                    else "The $totalCount items in ${if (isSingle) "this album" else "these albums"} will be moved to Trash. You can restore them later.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlbums(albums)
                        deleteAlbums = null
                    },
                ) { Text(if (totalCount == 0) "Delete" else "Move to Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteAlbums = null }) { Text("Cancel") } },
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



    uiState.coverAlbum?.let { album ->
        PremiumAlertDialog(
            onDismissRequest = viewModel::dismissCoverChoices,
            title = { Text("Choose cover") },
            text = {
                if (uiState.coverItems.isEmpty()) {
                    Text("No media in this album")
                } else {
                    Column {
                        Text(
                            text = if (album.hasCustomCover) {
                                "Tap a photo to use it as the cover."
                            } else {
                                "This album uses its most recent photo. Tap one to pin a cover instead."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.height(320.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(uiState.coverItems, key = { it.id }) { item ->
                                val isCurrentCover = album.customCoverUri == item.uri
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable { viewModel.setAlbumCover(album, item) },
                                ) {
                                    val context = LocalContext.current
                                    val coverItemRequest = remember(item.uri, item.dateModified) {
                                        ImageRequest.Builder(context)
                                            .data(item.uri)
                                            .size(384)
                                            .crossfade(false)
                                            .memoryCacheKey("${item.uri}_thumb")
                                            .placeholderMemoryCacheKey("${item.uri}_thumb")
                                            .build()
                                    }
                                    AsyncImage(
                                        model = coverItemRequest,
                                        contentDescription = if (isCurrentCover) {
                                            "${item.displayName}, current album cover"
                                        } else {
                                            "Use ${item.displayName} as album cover"
                                        },
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    // Without this the picker gives no clue which photo is
                                    // already the cover, so re-opening it is guesswork.
                                    if (isCurrentCover) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                                        )
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                                .size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                // Only offered once there is a pinned cover to undo.
                if (album.hasCustomCover) {
                    TextButton(onClick = { viewModel.clearAlbumCover(album) }) {
                        Text("Use automatic")
                    }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissCoverChoices) { Text("Cancel") } },
        )
    }
}

private sealed interface HierarchyDisplayItem {
    val id: String
    data class GroupHeader(val group: AlbumDisplayItem.Group, val isExpanded: Boolean) : HierarchyDisplayItem {
        override val id: String get() = "hdr_${group.name}"
    }
    data class GroupNestedAlbum(val album: Album, val groupName: String) : HierarchyDisplayItem {
        override val id: String get() = "nested_${groupName}_${album.id}"
    }
    data class StandaloneAlbum(val album: Album) : HierarchyDisplayItem {
        override val id: String get() = "single_${album.id}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGroupAccordionHeader(
    name: String,
    albumCount: Int,
    mediaCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDrillDown: () -> Unit,
    onRename: () -> Unit,
    onDissolve: () -> Unit,
    onCompress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevronRotation",
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onToggleExpand,
                onLongClick = { showMenu = true },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.FolderCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "$albumCount",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$albumCount albums • $mediaCount items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onDrillDown,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.OpenInNew,
                    contentDescription = "Open group",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onToggleExpand() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse group" else "Expand group",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation),
                )
            }

            PandaDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Open group") },
                    leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onDrillDown()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Rename group") },
                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Compress group") },
                    leadingIcon = { Icon(Icons.Outlined.Compress, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onCompress()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Dissolve group") },
                    leadingIcon = { Icon(Icons.Outlined.FolderOff, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onDissolve()
                    },
                )
            }
        }
    }
}

@Composable
private fun AlbumSelectionActionBar(
    selectedAlbums: List<Album>,
    onPin: () -> Unit,
    onCover: () -> Unit,
    onHide: () -> Unit,
    onRename: () -> Unit,
    onLock: () -> Unit,
    onExport: () -> Unit,
    onCompress: () -> Unit,
    onMoveToGroup: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isInsideGroup: Boolean = false,
    onUngroup: () -> Unit = {},
    onMerge: () -> Unit = {},
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val single = selectedAlbums.size == 1
    val first = selectedAlbums.firstOrNull()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics { isTraversalGroup = true },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isInsideGroup || selectedAlbums.any { it.groupName != null }) {
                AlbumBarAction(
                    label = "Ungroup",
                    icon = Icons.Outlined.FolderOff,
                    onClick = onUngroup,
                    modifier = Modifier.weight(1f),
                )
            } else {
                AlbumBarAction(
                    label = "Group",
                    icon = Icons.Outlined.CreateNewFolder,
                    onClick = onMoveToGroup,
                    modifier = Modifier.weight(1f),
                )
            }
            if (selectedAlbums.size >= 2) {
                AlbumBarAction(
                    label = "Merge",
                    icon = Icons.Outlined.CallMerge,
                    onClick = onMerge,
                    modifier = Modifier.weight(1f),
                )
            }
            AlbumBarAction(
                label = if (selectedAlbums.any { it.isHidden }) "Unhide" else "Hide",
                icon = if (selectedAlbums.any { it.isHidden }) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                onClick = onHide,
                modifier = Modifier.weight(1f),
            )
            AlbumBarAction(
                label = "Delete",
                icon = Icons.Outlined.Delete,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                destructive = true,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                AlbumBarAction(
                    label = "More",
                    icon = Icons.Outlined.MoreVert,
                    onClick = { showMoreMenu = true },
                )
                PandaDropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                ) {
                    if (single) {
                        DropdownMenuItem(
                            text = { Text("Change cover") },
                            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                            enabled = (first?.mediaCount ?: 0) > 0,
                            onClick = {
                                showMoreMenu = false
                                onCover()
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
                    }
                    if (selectedAlbums.size >= 2) {
                        DropdownMenuItem(
                            text = { Text("Merge albums") },
                            leadingIcon = { Icon(Icons.Outlined.CallMerge, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onMerge()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (selectedAlbums.any { it.isPinned }) "Unpin from top" else "Pin to top") },
                        leadingIcon = {
                            Icon(
                                if (selectedAlbums.any { it.isPinned }) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onPin()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (selectedAlbums.any { it.isLocked }) "Unlock" else "Lock") },
                        leadingIcon = {
                            Icon(
                                if (selectedAlbums.any { it.isLocked }) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onLock()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to group") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileMove, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onMoveToGroup()
                        },
                    )
                    if (isInsideGroup) {
                        DropdownMenuItem(
                            text = { Text("Move to another group") },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onMoveToGroup()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Export as ZIP") },
                        leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                        enabled = selectedAlbums.any { it.mediaCount > 0 },
                        onClick = {
                            showMoreMenu = false
                            onExport()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Compress album") },
                        leadingIcon = { Icon(Icons.Outlined.Compress, contentDescription = null) },
                        enabled = selectedAlbums.any { it.mediaCount > 0 },
                        onClick = {
                            showMoreMenu = false
                            onCompress()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumBarAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun CreateAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var albumName by remember { mutableStateOf("") }

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create album") },
        text = {
            OutlinedTextField(
                value = albumName,
                onValueChange = { albumName = it },
                singleLine = true,
                label = { Text("Album name") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = albumName.isNotBlank(),
                onClick = { onCreate(albumName) },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RenameAlbumDialog(
    album: Album,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(album.id) { mutableStateOf(album.name) }
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename album") },
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
            TextButton(
                enabled = name.isNotBlank() && name.trim() != album.name,
                onClick = { onRename(name) },
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var groupName by remember { mutableStateOf("") }
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create group") },
        text = {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                singleLine = true,
                label = { Text("Group name") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = groupName.isNotBlank(),
                onClick = { onCreate(groupName.trim()) },
            ) {
                Text("Create")
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
private fun RenameGroupDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Group name") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && name.trim() != currentName,
                onClick = { onRename(name.trim()) },
            ) {
                Text("Rename")
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
private fun MoveToGroupDialog(
    groups: List<String>,
    onDismiss: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onRemoveFromGroup: () -> Unit,
    onCreateNewGroup: () -> Unit,
) {
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to group") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    onClick = onCreateNewGroup,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Create new group", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (groups.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Existing Groups",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    groups.forEach { group ->
                        Surface(
                            onClick = { onSelectGroup(group) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null)
                                Text(group, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = onRemoveFromGroup,
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.FolderOff, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Text("Remove from group", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SamsungSmartCategoryBar(
    categories: SmartCategoryCounts,
    onVideosClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onCollagesClick: () -> Unit,
    onScreenshotsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        item {
            SamsungCategoryPill(
                title = "Videos",
                count = categories.videosCount,
                icon = Icons.Outlined.Videocam,
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onVideosClick,
            )
        }
        item {
            SamsungCategoryPill(
                title = "Favorites",
                count = categories.favoritesCount,
                icon = Icons.Filled.Favorite,
                iconTint = Color(0xFFE53935),
                onClick = onFavoritesClick,
            )
        }
        item {
            SamsungCategoryPill(
                title = "Collages",
                count = categories.collagesCount,
                icon = Icons.Outlined.GridView,
                iconTint = MaterialTheme.colorScheme.secondary,
                onClick = onCollagesClick,
            )
        }
        item {
            SamsungCategoryPill(
                title = "Screenshots",
                count = categories.screenshotsCount,
                icon = Icons.Outlined.Screenshot,
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onScreenshotsClick,
            )
        }
    }
}

@Composable
private fun SamsungCategoryPill(
    title: String,
    count: Int,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.defaultMinSize(minHeight = 40.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (count > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Authentic Samsung One UI Segmented Switch: My Albums vs Shared Albums.
 * Styled with minimalist tone-on-tone neutral pill cards, subtle badges, and fluid transitions.
 */
@Composable
private fun SamsungAlbumSegmentedSwitch(
    selectedTab: AlbumTab,
    onSelectTab: (AlbumTab) -> Unit,
    myAlbumsCount: Int,
    sharedAlbumsCount: Int,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()

    // Base segmented pill container
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
        },
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
        ) {
            val myAlbumsSelected = selectedTab == AlbumTab.MY_ALBUMS
            val sharedAlbumsSelected = selectedTab == AlbumTab.SHARED_ALBUMS

            val selectedContainer = if (isDark) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }

            // Animated backgrounds
            val myAlbumsBg by animateColorAsState(
                targetValue = if (myAlbumsSelected) selectedContainer else Color.Transparent,
                animationSpec = tween(180),
                label = "myAlbumsBg",
            )
            val sharedAlbumsBg by animateColorAsState(
                targetValue = if (sharedAlbumsSelected) selectedContainer else Color.Transparent,
                animationSpec = tween(180),
                label = "sharedAlbumsBg",
            )

            // Animated text colors
            val myAlbumsTextColor by animateColorAsState(
                targetValue = if (myAlbumsSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                animationSpec = tween(180),
                label = "myAlbumsTextColor",
            )
            val sharedAlbumsTextColor by animateColorAsState(
                targetValue = if (sharedAlbumsSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                animationSpec = tween(180),
                label = "sharedAlbumsTextColor",
            )

            // Animated badge colors
            val myBadgeBg by animateColorAsState(
                targetValue = if (myAlbumsSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                animationSpec = tween(180),
                label = "myBadgeBg",
            )
            val myBadgeTextColor by animateColorAsState(
                targetValue = if (myAlbumsSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                animationSpec = tween(180),
                label = "myBadgeTextColor",
            )

            val sharedBadgeBg by animateColorAsState(
                targetValue = if (sharedAlbumsSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                animationSpec = tween(180),
                label = "sharedBadgeBg",
            )
            val sharedBadgeTextColor by animateColorAsState(
                targetValue = if (sharedAlbumsSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                animationSpec = tween(180),
                label = "sharedBadgeTextColor",
            )

            val selectedBorder = if (isDark) {
                BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            } else {
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }

            // Tab 1: My Albums
            Surface(
                shape = RoundedCornerShape(19.dp),
                color = myAlbumsBg,
                border = if (myAlbumsSelected) selectedBorder else null,
                shadowElevation = if (myAlbumsSelected && !isDark) 1.5.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(19.dp))
                    .clickable {
                        if (!myAlbumsSelected) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelectTab(AlbumTab.MY_ALBUMS)
                        }
                    },
            ) {
                Row(
                    // Without fillMaxWidth the row wraps its content and Arrangement.Center has
                    // nothing to centre within, which left both segment labels left-aligned.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "My albums",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (myAlbumsSelected) FontWeight.Bold else FontWeight.Medium,
                            color = myAlbumsTextColor,
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = myBadgeBg,
                    ) {
                        Text(
                            text = myAlbumsCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = myBadgeTextColor,
                            ),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            // Tab 2: Shared Albums
            Surface(
                shape = RoundedCornerShape(19.dp),
                color = sharedAlbumsBg,
                border = if (sharedAlbumsSelected) selectedBorder else null,
                shadowElevation = if (sharedAlbumsSelected && !isDark) 1.5.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(19.dp))
                    .clickable {
                        if (!sharedAlbumsSelected) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelectTab(AlbumTab.SHARED_ALBUMS)
                        }
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.People,
                        contentDescription = null,
                        tint = sharedAlbumsTextColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Shared",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (sharedAlbumsSelected) FontWeight.Bold else FontWeight.Medium,
                            color = sharedAlbumsTextColor,
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = sharedBadgeBg,
                    ) {
                        Text(
                            text = sharedAlbumsCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = sharedBadgeTextColor,
                            ),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Collaborative Shared Album Card with cover preview, member avatars, and Quick Share actions.
 */
@Composable
private fun SharedAlbumCard(
    album: SharedAlbum,
    onInviteLink: () -> Unit,
    onCompress: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover preview
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (album.coverUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(album.coverUri)
                                .size(192)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surfaceContainerHighest,
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (album.isAutoSync) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "Auto-sync",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${album.members.size} members • ${album.items.size} photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(6.dp))
                    // Members avatars row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-6).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        album.members.take(4).forEach { member ->
                            Surface(
                                shape = CircleShape,
                                color = Color(member.avatarColor),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHighest),
                                modifier = Modifier.size(24.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = member.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        ),
                                    )
                                }
                            }
                        }
                        if (album.members.size > 4) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHighest),
                                modifier = Modifier.size(24.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "+${album.members.size - 4}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            )

            // Quick actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onInviteLink,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Invite Link",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.width(4.dp))

                Button(
                    onClick = onCompress,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Compress,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Vault Compress",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Samsung One UI New Shared Album Dialog.
 */
@Composable
private fun CreateSharedAlbumDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var albumTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.GroupAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "New Shared Album",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Invite family and friends to contribute photos over Quick Share and Samsung Cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = albumTitle,
                    onValueChange = { albumTitle = it },
                    placeholder = { Text("Album title (e.g., Summer Trip)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (albumTitle.isNotBlank()) {
                        onCreate(albumTitle.trim())
                        onDismiss()
                    }
                },
                enabled = albumTitle.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}


