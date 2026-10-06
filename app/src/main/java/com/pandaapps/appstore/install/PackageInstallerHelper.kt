package com.pandaapps.appstore.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/** Everything the result receiver needs, carried in the commit PendingIntent so it survives process death. */
data class InstallRequest(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val background: Boolean,
    val apk: File,
    val canSilentlyUpdate: Boolean = false,
    val isAlreadyInstalled: Boolean = false,
)

/** Thin wrapper over [PackageInstaller]: create session → copy APK → commit with a result PendingIntent. */
class PackageInstallerHelper(context: Context) {

    private val appContext = context.applicationContext
    private val installer: PackageInstaller = appContext.packageManager.packageInstaller

    /**
     * Creates and fills a session and commits it. Returns the session id.
     *
     * [onSessionCreated] gets the id before anything is written or committed, so the caller can
     * always find (and abandon) the session — even when it is cancelled while the commit binder
     * call is in flight. If the caller is cancelled before commit the session is abandoned here;
     * if the cancellation lands during the commit, the committed session is abandoned too.
     */
    suspend fun installAndCommit(
        request: InstallRequest,
        onSessionCreated: (sessionId: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(request.packageName)
            setSize(request.apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (request.canSilentlyUpdate) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                } else {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (!request.isAlreadyInstalled) {
                    setRequestUpdateOwnership(true)
                }
            }
        }
        val sessionId = installer.createSession(params)
        var committed = false
        try {
            onSessionCreated(sessionId)
            installer.openSession(sessionId).use { session ->
                request.apk.inputStream().use { input ->
                    session.openWrite(SESSION_FILE_NAME, 0, request.apk.length()).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                        session.fsync(output)
                    }
                }
                currentCoroutineContext().ensureActive()
                session.commit(resultIntent(sessionId, request).intentSender)
                committed = true
            }
            // Cancelled while committing: withContext is about to throw instead of returning the id,
            // so nobody else would know about this session. Don't let it install behind the user's back.
            if (!currentCoroutineContext().isActive) abandon(sessionId)
            sessionId
        } finally {
            if (!committed) abandon(sessionId)
        }
    }

    /** Abandons a session; safe to call for sessions that are gone or already finalized. */
    fun abandon(sessionId: Int) {
        runCatching { installer.abandonSession(sessionId) }
    }

    /**
     * Abandons every session this app owns for [packageName], committed or not, and returns how
     * many there were. Called before a new session for the package is created, so at most one
     * staged copy of an APK exists per package — earlier ones (e.g. a "tap to finish" from a
     * previous process the user ignored) would otherwise hold a full APK copy until the system
     * expires them days later.
     */
    fun abandonSessionsFor(packageName: String): Int {
        val stale = sessionsFor(packageName)
        stale.forEach { abandon(it.sessionId) }
        return stale.size
    }

    /**
     * True when a committed session for [packageName] is still open — it is waiting for the user to
     * confirm, or for the system to finish installing. Only knowable on API 31+ (`isCommitted`);
     * false on older versions.
     */
    fun hasCommittedSession(packageName: String): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sessionsFor(packageName).any { it.isCommitted }

    private fun sessionsFor(packageName: String): List<PackageInstaller.SessionInfo> =
        runCatching { installer.mySessions.filter { it.appPackageName == packageName } }.getOrDefault(emptyList())

    /**
     * Abandons this app's never-committed leftover sessions (e.g. the process died mid-copy).
     * Only on API 31+, where committed sessions (possibly awaiting the user) can be told apart.
     */
    fun abandonStaleSessions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        installer.mySessions
            .filter { !it.isCommitted }
            .forEach { abandon(it.sessionId) }
    }

    private fun resultIntent(sessionId: Int, request: InstallRequest): PendingIntent {
        val intent = Intent(appContext, InstallResultReceiver::class.java).apply {
            action = InstallResultReceiver.ACTION_SESSION_RESULT
            putExtra(InstallResultReceiver.EXTRA_PACKAGE, request.packageName)
            putExtra(InstallResultReceiver.EXTRA_APP_NAME, request.appName)
            putExtra(InstallResultReceiver.EXTRA_VERSION_NAME, request.versionName)
            putExtra(InstallResultReceiver.EXTRA_VERSION_CODE, request.versionCode)
            putExtra(InstallResultReceiver.EXTRA_BACKGROUND, request.background)
            putExtra(InstallResultReceiver.EXTRA_APK_PATH, request.apk.absolutePath)
        }
        // The system fills in EXTRA_STATUS etc., so the PendingIntent must be mutable on API 31+.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(appContext, sessionId, intent, flags)
    }

    private companion object {
        const val SESSION_FILE_NAME = "base.apk"
    }
}
