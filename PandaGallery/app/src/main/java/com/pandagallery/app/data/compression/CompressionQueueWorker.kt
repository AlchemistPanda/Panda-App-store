package com.pandagallery.app.data.compression

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import com.pandagallery.app.MainActivity
import com.pandagallery.app.R
import com.pandagallery.app.domain.model.formatFileSize
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class CompressionQueueWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(
            progress = CompressionTaskProgress(
                completedCount = 0,
                totalCount = 0,
                currentFileName = null,
                currentItemPercent = 0,
                savedBytesTotal = 0L,
            )
        )
    }

    override suspend fun doWork(): Result {
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG,
        )?.apply {
            setReferenceCounted(false)
            // Safety timeout of 2 hours for large batch compression
            acquire(2 * 60 * 60 * 1000L)
        }

        try {
            val processor = EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java,
            ).processor()

            // An appended follow-on run usually starts after the batch it queued behind has drained,
            // and a retry can start once the queue is empty. With nothing left, return before the
            // foreground promotion: a refused promotion would post "Compression paused" for an empty queue.
            if (!processor.hasWork()) {
                // Nothing is left to resume, so a "paused" notice from an earlier refused run is stale.
                notificationManager()?.cancel(RESUME_NOTIFICATION_ID)
                // A run killed after its last row completed can leave a GB-scale export behind, and the next
                // batch may be a long way off: reclaim it now, as processQueue would at its start.
                processor.sweepStaleWorkIfIdle()
                return Result.success()
            }

            // The batch only survives being backgrounded for as long as this promotion holds.
            // One retry covers the case where the platform briefly refuses the start (e.g. the
            // app was moving between states as the queue was scheduled).
            if (!promoteToForeground(getForegroundInfo())) {
                delay(1_000L)
                if (!promoteToForeground(getForegroundInfo())) {
                    // Still refused — typically the worker was started from BOOT_COMPLETED, from
                    // which Android 15 never allows a dataSync foreground service, or the app was
                    // backgrounded before it ran. Running the batch anyway as plain background
                    // work gets it stopped at the job runtime limit with the current video thrown
                    // away, over and over. Leave the rows queued and ask the user to open the app,
                    // whose resume check starts the batch again from the foreground.
                    postResumeNotification()
                    return Result.success()
                }
            }
            notificationManager()?.cancel(RESUME_NOTIFICATION_ID)

            var lastUpdateTime = 0L

            processor.processQueue { progress ->
                val now = System.currentTimeMillis()
                val isBatchFinished = progress.totalCount > 0 &&
                    progress.completedCount == progress.totalCount

                // Every worker reports progress, so with several in flight an un-throttled
                // update posts the same notification many times a second. Beyond being
                // wasteful, the platform rate-limits that and starts dropping updates, which
                // leaves the ongoing notification stale while the batch is still running.
                if (isBatchFinished || now - lastUpdateTime >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                    lastUpdateTime = now
                    promoteToForeground(createForegroundInfo(progress))
                }
            }

            return Result.success()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            // Per-task failures never reach here (the processor records them on the row); this is
            // the batch itself failing, e.g. the database briefly full. failure() also fails every
            // request appended to the unique chain, so nothing resumed until the app was reopened.
            // A retry runs processQueue again, which first resets this run's RUNNING rows to QUEUED.
            Log.e(TAG, "Compression batch failed; will retry", error)
            return Result.retry()
        } finally {
            if (wakeLock?.isHeld == true) {
                runCatching { wakeLock.release() }
            }
        }
    }

    /** Returns whether the worker is (still) running as a foreground service. */
    private suspend fun promoteToForeground(info: ForegroundInfo): Boolean =
        runCatching { setForeground(info) }
            .onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w(TAG, "Could not run the compression queue in the foreground", error)
            }
            .isSuccess

    private fun createForegroundInfo(
        progress: CompressionTaskProgress,
    ): ForegroundInfo {
        ensureNotificationChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_COMPRESSION
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val total = progress.totalCount
        val completed = progress.completedCount
        val currentItemPercent = progress.currentItemPercent.coerceIn(0, 100)
        val currentFileName = progress.currentFileName

        val overallPercent = if (total > 0) {
            ((completed * 100 + currentItemPercent) / total).coerceIn(0, 100)
        } else {
            0
        }

        val title = if (total > 0) {
            if (progress.isThermalThrottled) {
                "${applicationContext.getString(R.string.compression_notification_title)} • $overallPercent% (Cooling)"
            } else {
                "${applicationContext.getString(R.string.compression_notification_title)} • $overallPercent%"
            }
        } else {
            applicationContext.getString(R.string.compression_notification_title)
        }

        val contentText = when {
            total <= 0 -> applicationContext.getString(R.string.compression_notification_processing)
            currentFileName != null -> "$currentFileName ($currentItemPercent%)"
            else -> applicationContext.getString(
                R.string.compression_notification_progress_simple,
                completed,
                total,
            )
        }

        val subText = if (progress.isThermalThrottled) {
            "Cooling • ${progress.thermalDescription}"
        } else if (total > 0) {
            "${completed.coerceAtMost(total)} / $total"
        } else null

        val detailText = buildString {
            if (currentFileName != null) {
                append("Current: ").append(currentFileName)
                append(" (").append(currentItemPercent).append("%)\n")
            }
            if (total > 0) {
                append("Batch: ").append(completed).append(" of ").append(total).append(" completed (").append(overallPercent).append("%)")
            } else {
                append(applicationContext.getString(R.string.compression_notification_processing))
            }
            if (progress.savedBytesTotal > 0L) {
                append("\nSaved: ").append(formatFileSize(progress.savedBytesTotal))
            }
            if (progress.isThermalThrottled) {
                append("\nThermal Guard: ").append(progress.thermalDescription)
            }
        }

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_shortcut_cleanup)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .addAction(
                R.drawable.ic_shortcut_cleanup,
                applicationContext.getString(R.string.compression_notification_view_queue),
                pendingIntent,
            )

        if (subText != null) {
            builder.setSubText(subText)
        }

        if (total <= 0) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, overallPercent, false)
        }

        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notificationManager(): NotificationManager? =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private fun postResumeNotification() {
        ensureNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                action = MainActivity.ACTION_COMPRESSION
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_cleanup)
            .setContentTitle(applicationContext.getString(R.string.compression_notification_title))
            // ponytail: hardcoded English like the rest of this notification's detail text; move to strings.xml with the other compression strings
            .setContentText("Compression paused. Tap to resume.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        // Without POST_NOTIFICATIONS this throws or is dropped; the batch still resumes on next open.
        runCatching { notificationManager()?.notify(RESUME_NOTIFICATION_ID, notification) }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.compression_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = applicationContext.getString(R.string.compression_notification_channel_desc)
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun processor(): CompressionQueueProcessor
    }

    companion object {
        const val UNIQUE_WORK_NAME = "panda_compression_queue"
        private const val CHANNEL_ID = "panda_compression_channel"
        private const val NOTIFICATION_ID = 2001
        private const val RESUME_NOTIFICATION_ID = 2002
        private const val WAKELOCK_TAG = "PandaGallery:CompressionQueueWakeLock"
        private const val TAG = "CompressionQueue"

        /** Slow enough to stay under the platform's notification rate limit with every worker reporting. */
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
    }
}
