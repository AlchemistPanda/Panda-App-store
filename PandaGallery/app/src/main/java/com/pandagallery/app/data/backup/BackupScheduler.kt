package com.pandagallery.app.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pandagallery.app.domain.model.BackupSchedule
import com.pandagallery.app.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupScheduler @Inject constructor(@ApplicationContext context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun runNow(preferences: UserPreferences) {
        workManager.enqueueUniqueWork(
            BackupWorker.UNIQUE_MANUAL_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(preferences.constraints())
                .build(),
        )
    }

    fun updateSchedule(preferences: UserPreferences) {
        if (preferences.backupSchedule == BackupSchedule.MANUAL || preferences.backupFolderUri == null) {
            workManager.cancelUniqueWork(BackupWorker.UNIQUE_PERIODIC_WORK)
            return
        }
        val intervalDays = if (preferences.backupSchedule == BackupSchedule.DAILY) 1L else 7L
        workManager.enqueueUniquePeriodicWork(
            BackupWorker.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BackupWorker>(intervalDays, TimeUnit.DAYS)
                .setConstraints(preferences.constraints())
                .build(),
        )
    }

    fun observeManualWork() = workManager.getWorkInfosForUniqueWorkFlow(BackupWorker.UNIQUE_MANUAL_WORK)

    private fun UserPreferences.constraints() = Constraints.Builder()
        .setRequiredNetworkType(if (syncWifiOnly) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
        .setRequiresCharging(backupChargingOnly)
        .build()
}
