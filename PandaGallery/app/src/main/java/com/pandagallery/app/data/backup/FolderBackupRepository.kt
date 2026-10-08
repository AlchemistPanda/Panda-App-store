package com.pandagallery.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.pandagallery.app.data.local.dao.BackupDao
import com.pandagallery.app.data.local.entity.BackupRecordEntity
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class FolderBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val backupDao: BackupDao,
) {
    fun persistAccess(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    suspend fun backup(preferences: UserPreferences): BackupResult = withContext(Dispatchers.IO) {
        val rootUri = preferences.backupFolderUri ?: throw BackupError.DestinationUnavailable()
        val root = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
            ?: throw BackupError.DestinationUnavailable()
        val backupRoot = root.findFile(BACKUP_FOLDER_NAME)
            ?: root.createDirectory(BACKUP_FOLDER_NAME)
            ?: throw BackupError.DestinationUnavailable()
        val items = mediaRepository.getAllMediaSnapshot().filter {
            shouldIncludeInBackup(
                relativePath = it.relativePath,
                isVideo = it.isVideo,
                selectedAlbumPaths = preferences.backupSelectedAlbumPaths,
                includeVideos = preferences.backupIncludeVideos,
            )
        }

        var copied = 0
        var skipped = 0
        val failures = mutableListOf<String>()
        for (item in items) {
            coroutineContext.ensureActive()
            try {
                val existing = backupDao.get(rootUri, item.id)
                val unchanged = existing?.sourceDateModified == item.dateModified &&
                    existing.sourceSize == item.size &&
                    DocumentFile.fromSingleUri(context, Uri.parse(existing.destinationUri))?.exists() == true
                if (unchanged) {
                    skipped++
                    continue
                }

                val parent = if (preferences.backupPreserveAlbumStructure) {
                    backupFolderComponents(item.relativePath).fold(backupRoot) { folder, component ->
                        folder.findFile(component)?.takeIf(DocumentFile::isDirectory)
                            ?: folder.createDirectory(component)
                            ?: throw IOException("Unable to create backup folder $component")
                    }
                } else {
                    backupRoot
                }
                val destinationName = "${item.id}_${item.displayName.replace('/', '_')}"
                val destination = parent.findFile(destinationName)
                    ?: parent.createFile(item.mimeType, destinationName)
                    ?: throw IOException("Unable to create ${item.displayName}")
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(destination.uri, "wt")?.use { output ->
                        input.copyTo(output)
                    } ?: throw IOException("Unable to write ${item.displayName}")
                } ?: throw IOException("Unable to read ${item.displayName}")
                backupDao.upsert(
                    BackupRecordEntity(
                        destinationRootUri = rootUri,
                        mediaId = item.id,
                        sourceDateModified = item.dateModified,
                        sourceSize = item.size,
                        destinationUri = destination.uri.toString(),
                        relativePath = item.relativePath,
                        displayName = item.displayName,
                        mimeType = item.mimeType,
                        dateTaken = item.dateTaken,
                        backedUpAt = System.currentTimeMillis(),
                    )
                )
                copied++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                failures += "${item.displayName}: ${error.message ?: "copy failed"}"
            } catch (error: SecurityException) {
                throw BackupError.PermissionLost(error)
            }
        }
        BackupResult(scanned = items.size, copied = copied, skipped = skipped, failures = failures)
    }

    suspend fun restoreAll(rootUri: String): RestoreResult = withContext(Dispatchers.IO) {
        var restored = 0
        val failures = mutableListOf<String>()
        for (record in backupDao.getAll(rootUri)) {
            coroutineContext.ensureActive()
            try {
                restore(record)
                restored++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                failures += "${record.displayName}: ${error.message ?: "restore failed"}"
            } catch (error: SecurityException) {
                throw BackupError.PermissionLost(error)
            }
        }
        mediaRepository.syncMediaStore()
        RestoreResult(restored = restored, failures = failures)
    }

    private fun restore(record: BackupRecordEntity) {
        val collection = if (record.mimeType.startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val destination = context.contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, record.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, record.mimeType)
            record.relativePath?.let { put(MediaStore.MediaColumns.RELATIVE_PATH, it) }
            record.dateTaken?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: throw IOException("Unable to create restored file")
        try {
            context.contentResolver.openInputStream(Uri.parse(record.destinationUri))?.use { input ->
                context.contentResolver.openOutputStream(destination)?.use(input::copyTo)
                    ?: throw IOException("Unable to write restored file")
            } ?: throw IOException("Backup file is unavailable")
            context.contentResolver.update(destination, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
        } catch (error: Throwable) {
            context.contentResolver.delete(destination, null, null)
            throw error
        }
    }

    private companion object {
        const val BACKUP_FOLDER_NAME = "PandaGallery Backup"
    }
}

data class BackupResult(
    val scanned: Int,
    val copied: Int,
    val skipped: Int,
    val failures: List<String>,
)

data class RestoreResult(val restored: Int, val failures: List<String>)

sealed class BackupError(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class DestinationUnavailable : BackupError("The selected backup folder is no longer available")
    class PermissionLost(cause: Throwable) : BackupError("Access to the backup folder was lost", cause)
}
