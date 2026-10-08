package com.pandagallery.app.data.compression

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Resumes an interrupted compression batch after the device reboots.
 *
 * [CompressionQueueRepository] already recovers stuck `RUNNING` rows back to `QUEUED` the moment
 * its worker runs (see [CompressionQueueProcessor.processQueue]) — the gap this closes is that
 * nothing re-enqueues that worker after a reboot, so a queued batch would otherwise sit idle
 * until the user happened to reopen the app.
 */
class CompressionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootEntryPoint::class.java,
        )
        val repository = entryPoint.compressionQueueRepository()
        val safetyVault = entryPoint.safetyVaultRepository()
        // A device that stays off past a backup's expiry would otherwise wait for the next daily
        // pass; sweeping here means the policy is honoured as soon as the process is alive again.
        SafetyVaultCleanupWorker.schedule(context.applicationContext)
        val pendingResult = goAsync()
        // BroadcastReceivers have no coroutine scope of their own; goAsync() plus a
        // process-scoped one is what keeps the process alive long enough to check Room and
        // enqueue the worker without blocking the main thread.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.resumeAfterBootIfNeeded()
                runCatching { safetyVault.pruneExpired() }
            } finally {
                pendingResult.finish()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun compressionQueueRepository(): CompressionQueueRepository
        fun safetyVaultRepository(): SafetyVaultRepository
    }
}
