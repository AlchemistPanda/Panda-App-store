package com.pandaapps.appstore.install

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pandaapps.appstore.MainActivity
import com.pandaapps.appstore.PandaStoreApp
import com.pandaapps.appstore.R
import com.pandaapps.appstore.notify.Notifications
import com.pandaapps.appstore.util.DeepLinks
import com.pandaapps.appstore.util.Formatters
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that protects active downloads and installs from being killed, frozen,
 * or throttled by Android when the app is in the background.
 *
 * Runs with foregroundServiceType="dataSync". Displays an ongoing notification with current
 * download progress / installing state, and stops itself once no install is actively working
 * (an install waiting only for the user's confirmation doesn't keep it running).
 */
class InstallService : Service() {

    private val container by lazy { (applicationContext as PandaStoreApp).container }
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSelf(createInitialNotification())
        observeInstalls()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE)
            if (pkg != null) {
                container.installManager.cancel(pkg)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        super.onDestroy()
    }

    private fun startForegroundSelf(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.ID_INSTALL_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notifications.ID_INSTALL_SERVICE, notification)
        }
    }

    private fun observeInstalls() {
        observeJob?.cancel()
        observeJob = container.appScope.launch {
            container.installManager.states.collectLatest { statesMap ->
                val busy = statesMap.filter { it.value.isActivelyWorking }
                if (busy.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }

                // Show active downloading, verifying, or installing app
                val active = busy.entries.firstOrNull { it.value is InstallState.Downloading }
                    ?: busy.entries.firstOrNull { it.value == InstallState.Verifying }
                    ?: busy.entries.firstOrNull { it.value == InstallState.Installing }
                    ?: busy.entries.first()

                val pkg = active.key
                val state = active.value
                val app = container.catalogRepository.findApp(pkg)
                val appName = app?.name ?: pkg

                val notification = buildProgressNotification(pkg, appName, state)
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(Notifications.ID_INSTALL_SERVICE, notification)
            }
        }
    }

    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, Notifications.CHANNEL_INSTALLS)
            .setSmallIcon(R.drawable.ic_stat_panda)
            .setColor(Notifications.BRAND_COLOR)
            .setContentTitle("Panda App Store")
            .setContentText("Preparing install…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun buildProgressNotification(
        pkg: String,
        appName: String,
        state: InstallState,
    ): Notification {
        val clickIntent = Intent(Intent.ACTION_VIEW, DeepLinks.appUri(pkg), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentPending = PendingIntent.getActivity(
            this,
            pkg.hashCode(),
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = Intent(this, InstallService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_PACKAGE, pkg)
        }
        val cancelPending = PendingIntent.getService(
            this,
            ("cancel:$pkg").hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_INSTALLS)
            .setSmallIcon(R.drawable.ic_stat_panda)
            .setColor(Notifications.BRAND_COLOR)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentPending)
            .addAction(R.drawable.ic_stat_panda, "Cancel", cancelPending)

        when (state) {
            is InstallState.Downloading -> {
                val percent = (state.progress * 100).toInt()
                val text = Formatters.progressBytes(state.bytes, state.total)
                builder.setContentTitle("Downloading $appName ($percent%)")
                    .setContentText(text)
                    .setProgress(100, percent, state.total <= 0)
            }
            InstallState.Verifying -> {
                builder.setContentTitle("Verifying $appName")
                    .setContentText("Checking package integrity…")
                    .setProgress(0, 0, true)
            }
            InstallState.Installing -> {
                builder.setContentTitle("Installing $appName")
                    .setContentText("Android is installing the app…")
                    .setProgress(0, 0, true)
            }
            // Queued: the only other state isActivelyWorking lets through.
            else -> {
                builder.setContentTitle("Waiting to install $appName")
                    .setContentText("Another install is in progress…")
                    .setProgress(0, 0, true)
            }
        }

        return builder.build()
    }

    companion object {
        const val ACTION_CANCEL = "com.pandaapps.appstore.action.CANCEL_INSTALL"
        const val EXTRA_PACKAGE = "com.pandaapps.appstore.extra.PACKAGE"

        fun start(context: Context) {
            try {
                val intent = Intent(context, InstallService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // If background start is restricted, the caller continues on appScope / worker
            }
        }
    }
}
