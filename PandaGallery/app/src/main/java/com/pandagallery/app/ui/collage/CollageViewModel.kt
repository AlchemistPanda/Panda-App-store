package com.pandagallery.app.ui.collage

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pandagallery.app.data.collage.COLLAGE_MAX_ITEMS
import com.pandagallery.app.data.collage.COLLAGE_MAX_VIDEOS
import com.pandagallery.app.data.collage.COLLAGE_MIN_ITEMS
import com.pandagallery.app.data.collage.CollageAspect
import com.pandagallery.app.data.collage.CollageBackgroundMode
import com.pandagallery.app.data.collage.CollageConfiguration
import com.pandagallery.app.data.collage.CollageExporter
import com.pandagallery.app.data.collage.CollageFilter
import com.pandagallery.app.data.collage.CollageItemTransform
import com.pandagallery.app.data.collage.CollageLayoutMode
import com.pandagallery.app.data.collage.CollageLayouts
import com.pandagallery.app.data.collage.CollageStickerOverlay
import com.pandagallery.app.data.collage.CollageStroke
import com.pandagallery.app.data.collage.CollageTextOverlay
import com.pandagallery.app.data.collage.FreestyleLayouts
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.ui.navigation.CollageRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.pandagallery.app.data.collage.AUDIO_CATEGORIES
import com.pandagallery.app.data.collage.AudioCategory
import com.pandagallery.app.data.collage.CollageAudioTrack
import com.pandagallery.app.data.collage.LocalAudioRepository
import com.pandagallery.app.data.collage.OnlineAudioRepository

