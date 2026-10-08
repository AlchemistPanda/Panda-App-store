package com.pandagallery.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumPathTest {
    @Test
    fun `new album path sanitizes unsafe filename characters`() {
        assertEquals("Pictures/Summer Trip/", newAlbumRelativePath("  Summer/Trip  "))
        assertEquals("Pictures/Family 2026/", newAlbumRelativePath("Family:2026"))
    }

    @Test
    fun `existing album path always has one trailing separator`() {
        assertEquals("DCIM/Camera/", normalizedAlbumRelativePath("/DCIM/Camera"))
        assertEquals("Pictures/Screenshots/", normalizedAlbumRelativePath("Pictures/Screenshots///"))
    }
}
