package com.pandagallery.app.data.compression

import java.io.ByteArrayInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionInputStreamTest {
    @Test
    fun `successful stream open does not depend on decoder return value`() {
        var decoded = false

        withRequiredInputStream(
            open = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
            errorMessage = "Cannot open image input stream",
        ) {
            decoded = true
            null
        }

        assertTrue(decoded)
    }
}
