package com.pandagallery.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.backup.FolderBackupRepository
import com.pandagallery.app.data.backup.BackupScheduler
import com.pandagallery.app.data.backup.BackupWorker
import com.pandagallery.app.data.compression.ImageFormatSupport
import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.vault.VaultEncryptionWorker
import androidx.work.WorkManager
import com.pandagallery.app.data.local.PreferencesDataSource.PinLock
import com.pandagallery.app.data.security.PinLockManager
import com.pandagallery.app.data.security.PinVerification
import com.pandagallery.app.data.repository.MediaRepository
import com.pandagallery.app.data.smart.SmartIndexRepository
import com.pandagallery.app.data.smart.face.FaceModelState
import com.pandagallery.app.data.cleanup.DuplicateAutoResolveWorker
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.data.compression.SafetyVaultRepository
import com.pandagallery.app.domain.model.DuplicateKeepRule
import com.pandagallery.app.domain.compression.CompressionCalibration
import com.pandagallery.app.domain.compression.CompressionEstimator
import com.pandagallery.app.data.smart.SmartIndexScheduler
import com.pandagallery.app.domain.model.*
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    private val privateVaultRepository: com.pandagallery.app.data.vault.PrivateVaultRepository,
    private val pinLocks: PinLockManager,
    private val folderBackupRepository: FolderBackupRepository,
    private val backupScheduler: BackupScheduler,
    private val mediaRepository: MediaRepository,
    private val smartIndexRepository: SmartIndexRepository,
    private val smartIndexScheduler: SmartIndexScheduler,
    compressionQueueRepository: CompressionQueueRepository,
    private val safetyVaultRepository: SafetyVaultRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    imageFormatSupport: ImageFormatSupport,
) : ViewModel() {

    private val _backupStatus = MutableStateFlow<String?>(null)
    private val _smartIndexStatus = MutableStateFlow<String?>(null)
    private val _faceModelState = MutableStateFlow<FaceModelState?>(null)
    /**
     * A stand-in photo used to preview what each preset would do. Derived from the user's
     * own library so the numbers mean something to them, rather than a hardcoded example.
     */
    private val representativePhoto = mediaRepository.getAllMedia()
        .map(CompressionEstimator::representativeImage)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val calibration = compressionQueueRepository.calibration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompressionCalibration.None)
    private val albums = mediaRepository.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val backupWorkStatus = backupScheduler.observeManualWork()
        .map { work -> work.firstOrNull()?.toReadableBackupStatus() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Kept beside the main state rather than inside it: both are read on demand from the vault
     * directory, and folding them into the big combine would recompute every estimate whenever a
     * conversion ticked.
     */
    private val _vaultStorageBytes = MutableStateFlow(0L)

    /**
     * Size of the compression Safety Vault — the originals kept so an in-place compression can be
     * undone. Read from disk rather than summed from Room so orphaned copies are visible too.
     */
    private val _safetyVaultBytes = MutableStateFlow(0L)
    val safetyVaultBytes: StateFlow<Long> = _safetyVaultBytes.asStateFlow()
    val safetyVaultCount: StateFlow<Int> = safetyVaultRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Outcome of the last manual sweep, shown on the storage row itself. */
    private val _safetyVaultStatus = MutableStateFlow<String?>(null)
    val safetyVaultStatus: StateFlow<String?> = _safetyVaultStatus.asStateFlow()
    val vaultStorageBytes: StateFlow<Long> = _vaultStorageBytes.asStateFlow()
    /** Progress of the conversion worker, so Settings can report it while it runs. */
    val vaultConversion: StateFlow<VaultConversion> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(VaultEncryptionWorker.UNIQUE_WORK)
        .map { infos ->
            val info = infos.lastOrNull()
            when (info?.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> VaultConversion(
                    done = info.progress.getInt(VaultEncryptionWorker.PROGRESS_DONE, 0),
                    total = info.progress.getInt(VaultEncryptionWorker.PROGRESS_TOTAL, 0),
                    isRunning = true,
                )
                WorkInfo.State.FAILED ->
                    VaultConversion(isRunning = false, error = "the conversion did not finish")
                else -> VaultConversion(isRunning = false)
            }
        }
        .onEach { if (!it.isRunning) refreshVaultStorage() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultConversion())

    init {
        // Probing the model loads it once, up front, so the People toggle can explain
        // itself instead of silently doing nothing when the asset is absent.
        viewModelScope.launch { _faceModelState.value = smartIndexRepository.faceModelState() }
        refreshVaultStorage()
        refreshSafetyVaultStorage()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesDataSource.userPreferencesFlow,
        _backupStatus,
        backupWorkStatus,
        albums,
        _smartIndexStatus,
        _faceModelState,
        representativePhoto,
        calibration,
    ) { values ->
            @Suppress("UNCHECKED_CAST")
            val preferences = values[0] as UserPreferences
            val localStatus = values[1] as String?
            val workStatus = values[2] as String?
            val albumList = values[3] as List<Album>
            val smartIndexStatus = values[4] as String?
            val faceModel = values[5] as FaceModelState?
            val typicalPhoto = values[6] as CompressionEstimator.Input?
            val accuracy = values[7] as CompressionCalibration
            SettingsUiState(
                preferences = preferences,
                isLoading = false,
                backupStatus = workStatus ?: localStatus,
                isAvifEncodingSupported = imageFormatSupport.isAvifEncodingSupported,
                albums = albumList,
                smartIndexStatus = smartIndexStatus,
                faceModelStatus = faceModel?.toReadableStatus(),
                isFaceGroupingAvailable = faceModel is FaceModelState.Ready,
                currentEstimate = typicalPhoto?.let { photo ->
                    CompressionEstimator.estimate(photo, preferences, accuracy)
                },
                presetEstimates = typicalPhoto?.let { photo ->
                    CompressionPreset.entries.associateWith { preset ->
                        // Estimate against the preset's own values, not the live
                        // preferences, so each row previews what picking it would do.
                        CompressionEstimator.estimate(photo, preferences.applying(preset), accuracy)
                    }
                }.orEmpty(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(isLoading = false),
        )

    fun updateCompressionPreset(preset: CompressionPreset) {
        viewModelScope.launch {
            preferencesDataSource.updateCompressionPreset(preset)
        }
    }

    fun updateImageFormat(format: ImageFormat) {
        viewModelScope.launch {
            preferencesDataSource.updateImageFormat(format)
        }
    }

    fun updateImageQuality(quality: Int) {
        viewModelScope.launch {
            preferencesDataSource.updateImageQuality(quality)
        }
    }

    fun updateVideoResolution(resolution: VideoResolution) {
        viewModelScope.launch {
            preferencesDataSource.updateVideoResolution(resolution)
        }
    }

    fun updateVideoCodec(codec: VideoCodec) {
        viewModelScope.launch {
            preferencesDataSource.updateVideoCodec(codec)
        }
    }

    fun updateCompressedFolderPath(path: String) {
        viewModelScope.launch {
            preferencesDataSource.updateCompressedFolderPath(path)
        }
    }

    fun updateCompressionOriginalAction(action: CompressionOriginalAction) {
        viewModelScope.launch {
            preferencesDataSource.updateCompressionOriginalAction(action)
        }
    }

    fun updateCompressionConcurrency(concurrency: Int) {
        viewModelScope.launch {
            preferencesDataSource.updateCompressionConcurrency(concurrency)
        }
    }

    fun updateUseGpu(useGpu: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.updateUseGpu(useGpu)
        }
    }

    fun updateSyncWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.updateSyncWifiOnly(wifiOnly)
            rescheduleBackup { copy(syncWifiOnly = wifiOnly) }
        }
    }

    fun updateBackupSchedule(schedule: BackupSchedule) = viewModelScope.launch {
        preferencesDataSource.updateBackupSchedule(schedule)
        rescheduleBackup { copy(backupSchedule = schedule) }
    }

    fun updateBackupSelectedAlbumPaths(paths: Set<String>) = viewModelScope.launch {
        preferencesDataSource.updateBackupSelectedAlbumPaths(paths)
    }

    fun updateBackupIncludeVideos(include: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateBackupIncludeVideos(include)
    }

    fun updateBackupPreserveAlbumStructure(preserve: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateBackupPreserveAlbumStructure(preserve)
    }

    fun updateBackupChargingOnly(chargingOnly: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateBackupChargingOnly(chargingOnly)
        rescheduleBackup { copy(backupChargingOnly = chargingOnly) }
    }

    fun updateDuplicateAutoResolve(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateDuplicateAutoResolve(enabled)
        if (enabled) {
            DuplicateAutoResolveWorker.enable(context)
        } else {
            DuplicateAutoResolveWorker.disable(context)
        }
    }

    fun updateDuplicateKeepRule(rule: DuplicateKeepRule) = viewModelScope.launch {
        preferencesDataSource.updateDuplicateKeepRule(rule)
    }

    /**
     * The library-wide photo sort. Folders with their own choice keep it — clearing those is
     * [clearFolderSortOverrides], so changing the default never silently discards them.
     */
    fun updateMediaSortOrder(order: SortOrder) = viewModelScope.launch {
        preferencesDataSource.updateMediaSortOrder(order)
    }

    /**
     * Switches vault encryption and rewrites what is already stored to match.
     *
     * The preference is written first so anything imported mid-conversion is stored the new way,
     * and the conversion itself is per item, so an interruption leaves a mixed vault that still
     * opens rather than a broken one.
     */
    fun updatePrivateVaultEncryption(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updatePrivateVaultEncryption(enabled)
        // Handed to WorkManager rather than run here: this rewrites the only copy of the user's
        // private media, and leaving Settings must not abandon it half-done.
        VaultEncryptionWorker.enqueue(context)
    }

    fun refreshVaultStorage() = viewModelScope.launch {
        _vaultStorageBytes.value = privateVaultRepository.storageBytes()
    }

    fun updateShowAlbumSize(show: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateShowAlbumSize(show)
    }

    fun updateShowPicturesTab(show: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateShowPicturesTab(show)
    }

    fun updateAlbumSortOrder(order: AlbumSortOrder) = viewModelScope.launch {
        preferencesDataSource.updateAlbumSortOrder(order)
    }

    fun updatePerFolderSortEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updatePerFolderSortEnabled(enabled)
    }

    fun clearFolderSortOverrides() = viewModelScope.launch {
        preferencesDataSource.clearFolderSortOverrides()
    }

    fun updateAutoPlayVideos(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateAutoPlayVideos(enabled)
    }

    fun updateSmartIndexEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateSmartIndexEnabled(enabled)
        if (enabled) {
            smartIndexScheduler.enable()
            _smartIndexStatus.value = "Automatic indexing scheduled"
        } else {
            smartIndexScheduler.disable()
            preferencesDataSource.updateFaceGroupingEnabled(false)
            smartIndexRepository.clear()
            _smartIndexStatus.value = "On-device index cleared"
        }
    }

    fun updateFaceGroupingEnabled(enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            val model = smartIndexRepository.faceModelState()
            _faceModelState.value = model
            if (model !is FaceModelState.Ready) {
                // Refuse rather than enable a feature that cannot produce correct groups.
                _smartIndexStatus.value = model.toReadableStatus()
                return@launch
            }
            preferencesDataSource.updateFaceGroupingEnabled(true)
            smartIndexScheduler.enable()
            _smartIndexStatus.value = "People indexing scheduled"
        } else {
            preferencesDataSource.updateFaceGroupingEnabled(false)
            smartIndexRepository.clearFaces()
            _smartIndexStatus.value = "People index cleared"
        }
    }

    fun buildSmartIndex() = viewModelScope.launch {
        if (!uiState.value.preferences.smartIndexEnabled) return@launch
        _smartIndexStatus.value = "Preparing on-device index..."
        try {
            // Unlike the background worker, this runs in the foreground for as long as the
            // screen stays open rather than inside a time-limited WorkManager execution, so the
            // per-run item budget that protects the worker would only truncate this one early.
            val outcome = smartIndexRepository.indexAll(
                includeFaces = uiState.value.preferences.faceGroupingEnabled,
                maxFullPassesPerRun = Int.MAX_VALUE,
            ) { completed, total ->
                _smartIndexStatus.value = "Indexing $completed of $total"
            }
            // Unreadable files are reported rather than silently dropped, so a library that
            // never fully indexes is visible instead of just looking finished.
            _smartIndexStatus.value = if (outcome.skipped > 0) {
                "Smart index up to date · ${outcome.skipped} item${if (outcome.skipped == 1) "" else "s"} could not be read"
            } else {
                "Smart index is up to date"
            }
        } catch (error: com.google.mlkit.common.MlKitException) {
            _smartIndexStatus.value = error.message ?: "On-device indexing failed"
        } catch (error: java.io.IOException) {
            _smartIndexStatus.value = error.message ?: "Unable to read media for indexing"
        }
    }

    // ============================================
    // Lock PINs
    // ============================================

    fun setFolderLockPin(pin: String) = viewModelScope.launch {
        pinLocks.setPin(PinLock.FOLDERS, pin)
    }

    fun setPrivateVaultPin(pin: String) = viewModelScope.launch {
        pinLocks.setPin(PinLock.PRIVATE_VAULT, pin)
    }

    /**
     * Removing a lock is itself a privileged action, so the caller proves the current PIN first —
     * see the Settings screen's remove flow. This only performs the removal.
     */
    /**
     * Removes the folder-lock PIN, and unlocks the folders that were relying on it — a lock with no
     * PIN behind it can neither be opened nor lifted.
     */
    fun clearFolderLockPin() = viewModelScope.launch {
        pinLocks.clearPin(PinLock.FOLDERS)
        mediaRepository.clearFolderLocksWithoutPin()
    }

    fun clearPrivateVaultPin() = viewModelScope.launch { pinLocks.clearPin(PinLock.PRIVATE_VAULT) }

    suspend fun verifyFolderLockPin(pin: String): PinVerification = pinLocks.verify(PinLock.FOLDERS, pin)

    suspend fun verifyPrivateVaultPin(pin: String): PinVerification =
        pinLocks.verify(PinLock.PRIVATE_VAULT, pin)

    suspend fun folderLockoutRemainingMillis(): Long = pinLocks.lockoutRemainingMillis(PinLock.FOLDERS)

    suspend fun privateVaultLockoutRemainingMillis(): Long =
        pinLocks.lockoutRemainingMillis(PinLock.PRIVATE_VAULT)

    fun updateFolderLockBiometrics(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateFolderLockBiometrics(enabled)
    }

    fun updatePrivateVaultBiometrics(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updatePrivateVaultBiometrics(enabled)
    }

    fun updateAllowPrivateAlbumScreenshots(allow: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.updateAllowPrivateAlbumScreenshots(allow)
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            preferencesDataSource.updateThemeMode(themeMode)
        }
    }

    fun updateUseDynamicColors(useDynamic: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.updateUseDynamicColors(useDynamic)
        }
    }

    fun updateTrashRetentionDays(days: Int) {
        viewModelScope.launch { preferencesDataSource.updateTrashRetentionDays(days) }
    }

    fun updateSlideshowIntervalSeconds(seconds: Int) {
        viewModelScope.launch { preferencesDataSource.updateSlideshowIntervalSeconds(seconds) }
    }

    /** Wipes the on-device search index without turning indexing off. */
    fun clearSmartIndex() = viewModelScope.launch {
        smartIndexRepository.clear()
        _smartIndexStatus.value = "On-device index cleared"
    }

    fun disconnectBackupFolder() = viewModelScope.launch {
        preferencesDataSource.updateBackupFolder(null)
        backupScheduler.updateSchedule(uiState.value.preferences.copy(backupFolderUri = null))
        _backupStatus.value = "Backup folder disconnected"
    }

    fun updateAppIcon(appIcon: String) {
        viewModelScope.launch {
            preferencesDataSource.updateAppIcon(appIcon)
        }
    }

    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                folderBackupRepository.persistAccess(uri)
                preferencesDataSource.updateBackupFolder(uri.toString())
            }.onSuccess {
                _backupStatus.value = "Backup folder connected"
                backupScheduler.updateSchedule(uiState.value.preferences.copy(backupFolderUri = uri.toString()))
            }.onFailure {
                _backupStatus.value = it.message ?: "Unable to use that folder"
            }
        }
    }

    fun backupNow() {
        val preferences = uiState.value.preferences
        if (preferences.backupFolderUri == null) return
        _backupStatus.value = "Backup queued"
        backupScheduler.runNow(preferences)
    }

    fun restoreBackup() {
        val rootUri = uiState.value.preferences.backupFolderUri ?: return
        viewModelScope.launch {
            _backupStatus.value = "Restoring backup..."
            try {
                val result = folderBackupRepository.restoreAll(rootUri)
                _backupStatus.value = "${result.restored} items restored${if (result.failures.isEmpty()) "" else ", ${result.failures.size} failed"}"
            } catch (error: java.io.IOException) {
                _backupStatus.value = error.message ?: "Restore failed"
            } catch (error: SecurityException) {
                _backupStatus.value = "Backup folder access was lost"
            }
        }
    }

    private suspend fun rescheduleBackup(update: UserPreferences.() -> UserPreferences) {
        backupScheduler.updateSchedule(uiState.value.preferences.update())
    }

    fun setAutoPlayMotionPhotos(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateAutoPlayMotionPhotos(enabled)
    }

    fun setSuperHdrGainmapEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateSuperHdrGainmapEnabled(enabled)
    }

    fun setConvertHeifWhenSharing(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateConvertHeifWhenSharing(enabled)
    }

    fun setConvertRawWhenSharing(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateConvertRawWhenSharing(enabled)
    }

    fun setRemoveLocationWhenSharing(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateRemoveLocationWhenSharing(enabled)
    }

    fun setGalleryLabsUnlocked(unlocked: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateGalleryLabsUnlocked(unlocked)
    }

    fun setAdaptivePerceptualRateControl(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateAdaptivePerceptualRateControl(enabled)
    }

    fun setHardwareBitmapsDirectVram(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateHardwareBitmapsDirectVram(enabled)
    }

    fun setRotaryDialHaptics(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateRotaryDialHaptics(enabled)
    }

    fun setThermalThrottleMode(mode: ThermalThrottleMode) = viewModelScope.launch {
        preferencesDataSource.updateThermalThrottleMode(mode)
    }

    fun setSafetyVaultRetentionDays(days: Int) = viewModelScope.launch {
        preferencesDataSource.updateSafetyVaultRetentionDays(days)
        // Shortening the window should reclaim space from compressions already done, not just
        // future ones, so re-date the existing copies and sweep straight away.
        safetyVaultRepository.applyRetentionPolicy(days)
        safetyVaultRepository.pruneExpired()
        refreshSafetyVaultStorage()
    }

    fun setSafetyVaultEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateSafetyVaultEnabled(enabled)
    }

    fun refreshSafetyVaultStorage() = viewModelScope.launch {
        _safetyVaultStatus.value = null
        _safetyVaultBytes.value = safetyVaultRepository.storageBytes()
    }

    /** Deletes every kept original. Reverting a compressed photo stops working afterwards. */
    fun clearSafetyVault() = viewModelScope.launch {
        val reclaimed = safetyVaultRepository.clearAll()
        _safetyVaultBytes.value = safetyVaultRepository.storageBytes()
        _safetyVaultStatus.value = if (reclaimed > 0L) {
            "Cleared — ${formatFileSize(reclaimed)} reclaimed"
        } else {
            "Already empty — nothing to reclaim"
        }
    }

    fun setMotionPhotoCompressionMode(mode: MotionPhotoCompressionMode) = viewModelScope.launch {
        preferencesDataSource.updateMotionPhotoCompressionMode(mode)
    }

    fun setHardwareCodecAcceleration(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateHardwareCodecAcceleration(enabled)
    }

    fun setDynamicVideoPreviewInGrid(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateDynamicVideoPreviewInGrid(enabled)
    }

    fun setFilmstripPrecache(enabled: Boolean) = viewModelScope.launch {
        preferencesDataSource.updateFilmstripPrecache(enabled)
    }

    fun updateCompressionStatusPlacement(placement: CompressionStatusPlacement) = viewModelScope.launch {
        preferencesDataSource.updateCompressionStatusPlacement(placement)
    }

    fun resetGalleryLabsDefaults() = viewModelScope.launch {
        preferencesDataSource.updateAdaptivePerceptualRateControl(true)
        preferencesDataSource.updateHardwareBitmapsDirectVram(true)
        preferencesDataSource.updateRotaryDialHaptics(true)
        preferencesDataSource.updateSuperHdrGainmapEnabled(true)
        preferencesDataSource.updateConvertHeifWhenSharing(true)
        preferencesDataSource.updateConvertRawWhenSharing(true)
        preferencesDataSource.updateThermalThrottleMode(ThermalThrottleMode.NORMAL)
        preferencesDataSource.updateSafetyVaultRetentionDays(14)
        preferencesDataSource.updateSafetyVaultEnabled(true)
        preferencesDataSource.updateMotionPhotoCompressionMode(MotionPhotoCompressionMode.PRESERVE_CLIP)
        preferencesDataSource.updateHardwareCodecAcceleration(true)
        preferencesDataSource.updateDynamicVideoPreviewInGrid(true)
        preferencesDataSource.updateFilmstripPrecache(true)
    }
}

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = false,
    val backupStatus: String? = null,
    val isAvifEncodingSupported: Boolean = false,
    val albums: List<Album> = emptyList(),
    val smartIndexStatus: String? = null,
    val faceModelStatus: String? = null,
    val isFaceGroupingAvailable: Boolean = false,
    /** Predicted before/after for a typical photo under the settings as they stand. */
    val currentEstimate: CompressionEstimator.Estimate? = null,
    /** Predicted before/after for a typical photo in this library, per preset. */
    val presetEstimates: Map<CompressionPreset, CompressionEstimator.Estimate> = emptyMap(),
)

