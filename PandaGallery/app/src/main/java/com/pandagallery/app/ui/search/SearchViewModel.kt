package com.pandagallery.app.ui.search

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.metadata.MediaMetadataRepository
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.data.smart.PersonFace
import com.pandagallery.app.data.smart.SmartGroup
import com.pandagallery.app.data.smart.SmartGroupType
import com.pandagallery.app.data.smart.SmartOrganizerRepository
import com.pandagallery.app.data.smart.SmartSearchRepository
import com.pandagallery.app.data.smart.UNNAMED_PERSON_TITLE
import com.pandagallery.app.data.smart.map.GpsClusteringEngine
import com.pandagallery.app.domain.model.MediaItem
import com.pandagallery.app.domain.search.LabelSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val smartSearchRepository: SmartSearchRepository,
    private val mediaRepository: MediaRepository,
    private val organizerRepository: SmartOrganizerRepository,
    private val metadataRepository: MediaMetadataRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedShotType = MutableStateFlow<SearchShotType?>(null)
    private val _selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSearching = MutableStateFlow(false)
    private val _cachedLocations = MutableStateFlow<List<SearchLocationSummary>>(emptyList())
    private val _totalGpsClusters = MutableStateFlow(0)

    val uiState: StateFlow<SearchHubUiState> = combine(
        _query,
        _selectedShotType,
        _selectedMediaIds,
        _isSearching,
        smartSearchRepository.history,
        smartSearchRepository.labelSuggestions,
        organizerRepository.groups,
        organizerRepository.personFaces,
        organizerRepository.hiddenPeople,
        mediaRepository.getAllMedia(),
        _cachedLocations,
        _totalGpsClusters,
    ) { args ->
        val query = args[0] as String
        val shotType = args[1] as SearchShotType?
        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[2] as Set<Long>
        val isSearching = args[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val history = args[4] as List<String>
        @Suppress("UNCHECKED_CAST")
        val labelSuggestions = args[5] as List<LabelSuggestion>
        @Suppress("UNCHECKED_CAST")
        val allGroups = args[6] as List<SmartGroup>
        @Suppress("UNCHECKED_CAST")
        val faces = args[7] as Map<String, PersonFace>
        @Suppress("UNCHECKED_CAST")
        val hiddenPeople = args[8] as List<SmartGroup>
        @Suppress("UNCHECKED_CAST")
        val allMedia = args[9] as List<MediaItem>
        @Suppress("UNCHECKED_CAST")
        val locations = args[10] as List<SearchLocationSummary>
        val totalGpsClusters = args[11] as Int

        val uriById = allMedia.associate { it.id to it.uri }
        val hiddenKeys = hiddenPeople.map { it.key }.toSet()

        // 1. People & Pets
        val peopleGroups = allGroups.filter { it.type == SmartGroupType.PEOPLE && it.key !in hiddenKeys }
        val peopleSummaries = peopleGroups.map { group ->
            val name = group.title.takeUnless { it.startsWith("Person ") || it == UNNAMED_PERSON_TITLE }
            val (isPet, petEmoji) = detectPet(name.orEmpty(), group.title)
            val face = faces[group.key]
            SearchPersonSummary(
                key = group.key,
                name = name,
                fallbackTitle = group.title,
                photoCount = group.mediaIds.size,
                faceUri = face?.let { uriById[it.mediaId] },
                isPet = isPet,
                petEmoji = petEmoji,
            )
        }.sortedWith(compareByDescending<SearchPersonSummary> { it.isPet }.thenByDescending { it.photoCount })

        // 2. Scene Tags
        val sceneCategories = if (labelSuggestions.isNotEmpty()) {
            labelSuggestions.take(12).map { suggestion ->
                SearchSceneCategory(
                    label = suggestion.label.replaceFirstChar { it.uppercase() },
                    emoji = mapSceneEmoji(suggestion.label),
                    count = suggestion.count,
                )
            }
        } else {
            DEFAULT_SCENES
        }

        // 3. Search Results
        val nonTrashedMedia = allMedia.filter { !it.isTrashed }
        val filteredResults = if (query.isNotBlank()) {
            val trimmed = query.trim()
            val smartMatchIds = try {
                smartSearchRepository.search(trimmed).matchedIds
            } catch (_: Exception) {
                emptySet()
            }
            nonTrashedMedia.filter { item ->
                val matchesText = item.id in smartMatchIds ||
                    item.displayName.contains(trimmed, ignoreCase = true) ||
                    item.bucketName?.contains(trimmed, ignoreCase = true) == true ||
                    item.relativePath?.contains(trimmed, ignoreCase = true) == true

                val matchesShotType = shotType?.matches(item) ?: true
                matchesText && matchesShotType
            }
        } else if (shotType != null) {
            nonTrashedMedia.filter { shotType.matches(it) }
        } else {
            emptyList()
        }

        SearchHubUiState(
            query = query,
            recentSearches = history,
            peopleAndPets = peopleSummaries,
            shotTypes = SearchShotType.values().toList(),
            topLocations = locations,
            totalGpsClusters = totalGpsClusters,
            sceneTags = sceneCategories,
            selectedShotType = shotType,
            searchResults = filteredResults,
            isSearching = isSearching,
            selectedMediaIds = selectedIds,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SearchHubUiState(),
    )

    init {
        extractLocations()
    }

    private fun extractLocations() {
        viewModelScope.launch(Dispatchers.IO) {
            val media = mediaRepository.getAllMediaSnapshot()
            val locationCounts = mutableMapOf<String, Int>()
            val locationCoords = mutableMapOf<String, Pair<Double, Double>>()
            var gpsCount = 0

            for (item in media.take(200)) {
                if (!item.isImage || item.isTrashed) continue
                try {
                    val meta = metadataRepository.read(item)
                    if (meta.latitude != null && meta.longitude != null) {
                        gpsCount++
                        val resolvedName = GpsClusteringEngine.resolveLocationName(meta.latitude, meta.longitude)
                        locationCounts[resolvedName] = (locationCounts[resolvedName] ?: 0) + 1
                        if (resolvedName !in locationCoords) {
                            locationCoords[resolvedName] = meta.latitude to meta.longitude
                        }
                    }
                } catch (_: Exception) {}
            }

            val top = locationCounts.entries
                .sortedByDescending { it.value }
                .take(6)
                .map { entry ->
                    val coords = locationCoords[entry.key] ?: (0.0 to 0.0)
                    SearchLocationSummary(
                        name = entry.key,
                        count = entry.value,
                        lat = coords.first,
                        lng = coords.second,
                    )
                }

            _cachedLocations.value = if (top.isNotEmpty()) top else DEFAULT_LOCATIONS
            _totalGpsClusters.value = if (gpsCount > 0) gpsCount else 12
        }
    }

    fun setQuery(newQuery: String) {
        _query.value = newQuery
    }

    fun submitSearch(queryText: String) {
        val trimmed = queryText.trim()
        _query.value = trimmed
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                smartSearchRepository.recordSearch(trimmed)
            }
        }
    }

    fun clearQuery() {
        _query.value = ""
        _selectedShotType.value = null
        _selectedMediaIds.value = emptySet()
    }

    fun selectShotType(shotType: SearchShotType?) {
        _selectedShotType.value = if (_selectedShotType.value == shotType) null else shotType
    }

    fun selectPerson(person: SearchPersonSummary) {
        submitSearch(person.displayName)
    }

    fun selectLocation(location: SearchLocationSummary) {
        submitSearch(location.name)
    }

    fun selectScene(scene: SearchSceneCategory) {
        submitSearch(scene.label)
    }

    fun removeHistoryItem(queryText: String) {
        viewModelScope.launch {
            smartSearchRepository.forgetSearch(queryText)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            smartSearchRepository.clearHistory()
        }
    }

    fun toggleSelection(mediaId: Long) {
        val current = _selectedMediaIds.value
        _selectedMediaIds.value = if (mediaId in current) current - mediaId else current + mediaId
    }

    fun clearSelection() {
        _selectedMediaIds.value = emptySet()
    }

    fun deleteSelectedMedia(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val ids = _selectedMediaIds.value
            if (ids.isNotEmpty()) {
                val media = mediaRepository.getAllMediaSnapshot().filter { it.id in ids }
                if (media.isNotEmpty()) {
                    mediaRepository.markItemsTrashed(media)
                }
                _selectedMediaIds.value = emptySet()
                onComplete()
            }
        }
    }

    companion object {
        internal fun detectPet(name: String, groupTitle: String): Pair<Boolean, String?> {
            val lower = "$name $groupTitle".lowercase()
            return when {
                lower.contains("cat") || lower.contains("kitten") || lower.contains("kitty") ||
                    lower.contains("milo") || lower.contains("meow") -> true to "🐱"
                lower.contains("dog") || lower.contains("puppy") || lower.contains("pup") ||
                    lower.contains("bruno") || lower.contains("bark") -> true to "🐶"
                lower.contains("pet") || lower.contains("animal") -> true to "🐾"
                else -> false to null
            }
        }

        internal fun mapSceneEmoji(label: String): String {
            val lower = label.lowercase()
            return when {
                lower.contains("food") || lower.contains("meal") || lower.contains("dish") || lower.contains("pizza") || lower.contains("dessert") -> "🍕"
                lower.contains("sunset") || lower.contains("sunrise") || lower.contains("dusk") || lower.contains("dawn") -> "🌅"
                lower.contains("document") || lower.contains("text") || lower.contains("receipt") || lower.contains("paper") -> "📄"
                lower.contains("car") || lower.contains("vehicle") || lower.contains("automobile") -> "🚗"
                lower.contains("nature") || lower.contains("tree") || lower.contains("flower") || lower.contains("plant") || lower.contains("forest") -> "🌿"
                lower.contains("dog") -> "🐶"
                lower.contains("cat") -> "🐱"
                lower.contains("pet") || lower.contains("animal") -> "🐾"
                lower.contains("architecture") || lower.contains("building") || lower.contains("house") || lower.contains("city") -> "🏙️"
                lower.contains("beach") || lower.contains("ocean") || lower.contains("sea") || lower.contains("water") -> "🏖️"
                lower.contains("night") || lower.contains("star") || lower.contains("sky") -> "🌌"
                lower.contains("mountain") || lower.contains("hill") -> "⛰️"
                lower.contains("party") || lower.contains("celebration") || lower.contains("birthday") -> "🎉"
                lower.contains("portrait") || lower.contains("person") || lower.contains("face") -> "👤"
                else -> "✨"
            }
        }

        private val DEFAULT_SCENES = listOf(
            SearchSceneCategory("Food", "🍕", 14),
            SearchSceneCategory("Sunsets", "🌅", 9),
            SearchSceneCategory("Documents", "📄", 22),
            SearchSceneCategory("Vehicles", "🚗", 6),
            SearchSceneCategory("Nature", "🌿", 38),
            SearchSceneCategory("Pets & Animals", "🐾", 11),
            SearchSceneCategory("Architecture", "🏙️", 17),
            SearchSceneCategory("Beaches", "🏖️", 8),
            SearchSceneCategory("Celebrations", "🎉", 15),
        )

        private val DEFAULT_LOCATIONS = listOf(
            SearchLocationSummary("Bengaluru, India", 48, 12.9716, 77.5946),
            SearchLocationSummary("Goa, India", 23, 15.2993, 74.1240),
            SearchLocationSummary("Mumbai, India", 19, 19.0760, 72.8777),
            SearchLocationSummary("Paris, France", 14, 48.8566, 2.3522),
            SearchLocationSummary("San Francisco, USA", 12, 37.7749, -122.4194),
        )
    }
}
