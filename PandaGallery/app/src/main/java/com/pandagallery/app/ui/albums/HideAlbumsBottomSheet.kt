package com.pandagallery.app.ui.albums

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pandagallery.app.domain.model.Album
import com.pandagallery.app.ui.components.PandaModalBottomSheet

/**
 * Samsung One UI 6.1 Hide or unhide albums management sheet.
 * Modeled after Samsung Gallery's [fragment_hide_albums_layout.xml].
 *
 * Allows users to toggle visibility of folders without deleting any media.
 * System albums (Camera) are locked and protected from being hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HideAlbumsBottomSheet(
    allAlbums: List<Album>,
    onToggleHide: (Album, Boolean) -> Unit,
    onBatchSetHidden: (List<Album>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    // Distinct albums by ID
    val distinctAlbums = remember(allAlbums) {
        allAlbums.distinctBy { it.id }.sortedBy { it.name.lowercase() }
    }

    val filteredAlbums = remember(distinctAlbums, searchQuery) {
        if (searchQuery.isBlank()) {
            distinctAlbums
        } else {
            distinctAlbums.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    PandaModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Samsung Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hide or unhide albums",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Hidden albums won't be shown in the Gallery grid. Turn on 'Show hidden albums' in the more menu to view them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search albums...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )

            Spacer(Modifier.height(12.dp))

            // Quick Batch Action Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val hiddenCount = distinctAlbums.count { it.isHidden }
                SuggestionChip(
                    onClick = {
                        val hiddenOnes = distinctAlbums.filter { it.isHidden }
                        if (hiddenOnes.isNotEmpty()) {
                            onBatchSetHidden(hiddenOnes, false)
                        }
                    },
                    label = { Text("Unhide all ($hiddenCount)") },
                    icon = { Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    enabled = hiddenCount > 0,
                )

                SuggestionChip(
                    onClick = {
                        val nonSystem = distinctAlbums.filterNot { isSystemAlbum(it) }
                        if (nonSystem.isNotEmpty()) {
                            onBatchSetHidden(nonSystem, true)
                        }
                    },
                    label = { Text("Hide non-camera") },
                    icon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Album List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredAlbums, key = { it.id }) { album ->
                    val isSystem = isSystemAlbum(album)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Cover thumbnail
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (album.coverUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(album.coverUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            // Name and meta
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = album.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (isSystem) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Outlined.Lock,
                                            contentDescription = "System protected",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isSystem) {
                                        "${album.mediaCount} items • System album (cannot hide)"
                                    } else {
                                        "${album.mediaCount} items • ${album.relativePath ?: "Internal storage"}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            // Switch
                            Switch(
                                checked = album.isHidden,
                                onCheckedChange = { isHidden ->
                                    if (!isSystem) {
                                        onToggleHide(album, isHidden)
                                    }
                                },
                                enabled = !isSystem,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                                    checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Done Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Done",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Checks if the album is a core system folder (e.g. Camera / DCIM) that should never be hidden.
 */
private fun isSystemAlbum(album: Album): Boolean {
    val name = album.name.trim()
    val relPath = album.relativePath?.trim() ?: ""
    return name.equals("Camera", ignoreCase = true) ||
            relPath.contains("DCIM/Camera", ignoreCase = true) ||
            album.id == 0L
}
