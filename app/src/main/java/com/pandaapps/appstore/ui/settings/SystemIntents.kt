package com.pandaapps.appstore.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.pandaapps.appstore.appContainer

/** Intents and helpers for leaving the app from the Settings screen. */
internal object SystemIntents {

    const val NTFY_PACKAGE = "io.heckel.ntfy"

    /** This app's notification settings page (API 26+). */
    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** This app's "App info" page; a fallback when a more specific page is missing. */
    fun appDetailsSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** The ntfy listing in the Play Store app. */
    fun ntfyStoreListing(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$NTFY_PACKAGE"))

    /** The ntfy listing on the web, for devices without a store app. */
    fun ntfyWebListing(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$NTFY_PACKAGE"))
}

/**
 * Starts the first of [intents] that the device can handle. Returns false (and logs) when none can.
 * Intents get FLAG_ACTIVITY_NEW_TASK when [this] is not an Activity.
 */
internal fun Context.startFirstAvailable(vararg intents: Intent): Boolean {
    val needsNewTask = findActivity() == null
    for (intent in intents) {
        try {
            startActivity(if (needsNewTask) Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        } catch (e: SecurityException) {
            appContainer.appLog.w(TAG, "Not allowed to open ${intent.action}", e)
        }
    }
    appContainer.appLog.w(TAG, "No activity for ${intents.joinToString { it.action.orEmpty() }}")
    return false
}

/** The Activity behind a Compose `LocalContext`, if any. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "Settings"
