package com.pandagallery.app.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun `folding removes accents and case`() {
        assertEquals("cafe", SearchText.fold("Café"))
        assertEquals("munchen", SearchText.fold("MÜNCHEN"))
        assertEquals("jose", SearchText.fold("José"))
    }

    @Test
    fun `plurals stem to the same token on both sides of the index`() {
        assertEquals(SearchText.tokenize("receipt"), SearchText.tokenize("receipts"))
        assertEquals(SearchText.tokenize("puppy"), SearchText.tokenize("puppies"))
        assertEquals(SearchText.tokenize("box"), SearchText.tokenize("boxes"))
    }

    @Test
    fun `stemming leaves short and ambiguous words alone`() {
        assertEquals("bus", SearchText.stem("bus"))
        assertEquals("glass", SearchText.stem("glass"))
        assertEquals("this", SearchText.stem("this"))
    }

    @Test
    fun `punctuation splits tokens and stop words drop out`() {
        assertEquals(listOf("total", "42", "50"), SearchText.tokenize("Total: 42.50"))
        assertFalse(SearchText.tokenize("a photo of the beach").contains("the"))
    }

    /**
     * A decimal splits into two tokens, which is fine precisely because both sides of the
     * index split it the same way — the query's tokens are AND-ed, so "42.50" still only
     * matches documents containing both halves.
     */
    @Test
    fun `punctuation-split numbers still round-trip`() {
        val indexed = SearchText.normalizeForIndex("Subtotal 42.50 EUR")
        assertTrue(SearchText.tokenize("42.50").all { indexed.split(" ").contains(it) })
    }

    @Test
    fun `digits survive even when short`() {
        assertTrue(SearchText.tokenize("gate 7 boarding").contains("7"))
    }

    @Test
    fun `synonyms expand in both directions`() {
        val puppy = SearchText.tokenize("puppy").single()
        val dog = SearchText.tokenize("dog").single()

        assertTrue(SearchText.expand(puppy).contains(dog))
        assertTrue(SearchText.expand(dog).contains(puppy))
    }

    @Test
    fun `a word with no synonyms expands to itself`() {
        assertEquals(setOf("zqxwv"), SearchText.expand("zqxwv"))
    }

    @Test
    fun `index normalization is idempotent`() {
        val once = SearchText.normalizeForIndex("Receipts from the Café — 42.50")
        assertEquals(once, SearchText.normalizeForIndex(once))
    }
}
