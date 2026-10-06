package com.pandaapps.appstore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppStatusTest {

    private val signer = "fac6170000000000000000000000000000000000000000000000000000003b9c"
    private val otherSigner = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val oldRotatedSigner = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private fun release(
        versionCode: Long = 7,
        minSdk: Int? = 24,
        signerSha256: String? = signer,
    ) = CatalogRelease(
        versionName = "1.0.$versionCode",
        versionCode = versionCode,
        apkUrl = "https://example.com/app-$versionCode.apk",
        signerSha256 = signerSha256,
        minSdk = minSdk,
    )

    private fun installed(versionCode: Long, signers: Set<String> = setOf(signer)) =
        InstalledInfo(
            versionCode = versionCode,
            versionName = "x",
            signerSha256 = signers,
            installerPackage = null,
        )

    // ---- Not installed ----

    @Test
    fun notInstalled_compatibleDevice_isNotInstalled() {
        assertEquals(AppStatus.NotInstalled, computeStatus(release(minSdk = 24), null, deviceSdk = 35))
    }

    @Test
    fun notInstalled_deviceSdkEqualToMinSdk_isNotInstalled() {
        assertEquals(AppStatus.NotInstalled, computeStatus(release(minSdk = 30), null, deviceSdk = 30))
    }

    @Test
    fun notInstalled_deviceBelowMinSdk_isIncompatible() {
        assertEquals(AppStatus.Incompatible, computeStatus(release(minSdk = 34), null, deviceSdk = 33))
    }

    @Test
    fun notInstalled_nullMinSdk_isNotInstalled() {
        assertEquals(AppStatus.NotInstalled, computeStatus(release(minSdk = null), null, deviceSdk = 26))
    }

    @Test
    fun notInstalled_signerIsIrrelevant() {
        assertEquals(AppStatus.NotInstalled, computeStatus(release(signerSha256 = otherSigner), null, deviceSdk = 35))
    }

    // ---- Version comparison (signer matches, device compatible) ----

    @Test
    fun installedLower_isUpdateAvailable() {
        assertEquals(AppStatus.UpdateAvailable, computeStatus(release(versionCode = 7), installed(6), 35))
    }

    @Test
    fun installedEqual_isUpToDate() {
        assertEquals(AppStatus.UpToDate, computeStatus(release(versionCode = 7), installed(7), 35))
    }

    @Test
    fun installedHigher_isInstalledNewer() {
        assertEquals(AppStatus.InstalledNewer, computeStatus(release(versionCode = 7), installed(8), 35))
    }

    @Test
    fun versionCodesBeyondIntRange_compareAsLongs() {
        val big = Int.MAX_VALUE.toLong() + 10
        assertEquals(AppStatus.UpdateAvailable, computeStatus(release(versionCode = big), installed(big - 1), 35))
        assertEquals(AppStatus.UpToDate, computeStatus(release(versionCode = big), installed(big), 35))
    }

    // ---- Incompatible device with an installed app ----

    @Test
    fun installedLower_deviceBelowMinSdk_isIncompatible() {
        assertEquals(AppStatus.Incompatible, computeStatus(release(versionCode = 7, minSdk = 36), installed(6), 35))
    }

    @Test
    fun installedEqual_deviceBelowMinSdk_staysUpToDate() {
        assertEquals(AppStatus.UpToDate, computeStatus(release(versionCode = 7, minSdk = 36), installed(7), 35))
    }

    @Test
    fun installedHigher_deviceBelowMinSdk_staysInstalledNewer() {
        assertEquals(AppStatus.InstalledNewer, computeStatus(release(versionCode = 7, minSdk = 36), installed(8), 35))
    }

    // ---- Signers ----

    @Test
    fun signerMismatch_winsOverEveryVersionComparison() {
        val foreign = setOf(otherSigner)
        assertEquals(AppStatus.SignerMismatch, computeStatus(release(versionCode = 7), installed(6, foreign), 35))
        assertEquals(AppStatus.SignerMismatch, computeStatus(release(versionCode = 7), installed(7, foreign), 35))
        assertEquals(AppStatus.SignerMismatch, computeStatus(release(versionCode = 7), installed(8, foreign), 35))
    }

    @Test
    fun signerMismatch_winsOverIncompatible() {
        assertEquals(
            AppStatus.SignerMismatch,
            computeStatus(release(versionCode = 7, minSdk = 36), installed(6, setOf(otherSigner)), 35),
        )
    }

    @Test
    fun installedWithNoKnownSigners_andCatalogSigner_isSignerMismatch() {
        assertEquals(AppStatus.SignerMismatch, computeStatus(release(), installed(6, emptySet()), 35))
    }

    @Test
    fun rotatedKeyHistory_matchesCatalogSigner() {
        // Installed app reports its whole signing-certificate history (old + current key).
        val history = setOf(oldRotatedSigner, signer)
        assertEquals(AppStatus.UpdateAvailable, computeStatus(release(versionCode = 7), installed(6, history), 35))
        assertEquals(AppStatus.UpToDate, computeStatus(release(versionCode = 7), installed(7, history), 35))
    }

    @Test
    fun rotatedKeyHistory_matchingOldKeyInCatalog_stillMatches() {
        val history = setOf(oldRotatedSigner, signer)
        assertEquals(
            AppStatus.UpdateAvailable,
            computeStatus(release(versionCode = 7, signerSha256 = oldRotatedSigner), installed(6, history), 35),
        )
    }

    @Test
    fun nullCatalogSigner_skipsSignerCheck() {
        val r = release(versionCode = 7, signerSha256 = null)
        assertEquals(AppStatus.UpdateAvailable, computeStatus(r, installed(6, setOf(otherSigner)), 35))
        assertEquals(AppStatus.UpToDate, computeStatus(r, installed(7, emptySet()), 35))
        assertEquals(AppStatus.InstalledNewer, computeStatus(r, installed(8, setOf(otherSigner)), 35))
    }

    @Test
    fun blankCatalogSigner_isTreatedAsUnknown() {
        assertEquals(
            AppStatus.UpdateAvailable,
            computeStatus(release(versionCode = 7, signerSha256 = "  "), installed(6, setOf(otherSigner)), 35),
        )
    }

    @Test
    fun signerComparison_ignoresCaseAndSurroundingWhitespace() {
        val r = release(versionCode = 7, signerSha256 = "  ${signer.uppercase()} ")
        assertEquals(AppStatus.UpToDate, computeStatus(r, installed(7, setOf(signer)), 35))
        assertEquals(AppStatus.UpToDate, computeStatus(release(versionCode = 7), installed(7, setOf(signer.uppercase())), 35))
    }

    // ---- Full truth table ----

    private enum class Signer { CatalogNull, Match, Mismatch }
    private enum class Version { Lower, Equal, Higher }

    @Test
    fun fullTruthTable_installed() {
        data class Row(val version: Version, val signer: Signer, val tooOld: Boolean, val expected: AppStatus)

        val rows = listOf(
            Row(Version.Lower, Signer.Match, false, AppStatus.UpdateAvailable),
            Row(Version.Equal, Signer.Match, false, AppStatus.UpToDate),
            Row(Version.Higher, Signer.Match, false, AppStatus.InstalledNewer),
            Row(Version.Lower, Signer.Match, true, AppStatus.Incompatible),
            Row(Version.Equal, Signer.Match, true, AppStatus.UpToDate),
            Row(Version.Higher, Signer.Match, true, AppStatus.InstalledNewer),
            Row(Version.Lower, Signer.CatalogNull, false, AppStatus.UpdateAvailable),
            Row(Version.Equal, Signer.CatalogNull, false, AppStatus.UpToDate),
            Row(Version.Higher, Signer.CatalogNull, false, AppStatus.InstalledNewer),
            Row(Version.Lower, Signer.CatalogNull, true, AppStatus.Incompatible),
            Row(Version.Equal, Signer.CatalogNull, true, AppStatus.UpToDate),
            Row(Version.Higher, Signer.CatalogNull, true, AppStatus.InstalledNewer),
            Row(Version.Lower, Signer.Mismatch, false, AppStatus.SignerMismatch),
            Row(Version.Equal, Signer.Mismatch, false, AppStatus.SignerMismatch),
            Row(Version.Higher, Signer.Mismatch, false, AppStatus.SignerMismatch),
            Row(Version.Lower, Signer.Mismatch, true, AppStatus.SignerMismatch),
            Row(Version.Equal, Signer.Mismatch, true, AppStatus.SignerMismatch),
            Row(Version.Higher, Signer.Mismatch, true, AppStatus.SignerMismatch),
        )

        for (row in rows) {
            val latest = release(
                versionCode = 10,
                minSdk = 30,
                signerSha256 = if (row.signer == Signer.CatalogNull) null else signer,
            )
            val installedCode = when (row.version) {
                Version.Lower -> 9L
                Version.Equal -> 10L
                Version.Higher -> 11L
            }
            val installedSigners = if (row.signer == Signer.Mismatch) setOf(otherSigner) else setOf(signer)
            val deviceSdk = if (row.tooOld) 29 else 30
            assertEquals(
                "row $row",
                row.expected,
                computeStatus(latest, installed(installedCode, installedSigners), deviceSdk),
            )
        }
    }

    // ---- Helpers ----

    @Test
    fun toStoreApp_usesHighestVersionCodeAndComputesStatus() {
        val app = CatalogApp(
            packageName = "com.example.app",
            name = "Example",
            releases = listOf(release(versionCode = 6), release(versionCode = 7)),
        )
        val store = app.toStoreApp(installed(6), deviceSdk = 35)
        assertNotNull(store)
        store!!
        assertEquals(7L, store.latest.versionCode)
        assertEquals(AppStatus.UpdateAvailable, store.status)
        assertEquals("com.example.app", store.packageName)
        assertEquals("Example", store.name)
        assertSame(app, store.app)
    }

    @Test
    fun toStoreApp_withoutReleases_isNull() {
        val app = CatalogApp(packageName = "com.example.app", name = "Example")
        assertNull(app.toStoreApp(null, deviceSdk = 35))
    }

    @Test
    fun notifiedKey_isPackageColonVersionCode() {
        assertEquals("com.pandacollection.pandagarage:7", notifiedKey("com.pandacollection.pandagarage", 7))
    }
}
