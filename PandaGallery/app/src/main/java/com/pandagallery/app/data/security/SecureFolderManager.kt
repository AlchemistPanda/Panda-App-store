package com.pandagallery.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.pandagallery.app.data.local.dao.SecureFolderDao
import com.pandagallery.app.data.local.entity.SecureFolderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for Samsung One UI-style Secure Folder, handling hardware-backed KeyStore AES-256 GCM encryption.
 */
@Singleton
class SecureFolderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureFolderDao: SecureFolderDao,
) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val defaultKeyAlias = "panda_secure_folder_key"

    init {
        ensureKeyExists(defaultKeyAlias)
    }

    private fun ensureKeyExists(alias: String) {
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keySpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(alias: String): SecretKey {
        return keyStore.getKey(alias, null) as SecretKey
    }

    fun observeAllSecureItems(): Flow<List<SecureFolderEntity>> = secureFolderDao.observeAllSecureItems()

    /**
     * Encrypts a source file into the app's private secure folder storage.
     */
    suspend fun moveToSecureFolder(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        folderId: String? = null,
    ): SecureFolderEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val encryptedFileName = "sec_$id.bin"
        val secureDir = File(context.filesDir, "secure_folder").apply { mkdirs() }
        val targetEncryptedFile = File(secureDir, encryptedFileName)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getSecretKey(defaultKeyAlias)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv

        FileOutputStream(targetEncryptedFile).use { fos ->
            // Write 12-byte IV first
            fos.write(iv.size)
            fos.write(iv)
            CipherOutputStream(fos, cipher).use { cos ->
                FileInputStream(sourceFile).use { fis ->
                    fis.copyTo(cos)
                }
            }
        }

        val entity = SecureFolderEntity(
            id = id,
            originalDisplayName = displayName,
            mimeType = mimeType,
            originalSize = sourceFile.length(),
            encryptedFileName = encryptedFileName,
            encryptedKeyAlias = defaultKeyAlias,
            folderId = folderId,
            addedAt = System.currentTimeMillis(),
            isBiometricProtected = true
        )
        secureFolderDao.insertItem(entity)
        entity
    }

    /**
     * Decrypts an encrypted file back into an output stream or temporary cache file.
     */
    suspend fun decryptToTempFile(entity: SecureFolderEntity): File = withContext(Dispatchers.IO) {
        val secureDir = File(context.filesDir, "secure_folder")
        val encryptedFile = File(secureDir, entity.encryptedFileName)
        val tempFile = File(context.cacheDir, "dec_${entity.originalDisplayName}")

        FileInputStream(encryptedFile).use { fis ->
            val ivSize = fis.read()
            val iv = ByteArray(ivSize)
            fis.read(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = getSecretKey(entity.encryptedKeyAlias)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(tempFile).use { fos ->
                    cis.copyTo(fos)
                }
            }
        }
        tempFile
    }

    suspend fun removeSecureItem(entity: SecureFolderEntity) = withContext(Dispatchers.IO) {
        val secureDir = File(context.filesDir, "secure_folder")
        val encryptedFile = File(secureDir, entity.encryptedFileName)
        if (encryptedFile.exists()) {
            encryptedFile.delete()
        }
        secureFolderDao.deleteItem(entity)
    }
}
