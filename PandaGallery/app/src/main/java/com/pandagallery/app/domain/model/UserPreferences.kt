package com.pandagallery.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing the user's settings and configurations.
 */
@Serializable
data class UserPreferences(
    val compressionPreset: CompressionPreset = CompressionPreset.HIGH,
    val imageFormat: ImageFormat = ImageFormat.JPEG,
    /** 1-100, or [ADAPTIVE_IMAGE_QUALITY] to let the encoder pick per photo. */
    val imageQuality: Int = 75,
    val videoResolution: VideoResolution = VideoResolution.P1080,
    val videoCodec: VideoCodec = VideoCodec.H265,
    val compressedFolderPath: String = "Pictures/PandaGallery Compressed",
    val compressionOriginalAction: CompressionOriginalAction = CompressionOriginalAction.ASK,
    val compressionConcurrency: Int = defaultCompressionConcurrency(), // 1-10 files simultaneously
    val useGpu: Boolean = true,
    val syncWifiOnly: Boolean = true,
    val isDriveConnected: Boolean = false,
    val driveAccountEmail: String? = null,
    val backupFolderUri: String? = null,
    val lastBackupAt: Long? = null,
    val backupSchedule: BackupSchedule = BackupSchedule.MANUAL,
    val backupSelectedAlbumPaths: Set<String> = emptySet(),
    val backupIncludeVideos: Boolean = true,
    val backupPreserveAlbumStructure: Boolean = true,
    val backupChargingOnly: Boolean = false,
    val smartIndexEnabled: Boolean = false,
    val faceGroupingEnabled: Boolean = false,
    /**
     * Whether a PIN has been set for locked folders and for the Private folder.
     *
     * Two independent PINs, neither of them the device's screen lock: a folder PIN a user might
     * share with family should not also open the Private folder. Only the presence of each PIN is
     * exposed here — the hashes stay behind PreferencesDataSource, since a lock screen is the only
     * thing that has any business reading them.
     */
    val hasFolderLockPin: Boolean = false,
    val hasPrivateVaultPin: Boolean = false,
    /**
     * Lets a fingerprint or face stand in for typing the PIN. Off until the user asks for it: the
     * PIN is the credential they chose, and enrolling biometrics is a separate decision.
     */
    val folderLockBiometricsEnabled: Boolean = false,
    val privateVaultBiometricsEnabled: Boolean = false,
    val isPrivateAlbumConfigured: Boolean = false,
    /**
     * Lets screenshots, screen recording and screen-reading assistants (Circle to Search, Google
     * Assistant) capture the Private Album. Off by default: the vault sets `FLAG_SECURE`, which is
     * what blocks all of them at once, and the safe default for private media is to keep it on.
     */
    val allowPrivateAlbumScreenshots: Boolean = false,
    /**
     * Encrypts the bytes stored in the Private folder.
     *
     * On by default. Turning it off keeps private media in the app's own sandbox — still out of
     * the gallery and invisible to other apps — but stored as plain files, which is faster to
     * open and survives having the app's keys reset. Existing items are converted either way, so
     * the setting always describes the whole vault rather than only what comes next.
     */
    val privateVaultEncryption: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColors: Boolean = true,
    val appIcon: String = "icon_2",
    // View preferences — remembered between sessions instead of resetting on every screen.
    val mediaSortOrder: SortOrder = SortOrder.DATE_DESC,
    /**
     * Lets a folder keep a sort order of its own, instead of every grid in the app sharing one.
     *
     * On by default: sorting a folder by name is nearly always meant for that folder, and having
     * it silently re-sort the whole timeline is the surprising behaviour. [folderSortOverrides]
     * survives switching this off, so turning it back on restores each folder's choice.
     */
    val perFolderSortEnabled: Boolean = true,
    /** Per-folder sort choices, keyed by MediaStore bucket id. Empty until a folder is sorted. */
    val folderSortOverrides: Map<Long, SortOrder> = emptyMap(),
    /** Custom album grouping, keyed by album/bucket ID. */
    val albumGroupOverrides: Map<Long, String> = emptyMap(),
    val mediaContentFilter: MediaContentFilter = MediaContentFilter.ALL,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val albumSortOrder: AlbumSortOrder = AlbumSortOrder.NEWEST,
    val albumGridDensity: AlbumGridDensity = AlbumGridDensity.NORMAL,
    /**
     * Adds each folder's size next to its item count. Off by default: most of the time the count
     * is what a user is scanning for, and the size is only interesting when hunting for space.
     */
    val showAlbumSize: Boolean = false,
    /**
     * Shows the Pictures chronological timeline tab in the bottom navigation bar.
     * On by default matching Samsung Gallery One UI 6.1/7 primary navigation.
     */
    val showPicturesTab: Boolean = true,
    val trashRetentionDays: Int = 30,
    val slideshowIntervalSeconds: Int = 3,
    /**
     * Start videos playing as soon as the viewer opens them. On by default, matching
     * how the viewer has always behaved; turning it off leaves the first frame paused
     * so opening a video never makes noise unexpectedly.
     */
    val autoPlayVideos: Boolean = true,
    /**
     * Move redundant exact duplicates to Trash automatically during the periodic
     * maintenance pass. Off by default: deleting without being asked is not something to
     * opt users into, even when the copies are byte-identical.
     */
    val duplicateAutoResolve: Boolean = false,
    val duplicateKeepRule: DuplicateKeepRule = DuplicateKeepRule.LARGEST,
    val autoPlayMotionPhotos: Boolean = true,
    val superHdrGainmapEnabled: Boolean = true,
    val convertHeifWhenSharing: Boolean = true,
    val convertRawWhenSharing: Boolean = true,
    val removeLocationWhenSharing: Boolean = false,
    val galleryLabsUnlocked: Boolean = false,
    val adaptivePerceptualRateControl: Boolean = true,
    val hardwareBitmapsDirectVram: Boolean = true,
    val rotaryDialHaptics: Boolean = true,
    val thermalThrottleMode: ThermalThrottleMode = ThermalThrottleMode.NORMAL,
    val safetyVaultRetentionDays: Int = 14,
    val safetyVaultEnabled: Boolean = true,
    val motionPhotoCompressionMode: MotionPhotoCompressionMode = MotionPhotoCompressionMode.PRESERVE_CLIP,
    val hardwareCodecAcceleration: Boolean = true,
    val dynamicVideoPreviewInGrid: Boolean = true,
    val filmstripPrecache: Boolean = true,
    val compressionStatusPlacement: CompressionStatusPlacement = CompressionStatusPlacement.ABOVE_BOTTOM_BAR,
)

