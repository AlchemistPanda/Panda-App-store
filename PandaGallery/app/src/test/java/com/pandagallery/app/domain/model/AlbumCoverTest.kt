package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumCoverTest {
    private val live = setOf("content://media/1", "content://media/2", "content://media/3")

    @Test
    fun `an album with no chosen cover uses its most recent item`() {
        assertEquals(
            "content://media/3",
            resolveAlbumCover(customCoverUri = null, availableUris = live, automaticCoverUri = "content://media/3"),
        )
    }

    @Test
    fun `a chosen cover wins over the automatic one`() {
        assertEquals(
            "content://media/1",
            resolveAlbumCover("content://media/1", live, "content://media/3"),
        )
    }

    /**
     * The self-healing case. Without this the album shows a permanently blank tile after
     * the cover photo is deleted, with nothing on screen explaining why.
     */
    @Test
    fun `a chosen cover that no longer exists falls back to automatic`() {
        assertEquals(
            "content://media/3",
            resolveAlbumCover("content://media/deleted", live, "content://media/3"),
        )
    }

    @Test
    fun `an empty album has no cover at all`() {
        assertNull(resolveAlbumCover("content://media/deleted", emptySet(), null))
        assertNull(resolveAlbumCover(null, emptySet(), null))
    }

    /**
     * Guards the bug this replaced: `toMetadataEntity` used to default from the *displayed*
     * cover, so writing any other album property persisted the automatic cover as a chosen
     * one and the album silently stopped following new photos. Keeping the two values
     * distinct on the model is what makes that impossible to reintroduce by accident.
     */
    @Test
    fun `an automatic cover is not mistaken for a chosen one`() {
        val automatic = Album(
            id = 1,
            name = "Camera",
            coverUri = null,
            mediaCount = 5,
            lastModified = 0,
            customCoverUri = null,
        )

        assertFalse(automatic.hasCustomCover)
        assertNull(automatic.customCoverUri)
    }

}
