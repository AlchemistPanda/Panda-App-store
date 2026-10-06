package com.pandaapps.appstore.install

/** Per-package install pipeline state, published by [InstallManager.states]. Absent key == [Idle]. */
sealed interface InstallState {
    data object Idle : InstallState

    /** Waiting for another install to finish (installs run one at a time). */
    data object Queued : InstallState

    /** @param progress 0..1, or 0 while [total] is unknown. */
    data class Downloading(val progress: Float, val bytes: Long, val total: Long) : InstallState

    data object Verifying : InstallState

    /** Writing the APK into a PackageInstaller session / waiting for the system to install it. */
    data object Installing : InstallState

    /** The system needs the user to confirm (dialog shown, or a "tap to finish" notification posted). */
    data object PendingUserAction : InstallState

    data object Success : InstallState

    data class Failed(val message: String) : InstallState
}

/** True while work is in progress and the install can be cancelled. */
val InstallState.isBusy: Boolean
    get() = when (this) {
        InstallState.Queued, is InstallState.Downloading, InstallState.Verifying,
        InstallState.Installing, InstallState.PendingUserAction -> true
        InstallState.Idle, InstallState.Success, is InstallState.Failed -> false
    }

/** True for states that end a pipeline run. */
val InstallState.isTerminal: Boolean
    get() = this == InstallState.Idle || this == InstallState.Success || this is InstallState.Failed
