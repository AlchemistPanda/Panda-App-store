package com.pandagallery.app.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.compose.AsyncImage
import com.pandagallery.app.data.smart.SmartGroup
import com.pandagallery.app.data.smart.SmartGroupType
import com.pandagallery.app.ui.components.FaceCropTransformation
import com.pandagallery.app.ui.components.PremiumAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartOrganizerScreen(
    onBack: () -> Unit,
    onOpenGroup: (SmartGroupType, String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPeople: () -> Unit = {},
    viewModel: SmartOrganizerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renamingGroup by remember { mutableStateOf<SmartGroup?>(null) }
    var mergingGroup by remember { mutableStateOf<SmartGroup?>(null) }
    var dismissingGroup by remember { mutableStateOf<SmartGroup?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Memories & organization", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onOpenPeople) {
                        Icon(Icons.Outlined.People, contentDescription = "People & pets")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.smartIndexEnabled) {
                item { EnableIndexCard(onClick = onOpenSettings) }
            }
            if (state.groups.isEmpty()) {
                item { EmptyMemories() }
            }
            items(state.groups, key = { "${it.group.type}:${it.group.key}" }) { card ->
                MemoryCard(
                    card = card,
                    onClick = { onOpenGroup(card.group.type, card.group.key, card.group.title) },
                    onRename = if (card.group.type == SmartGroupType.PEOPLE) ({ renamingGroup = card.group }) else null,
                    onMerge = if (card.group.type == SmartGroupType.PEOPLE) ({ mergingGroup = card.group }) else null,
                    onDismiss = if (card.group.type == SmartGroupType.PEOPLE) ({ dismissingGroup = card.group }) else null,
                )
            }
        }
    }

    dismissingGroup?.let { group ->
        PremiumAlertDialog(
            onDismissRequest = { dismissingGroup = null },
            icon = { Icon(Icons.Outlined.NotInterested, contentDescription = null) },
            title = { Text("Not a person?") },
            text = {
                Text(
                    "\"${group.title}\" stops appearing in People. Its ${group.mediaIds.size} " +
                        "photos are not changed or deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissPersonGroup(group.key)
                        dismissingGroup = null
                    },
                ) { Text("Not a person") }
            },
            dismissButton = { TextButton(onClick = { dismissingGroup = null }) { Text("Cancel") } },
        )
    }

    renamingGroup?.let { group ->
        RenamePersonDialog(
            initialName = group.title.takeUnless { it.startsWith("Person candidate") }.orEmpty(),
            onDismiss = { renamingGroup = null },
            onSave = { name ->
                viewModel.renamePersonGroup(group.key, name)
                renamingGroup = null
            },
        )
    }

    mergingGroup?.let { source ->
        MergePersonDialog(
            source = source,
            destinations = state.groups
                .map { it.group }
                .filter { it.type == SmartGroupType.PEOPLE && it.key != source.key },
            onDismiss = { mergingGroup = null },
            onMerge = { destination ->
                viewModel.mergePersonGroups(source.key, destination.key)
                mergingGroup = null
            },
        )
    }
}

/**
 * A memory reads as pictures first: a cover strip, then the title and item count.
 */
@Composable
private fun MemoryCard(
    card: SmartGroupCard,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onMerge: (() -> Unit)?,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Column {
            if (card.coverUris.isNotEmpty() && card.group.type != SmartGroupType.PEOPLE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val context = LocalContext.current
                    card.coverUris.forEach { uri ->
                        val thumbRequest = remember(uri) {
                            ImageRequest.Builder(context)
                                .data(uri)
                                .size(384)
                                .crossfade(false)
                                .memoryCacheKey("${uri}_thumb")
                                .placeholderMemoryCacheKey("${uri}_thumb")
                                .build()
                        }
                        AsyncImage(
                            model = thumbRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val face = card.face
                val faceUri = card.faceUri
                if (face != null && faceUri != null) {
                    val context = LocalContext.current
                    val faceRequest = remember(faceUri, face) {
                        ImageRequest.Builder(context)
                            .data(faceUri)
                            .size(192)
                            .crossfade(false)
                            .transformations(
                                FaceCropTransformation(face.left, face.top, face.right, face.bottom),
                            )
                            .build()
                    }
                    // A person is recognisable by their face, not by a category icon.
                    AsyncImage(
                        model = faceRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = card.group.type.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = card.group.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${card.itemCount} ${if (card.itemCount == 1) "item" else "items"} · ${card.group.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onRename != null) {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Outlined.Edit, "Rename ${card.group.title}")
                    }
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.NotInterested, "${card.group.title} is not a person")
                    }
                }
                if (onMerge != null) {
                    IconButton(onClick = onMerge) {
                        Icon(Icons.AutoMirrored.Outlined.MergeType, "Merge ${card.group.title}")
                    }
                } else {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun SmartGroupType.icon(): ImageVector = when (this) {
    SmartGroupType.ON_THIS_DAY -> Icons.Outlined.Today
    SmartGroupType.RECAP -> Icons.Outlined.AutoAwesome
    SmartGroupType.PEOPLE -> Icons.Outlined.Face
    SmartGroupType.STACK -> Icons.Outlined.PhotoLibrary
}

@Composable
private fun EnableIndexCard(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text("Turn on smart search", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Object, text and people analysis is optional and runs only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun EmptyMemories() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(12.dp))
        Text("No memories yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Recaps, photo stacks and people groups appear as your library grows.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RenamePersonDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Face, contentDescription = null) },
        title = { Text("Name people group") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_PERSON_NAME_LENGTH) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MergePersonDialog(
    source: SmartGroup,
    destinations: List<SmartGroup>,
    onDismiss: () -> Unit,
    onMerge: (SmartGroup) -> Unit,
) {
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.MergeType, contentDescription = null) },
        title = { Text("Merge ${source.title}") },
        text = {
            if (destinations.isEmpty()) {
                Text("There are no other people groups to merge with yet.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(destinations, key = SmartGroup::key) { destination ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMerge(destination) }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(destination.title, style = MaterialTheme.typography.titleMedium)
                                Text(destination.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Outlined.MergeType, null)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MAX_PERSON_NAME_LENGTH = 60
