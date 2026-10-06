package com.pandaapps.appstore.install

import com.pandaapps.appstore.install.ApkVerifier.Companion.CATALOG_SIGNER_MESSAGE
import com.pandaapps.appstore.install.ApkVerifier.Companion.UNREADABLE_SIGNER_MESSAGE
import com.pandaapps.appstore.install.ApkVerifier.Companion.signerMismatchMessage
import com.pandaapps.appstore.install.ApkVerifier.Companion.signerProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkVerifierSignerTest {

    private val ours = "fac6173b9c"
    private val other = "0123456789"

    @Test
    fun matchingSigners_pass() {
        assertNull(signerProblem("Panda Garage", setOf(ours), catalogSigner = ours, installedSigners = null))
        assertNull(signerProblem("Panda Garage", setOf(ours), catalogSigner = ours, installedSigners = setOf(ours)))
    }

    @Test
    fun catalogSigner_isComparedCaseAndWhitespaceInsensitively() {
        assertNull(signerProblem("Panda Garage", setOf(ours), catalogSigner = "  FAC6173B9C ", installedSigners = null))
    }

    @Test
    fun unreadableSigners_failClosed() {
        // API 28 used to return no archive signers at all; that must never mean "skip the checks".
        assertEquals(UNREADABLE_SIGNER_MESSAGE, signerProblem("Panda Garage", emptySet(), ours, null))
        assertEquals(UNREADABLE_SIGNER_MESSAGE, signerProblem("Panda Garage", emptySet(), null, null))
        assertEquals(UNREADABLE_SIGNER_MESSAGE, signerProblem("Panda Garage", emptySet(), ours, setOf(ours)))
    }

    @Test
    fun archiveSignedByAnotherKey_thanTheCatalog_isRejected() {
        assertEquals(CATALOG_SIGNER_MESSAGE, signerProblem("Panda Garage", setOf(other), ours, null))
    }

    @Test
    fun blankCatalogSigner_skipsOnlyTheCatalogCheck() {
        assertNull(signerProblem("Panda Garage", setOf(other), catalogSigner = " ", installedSigners = null))
        assertEquals(
            signerMismatchMessage("Panda Garage"),
            signerProblem("Panda Garage", setOf(other), catalogSigner = null, installedSigners = setOf(ours)),
        )
    }

    @Test
    fun installedAppWithAnotherKey_getsTheUninstallFirstMessage() {
        assertEquals(
            signerMismatchMessage("Panda Garage"),
            signerProblem("Panda Garage", setOf(ours), catalogSigner = ours, installedSigners = setOf(other)),
        )
    }

    @Test
    fun rotatedKeyHistory_stillMatches() {
        assertNull(signerProblem("Panda Garage", setOf(ours), ours, installedSigners = setOf(other, ours)))
    }

    @Test
    fun installedWithUnknownSigners_isNotTreatedAsMismatch() {
        assertNull(signerProblem("Panda Garage", setOf(ours), ours, installedSigners = emptySet()))
    }
}
