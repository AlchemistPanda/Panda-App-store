package com.pandaapps.appstore.install

import com.pandaapps.appstore.install.ApkVerifier.Companion.CATALOG_SIGNER_MESSAGE
import com.pandaapps.appstore.install.ApkVerifier.Companion.TRUSTED_SIGNERS
import com.pandaapps.appstore.install.ApkVerifier.Companion.UNREADABLE_SIGNER_MESSAGE
import com.pandaapps.appstore.install.ApkVerifier.Companion.UNTRUSTED_SIGNER_MESSAGE
import com.pandaapps.appstore.install.ApkVerifier.Companion.signerMismatchMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkVerifierSignerTest {

    private val ours = "fac6173b9c"
    private val other = "0123456789"
    private val stranger = "deadbeef00"

    /** Both fake keys are trusted, so the tests below exercise the catalog/installed checks only. */
    private val trusted = setOf(ours, other)

    private fun signerProblem(
        appName: String,
        archiveSigners: Set<String>,
        catalogSigner: String?,
        installedSigners: Set<String>?,
    ) = ApkVerifier.signerProblem(appName, archiveSigners, catalogSigner, installedSigners, trustedSigners = trusted)

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

    @Test
    fun untrustedKey_isRejected_evenWhenTheCatalogAgrees() {
        // A compromised catalog listing the attacker's own key must not get a fresh install through.
        assertEquals(UNTRUSTED_SIGNER_MESSAGE, signerProblem("Panda Garage", setOf(stranger), stranger, null))
        assertEquals(UNTRUSTED_SIGNER_MESSAGE, signerProblem("Panda Garage", setOf(stranger), null, null))
    }

    @Test
    fun unreadableSigners_areReportedBeforeTheTrustCheck() {
        assertEquals(UNREADABLE_SIGNER_MESSAGE, signerProblem("Panda Garage", emptySet(), stranger, null))
    }

    @Test
    fun anyTrustedSignerInTheArchive_passesTheTrustCheck() {
        assertNull(signerProblem("Panda Garage", setOf(stranger, ours), ours, null))
    }

    @Test
    fun pinnedOwnerKeys_areTrustedByDefault() {
        val debugKey = "ae234c8a18366995e0f656128c383dc7f6464f42332c913552d12bb9b3abd509"
        val garageKey = "fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c"
        assertNull(ApkVerifier.signerProblem("Casio Hunt", setOf(debugKey), debugKey, null))
        assertNull(ApkVerifier.signerProblem("Panda Garage", setOf(garageKey), garageKey, null))
        assertEquals(UNTRUSTED_SIGNER_MESSAGE, ApkVerifier.signerProblem("Panda Garage", setOf(ours), ours, null))
        assertEquals(setOf(debugKey, garageKey), TRUSTED_SIGNERS)
    }
}
