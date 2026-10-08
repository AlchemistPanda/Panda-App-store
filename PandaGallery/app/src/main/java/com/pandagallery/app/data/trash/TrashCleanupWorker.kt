package com.pandagallery.app.data.trash

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pandagallery.app.data.local.dao.MediaDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

class TrashCleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val dao = deps.mediaDao()
        val trashItemDao = deps.trashItemDao()
        val now = System.currentTimeMillis()
        val expired = expiredTrashEntries(dao.getExpiredTrash(now), now)
        val expiredV2 = trashItemDao.getExpiredItems(now)
        if (expired.isEmpty() && expiredV2.isEmpty()) return Result.success()
        if (!MediaStore.canManageMedia(applicationContext)) {
            return Result.success(androidx.work.workDataOf(OUTPUT_NEEDS_APPROVAL to (expired.size + expiredV2.size)))
        }
        expired.forEach { entry ->
            runCatching {
                val deleted = applicationContext.contentResolver.delete(Uri.parse(entry.originalUri), null, null)
                if (deleted > 0) {
                    dao.deleteById(entry.mediaId)
                    dao.deleteTrashItem(entry)
                }
            }
        }
        if (expiredV2.isNotEmpty()) {
            val successfullyDeletedV2Ids = mutableListOf<Long>()
            expiredV2.forEach { item ->
                runCatching {
                    val deleted = applicationContext.contentResolver.delete(Uri.parse(item.originalUri), null, null)
                    if (deleted > 0) {
                        successfullyDeletedV2Ids.add(item.mediaId)
                    }
                }
            }
            if (successfullyDeletedV2Ids.isNotEmpty()) {
                trashItemDao.markPurged(successfullyDeletedV2Ids)
            }
        }
        return Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun mediaDao(): MediaDao
        fun trashItemDao(): com.pandagallery.app.data.local.dao.TrashItemDao
    }

    companion object {
        const val UNIQUE_WORK = "panda_trash_cleanup"
        const val OUTPUT_NEEDS_APPROVAL = "needs_approval"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
