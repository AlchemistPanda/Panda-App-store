package com.pandagallery.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CompressionConcurrencyPreferenceTest {

    @Test
    fun `compression concurrency defaults to core-based optimal count`() {
        val defaultPreferences = UserPreferences()
        assertEquals(defaultCompressionConcurrency(), defaultPreferences.compressionConcurrency)
    }

    @Test
    fun `compression concurrency allows configuring parallel values up to 10`() {
        val preferences = UserPreferences(compressionConcurrency = 4)
        assertEquals(4, preferences.compressionConcurrency)
    }

    @Test
    fun `compression concurrency bounds are properly coerced within 1 to 10`() {
        assertEquals(1, 0.coerceIn(1, 10))
        assertEquals(1, (-5).coerceIn(1, 10))
        assertEquals(1, 1.coerceIn(1, 10))
        assertEquals(5, 5.coerceIn(1, 10))
        assertEquals(10, 10.coerceIn(1, 10))
        assertEquals(10, 15.coerceIn(1, 10))
    }
}
