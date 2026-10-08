package com.pandagallery.app.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `each token becomes its own match expression`() {
        val parsed = SearchQuery.parse("coffee receipt")

        assertEquals(2, parsed.matchExpressions.size)
        // AND across expressions is applied by intersecting ids, so no expression may
        // contain a bare AND that would depend on SQLite's optional enhanced syntax.
        assertTrue(parsed.matchExpressions.none { it.contains("AND") })
        assertTrue(parsed.matchExpressions.all { it.contains("*") })
    }

    @Test
    fun `a match expression ORs the whole synonym cluster`() {
        val expression = SearchQuery.parse("puppy").matchExpressions.single()

        assertTrue(expression, expression.contains("dog*"))
        assertTrue(expression, expression.contains("OR"))
    }

    @Test
    fun `an empty or stop-word-only query parses to nothing`() {
        assertTrue(SearchQuery.parse("").isEmpty)
        assertTrue(SearchQuery.parse("   ").isEmpty)
        assertTrue(SearchQuery.parse("the and of").isEmpty)
    }

    @Test
    fun `a label hit outranks a buried OCR hit`() {
        val parsed = SearchQuery.parse("dog")
        val ranked = SearchQuery.rank(
            parsed,
            listOf(
                document(1, body = "dog"),
                document(2, labels = "dog"),
            ),
            "dog",
        )

        assertEquals(2L, ranked.first().mediaId)
    }

    @Test
    fun `an exact token outranks a prefix-only match`() {
        val parsed = SearchQuery.parse("beach")
        val ranked = SearchQuery.rank(
            parsed,
            listOf(
                document(1, labels = "beachfront"),
                document(2, labels = "beach"),
            ),
            "beach",
        )

        assertEquals(2L, ranked.first().mediaId)
    }

    @Test
    fun `matching more of the query scores higher`() {
        val parsed = SearchQuery.parse("coffee menu")
        val ranked = SearchQuery.rank(
            parsed,
            listOf(
                document(1, labels = "coffee"),
                document(2, labels = "coffee menu"),
            ),
            "coffee menu",
        )

        assertEquals(2L, ranked.first().mediaId)
    }

    @Test
    fun `a repeated word in OCR text cannot outweigh a real label`() {
        val parsed = SearchQuery.parse("cat")
        val ranked = SearchQuery.rank(
            parsed,
            listOf(
                document(1, body = List(40) { "cat" }.joinToString(" ")),
                document(2, labels = "cat"),
            ),
            "cat",
        )

        assertEquals(2L, ranked.first().mediaId)
    }

    @Test
    fun `ranking is stable for equal scores`() {
        val parsed = SearchQuery.parse("dog")
        val documents = listOf(document(1, labels = "dog"), document(2, labels = "dog"))

        assertEquals(
            SearchQuery.rank(parsed, documents, "dog").map(SearchQuery.Hit::mediaId),
            SearchQuery.rank(parsed, documents.reversed(), "dog").map(SearchQuery.Hit::mediaId),
        )
    }

    private fun document(
        mediaId: Long,
        labels: String = "",
        body: String = "",
        name: String = "",
    ) = SearchQuery.Document(mediaId, labels, body, name)
}
