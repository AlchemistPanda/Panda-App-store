package com.pandagallery.app.domain.search

import java.text.Normalizer

/**
 * Text handling shared by the indexer and the query path.
 *
 * Both sides must fold, stem and expand identically — a token stored one way and
 * queried another simply never matches, and that class of bug is invisible until a
 * user reports "search can't find my receipts". Everything here is pure so the
 * round trip is unit-testable without an emulator.
 */
object SearchText {

    /** Longest token we keep. Guards against OCR emitting a wall of glued characters. */
    private const val MAX_TOKEN_LENGTH = 32

    /** Tokens shorter than this are dropped unless they are digits (e.g. a year fragment). */
    private const val MIN_TOKEN_LENGTH = 2

    private val SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /**
     * Words that carry no signal in a photo library. Deliberately short: aggressive stop
     * word lists hurt OCR search, where "the menu" is a legitimate thing to look for.
     */
    private val STOP_WORDS = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is", "it",
        "of", "on", "or", "that", "the", "this", "to", "was", "were", "with",
    )

    /**
     * Bidirectional synonym clusters, written as ordinary words. Every member of a cluster
     * expands to the whole cluster, so the user's vocabulary does not have to match the ML
     * Kit label vocabulary.
     *
     * These are deliberately *not* pre-stemmed by hand — [SYNONYMS] runs them through the
     * same [fold] and [stem] the tokenizer uses. Hand-stemming them means guessing this
     * stemmer's exact output for every entry, and a single wrong guess silently disables
     * that synonym with nothing to notice it by.
     */
    private val SYNONYM_CLUSTERS: List<Set<String>> = listOf(
        setOf("dog", "puppy", "puppies", "canine", "doggo"),
        setOf("cat", "kitten", "kitty", "kitties", "feline"),
        setOf("car", "vehicle", "automobile", "auto"),
        setOf("bike", "bicycle", "cycling"),
        setOf("motorcycle", "motorbike", "scooter"),
        setOf("food", "meal", "dish", "cuisine"),
        setOf("drink", "beverage", "cocktail"),
        setOf("coffee", "espresso", "cappuccino", "latte"),
        setOf("beach", "seashore", "shore", "coast", "sand"),
        setOf("sea", "ocean", "wave", "water"),
        setOf("mountain", "hill", "peak", "summit", "alps"),
        setOf("forest", "woods", "jungle", "tree"),
        setOf("flower", "blossom", "bloom", "petal", "floral"),
        setOf("sky", "cloud", "sunset", "sunrise", "dusk", "dawn"),
        setOf("snow", "winter", "ice", "frost"),
        setOf("rain", "storm", "umbrella"),
        setOf("person", "people", "human", "portrait", "face"),
        setOf("selfie", "portrait"),
        setOf("smile", "smiling", "laugh", "happy"),
        setOf("baby", "babies", "infant", "newborn", "toddler"),
        setOf("wedding", "bride", "groom", "marriage"),
        setOf("birthday", "cake", "party", "celebration"),
        setOf("document", "paper", "scan", "receipt", "invoice", "bill"),
        setOf("screenshot", "screen", "capture"),
        setOf("text", "writing", "handwriting", "note"),
        setOf("menu", "restaurant", "cafe", "diner"),
        setOf("building", "architecture", "house", "home", "apartment"),
        setOf("city", "cities", "urban", "street", "downtown", "skyline"),
        setOf("night", "dark", "nightlife", "neon"),
        setOf("concert", "stage", "band", "performance", "music"),
        setOf("sport", "game", "match", "stadium"),
        setOf("travel", "trip", "vacation", "holiday", "tour"),
        setOf("map", "directions", "navigation"),
        setOf("book", "page", "reading", "library"),
        setOf("art", "painting", "drawing", "sketch", "museum"),
        setOf("bird", "avian"),
        setOf("fish", "aquarium", "underwater"),
        setOf("horse", "equestrian", "pony"),
        setOf("plant", "leaf", "green", "garden"),
        setOf("phone", "mobile", "smartphone"),
        setOf("computer", "laptop", "keyboard", "monitor"),
        setOf("passport", "licence", "license", "card"),
        setOf("ticket", "boarding", "pass"),
    )

    private val SYNONYMS: Map<String, Set<String>> = buildMap {
        SYNONYM_CLUSTERS.forEach { cluster ->
            // Normalize through the real pipeline so lookups by a tokenized term hit.
            val normalized = cluster.flatMapTo(linkedSetOf()) { tokenize(it) }
            normalized.forEach { member ->
                // A term may legitimately sit in more than one cluster (e.g. "water").
                put(member, getOrDefault(member, emptySet()) + normalized)
            }
        }
    }

    /** Lowercases and strips accents so "Café" and "cafe" are the same token. */
    fun fold(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()

    /**
     * Conservative English suffix stripping. This is not Porter — it deliberately only
     * handles the plural and tense endings that actually show up in photo search, because
     * an over-eager stemmer collides unrelated words and makes results feel random.
     */
    fun stem(token: String): String {
        if (token.length <= 3) return token
        return when {
            token.length > 4 && token.endsWith("ies") -> token.dropLast(3) + "y"
            token.length > 4 && (
                token.endsWith("sses") || token.endsWith("shes") ||
                    token.endsWith("ches") || token.endsWith("xes")
                ) -> token.dropLast(2)
            token.length > 3 && token.endsWith("s") &&
                !token.endsWith("ss") && !token.endsWith("us") && !token.endsWith("is") -> token.dropLast(1)
            token.length > 5 && token.endsWith("ing") -> token.dropLast(3)
            token.length > 4 && token.endsWith("ed") && !token.endsWith("eed") -> token.dropLast(2)
            else -> token
        }
    }

    /** Splits, folds and stems. The single entry point both sides of the index use. */
    fun tokenize(raw: String): List<String> = raw.split(SEPARATOR)
        .asSequence()
        .filter(String::isNotBlank)
        .map { fold(it) }
        .filter { it.length <= MAX_TOKEN_LENGTH }
        .filter { it.length >= MIN_TOKEN_LENGTH || it.all(Char::isDigit) }
        .filterNot { it in STOP_WORDS }
        .map { stem(it) }
        .toList()

    /** Index-time form: a normalized token stream FTS can tokenize on whitespace. */
    fun normalizeForIndex(raw: String): String = tokenize(raw).joinToString(" ")

    /** Every token that should be treated as equivalent to [token], including itself. */
    fun expand(token: String): Set<String> = SYNONYMS[token]?.plus(token) ?: setOf(token)
}
