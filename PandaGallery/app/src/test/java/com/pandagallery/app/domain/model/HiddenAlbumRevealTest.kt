package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAlbumRevealTest {
    private val albums = listOf(
        Album(1, "Camera", null, 3, 300, isPinned = false),
        Album(2, "Screenshots", null, 8, 200, isPinned = false, isHidden = true),
        Album(3, "WhatsApp", null, 5, 100, isPinned = false, isHidden = true),
    )

    @Test
    fun `hidden albums stay out of the grid by default`() {
        assertEquals(listOf(1L), visibleAlbums(albums, revealHidden = false).map(Album::id))
    }

    @Test
    fun `revealing brings hidden albums back without changing their state`() {
        val revealed = visibleAlbums(albums, revealHidden = true)

        assertEquals(listOf(1L, 2L, 3L), revealed.map(Album::id))
        // The reveal is a view concern; the stored flag must be untouched so hiding again
        // is just a matter of flipping the toggle back.
        assertTrue(revealed.filter(Album::isHidden).map(Album::id) == listOf(2L, 3L))
    }

    @Test
    fun `revealing is reversible and lossless`() {
        assertEquals(
            visibleAlbums(albums, revealHidden = false),
            visibleAlbums(visibleAlbums(albums, revealHidden = true), revealHidden = false),
        )
    }

    @Test
    fun `a library with nothing hidden looks the same either way`() {
        val nothingHidden = albums.map { it.copy(isHidden = false) }

        assertEquals(
            visibleAlbums(nothingHidden, revealHidden = false),
            visibleAlbums(nothingHidden, revealHidden = true),
        )
    }

    @Test
    fun `revealed albums still sort by the chosen order`() {
        val sorted = sortAlbums(visibleAlbums(albums, revealHidden = true), AlbumSortOrder.NAME_ASC)

        assertEquals(listOf("Camera", "Screenshots", "WhatsApp"), sorted.map(Album::name))
    }
}
