package com.pandaapps.appstore.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pandaapps.appstore.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Owns the unique periodic work [WORK_NAME] that runs [UpdateCheckWorker] (SPEC #8).
 *
 * - Interval comes from settings; `0` cancels the work.
 * - Network: CONNECTED, or UNMETERED when auto-update is on with "Wi-Fi only".
 * - [ExistingPeriodicWorkPolicy.UPDATE] keeps the existing schedule (next run time) while applying
 *   the new interval/constraints, so calling [schedule] repeatedly is cheap and safe.
 */
object UpdateScheduler {

    const val WORK_NAME = "update-check"

    private const val TAG = "Scheduler"
    private const val BACKOFF_MINUTES = 15L

    /** Enqueues (or replaces) the periodic update check, or cancels it when [intervalHours] is 0. */
    fun schedule(context: Context, intervalHours: Int, autoUpdate: Boolean, wifiOnly: Boolean) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val appLog = context.appContainer.appLog
        val request = buildRequest(intervalHours, autoUpdate, wifiOnly)
        if (request == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            appLog.i(TAG, "Background update checks off")
            return
        }
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        appLog.i(
            TAG,
            "Update check every ${intervalHours}h on ${networkTypeFor(autoUpdate, wifiOnly).label}" +
                if (autoUpdate) ", auto-update on" else "",
        )
    }

    /**
     * Reads the stored settings and calls [schedule]. Non-suspending: it launches on the app scope
     * and returns that [Job], so it can be called from `Application.onCreate`.
     */
    fun scheduleFromSettings(context: Context): Job {
        val appContext = context.applicationContext
        val container = appContext.appContainer
        return container.appScope.launch {
            val settings = container.settingsRepository.snapshot()
            schedule(appContext, settings.checkIntervalHours, settings.autoUpdate, settings.wifiOnly)
        }
    }

    /** The periodic request for these settings, or null when checks are off (pure apart from WorkManager types). */
    fun buildRequest(intervalHours: Int, autoUpdate: Boolean, wifiOnly: Boolean): PeriodicWorkRequest? {
        if (intervalHours <= 0) return null
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkTypeFor(autoUpdate, wifiOnly))
            .build()
        return PeriodicWorkRequestBuilder<UpdateCheckWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
    }

    /** Wi-Fi-only only matters when the worker downloads APKs, i.e. with auto-update on. */
    fun networkTypeFor(autoUpdate: Boolean, wifiOnly: Boolean): NetworkType =
        if (autoUpdate && wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

    private val NetworkType.label: String
        get() = if (this == NetworkType.UNMETERED) "Wi-Fi" else "any network"
}
