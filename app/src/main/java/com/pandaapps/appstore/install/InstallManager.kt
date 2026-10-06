package com.pandaapps.appstore.install

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.pandaapps.appstore.data.CatalogApp
import com.pandaapps.appstore.data.CatalogRelease
import com.pandaapps.appstore.data.InstalledAppsRepository
import com.pandaapps.appstore.notify.Notifications
import com.pandaapps.appstore.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Download → verify → PackageInstaller pipeline, one install at a time, with per-package state.
 *
 * - [install] fires and forgets on the manager's scope (UI).
 * - [installNow] runs the same pipeline in the caller's coroutine and returns once the session is
 *   committed (or failed) — the update worker uses it so the download happens inside the worker.
 * - The system reports the outcome to [InstallResultReceiver] → [onSessionResult].
 */
class InstallManager(
    context: Context,
    okHttp: OkHttpClient,
    private val installedApps: InstalledAppsRepository,
    private val appLog: AppLog,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    private val downloader = ApkDownloader(okHttp, File(appContext.cacheDir, APK_DIR))
    private val verifier = ApkVerifier(installedApps, appLog)
    private val installer = PackageInstallerHelper(appContext)

    /** Serialises installs: one download/commit at a time, the rest wait in [InstallState.Queued]. */
    private val pipelineLock = Mutex()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val sessions = ConcurrentHashMap<String, Int>()
    private val pendingConfirmations = ConcurrentHashMap<String, Intent>()
    private val unpromptedConfirmations = ConcurrentHashMap.newKeySet<String>()
    private val pendingReinstalls = ConcurrentHashMap<String, PendingReinstall>()

    /** The release each package's most recent install was for, so Retry repeats that one (e.g. a rollback). */
    private val requestedReleases = ConcurrentHashMap<String, CatalogRelease>()

    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())

    /** Per-package state; a missing key means [InstallState.Idle]. */
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()

    init {
        // "Uninstall & install this version": once the package is gone, install the chosen release.
        scope.launch {
            installedApps.changes.collect { onPackagesChanged() }
        }
    }

    fun stateOf(packageName: String): InstallState = _states.value[packageName] ?: InstallState.Idle

    /** Starts an install unless this package is already busy (see [installNow]). */
    fun install(app: CatalogApp, release: CatalogRelease, background: Boolean = false) {
        if (stateOf(app.packageName).isBusy) return
        scope.launch { installNow(app, release, background) }
    }

    /**
     * Runs the pipeline in the calling coroutine. Returns the state after the session commit
     * (usually [InstallState.Installing], or whatever the receiver already reported), or
     * [InstallState.Failed]. Cancelling the caller cancels the install (state → Idle).
     *
     * Returns the current state without doing anything while the package is busy — including after
     * its pipeline job has finished but the committed session is still installing or waiting for the
     * user ([InstallState.Installing] / [InstallState.PendingUserAction]). Replacing such a session
     * is only done by an explicit [cancel] followed by a new install.
     */
    suspend fun installNow(app: CatalogApp, release: CatalogRelease, background: Boolean = false): InstallState =
        coroutineScope {
            val pkg = app.packageName
            if (stateOf(pkg).isBusy) return@coroutineScope stateOf(pkg)
            // The pipeline runs as its own child job so cancel(pkg) never cancels the caller (e.g. the worker).
            val pipeline = async(start = CoroutineStart.LAZY) { runInstall(app, release, background) }
            if (jobs.putIfAbsent(pkg, pipeline) != null) {
                pipeline.cancel()
                return@coroutineScope stateOf(pkg)
            }
            requestedReleases[pkg] = release
            try {
                pipeline.await()
            } catch (e: CancellationException) {
                ensureActive() // rethrows if the caller itself was cancelled
                stateOf(pkg)
            } finally {
                jobs.remove(pkg, pipeline)
            }
        }

    private suspend fun runInstall(app: CatalogApp, release: CatalogRelease, background: Boolean): InstallState {
        val pkg = app.packageName
        sessions.remove(pkg)?.let(installer::abandon)
        pendingConfirmations.remove(pkg)
        unpromptedConfirmations.remove(pkg)
        Notifications.cancelForPackage(appContext, pkg)
        try {
            setState(pkg, InstallState.Queued)
            startInstallServiceIfNeeded()
            pipelineLock.withLock { runPipeline(app, release, background) }
        } catch (e: CancellationException) {
            onPipelineCancelled(pkg, app.name)
            throw e
        } catch (e: Exception) {
            // A blocking call aborted by our own cancellation (socket closed, session destroyed)
            // fails with an ordinary exception. That is a cancel, not an install failure.
            val context = currentCoroutineContext()
            if (!context.isActive) {
                onPipelineCancelled(pkg, app.name)
                context.ensureActive() // throws the job's CancellationException
            }
            val message = errorMessage(e)
            appLog.e(TAG, "Install of ${app.name} failed: $message", e)
            fail(pkg, app.name, message, background)
        }
        return stateOf(pkg)
    }

    private fun onPipelineCancelled(pkg: String, appName: String) {
        appLog.i(TAG, "Install of $appName cancelled")
        // A session this run created (possibly mid-commit) must not install after the cancel.
        sessions.remove(pkg)?.let(installer::abandon)
        setState(pkg, InstallState.Idle)
    }

    /** Suspends until [packageName] reaches Idle/Success/Failed/PendingUserAction or [timeoutMillis] passes. */
    suspend fun awaitResult(packageName: String, timeoutMillis: Long): InstallState =
        withTimeoutOrNull(timeoutMillis) {
            states.map { it[packageName] ?: InstallState.Idle }
                .first { it.isTerminal || it == InstallState.PendingUserAction }
        } ?: stateOf(packageName)

    /** Cancels a running download/verify/copy, or abandons a committed session still awaiting the user. */
    fun cancel(packageName: String) {
        pendingReinstalls.remove(packageName)
        jobs.remove(packageName)?.cancel()
        sessions.remove(packageName)?.let(installer::abandon)
        pendingConfirmations.remove(packageName)
        unpromptedConfirmations.remove(packageName)
        Notifications.cancelForPackage(appContext, packageName)
        downloader.deleteFor(packageName)
        setState(packageName, InstallState.Idle)
    }

    /** Opens the system uninstall dialog. */
    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            appLog.e(TAG, "No uninstaller available", e)
        }
    }

    /**
     * Rollback path for the previous release: uninstall now, and when the package is gone
     * install [release]. Expires after 10 minutes if the user never confirms the uninstall.
     */
    fun uninstallAndInstall(app: CatalogApp, release: CatalogRelease) {
        pendingReinstalls[app.packageName] = PendingReinstall(app, release, System.currentTimeMillis())
        appLog.i(TAG, "Uninstall ${app.name}, then install v${release.versionName}")
        uninstall(app.packageName)
    }

    /** True when an update of an installed app should complete without any prompt (SPEC #7). */
    fun canSilentlyUpdate(packageName: String): Boolean {
        val minTarget = silentUpdateMinTargetSdk(Build.VERSION.SDK_INT) ?: return false
        val isSelf = packageName == appContext.packageName
        if (!isSelf && installedApps.installerOf(packageName) != appContext.packageName) return false
        val target = installedApps.targetSdk(packageName) ?: return false
        return target >= minTarget
    }

    /** "Install unknown apps" granted for this app. */
    fun canRequestPackageInstalls(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" settings page for this app. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))

    /** Relaunches the system confirmation for an install waiting on the user. Returns false if none. */
    fun resumePendingUserAction(packageName: String): Boolean {
        unpromptedConfirmations.remove(packageName)
        val confirm = pendingConfirmations[packageName] ?: return false
        return try {
            appContext.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            appLog.w(TAG, "Could not reopen the install confirmation", e)
            false
        }
    }

    /**
     * If the app is in the foreground and there are pending confirmations that arrived
     * while in the background, launches the first one immediately so the user is prompted.
     */
    fun resumePendingConfirmations(): Boolean {
        if (!isAppInForeground()) return false
        val pkg = unpromptedConfirmations.firstOrNull() ?: return false
        val confirm = pendingConfirmations[pkg] ?: run {
            unpromptedConfirmations.remove(pkg)
            return false
        }
        return try {
            appLog.i(TAG, "Resuming pending confirmation for $pkg on foreground resume")
            unpromptedConfirmations.remove(pkg)
            appContext.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            appLog.w(TAG, "Could not launch pending confirmation for $pkg", e)
            false
        }
    }

    /** The release the latest install of [packageName] was started for (what Retry should repeat). */
    fun lastRequestedRelease(packageName: String): CatalogRelease? = requestedReleases[packageName]

    /**
     * True when an install of [packageName] has been committed and not finished yet: known to this
     * process ([InstallState.Installing] / [InstallState.PendingUserAction]) or, on API 31+, a
     * committed session left by an earlier process (e.g. a "tap to finish" the user hasn't acted on).
     */
    suspend fun hasInstallAwaitingResult(packageName: String): Boolean {
        val state = stateOf(packageName)
        if (state == InstallState.Installing || state == InstallState.PendingUserAction) return true
        return withContext(Dispatchers.IO) { installer.hasCommittedSession(packageName) }
    }

    /** Clears a Failed/Success state back to Idle. */
    fun clearState(packageName: String) {
        if (!stateOf(packageName).isBusy) setState(packageName, InstallState.Idle)
    }

    fun downloadsCacheSize(): Long = downloader.cacheSize()

    /** Deletes cached APKs (skipped while an install is running). Returns bytes freed. */
    fun clearDownloads(): Long {
        if (_states.value.values.any { it.isBusy }) return 0L
        return downloader.clearAll()
    }

    /** Startup housekeeping: stale APKs (> 1 day or already installed) and never-committed sessions. */
    fun cleanupOnStart() {
        scope.launch(Dispatchers.IO) {
            downloader.deleteOlderThan(ONE_DAY_MILLIS)
            File(appContext.cacheDir, APK_DIR).listFiles()?.forEach { file ->
                val match = APK_NAME.matchEntire(file.name) ?: return@forEach
                val (pkg, code) = match.destructured
                val installed = installedApps.get(pkg)
                if (installed != null && installed.versionCode >= code.toLong()) file.delete()
            }
            runCatching { installer.abandonStaleSessions() }
                .onFailure { appLog.w(TAG, "Could not clean up old install sessions", it) }
        }
    }

    /** Called by [InstallResultReceiver] with the PackageInstaller status broadcast. */
    fun onSessionResult(intent: Intent) {
        val pkg = intent.getStringExtra(InstallResultReceiver.EXTRA_PACKAGE) ?: return
        val appName = intent.getStringExtra(InstallResultReceiver.EXTRA_APP_NAME) ?: pkg
        val versionName = intent.getStringExtra(InstallResultReceiver.EXTRA_VERSION_NAME).orEmpty()
        val background = intent.getBooleanExtra(InstallResultReceiver.EXTRA_BACKGROUND, false)
        val apkPath = intent.getStringExtra(InstallResultReceiver.EXTRA_APK_PATH)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, NO_SESSION)

        when (val outcome = sessionOutcome(status, statusMessage)) {
            SessionOutcome.PendingUserAction -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    fail(pkg, appName, "Android asked for confirmation but gave no screen to show.", background)
                    return
                }
                pendingConfirmations[pkg] = confirm
                setState(pkg, InstallState.PendingUserAction)
                if (isAppInForeground()) {
                    appLog.i(TAG, "$appName needs confirmation; showing the system dialog")
                    try {
                        appContext.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        appLog.w(TAG, "Could not show the confirmation; posting a notification", e)
                        unpromptedConfirmations.add(pkg)
                        Notifications.showTapToFinish(appContext, pkg, appName, confirm)
                    }
                } else {
                    appLog.i(TAG, "$appName needs confirmation; posted 'tap to finish'")
                    unpromptedConfirmations.add(pkg)
                    Notifications.showTapToFinish(appContext, pkg, appName, confirm)
                }
            }

            SessionOutcome.Success -> {
                appLog.i(TAG, "$appName v$versionName installed")
                forgetSession(pkg, sessionId)
                requestedReleases.remove(pkg)
                pendingConfirmations.remove(pkg)
                unpromptedConfirmations.remove(pkg)
                apkPath?.let { File(it).delete() }
                setState(pkg, InstallState.Success)
                installedApps.refresh()
                if (background || !isAppInForeground()) {
                    Notifications.showInstallSuccess(appContext, pkg, appName, versionName)
                } else {
                    Notifications.cancelForPackage(appContext, pkg)
                }
                scope.launch {
                    delay(SUCCESS_STATE_MILLIS)
                    _states.update { if (it[pkg] == InstallState.Success) it - pkg else it }
                }
            }

            SessionOutcome.Aborted -> {
                appLog.i(TAG, "Install of $appName dismissed by the user")
                forgetSession(pkg, sessionId)
                pendingConfirmations.remove(pkg)
                unpromptedConfirmations.remove(pkg)
                Notifications.cancelForPackage(appContext, pkg)
                setState(pkg, InstallState.Idle)
            }

            is SessionOutcome.Failure -> {
                forgetSession(pkg, sessionId)
                pendingConfirmations.remove(pkg)
                unpromptedConfirmations.remove(pkg)
                appLog.e(TAG, "Install of $appName failed (status $status): ${statusMessage.orEmpty()}")
                fail(pkg, appName, outcome.message, background)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private suspend fun runPipeline(app: CatalogApp, release: CatalogRelease, background: Boolean) {
        val pkg = app.packageName
        // Sessions for this package left by an earlier run or process (committed ones waiting on a
        // tap the user never gave included) each hold a full staged APK copy; keep at most one.
        val abandoned = withContext(Dispatchers.IO) { installer.abandonSessionsFor(pkg) }
        if (abandoned > 0) appLog.i(TAG, "Abandoned $abandoned earlier install session(s) for ${app.name}")
        appLog.i(TAG, "Downloading ${app.name} v${release.versionName} (build ${release.versionCode})")
        setState(pkg, InstallState.Downloading(0f, 0L, release.size ?: -1L))
        val apk = downloader.download(
            url = release.apkUrl,
            packageName = pkg,
            versionCode = release.versionCode,
            expectedSize = release.size,
        ) { bytes, total ->
            val progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
            setState(pkg, InstallState.Downloading(progress, bytes, total))
        }

        setState(pkg, InstallState.Verifying)
        when (val result = verifier.verify(apk, app, release)) {
            is VerifyResult.Rejected -> {
                apk.delete()
                fail(pkg, app.name, result.message, background)
                return
            }
            VerifyResult.Ok -> Unit
        }

        setState(pkg, InstallState.Installing)
        appLog.i(TAG, "Committing install session for ${app.name}")
        val request = InstallRequest(
            packageName = pkg,
            appName = app.name,
            versionName = release.versionName,
            versionCode = release.versionCode,
            background = background,
            apk = apk,
            canSilentlyUpdate = canSilentlyUpdate(pkg),
            isAlreadyInstalled = installedApps.isInstalled(pkg),
        )
        // Track the session from creation on, so cancel() can abandon it even mid-commit.
        installer.installAndCommit(request) { sessionId -> sessions[pkg] = sessionId }
        if (pkg == appContext.packageName) {
            appLog.i(TAG, "Self-update committed; the app will restart when it completes")
        }
    }

    /** Forgets a finished session — only if it is the one tracked for [pkg], not a newer one. */
    private fun forgetSession(pkg: String, sessionId: Int) {
        if (sessionId == NO_SESSION) sessions.remove(pkg) else sessions.remove(pkg, sessionId)
    }

    private fun fail(pkg: String, appName: String, message: String, background: Boolean) {
        setState(pkg, InstallState.Failed(message))
        if (background || !isAppInForeground()) Notifications.showInstallFailed(appContext, pkg, appName, message)
    }

    private fun startInstallServiceIfNeeded() {
        if (isAppInForeground()) {
            InstallService.start(appContext)
        }
    }

    private fun setState(pkg: String, state: InstallState) {
        _states.update { if (state == InstallState.Idle) it - pkg else it + (pkg to state) }
    }

    private fun onPackagesChanged() {
        if (pendingReinstalls.isEmpty()) return
        val now = System.currentTimeMillis()
        pendingReinstalls.entries.toList().forEach { (pkg, pending) ->
            when {
                now - pending.requestedAt > REINSTALL_WINDOW_MILLIS -> pendingReinstalls.remove(pkg)
                !installedApps.isInstalled(pkg) && pendingReinstalls.remove(pkg, pending) -> {
                    appLog.i(TAG, "${pending.app.name} removed; installing v${pending.release.versionName}")
                    install(pending.app, pending.release)
                }
            }
        }
    }

    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun errorMessage(e: Exception): String = when (e) {
        is UnknownHostException -> "No internet connection."
        is SocketTimeoutException -> "The download timed out. Try again."
        is IOException -> e.message ?: "Download failed."
        is SecurityException -> "Android refused the install: ${e.message}"
        else -> e.message ?: e::class.java.simpleName
    }

    private data class PendingReinstall(val app: CatalogApp, val release: CatalogRelease, val requestedAt: Long)

    companion object {
        private const val TAG = "Install"
        private const val NO_SESSION = -1
        private const val APK_DIR = "apks"
        private val APK_NAME = Regex("^(.+)-(\\d+)\\.apk$")
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val SUCCESS_STATE_MILLIS = 4_000L
        private const val REINSTALL_WINDOW_MILLIS = 10 * 60 * 1000L

        /**
         * Minimum target SDK an installed app needs for a silent (no-prompt) update on a device
         * running [deviceSdk], or null when the device cannot update silently at all (< API 31).
         */
        fun silentUpdateMinTargetSdk(deviceSdk: Int): Int? = when {
            deviceSdk < Build.VERSION_CODES.S -> null
            deviceSdk < Build.VERSION_CODES.TIRAMISU -> 29
            deviceSdk < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> 30
            deviceSdk < Build.VERSION_CODES.VANILLA_ICE_CREAM -> 31
            else -> 33
        }
    }
}
