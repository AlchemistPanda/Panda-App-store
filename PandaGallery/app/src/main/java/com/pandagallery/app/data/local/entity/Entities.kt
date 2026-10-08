package com.pandagallery.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Room entity for caching media metadata from MediaStore.
 * This enables instant app launch without re-querying MediaStore.
 */
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["bucketId"]),
        Index(value = ["dateAdded"]),
        Index(value = ["dateTaken"]),
        Index(value = ["isFavorite"]),
        Index(value = ["isTrashed"]),
    ]
)
data class MediaEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val dateModified: Long,
    val dateTaken: Long?,
    val duration: Long?,
    val bucketId: Long?,
    val bucketName: String?,
    val relativePath: String?,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isCompressed: Boolean = false,
    val originalSize: Long? = null,
)

/**
 * Room entity for tracking user-created album metadata.
 */
@Entity(
    tableName = "albums",
    indices = [Index(value = ["name"])]
)
data class AlbumEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val coverUri: String?,
    val relativePath: String?,
    val isHidden: Boolean = false,
    val isPinned: Boolean = false,
    val isPrivate: Boolean = false,
    val isMemoriesVault: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    /**
     * Requires authentication before the album's contents can be opened. Unlike the
     * Private Album this does not move or encrypt anything — the files stay where they
     * are, so this is a speed bump for casual snooping, not protection against someone
     * with file access. Named honestly in the UI for that reason.
     */
    @ColumnInfo(defaultValue = "0") val isLocked: Boolean = false,
)

/**
 * Room entity for tracking trashed items with expiry.
 */
@Entity(
    tableName = "trash_items",
    indices = [Index(value = ["expiryDate"])]
)
data class TrashEntity(
    @PrimaryKey val mediaId: Long,
    val originalUri: String,
    val originalPath: String?,
    val trashedDate: Long,
    val expiryDate: Long,  // 30 days after trashed
)

@Entity(
    tableName = "backup_records",
    primaryKeys = ["destinationRootUri", "mediaId"],
    indices = [Index(value = ["backedUpAt"])],
)
data class BackupRecordEntity(
    val destinationRootUri: String,
    val mediaId: Long,
    val sourceDateModified: Long,
    val sourceSize: Long,
    val destinationUri: String,
    val relativePath: String?,
    val displayName: String,
    val mimeType: String,
    val dateTaken: Long?,
    val backedUpAt: Long,
)

@Entity(
    tableName = "smart_media_index",
    indices = [Index(value = ["indexedAt"])],
)
data class SmartMediaIndexEntity(
    @PrimaryKey val mediaId: Long,
    val sourceDateModified: Long,
    val recognizedText: String,
    val labels: String,
    val faceCount: Int,
    val indexedAt: Long,
    val facesIndexed: Boolean = false,
)

/**
 * One detected face's embedding.
 *
 * Stored as a float32 little-endian BLOB rather than a delimited string: these are a few
 * hundred bytes each and there can be tens of thousands of them, so the text round trip
 * would dominate both the database size and the clustering pass.
 *
 * [personKey] is the group this face landed in last time clustering ran. It is what carries
 * a user's chosen name forward when new photos arrive and the clusters are recomputed.
 */
@Entity(
    tableName = "face_embeddings",
    primaryKeys = ["mediaId", "faceIndex"],
    indices = [Index(value = ["mediaId"]), Index(value = ["personKey"])],
)
data class FaceEmbeddingEntity(
    val mediaId: Long,
    val faceIndex: Int,
    val embedding: ByteArray,
    val dimensions: Int,
    val quality: Float,
    val personKey: String?,
    val boxLeft: Int,
    val boxTop: Int,
    val boxRight: Int,
    val boxBottom: Int,
    /**
     * Size of the bitmap the box was measured against.
     *
     * The indexer works on a downscaled decode, so the raw box means nothing without the
     * frame it came from. Storing it makes the coordinates self-describing rather than
     * implicitly coupled to whatever the decode ceiling happened to be at index time.
     * Zero on rows written before this was recorded.
     */
    @ColumnInfo(defaultValue = "0") val sourceWidth: Int = 0,
    @ColumnInfo(defaultValue = "0") val sourceHeight: Int = 0,
) {
    // Generated equals/hashCode would compare the BLOB by reference; Room never relies on
    // them, but tests and set operations do.
    override fun equals(other: Any?): Boolean = other is FaceEmbeddingEntity &&
        mediaId == other.mediaId && faceIndex == other.faceIndex &&
        embedding.contentEquals(other.embedding) && dimensions == other.dimensions &&
        quality == other.quality && personKey == other.personKey &&
        boxLeft == other.boxLeft && boxTop == other.boxTop &&
        boxRight == other.boxRight && boxBottom == other.boxBottom

    override fun hashCode(): Int = 31 * (31 * mediaId.hashCode() + faceIndex) + embedding.contentHashCode()
}

@Entity(tableName = "person_group_preferences")
data class PersonGroupPreferenceEntity(
    @PrimaryKey val groupKey: String,
    val name: String?,
    val mergedIntoKey: String?,
    val updatedAt: Long,
    /**
     * Set when the user says "this isn't a person" — a poster, a face on a shirt, a
     * stranger in the background. Dismissed groups stay clustered (so they do not simply
     * reappear next run) but are never shown, unless the People screen is temporarily
     * revealing hidden groups so one can be restored.
     */
    @ColumnInfo(defaultValue = "0") val isDismissed: Boolean = false,
    /** Kept at the front of the People grid — the handful of people actually looked for. */
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
    /**
     * The photo whose face represents this person, when the user has chosen one.
     *
     * Null means "whichever face scored highest", which is the right answer until someone
     * disagrees with it. Stored rather than derived so the choice survives re-clustering.
     */
    val coverMediaId: Long? = null,
)

