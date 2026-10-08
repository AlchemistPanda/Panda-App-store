package com.pandagallery.app.ui.viewer

import com.pandagallery.app.domain.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the exact list a grid was showing into the full-screen viewer, so swiping
 * left and right stays inside Favorites, a search result, a Sweep review or a people
 * group instead of silently widening to the whole album.
 *
 * Also bridges selection state between gallery grids and the full-screen viewer, allowing
 * users to swipe, review, and toggle selections in high-resolution full-screen mode.
 *
 * Stores the in-memory [orderedItems] and [initialItem] so ViewerViewModel and ViewerScreen
 * can mount on frame 0 synchronously with no loading spinner, no black frame, and immediate
 * thumbnail rendering.
 */
@Singleton
class ViewerSession @Inject constructor() {

    @Volatile
    private var orderedIds: List<Long> = emptyList()

    @Volatile
    var orderedItems: List<MediaItem> = emptyList()
        private set

    @Volatile
    var initialItem: MediaItem? = null
        private set

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    fun publish(
        ids: List<Long>,
        selected: Set<Long> = emptySet(),
        inSelectionMode: Boolean = false,
    ) {
        publish(
            ids = ids,
            items = emptyList(),
            initialItem = null,
            selected = selected,
            inSelectionMode = inSelectionMode,
        )
    }

    fun publish(
        ids: List<Long>,
        items: List<MediaItem>,
        initialItem: MediaItem? = null,
        selected: Set<Long> = emptySet(),
        inSelectionMode: Boolean = false,
    ) {
        orderedIds = ids
        orderedItems = items
        this.initialItem = initialItem
        _selectedIds.value = selected
        _isSelectionMode.value = inSelectionMode || selected.isNotEmpty()
    }

    /**
     * Retrieves the synchronous items and target index when available,
     * allowing instant frame-0 pager rendering on the requested mediaId.
     */
    fun getInitialSession(mediaId: Long): InitialSessionData? {
        val items = orderedItems
        if (items.isNotEmpty()) {
            val index = items.indexOfFirst { it.id == mediaId }
            if (index >= 0) {
                return InitialSessionData(
                    items = items,
                    initialItem = items[index],
                    initialPage = index,
                )
            }
        }
        val clicked = initialItem
        if (clicked != null && clicked.id == mediaId) {
            return InitialSessionData(
                items = listOf(clicked),
                initialItem = clicked,
                initialPage = 0,
            )
        }
        return null
    }

    fun toggleSelection(mediaId: Long) {
        val current = _selectedIds.value
        val updated = if (mediaId in current) current - mediaId else current + mediaId
        _selectedIds.value = updated
        _isSelectionMode.value = true
    }

    fun setSelectedIds(ids: Set<Long>) {
        _selectedIds.value = ids
        _isSelectionMode.value = ids.isNotEmpty() || _isSelectionMode.value
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    private val _lastViewedMediaId = MutableStateFlow<Long?>(null)
    val lastViewedMediaId: StateFlow<Long?> = _lastViewedMediaId.asStateFlow()

    fun updateCurrentMediaId(mediaId: Long) {
        _lastViewedMediaId.value = mediaId
    }

    fun consumeLastViewedMediaId(): Long? {
        val id = _lastViewedMediaId.value
        _lastViewedMediaId.value = null
        return id
    }

    /** The published order, but only when it actually contains the item being opened. */
    fun idsContaining(mediaId: Long): List<Long>? =
        orderedIds.takeIf { it.size > 1 && mediaId in it }

    fun clear() {
        orderedIds = emptyList()
        orderedItems = emptyList()
        initialItem = null
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
        _lastViewedMediaId.value = null
    }

    data class InitialSessionData(
        val items: List<MediaItem>,
        val initialItem: MediaItem,
        val initialPage: Int,
    )
}
