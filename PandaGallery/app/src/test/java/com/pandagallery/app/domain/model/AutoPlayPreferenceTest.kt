package com.pandagallery.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The autoplay setting is read through a "not loaded yet" guard in both video surfaces
 * (ViewerScreen and PrivateVaultScreen). These pin the two contracts that guard depends on.
 */
class AutoPlayPreferenceTest {

    @Test
    fun `autoplay is on by default so existing behaviour is unchanged`() {
        assertTrue(UserPreferences().autoPlayVideos)
    }

    @Test
    fun `the preference survives an unrelated preference change`() {
        val preferences = UserPreferences(autoPlayVideos = false).copy(slideshowIntervalSeconds = 7)

        assertFalse(preferences.autoPlayVideos)
    }

    /**
     * Both screens gate playback on `autoPlayVideos.takeIf { preferencesLoaded }`, treating
     * null as "do not start yet". If that expression ever stopped yielding null before the
     * stored value arrives, a user who turned autoplay off would still hear a video when
     * opening one directly from another app.
     */
    @Test
    fun `playback stays gated until preferences have loaded`() {
        fun gate(autoPlay: Boolean, loaded: Boolean): Boolean? = autoPlay.takeIf { loaded }

        assertNull("must not play before the setting is known", gate(autoPlay = true, loaded = false))
        assertNull(gate(autoPlay = false, loaded = false))
        assertTrue(gate(autoPlay = true, loaded = true) == true)
        assertTrue(gate(autoPlay = false, loaded = true) == false)
    }
}
