package com.pandagallery.app.ui.collage

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BorderAll
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CropLandscape
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEmotions
import com.pandagallery.app.data.collage.AUDIO_CATEGORIES
import com.pandagallery.app.data.collage.AudioCategory
import com.pandagallery.app.data.collage.CollageAudioTrack
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridView
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pandagallery.app.data.collage.COLLAGE_GRADIENTS
import com.pandagallery.app.data.collage.COLLAGE_MAX_ITEMS
import com.pandagallery.app.data.collage.COLLAGE_MIN_ITEMS
import com.pandagallery.app.data.collage.COLLAGE_PATTERNS
import com.pandagallery.app.data.collage.COLLAGE_STICKER_PRESETS
import com.pandagallery.app.data.collage.CollageAspect
import com.pandagallery.app.data.collage.CollageBackgroundMode
import com.pandagallery.app.data.collage.CollageFilter
import com.pandagallery.app.data.collage.CollageItemTransform
import com.pandagallery.app.data.collage.CollageLayoutMode
import com.pandagallery.app.data.collage.ratio
import com.pandagallery.app.data.collage.CollagePatternKind
import com.pandagallery.app.data.collage.CollagePattern
import com.pandagallery.app.data.collage.CollageLayouts
import com.pandagallery.app.data.collage.CollageStickerOverlay
import com.pandagallery.app.data.collage.CollageTextOverlay
import com.pandagallery.app.data.collage.FreestyleLayouts
import com.pandagallery.app.data.collage.NormalizedRect
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.ui.components.LabeledMinimalSlider
import com.pandagallery.app.ui.components.oneUiGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.hypot
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontFamily
import com.pandagallery.app.data.collage.COLLAGE_FONTS
import com.pandagallery.app.data.collage.CollageStroke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** The editor's tool tray: a glass panel that runs off the bottom edge, so only its top is rounded. */
private val COLLAGE_TRAY_SHAPE = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** How far the canvas's ground is allowed to run under the tray, so the glass has something to blur. */
private val COLLAGE_TRAY_OVERLAP = 28.dp

/** A slider's position within its own range, as a plain 0-100 read-out. */
private fun percentOfRange(value: Float, max: Float): String =
    "${((value / max).coerceIn(0f, 1f) * 100f).roundToInt()}"

/**
 * Everything the editor can do, in one place.
 *
 * The three composables below used to take the same forty-odd lambdas each, re-declared at every
 * level; adding the Filter, Text, Sticker, Draw and Music tabs would have made that ninety. Passing
 * one immutable holder instead keeps the call sites readable and means a new action costs one line
 * rather than four.
 */
