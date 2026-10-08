package com.pandagallery.app.data.smart

/**
 * The face to show for a person group, as a fraction of its photo.
 *
 * Normalized rather than pixels so the crop applies to whatever resolution the image
 * loader happens to decode — the indexer measured the box on its own downscaled frame,
 * which is almost never the size a thumbnail is loaded at.
 */
data class PersonFace(
    val personKey: String,
    val mediaId: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val isUsable: Boolean get() = right > left && bottom > top
}

/**
 * Picks one face per person to represent them, and converts its box to fractions.
 *
 * Takes the face the user chose in [covers] if they chose one, otherwise the highest-quality
 * face: the same score that decides cluster seeding, so the portrait a group is shown by is
 * the clearest example of that person the library has.
 *
 * Photos removed from a group ([exclusions]) can never be its cover — being shown by a photo
 * the group no longer contains is exactly the wrong grouping the removal was correcting.
 *
 * Rows whose source frame was not recorded are skipped rather than guessed at — an
 * incorrectly mapped box crops to somebody's ear, which looks broken rather than absent.
 */
internal fun bestFacePerPerson(
    assignments: List<PersonFaceCandidate>,
    covers: Map<String, Long> = emptyMap(),
    exclusions: Map<String, Set<Long>> = emptyMap(),
): Map<String, PersonFace> {
    val best = LinkedHashMap<String, PersonFace>()
    assignments
        .sortedWith(
            compareByDescending<PersonFaceCandidate> { covers[it.personKey] == it.mediaId }
                .thenByDescending { it.quality }
                .thenBy { it.mediaId }
        )
        .forEach { candidate ->
            if (best.containsKey(candidate.personKey)) return@forEach
            if (candidate.mediaId in exclusions[candidate.personKey].orEmpty()) return@forEach
            if (candidate.sourceWidth <= 0 || candidate.sourceHeight <= 0) return@forEach
            val face = PersonFace(
                personKey = candidate.personKey,
                mediaId = candidate.mediaId,
                left = candidate.boxLeft.toFloat() / candidate.sourceWidth,
                top = candidate.boxTop.toFloat() / candidate.sourceHeight,
                right = candidate.boxRight.toFloat() / candidate.sourceWidth,
                bottom = candidate.boxBottom.toFloat() / candidate.sourceHeight,
            )
            if (face.isUsable) best[candidate.personKey] = face
        }
    return best
}

/** Flat view of a stored face row, free of Room types so the selection stays testable. */
internal data class PersonFaceCandidate(
    val personKey: String,
    val mediaId: Long,
    val quality: Float,
    val boxLeft: Int,
    val boxTop: Int,
    val boxRight: Int,
    val boxBottom: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)
