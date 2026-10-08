package com.pandagallery.app.data.compression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionOutputUriTest {
    @Test
    fun `image candidates bridge external and external primary MediaStore volumes`() {
        val candidates = compressionOutputUriCandidates(
            mediaId = 42L,
            isVideo = false,
            currentUri = "content://media/external/images/media/42",
        )

        assertEquals(2, candidates.size)
        assertTrue("content://media/external/images/media/42" in candidates)
        assertTrue("content://media/external_primary/images/media/42" in candidates)
    }

    @Test
    fun `video candidates use video collection`() {
        val candidates = compressionOutputUriCandidates(
            mediaId = 7L,
            isVideo = true,
            currentUri = "content://media/external/video/media/7",
        )

        assertTrue("content://media/external_primary/video/media/7" in candidates)
    }
}
