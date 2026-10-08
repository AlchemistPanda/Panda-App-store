package com.pandagallery.app.data.smart

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartIndexPolicyTest {
    @Test
    fun `unchanged fully indexed photo is skipped`() {
        assertFalse(shouldReindex(10, 10, includeFaces = true, facesIndexed = true))
    }

    @Test
    fun `enabling faces revisits photo indexed without faces`() {
        assertTrue(shouldReindex(10, 10, includeFaces = true, facesIndexed = false))
    }

    @Test
    fun `modified photo is always revisited`() {
        assertTrue(shouldReindex(10, 11, includeFaces = false, facesIndexed = true))
    }
}
