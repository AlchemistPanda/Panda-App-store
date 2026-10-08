package com.pandagallery.app.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.pandagallery.app.data.editing.*
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.ui.viewer.ViewerSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val engine: ImageEditorEngine,
    private val videoEngine: VideoEditorEngine,
    private val viewerSession: ViewerSession,
    private val backupManager: NondestructiveBackupManager,
    private val incomingMediaResolver: com.pandagallery.app.data.media.IncomingMediaResolver,
) : ViewModel() {
    private val initialMediaId = savedStateHandle.get<Long>("mediaId")
    private val initialItem = initialMediaId?.let { id ->
        viewerSession.getInitialSession(id)?.initialItem
            ?: viewerSession.orderedItems.firstOrNull { it.id == id }
    }

    private val _uiState = MutableStateFlow(
        EditorUiState(
            item = initialItem,
            videoEdit = if (initialItem?.isVideo == true) VideoEditState(trimEndMs = initialItem.duration ?: 0L) else VideoEditState(),
            isLoading = initialItem == null,
        )
    )
    val uiState = _uiState.asStateFlow()
    private val history = ArrayDeque<ImageEditState>()
    private val redoHistory = ArrayDeque<ImageEditState>()
    private val videoHistory = ArrayDeque<VideoEditState>()
    private val videoRedoHistory = ArrayDeque<VideoEditState>()

    init {
        initialItem?.let { checkBackup(it.id) }
    }

    private fun checkBackup(mediaId: Long) = viewModelScope.launch {
        val has = backupManager.hasBackup(mediaId)
        _uiState.update { it.copy(hasOriginalBackup = has) }
    }

    fun loadUri(uriString: String) = viewModelScope.launch {
        if (_uiState.value.item?.uri?.toString() == uriString && _uiState.value.item != null) return@launch
        if (_uiState.value.item == null) {
            _uiState.update { it.copy(isLoading = true) }
        }
        val uri = android.net.Uri.parse(uriString)
        val item = incomingMediaResolver.describe(uri)
        _uiState.update {
            it.copy(
                item = item ?: it.item,
                videoEdit = if (item?.isVideo == true) VideoEditState(trimEndMs = item.duration ?: 0L) else it.videoEdit,
                hasOriginalBackup = false,
                isLoading = false,
                error = if (item == null && it.item == null) "Media is unavailable" else null,
            )
        }
    }

    fun load(mediaId: Long) = viewModelScope.launch {
        if (mediaId <= 0) return@launch
        if (_uiState.value.item?.id == mediaId && _uiState.value.item != null) return@launch
        if (_uiState.value.item == null) {
            _uiState.update { it.copy(isLoading = true) }
        }
        val item = mediaRepository.getMediaById(mediaId)
        val hasBackup = item?.let { backupManager.hasBackup(it.id) } ?: false
        _uiState.update {
            it.copy(
                item = item ?: it.item,
                videoEdit = if (item?.isVideo == true) VideoEditState(trimEndMs = item.duration ?: 0L) else it.videoEdit,
                hasOriginalBackup = hasBackup,
                isLoading = false,
                error = if (item == null && it.item == null) "Media is unavailable" else null,
            )
        }
    }

    fun update(transform: (ImageEditState) -> ImageEditState) {
        val current = _uiState.value.edit
        val next = transform(current)
        if (next == current) return
        history.addLast(current)
        if (history.size > 50) history.removeFirst()
        redoHistory.clear()
        _uiState.update { it.copy(edit = next, canUndo = true, canRedo = false) }
    }

    fun undo() {
        if (_uiState.value.item?.isVideo == true) {
            val previous = videoHistory.removeLastOrNull() ?: return
            videoRedoHistory.addLast(_uiState.value.videoEdit)
            _uiState.update { it.copy(videoEdit = previous, canUndo = videoHistory.isNotEmpty(), canRedo = true) }
        } else {
            val previous = history.removeLastOrNull() ?: return
            redoHistory.addLast(_uiState.value.edit)
            _uiState.update { it.copy(edit = previous, canUndo = history.isNotEmpty(), canRedo = true) }
        }
    }

    fun redo() {
        if (_uiState.value.item?.isVideo == true) {
            val next = videoRedoHistory.removeLastOrNull() ?: return
            videoHistory.addLast(_uiState.value.videoEdit)
            _uiState.update { it.copy(videoEdit = next, canUndo = true, canRedo = videoRedoHistory.isNotEmpty()) }
        } else {
            val next = redoHistory.removeLastOrNull() ?: return
            history.addLast(_uiState.value.edit)
            _uiState.update { it.copy(edit = next, canUndo = true, canRedo = redoHistory.isNotEmpty()) }
        }
    }

    fun reset() {
        if (_uiState.value.item?.isVideo == true) {
            updateVideo { VideoEditState(trimEndMs = _uiState.value.item?.duration ?: 0L) }
        } else {
            update { ImageEditState() }
        }
    }

    fun revertToOriginal(onReverted: () -> Unit) = viewModelScope.launch {
        val item = _uiState.value.item ?: return@launch
        _uiState.update { it.copy(isLoading = true) }
        val success = backupManager.restoreOriginal(item)
        if (success) {
            history.clear()
            redoHistory.clear()
            _uiState.update {
                it.copy(
                    edit = ImageEditState(),
                    hasOriginalBackup = false,
                    canUndo = false,
                    canRedo = false,
                    isLoading = false,
                )
            }
            mediaRepository.syncMediaStore()
            onReverted()
        } else {
            _uiState.update { it.copy(isLoading = false, error = "No original version available") }
        }
    }

    fun updateVideo(transform: (VideoEditState) -> VideoEditState) {
        val current = _uiState.value.videoEdit
        val next = transform(current)
        if (next == current) return
        videoHistory.addLast(current)
        videoRedoHistory.clear()
        _uiState.update { it.copy(videoEdit = next, canUndo = true, canRedo = false) }
    }

    fun toggleAutoEnhance() {
        update { current ->
            if (current.autoEnhanced) {
                current.copy(
                    lightBalance = 0f,
                    exposure = 0f,
                    contrast = 0f,
                    highlights = 0f,
                    shadows = 0f,
                    saturation = 0f,
                    warmth = 0f,
                    sharpness = 0f,
                    autoEnhanced = false,
                )
            } else {
                current.copy(
                    lightBalance = 0.12f,
                    exposure = 0.08f,
                    contrast = 0.12f,
                    highlights = -0.08f,
                    shadows = 0.14f,
                    saturation = 0.10f,
                    warmth = 0.04f,
                    sharpness = 0.15f,
                    autoEnhanced = true,
                )
            }
        }
    }

    fun save(saveAsCopy: Boolean = true, onSaved: () -> Unit) = viewModelScope.launch {
        val item = _uiState.value.item ?: return@launch
        _uiState.update { it.copy(isSaving = true, error = null) }
        try {
            if (item.isVideo) {
                videoEngine.saveCopy(item, _uiState.value.videoEdit) { progress ->
                    _uiState.update { it.copy(saveProgress = progress) }
                }
            } else {
                engine.saveCopy(item, _uiState.value.edit, saveAsCopy = saveAsCopy)
            }
            mediaRepository.syncMediaStore()
            if (!saveAsCopy) {
                _uiState.update { it.copy(hasOriginalBackup = true) }
            }
            onSaved()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            _uiState.update { it.copy(error = error.message ?: "Unable to save edit") }
        } catch (error: SecurityException) {
            _uiState.update { it.copy(error = "Media access was lost") }
        } finally {
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    fun saveGenerativeCopy(
        bitmap: Bitmap,
        saveToVault: Boolean,
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        val item = _uiState.value.item ?: return@launch
        _uiState.update { it.copy(isSaving = true) }
        try {
            engine.saveGenerativeCopy(
                item = item,
                generativeBitmap = bitmap,
                saveAsCopy = true,
                useNearLosslessCompression = saveToVault,
            )
            mediaRepository.syncMediaStore()
            onSaved()
        } catch (error: Exception) {
            _uiState.update { it.copy(error = error.message ?: "Unable to save generative edit") }
        } finally {
            _uiState.update { it.copy(isSaving = false) }
        }
    }
}

data class EditorUiState(
    val item: MediaItem? = null,
    val edit: ImageEditState = ImageEditState(),
    val videoEdit: VideoEditState = VideoEditState(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasOriginalBackup: Boolean = false,
    val error: String? = null,
    val saveProgress: Int = 0,
)
