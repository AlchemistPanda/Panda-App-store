package com.pandaapps.appstore.ui

import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.data.CatalogApp
import com.pandaapps.appstore.data.CatalogRelease
import com.pandaapps.appstore.data.Settings
import com.pandaapps.appstore.data.StoreApp
import com.pandaapps.appstore.install.InstallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreUiStateTest {

    private fun storeApp(pkg: String, status: AppStatus): StoreApp {
        val release = CatalogRelease(versionName = "1.0", versionCode = 1, apkUrl = "https://x/$pkg.apk")
        return StoreApp(
            app = CatalogApp(packageName = pkg, name = pkg.substringAfterLast('.'), releases = listOf(release)),
            latest = release,
            installed = null,
            status = status,
        )
    }

    private val apps = listOf(
        storeApp("com.example.a", AppStatus.UpdateAvailable),
        storeApp("com.example.b", AppStatus.UpToDate),
        storeApp("com.example.c", AppStatus.UpdateAvailable),
        storeApp("com.example.d", AppStatus.NotInstalled),
    )

    @Test
    fun defaults() {
        val s = StoreUiState()
        assertTrue(s.isLoading)
        assertTrue(s.apps.isEmpty())
        assertEquals(0, s.updatesCount)
        assertFalse(s.anyBusy)
    }

    @Test
    fun updates_keepListOrder() {
        val s = StoreUiState(apps = apps)
        assertEquals(listOf("com.example.a", "com.example.c"), s.updates.map { it.packageName })
        assertEquals(2, s.updatesCount)
    }

    @Test
    fun app_lookupByPackage() {
        val s = StoreUiState(apps = apps)
        assertEquals("com.example.b", s.app("com.example.b")?.packageName)
        assertNull(s.app("com.example.zzz"))
    }

    @Test
    fun installState_missingKeyIsIdle() {
        val s = StoreUiState(installStates = mapOf("com.example.a" to InstallState.Installing))
        assertEquals(InstallState.Installing, s.installState("com.example.a"))
        assertEquals(InstallState.Idle, s.installState("com.example.b"))
    }

    @Test
    fun anyBusy_ignoresTerminalStates() {
        assertFalse(
            StoreUiState(
                installStates = mapOf(
                    "a" to InstallState.Idle,
                    "b" to InstallState.Success,
                    "c" to InstallState.Failed("x"),
                ),
            ).anyBusy,
        )
        assertTrue(StoreUiState(installStates = mapOf("a" to InstallState.Queued)).anyBusy)
    }

    @Test
    fun canOpenAndSilentUpdate_useTheirSets() {
        val s = StoreUiState(launchable = setOf("com.example.a"), silentUpdatable = setOf("com.example.b"))
        assertTrue(s.canOpen("com.example.a"))
        assertFalse(s.canOpen("com.example.b"))
        assertTrue(s.canSilentlyUpdate("com.example.b"))
        assertFalse(s.canSilentlyUpdate("com.example.a"))
    }

    @Test
    fun isAutoUpdatePaused_readsTheSettingsSet() {
        val s = StoreUiState(apps = apps, settings = Settings(autoUpdatePaused = setOf("com.example.a")))
        assertTrue(s.isAutoUpdatePaused("com.example.a"))
        assertFalse(s.isAutoUpdatePaused("com.example.b"))
        assertFalse(StoreUiState().isAutoUpdatePaused("com.example.a"))
    }
}
