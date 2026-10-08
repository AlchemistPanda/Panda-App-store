package com.pandagallery.app.ui.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTransferProgressTest {
    @Test
    fun `progress reads as a count of what is done`() {
        assertEquals("Copying 3 of 12", MediaTransferProgress(MediaOperationMode.COPY, 3, 12).label)
        assertEquals("Moving 0 of 4", MediaTransferProgress(MediaOperationMode.MOVE, 0, 4).label)
        assertEquals("Rotating 2 of 5", MediaTransferProgress(MediaOperationMode.ROTATE, 2, 5).label)
    }

    @Test
    fun `the bar fills in proportion`() {
        assertEquals(0.25f, MediaTransferProgress(MediaOperationMode.COPY, 1, 4).fraction, 0.001f)
        assertEquals(1f, MediaTransferProgress(MediaOperationMode.MOVE, 4, 4).fraction, 0.001f)
        assertEquals(0.5f, MediaTransferProgress(MediaOperationMode.ROTATE, 2, 4).fraction, 0.001f)
    }

    /** An empty transfer must not divide by zero on its way to a progress bar. */
    @Test
    fun `an empty transfer reports no progress rather than crashing`() {
        assertEquals(0f, MediaTransferProgress(MediaOperationMode.COPY, 0, 0).fraction, 0.001f)
    }
}
