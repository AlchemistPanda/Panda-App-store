package com.pandagallery.app.ui.compression

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.compression.CompressionEngine
import com.pandagallery.app.data.compression.CompressionEngine.CompressionPreviewResult
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.data.compression.ImageFormatSupport
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.domain.model.CompressionOriginalAction
import com.pandagallery.app.domain.model.ADAPTIVE_IMAGE_QUALITY
import com.pandagallery.app.domain.model.CompressionPreset
import com.pandagallery.app.domain.model.ImageFormat
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class CompressionStudioUiState(
    val isLoading: Boolean = true,
    val isGeneratingPreview: Boolean = false,
    val isSaving: Boolean = false,
    val mediaItem: MediaItem? = null,
    val previewResult: CompressionPreviewResult? = null,
    val selectedFormat: ImageFormat = ImageFormat.JPEG,
    val quality: Int = 75,
    val selectedPreset: CompressionPreset? = null,
    val splitFraction: Float = 0.5f,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val isTargetSizeMode: Boolean = false,
    val targetSizeBytes: Long? = null,
    val isRemasterEnhanceEnabled: Boolean = false,
    val remasterDetailLevel: Float = 0.5f,
    /** False hides AVIF: the device has no AVIF encoder, so choosing it could only fail. */
    val isAvifSupported: Boolean = true,
)

