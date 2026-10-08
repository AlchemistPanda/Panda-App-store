package com.pandagallery.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pandagallery.app.data.local.dao.MediaDao
import com.pandagallery.app.data.local.dao.CompressionTaskDao
import com.pandagallery.app.data.local.dao.BackupDao
import com.pandagallery.app.data.local.dao.SmartMediaDao
import com.pandagallery.app.data.local.dao.SafetyVaultDao
import com.pandagallery.app.data.local.dao.CoverDisplayDao
import com.pandagallery.app.data.local.dao.SharedAlbumDao
import com.pandagallery.app.data.local.dao.StoryDao
import com.pandagallery.app.data.local.dao.FaceGroupDao
import com.pandagallery.app.data.local.dao.TrashItemDao
import com.pandagallery.app.data.local.dao.SecureFolderDao
import com.pandagallery.app.data.local.dao.WidgetConfigDao
import com.pandagallery.app.data.local.dao.EdgePanelActionDao
import com.pandagallery.app.data.local.dao.RawFormatSupportDao
import com.pandagallery.app.data.local.entity.AlbumEntity
import com.pandagallery.app.data.local.entity.MediaEntity
import com.pandagallery.app.data.local.entity.TrashEntity
import com.pandagallery.app.data.local.entity.PrivateMediaEntity
import com.pandagallery.app.data.local.entity.PrivateFolderEntity
import com.pandagallery.app.data.local.entity.CompressionTaskEntity
import com.pandagallery.app.data.local.entity.BackupRecordEntity
import com.pandagallery.app.data.local.entity.SmartMediaIndexEntity
import com.pandagallery.app.data.local.entity.FaceEmbeddingEntity
import com.pandagallery.app.data.local.entity.PersonGroupPreferenceEntity
import com.pandagallery.app.data.local.entity.PersonMediaExclusionEntity
import com.pandagallery.app.data.local.entity.SearchHistoryEntity
import com.pandagallery.app.data.local.entity.SafetyVaultEntity
import com.pandagallery.app.data.local.entity.CoverDisplayEntity
import com.pandagallery.app.data.local.entity.SharedAlbumEntity
import com.pandagallery.app.data.local.entity.SharedLinkEntity
import com.pandagallery.app.data.local.entity.StoryEntity
import com.pandagallery.app.data.local.entity.StoryClipEntity
import com.pandagallery.app.data.local.entity.FaceGroupEntity
import com.pandagallery.app.data.local.entity.TrashItemEntity
import com.pandagallery.app.data.local.entity.SecureFolderEntity
import com.pandagallery.app.data.local.entity.WidgetConfigEntity
import com.pandagallery.app.data.local.entity.EdgePanelActionEntity
import com.pandagallery.app.data.local.entity.RawFormatSupportEntity

@Database(
    entities = [
        MediaEntity::class,
        AlbumEntity::class,
        TrashEntity::class,
        PrivateMediaEntity::class,
        PrivateFolderEntity::class,
        CompressionTaskEntity::class,
        BackupRecordEntity::class,
        SmartMediaIndexEntity::class,
        FaceEmbeddingEntity::class,
        PersonGroupPreferenceEntity::class,
        PersonMediaExclusionEntity::class,
        SearchHistoryEntity::class,
        SafetyVaultEntity::class,
        CoverDisplayEntity::class,
        SharedAlbumEntity::class,
        SharedLinkEntity::class,
        StoryEntity::class,
        StoryClipEntity::class,
        FaceGroupEntity::class,
        TrashItemEntity::class,
        SecureFolderEntity::class,
        WidgetConfigEntity::class,
        EdgePanelActionEntity::class,
        RawFormatSupportEntity::class,
    ],
    // v10 swaps the face brightness-hash column for real embeddings and adds the FTS table.
    // v11 adds estimate calibration, dismissable people groups, album locks and search history.
    // v12 records the frame each face box was measured in, so crops can be rendered.
    // v13 records whether each private item was stored encrypted, so the setting can change
    // without stranding items written under the previous one.
    // v14 adds per-photo removals from a person group, a chosen cover face, and pinned people.
    // v15 records whether a compression task ran lossless, so calibration can exclude it.
    // v16 adds safety_vault_items table for 14-day reversible compression backups.
    // v17 adds cover_display for foldable cover screen viewer.
    // v18 adds shared_album and shared_links for server-less link and QR sharing.
    // v19 adds stories and story_clips for auto-generated highlight reels.
    // v20 adds face_groups for people album clustering.
    // v21 adds trash_items_v2 for 30-day trash lifecycle.
    // v22 adds secure_folder_items for Samsung Knox-style Secure Folder.
    // v23 adds widget_configs and edge_panel_actions for system shortcuts.
    // v24 adds raw_format_support for RAW, HEIC, and AVIF decoding paths.
    // v25 adds targetSizeBytes and remasterDetailLevel to compression_tasks so Compression
    //     Studio's Target Size and AI Remaster choices survive the trip to the encoder.
    version = 26,
    exportSchema = false,
)
abstract class PandaDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun compressionTaskDao(): CompressionTaskDao
    abstract fun backupDao(): BackupDao
    abstract fun smartMediaDao(): SmartMediaDao
    abstract fun safetyVaultDao(): SafetyVaultDao
    abstract fun coverDisplayDao(): CoverDisplayDao
    abstract fun sharedAlbumDao(): SharedAlbumDao
    abstract fun storyDao(): StoryDao
    abstract fun faceGroupDao(): FaceGroupDao
    abstract fun trashItemDao(): TrashItemDao
    abstract fun secureFolderDao(): SecureFolderDao
    abstract fun widgetConfigDao(): WidgetConfigDao
    abstract fun edgePanelActionDao(): EdgePanelActionDao
    abstract fun rawFormatSupportDao(): RawFormatSupportDao

    companion object {
        const val DATABASE_NAME = "panda_gallery.db"
    }
}

