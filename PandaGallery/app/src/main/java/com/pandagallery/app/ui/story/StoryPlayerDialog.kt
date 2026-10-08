package com.pandagallery.app.ui.story

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.model.formatFileSize
import kotlinx.coroutines.delay

/**
 * Authentic Samsung One UI Fullscreen Story & Memories Player.
 * Features:
 * - Multi-segment top progress bar (one per photo in the story).
 * - Automatic slide progression (4.5s per photo) with smooth crossfade.
 * - Ken Burns animated slow pan & zoom for alive, emotional storytelling.
 * - Animated typography intro title card for cinematic presentation.
 * - Audio visualizer equalizer wave for ambient soundtrack.
 * - Tap navigation: tap right to skip, tap left to go back.
 * - Press & hold to pause playback.
 * - Actions: "Create Collage", "Compress Story Superpower", "Share", and Dismiss.
 */
@Composable
fun StoryPlayerDialog(
    title: String,
    subtitle: String,
    mediaItems: List<MediaItem>,
    badge: String = "✨ Highlight",
    dateRangeLabel: String = "",
    mood: String = "Serene",
    estimatedSavingsBytes: Long = 0L,
    onDismiss: () -> Unit,
    onCreateCollage: (List<Long>) -> Unit,
    onCompressStory: ((List<MediaItem>) -> Unit)? = null,
    onExportReel: ((List<MediaItem>, String, com.pandagallery.app.data.editing.BackgroundMusicTrack, (Int) -> Unit, (android.net.Uri) -> Unit, (String) -> Unit) -> Unit)? = null,
) {
    if (mediaItems.isEmpty()) {
        onDismiss()
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val context = LocalContext.current
        var currentIndex by remember { mutableIntStateOf(0) }
        var isPaused by remember { mutableStateOf(false) }
        var isMuted by remember { mutableStateOf(false) }
        var showIntroCard by remember { mutableStateOf(true) }
        var showCompressDialog by remember { mutableStateOf(false) }
        var isExporting by remember { mutableStateOf(false) }
        var exportProgress by remember { mutableIntStateOf(0) }
        var selectedSoundtrack by remember {
            mutableStateOf(
                when {
                    mood.contains("Lofi", ignoreCase = true) -> com.pandagallery.app.data.editing.BackgroundMusicTrack.CHILL_LOFI
                    mood.contains("Pulse", ignoreCase = true) || mood.contains("Drama", ignoreCase = true) -> com.pandagallery.app.data.editing.BackgroundMusicTrack.CINEMATIC_PULSE
                    mood.contains("Sunset", ignoreCase = true) || mood.contains("Groove", ignoreCase = true) -> com.pandagallery.app.data.editing.BackgroundMusicTrack.SUNSET_GROOVE
                    else -> com.pandagallery.app.data.editing.BackgroundMusicTrack.ACOUSTIC_BREEZE
                }
            )
        }
        val currentItem = mediaItems.getOrNull(currentIndex) ?: mediaItems.first()

        // Auto-dismiss the intro title card after 2.2 seconds
        LaunchedEffect(Unit) {
            delay(2200L)
            showIntroCard = false
        }

        // Soundtrack audio visualizer wave animation
        val waveTransition = rememberInfiniteTransition(label = "wave_anim")
        val bar1Height by waveTransition.animateFloat(
            initialValue = 4f,
            targetValue = 14f,
            animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
            label = "b1",
        )
        val bar2Height by waveTransition.animateFloat(
            initialValue = 12f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
            label = "b2",
        )
        val bar3Height by waveTransition.animateFloat(
            initialValue = 5f,
            targetValue = 16f,
            animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse),
            label = "b3",
        )

        // Ken Burns motion animation per slide
        val transition = rememberInfiniteTransition(label = "story_ken_burns")
        val kenBurnsScale by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "story_scale",
        )

        // Slide progression timer
        val slideDurationMs = 4500L
        var progressFraction by remember(currentIndex) { mutableFloatStateOf(0f) }

        LaunchedEffect(currentIndex, isPaused) {
            if (isPaused) return@LaunchedEffect
            val intervalMs = 50L
            val step = intervalMs.toFloat() / slideDurationMs.toFloat()
            while (progressFraction < 1f) {
                delay(intervalMs)
                if (!isPaused) {
                    progressFraction = (progressFraction + step).coerceIn(0f, 1f)
                }
            }
            if (currentIndex < mediaItems.size - 1) {
                currentIndex++
                progressFraction = 0f
            } else {
                onDismiss()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(currentIndex) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            val width = size.width
                            if (offset.x < width * 0.35f) {
                                // Tap left: previous slide
                                if (currentIndex > 0) {
                                    currentIndex--
                                    progressFraction = 0f
                                }
                            } else {
                                // Tap right: next slide
                                if (currentIndex < mediaItems.size - 1) {
                                    currentIndex++
                                    progressFraction = 0f
                                } else {
                                    onDismiss()
                                }
                            }
                        },
                    )
                },
        ) {
            // Background photo with Ken Burns effect
            val imageRequest = remember(currentItem.uri) {
                ImageRequest.Builder(context)
                    .data(currentItem.uri)
                    .crossfade(true)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = kenBurnsScale
                        scaleY = kenBurnsScale
                    },
            )

            // Top gradient overlay for header visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            // Top Header: Progress Bar Segments & Story Title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Multi-segment progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    mediaItems.forEachIndexed { index, _ ->
                        val segmentProgress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progressFraction
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(Color.White.copy(alpha = 0.35f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(segmentProgress)
                                    .background(Color.White),
                            )
                        }
                    }
                }

                // Story Title, Subtitle, and Header Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                            ),
                            color = Color.White,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Ambient soundtrack equalizer wave bars
                        if (!isMuted) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier
                                    .height(18.dp)
                                    .padding(end = 4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(bar1Height.dp)
                                        .background(Color(0xFF00E676), RoundedCornerShape(1.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(bar2Height.dp)
                                        .background(Color(0xFF00E676), RoundedCornerShape(1.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(bar3Height.dp)
                                        .background(Color(0xFF00E676), RoundedCornerShape(1.dp))
                                )
                            }
                        }

                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = if (isMuted) "Unmute audio" else "Mute audio",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close story",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // Typographic Title Intro Card Overlay
            AnimatedVisibility(
                visible = showIntroCard,
                enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.92f),
                exit = fadeOut(tween(500)) + scaleOut(targetScale = 1.05f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.clickable { showIntroCard = false },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.2f),
                        ) {
                            Text(
                                text = "$badge • $mood",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        if (dateRangeLabel.isNotBlank()) {
                            Text(
                                text = dateRangeLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                        Text(
                            text = "${mediaItems.size} moments captured",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // Bottom gradient overlay for action bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                    ),
            )

            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // One-tap Collage creation button
                    Surface(
                        onClick = {
                            onDismiss()
                            onCreateCollage(mediaItems.map { it.id })
                        },
                        shape = RoundedCornerShape(50.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesomeMosaic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Collage",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    }

                    // Highlight Reel MP4 Export
                    if (onExportReel != null) {
                        Surface(
                            onClick = {
                                isPaused = true
                                isExporting = true
                                exportProgress = 0
                                onExportReel(
                                    mediaItems,
                                    title,
                                    selectedSoundtrack,
                                    { p -> exportProgress = p },
                                    { uri ->
                                        isExporting = false
                                        android.widget.Toast.makeText(
                                            context,
                                            "Highlight reel exported to Movies/PandaGallery Stories",
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Highlight Reel"))
                                    },
                                    { errorMsg ->
                                        isExporting = false
                                        android.widget.Toast.makeText(
                                            context,
                                            "Export failed: $errorMsg",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            },
                            shape = RoundedCornerShape(50.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MovieCreation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Export Reel",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    ),
                                )
                            }
                        }
                    }

                    // Panda Signature Superpower: Compress Story
                    if (onCompressStory != null && estimatedSavingsBytes > 0L) {
                        Surface(
                            onClick = {
                                isPaused = true
                                showCompressDialog = true
                            },
                            shape = RoundedCornerShape(50.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF00E676).copy(alpha = 0.6f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Compress,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Save ~${formatFileSize(estimatedSavingsBytes)}",
                                    color = Color(0xFF00E676),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Share Story Photo
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = currentItem.mimeType
                            putExtra(Intent.EXTRA_STREAM, currentItem.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share photo"))
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .border(0.8.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Compress Confirmation Modal
            if (showCompressDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showCompressDialog = false
                        isPaused = false
                    },
                    icon = {
                        Icon(
                            Icons.Outlined.Compress,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(32.dp),
                        )
                    },
                    title = { Text("Compress Story Moments") },
                    text = {
                        Text(
                            "Optimize all ${mediaItems.size} photos in \"$title\" using Near-Lossless compression. " +
                            "Saves approximately ${formatFileSize(estimatedSavingsBytes)} of device storage while keeping photos visually pristine.",
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCompressDialog = false
                                isPaused = false
                                onCompressStory?.invoke(mediaItems)
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                        ) {
                            Text(
                                text = "Compress All",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showCompressDialog = false
                                isPaused = false
                            },
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }

            // Export Reel Progress Dialog
            if (isExporting) {
                AlertDialog(
                    onDismissRequest = { /* disallow dismiss during active export */ },
                    icon = {
                        Icon(
                            Icons.Outlined.MovieCreation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    },
                    title = { Text("Exporting Highlight Reel") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Rendering 1080p MP4 highlight video with \"${selectedSoundtrack.displayName}\" soundtrack...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                            LinearProgressIndicator(
                                progress = { exportProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.2f),
                            )
                            Text(
                                text = "$exportProgress%",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    confirmButton = {},
                )
            }
        }
    }
}
