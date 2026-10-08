package com.pandagallery.app.data.smart

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartIndexScheduler @Inject constructor(@ApplicationContext context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enable(runImmediately: Boolean = true) {
        ensurePeriodic(workManager)
        if (runImmediately) {
            enqueueImmediate(workManager)
        }
    }

    fun disable() {
        workManager.cancelUniqueWork(SmartIndexWorker.UNIQUE_PERIODIC_WORK)
        workManager.cancelUniqueWork(SmartIndexWorker.UNIQUE_IMMEDIATE_WORK)
    }

    companion object {
        fun ensurePeriodic(context: Context) = ensurePeriodic(WorkManager.getInstance(context))

        fun enqueueContentChange(context: Context) = enqueueImmediate(
            workManager = WorkManager.getInstance(context),
            delaySeconds = CONTENT_CHANGE_DEBOUNCE_SECONDS,
        )

        /**
         * Resumes a pass that hit its per-run item budget ([SmartIndexRepository.indexAll]'s
         * `hasMoreWork`), so a large backlog finishes over several runs instead of one Worker
         * execution racing WorkManager's time limit.
         */
        fun enqueueContinuation(context: Context) = enqueueImmediate(WorkManager.getInstance(context))

        /**
         * A catch-up pass on app start.
         *
         * The content observer only fires while the app is alive, so media added by other
         * apps in the meantime would otherwise wait for the daily charging job. Uses KEEP
         * rather than REPLACE so repeated launches do not keep pushing the delay back, and
         * the pass is incremental — unchanged photos cost one row lookup each.
         */
        fun enqueueCatchUp(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                SmartIndexWorker.UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SmartIndexWorker>()
                    .setConstraints(immediateConstraints())
                    .setInitialDelay(CATCH_UP_DELAY_SECONDS, TimeUnit.SECONDS)
                    .build(),
            )
        }

        private fun ensurePeriodic(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                SmartIndexWorker.UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SmartIndexWorker>(1, TimeUnit.DAYS)
                    .setConstraints(periodicConstraints())
                    .build(),
            )
        }

        private fun enqueueImmediate(workManager: WorkManager, delaySeconds: Long = 0L) {
            workManager.enqueueUniqueWork(
                SmartIndexWorker.UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SmartIndexWorker>()
                    .setConstraints(immediateConstraints())
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .build(),
            )
        }

        private fun immediateConstraints() = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        private fun periodicConstraints() = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiresCharging(true)
            .build()

        private const val CONTENT_CHANGE_DEBOUNCE_SECONDS = 20L

        /** Long enough to stay out of the way of a cold start. */
        private const val CATCH_UP_DELAY_SECONDS = 45L
    }
}
