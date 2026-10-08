package com.pandagallery.app.ui.editor

import android.view.HapticFeedbackConstants
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import kotlin.math.roundToInt
import com.pandagallery.app.R
import com.pandagallery.app.data.editing.*
import com.pandagallery.app.ui.components.PremiumAlertDialog
import kotlin.math.abs
import kotlinx.coroutines.withContext

private val OneUiAccent = Color(0xFF2C6CF5)
private val OneUiGreen = Color(0xFF34C759)
private val OneUiCardBg = Color(0xFF18181A)
private val OneUiSelectedCardBg = Color(0xFF2C2C2E)
private val OneUiBorderColor = Color(0xFF2C2C2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    mediaId: Long,
    uriString: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    var selectedTab by rememberSaveable { mutableStateOf(EditorTab.TRANSFORM) }
    var selectedDecoSubTool by rememberSaveable { mutableStateOf(DecorationSubTool.DRAW) }
    var selectedAiSubTool by rememberSaveable { mutableStateOf(AiSubTool.OBJECT_ERASER) }
    var transformSubMode by rememberSaveable { mutableStateOf(0) } // 0: Straighten, 1: Perspective, 2: Document Scan
    var draftDocCorners by remember { mutableStateOf(DocumentCorners()) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedPenType by rememberSaveable { mutableStateOf(PenType.REGULAR) }
    var brushColor by remember { mutableLongStateOf(0xffff3b30) }
    var brushWidth by remember { mutableFloatStateOf(0.010f) }
    var brushOpacity by remember { mutableFloatStateOf(1.0f) }
    var draftStroke by remember { mutableStateOf(emptyList<NormalizedPoint>()) }

    var isComparingOriginal by remember { mutableStateOf(false) }
    var showSaveMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAdvancedFormatDialog by remember { mutableStateOf(false) }
    var isEyedropperActive by remember { mutableStateOf(false) }
    var selectedTextIndex by remember { mutableIntStateOf(-1) }
    var selectedStickerIndex by remember { mutableIntStateOf(-1) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showGenerativeEditDialog by rememberSaveable { mutableStateOf(false) }
    var sourceBitmapForGenerative by remember { mutableStateOf<Bitmap?>(null) }

    val attemptClose = { if (state.canUndo || state.edit.hasChanges) showDiscardConfirm = true else onBack() }
    BackHandler(onBack = attemptClose)
    LaunchedEffect(mediaId, uriString) {
        if (!uriString.isNullOrEmpty()) {
            viewModel.loadUri(uriString)
        } else {
            viewModel.load(mediaId)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = attemptClose,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close editor",
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    // Hold to Compare Button
                    if (state.edit.hasChanges && state.item?.isVideo != true) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isComparingOriginal) OneUiAccent.copy(alpha = 0.25f) else Color(0xFF1E1E20),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = if (isComparingOriginal) OneUiAccent else Color.White.copy(alpha = 0.15f),
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            isComparingOriginal = true
                                            tryAwaitRelease()
                                            isComparingOriginal = false
                                        }
                                    )
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Compare,
                                    contentDescription = stringResource(R.string.editor_compare_hold),
                                    tint = if (isComparingOriginal) OneUiAccent else Color(0xFFD1D1D6),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Compare",
                                    color = if (isComparingOriginal) OneUiAccent else Color(0xFFD1D1D6),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    // Revert Button
                    val canRestore = state.edit.hasChanges || state.item?.isVideo == true
                    if (canRestore) {
                        TextButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.reset()
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White.copy(alpha = 0.85f),
                            ),
                        ) {
                            Text(stringResource(R.string.editor_revert), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(2.dp))
                    }

                    // Undo / Redo
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.undo()
                        },
                        enabled = state.canUndo,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) Color.White else Color(0xFF48484A),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.redo()
                        },
                        enabled = state.canRedo,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) Color.White else Color(0xFF48484A),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // Save Dropdown Menu
                    Box {
                        Button(
                            onClick = { showSaveMenu = true },
                            enabled = state.item != null && !state.isSaving,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                disabledContainerColor = Color(0xFF2C2C2E),
                                disabledContentColor = Color(0xFF6E6E73),
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .defaultMinSize(minHeight = 36.dp),
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    // saveProgress is a percentage; rendering the bare Int put a
                                    // naked "45" in the button with nothing to say what it meant.
                                    text = state.saveProgress.takeIf { it > 0 }?.let { "$it%" } ?: "…",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.editor_save),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSaveMenu,
                            onDismissRequest = { showSaveMenu = false },
                            modifier = Modifier.background(Color(0xFF1E1E20)),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_save_as_copy), color = Color.White) },
                                onClick = {
                                    showSaveMenu = false
                                    viewModel.save(saveAsCopy = true, onSaved = onSaved)
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.FileCopy, null, tint = Color.White)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_save_overwrite), color = Color.White) },
                                onClick = {
                                    showSaveMenu = false
                                    viewModel.save(saveAsCopy = false, onSaved = onSaved)
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Save, null, tint = Color.White)
                                },
                            )
                        }
                    }

                    // 3-Dots More Menu
                    Box {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showMoreMenu = true
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(Color(0xFF1E1E20)),
                        ) {
                            val canRevertOriginal = state.hasOriginalBackup || state.edit.hasChanges
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Revert to original",
                                        color = if (canRevertOriginal) Color.White else Color(0xFF6E6E73),
                                    )
                                },
                                enabled = canRevertOriginal,
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.revertToOriginal {
                                        Toast.makeText(context, "Original photo restored", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Restore,
                                        null,
                                        tint = if (canRevertOriginal) Color.White else Color(0xFF6E6E73),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Format and other options", color = Color.White) },
                                onClick = {
                                    showMoreMenu = false
                                    showAdvancedFormatDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Tune, null, tint = Color.White)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.edit.autoEnhanced) "Disable Auto enhance" else "Auto enhance",
                                        color = Color.White,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.toggleAutoEnhance()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.AutoFixHigh, null, tint = OneUiAccent)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        if (state.isLoading && state.item == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OneUiAccent)
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.item?.isVideo == true) {
                VideoEditorPreview(state.item!!.uri.toString(), Modifier.weight(1f).fillMaxWidth())
                Surface(tonalElevation = 3.dp) {
                    VideoEditControls(state.videoEdit, state.item?.duration ?: 0L, viewModel::updateVideo, Modifier.padding(16.dp))
                }
            } else {
                // Interactive Image Preview Canvas
                ImagePreview(
                    uri = state.item?.uri,
                    edit = if (isComparingOriginal) ImageEditState() else state.edit,
                    isComparingOriginal = isComparingOriginal,
                    sourceAspect = state.item?.let { item ->
                        if ((state.edit.rotationDegrees / 90) % 2 != 0) item.height.toFloat() / item.width else item.width.toFloat() / item.height
                    } ?: 1f,
                    interactiveBrush = !isComparingOriginal && !isEyedropperActive && (selectedTab == EditorTab.DECORATIONS && selectedDecoSubTool == DecorationSubTool.DRAW || selectedTab == EditorTab.AI_TOOLS && selectedAiSubTool == AiSubTool.OBJECT_ERASER),
                    isEraserMode = selectedTab == EditorTab.AI_TOOLS && selectedAiSubTool == AiSubTool.OBJECT_ERASER,
                    eraserBrushWidth = brushWidth,
                    penType = selectedPenType,
                    brushOpacity = brushOpacity,
                    isEyedropperActive = isEyedropperActive,
                    onColorPicked = {
                        brushColor = it
                        isEyedropperActive = false
                    },
                    draftStroke = draftStroke,
                    selectedTextIndex = selectedTextIndex,
                    selectedStickerIndex = selectedStickerIndex,
                    onSelectText = { selectedTextIndex = it; selectedStickerIndex = -1 },
                    onSelectSticker = { selectedStickerIndex = it; selectedTextIndex = -1 },
                    onDraftChange = { draftStroke = it },
                    onStrokeComplete = { points ->
                        val stroke = MarkupStroke(points, brushColor, brushWidth, selectedPenType, brushOpacity)
                        viewModel.update { edit ->
                            if (selectedTab == EditorTab.AI_TOOLS && selectedAiSubTool == AiSubTool.OBJECT_ERASER) {
                                edit.copy(eraseStrokes = edit.eraseStrokes + stroke)
                            } else {
                                edit.copy(markup = edit.markup + stroke)
                            }
                        }
                    },
                    showCrop = !isComparingOriginal && selectedTab == EditorTab.TRANSFORM && transformSubMode != 2,
                    onCropChange = { crop -> viewModel.update { it.copy(crop = crop) } },
                    showDocumentScan = !isComparingOriginal && selectedTab == EditorTab.TRANSFORM && transformSubMode == 2,
                    documentCorners = state.edit.documentCorners ?: draftDocCorners,
                    onDocumentCornersChange = { updated ->
                        draftDocCorners = updated
                        viewModel.update { it.copy(documentCorners = updated) }
                    },
                    onOverlayMoved = { target, position ->
                        viewModel.update { current ->
                            when (target) {
                                is OverlayTarget.Text -> current.copy(
                                    textOverlays = current.textOverlays.mapIndexed { index, overlay ->
                                        if (index == target.index) overlay.copy(position = position) else overlay
                                    },
                                )
                                is OverlayTarget.Sticker -> current.copy(
                                    stickers = current.stickers.mapIndexed { index, overlay ->
                                        if (index == target.index) overlay.copy(position = position) else overlay
                                    },
                                )
                            }
                        }
                    },
                    onUpdateText = { index, transform ->
                        viewModel.update { current ->
                            current.copy(
                                textOverlays = current.textOverlays.mapIndexed { i, ov ->
                                    if (i == index) transform(ov) else ov
                                }
                            )
                        }
                    },
                    onDeleteText = { index ->
                        selectedTextIndex = -1
                        viewModel.update { current ->
                            current.copy(
                                textOverlays = current.textOverlays.filterIndexed { i, _ -> i != index }
                            )
                        }
                    },
                    onUpdateSticker = { index, transform ->
                        viewModel.update { current ->
                            current.copy(
                                stickers = current.stickers.mapIndexed { i, ov ->
                                    if (i == index) transform(ov) else ov
                                }
                            )
                        }
                    },
                    onDeleteSticker = { index ->
                        selectedStickerIndex = -1
                        viewModel.update { current ->
                            current.copy(
                                stickers = current.stickers.filterIndexed { i, _ -> i != index }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }

                // Bottom Tool Adjustment Area
                Surface(color = Color(0xFF0C0C0E)) {
                    Column {
                        HorizontalDivider(color = Color(0xFF1E1E22), thickness = 0.8.dp)

                        SamsungToolControls(
                            tab = selectedTab,
                            previewUri = state.item?.uri,
                            edit = state.edit,
                            transformSubMode = transformSubMode,
                            onTransformSubModeChange = { transformSubMode = it },
                            draftDocCorners = state.edit.documentCorners ?: draftDocCorners,
                            onAutoDetectDocCorners = {
                                val uri = state.item?.uri
                                if (uri != null) {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val bmp = runCatching {
                                            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                                            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                                decoder.setTargetSampleSize(4)
                                                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                            }
                                        }.getOrNull()
                                        val detected = if (bmp != null) {
                                            val corners = DocumentDewarper.detectDocumentCorners(bmp)
                                            bmp.recycle()
                                            corners
                                        } else {
                                            DocumentCorners(
                                                NormalizedPoint(0.06f, 0.06f),
                                                NormalizedPoint(0.94f, 0.06f),
                                                NormalizedPoint(0.94f, 0.94f),
                                                NormalizedPoint(0.06f, 0.94f),
                                            )
                                        }
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            draftDocCorners = detected
                                            viewModel.update { it.copy(documentCorners = detected) }
                                        }
                                    }
                                }
                            },
                            onResetDocCorners = {
                                val defaultCorners = DocumentCorners()
                                draftDocCorners = defaultCorners
                                viewModel.update { it.copy(documentCorners = null) }
                            },
                            decoSubTool = selectedDecoSubTool,
                            aiSubTool = selectedAiSubTool,
                            penType = selectedPenType,
                            brushColor = brushColor,
                            brushWidth = brushWidth,
                            brushOpacity = brushOpacity,
                            isEyedropperActive = isEyedropperActive,
                            onDecoSubToolChange = { selectedDecoSubTool = it },
                            onAiSubToolChange = { selectedAiSubTool = it },
                            onPenTypeChange = { selectedPenType = it },
                            onBrushColor = { brushColor = it },
                            onBrushWidth = { brushWidth = it },
                            onBrushOpacity = { brushOpacity = it },
                            onToggleEyedropper = { isEyedropperActive = !isEyedropperActive },
                            onAddText = { showTextDialog = true },
                            onToggleAutoEnhance = viewModel::toggleAutoEnhance,
                            onUpdate = viewModel::update,
                            onOpenGenerativeEdit = {
                                val parsedUri = state.item?.uri ?: return@SamsungToolControls
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                        android.graphics.ImageDecoder.decodeBitmap(
                                            android.graphics.ImageDecoder.createSource(context.contentResolver, parsedUri)
                                        ) { dec, _, _ ->
                                            dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                        }
                                    } else {
                                        @Suppress("DEPRECATION")
                                        android.graphics.BitmapFactory.decodeStream(
                                            context.contentResolver.openInputStream(parsedUri)
                                        )
                                    }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        sourceBitmapForGenerative = bmp
                                        showGenerativeEditDialog = true
                                    }
                                }
                            },
                        )

                        // Samsung One UI 5-Category Bottom Navigation Bar
                        SamsungEditorTabBar(
                            selected = selectedTab,
                            onSelect = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedTab = it
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAdvancedFormatDialog) {
        SamsungAdvancedFormatDialog(
            initialFormat = state.edit.exportFormat,
            initialResolutionPercent = state.edit.exportResolutionPercent,
            initialStripLocation = state.edit.stripLocationData,
            onDismiss = { showAdvancedFormatDialog = false },
            onConfirm = { format, res, stripLoc ->
                showAdvancedFormatDialog = false
                viewModel.update {
                    it.copy(
                        exportFormat = format,
                        exportResolutionPercent = res,
                        stripLocationData = stripLoc,
                    )
                }
            },
        )
    }

    if (showTextDialog) {
        AddTextStylingDialog(
            initialColor = brushColor,
            onDismiss = { showTextDialog = false },
            onAdd = { text, color, bgStyle, fontStyle ->
                viewModel.update {
                    it.copy(
                        textOverlays = it.textOverlays + TextOverlay(
                            text = text,
                            color = color,
                            backgroundStyle = bgStyle,
                            fontStyle = fontStyle,
                        )
                    )
                }
                showTextDialog = false
            },
        )
    }

    if (showDiscardConfirm) {
        PremiumAlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) {
                    Text(stringResource(R.string.editor_discard_confirm), color = Color(0xFFFF453A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.editor_keep_editing))
                }
            },
        )
    }

    if (showGenerativeEditDialog && sourceBitmapForGenerative != null) {
        SamsungGenerativeEditDialog(
            sourceBitmap = sourceBitmapForGenerative!!,
            onDismiss = {
                showGenerativeEditDialog = false
                sourceBitmapForGenerative = null
            },
            onSaveToVault = { generatedBitmap ->
                showGenerativeEditDialog = false
                sourceBitmapForGenerative = null
                viewModel.saveGenerativeCopy(
                    bitmap = generatedBitmap,
                    saveToVault = true,
                    onSaved = onSaved,
                )
            },
            onSaveAsCopy = { generatedBitmap ->
                showGenerativeEditDialog = false
                sourceBitmapForGenerative = null
                viewModel.saveGenerativeCopy(
                    bitmap = generatedBitmap,
                    saveToVault = false,
                    onSaved = onSaved,
                )
            },
        )
    }
}

