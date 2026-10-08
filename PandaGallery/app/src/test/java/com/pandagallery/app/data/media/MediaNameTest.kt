package com.pandagallery.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaNameTest {
    @Test
    fun `rename preserves the original extension`() {
        assertEquals("Beach sunset.jpg", renamedMediaDisplayName("IMG_0042.jpg", "Beach sunset"))
        assertEquals("Holiday.mp4", renamedMediaDisplayName("VID_0042.mp4", "Holiday.mp4"))
    }

    @Test
    fun `rename removes unsafe path characters`() {
        assertEquals("Family trip.png", renamedMediaDisplayName("old.png", "Family/trip"))
    }
}
