package com.pandagallery.app.ui.story

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import coil3.compose.AsyncImage
import com.pandagallery.app.data.local.dao.MediaDao
import com.pandagallery.app.data.local.entity.StoryEntity
import com.pandagallery.app.data.repository.toDomain
import com.pandagallery.app.data.story.StoryEngine
import com.pandagallery.app.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    storyEngine: StoryEngine,
    mediaDao: MediaDao,
    onBack: () -> Unit,
    onPlayStory: (Long) -> Unit = {},
    onCreateCollage: (List<Long>) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StoryViewModel? = null,
) {
    val context = LocalContext.current
    val stories by storyEngine.observeAllStories().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var activeStoryEntity by remember { mutableStateOf<StoryEntity?>(null) }
    var activeStoryMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var showCreateStoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stories & Highlights", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateStoryDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Story")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (showCreateStoryDialog) {
            CreateStoryDialog(
                onDismiss = { showCreateStoryDialog = false },
                onConfirm = { title, template, count ->
                    scope.launch {
                        val allIds = mediaDao.getAllIds().take(count)
                        if (allIds.isNotEmpty()) {
                            if (viewModel != null) {
                                viewModel.createStory(title = title, mediaIds = allIds, template = template)
                            } else {
                                storyEngine.createHighlightReel(title = title, mediaIds = allIds, templateName = template)
                            }
                            Toast.makeText(context, "Story \"$title\" created", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No media available to create a story", Toast.LENGTH_SHORT).show()
                        }
                        showCreateStoryDialog = false
                    }
                }
            )
        }
        if (stories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No stories created yet.\nTap + to generate a story highlight reel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(stories, key = { it.id }) { story ->
                    var coverUri by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(story.coverMediaId) {
                        coverUri = mediaDao.getMediaById(story.coverMediaId)?.uri
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(0.75f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.DarkGray)
                            .clickable {
                                scope.launch {
                                    onPlayStory(story.id)
                                    val clips = storyEngine.getClipsForStory(story.id)
                                    val items = withContext(Dispatchers.IO) {
                                        clips.mapNotNull { clip ->
                                            mediaDao.getMediaById(clip.mediaId)?.toDomain()
                                        }
                                    }
                                    if (items.isNotEmpty()) {
                                        activeStoryMedia = items
                                        activeStoryEntity = story
                                    }
                                }
                            }
                    ) {
                        if (coverUri != null) {
                            AsyncImage(
                                model = coverUri,
                                contentDescription = story.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xCC000000)),
                                        startY = 150f
                                    )
                                )
                        )

                        // Story play badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                                .background(Color(0x88000000), RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White
                            )
                        }

                        // Story metadata bottom
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = story.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${story.durationSeconds}s • ${story.templateName}",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch { storyEngine.deleteStory(story.id) }
                            },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    activeStoryEntity?.let { story ->
        StoryPlayerDialog(
            title = story.title,
            subtitle = story.subtitle ?: "${story.durationSeconds}s",
            mediaItems = activeStoryMedia,
            onDismiss = {
                activeStoryEntity = null
                activeStoryMedia = emptyList()
            },
            onCreateCollage = { ids ->
                activeStoryEntity = null
                activeStoryMedia = emptyList()
                onCreateCollage(ids)
            },
            onCompressStory = { items ->
                activeStoryEntity = null
                activeStoryMedia = emptyList()
                viewModel?.compressStory(items)
            },
            onExportReel = { items, storyTitle, soundtrack, onProgress, onSuccess, onError ->
                viewModel?.exportStoryReel(
                    title = storyTitle,
                    items = items,
                    soundtrack = soundtrack,
                    onProgress = onProgress,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
        )
    }
}

@Composable
private fun CreateStoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, template: String, count: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf("Classic") }
    var itemCount by remember { mutableFloatStateOf(6f) }
    val templates = listOf("Classic", "Cinematic", "Dynamic", "Minimal")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Story Highlight") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Story Title") },
                    placeholder = { Text("e.g. Weekend Trip") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Template Style",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { template ->
                        FilterChip(
                            selected = selectedTemplate == template,
                            onClick = { selectedTemplate = template },
                            label = { Text(template, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }

                Text(
                    text = "Photo Count: ${itemCount.toInt()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = itemCount,
                    onValueChange = { itemCount = it },
                    valueRange = 3f..20f,
                    steps = 16,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifEmpty { "Story ${System.currentTimeMillis() % 10000}" }
                    onConfirm(finalTitle, selectedTemplate, itemCount.toInt())
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
            ) {
                Text(
                    text = "Create",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
