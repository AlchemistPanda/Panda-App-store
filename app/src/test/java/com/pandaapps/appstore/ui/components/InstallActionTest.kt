package com.pandaapps.appstore.ui.components

import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.install.InstallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallActionTest {

    private val busyStates = listOf(
        InstallState.Queued,
        InstallState.Downloading(0.3f, 30, 100),
        InstallState.Verifying,
        InstallState.Installing,
    )

    @Test
    fun idle_mapsFromStatus() {
        val idle = InstallState.Idle
        assertEquals(InstallAction.Install, installActionFor(AppStatus.NotInstalled, idle, canOpen = false))
        assertEquals(InstallAction.Update, installActionFor(AppStatus.UpdateAvailable, idle, canOpen = true))
        assertEquals(InstallAction.Open, installActionFor(AppStatus.UpToDate, idle, canOpen = true))
        assertEquals(InstallAction.Open, installActionFor(AppStatus.InstalledNewer, idle, canOpen = true))
        assertEquals(InstallAction.Open, installActionFor(AppStatus.SignerMismatch, idle, canOpen = true))
        assertEquals(InstallAction.Unavailable, installActionFor(AppStatus.Incompatible, idle, canOpen = true))
    }

    @Test
    fun installedWithoutLauncher_hasNoAction() {
        val idle = InstallState.Idle
        assertEquals(InstallAction.None, installActionFor(AppStatus.UpToDate, idle, canOpen = false))
        assertEquals(InstallAction.None, installActionFor(AppStatus.InstalledNewer, idle, canOpen = false))
        assertEquals(InstallAction.None, installActionFor(AppStatus.SignerMismatch, idle, canOpen = false))
    }

    @Test
    fun installStates_overrideStatus() {
        for (status in AppStatus.entries) {
            for (busy in busyStates) {
                assertEquals("$status/$busy", InstallAction.Progress, installActionFor(status, busy, canOpen = true))
            }
            assertEquals(InstallAction.Confirm, installActionFor(status, InstallState.PendingUserAction, canOpen = true))
            assertEquals(InstallAction.Done, installActionFor(status, InstallState.Success, canOpen = true))
            assertEquals(InstallAction.Retry, installActionFor(status, InstallState.Failed("x"), canOpen = true))
        }
    }

    @Test
    fun statusLabels() {
        assertEquals("Not installed", AppStatus.NotInstalled.label)
        assertEquals("Up to date", AppStatus.UpToDate.label)
        assertEquals("Update available", AppStatus.UpdateAvailable.label)
        assertEquals("Newer installed", AppStatus.InstalledNewer.label)
        assertEquals("Different key", AppStatus.SignerMismatch.label)
        assertEquals("Incompatible", AppStatus.Incompatible.label)
    }

    @Test
    fun installStateLabels() {
        assertNull(InstallState.Idle.label)
        assertEquals("Queued", InstallState.Queued.label)
        assertEquals("Downloading 7%", InstallState.Downloading(0.07f, 7, 100).label)
        assertEquals("Downloading", InstallState.Downloading(0f, 7, 0).label)
        assertEquals("Verifying", InstallState.Verifying.label)
        assertEquals("Installing", InstallState.Installing.label)
        assertEquals("Waiting for you", InstallState.PendingUserAction.label)
        assertEquals("Installed", InstallState.Success.label)
        assertEquals("Failed", InstallState.Failed("x").label)
    }
}
