package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineSectionsTest {
    @Test
    fun `date orders keep their day sections`() {
        assertTrue(sortSupportsDateSections(SortOrder.DATE_DESC))
        assertTrue(sortSupportsDateSections(SortOrder.DATE_ASC))
    }

    /**
     * The crash this guards: name and size orders interleave the days, so the grid emitted two
     * sections with the same date key and threw `Key "header_2026-08-18" was already used`. Both
     * halves also mis-reported that day's count and mis-targeted "select all in section".
     */
    @Test
    fun `name and size orders get no day sections`() {
        assertFalse(sortSupportsDateSections(SortOrder.NAME_ASC))
        assertFalse(sortSupportsDateSections(SortOrder.NAME_DESC))
        assertFalse(sortSupportsDateSections(SortOrder.SIZE_DESC))
        assertFalse(sortSupportsDateSections(SortOrder.SIZE_ASC))
    }

    /** A new sort order must make this decision deliberately rather than inherit "yes". */
    @Test
    fun `only the two date orders are sectioned`() {
        assertEquals(
            listOf(SortOrder.DATE_DESC, SortOrder.DATE_ASC),
            SortOrder.entries.filter(::sortSupportsDateSections),
        )
    }
}
