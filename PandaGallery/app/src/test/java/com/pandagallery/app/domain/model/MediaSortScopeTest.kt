package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSortScopeTest {
    private val overrides = mapOf(42L to SortOrder.NAME_ASC)

    @Test
    fun `a folder with its own sort uses it`() {
        assertEquals(
            SortOrder.NAME_ASC,
            resolveMediaSort(42L, SortOrder.DATE_DESC, overrides, perFolderSortEnabled = true),
        )
    }

    @Test
    fun `a folder without one follows the settings default`() {
        assertEquals(
            SortOrder.SIZE_DESC,
            resolveMediaSort(7L, SortOrder.SIZE_DESC, overrides, perFolderSortEnabled = true),
        )
    }

    /**
     * The timeline, favourites, search and collections all arrive here with a null bucket, and
     * none of them should ever pick up a folder's sort.
     */
    @Test
    fun `views that are not a folder always follow the default`() {
        assertEquals(
            SortOrder.DATE_ASC,
            resolveMediaSort(null, SortOrder.DATE_ASC, overrides, perFolderSortEnabled = true),
        )
    }

    /**
     * Switching per-folder sort off has to make every grid agree again — that is the whole
     * point of the setting — while leaving the stored overrides intact for switching it back.
     */
    @Test
    fun `overrides are ignored but kept while per-folder sort is off`() {
        assertEquals(
            SortOrder.DATE_DESC,
            resolveMediaSort(42L, SortOrder.DATE_DESC, overrides, perFolderSortEnabled = false),
        )
        assertEquals(SortOrder.NAME_ASC, overrides[42L])
    }

    @Test
    fun `overrides survive a round trip through datastore's string set`() {
        val stored = mapOf(1L to SortOrder.NAME_DESC, 2L to SortOrder.SIZE_ASC)
        assertEquals(stored, decodeFolderSortOverrides(encodeFolderSortOverrides(stored)))
    }

    /**
     * A preference file written by a future version, or corrupted, must not take every folder's
     * sort down with it — the readable entries still apply.
     */
    @Test
    fun `unreadable entries are dropped without losing the rest`() {
        assertEquals(
            mapOf(3L to SortOrder.DATE_ASC),
            decodeFolderSortOverrides(setOf("3:DATE_ASC", "notanid:DATE_ASC", "4:SORT_BY_VIBES", "", "5")),
        )
    }
}
