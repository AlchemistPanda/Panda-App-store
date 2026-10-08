package com.pandagallery.app.data.vault

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pandagallery.app.data.local.PreferencesDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Rewrites the Private folder to match the encryption setting.
 *
 * A worker rather than a coroutine in the Settings screen's scope, because this rewrites the only
 * copy of the user's private media: leaving Settings, or Android killing the process, used to
 * abandon the conversion half-done — some items converted, some not. Work survives both, and the
 * per-item flag means a run that is interrupted anyway leaves every item readable.
 *
 * Reads the preference itself rather than taking it as input, so a toggle flipped twice in quick
 * succession converges on whatever the setting finally says.
 */
class VaultEncryptionWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val encrypt = dependencies.preferences().userPreferencesFlow.first().privateVaultEncryption
        return try {
            val skipped = dependencies.vault().convertStorage(encrypt) { done, total ->
                setProgressAsync(
                    Data.Builder()
                        .putInt(PROGRESS_DONE, done)
                        .putInt(PROGRESS_TOTAL, total)
                        .build()
                )
            }
            if (skipped.isEmpty()) {
                Result.success()
            } else {
                // Reported rather than retried: an item that cannot be read will not read on the
                // next attempt either, and it is still listed and still openable as it was.
                Log.w(TAG, "Vault conversion left ${skipped.size} item(s) unchanged: $skipped")
                Result.success(Data.Builder().putInt(SKIPPED, skipped.size).build())
            }
        } catch (error: IOException) {
            // Storage full or a file briefly locked — worth another attempt; the conversion picks
            // up where it stopped because converted items are skipped.
            Log.w(TAG, "Vault conversion attempt $runAttemptCount failed", error)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (error: GeneralSecurityException) {
            // A key the vault can no longer use: retrying will not help, and the items stay
            // readable as they are, so this stops rather than looping.
            Log.e(TAG, "Vault conversion could not use the encryption key", error)
            Result.failure()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun preferences(): PreferencesDataSource
        fun vault(): PrivateVaultRepository
    }

    companion object {
        private const val TAG = "VaultEncryption"
        const val UNIQUE_WORK = "vault_encryption_conversion"
        const val PROGRESS_DONE = "done"
        const val PROGRESS_TOTAL = "total"
        const val SKIPPED = "skipped"
        private const val MAX_RETRIES = 3

        /**
         * Queues a conversion. REPLACE rather than KEEP: the newest setting is the one that should
         * win, and a run already under way is safe to restart.
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<VaultEncryptionWorker>().build(),
            )
        }
    }
}
