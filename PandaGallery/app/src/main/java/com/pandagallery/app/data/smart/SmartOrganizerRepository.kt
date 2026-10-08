package com.pandagallery.app.data.smart

import com.pandagallery.app.data.local.dao.SmartMediaDao
import com.pandagallery.app.data.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.pandagallery.app.data.local.entity.PersonGroupPreferenceEntity
import com.pandagallery.app.data.local.entity.PersonMediaExclusionEntity

@Singleton
class SmartOrganizerRepository @Inject constructor(
    mediaRepository: MediaRepository,
    private val smartMediaDao: SmartMediaDao,
) {
    /**
     * People come from the persisted cluster assignments rather than from the raw index, so
     * this flow stays cheap: it carries one small row per grouped face instead of every
     * photo's OCR text and every face embedding.
     */
    private val computedGroups: Flow<List<SmartGroup>> = combine(
        mediaRepository.getAllMedia(),
        smartMediaDao.observePersonAssignments(),
    ) { media, assignments ->
        buildSmartGroups(
            media,
            assignments.map { PersonAssignment(it.mediaId, it.personKey, it.quality) },
        )
    }

    private val preferences: Flow<List<PersonGroupPreference>> =
        smartMediaDao.observePersonGroupPreferences()
            .map { rows -> rows.map(PersonGroupPreferenceEntity::toDomain) }

    /** Photos the user removed from a group, keyed by the group they were removed from. */
    private val exclusions: Flow<Map<String, Set<Long>>> =
        smartMediaDao.observePersonMediaExclusions().map { rows ->
            rows.groupBy(PersonMediaExclusionEntity::groupKey)
                .mapValues { (_, group) -> group.mapTo(mutableSetOf(), PersonMediaExclusionEntity::mediaId) }
        }

    val groups: Flow<List<SmartGroup>> = combine(
        computedGroups,
        preferences,
        exclusions,
    ) { groups, preferences, exclusions ->
        applyPeopleGroupPreferences(groups, preferences, exclusions)
    }

    /**
     * Groups the user dismissed as "not a person", so the People screen can offer them back.
     * Never mixed into [groups] — dismissing something has to actually hide it.
     */
    val hiddenPeople: Flow<List<SmartGroup>> = combine(
        computedGroups,
        preferences,
        exclusions,
    ) { groups, preferences, exclusions ->
        hiddenPeopleGroups(groups, preferences, exclusions)
    }

    /**
     * How many photos have been removed from each group.
     *
     * Surfaced so the People screen can offer them back: undo lives on a snackbar that is gone
     * in seconds, and a wrong removal is often noticed long after that.
     */
    val removedPhotoCounts: Flow<Map<String, Int>> =
        exclusions.map { rows -> rows.mapValues { (_, ids) -> ids.size } }

    /** The people kept at the front of the grid, so a tile can offer to unpin one. */
    val pinnedPersonKeys: Flow<Set<String>> = preferences.map { rows ->
        rows.filter(PersonGroupPreference::isPinned).mapTo(mutableSetOf(), PersonGroupPreference::groupKey)
    }

    /** Groups shown by a face the user picked, so the automatic choice can be offered back. */
    val chosenCoverKeys: Flow<Set<String>> = preferences.map { rows ->
        rows.filter { it.coverMediaId != null }.mapTo(mutableSetOf(), PersonGroupPreference::groupKey)
    }

    /** One representative face per person group, for the portrait chips. */
    val personFaces: Flow<Map<String, PersonFace>> = combine(
        smartMediaDao.observePersonAssignments(),
        preferences,
        exclusions,
    ) { rows, preferences, exclusions ->
        val preferenceByKey = preferences.associateBy(PersonGroupPreference::groupKey)
        bestFacePerPerson(
            rows.map { row ->
                PersonFaceCandidate(
                    // Keyed by the group that is actually displayed: after a merge the faces
                    // still carry the key they were clustered under, and a lookup by that key
                    // would leave the surviving group with no portrait at all.
                    personKey = canonicalPersonKey(row.personKey, preferenceByKey),
                    mediaId = row.mediaId,
                    quality = row.quality,
                    boxLeft = row.boxLeft,
                    boxTop = row.boxTop,
                    boxRight = row.boxRight,
                    boxBottom = row.boxBottom,
                    sourceWidth = row.sourceWidth,
                    sourceHeight = row.sourceHeight,
                )
            },
            covers = preferences.mapNotNull { preference ->
                preference.coverMediaId?.let { preference.groupKey to it }
            }.toMap(),
            exclusions = exclusions,
        )
    }

    fun idsFor(type: SmartGroupType, key: String): Flow<Set<Long>> = groups.map { groups ->
        groups.firstOrNull { it.type == type && it.key == key }?.mediaIds.orEmpty()
    }

    suspend fun renamePersonGroup(groupKey: String, requestedName: String) {
        updatePreference(groupKey) { it.copy(name = requestedName.trim().takeIf(String::isNotEmpty)) }
    }

    /**
     * Marks a group as "not a person" so it stops being shown.
     *
     * The faces stay clustered. Deleting them would only mean the next indexing pass
     * rediscovered the same non-person and offered it again. Reversible with
     * [restorePersonGroup], because the judgement is one a tap can get wrong.
     */
    suspend fun dismissPersonGroup(groupKey: String) {
        updatePreference(groupKey) { it.copy(isDismissed = true) }
    }

    /** Brings back a group dismissed as "not a person". */
    suspend fun restorePersonGroup(groupKey: String) {
        updatePreference(groupKey) { it.copy(isDismissed = false) }
    }

    /** Keeps someone at the front of the People grid. */
    suspend fun setPersonPinned(groupKey: String, pinned: Boolean) {
        updatePreference(groupKey) { it.copy(isPinned = pinned) }
    }

    /**
     * Chooses which photo's face represents a person.
     *
     * Passing null goes back to the automatic choice rather than leaving the group stuck with a
     * cover the user has since removed from it.
     */
    suspend fun setPersonCover(groupKey: String, mediaId: Long?) {
        updatePreference(groupKey) { it.copy(coverMediaId = mediaId) }
    }

    /**
     * Takes photos out of a person group — the fix for faces the clustering got wrong.
     *
     * Recorded against the group rather than the face rows, since clustering rewrites those on
     * every run and the correction has to outlive that.
     */
    suspend fun removeFromPerson(groupKey: String, mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        val now = System.currentTimeMillis()
        smartMediaDao.upsertPersonMediaExclusions(
            mediaIds.map { PersonMediaExclusionEntity(groupKey, it, now) },
        )
        // The cover cannot be a photo that is no longer in the group.
        val cover = smartMediaDao.getPersonGroupPreference(groupKey)?.coverMediaId
        if (cover != null && cover in mediaIds) setPersonCover(groupKey, null)
    }

    /** Undo of [removeFromPerson]. */
    suspend fun restoreToPerson(groupKey: String, mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        smartMediaDao.deletePersonMediaExclusions(groupKey, mediaIds.toList())
    }

    /** Puts back everything ever removed from one group. */
    suspend fun restoreAllToPerson(groupKey: String) {
        smartMediaDao.deletePersonMediaExclusions(groupKey)
    }

    private suspend fun updatePreference(
        groupKey: String,
        update: (PersonGroupPreferenceEntity) -> PersonGroupPreferenceEntity,
    ) {
        val existing = smartMediaDao.getPersonGroupPreference(groupKey)
            ?: PersonGroupPreferenceEntity(
                groupKey = groupKey,
                name = null,
                mergedIntoKey = null,
                updatedAt = 0L,
            )
        // Read-modify-write of the whole row: each of these settings was previously written by
        // rebuilding the entity by hand, which quietly dropped whichever fields that caller did
        // not know about — a rename would clear a pin.
        smartMediaDao.upsertPersonGroupPreference(
            update(existing).copy(updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun mergePersonGroups(sourceKey: String, destinationKey: String) {
        if (sourceKey == destinationKey) return
        val preferences = smartMediaDao.getPersonGroupPreferences().map(PersonGroupPreferenceEntity::toDomain)
        val byKey = preferences.associateBy(PersonGroupPreference::groupKey)
        val sourceRoot = canonicalPersonKey(sourceKey, byKey)
        val destinationRoot = canonicalPersonKey(destinationKey, byKey)
        if (sourceRoot != destinationRoot) {
            smartMediaDao.mergePersonGroups(sourceRoot, destinationRoot, System.currentTimeMillis())
            // Removals follow the merge. They are recorded against the group the user was looking
            // at, so leaving them behind on a key nothing displays any more would quietly hand the
            // wrongly grouped photos back.
            val now = System.currentTimeMillis()
            val moved = smartMediaDao.getPersonMediaExclusions()
                .filter { it.groupKey == sourceRoot }
                .map { PersonMediaExclusionEntity(destinationRoot, it.mediaId, now) }
            if (moved.isNotEmpty()) smartMediaDao.upsertPersonMediaExclusions(moved)
        }
    }
}

internal fun PersonGroupPreferenceEntity.toDomain() =
    PersonGroupPreference(groupKey, name, mergedIntoKey, isDismissed, isPinned, coverMediaId)


