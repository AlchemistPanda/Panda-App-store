package com.pandagallery.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlbumExportNamingTest {

    @Test
    fun `an ordinary album name becomes a zip name`() {
        assertEquals("Holiday.zip", albumExportFileName("Holiday"))
    }

    @Test
    fun `characters that are illegal in filenames are replaced`() {
        val name = albumExportFileName("Trip: Italy / 2024")

        assertEquals("Trip_ Italy _ 2024.zip", name)
        assertFalse(name.contains('/'))
        assertFalse(name.contains(':'))
    }

    @Test
    fun `a blank album name still produces a usable file`() {
        assertEquals("Album.zip", albumExportFileName("   "))
        assertEquals("Album.zip", albumExportFileName(""))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Camera.zip", albumExportFileName("  Camera  "))
    }
}