private fun samplePixelFromUri(context: android.content.Context, uri: Any?, normX: Float, normY: Float): Long? {
    return try {
        val parsedUri = when (uri) {
            is android.net.Uri -> uri
            is String -> android.net.Uri.parse(uri)
            else -> null
        } ?: return null

        val inputStream = context.contentResolver.openInputStream(parsedUri) ?: return null
        val decoder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.graphics.BitmapRegionDecoder.newInstance(inputStream)
        } else {
            @Suppress("DEPRECATION")
            android.graphics.BitmapRegionDecoder.newInstance(inputStream, false)
        }
        if (decoder != null) {
            val w = decoder.width
            val h = decoder.height
            val px = (normX * w).toInt().coerceIn(0, (w - 1).coerceAtLeast(0))
            val py = (normY * h).toInt().coerceIn(0, (h - 1).coerceAtLeast(0))
            val rect = android.graphics.Rect(px, py, px + 1, py + 1)
            val bmp = decoder.decodeRegion(rect, android.graphics.BitmapFactory.Options())
            val color = bmp?.getPixel(0, 0)
            bmp?.recycle()
            inputStream.close()
            color?.let { (it.toLong() and 0xFFFFFFFFL) }
        } else {
            inputStream.close()
            null
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun ImagePreview(
    uri: Any?,
    edit: ImageEditState,
    isComparingOriginal: Boolean,
    sourceAspect: Float,
    interactiveBrush: Boolean,
    penType: PenType,
    brushOpacity: Float,
    isEraserMode: Boolean = false,
    eraserBrushWidth: Float = 0.015f,
    isEyedropperActive: Boolean = false,
    onColorPicked: (Long) -> Unit = {},
    draftStroke: List<NormalizedPoint>,
    selectedTextIndex: Int,
    selectedStickerIndex: Int,
    onSelectText: (Int) -> Unit,
    onSelectSticker: (Int) -> Unit,
    onDraftChange: (List<NormalizedPoint>) -> Unit,
    onStrokeComplete: (List<NormalizedPoint>) -> Unit,
    showCrop: Boolean,
    onCropChange: (NormalizedCrop) -> Unit,
    showDocumentScan: Boolean = false,
    documentCorners: DocumentCorners? = null,
    onDocumentCornersChange: (DocumentCorners) -> Unit = {},
    onOverlayMoved: (OverlayTarget, NormalizedPoint) -> Unit,
    onUpdateText: (Int, (TextOverlay) -> TextOverlay) -> Unit,
    onDeleteText: (Int) -> Unit,
    onUpdateSticker: (Int, (StickerOverlay) -> StickerOverlay) -> Unit,
    onDeleteSticker: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var hasSnapped by remember { mutableStateOf(false) }
    var previewBoxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier.background(Color.Black).padding(8.dp), contentAlignment = Alignment.Center) {
        val aspectModifier = when (edit.cropAspect) {
            CropAspect.SQUARE -> Modifier.aspectRatio(1f)
            CropAspect.FOUR_THREE -> Modifier.aspectRatio(4f / 3f)
            CropAspect.THREE_FOUR -> Modifier.aspectRatio(3f / 4f)
            CropAspect.SIXTEEN_NINE -> Modifier.aspectRatio(16f / 9f)
            CropAspect.NINE_SIXTEEN -> Modifier.aspectRatio(9f / 16f)
            CropAspect.TWO_THREE -> Modifier.aspectRatio(2f / 3f)
            CropAspect.THREE_TWO -> Modifier.aspectRatio(3f / 2f)
            CropAspect.ORIGINAL, CropAspect.FREE, CropAspect.FULL -> Modifier.aspectRatio(sourceAspect.coerceIn(0.25f, 4f))
        }

        val context = LocalContext.current
        val mainImageRequest = remember(uri) {
            ImageRequest.Builder(context)
                .data(uri)
                .placeholderMemoryCacheKey("${uri}_thumb")
                .crossfade(true)
                .build()
        }

        Box(
            aspectModifier
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { previewBoxSize = it }
                .pointerInput(interactiveBrush, isEyedropperActive) {
                    if (!interactiveBrush && !isEyedropperActive) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                            panOffset = if (zoom > 1f) panOffset + pan else Offset.Zero
                        }
                    }
                }
                .pointerInput(interactiveBrush, isEyedropperActive) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!isEyedropperActive) {
                                zoom = 1f
                                panOffset = Offset.Zero
                            }
                        },
                        onTap = { offset ->
                            if (isEyedropperActive && previewBoxSize.width > 0 && previewBoxSize.height > 0) {
                                val normX = (offset.x / previewBoxSize.width).coerceIn(0f, 1f)
                                val normY = (offset.y / previewBoxSize.height).coerceIn(0f, 1f)
                                val sampled = samplePixelFromUri(context, uri, normX, normY)
                                if (sampled != null) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onColorPicked(sampled)
                                }
                            } else {
                                onSelectText(-1)
                                onSelectSticker(-1)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = mainImageRequest,
                contentDescription = "Edited image preview",
                contentScale = if (edit.cropAspect == CropAspect.ORIGINAL || edit.cropAspect == CropAspect.FREE || edit.cropAspect == CropAspect.FULL) ContentScale.Fit else ContentScale.Crop,
                colorFilter = if (isComparingOriginal) null else previewColorFilter(edit),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    if (!isComparingOriginal) {
                        val straightenScale = calculateStraightenScale(edit.straightenDegrees, sourceAspect)
                        rotationZ = edit.rotationDegrees + edit.straightenDegrees
                        rotationX = -edit.perspectiveVertical
                        rotationY = edit.perspectiveHorizontal
                        cameraDistance = 12f * density
                        scaleX = (if (edit.flipHorizontal) -1f else 1f) * straightenScale * zoom
                        scaleY = (if (edit.flipVertical) -1f else 1f) * straightenScale * zoom
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
                },
            )

            // Canvas for Crop Grid & Drawing Strokes & Area Mosaics & Vignette
            Canvas(
                Modifier.fillMaxSize().then(
                    if (interactiveBrush) Modifier.pointerInput(interactiveBrush, penType) {
                        detectDragGestures(
                            onDragStart = { p ->
                                dragStartTime = System.currentTimeMillis()
                                hasSnapped = false
                                onDraftChange(listOf(NormalizedPoint(p.x / size.width, p.y / size.height)))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val pt = NormalizedPoint(change.position.x / size.width, change.position.y / size.height)
                                val currentPoints = draftStroke + pt
                                onDraftChange(currentPoints)

                                val elapsed = System.currentTimeMillis() - dragStartTime
                                if (currentPoints.size >= 8 && !hasSnapped && elapsed > 380) {
                                    val snapped = ShapeDetection.detectAndSnap(currentPoints)
                                    if (snapped != null) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        hasSnapped = true
                                        onDraftChange(snapped)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (draftStroke.size > 1) onStrokeComplete(draftStroke)
                                onDraftChange(emptyList())
                                hasSnapped = false
                            },
                            onDragCancel = {
                                onDraftChange(emptyList())
                                hasSnapped = false
                            },
                        )
                    } else Modifier
                ),
            ) {
                // Rule-of-thirds grid if crop aspect is fixed
                if (!isComparingOriginal && edit.cropAspect != CropAspect.ORIGINAL && edit.cropAspect != CropAspect.FULL && !showCrop) {
                    val grid = Color.White.copy(alpha = 0.45f)
                    drawLine(grid, start = Offset(size.width / 3f, 0f), end = Offset(size.width / 3f, size.height), strokeWidth = 1f)
                    drawLine(grid, start = Offset(size.width * 2f / 3f, 0f), end = Offset(size.width * 2f / 3f, size.height), strokeWidth = 1f)
                    drawLine(grid, start = Offset(0f, size.height / 3f), end = Offset(size.width, size.height / 3f), strokeWidth = 1f)
                    drawLine(grid, start = Offset(0f, size.height * 2f / 3f), end = Offset(size.width, size.height * 2f / 3f), strokeWidth = 1f)
                }

                if (!isComparingOriginal) {
                    // Vignette live preview
                    if (edit.vignette > 0f) {
                        val radius = size.maxDimension * 0.72f
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = (0.85f * edit.vignette).coerceIn(0f, 1f))),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = radius,
                            ),
                            size = size,
                        )
                    }

                    // Area Mosaics (Privacy Blur / Pixelation) live preview
                    edit.areaMosaics.forEach { mosaic ->
                        val mLeft = mosaic.rect.left * size.width
                        val mTop = mosaic.rect.top * size.height
                        val mRight = mosaic.rect.right * size.width
                        val mBottom = mosaic.rect.bottom * size.height
                        val mSize = Size(mRight - mLeft, mBottom - mTop)
                        val shade = Color(0xDD3A3A3C)
                        when (mosaic.shape) {
                            CropShape.RECTANGLE -> drawRect(shade, topLeft = Offset(mLeft, mTop), size = mSize)
                            CropShape.OVAL -> drawOval(shade, topLeft = Offset(mLeft, mTop), size = mSize)
                            CropShape.ROUNDED_RECT -> drawRoundRect(shade, topLeft = Offset(mLeft, mTop), size = mSize, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                        }
                    }

                    val allStrokes = edit.markup + if (interactiveBrush && draftStroke.isNotEmpty() && !isEraserMode) {
                        listOf(MarkupStroke(draftStroke, penType = penType, opacity = brushOpacity))
                    } else emptyList()

                    allStrokes.forEach { stroke ->
                        drawComposeStroke(stroke, size.width, size.height)
                    }

                    val allEraseStrokes = edit.eraseStrokes + if (interactiveBrush && draftStroke.isNotEmpty() && isEraserMode) {
                        listOf(MarkupStroke(draftStroke, widthFraction = eraserBrushWidth))
                    } else emptyList()

                    allEraseStrokes.forEach { stroke ->
                        // Samsung One UI glowing magenta outer boundary
                        drawComposeStroke(
                            stroke.copy(color = 0x88FF2D55, widthFraction = stroke.widthFraction * 1.35f),
                            size.width,
                            size.height,
                        )
                        // Inner core highlight
                        drawComposeStroke(
                            stroke.copy(color = 0xCCFFFFFF, widthFraction = stroke.widthFraction * 0.85f),
                            size.width,
                            size.height,
                        )
                    }
                }
            }

            // Interactive Text and Sticker Overlays with Samsung Bounding Box
            if (!isComparingOriginal) {
                edit.textOverlays.forEachIndexed { index, overlay ->
                    SamsungOverlayBox(
                        position = overlay.position,
                        rotationDegrees = overlay.rotationDegrees,
                        scale = overlay.scale,
                        isSelected = index == selectedTextIndex,
                        onSelect = { onSelectText(index) },
                        onMove = { moved -> onOverlayMoved(OverlayTarget.Text(index), moved) },
                        onRotate = { rot -> onUpdateText(index) { it.copy(rotationDegrees = rot) } },
                        onScale = { sc -> onUpdateText(index) { it.copy(scale = sc) } },
                        onDelete = { onDeleteText(index) },
                    ) {
                        val fontFamily = when (overlay.fontStyle) {
                            TextStyleFont.SERIF -> FontFamily.Serif
                            TextStyleFont.MONO -> FontFamily.Monospace
                            TextStyleFont.CURSIVE -> FontFamily.Cursive
                            TextStyleFont.DEFAULT -> FontFamily.Default
                        }
                        Surface(
                            shape = when (overlay.backgroundStyle) {
                                TextBackgroundStyle.CAPSULE -> RoundedCornerShape(50)
                                TextBackgroundStyle.SOLID -> RoundedCornerShape(8.dp)
                                TextBackgroundStyle.OUTLINE -> RoundedCornerShape(8.dp)
                                TextBackgroundStyle.NONE -> RoundedCornerShape(0.dp)
                            },
                            color = when (overlay.backgroundStyle) {
                                TextBackgroundStyle.NONE, TextBackgroundStyle.OUTLINE -> Color.Transparent
                                TextBackgroundStyle.SOLID, TextBackgroundStyle.CAPSULE -> Color.Black.copy(alpha = 0.75f)
                            },
                            border = if (overlay.backgroundStyle == TextBackgroundStyle.OUTLINE) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, Color(overlay.color))
                            } else null,
                            modifier = Modifier.padding(4.dp),
                        ) {
                            Text(
                                text = overlay.text,
                                color = Color(overlay.color),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = fontFamily,
                                ),
                                textAlign = when (overlay.textAlign.lowercase()) {
                                    "left" -> TextAlign.Left
                                    "right" -> TextAlign.Right
                                    else -> TextAlign.Center
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                edit.stickers.forEachIndexed { index, overlay ->
                    SamsungOverlayBox(
                        position = overlay.position,
                        rotationDegrees = overlay.rotationDegrees,
                        scale = overlay.scale,
                        isSelected = index == selectedStickerIndex,
                        onSelect = { onSelectSticker(index) },
                        onMove = { moved -> onOverlayMoved(OverlayTarget.Sticker(index), moved) },
                        onRotate = { rot -> onUpdateSticker(index) { it.copy(rotationDegrees = rot) } },
                        onScale = { sc -> onUpdateSticker(index) { it.copy(scale = sc) } },
                        onFlip = { onUpdateSticker(index) { it.copy(flipHorizontal = !it.flipHorizontal) } },
                        onDelete = { onDeleteSticker(index) },
                    ) {
                        Text(
                            text = overlay.symbol,
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier.graphicsLayer {
                                if (overlay.flipHorizontal) scaleX = -1f
                            },
                        )
                    }
                }

                if (showDocumentScan) {
                    DocumentScanOverlay(
                        corners = documentCorners ?: DocumentCorners(),
                        onCornersChange = onDocumentCornersChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (showCrop) {
                    SamsungCropOverlay(
                        crop = edit.crop,
                        cropAspect = edit.cropAspect,
                        cropShape = edit.cropShape,
                        sourceAspect = sourceAspect,
                        onCommit = onCropChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // "Original" Comparison Floating Badge
            if (isComparingOriginal) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            text = stringResource(R.string.editor_original_badge),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawComposeStroke(stroke: MarkupStroke, width: Float, height: Float) {
    if (stroke.points.size < 2) return
    val path = Path().apply {
        moveTo(stroke.points.first().x * width, stroke.points.first().y * height)
        stroke.points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
    }
    val baseWidth = size.minDimension * stroke.widthFraction
    val strokeAlpha = stroke.opacity.coerceIn(0.05f, 1f)
    when (stroke.penType) {
        PenType.REGULAR -> {
            drawPath(path, Color(stroke.color).copy(alpha = strokeAlpha), style = Stroke(width = baseWidth))
        }
        PenType.HIGHLIGHTER -> {
            drawPath(path, Color(stroke.color).copy(alpha = 0.45f * strokeAlpha), style = Stroke(width = baseWidth * 2.2f))
        }
        PenType.NEON -> {
            drawPath(path, Color(stroke.color).copy(alpha = 0.6f * strokeAlpha), style = Stroke(width = baseWidth * 2.5f))
            drawPath(path, Color.White.copy(alpha = strokeAlpha), style = Stroke(width = baseWidth * 0.8f))
        }
        PenType.CALLIGRAPHY -> {
            drawPath(path, Color(stroke.color).copy(alpha = strokeAlpha), style = Stroke(width = baseWidth * 1.5f))
        }
        PenType.PENCIL -> {
            drawPath(path, Color(stroke.color).copy(alpha = 0.85f * strokeAlpha), style = Stroke(width = baseWidth * 0.8f))
        }
        PenType.MOSAIC_PIXEL -> {
            drawPath(path, Color.Gray.copy(alpha = 0.65f * strokeAlpha), style = Stroke(width = baseWidth * 3.5f))
        }
        PenType.MOSAIC_BLUR -> {
            drawPath(path, Color.LightGray.copy(alpha = 0.5f * strokeAlpha), style = Stroke(width = baseWidth * 4f))
        }
    }
}

@Composable
private fun BoxScope.DraggableOverlay(
    position: NormalizedPoint,
    enabled: Boolean,
    onMove: (NormalizedPoint) -> Unit,
    content: @Composable () -> Unit,
) {
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { boxSize = it },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        (position.x * boxSize.width).toInt(),
                        (position.y * boxSize.height).toInt(),
                    )
                }
                .then(
                    if (!enabled) Modifier else Modifier.pointerInput(boxSize) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (boxSize.width == 0 || boxSize.height == 0) return@detectDragGestures
                            onMove(
                                NormalizedPoint(
                                    x = (position.x + dragAmount.x / boxSize.width).coerceIn(0.05f, 0.95f),
                                    y = (position.y + dragAmount.y / boxSize.height).coerceIn(0.05f, 0.95f),
                                ),
                            )
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

private sealed interface OverlayTarget {
    data class Text(val index: Int) : OverlayTarget
    data class Sticker(val index: Int) : OverlayTarget
}

@Composable
private fun SamsungToolControls(
    tab: EditorTab,
    previewUri: Any?,
    edit: ImageEditState,
    transformSubMode: Int = 0,
    onTransformSubModeChange: (Int) -> Unit = {},
    draftDocCorners: DocumentCorners = DocumentCorners(),
    onAutoDetectDocCorners: () -> Unit = {},
    onResetDocCorners: () -> Unit = {},
    decoSubTool: DecorationSubTool,
    aiSubTool: AiSubTool,
    penType: PenType,
    brushColor: Long,
    brushWidth: Float,
    brushOpacity: Float,
    isEyedropperActive: Boolean,
    onDecoSubToolChange: (DecorationSubTool) -> Unit,
    onAiSubToolChange: (AiSubTool) -> Unit,
    onPenTypeChange: (PenType) -> Unit,
    onBrushColor: (Long) -> Unit,
    onBrushWidth: (Float) -> Unit,
    onBrushOpacity: (Float) -> Unit,
    onToggleEyedropper: () -> Unit,
    onAddText: () -> Unit,
    onToggleAutoEnhance: () -> Unit,
    onUpdate: ((ImageEditState) -> ImageEditState) -> Unit,
    onOpenGenerativeEdit: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when (tab) {
            EditorTab.TRANSFORM -> {
                // Quick Transform Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    EditorAction(Icons.AutoMirrored.Outlined.RotateRight, stringResource(R.string.editor_rotate)) {
                        onUpdate { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
                    }
                    EditorAction(Icons.Outlined.Flip, stringResource(R.string.editor_flip_h), isActive = edit.flipHorizontal) {
                        onUpdate { it.copy(flipHorizontal = !it.flipHorizontal) }
                    }
                    EditorAction(Icons.Outlined.Flip, stringResource(R.string.editor_flip_v), isActive = edit.flipVertical) {
                        onUpdate { it.copy(flipVertical = !it.flipVertical) }
                    }
                    if (edit.rotationDegrees != 0 || edit.straightenDegrees != 0f || edit.flipHorizontal || edit.flipVertical || edit.cropAspect != CropAspect.ORIGINAL) {
                        EditorAction(Icons.Outlined.RestartAlt, stringResource(R.string.editor_reset)) {
                            onUpdate {
                                it.copy(
                                    rotationDegrees = 0,
                                    straightenDegrees = 0f,
                                    flipHorizontal = false,
                                    flipVertical = false,
                                    cropAspect = CropAspect.ORIGINAL,
                                    crop = NormalizedCrop(),
                                )
                            }
                        }
                    }
                }

                // Sub-mode switch: Straighten vs Perspective vs Document Scan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = OneUiCardBg,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, OneUiBorderColor),
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (transformSubMode == 0) OneUiAccent else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onTransformSubModeChange(0) },
                            ) {
                                Text(
                                    text = "Straighten",
                                    color = if (transformSubMode == 0) Color.White else Color(0xFFC7C7CC),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = if (transformSubMode == 0) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (transformSubMode == 1) OneUiAccent else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onTransformSubModeChange(1) },
                            ) {
                                Text(
                                    text = "Perspective",
                                    color = if (transformSubMode == 1) Color.White else Color(0xFFC7C7CC),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = if (transformSubMode == 1) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (transformSubMode == 2) OneUiAccent else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onTransformSubModeChange(2) },
                            ) {
                                Text(
                                    text = "📄 Scan",
                                    color = if (transformSubMode == 2) Color.White else Color(0xFFC7C7CC),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = if (transformSubMode == 2) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }

                if (transformSubMode == 0) {
                    // Samsung Straighten Ruler Dial Slider (-45° to +45°)
                    SamsungRulerDialSlider(
                        angleDegrees = edit.straightenDegrees,
                        onAngleChange = { v -> onUpdate { it.copy(straightenDegrees = v) } },
                    )
                } else if (transformSubMode == 1) {
                    // Samsung Perspective Keystone Sliders (-30° to +30°)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        SamsungOneUiSlider(
                            label = "Vertical Perspective",
                            value = edit.perspectiveVertical,
                            range = -30f..30f,
                            icon = Icons.Outlined.Transform,
                            valueFormatter = { v ->
                                val intVal = v.roundToInt()
                                if (intVal > 0) "+$intVal°" else "$intVal°"
                            },
                            onValueChange = { v -> onUpdate { it.copy(perspectiveVertical = v) } },
                            onReset = { onUpdate { it.copy(perspectiveVertical = 0f) } },
                        )
                        SamsungOneUiSlider(
                            label = "Horizontal Perspective",
                            value = edit.perspectiveHorizontal,
                            range = -30f..30f,
                            icon = Icons.Outlined.Transform,
                            valueFormatter = { v ->
                                val intVal = v.roundToInt()
                                if (intVal > 0) "+$intVal°" else "$intVal°"
                            },
                            onValueChange = { v -> onUpdate { it.copy(perspectiveHorizontal = v) } },
                            onReset = { onUpdate { it.copy(perspectiveHorizontal = 0f) } },
                        )
                    }
                } else {
                    // Samsung One UI Document Scan Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Action row: Auto-Detect, Dewarp & Flatten, Reset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = OneUiCardBg,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, OneUiAccent.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onAutoDetectDocCorners() },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoFixHigh,
                                        contentDescription = "Auto-Detect",
                                        tint = OneUiAccent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Auto-Detect",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp,
                                        ),
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (edit.documentCorners != null) OneUiAccent else OneUiCardBg,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (edit.documentCorners != null) OneUiAccent else OneUiBorderColor),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        onUpdate { it.copy(documentCorners = draftDocCorners) }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DocumentScanner,
                                        contentDescription = "Dewarp & Flatten",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = if (edit.documentCorners != null) "Dewarped ✓" else "Dewarp & Flatten",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp,
                                        ),
                                    )
                                }
                            }

                            if (edit.documentCorners != null) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color(0x33FF3B30),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFF3B30).copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { onResetDocCorners() },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.RestartAlt,
                                            contentDescription = "Reset Scan",
                                            tint = Color(0xFFFF3B30),
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Text(
                                            text = "Reset",
                                            color = Color(0xFFFF3B30),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.5.sp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        // Enhancement filter mode selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            DocumentEnhancementMode.entries.forEach { mode ->
                                val isSelected = edit.documentEnhancement == mode
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) OneUiAccent else OneUiCardBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.8.dp,
                                        if (isSelected) OneUiAccent else OneUiBorderColor,
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onUpdate { it.copy(documentEnhancement = mode) } },
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp,
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Aspect Ratio Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CropAspect.entries.forEach { aspect ->
                        val isSelected = aspect == edit.cropAspect
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) OneUiAccent else OneUiCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = if (isSelected) OneUiAccent else OneUiBorderColor,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onUpdate { it.copy(cropAspect = aspect) } },
                        ) {
                            Text(
                                text = aspect.label,
                                color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // Crop Shape Selector Chips (Rectangle, Rounded, Oval)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    CropShape.entries.forEach { shape ->
                        val isSelected = shape == edit.cropShape
                        val shapeLabel = when (shape) {
                            CropShape.RECTANGLE -> "Rectangle"
                            CropShape.ROUNDED_RECT -> "Rounded"
                            CropShape.OVAL -> "Oval"
                        }
                        // Same selected treatment as the sub-mode and aspect chips directly
                        // above: a solid accent fill with white text. This row used a 30%
                        // accent wash with accent-coloured text, so one panel showed two
                        // different ideas of what "selected" looks like.
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) OneUiAccent else OneUiCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = if (isSelected) OneUiAccent else OneUiBorderColor,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onUpdate { it.copy(cropShape = shape) } },
                        ) {
                            Text(
                                text = shapeLabel,
                                color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            EditorTab.FILTERS -> {
                // Filter Intensity One UI Slider
                SamsungOneUiSlider(
                    label = stringResource(R.string.editor_filter_intensity),
                    value = edit.filterIntensity,
                    range = 0f..1f,
                    icon = Icons.Outlined.Tune,
                    onValueChange = { v -> onUpdate { it.copy(filterIntensity = v) } },
                    onReset = { onUpdate { it.copy(filterIntensity = 1.0f) } },
                )

                Spacer(Modifier.height(6.dp))

                // Samsung One UI Filters Horizontal Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ImageFilter.entries.forEach { filter ->
                        val isSelected = filter == edit.filter
                        val cardShape = RoundedCornerShape(16.dp)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(cardShape)
                                .clickable { onUpdate { it.copy(filter = filter) } }
                                .padding(2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(cardShape)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.8.dp,
                                        color = if (isSelected) OneUiAccent else OneUiBorderColor,
                                        shape = cardShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                val context = LocalContext.current
                                val filterRequest = remember(previewUri) {
                                    ImageRequest.Builder(context)
                                        .data(previewUri)
                                        .size(128)
                                        .crossfade(false)
                                        .memoryCacheKey("${previewUri}_thumb")
                                        .placeholderMemoryCacheKey("${previewUri}_thumb")
                                        .build()
                                }
                                AsyncImage(
                                    model = filterRequest,
                                    contentDescription = filter.displayName,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = previewColorFilter(edit.copy(filter = filter, filterIntensity = 1f)),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = filter.displayName,
                                color = if (isSelected) OneUiAccent else Color(0xFF8E8E93),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }

            EditorTab.TONE -> {
                SamsungTonePanel(edit = edit, onToggleAutoEnhance = onToggleAutoEnhance, onUpdate = onUpdate)
            }

            EditorTab.DECORATIONS -> {
                SamsungDecorationsPanel(
                    decoSubTool = decoSubTool,
                    penType = penType,
                    brushColor = brushColor,
                    brushWidth = brushWidth,
                    brushOpacity = brushOpacity,
                    isEyedropperActive = isEyedropperActive,
                    edit = edit,
                    onDecoSubToolChange = onDecoSubToolChange,
                    onPenTypeChange = onPenTypeChange,
                    onBrushColor = onBrushColor,
                    onBrushWidth = onBrushWidth,
                    onBrushOpacity = onBrushOpacity,
                    onToggleEyedropper = onToggleEyedropper,
                    onAddText = onAddText,
                    onUpdate = onUpdate,
                )
            }

            EditorTab.AI_TOOLS -> {
                SamsungAiToolsPanel(
                    aiSubTool = aiSubTool,
                    brushWidth = brushWidth,
                    edit = edit,
                    onAiSubToolChange = onAiSubToolChange,
                    onBrushWidth = onBrushWidth,
                    onUpdate = onUpdate,
                    onOpenGenerativeEdit = onOpenGenerativeEdit,
                )
            }
        }
    }
}

@Composable
private fun SamsungTonePanel(
    edit: ImageEditState,
    onToggleAutoEnhance: () -> Unit,
    onUpdate: ((ImageEditState) -> ImageEditState) -> Unit,
) {
    var toneSubMode by rememberSaveable { mutableIntStateOf(0) } // 0: Sliders, 1: Curves, 2: HSL
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedHslChannel by rememberSaveable { mutableStateOf(HslColorChannel.RED) }
    var selectedCurveChannel by rememberSaveable { mutableStateOf(CurveChannel.MASTER) }

    // Sub-mode switch: Sliders vs Curves vs HSL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = OneUiCardBg,
            border = androidx.compose.foundation.BorderStroke(0.8.dp, OneUiBorderColor),
        ) {
            Row(modifier = Modifier.padding(2.dp)) {
                listOf("Tone", "Curves", "HSL").forEachIndexed { index, label ->
                    val isSelected = toneSubMode == index
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) OneUiAccent else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { toneSubMode = index },
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    when (toneSubMode) {
        0 -> {
            // TONE SLIDERS SUB-PANEL
            val toneOptions = listOf(
                ToneOption("Brightness", edit.brightness, Icons.Outlined.LightMode, -1f..1f),
                ToneOption("Light Balance", edit.lightBalance, Icons.Outlined.Balance, -1f..1f),
                ToneOption("Exposure", edit.exposure, Icons.Outlined.WbSunny, -1f..1f),
                ToneOption("Contrast", edit.contrast, Icons.Outlined.Contrast, -1f..1f),
                ToneOption("Highlights", edit.highlights, Icons.Outlined.Highlight, -1f..1f),
                ToneOption("Shadows", edit.shadows, Icons.Outlined.DarkMode, -1f..1f),
                ToneOption("Saturation", edit.saturation, Icons.Outlined.ColorLens, -1f..1f),
                ToneOption("Warmth", edit.warmth, Icons.Outlined.Thermostat, -1f..1f),
                ToneOption("Tint", edit.tint, Icons.Outlined.Colorize, -1f..1f),
                ToneOption("Sharpness", edit.sharpness, Icons.Outlined.Details, 0f..1f),
                ToneOption("Definition", edit.definition, Icons.Outlined.Camera, -1f..1f),
                ToneOption("Vignette", edit.vignette, Icons.Outlined.Vignette, 0f..1f),
                ToneOption("Temp", edit.whiteBalanceTemperature, Icons.Outlined.Thermostat, -1f..1f),
                ToneOption("Hue", edit.hueShift, Icons.Outlined.Gradient, -1f..1f),
            )

            // Tone Option Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (edit.autoEnhanced) OneUiAccent else OneUiCardBg,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = if (edit.autoEnhanced) OneUiAccent else OneUiBorderColor,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onToggleAutoEnhance),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoFixHigh,
                            contentDescription = null,
                            tint = if (edit.autoEnhanced) Color.White else OneUiAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.editor_auto_enhance),
                            color = if (edit.autoEnhanced) Color.White else Color(0xFFC7C7CC),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = if (edit.autoEnhanced) FontWeight.Bold else FontWeight.Medium,
                            ),
                        )
                    }
                }

                toneOptions.forEachIndexed { index, option ->
                    val isSelected = selectedIndex == index
                    val isModified = if (option.range.start < 0f) abs(option.value) > 0.005f else abs(option.value - option.range.start) > 0.005f
                    val itemShape = RoundedCornerShape(16.dp)

                    Surface(
                        shape = itemShape,
                        color = if (isSelected) OneUiSelectedCardBg else OneUiCardBg,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 1.5.dp else 0.8.dp,
                            color = if (isSelected) Color.White else OneUiBorderColor,
                        ),
                        modifier = Modifier
                            .clip(itemShape)
                            .clickable { selectedIndex = index },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.name,
                                    tint = if (isSelected) Color.White else Color(0xFF8E8E93),
                                    modifier = Modifier.size(16.dp),
                                )
                                if (isModified) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(OneUiGreen),
                                    )
                                }
                            }
                            Text(
                                text = option.name,
                                color = if (isSelected) Color.White else Color(0xFF8E8E93),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            val current = toneOptions[selectedIndex]
            SamsungOneUiSlider(
                label = current.name,
                value = current.value,
                range = current.range,
                icon = current.icon,
                onValueChange = { v ->
                    onUpdate { state ->
                        when (selectedIndex) {
                            0 -> state.copy(brightness = v)
                            1 -> state.copy(lightBalance = v)
                            2 -> state.copy(exposure = v)
                            3 -> state.copy(contrast = v)
                            4 -> state.copy(highlights = v)
                            5 -> state.copy(shadows = v)
                            6 -> state.copy(saturation = v)
                            7 -> state.copy(warmth = v)
                            8 -> state.copy(tint = v)
                            9 -> state.copy(sharpness = v)
                            10 -> state.copy(definition = v)
                            11 -> state.copy(vignette = v)
                            12 -> state.copy(whiteBalanceTemperature = v)
                            else -> state.copy(hueShift = v)
                        }
                    }
                },
                onReset = {
                    onUpdate { state ->
                        when (selectedIndex) {
                            0 -> state.copy(brightness = 0f)
                            1 -> state.copy(lightBalance = 0f)
                            2 -> state.copy(exposure = 0f)
                            3 -> state.copy(contrast = 0f)
                            4 -> state.copy(highlights = 0f)
                            5 -> state.copy(shadows = 0f)
                            6 -> state.copy(saturation = 0f)
                            7 -> state.copy(warmth = 0f)
                            8 -> state.copy(tint = 0f)
                            9 -> state.copy(sharpness = 0f)
                            10 -> state.copy(definition = 0f)
                            11 -> state.copy(vignette = 0f)
                            12 -> state.copy(whiteBalanceTemperature = 0f)
                            else -> state.copy(hueShift = 0f)
                        }
                    }
                },
            )
        }

        1 -> {
            // RGB TONE CURVES SUB-PANEL WITH INTERACTIVE SPLINE GRAPH
            SamsungCurvesGraph(
                selectedChannel = selectedCurveChannel,
                curvePoints = edit.curvePoints,
                onChannelChange = { selectedCurveChannel = it },
                onPointsChange = { channel, pts ->
                    onUpdate { state ->
                        state.copy(curvePoints = state.curvePoints + (channel to pts))
                    }
                },
                onPresetSelect = { preset ->
                    onUpdate { state ->
                        val presetPoints = when (preset) {
                            CurvePreset.NONE -> null
                            CurvePreset.FILMIC -> listOf(
                                CurveControlPoint(0f, 0.05f),
                                CurveControlPoint(0.25f, 0.22f),
                                CurveControlPoint(0.50f, 0.50f),
                                CurveControlPoint(0.75f, 0.78f),
                                CurveControlPoint(1f, 0.95f),
                            )
                            CurvePreset.PUNCHY -> listOf(
                                CurveControlPoint(0f, 0f),
                                CurveControlPoint(0.25f, 0.18f),
                                CurveControlPoint(0.50f, 0.50f),
                                CurveControlPoint(0.75f, 0.82f),
                                CurveControlPoint(1f, 1f),
                            )
                            CurvePreset.FADED -> listOf(
                                CurveControlPoint(0f, 0.12f),
                                CurveControlPoint(0.25f, 0.30f),
                                CurveControlPoint(0.50f, 0.50f),
                                CurveControlPoint(0.75f, 0.70f),
                                CurveControlPoint(1f, 0.88f),
                            )
                            CurvePreset.HIGH_KEY -> listOf(
                                CurveControlPoint(0f, 0f),
                                CurveControlPoint(0.25f, 0.35f),
                                CurveControlPoint(0.50f, 0.60f),
                                CurveControlPoint(0.75f, 0.82f),
                                CurveControlPoint(1f, 1f),
                            )
                            CurvePreset.DEEP_SHADOW -> listOf(
                                CurveControlPoint(0f, 0f),
                                CurveControlPoint(0.25f, 0.15f),
                                CurveControlPoint(0.50f, 0.40f),
                                CurveControlPoint(0.75f, 0.70f),
                                CurveControlPoint(1f, 1f),
                            )
                        }
                        if (presetPoints != null) {
                            state.copy(
                                curvePreset = preset,
                                curvePoints = state.curvePoints + (selectedCurveChannel to presetPoints),
                            )
                        } else {
                            val updated = state.curvePoints.toMutableMap()
                            updated.remove(selectedCurveChannel)
                            state.copy(curvePreset = preset, curvePoints = updated)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }

        2 -> {
            // HSL 8-COLOR SELECTIVE WHEEL SUB-PANEL
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 8 Color Channel Swatches
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HslColorChannel.entries.forEach { channel ->
                        val isSelected = channel == selectedHslChannel
                        val channelColor = Color(channel.colorHex)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(channelColor)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { selectedHslChannel = channel },
                        )
                    }
                }

                val currentAdj = edit.hslAdjustments[selectedHslChannel] ?: HslAdjustment()

                // Hue Slider for selected channel
                SamsungOneUiSlider(
                    label = "${selectedHslChannel.displayName} Hue",
                    value = currentAdj.hue,
                    range = -1f..1f,
                    accentColor = Color(selectedHslChannel.colorHex),
                    onValueChange = { v ->
                        onUpdate { state ->
                            val updated = state.hslAdjustments.toMutableMap()
                            updated[selectedHslChannel] = currentAdj.copy(hue = v)
                            state.copy(hslAdjustments = updated)
                        }
                    },
                    onReset = {
                        onUpdate { state ->
                            val updated = state.hslAdjustments.toMutableMap()
                            updated[selectedHslChannel] = currentAdj.copy(hue = 0f)
                            state.copy(hslAdjustments = updated)
                        }
                    },
                )

                // Saturation Slider for selected channel
                SamsungOneUiSlider(
                    label = "${selectedHslChannel.displayName} Saturation",
                    value = currentAdj.saturation,
                    range = -1f..1f,
                    accentColor = Color(selectedHslChannel.colorHex),
                    onValueChange = { v ->
                        onUpdate { state ->
                            val updated = state.hslAdjustments.toMutableMap()
                            updated[selectedHslChannel] = currentAdj.copy(saturation = v)
                            state.copy(hslAdjustments = updated)
                        }
                    },
                    onReset = {
                        onUpdate { state ->
                            val updated = state.hslAdjustments.toMutableMap()
                            updated[selectedHslChannel] = currentAdj.copy(saturation = 0f)
                            state.copy(hslAdjustments = updated)
                        }
                    },
                )

                // Luminance Slider for selected channel
                SamsungOneUiSlider(
                    label = "${selectedHslChannel.displayName} Luminance",
                    value = currentAdj.luminance,
                    range = -1f..1f,
                    accentColor = Color(selectedHslChannel.colorHex),
                    onValueChange = { v ->
                        onUpdate { state ->
                            val updated = state.hslAdjustments.toMutableMap()
                            updated[selectedHslChannel] = currentAdj.copy(luminance = v)
                            state.copy(hslAdjustments = updated)
                        }
                    },
                    onReset = {
                        onUpdate { state ->
                            val updated = state.hslAdjustments.toMutableMap()
                            updated[selectedHslChannel] = currentAdj.copy(luminance = 0f)
                            state.copy(hslAdjustments = updated)
                        }
                    },
                )
            }
        }
    }
}

