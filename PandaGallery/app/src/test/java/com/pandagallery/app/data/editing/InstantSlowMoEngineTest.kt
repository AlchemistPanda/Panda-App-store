package com.pandagallery.app.data.editing

import androidx.media3.common.Player
import com.pandagallery.app.domain.model.SlowMoExportConfig
import com.pandagallery.app.domain.model.SlowMoExportResult
import com.pandagallery.app.domain.model.SlowMoSessionState
import com.pandagallery.app.domain.model.SlowMoSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantSlowMoEngineTest {

    @Test
    fun slowMoSpeed_fromMultiplier_returnsNearestSpeed() {
        assertEquals(SlowMoSpeed.QUARTER, SlowMoSpeed.fromMultiplier(0.25f))
        assertEquals(SlowMoSpeed.QUARTER, SlowMoSpeed.fromMultiplier(0.2f))
        assertEquals(SlowMoSpeed.HALF, SlowMoSpeed.fromMultiplier(0.5f))
        assertEquals(SlowMoSpeed.HALF, SlowMoSpeed.fromMultiplier(0.45f))
        assertEquals(SlowMoSpeed.THREE_QUARTERS, SlowMoSpeed.fromMultiplier(0.75f))
        assertEquals(SlowMoSpeed.NORMAL, SlowMoSpeed.fromMultiplier(1.0f))
    }

    @Test
    fun calculateOutputDurationMs_fullDurationStretchedAccurately() {
        val configQuarter = SlowMoExportConfig(
            speedMultiplier = 0.25f,
            trimStartMs = 0L,
            trimEndMs = 0L,
        )
        // 10,000ms at 0.25x -> 40,000ms
        val outputMs = configQuarter.calculateOutputDurationMs(originalDurationMs = 10_000L)
        assertEquals(40_000L, outputMs)

        val configHalf = SlowMoExportConfig(
            speedMultiplier = 0.5f,
            trimStartMs = 0L,
            trimEndMs = 0L,
        )
        // 12,000ms at 0.5x -> 24,000ms
        val outputHalfMs = configHalf.calculateOutputDurationMs(originalDurationMs = 12_000L)
        assertEquals(24_000L, outputHalfMs)
    }

    @Test
    fun calculateOutputDurationMs_trimmedRangeStretchedAccurately() {
        val configTrimmed = SlowMoExportConfig(
            speedMultiplier = 0.25f,
            trimStartMs = 2_000L,
            trimEndMs = 6_000L,
        )
        // Effective range: 4,000ms. At 0.25x -> 16,000ms
        val outputMs = configTrimmed.calculateOutputDurationMs(originalDurationMs = 20_000L)
        assertEquals(16_000L, outputMs)
    }

    @Test
    fun slowMoSessionState_transitionValidation() {
        val initialSession = SlowMoSessionState(
            isActive = false,
            speedMultiplier = 0.25f,
            originalSpeed = 1.0f,
            wasPlayingBeforeHold = true,
            startPositionMs = 5_000L,
        )
        assertFalse(initialSession.isActive)
        assertEquals(1.0f, initialSession.originalSpeed)

        val activeSession = initialSession.copy(
            isActive = true,
            startPositionMs = 7_500L,
        )
        assertTrue(activeSession.isActive)
        assertEquals(0.25f, activeSession.speedMultiplier)
        assertEquals(7_500L, activeSession.startPositionMs)
    }

    @Test
    fun repeatModeToggling_cyclesCorrectly() {
        var currentMode = Player.REPEAT_MODE_OFF
        val toggleMode: (Int) -> Int = { mode ->
            if (mode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        }

        currentMode = toggleMode(currentMode)
        assertEquals(Player.REPEAT_MODE_ONE, currentMode)

        currentMode = toggleMode(currentMode)
        assertEquals(Player.REPEAT_MODE_OFF, currentMode)
    }

    @Test
    fun slowMoExportResult_successValues() {
        val result = SlowMoExportResult(
            isSuccess = true,
            originalDurationMs = 5_000L,
            outputDurationMs = 20_000L,
            savedToVault = true,
            bytesWritten = 10_240_000L,
        )
        assertTrue(result.isSuccess)
        assertEquals(5_000L, result.originalDurationMs)
        assertEquals(20_000L, result.outputDurationMs)
        assertTrue(result.savedToVault)
        assertEquals(10_240_000L, result.bytesWritten)
    }
}
