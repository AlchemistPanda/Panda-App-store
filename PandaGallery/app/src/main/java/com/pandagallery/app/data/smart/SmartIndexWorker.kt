package com.pandagallery.app.data.smart

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.mlkit.common.MlKitException
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.repository.MediaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.io.IOException

class SmartIndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val preferences = dependencies.preferences().userPreferencesFlow.first()
        if (!preferences.smartIndexEnabled) return Result.success()
        return try {
            dependencies.mediaRepository().syncMediaStore()
            val outcome = dependencies.smartIndexRepository()
                .indexAll(preferences.faceGroupingEnabled) { _, _ -> }
            if (outcome.hasMoreWork) SmartIndexScheduler.enqueueContinuation(applicationContext)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (_: MlKitException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun preferences(): PreferencesDataSource
        fun mediaRepository(): MediaRepository
        fun smartIndexRepository(): SmartIndexRepository
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "panda_smart_index_periodic"
        const val UNIQUE_IMMEDIATE_WORK = "panda_smart_index_immediate"
        private const val MAX_RETRIES = 3
    }
}
