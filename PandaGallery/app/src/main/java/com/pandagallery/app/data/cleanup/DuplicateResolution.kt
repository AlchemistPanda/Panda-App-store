package com.pandagallery.app.data.cleanup

import com.pandagallery.app.domain.model.DuplicateKeepRule

/**
 * Decides which copy of an exact duplicate group survives.
 *
 * Only ever applied to *byte-identical* groups. Similar photos and bursts are deliberately
 * excluded from automatic resolution: "looks alike" is a judgement call, and getting it
 * wrong deletes a photo the user wanted. Identical files carry no such risk — whichever
 * copy is kept, the pixels are the same.
 */
internal fun duplicatesToTrash(
    groups: List<List<CleanupCandidate>>,
    rule: DuplicateKeepRule,
): Set<Long> = groups.flatMapTo(mutableSetOf()) { group ->
    group.sortedByKeeper(rule).drop(1).map(CleanupCandidate::id)
}

/**
 * Orders a group with the copy to keep first.
 *
 * Every comparator ends with the id so the outcome is deterministic — an auto-resolve that
 * kept a different file each time it ran would be impossible to reason about, and this runs
 * unattended.
 */
private fun List<CleanupCandidate>.sortedByKeeper(rule: DuplicateKeepRule): List<CleanupCandidate> =
    when (rule) {
        DuplicateKeepRule.LARGEST -> sortedWith(
            compareByDescending<CleanupCandidate> { it.size }.thenBy { it.id }
        )
        DuplicateKeepRule.NEWEST -> sortedWith(
            compareByDescending<CleanupCandidate> { it.takenAt }.thenBy { it.id }
        )
        DuplicateKeepRule.OLDEST -> sortedWith(
            compareBy<CleanupCandidate> { it.takenAt }.thenBy { it.id }
        )
    }
