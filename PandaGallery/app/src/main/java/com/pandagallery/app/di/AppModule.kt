package com.pandagallery.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pandagallery.app.data.local.db.SmartSearchSchema
import com.pandagallery.app.data.local.dao.CompressionTaskDao
import com.pandagallery.app.data.local.dao.MediaDao
import com.pandagallery.app.data.local.dao.BackupDao
import com.pandagallery.app.data.local.dao.SmartMediaDao
import com.pandagallery.app.data.local.db.PandaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `compression_tasks` (`id` TEXT NOT NULL, `operationId` TEXT NOT NULL, `sourceMediaId` INTEGER NOT NULL, `sourceUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `originalBytes` INTEGER NOT NULL, `dateTaken` INTEGER, `status` TEXT NOT NULL, `progress` INTEGER NOT NULL, `outputUri` TEXT, `outputBytes` INTEGER, `savedBytes` INTEGER NOT NULL, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL, `startedAt` INTEGER, `completedAt` INTEGER, PRIMARY KEY(`id`))"""
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_compression_tasks_operationId` ON `compression_tasks` (`operationId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_compression_tasks_status` ON `compression_tasks` (`status`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_compression_tasks_createdAt` ON `compression_tasks` (`createdAt`)")
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `compression_tasks` ADD COLUMN `originalAction` TEXT NOT NULL DEFAULT 'COPY'")
            database.execSQL("ALTER TABLE `compression_tasks` ADD COLUMN `originalDeleteStatus` TEXT NOT NULL DEFAULT 'NOT_REQUIRED'")
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `private_media` ADD COLUMN `originalRelativePath` TEXT")
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `private_media` ADD COLUMN `folderId` TEXT")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_private_media_folderId` ON `private_media` (`folderId`)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `private_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_private_folders_name` ON `private_folders` (`name`)")
        }
    }

    private val migration6To7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `backup_records` (`destinationRootUri` TEXT NOT NULL, `mediaId` INTEGER NOT NULL, `sourceDateModified` INTEGER NOT NULL, `sourceSize` INTEGER NOT NULL, `destinationUri` TEXT NOT NULL, `relativePath` TEXT, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `dateTaken` INTEGER, `backedUpAt` INTEGER NOT NULL, PRIMARY KEY(`destinationRootUri`, `mediaId`))"""
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_records_backedUpAt` ON `backup_records` (`backedUpAt`)")
        }
    }

    private val migration7To8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""CREATE TABLE IF NOT EXISTS `smart_media_index` (`mediaId` INTEGER NOT NULL, `sourceDateModified` INTEGER NOT NULL, `recognizedText` TEXT NOT NULL, `labels` TEXT NOT NULL, `faceCount` INTEGER NOT NULL, `faceSignatures` TEXT NOT NULL, `indexedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))""")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_smart_media_index_indexedAt` ON `smart_media_index` (`indexedAt`)")
        }
    }

    private val migration8To9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `smart_media_index` ADD COLUMN `facesIndexed` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("""CREATE TABLE IF NOT EXISTS `person_group_preferences` (`groupKey` TEXT NOT NULL, `name` TEXT, `mergedIntoKey` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`groupKey`))""")
        }
    }

    /**
     * Replaces the face brightness-hash column with a real embedding table, and adds the
     * full-text search table.
     *
     * OCR text and labels are preserved — they are still valid, and re-running ML Kit over
     * a whole library is expensive. Face data is not preserved: the old 64-bit average
     * hashes are not comparable to embeddings in any way, so they are dropped and
     * `facesIndexed` is reset to force a face-only re-scan on the next background pass.
     */
    private val migration9To10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `smart_media_index_new` (`mediaId` INTEGER NOT NULL, `sourceDateModified` INTEGER NOT NULL, `recognizedText` TEXT NOT NULL, `labels` TEXT NOT NULL, `faceCount` INTEGER NOT NULL, `indexedAt` INTEGER NOT NULL, `facesIndexed` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))"""
            )
            database.execSQL(
                """INSERT INTO `smart_media_index_new` (`mediaId`, `sourceDateModified`, `recognizedText`, `labels`, `faceCount`, `indexedAt`, `facesIndexed`) SELECT `mediaId`, `sourceDateModified`, `recognizedText`, `labels`, 0, `indexedAt`, 0 FROM `smart_media_index`"""
            )
            database.execSQL("DROP TABLE `smart_media_index`")
            database.execSQL("ALTER TABLE `smart_media_index_new` RENAME TO `smart_media_index`")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_smart_media_index_indexedAt` ON `smart_media_index` (`indexedAt`)")

            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `face_embeddings` (`mediaId` INTEGER NOT NULL, `faceIndex` INTEGER NOT NULL, `embedding` BLOB NOT NULL, `dimensions` INTEGER NOT NULL, `quality` REAL NOT NULL, `personKey` TEXT, `boxLeft` INTEGER NOT NULL, `boxTop` INTEGER NOT NULL, `boxRight` INTEGER NOT NULL, `boxBottom` INTEGER NOT NULL, PRIMARY KEY(`mediaId`, `faceIndex`))"""
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_face_embeddings_mediaId` ON `face_embeddings` (`mediaId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_face_embeddings_personKey` ON `face_embeddings` (`personKey`)")

            // Group names were keyed off the old hash seeds, which no longer exist.
            database.execSQL("DELETE FROM `person_group_preferences`")

            database.execSQL(SmartSearchSchema.CREATE_TABLE)
        }
    }

    /**
     * Adds estimate calibration, dismissable people groups, per-album locks and search
     * history. All additive — nothing existing is rewritten, so no data is at risk.
     */
    private val migration10To11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `compression_tasks` ADD COLUMN `predictedBytes` INTEGER")
            database.execSQL("ALTER TABLE `person_group_preferences` ADD COLUMN `isDismissed` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `albums` ADD COLUMN `isLocked` INTEGER NOT NULL DEFAULT 0")
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `lastUsedAt` INTEGER NOT NULL, `useCount` INTEGER NOT NULL, PRIMARY KEY(`query`))"""
            )
        }
    }

    /**
     * Records the decode size each face box was measured against, so face crops can be
     * mapped onto an image loaded at any other resolution.
     */
    private val migration11To12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `face_embeddings` ADD COLUMN `sourceWidth` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `face_embeddings` ADD COLUMN `sourceHeight` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration12To13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Everything written before this version was encrypted, so that is the default the
            // existing rows take — anything else would make them unreadable.
            database.execSQL(
                "ALTER TABLE `private_media` ADD COLUMN `isEncrypted` INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    private val migration13To14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Face clustering gets people wrong sometimes, so a photo has to be removable from a
            // group — and the removal has to survive the next clustering run, which rewrites every
            // assignment. Hence a table of its own rather than a nulled-out face row.
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `person_media_exclusions` (`groupKey` TEXT NOT NULL, `mediaId` INTEGER NOT NULL, `excludedAt` INTEGER NOT NULL, PRIMARY KEY(`groupKey`, `mediaId`))"""
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_person_media_exclusions_groupKey` ON `person_media_exclusions` (`groupKey`)"
            )
            database.execSQL("ALTER TABLE `person_group_preferences` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `person_group_preferences` ADD COLUMN `coverMediaId` INTEGER")
        }
    }

    private val migration14To15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `compression_tasks` ADD COLUMN `isLossless` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration15To16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `safety_vault_items` (`id` TEXT NOT NULL, `compressedMediaId` INTEGER, `sourceMediaId` INTEGER NOT NULL, `originalDisplayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `originalBytes` INTEGER NOT NULL, `localBackupPath` TEXT NOT NULL, `dateTaken` INTEGER, `backedUpAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_safety_vault_items_compressedMediaId` ON `safety_vault_items` (`compressedMediaId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_safety_vault_items_sourceMediaId` ON `safety_vault_items` (`sourceMediaId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_safety_vault_items_expiresAt` ON `safety_vault_items` (`expiresAt`)")
        }
    }

    private val migration16To17 = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `cover_display` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `isCoverActive` INTEGER NOT NULL, `currentMediaId` INTEGER, `lastSyncTimestamp` INTEGER NOT NULL)"""
            )
        }
    }

    private val migration17To18 = object : Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `shared_album` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `albumId` INTEGER NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER, `shareToken` TEXT NOT NULL, `memberCount` INTEGER NOT NULL, `isPublic` INTEGER NOT NULL)"""
            )
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `shared_links` (`shareToken` TEXT NOT NULL, `albumId` INTEGER NOT NULL, `shareUrl` TEXT NOT NULL, `qrCodePath` TEXT, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER, PRIMARY KEY(`shareToken`))"""
            )
        }
    }

    private val migration18To19 = object : Migration(18, 19) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `stories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT, `coverMediaId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `musicTrackUri` TEXT, `templateName` TEXT NOT NULL, `isAutoGenerated` INTEGER NOT NULL)"""
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_createdAt` ON `stories` (`createdAt`)")
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `story_clips` (`storyId` INTEGER NOT NULL, `clipOrder` INTEGER NOT NULL, `mediaId` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `transitionEffect` TEXT NOT NULL, `caption` TEXT, PRIMARY KEY(`storyId`, `clipOrder`))"""
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_story_clips_storyId` ON `story_clips` (`storyId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_story_clips_mediaId` ON `story_clips` (`mediaId`)")
        }
    }

    private val migration19To20 = object : Migration(19, 20) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `face_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personKey` TEXT NOT NULL, `name` TEXT, `coverMediaId` INTEGER, `faceCount` INTEGER NOT NULL, `clusterId` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `isDismissed` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"""
            )
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_face_groups_personKey` ON `face_groups` (`personKey`)")
        }
    }

    private val migration20To21 = object : Migration(20, 21) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `trash_items_v2` (`mediaId` INTEGER NOT NULL, `originalUri` TEXT NOT NULL, `originalPath` TEXT, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `size` INTEGER NOT NULL, `trashedDate` INTEGER NOT NULL, `expiryDate` INTEGER NOT NULL, `isPurged` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))"""
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_trash_items_v2_expiryDate` ON `trash_items_v2` (`expiryDate`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_trash_items_v2_trashedDate` ON `trash_items_v2` (`trashedDate`)")
        }
    }

    private val migration21To22 = object : Migration(21, 22) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `secure_folder_items` (`id` TEXT NOT NULL, `originalDisplayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `originalSize` INTEGER NOT NULL, `encryptedFileName` TEXT NOT NULL, `encryptedKeyAlias` TEXT NOT NULL, `folderId` TEXT, `addedAt` INTEGER NOT NULL, `isBiometricProtected` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_secure_folder_items_folderId` ON `secure_folder_items` (`folderId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_secure_folder_items_encryptedFileName` ON `secure_folder_items` (`encryptedFileName`)")
        }
    }

    private val migration22To23 = object : Migration(22, 23) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `widget_configs` (`appWidgetId` INTEGER NOT NULL, `widgetType` TEXT NOT NULL, `targetAlbumId` INTEGER, `refreshIntervalMinutes` INTEGER NOT NULL, `showLabels` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`appWidgetId`))"""
            )
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `edge_panel_actions` (`actionId` TEXT NOT NULL, `label` TEXT NOT NULL, `iconResName` TEXT NOT NULL, `targetRoute` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, PRIMARY KEY(`actionId`))"""
            )
        }
    }

    private val migration23To24 = object : Migration(23, 24) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE IF NOT EXISTS `raw_format_support` (`extension` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `isHardwareAccelerated` INTEGER NOT NULL, `supportsThumbnailExtraction` INTEGER NOT NULL, `fallbackCodec` TEXT NOT NULL, PRIMARY KEY(`extension`))"""
            )
        }
    }

    /**
     * v25 carries the two Compression Studio choices that previously only affected its preview:
     * the Target Size budget and the AI Remaster strength. Both are nullable/defaulted, so
     * existing queued and completed rows keep behaving exactly as before.
     */
    private val migration24To25 = object : Migration(24, 25) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE compression_tasks ADD COLUMN targetSizeBytes INTEGER")
            database.execSQL(
                "ALTER TABLE compression_tasks ADD COLUMN remasterDetailLevel REAL NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v26 snapshots each compression task's encode settings at enqueue time and records where a
     * Safety Vault original lived, so a revert restores it to its own album. All columns are
     * nullable: older rows fall back to the current preferences / the default folder.
     */
    private val migration25To26 = object : Migration(25, 26) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE compression_tasks ADD COLUMN imageFormat TEXT")
            database.execSQL("ALTER TABLE compression_tasks ADD COLUMN imageQuality INTEGER")
            database.execSQL("ALTER TABLE compression_tasks ADD COLUMN videoResolution TEXT")
            database.execSQL("ALTER TABLE compression_tasks ADD COLUMN videoCodec TEXT")
            database.execSQL("ALTER TABLE safety_vault_items ADD COLUMN relativePath TEXT")
        }
    }

    /**
     * The FTS table is created outside Room's entity model (see [SmartSearchSchema]), so it
     * has to be established on first create and re-checked on open — the latter also covers
     * the destructive-migration path, which rebuilds only Room's own tables.
     */
    private val schemaCallback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(SmartSearchSchema.CREATE_TABLE)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL(SmartSearchSchema.CREATE_TABLE)
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): PandaDatabase {
        return Room.databaseBuilder(
            context,
            PandaDatabase::class.java,
            PandaDatabase.DATABASE_NAME,
        )
            .addMigrations(
                migration2To3, migration3To4, migration4To5, migration5To6,
                migration6To7, migration7To8, migration8To9, migration9To10,
                migration10To11, migration11To12, migration12To13, migration13To14,
                migration14To15, migration15To16, migration16To17, migration17To18,
                migration18To19, migration19To20, migration20To21, migration21To22,
                migration22To23, migration23To24, migration24To25, migration25To26,
            )
            .addCallback(schemaCallback)
            .fallbackToDestructiveMigration()
            .build()
        }

    @Provides
    @Singleton
    fun provideMediaDao(database: PandaDatabase): MediaDao {
        return database.mediaDao()
    }

    @Provides
    @Singleton
    fun provideCompressionTaskDao(database: PandaDatabase): CompressionTaskDao =
        database.compressionTaskDao()

    @Provides
    @Singleton
    fun provideBackupDao(database: PandaDatabase): BackupDao = database.backupDao()

    @Provides
    @Singleton
    fun provideSmartMediaDao(database: PandaDatabase): SmartMediaDao = database.smartMediaDao()

    @Provides
    @Singleton
    fun provideSafetyVaultDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.SafetyVaultDao =
        database.safetyVaultDao()

    @Provides
    @Singleton
    fun provideCoverDisplayDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.CoverDisplayDao =
        database.coverDisplayDao()

    @Provides
    @Singleton
    fun provideSharedAlbumDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.SharedAlbumDao =
        database.sharedAlbumDao()

    @Provides
    @Singleton
    fun provideStoryDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.StoryDao =
        database.storyDao()

    @Provides
    @Singleton
    fun provideFaceGroupDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.FaceGroupDao =
        database.faceGroupDao()

    @Provides
    @Singleton
    fun provideTrashItemDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.TrashItemDao =
        database.trashItemDao()

    @Provides
    @Singleton
    fun provideSecureFolderDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.SecureFolderDao =
        database.secureFolderDao()

    @Provides
    @Singleton
    fun provideWidgetConfigDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.WidgetConfigDao =
        database.widgetConfigDao()

    @Provides
    @Singleton
    fun provideEdgePanelActionDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.EdgePanelActionDao =
        database.edgePanelActionDao()

    @Provides
    @Singleton
    fun provideRawFormatSupportDao(database: PandaDatabase): com.pandagallery.app.data.local.dao.RawFormatSupportDao =
        database.rawFormatSupportDao()
}

