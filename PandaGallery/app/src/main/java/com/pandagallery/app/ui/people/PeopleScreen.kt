package com.pandagallery.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import com.pandagallery.app.data.smart.SmartGroupType
import com.pandagallery.app.ui.components.FaceCropTransformation
import com.pandagallery.app.ui.components.adaptiveColumnCount
import com.pandagallery.app.ui.components.PandaDropdownMenu
import com.pandagallery.app.ui.components.PremiumAlertDialog

/**
 * Everyone the on-device index has grouped, as a grid of faces.
 *
 * Tapping a face opens their photos; the overflow names, merges or dismisses a group. Naming is
 * the point of the screen, so an unnamed group says "Add name" rather than hiding the fact that
 * it is nameless behind a generated title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    onBack: () -> Unit,
    onOpenPerson: (SmartGroupType, String, String, Boolean) -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Person?>(null) }
    var merging by remember { mutableStateOf<Person?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    // Same contract as the hidden-albums reveal: it lasts for the visit and no longer.
    LifecycleStartEffect(Unit) {
        onStopOrDispose { viewModel.concealHiddenPeople() }
    }

    fun open(person: Person, startInSelection: Boolean = false) = onOpenPerson(
        SmartGroupType.PEOPLE,
        person.key,
        person.name ?: person.fallbackTitle,
        startInSelection,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("People", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "People options")
                        }
                        PandaDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            // Always listed, even at zero. An entry that only exists once
                            // something is hidden is an entry nobody can find when they need it.
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            state.isShowingHidden -> "Hide hidden people again"
                                            state.hiddenCount == 0 -> "No hidden people"
                                            else -> "Show hidden people (${state.hiddenCount})"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (state.isShowingHidden) Icons.Outlined.VisibilityOff
                                        else Icons.Outlined.Visibility,
                                        contentDescription = null,
                                    )
                                },
                                enabled = state.isShowingHidden || state.hiddenCount > 0,
                                onClick = {
                                    menuOpen = false
                                    viewModel.setHiddenPeopleRevealed(!state.isShowingHidden)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(adaptiveColumnCount(3, maxWidth)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.isEnabled) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EnablePeopleCard(onEnable = viewModel::enablePeopleGrouping)
                }
            } else if (state.people.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NoPeopleYet(photoCount = state.photoCount)
                }
            }

            items(state.people, key = Person::key) { person ->
                PersonTile(
                    person = person,
                    onClick = { open(person) },
                    onRename = { renaming = person },
                    onMerge = { merging = person },
                    onDismiss = { viewModel.dismiss(person.key) },
                    onFixPhotos = { open(person, startInSelection = true) },
                    onTogglePinned = { viewModel.setPinned(person.key, !person.isPinned) },
                    onRestoreRemoved = { viewModel.restoreRemovedPhotos(person.key) },
                    onClearCover = { viewModel.clearCover(person.key) },
                )
            }

            if (state.isShowingHidden) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HiddenPeopleBanner(
                        count = state.hiddenCount,
                        onHideAgain = { viewModel.setHiddenPeopleRevealed(false) },
                    )
                }
                items(state.hiddenPeople, key = { "hidden:${it.key}" }) { person ->
                    PersonTile(
                        person = person,
                        // Dimmed and not tappable through to their photos: this is a list to
                        // restore from, not a second copy of the grid above.
                        isHidden = true,
                        onClick = { viewModel.restore(person.key) },
                        onRename = { renaming = person },
                        onMerge = { merging = person },
                        onDismiss = { viewModel.dismiss(person.key) },
                        onRestore = { viewModel.restore(person.key) },
                    )
                }
            }
        }
        }
    }

    renaming?.let { person ->
        NamePersonDialog(
            currentName = person.name.orEmpty(),
            onDismiss = { renaming = null },
            onSave = { name ->
                viewModel.rename(person.key, name)
                renaming = null
            },
        )
    }

    merging?.let { person ->
        MergePersonDialog(
            source = person,
            candidates = state.people.filterNot { it.key == person.key },
            onDismiss = { merging = null },
            onMerge = { destination ->
                viewModel.merge(person.key, destination.key)
                merging = null
            },
        )
    }
}

@Composable
private fun PersonTile(
    person: Person,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
    onFixPhotos: (() -> Unit)? = null,
    onTogglePinned: (() -> Unit)? = null,
    onRestoreRemoved: (() -> Unit)? = null,
    onClearCover: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    isHidden: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .alpha(if (isHidden) 0.55f else 1f)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            val face = person.face
            if (person.faceUri != null && face != null) {
                val context = LocalContext.current
                val faceRequest = remember(person.faceUri, face) {
                    ImageRequest.Builder(context)
                        .data(person.faceUri)
                        .size(256)
                        .crossfade(false)
                        // Crops to the detected box, so the tile shows the person rather than
                        // whatever else was in the frame.
                        .transformations(
                            FaceCropTransformation(face.left, face.top, face.right, face.bottom),
                        )
                        .build()
                }
                AsyncImage(
                    model = faceRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                // On its own the icon disappeared into whatever the photo behind it happened to
                // be, so it sits on a filled circle instead — legible over any face.
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = CircleShape,
                ) {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Options for ${person.name ?: person.fallbackTitle}",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                PandaDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (person.name == null) "Add name" else "Rename") },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Merge with…") },
                        onClick = { menuOpen = false; onMerge() },
                    )
                    onFixPhotos?.let { fix ->
                        HorizontalDivider()
                        // The answer to "someone else's photo ended up in this group": both of
                        // these open their photos ready to pick, where the actions live.
                        DropdownMenuItem(
                            text = { Text("Remove photos…") },
                            onClick = { menuOpen = false; fix() },
                        )
                        DropdownMenuItem(
                            text = { Text("Change cover photo…") },
                            onClick = { menuOpen = false; fix() },
                        )
                    }
                    if (onClearCover != null && person.hasChosenCover) {
                        DropdownMenuItem(
                            text = { Text("Use the best face again") },
                            onClick = { menuOpen = false; onClearCover() },
                        )
                    }
                    if (onRestoreRemoved != null && person.removedCount > 0) {
                        // Undo lives on a snackbar for a few seconds; a removal is often
                        // regretted long after that, so the way back stays on the menu.
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Put back ${person.removedCount} removed " +
                                        if (person.removedCount == 1) "photo" else "photos"
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                            onClick = { menuOpen = false; onRestoreRemoved() },
                        )
                    }
                    onTogglePinned?.let { toggle ->
                        DropdownMenuItem(
                            text = { Text(if (person.isPinned) "Unpin from top" else "Pin to top") },
                            leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                            onClick = { menuOpen = false; toggle() },
                        )
                    }
                    if (onRestore != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Show in People again") },
                            leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                            onClick = { menuOpen = false; onRestore() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Not a person") },
                            onClick = { menuOpen = false; onDismiss() },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                isHidden -> person.name ?: "Hidden group"
                else -> person.name ?: "Add name"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (person.name == null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = when {
                isHidden -> "Tap to restore"
                person.photoCount == 1 -> "1 photo"
                else -> "${person.photoCount} photos"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Says what the temporary reveal is showing and how to end it.
 *
 * Without this the dismissed groups would look like ordinary people that had simply gone faint.
 */