/**
 * Stored in [UserPreferences.imageQuality] to mean "decide per photo".
 *
 * Not a quality value: the encoder reads it as a request to classify the image's content and
 * pick a quality from that (see
 * [com.pandagallery.app.data.compression.PerceptualQualityEstimator.recommendAdaptiveQuality]).
 * It has to survive being written and read back verbatim — clamping it into the 1..100 range
 * turns "Smart Auto" into quality 1, which is the worst setting the encoder has.
 */
const val ADAPTIVE_IMAGE_QUALITY = -1

/**
 * Normalises a quality value on its way into storage.
 *
 * [ADAPTIVE_IMAGE_QUALITY] passes through untouched; every other value is clamped to the
 * encoder's 1..100 range. A plain `coerceIn(1, 100)` here silently rewrites Smart Auto to
 * quality 1, which is both the opposite of what was selected and the most destructive setting
 * the encoder has.
 */
fun normalizeImageQuality(quality: Int): Int =
    if (quality == ADAPTIVE_IMAGE_QUALITY) ADAPTIVE_IMAGE_QUALITY else quality.coerceIn(1, 100)

/**
 * A named bundle of compression settings. Choosing a preset writes [imageFormat], [imageQuality],
 * [videoResolution] and [videoCodec] onto the user's preferences; the individual settings stay
 * independently editable afterwards, which is why [UserPreferences.matchedCompressionPreset] exists
 * to report whether they still line up with any preset.
 */