@Immutable
internal data class CollageActions(
    val onBack: () -> Unit = {},
    val onToggleItem: (MediaItem) -> Unit = {},
    val onFinishPicking: () -> Unit = {},
    val onEditSelection: () -> Unit = {},
    val onSelectCell: (Int) -> Unit = {},
    val onSwapItems: (Int, Int) -> Unit = { _, _ -> },
    val onMoveActive: (Int) -> Unit = {},
    val onRemoveActive: () -> Unit = {},
    val onReplaceActive: () -> Unit = {},
    val onAddItem: () -> Unit = {},
    val onSetTool: (CollageTool) -> Unit = {},
    val onSetLayoutMode: (CollageLayoutMode) -> Unit = {},
    val onSetLayout: (Int) -> Unit = {},
    val onSetAspect: (CollageAspect) -> Unit = {},
    val onSetSpacing: (Float) -> Unit = {},
    val onSetCorner: (Float) -> Unit = {},
    val onSetOuterMargin: (Float) -> Unit = {},
    val onSetSplitRatio: (Float) -> Unit = {},
    val onSetSubSplitRatio: (Float) -> Unit = {},
    val onShuffleFreestyle: () -> Unit = {},
    val onSetBackgroundMode: (CollageBackgroundMode) -> Unit = {},
    val onSetBackground: (Int) -> Unit = {},
    val onSetGradient: (Int) -> Unit = {},
    val onSetPattern: (Int) -> Unit = {},
    val onOpenColorPicker: () -> Unit = {},
    val onSetMuteVideos: (Boolean) -> Unit = {},
    val onRotateActive: () -> Unit = {},
    val onFlipActiveH: () -> Unit = {},
    val onFlipActiveV: () -> Unit = {},
    val onResetTransform: () -> Unit = {},
    val onToggleFit: () -> Unit = {},
    val onToggleFitAt: (Int) -> Unit = {},
    val onSetActiveFilter: (CollageFilter) -> Unit = {},
    val onSetGlobalFilter: (CollageFilter) -> Unit = {},
    val onTransformActive: (Long, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    val onAddSticker: (String) -> Unit = {},
    val onUpdateStickerPos: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onTransformSticker: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onRemoveSticker: (String) -> Unit = {},
    val onOpenTextDialog: () -> Unit = {},
    val onUpdateTextPos: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onTransformText: (String, Float, Float) -> Unit = { _, _, _ -> },
    val onRemoveText: (String) -> Unit = {},
    val onSelectText: (String?) -> Unit = {},
    val onSetTextColor: (Int) -> Unit = {},
    val onSetTextFont: (Int) -> Unit = {},
    val onToggleTextOutline: () -> Unit = {},
    val onToggleTextPlate: () -> Unit = {},
    val onSetTextSize: (Float) -> Unit = {},
    val onSetTextRotation: (Float) -> Unit = {},
    val onSetDrawingMode: (Boolean) -> Unit = {},
    val onSetDrawColor: (Int) -> Unit = {},
    val onSetDrawWidth: (Float) -> Unit = {},
    val onBeginStroke: (Float, Float) -> Unit = { _, _ -> },
    val onExtendStroke: (Float, Float) -> Unit = { _, _ -> },
    val onEndStroke: () -> Unit = {},
    val onUndoStroke: () -> Unit = {},
    val onClearStrokes: () -> Unit = {},
    val onOpenTrimDialog: () -> Unit = {},
    val onOpenAudioPicker: () -> Unit = {},
    val onOpenAudioTrim: () -> Unit = {},
    val onRemoveAudio: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onShare: () -> Unit = {},
)

@Composable
internal fun CollageScreen(
    onBack: () -> Unit,
    onSaved: (Uri) -> Unit,
    viewModel: CollageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showTrimDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorHandled()
        }
    }
    LaunchedEffect(uiState.savedUri) {
        uiState.savedUri?.let { uri ->
            if (uiState.shareAfterSave) {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = if (uiState.isVideoCollage) "video/mp4" else "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "Share collage",
                    )
                )
                viewModel.savedResultHandled()
            } else {
                viewModel.savedResultHandled()
                onSaved(uri)
            }
        }
    }
    BackHandler(enabled = uiState.stage == CollageStage.PICK && uiState.selectedItems.size >= COLLAGE_MIN_ITEMS) {
        viewModel.finishPicking()
    }
    // Sampled once per selection rather than when the Background tab opens, so the swatches are
    // already there the first time the user looks for them.
    LaunchedEffect(uiState.stage, uiState.selectedItems.map(MediaItem::id)) {
        if (uiState.stage == CollageStage.EDIT) viewModel.extractPalette()
    }

    val actions = remember(viewModel, onBack, onSaved) {
        CollageActions(
            onBack = onBack,
            onToggleItem = viewModel::toggleItem,
            onFinishPicking = viewModel::finishPicking,
            onEditSelection = viewModel::editSelection,
            onSelectCell = viewModel::selectCell,
            onSwapItems = viewModel::swapItems,
            onMoveActive = viewModel::moveActive,
            onRemoveActive = viewModel::removeActive,
            onReplaceActive = viewModel::replaceActive,
            onAddItem = viewModel::addItem,
            onSetTool = viewModel::setTool,
            onSetLayoutMode = viewModel::setLayoutMode,
            onSetLayout = viewModel::setLayout,
            onSetAspect = viewModel::setAspect,
            onSetSpacing = viewModel::setSpacing,
            onSetCorner = viewModel::setCorner,
            onSetOuterMargin = viewModel::setOuterMargin,
            onSetSplitRatio = viewModel::setSplitRatio,
            onSetSubSplitRatio = viewModel::setSubSplitRatio,
            onShuffleFreestyle = viewModel::shuffleFreestyle,
            onSetBackgroundMode = viewModel::setBackgroundMode,
            onSetBackground = viewModel::setBackground,
            onSetGradient = viewModel::setGradient,
            onSetPattern = viewModel::setPattern,
            onOpenColorPicker = viewModel::openColorPicker,
            onSetMuteVideos = viewModel::setMuteVideos,
            onRotateActive = viewModel::rotateActiveItem,
            onFlipActiveH = viewModel::flipActiveItemHorizontal,
            onFlipActiveV = viewModel::flipActiveItemVertical,
            onResetTransform = viewModel::resetActiveTransform,
            onToggleFit = viewModel::toggleActiveContentFit,
            onToggleFitAt = viewModel::toggleContentFitAt,
            onSetActiveFilter = viewModel::setActiveItemFilter,
            onSetGlobalFilter = viewModel::setGlobalFilter,
            onTransformActive = viewModel::updateTransform,
            onAddSticker = viewModel::addSticker,
            onUpdateStickerPos = viewModel::updateStickerPosition,
            onTransformSticker = viewModel::transformSticker,
            onRemoveSticker = viewModel::removeSticker,
            onOpenTextDialog = viewModel::openTextDialog,
            onUpdateTextPos = viewModel::updateTextPosition,
            onTransformText = viewModel::transformText,
            onRemoveText = viewModel::removeText,
            onSelectText = viewModel::selectText,
            onSetTextColor = viewModel::setActiveTextColor,
            onSetTextFont = viewModel::setActiveTextFont,
            onToggleTextOutline = viewModel::toggleActiveTextOutline,
            onToggleTextPlate = viewModel::toggleActiveTextPlate,
            onSetTextSize = viewModel::setActiveTextSize,
            onSetTextRotation = viewModel::setActiveTextRotation,
            onSetDrawingMode = viewModel::setDrawingMode,
            onSetDrawColor = viewModel::setDrawColor,
            onSetDrawWidth = viewModel::setDrawWidth,
            onBeginStroke = viewModel::beginStroke,
            onExtendStroke = viewModel::extendStroke,
            onEndStroke = viewModel::endStroke,
            onUndoStroke = viewModel::undoStroke,
            onClearStrokes = viewModel::clearStrokes,
            onOpenTrimDialog = { showTrimDialog = true },
            onOpenAudioPicker = viewModel::openAudioPicker,
            onOpenAudioTrim = viewModel::openAudioTrimDialog,
            onRemoveAudio = viewModel::removeAudioTrack,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onSave = viewModel::save,
            onShare = { viewModel.save(shareAfterSave = true) },
        )
    }

    CollageContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        actions = actions,
    )

    if (uiState.isAudioPickerOpen) {
        AudioPickerDialog(
            localAudios = uiState.localAudios,
            isLocalLoading = uiState.isLocalAudiosLoading,
            onlineAudios = uiState.onlineAudios,
            isOnlineLoading = uiState.isOnlineAudiosLoading,
            selectedCategory = uiState.selectedAudioCategory,
            audioSearchQuery = uiState.audioSearchQuery,
            previewTrack = uiState.previewAudioTrack,
            isPlaying = uiState.isPreviewPlaying,
            onSearchLocal = viewModel::loadLocalAudios,
            onSearchOnline = viewModel::searchOnlineAudios,
            onSelectCategory = viewModel::selectAudioCategory,
            onPickCustomUri = viewModel::selectCustomAudioUri,
            onSelectTrack = { track ->
                viewModel.setAudioTrack(track)
                viewModel.closeAudioPicker()
            },
            onTogglePreview = { track ->
                if (uiState.previewAudioTrack?.id == track.id && uiState.isPreviewPlaying) {
                    viewModel.setPreviewTrack(null)
                } else {
                    viewModel.setPreviewTrack(track)
                }
            },
            onDismiss = viewModel::closeAudioPicker,
        )
    }

    if (uiState.isAudioTrimDialogOpen && uiState.configuration.audioTrack != null) {
        AudioTrimDialog(
            track = uiState.configuration.audioTrack!!,
            initialVolume = uiState.configuration.audioVolume,
            onDismiss = viewModel::closeAudioTrimDialog,
            onConfirm = { startMs, endMs, volume ->
                viewModel.setAudioTrim(startMs, endMs)
                viewModel.setAudioVolume(volume)
                viewModel.closeAudioTrimDialog()
            },
        )
    }

    if (uiState.isTextDialogOpen) {
        AddTextOverlayDialog(
            onDismiss = viewModel::closeTextDialog,
            onConfirm = { text, color, fontIndex, outlined ->
                viewModel.addText(text, color, fontIndex, outlined)
                viewModel.closeTextDialog()
            },
        )
    }

    if (uiState.isColorPickerOpen) {
        ColorPickerDialog(
            initialColor = uiState.configuration.backgroundColor,
            onDismiss = viewModel::closeColorPicker,
            onConfirm = { color ->
                viewModel.setBackground(color)
                viewModel.closeColorPicker()
            },
        )
    }

    if (showTrimDialog) {
        val activeItem = uiState.activeItem
        if (activeItem != null && activeItem.isVideo) {
            val transform = uiState.activeTransform
            val durationMs = activeItem.duration ?: 10_000L
            VideoTrimDialog(
                videoName = activeItem.displayName,
                durationMs = durationMs,
                initialStartMs = transform.trimStartMs,
                initialEndMs = transform.trimEndMs ?: durationMs,
                onDismiss = { showTrimDialog = false },
                onConfirm = { startMs, endMs ->
                    viewModel.setVideoTrim(activeItem.id, startMs, endMs)
                    showTrimDialog = false
                },
            )
        } else {
            showTrimDialog = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollageContent(
    uiState: CollageUiState,
    snackbarHostState: SnackbarHostState,
    actions: CollageActions,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Any changes you've made to this collage will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        actions.onBack()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.stage == CollageStage.PICK) {
                        Text(
                            if (uiState.replacingIndex == null) "Select items" else "Replace item"
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.stage == CollageStage.PICK && uiState.selectedItems.size >= COLLAGE_MIN_ITEMS) {
                            actions.onFinishPicking()
                        } else if (uiState.stage == CollageStage.EDIT) {
                            showDiscardDialog = true
                        } else {
                            actions.onBack()
                        }
                    }) {
                        if (uiState.stage == CollageStage.EDIT) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (uiState.stage == CollageStage.PICK) {
                        TextButton(
                            onClick = actions.onFinishPicking,
                            enabled = uiState.selectedItems.size >= COLLAGE_MIN_ITEMS,
                        ) {
                            Text("Done (${uiState.selectedItems.size}/$COLLAGE_MAX_ITEMS)")
                        }
                    } else {
                        IconButton(onClick = actions.onUndo, enabled = uiState.canUndo) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = "Undo",
                                tint = LocalContentColor.current.copy(alpha = if (uiState.canUndo) 1f else 0.3f),
                            )
                        }
                        IconButton(onClick = actions.onRedo, enabled = uiState.canRedo) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Redo,
                                contentDescription = "Redo",
                                tint = LocalContentColor.current.copy(alpha = if (uiState.canRedo) 1f else 0.3f),
                            )
                        }
                        IconButton(onClick = actions.onAddItem, enabled = uiState.canAddMore) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "Add photo",
                                tint = LocalContentColor.current.copy(alpha = if (uiState.canAddMore) 1f else 0.3f),
                            )
                        }
                        IconButton(onClick = actions.onShare, enabled = !uiState.isSaving) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                        TextButton(
                            onClick = actions.onSave,
                            enabled = !uiState.isSaving,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(
                                "Save",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.availableItems.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.stage == CollageStage.PICK -> CollagePicker(
                uiState = uiState,
                onToggleItem = actions.onToggleItem,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> CollageEditor(
                uiState = uiState,
                actions = actions,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        if (uiState.isSaving) SavingOverlay(uiState.saveProgress, uiState.isVideoCollage)
    }
}

@Composable
private fun CollagePicker(
    uiState: CollageUiState,
    onToggleItem: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The picker used to be one undifferentiated grid of the entire library, which is fine at fifty
    // photos and unusable at five thousand. Albums narrow it; date headers give it somewhere to
    // scroll to.
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    val albums = remember(uiState.availableItems) {
        uiState.availableItems.mapNotNull(MediaItem::bucketName).distinct().sorted()
    }
    val visibleItems = remember(uiState.availableItems, selectedAlbum) {
        if (selectedAlbum == null) uiState.availableItems
        else uiState.availableItems.filter { it.bucketName == selectedAlbum }
    }
    val sections = remember(visibleItems) {
        val formatter = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        visibleItems
            .groupBy { formatter.format(Date((it.dateTaken ?: (it.dateAdded * 1000L)))) }
            .toList()
    }

    Column(modifier = modifier) {
        if (albums.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    TrayToggle("All", selectedAlbum == null) { selectedAlbum = null }
                }
                items(albums) { album ->
                    TrayToggle(album, selectedAlbum == album) {
                        selectedAlbum = if (selectedAlbum == album) null else album
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            sections.forEach { (title, sectionItems) ->
                item(key = "header-$title", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
                    )
                }
                items(sectionItems, key = MediaItem::id) { item ->
                    PickerTile(
                        item = item,
                        selectedIndex = uiState.selectedItems.indexOfFirst { it.id == item.id },
                        onToggle = { onToggleItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerTile(item: MediaItem, selectedIndex: Int, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(role = Role.Checkbox) { onToggle() }
            .semantics {
                contentDescription = "${item.displayName}, ${if (selectedIndex >= 0) "selected ${selectedIndex + 1}" else "not selected"}"
            },
    ) {
        val context = LocalContext.current
        val pickerThumbRequest = remember(item.uri, item.dateModified) {
            ImageRequest.Builder(context)
                .data(item.uri)
                .size(384)
                .crossfade(false)
                .memoryCacheKey("${item.uri}_thumb")
                .placeholderMemoryCacheKey("${item.uri}_thumb")
                .build()
        }
        AsyncImage(
            model = pickerThumbRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Icon(
                Icons.Outlined.SmartDisplay,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
        if (selectedIndex >= 0) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (selectedIndex + 1).toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollageEditor(
    uiState: CollageUiState,
    actions: CollageActions,
    modifier: Modifier = Modifier,
) {
    // The tool tray floats over the canvas as a single pane of glass rather than sitting below it
    // in a column: that is what gives the blur something to work on, and it lets the collage keep
    // the full height of the screen. The tray's height is not fixed — Border is three slider rows
    // tall, the other tabs are one — so it is measured and handed back as the canvas's bottom
    // padding instead of being hardcoded.
    val trayHaze = remember { HazeState() }
    var trayHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(modifier = modifier) {
        // Main Interactive Collage Canvas Preview
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(trayHaze)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    // Slightly less than the tray is tall: the collage still clears the glass, but
                    // the ground it sits on runs underneath it rather than stopping at a seam.
                    bottom = (trayHeight - COLLAGE_TRAY_OVERLAP).coerceAtLeast(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            CollageCanvas(
                items = uiState.selectedItems,
                uiState = uiState,
                actions = actions,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .aspectRatio(uiState.configuration.aspect.ratio(uiState.originalRatio)),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { trayHeight = with(density) { it.height.toDp() } }
                .oneUiGlass(shape = COLLAGE_TRAY_SHAPE, state = trayHaze, tintAlpha = 0.58f),
        ) {
            // Tool Options Tray
            ToolOptions(uiState = uiState, actions = actions)

            // Bottom Navigation Tools (Samsung 4 Tabs)
            ToolBar(uiState.activeTool, actions.onSetTool)
        }
    }
}

/**
 * Draws a [CollagePattern] into a Compose canvas. The mirror of `CollageExporter.drawPattern`; the
 * two are kept structurally identical so the preview matches the file that gets saved.
 */
private fun DrawScope.drawCollagePattern(pattern: CollagePattern) {
    drawRect(Color(pattern.backgroundColor))
    val ink = Color(pattern.inkColor)
    val tile = (minOf(size.width, size.height) * pattern.scale).coerceAtLeast(4f)
    when (pattern.kind) {
        CollagePatternKind.DOTS -> {
            val radius = tile * 0.16f
            var y = tile / 2f
            var row = 0
            while (y < size.height + tile) {
                var x = if (row % 2 == 0) tile / 2f else tile
                while (x < size.width + tile) {
                    drawCircle(ink, radius, Offset(x, y))
                    x += tile
                }
                y += tile
                row++
            }
        }
        CollagePatternKind.STRIPES -> {
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(ink, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = tile * 0.45f)
                x += tile * 2f
            }
        }
        CollagePatternKind.GRID -> {
            val stroke = (tile * 0.06f).coerceAtLeast(1f)
            var x = 0f
            while (x < size.width) {
                drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
                x += tile
            }
            var y = 0f
            while (y < size.height) {
                drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
                y += tile
            }
        }
        CollagePatternKind.CHECKER -> {
            var row = 0
            var y = 0f
            while (y < size.height) {
                var column = 0
                var x = 0f
                while (x < size.width) {
                    if ((row + column) % 2 == 0) {
                        drawRect(ink, Offset(x, y), Size(tile, tile))
                    }
                    x += tile
                    column++
                }
                y += tile
                row++
            }
        }
    }
}

@Composable
private fun CollageCanvas(
    items: List<MediaItem>,
    uiState: CollageUiState,
    actions: CollageActions,
    modifier: Modifier = Modifier,
) {
    val config = uiState.configuration
    val isFreestyle = config.layoutMode == CollageLayoutMode.FREESTYLE
    val haptic = LocalHapticFeedback.current

    // Drag-to-swap tracking state
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragTouchPos by remember { mutableStateOf<Offset?>(null) }
    var hoverTargetIndex by remember { mutableStateOf<Int?>(null) }

    val bgModifier = when (config.backgroundMode) {
        CollageBackgroundMode.COLOR -> Modifier.background(Color(config.backgroundColor))
        CollageBackgroundMode.GRADIENT -> {
            val grad = COLLAGE_GRADIENTS.getOrElse(config.gradientIndex) { COLLAGE_GRADIENTS[0] }
            Modifier.background(Brush.linearGradient(listOf(Color(grad.startColor), Color(grad.endColor))))
        }
        CollageBackgroundMode.PATTERN -> {
            val pattern = COLLAGE_PATTERNS.getOrElse(config.patternIndex) { COLLAGE_PATTERNS[0] }
            Modifier.drawBehind { drawCollagePattern(pattern) }
        }
        CollageBackgroundMode.BLURRED_MEDIA -> Modifier.background(Color.Black)
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(bgModifier)
            .semantics { contentDescription = "Collage preview with ${items.size} items" },
    ) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()

        if (config.backgroundMode == CollageBackgroundMode.BLURRED_MEDIA) {
            val bgUri = config.backgroundImageUri ?: items.firstOrNull()?.uri
            if (bgUri != null) {
                val context = LocalContext.current
                val bgThumbRequest = remember(bgUri) {
                    ImageRequest.Builder(context)
                        .data(bgUri)
                        .crossfade(true)
                        .placeholderMemoryCacheKey("${bgUri}_thumb")
                        .build()
                }
                AsyncImage(
                    model = bgThumbRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(20.dp),
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            }
        }

        val freestyleCells = if (isFreestyle) {
            remember(items.size, config.freestyleIndex) {
                FreestyleLayouts.cells(items.size, config.freestyleIndex)
            }
        } else emptyList()

        val gridCells = if (!isFreestyle) {
            remember(items.size, config.layoutIndex, config.splitRatio, config.subSplitRatio, config.spacingPercent, config.outerMarginPercent) {
                val margin = config.outerMarginPercent
                CollageLayouts.cells(items.size, config.layoutIndex, config.splitRatio, config.subSplitRatio).map { cell ->
                    val inset = cell.inset(config.spacingPercent / 2f)
                    NormalizedRect(
                        left = inset.left * (1f - margin * 2f) + margin,
                        top = inset.top * (1f - margin * 2f) + margin,
                        right = inset.right * (1f - margin * 2f) + margin,
                        bottom = inset.bottom * (1f - margin * 2f) + margin,
                    )
                }
            }
        } else emptyList()

        if (isFreestyle) {
            freestyleCells.forEachIndexed { index, cell ->
                if (index < items.size) {
                    val item = items[index]
                    val selected = index == uiState.activeIndex
                    val isBeingDragged = draggingIndex == index
                    val isHovered = hoverTargetIndex == index && draggingIndex != index
                    val transform = config.itemTransforms[item.id] ?: CollageItemTransform()
                    val effectiveFilter = if (transform.filter != CollageFilter.ORIGINAL) transform.filter else config.globalFilter

                    val cardWidth = maxWidth * cell.rect.width
                    val cardHeight = maxHeight * cell.rect.height
                    val cardCorner = (maxWidth.coerceAtMost(maxHeight).value * config.cornerPercent.coerceAtLeast(0.015f)).dp

                    Box(
                        modifier = Modifier
                            .offset(x = maxWidth * cell.rect.left, y = maxHeight * cell.rect.top)
                            .size(width = cardWidth, height = cardHeight)
                            .zIndex(if (isBeingDragged) 10f else if (selected) 5f else 1f)
                            .graphicsLayer {
                                rotationZ = cell.rotationDegrees
                                shadowElevation = if (isBeingDragged) 16.dp.toPx() else cell.elevation.dp.toPx()
                                scaleX = if (isBeingDragged) 1.06f else 1f
                                scaleY = if (isBeingDragged) 1.06f else 1f
                                shape = RoundedCornerShape(cardCorner)
                                clip = true
                            }
                            .background(Color.White)
                            .border(
                                width = if (isHovered) 4.dp else if (selected) 3.dp else 1.5.dp,
                                color = if (isHovered) MaterialTheme.colorScheme.tertiary else if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(cardCorner),
                            )
                            .pointerInput(item.id, index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingIndex = index
                                        hoverTargetIndex = null
                                    },
                                    onDragEnd = {
                                        hoverTargetIndex?.let { target ->
                                            if (target != index) actions.onSwapItems(index, target)
                                        }
                                        draggingIndex = null
                                        hoverTargetIndex = null
                                        dragTouchPos = null
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        hoverTargetIndex = null
                                        dragTouchPos = null
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val currentPos = (dragTouchPos ?: Offset(cell.rect.centerX * canvasWidthPx, cell.rect.centerY * canvasHeightPx)) + dragAmount
                                        dragTouchPos = currentPos
                                        val normX = currentPos.x / canvasWidthPx
                                        val normY = currentPos.y / canvasHeightPx
                                        val target = freestyleCells.indexOfFirst {
                                            normX in it.rect.left..it.rect.right && normY in it.rect.top..it.rect.bottom
                                        }
                                        hoverTargetIndex = if (target >= 0 && target < items.size) target else null
                                    },
                                )
                            }
                            .pointerInput(item.id, selected) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (selected && draggingIndex == null) {
                                        val normalizedPanX = pan.x / cardWidth.toPx()
                                        val normalizedPanY = pan.y / cardHeight.toPx()
                                        actions.onTransformActive(item.id, normalizedPanX, normalizedPanY, zoom)
                                    }
                                }
                            }
                            .pointerInput(index) {
                                detectTapGestures(
                                    onTap = { actions.onSelectCell(index) },
                                    onDoubleTap = { actions.onToggleFitAt(index) },
                                )
                            },
                    ) {
                        CellImage(item = item, transform = transform, filter = effectiveFilter)
                    }
                }
            }
        } else {
            // GRID MODE
            gridCells.forEachIndexed { index, cell ->
                if (index < items.size) {
                    val item = items[index]
                    val selected = index == uiState.activeIndex
                    val isBeingDragged = draggingIndex == index
                    val isHovered = hoverTargetIndex == index && draggingIndex != index
                    val transform = config.itemTransforms[item.id] ?: CollageItemTransform()
                    val effectiveFilter = if (transform.filter != CollageFilter.ORIGINAL) transform.filter else config.globalFilter
                    val corner = (maxWidth.coerceAtMost(maxHeight).value * config.cornerPercent).dp
                    val cellWidth = maxWidth * cell.width
                    val cellHeight = maxHeight * cell.height

                    Box(
                        modifier = Modifier
                            .offset(x = maxWidth * cell.left, y = maxHeight * cell.top)
                            .size(width = cellWidth, height = cellHeight)
                            .zIndex(if (isBeingDragged) 10f else if (selected) 5f else 1f)
                            .graphicsLayer {
                                shadowElevation = if (isBeingDragged) 16.dp.toPx() else 0f
                                scaleX = if (isBeingDragged) 1.05f else 1f
                                scaleY = if (isBeingDragged) 1.05f else 1f
                                shape = RoundedCornerShape(corner)
                                clip = true
                            }
                            .border(
                                width = if (isHovered) 4.dp else if (selected) 3.dp else 0.dp,
                                color = if (isHovered) MaterialTheme.colorScheme.tertiary else if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(corner),
                            )
                            .pointerInput(item.id, index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingIndex = index
                                        hoverTargetIndex = null
                                    },
                                    onDragEnd = {
                                        hoverTargetIndex?.let { target ->
                                            if (target != index) actions.onSwapItems(index, target)
                                        }
                                        draggingIndex = null
                                        hoverTargetIndex = null
                                        dragTouchPos = null
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        hoverTargetIndex = null
                                        dragTouchPos = null
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val currentPos = (dragTouchPos ?: Offset(cell.centerX * canvasWidthPx, cell.centerY * canvasHeightPx)) + dragAmount
                                        dragTouchPos = currentPos
                                        val normX = currentPos.x / canvasWidthPx
                                        val normY = currentPos.y / canvasHeightPx
                                        val target = gridCells.indexOfFirst {
                                            normX in it.left..it.right && normY in it.top..it.bottom
                                        }
                                        hoverTargetIndex = if (target >= 0 && target < items.size) target else null
                                    },
                                )
                            }
                            .pointerInput(item.id, selected) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (selected && draggingIndex == null) {
                                        val normalizedPanX = pan.x / cellWidth.toPx()
                                        val normalizedPanY = pan.y / cellHeight.toPx()
                                        actions.onTransformActive(item.id, normalizedPanX, normalizedPanY, zoom)
                                    }
                                }
                            }
                            .pointerInput(index) {
                                detectTapGestures(
                                    onTap = { actions.onSelectCell(index) },
                                    onDoubleTap = { actions.onToggleFitAt(index) },
                                )
                            },
                    ) {
                        CellImage(item = item, transform = transform, filter = effectiveFilter)
                    }
                }
            }

            // Interactive Draggable Divider Handles (Primary & Secondary)
            val allDividers = remember(items.size, config.layoutIndex, config.splitRatio, config.subSplitRatio) {
                CollageLayouts.dividers(items.size, config.layoutIndex, config.splitRatio, config.subSplitRatio)
            }
            if (allDividers.isNotEmpty() && draggingIndex == null) {
                allDividers.forEach { divider ->
                    if (divider.isHorizontal) {
                        val dividerY = (divider.position * (1f - config.outerMarginPercent * 2f) + config.outerMarginPercent).coerceIn(0.12f, 0.88f)
                        val crossCenter = ((divider.crossStart + divider.crossEnd) / 2f * (1f - config.outerMarginPercent * 2f) + config.outerMarginPercent).coerceIn(0.15f, 0.85f)
                        Box(
                            modifier = Modifier
                                .offset(x = maxWidth * crossCenter - 24.dp, y = maxHeight * dividerY - 18.dp)
                                .size(width = 48.dp, height = 36.dp)
                                .zIndex(15f)
                                .pointerInput(items.size, config.layoutIndex, divider.isSecondary) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaRatio = dragAmount.y / canvasHeightPx
                                        if (divider.isSecondary) {
                                            actions.onSetSubSplitRatio(config.subSplitRatio + deltaRatio)
                                        } else {
                                            actions.onSetSplitRatio(config.splitRatio + deltaRatio)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White.copy(alpha = 0.95f),
                                shadowElevation = 4.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.2f)),
                                modifier = Modifier.size(width = 32.dp, height = 6.dp),
                            ) {}
                        }
                    } else {
                        val dividerX = (divider.position * (1f - config.outerMarginPercent * 2f) + config.outerMarginPercent).coerceIn(0.12f, 0.88f)
                        val crossCenter = ((divider.crossStart + divider.crossEnd) / 2f * (1f - config.outerMarginPercent * 2f) + config.outerMarginPercent).coerceIn(0.15f, 0.85f)
                        Box(
                            modifier = Modifier
                                .offset(x = maxWidth * dividerX - 18.dp, y = maxHeight * crossCenter - 24.dp)
                                .size(width = 36.dp, height = 48.dp)
                                .zIndex(15f)
                                .pointerInput(items.size, config.layoutIndex, divider.isSecondary) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaRatio = dragAmount.x / canvasWidthPx
                                        if (divider.isSecondary) {
                                            actions.onSetSubSplitRatio(config.subSplitRatio + deltaRatio)
                                        } else {
                                            actions.onSetSplitRatio(config.splitRatio + deltaRatio)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White.copy(alpha = 0.95f),
                                shadowElevation = 4.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.2f)),
                                modifier = Modifier.size(width = 6.dp, height = 32.dp),
                            ) {}
                        }
                    }
                }
            }
        }

        // Floating In-Cell Quick Action Menu
        if (uiState.activeIndex in items.indices && draggingIndex == null) {
            val activeItem = items[uiState.activeIndex]
            val activeRect = if (isFreestyle) {
                freestyleCells.getOrNull(uiState.activeIndex)?.rect
            } else {
                gridCells.getOrNull(uiState.activeIndex)
            }
            if (activeRect != null) {
                val menuWidth = if (activeItem.isVideo) 194.dp else 160.dp
                val cellCenterX = maxWidth * activeRect.centerX
                val menuX = (cellCenterX - menuWidth / 2f).coerceIn(8.dp, maxWidth - menuWidth - 8.dp)
                val menuY = if (activeRect.bottom > 0.80f) {
                    (maxHeight * activeRect.top - 46.dp).coerceAtLeast(8.dp)
                } else {
                    (maxHeight * activeRect.bottom - 46.dp).coerceAtLeast(8.dp)
                }
                FloatingCellQuickMenu(
                    activeItem = activeItem,
                    onRotate = actions.onRotateActive,
                    onFlip = actions.onFlipActiveH,
                    onFlipVertical = actions.onFlipActiveV,
                    onReset = actions.onResetTransform,
                    onReplace = actions.onReplaceActive,
                    onDelete = actions.onRemoveActive,
                    onTrim = if (activeItem.isVideo) actions.onOpenTrimDialog else null,
                    modifier = Modifier
                        .offset(x = menuX, y = menuY)
                        .zIndex(20f),
                )
            }
        }

        // Freehand layer. Always rendered; only interactive while the Draw tab has drawing armed,
        // so the cells keep their own pan and pinch the rest of the time.
        val strokes = config.strokes
        if (strokes.isNotEmpty() || uiState.currentStroke != null || uiState.isDrawingMode) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f)
                    .then(
                        if (uiState.isDrawingMode) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        actions.onBeginStroke(offset.x / canvasWidthPx, offset.y / canvasHeightPx)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        actions.onExtendStroke(
                                            change.position.x / canvasWidthPx,
                                            change.position.y / canvasHeightPx,
                                        )
                                    },
                                    onDragEnd = actions.onEndStroke,
                                    onDragCancel = actions.onEndStroke,
                                )
                            }
                        } else Modifier
                    ),
            ) {
                (strokes + listOfNotNull(uiState.currentStroke)).forEach { stroke ->
                    if (stroke.points.size < 4) return@forEach
                    val path = Path()
                    path.moveTo(stroke.points[0] * size.width, stroke.points[1] * size.height)
                    var index = 2
                    while (index + 1 < stroke.points.size) {
                        path.lineTo(stroke.points[index] * size.width, stroke.points[index + 1] * size.height)
                        index += 2
                    }
                    drawPath(
                        path = path,
                        color = Color(stroke.color),
                        style = Stroke(
                            width = (stroke.widthFraction * size.width).coerceAtLeast(1f),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }

        // Draggable Sticker Overlays Layer
        config.stickerOverlays.forEach { sticker ->
            DraggableSticker(
                sticker = sticker,
                canvasWidth = maxWidth,
                canvasHeight = maxHeight,
                onUpdatePosition = { dx, dy -> actions.onUpdateStickerPos(sticker.id, dx, dy) },
                onTransform = { zoom, rotation -> actions.onTransformSticker(sticker.id, zoom, rotation) },
                onRemove = { actions.onRemoveSticker(sticker.id) },
            )
        }

        // Draggable Text Overlays Layer
        config.textOverlays.forEach { textOverlay ->
            DraggableText(
                textOverlay = textOverlay,
                canvasWidth = maxWidth,
                canvasHeight = maxHeight,
                selected = textOverlay.id == uiState.activeTextId,
                onSelect = {
                    actions.onSelectText(if (textOverlay.id == uiState.activeTextId) null else textOverlay.id)
                },
                onUpdatePosition = { dx, dy -> actions.onUpdateTextPos(textOverlay.id, dx, dy) },
                onTransform = { zoom, rotation -> actions.onTransformText(textOverlay.id, zoom, rotation) },
                onRemove = { actions.onRemoveText(textOverlay.id) },
            )
        }

        // Floating synchronized video playback & mute control pill
        if (items.any(MediaItem::isVideo)) {
            var isPlayingVideos by remember { mutableStateOf(false) }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .zIndex(20f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { isPlayingVideos = !isPlayingVideos },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlayingVideos) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlayingVideos) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    VerticalDivider(
                        modifier = Modifier.height(16.dp),
                        color = Color.White.copy(alpha = 0.3f),
                    )
                    IconButton(
                        onClick = { actions.onSetMuteVideos(!config.muteVideos) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = if (config.muteVideos) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = if (config.muteVideos) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingCellQuickMenu(
    activeItem: MediaItem,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onFlipVertical: () -> Unit,
    onReset: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onTrim: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xE6202020),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onReplace, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.FindReplace, contentDescription = "Replace", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onRotate, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onFlip, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Flip, contentDescription = "Flip horizontally", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onFlipVertical, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Flip,
                    contentDescription = "Flip vertically",
                    tint = Color.White,
                    // The same glyph turned a quarter turn: Material ships no vertical variant, and
                    // a rotated mirror icon reads more clearly here than an unrelated one.
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 90f },
                )
            }
            IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset this photo", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            if (onTrim != null) {
                IconButton(onClick = onTrim, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ContentCut, contentDescription = "Trim", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun DraggableSticker(
    sticker: CollageStickerOverlay,
    canvasWidth: androidx.compose.ui.unit.Dp,
    canvasHeight: androidx.compose.ui.unit.Dp,
    onUpdatePosition: (Float, Float) -> Unit,
    onTransform: (Float, Float) -> Unit,
    onRemove: () -> Unit,
) {
    var isSelected by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .zIndex(16f)
            .offset(
                x = canvasWidth * sticker.x - 24.dp,
                y = canvasHeight * sticker.y - 24.dp,
            )
            .graphicsLayer { rotationZ = sticker.rotation }
            // One transform detector handles all three: drag, pinch and twist. Two separate
            // detectors would fight over the same pointers and the sticker would jump.
            .pointerInput(sticker.id) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    if (pan != Offset.Zero) {
                        onUpdatePosition(pan.x / canvasWidth.toPx(), pan.y / canvasHeight.toPx())
                    }
                    if (zoom != 1f || rotation != 0f) onTransform(zoom, rotation)
                }
            }
            .clickable { isSelected = !isSelected },
    ) {
        Text(
            text = sticker.emoji,
            fontSize = (canvasWidth.value * sticker.sizeFraction).sp,
            modifier = Modifier.padding(6.dp),
        )
        if (isSelected) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clickable { onRemove() },
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Delete Sticker", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * A caption in the live preview.
 *
 * Deliberately built to match `CollageExporter.drawTextOverlay` property for property — font,
 * outline, plate, rotation. It previously drew every caption in bold sans on a fixed dark plate,
 * so what you positioned was never quite what got saved.
 */
@Composable
private fun DraggableText(
    textOverlay: CollageTextOverlay,
    canvasWidth: androidx.compose.ui.unit.Dp,
    canvasHeight: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    onSelect: () -> Unit,
    onUpdatePosition: (Float, Float) -> Unit,
    onTransform: (Float, Float) -> Unit,
    onRemove: () -> Unit,
) {
    val font = textOverlay.font
    val fontFamily = remember(textOverlay.fontIndex) {
        val style = when {
            font.bold && font.italic -> Typeface.BOLD_ITALIC
            font.bold -> Typeface.BOLD
            font.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        FontFamily(Typeface.create(font.familyName, style))
    }
    val fontSize = (canvasWidth.value * textOverlay.fontSizeFraction).sp
    val outlineColor = if (isLightArgb(textOverlay.color)) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)

    Box(
        modifier = Modifier
            .zIndex(17f)
            .offset(
                x = canvasWidth * textOverlay.x - 40.dp,
                y = canvasHeight * textOverlay.y - 18.dp,
            )
            .graphicsLayer { rotationZ = textOverlay.rotation }
            .pointerInput(textOverlay.id) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    if (pan != Offset.Zero) {
                        onUpdatePosition(pan.x / canvasWidth.toPx(), pan.y / canvasHeight.toPx())
                    }
                    if (zoom != 1f || rotation != 0f) onTransform(zoom, rotation)
                }
            }
            .clickable(onClick = onSelect),
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .then(
                    if (textOverlay.backgroundColor != null) {
                        Modifier
                            .clip(CircleShape)
                            .background(Color(textOverlay.backgroundColor))
                    } else Modifier
                )
                .then(
                    if (selected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    } else Modifier
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (textOverlay.outlined) {
                Text(
                    text = textOverlay.text,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    style = LocalTextStyle.current.copy(
                        color = outlineColor,
                        drawStyle = Stroke(width = with(LocalDensity.current) { (fontSize.toPx() * 0.09f) }, join = StrokeJoin.Round),
                    ),
                )
            }
            Text(
                text = textOverlay.text,
                color = Color(textOverlay.color),
                fontFamily = fontFamily,
                fontSize = fontSize,
            )
        }
        if (selected) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clickable { onRemove() },
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Delete Text", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun CellImage(
    item: MediaItem,
    transform: CollageItemTransform,
    filter: CollageFilter,
) {
    val colorFilter = remember(filter) {
        if (filter != CollageFilter.ORIGINAL) {
            ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(filter.toColorMatrix().array))
        } else null
    }

    val context = LocalContext.current
    val cellImageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(true)
            .placeholderMemoryCacheKey("${item.uri}_thumb")
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = cellImageRequest,
            contentDescription = item.displayName,
            contentScale = if (transform.contentFit) ContentScale.Fit else ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = transform.scale * (if (transform.flipHorizontal) -1f else 1f)
                    scaleY = transform.scale * (if (transform.flipVertical) -1f else 1f)
                    rotationZ = transform.rotationDegrees.toFloat()
                    translationX = transform.offsetX * size.width
                    translationY = transform.offsetY * size.height
                },
        )
        if (item.isVideo) {
            Icon(
                Icons.Outlined.SmartDisplay,
                contentDescription = "Video",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .shadow(4.dp, CircleShape),
            )
        }
    }
}