@Composable
private fun HiddenPeopleBanner(count: Int, onHideAgain: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$count marked \"not a person\" — tap one to bring it back",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onHideAgain) { Text("Hide again") }
        }
    }
}

@Composable
private fun EnablePeopleCard(onEnable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Find people in your photos", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Faces are detected and grouped on this device — nothing is uploaded. " +
                    "Grouping runs while charging, and can be turned off again in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onEnable) { Text("Turn on People") }
        }
    }
}

@Composable
private fun NoPeopleYet(photoCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.Face,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text("No people found yet", style = MaterialTheme.typography.titleMedium)
        Text(
            // Says what it is doing and what would change the answer, rather than leaving an
            // empty screen that looks broken.
            text = "Scanning $photoCount photos on this device. Groups appear once someone shows " +
                "up in at least two photos — screenshots and documents rarely produce any.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun NamePersonDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentName.isEmpty()) "Add a name" else "Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MergePersonDialog(
    source: Person,
    candidates: List<Person>,
    onDismiss: () -> Unit,
    onMerge: (Person) -> Unit,
) {
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${source.name ?: source.fallbackTitle} into") },
        text = {
            if (candidates.isEmpty()) {
                Text("There is no other group to merge with yet.")
            } else {
                Column {
                    candidates.forEach { candidate ->
                        TextButton(
                            onClick = { onMerge(candidate) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "${candidate.name ?: candidate.fallbackTitle} · " +
                                    "${candidate.photoCount} photos",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
