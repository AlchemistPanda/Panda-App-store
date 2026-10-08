package com.pandagallery.app.ui.vault

import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.pandagallery.app.ui.components.dragSelectGrid
import com.pandagallery.app.ui.components.calculateRangeSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.pandagallery.app.data.vault.PrivateMediaItem
import com.pandagallery.app.data.vault.PrivateFolder
import com.pandagallery.app.data.vault.RestoreDestination
import com.pandagallery.app.data.security.PinVerification
import com.pandagallery.app.ui.security.PinSetupDialog
import com.pandagallery.app.ui.security.rememberPinLockoutMessage
import com.pandagallery.app.ui.security.PinVerifyDialog
import com.pandagallery.app.ui.security.wrongPinMessage
import kotlinx.coroutines.launch
import com.pandagallery.app.ui.components.PremiumAlertDialog
import com.pandagallery.app.ui.components.PremiumDialogOption
import com.pandagallery.app.ui.components.adaptiveColumnCount
import com.pandagallery.app.ui.viewer.ZoomableImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PrivateVaultScreen(onBack: () -> Unit, viewModel: PrivateVaultViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewerItemId by remember { mutableStateOf<String?>(null) }
    var restoreItems by remember { mutableStateOf<List<PrivateMediaItem>?>(null) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var showMoveToFolder by remember { mutableStateOf(false) }
    var currentFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<PrivateFolder?>(null) }
    val currentFolder = uiState.folders.firstOrNull { it.id == currentFolderId }
    val visibleItems = uiState.items.filter { it.folderId == currentFolderId }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelection()
            viewModel.clearPreviews()
        }
    }

    // FLAG_SECURE is what keeps private media out of screenshots, screen recordings, the recents
    // thumbnail and screen-reading assistants (Circle to Search, Google Assistant) — one flag
    // covers all of them, so the setting that lifts it lifts all of them together.
    //
    // Keyed on the preference so flipping the toggle takes effect on the open screen. Note the
    // default is false, so the flag is still applied during the first frames before preferences
    // load — it fails closed, never open.
    val window = (context as? android.app.Activity)?.window
    DisposableEffect(window, uiState.allowScreenshots) {
        if (uiState.allowScreenshots) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // Leaving the app or switching to recents re-locks the vault immediately.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        unlocked = false
        viewerItemId = null
    }

    // The PIN sheet is shown by the gate below rather than opening on its own, so backing out of
    // it leaves the locked screen rather than a half-open vault.
    var showPinPrompt by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val lockoutMessage = rememberPinLockoutMessage(
        active = showPinPrompt,
        remainingMillis = viewModel::lockoutRemainingMillis,
    )

    /**
     * Offers the fingerprint shortcut, which stands in for typing the PIN when the user has turned
     * it on. Any error just returns to the keypad: biometrics are a convenience here, never the
     * only way in.
     */
    fun unlockWithBiometrics() {
        val activity = context as? FragmentActivity ?: return
        BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                showPinPrompt = false
                unlocked = true
            }
            override fun onAuthenticationError(code: Int, message: CharSequence) = Unit
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Private folder")
                .setSubtitle("Or cancel to enter your PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN")
                .build()
        )
    }

    // Opens the keypad as soon as the screen is ready, so entering the vault is one step rather
    // than two. Keyed on load alone: dismissing the sheet must leave the locked screen visible
    // instead of reopening it immediately.
    //
    // Nothing here re-locks. `unlocked` lives in this composable, so leaving the screen — for
    // Settings, for another app — drops it on its own, and a PIN changed in the meantime is proved
    // on the way back in.
    LaunchedEffect(uiState.preferencesLoaded) {
        if (uiState.preferencesLoaded && !unlocked) showPinPrompt = true
    }

    if (showPinPrompt) {
        if (uiState.hasPin) {
            PinVerifyDialog(
                title = "Enter Private folder PIN",
                message = "This PIN is separate from your folder lock PIN and from your phone's screen lock.",
                error = pinError,
                lockoutMessage = lockoutMessage,
                onSubmit = { pin ->
                    scope.launch {
                        when (val result = viewModel.verifyPin(pin)) {
                            PinVerification.Success -> {
                                pinError = null
                                showPinPrompt = false
                                unlocked = true
                            }
                            is PinVerification.Wrong -> pinError = wrongPinMessage(result)
                            is PinVerification.LockedOut -> pinError = null
                        }
                    }
                },
                onDismiss = { showPinPrompt = false; pinError = null },
                onUseBiometrics = if (uiState.biometricsEnabled) ::unlockWithBiometrics else null,
            )
        } else {
            // First entry: the vault cannot be opened until it has a PIN of its own, which is also
            // where the no-recovery warning is stated.
            PinSetupDialog(
                title = "Set a Private folder PIN",
                onPinChosen = { pin ->
                    scope.launch {
                        viewModel.setPin(pin)
                        showPinPrompt = false
                        unlocked = true
                    }
                },
                onDismiss = { showPinPrompt = false },
            )
        }
    }
    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll(visibleItems) }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "Select all")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text(currentFolder?.name ?: "Private", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentFolderId != null) currentFolderId = null else onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        if (currentFolderId == null) {
                            IconButton(onClick = { showCreateFolder = true }) {
                                Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Create private folder")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = {
            if (unlocked && uiState.isSelectionMode && viewerItemId == null) {
                VaultSelectionActionBar(
                    onRestore = {
                        restoreItems = visibleItems.filter { it.id in uiState.selectedIds }
                    },
                    onMove = { showMoveToFolder = true },
                    onDelete = { showBulkDeleteConfirmation = true },
                )
            }
        },
    ) { padding ->
        if (!uiState.preferencesLoaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (!unlocked) {
            Column(Modifier.fillMaxSize().padding(padding), Arrangement.Center, Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error ?: if (uiState.hasPin) {
                        "Private media is encrypted and locked"
                    } else {
                        "Choose a PIN to protect your private media"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { pinError = null; showPinPrompt = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Icon(Icons.Outlined.LockOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (uiState.hasPin) "Enter PIN" else "Set PIN",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (visibleItems.isEmpty() && (currentFolderId != null || uiState.folders.isEmpty())) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = if (currentFolderId == null) "No private media" else "This folder is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (currentFolderId == null) {
                            "Move photos and videos here to keep them encrypted behind your Private folder PIN."
                        } else {
                            "Move items into this folder from your gallery or another private folder."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val vaultGridState = rememberLazyGridState()
                var dragInitialSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
                var dragIsSelecting by remember { mutableStateOf(true) }
                val folderOffset = if (currentFolderId == null) uiState.folders.size else 0

                LazyVerticalGrid(
                    columns = GridCells.Fixed(adaptiveColumnCount(3, maxWidth)),
                    state = vaultGridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .dragSelectGrid(
                            gridState = vaultGridState,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelectableItem = { idx ->
                                idx >= folderOffset && (idx - folderOffset) < visibleItems.size
                            },
                            onDragStart = { startIdx ->
                                val mediaIdx = startIdx - folderOffset
                                val startMedia = visibleItems.getOrNull(mediaIdx)
                                if (startMedia != null) {
                                    val initial = uiState.selectedIds
                                    val selecting = !initial.contains(startMedia.id)
                                    dragInitialSelection = initial
                                    dragIsSelecting = selecting
                                    val updated = if (selecting) initial + startMedia.id else initial - startMedia.id
                                    viewModel.setSelectedIds(updated)
                                }
                            },
                            onDrag = { startIdx, currentIdx ->
                                val minMediaIdx = minOf(startIdx, currentIdx) - folderOffset
                                val maxMediaIdx = maxOf(startIdx, currentIdx) - folderOffset
                                val updated = calculateRangeSelection(
                                    startIndex = minMediaIdx,
                                    currentIndex = maxMediaIdx,
                                    initialSelection = dragInitialSelection,
                                    items = visibleItems,
                                    getItemId = { it.id },
                                    isSelecting = dragIsSelecting,
                                )
                                viewModel.setSelectedIds(updated)
                            },
                            onDragEnd = {
                                dragInitialSelection = emptySet()
                            },
                        ),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (currentFolderId == null) {
                        items(uiState.folders, key = { "folder:${it.id}" }) { folder ->
                            PrivateFolderCard(
                                name = folder.name,
                                itemCount = uiState.items.count { it.folderId == folder.id },
                                onClick = {
                                    viewModel.clearSelection()
                                    currentFolderId = folder.id
                                },
                                onLongClick = { folderToDelete = folder },
                            )
                        }
                    }
                    items(visibleItems, key = { it.id }) { item ->
                        VaultThumbnail(
                            item = item,
                            viewModel = viewModel,
                            isSelected = item.id in uiState.selectedIds,
                            isSelectionMode = uiState.isSelectionMode,
                            onClick = { viewerItemId = item.id },
                            onLongClick = { viewModel.toggleSelection(item) },
                            onToggleSelection = { viewModel.toggleSelection(item) },
                        )
                    }
                }
            }
        }
    }
    viewerItemId?.let { itemId ->
        PrivateVaultViewer(
            items = visibleItems,
            initialItemId = itemId,
            viewModel = viewModel,
            onClose = { viewerItemId = null },
            onRestore = { restoreItems = listOf(it) },
        )
    }

    restoreItems?.let { items ->
        RestoreDestinationDialog(
            items = items,
            onDismiss = { restoreItems = null },
            onRestore = { destination ->
                if (uiState.isSelectionMode) {
                    viewModel.restoreSelected(destination)
                } else {
                    viewModel.restore(items, destination)
                }
                restoreItems = null
                viewerItemId = null
            },
        )
    }

    if (showBulkDeleteConfirmation) {
        PremiumAlertDialog(
            onDismissRequest = { showBulkDeleteConfirmation = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete ${uiState.selectedIds.size} items permanently?") },
            text = { Text("These encrypted items will be removed from the Private Album and cannot be recovered.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteConfirmation = false
                        viewModel.deleteSelected()
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }


    if (showCreateFolder) {
        CreatePrivateFolderDialog(
            onDismiss = { showCreateFolder = false },
            onCreate = { name ->
                viewModel.createFolder(name)
                showCreateFolder = false
            },
        )
    }

    if (showMoveToFolder) {
        MoveToPrivateFolderDialog(
            folders = uiState.folders,
            currentFolderId = currentFolderId,
            onDismiss = { showMoveToFolder = false },
            onMove = { folderId ->
                viewModel.moveSelected(folderId)
                showMoveToFolder = false
            },
        )
    }

    uiState.folderError?.let { message ->
        PremiumAlertDialog(
            onDismissRequest = viewModel::clearFolderError,
            title = { Text("Private folder") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearFolderError) { Text("OK") }
            },
        )
    }


    folderToDelete?.let { folder ->
        val itemCount = uiState.items.count { it.folderId == folder.id }
        PremiumAlertDialog(
            onDismissRequest = { folderToDelete = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete ${folder.name}?") },
            text = {
                Text(
                    if (itemCount == 0) "The empty folder will be removed."
                    else "This folder contains $itemCount ${if (itemCount == 1) "item" else "items"}. Move or restore them first.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id)
                        folderToDelete = null
                    },
                    enabled = itemCount == 0,
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrivateFolderCard(
    name: String,
    itemCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = "$itemCount ${if (itemCount == 1) "item" else "items"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultThumbnail(
    item: PrivateMediaItem,
    viewModel: PrivateVaultViewModel,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: (() -> Unit)? = null,
) {
    var file by remember(item.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(item.id) { file = viewModel.preview(item) }
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            if (file == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AsyncImage(file, item.originalName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            } else {
                                Color.Black.copy(alpha = 0.08f)
                            },
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onToggleSelection?.invoke() ?: onClick()
                        }
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultSelectionActionBar(
    onRestore: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
        ) {
            VaultSelectionAction(
                label = "Restore",
                icon = Icons.Outlined.Restore,
                onClick = onRestore,
                modifier = Modifier.weight(1f),
            )
            VaultSelectionAction(
                label = "Move",
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                onClick = onMove,
                modifier = Modifier.weight(1f),
            )
            VaultSelectionAction(
                label = "Delete",
                icon = Icons.Outlined.Delete,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CreatePrivateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
        title = { Text("New private folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                label = { Text("Folder name") },
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
private fun MoveToPrivateFolderDialog(
    folders: List<com.pandagallery.app.data.vault.PrivateFolder>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
) {
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
        title = { Text("Move to") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PremiumDialogOption(
                    title = "Private Album",
                    description = "Move to the main private area",
                    icon = Icons.Outlined.Lock,
                    onClick = { onMove(null) },
                    enabled = currentFolderId != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                folders.forEach { folder ->
                    PremiumDialogOption(
                        title = folder.name,
                        description = "Private folder",
                        icon = Icons.Outlined.Folder,
                        onClick = { onMove(folder.id) },
                        enabled = folder.id != currentFolderId,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (folders.isEmpty()) {
                    Text(
                        "Create a private folder first",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun VaultSelectionAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@Composable
private fun RestoreDestinationDialog(
    items: List<PrivateMediaItem>,
    onDismiss: () -> Unit,
    onRestore: (RestoreDestination) -> Unit,
) {
    val knownPaths = items.mapNotNull { it.originalRelativePath }.distinct()
    val originalFolderDescription = when {
        knownPaths.isEmpty() -> "Original location unavailable; these items will use PandaGallery Restored"
        knownPaths.size == 1 && items.all { it.originalRelativePath != null } -> knownPaths.single()
        items.any { it.originalRelativePath == null } -> "Original folders where known; older items use PandaGallery Restored"
        else -> "Restore each item to its original folder"
    }

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
        title = { Text("Restore to") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PremiumDialogOption(
                    title = "Original folder",
                    description = originalFolderDescription,
                    icon = Icons.Outlined.Restore,
                    onClick = { onRestore(RestoreDestination.ORIGINAL_FOLDER) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PremiumDialogOption(
                    title = "PandaGallery Restored",
                    description = "Pictures/PandaGallery Restored",
                    icon = Icons.Outlined.Folder,
                    onClick = { onRestore(RestoreDestination.RESTORED_FOLDER) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivateVaultViewer(
    items: List<PrivateMediaItem>,
    initialItemId: String,
    viewModel: PrivateVaultViewModel,
    onClose: () -> Unit,
    onRestore: (PrivateMediaItem) -> Unit,
) {
    val initialPage = remember(items, initialItemId) {
        items.indexOfFirst { it.id == initialItemId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = items::size)
    val currentItem = items.getOrNull(pagerState.currentPage)
    val vaultState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<PrivateMediaItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            VaultViewerPage(
                item = item,
                isActive = page == pagerState.currentPage,
                viewModel = viewModel,
                onTap = { showControls = !showControls },
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = currentItem?.originalName.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Private Album",
                        )
                    }
                },
                actions = {
                    currentItem?.let { item ->
                        val isSelected = item.id in vaultState.selectedIds
                        IconButton(onClick = { viewModel.toggleSelection(item) }) {
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = if (isSelected) "Selected" else "Not selected",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            if (items.isNotEmpty()) {
                                append("${pagerState.currentPage + 1}/${items.size}")
                            }
                            if (vaultState.selectedIds.isNotEmpty()) {
                                append("  ·  ${vaultState.selectedIds.size} selected")
                            }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.65f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }

        AnimatedVisibility(
            visible = showControls && currentItem != null,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            currentItem?.let { item ->
                Surface(color = Color.Black.copy(alpha = 0.72f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                    ) {
                        if (items.size > 1) {
                            VaultThumbnailFilmstrip(
                                items = items,
                                currentPage = pagerState.currentPage,
                                selectedIds = vaultState.selectedIds,
                                viewModel = viewModel,
                                onItemClick = { index ->
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                onItemLongClick = { vaultItem ->
                                    viewModel.toggleSelection(vaultItem)
                                },
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            VaultViewerAction(
                                label = "Restore",
                                icon = Icons.Outlined.Restore,
                                onClick = { onRestore(item) },
                                modifier = Modifier.weight(1f),
                            )
                            VaultViewerAction(
                                label = "Delete",
                                icon = Icons.Outlined.Delete,
                                onClick = { pendingDelete = item },
                                modifier = Modifier.weight(1f),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        PremiumAlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete permanently?") },
            text = { Text("This removes ${item.originalName} from the encrypted Private Album and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(item)
                        pendingDelete = null
                        onClose()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun VaultViewerPage(
    item: PrivateMediaItem,
    isActive: Boolean,
    viewModel: PrivateVaultViewModel,
    onTap: () -> Unit,
) {
    var file by remember(item.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(item.id) { file = viewModel.preview(item) }

    val vaultState by viewModel.uiState.collectAsStateWithLifecycle()
    // Null until preferences load; see the matching guard in ViewerScreen.
    val autoPlayVideos = vaultState.autoPlayVideos.takeIf { vaultState.preferencesLoaded }

    val previewFile = file
    if (previewFile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else if (item.mimeType.startsWith("video/")) {
        VaultVideoPlayer(
            file = previewFile,
            isActive = isActive,
            autoPlay = autoPlayVideos,
            contentDescription = item.originalName,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        ZoomableImage(
            uri = Uri.fromFile(previewFile),
            contentDescription = item.originalName,
            onTap = onTap,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
private fun VaultVideoPlayer(
    file: File,
    isActive: Boolean,
    /** Null while the preference is still loading, so nothing starts before it is known. */
    autoPlay: Boolean?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(file, isActive) {
        if (isActive) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(PlayerMediaItem.fromUri(Uri.fromFile(file)))
                prepare()
                playWhenReady = false
            }
        } else {
            null
        }
    }

    LaunchedEffect(player, autoPlay) {
        if (autoPlay == true) player?.play()
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (player == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            AndroidView(
                factory = { playerContext ->
                    PlayerView(playerContext).apply {
                        this.player = player
                        useController = true
                        controllerAutoShow = true
                        controllerHideOnTouch = true
                        this.contentDescription = contentDescription
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VaultViewerAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultThumbnailFilmstrip(
    items: List<PrivateMediaItem>,
    currentPage: Int,
    selectedIds: Set<String>,
    viewModel: PrivateVaultViewModel,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (PrivateMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentPage) {
        if (currentPage in items.indices) {
            listState.animateScrollToItem((currentPage - 2).coerceAtLeast(0))
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            val isCurrent = index == currentPage
            val isSelected = item.id in selectedIds
            var file by remember(item.id) { mutableStateOf<File?>(null) }
            LaunchedEffect(item.id) { file = viewModel.preview(item) }

            Box(
                modifier = Modifier
                    .size(if (isCurrent) 48.dp else 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (isCurrent) {
                            Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp))
                        } else if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        } else {
                            Modifier
                        }
                    )
                    .combinedClickable(
                        onClick = { onItemClick(index) },
                        onLongClick = { onItemLongClick(item) },
                    ),
            ) {
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = item.originalName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    )
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }
    }
}
