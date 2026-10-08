package com.pandagallery.app.data.editing

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoEditStateTest {
    @Test fun `trim range stays within duration and keeps minimum gap`() {
        assertEquals(1_000L..9_000L, normalizedTrimRange(1_000, 9_000, 10_000))
        assertEquals(9_500L..10_000L, normalizedTrimRange(11_000, 9_800, 10_000))
        assertEquals(0L..500L, normalizedTrimRange(-1_000, 100, 10_000))
    }

    @Test
    fun `hasBackgroundAudio returns true when preset or custom audio is set`() {
        val defaultState = VideoEditState()
        assertEquals(false, defaultState.hasBackgroundAudio)

        val presetState = VideoEditState(backgroundMusic = BackgroundMusicTrack.CHILL_LOFI)
        assertEquals(true, presetState.hasBackgroundAudio)

        val customState = VideoEditState(customAudioUri = "content://media/external/audio/123")
        assertEquals(true, customState.hasBackgroundAudio)

        val emptyCustomState = VideoEditState(customAudioUri = "")
        assertEquals(false, emptyCustomState.hasBackgroundAudio)
    }

    @Test
    fun `effectiveBgmVolume correctly attenuates BGM when audio ducking is active and video is audible`() {
        val normalState = VideoEditState(
            bgmVolume = 0.8f,
            videoAudioVolume = 1.0f,
            audioDucking = true,
            mute = false,
        )
        // Ducked to 35% of original BGM volume: 0.8 * 0.35 = 0.28
        assertEquals(0.28f, normalState.effectiveBgmVolume, 0.001f)

        // When muted, no ducking should occur
        val mutedVideoState = normalState.copy(mute = true)
        assertEquals(0.8f, mutedVideoState.effectiveBgmVolume, 0.001f)

        // When video audio volume is zero (silent), no ducking should occur
        val silentVideoState = normalState.copy(videoAudioVolume = 0.0f)
        assertEquals(0.8f, silentVideoState.effectiveBgmVolume, 0.001f)

        // When audio ducking is explicitly disabled
        val noDuckingState = normalState.copy(audioDucking = false)
        assertEquals(0.8f, noDuckingState.effectiveBgmVolume, 0.001f)
    }

    @Test
    fun `effectiveBgmVolume is safely clamped to 0f to 1f range`() {
        val highBgm = VideoEditState(bgmVolume = 1.5f, audioDucking = false)
        assertEquals(1.0f, highBgm.effectiveBgmVolume, 0.001f)

        val negativeBgm = VideoEditState(bgmVolume = -0.5f, audioDucking = false)
        assertEquals(0.0f, negativeBgm.effectiveBgmVolume, 0.001f)
    }
}