@Composable
private fun ToolOptions(
    uiState: CollageUiState,
    actions: CollageActions,
) {
    // No container colour of its own: the tray is drawn by the glass panel this sits inside, and an
    // opaque surface painted over the blur would defeat it. The height is a floor rather than a
    // fixed value so the three-row Border tab is not clipped.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .padding(top = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
            when (uiState.activeTool) {
                CollageTool.LAYOUT -> {
                    val itemCount = uiState.selectedItems.size
                    val isFreestyle = uiState.configuration.layoutMode == CollageLayoutMode.FREESTYLE
                    val gridVariants = CollageLayouts.variantCount(itemCount)

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // 1. Freeform Tile
                        item {
                            FreeformLayoutTile(
                                selected = isFreestyle,
                                onClick = {
                                    if (isFreestyle) actions.onShuffleFreestyle() else actions.onSetLayoutMode(CollageLayoutMode.FREESTYLE)
                                },
                            )
                        }

                        // 2. Grid Variation Tiles
                        items(gridVariants) { index ->
                            LayoutThumbnail(
                                itemCount = itemCount,
                                layoutIndex = index,
                                selected = !isFreestyle && uiState.configuration.layoutIndex == index,
                                onClick = {
                                    actions.onSetLayoutMode(CollageLayoutMode.GRID)
                                    actions.onSetLayout(index)
                                },
                            )
                        }
                    }
                }

                CollageTool.RATIO -> {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(CollageAspect.entries) { aspect ->
                            RatioButton(aspect, aspect == uiState.configuration.aspect) { actions.onSetAspect(aspect) }
                        }
                    }
                }

                CollageTool.BORDER -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // The read-outs are the slider's own share of its range, not the raw
                        // fraction of the canvas: "0.03" means nothing to someone dragging a border
                        // in, whereas "50" places the handle against the two ends they can feel.
                        LabeledMinimalSlider(
                            label = "Margin",
                            value = uiState.configuration.outerMarginPercent,
                            onValueChange = actions.onSetOuterMargin,
                            valueRange = 0f..0.06f,
                            valueLabel = percentOfRange(uiState.configuration.outerMarginPercent, 0.06f),
                        )
                        LabeledMinimalSlider(
                            label = "Spacing",
                            value = uiState.configuration.spacingPercent,
                            onValueChange = actions.onSetSpacing,
                            valueRange = 0f..0.06f,
                            valueLabel = percentOfRange(uiState.configuration.spacingPercent, 0.06f),
                        )
                        LabeledMinimalSlider(
                            label = "Corners",
                            value = uiState.configuration.cornerPercent,
                            onValueChange = actions.onSetCorner,
                            valueRange = 0f..0.08f,
                            valueLabel = percentOfRange(uiState.configuration.cornerPercent, 0.08f),
                        )
                    }
                }

                CollageTool.BACKGROUND -> {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Blur Option
                        item {
                            BlurBackgroundButton(
                                selected = uiState.configuration.backgroundMode == CollageBackgroundMode.BLURRED_MEDIA,
                                onClick = { actions.onSetBackgroundMode(CollageBackgroundMode.BLURRED_MEDIA) },
                            )
                        }
                        // A custom colour of the user's choosing, and shades lifted from the photos.
                        item {
                            TrayActionButton(Icons.Outlined.Colorize, "Custom colour", actions.onOpenColorPicker)
                        }
                        items(uiState.extractedColors) { color ->
                            ColorButton(
                                color = color,
                                selected = uiState.configuration.backgroundMode == CollageBackgroundMode.COLOR &&
                                        uiState.configuration.backgroundColor == color,
                                onClick = { actions.onSetBackground(color) },
                            )
                        }
                        // Colors
                        items(COLLAGE_COLORS) { color ->
                            ColorButton(
                                color = color,
                                selected = uiState.configuration.backgroundMode == CollageBackgroundMode.COLOR &&
                                        uiState.configuration.backgroundColor == color,
                                onClick = { actions.onSetBackground(color) },
                            )
                        }
                        // Gradients
                        itemsIndexed(COLLAGE_GRADIENTS) { index, grad ->
                            GradientButton(
                                gradient = grad,
                                selected = uiState.configuration.backgroundMode == CollageBackgroundMode.GRADIENT &&
                                        uiState.configuration.gradientIndex == index,
                                onClick = { actions.onSetGradient(index) },
                            )
                        }
                        // Patterns
                        itemsIndexed(COLLAGE_PATTERNS) { index, pattern ->
                            PatternButton(
                                pattern = pattern,
                                selected = uiState.configuration.backgroundMode == CollageBackgroundMode.PATTERN &&
                                        uiState.configuration.patternIndex == index,
                                onClick = { actions.onSetPattern(index) },
                            )
                        }
                    }
                }

                CollageTool.FILTER -> FilterTray(uiState, actions)
                CollageTool.TEXT -> TextTray(uiState, actions)
                CollageTool.STICKER -> StickerTray(actions)
                CollageTool.DRAW -> DrawTray(uiState, actions)
                CollageTool.MUSIC -> MusicTray(uiState, actions)
            }
    }
}

