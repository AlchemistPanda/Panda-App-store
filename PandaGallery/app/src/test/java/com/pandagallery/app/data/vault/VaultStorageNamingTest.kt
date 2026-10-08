package com.pandagallery.app.data.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that keeps the private folder readable when encryption is switched.
 *
 * Guards a bug that reached the device: the storage mode was recorded only in the database, so a
 * conversion interrupted between rewriting a file and updating its row left the two disagreeing.
 * Guessing wrong is not symmetric — decrypting a plaintext file throws, but reading an encrypted
 * file "as plaintext" succeeds and hands back ciphertext, which would land inside a restored photo
 * as garbage. The file name has to carry the answer.
 */
class VaultStorageNamingTest {
    @Test
    fun `an encrypted item keeps the historical suffix`() {
        assertEquals("abc.vault", vaultFileNameFor("abc", encrypted = true))
    }

    @Test
    fun `a plaintext item is named distinctly`() {
        assertEquals("abc.plainvault", vaultFileNameFor("abc", encrypted = false))
    }

    @Test
    fun `the name alone says how the file is stored`() {
        assertTrue(isEncryptedVaultFileName(vaultFileNameFor("abc", encrypted = true)))
        assertFalse(isEncryptedVaultFileName(vaultFileNameFor("abc", encrypted = false)))
    }

    /** Files written before the setting existed end in `.vault`, and are encrypted. */
    @Test
    fun `pre-existing names are treated as encrypted`() {
        assertTrue(isEncryptedVaultFileName("9023c0c3-be79-4c3d-bd34-63775fc78944.vault"))
    }
}
