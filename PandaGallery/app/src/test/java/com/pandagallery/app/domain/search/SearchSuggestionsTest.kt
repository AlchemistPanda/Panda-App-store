package com.pandagallery.app.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionsTest {

    private fun row(vararg labels: String) = labels.joinToString(LABEL_SEPARATOR)

    @Test
    fun `labels are ranked by how often they appear`() {
        val suggestions = topLabels(
            List(10) { row("Dog", "Grass") } + List(5) { row("Beach") },
            minimumCount = 3,
        )

        assertEquals(listOf("Dog", "Grass", "Beach"), suggestions.map(LabelSuggestion::label))
        assertEquals(10, suggestions.first().count)
    }

    /**
     * Rare labels are usually misclassifications, and one wrong suggestion makes the whole
     * row read as broken.
     */
    @Test
    fun `labels seen only once or twice are not suggested`() {
        val suggestions = topLabels(List(5) { row("Dog") } + listOf(row("Submarine")), minimumCount = 3)

        assertEquals(listOf("Dog"), suggestions.map(LabelSuggestion::label))
    }

    @Test
    fun `equally common labels sort alphabetically so the row does not reshuffle`() {
        val suggestions = topLabels(List(4) { row("Zebra", "Apple") }, minimumCount = 3)

        assertEquals(listOf("Apple", "Zebra"), suggestions.map(LabelSuggestion::label))
    }

    @Test
    fun `the list is capped`() {
        val rows = (1..30).map { index -> row(*Array(4) { "Label$index" }) }

        assertEquals(5, topLabels(rows, limit = 5, minimumCount = 1).size)
    }

    @Test
    fun `empty and blank labels are ignored`() {
        val suggestions = topLabels(List(4) { row("", "  ", "Dog") }, minimumCount = 3)

        assertEquals(listOf("Dog"), suggestions.map(LabelSuggestion::label))
    }

    @Test
    fun `an unindexed library suggests nothing`() {
        assertTrue(topLabels(emptyList()).isEmpty())
    }

    @Test
    fun `only real queries are remembered`() {
        assertFalse(isWorthRemembering(""))
        assertFalse(isWorthRemembering("   "))
        assertFalse(isWorthRemembering("d"))
        assertTrue(isWorthRemembering("do"))
        assertTrue(isWorthRemembering("  dog  "))
    }
}
