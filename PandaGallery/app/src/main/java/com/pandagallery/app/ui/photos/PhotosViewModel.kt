package com.pandagallery.app.ui.photos

import android.content.Context
import android.app.PendingIntent
import android.net.Uri
import android.provider.MediaStore
import com.pandagallery.app.data.diagnostics.CrashLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.data.cleanup.CleanupRepository
import com.pandagallery.app.data.cleanup.CleanupCategory
import com.pandagallery.app.data.smart.SmartIndexRepository
import com.pandagallery.app.data.smart.SmartSearchRepository
import com.pandagallery.app.data.smart.SmartOrganizerRepository
import com.pandagallery.app.data.smart.SmartGroupType
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.data.sharing.MediaSharePreparer
import com.pandagallery.app.data.vault.PrivateVaultRepository
import com.pandagallery.app.domain.compression.CompressionEstimator
import com.pandagallery.app.domain.compression.toEstimatorInput
import com.pandagallery.app.domain.model.CompressionOriginalAction
import com.pandagallery.app.domain.model.Album
import com.pandagallery.app.domain.model.GridDensity
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.SortOrder
import com.pandagallery.app.domain.model.sortSupportsDateSections
import com.pandagallery.app.domain.model.resolveMediaSort
import com.pandagallery.app.domain.model.TimelineHeader
import com.pandagallery.app.domain.model.TimelineItem
import com.pandagallery.app.domain.model.zoomIn
import com.pandagallery.app.domain.model.zoomOut
import com.pandagallery.app.domain.model.bulkFavoriteTarget
import com.pandagallery.app.domain.model.MediaContentFilter
import com.pandagallery.app.domain.model.filterMedia
import com.pandagallery.app.domain.model.MediaSearchCriteria
import com.pandagallery.app.domain.model.SmartCollection
import com.pandagallery.app.domain.model.filterCollection
import com.pandagallery.app.domain.model.filterMediaForSearch
import com.pandagallery.app.domain.model.StoryHighlight
import com.pandagallery.app.domain.model.buildStoryHighlights
import com.pandagallery.app.domain.search.LabelSuggestion
import com.pandagallery.app.data.metadata.MediaMetadataRepository
import com.pandagallery.app.domain.model.PhotoGpsItem
import com.pandagallery.app.domain.model.PhotoGpsCluster
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val privateVaultRepository: PrivateVaultRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val compressionQueueRepository: CompressionQueueRepository,
    private val mediaSharePreparer: MediaSharePreparer,
    private val cleanupRepository: CleanupRepository,
    private val smartIndexRepository: SmartIndexRepository,
    private val smartSearchRepository: SmartSearchRepository,
    private val smartOrganizerRepository: SmartOrganizerRepository,
    private val viewerSession: com.pandagallery.app.ui.viewer.ViewerSession,
    private val storyHighlightExporter: com.pandagallery.app.data.story.StoryHighlightExporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val initialFilter = resolveInitialFilter(savedStateHandle)

    private val _uiState = MutableStateFlow(PhotosUiState(personGroupKey = initialFilter.personGroupKey()))
    internal val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    private val _gridDensity = MutableStateFlow(GridDensity.NORMAL)
    private val _mediaFilter = MutableStateFlow<MediaFilter>(initialFilter)
    private val _contentFilter = MutableStateFlow(MediaContentFilter.ALL)
    private val _searchCriteria = MutableStateFlow(MediaSearchCriteria())
    private val _pendingMutation = MutableStateFlow<PendingMediaMutation?>(null)
    val pendingMutation: StateFlow<PendingMediaMutation?> = _pendingMutation.asStateFlow()
    /**
     * The rest of a batch too large for one system confirmation, asked for one chunk at a time
     * as each is approved. See [MUTATION_CHUNK_SIZE].
     */
    private val remainingMutationChunks = kotlin.collections.ArrayDeque<MediaMutation>()
    private val _compressionPrompt = MutableStateFlow<CompressionPrompt?>(null)
    val compressionPrompt: StateFlow<CompressionPrompt?> = _compressionPrompt.asStateFlow()
    private val _trashCompressPrompt = MutableStateFlow<TrashCompressPrompt?>(null)
    val trashCompressPrompt: StateFlow<TrashCompressPrompt?> = _trashCompressPrompt.asStateFlow()
    private val _compressionQueued = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val compressionQueued = _compressionQueued.asSharedFlow()
    private val _albumTransferPrompt = MutableStateFlow<AlbumTransferPrompt?>(null)
    internal val albumTransferPrompt: StateFlow<AlbumTransferPrompt?> = _albumTransferPrompt.asStateFlow()
    private val _isStorageOpportunityDismissed = MutableStateFlow(false)
    private val _galleryMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    internal val galleryMessage = _galleryMessage.asSharedFlow()
    /**
     * Progress of a copy or move, so a long transfer reports itself the way compression does
     * rather than leaving the screen looking frozen for minutes.
     */
    private val _transferProgress = MutableStateFlow<MediaTransferProgress?>(null)
    internal val transferProgress: StateFlow<MediaTransferProgress?> = _transferProgress.asStateFlow()
    private val _renamePrompt = MutableStateFlow<MediaItem?>(null)
    internal val renamePrompt: StateFlow<MediaItem?> = _renamePrompt.asStateFlow()
    /**
     * The last "remove from this person", kept so it can be offered back.
     *
     * Undo rather than a confirmation dialog: removing photos from a group is cheap to reverse
     * and the user is usually removing several in a row, which a dialog per batch would make
     * tedious.
     */
    private val _personRemoval = MutableStateFlow<PersonRemoval?>(null)
    internal val personRemoval: StateFlow<PersonRemoval?> = _personRemoval.asStateFlow()

    data class TrashOperation(val items: List<MediaItem>)
    private val _lastTrashOperation = MutableStateFlow<TrashOperation?>(null)
    internal val lastTrashOperation: StateFlow<TrashOperation?> = _lastTrashOperation.asStateFlow()

    fun undoTrash() {
        val op = _lastTrashOperation.value ?: return
        _lastTrashOperation.value = null
        viewModelScope.launch {
            mediaRepository.markItemsRestored(op.items)
        }
    }

    fun clearTrashOperation() {
        _lastTrashOperation.value = null
    }

    fun consumeLastViewedMediaId(): Long? = viewerSession.consumeLastViewedMediaId()

    /**
     * The sort the visible grid uses: a folder's own choice when it has one, otherwise the
     * library-wide default. Derived rather than held as state so Settings, the sort menu and
     * every open grid cannot drift apart — there is one stored value behind all of them.
     */
    private val activeSortOrder = combine(
        _mediaFilter,
        preferencesDataSource.userPreferencesFlow,
    ) { filter, preferences ->
        val bucketId = (filter as? MediaFilter.Bucket)?.bucketId
        MediaSortState(
            order = resolveMediaSort(
                bucketId = bucketId,
                defaultSort = preferences.mediaSortOrder,
                folderOverrides = preferences.folderSortOverrides,
                perFolderSortEnabled = preferences.perFolderSortEnabled,
            ),
            // Drives the "this folder only" note in the sort menu, so the user can tell which
            // of the two scopes they are about to change before they change it.
            isFolderScoped = bucketId != null && preferences.perFolderSortEnabled,
        )
    }.distinctUntilChanged()

    init {
        loadMedia(showSpinner = false)
        observeMedia()
        observeTrashExpiry()
        observeSearchAlbums()
        observeSearchDiscovery()
        observePeopleSuggestions()
        observeViewPreferences()
        observeLibraryChanges()
        observeViewerSessionSelection()
    }

    /**
     * Re-syncs whenever the device's media changes, so photos taken or deleted in
     * another app show up without restarting PandaGallery.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeLibraryChanges() {
        viewModelScope.launch {
            mediaRepository.mediaStoreChanges()
                .drop(1) // the observer replays immediately; init already synced
                .debounce(1000L)
                .collect { runCatching { mediaRepository.syncMediaStore() } }
        }
    }

    /**
     * Filter and grid density are restored from the last session. Sort order is not seeded here:
     * it is derived from preferences on every emission by [activeSortOrder], so a default changed
     * in Settings reaches an already-open grid instead of waiting for the next launch.
     */
    private fun observeViewPreferences() {
        viewModelScope.launch {
            preferencesDataSource.userPreferencesFlow.collect { preferences ->
                _contentFilter.value = preferences.mediaContentFilter
                _gridDensity.value = preferences.gridDensity
                _uiState.update {
                    it.copy(
                        trashRetentionDays = preferences.trashRetentionDays,
                        removeLocationWhenSharing = preferences.removeLocationWhenSharing,
                        convertHeifWhenSharing = preferences.convertHeifWhenSharing,
                    )
                }
            }
        }
    }

    /**
     * Feeds the empty search state: what the index found, and what the user searched before.
     * Both only make sense before a query exists, so they are cheap to keep resident.
     */
    /**
     * Named people, offered on the empty search screen.
     *
     * A name the user typed on the People screen is not in any photo's text, so nothing about it
     * is discoverable by browsing — a chip per person is how "search for photos of X" becomes
     * something you can find rather than something you have to guess is supported.
     */
    private fun observePeopleSuggestions() {
        viewModelScope.launch {
            combine(
                smartOrganizerRepository.groups,
                smartOrganizerRepository.personFaces,
                mediaRepository.getAllMedia(),
                preferencesDataSource.userPreferencesFlow,
            ) { groups, faces, media, preferences ->
                if (!preferences.faceGroupingEnabled) return@combine emptyList()
                val uriById = media.associate { it.id to it.uri }
                groups
                    .filter { it.type == SmartGroupType.PEOPLE && !it.title.startsWith("Person ") }
                    .mapNotNull { group ->
                        val face = faces[group.key] ?: return@mapNotNull null
                        PersonSuggestion(
                            name = group.title,
                            photoCount = group.mediaIds.size,
                            faceUri = uriById[face.mediaId],
                            face = face,
                        )
                    }
            }.collect { people ->
                _uiState.update { it.copy(peopleSuggestions = people) }
            }
        }
    }

    private fun observeSearchDiscovery() {
        viewModelScope.launch {
            combine(
                smartSearchRepository.labelSuggestions,
                smartSearchRepository.history,
                preferencesDataSource.userPreferencesFlow,
            ) { labels, history, preferences ->
                Triple(labels.takeIf { preferences.smartIndexEnabled }.orEmpty(), history, Unit)
            }.collect { (labels, history, _) ->
                _uiState.update { it.copy(labelSuggestions = labels, searchHistory = history) }
            }
        }
    }

    /**
     * Runs a query the user picked rather than typed. Tapping a suggestion is as deliberate
     * as pressing search, so it is remembered the same way.
     */
    fun searchFor(query: String) {
        updateSearchQuery(query)
        commitSearchQuery()
    }

    /** Called from the keyboard's search action, so only committed queries are stored. */
    fun commitSearchQuery() = viewModelScope.launch {
        smartSearchRepository.recordSearch(_searchCriteria.value.query)
    }

    fun forgetSearchQuery(query: String) = viewModelScope.launch {
        smartSearchRepository.forgetSearch(query)
    }

    fun clearSearchHistory() = viewModelScope.launch {
        smartSearchRepository.clearHistory()
    }

    private fun observeSearchAlbums() {
        viewModelScope.launch {
            mediaRepository.getAlbums().collect { albums ->
                _uiState.update { it.copy(searchAlbums = albums) }
            }
        }
    }

    private fun observeTrashExpiry() {
        viewModelScope.launch {
            mediaRepository.getTrashExpiryDates().collect { expiryDates ->
                val now = System.currentTimeMillis()
                val days = expiryDates.mapValues { (_, expiry) ->
                    (((expiry - now).coerceAtLeast(0L) + DAY_MILLIS - 1L) / DAY_MILLIS).toInt()
                }
                _uiState.update { it.copy(trashRemainingDays = days) }
            }
        }
    }

    private fun loadMedia(showSpinner: Boolean = false) {
        viewModelScope.launch {
            if (showSpinner && _uiState.value.timelineItems.isEmpty()) {
                _uiState.update { it.copy(isLoading = true) }
            }
            try {
                mediaRepository.syncMediaStore()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.message)
                }
            }
        }
    }

    /** Manual pull-to-refresh; keeps the grid visible while it runs. */
    fun refreshFromPull() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadMedia(showSpinner = false)
    }

    /**
     * Hands the currently visible, sorted list to the viewer so paging stays inside
     * this screen's results instead of widening to the whole album.
     * Also carries the active selection and selection mode so swiping and selecting in the
     * full-screen viewer stays in sync.
     */
    fun prepareViewerSession(clickedItem: MediaItem? = null) {
        val items = _uiState.value.mediaItems
        viewerSession.publish(
            ids = items.map { it.id },
            items = items,
            initialItem = clickedItem,
            selected = _uiState.value.selectedItems,
            inSelectionMode = _uiState.value.isSelectionMode,
        )
    }

    private fun observeViewerSessionSelection() {
        viewModelScope.launch {
            viewerSession.selectedIds.collect { selected ->
                _uiState.update { current ->
                    if (current.selectedItems != selected) {
                        current.copy(
                            selectedItems = selected,
                            isSelectionMode = selected.isNotEmpty() || current.isSelectionMode,
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeMedia() {
        viewModelScope.launch {
            val searchInputs = combine(
                _contentFilter,
                // Debounced so typing doesn't re-query the index per keystroke.
                _searchCriteria.debounce { criteria -> if (criteria.query.isBlank()) 0L else 220L },
                // A short change token rather than the index contents: the old version held
                // every photo's OCR text in memory just to answer one query.
                smartIndexRepository.observeIndexVersion(),
                preferencesDataSource.userPreferencesFlow,
            ) { contentFilter, criteria, indexVersion, preferences ->
                SearchTrigger(contentFilter, criteria, preferences.smartIndexEnabled, indexVersion)
            }.mapLatest { trigger ->
                val matches = if (trigger.smartIndexEnabled && trigger.criteria.query.isNotBlank()) {
                    smartSearchRepository.search(trigger.criteria.query).matchedIds
                } else {
                    emptySet()
                }
                SearchInputs(trigger.contentFilter, trigger.criteria, matches)
            }
            combine(
                _mediaFilter.flatMapLatest { filter ->
                    when (filter) {
                        MediaFilter.All -> mediaRepository.getAllMedia()
                        MediaFilter.Videos -> mediaRepository.getAllMedia().map { items -> items.filter { it.isVideo } }
                        is MediaFilter.Bucket -> mediaRepository.getMediaByBucket(filter.bucketId)
                        MediaFilter.Favorites -> mediaRepository.getFavorites()
                        MediaFilter.Trash -> mediaRepository.getTrashedMedia()
                        MediaFilter.Search -> mediaRepository.getAllMedia()
                        is MediaFilter.Collection -> mediaRepository.getAllMedia().map { filterCollection(it, filter.collection) }
                        is MediaFilter.Cleanup -> mediaRepository.getAllMedia().map { items ->
                            val ids = cleanupRepository.idsFor(filter.category)
                            items.filter { it.id in ids }
                        }
                        is MediaFilter.SmartGroup -> combine(
                            mediaRepository.getAllMedia(),
                            smartOrganizerRepository.idsFor(filter.type, filter.key),
                        ) { items, ids -> items.filter { it.id in ids } }
                    }
                },
                activeSortOrder,
                _gridDensity,
                searchInputs,
                _isStorageOpportunityDismissed,
            ) { media, sortState, density, search, isOpportunityDismissed ->
                val sort = sortState.order
                val searched = if (_mediaFilter.value == MediaFilter.Search) {
                    filterMediaForSearch(media, search.criteria, search.smartMatchIds)
                } else {
                    media
                }
                val sorted = sortMedia(filterMedia(searched, search.contentFilter), sort)
                val timeline = buildTimeline(sorted, sort, density)
                val opportunity = if (_mediaFilter.value == MediaFilter.All && !isOpportunityDismissed) {
                    val candidates = sorted.filter { !it.isTrashed && !it.isCompressed && it.size >= 4_000_000L }
                    if (candidates.size >= 3) {
                        val totalBytes = candidates.sumOf { it.size }
                        val savedBytes = (totalBytes * 0.65).toLong()
                        StorageOpportunity(
                            candidateCount = candidates.size,
                            totalOriginalBytes = totalBytes,
                            potentialSavedBytes = savedBytes,
                            candidateIds = candidates.map { it.id },
                        )
                    } else null
                } else null
                _uiState.update {
                    it.copy(
                        mediaItems = sorted,
                        timelineItems = timeline,
                        storyHighlights = buildStoryHighlights(sorted),
                        sortOrder = sort,
                        isFolderSortScoped = sortState.isFolderScoped,
                        gridDensity = density,
                        contentFilter = search.contentFilter,
                        searchCriteria = search.criteria,
                        mediaCount = sorted.size,
                        storageOpportunity = opportunity,
                        hasLoadedFromCache = true,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
            }.flowOn(Dispatchers.Default).collect()
        }

        // Track counts
        viewModelScope.launch {
            mediaRepository.getMediaCount().collect { count ->
                _uiState.update { it.copy(mediaCount = count) }
            }
        }
    }

    fun refresh() {
        loadMedia()
    }

    /**
     * Applies a sort chosen from the toolbar. Inside a folder with per-folder sort on this is
     * remembered for that folder alone; anywhere else it becomes the library-wide default, which
     * is what the same menu has always done.
     */
    fun setSortOrder(order: SortOrder) {
        val bucketId = (_mediaFilter.value as? MediaFilter.Bucket)?.bucketId
        viewModelScope.launch {
            val preferences = preferencesDataSource.userPreferencesFlow.first()
            when {
                bucketId == null || !preferences.perFolderSortEnabled ->
                    preferencesDataSource.updateMediaSortOrder(order)
                // Choosing the default is how a folder goes back to following it, so it drops the
                // override rather than storing a copy — otherwise Settings would keep counting the
                // folder as sorted differently while it looked identical.
                order == preferences.mediaSortOrder ->
                    preferencesDataSource.clearFolderSortOrder(bucketId)
                else -> preferencesDataSource.updateFolderSortOrder(bucketId, order)
            }
        }
    }

    fun setMediaFilter(filter: MediaFilter) {
        if (_mediaFilter.value == filter) return
        _mediaFilter.value = filter
        _uiState.update { it.copy(personGroupKey = filter.personGroupKey()) }
    }

    /**
     * Takes the selected photos out of the person group being viewed.
     *
     * The faces stay indexed — this is a statement about who is in the photo, not a reason to
     * re-detect anything — so it is recorded as an exclusion the next clustering run respects.
     */
    internal fun removeSelectedFromPerson() {
        val groupKey = _mediaFilter.value.personGroupKey() ?: return
        val mediaIds = _uiState.value.selectedItems.toList()
        if (mediaIds.isEmpty()) return
        viewModelScope.launch {
            smartOrganizerRepository.removeFromPerson(groupKey, mediaIds)
            clearSelection()
            _personRemoval.value = PersonRemoval(groupKey, mediaIds)
        }
    }

    internal fun undoPersonRemoval() {
        val removal = _personRemoval.value ?: return
        _personRemoval.value = null
        viewModelScope.launch {
            smartOrganizerRepository.restoreToPerson(removal.groupKey, removal.mediaIds)
        }
    }

    internal fun clearPersonRemoval() {
        _personRemoval.value = null
    }

    /** Chooses which photo's face represents this person on the People screen. */
    internal fun setPersonCoverFromSelection() {
        val groupKey = _mediaFilter.value.personGroupKey() ?: return
        val mediaId = _uiState.value.selectedItems.singleOrNull() ?: return
        viewModelScope.launch {
            smartOrganizerRepository.setPersonCover(groupKey, mediaId)
            clearSelection()
            _galleryMessage.tryEmit("Cover photo updated")
        }
    }

    fun updateSearchQuery(query: String) {
        _searchCriteria.update { it.copy(query = query) }
    }

    internal fun updateSearchCriteria(criteria: MediaSearchCriteria) {
        _searchCriteria.value = criteria
    }

    fun setContentFilter(filter: MediaContentFilter) {
        _contentFilter.value = filter
        viewModelScope.launch { preferencesDataSource.updateMediaContentFilter(filter) }
    }

    fun setGridDensity(density: GridDensity) {
        _gridDensity.value = density
        viewModelScope.launch { preferencesDataSource.updateGridDensity(density) }
    }

    fun cycleGridDensity() {
        setGridDensity(
            when (_gridDensity.value) {
                GridDensity.NORMAL -> GridDensity.MONTH
                GridDensity.MONTH -> GridDensity.YEAR
                GridDensity.YEAR -> GridDensity.EXPANDED
                GridDensity.EXPANDED -> GridDensity.NORMAL
            },
        )
    }

    fun zoomInGrid() = setGridDensity(_gridDensity.value.zoomIn())

    fun zoomOutGrid() = setGridDensity(_gridDensity.value.zoomOut())

    // Selection management
    fun toggleSelection(item: MediaItem) {
        var updatedSelection: Set<Long> = emptySet()
        _uiState.update { state ->
            val selected = state.selectedItems.toMutableSet()
            if (selected.contains(item.id)) {
                selected.remove(item.id)
            } else {
                selected.add(item.id)
            }
            updatedSelection = selected
            state.copy(
                selectedItems = selected,
                isSelectionMode = true,
            )
        }
        viewerSession.setSelectedIds(updatedSelection)
    }

    fun setSelectedItems(items: Set<Long>) {
        _uiState.update { state ->
            state.copy(
                selectedItems = items,
                isSelectionMode = items.isNotEmpty() || state.isSelectionMode,
            )
        }
        viewerSession.setSelectedIds(items)
    }

    /**
     * Selects every copy except the best one in each duplicate group — the action a
     * duplicate finder exists for.
     */
    internal fun selectRedundantDuplicates() {
        val filter = _mediaFilter.value as? MediaFilter.Cleanup ?: return
        val redundant = cleanupRepository.redundantIdsFor(filter.category)
        if (redundant.isEmpty()) return
        var updatedSelection: Set<Long> = emptySet()
        _uiState.update { state ->
            val selected = state.mediaItems.map { it.id }.filterTo(mutableSetOf()) { it in redundant }
            updatedSelection = selected
            state.copy(
                selectedItems = selected,
                isSelectionMode = true,
            )
        }
        viewerSession.setSelectedIds(updatedSelection)
    }

    internal fun redundantDuplicateCount(): Int {
        val filter = _mediaFilter.value as? MediaFilter.Cleanup ?: return 0
        return cleanupRepository.redundantIdsFor(filter.category).size
    }

    internal fun duplicateGroupCount(): Int {
        val filter = _mediaFilter.value as? MediaFilter.Cleanup ?: return 0
        return cleanupRepository.groupCountFor(filter.category)
    }

    fun selectAll() {
        var updatedSelection: Set<Long> = emptySet()
        _uiState.update { state ->
            val all = state.mediaItems.map { it.id }.toSet()
            updatedSelection = all
            state.copy(
                selectedItems = all,
                isSelectionMode = true,
            )
        }
        viewerSession.setSelectedIds(updatedSelection)
    }

    fun toggleDateSelection(dateKey: String) {
        var updatedSelection: Set<Long>? = null
        _uiState.update { state ->
            val dateIds = state.timelineItems
                .filterIsInstance<TimelineItem.Header>()
                .firstOrNull { it.header.dateKey == dateKey }
                ?.header
                ?.itemIds
                .orEmpty()
            if (dateIds.isEmpty()) return@update state

            val newSelection = if (dateIds.all(state.selectedItems::contains)) {
                state.selectedItems - dateIds
            } else {
                state.selectedItems + dateIds
            }
            updatedSelection = newSelection
            state.copy(
                selectedItems = newSelection,
                isSelectionMode = true,
            )
        }
        updatedSelection?.let { viewerSession.setSelectedIds(it) }
    }

    fun clearSelection() {
        viewerSession.clearSelection()
        _uiState.update {
            it.copy(selectedItems = emptySet(), isSelectionMode = false)
        }
    }

    /** Enters selection mode with nothing selected — the user picks the items. */
    fun enterSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = true) }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            mediaRepository.toggleFavorite(item)
        }
    }

    fun trashSelectedItems() {
        requestMutation(MediaMutation.Trash(selectedItems()))
    }

    fun restoreSelectedItems() {
        requestMutation(MediaMutation.Restore(selectedItems()))
    }

    internal fun permanentlyDeleteSelectedItems() {
        requestMutation(MediaMutation.PermanentDelete(selectedItems()))
    }

    /**
     * Items whose retention window has already lapsed. Android's auto-cleanup worker
     * can only delete these unsupervised when MANAGE_MEDIA is granted, so the Trash
     * screen offers to clear them with a normal delete confirmation instead.
     */
    internal fun deleteExpiredTrashItems() {
        val expiredIds = _uiState.value.trashRemainingDays.filterValues { it <= 0 }.keys
        val expired = _uiState.value.mediaItems.filter { it.id in expiredIds }
        if (expired.isEmpty()) return
        requestMutation(MediaMutation.PermanentDelete(expired))
    }

    internal fun emptyTrash() {
        requestMutation(MediaMutation.PermanentDelete(_uiState.value.mediaItems))
    }

    fun favoriteSelectedItems() {
        val items = selectedItems()
        requestMutation(MediaMutation.Favorite(items, bulkFavoriteTarget(items.map { it.isFavorite })))
    }

    fun moveSelectedToPrivate() {
        val items = selectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val vaultIds = runCatching { privateVaultRepository.import(items) }
                .onFailure { error ->
                    CrashLog.e(TAG, "Private vault import failed for ${items.size} items", error)
                    reportActionError(error.message, "Unable to move items to Private")
                }
                .getOrNull()
            if (vaultIds != null) {
                runCatching {
                    MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri })
                }.onSuccess { request ->
                    _pendingMutation.value = PendingMediaMutation(request, MediaMutation.MoveToPrivate(items, vaultIds))
                }.onFailure { error ->
                    // The vault already holds copies; don't leave them orphaned when the
                    // originals can't be removed.
                    CrashLog.e(TAG, "Delete request for Private move failed (${items.size} items)", error)
                    runCatching { privateVaultRepository.delete(vaultIds) }
                    reportActionError(null, "Unable to move items to Private")
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun completePendingMutation(approved: Boolean) {
        val request = _pendingMutation.value ?: return
        _pendingMutation.value = null
        clearSelection()
        val nextChunk = if (approved) remainingMutationChunks.removeFirstOrNull() else null
        if (!approved) remainingMutationChunks.clear()

        viewModelScope.launch {
            try {
                applyApprovedMutation(request, approved)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashLog.e(TAG, "Completing ${request.mutation::class.simpleName} of ${request.mutation.items.size} items failed", e)
                reportActionError(e.message, "Something went wrong finishing that action")
            }
            nextChunk?.let(::requestMutationChunk)
        }
    }

    private suspend fun applyApprovedMutation(request: PendingMediaMutation, approved: Boolean) {
        if (!approved) {
            if (request.mutation is MediaMutation.MoveToPrivate) {
                privateVaultRepository.delete(request.mutation.vaultIds)
            }
            return
        }
        when (val mutation = request.mutation) {
            is MediaMutation.Favorite -> mediaRepository.setFavorite(mutation.items, mutation.favorite)
            is MediaMutation.Trash -> {
                mediaRepository.markItemsTrashed(mutation.items)
                // A chunked batch accumulates, so Undo restores all of it.
                val earlier = if (request.continuesBatch) _lastTrashOperation.value?.items.orEmpty() else emptyList()
                _lastTrashOperation.value = TrashOperation(earlier + mutation.items)
            }
            is MediaMutation.Restore -> mediaRepository.markItemsRestored(mutation.items)
            is MediaMutation.CompressAndRestore -> {
                mediaRepository.markItemsRestored(mutation.items)
                try {
                    compressionQueueRepository.enqueue(mutation.items, mutation.action)
                    _compressionQueued.emit(mutation.items.size)
                } catch (e: Exception) {
                    reportActionError(e.message, "Unable to queue compression")
                }
            }
            is MediaMutation.MoveToPrivate -> mutation.items.forEach { mediaRepository.removeMedia(it.id) }
            is MediaMutation.MoveToAlbum -> completeMoveToAlbum(mutation.items, mutation.relativePath)
            is MediaMutation.Rename -> completeRename(mutation.item, mutation.requestedName)
            is MediaMutation.PermanentDelete -> mediaRepository.permanentlyDelete(mutation.items)
            is MediaMutation.Rotate -> completeRotate(mutation.items, mutation.degrees)
            is MediaMutation.DeleteOriginalsAfterCompression -> mutation.items.forEach { mediaRepository.removeMedia(it.id) }
        }
    }

    fun shareSelectedItems(
        removeLocation: Boolean? = null,
        convertHeifRaw: Boolean? = null,
    ) {
        val items = selectedItems()
        if (items.isEmpty()) return

        viewModelScope.launch {
            try {
                val payload = mediaSharePreparer.prepare(
                    items = items,
                    stripLocation = removeLocation,
                    convertHeif = convertHeifRaw,
                    convertRaw = convertHeifRaw,
                )
                context.startActivity(payload.createChooserIntent())
                clearSelection()
            } catch (error: IOException) {
                reportActionError(null, "Unable to prepare the selected images for sharing")
            } catch (error: IllegalStateException) {
                reportActionError(null, "Unable to prepare the selected images for sharing")
            } catch (error: SecurityException) {
                reportActionError(null, "Unable to access the selected images for sharing")
            }
        }
    }

    internal fun showAlbumTransfer(mode: AlbumTransferMode) {
        val items = selectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            _albumTransferPrompt.value = AlbumTransferPrompt(
                items = items,
                albums = mediaRepository.getAlbums().first(),
                mode = mode,
            )
        }
    }

    internal fun selectAlbumTransferDestination(relativePath: String) {
        _albumTransferPrompt.update { prompt ->
            prompt?.copy(selectedDestinationPath = relativePath)
        }
    }

    internal fun dismissAlbumTransfer() {
        _albumTransferPrompt.value = null
    }

    internal fun confirmAlbumTransfer() {
        val prompt = _albumTransferPrompt.value ?: return
        val relativePath = prompt.selectedDestinationPath ?: return
        _albumTransferPrompt.value = null
        when (prompt.mode) {
            AlbumTransferMode.COPY -> copyToAlbum(prompt.items, relativePath)
            AlbumTransferMode.MOVE -> moveToAlbum(prompt.items, relativePath)
        }
    }

    internal fun createAlbumAndTransfer(name: String) {
        val prompt = _albumTransferPrompt.value ?: return
        viewModelScope.launch {
            mediaRepository.createAlbum(name)
                .onSuccess { path ->
                    when (prompt.mode) {
                        AlbumTransferMode.COPY -> copyToAlbum(prompt.items, path)
                        AlbumTransferMode.MOVE -> moveToAlbum(prompt.items, path)
                    }
                    _albumTransferPrompt.value = null
                }
                .onFailure { error -> reportActionError(error.message, "Unable to create album") }
        }
    }

    internal fun showRenameSelected() {
        val items = selectedItems()
        if (items.size == 1) {
            _renamePrompt.value = items.single()
        } else {
            _galleryMessage.tryEmit("Select one item to rename")
        }
    }

    internal fun dismissRename() {
        _renamePrompt.value = null
    }

    internal fun renameSelected(requestedName: String) {
        val item = _renamePrompt.value ?: return
        _renamePrompt.value = null
        if (MediaStore.canManageMedia(context)) {
            completeRename(item, requestedName)
        } else {
            requestMutation(MediaMutation.Rename(item, requestedName))
        }
    }

    private fun completeRename(item: MediaItem, requestedName: String) = viewModelScope.launch {
        mediaRepository.renameMedia(item, requestedName)
            .onSuccess { displayName ->
                clearSelection()
                _galleryMessage.emit("Renamed to $displayName")
            }
            .onFailure { error -> reportActionError(error.message, "Unable to rename media") }
    }

    private fun copyToAlbum(items: List<MediaItem>, relativePath: String) = viewModelScope.launch {
        _transferProgress.value = MediaTransferProgress(MediaOperationMode.COPY, 0, items.size)
        mediaRepository.copyToAlbum(items, relativePath) { done, total ->
            _transferProgress.value = MediaTransferProgress(MediaOperationMode.COPY, done, total)
        }
            .onSuccess { count ->
                clearSelection()
                _galleryMessage.emit("Copied $count item${if (count == 1) "" else "s"}")
            }
            .onFailure { error -> reportActionError(error.message, "Unable to copy media") }
        _transferProgress.value = null
    }

    private fun moveToAlbum(items: List<MediaItem>, relativePath: String) {
        if (MediaStore.canManageMedia(context)) {
            completeMoveToAlbum(items, relativePath)
        } else {
            requestMutation(MediaMutation.MoveToAlbum(items, relativePath))
        }
    }

    private fun completeMoveToAlbum(items: List<MediaItem>, relativePath: String) = viewModelScope.launch {
        _transferProgress.value = MediaTransferProgress(MediaOperationMode.MOVE, 0, items.size)
        mediaRepository.moveToAlbum(items, relativePath) { done, total ->
            _transferProgress.value = MediaTransferProgress(MediaOperationMode.MOVE, done, total)
        }
            .onSuccess { count ->
                clearSelection()
                _galleryMessage.emit("Moved $count item${if (count == 1) "" else "s"}")
            }
            .onFailure { error -> reportActionError(error.message, "Unable to move media") }
        _transferProgress.value = null
    }

    private fun reportActionError(message: String?, fallback: String) {
        _galleryMessage.tryEmit(message?.takeIf { it.isNotBlank() } ?: fallback)
    }

    private fun selectedItems(): List<MediaItem> =
        _uiState.value.mediaItems.filter { it.id in _uiState.value.selectedItems }

    private fun requestMutation(mutation: MediaMutation) {
        if (mutation.items.isEmpty()) {
            clearSelection()
            return
        }
        remainingMutationChunks.clear()
        val chunks = mutation.items.chunked(MUTATION_CHUNK_SIZE)
        if (chunks.size > 1) {
            chunks.drop(1).forEach { remainingMutationChunks.addLast(mutation.withItems(it)) }
            requestMutationChunk(mutation.withItems(chunks.first()), continuesBatch = false)
        } else {
            requestMutationChunk(mutation, continuesBatch = false)
        }
    }

    /**
     * Asks the system to confirm one chunk. Creating the request is a binder call into
     * MediaProvider that can fail (a URI that no longer exists, a revoked permission, an
     * oversized batch); it used to run unguarded on the main thread and take the app down.
     */
    private fun requestMutationChunk(mutation: MediaMutation, continuesBatch: Boolean = true) {
        val pendingIntent = try {
            createMutationRequest(mutation)
        } catch (e: Exception) {
            remainingMutationChunks.clear()
            CrashLog.e(TAG, "Creating ${mutation::class.simpleName} request for ${mutation.items.size} items failed", e)
            reportActionError(null, "Unable to start that action. Details are in Settings › Diagnostics.")
            clearSelection()
            return
        }
        _pendingMutation.value = PendingMediaMutation(pendingIntent, mutation, continuesBatch)
    }

    private fun createMutationRequest(mutation: MediaMutation): PendingIntent {
        val uris = mutation.items.map { it.uri }
        return when (mutation) {
            is MediaMutation.Favorite -> MediaStore.createFavoriteRequest(
                context.contentResolver,
                uris,
                mutation.favorite,
            )
            is MediaMutation.Trash -> MediaStore.createTrashRequest(
                context.contentResolver,
                uris,
                true,
            )
            is MediaMutation.Restore -> MediaStore.createTrashRequest(
                context.contentResolver,
                uris,
                false,
            )
            is MediaMutation.CompressAndRestore -> MediaStore.createTrashRequest(
                context.contentResolver,
                uris,
                false,
            )
            is MediaMutation.DeleteOriginalsAfterCompression -> MediaStore.createDeleteRequest(
                context.contentResolver,
                uris,
            )
            is MediaMutation.MoveToAlbum -> MediaStore.createWriteRequest(
                context.contentResolver,
                uris,
            )
            is MediaMutation.Rename -> MediaStore.createWriteRequest(
                context.contentResolver,
                uris,
            )
            is MediaMutation.PermanentDelete -> MediaStore.createDeleteRequest(
                context.contentResolver,
                uris,
            )
            is MediaMutation.Rotate -> MediaStore.createWriteRequest(
                context.contentResolver,
                uris,
            )
            is MediaMutation.MoveToPrivate -> error("Private moves create their own delete request")
        }
    }

    fun compressSelectedItems(actionOverride: CompressionOriginalAction? = null) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedItems
            val items = _uiState.value.mediaItems.filter { it.id in selected }
            if (items.isEmpty()) return@launch
            val prefs = preferencesDataSource.userPreferencesFlow.first()
            if (actionOverride == null) {
                _compressionPrompt.value = CompressionPrompt(
                    itemCount = items.size,
                    folderPath = prefs.compressedFolderPath,
                    // Estimated from the real selection, so the user sees what this
                    // specific batch costs rather than a generic claim.
                    estimate = CompressionEstimator.estimate(
                        items.map { it.toEstimatorInput() },
                        prefs,
                        compressionQueueRepository.calibration.first(),
                    ),
                )
                return@launch
            }
            val action = actionOverride
            try {
                compressionQueueRepository.enqueue(items, action)
                clearSelection()
                _compressionQueued.emit(items.size)
            } catch (e: Exception) {
                reportActionError(e.message, "Unable to queue compression")
                clearSelection()
            }
        }
    }

    fun compressItems(items: List<MediaItem>, action: CompressionOriginalAction = CompressionOriginalAction.MOVE) {
        viewModelScope.launch {
            try {
                compressionQueueRepository.enqueue(items, action)
                _compressionQueued.emit(items.size)
            } catch (e: Exception) {
                reportActionError(e.message, "Unable to queue compression")
            }
        }
    }

    fun exportStoryReel(
        title: String,
        items: List<MediaItem>,
        soundtrack: com.pandagallery.app.data.editing.BackgroundMusicTrack = com.pandagallery.app.data.editing.BackgroundMusicTrack.ACOUSTIC_BREEZE,
        onProgress: (Int) -> Unit = {},
        onSuccess: (android.net.Uri) -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val uri = storyHighlightExporter.exportHighlightReel(
                    title = title,
                    mediaItems = items,
                    soundtrack = soundtrack,
                    onProgress = onProgress,
                )
                mediaRepository.syncMediaStore()
                onSuccess(uri)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to export highlight reel")
            }
        }
    }

    fun dismissCompressionPrompt() {
        _compressionPrompt.value = null
    }

    fun chooseCompressionAction(action: CompressionOriginalAction) {
        _compressionPrompt.value = null
        compressSelectedItems(action)
    }

    fun dismissStorageOpportunity() {
        _isStorageOpportunityDismissed.value = true
    }

    fun promptStorageOpportunityCompression() {
        val opp = _uiState.value.storageOpportunity ?: return
        val candidateSet = opp.candidateIds.toSet()
        _uiState.update {
            it.copy(
                selectedItems = candidateSet,
                isSelectionMode = true,
            )
        }
        compressSelectedItems()
    }

    fun promptCompressSelectedTrashItems() {
        val selected = _uiState.value.selectedItems
        val items = _uiState.value.mediaItems.filter { it.id in selected }
        if (items.isEmpty()) return
        val totalBytes = items.sumOf { it.size }
        val savedBytes = (totalBytes * 0.65).toLong()
        _trashCompressPrompt.value = TrashCompressPrompt(
            items = items,
            totalBytes = totalBytes,
            potentialSavedBytes = savedBytes,
        )
    }

    fun promptCompressAllTrashItems() {
        val items = _uiState.value.mediaItems
        if (items.isEmpty()) return
        val totalBytes = items.sumOf { it.size }
        val savedBytes = (totalBytes * 0.65).toLong()
        _trashCompressPrompt.value = TrashCompressPrompt(
            items = items,
            totalBytes = totalBytes,
            potentialSavedBytes = savedBytes,
        )
    }

    fun dismissTrashCompressPrompt() {
        _trashCompressPrompt.value = null
    }

    fun confirmCompressAndRestoreTrash() {
        val prompt = _trashCompressPrompt.value ?: return
        _trashCompressPrompt.value = null
        requestMutation(MediaMutation.CompressAndRestore(prompt.items))
    }

    /**
     * Rotates selected image items by [degrees] clockwise in place.
     * Non-image items (videos) in the selection are automatically omitted.
     */
    fun rotateSelectedItems(degrees: Float = 90f) {
        val items = selectedItems()
        if (items.isEmpty()) return
        val imageItems = items.filter { it.isImage && !it.isVideo }
        if (imageItems.isEmpty()) {
            _galleryMessage.tryEmit("Only images can be rotated")
            return
        }
        if (MediaStore.canManageMedia(context)) {
            clearSelection()
            completeRotate(imageItems, degrees)
        } else {
            requestMutation(MediaMutation.Rotate(imageItems, degrees))
        }
    }

    private fun completeRotate(items: List<MediaItem>, degrees: Float) {
        viewModelScope.launch {
            val imageItems = items.filter { it.isImage && !it.isVideo }
            if (imageItems.isEmpty()) {
                _galleryMessage.emit("No images to rotate")
                return@launch
            }
            _transferProgress.value = MediaTransferProgress(MediaOperationMode.ROTATE, 0, imageItems.size)
            val result = mediaRepository.rotateImages(imageItems, degrees) { done, total ->
                _transferProgress.value = MediaTransferProgress(MediaOperationMode.ROTATE, done, total)
            }
            _transferProgress.value = null
            result.onSuccess { count ->
                _galleryMessage.emit("Rotated $count image${if (count == 1) "" else "s"}")
            }.onFailure { error ->
                reportActionError(error.message, "Unable to rotate images")
            }
        }
    }

    fun updateSelectedDateTime(targetDateTimeMillis: Long, shiftOffsetMillis: Long? = null) {
        val items = selectedItems()
        if (items.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            mediaRepository.updateDateTime(items, targetDateTimeMillis, shiftOffsetMillis)
                .onSuccess { count ->
                    _galleryMessage.emit("Updated date and time for $count item${if (count == 1) "" else "s"}")
                }
                .onFailure { error ->
                    reportActionError(error.message, "Unable to update date and time")
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun updateSelectedLocation(latitude: Double?, longitude: Double?) {
        val items = selectedItems()
        if (items.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            mediaRepository.updateLocation(items, latitude, longitude)
                .onSuccess { count ->
                    val msg = if (latitude != null && longitude != null) {
                        "Updated location for $count item${if (count == 1) "" else "s"}"
                    } else {
                        "Removed location from $count item${if (count == 1) "" else "s"}"
                    }
                    _galleryMessage.emit(msg)
                }
                .onFailure { error ->
                    reportActionError(error.message, "Unable to update location")
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private val metadataRepository: MediaMetadataRepository by lazy { MediaMetadataRepository(context) }
    private val _geotaggedPhotos = MutableStateFlow<List<PhotoGpsItem>>(emptyList())
    val geotaggedPhotos: StateFlow<List<PhotoGpsItem>> = _geotaggedPhotos.asStateFlow()

    fun loadGeotaggedPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = _uiState.value.mediaItems
            val gpsList = mutableListOf<PhotoGpsItem>()
            for (item in items.take(150)) {
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
            _geotaggedPhotos.value = gpsList
        }
    }

    fun compressCluster(cluster: PhotoGpsCluster) {
        viewModelScope.launch {
            val mediaIds = cluster.items.map { it.mediaId }.toSet()
            val itemsToCompress = _uiState.value.mediaItems.filter { it.id in mediaIds }
            if (itemsToCompress.isNotEmpty()) {
                compressionQueueRepository.enqueue(itemsToCompress, CompressionOriginalAction.MOVE)
                _galleryMessage.emit("Enqueued ${itemsToCompress.size} photos for Safety Vault compression")
            }
        }
    }

    fun stripClusterGps(cluster: PhotoGpsCluster) {
        viewModelScope.launch {
            val mediaIds = cluster.items.map { it.mediaId }.toSet()
            val itemsToStrip = _uiState.value.mediaItems.filter { it.id in mediaIds }
            if (itemsToStrip.isNotEmpty()) {
                mediaRepository.updateLocation(itemsToStrip, null, null)
                _geotaggedPhotos.update { current -> current.filterNot { it.mediaId in mediaIds } }
                _galleryMessage.emit("Removed GPS from ${itemsToStrip.size} photos")
            }
        }
    }

    // ============================================
    // Timeline building
    // ============================================

    /**
     * Groups the grid into date sections.
     *
     * Only a date sort can carry date headers: under Name or Size the same day reappears further
     * down the list, which produced two sections with one date between them — a section header
     * whose count and "select all" covered items that were nowhere near it, and duplicate keys
     * that crashed the grid outright. Those sorts get a plain grid instead, which is also what
     * the user asked for by choosing them.
     */
    private fun buildTimeline(
        items: List<MediaItem>,
        order: SortOrder,
        density: GridDensity = GridDensity.NORMAL,
    ): List<TimelineItem> {
        if (items.isEmpty()) return emptyList()
        if (!sortSupportsDateSections(order)) return items.map(TimelineItem::Media)

        val timeline = mutableListOf<TimelineItem>()
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        val keyFormat = when (density) {
            GridDensity.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault())
            GridDensity.MONTH -> SimpleDateFormat("yyyy-MM", Locale.getDefault())
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
        val labelFormat = when (density) {
            GridDensity.YEAR -> SimpleDateFormat("yyyy", Locale.getDefault())
            GridDensity.MONTH -> SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        }

        val itemsByDate = items.groupBy { keyFormat.format(Date(it.sortDate)) }
        var currentDateKey = ""

        items.forEach { item ->
            val itemDate = Date(item.sortDate)
            val itemCal = Calendar.getInstance().apply { time = itemDate }
            val dateKey = keyFormat.format(itemDate)

            if (dateKey != currentDateKey) {
                currentDateKey = dateKey
                val label = when (density) {
                    GridDensity.YEAR -> labelFormat.format(itemDate)
                    GridDensity.MONTH -> labelFormat.format(itemDate)
                    else -> when {
                        isSameDay(itemCal, today) -> "Today"
                        isSameDay(itemCal, yesterday) -> "Yesterday"
                        else -> labelFormat.format(itemDate)
                    }
                }
                timeline.add(
                    TimelineItem.Header(
                        TimelineHeader(
                            label = label,
                            dateKey = dateKey,
                            itemCount = itemsByDate[dateKey].orEmpty().size,
                            itemIds = itemsByDate[dateKey].orEmpty().mapTo(mutableSetOf()) { it.id },
                        )
                    )
                )
            }
            timeline.add(TimelineItem.Media(item))
        }
        return timeline
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun sortMedia(items: List<MediaItem>, order: SortOrder): List<MediaItem> {
        return when (order) {
            SortOrder.DATE_DESC -> items.sortedByDescending { it.sortDate }
            SortOrder.DATE_ASC -> items.sortedBy { it.sortDate }
            SortOrder.NAME_ASC -> items.sortedBy { it.displayName.lowercase() }
            SortOrder.NAME_DESC -> items.sortedByDescending { it.displayName.lowercase() }
            SortOrder.SIZE_DESC -> items.sortedByDescending { it.size }
            SortOrder.SIZE_ASC -> items.sortedBy { it.size }
        }
    }
}

/** A named person offered as a search suggestion, shown by their face. */
internal data class PersonSuggestion(
    val name: String,
    val photoCount: Int,
    val faceUri: android.net.Uri?,
    val face: com.pandagallery.app.data.smart.PersonFace,
)

/** The sort a grid is using, and whether changing it affects this folder alone. */
private data class MediaSortState(val order: SortOrder, val isFolderScoped: Boolean)


internal data class PhotosUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val timelineItems: List<TimelineItem> = emptyList(),
    val storyHighlights: List<StoryHighlight> = emptyList(),
    val selectedItems: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoadedFromCache: Boolean = false,
    val error: String? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    /** True while the sort shown belongs to this folder alone rather than the whole library. */
    val isFolderSortScoped: Boolean = false,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val mediaCount: Int = 0,
    val trashRemainingDays: Map<Long, Int> = emptyMap(),
    val trashRetentionDays: Int = 30,
    val contentFilter: MediaContentFilter = MediaContentFilter.ALL,
    val searchCriteria: MediaSearchCriteria = MediaSearchCriteria(),
    val searchAlbums: List<Album> = emptyList(),
    /** Browsable labels from the on-device index; empty when smart search is off. */
    val labelSuggestions: List<LabelSuggestion> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    /** Named people, for the "search for someone" row on the empty search screen. */
    val peopleSuggestions: List<PersonSuggestion> = emptyList(),
    /**
     * The person group this grid belongs to, when it is one. Drives the actions that only make
     * sense there: taking a wrongly grouped photo out, and choosing the face on their tile.
     */
    val personGroupKey: String? = null,
    /** Suggests batch compression when uncompressed photos exceed candidate threshold. */
    val storageOpportunity: StorageOpportunity? = null,
    val removeLocationWhenSharing: Boolean = false,
    val convertHeifWhenSharing: Boolean = true,
)

/** Contextual recommendation for reclaiming space via media compression. */
internal data class StorageOpportunity(
    val candidateCount: Int,
    val totalOriginalBytes: Long,
    val potentialSavedBytes: Long,
    val candidateIds: List<Long>,
)


/** One batch of photos removed from a person group, so it can be put back. */
internal data class PersonRemoval(
    val groupKey: String,
    val mediaIds: List<Long>,
)

/** Non-null only for a people group: the other smart groups have nothing to be removed from. */
internal fun MediaFilter.personGroupKey(): String? =
    (this as? MediaFilter.SmartGroup)?.takeIf { it.type == SmartGroupType.PEOPLE }?.key

sealed interface MediaFilter {
    data object All : MediaFilter
    data object Videos : MediaFilter
    data class Bucket(val bucketId: Long) : MediaFilter
    data object Favorites : MediaFilter
    data object Trash : MediaFilter
    data object Search : MediaFilter
    data class Collection(val collection: SmartCollection) : MediaFilter
    data class Cleanup(val category: com.pandagallery.app.data.cleanup.CleanupCategory) : MediaFilter
    data class SmartGroup(val type: SmartGroupType, val key: String) : MediaFilter
}

data class PendingMediaMutation(
    val pendingIntent: PendingIntent,
    val mutation: MediaMutation,
    /** True for the second and later chunks of one large batch. */
    val continuesBatch: Boolean = false,
)

/**
 * Items per system confirmation. The whole URI list travels in one binder transaction to
 * MediaProvider; a "select all" over a big library overflows the ~1 MB binder buffer, which
 * surfaced as a NullPointerException inside MediaStore and crashed the app.
 */
private const val MUTATION_CHUNK_SIZE = 1000
private const val TAG = "PhotosViewModel"

private fun MediaMutation.withItems(items: List<MediaItem>): MediaMutation = when (this) {
    is MediaMutation.Favorite -> copy(items = items)
    is MediaMutation.Trash -> copy(items = items)
    is MediaMutation.Restore -> copy(items = items)
    is MediaMutation.MoveToAlbum -> copy(items = items)
    is MediaMutation.PermanentDelete -> copy(items = items)
    is MediaMutation.Rotate -> copy(items = items)
    is MediaMutation.DeleteOriginalsAfterCompression -> copy(items = items)
    is MediaMutation.CompressAndRestore -> copy(items = items)
    // Single-item, or carries state tied to the whole selection.
    is MediaMutation.Rename, is MediaMutation.MoveToPrivate -> this
}

data class CompressionPrompt(
    val itemCount: Int,
    val folderPath: String,
    /** Predicted result for this exact selection, shown before the user commits. */
    val estimate: CompressionEstimator.Estimate = CompressionEstimator.Estimate.Empty,
)

sealed interface MediaMutation {
    val items: List<MediaItem>

    data class Favorite(
        override val items: List<MediaItem>,
        val favorite: Boolean,
    ) : MediaMutation
    data class Trash(override val items: List<MediaItem>) : MediaMutation
    data class Restore(override val items: List<MediaItem>) : MediaMutation
    data class MoveToAlbum(
        override val items: List<MediaItem>,
        val relativePath: String,
    ) : MediaMutation
    data class Rename(
        val item: MediaItem,
        val requestedName: String,
    ) : MediaMutation {
        override val items: List<MediaItem> = listOf(item)
    }
    data class PermanentDelete(override val items: List<MediaItem>) : MediaMutation
    data class Rotate(
        override val items: List<MediaItem>,
        val degrees: Float = 90f,
    ) : MediaMutation
    data class MoveToPrivate(
        override val items: List<MediaItem>,
        val vaultIds: List<String>,
    ) : MediaMutation
    data class DeleteOriginalsAfterCompression(override val items: List<MediaItem>) : MediaMutation
    data class CompressAndRestore(
        override val items: List<MediaItem>,
        val action: CompressionOriginalAction = CompressionOriginalAction.MOVE,
    ) : MediaMutation
}

data class TrashCompressPrompt(
    val items: List<MediaItem>,
    val totalBytes: Long,
    val potentialSavedBytes: Long,
)

/** How far a copy or move has got. [done] counts items finished, out of [total]. */
internal data class MediaTransferProgress(
    val mode: MediaOperationMode,
    val done: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total

    val label: String
        get() = when (mode) {
            MediaOperationMode.COPY -> "Copying $done of $total"
            MediaOperationMode.MOVE -> "Moving $done of $total"
            MediaOperationMode.ROTATE -> "Rotating $done of $total"
        }
}

internal enum class MediaOperationMode {
    MOVE,
    COPY,
    ROTATE,
}

internal enum class AlbumTransferMode {
    MOVE,
    COPY,
}

internal data class AlbumTransferPrompt(
    val items: List<MediaItem>,
    val albums: List<Album>,
    val mode: AlbumTransferMode,
    val selectedDestinationPath: String? = null,
)

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

private data class SearchInputs(
    val contentFilter: MediaContentFilter,
    val criteria: MediaSearchCriteria,
    /** Ids the on-device index matched for [criteria]'s query; empty when it is off. */
    val smartMatchIds: Set<Long>,
)

/** What a query depends on, before the index is actually consulted. */
private data class SearchTrigger(
    val contentFilter: MediaContentFilter,
    val criteria: MediaSearchCriteria,
    val smartIndexEnabled: Boolean,
    val indexVersion: String,
)

private fun resolveInitialFilter(savedStateHandle: SavedStateHandle): MediaFilter {
    val bucketId = savedStateHandle.get<Long>("bucketId")
    if (bucketId != null) return MediaFilter.Bucket(bucketId)

    val collection = savedStateHandle.get<SmartCollection>("collection")
    if (collection != null) return MediaFilter.Collection(collection)

    val category = savedStateHandle.get<CleanupCategory>("category")
    if (category != null) return MediaFilter.Cleanup(category)

    val type = savedStateHandle.get<SmartGroupType>("type")
    val key = savedStateHandle.get<String>("key")
    if (type != null && key != null) return MediaFilter.SmartGroup(type, key)

    return MediaFilter.All
}
