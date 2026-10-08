package com.pandagallery.app.data.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonNameSearchTest {
    private fun preference(
        key: String,
        name: String? = null,
        mergedInto: String? = null,
        dismissed: Boolean = false,
    ) = PersonGroupPreference(key, name, mergedInto, dismissed)

    @Test
    fun `a named group matches its own name`() {
        val prefs = listOf(preference("p:1", name = "Mum"))
        assertEquals(setOf("p:1"), personKeysMatchingName(prefs, "Mum"))
    }

    @Test
    fun `matching ignores case and matches part of a name`() {
        val prefs = listOf(preference("p:1", name = "Anita Sharma"))
        assertEquals(setOf("p:1"), personKeysMatchingName(prefs, "anita"))
        assertEquals(setOf("p:1"), personKeysMatchingName(prefs, "SHARMA"))
    }

    /**
     * The case merging exists for: clustering split one person in two, the user merged them and
     * named the survivor. Photos still filed under the merged-away group are the same person, so
     * they have to answer to that name too.
     */
    @Test
    fun `a merged-away group answers to the name of the group it merged into`() {
        val prefs = listOf(
            preference("p:1", mergedInto = "p:2"),
            preference("p:2", name = "Mum"),
        )
        assertEquals(setOf("p:1", "p:2"), personKeysMatchingName(prefs, "Mum"))
    }

    @Test
    fun `dismissed groups stay out of search`() {
        val prefs = listOf(preference("p:1", name = "Poster", dismissed = true))
        assertTrue(personKeysMatchingName(prefs, "Poster").isEmpty())
    }

    @Test
    fun `unnamed groups match nothing`() {
        assertTrue(personKeysMatchingName(listOf(preference("p:1")), "Mum").isEmpty())
    }

    @Test
    fun `an empty query matches nobody rather than everybody`() {
        val prefs = listOf(preference("p:1", name = "Mum"))
        assertTrue(personKeysMatchingName(prefs, "").isEmpty())
        assertTrue(personKeysMatchingName(prefs, "   ").isEmpty())
    }

    /** Repeated merges can be edited into a loop; resolving one must still terminate. */
    @Test
    fun `a merge loop does not hang`() {
        val prefs = listOf(
            preference("p:1", mergedInto = "p:2"),
            preference("p:2", name = "Mum", mergedInto = "p:1"),
        )
        assertEquals(setOf("p:1", "p:2"), personKeysMatchingName(prefs, "Mum"))
    }
}