/**
 * One photo the user removed from a person group.
 *
 * Clustering is never going to be perfect, so a wrongly grouped photo has to be removable —
 * and the removal has to outlive the next indexing pass. Deleting the face row would not do
 * that: the face would simply be re-detected and re-clustered into the same group. The
 * exclusion is applied where the group is assembled for display instead.
 *
 * [groupKey] is the group's canonical key, the one merges resolve to, because that is the
 * group the user was looking at when they removed the photo.
 */
@Entity(
    tableName = "person_media_exclusions",
    primaryKeys = ["groupKey", "mediaId"],
    indices = [Index(value = ["groupKey"])],
)
data class PersonMediaExclusionEntity(
    val groupKey: String,
    val mediaId: Long,
    val excludedAt: Long,
)

/**
 * A query the user has run. Ordered by recency, capped in the repository, and never
 * written while the private vault is open.
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val lastUsedAt: Long,
    val useCount: Int = 1,
)

@Entity(
    tableName = "private_media",
    indices = [Index(value = ["folderId"])],
)
data class PrivateMediaEntity(
    @PrimaryKey val id: String,
    val originalName: String,
    val mimeType: String,
    val size: Long,
    val dateTaken: Long?,
    val duration: Long?,
    val encryptedFileName: String,
    /**
     * Whether this item's bytes on disk are encrypted.
     *
     * Per item rather than per app: turning encryption off in Settings must not orphan the items
     * already written encrypted, and turning it on must not make plain ones unreadable. Existing
     * rows migrate as `true`, which is what they are.
     */
    val isEncrypted: Boolean = true,
    val originalRelativePath: String?,
    val folderId: String?,
)

@Entity(
    tableName = "private_folders",
    indices = [Index(value = ["name"], unique = true)],
)
data class PrivateFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "compression_tasks",
    indices = [
        Index(value = ["operationId"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
    ],
)
data class CompressionTaskEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val sourceMediaId: Long,
    val sourceUri: String,
    val displayName: String,
    val mimeType: String,
    val originalBytes: Long,
    val dateTaken: Long? = null,
    val status: String,
    val progress: Int = 0,
    val outputUri: String? = null,
    val outputBytes: Long? = null,
    val savedBytes: Long = 0,
    val errorMessage: String? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    @ColumnInfo(defaultValue = "'COPY'") val originalAction: String = "COPY",
    @ColumnInfo(defaultValue = "'NOT_REQUIRED'") val originalDeleteStatus: String = "NOT_REQUIRED",
    /**
     * What the estimator predicted before this task ran. Kept so the prediction can be
     * scored against [outputBytes] afterwards and future estimates corrected toward what
     * this device and this library actually produce.
     */
    val predictedBytes: Long? = null,
    /**
     * Whether this task ran with the lossless preset. Lossless output size doesn't follow the
     * same curve as the lossy presets, so these are recorded but kept out of
     * [com.pandagallery.app.data.local.dao.CompressionTaskDao.observeCalibrationSamples] —
     * folding them in would skew the correction factor lossy jobs are calibrated against.
     */
    @ColumnInfo(defaultValue = "0") val isLossless: Boolean = false,
    /**
     * A hard output budget in bytes, set when the job was started from Compression Studio's
     * Target Size mode. Null means the ordinary quality-driven path.
     *
     * It has to travel with the task rather than be read from preferences at encode time: the
     * budget is a property of *this* job, and the Studio preview is otherwise the only place it
     * has any effect — the queue would quietly re-encode at whatever the quality slider happened
     * to be on, which is not what the sheet promised.
     */
    val targetSizeBytes: Long? = null,
    /**
     * Strength of the Studio's AI Remaster detail pass, 0 when it was off. Same reasoning as
     * [targetSizeBytes]: without it, the enhancement the user approved in the preview is
     * dropped on the way to the encoder.
     */
    @ColumnInfo(defaultValue = "0") val remasterDetailLevel: Float = 0f,
    /**
     * The encode settings this task was queued with (enum names / quality), snapshotted at
     * enqueue time. The processor used to re-read the global preferences when each task ran, so
     * changing the preset mid-batch — or a batch resumed after a reboot — silently re-encoded the
     * rest of the queue at whatever the settings were by then, while [isLossless] and
     * [predictedBytes] still described the old ones. Null on rows written before v26, which fall
     * back to the current preferences as before.
     */
    val imageFormat: String? = null,
    val imageQuality: Int? = null,
    val videoResolution: String? = null,
    val videoCodec: String? = null,
)

/**
 * Stores local safety-net backups of uncompressed originals when a user performs
 * an in-place compression ("Replace Original").
 *
 * This allows users to fearlessly compress media while retaining the ability to
 * revert back to the exact uncompressed original within a 14-day safety window.
 */
@Entity(
    tableName = "safety_vault_items",
    indices = [
        Index(value = ["compressedMediaId"]),
        Index(value = ["sourceMediaId"]),
        Index(value = ["expiresAt"]),
    ],
)
data class SafetyVaultEntity(
    @PrimaryKey val id: String,
    val compressedMediaId: Long? = null,
    val sourceMediaId: Long,
    val originalDisplayName: String,
    val mimeType: String,
    val originalBytes: Long,
    val localBackupPath: String,
    val dateTaken: Long? = null,
    /**
     * The original's MediaStore RELATIVE_PATH (e.g. `DCIM/Camera/`), so a revert puts it back in
     * its own album instead of the collection's default folder. Null on rows from before v26.
     */
    val relativePath: String? = null,
    val backedUpAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (14L * 24 * 60 * 60 * 1000), // 14 days
)