/** Progress of re-writing the vault after the encryption setting changed. */
data class VaultConversion(
    val done: Int = 0,
    val total: Int = 0,
    val isRunning: Boolean = false,
    val error: String? = null,
)

/** The preferences that picking [preset] would produce, without persisting anything. */
private fun UserPreferences.applying(preset: CompressionPreset) = copy(
    compressionPreset = preset,
    imageFormat = preset.imageFormat,
    imageQuality = preset.imageQuality,
    videoResolution = preset.videoResolution,
    videoCodec = preset.videoCodec,
)

private fun FaceModelState?.toReadableStatus(): String = when (this) {
    is FaceModelState.Ready -> "Face recognition model ready (${dimensions}-D embeddings)"
    FaceModelState.Missing ->
        "Face recognition model not installed - run scripts/download_face_model.sh and rebuild"
    is FaceModelState.Failed -> "Face recognition model unavailable: $reason"
    null -> "Checking face recognition model..."
}

private fun WorkInfo.toReadableBackupStatus(): String? = when (state) {
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Backup queued"
    WorkInfo.State.RUNNING -> "Backing up media..."
    WorkInfo.State.SUCCEEDED -> {
        val copied = outputData.getInt(BackupWorker.OUTPUT_COPIED, 0)
        val skipped = outputData.getInt(BackupWorker.OUTPUT_SKIPPED, 0)
        val failed = outputData.getInt(BackupWorker.OUTPUT_FAILED, 0)
        "$copied backed up, $skipped unchanged${if (failed == 0) "" else ", $failed failed"}"
    }
    WorkInfo.State.FAILED -> "Backup failed"
    WorkInfo.State.CANCELLED -> null
}
