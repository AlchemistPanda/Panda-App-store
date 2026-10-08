package com.pandagallery.app.data.compression

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Enforces the Safety Vault retention policy.
 *
 * [SafetyVaultRepository.pruneExpired] existed from the start but had no caller, so every original
 * kept before an in-place compression stayed in private storage forever no matter which retention
 * window was chosen. Compressing a library therefore grew its footprint instead of shrinking it.
 * A daily pass is granular enough for a policy measured in days and cheap enough to run unconditionally.
 */
class SafetyVaultCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            Dependencies::class.java,
        ).safetyVaultRepository()
        return runCatching { repository.pruneExpired() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun safetyVaultRepository(): SafetyVaultRepository
    }

    companion object {
        const val UNIQUE_WORK = "panda_safety_vault_cleanup"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SafetyVaultCleanupWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
