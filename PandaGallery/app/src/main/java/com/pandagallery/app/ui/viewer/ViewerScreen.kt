package com.pandagallery.app.ui.viewer

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.CachePolicy
import coil3.compose.AsyncImage
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.CompressionOriginalAction
import com.pandagallery.app.data.local.entity.SafetyVaultEntity
import com.pandagallery.app.ui.viewer.components.SmartOptimizeBottomSheet
import com.pandagallery.app.ui.theme.FavoriteRed
import com.pandagallery.app.ui.components.PandaDropdownMenu
import com.pandagallery.app.ui.components.PremiumAlertDialog
import com.pandagallery.app.ui.components.pandaGlass
import com.pandagallery.app.ui.components.pandaGlassStyle
import com.pandagallery.app.ui.components.SwipeDirection
import com.pandagallery.app.ui.components.resolveSwipeDirection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pandagallery.app.ui.viewer.video.SamsungVideoGesturesOverlay
import com.pandagallery.app.ui.viewer.video.SeekDirection
import com.pandagallery.app.ui.viewer.video.SaveSlowMoBottomSheet
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import android.graphics.RectF
import com.pandagallery.app.data.media.SubjectCutoutResult
import com.pandagallery.app.data.media.SubjectSegmentationHelper
import com.pandagallery.app.data.smart.LiveTextRecognizer
import com.pandagallery.app.data.smart.RecognizedTextResult
import com.pandagallery.app.ui.viewer.components.ImageClipperOverlay
import com.pandagallery.app.ui.viewer.components.LiveTextOverlay
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import android.view.TextureView
import android.view.ViewGroup
import android.view.View
import android.view.HapticFeedbackConstants
import android.widget.Toast
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.content.ClipboardManager
import android.content.ClipData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Environment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import android.view.LayoutInflater
import com.pandagallery.app.R
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.pandagallery.app.data.metadata.MediaMetadata
import com.pandagallery.app.data.metadata.MediaFormat
import com.pandagallery.app.data.media.MotionPhotoHelper
import com.pandagallery.app.ui.components.SamsungShareBottomSheet
import com.pandagallery.app.ui.remaster.SamsungRemasterDialog
import com.pandagallery.app.domain.model.GeoPoint
import com.pandagallery.app.domain.model.PhotoGpsItem
import com.pandagallery.app.domain.model.PhotoGpsCluster
import com.pandagallery.app.ui.map.SamsungInteractiveMapSheet
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    mediaId: Long?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    /**
     * Set instead of [mediaId] when another app handed over an item MediaStore has no row
     * for. Such an item is displayed read-only: there is nothing to favourite, trash or
     * compress, and no album to page through.
     */
    externalUri: String? = null,
    onOpenCompressionStudio: (Long) -> Unit = {},
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val compressionPrompt by viewModel.compressionPrompt.collectAsStateWithLifecycle()
    val quickOptimizeEstimate by viewModel.quickOptimizeEstimate.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val activeSafetyBackup by viewModel.activeSafetyBackup.collectAsStateWithLifecycle()
    val pendingPrivateMove by viewModel.pendingPrivateMove.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showSmartOptimizeSheet by remember { mutableStateOf(false) }
    var showRevertConfirmDialog by remember { mutableStateOf(false) }
    var showShareBottomSheet by remember { mutableStateOf(false) }
    var isSlideshow by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isExtractingText by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf<String?>(null) }
    var showExtractedTextSheet by remember { mutableStateOf(false) }
    var activeLiveTextResult by remember { mutableStateOf<RecognizedTextResult?>(null) }
    var isLiveTextOverlayActive by remember { mutableStateOf(false) }
    var activeMotionPhotoFile by remember { mutableStateOf<File?>(null) }
    var isExtractingMotionPhoto by remember { mutableStateOf(false) }
    val motionPhotoCache = remember { mutableStateMapOf<Uri, File>() }
    var isHoldingMotionPhoto by remember { mutableStateOf(false) }
    val pendingRotations = remember { mutableStateMapOf<Long, Float>() }
    var showRemasterDialog by remember { mutableStateOf(false) }

    var showSaveRotationDialog by remember { mutableStateOf(false) }
    var showInteractiveMapSheet by remember { mutableStateOf(false) }
    var mapInitialCenter by remember { mutableStateOf<GeoPoint?>(null) }
    val geotaggedItems by viewModel.geotaggedItems.collectAsStateWithLifecycle()
    var showGenerativeEditDialogViewer by remember { mutableStateOf(false) }
    var sourceBitmapForGenerativeViewer by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val launchLiveTextScan: (MediaItem) -> Unit = { item ->
        if (!isExtractingText) {
            val cached = activeLiveTextResult
            if (cached != null && cached.isNotEmpty) {
                isLiveTextOverlayActive = true
            } else {
                isExtractingText = true
                coroutineScope.launch {
                    try {
                        val result = LiveTextRecognizer.recognizeText(context, item.uri)
                        isExtractingText = false
                        if (result.isNotEmpty) {
                            activeLiveTextResult = result
                            isLiveTextOverlayActive = true
                        } else {
                            Toast.makeText(context, "No text detected in this photo", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        isExtractingText = false
                        Toast.makeText(context, "Text scan failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Samsung One UI Super HDR Gainmap display boost (Android 14+ / Upside Down Cake)
    DisposableEffect(uiState.superHdrGainmapEnabled) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val activity = (context as? Activity)
            val window = activity?.window
            val prevColorMode = window?.colorMode ?: ActivityInfo.COLOR_MODE_DEFAULT
            if (uiState.superHdrGainmapEnabled) {
                window?.colorMode = ActivityInfo.COLOR_MODE_HDR
            } else {
                window?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
            onDispose {
                window?.colorMode = prevColorMode
            }
        } else {
            onDispose { }
        }
    }

    var pendingAction by remember { mutableStateOf<ViewerMutation?>(null) }
    var pendingPrivateAccessItem by remember { mutableStateOf<MediaItem?>(null) }
    val mutationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (action == null) return@rememberLauncherForActivityResult
        val approved = result.resultCode == android.app.Activity.RESULT_OK

        when (action) {
            is ViewerMutation.Favorite -> if (approved) {
                viewModel.setFavorite(action.item, action.favorite)
            }
            is ViewerMutation.Trash -> if (approved) {
                action.item.let(viewModel::markItemTrashed)
                onBack()
            }
            ViewerMutation.MoveToPrivate -> {
                viewModel.completePrivateMove(approved)
                if (approved) onBack()
            }
            is ViewerMutation.RemoveLocation -> if (approved) viewModel.removeLocation(action.item)
            is ViewerMutation.Rotate -> if (approved) {
                viewModel.saveRotation(action.item, action.degrees) {
                    pendingRotations.remove(action.item.id)
                    if (action.andThenBack) onBack()
                }
            }
            is ViewerMutation.Rename -> if (approved) {
                viewModel.renameMedia(action.item, action.requestedName)
            }
        }
    }

    val handleRename: (MediaItem, String) -> Unit = { item, requestedName ->
        if (MediaStore.canManageMedia(context)) {
            viewModel.renameMedia(item, requestedName)
        } else {
            pendingAction = ViewerMutation.Rename(item, requestedName)
            val request = MediaStore.createWriteRequest(
                context.contentResolver,
                listOf(item.uri),
            )
            mutationLauncher.launch(
                IntentSenderRequest.Builder(request.intentSender).build(),
            )
        }
    }

    val handleToggleFavorite: (MediaItem) -> Unit = { item ->
        pendingAction = ViewerMutation.Favorite(item, !item.isFavorite)
        val request = MediaStore.createFavoriteRequest(
            context.contentResolver,
            listOf(item.uri),
            !item.isFavorite,
        )
        mutationLauncher.launch(
            IntentSenderRequest.Builder(request.intentSender).build(),
        )
    }

    val handleSaveRotation: (MediaItem, Float, Boolean) -> Unit = { item, degrees, andThenBack ->
        if (degrees % 360f == 0f) {
            pendingRotations.remove(item.id)
            if (andThenBack) onBack()
        } else if (MediaStore.canManageMedia(context)) {
            viewModel.saveRotation(item, degrees) {
                pendingRotations.remove(item.id)
                if (andThenBack) onBack()
            }
        } else {
            pendingAction = ViewerMutation.Rotate(item, degrees, andThenBack)
            val request = MediaStore.createWriteRequest(
                context.contentResolver,
                listOf(item.uri),
            )
            mutationLauncher.launch(
                IntentSenderRequest.Builder(request.intentSender).build(),
            )
        }
    }
    val mediaManagementLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val item = pendingPrivateAccessItem
        pendingPrivateAccessItem = null
        if (item != null && MediaStore.canManageMedia(context)) {
            viewModel.moveItemToPrivate(item)
        } else {
            Toast.makeText(
                context,
                "Media management access was not enabled",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(mediaId, externalUri) {
        if (externalUri != null) {
            viewModel.loadExternalMedia(externalUri)
        } else if (mediaId != null) {
            viewModel.loadMedia(mediaId)
        }
    }

    LaunchedEffect(pendingPrivateMove) {
        pendingPrivateMove?.let { move ->
            pendingAction = ViewerMutation.MoveToPrivate
            mutationLauncher.launch(
                IntentSenderRequest.Builder(move.deleteRequest.intentSender).build(),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.compressionQueuedEvent.collect {
            android.widget.Toast.makeText(
                context,
                "Added to compression queue",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.initialPage,
        pageCount = { uiState.mediaItems.size },
    )
    val viewerHazeState = remember { HazeState() }
    val backgroundAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "viewer_bg_fade",
    )
    var currentDismissDrag by remember { mutableFloatStateOf(0f) }
    var shutterFlash by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 140.dp.toPx() } }
    val controlsDragThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }
    val effectiveBgAlpha: () -> Float = {
        val fraction = if (currentDismissDrag > 0f) {
            (currentDismissDrag / (dismissThresholdPx * 2.2f)).coerceIn(0f, 1f)
        } else 0f
        ((1f - fraction * 0.95f) * backgroundAlpha).coerceIn(0f, 1f)
    }
    val controlsDragAlpha: () -> Float = {
        (1f - (currentDismissDrag / controlsDragThresholdPx)).coerceIn(0f, 1f)
    }

    LaunchedEffect(pagerState.currentPage, uiState.mediaItems) {
        currentDismissDrag = 0f
        // High-priority neighbor prefetching in Coil for instant swiping
        val imageLoader = coil3.SingletonImageLoader.get(context)
        val neighbors = listOfNotNull(
            uiState.mediaItems.getOrNull(pagerState.currentPage - 1),
            uiState.mediaItems.getOrNull(pagerState.currentPage + 1),
            uiState.mediaItems.getOrNull(pagerState.currentPage + 2),
        )
        neighbors.forEach { neighbor ->
            if (neighbor.isImage) {
                val req = ImageRequest.Builder(context)
                    .data(neighbor.uri)
                    .build()
                imageLoader.enqueue(req)
            }
        }
    }

    LaunchedEffect(uiState.initialPage) {
        if (uiState.mediaItems.isNotEmpty() && pagerState.currentPage != uiState.initialPage) {
            pagerState.scrollToPage(uiState.initialPage)
        }
    }

    val currentItem = uiState.mediaItems.getOrNull(pagerState.currentPage) ?: uiState.currentItem
    val currentRotation = currentItem?.id?.let { pendingRotations[it] } ?: 0f
    val hasUnsavedRotation = currentRotation % 360f != 0f

    var activeClipperCutout by remember { mutableStateOf<SubjectCutoutResult?>(null) }
    var clipperImageScreenRect by remember { mutableStateOf<RectF?>(null) }

    LaunchedEffect(pagerState.currentPage) {
        activeClipperCutout = null
        clipperImageScreenRect = null
        activeLiveTextResult = null
        isLiveTextOverlayActive = false
        showControls = true
    }

    LaunchedEffect(currentItem?.id) {
        currentItem?.id?.let { viewModel.onMediaActive(it) }
    }

    val isCurrentMotionPhoto = uiState.metadata?.format == MediaFormat.MOTION_PHOTO
    LaunchedEffect(currentItem?.uri, isCurrentMotionPhoto) {
        val uri = currentItem?.uri
        if (uri != null && isCurrentMotionPhoto && !motionPhotoCache.containsKey(uri)) {
            val file = MotionPhotoHelper.extractMotionVideo(context, uri)
            if (file != null) {
                motionPhotoCache[uri] = file
            }
        }
    }

    // The details sheet and the overflow menu already stop a back gesture themselves
    // (ModalBottomSheet and DropdownMenu both dismiss on back by default) — slideshow is a plain
    // toggle with nothing wired up, so back would otherwise pop the whole viewer instead of just
    // stopping it. Unsaved rotation also prompts before popping.
    BackHandler(enabled = isLiveTextOverlayActive || activeClipperCutout != null || isSlideshow || hasUnsavedRotation) {
        if (isLiveTextOverlayActive) {
            isLiveTextOverlayActive = false
        } else if (activeClipperCutout != null) {
            activeClipperCutout = null
            clipperImageScreenRect = null
        } else if (isSlideshow) {
            isSlideshow = false
        } else if (hasUnsavedRotation) {
            showSaveRotationDialog = true
        }
    }

    LaunchedEffect(showDetailsSheet, currentItem?.id) {
        if (showDetailsSheet) currentItem?.let(viewModel::loadMetadata)
    }

    LaunchedEffect(isSlideshow, uiState.mediaItems.size, uiState.slideshowIntervalSeconds) {
        while (isSlideshow && uiState.mediaItems.size > 1) {
            delay(uiState.slideshowIntervalSeconds.coerceIn(1, 30) * 1_000L)
            if (pagerState.currentPage >= uiState.mediaItems.lastIndex) {
                isSlideshow = false
            } else {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    // A slideshow the screen dims out of is not a slideshow.
    val view = LocalView.current
    DisposableEffect(isSlideshow, view) {
        view.keepScreenOn = isSlideshow
        onDispose { view.keepScreenOn = false }
    }

    // Action failures used to be swallowed entirely.
    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Gesture Overlay feedback states
    var gestureOverlayText by remember { mutableStateOf("") }
    var gestureOverlayIcon by remember { mutableStateOf<ImageVector?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val configuration = LocalConfiguration.current
    val viewConfiguration = LocalViewConfiguration.current
    val activity = context as? Activity

    // The fullscreen toggle locks the activity; make sure that lock leaves with the viewer.
    // Also restore initial window brightness so video brightness overrides don't leak out of the viewer.
    DisposableEffect(activity) {
        val initialBrightness = activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                win.attributes = win.attributes.apply {
                    screenBrightness = initialBrightness
                }
            }
        }
    }

    val player = remember(currentItem?.id, currentItem?.uri) {
        if (currentItem?.isVideo == true) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(PlayerMediaItem.fromUri(currentItem.uri))
                prepare()
                // Starts paused; autoplay is applied below once the setting is known.
                playWhenReady = false
            }
        } else {
            null
        }
    }

    // Null until the stored preferences arrive, so opening a video directly cannot
    // autoplay for a moment before the setting is known.
    val autoPlayVideos = uiState.autoPlayVideos.takeIf { uiState.preferencesLoaded }

    // Applied as an effect rather than at construction, so playback starts only once the
    // setting is known -- and toggling it later never yanks a clip already playing.
    LaunchedEffect(player, autoPlayVideos) {
        if (autoPlayVideos == true) player?.play()
    }

    var isVideoPlaying by remember { mutableStateOf(false) }
    var lastControlsInteraction by remember { mutableLongStateOf(0L) }
    var isVideoScrubbing by remember { mutableStateOf(false) }
    var isVideoSeeking by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        playerViewRef = null
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isVideoPlaying = isPlaying
                if (isPlaying) {
                    lastControlsInteraction = System.currentTimeMillis()
                }
            }
        }
        player?.addListener(listener)
        isVideoPlaying = player?.isPlaying == true

        onDispose {
            player?.removeListener(listener)
            isVideoPlaying = false
            playerViewRef?.player = null
            playerViewRef = null
            player?.release()
        }
    }

    // Samsung One UI 6.1 / 7: Automatically fade out controls during video playback after 3s of inactivity,
    // but NEVER while the user is actively scrubbing the seekbar or swipe-seeking!
    LaunchedEffect(isVideoPlaying, showControls, lastControlsInteraction, isVideoScrubbing, isVideoSeeking) {
        if (isVideoPlaying && showControls && !isVideoScrubbing && !isVideoSeeking) {
            delay(3000)
            if (!isVideoScrubbing && !isVideoSeeking) {
                showControls = false
            }
        }
    }

    LifecycleResumeEffect(player) {
        onPauseOrDispose {
            player?.pause()
        }
    }

    var showSaveSlowMoSheet by remember { mutableStateOf(false) }
    var isSlowMoExporting by remember { mutableStateOf(false) }
    var slowMoExportProgress by remember { mutableIntStateOf(0) }
    var isCurrentItemZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        isCurrentItemZoomed = false
        activity?.window?.let { win ->
            if (win.attributes.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                win.attributes = win.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            player?.pause()
        }
    }

    val isPagerScrollEnabled = !isCurrentItemZoomed && (!isVideoScrubbing)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind { drawRect(Color.Black.copy(alpha = effectiveBgAlpha())) },
    ) {
        // Camera Shutter Flash Overlay for Video Frame Capture
        AnimatedVisibility(
            visible = shutterFlash,
            enter = fadeIn(tween(40)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }
        if (uiState.mediaItems.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                key = { page -> uiState.mediaItems.getOrNull(page)?.id ?: page.toLong() },
                beyondViewportPageCount = 1,
                pageSpacing = 16.dp,
                userScrollEnabled = isPagerScrollEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(viewerHazeState),
            ) { page ->
                val pageItem = uiState.mediaItems[page]
                if (pageItem.isVideo) {
                    val activePlayer = if (page == pagerState.currentPage) player else null
                    VideoPlayer(
                        player = activePlayer,
                        isPlaying = isVideoPlaying,
                        uri = pageItem.uri,
                        contentDescription = pageItem.displayName,
                        onPlayerViewCreated = { playerViewRef = it },
                        modifier = Modifier.fillMaxSize(),
                        onToggleControls = {
                            showControls = !showControls
                            if (showControls) {
                                lastControlsInteraction = System.currentTimeMillis()
                            }
                        },
                        onSeekRelative = { deltaMs ->
                            lastControlsInteraction = System.currentTimeMillis()
                            activePlayer?.let { p ->
                                val target = (p.currentPosition + deltaMs).coerceIn(0L, p.duration.coerceAtLeast(0L))
                                p.seekTo(target)
                            }
                        },
                        onDismissRequest = {
                            if (hasUnsavedRotation) {
                                showSaveRotationDialog = true
                            } else {
                                onBack()
                            }
                        },
                        onSwipeUp = null,
                        onSwipeSeekingChanged = { isSeeking ->
                            isVideoSeeking = isSeeking
                            if (isSeeking) {
                                lastControlsInteraction = System.currentTimeMillis()
                            }
                        },
                    )
                } else {
                    val isMotion = (pageItem.id == currentItem?.id) && isCurrentMotionPhoto
                    Box(modifier = Modifier.fillMaxSize()) {
                        ZoomableImage(
                            uri = pageItem.uri,
                            contentDescription = pageItem.displayName,
                            onTap = { showControls = !showControls },
                            modifier = Modifier.fillMaxSize(),
                            rotationZ = pendingRotations[pageItem.id] ?: 0f,
                            cacheKey = "${pageItem.width}x${pageItem.height}_${pageItem.size}",
                            isMotionPhoto = isMotion,
                            onHoldMotionPhoto = { holding ->
                                isHoldingMotionPhoto = holding
                            },
                            onDismissRequest = {
                                if (hasUnsavedRotation) {
                                    showSaveRotationDialog = true
                                } else {
                                    onBack()
                                }
                            },
                            onSwipeUp = {
                                showDetailsSheet = true
                            },
                            onQuickCrop = { s, ox, oy, cw, ch ->
                                performQuickCrop(
                                    context = context,
                                    imageUri = pageItem.uri,
                                    scale = s,
                                    offsetX = ox,
                                    offsetY = oy,
                                    containerWidth = cw,
                                    containerHeight = ch,
                                    rotationZ = pendingRotations[pageItem.id] ?: 0f,
                                    coroutineScope = coroutineScope,
                                )
                            },
                            onDismissDragChange = { drag ->
                                if (page == pagerState.currentPage) {
                                    currentDismissDrag = drag.coerceAtLeast(0f)
                                }
                            },
                            onSubjectCutout = { cutout, screenRect ->
                                activeClipperCutout = cutout
                                clipperImageScreenRect = screenRect
                            },
                            onZoomChanged = { isZoomed ->
                                if (page == pagerState.currentPage) {
                                    isCurrentItemZoomed = isZoomed
                                }
                            },
                        )

                        val motionFile = motionPhotoCache[pageItem.uri]
                        if (isMotion && isHoldingMotionPhoto && motionFile != null) {
                            InlineMotionPlayer(
                                videoFile = motionFile,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

            // Top & bottom controls with fade animation
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(120)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = controlsDragAlpha() },
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Top bar
                    TopAppBar(
                        modifier = Modifier.pandaGlass(
                            viewerHazeState,
                            // Stays dark in both themes: these controls are white-on-photo chrome,
                            // not app-background surfaces.
                            pandaGlassStyle(tintAlpha = 0.42f, blurRadius = 32.dp, tint = Color.Black),
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    player?.pause()
                                    if (hasUnsavedRotation) {
                                        showSaveRotationDialog = true
                                    } else {
                                        onBack()
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                )
                            }
                        },
                        title = {
                            currentItem?.let { item ->
                                Column {
                                    Text(
                                        text = item.displayName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append(viewerDateLabel(item.sortDate))
                                            append("  ·  ${pagerState.currentPage + 1}/${uiState.mediaItems.size}")
                                            if (selectedIds.isNotEmpty() || isSelectionMode) {
                                                append("  ·  ${selectedIds.size} selected")
                                            }
                                        },
                                        color = Color.White.copy(alpha = 0.75f),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        },
                        actions = {
                            if (currentItem != null && !uiState.isExternal) {
                                val isSelected = currentItem.id in selectedIds
                                IconButton(
                                    onClick = {
                                        viewModel.toggleSelection(currentItem.id)
                                    }
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color.Black, CircleShape),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                .background(Color.Black.copy(alpha = 0.25f), CircleShape),
                                        )
                                    }
                                }
                            }
                            if (currentItem?.isVideo == true) {
                                IconButton(
                                    onClick = {
                                        playerViewRef?.let { view ->
                                            val textureView = findTextureView(view)
                                            val bitmap = textureView?.bitmap
                                            if (bitmap != null) {
                                                saveBitmapToScreenshots(context, bitmap)
                                            } else {
                                                Toast.makeText(context, "Snapshot failed (Video surface not ready)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.CameraAlt,
                                        contentDescription = "Take Snapshot",
                                        tint = Color.White
                                    )
                                }
                            }
                            // Rotate, Details and Slideshow live in the overflow menu below.
                            // Five always-on actions plus the back button left the title about
                            // 70dp, which truncated every filename to "Docum…".
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "More options", tint = Color.White)
                                }
                                PandaDropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Details") },
                                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            showDetailsSheet = true
                                        },
                                    )
                                    if (uiState.mediaItems.size > 1) {
                                        DropdownMenuItem(
                                            text = { Text(if (isSlideshow) "Stop slideshow" else "Start slideshow") },
                                            leadingIcon = {
                                                Icon(
                                                    if (isSlideshow) Icons.Outlined.PausePresentation else Icons.Outlined.Slideshow,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                isSlideshow = !isSlideshow
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Rotate clockwise") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null) },
                                        enabled = currentItem?.isImage == true && !uiState.isExternal,
                                        onClick = {
                                            showMoreMenu = false
                                            currentItem?.id?.let { id ->
                                                val newDeg = ((pendingRotations[id] ?: 0f) + 90f) % 360f
                                                if (newDeg == 0f) pendingRotations.remove(id) else pendingRotations[id] = newDeg
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rotate counter-clockwise") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.RotateLeft, contentDescription = null) },
                                        enabled = currentItem?.isImage == true && !uiState.isExternal,
                                        onClick = {
                                            showMoreMenu = false
                                            currentItem?.id?.let { id ->
                                                val newDeg = ((pendingRotations[id] ?: 0f) + 270f) % 360f
                                                if (newDeg == 0f) pendingRotations.remove(id) else pendingRotations[id] = newDeg
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                        // The editor saves back through MediaStore, which an
                                        // item with no row there cannot be written to.
                                        enabled = currentItem != null && !uiState.isExternal,
                                        onClick = {
                                            showMoreMenu = false
                                            currentItem?.let { onEdit(it.id) }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remaster picture") },
                                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                                        enabled = currentItem != null && currentItem.isImage && !uiState.isExternal && currentItem.isTrashed == false,
                                        onClick = {
                                            showMoreMenu = false
                                            showRemasterDialog = true
                                        },
                                    )
                                    val isCurrentMotionPhotoItem = currentItem != null && currentItem.isImage && uiState.metadata?.format == MediaFormat.MOTION_PHOTO
                                    if (isCurrentMotionPhotoItem && !uiState.isExternal && currentItem?.isTrashed == false) {
                                        DropdownMenuItem(
                                            text = { Text("Save as video") },
                                            leadingIcon = { Icon(Icons.Outlined.MovieCreation, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                viewModel.saveMotionPhotoAsVideo(currentItem)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete motion photo clip") },
                                            leadingIcon = { Icon(Icons.Outlined.LayersClear, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                viewModel.stripMotionPhoto(currentItem)
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Set as wallpaper") },
                                        leadingIcon = { Icon(Icons.Outlined.Wallpaper, contentDescription = null) },
                                        enabled = currentItem?.isImage == true,
                                        onClick = {
                                            showMoreMenu = false
                                            currentItem?.let { item ->
                                                val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                                    setDataAndType(item.uri, item.mimeType)
                                                    putExtra("mimeType", item.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                runCatching { context.startActivity(Intent.createChooser(intent, "Set image as")) }
                                                    .onFailure { Toast.makeText(context, "Wallpaper action is unavailable", Toast.LENGTH_SHORT).show() }
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Use as album cover") },
                                        leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                        // Trashed items are about to disappear, and an item
                                        // with no bucket has no album to be the cover of.
                                        enabled = currentItem?.bucketId != null && currentItem?.isTrashed == false,
                                        onClick = {
                                            showMoreMenu = false
                                            currentItem?.let(viewModel::setAsAlbumCover)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move to Private Vault") },
                                         leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                                         enabled = currentItem != null && !uiState.isExternal && currentItem?.isTrashed == false && !uiState.isMovingToPrivate,
                                         onClick = {
                                             showMoreMenu = false
                                             currentItem?.let { item ->
                                                 if (MediaStore.canManageMedia(context)) {
                                                     viewModel.moveItemToPrivate(item)
                                                 } else {
                                                     pendingPrivateAccessItem = item
                                                 }
                                             }
                                         },
                                     )
                                     DropdownMenuItem(
                                         text = { Text("Compress item") },
                                         leadingIcon = { Icon(Icons.Outlined.Compress, contentDescription = null) },
                                         enabled = currentItem != null && !uiState.isExternal,
                                         onClick = {
                                             showMoreMenu = false
                                             currentItem?.let { item ->
                                                 if (item.isImage) {
                                                     onOpenCompressionStudio(item.id)
                                                 } else {
                                                     viewModel.compressItem(item)
                                                 }
                                             }
                                         },
                                     )
                                     DropdownMenuItem(
                                         text = { Text("Play on another screen") },
                                        leadingIcon = { Icon(Icons.Outlined.CastConnected, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            currentItem?.let { item ->
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(item.uri, item.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                runCatching { context.startActivity(Intent.createChooser(intent, "Open or cast with")) }
                                                    .onFailure { Toast.makeText(context, "No compatible TV or media app is available", Toast.LENGTH_SHORT).show() }
                                            }
                                        },
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                    )

                    currentItem?.let { activeItem ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.6f)
                                        )
                                    )
                                )
                        ) {
                            if ((isSelectionMode || selectedIds.isNotEmpty()) && !uiState.isExternal) {
                                val isSelected = activeItem.id in selectedIds
                                val haptic = LocalHapticFeedback.current
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleSelection(activeItem.id)
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        color = if (isSelected) Color.White else Color(0xDD1C1C1E),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                        ),
                                        shadowElevation = 6.dp,
                                        modifier = Modifier.animateContentSize(
                                            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                                        ),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                                contentDescription = if (isSelected) "Included in selection" else "Not included in selection",
                                                tint = if (isSelected) Color.Black else Color.White,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Text(
                                                text = if (isSelected) {
                                                    if (selectedIds.size > 1) "Included (${selectedIds.size})" else "Included"
                                                } else {
                                                    if (activeItem.isVideo) "Include video" else "Include photo"
                                                },
                                                color = if (isSelected) Color.Black else Color.White,
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }

                            // Samsung One UI Motion Photo Pill & Smart Storage Optimize Pill
                            if (!isSelectionMode && activeItem.isImage) {
                                val isMotionPhoto = activeItem.isImage && uiState.metadata?.format == MediaFormat.MOTION_PHOTO
                                val estimate = quickOptimizeEstimate
                                val canShowOptimize = estimate != null && !activeItem.isCompressed && estimate.savedBytes > 300_000L
                                if (isMotionPhoto || canShowOptimize) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isMotionPhoto) {
                                            Surface(
                                                onClick = {
                                                    val cached = motionPhotoCache[activeItem.uri]
                                                    if (cached != null) {
                                                        activeMotionPhotoFile = cached
                                                    } else if (!isExtractingMotionPhoto) {
                                                        isExtractingMotionPhoto = true
                                                        coroutineScope.launch {
                                                            val file = MotionPhotoHelper.extractMotionVideo(context, activeItem.uri)
                                                            isExtractingMotionPhoto = false
                                                            if (file != null) {
                                                                motionPhotoCache[activeItem.uri] = file
                                                                activeMotionPhotoFile = file
                                                            } else {
                                                                Toast.makeText(context, "No embedded motion video found", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                color = Color(0xDD1C1C1E),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                                shadowElevation = 6.dp,
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    if (isExtractingMotionPhoto) {
                                                        CircularProgressIndicator(
                                                            color = Color.White,
                                                            strokeWidth = 2.dp,
                                                            modifier = Modifier.size(14.dp),
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "View motion photo",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                    Text(
                                                        text = "View motion photo",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 13.sp,
                                                        ),
                                                    )
                                                }
                                            }
                                        }

                                        if (canShowOptimize && estimate != null) {
                                            Surface(
                                                onClick = { showSmartOptimizeSheet = true },
                                                shape = RoundedCornerShape(20.dp),
                                                color = Color(0xEE1E293B),
                                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                                shadowElevation = 6.dp,
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.AutoAwesome,
                                                        contentDescription = "Smart Storage Optimizer",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(15.dp),
                                                    )
                                                    Text(
                                                        text = "Optimize: ~${android.text.format.Formatter.formatShortFileSize(context, estimate.savedBytes)}",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (uiState.mediaItems.size > 1 && !uiState.isExternal && !activeItem.isVideo) {
                                ViewerThumbnailFilmstrip(
                                    mediaItems = uiState.mediaItems,
                                    currentPage = pagerState.currentPage,
                                    selectedIds = selectedIds,
                                    isSelectionMode = isSelectionMode || selectedIds.isNotEmpty(),
                                    onItemClick = { index ->
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                    onItemLongClick = { mediaId ->
                                        viewModel.toggleSelection(mediaId)
                                    },
                                )
                            }

                            player?.let { activePlayer ->
                                VideoPlaybackControls(
                                    player = activePlayer,
                                    onUserInteraction = {
                                        lastControlsInteraction = System.currentTimeMillis()
                                    },
                                    onScrubbingChanged = { scrubbing ->
                                        isVideoScrubbing = scrubbing
                                        lastControlsInteraction = System.currentTimeMillis()
                                    },
                                    onCaptureFrame = {
                                        lastControlsInteraction = System.currentTimeMillis()
                                        activeItem.let { item ->
                                            shutterFlash = true
                                            coroutineScope.launch {
                                                delay(250)
                                                shutterFlash = false
                                            }
                                            viewModel.captureCurrentFrame(item.uri, activePlayer.currentPosition) { result ->
                                                if (result.isSuccess) {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                        }
                                    },
                                    onCompressVideo = {
                                        lastControlsInteraction = System.currentTimeMillis()
                                        // Not the Studio: it decodes a still for its before/after
                                        // preview and has no video path, so a video sent there
                                        // only ever showed a decode failure.
                                        viewModel.compressItem(activeItem)
                                    },
                                    onSmartOptimize = {
                                        lastControlsInteraction = System.currentTimeMillis()
                                        showSmartOptimizeSheet = true
                                    },
                                    estimate = quickOptimizeEstimate,
                                    onSaveSlowMo = {
                                        lastControlsInteraction = System.currentTimeMillis()
                                        showSaveSlowMoSheet = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // Was two stacked paddings (32dp a side), which squeezed
                                        // the action pills and the seek bar out of alignment with
                                        // the rest of the viewer chrome.
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            BottomActionBar(
                                item = activeItem,
                                isSelectionMode = isSelectionMode || selectedIds.isNotEmpty(),
                                isSelected = activeItem.id in selectedIds,
                                onToggleSelection = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelection(activeItem.id)
                                },
                                galleryActionsEnabled = !uiState.isExternal,
                                onCompress = { viewModel.compressItem(activeItem) },
                                onShare = { showShareBottomSheet = true },
                                onEdit = { onEdit(activeItem.id) },
                                onFavorite = {
                                    pendingAction = ViewerMutation.Favorite(activeItem, !activeItem.isFavorite)
                                    val request = MediaStore.createFavoriteRequest(
                                        context.contentResolver,
                                        listOf(activeItem.uri),
                                        !activeItem.isFavorite,
                                    )
                                    mutationLauncher.launch(
                                        IntentSenderRequest.Builder(request.intentSender).build(),
                                    )
                                },
                                onMoveToPrivate = {
                                    if (MediaStore.canManageMedia(context)) {
                                        viewModel.moveItemToPrivate(activeItem)
                                    } else {
                                        pendingPrivateAccessItem = activeItem
                                    }
                                },
                                isMovingToPrivate = uiState.isMovingToPrivate,
                                onDelete = {
                                    pendingAction = ViewerMutation.Trash(activeItem)
                                    val request = MediaStore.createTrashRequest(
                                        context.contentResolver,
                                        listOf(activeItem.uri),
                                        true,
                                    )
                                    mutationLauncher.launch(
                                        IntentSenderRequest.Builder(request.intentSender).build(),
                                    )
                                },
                                onMore = {
                                    showMoreMenu = true
                                },
                            )
                        }
                    }
                }
            }
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // Floating Save / Reset bar for rotated single image
        AnimatedVisibility(
            visible = hasUnsavedRotation,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 70.dp)
                .graphicsLayer { alpha = controlsDragAlpha() },
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                shadowElevation = 8.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Rotate90DegreesCw,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Rotated ${currentRotation.toInt()}°",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(
                        onClick = { currentItem?.id?.let { pendingRotations.remove(it) } },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "Reset",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Button(
                        onClick = {
                            currentItem?.let { item ->
                                handleSaveRotation(item, currentRotation, false)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                    ) {
                        Text(
                            text = "Save",
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Gesture Overlay Visual Feedback Card
        if (gestureOverlayText.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gestureOverlayIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = gestureOverlayText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }


        // Details bottom sheet
        if (showDetailsSheet && currentItem != null) {
            DetailsBottomSheet(
                item = currentItem,
                metadata = uiState.metadata?.takeIf { it.mediaId == currentItem.id },
                onDismiss = { showDetailsSheet = false },
                onOpenMap = { latitude, longitude ->
                    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        .onFailure { Toast.makeText(context, "No map app is available", Toast.LENGTH_SHORT).show() }
                    showDetailsSheet = false
                    mapInitialCenter = GeoPoint(latitude, longitude)
                    viewModel.loadGeotaggedItems()
                    showInteractiveMapSheet = true
                },
                quickOptimizeEstimate = quickOptimizeEstimate,
                canRemoveLocation = !uiState.isExternal,
                onRemoveLocation = {
                    if (MediaStore.canManageMedia(context)) {
                        viewModel.removeLocation(currentItem)
                    } else {
                        pendingAction = ViewerMutation.RemoveLocation(currentItem)
                        val request = MediaStore.createWriteRequest(context.contentResolver, listOf(currentItem.uri))
                        mutationLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }
                },
                onActionSuggestion = { suggestion ->
                    showDetailsSheet = false
                    if (suggestion == "remaster") {
                        showRemasterDialog = true
                    } else {
                        onEdit(currentItem.id)
                    }
                },
                onOpenCompressionStudio = {
                    showDetailsSheet = false
                    onOpenCompressionStudio(currentItem.id)
                },
                onCompressItem = {
                    showDetailsSheet = false
                    viewModel.compressItem(currentItem)
                },
                safetyBackup = activeSafetyBackup,
                onRevertOriginal = {
                    showRevertConfirmDialog = true
                },
                onSmartOptimize = {
                    showDetailsSheet = false
                    showSmartOptimizeSheet = true
                },
                onOpenGenerativeEdit = if (currentItem?.isImage == true) {{
                    showDetailsSheet = false
                    showGenerativeEditDialogViewer = true
                }} else null,
                onRename = handleRename,
                onToggleFavorite = handleToggleFavorite,
            )
        }

        // Samsung One UI Interactive Map View Sheet
        if (showInteractiveMapSheet) {
            val allMapItems = remember(geotaggedItems, currentItem, uiState.metadata) {
                val list = geotaggedItems.toMutableList()
                val meta = uiState.metadata
                if (currentItem != null && meta?.latitude != null && meta.longitude != null) {
                    if (list.none { it.mediaId == currentItem.id }) {
                        list.add(
                            0,
                            PhotoGpsItem(
                                mediaId = currentItem.id,
                                uri = currentItem.uri,
                                displayName = currentItem.displayName,
                                latitude = meta.latitude,
                                longitude = meta.longitude,
                                dateTaken = currentItem.dateTaken,
                                size = currentItem.size,
                                isVideo = currentItem.isVideo,
                            )
                        )
                    }
                }
                list
            }

            SamsungInteractiveMapSheet(
                items = allMapItems,
                initialCenter = mapInitialCenter,
                onDismiss = { showInteractiveMapSheet = false },
                onOpenMedia = { targetMediaId ->
                    showInteractiveMapSheet = false
                    val targetIndex = uiState.mediaItems.indexOfFirst { it.id == targetMediaId }
                    if (targetIndex >= 0) {
                        coroutineScope.launch {
                            pagerState.scrollToPage(targetIndex)
                        }
                    }
                },
                onCompressCluster = { cluster ->
                    viewModel.compressCluster(cluster)
                },
                onStripGps = { cluster ->
                    showInteractiveMapSheet = false
                    if (currentItem != null && cluster.items.any { it.mediaId == currentItem.id }) {
                        viewModel.removeLocation(currentItem)
                    }
                },
            )
        }

        // Smart Storage Optimizer Modal Sheet
        if (showSmartOptimizeSheet && currentItem != null) {
            SmartOptimizeBottomSheet(
                item = currentItem,
                preferences = userPreferences,
                onDismiss = { showSmartOptimizeSheet = false },
                onConfirmAction = { preset, remasterDetailLevel, action ->
                    showSmartOptimizeSheet = false
                    viewModel.optimizeActiveItem(
                        currentItem,
                        preset = preset,
                        remasterDetailLevel = remasterDetailLevel,
                        action = action,
                    )
                    val msg = when {
                        action != com.pandagallery.app.domain.model.CompressionOriginalAction.MOVE -> "Optimizing copy..."
                        userPreferences?.safetyVaultEnabled == false -> "Optimizing & replacing (Safety Vault is off)..."
                        else -> "Optimizing & backing up to safety vault..."
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                onOpenStudio = {
                    showSmartOptimizeSheet = false
                    onOpenCompressionStudio(currentItem.id)
                },
                onStripMotionPhoto = {
                    showSmartOptimizeSheet = false
                    viewModel.stripMotionPhoto(currentItem)
                },
            )
        }

        // Revert Lossless Original Confirmation Dialog
        if (showRevertConfirmDialog && activeSafetyBackup != null && currentItem != null) {
            AlertDialog(
                onDismissRequest = { showRevertConfirmDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Revert to Original?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This will restore your original uncompressed file (${android.text.format.Formatter.formatShortFileSize(context, activeSafetyBackup?.originalBytes ?: 0L)}) from the safety vault and remove the compressed version.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRevertConfirmDialog = false
                            activeSafetyBackup?.let { vault ->
                                viewModel.revertActiveItem(vault)
                                Toast.makeText(context, "Restoring original file...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Revert (Lossless)")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRevertConfirmDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Samsung One UI Share Bottom Sheet
        if (showShareBottomSheet && currentItem != null) {
            SamsungShareBottomSheet(
                items = listOf(currentItem),
                initialRemoveLocation = uiState.removeLocationWhenSharing,
                initialConvertHeifRaw = uiState.convertHeifWhenSharing,
                onDismiss = { showShareBottomSheet = false },
                onConfirmShare = { removeLocation, convertHeifRaw ->
                    showShareBottomSheet = false
                    viewModel.shareItem(
                        currentItem,
                        removeLocation = removeLocation,
                        convertHeifRaw = convertHeifRaw,
                    )
                },
            )
        }

        // Samsung One UI Instant Slow-Mo Save Bottom Sheet
        if (showSaveSlowMoSheet && currentItem != null) {
            SaveSlowMoBottomSheet(
                originalDurationMs = currentItem.duration ?: (player?.duration?.coerceAtLeast(0L) ?: 0L),
                isExporting = isSlowMoExporting,
                exportProgress = slowMoExportProgress,
                onDismiss = {
                    if (!isSlowMoExporting) {
                        showSaveSlowMoSheet = false
                    }
                },
                onExport = { config ->
                    isSlowMoExporting = true
                    slowMoExportProgress = 0
                    coroutineScope.launch {
                        val result = viewModel.exportSlowMo(currentItem, config) { progress ->
                            slowMoExportProgress = progress
                        }
                        isSlowMoExporting = false
                        showSaveSlowMoSheet = false
                        if (result.isSuccess) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                            Toast.makeText(context, "Slow-Mo clip saved to Movies/PandaGallery SlowMo", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, result.errorMessage ?: "Failed to save slow-mo video", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // Samsung One UI Extracted Text Sheet
        if (showExtractedTextSheet && extractedText != null) {
            ModalBottomSheet(
                onDismissRequest = { showExtractedTextSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "T",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Extracted Text",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${extractedText?.length ?: 0} characters found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { showExtractedTextSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(
                                text = extractedText ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", extractedText))
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                                showExtractedTextSheet = false
                            },
                            shape = RoundedCornerShape(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy all")
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, extractedText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share text"))
                            },
                            shape = RoundedCornerShape(50.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }

        // Samsung One UI Dedicated AI Remaster Dialog
        if (showRemasterDialog && currentItem != null && currentItem.isImage) {
            SamsungRemasterDialog(
                item = currentItem,
                onDismiss = { showRemasterDialog = false },
                onApplyRemaster = { bitmap, level -> viewModel.applyRemaster(bitmap, level) },
                onSaveRemaster = { item, bitmap, saveAsCopy, useNearLossless ->
                    viewModel.saveRemaster(item, bitmap, saveAsCopy, useNearLossless)
                },
            )
        }

        // Generative Edit: decode bitmap lazily when dialog is requested
        LaunchedEffect(showGenerativeEditDialogViewer, currentItem?.id) {
            if (showGenerativeEditDialogViewer && currentItem != null && currentItem.isImage) {
                val parsedUri = when (val u = currentItem.uri) {
                    is android.net.Uri -> u
                    is String -> android.net.Uri.parse(u as String)
                    else -> null
                }
                if (parsedUri != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                            sourceBitmapForGenerativeViewer = bmp
                        }
                    }
                }
            } else if (!showGenerativeEditDialogViewer) {
                sourceBitmapForGenerativeViewer = null
            }
        }

        if (showGenerativeEditDialogViewer && sourceBitmapForGenerativeViewer != null) {
            com.pandagallery.app.ui.editor.SamsungGenerativeEditDialog(
                sourceBitmap = sourceBitmapForGenerativeViewer!!,
                onDismiss = {
                    showGenerativeEditDialogViewer = false
                    sourceBitmapForGenerativeViewer = null
                },
                onSaveToVault = { generatedBitmap ->
                    showGenerativeEditDialogViewer = false
                    sourceBitmapForGenerativeViewer = null
                    viewModel.saveGenerativeCopy(generatedBitmap, saveToVault = true)
                },
                onSaveAsCopy = { generatedBitmap ->
                    showGenerativeEditDialogViewer = false
                    sourceBitmapForGenerativeViewer = null
                    viewModel.saveGenerativeCopy(generatedBitmap, saveToVault = false)
                },
            )
        }



        // Samsung One UI Motion Photo Player Dialog
        activeMotionPhotoFile?.let { videoFile ->
            MotionPhotoDialog(
                videoFile = videoFile,
                onDismiss = { activeMotionPhotoFile = null },
                onShare = { uri ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share motion video"))
                },
            )
        }

        // Compression dialog
        compressionPrompt?.let { prompt ->
            PremiumAlertDialog(
                onDismissRequest = viewModel::dismissCompressionPrompt,
                icon = { Icon(Icons.Outlined.Compress, contentDescription = null) },
                title = { Text("Compress item") },
                text = {
                    Text("Copy keeps the original. Move removes it after compression succeeds and Android authorizes the deletion.")
                },
                // Copy and Move both sit in the confirm slot so the dismiss slot can hold a real
                // Cancel. With Copy in the dismiss position there was no visible way out, and the
                // button where people look for "Cancel" started a compression job instead.
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            currentItem?.let { item ->
                                viewModel.chooseCompressionAction(item, CompressionOriginalAction.COPY)
                            }
                        }) {
                            Text("Copy")
                        }
                        TextButton(onClick = {
                            currentItem?.let { item ->
                                viewModel.chooseCompressionAction(item, CompressionOriginalAction.MOVE)
                            }
                        }) {
                            Text("Move")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissCompressionPrompt) {
                        Text("Cancel")
                    }
                },
            )
        }

        pendingPrivateAccessItem?.let { item ->
            PremiumAlertDialog(
                onDismissRequest = { pendingPrivateAccessItem = null },
                icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
                title = { Text("Allow automatic Private moves?") },
                text = {
                    Text("Android requires one Media management grant before PandaGallery can remove public originals without asking every time.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            runCatching {
                                mediaManagementLauncher.launch(
                                    Intent(
                                        Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }.onFailure {
                                pendingPrivateAccessItem = null
                                viewModel.moveItemToPrivate(item)
                            }
                        },
                    ) { Text("Open settings") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingPrivateAccessItem = null
                            viewModel.moveItemToPrivate(item)
                        },
                    ) { Text("Continue with confirmation") }
                },
            )
        }
        if (showSaveRotationDialog) {
            PremiumAlertDialog(
                onDismissRequest = { showSaveRotationDialog = false },
                icon = { Icon(Icons.Outlined.Rotate90DegreesCw, contentDescription = null) },
                title = { Text("Save orientation?") },
                text = {
                    Text("Do you want to save the new orientation for \"${currentItem?.displayName ?: "this photo"}\"?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSaveRotationDialog = false
                            currentItem?.let { item ->
                                handleSaveRotation(item, currentRotation, true)
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSaveRotationDialog = false
                            currentItem?.id?.let { pendingRotations.remove(it) }
                            onBack()
                        }
                    ) {
                        Text("Discard")
                    }
                }
            )
        }

        // Samsung One UI Image Clipper Lifted Object Overlay
        val currentCutout = activeClipperCutout
        val currentScreenRect = clipperImageScreenRect
        if (currentCutout != null && currentScreenRect != null) {
            ImageClipperOverlay(
                cutout = currentCutout,
                imageScreenRect = currentScreenRect,
                onCopy = {
                    coroutineScope.launch {
                        val copied = SubjectSegmentationHelper.copyCutoutToClipboard(context, currentCutout.cutoutBitmap)
                        if (copied) {
                            Toast.makeText(context, "Copied sticker to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        activeClipperCutout = null
                        clipperImageScreenRect = null
                    }
                },
                onShare = {
                    coroutineScope.launch {
                        SubjectSegmentationHelper.shareCutout(context, currentCutout.cutoutBitmap)
                        activeClipperCutout = null
                        clipperImageScreenRect = null
                    }
                },
                onSave = {
                    coroutineScope.launch {
                        val savedUri = SubjectSegmentationHelper.saveCutoutToGallery(
                            context = context,
                            cutoutBitmap = currentCutout.cutoutBitmap,
                            baseName = currentItem?.displayName ?: "cutout",
                        )
                        if (savedUri != null) {
                            Toast.makeText(context, "Saved to Pictures/PandaGallery/Stickers", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to save sticker", Toast.LENGTH_SHORT).show()
                        }
                        activeClipperCutout = null
                        clipperImageScreenRect = null
                    }
                },
                onDismiss = {
                    activeClipperCutout = null
                    clipperImageScreenRect = null
                },
            )
        }

        // Samsung One UI 6.1 Floating Live Text "T" Button
        val isCurrentItemImage = currentItem?.isImage == true
        AnimatedVisibility(
            visible = showControls && isCurrentItemImage && !isLiveTextOverlayActive,
            enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 400f)) + fadeIn(),
            exit = scaleOut(spring(dampingRatio = 0.75f, stiffness = 400f)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 96.dp)
                .graphicsLayer { alpha = controlsDragAlpha() },
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFFD600), // Vibrant Samsung One UI Gold
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        currentItem?.let { launchLiveTextScan(it) }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isExtractingText) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = "T",
                            color = Color.Black,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                            ),
                        )
                    }
                }
            }
        }

        // Samsung One UI 6.1 Live Text Interactive OCR Canvas Overlay
        val liveTextResult = activeLiveTextResult
        if (isLiveTextOverlayActive && liveTextResult != null) {
            LiveTextOverlay(
                textResult = liveTextResult,
                onDismiss = { isLiveTextOverlayActive = false },
            )
        }
    }
}

private sealed interface ViewerMutation {
    data class Favorite(val item: MediaItem, val favorite: Boolean) : ViewerMutation
    data class Trash(val item: MediaItem) : ViewerMutation
    data class RemoveLocation(val item: MediaItem) : ViewerMutation
    data class Rotate(val item: MediaItem, val degrees: Float, val andThenBack: Boolean = false) : ViewerMutation
    data object MoveToPrivate : ViewerMutation
    data class Rename(val item: MediaItem, val requestedName: String) : ViewerMutation
}

@Composable
fun VideoPlayer(
    player: ExoPlayer?,
    isPlaying: Boolean = false,
    uri: Uri? = null,
    contentDescription: String,
    onPlayerViewCreated: (PlayerView) -> Unit,
    modifier: Modifier = Modifier,
    onToggleControls: () -> Unit = {},
    onSeekRelative: (Long) -> Unit = {},
    onDismissRequest: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeSeekingChanged: ((Boolean) -> Unit)? = null,
) {
    var activeSeekRipple by remember { mutableStateOf<SeekDirection?>(null) }
    var isSlowMoActive by remember { mutableStateOf(false) }
    var slowMoSpeed by remember { mutableFloatStateOf(0.25f) }
    var previousSpeedBeforeSlowMo by remember { mutableFloatStateOf(1.0f) }
    var wasPlayingBeforeSlowMo by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        if (uri != null) {
            val context = LocalContext.current
            val videoImageRequest = remember(uri) {
                ImageRequest.Builder(context)
                    .data(uri)
                    .placeholderMemoryCacheKey("${uri}_thumb")
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = videoImageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        player?.let { activePlayer ->
            AndroidView(
                factory = { context ->
                    val view = LayoutInflater.from(context).inflate(R.layout.custom_player_view, null) as PlayerView
                    view.apply {
                        this.player = activePlayer
                        isClickable = false
                        isFocusable = false
                        setOnTouchListener { _, _ -> false }
                        onPlayerViewCreated(this)
                    }
                },
                update = {
                    it.player = activePlayer
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Samsung One UI 6.1 Gesture & Overlay System:
            // - Horizontal swipe passes through to HorizontalPager for seamless next/prev photo & video navigation
            // - Left vertical swipe for brightness HUD
            // - Right vertical swipe for volume HUD
            // - Double-tap ±10s seek ripples
            // - Press & hold for Instant Slow-Mo (0.25x)
            // - Single tap to toggle controls
            // - Dismiss on swipe down from top edge, Details on swipe up from bottom edge
            SamsungVideoGesturesOverlay(
                player = activePlayer,
                isPlaying = isPlaying,
                onToggleControls = onToggleControls,
                onSeekRelative = onSeekRelative,
                activeSeekRipple = activeSeekRipple,
                isSlowMoActive = isSlowMoActive,
                slowMoSpeedMultiplier = slowMoSpeed,
                onSlowMoStart = {
                    isSlowMoActive = true
                    previousSpeedBeforeSlowMo = activePlayer.playbackParameters.speed
                    wasPlayingBeforeSlowMo = activePlayer.isPlaying
                    activePlayer.setPlaybackSpeed(slowMoSpeed)
                    if (!activePlayer.isPlaying) activePlayer.play()
                },
                onSlowMoEnd = {
                    isSlowMoActive = false
                    activePlayer.setPlaybackSpeed(previousSpeedBeforeSlowMo)
                    if (!wasPlayingBeforeSlowMo) activePlayer.pause()
                },
                onDismissRequest = onDismissRequest,
                onSwipeUp = onSwipeUp,
                onSwipeSeekingChanged = onSwipeSeekingChanged,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: if (uri == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else Unit
    }
}

@Composable
fun VideoPlaybackControls(
    player: ExoPlayer,
    onUserInteraction: () -> Unit = {},
    onCaptureFrame: () -> Unit = {},
    onCompressVideo: () -> Unit = {},
    onSmartOptimize: (() -> Unit)? = null,
    estimate: com.pandagallery.app.domain.compression.CompressionEstimator.Estimate? = null,
    onSaveSlowMo: () -> Unit = {},
    onScrubbingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val view = LocalView.current

    var playbackPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var lastSeekTimestamp by remember { mutableLongStateOf(0L) }

    val speedOptions = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }
            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
                playbackPosition = player.currentPosition
                bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
                if (!isScrubbing) {
                    playbackPosition = player.currentPosition
                }
            }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                currentSpeed = playbackParameters.speed
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        duration = player.duration.coerceAtLeast(0L)
        bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
        playbackPosition = player.currentPosition
        currentSpeed = player.playbackParameters.speed

        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, isPlaying) {
        if (isPlaying) {
            while (true) {
                playbackPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
                kotlinx.coroutines.delay(200)
                if (!isScrubbing) {
                    playbackPosition = player.currentPosition
                    bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
                    duration = player.duration.coerceAtLeast(0L)
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Action Pills Row (Horizontally scrollable, beautifully aligned capsules)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Compress or Smart Optimize Pill
            val canOptimize = estimate != null && estimate.savedBytes > 300_000L
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (canOptimize) Color(0xEE1E293B) else Color.White.copy(alpha = 0.15f),
                border = BorderStroke(
                    1.dp,
                    if (canOptimize) Color(0xFF38BDF8).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier.clickable {
                    onUserInteraction()
                    if (canOptimize && onSmartOptimize != null) {
                        onSmartOptimize()
                    } else {
                        onCompressVideo()
                    }
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (canOptimize) Icons.Outlined.AutoAwesome else Icons.Outlined.Compress,
                        contentDescription = "Compress or Optimize Video",
                        tint = if (canOptimize) Color(0xFF38BDF8) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (canOptimize && estimate != null) {
                            "Optimize: ~${android.text.format.Formatter.formatShortFileSize(context, estimate.savedBytes)}"
                        } else {
                            "Compress Video"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Slow-Mo Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.15f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.clickable {
                    onUserInteraction()
                    onSaveSlowMo()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SlowMotionVideo,
                        contentDescription = "Save Slow-Mo",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Slow-Mo",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Speed Pill
            Box {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.clickable {
                        onUserInteraction()
                        showSpeedMenu = true
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = "Playback Speed",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${currentSpeed}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                PandaDropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false },
                ) {
                    speedOptions.forEach { speed ->
                        val label = when (speed) {
                            0.25f -> "0.25x (Super Slow)"
                            0.5f -> "0.5x (Slow-Mo)"
                            1.0f -> "1.0x (Normal)"
                            else -> "${speed}x"
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal,
                                    color = if (speed == currentSpeed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onUserInteraction()
                                showSpeedMenu = false
                                player.setPlaybackSpeed(speed)
                                currentSpeed = speed
                            }
                        )
                    }
                }
            }

            // Capture Frame (Snapshot) Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.15f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.clickable {
                    onUserInteraction()
                    onCaptureFrame()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = "Capture frame",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Snapshot",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Loop Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Color(0xEE1E293B) else Color.White.copy(alpha = 0.15f),
                border = BorderStroke(
                    0.8.dp,
                    if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Color(0xFF38BDF8).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier.clickable {
                    onUserInteraction()
                    val nextMode = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                        androidx.media3.common.Player.REPEAT_MODE_OFF
                    } else {
                        androidx.media3.common.Player.REPEAT_MODE_ONE
                    }
                    player.repeatMode = nextMode
                    repeatMode = nextMode
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                        contentDescription = "Loop video",
                        tint = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Color(0xFF38BDF8) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) "Loop On" else "Loop",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Fullscreen Orientation Toggle Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.15f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.clickable {
                    onUserInteraction()
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    activity?.requestedOrientation = if (isLandscape) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            Icons.Outlined.FullscreenExit
                        } else {
                            Icons.Outlined.Fullscreen
                        },
                        contentDescription = "Toggle Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "Exit Full" else "Fullscreen",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Row 2: Centered Playback Controls (-10s, Play/Pause, +10s)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    onUserInteraction()
                    val newPos = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                    player.seekTo(newPos)
                    playbackPosition = newPos
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FastRewind,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(28.dp))

            Surface(
                onClick = {
                    onUserInteraction()
                    if (isPlaying) player.pause() else player.play()
                },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                shadowElevation = 8.dp,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.width(28.dp))

            IconButton(
                onClick = {
                    onUserInteraction()
                    val newPos = (player.currentPosition + 10_000L).coerceAtMost(duration)
                    player.seekTo(newPos)
                    playbackPosition = newPos
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FastForward,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Row 3: Seek Bar Row (AT THE BOTTOM of VideoPlaybackControls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayedCurrentPosition = if (isScrubbing) scrubPositionMs else playbackPosition
            Text(
                text = formatTime(displayedCurrentPosition),
                color = if (isScrubbing) Color(0xFF38BDF8) else Color.White,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.SemiBold
                ),
            )

            CustomSeekBar(
                position = playbackPosition,
                duration = duration,
                bufferedPosition = bufferedPosition,
                onSeekStarted = {
                    isScrubbing = true
                    onScrubbingChanged(true)
                    scrubPositionMs = playbackPosition
                    wasPlayingBeforeScrub = isPlaying
                    if (isPlaying) {
                        player.pause()
                    }
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onUserInteraction()
                },
                onSeek = { newPositionMs ->
                    scrubPositionMs = newPositionMs
                    onUserInteraction()
                    val now = System.currentTimeMillis()
                    if (now - lastSeekTimestamp >= 40L) {
                        lastSeekTimestamp = now
                        player.seekTo(newPositionMs)
                    }
                },
                onSeekFinished = { finalPositionMs ->
                    player.seekTo(finalPositionMs)
                    playbackPosition = finalPositionMs
                    isScrubbing = false
                    onScrubbingChanged(false)
                    if (wasPlayingBeforeScrub) {
                        player.play()
                    }
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onUserInteraction()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Text(
                text = formatTime(duration),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}


private fun findTextureView(view: View): TextureView? {
    if (view is TextureView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val result = findTextureView(child)
            if (result != null) return result
        }
    }
    return null
}

private fun saveBitmapToScreenshots(
    context: Context,
    bitmap: Bitmap,
    prefix: String = "PandaSnapshot",
    successMessage: String = "Snapshot saved to Screenshots",
) {
    try {
        val resolver = context.contentResolver
        val filename = "${prefix}_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save snapshot", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Failed to create media file", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error saving snapshot: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun performQuickCrop(
    context: Context,
    imageUri: Uri,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    containerWidth: Int,
    containerHeight: Int,
    rotationZ: Float = 0f,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    coroutineScope.launch(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(imageUri) ?: return@launch
            val fullBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (fullBitmap == null) return@launch

            val orientedBitmap = if (rotationZ % 360f != 0f) {
                val matrix = Matrix().apply { postRotate(rotationZ) }
                val rotated = Bitmap.createBitmap(fullBitmap, 0, 0, fullBitmap.width, fullBitmap.height, matrix, true)
                if (rotated != fullBitmap) fullBitmap.recycle()
                rotated
            } else {
                fullBitmap
            }

            val imgW = orientedBitmap.width.toFloat()
            val imgH = orientedBitmap.height.toFloat()
            val boxW = containerWidth.toFloat()
            val boxH = containerHeight.toFloat()
            if (boxW <= 0 || boxH <= 0 || imgW <= 0 || imgH <= 0) {
                orientedBitmap.recycle()
                return@launch
            }

            val fitScale = minOf(boxW / imgW, boxH / imgH)
            val effectiveScale = (scale * fitScale).coerceAtLeast(0.001f)

            val xMin = ((imgW / 2f) + (-boxW / 2f - offsetX) / effectiveScale).coerceIn(0f, imgW - 1f)
            val yMin = ((imgH / 2f) + (-boxH / 2f - offsetY) / effectiveScale).coerceIn(0f, imgH - 1f)
            val xMax = ((imgW / 2f) + (boxW / 2f - offsetX) / effectiveScale).coerceIn(xMin + 1f, imgW)
            val yMax = ((imgH / 2f) + (boxH / 2f - offsetY) / effectiveScale).coerceIn(yMin + 1f, imgH)

            val cropX = xMin.toInt()
            val cropY = yMin.toInt()
            val cropW = (xMax - xMin).toInt().coerceAtLeast(1)
            val cropH = (yMax - yMin).toInt().coerceAtLeast(1)

            val croppedBitmap = Bitmap.createBitmap(orientedBitmap, cropX, cropY, cropW, cropH)
            if (croppedBitmap != orientedBitmap) {
                orientedBitmap.recycle()
            }

            withContext(Dispatchers.Main) {
                saveBitmapToScreenshots(
                    context = context,
                    bitmap = croppedBitmap,
                    prefix = "PandaCrop",
                    successMessage = "Cropped image saved",
                )
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Quick crop failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun viewerDateLabel(millis: Long): String =
    java.text.DateFormat
        .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(millis))

internal fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

private suspend fun extractCutoutForTouch(
    context: Context,
    imageUri: Uri,
    rotationZ: Float,
    touchOffset: Offset,
    containerSize: androidx.compose.ui.unit.IntSize,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): Pair<SubjectCutoutResult, RectF>? {
    return withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(imageUri) ?: return@withContext null
        val fullBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (fullBitmap == null) return@withContext null

        val orientedBitmap = if (rotationZ % 360f != 0f) {
            val matrix = Matrix().apply { postRotate(rotationZ) }
            val rotated = Bitmap.createBitmap(fullBitmap, 0, 0, fullBitmap.width, fullBitmap.height, matrix, true)
            if (rotated != fullBitmap) fullBitmap.recycle()
            rotated
        } else {
            fullBitmap
        }

        val imgW = orientedBitmap.width.toFloat()
        val imgH = orientedBitmap.height.toFloat()
        val boxW = containerSize.width.toFloat()
        val boxH = containerSize.height.toFloat()
        if (boxW <= 0 || boxH <= 0 || imgW <= 0 || imgH <= 0) {
            orientedBitmap.recycle()
            return@withContext null
        }

        val fitScale = minOf(boxW / imgW, boxH / imgH)
        val fitWidth = imgW * fitScale
        val fitHeight = imgH * fitScale

        val centerX = boxW / 2f + offsetX
        val centerY = boxH / 2f + offsetY

        val displayW = fitWidth * scale
        val displayH = fitHeight * scale
        val imageLeft = centerX - displayW / 2f
        val imageTop = centerY - displayH / 2f
        val imageRight = imageLeft + displayW
        val imageBottom = imageTop + displayH

        val imageRect = RectF(imageLeft, imageTop, imageRight, imageBottom)

        if (touchOffset.x < imageLeft || touchOffset.x > imageRight ||
            touchOffset.y < imageTop || touchOffset.y > imageBottom
        ) {
            orientedBitmap.recycle()
            return@withContext null
        }

        val touchXFraction = ((touchOffset.x - imageLeft) / displayW).coerceIn(0f, 1f)
        val touchYFraction = ((touchOffset.y - imageTop) / displayH).coerceIn(0f, 1f)

        val cutoutResult = SubjectSegmentationHelper.extractSubjectCutout(
            sourceBitmap = orientedBitmap,
            touchXFraction = touchXFraction,
            touchYFraction = touchYFraction,
        )

        if (cutoutResult != null) {
            Pair(cutoutResult, imageRect)
        } else {
            null
        }
    }
}

/**
 * Zoomable image with pinch-to-zoom and double-tap zoom.
 */
@Composable
fun ZoomableImage(
    uri: Uri,
    contentDescription: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    rotationZ: Float = 0f,
    cacheKey: Any? = null,
    isMotionPhoto: Boolean = false,
    onHoldMotionPhoto: ((Boolean) -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    onQuickCrop: ((scale: Float, offsetX: Float, offsetY: Float, containerWidth: Int, containerHeight: Int) -> Unit)? = null,
    onDismissDragChange: ((Float) -> Unit)? = null,
    onPageScrollStart: (() -> Unit)? = null,
    onPageScrollDelta: ((Float) -> Unit)? = null,
    onPageScrollEnd: ((Float, Float) -> Unit)? = null,
    onSubjectCutout: ((SubjectCutoutResult, RectF) -> Unit)? = null,
    onZoomChanged: ((Boolean) -> Unit)? = null,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val coroutineScope = rememberCoroutineScope()
    key(uri, cacheKey) {
        var scale by remember(uri) { mutableFloatStateOf(1f) }
        var offsetX by remember(uri) { mutableFloatStateOf(0f) }
        var offsetY by remember(uri) { mutableFloatStateOf(0f) }
        var containerSize by remember(uri) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

        // How far the scaled image may travel before its edge would leave the frame.
        fun maxOffsetX() = (containerSize.width * (scale - 1f) / 2f).coerceAtLeast(0f)
        fun maxOffsetY() = (containerSize.height * (scale - 1f) / 2f).coerceAtLeast(0f)

        // Swipe down on an un-zoomed photo to close, the gesture people try first.
        // Swipe down to dismiss, swipe up to open One UI Details sheet.
        var dismissDrag by remember(uri) { mutableFloatStateOf(0f) }
        val dismissThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 140.dp.toPx() }
        val viewConfiguration = androidx.compose.ui.platform.LocalViewConfiguration.current

        Box(modifier = modifier.fillMaxSize()) {
            val context = LocalContext.current
            val imageRequest = remember(uri, cacheKey) {
                ImageRequest.Builder(context)
                    .data(uri)
                    .placeholderMemoryCacheKey("${uri}_thumb")
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .graphicsLayer {
                        this.rotationZ = rotationZ
                        val dismissProgress = if (dismissDrag > 0f) (dismissDrag / (dismissThresholdPx * 2.5f)).coerceIn(0f, 1f) else 0f
                        val dismissScale = 1f - (dismissProgress * 0.22f)
                        scaleX = scale * dismissScale
                        scaleY = scale * dismissScale
                        translationX = offsetX
                        translationY = offsetY + dismissDrag
                        alpha = 1f - (kotlin.math.abs(dismissDrag) / (dismissThresholdPx * 3f)).coerceIn(0f, 0.6f)
                        if (dismissProgress > 0.01f) {
                            shape = androidx.compose.foundation.shape.RoundedCornerShape((dismissProgress * 24).dp)
                            clip = true
                        }
                    }
                    .pointerInput(uri, dismissThresholdPx, isMotionPhoto) {
                        awaitEachGesture {
                            var isPinching = false
                            var resolvedDirection: SwipeDirection? = null
                            var initialTouchPos = Offset.Zero
                            var lastTouchPos = Offset.Zero
                            val velocityTracker = VelocityTracker()
                            var isHoldingMotion = false
                            var holdJob: kotlinx.coroutines.Job? = null
                            var clipperJob: kotlinx.coroutines.Job? = null

                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            initialTouchPos = firstDown.position
                            lastTouchPos = firstDown.position
                            velocityTracker.addPosition(firstDown.uptimeMillis, firstDown.position)

                            if (isMotionPhoto && onHoldMotionPhoto != null && scale <= 1.05f) {
                                holdJob = coroutineScope.launch {
                                    kotlinx.coroutines.delay(320)
                                    isHoldingMotion = true
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    onHoldMotionPhoto(true)
                                }
                            } else if (!isMotionPhoto && onSubjectCutout != null && scale <= 1.25f) {
                                clipperJob = coroutineScope.launch {
                                    kotlinx.coroutines.delay(380)
                                    val result = extractCutoutForTouch(
                                        context = context,
                                        imageUri = uri,
                                        rotationZ = rotationZ,
                                        touchOffset = initialTouchPos,
                                        containerSize = containerSize,
                                        scale = scale,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                    )
                                    if (result != null) {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                        onSubjectCutout(result.first, result.second)
                                    }
                                }
                            }

                            do {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                val pressedChanges = changes.filter { it.pressed }
                                val pointerCount = pressedChanges.size

                                if (pointerCount >= 2) {
                                    holdJob?.cancel()
                                    clipperJob?.cancel()
                                    if (isHoldingMotion) {
                                        isHoldingMotion = false
                                        onHoldMotionPhoto?.invoke(false)
                                    }
                                    // Multi-touch: Exclusively pinch-to-zoom and pan.
                                    // Disarm any pending vertical dismiss drag so zooming never jumps down.
                                    if (resolvedDirection != null) {
                                        resolvedDirection = null
                                        dismissDrag = 0f
                                        onDismissDragChange?.invoke(0f)
                                    }
                                    isPinching = true

                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()

                                    // Samsung Gallery pinch-to-shrink exit gesture (PhotoViewMotionControl.java isPinchDirectionIn)
                                    if (scale <= 1.0f && zoomChange < 0.84f && onDismissRequest != null) {
                                        pressedChanges.forEach { it.consume() }
                                        onDismissRequest()
                                        break
                                    }

                                    val newScale = (scale * zoomChange).coerceIn(1f, 10f)
                                    val wasZoomed = scale > 1.05f
                                    val isNowZoomed = newScale > 1.05f
                                    if (wasZoomed != isNowZoomed) {
                                        onZoomChanged?.invoke(isNowZoomed)
                                    }
                                    scale = newScale

                                    if (scale > 1f) {
                                        offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX(), maxOffsetX())
                                        offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY(), maxOffsetY())
                                        pressedChanges.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                } else if (pointerCount == 1 && !isPinching) {
                                    val change = pressedChanges.firstOrNull() ?: break
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    lastTouchPos = change.position
                                    val totalDy = change.position.y - initialTouchPos.y
                                    val totalDx = change.position.x - initialTouchPos.x

                                    if (kotlin.math.abs(totalDx) > viewConfiguration.touchSlop ||
                                        kotlin.math.abs(totalDy) > viewConfiguration.touchSlop
                                    ) {
                                        holdJob?.cancel()
                                        clipperJob?.cancel()
                                        if (isHoldingMotion) {
                                            isHoldingMotion = false
                                            onHoldMotionPhoto?.invoke(false)
                                        }
                                    }

                                    if (scale > 1.05f) {
                                        // Zoomed single-finger pan
                                        val panChange = change.position - change.previousPosition
                                        if (change.positionChanged()) {
                                            change.consume()
                                            val newX = offsetX + panChange.x
                                            val newY = offsetY + panChange.y
                                            val clampedX = newX.coerceIn(-maxOffsetX(), maxOffsetX())
                                            val clampedY = newY.coerceIn(-maxOffsetY(), maxOffsetY())
                                            offsetX = clampedX
                                            offsetY = clampedY
                                            if (clampedX == newX) {
                                                change.consume()
                                            }
                                        }
                                    } else {
                                        // 1x scale: 4-way swipe navigation (LEFT, RIGHT, UP, DOWN)
                                        if (resolvedDirection == null) {
                                            resolvedDirection = resolveSwipeDirection(
                                                dx = totalDx,
                                                dy = totalDy,
                                                touchSlop = viewConfiguration.touchSlop,
                                            )
                                        }

                                        when (resolvedDirection) {
                                            SwipeDirection.LEFT, SwipeDirection.RIGHT -> {
                                                // Horizontal swipe at 1x scale:
                                                // DO NOT consume so HorizontalPager scrolls natively!
                                            }
                                            SwipeDirection.UP, SwipeDirection.DOWN -> {
                                                val dragAmount = change.position.y - change.previousPosition.y
                                                dismissDrag += dragAmount
                                                onDismissDragChange?.invoke(dismissDrag)
                                                change.consume()
                                            }
                                            null -> {
                                                // Within touch slop
                                            }
                                        }
                                    }
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            } while (event.changes.any { it.pressed })

                            holdJob?.cancel()
                            clipperJob?.cancel()
                            if (isHoldingMotion) {
                                isHoldingMotion = false
                                onHoldMotionPhoto?.invoke(false)
                            }

                            // Gesture ended: all fingers lifted
                            when (resolvedDirection) {
                                SwipeDirection.LEFT, SwipeDirection.RIGHT -> {
                                    // Handled natively by HorizontalPager!
                                }
                                SwipeDirection.UP, SwipeDirection.DOWN -> {
                                    if (dismissDrag > dismissThresholdPx && onDismissRequest != null) {
                                        onDismissRequest()
                                    } else if (dismissDrag < -dismissThresholdPx && onSwipeUp != null) {
                                        onSwipeUp()
                                        dismissDrag = 0f
                                        onDismissDragChange?.invoke(0f)
                                    } else {
                                        // Smooth spring recoil to 0
                                        coroutineScope.launch {
                                            androidx.compose.animation.core.animate(
                                                initialValue = dismissDrag,
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = 0.75f,
                                                    stiffness = 380f,
                                                )
                                            ) { value, _ ->
                                                dismissDrag = value
                                                onDismissDragChange?.invoke(value)
                                            }
                                        }
                                    }
                                }
                                null -> {
                                    // Taps handled by detectTapGestures
                                }
                            }
                        }
                    }
                    .pointerInput(uri) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = { tapOffset ->
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                coroutineScope.launch {
                                    if (scale > 1.05f) {
                                        // Smoothly animate zoom back to 1x
                                        onZoomChanged?.invoke(false)
                                        val startScale = scale
                                        val startX = offsetX
                                        val startY = offsetY
                                        androidx.compose.animation.core.animate(
                                            initialValue = 0f,
                                            targetValue = 1f,
                                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                        ) { fraction, _ ->
                                            scale = androidx.compose.ui.util.lerp(startScale, 1f, fraction)
                                            offsetX = androidx.compose.ui.util.lerp(startX, 0f, fraction)
                                            offsetY = androidx.compose.ui.util.lerp(startY, 0f, fraction)
                                        }
                                    } else {
                                        // Focus zoom into the tapped coordinate
                                        onZoomChanged?.invoke(true)
                                        val targetScale = 2.5f
                                        val centerX = containerSize.width / 2f
                                        val centerY = containerSize.height / 2f
                                        val targetMaxX = (containerSize.width * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                        val targetMaxY = (containerSize.height * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                        val targetX = (-(tapOffset.x - centerX) * (targetScale - 1f)).coerceIn(-targetMaxX, targetMaxX)
                                        val targetY = (-(tapOffset.y - centerY) * (targetScale - 1f)).coerceIn(-targetMaxY, targetMaxY)

                                        val startScale = scale
                                        val startX = offsetX
                                        val startY = offsetY
                                        androidx.compose.animation.core.animate(
                                            initialValue = 0f,
                                            targetValue = 1f,
                                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                                        ) { fraction, _ ->
                                            scale = androidx.compose.ui.util.lerp(startScale, targetScale, fraction)
                                            offsetX = androidx.compose.ui.util.lerp(startX, targetX, fraction)
                                            offsetY = androidx.compose.ui.util.lerp(startY, targetY, fraction)
                                        }
                                    }
                                }
                            },
                            onLongPress = { pressOffset ->
                                if (!isMotionPhoto && onSubjectCutout != null) {
                                    coroutineScope.launch {
                                        val result = extractCutoutForTouch(
                                            context = context,
                                            imageUri = uri,
                                            rotationZ = rotationZ,
                                            touchOffset = pressOffset,
                                            containerSize = containerSize,
                                            scale = scale,
                                            offsetX = offsetX,
                                            offsetY = offsetY,
                                        )
                                        if (result != null) {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                            onSubjectCutout(result.first, result.second)
                                        }
                                    }
                                }
                            },
                        )
                    },
            )

            // Samsung Gallery Quick Crop Floating FAB (viewer_quick_crop_layout.xml)
            AnimatedVisibility(
                visible = scale > 1.12f && onQuickCrop != null,
                enter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 400f)) + fadeIn(),
                exit = scaleOut(spring(dampingRatio = 0.75f, stiffness = 400f)) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 64.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Black.copy(alpha = 0.70f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .clickable {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                            onQuickCrop?.invoke(scale, offsetX, offsetY, containerSize.width, containerSize.height)
                        },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Crop,
                            contentDescription = "Quick Crop",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Quick Crop",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun InlineMotionPlayer(
    videoFile: File,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val videoUri = remember(videoFile) { Uri.fromFile(videoFile) }
    val exoPlayer = remember(videoFile) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(videoUri))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Samsung One UI top pill indicator
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Black.copy(alpha = 0.70f),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Motion Photo",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp,
                    ),
                    color = Color.White,
                )
            }
        }
    }
}


// Custom extension to add tap gesture detection alongside transformable
private fun Modifier.detectTapGestures(
    onPress: suspend androidx.compose.foundation.gestures.PressGestureScope.(androidx.compose.ui.geometry.Offset) -> Unit = {},
    onTap: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onDoubleTap: (androidx.compose.ui.geometry.Offset) -> Unit = {},
    onLongPress: (androidx.compose.ui.geometry.Offset) -> Unit = {},
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectTapGestures(
            onPress = onPress,
            onTap = onTap,
            onDoubleTap = onDoubleTap,
            onLongPress = onLongPress,
        )
    }
)

/**
 * Bottom action bar in viewer.
 */
@Composable
fun BottomActionBar(
    item: MediaItem,
    onShare: () -> Unit,
    onCompress: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onMoveToPrivate: () -> Unit,
    isMovingToPrivate: Boolean,
    onDelete: () -> Unit,
    /**
     * False for an item another app handed over that MediaStore has no row for. Favourite,
     * trash, compress and Private all go through MediaStore, so they are left out rather
     * than offered and failed.
     */
    onMore: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    galleryActionsEnabled: Boolean = true,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode && onToggleSelection != null) {
                IconButton(
                    onClick = onToggleSelection,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                            CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.Check else Icons.Outlined.Add,
                        contentDescription = if (isSelected) "Deselect" else "Select",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = "Share",
                    tint = Color.White,
                )
            }
            if (galleryActionsEnabled) {
                IconButton(onClick = onCompress) {
                    Icon(
                        Icons.Outlined.Compress,
                        contentDescription = "Compress",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit",
                        tint = Color.White,
                    )
                }
                // Favourite lives in SeslFavoriteButton further down this row; a plain heart
                // IconButton here as well put two identical hearts in the bar.
                if (!item.isTrashed) {
                    IconButton(
                        onClick = onMoveToPrivate,
                        enabled = !isMovingToPrivate,
                    ) {
                        if (isMovingToPrivate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = "Move to Private",
                                tint = Color.White,
                            )
                        }
                    }
                }
                SeslFavoriteButton(
                    isFavorite = item.isFavorite,
                    onClick = onFavorite,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onMore) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Samsung One UI SESL Spring-Animated Favorite Button.
 * Bounces smoothly with tactile haptics and expanding aura on favoriting.
 */
@Composable
private fun SeslFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val burstRadius = remember { androidx.compose.animation.core.Animatable(0f) }
    val burstAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Glowing aura burst ring on favorite toggle
        if (burstAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .size((burstRadius.value * 40).dp)
                    .clip(CircleShape)
                    .background(FavoriteRed.copy(alpha = burstAlpha.value * 0.45f))
            )
        }

        IconButton(
            onClick = {
                val willBeFavorite = !isFavorite
                if (willBeFavorite) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                } else {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
                coroutineScope.launch {
                    burstRadius.snapTo(0.2f)
                    burstAlpha.snapTo(0.85f)
                    launch {
                        scale.snapTo(1f)
                        scale.animateTo(
                            targetValue = 1.38f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            )
                        )
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            )
                        )
                    }
                    launch {
                        burstRadius.animateTo(1.35f, tween(260, easing = FastOutSlowInEasing))
                    }
                    launch {
                        burstAlpha.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                    }
                }
                onClick()
            },
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) FavoriteRed else Color.White,
            )
        }
    }
}

/**
 * EXIF details bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsBottomSheet(
    item: MediaItem,
    metadata: MediaMetadata?,
    onDismiss: () -> Unit,
    onOpenMap: (Double, Double) -> Unit,
    onRemoveLocation: () -> Unit,
    /** Editing EXIF in place needs a MediaStore row, which an external item has none of. */
    canRemoveLocation: Boolean = true,
    onActionSuggestion: ((String) -> Unit)? = null,
    onOpenCompressionStudio: (() -> Unit)? = null,
    onCompressItem: (() -> Unit)? = null,
    /** The real prediction for *this* file, or null when there is nothing worth promising. */
    quickOptimizeEstimate: com.pandagallery.app.domain.compression.CompressionEstimator.Estimate? = null,
    safetyBackup: SafetyVaultEntity? = null,
    onRevertOriginal: (() -> Unit)? = null,
    onSmartOptimize: (() -> Unit)? = null,
    onOpenGenerativeEdit: (() -> Unit)? = null,
    onRename: ((MediaItem, String) -> Unit)? = null,
    onToggleFavorite: ((MediaItem) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var userRating by remember(item.id, item.isFavorite) { mutableIntStateOf(if (item.isFavorite) 5 else 0) }
    var selectedTags by remember(item.id, item.isFavorite) {
        mutableStateOf(
            setOfNotNull(
                item.bucketName?.let { "#$it" },
                if (item.isFavorite) "#Favorite" else null,
                if (item.isCompressed) "#Compressed" else null,
                if (item.isVideo) "#Video" else "#Photo",
            )
        )
    }
    val allTags = remember {
        listOf("#Favorite", "#Vacation", "#Family", "#Nature", "#CameraOriginal", "#Screenshots", "#Documents", "#Portrait", "#Video", "#Photo")
    }
    var isEditingTitle by remember(item.id) { mutableStateOf(false) }
    var currentTitle by remember(item.id) { mutableStateOf(item.displayName.substringBeforeLast('.')) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Row: Title & Close/Edit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                )

                // C2PA Content Credentials Authenticity Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (item.isCompressed) Color(0xFF1E3A8A).copy(alpha = 0.4f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = if (item.isCompressed) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Verified,
                            contentDescription = "C2PA Verified",
                            tint = if (item.isCompressed) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = if (item.isCompressed) "C2PA Verified Archive" else "C2PA Camera Original",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                            ),
                            color = if (item.isCompressed) Color(0xFF93C5FD) else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // In-place editable Title / Caption
            if (isEditingTitle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = currentTitle,
                        onValueChange = { currentTitle = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("Title") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    IconButton(onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        val trimmed = currentTitle.trim()
                        if (trimmed.isNotBlank() && trimmed != item.displayName.substringBeforeLast('.')) {
                            onRename?.invoke(item, trimmed)
                        }
                        isEditingTitle = false
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isEditingTitle = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Samsung Gallery 15.9 5-Star Rating Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Rating",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (star in 1..5) {
                        Icon(
                            imageVector = if (star <= userRating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "$star stars",
                            tint = if (star <= userRating) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    val newRating = if (userRating == star) 0 else star
                                    userRating = newRating
                                    if (newRating == 5 && !item.isFavorite) {
                                        onToggleFavorite?.invoke(item)
                                    } else if (newRating == 0 && item.isFavorite) {
                                        onToggleFavorite?.invoke(item)
                                    }
                                }
                        )
                    }
                }
            }

            // Interactive Tag Pills Row (#Tags)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    ),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(allTags) { tag ->
                        val isSelected = selectedTags.contains(tag)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(
                                0.8.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    val willBeSelected = !isSelected
                                    selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                                    if (tag == "#Favorite" && willBeSelected != item.isFavorite) {
                                        onToggleFavorite?.invoke(item)
                                    }
                                }
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            // Reversible Safety Net Banner Card
            if (safetyBackup != null) {
                val daysRemaining = remember(safetyBackup.expiresAt) {
                    val diff = safetyBackup.expiresAt - System.currentTimeMillis()
                    (diff / (1000L * 60 * 60 * 24)).coerceAtLeast(1)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F2618),
                    border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    // Retention is a setting (and can be "forever"), so no fixed day count here.
                                    text = "Safety Net Active",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF4ADE80),
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF22C55E).copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = if (safetyBackup.expiresAt == Long.MAX_VALUE) "No expiry" else "$daysRemaining d left",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF86EFAC),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = "Uncompressed original (${android.text.format.Formatter.formatShortFileSize(context, safetyBackup.originalBytes)}) is safely stored in local vault. Revert anytime without quality loss.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD1D5DB),
                        )
                        Button(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                onRevertOriginal?.invoke()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22C55E),
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Undo,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Revert to Original",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // AI Suggestions Recommendation Strip (Samsung Gallery 15.9 style)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "AI Suggestions",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    ),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    item {
                        SuggestionChip(
                            icon = "✨",
                            label = "Remaster",
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                onActionSuggestion?.invoke("remaster")
                            },
                        )
                    }
                    // Was a hardcoded "65%" for video and "75%" for images, quoted per file
                    // regardless of the file. On an already-compressed clip the true answer is
                    // zero, and the estimator two lines away knows it — so quote that, and say
                    // nothing about savings when it has nothing to promise.
                    val estimatedSavings = quickOptimizeEstimate
                        ?.takeIf { it.isMeaningful && it.savingsPercent > 0 }
                        ?.let { " (Save ~${it.savingsPercent}%)" }
                        .orEmpty()
                    item {
                        SuggestionChip(
                            icon = "🗜️",
                            label = "Optimize Storage$estimatedSavings",
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                if (onSmartOptimize != null) {
                                    onSmartOptimize()
                                } else if (item.isImage && onOpenCompressionStudio != null) {
                                    onOpenCompressionStudio()
                                } else {
                                    onCompressItem?.invoke()
                                }
                            },
                        )
                    }
                    if (item.isImage) {
                        item {
                            SuggestionChip(
                                icon = "👤",
                                label = "Add Portrait Blur",
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    onActionSuggestion?.invoke("portrait")
                                },
                            )
                        }
                        item {
                            SuggestionChip(
                                icon = "🧹",
                                label = "Erase Clutter",
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    onActionSuggestion?.invoke("eraser")
                                },
                            )
                        }
                        item {
                            SuggestionChip(
                                icon = "⏱️",
                                label = "24-hr Time Lapse",
                                onClick = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    onActionSuggestion?.invoke("timelapse")
                                },
                            )
                        }
                        if (onOpenGenerativeEdit != null) {
                            item {
                                SuggestionChip(
                                    icon = "✨",
                                    label = "Generative Edit (AI Fill)",
                                    onClick = {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                        onOpenGenerativeEdit()
                                    },
                                )
                            }
                        }
                    }   // end if (item.isImage)
                }   // end LazyRow content lambda
            }   // end Column (AI Suggestions)


            // Photographic Exposure HUD Card (f-stop, Shutter, ISO, Focal Length)
            if (metadata?.aperture != null || metadata?.exposureTime != null || metadata?.iso != null || metadata?.focalLength != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        metadata.aperture?.let {
                            HudItem(label = "Aperture", value = "f/%.1f".format(it))
                        }
                        metadata.exposureTime?.let {
                            HudItem(label = "Shutter", value = it)
                        }
                        metadata.iso?.let {
                            HudItem(label = "ISO", value = it.toString())
                        }
                        metadata.focalLength?.let {
                            HudItem(label = "Focal Length", value = "%.1fmm".format(it))
                        }
                    }
                }
            }

            DetailRow("File name", item.displayName)
            DetailRow("Type", item.mimeType)
            metadata?.let { DetailRow("Format", it.format.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)) }
            val compressionPercent = item.compressionPercent
            val originalSize = item.formattedOriginalSize
            if (compressionPercent != null && originalSize != null) {
                DetailRow("Compression saved", "$compressionPercent%")
                DetailRow("Original size", originalSize)
                DetailRow("Current size", item.formattedSize)
            } else {
                DetailRow("Size", item.formattedSize)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Size",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.4f),
                        )
                        Text(
                            text = item.formattedSize,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.6f),
                        )
                    }
                    if (item.isImage && onOpenCompressionStudio != null) {
                        Surface(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                onOpenCompressionStudio()
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "✨ Compress",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            DetailRow("Resolution", item.resolution)

            item.formattedDuration?.let {
                DetailRow("Duration", it)
            }

            item.bucketName?.let {
                DetailRow("Album", it)
            }

            item.relativePath?.let {
                DetailRow("Path", it)
            }

            if (metadata?.cameraMake != null || metadata?.cameraModel != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF142416),
                    border = BorderStroke(0.8.dp, Color(0xFF2E592F)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Verified,
                            contentDescription = null,
                            tint = Color(0xFF80E386),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Verified Hardware Capture · Original Sensor Data",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF80E386),
                        )
                    }
                }
            }

            metadata?.cameraMake?.let { DetailRow("Camera make", it) }
            metadata?.cameraModel?.let { DetailRow("Camera model", it) }
            metadata?.lensModel?.let { DetailRow("Lens", it) }
            metadata?.aperture?.let { DetailRow("Aperture", "f/%.1f".format(it)) }
            metadata?.exposureTime?.let { DetailRow("Exposure", it) }
            metadata?.iso?.let { DetailRow("ISO", it.toString()) }
            metadata?.focalLength?.let { DetailRow("Focal length", "%.1f mm".format(it)) }
            metadata?.dateTimeOriginal?.let { DetailRow("Captured", it) }
            metadata?.altitude?.let { DetailRow("Altitude", "%.1f m".format(it)) }

            if (metadata?.hasLocation == true) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF161618),
                    border = BorderStroke(1.dp, Color(0xFF28282B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Location Coordinates",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                                Text(
                                    text = "%.5f, %.5f".format(metadata.latitude, metadata.longitude),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.65f),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stylized Mini Map Radar/Grid Canvas (Samsung Gallery Geotag Preview)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F141C))
                                .border(0.8.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                .clickable { onOpenMap(metadata.latitude!!, metadata.longitude!!) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val step = 22.dp.toPx()
                                var x = 0f
                                while (x < size.width) {
                                    drawLine(Color(0xFF1E293B).copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                                    x += step
                                }
                                var y = 0f
                                while (y < size.height) {
                                    drawLine(Color(0xFF1E293B).copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                    y += step
                                }
                                drawCircle(Color.White.copy(alpha = 0.15f), radius = 34.dp.toPx(), center = Offset(cx, cy))
                                drawCircle(Color.White.copy(alpha = 0.35f), radius = 18.dp.toPx(), center = Offset(cx, cy))
                                drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(cx, cy))
                            }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(12.dp))
                                Text("GPS Geotag Pin", color = Color(0xFFE2E8F0), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onOpenMap(metadata.latitude!!, metadata.longitude!!) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 38.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Map,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open in Maps",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (canRemoveLocation) {
                                OutlinedButton(
                                    onClick = onRemoveLocation,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 38.dp),
                                ) {
                                    Text(
                                        text = "Remove",
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SuggestionChip(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = icon, fontSize = 13.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HudItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            ),
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bufferedPosition: Long = 0L,
    onSeekStarted: (() -> Unit)? = null,
    onSeekFinished: ((Long) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()

    var internalDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val isInteracting = internalDragging || isPressed || isDragged

    LaunchedEffect(isInteracting) {
        if (isInteracting) {
            onSeekStarted?.invoke()
        }
    }

    val currentProgress = if (internalDragging) {
        dragProgress
    } else if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f

    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) 7.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 450f),
        label = "trackHeight",
    )
    val thumbAnimSize by animateDpAsState(
        targetValue = if (isInteracting) 20.dp else 14.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 450f),
        label = "thumbSize",
    )
    val haloAnimSize by animateDpAsState(
        targetValue = if (isInteracting) 34.dp else 0.dp,
        animationSpec = tween(120),
        label = "haloSize",
    )

    Slider(
        value = currentProgress.coerceIn(0f, 1f),
        onValueChange = { newProgress ->
            if (!internalDragging) {
                internalDragging = true
                onSeekStarted?.invoke()
            }
            dragProgress = newProgress
            if (duration > 0) {
                val targetMs = (newProgress * duration).toLong().coerceIn(0L, duration)
                onSeek(targetMs)
            }
        },
        onValueChangeFinished = {
            internalDragging = false
            if (duration > 0) {
                val targetMs = (dragProgress * duration).toLong().coerceIn(0L, duration)
                onSeekFinished?.invoke(targetMs)
            }
        },
        enabled = duration > 0,
        modifier = modifier.height(48.dp),
        interactionSource = interactionSource,
        thumb = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.wrapContentSize(unbounded = true),
            ) {
                // Floating timestamp preview tooltip bubble above the thumb
                androidx.compose.animation.AnimatedVisibility(
                    visible = isInteracting && duration > 0,
                    enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.8f),
                    exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.8f),
                    modifier = Modifier.offset(y = (-40).dp),
                ) {
                    val previewMs = (currentProgress * duration).toLong().coerceIn(0L, duration)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xEE0F172A),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f)),
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color(0xFF38BDF8), CircleShape)
                            )
                            Text(
                                text = formatTime(previewMs),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                ),
                            )
                        }
                    }
                }

                // Outer luminous halo when active
                if (haloAnimSize > 0.dp) {
                    Box(
                        modifier = Modifier
                            .size(haloAnimSize)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.28f), CircleShape)
                    )
                }

                // Main Thumb Circle
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(thumbAnimSize),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(if (isInteracting) 6.dp else 4.dp)
                                .background(Color(0xFF0284C7), CircleShape)
                        )
                    }
                }
            }
        },
        track = { sliderState ->
            val fraction = sliderState.value.coerceIn(0f, 1f)
            val bufferedFraction = if (duration > 0) {
                (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else 0f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
            ) {
                val strokeWidth = size.height
                val cornerRadius = strokeWidth / 2f
                val y = size.height / 2f
                val startX = cornerRadius
                val endX = (size.width - cornerRadius).coerceAtLeast(startX)

                // 1. Background track (unplayed)
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )

                // 2. Buffered track
                if (bufferedFraction > 0.001f && endX > startX) {
                    val bufferedEnd = (size.width * bufferedFraction).coerceIn(startX, endX)
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(startX, y),
                        end = Offset(bufferedEnd, y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }

                // 3. Played progress track
                if (fraction > 0.001f && endX > startX) {
                    val playedEnd = (size.width * fraction).coerceIn(startX, endX)
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0284C7), // Sky Blue 600
                                Color(0xFF38BDF8), // Sky Blue 400
                                Color(0xFF7DD3FC), // Sky Blue 300
                            ),
                            startX = 0f,
                            endX = size.width,
                        ),
                        start = Offset(startX, y),
                        end = Offset(playedEnd, y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerThumbnailFilmstrip(
    mediaItems: List<MediaItem>,
    currentPage: Int,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean = false,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentPage) {
        if (currentPage in mediaItems.indices) {
            listState.animateScrollToItem(
                index = (currentPage - 2).coerceAtLeast(0)
            )
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = mediaItems,
            key = { _: Int, item: MediaItem -> item.id },
        ) { index: Int, item: MediaItem ->
            val isCurrent = index == currentPage
            val isSelected = item.id in selectedIds
            val context = LocalContext.current

            val imageRequest = remember(item.uri) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(384)
                    .crossfade(false)
                    .memoryCacheKey("${item.uri}_thumb")
                    .placeholderMemoryCacheKey("${item.uri}_thumb")
                    .diskCacheKey("${item.uri}_thumb")
                    .build()
            }

            Box(
                modifier = Modifier
                    .size(if (isCurrent) 48.dp else 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (isCurrent) {
                            Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp))
                        } else if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        } else {
                            Modifier
                        }
                    )
                    .combinedClickable(
                        onClick = {
                            if (isCurrent && (isSelectionMode || item.id in selectedIds)) {
                                onItemLongClick(item.id)
                            } else {
                                onItemClick(index)
                            }
                        },
                        onLongClick = { onItemLongClick(item.id) },
                    ),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    )
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .background(Color.White, CircleShape),
                    )
                }

                if (item.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircleFilled,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomStart)
                            .padding(2.dp),
                    )
                }
            }
        }
    }
}
