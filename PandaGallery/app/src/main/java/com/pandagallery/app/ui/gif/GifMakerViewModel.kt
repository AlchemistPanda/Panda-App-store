package com.pandagallery.app.ui.gif

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pandagallery.app.data.gif.GifAspect
import com.pandagallery.app.data.gif.GifConfiguration
import com.pandagallery.app.data.gif.GifDirection
import com.pandagallery.app.data.gif.GifExporter
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.ui.navigation.GifMakerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GifMakerUiState(
    val items: List<MediaItem> = emptyList(),
    val activeFrameIndex: Int = 0,
    val isPlaying: Boolean = true,
    val speed: Float = 1.0f,
    val direction: GifDirection = GifDirection.FORWARD,
    val aspect: GifAspect = GifAspect.ORIGINAL,
    val optimizeCompression: Boolean = true,
    val isExporting: Boolean = false,
    val exportProgress: Int = 0,
    val exportedUri: Uri? = null,
    val error: String? = null,
)

@HiltViewModel
class GifMakerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val gifExporter: GifExporter,
) : ViewModel() {

    private val initialIds: List<Long> = runCatching {
        savedStateHandle.toRoute<GifMakerRoute>().mediaIds.distinct()
    }.getOrDefault(emptyList())

    private val _uiState = MutableStateFlow(GifMakerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val allMedia = mediaRepository.getAllMediaSnapshot()
            val mediaMap = allMedia.associateBy(MediaItem::id)
            val selected = initialIds.mapNotNull(mediaMap::get)
            _uiState.update { it.copy(items = selected) }
        }
    }

    fun setSpeed(speed: Float) {
        _uiState.update { it.copy(speed = speed) }
    }

    fun setDirection(direction: GifDirection) {
        _uiState.update { it.copy(direction = direction) }
    }

    fun setAspect(aspect: GifAspect) {
        _uiState.update { it.copy(aspect = aspect) }
    }

    fun setOptimizeCompression(enabled: Boolean) {
        _uiState.update { it.copy(optimizeCompression = enabled) }
    }

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun setActiveFrameIndex(index: Int) {
        _uiState.update { it.copy(activeFrameIndex = index.coerceIn(0, (it.items.size - 1).coerceAtLeast(0))) }
    }

    fun deleteFrame(index: Int) {
        _uiState.update { state ->
            if (state.items.size <= 2) return@update state // Minimum 2 frames for animation
            val updated = state.items.toMutableList().apply { removeAt(index) }
            state.copy(
                items = updated,
                activeFrameIndex = state.activeFrameIndex.coerceAtMost(updated.size - 1),
            )
        }
    }

    fun moveFrame(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.items.indices || toIndex !in state.items.indices) return@update state
            val updated = state.items.toMutableList().apply {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
            state.copy(items = updated, activeFrameIndex = toIndex)
        }
    }

    fun exportGif(onSuccess: (Uri) -> Unit) {
        val state = _uiState.value
        if (state.items.size < 2 || state.isExporting) return

        _uiState.update { it.copy(isExporting = true, exportProgress = 0, error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = GifConfiguration(
                    speed = state.speed,
                    direction = state.direction,
                    aspect = state.aspect,
                    optimizeCompression = state.optimizeCompression,
                )
                val uri = gifExporter.exportGif(state.items, config) { progress ->
                    _uiState.update { it.copy(exportProgress = progress) }
                }
                _uiState.update { it.copy(isExporting = false, exportedUri = uri) }
                viewModelScope.launch(Dispatchers.Main) {
                    onSuccess(uri)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message ?: "Failed to export GIF") }
            }
        }
    }
}
