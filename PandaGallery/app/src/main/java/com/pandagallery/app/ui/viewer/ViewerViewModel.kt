package com.pandagallery.app.ui.viewer

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.editing.ImageEditorEngine
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.data.media.IncomingMediaResolver
import com.pandagallery.app.data.sharing.MediaSharePreparer
import com.pandagallery.app.data.vault.PrivateVaultRepository
import com.pandagallery.app.data.metadata.MediaMetadataRepository
import com.pandagallery.app.data.metadata.MediaMetadata
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.data.video.VideoFrameCaptureManager
import com.pandagallery.app.data.editing.VideoEditorEngine
import com.pandagallery.app.domain.model.CompressionOriginalAction
import com.pandagallery.app.domain.model.SlowMoExportConfig
import com.pandagallery.app.domain.model.SlowMoExportResult
import com.pandagallery.app.domain.model.PhotoGpsItem
import com.pandagallery.app.domain.model.PhotoGpsCluster
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import javax.inject.Inject

import com.pandagallery.app.data.compression.SafetyVaultRepository
import com.pandagallery.app.data.local.entity.SafetyVaultEntity
import com.pandagallery.app.data.media.MotionPhotoHelper
import com.pandagallery.app.domain.compression.CompressionEstimator
import com.pandagallery.app.domain.model.CompressionPreset

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val preferencesDataSource: com.pandagallery.app.data.local.PreferencesDataSource,
    private val privateVaultRepository: PrivateVaultRepository,
    private val compressionQueueRepository: CompressionQueueRepository,
    private val safetyVaultRepository: SafetyVaultRepository,
    private val mediaSharePreparer: MediaSharePreparer,
    private val metadataRepository: MediaMetadataRepository,
    private val incomingMediaResolver: IncomingMediaResolver,
    private val viewerSession: ViewerSession,
    private val imageEditorEngine: ImageEditorEngine,
    private val videoFrameCaptureManager: VideoFrameCaptureManager,
    private val videoEditorEngine: VideoEditorEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val initialSessionData = savedStateHandle.get<Long>("mediaId")?.let {
        viewerSession.getInitialSession(it)
    }

    private val _uiState = MutableStateFlow(
        if (initialSessionData != null) {
            ViewerUiState(
                currentItem = initialSessionData.initialItem,
                mediaItems = initialSessionData.items,
                initialPage = initialSessionData.initialPage,
                isLoading = false,
            )
        } else {
            ViewerUiState()
        }
    )
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _compressionPrompt = MutableStateFlow<CompressionPrompt?>(null)
    val compressionPrompt: StateFlow<CompressionPrompt?> = _compressionPrompt.asStateFlow()

    private val _compressionQueuedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val compressionQueuedEvent = _compressionQueuedEvent.asSharedFlow()

    private val _activeSafetyBackup = MutableStateFlow<SafetyVaultEntity?>(null)
    val activeSafetyBackup: StateFlow<SafetyVaultEntity?> = _activeSafetyBackup.asStateFlow()

    /**
     * Live preferences for the Smart Optimize sheet, which estimates each preset itself and
     * words its Safety Vault promise from the user's actual retention (or the vault being off).
     */
    val userPreferences: StateFlow<com.pandagallery.app.domain.model.UserPreferences?> =
        preferencesDataSource.userPreferencesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _quickOptimizeEstimate = MutableStateFlow<CompressionEstimator.Estimate?>(null)
    val quickOptimizeEstimate: StateFlow<CompressionEstimator.Estimate?> = _quickOptimizeEstimate.asStateFlow()

    private val _pendingPrivateMove = MutableStateFlow<PendingPrivateMove?>(null)
    val pendingPrivateMove: StateFlow<PendingPrivateMove?> = _pendingPrivateMove.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()

    val selectedIds: StateFlow<Set<Long>> = viewerSession.selectedIds
    val isSelectionMode: StateFlow<Boolean> = viewerSession.isSelectionMode

    fun toggleSelection(mediaId: Long) {
        viewerSession.toggleSelection(mediaId)
    }

    fun clearSelection() {
        viewerSession.clearSelection()
    }

    fun onMediaActive(mediaId: Long) {
        viewerSession.updateCurrentMediaId(mediaId)
        viewModelScope.launch {
            _activeSafetyBackup.value = safetyVaultRepository.getSafetyItem(mediaId)
            val current = _uiState.value.mediaItems.firstOrNull { it.id == mediaId } ?: _uiState.value.currentItem
            if (current != null && !current.isCompressed && ((current.isImage && current.size > 2_500_000L) || (current.isVideo && current.size > 15_000_000L))) {
                val prefs = preferencesDataSource.userPreferencesFlow.first()
                val input = CompressionEstimator.Input(
                    sizeBytes = current.size,
                    width = current.width,
                    height = current.height,
                    isVideo = current.isVideo,
                    durationMillis = current.duration,
                )
                val estimate = CompressionEstimator.estimate(input, prefs)
                _quickOptimizeEstimate.value = if (estimate.savedBytes > 400_000L) estimate else null
            } else {
                _quickOptimizeEstimate.value = null
            }
        }
    }

    init {
        viewModelScope.launch {
            preferencesDataSource.userPreferencesFlow.collect { preferences ->
                _uiState.update {
                    it.copy(
                        slideshowIntervalSeconds = preferences.slideshowIntervalSeconds,
                        autoPlayVideos = preferences.autoPlayVideos,
                        superHdrGainmapEnabled = preferences.superHdrGainmapEnabled,
                        removeLocationWhenSharing = preferences.removeLocationWhenSharing,
                        convertHeifWhenSharing = preferences.convertHeifWhenSharing,
                        preferencesLoaded = true,
                    )
                }
            }
        }
    }

    /**
     * Pins the photo on screen as its album's cover.
     *
     * Resolved from the item's bucket rather than taking an album argument, so the action
     * works from wherever the viewer was opened — a search result or a smart group knows
     * nothing about which album it came from.
     */
    fun setAsAlbumCover(item: MediaItem) = viewModelScope.launch {
        val bucketId = item.bucketId
        if (bucketId == null) {
            _message.emit("This item isn't in an album")
            return@launch
        }
        // includeHidden so the action still works while browsing a revealed hidden album.
        val album = mediaRepository.getAlbums(includeHidden = true).first()
            .firstOrNull { it.id == bucketId }
        if (album == null) {
            _message.emit("Couldn't find this item's album")
            return@launch
        }
        runCatching { mediaRepository.setAlbumCover(album, item.uri) }
            .onSuccess { _message.emit("Cover set for ${album.name}") }
            .onFailure { _message.emit(it.message ?: "Unable to set album cover") }
    }

    /** Clears an error after it has been shown, so it isn't reported twice. */
    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    fun loadMedia(mediaId: Long) {
        val alreadyLoaded = _uiState.value.mediaItems.any { it.id == mediaId }
        if (!alreadyLoaded) {
            val sessionData = viewerSession.getInitialSession(mediaId)
            if (sessionData != null) {
                _uiState.update {
                    it.copy(
                        currentItem = sessionData.initialItem,
                        mediaItems = sessionData.items,
                        initialPage = sessionData.initialPage,
                        isLoading = false,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = true) }
            }
        }
        viewModelScope.launch {
            val item = mediaRepository.getMediaById(mediaId)
            // Prefer the list the user was actually looking at; fall back to the album.
            val sessionItems = viewerSession.idsContaining(mediaId)?.let { ids ->
                val byId = (mediaRepository.getAllMediaSnapshot() + mediaRepository.getTrashedMedia().first())
                    .associateBy { it.id }
                ids.mapNotNull(byId::get).takeIf { it.isNotEmpty() }
            }
            val items = when {
                sessionItems != null -> sessionItems
                _uiState.value.mediaItems.isNotEmpty() -> _uiState.value.mediaItems
                item?.isTrashed == true -> mediaRepository.getTrashedMedia().first()
                item?.bucketId != null -> mediaRepository.getMediaByBucket(item.bucketId).first()
                else -> mediaRepository.getAllMediaSnapshot()
            }.let { media ->
                if (item != null && media.none { it.id == item.id }) listOf(item) + media else media
            }
            val itemsWithCompression = compressionQueueRepository.withCompressionMetadata(items)
            val currentItem = item?.let { requested ->
                itemsWithCompression.firstOrNull { it.uri == requested.uri }
                    ?: compressionQueueRepository.withCompressionMetadata(listOf(requested)).first()
            } ?: itemsWithCompression.firstOrNull { it.id == mediaId }
            _uiState.update {
                it.copy(
                    currentItem = currentItem ?: it.currentItem,
                    mediaItems = itemsWithCompression,
                    initialPage = itemsWithCompression.indexOfFirst { media -> media.id == mediaId }.coerceAtLeast(0),
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Shows a single item another app handed over that MediaStore has no row for — a chat
     * attachment kept outside the indexed folders, typically. There is no album to page
     * through and no library row to act on, so the item stands alone and read-only.
     */
    fun loadExternalMedia(uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val item = incomingMediaResolver.describe(android.net.Uri.parse(uri))
            _uiState.update {
                it.copy(
                    currentItem = item,
                    mediaItems = listOfNotNull(item),
                    initialPage = 0,
                    isExternal = item != null,
                    isLoading = false,
                    error = if (item == null) "Couldn't read this file" else null,
                )
            }
        }
    }

    fun setFavorite(item: MediaItem, favorite: Boolean) {
        viewModelScope.launch {
            mediaRepository.setFavorite(listOf(item), favorite)
            val refreshed = mediaRepository.getMediaById(item.id) ?: item.copy(isFavorite = favorite)
            val updated = compressionQueueRepository.withCompressionMetadata(listOf(refreshed)).first()
            _uiState.update { state ->
                state.copy(
                    currentItem = updated,
                    mediaItems = state.mediaItems.map { if (it.id == item.id) updated else it },
                )
            }
        }
    }

    fun renameMedia(item: MediaItem, requestedName: String) = viewModelScope.launch {
        mediaRepository.renameMedia(item, requestedName)
            .onSuccess { newDisplayName ->
                val refreshed = mediaRepository.getMediaById(item.id) ?: item.copy(displayName = newDisplayName)
                val updated = compressionQueueRepository.withCompressionMetadata(listOf(refreshed)).first()
                _uiState.update { state ->
                    state.copy(
                        currentItem = if (state.currentItem?.id == item.id) updated else state.currentItem,
                        mediaItems = state.mediaItems.map { if (it.id == item.id) updated else it },
                    )
                }
                _message.emit("Renamed to $newDisplayName")
            }
            .onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Unable to rename media") }
            }
    }

    fun loadMetadata(item: MediaItem) = viewModelScope.launch {
        if (_uiState.value.metadata?.mediaId == item.id) return@launch
        try {
            _uiState.update { it.copy(metadata = metadataRepository.read(item)) }
        } catch (error: IOException) {
            _uiState.update { it.copy(error = error.message ?: "Unable to read metadata") }
        } catch (error: SecurityException) {
            _uiState.update { it.copy(error = "Media access was lost") }
        }
    }

    private val _geotaggedItems = MutableStateFlow<List<PhotoGpsItem>>(emptyList())
    val geotaggedItems: StateFlow<List<PhotoGpsItem>> = _geotaggedItems.asStateFlow()

    fun loadGeotaggedItems() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val items = _uiState.value.mediaItems
            val gpsList = mutableListOf<PhotoGpsItem>()
            val currentMeta = _uiState.value.metadata
            if (currentMeta?.latitude != null && currentMeta.longitude != null) {
                val cur = items.firstOrNull { it.id == currentMeta.mediaId } ?: _uiState.value.currentItem
                if (cur != null) {
                    gpsList.add(
                        PhotoGpsItem(
                            mediaId = cur.id,
                            uri = cur.uri,
                            displayName = cur.displayName,
                            latitude = currentMeta.latitude,
                            longitude = currentMeta.longitude,
                            dateTaken = cur.dateTaken,
                            size = cur.size,
                            isVideo = cur.isVideo,
                        )
                    )
                }
            }
            for (item in items.take(60)) {
                if (gpsList.any { it.mediaId == item.id }) continue
                if (!item.isImage) continue
                try {
                    val meta = metadataRepository.read(item)
                    if (meta.latitude != null && meta.longitude != null) {
                        gpsList.add(
                            PhotoGpsItem(
                                mediaId = item.id,
                                uri = item.uri,
                                displayName = item.displayName,
                                latitude = meta.latitude,
                                longitude = meta.longitude,
                                dateTaken = item.dateTaken,
                                size = item.size,
                                isVideo = item.isVideo,
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
            _geotaggedItems.value = gpsList
        }
    }

    fun compressCluster(cluster: PhotoGpsCluster) {
        viewModelScope.launch {
            val mediaIds = cluster.items.map { it.mediaId }.toSet()
            val items = _uiState.value.mediaItems.filter { it.id in mediaIds }
            if (items.isNotEmpty()) {
                compressionQueueRepository.enqueue(items, CompressionOriginalAction.MOVE)
                _message.emit("Queued ${items.size} photos for Safety Vault compression")
            }
        }
    }

    fun removeLocation(item: MediaItem) = viewModelScope.launch {
        try {
            metadataRepository.removeLocation(item)
            _uiState.update { it.copy(metadata = metadataRepository.read(item)) }
            _geotaggedItems.update { current -> current.filterNot { it.mediaId == item.id } }
        } catch (error: IOException) {
            _uiState.update { it.copy(error = error.message ?: "Unable to remove location") }
        } catch (error: SecurityException) {
            _uiState.update { it.copy(error = "Android did not allow this metadata change") }
        }
    }

    fun saveGenerativeCopy(bitmap: Bitmap, saveToVault: Boolean) = viewModelScope.launch {
        val item = _uiState.value.currentItem ?: return@launch
        try {
            if (saveToVault) {
                safetyVaultRepository.backupOriginal(
                    sourceUri = item.uri,
                    sourceMediaId = item.id,
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    originalBytes = item.size,
                    dateTaken = item.dateTaken,
                    force = true,
                )
            }
            imageEditorEngine.saveGenerativeCopy(
                item = item,
                generativeBitmap = bitmap,
                saveAsCopy = true,
                useNearLosslessCompression = saveToVault,
            )
            mediaRepository.syncMediaStore()
            _message.emit(if (saveToVault) "Saved generative edit to Safety Vault (-65%)" else "Saved generative copy")
        } catch (error: Exception) {
            _uiState.update { it.copy(error = error.message ?: "Unable to save generative edit") }
        }
    }

    fun markItemTrashed(item: MediaItem) {
        viewModelScope.launch {
            mediaRepository.markItemsTrashed(listOf(item))
        }
    }

    fun shareItem(
        item: MediaItem,
        removeLocation: Boolean? = null,
        convertHeifRaw: Boolean? = null,
    ) {
        viewModelScope.launch {
            try {
                // An external item's URI belongs to another app's provider: passing it
                // straight on hands the target something it has no permission to read.
                val payload = mediaSharePreparer.prepare(
                    items = listOf(item),
                    copyOriginals = _uiState.value.isExternal,
                    stripLocation = removeLocation,
                    convertHeif = convertHeifRaw,
                    convertRaw = convertHeifRaw,
                )
                context.startActivity(payload.createChooserIntent())
            } catch (error: IOException) {
                _uiState.update { it.copy(error = "Unable to prepare this image for sharing") }
            } catch (error: IllegalStateException) {
                _uiState.update { it.copy(error = "Unable to prepare this image for sharing") }
            } catch (error: SecurityException) {
                _uiState.update { it.copy(error = "Unable to access this image for sharing") }
            }
        }
    }

    fun moveItemToPrivate(item: MediaItem) {
        if (_pendingPrivateMove.value != null || _uiState.value.isMovingToPrivate) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMovingToPrivate = true, error = null) }
            var vaultIds = emptyList<String>()
            try {
                vaultIds = privateVaultRepository.import(listOf(item))
                _pendingPrivateMove.value = PendingPrivateMove(
                        deleteRequest = MediaStore.createDeleteRequest(
                            context.contentResolver,
                            listOf(item.uri),
                        ),
                        item = item,
                        vaultIds = vaultIds,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (vaultIds.isNotEmpty()) {
                    privateVaultRepository.delete(vaultIds)
                }
                _uiState.update { it.copy(error = error.message ?: "Unable to move item to Private") }
            } finally {
                _uiState.update { it.copy(isMovingToPrivate = false) }
            }
        }
    }

    fun completePrivateMove(approved: Boolean) {
        val move = _pendingPrivateMove.value ?: return
        _pendingPrivateMove.value = null
        viewModelScope.launch {
            if (approved) {
                mediaRepository.removeMedia(move.item.id)
            } else {
                privateVaultRepository.delete(move.vaultIds)
            }
        }
    }

    fun compressItem(item: MediaItem, actionOverride: CompressionOriginalAction? = null) {
        viewModelScope.launch {
            val prefs = mediaRepository.compressionPreferences()
            if (actionOverride == null) {
                _compressionPrompt.value = CompressionPrompt(
                    itemCount = 1,
                    folderPath = prefs.compressedFolderPath,
                )
                return@launch
            }
            val action = actionOverride
            try {
                compressionQueueRepository.enqueue(listOf(item), action)
                _compressionQueuedEvent.emit(Unit)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissCompressionPrompt() {
        _compressionPrompt.value = null
    }

    fun chooseCompressionAction(item: MediaItem, action: CompressionOriginalAction) {
        _compressionPrompt.value = null
        compressItem(item, action)
    }

    /**
     * Queues [item] with exactly what the Smart Optimize sheet showed: the picked [preset] and
     * the AI Remaster switch travel with the task. Both used to be dropped, so "Max Space" was
     * encoded at whatever the global settings said and the original replaced regardless.
     */
    fun optimizeActiveItem(
        item: MediaItem,
        preset: CompressionPreset = CompressionPreset.MEDIUM,
        remasterDetailLevel: Float = 0f,
        action: CompressionOriginalAction = CompressionOriginalAction.MOVE,
    ) {
        viewModelScope.launch {
            try {
                compressionQueueRepository.enqueue(
                    items = listOf(item),
                    action = action,
                    remasterDetailLevel = remasterDetailLevel,
                    imageFormat = preset.imageFormat,
                    imageQuality = preset.imageQuality,
                    videoResolution = preset.videoResolution,
                    videoCodec = preset.videoCodec,
                )
                _compressionQueuedEvent.emit(Unit)
                _message.emit("Queued for Smart Optimization")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun revertActiveItem(safetyEntity: SafetyVaultEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val restoredUri = safetyVaultRepository.revertOriginal(safetyEntity)
            if (restoredUri != null) {
                mediaRepository.syncMediaStore()
                _activeSafetyBackup.value = null
                _message.emit("Original uncompressed photo restored!")
                loadMedia(safetyEntity.sourceMediaId)
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to restore original file") }
            }
        }
    }

    fun stripMotionPhoto(item: MediaItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 1. Back up the unstripped motion photo so the strip can be undone. Returns
                // null when the user has turned the Safety Vault off, which makes this one-way.
                val safetyEntity = safetyVaultRepository.backupOriginal(
                    sourceUri = item.uri,
                    sourceMediaId = item.id,
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    originalBytes = item.size,
                    dateTaken = item.dateTaken,
                )
                if (safetyEntity != null) {
                    safetyVaultRepository.linkCompressedMedia(item.id, item.id)
                }

                // 2. Strip embedded MP4 micro-video track to a temporary file
                val tempFile = File(context.cacheDir, "stripped_${item.id}_${System.currentTimeMillis()}.jpg")
                val success = MotionPhotoHelper.stripMotionVideo(context, item.uri, tempFile)
                if (!success || !tempFile.exists() || tempFile.length() <= 0L) {
                    tempFile.delete()
                    _uiState.update { it.copy(isLoading = false, error = "Could not strip motion clip from file") }
                    return@launch
                }

                val savedBytes = (item.size - tempFile.length()).coerceAtLeast(0L)

                // 3. Overwrite the file in MediaStore
                context.contentResolver.openOutputStream(item.uri, "wt")?.use { out ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                tempFile.delete()

                // 4. Invalidate image caches and refresh
                runCatching {
                    val imageLoader = coil3.SingletonImageLoader.get(context)
                    imageLoader.diskCache?.remove(item.uri.toString())
                    imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key(item.uri.toString()))
                }
                mediaRepository.syncMediaStore()
                loadMedia(item.id)
                _message.emit("Motion clip deleted • Saved ${com.pandagallery.app.domain.model.formatFileSize(savedBytes)} (14-day reversible)")
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to delete motion clip: ${e.message}") }
            }
        }
    }

    fun saveMotionPhotoAsVideo(item: MediaItem) {
        viewModelScope.launch {
            try {
                val videoFile = MotionPhotoHelper.extractMotionVideo(context, item.uri)
                if (videoFile == null) {
                    _message.emit("No motion video found in this photo")
                    return@launch
                }
                val baseName = "${item.displayName.substringBeforeLast('.', item.displayName)}_motion"
                val savedUri = MotionPhotoHelper.saveMotionVideoToGallery(context, videoFile, baseName)
                if (savedUri != null) {
                    mediaRepository.syncMediaStore()
                    _message.emit("Saved motion video to Gallery")
                } else {
                    _message.emit("Failed to save motion video")
                }
            } catch (e: Exception) {
                _message.emit("Error saving video: ${e.message}")
            }
        }
    }

    fun saveRotation(
        item: MediaItem,
        degrees: Float,
        onComplete: () -> Unit = {},
    ) {
        if (degrees % 360f == 0f) {
            onComplete()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = mediaRepository.rotateImages(listOf(item), degrees)
                result.onSuccess {
                    runCatching {
                        val imageLoader = coil3.SingletonImageLoader.get(context)
                        imageLoader.diskCache?.remove(item.uri.toString())
                        imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key(item.uri.toString()))
                    }
                    val refreshed = mediaRepository.getMediaById(item.id) ?: item
                    val withCompression = compressionQueueRepository.withCompressionMetadata(listOf(refreshed)).first()
                    _uiState.update { state ->
                        state.copy(
                            currentItem = withCompression,
                            mediaItems = state.mediaItems.map { if (it.id == item.id) withCompression else it },
                            isLoading = false,
                        )
                    }
                    _message.emit("Orientation saved")
                    onComplete()
                }.onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to save orientation") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to save orientation") }
            }
        }
    }

    fun applyRemaster(source: Bitmap, level: Float): Bitmap {
        return imageEditorEngine.applyRemaster(source, level, recycleSource = false)
    }

    suspend fun saveRemaster(
        item: MediaItem,
        remasteredBitmap: Bitmap,
        saveAsCopy: Boolean = true,
        useNearLosslessCompression: Boolean = true,
    ): Uri {
        val destination = imageEditorEngine.saveRemaster(
            item = item,
            remasteredBitmap = remasteredBitmap,
            saveAsCopy = saveAsCopy,
            useNearLosslessCompression = useNearLosslessCompression,
        )
        mediaRepository.syncMediaStore()
        if (!saveAsCopy) {
            val refreshed = mediaRepository.getMediaById(item.id) ?: item
            _uiState.update { state ->
                state.copy(
                    currentItem = refreshed,
                    mediaItems = state.mediaItems.map { if (it.id == item.id) refreshed else it },
                )
            }
        }
        _message.emit(if (saveAsCopy) "Remastered copy saved" else "Remastered picture saved")
        return destination
    }

    /**
     * Captures the current frame of the playing video at [positionMs] and saves
     * it as a pristine JPEG in `Pictures/PandaGallery Captures/`.
     */
    fun captureCurrentFrame(videoUri: Uri, positionMs: Long, onResult: (Result<Uri>) -> Unit) {
        viewModelScope.launch {
            val result = videoFrameCaptureManager.captureFrame(videoUri, positionMs)
            result.onSuccess {
                _message.emit("Frame saved to Pictures/PandaGallery Captures")
            }.onFailure { error ->
                _message.emit("Failed to capture frame: ${error.localizedMessage ?: "Unknown error"}")
            }
            onResult(result)
        }
    }

    /**
     * Exports a slow-motion version of the video using Media3 Transformer and hardware acceleration.
     * Optionally safeguards into the safety vault with HEVC compression.
     */
    suspend fun exportSlowMo(
        item: MediaItem,
        config: SlowMoExportConfig,
        onProgress: (Int) -> Unit,
    ): SlowMoExportResult {
        val result = videoEditorEngine.exportSlowMo(item, config, onProgress)
        if (result.isSuccess) {
            _message.emit("Slow-Mo video saved to Movies/PandaGallery SlowMo")
            if (config.compressToVault) {
                runCatching {
                    safetyVaultRepository.backupOriginal(
                        sourceUri = item.uri,
                        sourceMediaId = item.id,
                        displayName = item.displayName,
                        mimeType = item.mimeType,
                        originalBytes = item.size,
                        dateTaken = item.dateTaken,
                        force = true,
                    )
                }
            }
        } else {
            _message.emit("Failed to export slow-mo: ${result.errorMessage ?: "Unknown error"}")
        }
        return result
    }
}

data class CompressionPrompt(
    val itemCount: Int,
    val folderPath: String,
)

data class ViewerUiState(
    val currentItem: MediaItem? = null,
    val mediaItems: List<MediaItem> = emptyList(),
    val initialPage: Int = 0,
    val isLoading: Boolean = false,
    /** True while showing an item handed over by another app that has no MediaStore row. */
    val isExternal: Boolean = false,
    val isMovingToPrivate: Boolean = false,
    val slideshowIntervalSeconds: Int = 3,
    val autoPlayVideos: Boolean = true,
    val superHdrGainmapEnabled: Boolean = true,
    val removeLocationWhenSharing: Boolean = false,
    val convertHeifWhenSharing: Boolean = true,
    /**
     * False until the stored preferences have been read. The viewer waits for this
     * before starting playback, so opening a video directly cannot autoplay for a
     * moment before the setting loads.
     */
    val preferencesLoaded: Boolean = false,
    val error: String? = null,
    val metadata: MediaMetadata? = null,
)

data class PendingPrivateMove(
    val deleteRequest: PendingIntent,
    val item: MediaItem,
    val vaultIds: List<String>,
)
