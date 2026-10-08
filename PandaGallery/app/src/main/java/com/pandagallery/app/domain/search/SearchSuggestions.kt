package com.pandagallery.app.domain.search

/** A label the on-device index found, with how many items carry it. */
data class LabelSuggestion(val label: String, val count: Int)

/** ML Kit labels are joined with the ASCII unit separator; the index stores them that way. */
const val LABEL_SEPARATOR = "\u001F"

/**
 * The most common labels across the library, strongest first.
 *
 * Smart search has no discoverability on its own — nothing tells a user they can type
 * "dog". Showing what the index actually found turns that into something browsable, and
 * doubles as an honest window into what the on-device model believes is in their photos.
 *
 * Labels appearing on only one or two items are dropped: they are usually
 * misclassifications, and a wrong label offered as a suggestion reads as a bug.
 */
fun topLabels(
    labelRows: List<String>,
    limit: Int = 12,
    minimumCount: Int = 3,
): List<LabelSuggestion> = labelRows
    .asSequence()
    .flatMap { it.split(LABEL_SEPARATOR) }
    .map(String::trim)
    .filter(String::isNotEmpty)
    .groupingBy { it }
    .eachCount()
    .asSequence()
    // Count first, then alphabetical, so equally common labels do not reorder themselves
    // between recompositions.
    .filter { it.value >= minimumCount }
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .take(limit)
    .map { LabelSuggestion(it.key, it.value) }
    .toList()

/**
 * Whether a query is worth remembering.
 *
 * Single characters and pure whitespace are keystrokes on the way somewhere, not searches,
 * and filling the history with them makes it useless.
 */
fun isWorthRemembering(query: String): Boolean = query.trim().length >= 2
