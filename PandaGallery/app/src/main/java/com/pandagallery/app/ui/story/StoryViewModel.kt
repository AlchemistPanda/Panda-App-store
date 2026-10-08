package com.pandagallery.app.ui.story

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.data.editing.BackgroundMusicTrack
import com.pandagallery.app.data.story.StoryHighlightExporter
import com.pandagallery.app.data.local.dao.MediaDao
import com.pandagallery.app.data.local.entity.StoryEntity
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.data.story.StoryEngine
import com.pandagallery.app.domain.model.CompressionOriginalAction
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoryViewModel @Inject constructor(
    val storyEngine: StoryEngine,
    val mediaDao: MediaDao,
    private val storyHighlightExporter: StoryHighlightExporter,
    private val mediaRepository: MediaRepository,
    private val compressionQueueRepository: CompressionQueueRepository,
) : ViewModel() {

    val stories: StateFlow<List<StoryEntity>> = storyEngine.observeAllStories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createStory(title: String, mediaIds: List<Long>, template: String = "Classic") {
        viewModelScope.launch {
            storyEngine.createHighlightReel(title, mediaIds, template)
        }
    }

    fun deleteStory(storyId: Long) {
        viewModelScope.launch {
            storyEngine.deleteStory(storyId)
        }
    }

    fun compressStory(items: List<MediaItem>) {
        viewModelScope.launch {
            compressionQueueRepository.enqueue(items, CompressionOriginalAction.MOVE)
        }
    }

    fun exportStoryReel(
        title: String,
        items: List<MediaItem>,
        soundtrack: BackgroundMusicTrack = BackgroundMusicTrack.ACOUSTIC_BREEZE,
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
}
