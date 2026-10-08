package com.pandagallery.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.pandagallery.app.data.local.db.SmartSearchSchema
import com.pandagallery.app.data.local.entity.FaceEmbeddingEntity
import com.pandagallery.app.data.local.entity.PersonGroupPreferenceEntity
import com.pandagallery.app.data.local.entity.PersonMediaExclusionEntity
import com.pandagallery.app.data.local.entity.SearchHistoryEntity
import com.pandagallery.app.data.local.entity.SmartMediaIndexEntity
import kotlinx.coroutines.flow.Flow

/** A media id returned by a full-text match. */
data class SearchMatchRow(val mediaId: Long)

/** The stored, normalized text of one indexed photo, used for relevance ranking. */
data class SearchDocumentRow(
    val mediaId: Long,
    val labels: String,
    val body: String,
    val name: String,
)

/** Which person group a photo's face was assigned to by the last clustering run. */
data class PersonAssignmentRow(
    val mediaId: Long,
    val faceIndex: Int,
    val personKey: String,
    val quality: Float,
    val boxLeft: Int = 0,
    val boxTop: Int = 0,
    val boxRight: Int = 0,
    val boxBottom: Int = 0,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
)

@Dao
interface SmartMediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SmartMediaIndexEntity)

    @Query("SELECT * FROM smart_media_index ORDER BY mediaId ASC")
    fun observeAll(): Flow<List<SmartMediaIndexEntity>>

    @Query("SELECT * FROM smart_media_index ORDER BY mediaId ASC")
    suspend fun getAll(): List<SmartMediaIndexEntity>

    @Query("SELECT mediaId FROM smart_media_index")
    suspend fun getAllMediaIds(): List<Long>

    @Query("SELECT * FROM smart_media_index WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Long): SmartMediaIndexEntity?

    /**
     * A cheap change token for the whole index. Search re-runs when this changes instead of
     * observing every row, which is what kept the old implementation holding every photo's
     * OCR text in memory.
     */
    @Query("SELECT COUNT(*) || ':' || IFNULL(MAX(indexedAt), 0) FROM smart_media_index")
    fun observeIndexVersion(): Flow<String>

    /** Raw label strings, for building browsable suggestions. */
    @Query("SELECT labels FROM smart_media_index WHERE labels != ''")
    fun observeLabelRows(): Flow<List<String>>

    @Query("DELETE FROM smart_media_index")
    suspend fun clear()

    @Query("DELETE FROM smart_media_index WHERE mediaId IN (:mediaIds)")
    suspend fun deleteMediaIds(mediaIds: List<Long>)

    @Query("UPDATE smart_media_index SET faceCount = 0, facesIndexed = 0")
    suspend fun clearFaceFlags()

    // ---------------------------------------------------------------- full-text search

    // Reads only. Room's @RawQuery is a SELECT facility, so writes to the virtual table go
    // through SmartSearchIndex, which talks to the SupportSQLiteDatabase directly.

    @RawQuery
    suspend fun rawMatch(query: SupportSQLiteQuery): List<SearchMatchRow>

    @RawQuery
    suspend fun rawDocuments(query: SupportSQLiteQuery): List<SearchDocumentRow>

    /** @param matchExpression a single FTS4 MATCH expression, e.g. `dog* OR puppi*`. */
    suspend fun matchIds(matchExpression: String): List<Long> =
        rawMatch(SimpleSQLiteQuery(SmartSearchSchema.MATCH_IDS, arrayOf(matchExpression)))
            .map(SearchMatchRow::mediaId)

    /** Ids that already have a full-text row, so backfill can skip them. */
    suspend fun searchDocumentIds(): List<Long> =
        rawMatch(SimpleSQLiteQuery(SmartSearchSchema.ALL_DOCIDS)).map(SearchMatchRow::mediaId)

    suspend fun searchDocuments(mediaIds: List<Long>): List<SearchDocumentRow> {
        if (mediaIds.isEmpty()) return emptyList()
        return mediaIds.chunked(SQLITE_BIND_LIMIT).flatMap { chunk ->
            rawDocuments(SimpleSQLiteQuery(SmartSearchSchema.documentsFor(chunk.size), chunk.toTypedArray()))
        }
    }

    // ------------------------------------------------------------------ face embeddings

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFaces(faces: List<FaceEmbeddingEntity>)

    @Query("SELECT * FROM face_embeddings")
    suspend fun getAllFaces(): List<FaceEmbeddingEntity>

    @Query("DELETE FROM face_embeddings WHERE mediaId IN (:mediaIds)")
    suspend fun deleteFacesFor(mediaIds: List<Long>)

    @Query("DELETE FROM face_embeddings")
    suspend fun clearFaces()

    @Query("UPDATE face_embeddings SET personKey = :personKey WHERE mediaId = :mediaId AND faceIndex = :faceIndex")
    suspend fun assignPerson(mediaId: Long, faceIndex: Int, personKey: String?)

    @Query("UPDATE face_embeddings SET personKey = NULL")
    suspend fun clearPersonAssignments()

    @Query(
        "SELECT mediaId, faceIndex, personKey, quality, boxLeft, boxTop, boxRight, boxBottom, " +
            "sourceWidth, sourceHeight FROM face_embeddings " +
            "WHERE personKey IS NOT NULL ORDER BY personKey ASC, quality DESC, mediaId ASC"
    )
    fun observePersonAssignments(): Flow<List<PersonAssignmentRow>>

    @Transaction
    suspend fun replacePersonAssignments(assignments: List<PersonAssignmentRow>) {
        clearPersonAssignments()
        assignments.forEach { assignPerson(it.mediaId, it.faceIndex, it.personKey) }
    }

    // ------------------------------------------------------------------ search history

    @Query("SELECT * FROM search_history ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeSearchHistory(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE `query` = :query")
    suspend fun getSearchHistoryEntry(query: String): SearchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchHistory(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    /** Keeps the table from growing without bound as queries accumulate. */
    @Query("DELETE FROM search_history WHERE `query` NOT IN (SELECT `query` FROM search_history ORDER BY lastUsedAt DESC LIMIT :keep)")
    suspend fun trimSearchHistory(keep: Int)

    @Transaction
    suspend fun recordSearch(query: String, now: Long, keep: Int) {
        val existing = getSearchHistoryEntry(query)
        upsertSearchHistory(
            SearchHistoryEntity(
                query = query,
                lastUsedAt = now,
                useCount = (existing?.useCount ?: 0) + 1,
            )
        )
        trimSearchHistory(keep)
    }

    // ------------------------------------------------------------- people group renaming

    @Query("SELECT * FROM person_group_preferences ORDER BY updatedAt DESC")
    fun observePersonGroupPreferences(): Flow<List<PersonGroupPreferenceEntity>>

    @Query("SELECT * FROM person_group_preferences")
    suspend fun getPersonGroupPreferences(): List<PersonGroupPreferenceEntity>

    /** Every photo a named person appears in — see [com.pandagallery.app.data.smart.personKeysMatchingName]. */
    @Query("SELECT DISTINCT mediaId FROM face_embeddings WHERE personKey IN (:personKeys)")
    suspend fun mediaIdsForPersonKeys(personKeys: Collection<String>): List<Long>

    @Query("SELECT * FROM person_group_preferences WHERE groupKey = :groupKey")
    suspend fun getPersonGroupPreference(groupKey: String): PersonGroupPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonGroupPreference(entity: PersonGroupPreferenceEntity)

    @Query("DELETE FROM person_group_preferences")
    suspend fun clearPersonGroupPreferences()

    // ------------------------------------------------------- photos removed from a person

    @Query("SELECT * FROM person_media_exclusions")
    fun observePersonMediaExclusions(): Flow<List<PersonMediaExclusionEntity>>

    @Query("SELECT * FROM person_media_exclusions")
    suspend fun getPersonMediaExclusions(): List<PersonMediaExclusionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonMediaExclusions(rows: List<PersonMediaExclusionEntity>)

    /** Undo of a removal: the photo goes back to being part of the group. */
    @Query("DELETE FROM person_media_exclusions WHERE groupKey = :groupKey AND mediaId IN (:mediaIds)")
    suspend fun deletePersonMediaExclusions(groupKey: String, mediaIds: List<Long>)

    /** Puts every photo removed from one group back, for a removal regretted later. */
    @Query("DELETE FROM person_media_exclusions WHERE groupKey = :groupKey")
    suspend fun deletePersonMediaExclusions(groupKey: String)

    @Query("DELETE FROM person_media_exclusions")
    suspend fun clearPersonMediaExclusions()

    /** Room-side wipe. The FTS table is cleared alongside this by [com.pandagallery.app.data.smart.SmartIndexRepository]. */
    @Transaction
    suspend fun clearAllSmartData() {
        clear()
        clearFaces()
        clearPersonGroupPreferences()
        clearPersonMediaExclusions()
    }

    @Transaction
    suspend fun clearAllPeopleData() {
        clearFaces()
        clearFaceFlags()
        clearPersonGroupPreferences()
        clearPersonMediaExclusions()
    }

    @Transaction
    suspend fun mergePersonGroups(sourceKey: String, destinationKey: String, updatedAt: Long) {
        val source = getPersonGroupPreference(sourceKey)
        val destination = getPersonGroupPreference(destinationKey)
        if (destination == null) {
            upsertPersonGroupPreference(PersonGroupPreferenceEntity(destinationKey, null, null, updatedAt))
        }
        upsertPersonGroupPreference(
            PersonGroupPreferenceEntity(
                groupKey = sourceKey,
                name = source?.name,
                mergedIntoKey = destinationKey,
                updatedAt = updatedAt,
                isDismissed = source?.isDismissed == true,
                isPinned = source?.isPinned == true,
                // The cover belongs to whichever group is now displayed, so a merged-away
                // group's choice is dropped rather than fighting the destination's.
                coverMediaId = null,
            )
        )
    }

}

/** SQLite's default SQLITE_MAX_VARIABLE_NUMBER is 999; stay clear of it. */
private const val SQLITE_BIND_LIMIT = 900
