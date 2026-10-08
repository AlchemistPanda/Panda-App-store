package com.pandagallery.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.pandagallery.app.data.compression.ImageFormatSupport
import com.pandagallery.app.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Delegate for creating DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageFormatSupport: ImageFormatSupport,
) {
    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val COMPRESSION_PRESET = stringPreferencesKey("compression_preset")
        val IMAGE_FORMAT = stringPreferencesKey("image_format")
        val IMAGE_QUALITY = intPreferencesKey("image_quality")
        val VIDEO_RESOLUTION = stringPreferencesKey("video_resolution")
        val VIDEO_CODEC = stringPreferencesKey("video_codec")
        val COMPRESSED_FOLDER_PATH = stringPreferencesKey("compressed_folder_path")
        val COMPRESSION_ORIGINAL_ACTION = stringPreferencesKey("compression_original_action")
        val COMPRESSION_CONCURRENCY = intPreferencesKey("compression_concurrency")
        val USE_GPU = booleanPreferencesKey("use_gpu")
        val SYNC_WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val IS_DRIVE_CONNECTED = booleanPreferencesKey("is_drive_connected")
        val DRIVE_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val BACKUP_SCHEDULE = stringPreferencesKey("backup_schedule")
        val BACKUP_SELECTED_ALBUM_PATHS = stringSetPreferencesKey("backup_selected_album_paths")
        val BACKUP_INCLUDE_VIDEOS = booleanPreferencesKey("backup_include_videos")
        val BACKUP_PRESERVE_ALBUM_STRUCTURE = booleanPreferencesKey("backup_preserve_album_structure")
        val BACKUP_CHARGING_ONLY = booleanPreferencesKey("backup_charging_only")
        val SMART_INDEX_ENABLED = booleanPreferencesKey("smart_index_enabled")
        val FACE_GROUPING_ENABLED = booleanPreferencesKey("face_grouping_enabled")
        // Hashes, never the PINs themselves — see com.pandagallery.app.domain.security.hashPin.
        val FOLDER_LOCK_PIN = stringPreferencesKey("folder_lock_pin")
        val PRIVATE_VAULT_PIN = stringPreferencesKey("private_vault_pin")
        val FOLDER_LOCK_BIOMETRICS = booleanPreferencesKey("folder_lock_biometrics")
        val PRIVATE_VAULT_BIOMETRICS = booleanPreferencesKey("private_vault_biometrics")
        val FOLDER_PIN_FAILURES = intPreferencesKey("folder_pin_failures")
        val FOLDER_PIN_LAST_FAILURE = longPreferencesKey("folder_pin_last_failure")
        val VAULT_PIN_FAILURES = intPreferencesKey("vault_pin_failures")
        val VAULT_PIN_LAST_FAILURE = longPreferencesKey("vault_pin_last_failure")
        val IS_PRIVATE_ALBUM_CONFIGURED = booleanPreferencesKey("is_private_album_configured")
        val ALLOW_PRIVATE_ALBUM_SCREENSHOTS = booleanPreferencesKey("allow_private_album_screenshots")
        val PRIVATE_VAULT_ENCRYPTION = booleanPreferencesKey("private_vault_encryption")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val APP_ICON = stringPreferencesKey("app_icon")
        val MEDIA_SORT_ORDER = stringPreferencesKey("media_sort_order")
        val PER_FOLDER_SORT_ENABLED = booleanPreferencesKey("per_folder_sort_enabled")
        val FOLDER_SORT_OVERRIDES = stringSetPreferencesKey("folder_sort_overrides")
        val MEDIA_CONTENT_FILTER = stringPreferencesKey("media_content_filter")
        val GRID_DENSITY = stringPreferencesKey("grid_density")
        val ALBUM_SORT_ORDER = stringPreferencesKey("album_sort_order")
        val ALBUM_GRID_DENSITY = stringPreferencesKey("album_grid_density")
        val SHOW_ALBUM_SIZE = booleanPreferencesKey("show_album_size")
        val SHOW_PICTURES_TAB = booleanPreferencesKey("show_pictures_tab")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val SLIDESHOW_INTERVAL_SECONDS = intPreferencesKey("slideshow_interval_seconds")
        val AUTO_PLAY_VIDEOS = booleanPreferencesKey("auto_play_videos")
        val DUPLICATE_AUTO_RESOLVE = booleanPreferencesKey("duplicate_auto_resolve")
        val DUPLICATE_KEEP_RULE = stringPreferencesKey("duplicate_keep_rule")
        val AUTO_PLAY_MOTION_PHOTOS = booleanPreferencesKey("auto_play_motion_photos")
        val SUPER_HDR_GAINMAP_ENABLED = booleanPreferencesKey("super_hdr_gainmap_enabled")
        val CONVERT_HEIF_WHEN_SHARING = booleanPreferencesKey("convert_heif_when_sharing")
        val CONVERT_RAW_WHEN_SHARING = booleanPreferencesKey("convert_raw_when_sharing")
        val REMOVE_LOCATION_WHEN_SHARING = booleanPreferencesKey("remove_location_when_sharing")
        val GALLERY_LABS_UNLOCKED = booleanPreferencesKey("gallery_labs_unlocked")
        val ADAPTIVE_PERCEPTUAL_RATE_CONTROL = booleanPreferencesKey("adaptive_perceptual_rate_control")
        val HARDWARE_BITMAPS_DIRECT_VRAM = booleanPreferencesKey("hardware_bitmaps_direct_vram")
        val ROTARY_DIAL_HAPTICS = booleanPreferencesKey("rotary_dial_haptics")
        val THERMAL_THROTTLE_MODE = stringPreferencesKey("thermal_throttle_mode")
        val SAFETY_VAULT_RETENTION_DAYS = intPreferencesKey("safety_vault_retention_days")
        val SAFETY_VAULT_ENABLED = booleanPreferencesKey("safety_vault_enabled")
        val MOTION_PHOTO_COMPRESSION_MODE = stringPreferencesKey("motion_photo_compression_mode")
        val HARDWARE_CODEC_ACCELERATION = booleanPreferencesKey("hardware_codec_acceleration")
        val DYNAMIC_VIDEO_PREVIEW_IN_GRID = booleanPreferencesKey("dynamic_video_preview_in_grid")
        val FILMSTRIP_PRECACHE = booleanPreferencesKey("filmstrip_precache")
        val ALBUM_GROUP_OVERRIDES = stringSetPreferencesKey("album_group_overrides")
        val COMPRESSION_STATUS_PLACEMENT = stringPreferencesKey("compression_status_placement")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            mapUserPreferences(preferences)
        }

    private fun mapUserPreferences(preferences: Preferences): UserPreferences {
        val compressionPreset = enumValueOrDefault(
            preferences[PreferencesKeys.COMPRESSION_PRESET],
            CompressionPreset.HIGH,
        )
        val rawPresetName = preferences[PreferencesKeys.COMPRESSION_PRESET]
        val rawFormatName = preferences[PreferencesKeys.IMAGE_FORMAT]
        val rawResName = preferences[PreferencesKeys.VIDEO_RESOLUTION]
        val rawQuality = preferences[PreferencesKeys.IMAGE_QUALITY]

        // If the user had the legacy default combination (WEBP/P720 under default preset),
        // seamlessly migrate to the new default (HIGH: JPEG + 75% quality + 1080p).
        val isDefaultPreset = rawPresetName == null || rawPresetName == CompressionPreset.HIGH.name || rawPresetName == "MEDIUM"
        val storedImageFormat = if (isDefaultPreset && rawFormatName == "WEBP") {
            ImageFormat.JPEG
        } else {
            enumValueOrDefault(rawFormatName, ImageFormat.JPEG)
        }
        val imageFormat = storedImageFormat.takeUnless {
            it == ImageFormat.AVIF && !imageFormatSupport.isAvifEncodingSupported
        } ?: ImageFormat.JPEG
        val imageQuality = if (rawPresetName == null) {
            75
        } else {
            rawQuality ?: 75
        }
        val videoResolution = if (isDefaultPreset && rawResName == "P720") {
            VideoResolution.P1080
        } else {
            enumValueOrDefault(rawResName, VideoResolution.P1080)
        }
        val videoCodec = enumValueOrDefault(
            preferences[PreferencesKeys.VIDEO_CODEC],
            VideoCodec.H265,
        )
        val compressedFolderPath = preferences[PreferencesKeys.COMPRESSED_FOLDER_PATH]
            ?: "Pictures/PandaGallery Compressed"
        val compressionOriginalAction = CompressionOriginalAction.valueOf(
            preferences[PreferencesKeys.COMPRESSION_ORIGINAL_ACTION] ?: CompressionOriginalAction.ASK.name
        )
        val compressionConcurrency = (preferences[PreferencesKeys.COMPRESSION_CONCURRENCY] ?: defaultCompressionConcurrency()).coerceIn(1, 10)
        val useGpu = preferences[PreferencesKeys.USE_GPU] ?: true
        val syncWifiOnly = preferences[PreferencesKeys.SYNC_WIFI_ONLY] ?: true
        val isDriveConnected = preferences[PreferencesKeys.IS_DRIVE_CONNECTED] ?: false
        val driveAccountEmail = preferences[PreferencesKeys.DRIVE_ACCOUNT_EMAIL]
        val backupFolderUri = preferences[PreferencesKeys.BACKUP_FOLDER_URI]
        val lastBackupAt = preferences[PreferencesKeys.LAST_BACKUP_AT]
        val backupSchedule = enumValueOrDefault(
            preferences[PreferencesKeys.BACKUP_SCHEDULE],
            BackupSchedule.MANUAL,
        )
        val backupSelectedAlbumPaths = preferences[PreferencesKeys.BACKUP_SELECTED_ALBUM_PATHS].orEmpty()
        val backupIncludeVideos = preferences[PreferencesKeys.BACKUP_INCLUDE_VIDEOS] ?: true
        val backupPreserveAlbumStructure = preferences[PreferencesKeys.BACKUP_PRESERVE_ALBUM_STRUCTURE] ?: true
        val backupChargingOnly = preferences[PreferencesKeys.BACKUP_CHARGING_ONLY] ?: false
        val smartIndexEnabled = preferences[PreferencesKeys.SMART_INDEX_ENABLED] ?: false
        val faceGroupingEnabled = preferences[PreferencesKeys.FACE_GROUPING_ENABLED] ?: false
        // Only whether a PIN exists travels through UI state; the hash itself is read on demand
        // by the lock screens (see folderLockPinHash / privateVaultPinHash).
        val hasFolderLockPin = preferences[PreferencesKeys.FOLDER_LOCK_PIN] != null
        val hasPrivateVaultPin = preferences[PreferencesKeys.PRIVATE_VAULT_PIN] != null
        val folderLockBiometricsEnabled = preferences[PreferencesKeys.FOLDER_LOCK_BIOMETRICS] ?: false
        val privateVaultBiometricsEnabled = preferences[PreferencesKeys.PRIVATE_VAULT_BIOMETRICS] ?: false
        val isPrivateAlbumConfigured = preferences[PreferencesKeys.IS_PRIVATE_ALBUM_CONFIGURED] ?: false
        val allowPrivateAlbumScreenshots =
            preferences[PreferencesKeys.ALLOW_PRIVATE_ALBUM_SCREENSHOTS] ?: false
        val privateVaultEncryption = preferences[PreferencesKeys.PRIVATE_VAULT_ENCRYPTION] ?: true
        val themeMode = ThemeMode.valueOf(
            preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        )
        val useDynamicColors = preferences[PreferencesKeys.USE_DYNAMIC_COLORS] ?: true
        val appIcon = preferences[PreferencesKeys.APP_ICON] ?: "icon_2"
        val mediaSortOrder = enumValueOrDefault(preferences[PreferencesKeys.MEDIA_SORT_ORDER], SortOrder.DATE_DESC)
        val perFolderSortEnabled = preferences[PreferencesKeys.PER_FOLDER_SORT_ENABLED] ?: true
        val folderSortOverrides =
            decodeFolderSortOverrides(preferences[PreferencesKeys.FOLDER_SORT_OVERRIDES].orEmpty())
        val albumGroupOverrides =
            decodeAlbumGroupOverrides(preferences[PreferencesKeys.ALBUM_GROUP_OVERRIDES].orEmpty())
        val mediaContentFilter = enumValueOrDefault(preferences[PreferencesKeys.MEDIA_CONTENT_FILTER], MediaContentFilter.ALL)
        val gridDensity = enumValueOrDefault(preferences[PreferencesKeys.GRID_DENSITY], GridDensity.NORMAL)
        val albumSortOrder = enumValueOrDefault(preferences[PreferencesKeys.ALBUM_SORT_ORDER], AlbumSortOrder.NEWEST)
        val albumGridDensity = enumValueOrDefault(preferences[PreferencesKeys.ALBUM_GRID_DENSITY], AlbumGridDensity.NORMAL)
        val showAlbumSize = preferences[PreferencesKeys.SHOW_ALBUM_SIZE] ?: false
        val showPicturesTab = preferences[PreferencesKeys.SHOW_PICTURES_TAB] ?: false
        val trashRetentionDays = (preferences[PreferencesKeys.TRASH_RETENTION_DAYS] ?: 30).coerceIn(1, 90)
        val slideshowIntervalSeconds = (preferences[PreferencesKeys.SLIDESHOW_INTERVAL_SECONDS] ?: 3).coerceIn(1, 30)
        val autoPlayVideos = preferences[PreferencesKeys.AUTO_PLAY_VIDEOS] ?: true
        val duplicateAutoResolve = preferences[PreferencesKeys.DUPLICATE_AUTO_RESOLVE] ?: false
        val duplicateKeepRule = enumValueOrDefault(preferences[PreferencesKeys.DUPLICATE_KEEP_RULE], DuplicateKeepRule.LARGEST)
        val autoPlayMotionPhotos = preferences[PreferencesKeys.AUTO_PLAY_MOTION_PHOTOS] ?: true
        val superHdrGainmapEnabled = preferences[PreferencesKeys.SUPER_HDR_GAINMAP_ENABLED] ?: true
        val convertHeifWhenSharing = preferences[PreferencesKeys.CONVERT_HEIF_WHEN_SHARING] ?: true
        val convertRawWhenSharing = preferences[PreferencesKeys.CONVERT_RAW_WHEN_SHARING] ?: true
        val removeLocationWhenSharing = preferences[PreferencesKeys.REMOVE_LOCATION_WHEN_SHARING] ?: false
        val galleryLabsUnlocked = preferences[PreferencesKeys.GALLERY_LABS_UNLOCKED] ?: false
        val adaptivePerceptualRateControl = preferences[PreferencesKeys.ADAPTIVE_PERCEPTUAL_RATE_CONTROL] ?: true
        val hardwareBitmapsDirectVram = preferences[PreferencesKeys.HARDWARE_BITMAPS_DIRECT_VRAM] ?: true
        val rotaryDialHaptics = preferences[PreferencesKeys.ROTARY_DIAL_HAPTICS] ?: true
        val thermalThrottleMode = enumValueOrDefault(
            preferences[PreferencesKeys.THERMAL_THROTTLE_MODE],
            ThermalThrottleMode.NORMAL
        )
        val safetyVaultRetentionDays = (preferences[PreferencesKeys.SAFETY_VAULT_RETENTION_DAYS] ?: 14).coerceIn(0, 90)
        val safetyVaultEnabled = preferences[PreferencesKeys.SAFETY_VAULT_ENABLED] ?: true
        val motionPhotoCompressionMode = enumValueOrDefault(
            preferences[PreferencesKeys.MOTION_PHOTO_COMPRESSION_MODE],
            MotionPhotoCompressionMode.PRESERVE_CLIP
        )
        val hardwareCodecAcceleration = preferences[PreferencesKeys.HARDWARE_CODEC_ACCELERATION] ?: true
        val dynamicVideoPreviewInGrid = preferences[PreferencesKeys.DYNAMIC_VIDEO_PREVIEW_IN_GRID] ?: true
        val filmstripPrecache = preferences[PreferencesKeys.FILMSTRIP_PRECACHE] ?: true
        val compressionStatusPlacement = enumValueOrDefault(
            preferences[PreferencesKeys.COMPRESSION_STATUS_PLACEMENT],
            CompressionStatusPlacement.ABOVE_BOTTOM_BAR,
        )

        return UserPreferences(
            compressionPreset = compressionPreset,
            imageFormat = imageFormat,
            imageQuality = imageQuality,
            videoResolution = videoResolution,
            videoCodec = videoCodec,
            compressedFolderPath = compressedFolderPath,
            compressionOriginalAction = compressionOriginalAction,
            compressionConcurrency = compressionConcurrency,
            useGpu = useGpu,
            syncWifiOnly = syncWifiOnly,
            isDriveConnected = isDriveConnected,
            driveAccountEmail = driveAccountEmail,
            backupFolderUri = backupFolderUri,
            lastBackupAt = lastBackupAt,
            backupSchedule = backupSchedule,
            backupSelectedAlbumPaths = backupSelectedAlbumPaths,
            backupIncludeVideos = backupIncludeVideos,
            backupPreserveAlbumStructure = backupPreserveAlbumStructure,
            backupChargingOnly = backupChargingOnly,
            smartIndexEnabled = smartIndexEnabled,
            faceGroupingEnabled = faceGroupingEnabled,
            hasFolderLockPin = hasFolderLockPin,
            hasPrivateVaultPin = hasPrivateVaultPin,
            folderLockBiometricsEnabled = folderLockBiometricsEnabled,
            privateVaultBiometricsEnabled = privateVaultBiometricsEnabled,
            isPrivateAlbumConfigured = isPrivateAlbumConfigured,
            allowPrivateAlbumScreenshots = allowPrivateAlbumScreenshots,
            privateVaultEncryption = privateVaultEncryption,
            themeMode = themeMode,
            useDynamicColors = useDynamicColors,
            appIcon = appIcon,
            mediaSortOrder = mediaSortOrder,
            perFolderSortEnabled = perFolderSortEnabled,
            folderSortOverrides = folderSortOverrides,
            albumGroupOverrides = albumGroupOverrides,
            mediaContentFilter = mediaContentFilter,
            gridDensity = gridDensity,
            albumSortOrder = albumSortOrder,
            albumGridDensity = albumGridDensity,
            showAlbumSize = showAlbumSize,
            showPicturesTab = showPicturesTab,
            trashRetentionDays = trashRetentionDays,
            slideshowIntervalSeconds = slideshowIntervalSeconds,
            autoPlayVideos = autoPlayVideos,
            duplicateAutoResolve = duplicateAutoResolve,
            duplicateKeepRule = duplicateKeepRule,
            autoPlayMotionPhotos = autoPlayMotionPhotos,
            superHdrGainmapEnabled = superHdrGainmapEnabled,
            convertHeifWhenSharing = convertHeifWhenSharing,
            convertRawWhenSharing = convertRawWhenSharing,
            removeLocationWhenSharing = removeLocationWhenSharing,
            galleryLabsUnlocked = galleryLabsUnlocked,
            adaptivePerceptualRateControl = adaptivePerceptualRateControl,
            hardwareBitmapsDirectVram = hardwareBitmapsDirectVram,
            rotaryDialHaptics = rotaryDialHaptics,
            thermalThrottleMode = thermalThrottleMode,
            safetyVaultRetentionDays = safetyVaultRetentionDays,
            safetyVaultEnabled = safetyVaultEnabled,
            motionPhotoCompressionMode = motionPhotoCompressionMode,
            hardwareCodecAcceleration = hardwareCodecAcceleration,
            dynamicVideoPreviewInGrid = dynamicVideoPreviewInGrid,
            filmstripPrecache = filmstripPrecache,
            compressionStatusPlacement = compressionStatusPlacement,
        )
    }

    suspend fun updateCompressionPreset(preset: CompressionPreset) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPRESSION_PRESET] = preset.name
            // The preset carries its own values (see CompressionPreset), so there is a single
            // source of truth for both writing them here and matching them back in
            // UserPreferences.matchedCompressionPreset.
            preferences[PreferencesKeys.IMAGE_FORMAT] = preset.imageFormat.name
            preferences[PreferencesKeys.IMAGE_QUALITY] = preset.imageQuality
            preferences[PreferencesKeys.VIDEO_RESOLUTION] = preset.videoResolution.name
            preferences[PreferencesKeys.VIDEO_CODEC] = preset.videoCodec.name
        }
    }

    suspend fun updateImageFormat(format: ImageFormat) {
        require(format != ImageFormat.AVIF || imageFormatSupport.isAvifEncodingSupported) {
            "AVIF encoding is not supported on this device"
        }
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IMAGE_FORMAT] = format.name
        }
    }

    /**
     * [ADAPTIVE_IMAGE_QUALITY] is stored verbatim; everything else is clamped to the encoder's
     * range. Clamping the sentinel too is what turned the Smart Auto preset into quality 1 —
     * the *worst* setting available — the moment anything wrote the quality back.
     */
    suspend fun updateImageQuality(quality: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IMAGE_QUALITY] = normalizeImageQuality(quality)
        }
    }

    suspend fun updateVideoResolution(resolution: VideoResolution) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIDEO_RESOLUTION] = resolution.name
        }
    }

    suspend fun updateVideoCodec(codec: VideoCodec) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIDEO_CODEC] = codec.name
        }
    }

    suspend fun updateCompressedFolderPath(path: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPRESSED_FOLDER_PATH] = path
                .trim()
                .trim('/')
                .ifBlank { "Pictures/PandaGallery Compressed" }
        }
    }

    suspend fun updateCompressionOriginalAction(action: CompressionOriginalAction) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPRESSION_ORIGINAL_ACTION] = action.name
        }
    }

    suspend fun updateCompressionConcurrency(concurrency: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPRESSION_CONCURRENCY] = concurrency.coerceIn(1, 10)
        }
    }

    suspend fun updateUseGpu(useGpu: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_GPU] = useGpu
        }
    }

    suspend fun updateSyncWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_WIFI_ONLY] = wifiOnly
        }
    }

    suspend fun updateDriveConnection(connected: Boolean, email: String?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DRIVE_CONNECTED] = connected
            if (email != null) {
                preferences[PreferencesKeys.DRIVE_ACCOUNT_EMAIL] = email
            } else {
                preferences.remove(PreferencesKeys.DRIVE_ACCOUNT_EMAIL)
            }
        }
    }

    suspend fun updateBackupFolder(uri: String?) {
        dataStore.edit { preferences ->
            if (uri == null) preferences.remove(PreferencesKeys.BACKUP_FOLDER_URI)
            else preferences[PreferencesKeys.BACKUP_FOLDER_URI] = uri
        }
    }

    suspend fun updateLastBackupAt(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_BACKUP_AT] = timestamp
        }
    }

    suspend fun updateBackupSchedule(schedule: BackupSchedule) {
        dataStore.edit { it[PreferencesKeys.BACKUP_SCHEDULE] = schedule.name }
    }

    suspend fun updateBackupSelectedAlbumPaths(paths: Set<String>) {
        dataStore.edit { it[PreferencesKeys.BACKUP_SELECTED_ALBUM_PATHS] = paths.mapTo(mutableSetOf()) { path -> path.trim('/') } }
    }

    suspend fun updateBackupIncludeVideos(include: Boolean) {
        dataStore.edit { it[PreferencesKeys.BACKUP_INCLUDE_VIDEOS] = include }
    }

    suspend fun updateBackupPreserveAlbumStructure(preserve: Boolean) {
        dataStore.edit { it[PreferencesKeys.BACKUP_PRESERVE_ALBUM_STRUCTURE] = preserve }
    }

    suspend fun updateBackupChargingOnly(chargingOnly: Boolean) {
        dataStore.edit { it[PreferencesKeys.BACKUP_CHARGING_ONLY] = chargingOnly }
    }

    suspend fun updateSmartIndexEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SMART_INDEX_ENABLED] = enabled }
    }

    suspend fun updateFaceGroupingEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FACE_GROUPING_ENABLED] = enabled }
    }

    // ============================================
    // Lock PINs
    //
    // The app keeps its own PINs rather than deferring to the device screen lock, so a folder's
    // PIN can differ from the phone's and from the Private folder's. Only hashes are stored, and
    // they are read through these functions instead of riding along in UserPreferences — a lock
    // screen needs the hash, no other screen ever should.
    // ============================================

    /** The stored hash for locked folders, or null when no folder PIN has been set. */
    suspend fun folderLockPinHash(): String? = dataStore.data.first()[PreferencesKeys.FOLDER_LOCK_PIN]

    /** The stored hash for the Private folder, or null when no PIN has been set. */
    suspend fun privateVaultPinHash(): String? = dataStore.data.first()[PreferencesKeys.PRIVATE_VAULT_PIN]

    /**
     * Sets or replaces the folder-lock PIN.
     *
     * Hashing happens at the call site so a plaintext PIN never reaches this layer; clearing the
     * failure counters is part of the same write, because a PIN that was just chosen should not
     * start life inside a lockout left over from the old one.
     */
    suspend fun setFolderLockPinHash(hash: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FOLDER_LOCK_PIN] = hash
            preferences.remove(PreferencesKeys.FOLDER_PIN_FAILURES)
            preferences.remove(PreferencesKeys.FOLDER_PIN_LAST_FAILURE)
        }
    }

    suspend fun setPrivateVaultPinHash(hash: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRIVATE_VAULT_PIN] = hash
            preferences.remove(PreferencesKeys.VAULT_PIN_FAILURES)
            preferences.remove(PreferencesKeys.VAULT_PIN_LAST_FAILURE)
        }
    }

    /**
     * Removes the folder-lock PIN. Biometric unlocking goes with it: it was a shortcut past this
     * PIN, so leaving it on would turn "no PIN set" into "fingerprint is the only credential".
     */
    suspend fun clearFolderLockPin() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.FOLDER_LOCK_PIN)
            preferences.remove(PreferencesKeys.FOLDER_LOCK_BIOMETRICS)
            preferences.remove(PreferencesKeys.FOLDER_PIN_FAILURES)
            preferences.remove(PreferencesKeys.FOLDER_PIN_LAST_FAILURE)
        }
    }

    suspend fun clearPrivateVaultPin() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.PRIVATE_VAULT_PIN)
            preferences.remove(PreferencesKeys.PRIVATE_VAULT_BIOMETRICS)
            preferences.remove(PreferencesKeys.VAULT_PIN_FAILURES)
            preferences.remove(PreferencesKeys.VAULT_PIN_LAST_FAILURE)
        }
    }

    suspend fun updateFolderLockBiometrics(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FOLDER_LOCK_BIOMETRICS] = enabled }
    }

    suspend fun updatePrivateVaultBiometrics(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.PRIVATE_VAULT_BIOMETRICS] = enabled }
    }

    /**
     * The wrong-guess history behind the lockout, kept in storage rather than in memory so
     * force-stopping the app cannot hand an attacker a fresh set of attempts.
     */
    fun pinAttemptsFlow(lock: PinLock): Flow<PinAttempts> = dataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .map { preferences ->
            PinAttempts(
                failedAttempts = preferences[lock.failureKey] ?: 0,
                lastFailureAt = preferences[lock.lastFailureKey] ?: 0L,
            )
        }

    suspend fun recordPinFailure(lock: PinLock, at: Long) {
        dataStore.edit { preferences ->
            preferences[lock.failureKey] = (preferences[lock.failureKey] ?: 0) + 1
            preferences[lock.lastFailureKey] = at
        }
    }

    suspend fun clearPinFailures(lock: PinLock) {
        dataStore.edit { preferences ->
            preferences.remove(lock.failureKey)
            preferences.remove(lock.lastFailureKey)
        }
    }

    /** Which of the two locks a call refers to. */
    enum class PinLock {
        FOLDERS,
        PRIVATE_VAULT,
        ;

        internal val failureKey: Preferences.Key<Int>
            get() = when (this) {
                FOLDERS -> PreferencesKeys.FOLDER_PIN_FAILURES
                PRIVATE_VAULT -> PreferencesKeys.VAULT_PIN_FAILURES
            }

        internal val lastFailureKey: Preferences.Key<Long>
            get() = when (this) {
                FOLDERS -> PreferencesKeys.FOLDER_PIN_LAST_FAILURE
                PRIVATE_VAULT -> PreferencesKeys.VAULT_PIN_LAST_FAILURE
            }
    }

    suspend fun updateAllowPrivateAlbumScreenshots(allow: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALLOW_PRIVATE_ALBUM_SCREENSHOTS] = allow
        }
    }

    suspend fun updateIsPrivateAlbumConfigured(configured: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_PRIVATE_ALBUM_CONFIGURED] = configured
        }
    }

    suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun updateUseDynamicColors(useDynamic: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS] = useDynamic
        }
    }
 
    suspend fun updateAppIcon(appIcon: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_ICON] = appIcon
        }
    }

    suspend fun updateMediaSortOrder(order: SortOrder) {
        dataStore.edit { it[PreferencesKeys.MEDIA_SORT_ORDER] = order.name }
    }

    suspend fun updatePrivateVaultEncryption(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.PRIVATE_VAULT_ENCRYPTION] = enabled }
    }

    suspend fun updatePerFolderSortEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.PER_FOLDER_SORT_ENABLED] = enabled }
    }

    /**
     * Remembers one folder's sort order. Read-modify-write inside a single `edit` so two folders
     * sorted in quick succession cannot clobber each other's entry.
     */
    suspend fun updateFolderSortOrder(bucketId: Long, order: SortOrder) {
        dataStore.edit { preferences ->
            val current = decodeFolderSortOverrides(preferences[PreferencesKeys.FOLDER_SORT_OVERRIDES].orEmpty())
            preferences[PreferencesKeys.FOLDER_SORT_OVERRIDES] =
                encodeFolderSortOverrides(current + (bucketId to order))
        }
    }

    /** Sends one folder back to the library-wide default sort. */
    suspend fun clearFolderSortOrder(bucketId: Long) {
        dataStore.edit { preferences ->
            val remaining = decodeFolderSortOverrides(preferences[PreferencesKeys.FOLDER_SORT_OVERRIDES].orEmpty()) - bucketId
            if (remaining.isEmpty()) {
                preferences.remove(PreferencesKeys.FOLDER_SORT_OVERRIDES)
            } else {
                preferences[PreferencesKeys.FOLDER_SORT_OVERRIDES] = encodeFolderSortOverrides(remaining)
            }
        }
    }

    /** Sends every folder back to the library-wide default sort. */
    suspend fun clearFolderSortOverrides() {
        dataStore.edit { it.remove(PreferencesKeys.FOLDER_SORT_OVERRIDES) }
    }

    suspend fun updateMediaContentFilter(filter: MediaContentFilter) {
        dataStore.edit { it[PreferencesKeys.MEDIA_CONTENT_FILTER] = filter.name }
    }

    suspend fun updateGridDensity(density: GridDensity) {
        dataStore.edit { it[PreferencesKeys.GRID_DENSITY] = density.name }
    }

    suspend fun updateAlbumSortOrder(order: AlbumSortOrder) {
        dataStore.edit { it[PreferencesKeys.ALBUM_SORT_ORDER] = order.name }
    }

    suspend fun updateShowAlbumSize(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_ALBUM_SIZE] = show }
    }

    suspend fun updateShowPicturesTab(show: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHOW_PICTURES_TAB] = show }
    }

    suspend fun updateAlbumGridDensity(density: AlbumGridDensity) {
        dataStore.edit { it[PreferencesKeys.ALBUM_GRID_DENSITY] = density.name }
    }

    suspend fun updateTrashRetentionDays(days: Int) {
        dataStore.edit { it[PreferencesKeys.TRASH_RETENTION_DAYS] = days.coerceIn(1, 90) }
    }

    suspend fun updateSlideshowIntervalSeconds(seconds: Int) {
        dataStore.edit { it[PreferencesKeys.SLIDESHOW_INTERVAL_SECONDS] = seconds.coerceIn(1, 30) }
    }

    suspend fun updateAutoPlayVideos(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AUTO_PLAY_VIDEOS] = enabled }
    }

    suspend fun updateDuplicateAutoResolve(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DUPLICATE_AUTO_RESOLVE] = enabled }
    }

    suspend fun updateDuplicateKeepRule(rule: DuplicateKeepRule) {
        dataStore.edit { it[PreferencesKeys.DUPLICATE_KEEP_RULE] = rule.name }
    }

    suspend fun updateAutoPlayMotionPhotos(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AUTO_PLAY_MOTION_PHOTOS] = enabled }
    }

    suspend fun updateSuperHdrGainmapEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SUPER_HDR_GAINMAP_ENABLED] = enabled }
    }

    suspend fun updateConvertHeifWhenSharing(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CONVERT_HEIF_WHEN_SHARING] = enabled }
    }

    suspend fun updateConvertRawWhenSharing(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CONVERT_RAW_WHEN_SHARING] = enabled }
    }

    suspend fun updateRemoveLocationWhenSharing(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.REMOVE_LOCATION_WHEN_SHARING] = enabled }
    }

    suspend fun updateGalleryLabsUnlocked(unlocked: Boolean) {
        dataStore.edit { it[PreferencesKeys.GALLERY_LABS_UNLOCKED] = unlocked }
    }

    suspend fun updateAdaptivePerceptualRateControl(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.ADAPTIVE_PERCEPTUAL_RATE_CONTROL] = enabled }
    }

    suspend fun updateHardwareBitmapsDirectVram(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HARDWARE_BITMAPS_DIRECT_VRAM] = enabled }
    }

    suspend fun updateRotaryDialHaptics(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.ROTARY_DIAL_HAPTICS] = enabled }
    }

    suspend fun updateThermalThrottleMode(mode: ThermalThrottleMode) {
        dataStore.edit { it[PreferencesKeys.THERMAL_THROTTLE_MODE] = mode.name }
    }

    suspend fun updateSafetyVaultRetentionDays(days: Int) {
        dataStore.edit { it[PreferencesKeys.SAFETY_VAULT_RETENTION_DAYS] = days.coerceIn(0, 90) }
    }

    suspend fun updateSafetyVaultEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SAFETY_VAULT_ENABLED] = enabled }
    }

    suspend fun updateMotionPhotoCompressionMode(mode: MotionPhotoCompressionMode) {
        dataStore.edit { it[PreferencesKeys.MOTION_PHOTO_COMPRESSION_MODE] = mode.name }
    }

    suspend fun updateHardwareCodecAcceleration(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HARDWARE_CODEC_ACCELERATION] = enabled }
    }

    suspend fun updateDynamicVideoPreviewInGrid(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DYNAMIC_VIDEO_PREVIEW_IN_GRID] = enabled }
    }

    suspend fun updateFilmstripPrecache(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FILMSTRIP_PRECACHE] = enabled }
    }

    suspend fun updateAlbumGroup(albumId: Long, groupName: String?) {
        dataStore.edit { preferences ->
            val current = decodeAlbumGroupOverrides(preferences[PreferencesKeys.ALBUM_GROUP_OVERRIDES].orEmpty()).toMutableMap()
            if (groupName.isNullOrBlank()) {
                current.remove(albumId)
            } else {
                current[albumId] = groupName.trim()
            }
            preferences[PreferencesKeys.ALBUM_GROUP_OVERRIDES] = encodeAlbumGroupOverrides(current)
        }
    }

    suspend fun updateAlbumsGroup(albumIds: Set<Long>, groupName: String?) {
        dataStore.edit { preferences ->
            val current = decodeAlbumGroupOverrides(preferences[PreferencesKeys.ALBUM_GROUP_OVERRIDES].orEmpty()).toMutableMap()
            for (id in albumIds) {
                if (groupName.isNullOrBlank()) {
                    current.remove(id)
                } else {
                    current[id] = groupName.trim()
                }
            }
            preferences[PreferencesKeys.ALBUM_GROUP_OVERRIDES] = encodeAlbumGroupOverrides(current)
        }
    }

    suspend fun renameAlbumGroup(oldName: String, newName: String) {
        if (oldName.isBlank() || newName.isBlank() || oldName == newName) return
        dataStore.edit { preferences ->
            val current = decodeAlbumGroupOverrides(preferences[PreferencesKeys.ALBUM_GROUP_OVERRIDES].orEmpty()).toMutableMap()
            for ((id, grp) in current.toMap()) {
                if (grp.equals(oldName.trim(), ignoreCase = true)) {
                    current[id] = newName.trim()
                }
            }
            preferences[PreferencesKeys.ALBUM_GROUP_OVERRIDES] = encodeAlbumGroupOverrides(current)
        }
    }

    suspend fun dissolveAlbumGroup(groupName: String) {
        if (groupName.isBlank()) return
        dataStore.edit { preferences ->
            val current = decodeAlbumGroupOverrides(preferences[PreferencesKeys.ALBUM_GROUP_OVERRIDES].orEmpty()).toMutableMap()
            val iterator = current.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.equals(groupName.trim(), ignoreCase = true)) {
                    iterator.remove()
                }
            }
            preferences[PreferencesKeys.ALBUM_GROUP_OVERRIDES] = encodeAlbumGroupOverrides(current)
        }
    }

    suspend fun updateCompressionStatusPlacement(placement: CompressionStatusPlacement) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPRESSION_STATUS_PLACEMENT] = placement.name
        }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
