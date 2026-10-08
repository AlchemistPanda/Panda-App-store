package com.pandagallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an item locked within the Samsung One UI-style Secure Folder,
 * protected by AES-256 Android KeyStore encryption and biometrics.
 */
@Entity(
    tableName = "secure_folder_items",
    indices = [Index(value = ["folderId"]), Index(value = ["encryptedFileName"])]
)
data class SecureFolderEntity(
    @PrimaryKey
    val id: String,
    val originalDisplayName: String,
    val mimeType: String,
    val originalSize: Long,
    val encryptedFileName: String,
    val encryptedKeyAlias: String,
    val folderId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val isBiometricProtected: Boolean = true
)
