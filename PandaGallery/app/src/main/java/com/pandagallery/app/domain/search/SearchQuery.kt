package com.pandagallery.app.domain.search

/**
 * Builds FTS4 MATCH expressions and ranks the rows that come back.
 *
 * Retrieval is deliberately split into one MATCH per query token rather than a single
 * parenthesised expression: SQLite's enhanced query syntax (which is what makes
 * `(a OR b) AND (c OR d)` work) depends on SQLITE_ENABLE_FTS3_PARENTHESIS, and that is
 * not reliably compiled into the SQLite shipped on every Android device. Intersecting
 * per-token id sets in Kotlin gives the same AND semantics on every device.
 */
object SearchQuery {

    /** Field weights. Labels are the strongest signal, OCR body text the noisiest. */
    private const val WEIGHT_LABELS = 3.0
    private const val WEIGHT_NAME = 2.5
    private const val WEIGHT_BODY = 1.0

    /** A prefix-only hit ("beach" matching "beachfront") counts, but for less. */
    private const val PREFIX_FACTOR = 0.6

    /** Whole query appearing verbatim in the filename or album is a very strong signal. */
    private const val PHRASE_BONUS = 4.0

    /** Terms the user typed, already folded and stemmed. */
    data class Parsed(val tokens: List<String>) {
        val isEmpty: Boolean get() = tokens.isEmpty()

        /** One MATCH expression per token, each OR-ing that token's synonym cluster. */
        val matchExpressions: List<String>
            get() = tokens.map { token ->
                SearchText.expand(token)
                    .filter { it.isNotBlank() }
                    .sorted()
                    .joinToString(" OR ") { "$it*" }
            }
    }

    fun parse(raw: String): Parsed = Parsed(SearchText.tokenize(raw).distinct())

    /** The stored, already-normalized text of one indexed photo. */
    data class Document(
        val mediaId: Long,
        val labels: String,
        val body: String,
        val name: String,
    )

    data class Hit(val mediaId: Long, val score: Double)

    /**
     * Scores candidates that already satisfy the AND retrieval. Ordering is stable:
     * equal scores fall back to media id so results never shuffle between recompositions.
     */
    fun rank(parsed: Parsed, documents: List<Document>, rawQuery: String = ""): List<Hit> {
        if (parsed.isEmpty) return documents.map { Hit(it.mediaId, 0.0) }.sortedByDescending { it.mediaId }
        val phrase = SearchText.normalizeForIndex(rawQuery)
        return documents
            .map { document ->
                var score = 0.0
                parsed.tokens.forEach { token ->
                    val variants = SearchText.expand(token)
                    score += fieldScore(document.labels, variants) * WEIGHT_LABELS
                    score += fieldScore(document.name, variants) * WEIGHT_NAME
                    score += fieldScore(document.body, variants) * WEIGHT_BODY
                }
                if (phrase.isNotBlank() && parsed.tokens.size > 1 && document.name.contains(phrase)) {
                    score += PHRASE_BONUS
                }
                Hit(document.mediaId, score)
            }
            .sortedWith(compareByDescending<Hit> { it.score }.thenByDescending { it.mediaId })
    }

    /**
     * Best match of any synonym variant against one field. Exact token beats prefix, and a
     * field only ever contributes once per query token so a word repeated 40 times in an
     * OCR block cannot drown out a genuine label match.
     */
    private fun fieldScore(field: String, variants: Set<String>): Double {
        if (field.isEmpty()) return 0.0
        val fieldTokens = field.split(' ')
        var best = 0.0
        variants.forEach { variant ->
            if (variant.isEmpty()) return@forEach
            fieldTokens.forEach { fieldToken ->
                when {
                    fieldToken == variant -> best = maxOf(best, 1.0)
                    fieldToken.startsWith(variant) -> best = maxOf(best, PREFIX_FACTOR)
                }
            }
        }
        return best
    }
}
