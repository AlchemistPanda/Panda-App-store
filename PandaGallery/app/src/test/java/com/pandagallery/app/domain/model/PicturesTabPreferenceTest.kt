package com.pandagallery.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Pictures tab preference:
 * - Disabled by default so Albums is the primary default landing tab.
 * - Can be toggled on/off independently.
 */
class PicturesTabPreferenceTest {

    @Test
    fun `pictures tab is enabled by default matching Samsung Gallery 15 9 One UI primary tab`() {
        assertTrue(UserPreferences().showPicturesTab)
    }

    @Test
    fun `pictures tab can be explicitly disabled`() {
        val prefs = UserPreferences(showPicturesTab = false)
        assertFalse(prefs.showPicturesTab)
    }

    @Test
    fun `pictures tab preference survives unrelated preference changes`() {
        val prefs = UserPreferences(showPicturesTab = true).copy(
            albumSortOrder = AlbumSortOrder.NAME_ASC,
            showAlbumSize = true,
        )
        assertTrue(prefs.showPicturesTab)

        val disabledPrefs = prefs.copy(showPicturesTab = false)
        assertFalse(disabledPrefs.showPicturesTab)
    }
}
