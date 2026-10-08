package com.pandagallery.app.ui.albums

import android.app.PendingIntent
import android.content.Context
import android.provider.MediaStore
import com.pandagallery.app.data.diagnostics.CrashLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.local.PreferencesDataSource.PinLock
import com.pandagallery.app.data.security.PinLockManager
import com.pandagallery.app.data.security.PinVerification
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.domain.model.Album
import com.pandagallery.app.domain.model.AlbumGridDensity
import com.pandagallery.app.domain.model.AlbumSortOrder
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.formatFileSize
import com.pandagallery.app.domain.model.sortAlbums
import com.pandagallery.app.domain.model.visibleAlbums
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.data.share.QuickShareEngine
import com.pandagallery.app.domain.compression.CompressionEstimator
import com.pandagallery.app.domain.compression.toEstimatorInput
import com.pandagallery.app.domain.model.*

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val albumExportRepository: com.pandagallery.app.data.export.AlbumExportRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val pinLocks: PinLockManager,
    private val viewerSession: com.pandagallery.app.ui.viewer.ViewerSession,
    private val compressionQueueRepository: CompressionQueueRepository,
    private val quickShareEngine: QuickShareEngine = QuickShareEngine(),
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumsUiState())
    internal val uiState: StateFlow<AlbumsUiState> = _uiState.asStateFlow()
    private val _pendingMutation = MutableStateFlow<PendingAlbumMutation?>(null)
    private val pendingDeleteChunks = kotlin.collections.ArrayDeque<AlbumMutation.Delete>()
    internal val pendingMutation = _pendingMutation.asStateFlow()
    private val _compressionPrompt = MutableStateFlow<AlbumCompressionPrompt?>(null)
    internal val compressionPrompt: StateFlow<AlbumCompressionPrompt?> = _compressionPrompt.asStateFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    internal val message = _message.asSharedFlow()
    /**
     * Read from preferences on every emission rather than held locally, so the order chosen in
     * Settings and the order chosen from this screen's menu are the same stored value.
     */
    private val sortOrder = preferencesDataSource.userPreferencesFlow
        .map { it.albumSortOrder }
        .distinctUntilChanged()

    /**
     * Session-only reveal of hidden albums.
     *
     * Deliberately not persisted: "hidden" is a state the user chose, and a reveal that
     * outlived the session would quietly undo it. Reset whenever the app leaves the
     * foreground, mirroring how the Private Album re-locks.
     */
    private val revealHidden = MutableStateFlow(false)
    private val _pendingExport = MutableStateFlow<PendingAlbumExport?>(null)
    internal val pendingExport: StateFlow<PendingAlbumExport?> = _pendingExport.asStateFlow()

    init {
        // Before anything is shown: a folder left flagged locked with no PIN in existence would
        // otherwise present a prompt nothing can answer.
        viewModelScope.launch { mediaRepository.clearFolderLocksWithoutPin() }
        refresh()
        observeAlbums()
        observeHiddenAlbums()
        observeFavorites()
        observeTrash()
        observeSmartCategories()
        observeViewPreferences()
        observeLibraryChanges()
    }

    /** Keeps the album list in step with photos added or removed by other apps. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeLibraryChanges() {
        viewModelScope.launch {
            mediaRepository.mediaStoreChanges()
                .drop(1)
                // A compression batch publishes a file every few seconds, each firing several
                // notifications; syncing on every one of them re-read the whole library.
                .debounce(1000L)
                .collect { runCatching { mediaRepository.syncMediaStore() } }
        }
    }

    private fun observeViewPreferences() {
        viewModelScope.launch {
            val preferences = preferencesDataSource.userPreferencesFlow.first()
            _uiState.update { it.copy(gridDensity = preferences.albumGridDensity) }
        }
        // Collected rather than read once: a PIN set in Settings has to reach a locked folder on
        // an already-open screen, otherwise it still refuses to open until the app restarts.
        viewModelScope.launch {
            preferencesDataSource.userPreferencesFlow.collect { preferences ->
                _uiState.update {
                    it.copy(
                        showAlbumSize = preferences.showAlbumSize,
                        hasFolderLockPin = preferences.hasFolderLockPin,
                        folderLockBiometricsEnabled = preferences.folderLockBiometricsEnabled,
                    )
                }
            }
        }
    }

    /** Checks the shared folder-lock PIN. Attempt counting and backoff live in [PinLockManager]. */
    internal suspend fun verifyFolderPin(pin: String): PinVerification = pinLocks.verify(PinLock.FOLDERS, pin)

    /** Chooses the folder-lock PIN, for the first folder a user locks. */
    internal suspend fun setFolderPin(pin: String) = pinLocks.setPin(PinLock.FOLDERS, pin)

    internal suspend fun folderLockoutRemainingMillis(): Long = pinLocks.lockoutRemainingMillis(PinLock.FOLDERS)

    /** Opening an album from here means the viewer should page through that album. */
    internal fun prepareViewerSession(
        ids: List<Long>,
        selected: Set<Long> = emptySet(),
        inSelectionMode: Boolean = false,
    ) {
        viewerSession.publish(ids, selected, inSelectionMode)
    }

    private fun refresh() {
        viewModelScope.launch {
            runCatching { mediaRepository.syncMediaStore() }
        }
    }

    private fun observeAlbums() {
        viewModelScope.launch {
            combine(
                // Always ask for everything, then filter here, so flipping the reveal is
                // instant rather than re-querying the whole album list.
                mediaRepository.getAlbums(includeHidden = true),
                sortOrder,
                revealHidden,
            ) { albums, order, revealed ->
                Triple(sortAlbums(visibleAlbums(albums, revealed), order), order, revealed)
            }.distinctUntilChanged().collect { (albums, order, revealed) ->
                _uiState.update {
                    it.copy(
                        albums = albums,
                        sortOrder = order,
                        isRevealingHidden = revealed,
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** Temporarily shows hidden albums in the grid without changing their hidden state. */
    internal fun setHiddenAlbumsRevealed(revealed: Boolean) {
        revealHidden.value = revealed
    }

    /**
     * Called when the albums screen stops. Hiding again on the way out is what makes the
     * reveal temporary — otherwise it would silently persist for the rest of the process.
     */
    internal fun concealHiddenAlbums() {
        revealHidden.value = false
    }

    private fun observeHiddenAlbums() {
        viewModelScope.launch {
            mediaRepository.getAlbums(includeHidden = true).collect { albums ->
                _uiState.update { it.copy(hiddenAlbums = albums.filter(Album::isHidden)) }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            mediaRepository.getFavoritesCount().collect { count ->
                _uiState.update { it.copy(favoritesCount = count) }
            }
        }
    }

    private fun observeTrash() {
        viewModelScope.launch {
            mediaRepository.getTrashedCount().collect { count ->
                _uiState.update { it.copy(trashCount = count) }
            }
        }
    }

    private fun observeSmartCategories() {
        viewModelScope.launch {
            mediaRepository.getAllMedia().collect { allItems ->
                val nonTrashed = allItems.filterNot(MediaItem::isTrashed)
                val videos = nonTrashed.count(MediaItem::isVideo)
                val favorites = nonTrashed.count(MediaItem::isFavorite)
                val collages = nonTrashed.count {
                    it.bucketName.equals("Collages", ignoreCase = true) ||
                    it.bucketName.equals("Collage", ignoreCase = true) ||
                    it.relativePath?.contains("Collage", ignoreCase = true) == true
                }
                val screenshots = nonTrashed.count {
                    it.bucketName.equals("Screenshots", ignoreCase = true) ||
                    it.relativePath?.contains("Screenshots", ignoreCase = true) == true
                }
                val gifs = nonTrashed.count {
                    it.mimeType.equals("image/gif", ignoreCase = true)
                }
                val initialShared = quickShareEngine.getInitialSharedAlbums(nonTrashed)
                _uiState.update {
                    it.copy(
                        smartCategories = SmartCategoryCounts(
                            videosCount = videos,
                            favoritesCount = favorites,
                            collagesCount = collages,
                            screenshotsCount = screenshots,
                            gifsCount = gifs,
                        ),
                        sharedAlbums = if (it.sharedAlbums.isEmpty()) initialShared else it.sharedAlbums,
                    )
                }
            }
        }
    }

    fun selectAlbumTab(tab: AlbumTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun createSharedAlbum(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val newAlbum = SharedAlbum(
            id = "shared_${System.currentTimeMillis()}",
            title = trimmed,
            subtitle = "1 contributor • 0 photos",
            coverUri = null,
            members = listOf(
                SharedAlbumMember("m_current", "You", SharedAlbumRole.OWNER, 0xFF2C6CF5, isCurrentUser = true)
            ),
            items = emptyList(),
            inviteCode = "SAMSUNG-SHARE-${(1000..9999).random()}",
            isAutoSync = true,
        )
        _uiState.update { it.copy(sharedAlbums = listOf(newAlbum) + it.sharedAlbums) }
        viewModelScope.launch {
            _message.emit("Created shared album \"$trimmed\"")
        }
    }

    fun compressSharedAlbum(album: SharedAlbum) {
        if (album.items.isEmpty()) {
            viewModelScope.launch { _message.emit("Shared album has no photos to compress") }
            return
        }
        viewModelScope.launch {
            try {
                compressionQueueRepository.enqueue(album.items, CompressionOriginalAction.MOVE)
                _message.emit("Queued ${album.items.size} photos from \"${album.title}\" for vault compression")
            } catch (e: Exception) {
                _message.emit("Unable to queue compression: ${e.message}")
            }
        }
    }

    fun createAlbum(name: String) {
        viewModelScope.launch {
            mediaRepository.createAlbum(name)
                .onSuccess { _message.emit("Album created") }
                .onFailure { error -> reportError(error.message, "Unable to create album") }
        }
    }

    fun createGroup(name: String, albumIds: Set<Long> = emptySet()) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            if (albumIds.isNotEmpty()) {
                mediaRepository.updateAlbumsGroup(albumIds, trimmed)
            }
            _message.emit("Group \"$trimmed\" created")
        }
    }

    fun moveAlbumsToGroup(albumIds: Set<Long>, groupName: String?) {
        if (albumIds.isEmpty()) return
        viewModelScope.launch {
            mediaRepository.updateAlbumsGroup(albumIds, groupName)
            val msg = if (groupName.isNullOrBlank()) "Removed from group" else "Moved to $groupName"
            _message.emit(msg)
        }
    }

    fun renameGroup(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == oldName) return
        viewModelScope.launch {
            mediaRepository.renameAlbumGroup(oldName, trimmed)
            _message.emit("Group renamed to \"$trimmed\"")
        }
    }

    fun dissolveGroup(groupName: String) {
        if (groupName.isBlank()) return
        viewModelScope.launch {
            mediaRepository.dissolveAlbumGroup(groupName)
            _message.emit("Group \"$groupName\" dissolved")
        }
    }

    fun toggleGroupExpanded(groupName: String) {
        _uiState.update { state ->
            val updated = if (groupName in state.expandedGroupNames) {
                state.expandedGroupNames - groupName
            } else {
                state.expandedGroupNames + groupName
            }
            state.copy(expandedGroupNames = updated)
        }
    }

    fun setAllGroupsExpanded(expanded: Boolean) {
        _uiState.update { state ->
            val allGroups = state.albums.mapNotNull { it.groupName }.toSet()
            state.copy(expandedGroupNames = if (expanded) allGroups else emptySet())
        }
    }

    fun setHierarchyView(enabled: Boolean) {
        _uiState.update { it.copy(isHierarchyView = enabled) }
    }

    fun batchSetAlbumHidden(albums: List<Album>, hidden: Boolean) {
        if (albums.isEmpty()) return
        viewModelScope.launch {
            mediaRepository.batchSetAlbumHidden(albums, hidden)
            _message.emit(if (hidden) "Hidden ${albums.size} albums" else "Unhidden ${albums.size} albums")
        }
    }

    fun mergeAlbums(sourceAlbums: List<Album>, targetAlbum: Album) {
        if (sourceAlbums.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMerging = true, mergeProgress = 0f) }
            mediaRepository.mergeAlbums(
                sourceAlbums = sourceAlbums,
                targetAlbum = targetAlbum,
                onProgress = { done, total ->
                    _uiState.update { it.copy(mergeProgress = if (total > 0) done.toFloat() / total else 0f) }
                }
            ).onSuccess { count ->
                _uiState.update { it.copy(isMerging = false, mergeProgress = null) }
                _message.emit("Merged into \"${targetAlbum.name}\" ($count photos moved)")
            }.onFailure { error ->
                _uiState.update { it.copy(isMerging = false, mergeProgress = null) }
                _message.emit("Failed to merge albums: ${error.message}")
            }
        }
    }

    fun addCommentToSharedAlbum(albumId: String, text: String, author: String = "You") {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            val updatedShared = state.sharedAlbums.map { album ->
                if (album.id == albumId) {
                    val newComment = SharedAlbumComment(
                        id = "comment_${System.currentTimeMillis()}",
                        author = author,
                        avatarColor = 0xFF2C6CF5,
                        text = trimmed,
                        timestamp = System.currentTimeMillis(),
                    )
                    album.copy(comments = album.comments + newComment)
                } else {
                    album
                }
            }
            state.copy(sharedAlbums = updatedShared)
        }
    }

    fun addPhotosToSharedAlbum(albumId: String, items: List<MediaItem>) {
        if (items.isEmpty()) return
        _uiState.update { state ->
            val updatedShared = state.sharedAlbums.map { album ->
                if (album.id == albumId) {
                    val existingIds = album.items.map { it.id }.toSet()
                    val newItems = items.filter { it.id !in existingIds }
                    album.copy(
                        items = album.items + newItems,
                        subtitle = "${album.members.size} contributors • ${album.items.size + newItems.size} photos",
                        coverUri = album.coverUri ?: newItems.firstOrNull()?.uri,
                    )
                } else {
                    album
                }
            }
            state.copy(sharedAlbums = updatedShared)
        }
        viewModelScope.launch {
            _message.emit("Added ${items.size} photos to shared album")
        }
    }

    private suspend fun reportError(message: String?, fallback: String) {
        _message.emit(message?.takeIf { it.isNotBlank() } ?: fallback)
    }

    internal fun renameAlbum(album: Album, newName: String) {
        viewModelScope.launch {
            val items = mediaRepository.getMediaByBucket(album.id).first()
            if (items.isEmpty() || MediaStore.canManageMedia(context)) {
                completeRename(album, newName)
            } else {
                runCatching { MediaStore.createWriteRequest(context.contentResolver, items.map { it.uri }) }
                    .onSuccess { request ->
                        _pendingMutation.value = PendingAlbumMutation(request, AlbumMutation.Rename(album, items, newName))
                    }
                    .onFailure { error ->
                        CrashLog.e(TAG, "Rename request for album with ${items.size} items failed", error)
                        reportError(null, "Unable to rename album")
                    }
            }
        }
    }

    internal fun deleteAlbum(album: Album) = deleteAlbums(listOf(album))

    /**
     * Moves every item of [albums] to Trash behind one system confirmation per
     * [DELETE_CHUNK_SIZE] items. Deleting several albums used to issue one request per album,
     * each overwriting the last, so only the final album's result was ever recorded.
     */
    internal fun deleteAlbums(albums: List<Album>) {
        if (albums.isEmpty()) return
        viewModelScope.launch {
            try {
                val itemsByAlbum = albums.associateWith { mediaRepository.getMediaByBucket(it.id).first() }
                itemsByAlbum.filterValues { it.isEmpty() }.keys.forEach { empty ->
                    mediaRepository.deleteEmptyAlbum(empty.id)
                        .onFailure { error -> reportError(error.message, "Unable to delete album") }
                }
                val items = itemsByAlbum.values.flatten()
                if (items.isEmpty()) {
                    _message.emit(if (albums.size == 1) "Album deleted" else "Albums deleted")
                    return@launch
                }
                pendingDeleteChunks.clear()
                items.chunked(DELETE_CHUNK_SIZE).forEach { chunk ->
                    pendingDeleteChunks.addLast(AlbumMutation.Delete(albums.first(), chunk))
                }
                requestNextDeleteChunk()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashLog.e(TAG, "Deleting ${albums.size} albums failed", e)
                reportError(null, "Unable to delete album")
            }
        }
    }

    private suspend fun requestNextDeleteChunk() {
        val mutation = pendingDeleteChunks.removeFirstOrNull() ?: return
        runCatching {
            MediaStore.createTrashRequest(context.contentResolver, mutation.items.map { it.uri }, true)
        }.onSuccess { request ->
            _pendingMutation.value = PendingAlbumMutation(request, mutation)
        }.onFailure { error ->
            pendingDeleteChunks.clear()
            CrashLog.e(TAG, "Trash request for ${mutation.items.size} album items failed", error)
            reportError(null, "Unable to delete album")
        }
    }

    internal fun setSortOrder(order: AlbumSortOrder) {
        viewModelScope.launch { preferencesDataSource.updateAlbumSortOrder(order) }
    }

    internal fun setGridDensity(density: AlbumGridDensity) {
        _uiState.update { it.copy(gridDensity = density) }
        viewModelScope.launch { preferencesDataSource.updateAlbumGridDensity(density) }
    }

    internal fun togglePinned(album: Album) = viewModelScope.launch {
        mediaRepository.setAlbumPinned(album, !album.isPinned)
        _message.emit(if (album.isPinned) "Album unpinned" else "Album pinned")
    }

    internal fun setHidden(album: Album, hidden: Boolean) = viewModelScope.launch {
        mediaRepository.setAlbumHidden(album, hidden)
        _message.emit(if (hidden) "Album hidden" else "Album shown")
    }

    internal fun loadCoverChoices(album: Album) = viewModelScope.launch {
        val items = mediaRepository.getMediaByBucket(album.id).first()
        _uiState.update { it.copy(coverAlbum = album, coverItems = items) }
    }

    internal fun dismissCoverChoices() {
        _uiState.update { it.copy(coverAlbum = null, coverItems = emptyList()) }
    }

    internal fun setAlbumCover(album: Album, item: MediaItem) = viewModelScope.launch {
        mediaRepository.setAlbumCover(album, item.uri)
        dismissCoverChoices()
        _message.emit("Album cover updated")
    }

    internal fun setLocked(album: Album, locked: Boolean) = viewModelScope.launch {
        mediaRepository.setAlbumLocked(album, locked)
        _message.emit(if (locked) "Album locked" else "Album unlocked")
    }

    /** Prepares an export; the screen then asks the user where to write the zip. */
    internal fun prepareExport(album: Album) = viewModelScope.launch {
        val items = mediaRepository.getMediaByBucket(album.id).first().filterNot(MediaItem::isTrashed)
        if (items.isEmpty()) {
            _message.emit("${album.name} has nothing to export")
            return@launch
        }
        _pendingExport.value = PendingAlbumExport(album, items)
    }

    internal fun cancelExport() {
        _pendingExport.value = null
    }

    /** Writes the prepared album into [destination] as a single zip. */
    internal fun exportTo(destination: android.net.Uri) = viewModelScope.launch {
        val pending = _pendingExport.value ?: return@launch
        _pendingExport.value = null
        _uiState.update { it.copy(exportProgress = 0f) }
        try {
            val result = albumExportRepository.exportToZip(pending.items, destination) { done, total ->
                _uiState.update { it.copy(exportProgress = done.toFloat() / total) }
            }
            _message.emit(
                buildString {
                    append("Exported ${result.exported} item${if (result.exported == 1) "" else "s"}")
                    append(" (${formatFileSize(result.totalBytes)})")
                    if (result.hasFailures) append(" · ${result.skipped} unreadable")
                }
            )
        } catch (error: java.io.IOException) {
            _message.emit(error.message ?: "Export failed")
        } finally {
            _uiState.update { it.copy(exportProgress = null) }
        }
    }

    /** Hands the album back to picking its own cover from its most recent item. */
    internal fun clearAlbumCover(album: Album) = viewModelScope.launch {
        mediaRepository.clearAlbumCover(album)
        dismissCoverChoices()
        _message.emit("Album cover reset to automatic")
    }

    internal fun completePendingMutation(approved: Boolean) {
        val pending = _pendingMutation.value ?: return
        _pendingMutation.value = null
        if (!approved) {
            pendingDeleteChunks.clear()
            return
        }
        when (val mutation = pending.mutation) {
            is AlbumMutation.Rename -> completeRename(mutation.album, mutation.newName)
            is AlbumMutation.Delete -> viewModelScope.launch {
                try {
                    mediaRepository.markItemsTrashed(mutation.items)
                    if (pendingDeleteChunks.isEmpty()) {
                        mediaRepository.syncMediaStore()
                        _message.emit("Moved to Trash")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CrashLog.e(TAG, "Recording ${mutation.items.size} trashed album items failed", e)
                }
                requestNextDeleteChunk()
            }
        }
    }

    private fun completeRename(album: Album, newName: String) = viewModelScope.launch {
        // renameAlbum already returns a Result; the sync it triggers afterwards may still throw.
        runCatching { mediaRepository.renameAlbum(album, newName) }
            .getOrElse { Result.failure(it) }
            .onSuccess { _message.emit("Album renamed") }
            .onFailure { error -> reportError(error.message, "Unable to rename album") }
    }

    fun compressAlbums(albums: List<Album>, actionOverride: CompressionOriginalAction? = null) {
        viewModelScope.launch {
            if (albums.isEmpty()) return@launch
            val allItems = mutableListOf<MediaItem>()
            albums.forEach { album ->
                val items = mediaRepository.getMediaByBucket(album.id).first()
                allItems.addAll(items)
            }
            if (allItems.isEmpty()) {
                _message.emit("No media items found in selected albums")
                return@launch
            }
            val prefs = preferencesDataSource.userPreferencesFlow.first()
            if (actionOverride == null) {
                val estimate = CompressionEstimator.estimate(
                    allItems.map { it.toEstimatorInput() },
                    prefs,
                    compressionQueueRepository.calibration.first(),
                )
                _compressionPrompt.value = AlbumCompressionPrompt(
                    itemCount = allItems.size,
                    estimate = estimate,
                    items = allItems,
                )
                return@launch
            }
            try {
                compressionQueueRepository.enqueue(allItems, actionOverride)
                _message.emit("Queued ${allItems.size} items for compression")
            } catch (e: Exception) {
                _message.emit("Unable to queue compression: ${e.message}")
            }
        }
    }

    fun compressGroup(groupName: String, actionOverride: CompressionOriginalAction? = null) {
        val albumsInGroup = _uiState.value.albums.filter { it.groupName == groupName }
        if (albumsInGroup.isEmpty()) {
            viewModelScope.launch { _message.emit("No albums found in group \"$groupName\"") }
            return
        }
        compressAlbums(albumsInGroup, actionOverride)
    }

    fun dismissCompressionPrompt() {
        _compressionPrompt.value = null
    }

    fun chooseCompressionAction(action: CompressionOriginalAction) {
        val prompt = _compressionPrompt.value
        _compressionPrompt.value = null
        if (prompt != null && prompt.items.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    compressionQueueRepository.enqueue(prompt.items, action)
                    _message.emit("Queued ${prompt.items.size} items for compression")
                } catch (e: Exception) {
                    _message.emit("Unable to queue compression: ${e.message}")
                }
            }
        }
    }

}

