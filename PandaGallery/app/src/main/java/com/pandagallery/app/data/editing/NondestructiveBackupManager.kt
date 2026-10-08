package com.pandagallery.app.data.editing

import android.content.Context
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NondestructiveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val backupDir = File(context.filesDir, "photoeditor_originals").apply {
        if (!exists()) mkdirs()
    }

    private fun backupFile(mediaId: Long): File = File(backupDir, "${mediaId}.orig")

    suspend fun ensureBackup(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val target = backupFile(item.id)
        if (target.exists() && target.length() > 0) return@withContext true
        try {
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target.exists() && target.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun hasBackup(mediaId: Long): Boolean = withContext(Dispatchers.IO) {
        val file = backupFile(mediaId)
        file.exists() && file.length() > 0
    }

    suspend fun restoreOriginal(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val file = backupFile(item.id)
        if (!file.exists() || file.length() == 0L) return@withContext false
        try {
            FileInputStream(file).use { input ->
                context.contentResolver.openOutputStream(item.uri, "rwt")?.use { output ->
                    input.copyTo(output)
                } ?: return@withContext false
            }
            file.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearBackup(mediaId: Long) = withContext(Dispatchers.IO) {
        val file = backupFile(mediaId)
        if (file.exists()) file.delete()
    }
}