private data class ToneOption(
    val name: String,
    val value: Float,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val range: ClosedFloatingPointRange<Float>,
)

@Composable
private fun SamsungDecorationsPanel(
    decoSubTool: DecorationSubTool,
    penType: PenType,
    brushColor: Long,
    brushWidth: Float,
    brushOpacity: Float,
    isEyedropperActive: Boolean,
    edit: ImageEditState,
    onDecoSubToolChange: (DecorationSubTool) -> Unit,
    onPenTypeChange: (PenType) -> Unit,
    onBrushColor: (Long) -> Unit,
    onBrushWidth: (Float) -> Unit,
    onBrushOpacity: (Float) -> Unit,
    onToggleEyedropper: () -> Unit,
    onAddText: () -> Unit,
    onUpdate: ((ImageEditState) -> ImageEditState) -> Unit,
) {
    // Sub-tools row (Draw, Text, Sticker, Mosaic)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        DecorationSubTool.entries.forEach { tool ->
            val isSelected = tool == decoSubTool
            val (icon, label) = when (tool) {
                DecorationSubTool.DRAW -> Icons.Outlined.Draw to stringResource(R.string.editor_tool_draw)
                DecorationSubTool.TEXT -> Icons.Outlined.TextFields to stringResource(R.string.editor_tool_text)
                DecorationSubTool.STICKER -> Icons.Outlined.EmojiEmotions to stringResource(R.string.editor_tool_stickers)
                DecorationSubTool.MOSAIC -> Icons.Outlined.BlurOn to "Mosaic"
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) OneUiSelectedCardBg else OneUiCardBg,
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.8.dp,
                    color = if (isSelected) Color.White else OneUiBorderColor,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onDecoSubToolChange(tool) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) Color.White else Color(0xFF8E8E93),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }

    when (decoSubTool) {
        DecorationSubTool.DRAW -> {
            // Pen Styles row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PenType.entries.forEach { type ->
                    val isSelected = type == penType
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color.White else OneUiCardBg,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 0.8.dp,
                            color = if (isSelected) Color.White else OneUiBorderColor,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPenTypeChange(type) },
                    ) {
                        Text(
                            text = type.displayName,
                            color = if (isSelected) Color.Black else Color(0xFFC7C7CC),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Dedicated Mosaic/Privacy Blur Sliders if selected
            if (penType == PenType.MOSAIC_PIXEL) {
                SamsungOneUiSlider(
                    label = "Pixel Block Size",
                    value = (edit.mosaicBlockSize - 4f) / 60f,
                    range = 0f..1f,
                    icon = Icons.Outlined.GridOn,
                    onValueChange = { frac -> onUpdate { it.copy(mosaicBlockSize = 4f + frac * 60f) } },
                    onReset = { onUpdate { it.copy(mosaicBlockSize = 16f) } },
                )
                Spacer(Modifier.height(4.dp))
            } else if (penType == PenType.MOSAIC_BLUR) {
                SamsungOneUiSlider(
                    label = "Blur Radius",
                    value = (edit.mosaicBlurRadius - 5f) / 45f,
                    range = 0f..1f,
                    icon = Icons.Outlined.BlurCircular,
                    onValueChange = { frac -> onUpdate { it.copy(mosaicBlurRadius = 5f + frac * 45f) } },
                    onReset = { onUpdate { it.copy(mosaicBlurRadius = 15f) } },
                )
                Spacer(Modifier.height(4.dp))
            }

            // Color Swatches, Eyedropper & Brush Controls
            SamsungBrushControls(
                brushColor = brushColor,
                brushWidth = brushWidth,
                brushOpacity = brushOpacity,
                isEyedropperActive = isEyedropperActive,
                onBrushColor = onBrushColor,
                onBrushWidth = onBrushWidth,
                onBrushOpacity = onBrushOpacity,
                onToggleEyedropper = onToggleEyedropper,
                onClearStrokes = { onUpdate { it.copy(markup = emptyList()) } },
                hasStrokes = edit.markup.isNotEmpty(),
            )
        }

        DecorationSubTool.TEXT -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Font Family Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Font:",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(end = 2.dp),
                    )
                    TextStyleFont.entries.forEach { font ->
                        val isSelected = font == edit.selectedTextFont
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) OneUiAccent else OneUiCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = if (isSelected) OneUiAccent else OneUiBorderColor,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onUpdate { it.copy(selectedTextFont = font) } },
                        ) {
                            Text(
                                text = font.displayName,
                                color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // Background Style Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Style:",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(end = 2.dp),
                    )
                    TextBackgroundStyle.entries.forEach { bgStyle ->
                        val isSelected = bgStyle == edit.selectedTextBgStyle
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color.White else OneUiCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = if (isSelected) Color.White else OneUiBorderColor,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onUpdate { it.copy(selectedTextBgStyle = bgStyle) } },
                        ) {
                            Text(
                                text = bgStyle.displayName,
                                color = if (isSelected) Color.Black else Color(0xFFC7C7CC),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // Add Text & Remove Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = onAddText,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OneUiAccent,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.TextFields, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add text", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    if (edit.textOverlays.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = { onUpdate { it.copy(textOverlays = it.textOverlays.dropLast(1)) } },
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF444444)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF453A)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text("Remove last", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        DecorationSubTool.STICKER -> {
            val stickers = listOf("❤️", "✨", "🔥", "🐼", "🎉", "🌸", "😂", "👍", "⭐️", "⚡️", "💯", "🎈", "💎", "📸", "👑", "🚀", "🌟", "🎯", "🎨", "🏆")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stickers.forEach { symbol ->
                    Surface(
                        shape = CircleShape,
                        color = OneUiCardBg,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, OneUiBorderColor),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable { onUpdate { it.copy(stickers = it.stickers + StickerOverlay(symbol)) } },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(symbol, fontSize = 22.sp)
                        }
                    }
                }
            }
        }

        DecorationSubTool.MOSAIC -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Shape selector: Rectangle, Rounded, Oval
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Shape:", color = Color(0xFF8E8E93), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                    CropShape.entries.forEach { shape ->
                        val isSelected = edit.areaMosaics.lastOrNull()?.shape == shape
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) OneUiAccent else OneUiCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.8.dp,
                                color = if (isSelected) OneUiAccent else OneUiBorderColor,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onUpdate { state ->
                                        if (state.areaMosaics.isEmpty()) {
                                            state.copy(areaMosaics = listOf(AreaMosaic(shape = shape)))
                                        } else {
                                            val updated = state.areaMosaics.toMutableList()
                                            val last = updated.removeAt(updated.lastIndex)
                                            updated.add(last.copy(shape = shape))
                                            state.copy(areaMosaics = updated)
                                        }
                                    }
                                },
                        ) {
                            Text(
                                text = when (shape) {
                                    CropShape.RECTANGLE -> "Rectangle"
                                    CropShape.ROUNDED_RECT -> "Rounded"
                                    CropShape.OVAL -> "Oval"
                                },
                                color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                // Blur vs Pixel toggle
                val isBlur = edit.areaMosaics.lastOrNull()?.isBlur ?: false
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isBlur) OneUiAccent else OneUiCardBg,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isBlur) OneUiAccent else OneUiBorderColor),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onUpdate { state ->
                                    if (state.areaMosaics.isNotEmpty()) {
                                        val updated = state.areaMosaics.toMutableList()
                                        val last = updated.removeAt(updated.lastIndex)
                                        updated.add(last.copy(isBlur = true))
                                        state.copy(areaMosaics = updated)
                                    } else {
                                        state
                                    }
                                }
                            },
                    ) {
                        Text(
                            "Blur",
                            color = if (isBlur) Color.White else Color(0xFFC7C7CC),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = if (isBlur) FontWeight.Bold else FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (!isBlur) OneUiAccent else OneUiCardBg,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, if (!isBlur) OneUiAccent else OneUiBorderColor),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onUpdate { state ->
                                    if (state.areaMosaics.isNotEmpty()) {
                                        val updated = state.areaMosaics.toMutableList()
                                        val last = updated.removeAt(updated.lastIndex)
                                        updated.add(last.copy(isBlur = false))
                                        state.copy(areaMosaics = updated)
                                    } else {
                                        state
                                    }
                                }
                            },
                    ) {
                        Text(
                            "Pixelate",
                            color = if (!isBlur) Color.White else Color(0xFFC7C7CC),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = if (!isBlur) FontWeight.Bold else FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        )
                    }
                }

                // Add Area / Clear Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Button(
                        onClick = {
                            onUpdate { state ->
                                state.copy(
                                    areaMosaics = state.areaMosaics + AreaMosaic()
                                )
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OneUiAccent, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Privacy Box", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    if (edit.areaMosaics.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onUpdate { it.copy(areaMosaics = emptyList()) } },
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF444444)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF453A)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text("Clear", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SamsungAiToolsPanel(
    aiSubTool: AiSubTool,
    brushWidth: Float,
    edit: ImageEditState,
    onAiSubToolChange: (AiSubTool) -> Unit,
    onBrushWidth: (Float) -> Unit,
    onUpdate: ((ImageEditState) -> ImageEditState) -> Unit,
    onOpenGenerativeEdit: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Sub-tools row (Object Eraser, Portrait Studio, AI Remaster, Subject Lift, Generative Edit)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AiSubTool.entries.forEach { tool ->
            val isSelected = tool == aiSubTool
            val (icon, label) = when (tool) {
                AiSubTool.OBJECT_ERASER -> Icons.Outlined.AutoFixHigh to stringResource(R.string.editor_ai_eraser)
                AiSubTool.PORTRAIT -> Icons.Outlined.FaceRetouchingNatural to stringResource(R.string.editor_ai_portrait)
                AiSubTool.REMASTER -> Icons.Outlined.ShutterSpeed to stringResource(R.string.editor_ai_remaster)
                AiSubTool.SUBJECT_LIFT -> Icons.Outlined.AutoAwesome to "Subject Lift"
                AiSubTool.GENERATIVE_EDIT -> Icons.Outlined.AutoAwesome to "Generative Edit"
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) OneUiSelectedCardBg else OneUiCardBg,
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.8.dp,
                    color = if (isSelected) Color.White else OneUiBorderColor,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onAiSubToolChange(tool) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) Color.White else Color(0xFF8E8E93),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }

    when (aiSubTool) {
        AiSubTool.OBJECT_ERASER -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SamsungOneUiSlider(
                        label = "Eraser size",
                        value = (brushWidth - 0.005f) / 0.035f,
                        range = 0f..1f,
                        icon = Icons.Outlined.Brush,
                        modifier = Modifier.weight(1f),
                        onValueChange = { frac -> onBrushWidth(0.005f + frac * 0.035f) },
                    )
                    if (edit.eraseStrokes.isNotEmpty()) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                android.widget.Toast.makeText(
                                    context,
                                    "${edit.eraseStrokes.size} object(s) marked for erasure. Inpainting will be applied on Save.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OneUiAccent),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Erase (${edit.eraseStrokes.size})", fontSize = 11.5.sp)
                        }
                        OutlinedButton(
                            onClick = { onUpdate { it.copy(eraseStrokes = emptyList()) } },
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF444444)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF453A)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Clear", fontSize = 11.5.sp)
                        }
                    }
                }

                if (edit.eraseStrokes.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Tap or draw around anything you want to erase",
                            color = Color(0xFF8E8E93),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        )
                    }
                }

                // Erase Shadows & Erase Reflections AI Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (edit.eraseShadows) OneUiAccent else OneUiCardBg,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 0.8.dp,
                            color = if (edit.eraseShadows) OneUiAccent else OneUiBorderColor,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onUpdate {
                                    it.copy(
                                        eraseShadows = !it.eraseShadows,
                                        shadows = if (!it.eraseShadows) 0.35f else 0f,
                                    )
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                Icons.Outlined.WbTwilight,
                                null,
                                tint = if (edit.eraseShadows) Color.White else OneUiAccent,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                "Erase shadows",
                                color = if (edit.eraseShadows) Color.White else Color(0xFFC7C7CC),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (edit.eraseReflections) OneUiAccent else OneUiCardBg,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 0.8.dp,
                            color = if (edit.eraseReflections) OneUiAccent else OneUiBorderColor,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onUpdate {
                                    it.copy(
                                        eraseReflections = !it.eraseReflections,
                                        contrast = if (!it.eraseReflections) 0.15f else 0f,
                                        highlights = if (!it.eraseReflections) -0.2f else 0f,
                                    )
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Flare,
                                null,
                                tint = if (edit.eraseReflections) Color.White else OneUiAccent,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                "Erase reflections",
                                color = if (edit.eraseReflections) Color.White else Color(0xFFC7C7CC),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
        }

        AiSubTool.PORTRAIT -> {
            var selectedPortraitIndex by rememberSaveable { mutableIntStateOf(0) }
            val portraitOptions = listOf(
                Triple("Blur", edit.portraitBlur, Icons.Outlined.BlurCircular),
                Triple("Smooth", edit.faceSmoothness, Icons.Outlined.FaceRetouchingNatural),
                Triple("Tone", edit.faceTone, Icons.Outlined.WbSunny),
                Triple("Eyes", edit.faceEyeBrighten, Icons.Outlined.Visibility),
                Triple("Slim", edit.faceSlim, Icons.Outlined.Face),
                Triple("Red-Eye", if (edit.faceRedEyeCorrection) 1f else 0f, Icons.Outlined.RemoveRedEye),
                Triple("Relight", edit.faceRelightIntensity, Icons.Outlined.Lightbulb),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1. Multi-Face Avatar Selection Row (Samsung Multi-face Retouching)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val faceOptions = listOf("👥 All Faces", "👤 Face 1", "👤 Face 2")
                    faceOptions.forEachIndexed { index, label ->
                        val isFaceSelected = edit.selectedFaceIndex == index
                        Surface(
                            shape = CircleShape,
                            color = if (isFaceSelected) OneUiAccent else OneUiCardBg,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isFaceSelected) OneUiAccent else OneUiBorderColor,
                            ),
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(CircleShape)
                                .clickable { onUpdate { it.copy(selectedFaceIndex = index) } },
                        ) {
                            Text(
                                text = label,
                                color = if (isFaceSelected) Color.White else Color(0xFFC7C7CC),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isFaceSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // 2. Studio & Generative AI Lighting Presets Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PortraitLightingMode.entries.forEach { mode ->
                        val isSelected = mode == edit.portraitLighting
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color.White else OneUiCardBg,
                            border = BorderStroke(
                                width = 0.8.dp,
                                color = if (isSelected) Color.White else OneUiBorderColor,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onUpdate { it.copy(portraitLighting = mode) } },
                        ) {
                            Text(
                                text = mode.displayName,
                                color = if (isSelected) Color.Black else Color(0xFFC7C7CC),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // 3. Portrait Retouch Parameters Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    portraitOptions.forEachIndexed { index, (name, value, icon) ->
                        val isSelected = selectedPortraitIndex == index
                        val isModified = abs(value) > 0.005f
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) OneUiSelectedCardBg else OneUiCardBg,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.8.dp,
                                color = if (isSelected) Color.White else OneUiBorderColor,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedPortraitIndex = index },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(icon, null, tint = if (isSelected) Color.White else Color(0xFF8E8E93), modifier = Modifier.size(15.dp))
                                Text(
                                    text = name,
                                    color = if (isSelected) Color.White else Color(0xFF8E8E93),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                )
                                if (isModified) {
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(OneUiGreen))
                                }
                            }
                        }
                    }
                }

                // 4. Parameter Control (Sliders / Relight Compass / Red-Eye Toggle)
                when (selectedPortraitIndex) {
                    5 -> {
                        // Red-Eye Fix Control
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (edit.faceRedEyeCorrection) OneUiAccent else OneUiCardBg,
                            border = BorderStroke(1.dp, if (edit.faceRedEyeCorrection) OneUiAccent else OneUiBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onUpdate { it.copy(faceRedEyeCorrection = !it.faceRedEyeCorrection) } },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.RemoveRedEye, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("Auto Red-Eye Fix", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text(if (edit.faceRedEyeCorrection) "Active · Red pupil glare desaturated" else "Tap to detect & remove flash reflection", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(
                                    text = if (edit.faceRedEyeCorrection) "ON" else "OFF",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (edit.faceRedEyeCorrection) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    6 -> {
                        // Interactive 3D Directional Relighting Compass Widget
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(OneUiCardBg)
                                    .border(1.dp, OneUiBorderColor, CircleShape)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, _ ->
                                            change.consume()
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            val diff = change.position - center
                                            val rad = kotlin.math.atan2(diff.y, diff.x)
                                            var deg = Math.toDegrees(rad.toDouble()).toFloat()
                                            if (deg < 0) deg += 360f
                                            onUpdate { it.copy(faceRelightAngle = deg, faceRelightIntensity = it.faceRelightIntensity.coerceAtLeast(0.5f)) }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Face,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp),
                                )
                                val rad = Math.toRadians(edit.faceRelightAngle.toDouble())
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    drawCircle(
                                        color = OneUiAccent.copy(alpha = 0.3f),
                                        radius = size.width / 2f - 10.dp.toPx(),
                                        style = Stroke(width = 1.dp.toPx()),
                                    )
                                    val orbPos = c + Offset(
                                        (size.width / 2f - 10.dp.toPx()) * kotlin.math.cos(rad).toFloat(),
                                        (size.height / 2f - 10.dp.toPx()) * kotlin.math.sin(rad).toFloat(),
                                    )
                                    drawCircle(
                                        color = Color(0xFFFFD54F),
                                        radius = 7.dp.toPx(),
                                        center = orbPos,
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 3.dp.toPx(),
                                        center = orbPos,
                                    )
                                }
                            }
                            SamsungOneUiSlider(
                                label = "Light Intensity (${edit.faceRelightAngle.toInt()}°)",
                                value = edit.faceRelightIntensity,
                                range = 0f..1f,
                                icon = Icons.Outlined.Lightbulb,
                                onValueChange = { v -> onUpdate { it.copy(faceRelightIntensity = v) } },
                                onReset = { onUpdate { it.copy(faceRelightIntensity = 0f) } },
                            )
                        }
                    }
                    else -> {
                        val current = portraitOptions[selectedPortraitIndex]
                        SamsungOneUiSlider(
                            label = current.first,
                            value = current.second,
                            range = if (selectedPortraitIndex == 2) -1f..1f else 0f..1f,
                            icon = current.third,
                            onValueChange = { v ->
                                onUpdate { state ->
                                    when (selectedPortraitIndex) {
                                        0 -> state.copy(portraitBlur = v)
                                        1 -> state.copy(faceSmoothness = v)
                                        2 -> state.copy(faceTone = v)
                                        3 -> state.copy(faceEyeBrighten = v)
                                        else -> state.copy(faceSlim = v)
                                    }
                                }
                            },
                            onReset = {
                                onUpdate { state ->
                                    when (selectedPortraitIndex) {
                                        0 -> state.copy(portraitBlur = 0f)
                                        1 -> state.copy(faceSmoothness = 0f)
                                        2 -> state.copy(faceTone = 0f)
                                        3 -> state.copy(faceEyeBrighten = 0f)
                                        else -> state.copy(faceSlim = 0f)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        AiSubTool.REMASTER -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SamsungOneUiSlider(
                    label = "Detail Enhancer",
                    value = edit.remasterDetailLevel,
                    range = 0f..1f,
                    icon = Icons.Outlined.ShutterSpeed,
                    onValueChange = { v ->
                        onUpdate {
                            it.copy(
                                remasterDetailLevel = v,
                                sharpness = v * 0.45f,
                                definition = v * 0.35f,
                                contrast = v * 0.15f,
                            )
                        }
                    },
                )

                // Before/After Split Comparison Slider Control
                SamsungOneUiSlider(
                    label = "Before / After Split Position",
                    value = edit.remasterSplitSliderFraction,
                    range = 0f..1f,
                    icon = Icons.Outlined.Compare,
                    onValueChange = { v -> onUpdate { it.copy(remasterSplitSliderFraction = v) } },
                    onReset = { onUpdate { it.copy(remasterSplitSliderFraction = 0.5f) } },
                )
            }
        }

        AiSubTool.SUBJECT_LIFT -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onUpdate { it.copy(isSubjectLifted = !it.isSubjectLifted) } },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (edit.isSubjectLifted) OneUiGreen else OneUiAccent,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (edit.isSubjectLifted) "Subject Lifted ✨" else "Lift Subject",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                    )
                }

                if (edit.isSubjectLifted) {
                    OutlinedButton(
                        onClick = {
                            onUpdate {
                                it.copy(
                                    stickers = it.stickers + StickerOverlay(
                                        symbol = "✨",
                                        position = NormalizedPoint(0.5f, 0.5f),
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, OneUiAccent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OneUiAccent),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.AddReaction, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Sticker", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        AiSubTool.GENERATIVE_EDIT -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenGenerativeEdit,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OneUiGalaxyAiCyan,
                        contentColor = Color.Black,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Open Generative Canvas ✨",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }

                Text(
                    text = "Level horizons, tilt perspective & expand borders with on-device AI",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SamsungBrushControls(
    brushColor: Long,
    brushWidth: Float,
    brushOpacity: Float,
    isEyedropperActive: Boolean,
    onBrushColor: (Long) -> Unit,
    onBrushWidth: (Float) -> Unit,
    onBrushOpacity: (Float) -> Unit,
    onToggleEyedropper: () -> Unit,
    onClearStrokes: () -> Unit,
    hasStrokes: Boolean,
) {
    val swatches = listOf(
        0xffff3b30, 0xffff9500, 0xffffcc00, 0xff34c759,
        0xff00c7be, 0xff007aff, 0xff5856d6, 0xffaf52de,
        0xffffffff, 0xff8e8e93, 0xff000000,
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Size and Opacity sliders side-by-side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                SamsungOneUiSlider(
                    label = "Size",
                    value = brushWidth,
                    range = 0.003f..0.040f,
                    icon = Icons.Outlined.LineWeight,
                    valueFormatter = { "${(it * 1000).toInt()}" },
                    onValueChange = onBrushWidth,
                    onReset = { onBrushWidth(0.010f) },
                )
            }
            Box(Modifier.weight(1f)) {
                SamsungOneUiSlider(
                    label = "Opacity",
                    value = brushOpacity,
                    range = 0.05f..1.0f,
                    icon = Icons.Outlined.Opacity,
                    valueFormatter = { "${(it * 100).toInt()}%" },
                    onValueChange = onBrushOpacity,
                    onReset = { onBrushOpacity(1.0f) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Eyedropper button
            Surface(
                shape = CircleShape,
                color = if (isEyedropperActive) OneUiAccent else OneUiCardBg,
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.8.dp,
                    color = if (isEyedropperActive) OneUiAccent else OneUiBorderColor,
                ),
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleEyedropper),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Colorize,
                        contentDescription = "Eyedropper",
                        tint = if (isEyedropperActive) Color.White else Color(0xFFC7C7CC),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Color Swatches
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                swatches.forEach { value ->
                    val selected = brushColor == value
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onBrushColor(value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(value))
                                .border(
                                    width = if (selected) 2.5.dp else 0.8.dp,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }

            if (hasStrokes) {
                OutlinedButton(
                    onClick = onClearStrokes,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF444444)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF453A)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Clear", fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
private fun SamsungEditorTabBar(
    selected: EditorTab,
    onSelect: (EditorTab) -> Unit,
) {
    val tabs = listOf(
        Triple(EditorTab.TRANSFORM, Icons.Outlined.CropRotate, stringResource(R.string.editor_tab_transform)),
        Triple(EditorTab.FILTERS, Icons.Outlined.FilterVintage, stringResource(R.string.editor_tab_filters)),
        Triple(EditorTab.TONE, Icons.Outlined.Tune, stringResource(R.string.editor_tab_tone)),
        Triple(EditorTab.DECORATIONS, Icons.Outlined.Draw, stringResource(R.string.editor_tab_decorations)),
        Triple(EditorTab.AI_TOOLS, Icons.Outlined.AutoFixHigh, stringResource(R.string.editor_tab_ai_tools)),
    )

    Surface(
        color = Color(0xFF0C0C0E),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { (tab, icon, label) ->
                val active = tab == selected
                val interactionSource = remember { MutableInteractionSource() }
                val contentColor by animateColorAsState(
                    targetValue = if (active) OneUiAccent else Color(0xFF8E8E93),
                    animationSpec = tween(120),
                    label = "tabContentColor",
                )
                val pillAlpha by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = tween(140),
                    label = "tabPillAlpha",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(),
                            onClick = { onSelect(tab) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pillAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(34.dp)
                                .graphicsLayer { alpha = pillAlpha }
                                .clip(RoundedCornerShape(17.dp))
                                .background(Color(0xFF222226))
                                .border(0.8.dp, OneUiAccent.copy(alpha = 0.35f), RoundedCornerShape(17.dp)),
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTextStylingDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onAdd: (String, Long, TextBackgroundStyle, TextStyleFont) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(initialColor) }
    var selectedBgStyle by remember { mutableStateOf(TextBackgroundStyle.NONE) }
    var selectedFont by remember { mutableStateOf(TextStyleFont.DEFAULT) }

    val colors = listOf(0xffffffff, 0xffff3b30, 0xffffcc00, 0xff34c759, 0xff007aff, 0xffaf52de, 0xff000000)

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Text", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Enter text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Font Style Choice
                Text("Font Style", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextStyleFont.entries.forEach { font ->
                        val isSelected = font == selectedFont
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) OneUiAccent else Color(0xFF2C2C2E),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedFont = font },
                        ) {
                            Text(
                                text = font.name.lowercase().replaceFirstChar(Char::titlecase),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                // Background Style Choice
                Text("Background", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextBackgroundStyle.entries.forEach { style ->
                        val isSelected = style == selectedBgStyle
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) OneUiAccent else Color(0xFF2C2C2E),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedBgStyle = style },
                        ) {
                            Text(
                                text = style.name.lowercase().replaceFirstChar(Char::titlecase),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                // Color Swatches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    colors.forEach { c ->
                        val isSelected = c == selectedColor
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (isSelected) 2.dp else 0.8.dp,
                                    color = if (isSelected) OneUiAccent else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape,
                                )
                                .clickable { selectedColor = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(text.trim(), selectedColor, selectedBgStyle, selectedFont) },
                enabled = text.isNotBlank(),
            ) {
                Text("Add", fontWeight = FontWeight.Bold, color = OneUiAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) OneUiSelectedCardBg else OneUiCardBg,
        border = androidx.compose.foundation.BorderStroke(
            width = 0.8.dp,
            color = if (isActive) Color.White else OneUiBorderColor,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (isActive) Color.White else Color(0xFFC7C7CC), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = if (isActive) Color.White else Color(0xFFC7C7CC), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun previewColorFilter(edit: ImageEditState): ColorFilter? {
    if (!edit.hasChanges) return null
    return ColorFilter.colorMatrix(ColorMatrix(toneColorMatrixArray(edit)))
}

@Composable
private fun VideoEditorPreview(uri: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(PlayerMediaItem.fromUri(uri)); prepare() } }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { ctx -> PlayerView(ctx).also { view -> view.player = player } }, modifier = modifier.background(Color.Black))
}

@Composable
private fun VideoEditControls(
    edit: VideoEditState,
    durationMs: Long,
    onUpdate: ((VideoEditState) -> VideoEditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(500L)
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Custom Audio"
            onUpdate {
                it.copy(
                    customAudioUri = uri.toString(),
                    customAudioTitle = title,
                    backgroundMusic = BackgroundMusicTrack.NONE,
                )
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F0F))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Trim Range Header with sub-second precision
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCut,
                    contentDescription = null,
                    tint = Color(0xFFFFD600),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Trim Timeline",
                    color = Color(0xFFF2F2F7),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            // Duration and selected time badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFD600).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFD600).copy(alpha = 0.4f)),
                ) {
                    val selectedSec = (edit.trimEndMs - edit.trimStartMs).coerceAtLeast(0) / 1000f
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", selectedSec)}s selected",
                        color = Color(0xFFFFD600),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2C2C2E),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f)),
                ) {
                    Text(
                        text = "${formatDuration(edit.trimStartMs)} / ${formatDuration(duration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // Samsung Gallery Visual Filmstrip Bar with Perforations & Dual Grab Brackets
        val startFrac = (edit.trimStartMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val endFrac = (edit.trimEndMs.coerceAtLeast(500L).toFloat() / duration.toFloat()).coerceIn(startFrac, 1f)
        val view = LocalView.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF18181A)),
        ) {
            // Filmstrip background with sprocket holes & frame separators
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize(),
            ) {
                val w = size.width
                val h = size.height
                val frameStep = 44.dp.toPx()
                var x = 0f

                // Draw video frame separators
                while (x < w) {
                    drawLine(
                        color = Color(0xFF2C2C2E),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1.2.dp.toPx(),
                    )
                    x += frameStep
                }

                // Draw film sprocket perforations along top and bottom edges
                val holeWidth = 6.dp.toPx()
                val holeHeight = 4.dp.toPx()
                val holeSpacing = 16.dp.toPx()
                var holeX = 6.dp.toPx()
                while (holeX < w) {
                    // Top hole
                    drawRoundRect(
                        color = Color(0xFF0F0F10),
                        topLeft = Offset(holeX, 3.dp.toPx()),
                        size = Size(holeWidth, holeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                    )
                    // Bottom hole
                    drawRoundRect(
                        color = Color(0xFF0F0F10),
                        topLeft = Offset(holeX, h - 3.dp.toPx() - holeHeight),
                        size = Size(holeWidth, holeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                    )
                    holeX += holeSpacing
                }

                val selLeft = w * startFrac
                val selRight = w * endFrac

                // Dimmed scrim outside the trim range
                if (selLeft > 0f) {
                    drawRect(
                        color = Color(0xBB000000),
                        topLeft = Offset.Zero,
                        size = Size(selLeft, h),
                    )
                }
                if (selRight < w) {
                    drawRect(
                        color = Color(0xBB000000),
                        topLeft = Offset(selRight, 0f),
                        size = Size(w - selRight, h),
                    )
                }

                // Luminous yellow bounding frame for the active video range
                val selWidth = (selRight - selLeft).coerceAtLeast(1f)
                // Top border
                drawRect(
                    color = Color(0xFFFFD600),
                    topLeft = Offset(selLeft, 0f),
                    size = Size(selWidth, 3.dp.toPx()),
                )
                // Bottom border
                drawRect(
                    color = Color(0xFFFFD600),
                    topLeft = Offset(selLeft, h - 3.dp.toPx()),
                    size = Size(selWidth, 3.dp.toPx()),
                )

                // Left bracket grab handle
                val handleWidth = 14.dp.toPx()
                drawRoundRect(
                    color = Color(0xFFFFD600),
                    topLeft = Offset(selLeft - handleWidth * 0.5f, 0f),
                    size = Size(handleWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                )
                // Grip ridges on left handle
                val gripColor = Color(0xFF1E1E1E)
                val gripH = 16.dp.toPx()
                val gripY = (h - gripH) / 2f
                val hLeft = selLeft - handleWidth * 0.5f
                drawLine(gripColor, Offset(hLeft + 4.dp.toPx(), gripY), Offset(hLeft + 4.dp.toPx(), gripY + gripH), 1.5.dp.toPx())
                drawLine(gripColor, Offset(hLeft + 7.dp.toPx(), gripY), Offset(hLeft + 7.dp.toPx(), gripY + gripH), 1.5.dp.toPx())
                drawLine(gripColor, Offset(hLeft + 10.dp.toPx(), gripY), Offset(hLeft + 10.dp.toPx(), gripY + gripH), 1.5.dp.toPx())

                // Right bracket grab handle
                drawRoundRect(
                    color = Color(0xFFFFD600),
                    topLeft = Offset(selRight - handleWidth * 0.5f, 0f),
                    size = Size(handleWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                )
                // Grip ridges on right handle
                val hRight = selRight - handleWidth * 0.5f
                drawLine(gripColor, Offset(hRight + 4.dp.toPx(), gripY), Offset(hRight + 4.dp.toPx(), gripY + gripH), 1.5.dp.toPx())
                drawLine(gripColor, Offset(hRight + 7.dp.toPx(), gripY), Offset(hRight + 7.dp.toPx(), gripY + gripH), 1.5.dp.toPx())
                drawLine(gripColor, Offset(hRight + 10.dp.toPx(), gripY), Offset(hRight + 10.dp.toPx(), gripY + gripH), 1.5.dp.toPx())
            }

            // Interactive RangeSlider with transparent tracks layered cleanly on the filmstrip
            RangeSlider(
                value = edit.trimStartMs.toFloat()..edit.trimEndMs.coerceAtLeast(500L).toFloat(),
                onValueChange = { range ->
                    val newStart = range.start.toLong()
                    val newEnd = range.endInclusive.toLong()
                    if (newStart != edit.trimStartMs || newEnd != edit.trimEndMs) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    onUpdate { it.copy(trimStartMs = newStart, trimEndMs = newEnd) }
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
            )
        }

        // Speed Curve Presets & Instant Slow-Mo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Speed:",
                color = Color(0xFF8E8E93),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                modifier = Modifier.padding(end = 2.dp),
            )
            VideoSpeed.entries.forEach { speed ->
                val isSelected = speed == edit.speed
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) OneUiAccent else Color(0xFF1C1C1E),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = if (isSelected) OneUiAccent else Color(0xFF333333),
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onUpdate { it.copy(speed = speed) } },
                ) {
                    Text(
                        text = speed.label,
                        color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // Instant Slow-Mo AI Frame Interpolation Toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (edit.isInstantSlowMoActive) OneUiGreen else Color(0xFF1C1C1E),
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.8.dp,
                    color = if (edit.isInstantSlowMoActive) OneUiGreen else Color(0xFF333333),
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onUpdate { it.copy(isInstantSlowMoActive = !it.isInstantSlowMoActive) } },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.SlowMotionVideo,
                        null,
                        tint = if (edit.isInstantSlowMoActive) Color.Black else OneUiGreen,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "Instant Slow-Mo",
                        color = if (edit.isInstantSlowMoActive) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (edit.isInstantSlowMoActive) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                }
            }
        }

        // Audio & Tool Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rotate Step Button
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1C1C1E),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF333333)),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onUpdate { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) } },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.RotateRight, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${edit.rotationDegrees}°",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }

            // Mute Video Audio Toggle
            val isMuted = edit.mute
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isMuted) Color.White else Color(0xFF1C1C1E),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isMuted) Color.White else Color(0xFF333333)),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onUpdate { it.copy(mute = !it.mute) } },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = null,
                        tint = if (isMuted) Color.Black else Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isMuted) "Muted" else "Mute",
                        color = if (isMuted) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isMuted) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                }
            }

            // AI Audio Eraser / Noise Reduction Toggle
            val audioEraser = edit.audioEraserNoiseReduction
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (audioEraser) OneUiAccent else Color(0xFF1C1C1E),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (audioEraser) OneUiAccent else Color(0xFF333333)),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onUpdate { it.copy(audioEraserNoiseReduction = !it.audioEraserNoiseReduction) } },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = if (audioEraser) Color.White else OneUiAccent,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Audio Eraser",
                        color = if (audioEraser) Color.White else Color(0xFFC7C7CC),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (audioEraser) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                }
            }

            // Audio Ducking Toggle
            val duckingActive = edit.audioDucking
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (duckingActive) OneUiAccent.copy(alpha = 0.2f) else Color(0xFF1C1C1E),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (duckingActive) OneUiAccent else Color(0xFF333333)),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onUpdate { it.copy(audioDucking = !it.audioDucking) } },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = if (duckingActive) OneUiAccent else Color(0xFF8E8E93),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (duckingActive) "Ducking ON" else "Ducking OFF",
                        color = if (duckingActive) OneUiAccent else Color(0xFFC7C7CC),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (duckingActive) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                }
            }

            // Export Resolution Selector
            VideoExportResolution.entries.forEach { res ->
                val isSelected = res == edit.exportResolution
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) OneUiSelectedCardBg else Color(0xFF1C1C1E),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = if (isSelected) OneUiAccent else Color(0xFF333333),
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onUpdate { it.copy(exportResolution = res) } },
                ) {
                    Text(
                        text = res.label,
                        color = if (isSelected) OneUiAccent else Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Background Music Track Selector & Dual Audio Mixer
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Track selector row (Presets + Custom Device Music)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.MusicNote, null, tint = OneUiAccent, modifier = Modifier.size(15.dp))
                Text(
                    "Soundtrack:",
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                )

                // "None" option
                val isNoneSelected = edit.backgroundMusic == BackgroundMusicTrack.NONE && edit.customAudioUri == null
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isNoneSelected) OneUiAccent else Color(0xFF1C1C1E),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = if (isNoneSelected) OneUiAccent else Color(0xFF333333),
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onUpdate {
                                it.copy(
                                    backgroundMusic = BackgroundMusicTrack.NONE,
                                    customAudioUri = null,
                                    customAudioTitle = null,
                                )
                            }
                        },
                ) {
                    Text(
                        text = "None",
                        color = if (isNoneSelected) Color.White else Color(0xFFC7C7CC),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isNoneSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                // Custom Device Audio Chip / Picker
                if (!edit.customAudioUri.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = OneUiAccent.copy(alpha = 0.22f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, OneUiAccent),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Outlined.Audiotrack, null, tint = OneUiAccent, modifier = Modifier.size(13.dp))
                            Text(
                                text = edit.customAudioTitle ?: "Custom",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                            )
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = "Clear custom audio",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        onUpdate { it.copy(customAudioUri = null, customAudioTitle = null) }
                                    },
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1C1C1E),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF444444)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { audioPickerLauncher.launch("audio/*") },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Text(
                                text = "Add Music",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }

                // Preset Tracks
                BackgroundMusicTrack.entries.filter { it != BackgroundMusicTrack.NONE }.forEach { track ->
                    val isSelected = track == edit.backgroundMusic && edit.customAudioUri == null
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) OneUiAccent else Color(0xFF1C1C1E),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 0.8.dp,
                            color = if (isSelected) OneUiAccent else Color(0xFF333333),
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onUpdate {
                                    it.copy(
                                        backgroundMusic = track,
                                        customAudioUri = null,
                                        customAudioTitle = null,
                                    )
                                }
                            },
                    ) {
                        Text(
                            text = track.displayName,
                            color = if (isSelected) Color.White else Color(0xFFC7C7CC),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Original Video Volume Slider
            if (!edit.mute) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Video Vol",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        modifier = Modifier.width(55.dp),
                    )
                    Slider(
                        value = edit.videoAudioVolume,
                        onValueChange = { v -> onUpdate { it.copy(videoAudioVolume = v) } },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF333333),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(edit.videoAudioVolume * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.width(32.dp),
                    )
                }
            }

            // BGM Volume Slider when a background track is selected
            if (edit.hasBackgroundAudio) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = OneUiAccent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "BGM Vol",
                        color = Color(0xFF8E8E93),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        modifier = Modifier.width(55.dp),
                    )
                    Slider(
                        value = edit.bgmVolume,
                        onValueChange = { v -> onUpdate { it.copy(bgmVolume = v) } },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = OneUiAccent,
                            activeTrackColor = OneUiAccent,
                            inactiveTrackColor = Color(0xFF333333),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(edit.bgmVolume * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.width(32.dp),
                    )
                }

                // Audio Ducking Indicator
                if (edit.audioDucking && !edit.mute && edit.videoAudioVolume > 0.05f) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = OneUiAccent.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.6.dp, OneUiAccent.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = OneUiAccent,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "Audio Ducking active: BGM smoothly lowers to ${(edit.effectiveBgmVolume * 100).toInt()}% during video audio",
                                color = OneUiAccent,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalMs = milliseconds.coerceAtLeast(0)
    val seconds = totalMs / 1000
    val ms = (totalMs % 1000) / 100
    return "%d:%02d.%d".format(seconds / 60, seconds % 60, ms)
}
