package com.pandaapps.appstore.install

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallMessagesTest {

    @Test
    fun success_mapsToSuccess() {
        assertEquals(SessionOutcome.Success, sessionOutcome(PackageInstaller.STATUS_SUCCESS, null))
    }

    @Test
    fun pendingUserAction_mapsToPendingUserAction() {
        assertEquals(
            SessionOutcome.PendingUserAction,
            sessionOutcome(PackageInstaller.STATUS_PENDING_USER_ACTION, "ignored"),
        )
    }

    @Test
    fun aborted_isNotAnError() {
        assertEquals(SessionOutcome.Aborted, sessionOutcome(PackageInstaller.STATUS_FAILURE_ABORTED, "User rejected"))
    }

    @Test
    fun everyFailureStatus_mapsToFailureWithItsMessage() {
        val failures = listOf(
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_TIMEOUT,
            9999,
        )
        for (status in failures) {
            val outcome = sessionOutcome(status, "detail")
            assertEquals(
                "status $status",
                SessionOutcome.Failure(installFailureMessage(status, "detail")),
                outcome,
            )
        }
    }

    @Test
    fun conflict_mentionsKeyOrDowngradeAndUninstall() {
        val msg = installFailureMessage(PackageInstaller.STATUS_FAILURE_CONFLICT, null)
        assertTrue(msg, msg.contains("different key"))
        assertTrue(msg, msg.contains("downgrade"))
        assertTrue(msg, msg.contains("Uninstall"))
    }

    @Test
    fun knownStatuses_haveSpecificMessages() {
        assertEquals(
            "This app is not compatible with this device.",
            installFailureMessage(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, null),
        )
        assertEquals(
            "Not enough storage space to install.",
            installFailureMessage(PackageInstaller.STATUS_FAILURE_STORAGE, null),
        )
        assertEquals(
            "The APK is invalid or corrupted.",
            installFailureMessage(PackageInstaller.STATUS_FAILURE_INVALID, null),
        )
        assertEquals(
            "Installation was blocked by the system or a device policy.",
            installFailureMessage(PackageInstaller.STATUS_FAILURE_BLOCKED, null),
        )
        assertEquals(
            "Installation timed out. Try again.",
            installFailureMessage(PackageInstaller.STATUS_FAILURE_TIMEOUT, null),
        )
    }

    @Test
    fun genericAndUnknownStatuses_useFallbackMessage() {
        assertEquals("Installation failed.", installFailureMessage(PackageInstaller.STATUS_FAILURE, null))
        assertEquals("Installation failed.", installFailureMessage(42, null))
    }

    @Test
    fun statusMessage_isAppendedTrimmed() {
        assertEquals(
            "Not enough storage space to install. (INSTALL_FAILED_INSUFFICIENT_STORAGE)",
            installFailureMessage(PackageInstaller.STATUS_FAILURE_STORAGE, "  INSTALL_FAILED_INSUFFICIENT_STORAGE \n"),
        )
    }

    @Test
    fun blankStatusMessage_isOmitted() {
        assertEquals("Installation failed.", installFailureMessage(PackageInstaller.STATUS_FAILURE, "   "))
        assertEquals("Installation failed.", installFailureMessage(PackageInstaller.STATUS_FAILURE, ""))
    }
}
