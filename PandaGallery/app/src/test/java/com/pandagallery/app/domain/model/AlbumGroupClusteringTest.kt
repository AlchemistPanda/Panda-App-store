package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumGroupClusteringTest {

    private fun createAlbum(
        id: Long,
        name: String,
        groupName: String? = null,
        mediaCount: Int = 10,
        sizeBytes: Long = 1024L,
        lastModified: Long = id * 1000L,
        isPinned: Boolean = false,
    ): Album = Album(
        id = id,
        name = name,
        coverUri = null,
        mediaCount = mediaCount,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        isPinned = isPinned,
        groupName = groupName,
    )

    @Test
    fun `ungrouped albums remain single items`() {
        val albums = listOf(
            createAlbum(1, "Camera"),
            createAlbum(2, "Screenshots"),
        )
        val displayItems = clusterAlbumsForDisplay(albums, AlbumSortOrder.NAME_ASC)
        assertEquals(2, displayItems.size)
        assertTrue(displayItems.all { it is AlbumDisplayItem.Single })
    }

    @Test
    fun `grouped albums collapse into single group item with correct aggregates`() {
        val albums = listOf(
            createAlbum(1, "Camera"),
            createAlbum(2, "Kyoto Trip", groupName = "Vacations", mediaCount = 50, sizeBytes = 5000L),
            createAlbum(3, "Tokyo Trip", groupName = "Vacations", mediaCount = 30, sizeBytes = 3000L),
            createAlbum(4, "Screenshots"),
        )
        val displayItems = clusterAlbumsForDisplay(albums, AlbumSortOrder.NAME_ASC)
        assertEquals(3, displayItems.size)

        val group = displayItems.filterIsInstance<AlbumDisplayItem.Group>().first()
        assertEquals("Vacations", group.name)
        assertEquals(2, group.albums.size)
        assertEquals(80, group.totalMediaCount)
        assertEquals(8000L, group.totalSizeBytes)
    }

    @Test
    fun `group covers take at most 4 preview URIs`() {
        val albums = (1..6).map { i ->
            createAlbum(i.toLong(), "Album $i", groupName = "MassiveGroup")
        }
        val displayItems = clusterAlbumsForDisplay(albums, AlbumSortOrder.NEWEST)
        val group = displayItems.filterIsInstance<AlbumDisplayItem.Group>().first()
        assertEquals(6, group.albums.size)
    }

    @Test
    fun `pinned group sorts to top`() {
        val albums = listOf(
            createAlbum(1, "Zebra"),
            createAlbum(2, "Apple", groupName = "ImportantGroup", isPinned = true),
        )
        val displayItems = clusterAlbumsForDisplay(albums, AlbumSortOrder.NAME_ASC)
        assertEquals(2, displayItems.size)
        assertTrue(displayItems.first() is AlbumDisplayItem.Group)
    }
}
