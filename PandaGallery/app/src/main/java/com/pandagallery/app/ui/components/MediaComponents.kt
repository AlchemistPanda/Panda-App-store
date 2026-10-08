package com.pandagallery.app.ui.components

import com.pandagallery.app.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import android.net.Uri
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.view.HapticFeedbackConstants
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.ui.theme.CompressionBadge
import com.pandagallery.app.ui.theme.FavoriteRed

@Composable
fun AdaptiveSingleLineText(
    text: String,
    style: TextStyle,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val fittedFontSize = remember(text, style, minFontSize, maxWidthPx) {
            if (maxWidthPx <= 0 || text.isEmpty()) return@remember style.fontSize

            // Pass 1: Measure at default style.fontSize
            val initialResult = textMeasurer.measure(
                text = AnnotatedString(text),
                style = style.copy(fontSize = style.fontSize, fontWeight = fontWeight),
                maxLines = 1,
                softWrap = false,
            )
            if (initialResult.size.width <= maxWidthPx) {
                return@remember style.fontSize
            }

            // Single-step proportional calculation
            val minVal = minFontSize.value
            val maxVal = style.fontSize.value
            val ratio = (maxWidthPx.toFloat() / initialResult.size.width.toFloat()).coerceIn(0f, 1f)
            val estimatedVal = (maxVal * ratio).coerceIn(minVal, maxVal)
            val estimatedSp = estimatedVal.sp

            // Pass 2: Verify fitted size with slight nudge for kerning if needed
            val secondResult = textMeasurer.measure(
                text = AnnotatedString(text),
                style = style.copy(fontSize = estimatedSp, fontWeight = fontWeight),
                maxLines = 1,
                softWrap = false,
            )
            if (secondResult.size.width <= maxWidthPx) {
                estimatedSp
            } else {
                (estimatedVal - 0.5f).coerceAtLeast(minVal).sp
            }
        }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = style.copy(fontSize = fittedFontSize),
            color = color,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Single media thumbnail in the gallery grid.
 * Features:
 * - Animated selection checkmark
 * - Video duration overlay
 * - Favorite heart indicator
 * - Compressed item badge
 * - Gradient overlay for visibility
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnail(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleSelection: (() -> Unit)? = null,
    onExpandClick: (() -> Unit)? = null,
    statusLabel: String? = null,
    targetThumbnailSize: Int = 384,
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uri, item.dateModified) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(512)
            .crossfade(150)
            .memoryCacheKey("${item.uri}_thumb")
            .placeholderMemoryCacheKey("${item.uri}_thumb")
            .diskCacheKey("${item.uri}_thumb")
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        // Thumbnail image — fast hardware thumbnail with instant memory cache display
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Selection overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp),
                    )
            )
        }

        // Selection checkbox — standalone tap target so tapping the circle toggles, while tapping the image opens viewer
        if (isSelectionMode) {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = 0.75f,
                        stiffness = 350f,
                    )
                ) + fadeIn(animationSpec = tween(100)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            ) {
                SeslCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection?.invoke() ?: onClick() },
                    activeColor = MaterialTheme.colorScheme.primary,
                    size = 24.dp,
                )
            }
        }

        // Samsung Gallery-style expand button — allows inspecting photo in high resolution during multi-select
        if (isSelectionMode && onExpandClick != null) {
            val view = LocalView.current
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = 0.75f,
                        stiffness = 350f,
                    )
                ) + fadeIn(animationSpec = tween(100)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onExpandClick()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.gallery_ic_expand),
                        contentDescription = "Inspect photo in full screen",
                        modifier = Modifier.size(22.dp),
                        tint = Color.Unspecified,
                    )
                }
            }
        }

        // Bottom gradient for overlays
        if (item.isVideo || item.isFavorite || item.isCompressed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                        )
                    )
            )
        }

        // Video duration badge
        if (item.isVideo && item.formattedDuration != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = if (isSelectionMode && onExpandClick != null) 34.dp else 4.dp,
                        bottom = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = item.formattedDuration!!,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Favorite heart
        if (item.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Favorite",
                tint = FavoriteRed,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(16.dp),
            )
        }

        // Compression badge
        if (item.isCompressed) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(8.dp)
                    .background(CompressionBadge, CircleShape),
            )
        }


        statusLabel?.let { label ->
            val isExpired = label.equals("Expired", ignoreCase = true) || label.startsWith("0")
            val daysNumber = label.filter { it.isDigit() }.toIntOrNull()
            val isUrgent = isExpired || (daysNumber != null && daysNumber <= 3)

            val badgeColor = when {
                isExpired -> Color(0xFFD32F2F)
                isUrgent -> Color(0xFFE65100).copy(alpha = 0.92f)
                else -> Color.Black.copy(alpha = 0.70f)
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = if (isUrgent) Icons.Outlined.WarningAmber else Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Timeline section header — "Today", "Yesterday", "August 3, 2026"
 */
@Composable
fun TimelineSectionHeader(
    label: String,
    itemCount: Int,
    selectedCount: Int? = null,
    onToggleSelection: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selectedCount != null && onToggleSelection != null) {
                    Modifier.clickable(onClick = onToggleSelection)
                } else Modifier
            )
            .semantics(mergeDescendants = true) {
                contentDescription = if (selectedCount != null) {
                    "$label, $selectedCount of $itemCount selected"
                } else {
                    "$label, $itemCount items"
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (selectedCount == null) "$itemCount" else "$selectedCount/$itemCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedCount != null && onToggleSelection != null) {
                Spacer(Modifier.width(4.dp))
                // The same circular selector the thumbnails use. A square Material checkbox
                // here meant the header and the items it governs disagreed about what a
                // selection control looks like.
                SeslCheckbox(
                    checked = itemCount > 0 && selectedCount == itemCount,
                    indeterminate = selectedCount in 1 until itemCount,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.semantics {
                        contentDescription = if (selectedCount == itemCount) {
                            "Deselect all items from $label"
                        } else {
                            "Select all items from $label"
                        }
                    },
                )
            }
        }
    }
}