@HiltViewModel
internal class CollageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val localAudioRepository: LocalAudioRepository,
    private val onlineAudioRepository: OnlineAudioRepository,
    private val exporter: CollageExporter,
) : ViewModel() {
    private val initialIds = savedStateHandle.toRoute<CollageRoute>().mediaIds.distinct().take(COLLAGE_MAX_ITEMS)
    private val _uiState = MutableStateFlow(CollageUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val available = mediaRepository.getAllMediaSnapshot().filterNot(MediaItem::isTrashed)
            val byId = available.associateBy(MediaItem::id)
            val selected = initialIds.mapNotNull(byId::get)
            _uiState.update {
                it.copy(
                    availableItems = available,
                    selectedItems = selected,
                    stage = if (selected.size >= COLLAGE_MIN_ITEMS) CollageStage.EDIT else CollageStage.PICK,
                    isLoading = false,
                    error = if (initialIds.isNotEmpty() && selected.size < COLLAGE_MIN_ITEMS) {
                        "Some selected items are no longer available"
                    } else null,
                )
            }
        }
    }

    fun toggleItem(item: MediaItem) {
        val state = _uiState.value
        val selected = state.selectedItems
        val replacing = state.replacingIndex
        if (replacing != null) {
            if (selected.any { it.id == item.id }) {
                _uiState.update { it.copy(error = "That item is already in the collage") }
            } else if (
                item.isVideo &&
                selected.count(MediaItem::isVideo) - (if (selected[replacing].isVideo) 1 else 0) >= COLLAGE_MAX_VIDEOS
            ) {
                _uiState.update { it.copy(error = "You can include up to $COLLAGE_MAX_VIDEOS videos") }
            } else {
                _uiState.update {
                    it.copy(
                        selectedItems = selected.toMutableList().apply { set(replacing, item) },
                        replacingIndex = null,
                        stage = CollageStage.EDIT,
                    )
                }
            }
            return
        }
        if (selected.any { it.id == item.id }) {
            _uiState.update { it.copy(selectedItems = selected.filterNot { selectedItem -> selectedItem.id == item.id }) }
            return
        }
        if (selected.size >= COLLAGE_MAX_ITEMS) {
            _uiState.update { it.copy(error = "You can include up to $COLLAGE_MAX_ITEMS items") }
            return
        }
        if (item.isVideo && selected.count(MediaItem::isVideo) >= COLLAGE_MAX_VIDEOS) {
            _uiState.update { it.copy(error = "You can include up to $COLLAGE_MAX_VIDEOS videos") }
            return
        }
        _uiState.update { it.copy(selectedItems = selected + item) }
    }

    fun finishPicking() {
        _uiState.update { it.copy(stage = CollageStage.EDIT, replacingIndex = null) }
    }

    fun editSelection() {
        if (_uiState.value.selectedItems.size < COLLAGE_MIN_ITEMS) {
            _uiState.update { it.copy(error = "Select at least $COLLAGE_MIN_ITEMS items") }
        } else {
            _uiState.update { it.copy(stage = CollageStage.EDIT, replacingIndex = null) }
        }
    }

    fun selectCell(index: Int) {
        _uiState.update { it.copy(activeIndex = index) }
    }

    fun swapItems(first: Int, second: Int) {
        val selected = _uiState.value.selectedItems.toMutableList()
        if (first in selected.indices && second in selected.indices && first != second) {
            val temp = selected[first]
            selected[first] = selected[second]
            selected[second] = temp
            _uiState.update {
                it.copy(
                    selectedItems = selected,
                    activeIndex = second,
                )
            }
        }
    }

    fun moveActive(direction: Int) {
        val state = _uiState.value
        val currentIndex = state.activeIndex
        val targetIndex = currentIndex + direction
        if (targetIndex in state.selectedItems.indices) {
            swapItems(currentIndex, targetIndex)
        }
    }

    fun removeActive() {
        _uiState.update { state ->
            if (state.selectedItems.size <= COLLAGE_MIN_ITEMS) {
                state.copy(error = "A collage needs at least $COLLAGE_MIN_ITEMS items")
            } else {
                val nextSelected = state.selectedItems.toMutableList().apply { removeAt(state.activeIndex) }
                state.copy(selectedItems = nextSelected, activeIndex = 0.coerceAtMost(nextSelected.lastIndex))
            }
        }
    }

    fun replaceActive() {
        _uiState.update { it.copy(stage = CollageStage.PICK, replacingIndex = it.activeIndex) }
    }

    fun addItem() {
        if (_uiState.value.selectedItems.size < COLLAGE_MAX_ITEMS) {
            _uiState.update { it.copy(stage = CollageStage.PICK, replacingIndex = null) }
        }
    }

    fun setTool(tool: CollageTool) {
        // Leaving the Draw tab must not leave the canvas swallowing pan gestures.
        _uiState.update { it.copy(activeTool = tool, isDrawingMode = tool == CollageTool.DRAW && it.isDrawingMode) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Edit history
    //
    // Every change to [CollageConfiguration] goes through [editConfig], which is what makes undo
    // possible at all: the configuration is a single immutable value, so a stack of previous ones
    // is a complete history with no per-feature bookkeeping.
    //
    // Consecutive edits carrying the same label inside [HISTORY_COALESCE_MS] collapse into one
    // entry. Without that a single slider drag or pinch — which fire on every frame — would bury
    // the useful history under forty identical-looking steps.
    // ─────────────────────────────────────────────────────────────────────────────

    private fun editConfig(label: String, transform: (CollageUiState) -> CollageConfiguration) {
        _uiState.update { state ->
            val next = transform(state)
            if (next == state.configuration) return@update state
            val now = System.currentTimeMillis()
            val coalesce = label == state.lastEditLabel && now - state.lastEditAtMs < HISTORY_COALESCE_MS
            state.copy(
                configuration = next,
                undoStack = if (coalesce) state.undoStack else (state.undoStack + state.configuration).takeLast(HISTORY_LIMIT),
                redoStack = emptyList(),
                lastEditLabel = label,
                lastEditAtMs = now,
            )
        }
    }

    fun undo() {
        _uiState.update { state ->
            val previous = state.undoStack.lastOrNull() ?: return@update state
            state.copy(
                configuration = previous,
                undoStack = state.undoStack.dropLast(1),
                redoStack = state.redoStack + state.configuration,
                // Cleared so the next edit always opens a fresh history entry rather than
                // coalescing into the step that was just undone.
                lastEditLabel = null,
            )
        }
    }

    fun redo() {
        _uiState.update { state ->
            val next = state.redoStack.lastOrNull() ?: return@update state
            state.copy(
                configuration = next,
                redoStack = state.redoStack.dropLast(1),
                undoStack = (state.undoStack + state.configuration).takeLast(HISTORY_LIMIT),
                lastEditLabel = null,
            )
        }
    }

    fun setLayoutMode(mode: CollageLayoutMode) {
        editConfig("layoutMode") { state ->
            state.configuration.copy(layoutMode = mode, layoutIndex = 0, freestyleIndex = 0)
        }
    }

    fun setLayout(index: Int) {
        editConfig("layout") { state ->
            if (state.configuration.layoutMode == CollageLayoutMode.FREESTYLE) {
                val count = FreestyleLayouts.variantCount(state.selectedItems.size)
                state.configuration.copy(freestyleIndex = index.mod(count))
            } else {
                val count = CollageLayouts.variantCount(state.selectedItems.size)
                state.configuration.copy(layoutIndex = index.mod(count), splitRatio = 0.5f, subSplitRatio = 0.5f)
            }
        }
    }

    fun shuffleFreestyle() {
        editConfig("shuffle") { state ->
            val count = FreestyleLayouts.variantCount(state.selectedItems.size)
            val nextIndex = (state.configuration.freestyleIndex + 1).mod(count)
            val angles = listOf(-12, -8, -4, 0, 4, 8, 12)
            val randomTransforms = state.selectedItems.associate { item ->
                val current = state.configuration.itemTransforms[item.id] ?: CollageItemTransform()
                item.id to current.copy(rotationDegrees = angles.random())
            }
            state.configuration.copy(
                layoutMode = CollageLayoutMode.FREESTYLE,
                freestyleIndex = nextIndex,
                itemTransforms = randomTransforms,
            )
        }
    }

    fun setSplitRatio(ratio: Float) {
        editConfig("splitRatio") { it.configuration.copy(splitRatio = ratio.coerceIn(0.2f, 0.8f)) }
    }

    fun setSubSplitRatio(ratio: Float) {
        editConfig("subSplitRatio") { it.configuration.copy(subSplitRatio = ratio.coerceIn(0.2f, 0.8f)) }
    }

    fun setAspect(aspect: CollageAspect) {
        editConfig("aspect") { it.configuration.copy(aspect = aspect) }
    }

    fun setSpacing(spacing: Float) {
        editConfig("spacing") { it.configuration.copy(spacingPercent = spacing) }
    }

    fun setCorner(corner: Float) {
        editConfig("corner") { it.configuration.copy(cornerPercent = corner) }
    }

    fun setOuterMargin(margin: Float) {
        editConfig("margin") { it.configuration.copy(outerMarginPercent = margin) }
    }

    fun setBackgroundMode(mode: CollageBackgroundMode) {
        editConfig("backgroundMode") { it.configuration.copy(backgroundMode = mode) }
    }

    fun setBackground(color: Int) {
        editConfig("background") { it.configuration.copy(backgroundColor = color, backgroundMode = CollageBackgroundMode.COLOR) }
    }

    fun setGradient(index: Int) {
        editConfig("background") { it.configuration.copy(gradientIndex = index, backgroundMode = CollageBackgroundMode.GRADIENT) }
    }

    fun setMuteVideos(mute: Boolean) {
        editConfig("mute") { it.configuration.copy(muteVideos = mute) }
    }

    fun setPattern(index: Int) {
        editConfig("background") {
            it.configuration.copy(patternIndex = index, backgroundMode = CollageBackgroundMode.PATTERN)
        }
    }

    fun openColorPicker() {
        _uiState.update { it.copy(isColorPickerOpen = true) }
    }

    fun closeColorPicker() {
        _uiState.update { it.copy(isColorPickerOpen = false) }
    }

    /**
     * Pull a handful of dominant colours out of the selected photos so the background palette can
     * offer shades that actually belong with the collage.
     *
     * Decoded at a deliberately brutal 32px so the whole pass costs a few milliseconds; colours are
     * bucketed coarsely, near-greys dropped (they make for a dull background and there are already
     * neutral presets), and the most common buckets win.
     */
    fun extractPalette() {
        val items = _uiState.value.selectedItems.filterNot(MediaItem::isVideo).take(4)
        if (items.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val counts = HashMap<Int, Int>()
            for (item in items) {
                val bitmap = runCatching { exporter.decodeThumbnail(item, 32) }.getOrNull() ?: continue
                for (x in 0 until bitmap.width) {
                    for (y in 0 until bitmap.height) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val max = maxOf(r, g, b)
                        val min = minOf(r, g, b)
                        if (max - min < 28) continue
                        val key = (r / 48 shl 16) or (g / 48 shl 8) or (b / 48)
                        counts[key] = (counts[key] ?: 0) + 1
                    }
                }
                bitmap.recycle()
            }
            val colors = counts.entries
                .sortedByDescending(Map.Entry<Int, Int>::value)
                .take(6)
                .map { (key, _) ->
                    val r = ((key shr 16) and 0xFF) * 48 + 24
                    val g = ((key shr 8) and 0xFF) * 48 + 24
                    val b = (key and 0xFF) * 48 + 24
                    0xFF000000.toInt() or (r.coerceAtMost(255) shl 16) or (g.coerceAtMost(255) shl 8) or b.coerceAtMost(255)
                }
            _uiState.update { it.copy(extractedColors = colors) }
        }
    }

    // ── Freehand drawing ────────────────────────────────────────────────────────

    fun setDrawingMode(enabled: Boolean) {
        _uiState.update { it.copy(isDrawingMode = enabled, currentStroke = null) }
    }

    fun setDrawColor(color: Int) {
        _uiState.update { it.copy(drawColor = color) }
    }

    fun setDrawWidth(width: Float) {
        _uiState.update { it.copy(drawWidth = width.coerceIn(0.002f, 0.04f)) }
    }

    fun beginStroke(x: Float, y: Float) {
        _uiState.update { state ->
            state.copy(
                currentStroke = CollageStroke(
                    color = state.drawColor,
                    widthFraction = state.drawWidth,
                    points = listOf(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)),
                ),
            )
        }
    }

    fun extendStroke(x: Float, y: Float) {
        _uiState.update { state ->
            val stroke = state.currentStroke ?: return@update state
            state.copy(currentStroke = stroke.copy(points = stroke.points + x.coerceIn(0f, 1f) + y.coerceIn(0f, 1f)))
        }
    }

    fun endStroke() {
        val stroke = _uiState.value.currentStroke ?: return
        _uiState.update { it.copy(currentStroke = null) }
        // A dot is a legitimate mark, but a single point has no segment to stroke, so it is
        // doubled into a zero-length line the renderers can draw with a round cap.
        val points = if (stroke.points.size >= 4) stroke.points else stroke.points + stroke.points
        editConfig("stroke-${stroke.id}") { it.configuration.copy(strokes = it.configuration.strokes + stroke.copy(points = points)) }
    }

    fun undoStroke() {
        editConfig("undoStroke") { it.configuration.copy(strokes = it.configuration.strokes.dropLast(1)) }
    }

    fun clearStrokes() {
        editConfig("clearStrokes") { it.configuration.copy(strokes = emptyList()) }
    }

    // In-Cell Transform Controls
    fun rotateActiveItem() {
        val state = _uiState.value
        val activeItem = state.selectedItems.getOrNull(state.activeIndex) ?: return
        val currentTransform = state.configuration.itemTransforms[activeItem.id] ?: CollageItemTransform()
        val nextRotation = (currentTransform.rotationDegrees + 90) % 360
        updateActiveTransform(currentTransform.copy(rotationDegrees = nextRotation))
    }

    fun flipActiveItemHorizontal() {
        val state = _uiState.value
        val activeItem = state.selectedItems.getOrNull(state.activeIndex) ?: return
        val currentTransform = state.configuration.itemTransforms[activeItem.id] ?: CollageItemTransform()
        updateActiveTransform(currentTransform.copy(flipHorizontal = !currentTransform.flipHorizontal))
    }

    fun flipActiveItemVertical() {
        val state = _uiState.value
        val activeItem = state.selectedItems.getOrNull(state.activeIndex) ?: return
        val currentTransform = state.configuration.itemTransforms[activeItem.id] ?: CollageItemTransform()
        updateActiveTransform(currentTransform.copy(flipVertical = !currentTransform.flipVertical))
    }

    fun resetActiveTransform() {
        val state = _uiState.value
        val activeItem = state.selectedItems.getOrNull(state.activeIndex) ?: return
        val currentTransform = state.configuration.itemTransforms[activeItem.id] ?: CollageItemTransform()
        updateActiveTransform(currentTransform.copy(scale = 1f, offsetX = 0f, offsetY = 0f, rotationDegrees = 0, flipHorizontal = false, flipVertical = false))
    }

    fun setActiveItemFilter(filter: CollageFilter) {
        val state = _uiState.value
        val activeItem = state.selectedItems.getOrNull(state.activeIndex) ?: return
        val currentTransform = state.configuration.itemTransforms[activeItem.id] ?: CollageItemTransform()
        updateActiveTransform(currentTransform.copy(filter = filter))
    }

    fun setGlobalFilter(filter: CollageFilter) {
        editConfig("filter") { it.configuration.copy(globalFilter = filter) }
    }

    fun updateTransform(itemId: Long, panDeltaX: Float, panDeltaY: Float, zoomFactor: Float) {
        val state = _uiState.value
        val currentTransform = state.configuration.itemTransforms[itemId] ?: CollageItemTransform()
        val newScale = (currentTransform.scale * zoomFactor).coerceIn(1.0f, 3.5f)
        val maxPan = (newScale - 1f) * 0.5f
        val newOffsetX = if (newScale <= 1.0f) 0f else (currentTransform.offsetX + panDeltaX).coerceIn(-maxPan, maxPan)
        val newOffsetY = if (newScale <= 1.0f) 0f else (currentTransform.offsetY + panDeltaY).coerceIn(-maxPan, maxPan)
        val updated = currentTransform.copy(
            scale = newScale,
            offsetX = newOffsetX,
            offsetY = newOffsetY,
        )
        editConfig("transform") { it.configuration.copy(itemTransforms = it.configuration.itemTransforms + (itemId to updated)) }
    }

    // Video Trimming
    fun setVideoTrim(itemId: Long, startMs: Long, endMs: Long?) {
        val state = _uiState.value
        val currentTransform = state.configuration.itemTransforms[itemId] ?: CollageItemTransform()
        val updated = currentTransform.copy(trimStartMs = startMs, trimEndMs = endMs)
        editConfig("trim") { it.configuration.copy(itemTransforms = it.configuration.itemTransforms + (itemId to updated)) }
    }

    // Stickers & Text Overlays
    fun addSticker(emoji: String) {
        val newSticker = CollageStickerOverlay(emoji = emoji, x = 0.5f, y = 0.5f)
        editConfig("addSticker") { it.configuration.copy(stickerOverlays = it.configuration.stickerOverlays + newSticker) }
    }

    fun updateStickerPosition(id: String, deltaX: Float, deltaY: Float) {
        editConfig("stickerMove") { state ->
            state.configuration.copy(
                stickerOverlays = state.configuration.stickerOverlays.map { sticker ->
                    if (sticker.id != id) sticker else sticker.copy(
                        x = (sticker.x + deltaX).coerceIn(0.05f, 0.95f),
                        y = (sticker.y + deltaY).coerceIn(0.05f, 0.95f),
                    )
                },
            )
        }
    }

    /** Pinch and twist on a sticker. [zoomFactor] is relative, [rotationDelta] is in degrees. */
    fun transformSticker(id: String, zoomFactor: Float, rotationDelta: Float) {
        editConfig("stickerScale") { state ->
            state.configuration.copy(
                stickerOverlays = state.configuration.stickerOverlays.map { sticker ->
                    if (sticker.id != id) sticker else sticker.copy(
                        sizeFraction = (sticker.sizeFraction * zoomFactor).coerceIn(0.04f, 0.6f),
                        rotation = sticker.rotation + rotationDelta,
                    )
                },
            )
        }
    }

    fun removeSticker(id: String) {
        editConfig("removeSticker") {
            it.configuration.copy(stickerOverlays = it.configuration.stickerOverlays.filterNot { s -> s.id == id })
        }
    }

    fun addText(text: String, color: Int = 0xFFFFFFFF.toInt(), fontIndex: Int = 0, outlined: Boolean = true) {
        if (text.isBlank()) return
        val newText = CollageTextOverlay(
            text = text.trim(),
            color = color,
            x = 0.5f,
            y = 0.5f,
            fontIndex = fontIndex,
            outlined = outlined,
        )
        editConfig("addText") { it.configuration.copy(textOverlays = it.configuration.textOverlays + newText) }
        // Newly added captions become the styling target, so the Text tab's controls act on the
        // thing the user just created rather than on nothing.
        _uiState.update { it.copy(activeTextId = newText.id) }
    }

    fun updateTextPosition(id: String, deltaX: Float, deltaY: Float) {
        editConfig("textMove") { state ->
            state.configuration.copy(
                textOverlays = state.configuration.textOverlays.map { text ->
                    if (text.id != id) text else text.copy(
                        x = (text.x + deltaX).coerceIn(0.05f, 0.95f),
                        y = (text.y + deltaY).coerceIn(0.05f, 0.95f),
                    )
                },
            )
        }
    }

    /** Pinch and twist on a caption. */
    fun transformText(id: String, zoomFactor: Float, rotationDelta: Float) {
        editConfig("textScale") { state ->
            state.configuration.copy(
                textOverlays = state.configuration.textOverlays.map { text ->
                    if (text.id != id) text else text.copy(
                        fontSizeFraction = (text.fontSizeFraction * zoomFactor).coerceIn(0.02f, 0.30f),
                        rotation = text.rotation + rotationDelta,
                    )
                },
            )
        }
    }

    fun selectText(id: String?) {
        _uiState.update { it.copy(activeTextId = id) }
    }

    private fun editActiveText(label: String, block: (CollageTextOverlay) -> CollageTextOverlay) {
        val id = _uiState.value.activeTextId ?: return
        editConfig(label) { state ->
            state.configuration.copy(
                textOverlays = state.configuration.textOverlays.map { if (it.id == id) block(it) else it },
            )
        }
    }

    fun setActiveTextColor(color: Int) = editActiveText("textColor") { it.copy(color = color) }

    fun setActiveTextFont(index: Int) = editActiveText("textFont") { it.copy(fontIndex = index) }

    fun toggleActiveTextOutline() = editActiveText("textOutline") { it.copy(outlined = !it.outlined) }

    fun toggleActiveTextPlate() = editActiveText("textPlate") {
        // Cycles none → dark plate → light plate, which covers the two cases that actually rescue
        // legibility without asking the user to pick a colour.
        val next = when (it.backgroundColor) {
            null -> 0x99000000.toInt()
            0x99000000.toInt() -> 0xCCFFFFFF.toInt()
            else -> null
        }
        it.copy(backgroundColor = next)
    }

    fun setActiveTextSize(fraction: Float) = editActiveText("textSize") {
        it.copy(fontSizeFraction = fraction.coerceIn(0.02f, 0.30f))
    }

    fun setActiveTextRotation(degrees: Float) = editActiveText("textRotation") {
        it.copy(rotation = degrees.coerceIn(-180f, 180f))
    }

    fun removeText(id: String) {
        editConfig("removeText") {
            it.configuration.copy(textOverlays = it.configuration.textOverlays.filterNot { t -> t.id == id })
        }
        _uiState.update { if (it.activeTextId == id) it.copy(activeTextId = null) else it }
    }

    fun openTextDialog() {
        _uiState.update { it.copy(isTextDialogOpen = true) }
    }

    fun closeTextDialog() {
        _uiState.update { it.copy(isTextDialogOpen = false) }
    }

    private fun updateActiveTransform(transform: CollageItemTransform, label: String = "transform") {
        editConfig(label) { state ->
            val activeItem = state.selectedItems.getOrNull(state.activeIndex) ?: return@editConfig state.configuration
            state.configuration.copy(
                itemTransforms = state.configuration.itemTransforms + (activeItem.id to transform),
            )
        }
    }

    /** Double-tap on a cell: swap between cropping the photo to fill and fitting it whole. */
    fun toggleActiveContentFit() {
        val state = _uiState.value
        val activeItem = state.selectedItems.getOrNull(state.activeIndex) ?: return
        val current = state.configuration.itemTransforms[activeItem.id] ?: CollageItemTransform()
        // Fitting a zoomed-in photo shows nothing useful, so the zoom resets with the mode.
        updateActiveTransform(
            current.copy(contentFit = !current.contentFit, scale = 1f, offsetX = 0f, offsetY = 0f),
            label = "fit",
        )
    }

    fun toggleContentFitAt(index: Int) {
        _uiState.update { it.copy(activeIndex = index) }
        toggleActiveContentFit()
    }

    private inline fun itOrCopy(state: CollageUiState, block: CollageUiState.() -> CollageUiState): CollageUiState =
        state.block()

    // Audio Background Music
    fun setAudioTrack(track: CollageAudioTrack?) {
        editConfig("audio") { it.configuration.copy(audioTrack = track) }
    }

    fun removeAudioTrack() {
        editConfig("audio") { it.configuration.copy(audioTrack = null) }
    }

    fun setAudioTrim(startMs: Long, endMs: Long?) {
        val currentTrack = _uiState.value.configuration.audioTrack ?: return
        val updated = currentTrack.copy(trimStartMs = startMs, trimEndMs = endMs)
        _uiState.update { it.copy(configuration = it.configuration.copy(audioTrack = updated)) }
    }

    fun setAudioVolume(volume: Float) {
        _uiState.update { it.copy(configuration = it.configuration.copy(audioVolume = volume.coerceIn(0f, 1f))) }
    }

    fun openAudioPicker() {
        _uiState.update { it.copy(isAudioPickerOpen = true) }
        loadLocalAudios()
        if (_uiState.value.onlineAudios.isEmpty()) {
            loadCategoryAudios(_uiState.value.selectedAudioCategory)
        }
    }

    fun closeAudioPicker() {
        _uiState.update { it.copy(isAudioPickerOpen = false, previewAudioTrack = null, isPreviewPlaying = false) }
    }

    fun openAudioTrimDialog() {
        _uiState.update { it.copy(isAudioTrimDialogOpen = true) }
    }

    fun closeAudioTrimDialog() {
        _uiState.update { it.copy(isAudioTrimDialogOpen = false) }
    }

    fun loadLocalAudios(query: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocalAudiosLoading = true) }
            val audios = localAudioRepository.getDeviceAudios(query)
            _uiState.update { it.copy(localAudios = audios, isLocalAudiosLoading = false) }
        }
    }

    fun searchOnlineAudios(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOnlineAudiosLoading = true, audioSearchQuery = query) }
            val results = if (query.isBlank()) {
                onlineAudioRepository.getCategorySongs(_uiState.value.selectedAudioCategory)
            } else {
                onlineAudioRepository.searchSongs(query)
            }
            _uiState.update { it.copy(onlineAudios = results, isOnlineAudiosLoading = false) }
        }
    }

    fun selectAudioCategory(category: AudioCategory) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedAudioCategory = category, isOnlineAudiosLoading = true, audioSearchQuery = "") }
            val results = onlineAudioRepository.getCategorySongs(category)
            _uiState.update { it.copy(onlineAudios = results, isOnlineAudiosLoading = false) }
        }
    }

    fun selectCustomAudioUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val track = localAudioRepository.resolveTrackFromUri(uri)
                setAudioTrack(track)
                closeAudioPicker()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Unable to load selected audio file") }
            }
        }
    }

    private fun loadCategoryAudios(category: AudioCategory) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOnlineAudiosLoading = true) }
            val results = onlineAudioRepository.getCategorySongs(category)
            _uiState.update { it.copy(onlineAudios = results, isOnlineAudiosLoading = false) }
        }
    }

    fun setPreviewTrack(track: CollageAudioTrack?) {
        _uiState.update {
            it.copy(
                previewAudioTrack = track,
                isPreviewPlaying = track != null,
            )
        }
    }

    fun togglePreviewPlaying() {
        _uiState.update { it.copy(isPreviewPlaying = !it.isPreviewPlaying) }
    }

    fun save(shareAfterSave: Boolean = false) {
        val state = _uiState.value
        if (state.isSaving || state.selectedItems.size < COLLAGE_MIN_ITEMS) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveProgress = 0, error = null) }
            try {
                val uri = exporter.export(state.selectedItems, state.configuration) { progress ->
                    _uiState.update { it.copy(saveProgress = progress) }
                }
                mediaRepository.syncMediaStore()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedUri = uri,
                        shareAfterSave = shareAfterSave,
                        saveProgress = 100,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(isSaving = false, error = error.message ?: "Unable to save collage") }
            }
        }
    }

    fun savedResultHandled() {
        _uiState.update { it.copy(savedUri = null, shareAfterSave = false) }
    }

    fun errorHandled() {
        _uiState.update { it.copy(error = null) }
    }
}