internal data class SmartCategoryCounts(
    val videosCount: Int = 0,
    val favoritesCount: Int = 0,
    val collagesCount: Int = 0,
    val screenshotsCount: Int = 0,
    val gifsCount: Int = 0,
)

internal data class AlbumsUiState(
    val albums: List<Album> = emptyList(),
    val hiddenAlbums: List<Album> = emptyList(),
    /** True while hidden albums are being shown temporarily. */
    val isRevealingHidden: Boolean = false,
    /** 0..1 while an album export is running, null otherwise. */
    val exportProgress: Float? = null,
    val isLoading: Boolean = true,
    val favoritesCount: Int = 0,
    val trashCount: Int = 0,
    val error: String? = null,
    val sortOrder: AlbumSortOrder = AlbumSortOrder.NEWEST,
    val gridDensity: AlbumGridDensity = AlbumGridDensity.NORMAL,
    val coverAlbum: Album? = null,
    val coverItems: List<MediaItem> = emptyList(),
    /** Adds each folder's size beside its count — Settings → Gallery. */
    val showAlbumSize: Boolean = false,
    /** Whether a folder-lock PIN exists; without one, locking or opening a folder asks for one. */
    val hasFolderLockPin: Boolean = false,
    val folderLockBiometricsEnabled: Boolean = false,
    val smartCategories: SmartCategoryCounts = SmartCategoryCounts(),
    val sharedAlbums: List<SharedAlbum> = emptyList(),
    val selectedTab: AlbumTab = AlbumTab.MY_ALBUMS,
    val expandedGroupNames: Set<String> = emptySet(),
    val isHierarchyView: Boolean = false,
    val isMerging: Boolean = false,
    val mergeProgress: Float? = null,
)

enum class AlbumTab {
    MY_ALBUMS,
    SHARED_ALBUMS,
}

/** An album waiting for the user to choose where its zip should go. */
internal data class PendingAlbumExport(
    val album: Album,
    val items: List<MediaItem>,
)

internal data class PendingAlbumMutation(
    val pendingIntent: PendingIntent,
    val mutation: AlbumMutation,
)

private const val TAG = "AlbumsViewModel"
private const val DELETE_CHUNK_SIZE = 1000

internal sealed interface AlbumMutation {
    val album: Album
    val items: List<MediaItem>

    data class Rename(
        override val album: Album,
        override val items: List<MediaItem>,
        val newName: String,
    ) : AlbumMutation

    data class Delete(
        override val album: Album,
        override val items: List<MediaItem>,
    ) : AlbumMutation
}

internal data class AlbumCompressionPrompt(
    val itemCount: Int,
    val estimate: CompressionEstimator.Estimate,
    val items: List<MediaItem>,
)