@HiltViewModel
class CompressionStudioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val compressionEngine: CompressionEngine,
    private val compressionQueueRepository: CompressionQueueRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val imageFormatSupport: ImageFormatSupport,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val mediaId: Long = checkNotNull(savedStateHandle["mediaId"]) {
        "mediaId is required for CompressionStudio"
    }

    private val _uiState = MutableStateFlow(CompressionStudioUiState())
    val uiState: StateFlow<CompressionStudioUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var previewJob: Job? = null

    /**
     * Cancelling [previewJob] does not stop an encode already inside the engine (its blocking
     * decode/encode/SSIM work has no cancellation points), so without this every quick chip tap
     * stacked another full pipeline, each holding several large bitmaps. Holding the lock until
     * the cancelled run actually returns keeps at most one pipeline alive at a time.
     */
    private val previewMutex = Mutex()

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val item = mediaRepository.getMediaById(mediaId)
            if (item == null) {
                val message = "Media item not found"
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
                _toastEvent.emit(message)
                return@launch
            }

            val prefs = preferencesDataSource.userPreferencesFlow.first()
            val initialFormat = prefs.imageFormat
            val initialQuality = prefs.imageQuality

            // No fallback preset: highlighting HIGH while the preview encodes at, say, JPEG 60
            // claims a setting that is not the one in use. Hand-tuned prefs show no chip selected.
            val matchingPreset = CompressionPreset.entries.firstOrNull {
                it.imageFormat == initialFormat && it.imageQuality == initialQuality
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    mediaItem = item,
                    selectedFormat = initialFormat,
                    quality = initialQuality,
                    selectedPreset = matchingPreset,
                    isAvifSupported = imageFormatSupport.isAvifEncodingSupported,
                )
            }

            triggerPreviewGeneration(item, initialFormat, initialQuality, debounceMillis = 0L)
        }
    }

    /** The error screen's Retry: reloads when the item never resolved, otherwise re-runs the preview. */
    fun retryPreview() {
        val current = _uiState.value
        val item = current.mediaItem
        if (item == null) {
            _uiState.update { it.copy(errorMessage = null) }
            loadMedia()
        } else {
            triggerPreviewGeneration(item, current.selectedFormat, current.quality, debounceMillis = 0L)
        }
    }

    fun setFormat(format: ImageFormat) {
        val current = _uiState.value
        if (format == ImageFormat.AVIF && !current.isAvifSupported) {
            // The preview would silently fall back to WebP and the save would then fail when
            // preferences refused the format, leaving the user staring at a screen that did
            // nothing at all.
            viewModelScope.launch { _toastEvent.emit("This device has no AVIF encoder") }
            return
        }
        if (current.selectedFormat == format) return
        _uiState.update {
            it.copy(
                selectedFormat = format,
                selectedPreset = null,
            )
        }
        val item = current.mediaItem ?: return
        triggerPreviewGeneration(item, format, current.quality)
    }

    fun setQuality(quality: Int) {
        val clamped = quality.coerceIn(1, 100)
        _uiState.update {
            it.copy(
                quality = clamped,
                selectedPreset = null,
            )
        }
        val current = _uiState.value
        val item = current.mediaItem ?: return
        triggerPreviewGeneration(item, current.selectedFormat, clamped)
    }

    fun setPreset(preset: CompressionPreset) {
        _uiState.update {
            it.copy(
                selectedPreset = preset,
                selectedFormat = preset.imageFormat,
                quality = preset.imageQuality,
                isTargetSizeMode = false,
                targetSizeBytes = null,
            )
        }
        val item = _uiState.value.mediaItem ?: return
        triggerPreviewGeneration(item, preset.imageFormat, preset.imageQuality)
    }

    fun setTargetSize(targetBytes: Long) {
        _uiState.update {
            it.copy(
                isTargetSizeMode = true,
                targetSizeBytes = targetBytes,
                selectedPreset = null,
            )
        }
        val item = _uiState.value.mediaItem ?: return
        triggerPreviewGeneration(item, _uiState.value.selectedFormat, _uiState.value.quality)
    }

    fun disableTargetSize() {
        _uiState.update {
            it.copy(
                isTargetSizeMode = false,
                targetSizeBytes = null,
            )
        }
        val item = _uiState.value.mediaItem ?: return
        triggerPreviewGeneration(item, _uiState.value.selectedFormat, _uiState.value.quality)
    }

    fun setSplitFraction(fraction: Float) {
        _uiState.update { it.copy(splitFraction = fraction.coerceIn(0.05f, 0.95f)) }
    }

    fun toggleRemasterEnhance(enabled: Boolean) {
        val current = _uiState.value
        if (current.isRemasterEnhanceEnabled == enabled) return
        _uiState.update { it.copy(isRemasterEnhanceEnabled = enabled) }
        val item = current.mediaItem ?: return
        triggerPreviewGeneration(
            item = item,
            format = current.selectedFormat,
            quality = current.quality,
            remasterLevel = if (enabled) current.remasterDetailLevel else 0f,
        )
    }

    fun setRemasterDetailLevel(level: Float) {
        val clamped = level.coerceIn(0.1f, 1.0f)
        _uiState.update { it.copy(remasterDetailLevel = clamped) }
        val current = _uiState.value
        if (!current.isRemasterEnhanceEnabled) return
        val item = current.mediaItem ?: return
        triggerPreviewGeneration(
            item = item,
            format = current.selectedFormat,
            quality = current.quality,
            remasterLevel = clamped,
        )
    }

    private fun triggerPreviewGeneration(
        item: MediaItem,
        format: ImageFormat,
        quality: Int,
        remasterLevel: Float = if (_uiState.value.isRemasterEnhanceEnabled) _uiState.value.remasterDetailLevel else 0f,
        /**
         * Every trigger is debounced, not just the sliders: tapping through the preset chips or
         * the 1/2/5 MB pills used to start a full pipeline per tap. Only the newest survives.
         */
        debounceMillis: Long = PREVIEW_DEBOUNCE_MILLIS,
    ) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(debounceMillis)
            // Clearing the error here turns the error screen back into the spinner on Retry.
            _uiState.update { it.copy(isGeneratingPreview = true, errorMessage = null) }
            try {
                previewMutex.withLock { runPreview(item, format, quality, remasterLevel) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = "Preview generation failed: ${e.localizedMessage ?: "Unknown error"}"
                _toastEvent.emit(message)
                _uiState.update { it.copy(isGeneratingPreview = false, errorMessage = message) }
            }
        }
    }

    private suspend fun runPreview(item: MediaItem, format: ImageFormat, quality: Int, remasterLevel: Float) {
        val state = _uiState.value
        val preview = if (state.isTargetSizeMode && state.targetSizeBytes != null) {
            compressionEngine.generateTargetSizePreview(
                imageUri = item.uri,
                targetSizeBytes = state.targetSizeBytes,
                format = format,
                remasterDetailLevel = remasterLevel,
            )
        } else {
            compressionEngine.generateCompressionPreview(
                imageUri = item.uri,
                format = format,
                quality = quality,
                remasterDetailLevel = remasterLevel,
            )
        }
        _uiState.update {
            it.copy(
                isGeneratingPreview = false,
                previewResult = preview,
                errorMessage = null,
            )
        }
    }

    fun saveAsCopy() = enqueueCurrentSettings(
        action = CompressionOriginalAction.COPY,
        successMessage = "Saved copy to compressed vault",
        failurePrefix = "Failed to save",
    )

    fun replaceOriginal() = enqueueCurrentSettings(
        action = CompressionOriginalAction.MOVE,
        successMessage = "Original compressed and backed up to Trash",
        failurePrefix = "Failed to replace",
    )

    /**
     * Queues the job the preview has been showing.
     *
     * Everything the studio lets you change has to be handed to the queue here. Target Size and
     * the remaster strength used to exist only in the preview: the sheet promised "fit within
     * 2 MB", and the queue then re-encoded at whatever the quality slider happened to be on.
     *
     * Format and quality go with the task too, rather than being written into the global
     * preferences first: that silently changed the default for every later batch, and a task
     * still waiting in the queue was encoded with whatever the prefs said when it finally ran.
     */
    private fun enqueueCurrentSettings(
        action: CompressionOriginalAction,
        successMessage: String,
        failurePrefix: String,
    ) {
        val current = _uiState.value
        val item = current.mediaItem ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                compressionQueueRepository.enqueue(
                    items = listOf(item),
                    action = action,
                    targetSizeBytes = current.targetSizeBytes.takeIf { current.isTargetSizeMode },
                    remasterDetailLevel = if (current.isRemasterEnhanceEnabled) current.remasterDetailLevel else 0f,
                    imageFormat = current.selectedFormat,
                    imageQuality = current.quality,
                )
                _toastEvent.emit(successMessage)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                val message = "$failurePrefix: ${e.localizedMessage ?: "Unknown error"}"
                // The studio has no inline error surface, so a failure here used to leave the
                // button springing back with nothing else happening.
                _toastEvent.emit(message)
                _uiState.update { it.copy(isSaving = false, errorMessage = message) }
            }
        }
    }
}

private const val PREVIEW_DEBOUNCE_MILLIS = 150L