/**
 * The filter strip. Each tile previews the real photo with the filter applied rather than showing a
 * swatch, because a colour-matrix name means nothing until you see what it does to your own image.
 *
 * "All" writes the global filter; "One" writes the selected cell's own, which the exporter already
 * prefers over the global one.
 */
@Composable
private fun FilterTray(uiState: CollageUiState, actions: CollageActions) {
    var applyToAll by remember { mutableStateOf(true) }
    val previewItem = (if (applyToAll) uiState.selectedItems.firstOrNull() else uiState.activeItem)
        ?: uiState.selectedItems.firstOrNull()
    val activeFilter = if (applyToAll) {
        uiState.configuration.globalFilter
    } else {
        uiState.activeTransform.filter
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrayToggle("All photos", applyToAll) { applyToAll = true }
            TrayToggle("This one", !applyToAll) { applyToAll = false }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(CollageFilter.entries) { filter ->
                FilterTile(
                    item = previewItem,
                    filter = filter,
                    selected = filter == activeFilter,
                    onClick = {
                        if (applyToAll) actions.onSetGlobalFilter(filter) else actions.onSetActiveFilter(filter)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterTile(
    item: MediaItem?,
    filter: CollageFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorFilter = remember(filter) {
        if (filter == CollageFilter.ORIGINAL) null
        else ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(filter.toColorMatrix().array))
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .border(2.dp, border, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        ) {
            if (item != null) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            filter.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Captions: add one, pick one to style, then restyle it without reopening a dialog. */
@Composable
private fun TextTray(uiState: CollageUiState, actions: CollageActions) {
    val captions = uiState.configuration.textOverlays
    val active = uiState.activeText

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                TrayActionButton(Icons.Outlined.TextFields, "Add caption", actions.onOpenTextDialog)
            }
            items(captions, key = { it.id }) { caption ->
                TrayChip(
                    label = caption.text,
                    selected = caption.id == uiState.activeTextId,
                    onClick = { actions.onSelectText(if (caption.id == uiState.activeTextId) null else caption.id) },
                    onRemove = { actions.onRemoveText(caption.id) },
                )
            }
        }

        if (active != null) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(COLLAGE_FONTS) { index, font ->
                    TrayChip(
                        label = font.label,
                        selected = index == active.fontIndex,
                        onClick = { actions.onSetTextFont(index) },
                    )
                }
                item {
                    TrayToggle("Outline", active.outlined, actions.onToggleTextOutline)
                }
                item {
                    TrayToggle("Plate", active.backgroundColor != null, actions.onToggleTextPlate)
                }
                items(COLLAGE_COLORS) { color ->
                    ColorButton(color, color == active.color) { actions.onSetTextColor(color) }
                }
            }
            LabeledMinimalSlider(
                label = "Size",
                value = active.fontSizeFraction,
                onValueChange = actions.onSetTextSize,
                valueRange = 0.02f..0.30f,
                valueLabel = percentOfRange(active.fontSizeFraction - 0.02f, 0.28f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            LabeledMinimalSlider(
                label = "Angle",
                value = active.rotation,
                onValueChange = actions.onSetTextRotation,
                valueRange = -180f..180f,
                valueLabel = "${active.rotation.roundToInt()}",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            Text(
                if (captions.isEmpty()) "Add a caption, then drag or pinch it on the collage."
                else "Tap a caption to restyle it.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun StickerTray(actions: CollageActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Tap to add — then drag or pinch it on the collage.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(COLLAGE_STICKER_PRESETS) { emoji ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .clickable { actions.onAddSticker(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun DrawTray(uiState: CollageUiState, actions: CollageActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                TrayToggle(
                    label = if (uiState.isDrawingMode) "Drawing" else "Draw",
                    selected = uiState.isDrawingMode,
                    onClick = { actions.onSetDrawingMode(!uiState.isDrawingMode) },
                )
            }
            item { TrayActionButton(Icons.AutoMirrored.Outlined.Undo, "Undo stroke", actions.onUndoStroke) }
            item { TrayActionButton(Icons.Outlined.Delete, "Clear drawing", actions.onClearStrokes) }
            items(COLLAGE_COLORS) { color ->
                ColorButton(color, color == uiState.drawColor) { actions.onSetDrawColor(color) }
            }
        }
        LabeledMinimalSlider(
            label = "Brush",
            value = uiState.drawWidth,
            onValueChange = actions.onSetDrawWidth,
            valueRange = 0.002f..0.04f,
            valueLabel = percentOfRange(uiState.drawWidth - 0.002f, 0.038f),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun MusicTray(uiState: CollageUiState, actions: CollageActions) {
    val track = uiState.configuration.audioTrack
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (track == null) {
            Text(
                if (uiState.isVideoCollage) "Add a soundtrack to your video collage."
                else "Adding music turns this collage into a video.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = actions.onOpenAudioPicker) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add music")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (track.artist.isNotBlank()) {
                        Text(
                            track.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = actions.onOpenAudioTrim) {
                    Icon(Icons.Outlined.ContentCut, contentDescription = "Trim music")
                }
                IconButton(onClick = actions.onRemoveAudio) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove music")
                }
            }
        }
        if (uiState.isVideoCollage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrayToggle(
                    label = if (uiState.configuration.muteVideos) "Clips muted" else "Clip sound on",
                    selected = !uiState.configuration.muteVideos,
                    onClick = { actions.onSetMuteVideos(!uiState.configuration.muteVideos) },
                )
            }
        }
    }
}

// ── Small shared tray controls ───────────────────────────────────────────────

@Composable
private fun TrayToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else Color.Transparent,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TrayActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TrayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else Color.Transparent,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = if (onRemove == null) 14.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PatternButton(pattern: CollagePattern, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .drawBehind { drawCollagePattern(pattern) }
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected pattern",
                tint = if (isLightArgb(pattern.backgroundColor)) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun isLightArgb(color: Int): Boolean {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b) > 150f
}

@Composable
private fun FreeformLayoutTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val strokeWidth = if (selected) 1.5.dp else 1.dp
    val tileShape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(tileShape).clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(tileShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                )
                .border(strokeWidth, borderColor, tileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = "Freeform Layout",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun BlurBackgroundButton(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF333333))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Blur", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddTextOverlayDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var fontIndex by remember { mutableIntStateOf(0) }
    var outlined by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Caption") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Enter text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Font", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(COLLAGE_FONTS) { index, font ->
                        TrayChip(font.label, index == fontIndex, onClick = { fontIndex = index })
                    }
                }
                Text("Text Color", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(COLLAGE_COLORS) { color ->
                        ColorButton(color, color == selectedColor) { selectedColor = color }
                    }
                }
                TrayToggle("Outline", outlined) { outlined = !outlined }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text, selectedColor, fontIndex, outlined) },
                enabled = text.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * A compact HSV picker: hue on one bar, then saturation and lightness as their own bars rather than
 * a 2-D field. Three one-dimensional controls are far easier to hit accurately on a phone than a
 * square gradient, and they map exactly onto the sliders used everywhere else in this editor.
 */
@Composable
private fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(color))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                (0..6).map { Color(AndroidColor.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }
                            )
                        ),
                )
                LabeledMinimalSlider(
                    label = "Hue",
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    valueLabel = "${hue.roundToInt()}",
                )
                LabeledMinimalSlider(
                    label = "Sat",
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f,
                    valueLabel = "${(saturation * 100).roundToInt()}",
                )
                LabeledMinimalSlider(
                    label = "Light",
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..1f,
                    valueLabel = "${(value * 100).roundToInt()}",
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(color) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun VideoTrimDialog(
    videoName: String,
    durationMs: Long,
    initialStartMs: Long,
    initialEndMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    var range by remember {
        mutableStateOf(initialStartMs.toFloat()..initialEndMs.toFloat().coerceAtMost(durationMs.toFloat()))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trim Video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(videoName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val startSec = (range.start / 1000f).toInt()
                val endSec = (range.endInclusive / 1000f).toInt()
                val trimDurationSec = endSec - startSec

                Text("Segment: $startSec s – $endSec s ($trimDurationSec s)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(range.start.toLong(), range.endInclusive.toLong()) }) {
                Text("Apply Trim")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun LayoutThumbnail(
    itemCount: Int,
    layoutIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = remember(itemCount, layoutIndex) { CollageLayouts.cells(itemCount, layoutIndex) }
    val cellColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    // A translucent wash rather than an opaque surface: these tiles sit on the glass tray, and a
    // filled square would punch a hole in the blur behind them.
    Canvas(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp),
            )
            .padding(7.dp),
    ) {
        cells.forEach { cell ->
            drawRect(
                color = cellColor,
                topLeft = Offset(size.width * cell.left, size.height * cell.top),
                size = Size(size.width * cell.width, size.height * cell.height),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

@Composable
private fun RatioButton(aspect: CollageAspect, selected: Boolean, onClick: () -> Unit) {
    // An outlined pill rather than a filled tonal circle: on the glass tray a solid fill behind
    // every ratio reads as four competing buttons instead of one selected value.
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 52.dp)
            .height(36.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            aspect.label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ColorButton(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // A small tick, not a filled CheckCircle: at icon default size the check covered the whole
        // 40dp swatch, so the selected colour was the one colour you could not see.
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected color",
                tint = if (Color(color).luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun GradientButton(gradient: com.pandagallery.app.data.collage.CollageGradient, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(gradient.startColor), Color(gradient.endColor))))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected gradient",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ToolBar(active: CollageTool, onSetTool: (CollageTool) -> Unit) {
    val tools = CollageTool.entries
    val listState = rememberLazyListState()
    // Nine tools do not fit a phone's width, so the row scrolls — and scrolls itself to whichever
    // tool is selected, so switching tabs from the canvas never leaves the active one off-screen.
    LaunchedEffect(active) {
        listState.animateScrollToItem(tools.indexOf(active).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tools) { tool ->
            val icon = when (tool) {
                CollageTool.LAYOUT -> Icons.Outlined.GridView
                CollageTool.RATIO -> Icons.Outlined.CropLandscape
                CollageTool.BORDER -> Icons.Outlined.BorderAll
                CollageTool.BACKGROUND -> Icons.Outlined.ColorLens
                CollageTool.FILTER -> Icons.Outlined.FilterVintage
                CollageTool.TEXT -> Icons.Outlined.TextFields
                CollageTool.STICKER -> Icons.Outlined.EmojiEmotions
                CollageTool.DRAW -> Icons.Outlined.Brush
                CollageTool.MUSIC -> Icons.Outlined.MusicNote
            }
            val selected = tool == active
            val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSetTool(tool) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The active tab is marked by a tinted pill behind its icon rather than colour
                // alone, which is what keeps it legible once the row is sitting on glass.
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 28.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    tool.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = tint,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SavingOverlay(progress: Int, video: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.scrim.copy(alpha = .65f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (video) "Exporting Video Collage" else "Exporting Collage",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (video) {
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("$progress%", style = MaterialTheme.typography.bodyMedium)
                    } else CircularProgressIndicator()
                }
            }
        }
    }
}

private val COLLAGE_COLORS = listOf(
    0xFFFFFFFF.toInt(), // White
    0xFF000000.toInt(), // Black
    0xFFF5F5F5.toInt(), // Off-White
    0xFF212121.toInt(), // Charcoal
    0xFFFFF8E7.toInt(), // Sand / Cream
    0xFFE1F5FE.toInt(), // Sky Blue
    0xFFFCE4EC.toInt(), // Pastel Rose
    0xFFE8F5E9.toInt(), // Mint Green
    0xFFF3E5F5.toInt(), // Lavender
    0xFFECEFF1.toInt(), // Cool Slate
)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun AudioTrackRowItem(
    track: CollageAudioTrack,
    isPreviewing: Boolean,
    onTogglePreview: () -> Unit,
    onSelect: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isPreviewing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onTogglePreview,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPreviewing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                if (!track.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    Text(
                        text = "${track.artist} • ${formatDuration(track.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            FilledTonalButton(
                onClick = onSelect,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("Use", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AudioPickerDialog(
    localAudios: List<CollageAudioTrack>,
    isLocalLoading: Boolean,
    onlineAudios: List<CollageAudioTrack>,
    isOnlineLoading: Boolean,
    selectedCategory: AudioCategory,
    audioSearchQuery: String,
    previewTrack: CollageAudioTrack?,
    isPlaying: Boolean,
    onSearchLocal: (String) -> Unit,
    onSearchOnline: (String) -> Unit,
    onSelectCategory: (AudioCategory) -> Unit,
    onPickCustomUri: (Uri) -> Unit,
    onSelectTrack: (CollageAudioTrack) -> Unit,
    onTogglePreview: (CollageAudioTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var localSearchText by remember { mutableStateOf("") }
    var onlineSearchText by remember { mutableStateOf(audioSearchQuery) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { onPickCustomUri(it) }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(previewTrack, isPlaying) {
        if (previewTrack != null && isPlaying) {
            mediaPlayer?.release()
            val player = MediaPlayer().apply {
                try {
                    setDataSource(context, previewTrack.uri)
                    prepare()
                    start()
                    isLooping = true
                } catch (_: Exception) {}
            }
            mediaPlayer = player
        } else {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Add Background Music", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Tab Selection: Online vs Local
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("🌐 Online Music") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("📱 My Device") },
                    )
                }

                if (selectedTab == 0) {
                    // Online Music Tab
                    OutlinedTextField(
                        value = onlineSearchText,
                        onValueChange = {
                            onlineSearchText = it
                            onSearchOnline(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search Malayalam, Hindi, English...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )

                    // Curated Categories Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(AUDIO_CATEGORIES, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCategory.id == cat.id && onlineSearchText.isBlank(),
                                onClick = {
                                    onlineSearchText = ""
                                    onSelectCategory(cat)
                                },
                                label = { Text("${cat.icon} ${cat.title}") },
                            )
                        }
                    }

                    if (isOnlineLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else if (onlineAudios.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (onlineSearchText.isBlank()) "No songs found in this category" else "No songs found for '$onlineSearchText'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(onlineAudios, key = { it.id }) { track ->
                                AudioTrackRowItem(
                                    track = track,
                                    isPreviewing = previewTrack?.id == track.id && isPlaying,
                                    onTogglePreview = { onTogglePreview(track) },
                                    onSelect = { onSelectTrack(track) },
                                )
                            }
                        }
                    }
                } else {
                    // Local Device Tab
                    Button(
                        onClick = { filePickerLauncher.launch("audio/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Browse Audio Files",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    OutlinedTextField(
                        value = localSearchText,
                        onValueChange = {
                            localSearchText = it
                            onSearchLocal(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search songs on phone...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )

                    if (isLocalLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else if (localAudios.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (localSearchText.isBlank()) "No audio files found on phone" else "No matching songs found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(localAudios, key = { it.id }) { track ->
                                AudioTrackRowItem(
                                    track = track,
                                    isPreviewing = previewTrack?.id == track.id && isPlaying,
                                    onTogglePreview = { onTogglePreview(track) },
                                    onSelect = { onSelectTrack(track) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AudioTrimDialog(
    track: CollageAudioTrack,
    initialVolume: Float = 1.0f,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long?, Float) -> Unit,
) {
    val totalDurationMs = if (track.durationMs > 0) track.durationMs else 60_000L
    var startMs by remember { mutableFloatStateOf(track.trimStartMs.toFloat()) }
    var durationSec by remember { mutableIntStateOf(15) } // default 15s clip
    var volume by remember { mutableFloatStateOf(initialVolume) }

    val endMs = (startMs + durationSec * 1000L).coerceAtMost(totalDurationMs.toFloat()).toLong()

    val context = LocalContext.current
    var isPreviewing by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(isPreviewing, startMs, volume) {
        if (isPreviewing) {
            mediaPlayer?.release()
            val player = MediaPlayer().apply {
                try {
                    setDataSource(context, track.uri)
                    prepare()
                    seekTo(startMs.toInt())
                    setVolume(volume, volume)
                    start()
                } catch (_: Exception) {}
            }
            mediaPlayer = player
        } else {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Adjust Background Audio", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "${track.title} - ${track.artist}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )

                Text(
                    text = "Playing: ${formatDuration(startMs.toLong())} - ${formatDuration(endMs)} (${durationSec}s snippet)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Start position slider
                Column {
                    Text("Start Position: ${formatDuration(startMs.toLong())}", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = startMs,
                        onValueChange = {
                            startMs = it
                            if (isPreviewing) {
                                isPreviewing = false
                            }
                        },
                        valueRange = 0f..(totalDurationMs - 3000L).coerceAtLeast(0L).toFloat(),
                    )
                }

                // Volume slider
                Column {
                    Text("Music Volume: ${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            mediaPlayer?.setVolume(it, it)
                        },
                        valueRange = 0f..1f,
                    )
                }

                // Snippet length chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Clip Length:", style = MaterialTheme.typography.labelSmall)
                    listOf(10, 15, 30, 60).forEach { sec ->
                        FilterChip(
                            selected = durationSec == sec,
                            onClick = { durationSec = sec },
                            label = { Text("${sec}s") },
                        )
                    }
                }

                // Preview playback button
                FilledTonalButton(
                    onClick = { isPreviewing = !isPreviewing },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPreviewing) "Pause Snippet Preview" else "Play Snippet Preview")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(startMs.toLong(), endMs, volume)
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}


