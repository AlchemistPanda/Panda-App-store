package com.pandagallery.app.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write side of the full-text table.
 *
 * Room's `@RawQuery` only covers SELECTs, so inserts and deletes against the virtual table
 * go straight to the SupportSQLiteDatabase. Reads stay on
 * [com.pandagallery.app.data.local.dao.SmartMediaDao] where the cursor mapping is generated.
 */
@Singleton
class SmartSearchIndex @Inject constructor(
    private val database: PandaDatabase,
) {
    private val writable: SupportSQLiteDatabase
        get() = database.openHelper.writableDatabase

    /**
     * Replaces one photo's searchable text. All three fields must already be normalized by
     * [com.pandagallery.app.domain.search.SearchText].
     */
    fun upsert(mediaId: Long, labels: String, body: String, name: String) {
        writable.execSQL(SmartSearchSchema.UPSERT, arrayOf<Any>(mediaId, labels, body, name))
    }

    /** Batched so pruning a large deletion is one transaction rather than N. */
    fun delete(mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        val db = writable
        db.beginTransaction()
        try {
            mediaIds.forEach { db.execSQL(SmartSearchSchema.DELETE_BY_DOCID, arrayOf<Any>(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clear() {
        writable.execSQL(SmartSearchSchema.CLEAR)
    }

    /**
     * Rebuilds the virtual table from scratch. Used when the schema of what we store
     * changes, so stale rows indexed under old normalization rules cannot linger and
     * silently fail to match.
     */
    fun recreate() {
        val db = writable
        db.execSQL(SmartSearchSchema.DROP_TABLE)
        db.execSQL(SmartSearchSchema.CREATE_TABLE)
    }
}
