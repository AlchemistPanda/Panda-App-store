package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumSortingTest {
    private val albums = listOf(
        Album(1, "Camera", null, 3, 100, isPinned = false),
        Album(2, "Downloads", null, 8, 300, isPinned = true),
        Album(3, "Animals", null, 5, 200, isPinned = false),
    )

    @Test
    fun `pinned albums remain first when sorting by name`() {
        assertEquals(listOf(2L, 3L, 1L), sortAlbums(albums, AlbumSortOrder.NAME_ASC).map { it.id })
    }

    @Test
    fun `albums sort by newest and item count`() {
        assertEquals(listOf(2L, 3L, 1L), sortAlbums(albums, AlbumSortOrder.NEWEST).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), sortAlbums(albums, AlbumSortOrder.ITEM_COUNT).map { it.id })
    }

    @Test
    fun `favorite target removes favorite when every selection is already favorite`() {
        assertEquals(false, bulkFavoriteTarget(listOf(true, true)))
        assertEquals(true, bulkFavoriteTarget(listOf(true, false)))
        assertEquals(true, bulkFavoriteTarget(emptyList()))
    }
}
