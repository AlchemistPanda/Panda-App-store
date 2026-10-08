package com.pandagallery.app.data.repository

import com.pandagallery.app.data.local.entity.MediaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumCoverSelectionTest {
    private fun item(id: Long, dateTaken: Long?, dateAddedSeconds: Long) = MediaEntity(
        id = id,
        uri = "content://media/$id",
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        size = 1_000L,
        width = 100,
        height = 100,
        dateAdded = dateAddedSeconds,
        dateModified = dateAddedSeconds,
        dateTaken = dateTaken,
        duration = null,
        bucketId = 1L,
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
    )

    @Test
    fun `the most recently captured item becomes the cover`() {
        val newest = item(id = 2, dateTaken = 2_000_000_000_000L, dateAddedSeconds = 1_000_000)
        val older = item(id = 1, dateTaken = 1_000_000_000_000L, dateAddedSeconds = 1_000_000)

        assertEquals(newest, newestInAlbum(listOf(older, newest)))
    }

    /**
     * The reported bug. A screenshot taken minutes ago has no `dateTaken`, so ordering on that
     * column alone left the album showing a photo from years back — the newest item in the folder
     * was the one the cover ignored.
     */
    @Test
    fun `an item with no capture date can still be the newest`() {
        val screenshotToday = item(id = 9, dateTaken = 0L, dateAddedSeconds = 1_800_000_000)
        val oldPhoto = item(id = 3, dateTaken = 1_400_000_000_000L, dateAddedSeconds = 1_400_000_000)

        assertEquals(screenshotToday, newestInAlbum(listOf(oldPhoto, screenshotToday)))
    }

    /** Equal dates resolve the same way every emission, so covers do not flicker between two items. */
    @Test
    fun `items sharing a date resolve to the same one every time`() {
        val a = item(id = 4, dateTaken = 1_700_000_000_000L, dateAddedSeconds = 1_700_000_000)
        val b = item(id = 5, dateTaken = 1_700_000_000_000L, dateAddedSeconds = 1_700_000_000)

        assertEquals(b, newestInAlbum(listOf(a, b)))
        assertEquals(b, newestInAlbum(listOf(b, a)))
    }

    @Test
    fun `an empty album has no cover candidate`() {
        assertNull(newestInAlbum(emptyList()))
    }
}
