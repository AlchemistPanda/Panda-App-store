package com.pandagallery.app.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pandagallery.app.data.local.PreferencesDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.io.IOException

class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val preferences = dependencies.preferences().userPreferencesFlow.first()
        if (preferences.backupFolderUri == null) return Result.success()
        return try {
            val outcome = dependencies.repository().backup(preferences)
            dependencies.preferences().updateLastBackupAt(System.currentTimeMillis())
            Result.success(
                androidx.work.workDataOf(
                    OUTPUT_COPIED to outcome.copied,
                    OUTPUT_SKIPPED to outcome.skipped,
                    OUTPUT_FAILED to outcome.failures.size,
                )
            )
        } catch (_: BackupError.PermissionLost) {
            Result.failure()
        } catch (_: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun repository(): FolderBackupRepository
        fun preferences(): PreferencesDataSource
    }

    companion object {
        const val UNIQUE_MANUAL_WORK = "panda_backup_manual"
        const val UNIQUE_PERIODIC_WORK = "panda_backup_periodic"
        const val OUTPUT_COPIED = "copied"
        const val OUTPUT_SKIPPED = "skipped"
        const val OUTPUT_FAILED = "failed"
    }
}
