package com.pandagallery.app.data.smart

import android.database.sqlite.SQLiteException
import com.pandagallery.app.data.local.dao.SearchDocumentRow
import com.pandagallery.app.data.local.dao.SmartMediaDao
import com.pandagallery.app.data.local.entity.SearchHistoryEntity
import com.pandagallery.app.data.local.entity.PersonGroupPreferenceEntity
import com.pandagallery.app.domain.search.LabelSuggestion
import com.pandagallery.app.domain.search.SearchQuery
import com.pandagallery.app.domain.search.isWorthRemembering
import com.pandagallery.app.domain.search.topLabels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matched photos, strongest first.
 *
 * [matchedIds] is what filtering needs; [ranked] preserves relevance order for callers that
 * want it (the grid keeps the user's chosen sort, since date headers depend on it).
 */
data class SmartSearchResult(
    val matchedIds: Set<Long>,
    val ranked: List<Long>,
) {
    companion object {
        val Empty = SmartSearchResult(emptySet(), emptyList())
    }
}

@Singleton
class SmartSearchRepository @Inject constructor(
    private val dao: SmartMediaDao,
) {
    /** What the on-device index actually found, most common first. */
    val labelSuggestions: Flow<List<LabelSuggestion>> =
        dao.observeLabelRows().map { rows -> topLabels(rows) }

    /** Recent searches, newest first. */
    val history: Flow<List<String>> = dao.observeSearchHistory(HISTORY_LIMIT)
        .map { entries -> entries.map(SearchHistoryEntity::query) }

    /**
     * Remembers a query the user committed to.
     *
     * Called on the keyboard's search action rather than as they type — recording every
     * debounced keystroke would fill the history with "d", "do", "dog".
     */
    suspend fun recordSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (!isWorthRemembering(query)) return
        dao.recordSearch(query, System.currentTimeMillis(), HISTORY_LIMIT)
    }

    suspend fun forgetSearch(query: String) = dao.deleteSearchHistory(query)

    suspend fun clearHistory() = dao.clearSearchHistory()

    /**
     * Runs one query against the full-text index.
     *
     * Each query token is matched separately and the id sets intersected, giving AND
     * semantics without depending on SQLite's optional parenthesised query syntax. The
     * intersection short-circuits, so an impossible term costs one cheap query rather than
     * a full scan per remaining term.
     */
    suspend fun search(rawQuery: String): SmartSearchResult = withContext(Dispatchers.IO) {
        val parsed = SearchQuery.parse(rawQuery)
        if (parsed.isEmpty) return@withContext SmartSearchResult.Empty

        // People are matched by name rather than through the text index: a name the user typed on
        // the People screen is not part of any photo's content, so it never reaches the FTS
        // document. Searching someone's name is the obvious way to find their photos, so it is
        // answered here instead of being silently unsupported.
        val peopleMatches = personMatches(rawQuery)

        var intersection: MutableSet<Long>? = null
        for (expression in parsed.matchExpressions) {
            val ids = try {
                dao.matchIds(expression).toHashSet()
            } catch (_: SQLiteException) {
                // A malformed MATCH is a bug in expression building, not a user error;
                // returning nothing beats surfacing a crash mid-typing.
                return@withContext SmartSearchResult.Empty
            }
            if (intersection == null) intersection = ids else intersection.retainAll(ids)
            if (intersection.isEmpty()) break
        }
        val textMatches = intersection.orEmpty()
        if (textMatches.isEmpty() && peopleMatches.isEmpty()) return@withContext SmartSearchResult.Empty

        val documents = dao.searchDocuments(textMatches.toList()).map(SearchDocumentRow::toDocument)
        val ranked = SearchQuery.rank(parsed, documents, rawQuery).map(SearchQuery.Hit::mediaId)
        SmartSearchResult(
            matchedIds = textMatches + peopleMatches,
            // A name is a deliberate label the user attached themselves, so those photos lead;
            // the text hits keep their relevance order behind them.
            ranked = (peopleMatches.toList() + ranked).distinct(),
        )
    }

    /** Photos of everyone whose name matches [rawQuery]; empty when nobody has been named. */
    private suspend fun personMatches(rawQuery: String): Set<Long> {
        val preferences = dao.getPersonGroupPreferences()
        if (preferences.isEmpty()) return emptySet()
        val domain = preferences.map(PersonGroupPreferenceEntity::toDomain)
        val keys = personKeysMatchingName(domain, rawQuery)
        if (keys.isEmpty()) return emptySet()
        val matches = dao.mediaIdsForPersonKeys(keys).toMutableSet()
        // A photo the user removed from someone's group is not a photo of them, so searching
        // their name must not hand it back.
        val byKey = domain.associateBy(PersonGroupPreference::groupKey)
        val roots = keys.mapTo(mutableSetOf()) { canonicalPersonKey(it, byKey) }
        dao.getPersonMediaExclusions()
            .filter { it.groupKey in roots }
            .forEach { matches.remove(it.mediaId) }
        return matches
    }

    private companion object {
        /** Enough to be useful without turning the empty state into a wall of text. */
        const val HISTORY_LIMIT = 8
    }
}

private fun SearchDocumentRow.toDocument() = SearchQuery.Document(
    mediaId = mediaId,
    labels = labels,
    body = body,
    name = name,
)
