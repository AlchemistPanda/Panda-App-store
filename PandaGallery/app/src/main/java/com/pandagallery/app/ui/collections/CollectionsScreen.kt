package com.pandagallery.app.ui.collections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pandagallery.app.domain.model.SmartCollection
import com.pandagallery.app.domain.model.StoryHighlight
import com.pandagallery.app.ui.components.SeslPillButton
import com.pandagallery.app.ui.components.SeslReachabilityHeader
import com.pandagallery.app.ui.components.adaptiveColumnCount
import com.pandagallery.app.ui.story.StoryPlayerDialog

/**
 * Authentic Samsung One UI Stories & Memories Hub.
 * Clones Samsung Gallery's dedicated "Stories" tab:
 * - SeslReachabilityHeader with large One UI typography and action pill.
 * - Featured Story Hero card with Ken Burns slow pan & zoom and play button.
 * - Horizontal carousel of Memories ("On This Day", "Favorites", "Recents", "Custom Story").
 * - Clean Media Types & Smart Collections grid below.
 * - Fullscreen interactive StoryPlayerDialog with collage creation.
 */
@Composable
fun CollectionsScreen(
    onFavoritesClick: () -> Unit,
    onVideosClick: () -> Unit,
    onRecentClick: () -> Unit,
    onTrashClick: () -> Unit,
    onSmartCollectionClick: (SmartCollection) -> Unit,
    onCleanupClick: () -> Unit,
    onSmartOrganizerClick: () -> Unit,
    onPeopleClick: () -> Unit = {},
    onCreateCollage: (List<Long>) -> Unit = {},
    onCreateStory: () -> Unit = {},
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeStory by remember { mutableStateOf<StoryHighlight?>(null) }

    val tools = listOf(
        CollectionItem("Panda Sweep", "Reclaim storage", Icons.Outlined.DeleteSweep, null, onCleanupClick),
        CollectionItem("People & pets", "Recognized faces & groups", Icons.Outlined.People, null, onPeopleClick),
        CollectionItem("Memories & stacks", "Recaps and groups", Icons.Outlined.AutoAwesome, null, onSmartOrganizerClick),
    )
    val collections = listOf(
        CollectionItem("Favorites", null, Icons.Filled.Favorite, CollectionKey.FAVORITES, onFavoritesClick),
        CollectionItem("Videos", null, Icons.Outlined.VideoLibrary, CollectionKey.VIDEOS, onVideosClick),
        CollectionItem("Recent", null, Icons.Outlined.History, CollectionKey.RECENT, onRecentClick),
        CollectionItem("Trash", null, Icons.Outlined.Delete, CollectionKey.TRASH, onTrashClick),
        CollectionItem("Screenshots", null, Icons.Outlined.Screenshot, CollectionKey.Smart(SmartCollection.SCREENSHOTS)) { onSmartCollectionClick(SmartCollection.SCREENSHOTS) },
        CollectionItem("GIFs", null, Icons.Outlined.GifBox, CollectionKey.Smart(SmartCollection.GIFS)) { onSmartCollectionClick(SmartCollection.GIFS) },
        CollectionItem("Documents", null, Icons.Outlined.Description, CollectionKey.Smart(SmartCollection.DOCUMENTS)) { onSmartCollectionClick(SmartCollection.DOCUMENTS) },
        CollectionItem("Downloads", null, Icons.Outlined.Download, CollectionKey.Smart(SmartCollection.DOWNLOADS)) { onSmartCollectionClick(SmartCollection.DOWNLOADS) },
        CollectionItem("Large files", null, Icons.Outlined.Storage, CollectionKey.Smart(SmartCollection.LARGE_FILES)) { onSmartCollectionClick(SmartCollection.LARGE_FILES) },
        CollectionItem("Recently added", null, Icons.Outlined.NewReleases, CollectionKey.Smart(SmartCollection.RECENTLY_ADDED)) { onSmartCollectionClick(SmartCollection.RECENTLY_ADDED) },
        CollectionItem("Locations", null, Icons.Outlined.Place, CollectionKey.Smart(SmartCollection.LOCATIONS)) { onSmartCollectionClick(SmartCollection.LOCATIONS) },
    )
    // Once counts have loaded, empty smart collections are dropped rather than leading to a dead end.
    val visibleCollections = if (uiState.isLoading) {
        collections
    } else {
        collections.filter { item ->
            val summary = uiState.summaries[item.key]
            item.key == CollectionKey.FAVORITES || item.key == CollectionKey.TRASH || (summary?.count ?: 0) > 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        SeslReachabilityHeader(
            title = "Stories",
            subtitle = if (uiState.stories.isNotEmpty()) {
                "${uiState.stories.size} memories to relive"
            } else {
                "Relive cherished moments"
            },
            action = {
                SeslPillButton(
                    text = "Create story",
                    icon = Icons.Filled.Add,
                    onClick = onCreateStory,
                )
            },
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(adaptiveColumnCount(2, maxWidth)),
                contentPadding = PaddingValues(bottom = 138.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1. Featured Hero Story Card (if any story exists)
                if (uiState.stories.isNotEmpty()) {
                    val heroStory = uiState.stories.first()
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FeaturedStoryHeroCard(
                            story = heroStory,
                            onClick = { activeStory = heroStory },
                        )
                    }
                }

                // 2. Stories Carousel Row (if there are multiple stories or quick create)
                if (uiState.stories.size > 1) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = "Memories & Highlights",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(uiState.stories.drop(1), key = { it.id }) { story ->
                                    CollectionsStoryCard(
                                        story = story,
                                        onClick = { activeStory = story },
                                    )
                                }
                                // Create custom story card
                                item {
                                    CreateStoryActionCard(onClick = onCreateStory)
                                }
                            }
                        }
                    }
                }

                // 3. Section Title for Media Collections
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Media Types & Collections",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }

                // 4. Utility & Smart Tools
                items(tools, key = { it.label }, span = { GridItemSpan(maxLineSpan) }) { item ->
                    CollectionTile(item = item, summary = null, prominent = true)
                }

                // 5. Media Type Collections
                items(visibleCollections, key = { it.label }) { item ->
                    CollectionTile(item = item, summary = uiState.summaries[item.key])
                }
            }
        }
    }

    // Full-screen Samsung One UI Story Player Dialog
    activeStory?.let { story ->
        val storyMediaItems = remember(story, uiState.allMedia) {
            story.items.ifEmpty {
                val idSet = story.mediaIds.toSet()
                val found = uiState.allMedia.filter { it.id in idSet }
                found.ifEmpty { uiState.allMedia.take(12) }
            }
        }
        StoryPlayerDialog(
            title = story.title,
            subtitle = story.subtitle,
            mediaItems = storyMediaItems,
            badge = story.badge,
            dateRangeLabel = story.dateRangeLabel,
            mood = story.mood,
            estimatedSavingsBytes = story.estimatedSavingsBytes,
            onDismiss = { activeStory = null },
            onCreateCollage = { ids ->
                activeStory = null
                onCreateCollage(ids)
            },
            onCompressStory = { items ->
                activeStory = null
                viewModel.compressStory(items)
            },
            onExportReel = { items, storyTitle, soundtrack, onProgress, onSuccess, onError ->
                viewModel.exportStoryReel(
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

/**
 * Large immersive featured story hero card with subtle Ken Burns animation,
 * dark gradient scrim, and a frosted Play Slideshow button.
 */
@Composable
private fun FeaturedStoryHeroCard(
    story: StoryHighlight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_ken_burns")
    val kenBurnsScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hero_scale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF18181A))
            .clickable(onClick = onClick),
    ) {
        if (story.coverUri != null) {
            val context = LocalContext.current
            val coverRequest = remember(story.coverUri) {
                ImageRequest.Builder(context)
                    .data(story.coverUri)
                    .size(720)
                    .crossfade(true)
                    .memoryCacheKey("${story.coverUri}_hero")
                    .placeholderMemoryCacheKey("${story.coverUri}_hero")
                    .build()
            }
            AsyncImage(
                model = coverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = kenBurnsScale
                        scaleY = kenBurnsScale
                    },
            )
        }

        // Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.82f),
                        ),
                    ),
                ),
        )

        // Floating Story Badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(14.dp)
                .align(Alignment.TopStart),
        ) {
            Text(
                text = story.badge,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // Frosted Play story button
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color.White.copy(alpha = 0.22f),
            modifier = Modifier
                .padding(14.dp)
                .align(Alignment.TopEnd),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play story",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Play",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }

        // Title and Subtitle at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = story.title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = story.subtitle,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Samsung One UI story card for the horizontal carousel.
 */
@Composable
private fun CollectionsStoryCard(
    story: StoryHighlight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "carousel_ken_burns")
    val kenBurnsScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "card_scale",
    )

    Box(
        modifier = modifier
            .width(160.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick),
    ) {
        if (story.coverUri != null) {
            val context = LocalContext.current
            val coverRequest = remember(story.coverUri) {
                ImageRequest.Builder(context)
                    .data(story.coverUri)
                    .size(512)
                    .crossfade(true)
                    .memoryCacheKey("${story.coverUri}_thumb")
                    .placeholderMemoryCacheKey("${story.coverUri}_thumb")
                    .build()
            }
            AsyncImage(
                model = coverRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = kenBurnsScale
                        scaleY = kenBurnsScale
                    },
            )
        }

        // Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )

        // Floating Story Badge
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(10.dp)
                .align(Alignment.TopStart),
        ) {
            Text(
                text = story.badge,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        // Title and Subtitle at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = story.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = story.subtitle,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Tile that invites the user to create their own story or collage.
 */
@Composable
private fun CreateStoryActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .width(150.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Create story",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "New Story",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Pick photos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CollectionTile(
    item: CollectionItem,
    summary: CollectionSummary?,
    prominent: Boolean = false,
) {
    val shape = RoundedCornerShape(22.dp)
    val container = if (prominent) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (prominent) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val countLabel = summary?.count?.let { "$it ${if (it == 1) "item" else "items"}" } ?: item.subtitle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(shape)
            .background(container)
            .clickable(onClick = item.onClick)
            .semantics {
                contentDescription = listOfNotNull(item.label, countLabel).joinToString(", ")
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(content.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            val coverUri = summary?.coverUri
            if (coverUri != null) {
                val context = LocalContext.current
                val coverRequest = remember(coverUri) {
                    ImageRequest.Builder(context)
                        .data(coverUri)
                        .size(256)
                        .crossfade(false)
                        .memoryCacheKey("${coverUri}_thumb")
                        .placeholderMemoryCacheKey("${coverUri}_thumb")
                        .build()
                }
                AsyncImage(
                    model = coverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            ),
                        ),
                )
            }
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (coverUri != null) Color.White else content,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = item.label,
                color = content,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (countLabel != null) {
                Text(
                    text = countLabel,
                    color = content.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

private data class CollectionItem(
    val label: String,
    val subtitle: String?,
    val icon: ImageVector,
    val key: CollectionKey?,
    val onClick: () -> Unit,
)
