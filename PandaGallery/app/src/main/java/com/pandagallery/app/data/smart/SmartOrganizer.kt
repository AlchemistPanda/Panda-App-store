package com.pandagallery.app.data.smart

import com.pandagallery.app.domain.model.MediaItem
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Serializable
enum class SmartGroupType { ON_THIS_DAY, RECAP, PEOPLE, STACK }

data class SmartGroup(
    val type: SmartGroupType,
    val key: String,
    val title: String,
    val subtitle: String,
    val mediaIds: Set<Long>,
)

data class PersonGroupPreference(
    val groupKey: String,
    val name: String?,
    val mergedIntoKey: String?,
    /** "This isn't a person" — a poster, a face on a shirt, a stranger in the background. */
    val isDismissed: Boolean = false,
    /** Shown before everyone else, so the people actually looked for are not scrolled to. */
    val isPinned: Boolean = false,
    /** The photo whose face represents them, when the highest-scoring one was not wanted. */
    val coverMediaId: Long? = null,
)

/**
 * One face's cluster membership, as persisted by the last clustering run.
 * [quality] orders photos within a group so the best shot of a person leads the cover.
 */
data class PersonAssignment(
    val mediaId: Long,
    val personKey: String,
    val quality: Float,
)

internal fun buildSmartGroups(
    media: List<MediaItem>,
    people: List<PersonAssignment>,
    nowMillis: Long = System.currentTimeMillis(),
): List<SmartGroup> = buildSmartGroupsFromCandidates(
    media.map { SmartMediaCandidate(it.id, it.sortDate, it.isImage, it.isTrashed) },
    people,
    nowMillis,
)

internal data class SmartMediaCandidate(
    val id: Long,
    val sortDate: Long,
    val isImage: Boolean = true,
    val isTrashed: Boolean = false,
)

internal fun buildSmartGroupsFromCandidates(
    media: List<SmartMediaCandidate>,
    people: List<PersonAssignment>,
    nowMillis: Long = System.currentTimeMillis(),
): List<SmartGroup> {
    val dated = media.filter { it.isImage && !it.isTrashed }.sortedByDescending(SmartMediaCandidate::sortDate)
    val groups = mutableListOf<SmartGroup>()
    val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val onThisDay = dated.filter { item ->
        Calendar.getInstance().apply { timeInMillis = item.sortDate }.let {
            it[Calendar.MONTH] == today[Calendar.MONTH] &&
                it[Calendar.DAY_OF_MONTH] == today[Calendar.DAY_OF_MONTH] &&
                it[Calendar.YEAR] < today[Calendar.YEAR]
        }
    }
    if (onThisDay.isNotEmpty()) {
        groups += SmartGroup(
            SmartGroupType.ON_THIS_DAY,
            "today",
            "On this day",
            "${onThisDay.size} memories from previous years",
            onThisDay.mapTo(linkedSetOf(), SmartMediaCandidate::id),
        )
    }

    dated.groupBy { MONTH_KEY.format(Date(it.sortDate)) }
        .entries
        .sortedByDescending { it.key }
        .take(6)
        .filter { it.value.size >= 3 }
        .forEach { (key, items) ->
            groups += SmartGroup(
                SmartGroupType.RECAP,
                key,
                MONTH_TITLE.format(Date(items.first().sortDate)),
                "${items.size} photo recap",
                items.mapTo(linkedSetOf(), SmartMediaCandidate::id),
            )
        }

    val visibleIds = dated.mapTo(hashSetOf(), SmartMediaCandidate::id)
    buildPeopleGroups(people, visibleIds).forEachIndexed { position, cluster ->
        groups += SmartGroup(
            SmartGroupType.PEOPLE,
            cluster.key,
            "Person ${position + 1}",
            "${cluster.mediaIds.size} photos · name this group",
            cluster.mediaIds,
        )
    }

    dated.sortedBy(SmartMediaCandidate::sortDate)
        .fold(mutableListOf<MutableList<SmartMediaCandidate>>()) { stacks, item ->
            val current = stacks.lastOrNull()
            if (current != null && item.sortDate - current.last().sortDate <= STACK_WINDOW_MILLIS) current += item
            else stacks += mutableListOf(item)
            stacks
        }
        .filter { it.size >= 2 }
        .takeLast(12)
        .reversed()
        .forEach { stack ->
            groups += SmartGroup(
                SmartGroupType.STACK,
                stack.first().id.toString(),
                "Photo stack",
                "${stack.size} shots taken together",
                stack.mapTo(linkedSetOf(), SmartMediaCandidate::id),
            )
        }
    return groups
}

