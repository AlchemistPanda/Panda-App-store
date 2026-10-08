package com.pandagallery.app.ui.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.local.PreferencesDataSource
import android.net.Uri
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.data.smart.SmartGroup
import com.pandagallery.app.data.smart.PersonFace
import com.pandagallery.app.data.smart.SmartOrganizerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartOrganizerViewModel @Inject constructor(
    private val organizerRepository: SmartOrganizerRepository,
    mediaRepository: MediaRepository,
    preferencesDataSource: PreferencesDataSource,
) : ViewModel() {
    val uiState: StateFlow<SmartOrganizerUiState> = combine(
        organizerRepository.groups,
        preferencesDataSource.userPreferencesFlow,
        mediaRepository.getAllMedia(),
        organizerRepository.personFaces,
    ) { groups, preferences, media, faces ->
        val uriById = media.associate { it.id to it.uri }
        SmartOrganizerUiState(
            groups = groups
                .filterNot { it.type.name == "PEOPLE" && !preferences.faceGroupingEnabled }
                .map { group ->
                    val face = faces[group.key]
                    SmartGroupCard(
                        group = group,
                        // A memory without pictures in it isn't a memory.
                        coverUris = group.mediaIds.mapNotNull(uriById::get).take(4),
                        itemCount = group.mediaIds.size,
                        // People are shown by their face, not by a photo they appear in.
                        faceUri = face?.let { uriById[it.mediaId] },
                        face = face,
                    )
                },
            smartIndexEnabled = preferences.smartIndexEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartOrganizerUiState())

    fun renamePersonGroup(groupKey: String, name: String) {
        viewModelScope.launch { organizerRepository.renamePersonGroup(groupKey, name) }
    }

    /** "Not a person" — hides a cluster that is a poster, a logo, or a stranger. */
    fun dismissPersonGroup(groupKey: String) {
        viewModelScope.launch { organizerRepository.dismissPersonGroup(groupKey) }
    }

    fun mergePersonGroups(sourceKey: String, destinationKey: String) {
        viewModelScope.launch { organizerRepository.mergePersonGroups(sourceKey, destinationKey) }
    }
}

data class SmartOrganizerUiState(
    val groups: List<SmartGroupCard> = emptyList(),
    val smartIndexEnabled: Boolean = false,
)

data class SmartGroupCard(
    val group: SmartGroup,
    val coverUris: List<Uri>,
    val itemCount: Int,
    val faceUri: Uri? = null,
    val face: PersonFace? = null,
)