/**
 * Album cover card for the Albums tab.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    name: String,
    coverUri: Any?,
    mediaCount: Int,
    onClick: () -> Unit,
    /** Formatted folder size, or null while the size preference is off. */
    sizeLabel: String? = null,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    enabled: Boolean = true,
    isPinned: Boolean = false,
    /** Marks an album that is only visible because hidden albums are being revealed. */
    isHidden: Boolean = false,
    /** Marks an album that asks for device authentication before it opens. */
    isLocked: Boolean = false,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("$name, $mediaCount ${if (mediaCount == 1) "item" else "items"}")
                    if (isPinned) append(", pinned")
                    if (isHidden) append(", hidden")
                    if (isLocked) append(", locked")
                }
                selected = isSelected
            }
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val albumCoverRequest = remember(coverUri) {
            coverUri?.let { uri ->
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(384)
                    .crossfade(false)
                    .memoryCacheKey("${uri}_thumb")
                    .placeholderMemoryCacheKey("${uri}_thumb")
                    .diskCacheKey("${uri}_thumb")
                    .build()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.medium)
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (albumCoverRequest != null) {
                AsyncImage(
                    model = albumCoverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            } else if (isLocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                        .padding(4.dp)
                        .size(14.dp),
                )
            } else if (isHidden) {
                // Only ever on screen during a temporary reveal, so it needs to be obvious
                // which albums will disappear again.
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                        .padding(4.dp)
                        .size(14.dp),
                )
            } else if (isPinned) {
                // Pinned albums sort to the top — say so, rather than looking arbitrary.
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                        .padding(4.dp)
                        .size(14.dp),
                )
            }
        }

        // One fixed size for every album label. Shrinking each label to fit its own name gave
        // neighbouring cards different font sizes and therefore different label heights, so the
        // counts underneath no longer lined up across a row and the grid looked ragged.
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        Text(
            // "12 · 1.4 GB" when sizes are switched on, the count alone otherwise.
            text = sizeLabel?.let { "$mediaCount · $it" } ?: "$mediaCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumListRow(
    name: String,
    coverUri: Any?,
    mediaCount: Int,
    onClick: () -> Unit,
    sizeLabel: String? = null,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    enabled: Boolean = true,
    isPinned: Boolean = false,
    isHidden: Boolean = false,
    isLocked: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val albumCoverRequest = remember(coverUri) {
            coverUri?.let { uri ->
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(384)
                    .crossfade(false)
                    .memoryCacheKey("${uri}_thumb")
                    .placeholderMemoryCacheKey("${uri}_thumb")
                    .diskCacheKey("${uri}_thumb")
                    .build()
            }
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (albumCoverRequest != null) {
                AsyncImage(
                    model = albumCoverRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            val detail = if (sizeLabel != null) "$mediaCount items • $sizeLabel" else "$mediaCount items"
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        if (isLocked) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        if (isPinned) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Album group card for the Albums tab matching Samsung Gallery One UI 6.1.
 * Renders a 4-quadrant dynamic collage of covers from member albums,
 * a folder pill badge with album count, group name, and item counter.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumGroupCard(
    name: String,
    coverUris: List<Uri>,
    albumCount: Int,
    mediaCount: Int,
    onClick: () -> Unit,
    sizeLabel: String? = null,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    enabled: Boolean = true,
    isPinned: Boolean = false,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name group, $albumCount albums, $mediaCount items"
                selected = isSelected
            }
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.medium)
                    } else {
                        Modifier
                    }
                ),
        ) {
            when {
                coverUris.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                coverUris.size == 1 -> {
                    val request = remember(coverUris[0]) {
                        ImageRequest.Builder(context)
                            .data(coverUris[0])
                            .size(384)
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                coverUris.size in 2..3 -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val req1 = remember(coverUris[0]) {
                            ImageRequest.Builder(context).data(coverUris[0]).size(256).crossfade(false).build()
                        }
                        val req2 = remember(coverUris[1]) {
                            ImageRequest.Builder(context).data(coverUris[1]).size(256).crossfade(false).build()
                        }
                        AsyncImage(
                            model = req1,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background)
                        )
                        AsyncImage(
                            model = req2,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
                else -> {
                    // 4-quadrant preview (2x2 grid)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            val req1 = remember(coverUris[0]) { ImageRequest.Builder(context).data(coverUris[0]).size(192).crossfade(false).build() }
                            val req2 = remember(coverUris[1]) { ImageRequest.Builder(context).data(coverUris[1]).size(192).crossfade(false).build() }
                            AsyncImage(
                                model = req1,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            Box(modifier = Modifier.width(1.5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background))
                            AsyncImage(
                                model = req2,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(MaterialTheme.colorScheme.background))
                        Row(modifier = Modifier.weight(1f)) {
                            val req3 = remember(coverUris[2]) { ImageRequest.Builder(context).data(coverUris[2]).size(192).crossfade(false).build() }
                            val req4 = remember(coverUris[3]) { ImageRequest.Builder(context).data(coverUris[3]).size(192).crossfade(false).build() }
                            AsyncImage(
                                model = req3,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            Box(modifier = Modifier.width(1.5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background))
                            AsyncImage(
                                model = req4,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }

            // Samsung One UI Folder Pill Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "$albumCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            } else if (isPinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                        .padding(4.dp)
                        .size(14.dp),
                )
            }
        }

        // Fixed size, like AlbumCard — group cards sit in the same grid as album cards and have
        // to share their label metrics or the two kinds of tile stop lining up.
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        Text(
            text = sizeLabel?.let { "$albumCount albums · $mediaCount · $it" } ?: "$albumCount albums · $mediaCount items",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumGroupListRow(
    name: String,
    coverUris: List<Uri>,
    albumCount: Int,
    mediaCount: Int,
    onClick: () -> Unit,
    sizeLabel: String? = null,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    enabled: Boolean = true,
    isPinned: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUris.isEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                val primaryReq = remember(coverUris.first()) {
                    ImageRequest.Builder(context).data(coverUris.first()).size(256).crossfade(false).build()
                }
                AsyncImage(
                    model = primaryReq,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = "$albumCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            val detail = if (sizeLabel != null) "$albumCount albums • $mediaCount items • $sizeLabel" else "$albumCount albums • $mediaCount items"
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        if (isPinned) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

