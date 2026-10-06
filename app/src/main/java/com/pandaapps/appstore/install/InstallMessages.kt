package com.pandaapps.appstore.install

import android.content.pm.PackageInstaller

/**
 * Pure mapping from a PackageInstaller session status to what the UI should show.
 * (The PackageInstaller.STATUS_* values are compile-time constants, so this is JVM-testable.)
 */
sealed interface SessionOutcome {
    data object Success : SessionOutcome
    data object PendingUserAction : SessionOutcome

    /** The user dismissed the system dialog — not an error, go back to Idle. */
    data object Aborted : SessionOutcome
    data class Failure(val message: String) : SessionOutcome
}

fun sessionOutcome(status: Int, statusMessage: String?): SessionOutcome = when (status) {
    PackageInstaller.STATUS_SUCCESS -> SessionOutcome.Success
    PackageInstaller.STATUS_PENDING_USER_ACTION -> SessionOutcome.PendingUserAction
    PackageInstaller.STATUS_FAILURE_ABORTED -> SessionOutcome.Aborted
    else -> SessionOutcome.Failure(installFailureMessage(status, statusMessage))
}

fun installFailureMessage(status: Int, statusMessage: String?): String {
    val base = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Install conflicts with the installed app — it is probably signed with a different key, " +
                "or this would be a downgrade. Uninstall it first, then install."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "This app is not compatible with this device."
        PackageInstaller.STATUS_FAILURE_STORAGE ->
            "Not enough storage space to install."
        PackageInstaller.STATUS_FAILURE_INVALID ->
            "The APK is invalid or corrupted."
        PackageInstaller.STATUS_FAILURE_BLOCKED ->
            "Installation was blocked by the system or a device policy."
        PackageInstaller.STATUS_FAILURE_TIMEOUT ->
            "Installation timed out. Try again."
        else -> "Installation failed."
    }
    val detail = statusMessage?.trim()?.takeIf { it.isNotEmpty() }
    return if (detail != null) "$base ($detail)" else base
}
