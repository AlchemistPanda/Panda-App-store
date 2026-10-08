package com.pandagallery.app.data.cleanup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.repository.MediaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Moves redundant byte-identical copies to Trash on a schedule.
 *
 * Two deliberate limits on how far this goes:
 *
 * - **Exact duplicates only.** Similar photos and burst frames are never auto-resolved.
 *   "These look the same" is a judgement the user has to make; "these are the same bytes"
 *   is not.
 * - **Trash, never delete.** Everything lands in the 30-day Trash, so a wrong call is
 *   always recoverable. An unattended job must not be able to lose a photo permanently.
 */
class DuplicateAutoResolveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val preferences = dependencies.preferences().userPreferencesFlow.first()
        if (!preferences.duplicateAutoResolve) return Result.success()

        return try {
            val cleanup = dependencies.cleanupRepository()
            cleanup.scan()
            val trashed = cleanup.autoResolveExactDuplicates(
                rule = preferences.duplicateKeepRule,
                retentionDays = preferences.trashRetentionDays,
            )
            Result.success(workDataOf(OUTPUT_TRASHED to trashed))
        } catch (_: SecurityException) {
            // Media permission was revoked; nothing to retry until the user returns.
            Result.success()
        } catch (_: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun preferences(): PreferencesDataSource
        fun cleanupRepository(): CleanupRepository
        fun mediaRepository(): MediaRepository
    }

    companion object {
        const val UNIQUE_WORK = "panda_duplicate_auto_resolve"
        const val OUTPUT_TRASHED = "trashed"
        private const val MAX_RETRIES = 3

        /**
         * Weekly while charging. Hashing every file in the library is expensive, and
         * duplicates do not accumulate fast enough to justify running it more often.
         */
        fun enable(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DuplicateAutoResolveWorker>(7, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresCharging(true)
                            .setRequiresBatteryNotLow(true)
                            .setRequiresStorageNotLow(true)
                            .build()
                    )
                    .build(),
            )
        }

        fun disable(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
