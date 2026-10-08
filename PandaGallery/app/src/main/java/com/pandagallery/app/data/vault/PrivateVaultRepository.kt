package com.pandagallery.app.data.vault

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.local.dao.MediaDao
import com.pandagallery.app.data.local.entity.PrivateMediaEntity
import com.pandagallery.app.data.local.entity.PrivateFolderEntity
import com.pandagallery.app.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.io.SequenceInputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PrivateMediaItem(
    val id: String,
    val originalName: String,
    val mimeType: String,
    val size: Long,
    val dateTaken: Long?,
    val duration: Long?,
    val originalRelativePath: String?,
    val folderId: String?,
)

data class PrivateFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
)

enum class RestoreDestination {
    ORIGINAL_FOLDER,
    RESTORED_FOLDER,
}

@Singleton
class PrivateVaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val preferences: PreferencesDataSource,
) {
    private val vaultDir = File(context.filesDir, "private_vault").apply { mkdirs() }
    private val previewDir = File(context.cacheDir, "private_vault_previews").apply { mkdirs() }
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun observeItems(): Flow<List<PrivateMediaItem>> = mediaDao.getPrivateMedia().map { items ->
        items.map {
            PrivateMediaItem(
                id = it.id,
                originalName = it.originalName,
                mimeType = it.mimeType,
                size = it.size,
                dateTaken = it.dateTaken,
                duration = it.duration,
                originalRelativePath = it.originalRelativePath,
                folderId = it.folderId,
            )
        }
    }

    fun observeFolders(): Flow<List<PrivateFolder>> = mediaDao.getPrivateFolders().map { folders ->
        folders.map { PrivateFolder(id = it.id, name = it.name, createdAt = it.createdAt) }
    }

    fun observeCount(): Flow<Int> = mediaDao.getPrivateMediaCount()

    suspend fun import(items: List<MediaItem>): List<String> = withContext(Dispatchers.IO) {
        val encryptNewItems = preferences.userPreferencesFlow.first().privateVaultEncryption
        val saved = mutableListOf<PrivateMediaEntity>()
        try {
            items.forEach { item ->
                val id = UUID.randomUUID().toString()
                val encryptedName = vaultFileNameFor(id, encryptNewItems)
                val destination = File(vaultDir, encryptedName)
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    writeVaultFile(destination, encrypt = encryptNewItems) { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to read ${item.displayName}")
                saved += PrivateMediaEntity(
                    id = id,
                    originalName = item.displayName,
                    mimeType = item.mimeType,
                    size = item.size,
                    dateTaken = item.dateTaken,
                    duration = item.duration,
                    encryptedFileName = encryptedName,
                    isEncrypted = encryptNewItems,
                    originalRelativePath = item.relativePath,
                    folderId = null,
                )
            }
            mediaDao.insertPrivateMedia(saved)
            saved.map { it.id }
        } catch (error: Exception) {
            saved.forEach { File(vaultDir, it.encryptedFileName).delete() }
            throw error
        }
    }

    suspend fun previewFile(item: PrivateMediaItem): File = withContext(Dispatchers.IO) {
        val entity = mediaDao.getPrivateMediaById(item.id)
            ?: error("Private item no longer exists")
        val extension = item.originalName.substringAfterLast('.', "bin")
        val preview = File(previewDir, "${item.id}.$extension")
        if (!preview.exists()) {
            readVaultFile(entity).use { input ->
                preview.outputStream().use { output -> input.copyTo(output) }
            }
        }
        preview
    }

    suspend fun restore(
        item: PrivateMediaItem,
        destination: RestoreDestination,
    ) = withContext(Dispatchers.IO) {
        val entity = mediaDao.getPrivateMediaById(item.id)
            ?: error("Private item no longer exists")
        val collection = if (item.mimeType.startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val restorePath = when (destination) {
            RestoreDestination.ORIGINAL_FOLDER -> item.originalRelativePath
                ?: RESTORED_RELATIVE_PATH
            RestoreDestination.RESTORED_FOLDER -> RESTORED_RELATIVE_PATH
        }
        val uri = context.contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, restorePath)
            item.dateTaken?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("Unable to create restored media")
        try {
            readVaultFile(entity).use { input ->
                context.contentResolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                    ?: error("Unable to write restored media")
            }
            context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            delete(listOf(item.id))
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    suspend fun restore(
        items: List<PrivateMediaItem>,
        destination: RestoreDestination,
    ) {
        items.forEach { restore(it, destination) }
    }

    suspend fun delete(ids: List<String>) = withContext(Dispatchers.IO) {
        val entities = mediaDao.getPrivateMediaByIds(ids)
        entities.forEach {
            File(vaultDir, it.encryptedFileName).delete()
            previewDir.listFiles()?.filter { file -> file.name.startsWith(it.id) }?.forEach(File::delete)
        }
        mediaDao.deletePrivateMedia(ids)
    }

    suspend fun createFolder(name: String) = withContext(Dispatchers.IO) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Folder name cannot be empty" }
        require(mediaDao.getPrivateFolderNameCount(normalizedName) == 0) {
            "A private folder with this name already exists"
        }
        mediaDao.insertPrivateFolder(
            PrivateFolderEntity(
                id = UUID.randomUUID().toString(),
                name = normalizedName,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun moveToFolder(ids: List<String>, folderId: String?) = withContext(Dispatchers.IO) {
        mediaDao.movePrivateMedia(ids, folderId)
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        require(mediaDao.getPrivateFolderItemCount(folderId) == 0) {
            "Move or restore the items before deleting this folder"
        }
        mediaDao.deletePrivateFolder(folderId)
    }

    fun clearPreviews() {
        previewDir.listFiles()?.forEach(File::delete)
    }

    /**
     * Opens a vault item, reading it the way its own file name says it was written.
     *
     * The name is the source of truth, not the database flag. The two can disagree — a conversion
     * interrupted between replacing a file and updating its row leaves exactly that — and guessing
     * wrong is unrecoverable in one direction: decrypting a plaintext file throws, but reading an
     * encrypted file "as plaintext" quietly succeeds and hands back ciphertext, which would then be
     * written into a restored photo as garbage. Encoding the answer in the name removes the guess.
     */
    private fun readVaultFile(entity: PrivateMediaEntity): InputStream {
        val file = File(vaultDir, entity.encryptedFileName)
        return if (isEncryptedVaultFileName(entity.encryptedFileName)) {
            encryptedFile(file).openFileInput()
        } else {
            file.inputStream()
        }
    }

    private inline fun writeVaultFile(destination: File, encrypt: Boolean, write: (OutputStream) -> Unit) {
        if (encrypt) {
            encryptedFile(destination).openFileOutput().use(write)
        } else {
            destination.outputStream().use(write)
        }
    }

    /**
     * Rewrites stored items so they match [encrypt].
     *
     * Each item is converted through a temporary file and only then swapped in, with its row
     * updated immediately afterwards — so an interruption leaves every item either fully converted
     * or untouched, never a half-written file the vault can no longer read. Items already in the
     * requested state are skipped, which makes this safe to re-run.
     */
    /** @return the names of items that could not be converted and were left untouched. */
    suspend fun convertStorage(
        encrypt: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<String> = withContext(Dispatchers.IO) {
        val skipped = mutableListOf<String>()
        val pending = mediaDao.getAllPrivateMedia()
            .filter { isEncryptedVaultFileName(it.encryptedFileName) != encrypt }
        pending.forEachIndexed { index, entity ->
            val source = File(vaultDir, entity.encryptedFileName)
            if (!source.exists()) return@forEachIndexed
            val convertedName = vaultFileNameFor(entity.id, encrypt)
            val converted = File(vaultDir, convertedName)
            try {
                readVaultFile(entity).use { input ->
                    writeVaultFile(converted, encrypt) { output -> input.copyTo(output) }
                }
                // The row is repointed before the old file goes: interrupted in between, the item
                // already refers to the converted copy and the stale one is only wasted space.
                mediaDao.insertPrivateMedia(
                    listOf(entity.copy(encryptedFileName = convertedName, isEncrypted = encrypt)),
                )
                source.delete()
            } catch (error: IOException) {
                // One unreadable item must not strand the rest. It is left exactly as it was —
                // still listed, still pointing at its own file — and the run carries on, because
                // failing the whole conversion would leave the vault half-converted for good.
                converted.delete()
                skipped += entity.originalName
            } catch (error: GeneralSecurityException) {
                converted.delete()
                skipped += entity.originalName
            }
            onProgress(index + 1, pending.size)
        }
        // Previews are plaintext copies of whatever was stored; drop them so nothing lingers in
        // the cache from before the change.
        clearPreviews()
        skipped
    }

    /** Bytes the private folder occupies on disk, for the usage line in Settings. */
    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
        vaultDir.listFiles()?.sumOf(File::length) ?: 0L
    }

    private fun encryptedFile(file: File) = EncryptedFile.Builder(
        context,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()

    private companion object {
        const val RESTORED_RELATIVE_PATH = "Pictures/PandaGallery Restored"
    }
}

/** The historical suffix for encrypted items; plaintext ones get their own. */
private const val ENCRYPTED_SUFFIX = ".vault"
private const val PLAIN_SUFFIX = ".plainvault"

/**
 * Names a vault file after the way it is stored, so a directory listing alone says which is which
 * and no database row has to be trusted for it.
 */
internal fun vaultFileNameFor(id: String, encrypted: Boolean): String =
    if (encrypted) "$id$ENCRYPTED_SUFFIX" else "$id$PLAIN_SUFFIX"

/** Files written before the encryption setting existed end in `.vault` and are encrypted. */
internal fun isEncryptedVaultFileName(name: String): Boolean = name.endsWith(ENCRYPTED_SUFFIX)
