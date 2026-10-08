package com.pandagallery.app.ui.collections

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.SmartCollection
import com.pandagallery.app.domain.model.filterCollection
import com.pandagallery.app.domain.model.StoryHighlight
import com.pandagallery.app.domain.model.buildStoryHighlights
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.domain.model.CompressionOriginalAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Supplies the item count and a cover thumbnail for every collection tile,
 * and generates authentic Samsung One UI Stories and Memories.
 */
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val compressionQueueRepository: CompressionQueueRepository,
    private val storyHighlightExporter: com.pandagallery.app.data.story.StoryHighlightExporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    fun compressStory(items: List<MediaItem>) {
        viewModelScope.launch {
            compressionQueueRepository.enqueue(items, CompressionOriginalAction.MOVE)
        }
    }

    fun exportStoryReel(
        title: String,
        items: List<MediaItem>,
        soundtrack: com.pandagallery.app.data.editing.BackgroundMusicTrack = com.pandagallery.app.data.editing.BackgroundMusicTrack.ACOUSTIC_BREEZE,
        onProgress: (Int) -> Unit = {},
        onSuccess: (Uri) -> Unit = {},
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

    init {
        viewModelScope.launch {
            combine(
                mediaRepository.getAllMedia(),
                mediaRepository.getFavorites(),
                mediaRepository.getTrashedMedia(),
            ) { all, favorites, trashed ->
                val recentCutoff = System.currentTimeMillis() / 1000L - 7L * 24L * 60L * 60L
                val summaries = buildMap {
                    put(CollectionKey.FAVORITES, favorites.summary())
                    put(CollectionKey.VIDEOS, all.filter { it.isVideo }.summary())
                    put(CollectionKey.RECENT, all.filter { it.dateAdded >= recentCutoff }.summary())
                    put(CollectionKey.TRASH, trashed.summary())
                    SmartCollection.entries.forEach { collection ->
                        put(CollectionKey.Smart(collection), filterCollection(all, collection).summary())
                    }
                }
                val stories = buildStoryHighlights(all)
                CollectionsUiState(
                    summaries = summaries,
                    stories = stories,
                    allMedia = all,
                    isLoading = false,
                )
            }.flowOn(Dispatchers.Default).collect { state -> _uiState.update { state } }
        }
    }

    private fun List<MediaItem>.summary() = CollectionSummary(
        count = size,
        coverUri = maxByOrNull { it.sortDate }?.uri,
    )
}

data class CollectionsUiState(
    val summaries: Map<CollectionKey, CollectionSummary> = emptyMap(),
    val stories: List<StoryHighlight> = emptyList(),
    val allMedia: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
)

data class CollectionSummary(
    val count: Int,
    val coverUri: Uri?,
)

sealed interface CollectionKey {
    data object FAVORITES : CollectionKey
    data object VIDEOS : CollectionKey
    data object RECENT : CollectionKey
    data object TRASH : CollectionKey
    data class Smart(val collection: SmartCollection) : CollectionKey
}
