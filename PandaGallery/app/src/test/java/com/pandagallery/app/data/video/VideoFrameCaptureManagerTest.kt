package com.pandagallery.app.data.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFrameCaptureManagerTest {

    @Test
    fun generateCaptureDisplayNameFormatsCorrectly() {
        val name = VideoFrameCaptureManager.generateCaptureDisplayName(1700000000L, 4500L)
        assertEquals("Capture_1700000000_4500ms.jpg", name)
    }

    @Test
    fun calculateTargetPositionClampsWithinDurationBounds() {
        // Forward within bounds
        val pos1 = VideoFrameCaptureManager.calculateTargetPosition(5000L, 10000L, 60000L)
        assertEquals(15000L, pos1)

        // Forward beyond duration clamps to duration
        val pos2 = VideoFrameCaptureManager.calculateTargetPosition(55000L, 10000L, 60000L)
        assertEquals(60000L, pos2)

        // Backward past 0 clamps to 0
        val pos3 = VideoFrameCaptureManager.calculateTargetPosition(4000L, -10000L, 60000L)
        assertEquals(0L, pos3)
    }
}