internal data class CollageUiState(
    val availableItems: List<MediaItem> = emptyList(),
    val selectedItems: List<MediaItem> = emptyList(),
    val stage: CollageStage = CollageStage.PICK,
    val configuration: CollageConfiguration = CollageConfiguration(),
    val activeTool: CollageTool = CollageTool.LAYOUT,
    val activeIndex: Int = 0,
    val replacingIndex: Int? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveProgress: Int = 0,
    val savedUri: Uri? = null,
    val shareAfterSave: Boolean = false,
    val error: String? = null,
    val isAudioPickerOpen: Boolean = false,
    val isAudioTrimDialogOpen: Boolean = false,
    val localAudios: List<CollageAudioTrack> = emptyList(),
    val isLocalAudiosLoading: Boolean = false,
    val onlineAudios: List<CollageAudioTrack> = emptyList(),
    val isOnlineAudiosLoading: Boolean = false,
    val selectedAudioCategory: AudioCategory = AUDIO_CATEGORIES[0],
    val audioSearchQuery: String = "",
    val previewAudioTrack: CollageAudioTrack? = null,
    val isPreviewPlaying: Boolean = false,
    /** Previous configurations, oldest first. Capped at [HISTORY_LIMIT]. */
    val undoStack: List<CollageConfiguration> = emptyList(),
    val redoStack: List<CollageConfiguration> = emptyList(),
    val lastEditLabel: String? = null,
    val lastEditAtMs: Long = 0L,
    /** The caption currently being styled, if any. */
    val activeTextId: String? = null,
    /** Dominant colours pulled out of the selected photos, offered as background choices. */
    val extractedColors: List<Int> = emptyList(),
    val isColorPickerOpen: Boolean = false,
    val isTextDialogOpen: Boolean = false,
    /** While true the canvas captures freehand strokes instead of panning cells. */
    val isDrawingMode: Boolean = false,
    val drawColor: Int = 0xFFFFFFFF.toInt(),
    val drawWidth: Float = 0.006f,
    /**
     * The stroke being drawn right now. Held outside the configuration so a single mark costs one
     * undo step on release rather than one per touch event.
     */
    val currentStroke: CollageStroke? = null,
) {
    val videoCount: Int get() = selectedItems.count(MediaItem::isVideo)
    val isVideoCollage: Boolean get() = videoCount > 0
    val activeItem: MediaItem? get() = selectedItems.getOrNull(activeIndex)
    val activeTransform: CollageItemTransform get() = activeItem?.let { configuration.itemTransforms[it.id] } ?: CollageItemTransform()
    /** The first photo's shape, which is what [CollageAspect.ORIGINAL] follows. */
    val originalRatio: Float
        get() = selectedItems.firstOrNull()
            ?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { it.width.toFloat() / it.height }
            ?: 1f
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val activeText: CollageTextOverlay? get() = configuration.textOverlays.firstOrNull { it.id == activeTextId }
    val canAddMore: Boolean get() = selectedItems.size < COLLAGE_MAX_ITEMS
}

internal enum class CollageStage { PICK, EDIT }

internal enum class CollageTool(val label: String) {
    LAYOUT("Layout"),
    RATIO("Ratio"),
    BORDER("Border"),
    BACKGROUND("Background"),
    FILTER("Filter"),
    TEXT("Text"),
    STICKER("Sticker"),
    DRAW("Draw"),
    MUSIC("Music"),
}

/** Consecutive edits of the same kind inside this window collapse into one undo step. */
private const val HISTORY_COALESCE_MS = 700L

/** How many steps of undo to keep. Each entry is one immutable configuration. */
private const val HISTORY_LIMIT = 40
