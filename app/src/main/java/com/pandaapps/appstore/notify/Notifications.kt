package com.pandaapps.appstore.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pandaapps.appstore.MainActivity
import com.pandaapps.appstore.R
import com.pandaapps.appstore.data.StoreApp
import com.pandaapps.appstore.util.DeepLinks

/**
 * Notification channels and every notification the app posts. Content taps deep-link into
 * [MainActivity] (`pandastore://home` or `pandastore://app/<pkg>`), so they share the ntfy path.
 *
 * Per-package notifications use tag `install:<pkg>` and id [ID_INSTALL], so a newer one for the same
 * app replaces the older one (e.g. "Tap to finish" → "updated to v1.0.7").
 */
object Notifications {

    const val CHANNEL_UPDATES = "updates"
    const val CHANNEL_INSTALLS = "installs"

    private const val ID_UPDATES_SUMMARY = 1000
    private const val ID_INSTALL = 2000
    const val ID_INSTALL_SERVICE = 3000
    private const val TAG_UPDATES = "updates"
    private const val MAX_INBOX_LINES = 6
    const val BRAND_COLOR = 0xFF4CAF50.toInt()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_UPDATES, "Updates available", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When new versions of your apps are published"
                },
                NotificationChannel(CHANNEL_INSTALLS, "Installs", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Install results and installs that need a tap to finish"
                },
            )
        )
    }

    /** Runtime permission (API 33+) granted and notifications not blocked for the app. */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * One summary notification: "Panda Garage update available" for a single app (opens its detail)
     * or "N updates available" (opens home). An empty list cancels it.
     */
    fun showUpdatesAvailable(context: Context, updates: List<StoreApp>) {
        if (updates.isEmpty()) {
            cancelUpdatesAvailable(context)
            return
        }
        val single = updates.singleOrNull()
        val title: String
        val text: String
        val uri: Uri
        if (single != null) {
            title = "${single.name} update available"
            text = "Version ${single.latest.versionName} (build ${single.latest.versionCode}) is ready to install."
            uri = DeepLinks.appUri(single.packageName)
        } else {
            title = "${updates.size} updates available"
            text = updates.joinToString { it.name }
            uri = DeepLinks.homeUri()
        }
        val builder = baseBuilder(context, CHANNEL_UPDATES)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(if (single != null) NotificationCompat.BigTextStyle().bigText(text) else inboxStyle(updates))
            .setNumber(updates.size)
            .setContentIntent(deepLinkPendingIntent(context, uri))
        post(context, TAG_UPDATES, ID_UPDATES_SUMMARY, builder)
    }

    fun cancelUpdatesAvailable(context: Context) {
        NotificationManagerCompat.from(context).cancel(TAG_UPDATES, ID_UPDATES_SUMMARY)
    }

    /** "Tap to finish installing <name>" — its tap launches the system confirmation [confirmIntent]. */
    fun showTapToFinish(context: Context, packageName: String, appName: String, confirmIntent: Intent) {
        val launch = Intent(confirmIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context,
            requestCode("confirm", packageName),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = baseBuilder(context, CHANNEL_INSTALLS)
            .setContentTitle("Tap to finish installing $appName")
            .setContentText("Android needs you to confirm this install.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pending)
        post(context, installTag(packageName), ID_INSTALL, builder)
    }

    /** "<name> updated to v<version>" (or "installed" for a first install). Opens the app's detail screen. */
    fun showInstallSuccess(context: Context, packageName: String, appName: String, versionName: String, wasUpdate: Boolean = true) {
        val verb = if (wasUpdate) "updated to" else "installed:"
        val builder = baseBuilder(context, CHANNEL_INSTALLS)
            .setContentTitle("$appName $verb v$versionName")
            .setContentText("Tap to see what's new.")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(deepLinkPendingIntent(context, DeepLinks.appUri(packageName)))
        post(context, installTag(packageName), ID_INSTALL, builder)
    }

    fun showInstallFailed(context: Context, packageName: String, appName: String, message: String) {
        val builder = baseBuilder(context, CHANNEL_INSTALLS)
            .setContentTitle("Couldn't install $appName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(deepLinkPendingIntent(context, DeepLinks.appUri(packageName)))
        post(context, installTag(packageName), ID_INSTALL, builder)
    }

    /** Removes the per-package install notification (tap-to-finish / result). */
    fun cancelForPackage(context: Context, packageName: String) {
        NotificationManagerCompat.from(context).cancel(installTag(packageName), ID_INSTALL)
    }

    /** Explicit VIEW intent for [uri] into [MainActivity] (singleTop → onNewIntent when running). */
    fun deepLinkPendingIntent(context: Context, uri: Uri): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode("link", uri.toString()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun inboxStyle(updates: List<StoreApp>): NotificationCompat.InboxStyle {
        val style = NotificationCompat.InboxStyle()
        updates.take(MAX_INBOX_LINES).forEach { style.addLine("${it.name}  v${it.latest.versionName}") }
        if (updates.size > MAX_INBOX_LINES) style.setSummaryText("+${updates.size - MAX_INBOX_LINES} more")
        return style
    }

    private fun baseBuilder(context: Context, channel: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_panda)
            .setColor(BRAND_COLOR)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

    @SuppressLint("MissingPermission") // checked by canPost()
    private fun post(context: Context, tag: String, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(tag, id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private fun installTag(packageName: String) = "install:$packageName"

    private fun requestCode(kind: String, key: String) = "$kind:$key".hashCode()
}
