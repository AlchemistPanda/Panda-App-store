package com.pandaapps.appstore.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStateTest {

    private val all: List<InstallState> = listOf(
        InstallState.Idle,
        InstallState.Queued,
        InstallState.Downloading(progress = 0.5f, bytes = 50, total = 100),
        InstallState.Verifying,
        InstallState.Installing,
        InstallState.PendingUserAction,
        InstallState.Success,
        InstallState.Failed("boom"),
    )

    @Test
    fun isBusy_onlyWhileWorkIsInProgress() {
        val busy = all.filter { it.isBusy }
        assertEquals(
            listOf(
                InstallState.Queued,
                InstallState.Downloading(0.5f, 50, 100),
                InstallState.Verifying,
                InstallState.Installing,
                InstallState.PendingUserAction,
            ),
            busy,
        )
    }

    @Test
    fun isActivelyWorking_isBusyMinusPendingUserAction() {
        assertEquals(
            listOf(
                InstallState.Queued,
                InstallState.Downloading(0.5f, 50, 100),
                InstallState.Verifying,
                InstallState.Installing,
            ),
            all.filter { it.isActivelyWorking },
        )
        assertTrue(InstallState.PendingUserAction.isBusy)
        assertFalse(InstallState.PendingUserAction.isActivelyWorking)
    }

    @Test
    fun isTerminal_forIdleSuccessAndFailed() {
        assertEquals(
            listOf(InstallState.Idle, InstallState.Success, InstallState.Failed("boom")),
            all.filter { it.isTerminal },
        )
    }

    @Test
    fun busyAndTerminal_areMutuallyExclusiveAndCoverEveryState() {
        for (state in all) {
            assertTrue("$state", state.isBusy xor state.isTerminal)
        }
    }

    @Test
    fun downloadingWithUnknownTotal_isStillBusy() {
        assertTrue(InstallState.Downloading(0f, 1234, -1).isBusy)
        assertFalse(InstallState.Downloading(0f, 1234, -1).isTerminal)
    }

    @Test
    fun silentUpdateMinTargetSdk_followsAndroidRules() {
        assertNull(InstallManager.silentUpdateMinTargetSdk(26))
        assertNull(InstallManager.silentUpdateMinTargetSdk(30))
        assertEquals(29, InstallManager.silentUpdateMinTargetSdk(31))
        assertEquals(29, InstallManager.silentUpdateMinTargetSdk(32))
        assertEquals(30, InstallManager.silentUpdateMinTargetSdk(33))
        assertEquals(31, InstallManager.silentUpdateMinTargetSdk(34))
        assertEquals(33, InstallManager.silentUpdateMinTargetSdk(35))
        assertEquals(33, InstallManager.silentUpdateMinTargetSdk(36))
    }

    @Test
    fun signerMismatchMessage_matchesSpecWording() {
        assertEquals(
            "This build is signed with a different key than the installed app. " +
                "Uninstall Panda Garage first, then install.",
            ApkVerifier.signerMismatchMessage("Panda Garage"),
        )
    }

    @Test
    fun installRequest_defaultsAndValues() {
        val req = InstallRequest(
            packageName = "com.test",
            appName = "Test App",
            versionName = "1.0.0",
            versionCode = 100L,
            background = true,
            apk = java.io.File("/tmp/test.apk"),
            canSilentlyUpdate = true,
            isAlreadyInstalled = false,
        )
        assertTrue(req.canSilentlyUpdate)
        assertFalse(req.isAlreadyInstalled)
        assertTrue(req.background)
    }
}
