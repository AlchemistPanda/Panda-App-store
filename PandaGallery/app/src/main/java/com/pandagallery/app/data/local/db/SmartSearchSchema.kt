package com.pandagallery.app.data.local.db

/**
 * The full-text search table.
 *
 * Deliberately *not* a Room `@Fts4` entity. Room validates every entity it knows about
 * against the live schema on open, and the DDL it generates for FTS virtual tables has to
 * be reproduced byte-for-byte inside the migration or the app crashes on upgrade. Creating
 * it by hand and reaching it through `@RawQuery` keeps that whole failure mode off the
 * table — Room ignores tables it does not model.
 *
 * `docid` is the media id, so the FTS row and the [com.pandagallery.app.data.local.entity.SmartMediaIndexEntity]
 * row for a photo are joined implicitly.
 *
 * Contents are stored pre-normalized by [com.pandagallery.app.domain.search.SearchText], so
 * `unicode61` only has to split on whitespace — folding, stemming and stop words are
 * already applied on both the index and query side.
 */
object SmartSearchSchema {
    const val TABLE = "smart_search_index"

    const val CREATE_TABLE =
        "CREATE VIRTUAL TABLE IF NOT EXISTS `$TABLE` USING fts4(`labels`, `body`, `name`, tokenize=unicode61)"

    const val DROP_TABLE = "DROP TABLE IF EXISTS `$TABLE`"

    const val UPSERT =
        "INSERT OR REPLACE INTO `$TABLE` (`docid`, `labels`, `body`, `name`) VALUES (?, ?, ?, ?)"

    const val DELETE_BY_DOCID = "DELETE FROM `$TABLE` WHERE `docid` = ?"

    const val CLEAR = "DELETE FROM `$TABLE`"

    const val MATCH_IDS = "SELECT `docid` AS `mediaId` FROM `$TABLE` WHERE `$TABLE` MATCH ?"

    /** Used to spot photos whose text is indexed but whose FTS row is missing (e.g. after upgrade). */
    const val ALL_DOCIDS = "SELECT `docid` AS `mediaId` FROM `$TABLE`"

    fun documentsFor(count: Int): String {
        val placeholders = List(count) { "?" }.joinToString(", ")
        return "SELECT `docid` AS `mediaId`, `labels`, `body`, `name` FROM `$TABLE` WHERE `docid` IN ($placeholders)"
    }
}
