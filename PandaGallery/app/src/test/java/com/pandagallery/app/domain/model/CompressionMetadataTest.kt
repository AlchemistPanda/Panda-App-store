package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompressionMetadataTest {
    @Test
    fun `compression percent uses original and current byte sizes`() {
        assertEquals(75, calculateCompressionPercent(originalBytes = 4_000L, currentBytes = 1_000L))
        assertEquals(0, calculateCompressionPercent(originalBytes = 1_000L, currentBytes = 1_500L))
    }

    @Test
    fun `compression percent is absent without a valid original size`() {
        assertNull(calculateCompressionPercent(originalBytes = null, currentBytes = 500L))
        assertNull(calculateCompressionPercent(originalBytes = 0L, currentBytes = 500L))
    }

    @Test
    fun `metadata sizes use the same readable formatter`() {
        assertEquals("1.0 MB", formatFileSize(1_048_576L))
        assertEquals("512 KB", formatFileSize(524_288L))
    }
}
