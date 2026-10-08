package com.pandagallery.app.ui.people

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.data.smart.PersonFace
import com.pandagallery.app.data.smart.SmartGroup
import com.pandagallery.app.data.smart.SmartGroupType
import com.pandagallery.app.data.smart.SmartIndexRepository
import com.pandagallery.app.data.smart.SmartIndexScheduler
import com.pandagallery.app.data.smart.SmartOrganizerRepository
import com.pandagallery.app.data.smart.UNNAMED_PERSON_TITLE
import com.pandagallery.app.data.smart.face.FaceEmbedder
import com.pandagallery.app.data.smart.face.FaceModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The People screen: every face group the index has found, named or waiting for a name.
 *
 * People are their own destination rather than cards mixed into Memories, because that is how a
 * user looks for them — "photos of X", not "what happened in August". The list is derived from
 * the same clustering the organizer uses, so naming here shows up there too.
 */
@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val organizerRepository: SmartOrganizerRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val smartIndexRepository: SmartIndexRepository,
    private val smartIndexScheduler: SmartIndexScheduler,
    private val faceEmbedder: FaceEmbedder,
    mediaRepository: MediaRepository,
) : ViewModel() {

    /**
     * Session-only reveal of the groups dismissed as "not a person".
     *
     * Deliberately not persisted, and reset when the screen stops: hiding a group is a decision
     * the user made, and a reveal that outlived the visit would quietly undo it. The same
     * arrangement as the hidden-albums reveal.
     */
    private val revealHidden = MutableStateFlow(false)

    private val peopleGroups = combine(
        organizerRepository.groups.map { groups -> groups.filter { it.type == SmartGroupType.PEOPLE } },
        organizerRepository.hiddenPeople,
        organizerRepository.pinnedPersonKeys,
        organizerRepository.removedPhotoCounts,
        combine(organizerRepository.chosenCoverKeys, revealHidden, ::Pair),
    ) { visible, hidden, pinned, removed, (chosenCovers, reveal) ->
        PeopleGroups(visible, hidden, pinned, removed, chosenCovers, reveal)
    }

    val uiState: StateFlow<PeopleUiState> = combine(
        peopleGroups,
        organizerRepository.personFaces,
        preferencesDataSource.userPreferencesFlow,
        mediaRepository.getAllMedia(),
        smartIndexRepository.observeIndexVersion(),
    ) { groups, faces, preferences, media, _ ->
        val uriById = media.associate { it.id to it.uri }
        fun toPerson(group: SmartGroup) = group.toPerson(
            face = faces[group.key],
            uriById = uriById,
            isPinned = group.key in groups.pinned,
            removedCount = groups.removed[group.key] ?: 0,
            hasChosenCover = group.key in groups.chosenCovers,
        )
        // Revealing nothing is not a reveal: restoring the last hidden group has to end the
        // reveal on its own, or the screen keeps a banner about zero groups.
        val showingHidden = groups.revealHidden && groups.hidden.isNotEmpty()
        PeopleUiState(
            people = groups.visible.map(::toPerson),
            // Kept out of [people] rather than flagged inside it: a dismissed group must not
            // reappear in the grid just because the screen knows about it.
            hiddenPeople = if (showingHidden) groups.hidden.map(::toPerson) else emptyList(),
            hiddenCount = groups.hidden.size,
            isShowingHidden = showingHidden,
            isEnabled = preferences.smartIndexEnabled && preferences.faceGroupingEnabled,
            smartIndexEnabled = preferences.smartIndexEnabled,
            photoCount = media.count { it.isImage && !it.isTrashed },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState())

    /**
     * Turns people grouping on from this screen, so it can be switched on where it is missed
     * rather than only in Settings.
     *
     * Both preferences are needed: face grouping rides on the same indexing pass that does labels
     * and text, so enabling it alone would leave nothing to cluster.
     */
    fun enablePeopleGrouping() = viewModelScope.launch {
        if (faceEmbedder.prepare() !is FaceModelState.Ready) return@launch
        preferencesDataSource.updateSmartIndexEnabled(true)
        preferencesDataSource.updateFaceGroupingEnabled(true)
        smartIndexScheduler.enable(runImmediately = true)
    }

    fun rename(key: String, name: String) = viewModelScope.launch {
        organizerRepository.renamePersonGroup(key, name)
    }

    /** "Not a person" — a poster, a face on a shirt, a stranger caught in the background. */
    fun dismiss(key: String) = viewModelScope.launch {
        // Ends any reveal in progress: dismissing something has to make it disappear, not move
        // it into the revealed section still on screen.
        revealHidden.value = false
        organizerRepository.dismissPersonGroup(key)
    }

    /** Undoes a "Not a person", from the temporary reveal of hidden groups. */
    fun restore(key: String) = viewModelScope.launch {
        organizerRepository.restorePersonGroup(key)
    }

    /** Folds one group into another when the clustering split the same person in two. */
    fun merge(sourceKey: String, destinationKey: String) = viewModelScope.launch {
        organizerRepository.mergePersonGroups(sourceKey, destinationKey)
    }

    /** Puts back every photo taken out of a group — the way back from a removal regretted later. */
    fun restoreRemovedPhotos(key: String) = viewModelScope.launch {
        organizerRepository.restoreAllToPerson(key)
    }

    /** Drops a chosen cover, going back to the clearest face the index found. */
    fun clearCover(key: String) = viewModelScope.launch {
        organizerRepository.setPersonCover(key, null)
    }

    /** Keeps someone at the front of the grid, for the few people actually looked for. */
    fun setPinned(key: String, pinned: Boolean) = viewModelScope.launch {
        organizerRepository.setPersonPinned(key, pinned)
    }

    /** Shows the dismissed groups for this visit only, so one can be restored. */
    fun setHiddenPeopleRevealed(revealed: Boolean) {
        revealHidden.value = revealed
    }

    /** Called when the screen stops. Concealing on the way out is what keeps the reveal temporary. */
    fun concealHiddenPeople() {
        revealHidden.value = false
    }

    private fun SmartGroup.toPerson(
        face: PersonFace?,
        uriById: Map<Long, Uri>,
        isPinned: Boolean,
        removedCount: Int,
        hasChosenCover: Boolean,
    ) = Person(
        key = key,
        // A group keeps its generated title until it is named; the screen needs to tell the
        // two apart to offer "Add name".
        name = title.takeUnless { it.startsWith(GENERATED_TITLE_PREFIX) || it == UNNAMED_PERSON_TITLE },
        fallbackTitle = title,
        photoCount = mediaIds.size,
        faceUri = face?.let { uriById[it.mediaId] },
        face = face,
        isPinned = isPinned,
        removedCount = removedCount,
        hasChosenCover = hasChosenCover,
    )

    private data class PeopleGroups(
        val visible: List<SmartGroup>,
        val hidden: List<SmartGroup>,
        val pinned: Set<String>,
        val removed: Map<String, Int>,
        val chosenCovers: Set<String>,
        val revealHidden: Boolean,
    )

    private companion object {
        const val GENERATED_TITLE_PREFIX = "Person "
    }
}

data class Person(
    val key: String,
    /** Null until the user names them; [fallbackTitle] is what to show meanwhile. */
    val name: String?,
    val fallbackTitle: String,
    val photoCount: Int,
    val faceUri: Uri?,
    val face: PersonFace?,
    val isPinned: Boolean = false,
    /** Photos the user took out of this group; offered back from the tile's menu. */
    val removedCount: Int = 0,
    /** True when the face shown was picked rather than scored highest. */
    val hasChosenCover: Boolean = false,
)

data class PeopleUiState(
    val people: List<Person> = emptyList(),
    /** Only populated while [isShowingHidden] — see [PeopleViewModel.setHiddenPeopleRevealed]. */
    val hiddenPeople: List<Person> = emptyList(),
    /** Known even when nothing is revealed, so the menu can say how many there are. */
    val hiddenCount: Int = 0,
    val isShowingHidden: Boolean = false,
    /** Both switches on — the only state in which faces are ever looked for. */
    val isEnabled: Boolean = false,
    val smartIndexEnabled: Boolean = false,
    /** How many photos exist to search, so an empty list can say whether that is expected. */
    val photoCount: Int = 0,
)

/** Kept out of [SmartGroup] so the organizer screen and this one agree on what an unnamed group is. */
internal fun SmartGroup.isUnnamedPerson(): Boolean =
    type == SmartGroupType.PEOPLE && title.startsWith("Person ")
