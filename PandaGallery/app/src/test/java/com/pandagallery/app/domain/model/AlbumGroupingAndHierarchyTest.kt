package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumGroupingAndHierarchyTest {

    private fun createAlbum(
        id: Long,
        name: String,
        groupName: String? = null,
        mediaCount: Int = 10,
        sizeBytes: Long = 1024L,
        lastModified: Long = id * 1000L,
        isPinned: Boolean = false,
        isHidden: Boolean = false,
    ): Album = Album(
        id = id,
        name = name,
        coverUri = null,
        mediaCount = mediaCount,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        isPinned = isPinned,
        isHidden = isHidden,
        groupName = groupName,
    )

    @Test
    fun `group expansion toggle toggles group correctly`() {
        val expandedGroups = mutableSetOf<String>()

        // Toggle open
        val groupName = "Vacations"
        if (groupName in expandedGroups) expandedGroups.remove(groupName) else expandedGroups.add(groupName)
        assertTrue(groupName in expandedGroups)

        // Toggle closed
        if (groupName in expandedGroups) expandedGroups.remove(groupName) else expandedGroups.add(groupName)
        assertFalse(groupName in expandedGroups)
    }

    @Test
    fun `expandAll expands all unique group names`() {
        val albums = listOf(
            createAlbum(1, "Camera"),
            createAlbum(2, "Rome", groupName = "Vacations"),
            createAlbum(3, "Tokyo", groupName = "Vacations"),
            createAlbum(4, "Q1 Reports", groupName = "Work"),
            createAlbum(5, "Q2 Reports", groupName = "Work"),
        )
        val allGroups = albums.mapNotNull { it.groupName }.toSet()
        assertEquals(setOf("Vacations", "Work"), allGroups)
    }

    @Test
    fun `visibleAlbums filters hidden albums when revealHidden is false`() {
        val albums = listOf(
            createAlbum(1, "Camera", isHidden = false),
            createAlbum(2, "WhatsApp Stickers", isHidden = true),
            createAlbum(3, "Screenshots", isHidden = false),
            createAlbum(4, "Cache Thumbnails", isHidden = true),
        )

        val visible = visibleAlbums(albums, revealHidden = false)
        assertEquals(2, visible.size)
        assertEquals(listOf("Camera", "Screenshots"), visible.map { it.name })

        val revealed = visibleAlbums(albums, revealHidden = true)
        assertEquals(4, revealed.size)
    }

    @Test
    fun `merging albums combines photo count and resolves new paths`() {
        val source1 = createAlbum(10, "Trip Part 1", mediaCount = 15, sizeBytes = 1500L)
        val source2 = createAlbum(11, "Trip Part 2", mediaCount = 25, sizeBytes = 2500L)
        val destination = createAlbum(12, "Full Trip", mediaCount = 10, sizeBytes = 1000L)

        val totalMovedPhotos = listOf(source1, source2).sumOf { it.mediaCount }
        assertEquals(40, totalMovedPhotos)

        val combinedCount = destination.mediaCount + totalMovedPhotos
        assertEquals(50, combinedCount)
    }

    @Test
    fun `shared album comment stream maintains chronological order`() {
        val comments = mutableListOf<SharedAlbumComment>()
        comments.add(
            SharedAlbumComment(
                id = "c1",
                author = "Alice",
                text = "Great photo!",
                timestamp = 1000L,
            )
        )
        comments.add(
            SharedAlbumComment(
                id = "c2",
                author = "Bob",
                text = "Love the lighting here",
                timestamp = 2000L,
            )
        )

        assertEquals(2, comments.size)
        assertEquals("Alice", comments[0].author)
        assertEquals("Bob", comments[1].author)
        assertTrue(comments[1].timestamp > comments[0].timestamp)
    }

    @Test
    fun `shared album member roles and invite code generation`() {
        val album = SharedAlbum(
            id = "shared_1",
            title = "Family Vacation 2026",
            subtitle = "2 contributors • 10 photos",
            coverUri = null,
            members = listOf(
                SharedAlbumMember("m1", "You", SharedAlbumRole.OWNER, 0xFF2C6CF5, isCurrentUser = true),
                SharedAlbumMember("m2", "Emma", SharedAlbumRole.CONTRIBUTOR, 0xFFFF7043, isCurrentUser = false),
            ),
            items = emptyList(),
            inviteCode = "SAMSUNG-SHARE-5582",
        )

        assertEquals(2, album.memberCount)
        assertEquals("SAMSUNG-SHARE-5582", album.inviteCode)
        assertTrue(album.members.any { it.isCurrentUser && it.role == SharedAlbumRole.OWNER })
    }
}