/**
 * One person group as it should be displayed: merges resolved, removed photos dropped.
 *
 * Carried alongside the [SmartGroup] rather than folded into it so the People screen can show
 * hidden groups (to restore one) without every other consumer having to filter them out.
 */
internal data class PeopleGroupView(
    val group: SmartGroup,
    /** Null until the user names them; each list decides what to call a nameless group. */
    val name: String?,
    val isDismissed: Boolean,
    val isPinned: Boolean,
)

/**
 * Follows a chain of merges to the group that is actually displayed.
 *
 * The chain comes from repeated user edits, so a loop is possible: A merged into B merged back
 * into A. Stopping at the key that would close the loop keeps this terminating, and keeps a
 * looped pair resolving to each other's groups rather than to nothing.
 */
internal fun canonicalPersonKey(start: String, preferences: Map<String, PersonGroupPreference>): String {
    var current = start
    val visited = mutableSetOf<String>()
    while (visited.add(current)) {
        val next = preferences[current]?.mergedIntoKey ?: return current
        if (next in visited) return current
        current = next
    }
    return current
}

/**
 * Merges, names, orders and prunes the people groups.
 *
 * [exclusions] are the photos the user removed from a group, keyed by canonical group key.
 * They are applied here — at the point of display — because clustering rewrites every face
 * assignment on each run, so a removal recorded against a face row would not survive one.
 */
internal fun peopleGroupViews(
    groups: List<SmartGroup>,
    preferences: List<PersonGroupPreference>,
    exclusions: Map<String, Set<Long>> = emptyMap(),
): List<PeopleGroupView> {
    val preferenceByKey = preferences.associateBy(PersonGroupPreference::groupKey)
    return groups.filter { it.type == SmartGroupType.PEOPLE }
        .groupBy { canonicalPersonKey(it.key, preferenceByKey) }
        .entries
        .map { (key, merged) ->
            val preference = preferenceByKey[key]
            val mediaIds = merged.flatMapTo(linkedSetOf(), SmartGroup::mediaIds)
            mediaIds.removeAll(exclusions[key].orEmpty())
            Triple(key, mediaIds, preference)
        }
        // A group every photo has been removed from is not a person any more, it is an empty
        // list; showing it would only offer a face nobody appears in.
        .filter { (_, mediaIds, _) -> mediaIds.isNotEmpty() }
        // Pinned first, then the biggest groups: whoever the user marked is who they are
        // looking for, and past that "most photos" is the best guess at who matters.
        .sortedWith(
            compareByDescending<Triple<String, Set<Long>, PersonGroupPreference?>> { it.third?.isPinned == true }
                .thenByDescending { it.second.size }
                .thenBy { it.first }
        )
        .map { (key, mediaIds, preference) ->
            val name = preference?.name?.takeIf(String::isNotBlank)
            PeopleGroupView(
                group = SmartGroup(
                    type = SmartGroupType.PEOPLE,
                    key = key,
                    title = name ?: UNNAMED_PERSON_TITLE,
                    subtitle = "${mediaIds.size} photos",
                    mediaIds = mediaIds,
                ),
                name = name,
                isDismissed = preference?.isDismissed == true,
                isPinned = preference?.isPinned == true,
            )
        }
}