enum class CompressionPreset(
    val imageFormat: ImageFormat,
    val imageQuality: Int,
    val videoResolution: VideoResolution,
    val videoCodec: VideoCodec,
) {
    /** Intelligent content-adaptive quality (AI auto). */
    SMART_AUTO(ImageFormat.JPEG, ADAPTIVE_IMAGE_QUALITY, VideoResolution.P1080, VideoCodec.H265),

    /** Maximum space savings. */
    LOW(ImageFormat.JPEG, 30, VideoResolution.P480, VideoCodec.H265),

    /** Good balance (default). */
    MEDIUM(ImageFormat.JPEG, 50, VideoResolution.P1080, VideoCodec.H265),

    /** Near-original quality. */
    HIGH(ImageFormat.JPEG, 75, VideoResolution.P1080, VideoCodec.H265),

    /**
     * Near-lossless archival quality. Produces images with perceptual SSIM >= 0.985
     * and preserves chroma edge sharpness while achieving 50-65% space savings.
     */
    NEAR_LOSSLESS(ImageFormat.WEBP, 92, VideoResolution.ORIGINAL, VideoCodec.H265),

    /**
     * No visible quality loss. Images are re-encoded as true lossless WebP (see
     * [com.pandagallery.app.data.compression.CompressionEngine]); video has no lossless codec
     * path, so it is kept at its original resolution instead. Files are larger, often close to
     * or bigger than the original — [com.pandagallery.app.domain.compression.CompressionEstimator]
     * predicts accordingly rather than promising the savings the lossy presets do.
     */
    LOSSLESS(ImageFormat.WEBP, 100, VideoResolution.ORIGINAL, VideoCodec.H265),
}

/**
 * The preset whose values the current settings exactly match, or `null` once they've been
 * hand-tuned.
 *
 * Presets are a one-way macro: picking "Medium" sets quality to 50, but then nudging quality to 90
 * leaves the stored preset saying "Medium". Reading the preset back to the user without this check
 * reports a preset they are no longer on.
 */
val UserPreferences.matchedCompressionPreset: CompressionPreset?
    get() = CompressionPreset.entries.firstOrNull { preset ->
        preset.imageFormat == imageFormat &&
            preset.imageQuality == imageQuality &&
            preset.videoResolution == videoResolution &&
            preset.videoCodec == videoCodec
    }

/** Which copy of a byte-identical duplicate group to keep. */
@Serializable
enum class DuplicateKeepRule(val label: String, val description: String) {
    LARGEST("Largest file", "Usually the least re-compressed copy"),
    NEWEST("Newest", "Keeps the most recently taken copy"),
    OLDEST("Oldest", "Keeps the original import"),
}

enum class ImageFormat {
    WEBP,
    AVIF,
    JPEG,
}

enum class VideoResolution(val label: String, val height: Int) {
    P480("480p", 480),
    P720("720p", 720),
    P1080("1080p", 1080),
    ORIGINAL("Original", -1),
}

enum class VideoCodec {
    H264,
    H265, // HEVC
    AV1,
}

enum class CompressionOriginalAction {
    ASK,
    COPY,
    MOVE,
}

/**
 * Wrong-PIN history for one lock, as stored. Feeds the backoff in
 * [com.pandagallery.app.domain.security.pinLockoutRemainingMillis].
 */
data class PinAttempts(
    val failedAttempts: Int = 0,
    val lastFailureAt: Long = 0L,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class BackupSchedule {
    MANUAL,
    DAILY,
    WEEKLY,
}

/**
 * Calculates optimal default compression worker concurrency based on available CPU cores.
 * For an 8-core CPU, this defaults to 4 concurrent workers, avoiding over-saturating CPU
 * while maximizing parallel throughput across multiple photos.
 */
fun defaultCompressionConcurrency(): Int =
    (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)

enum class ThermalThrottleMode(val label: String, val description: String) {
    NORMAL("Normal (40°C)", "Standard thermal regulation & cooldown pauses"),
    STRICT("Strict (37°C)", "Aggressive thermal cooling for hot environments & gaming"),
    TURBO("Turbo (Disabled)", "Maximum speed without thermal pauses (Requires cooling)"),
}

enum class MotionPhotoCompressionMode(val label: String, val description: String) {
    PRESERVE_CLIP("Preserve Motion Clip", "Keeps interactive video trailer in compressed photo"),
    AUTO_STRIP("Auto-Strip Clip", "Strips embedded MP4 to maximize storage recovery (5-15MB/photo)"),
}

@Serializable
enum class CompressionStatusPlacement(val label: String, val description: String) {
    ABOVE_BOTTOM_BAR("Above bottom navigation bar", "Floats above bottom bar, keeping top search and menus free"),
    BELOW_TOP_BAR("Below top app bar", "Docks beneath the top app bar, leaving top icons clickable"),
    COMPACT_ISLAND("Compact island", "Centered mini capsule at the top that doesn't block side buttons"),
}

