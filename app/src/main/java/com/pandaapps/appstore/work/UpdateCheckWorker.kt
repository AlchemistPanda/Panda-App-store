package com.pandaapps.appstore.work

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pandaapps.appstore.AppContainer
import com.pandaapps.appstore.appContainer
import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.data.Settings
import com.pandaapps.appstore.data.StoreApp
import com.pandaapps.appstore.data.notifiedKey
import com.pandaapps.appstore.data.toStoreApp
import com.pandaapps.appstore.install.InstallManager
import com.pandaapps.appstore.install.InstallState
import com.pandaapps.appstore.install.isBusy
import com.pandaapps.appstore.notify.Notifications
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Periodic background check (SPEC #8), scheduled by [UpdateScheduler]:
 *
 * 1. Refresh the catalog. Network/HTTP failure → [Result.retry] (bounded by [MAX_ATTEMPTS]).
 * 2. Compute statuses against what is installed. Only [AppStatus.UpdateAvailable] counts, so
 *    SignerMismatch, Incompatible and InstalledNewer apps are never updated or announced.
 * 3. If auto-update is on and allowed right now (install permission granted, and an unmetered
 *    network when "Wi-Fi only"), download + verify updates inside this worker's coroutine via
 *    [InstallManager.installNow] with `background = true`, within [AutoUpdatePolicy]'s time budget.
 *    Silent-eligible apps finish without a prompt; the others reach the receiver's
 *    PendingUserAction path ("Tap to finish installing") — once per version, never every period.
 *    Panda App Store itself goes last because a successful self-update kills the process.
 *    Apps whose auto-update the user paused are skipped here but still announced in step 4.
 * 4. Post one summary notification for updates the install pipeline did not report on itself,
 *    but only when at least one of them is a version not yet in `notifiedVersions`; then record
 *    them. This also runs when WorkManager stops the worker mid-download.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val startedAt = SystemClock.elapsedRealtime()
        val container = applicationContext.appContainer
        val appLog = container.appLog
        val settings = container.settingsRepository.snapshot()
        appLog.i(TAG, "Background check started (attempt ${runAttemptCount + 1})")

        val catalog = container.catalogRepository.refresh().getOrElse { error ->
            return if (error is IOException && runAttemptCount + 1 < MAX_ATTEMPTS) {
                appLog.w(TAG, "Catalog refresh failed; will retry: ${error.message}")
                Result.retry()
            } else {
                appLog.w(TAG, "Catalog refresh failed; waiting for the next scheduled check: ${error.message}")
                Result.failure()
            }
        }

        val installed = container.installedAppsRepository
        val deviceSdk = Build.VERSION.SDK_INT
        val updates = catalog.apps
            .mapNotNull { it.toStoreApp(installed.get(it.packageName), deviceSdk) }
            .filter { it.status == AppStatus.UpdateAvailable }
        appLog.i(TAG, "${updates.size} update(s) available")

        // Packages whose install the pipeline reports on itself (success / failure / "tap to finish").
        val handled = mutableSetOf<String>()
        try {
            if (canAutoUpdate(container, settings, updates)) {
                autoUpdate(container, settings, updates, startedAt, handled)
            }
        } finally {
            // Also when WorkManager stops us (time limit, Wi-Fi lost) in the middle of a download:
            // whatever wasn't handed to the install pipeline must still be announced.
            withContext(NonCancellable) {
                notifyNewUpdates(container, updates, handled)
            }
        }
        return Result.success()
    }

    // --- Auto-update ---------------------------------------------------------------------------

    private fun canAutoUpdate(container: AppContainer, settings: Settings, updates: List<StoreApp>): Boolean {
        val appLog = container.appLog
        return when {
            !settings.autoUpdate || updates.isEmpty() -> false
            !container.installManager.canRequestPackageInstalls() -> {
                appLog.w(TAG, "Auto-update skipped: \"Install unknown apps\" is not allowed")
                false
            }
            settings.wifiOnly && isActiveNetworkMetered() -> {
                appLog.i(TAG, "Auto-update skipped: on a metered network and Wi-Fi only is on")
                false
            }
            else -> true
        }
    }

    /**
     * Installs [updates] one by one in this coroutine, inside the time budget, then waits (bounded)
     * for the system to report silent installs. Adds to [handled] every package whose install got
     * far enough to produce its own notification.
     */
    private suspend fun autoUpdate(
        container: AppContainer,
        settings: Settings,
        updates: List<StoreApp>,
        startedAt: Long,
        handled: MutableSet<String>,
    ) {
        val appLog = container.appLog
        val installManager = container.installManager
        val settingsRepository = container.settingsRepository
        val selfPackage = applicationContext.packageName
        val deadline = startedAt + AutoUpdatePolicy.INSTALL_BUDGET_MILLIS
        val committed = linkedSetOf<String>()
        var bytesPerSecond: Double? = null

        for (update in updates.sortedBy { it.packageName == selfPackage }) {
            if (isStopped) break
            val pkg = update.packageName
            val release = update.latest
            val key = notifiedKey(pkg, release.versionCode)
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining < AutoUpdatePolicy.MIN_START_MILLIS) {
                appLog.i(TAG, "Leaving the remaining updates for the next check (time budget used)")
                break
            }

            val silent = installManager.canSilentlyUpdate(pkg)
            val busy = installManager.stateOf(pkg).isBusy
            val skip = AutoUpdatePolicy.skipReason(
                key,
                silent,
                settings.notifiedVersions,
                settings.deferredVersions,
                settings.autoUpdatePaused,
            )
                ?: when {
                    busy || installManager.hasInstallAwaitingResult(pkg) -> AutoUpdateSkip.InProgress
                    !AutoUpdatePolicy.canFinishInTime(release.size, bytesPerSecond, remaining) ->
                        AutoUpdateSkip.NotEnoughTime
                    else -> null
                }
            if (skip != null) {
                appLog.i(TAG, "Not auto-updating ${update.name}: ${skip.description}")
                // Something this process is installing right now reports its own result.
                if (busy) handled += pkg
                continue
            }

            appLog.i(
                TAG,
                "Auto-updating ${update.name} to v${release.versionName}" +
                    if (silent) " (silent)" else " (needs a tap)",
            )
            val attempt = attemptInstall(installManager, update, remaining)
            when (val state = attempt.state) {
                null -> {
                    val download = attempt.lastProgress
                    val tooSlow = AutoUpdatePolicy.isTooSlowForBackground(
                        bytes = download?.bytes ?: 0L,
                        total = download?.total?.takeIf { it > 0 } ?: release.size ?: -1L,
                        elapsedMillis = attempt.downloadMillis,
                        budgetMillis = AutoUpdatePolicy.INSTALL_BUDGET_MILLIS,
                    )
                    if (tooSlow) {
                        appLog.w(TAG, "${update.name}: download too slow for a background run; announcing it instead")
                        settingsRepository.markDeferred(listOf(key))
                    } else {
                        appLog.i(TAG, "${update.name}: out of time; will try again on the next check")
                    }
                    break
                }
                is InstallState.Failed -> {
                    appLog.w(TAG, "${update.name}: ${state.message}")
                    handled += pkg // "Couldn't install" was posted
                }
                InstallState.Installing, InstallState.PendingUserAction, InstallState.Success -> {
                    handled += pkg
                    committed += pkg
                    // Offered (or installing): don't start this version again on later runs if it needs a tap.
                    settingsRepository.markNotified(listOf(key))
                }
                else -> appLog.d(TAG, "${update.name} → $state") // e.g. cancelled from the app meanwhile
            }
            attempt.throughput?.let { bytesPerSecond = it }
        }

        // Keep the process alive while committed sessions finish, so results are reported promptly.
        val waitUntil = minOf(SystemClock.elapsedRealtime(), deadline) + AutoUpdatePolicy.RESULT_WAIT_MILLIS
        for (pkg in committed) {
            if (pkg == selfPackage) continue
            val remaining = waitUntil - SystemClock.elapsedRealtime()
            if (remaining <= 0 || isStopped) break
            val result = installManager.awaitResult(pkg, remaining)
            appLog.d(TAG, "$pkg → $result")
        }
    }

    /**
     * One background install, cut off after [timeoutMillis] (→ [Attempt.state] null, install back
     * to Idle). Watches the download progress to measure throughput.
     */
    private suspend fun attemptInstall(installManager: InstallManager, update: StoreApp, timeoutMillis: Long): Attempt =
        coroutineScope {
            val pkg = update.packageName
            var lastProgress: InstallState.Downloading? = null
            var firstAt = 0L
            var lastAt = 0L
            val watcher = launch {
                installManager.states.collect { states ->
                    val download = states[pkg] as? InstallState.Downloading ?: return@collect
                    val now = SystemClock.elapsedRealtime()
                    if (lastProgress == null) firstAt = now
                    lastProgress = download
                    lastAt = now
                }
            }
            val state = try {
                withTimeoutOrNull(timeoutMillis) {
                    installManager.installNow(update.app, update.latest, background = true)
                }
            } finally {
                watcher.cancelAndJoin()
            }
            Attempt(state, lastProgress, downloadMillis = lastAt - firstAt)
        }

    /** @property state the install's state, or null when it ran out of time and was cancelled. */
    private class Attempt(
        val state: InstallState?,
        val lastProgress: InstallState.Downloading?,
        val downloadMillis: Long,
    ) {
        val throughput: Double?
            get() = lastProgress?.let { AutoUpdatePolicy.throughput(it.bytes, downloadMillis) }
    }

    private fun isActiveNetworkMetered(): Boolean =
        applicationContext.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true

    // --- Notifications -------------------------------------------------------------------------

    /**
     * Announces the [updates] not in [handled] whose version is new, in one summary. Keys of every
     * pending update are kept (so a version offered through "tap to finish" is not announced again);
     * keys of versions no longer pending are forgotten.
     */
    private suspend fun notifyNewUpdates(container: AppContainer, updates: List<StoreApp>, handled: Set<String>) {
        val settingsRepository = container.settingsRepository
        settingsRepository.retainPending(updates.mapTo(HashSet()) { notifiedKey(it.packageName, it.latest.versionCode) })

        val pending = updates.filterNot { it.packageName in handled }
        if (pending.isEmpty()) {
            Notifications.cancelUpdatesAvailable(applicationContext)
            return
        }
        val pendingKeys = pending.map { notifiedKey(it.packageName, it.latest.versionCode) }.toSet()
        // Re-read: auto-update may have recorded keys during this run.
        val newKeys = pendingKeys - settingsRepository.snapshot().notifiedVersions
        if (newKeys.isEmpty()) {
            container.appLog.d(TAG, "No new versions to announce")
            return
        }
        if (!Notifications.canPost(applicationContext)) {
            // Leave them unrecorded so they are announced once notifications are allowed.
            container.appLog.w(TAG, "${newKeys.size} new update(s), but notifications are not allowed")
            return
        }
        // The summary lists every pending update; it is only (re)posted when something is new.
        Notifications.showUpdatesAvailable(applicationContext, pending)
        settingsRepository.markNotified(newKeys)
        container.appLog.i(TAG, "Announced ${newKeys.size} new version(s)")
    }

    private companion object {
        const val TAG = "UpdateWorker"

        /** Total attempts for one scheduled run before giving up until the next period. */
        const val MAX_ATTEMPTS = 3
    }
}
