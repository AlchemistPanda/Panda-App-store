package com.pandagallery.app.data.cleanup

internal data class CleanupCandidate(
    val id: Long,
    val size: Long,
    val exactHash: String?,
    val perceptualHash: Long?,
    val blurScore: Double?,
    /** When the shot was taken, for telling a burst apart from two similar photos. */
    val takenAt: Long = 0L,
)

internal fun exactDuplicateGroups(candidates: List<CleanupCandidate>): List<List<CleanupCandidate>> =
    candidates.filter { it.exactHash != null }.groupBy { it.exactHash }.values.filter { it.size > 1 }

internal fun similarMediaGroups(
    candidates: List<CleanupCandidate>,
    maximumDistance: Int = 8,
): List<List<CleanupCandidate>> {
    val remaining = candidates.filter { it.perceptualHash != null }.toMutableList()
    val groups = mutableListOf<List<CleanupCandidate>>()
    while (remaining.isNotEmpty()) {
        val seed = remaining.removeAt(0)
        val matches = remaining.filter { other ->
            java.lang.Long.bitCount(seed.perceptualHash!! xor other.perceptualHash!!) <= maximumDistance
        }
        if (matches.isNotEmpty()) {
            groups += listOf(seed) + matches
            remaining.removeAll(matches.toSet())
        }
    }
    return groups
}

internal fun estimatedDuplicateSavings(groups: List<List<CleanupCandidate>>): Long =
    groups.sumOf { group -> group.sortedByDescending { it.size }.drop(1).sumOf { it.size } }

/**
 * Everything in a duplicate group except the copy worth keeping — the largest file,
 * which for identical shots is the least re-compressed one.
 */
internal fun redundantIds(groups: List<List<CleanupCandidate>>): Set<Long> =
    groups.flatMapTo(mutableSetOf()) { group ->
        group.sortedByDescending { it.size }.drop(1).map { it.id }
    }

/** Photos taken this close together are candidates for the same burst. */
internal const val BURST_WINDOW_MILLIS = 3_000L

/**
 * Groups rapid sequences of near-identical shots.
 *
 * Distinct from [similarMediaGroups], which compares appearance across the whole library
 * and so will happily group two sunsets taken years apart. A burst is defined by both
 * closeness in time and appearance, which is what makes it safe to keep only one.
 *
 * @param maximumDistance looser than the similarity threshold — frames in a burst differ
 *   slightly by definition, since the subject is moving.
 */
internal fun burstGroups(
    candidates: List<CleanupCandidate>,
    windowMillis: Long = BURST_WINDOW_MILLIS,
    maximumDistance: Int = 12,
): List<List<CleanupCandidate>> {
    val ordered = candidates
        .filter { it.perceptualHash != null && it.takenAt > 0L }
        .sortedWith(compareBy({ it.takenAt }, { it.id }))
    val groups = mutableListOf<List<CleanupCandidate>>()
    var current = mutableListOf<CleanupCandidate>()

    fun flush() {
        if (current.size >= 2) groups += current.toList()
        current = mutableListOf()
    }

    ordered.forEach { candidate ->
        val previous = current.lastOrNull()
        val continuesBurst = previous != null &&
            candidate.takenAt - previous.takenAt <= windowMillis &&
            java.lang.Long.bitCount(previous.perceptualHash!! xor candidate.perceptualHash!!) <= maximumDistance
        if (continuesBurst) current += candidate else { flush(); current += candidate }
    }
    flush()
    return groups
}

/**
 * Everything in a burst except the frame worth keeping.
 *
 * Keeps the *sharpest* rather than the largest. In a burst the subject is moving and the
 * frames are near-identical in size, so file size says nothing useful — but one frame is
 * usually in focus and the rest are not, and that is the one anybody wants.
 */
internal fun redundantBurstIds(groups: List<List<CleanupCandidate>>): Set<Long> =
    groups.flatMapTo(mutableSetOf()) { group -> group.sortedByBurstKeeper().drop(1).map { it.id } }

internal fun estimatedBurstSavings(groups: List<List<CleanupCandidate>>): Long =
    groups.sumOf { group -> group.sortedByBurstKeeper().drop(1).sumOf { it.size } }

private fun List<CleanupCandidate>.sortedByBurstKeeper(): List<CleanupCandidate> = sortedWith(
    compareByDescending<CleanupCandidate> { it.blurScore ?: -1.0 }
        .thenByDescending { it.size }
        .thenBy { it.id }
)
