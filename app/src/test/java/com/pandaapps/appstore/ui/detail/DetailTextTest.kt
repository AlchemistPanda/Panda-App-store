package com.pandaapps.appstore.ui.detail

import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.data.CatalogApp
import com.pandaapps.appstore.data.CatalogNote
import com.pandaapps.appstore.data.CatalogRelease
import com.pandaapps.appstore.data.InstalledInfo
import com.pandaapps.appstore.data.StoreApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailTextTest {

    private val latest = CatalogRelease(versionName = "1.0.6", versionCode = 7, apkUrl = "https://x/a.apk", minSdk = 24)

    private fun app(status: AppStatus, installedCode: Long? = 6) = StoreApp(
        app = CatalogApp(packageName = "com.example.garage", name = "Panda Garage", releases = listOf(latest)),
        latest = latest,
        installed = installedCode?.let { InstalledInfo(it, "1.0.$it", emptySet(), null) },
        status = status,
    )

    @Test
    fun androidVersionNames() {
        assertEquals("7.0", androidVersionName(24))
        assertEquals("12L", androidVersionName(32))
        assertEquals("16", androidVersionName(36))
        assertEquals("API 40", androidVersionName(40))
        assertEquals("Android 7.0 (API 24)", androidLabel(24))
        assertEquals("API 40", androidLabel(40))
    }

    @Test
    fun requirementText_handlesMissingAndUnknown() {
        assertNull(requirementText(null))
        assertEquals("Android 8.0 or newer", requirementText(26))
        assertEquals("API 40 or newer", requirementText(40))
    }

    @Test
    fun versionWithBuild_withAndWithoutName() {
        assertEquals("v1.0.6 · build 7", versionWithBuild("1.0.6", 7))
        assertEquals("build 7", versionWithBuild(null, 7))
        assertEquals("build 7", versionWithBuild(" ", 7))
    }

    @Test
    fun everyStatusHasAnExplanation() {
        AppStatus.entries.forEach { status ->
            val text = statusExplanation(app(status), deviceSdk = 35, silent = false)
            assertTrue(status.name, text.title.isNotBlank() && text.body.isNotBlank())
        }
    }

    @Test
    fun updateAvailable_mentionsPromptOnlyWhenNotSilent() {
        val prompt = statusExplanation(app(AppStatus.UpdateAvailable), 35, silent = false).body
        val silent = statusExplanation(app(AppStatus.UpdateAvailable), 35, silent = true).body
        assertTrue(prompt.contains("confirm"))
        assertFalse(silent.contains("confirm the update"))
        assertTrue(prompt.contains("v1.0.6 · build 7") && prompt.contains("v1.0.6 · build 6"))
    }

    @Test
    fun signerMismatch_saysUninstallFirst() {
        assertTrue(statusExplanation(app(AppStatus.SignerMismatch), 35, false).body.contains("Uninstall Panda Garage first"))
    }

    @Test
    fun incompatible_namesBothVersions() {
        val body = statusExplanation(app(AppStatus.Incompatible, installedCode = null), deviceSdk = 23, silent = false).body
        assertTrue(body.contains("Android 7.0 (API 24)"))
        assertTrue(body.contains("Android 6 (API 23)"))
    }

    @Test
    fun releaseIncompatibility_onlyBelowMinSdk() {
        assertNull(releaseIncompatibility(latest, 24))
        assertNull(releaseIncompatibility(latest.copy(minSdk = null), 21))
        assertEquals("Needs Android 7.0 (API 24) or newer.", releaseIncompatibility(latest, 23))
    }

    @Test
    fun olderVersions_excludesCurrentReleasesAndSortsNewestFirst() {
        val r7 = CatalogRelease(versionName = "1.0.6", versionCode = 7, apkUrl = "u7")
        val r6 = CatalogRelease(versionName = "1.0.5", versionCode = 6, apkUrl = "u6")
        val app = CatalogApp(
            packageName = "p.q",
            name = "Q",
            releases = listOf(r7, r6),
            history = listOf(
                CatalogNote("1.0.6", 7, notes = "seven"),
                CatalogNote("1.0.3", 4, notes = "four"),
                CatalogNote("1.0.5", 6, notes = "six"),
                CatalogNote("1.0.4", 5, notes = "five"),
                CatalogNote("1.0.4", 5, notes = "dup"),
            ),
        )
        assertEquals(listOf(5L, 4L), olderVersions(app).map { it.versionCode })
        assertEquals("five", olderVersions(app).first().notes)
        assertTrue(olderVersions(app.copy(history = emptyList())).isEmpty())
    }

    @Test
    fun autoUpdateRowSubtitle_isHonestAboutTheGlobalSwitch() {
        assertEquals("Auto-update is off in Settings", autoUpdateRowSubtitle(globalOn = false, paused = false))
        assertEquals("Auto-update is off in Settings", autoUpdateRowSubtitle(globalOn = false, paused = true))
        assertTrue(autoUpdateRowSubtitle(globalOn = true, paused = true).startsWith("Paused"))
        assertEquals("Updates install in the background", autoUpdateRowSubtitle(globalOn = true, paused = false))
    }
}
