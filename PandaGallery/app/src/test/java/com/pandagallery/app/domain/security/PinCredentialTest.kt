package com.pandagallery.app.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinCredentialTest {
    @Test
    fun `the PIN that was set verifies`() {
        val record = hashPin("4821")
        assertTrue(verifyPin("4821", record))
    }

    @Test
    fun `any other PIN does not`() {
        val record = hashPin("4821")
        assertFalse(verifyPin("4822", record))
        assertFalse(verifyPin("482", record))
        assertFalse(verifyPin("48210", record))
        assertFalse(verifyPin("", record))
    }

    /** The PIN must not be recoverable from what gets written to storage. */
    @Test
    fun `the stored record does not contain the PIN`() {
        assertFalse(hashPin("4821").contains("4821"))
    }

    /**
     * A per-PIN salt is what stops the two locks from betraying each other: using one PIN for both
     * folders and the vault must not produce two identical records, or reading the preference file
     * would reveal that they match.
     */
    @Test
    fun `the same PIN hashes differently every time`() {
        val first = hashPin("4821")
        val second = hashPin("4821")
        assertNotEquals(first, second)
        assertTrue(verifyPin("4821", first))
        assertTrue(verifyPin("4821", second))
    }

    /** A lock screen asking about an unreadable or absent record must be told "no", not crash. */
    @Test
    fun `a missing or damaged record never verifies`() {
        assertFalse(verifyPin("4821", null))
        assertFalse(verifyPin("4821", ""))
        assertFalse(verifyPin("4821", "4821"))
        assertFalse(verifyPin("4821", "pbkdf2-sha256\$200000\$notbase64\$notbase64"))
        assertFalse(verifyPin("4821", "pbkdf2-sha256\$0\$c2FsdA\$aGFzaA"))
        assertFalse(verifyPin("4821", "sha1\$1\$c2FsdA\$aGFzaA"))
        assertFalse(verifyPin("4821", hashPin("4821").substringBeforeLast('$')))
    }

    /** The record states its own parameters so the iteration count can be raised later. */
    @Test
    fun `the record is self describing`() {
        val parts = hashPin("4821").split('$')
        assertEquals(4, parts.size)
        assertEquals("pbkdf2-sha256", parts[0])
        assertTrue(parts[1].toInt() >= 200_000)
    }

    @Test
    fun `pins are four to eight digits`() {
        assertTrue(isValidPin("1234"))
        assertTrue(isValidPin("12345678"))
        assertFalse(isValidPin("123"))
        assertFalse(isValidPin("123456789"))
        assertFalse(isValidPin("12a4"))
        assertFalse(isValidPin("12 4"))
        assertFalse(isValidPin(""))
    }
}
