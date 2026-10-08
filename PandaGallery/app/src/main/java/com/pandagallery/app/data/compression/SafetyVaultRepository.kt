package com.pandagallery.app.data.compression

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.local.dao.SafetyVaultDao
import com.pandagallery.app.data.local.entity.SafetyVaultEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyVaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safetyVaultDao: SafetyVaultDao,
    private val preferencesDataSource: PreferencesDataSource,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private val vaultDir: File by lazy {
        File(context.filesDir, "SafetyVault").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Backs up the original uncompressed media file into private storage before
     * in-place replacement. Returns the created [SafetyVaultEntity] if successful.
     *
     * Returns null both when the vault is switched off and when the copy fails (disk full, source
     * unreadable, insert failed); callers that are about to offer the original for deletion must
     * tell those apart — see [CompressionQueueProcessor]. A failed copy leaves nothing behind.
     *
     * The copy is written to a `.part` name and only renamed to its final name after the row that
     * claims it is inserted. [pruneExpired] treats any unclaimed file as an orphan, so writing the
     * final name first let a sweep running mid-copy delete a multi-GB video backup while the row
     * for it was still about to be inserted.
     */
    suspend fun backupOriginal(
        sourceUri: Uri,
        sourceMediaId: Long,
        displayName: String,
        mimeType: String,
        originalBytes: Long,
        dateTaken: Long?,
        /**
         * Bypasses the "keep originals" preference for backups the user asked for by name — the
         * save-to-vault checkboxes on the generative edit and slow-mo exports. Turning the safety
         * net off should silence the automatic copies, not quietly ignore an explicit request.
         */
        force: Boolean = false,
    ): SafetyVaultEntity? {
        // The row this call claimed, set once it is inserted and renamed. withContext drops its result
        // when the coroutine is cancelled as the copy returns, so the caller never sees the entity and
        // cannot discard it; the outer catch does.
        var claimed: SafetyVaultEntity? = null
        return try {
            withContext(Dispatchers.IO) {
                val preferences = preferencesDataSource.userPreferencesFlow.first()
                if (!force && !preferences.safetyVaultEnabled) return@withContext null
                val safetyFile = File(vaultDir, "${sourceMediaId}_${UUID.randomUUID().toString().take(8)}")
                val partFile = File(vaultDir, safetyFile.name + PART_SUFFIX)
                var entity: SafetyVaultEntity? = null
                try {
                    val copied = contentResolver.openInputStream(sourceUri)?.use { input ->
                        FileOutputStream(partFile).use { output -> input.copyTo(output) }
                    }
                    if (copied == null || partFile.length() <= 0L) return@withContext null

                    val retentionDays = preferences.safetyVaultRetentionDays
                    val expiresAt = if (retentionDays <= 0) {
                        Long.MAX_VALUE // Forever / manual pruning only
                    } else {
                        System.currentTimeMillis() + (retentionDays.toLong() * 24L * 60 * 60 * 1000)
                    }

                    entity = SafetyVaultEntity(
                        id = UUID.randomUUID().toString(),
                        compressedMediaId = null,
                        sourceMediaId = sourceMediaId,
                        originalDisplayName = displayName,
                        mimeType = mimeType,
                        originalBytes = originalBytes,
                        localBackupPath = safetyFile.absolutePath,
                        dateTaken = dateTaken,
                        relativePath = queryRelativePath(sourceUri),
                        expiresAt = expiresAt,
                    )
                    safetyVaultDao.insert(entity)
                    check(partFile.renameTo(safetyFile)) { "Could not finalise the Safety Vault copy" }
                    claimed = entity
                    entity
                } catch (cancelled: CancellationException) {
                    entity?.let { discardBackup(it) }
                    throw cancelled
                } catch (error: Throwable) {
                    com.pandagallery.app.data.diagnostics.CrashLog.e("SafetyVault", "Backing up $displayName failed", error)
                    entity?.let { discardBackup(it) }
                    null
                } finally {
                    // Covers the early `return null` paths too; a no-op once renamed.
                    partFile.delete()
                }
            }
        } catch (cancelled: CancellationException) {
            // Cancelled as the copy returned: the row and file are claimed, but no caller holds them.
            claimed?.let { discardBackup(it) }
            throw cancelled
        }
    }

    /**
     * Removes a backup that no completed task has claimed: its row and its file. Used when a copy
     * fails part-way, and when a MOVE that took its backup fails or is stopped before its completion
     * is recorded. Runs even while the caller is being cancelled, so a stopped batch doesn't leave a
     * full-size copy behind. Only call it while the original is still in place, which holds until the
     * task completes (see `pendingMoveDeletionTasks`).
     */
    suspend fun discardBackup(backup: SafetyVaultEntity) {
        withContext(NonCancellable) {
            File(backup.localBackupPath).delete()
            runCatching { safetyVaultDao.deleteById(backup.id) }
        }
    }

    private fun queryRelativePath(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Associates the resulting compressed media item's MediaStore ID with exactly the backup
     * [backupId] — the row [backupOriginal] returned for this job.
     */
    suspend fun linkCompressedMedia(backupId: String, compressedMediaId: Long) = withContext(Dispatchers.IO) {
        safetyVaultDao.linkCompressedMediaById(backupId, compressedMediaId)
    }

    /**
     * Links only the newest backup of [sourceMediaId]. Kept for callers that don't hold the row
     * id; it used to re-point *every* backup of the source, which broke revert for older copies.
     */
    // ponytail: ViewerViewModel.stripMotionPhoto should pass safetyEntity.id to the String overload
    suspend fun linkCompressedMedia(sourceMediaId: Long, compressedMediaId: Long) = withContext(Dispatchers.IO) {
        safetyVaultDao.linkNewestBackup(sourceMediaId, compressedMediaId)
    }

    suspend fun getSafetyItem(mediaId: Long): SafetyVaultEntity? = withContext(Dispatchers.IO) {
        safetyVaultDao.getByCompressedMediaId(mediaId) ?: safetyVaultDao.getBySourceMediaId(mediaId)
    }

    fun observeSafetyItem(mediaId: Long): Flow<SafetyVaultEntity?> {
        return safetyVaultDao.observeByCompressedMediaId(mediaId)
    }

    /**
     * Restores the original uncompressed file back to MediaStore — into the folder it came from
     * when that was recorded — and only then removes the safety copy. Returns the new Uri of the
     * restored original media in MediaStore.
     *
     * The backup is deleted only once the restored item is confirmed to hold the whole file; it
     * is the user's last copy of the original.
     */
    suspend fun revertOriginal(safetyEntity: SafetyVaultEntity): Uri? = withContext(Dispatchers.IO) {
        val backupFile = File(safetyEntity.localBackupPath)
        if (!backupFile.exists() || backupFile.length() <= 0L) {
            return@withContext null
        }

        val isVideo = safetyEntity.mimeType.startsWith("video/")
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safetyEntity.originalDisplayName)
            put(MediaStore.MediaColumns.MIME_TYPE, safetyEntity.mimeType)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            safetyEntity.dateTaken?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
        }

        // Some original folders can't be written by this app (another app's Android/media/
        // directory, e.g. WhatsApp's); fall back to the collection's default folder for those.
        val restoredUri = safetyEntity.relativePath
            ?.let { path ->
                runCatching {
                    contentResolver.insert(collection, ContentValues(values).apply { put(MediaStore.MediaColumns.RELATIVE_PATH, path) })
                }.getOrNull()
            }
            ?: runCatching { contentResolver.insert(collection, values) }.getOrNull()
            ?: return@withContext null
        try {
            backupFile.inputStream().use { input ->
                contentResolver.openOutputStream(restoredUri)?.use { output ->
                    input.copyTo(output)
                } ?: error("Failed to open output stream for restored file")
            }
            val writtenBytes = contentResolver.openFileDescriptor(restoredUri, "r")?.use { it.statSize } ?: -1L
            check(writtenBytes == backupFile.length()) {
                "Restored file is $writtenBytes bytes, backup is ${backupFile.length()}"
            }

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            safetyEntity.dateTaken?.let { values.put(MediaStore.MediaColumns.DATE_TAKEN, it) }
            check(contentResolver.update(restoredUri, values, null, null) > 0) { "Could not publish the restored file" }

            // Cleanup safety file & database entry
            backupFile.delete()
            safetyVaultDao.deleteById(safetyEntity.id)

            restoredUri
        } catch (e: Throwable) {
            runCatching { contentResolver.delete(restoredUri, null, null) }
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * Drops backups whose retention window has passed, then sweeps the vault directory for files no
     * row points at any more. Orphans accumulate whenever a backup is written but the insert that
     * would have claimed it fails, and nothing else would ever reclaim them.
     *
     * Returns the number of bytes handed back to the device.
     */
    suspend fun pruneExpired(): Long = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        for (item in safetyVaultDao.getExpired()) {
            val file = File(item.localBackupPath)
            if (file.exists()) {
                reclaimed += file.length()
                file.delete()
            }
            safetyVaultDao.deleteById(item.id)
        }

        // The referenced snapshot is taken before listing, so a backup can land in between: its row is
        // inserted and its `.part` renamed to the final name after the snapshot, and the listing then
        // sees a file no snapshot names. Every unclaimed file therefore gets the grace window. A
        // `.part` still being copied keeps being touched, and a renamed copy keeps the write time of
        // its last byte, so both are inside the window. Only debris from a killed copy, or a file
        // nothing has claimed for a long while, is old enough to go.
        val referenced = safetyVaultDao.getAllBackupPaths().toHashSet()
        val now = System.currentTimeMillis()
        vaultDir.listFiles()?.forEach { file ->
            if (file.absolutePath in referenced) return@forEach
            if (!isPastSweepGrace(lastModifiedMs = file.lastModified(), nowMs = now)) return@forEach
            reclaimed += file.length()
            file.delete()
        }
        reclaimed
    }

    /**
     * Deletes every safety copy on demand. Reverting a compressed photo is no longer possible
     * afterwards, so this is only ever reached behind a confirmation.
     *
     * Returns the number of bytes reclaimed.
     */
    suspend fun clearAll(): Long = withContext(Dispatchers.IO) {
        val reclaimed = vaultDir.listFiles()?.sumOf(File::length) ?: 0L
        vaultDir.listFiles()?.forEach { it.delete() }
        safetyVaultDao.deleteAll()
        reclaimed
    }

    /**
     * Re-dates the copies already on disk against a retention window the user has just changed, so
     * shortening the policy reclaims space from past compressions rather than only future ones.
     */
    suspend fun applyRetentionPolicy(retentionDays: Int) = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) {
            safetyVaultDao.setExpiryForAll(Long.MAX_VALUE)
        } else {
            safetyVaultDao.reapplyRetention(retentionDays.toLong() * 24L * 60 * 60 * 1000)
        }
    }

    /** Actual bytes on disk, which counts orphans the database has lost track of. */
    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
        vaultDir.listFiles()?.sumOf(File::length) ?: 0L
    }

    fun observeCount(): Flow<Int> = safetyVaultDao.observeCount()

    private companion object {
        const val PART_SUFFIX = ".part"
    }
}

/**
 * How long an unclaimed vault file is left alone before [SafetyVaultRepository.pruneExpired] may
 * delete it. Six hours is far longer than any copy takes to finish and be claimed, and short enough
 * that real debris is reclaimed within a day.
 */
// ponytail: one window for all unclaimed files; upgrade = re-read the referenced set after listing so final-name files need no grace (.part files still do)
internal const val VAULT_SWEEP_GRACE_MS = 6L * 60 * 60 * 1000

/** Whether a vault file last written at [lastModifiedMs] is old enough for the sweep to delete it. */
internal fun isPastSweepGrace(lastModifiedMs: Long, nowMs: Long): Boolean =
    nowMs - lastModifiedMs >= VAULT_SWEEP_GRACE_MS