internal fun applyPeopleGroupPreferences(
    groups: List<SmartGroup>,
    preferences: List<PersonGroupPreference>,
    exclusions: Map<String, Set<Long>> = emptyMap(),
): List<SmartGroup> {
    val nonPeople = groups.filterNot { it.type == SmartGroupType.PEOPLE }
    // Dismissed groups stay clustered so they do not simply reappear on the next run;
    // they are only ever filtered out at the point of display.
    val people = peopleGroupViews(groups, preferences, exclusions)
        .filterNot(PeopleGroupView::isDismissed)
        // Numbered over the visible groups alone, so hiding one does not leave a gap in the
        // sequence the user sees.
        .mapIndexed { index, view -> view.group.copy(title = view.name ?: "Person ${index + 1}") }
    // People first. They were appended last, which put a person behind six monthly recaps and up
    // to twelve photo stacks — far enough down that the feature looked broken rather than merely
    // buried. Recaps and stacks are things to browse; a person is something you go looking for.
    return people + nonPeople
}

/**
 * The groups the user dismissed as "not a person", for the People screen's temporary reveal.
 *
 * Without this, "Not a person" is a one-way door: a face group dismissed by mistake could never
 * be got back. Numbered titles would clash with the visible list's, so unnamed hidden groups
 * are simply called what they are.
 */
internal fun hiddenPeopleGroups(
    groups: List<SmartGroup>,
    preferences: List<PersonGroupPreference>,
    exclusions: Map<String, Set<Long>> = emptyMap(),
): List<SmartGroup> = peopleGroupViews(groups, preferences, exclusions)
    .filter(PeopleGroupView::isDismissed)
    .map(PeopleGroupView::group)

/** What an unnamed group is called where positional numbering would collide with the main list. */
internal const val UNNAMED_PERSON_TITLE = "Unnamed person"

/**
 * The person groups whose name matches [query], following merges.
 *
 * Naming someone is the whole point of the People screen, so the name has to be searchable — and a
 * name lives on the group the user typed it into, which may be the destination of a merge rather
 * than the group a given face still belongs to. Resolving the chain here means a photo of someone
 * found through a merged-away cluster still answers to their name.
 *
 * Dismissed groups are excluded: "not a person" means it should stop turning up, in search too.
 */
internal fun personKeysMatchingName(
    preferences: List<PersonGroupPreference>,
    query: String,
): Set<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptySet()
    val byKey = preferences.associateBy(PersonGroupPreference::groupKey)

    return preferences.mapNotNullTo(mutableSetOf()) { preference ->
        val resolved = byKey[canonicalPersonKey(preference.groupKey, byKey)] ?: return@mapNotNullTo null
        val name = resolved.name ?: preference.name ?: return@mapNotNullTo null
        val dismissed = resolved.isDismissed || preference.isDismissed
        preference.groupKey.takeIf { !dismissed && name.contains(trimmed, ignoreCase = true) }
    }
}

private data class PeopleCluster(val key: String, val mediaIds: Set<Long>)

/**
 * Turns persisted face assignments into displayable groups.
 *
 * The clustering itself already happened in the background (see
 * [com.pandagallery.app.data.smart.SmartIndexRepository.recluster]) - this only shapes the
 * result for the UI: it drops photos that are no longer visible (trashed, deleted) and
 * orders each group's media by face quality, so a group's cover is the best shot of them.
 */
private fun buildPeopleGroups(
    people: List<PersonAssignment>,
    visibleIds: Set<Long>,
): List<PeopleCluster> = people
    .filter { it.mediaId in visibleIds }
    .groupBy(PersonAssignment::personKey)
    .map { (key, assignments) ->
        PeopleCluster(
            key = key,
            mediaIds = assignments
                .sortedWith(compareByDescending<PersonAssignment> { it.quality }.thenBy { it.mediaId })
                .mapTo(linkedSetOf(), PersonAssignment::mediaId),
        )
    }
    .filter { it.mediaIds.size >= MIN_PHOTOS_PER_PERSON }
    .sortedWith(compareByDescending<PeopleCluster> { it.mediaIds.size }.thenBy { it.key })

private val MONTH_KEY = SimpleDateFormat("yyyy-MM", Locale.US)
private val MONTH_TITLE = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
private const val STACK_WINDOW_MILLIS = 15_000L

/** A single photo of someone is not a person group, it is a photo. */
private const val MIN_PHOTOS_PER_PERSON = 2
