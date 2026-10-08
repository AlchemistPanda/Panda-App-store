package com.pandagallery.app.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.biometric.BiometricManager
import com.pandagallery.app.data.security.PinVerification
import com.pandagallery.app.ui.security.PinSetupDialog
import com.pandagallery.app.ui.security.rememberPinLockoutMessage
import com.pandagallery.app.ui.security.PinVerifyDialog
import com.pandagallery.app.ui.security.wrongPinMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandagallery.app.domain.model.*
import com.pandagallery.app.domain.model.ADAPTIVE_IMAGE_QUALITY
import com.pandagallery.app.domain.model.matchedCompressionPreset
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import com.pandagallery.app.BuildConfig
import com.pandagallery.app.data.update.AppUpdateChecker
import com.pandagallery.app.R
import com.pandagallery.app.ui.components.PandaSelectableOption
import com.pandagallery.app.ui.components.PandaSwitch
import com.pandagallery.app.ui.components.PremiumAlertDialog
import com.pandagallery.app.ui.components.SeslReachabilityHeader
import com.pandagallery.app.ui.components.SeslSwitch
import com.pandagallery.app.ui.theme.rememberAdaptiveAccent
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPrivateFolderClick: () -> Unit,
    onCompressionQueueClick: () -> Unit,
    onCoverViewerClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = uiState.preferences
    val context = LocalContext.current
    var canManageMedia by remember { mutableStateOf(MediaStore.canManageMedia(context)) }
    // Whether the fingerprint shortcut can be offered at all. Checked here rather than at the
    // prompt so the toggle can explain itself instead of failing when tapped.
    val biometricsAvailable = remember(context) {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    val scope = rememberCoroutineScope()
    val vaultStorageBytes by viewModel.vaultStorageBytes.collectAsStateWithLifecycle()
    val vaultConversion by viewModel.vaultConversion.collectAsStateWithLifecycle()
    val safetyVaultBytes by viewModel.safetyVaultBytes.collectAsStateWithLifecycle()
    val safetyVaultCount by viewModel.safetyVaultCount.collectAsStateWithLifecycle()
    val safetyVaultStatus by viewModel.safetyVaultStatus.collectAsStateWithLifecycle()

    var showPresetDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var showImageQualityDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showCompressedFolderDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var folderPinAction by remember { mutableStateOf<PinAction?>(null) }
    var vaultPinAction by remember { mutableStateOf<PinAction?>(null) }
    var showIconDialog by remember { mutableStateOf(false) }
    var showBackupScheduleDialog by remember { mutableStateOf(false) }
    var showBackupAlbumsDialog by remember { mutableStateOf(false) }
    var showTrashRetentionDialog by remember { mutableStateOf(false) }
    var showSlideshowDialog by remember { mutableStateOf(false) }
    var showDuplicateRuleDialog by remember { mutableStateOf(false) }
    var showMediaSortDialog by remember { mutableStateOf(false) }
    var showAlbumSortDialog by remember { mutableStateOf(false) }
    var showResetFolderSortDialog by remember { mutableStateOf(false) }
    var showDisconnectBackupDialog by remember { mutableStateOf(false) }
    var showOriginalActionDialog by remember { mutableStateOf(false) }
    var showStatusPlacementDialog by remember { mutableStateOf(false) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }
    var showThermalThrottleDialog by remember { mutableStateOf(false) }
    var showSafetyVaultRetentionDialog by remember { mutableStateOf(false) }
    var showClearSafetyVaultDialog by remember { mutableStateOf(false) }
    var showMotionPhotoCompressionDialog by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var updateCheckEnabled by remember { mutableStateOf(AppUpdateChecker.isEnabled(context)) }
    val scrollState = rememberScrollState()
    val view = LocalView.current
    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::setBackupFolder) }
    val mediaManagementLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        canManageMedia = MediaStore.canManageMedia(context)
    }

    LifecycleResumeEffect(Unit) {
        canManageMedia = MediaStore.canManageMedia(context)
        viewModel.refreshSafetyVaultStorage()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = scrollState.value > 80,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                    ) {
                        Text(
                            text = "Gallery settings",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            ),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SeslReachabilityHeader(
                    title = "Gallery settings",
                    subtitle = if (preferences.galleryLabsUnlocked) "Labs unlocked · Experimental suite active" else null,
                )

                // ============================================
                // Compression Settings (Memories Vault)
                // ============================================
                SettingsSection(title = "MEMORIES VAULT") {
                    SettingsItem(
                        icon = Icons.Outlined.ManageHistory,
                        iconBgColor = Color(0xFF00BCD4),
                        title = "Compression Queue & History",
                        subtitle = "Track progress, pause, retry, and see space saved",
                        onClick = onCompressionQueueClick,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Compress,
                        iconBgColor = Color(0xFF2196F3), // Light Blue
                        title = "Compression Quality Preset",
                        // Reflects the settings as they actually stand, not the last preset tapped —
                        // the two diverge as soon as quality or resolution is edited by hand.
                        subtitle = buildString {
                            append(
                                when (preferences.matchedCompressionPreset) {
                                    CompressionPreset.SMART_AUTO -> "Smart Auto — quality chosen per photo"
                                    CompressionPreset.NEAR_LOSSLESS -> "Near-Lossless — Archival quality, minimal visible change"
                                    CompressionPreset.LOW -> "Low — Maximum space savings"
                                    CompressionPreset.MEDIUM -> "Medium — Good viewing quality"
                                    CompressionPreset.HIGH -> "High — Near-original quality"
                                    CompressionPreset.LOSSLESS -> "Lossless — No visible quality loss, larger files"
                                    null -> {
                                        val qualityLabel = if (preferences.imageQuality == ADAPTIVE_IMAGE_QUALITY) {
                                            "Smart Auto"
                                        } else {
                                            "${preferences.imageQuality}%"
                                        }
                                        "Custom — $qualityLabel images, ${preferences.videoResolution.label} video"
                                    }
                                }
                            )
                            // Leads with what the setting means, then what it costs in bytes.
                            uiState.currentEstimate?.takeIf { it.isMeaningful }?.let {
                                append("\nTypical photo: ${it.summaryWithSavings}")
                            }
                        },
                        onClick = { showPresetDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Image,
                        iconBgColor = Color(0xFFFF9800), // Orange
                        title = "Default Image Format",
                        subtitle = when (preferences.imageFormat) {
                            ImageFormat.JPEG -> "JPEG (.jpg)"
                            ImageFormat.WEBP -> "WebP (.webp)"
                            ImageFormat.AVIF -> "AVIF (.avif)"
                        },
                        onClick = { showFormatDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Tune,
                        iconBgColor = Color(0xFF9C27B0), // Purple
                        title = "Image Quality",
                        subtitle = if (preferences.imageQuality == ADAPTIVE_IMAGE_QUALITY) {
                            "Smart Auto — chosen per photo"
                        } else {
                            "${preferences.imageQuality}% visual quality"
                        },
                        onClick = { showImageQualityDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.VideoSettings,
                        iconBgColor = Color(0xFF4CAF50), // Green
                        title = "Default Video Quality",
                        subtitle = "${preferences.videoResolution.label}, ${preferences.videoCodec.name}",
                        onClick = { showVideoQualityDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.FolderZip,
                        iconBgColor = Color(0xFFFFC107), // Amber
                        title = "Compressed Folder",
                        subtitle = preferences.compressedFolderPath,
                        onClick = { showCompressedFolderDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.SwapHoriz,
                        iconBgColor = Color(0xFF7E57C2),
                        title = "After compressing",
                        subtitle = when (preferences.compressionOriginalAction) {
                            CompressionOriginalAction.ASK -> "Ask each time"
                            CompressionOriginalAction.COPY -> "Keep the originals"
                            CompressionOriginalAction.MOVE -> "Delete originals once compressed"
                        },
                        onClick = { showOriginalActionDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Speed,
                        iconBgColor = Color(0xFF0288D1),
                        title = "Parallel Compression",
                        subtitle = buildString {
                            val count = preferences.compressionConcurrency
                            val recommended = defaultCompressionConcurrency()
                            val cores = Runtime.getRuntime().availableProcessors()
                            if (count == 1) {
                                append("1 file at a time (Sequential)")
                            } else {
                                append("$count files at a time")
                            }
                            if (count == recommended) {
                                append(" · Recommended ($cores cores)")
                            } else {
                                append(" · Recommended: $recommended ($cores cores)")
                            }
                        },
                        onClick = { showConcurrencyDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Memory,
                        iconBgColor = Color(0xFF26A69A),
                        title = "Hardware acceleration",
                        subtitle = if (preferences.useGpu) {
                            "Video uses the device encoder — fastest"
                        } else {
                            "Video uses a software encoder — slower"
                        },
                        isToggle = true,
                        isToggled = preferences.useGpu,
                        onToggle = viewModel::updateUseGpu,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Layers,
                        iconBgColor = Color(0xFF673AB7),
                        title = "Progress Bar Placement",
                        subtitle = when (preferences.compressionStatusPlacement) {
                            CompressionStatusPlacement.ABOVE_BOTTOM_BAR -> "Above bottom navigation bar (Recommended)"
                            CompressionStatusPlacement.BELOW_TOP_BAR -> "Below top app bar"
                            CompressionStatusPlacement.COMPACT_ISLAND -> "Compact island (Dynamic Capsule)"
                        },
                        onClick = { showStatusPlacementDialog = true },
                    )
                }

                // ============================================
                // Backup
                // ============================================
                SettingsSection(title = "BACKUP") {
                    SettingsItem(
                        icon = Icons.Outlined.CloudSync,
                        iconBgColor = Color(0xFF00BCD4), // Teal
                        title = "Backup Folder",
                        subtitle = if (preferences.backupFolderUri != null) {
                            preferences.lastBackupAt?.let { "Connected. Last backup saved" }
                                ?: "Connected. Choose Back up now"
                        } else {
                            "Choose device storage or Google Drive"
                        },
                        onClick = { backupFolderLauncher.launch(null) },
                    )
                    if (preferences.backupFolderUri != null) {
                        SettingsItem(
                            icon = Icons.Outlined.Backup,
                            iconBgColor = Color(0xFF00E676), // Bright Green
                            title = "Back up now",
                            subtitle = uiState.backupStatus ?: "Copy all visible media to folder",
                            onClick = viewModel::backupNow,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Restore,
                            iconBgColor = MaterialTheme.colorScheme.tertiary,
                            title = "Restore backup",
                            subtitle = "Restore all recorded items to their original albums",
                            onClick = viewModel::restoreBackup,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Schedule,
                            iconBgColor = MaterialTheme.colorScheme.secondary,
                            title = "Backup schedule",
                            subtitle = preferences.backupSchedule.readableName(),
                            onClick = { showBackupScheduleDialog = true },
                        )
                        SettingsItem(
                            icon = Icons.Outlined.PhotoAlbum,
                            iconBgColor = MaterialTheme.colorScheme.primary,
                            title = "Albums to back up",
                            subtitle = if (preferences.backupSelectedAlbumPaths.isEmpty()) {
                                "All albums"
                            } else {
                                "${preferences.backupSelectedAlbumPaths.size} selected"
                            },
                            onClick = { showBackupAlbumsDialog = true },
                        )
                        SettingsItem(
                            icon = Icons.Outlined.VideoLibrary,
                            iconBgColor = MaterialTheme.colorScheme.secondary,
                            title = "Include videos",
                            subtitle = "Back up videos as well as images",
                            isToggle = true,
                            isToggled = preferences.backupIncludeVideos,
                            onToggle = viewModel::updateBackupIncludeVideos,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.AccountTree,
                            iconBgColor = MaterialTheme.colorScheme.tertiary,
                            title = "Preserve album folders",
                            subtitle = "Keep the same folder structure in the backup",
                            isToggle = true,
                            isToggled = preferences.backupPreserveAlbumStructure,
                            onToggle = viewModel::updateBackupPreserveAlbumStructure,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Wifi,
                            iconBgColor = MaterialTheme.colorScheme.primary,
                            title = "Wi-Fi only",
                            subtitle = "Wait for an unmetered network",
                            isToggle = true,
                            isToggled = preferences.syncWifiOnly,
                            onToggle = viewModel::updateSyncWifiOnly,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.BatteryChargingFull,
                            iconBgColor = MaterialTheme.colorScheme.secondary,
                            title = "While charging only",
                            subtitle = "Reduce battery use during scheduled backups",
                            isToggle = true,
                            isToggled = preferences.backupChargingOnly,
                            onToggle = viewModel::updateBackupChargingOnly,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.LinkOff,
                            iconBgColor = Color(0xFFEF5350),
                            title = "Disconnect backup folder",
                            subtitle = "Stop backing up. Files already copied are kept.",
                            onClick = { showDisconnectBackupDialog = true },
                        )
                    }
                }

                // ============================================
                // Privacy & Security
                // ============================================
                SettingsSection(title = "PRIVACY & SECURITY") {
                    SettingsItem(
                        icon = Icons.Outlined.AdminPanelSettings,
                        iconBgColor = Color(0xFF00BCD4),
                        title = "Automatic media moves",
                        subtitle = if (canManageMedia) {
                            "Allowed — Private moves no longer need repeated delete confirmation"
                        } else {
                            "Allow once to remove public originals automatically"
                        },
                        onClick = {
                            mediaManagementLauncher.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.FolderSpecial,
                        iconBgColor = Color(0xFFF44336), // Red
                        title = "Private folder",
                        subtitle = "Encrypted media behind its own PIN",
                        onClick = onPrivateFolderClick,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Lock,
                        iconBgColor = Color(0xFFE91E63), // Pink
                        title = "Private folder PIN",
                        subtitle = if (preferences.hasPrivateVaultPin) {
                            "PIN set — tap to change or remove"
                        } else {
                            "Not set — the Private folder asks for one when you open it"
                        },
                        onClick = { vaultPinAction = PinAction.forExisting(preferences.hasPrivateVaultPin) },
                    )
                    if (preferences.hasPrivateVaultPin) {
                        SettingsItem(
                            icon = Icons.Outlined.Fingerprint,
                            iconBgColor = Color(0xFF00838F),
                            title = "Fingerprint for Private folder",
                            subtitle = biometricShortcutSubtitle(
                                enabled = preferences.privateVaultBiometricsEnabled,
                                available = biometricsAvailable,
                            ),
                            isToggle = true,
                            isToggled = preferences.privateVaultBiometricsEnabled && biometricsAvailable,
                            onToggle = { enabled ->
                                if (biometricsAvailable) viewModel.updatePrivateVaultBiometrics(enabled)
                            },
                        )
                    }
                    SettingsItem(
                        icon = Icons.Outlined.FolderOff,
                        iconBgColor = Color(0xFF6D4C41),
                        title = "Folder lock PIN",
                        subtitle = if (preferences.hasFolderLockPin) {
                            "PIN set — opens every locked folder"
                        } else {
                            "Not set — choose one when you lock a folder"
                        },
                        onClick = { folderPinAction = PinAction.forExisting(preferences.hasFolderLockPin) },
                    )
                    if (preferences.hasFolderLockPin) {
                        SettingsItem(
                            icon = Icons.Outlined.Fingerprint,
                            iconBgColor = Color(0xFF00695C),
                            title = "Fingerprint for locked folders",
                            subtitle = biometricShortcutSubtitle(
                                enabled = preferences.folderLockBiometricsEnabled,
                                available = biometricsAvailable,
                            ),
                            isToggle = true,
                            isToggled = preferences.folderLockBiometricsEnabled && biometricsAvailable,
                            onToggle = { enabled ->
                                if (biometricsAvailable) viewModel.updateFolderLockBiometrics(enabled)
                            },
                        )
                    }
                    SettingsItem(
                        icon = Icons.Outlined.EnhancedEncryption,
                        iconBgColor = Color(0xFF5E35B1),
                        title = "Encrypt private folder",
                        subtitle = when {
                            vaultConversion.isRunning && vaultConversion.total > 0 ->
                                "Converting ${vaultConversion.done} of ${vaultConversion.total}…"
                            vaultConversion.isRunning -> "Converting stored items…"
                            vaultConversion.error != null -> "Could not convert: ${vaultConversion.error}"
                            preferences.privateVaultEncryption ->
                                "Files are encrypted. Turning this off keeps them in the app's " +
                                    "private storage, out of your gallery and other apps."
                            else ->
                                "Files are stored unencrypted in the app's private storage — " +
                                    "isolated from your gallery, but readable with root access."
                        },
                        isToggle = true,
                        isToggled = preferences.privateVaultEncryption,
                        onToggle = viewModel::updatePrivateVaultEncryption,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.PieChart,
                        iconBgColor = Color(0xFF3949AB),
                        title = "Private folder storage",
                        subtitle = if (vaultStorageBytes > 0L) {
                            "${formatFileSize(vaultStorageBytes)} stored in the private folder"
                        } else {
                            "Nothing stored yet"
                        },
                        onClick = viewModel::refreshVaultStorage,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.ScreenshotMonitor,
                        iconBgColor = Color(0xFFFF7043), // Deep Orange
                        // Titles are single-line and subtitles are two, and a toggle row's text
                        // column is narrower than a chevron row's — both stay short enough to fit.
                        title = "Private Album screenshots",
                        subtitle = if (preferences.allowPrivateAlbumScreenshots) {
                            "Also allows recording and Circle to Search"
                        } else {
                            "Also blocks recording and Circle to Search"
                        },
                        isToggle = true,
                        isToggled = preferences.allowPrivateAlbumScreenshots,
                        onToggle = viewModel::updateAllowPrivateAlbumScreenshots,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Search,
                        iconBgColor = Color(0xFF3F51B5),
                        title = "On-device smart search",
                        subtitle = "Automatically index new and changed photos on-device while charging",
                        isToggle = true,
                        isToggled = preferences.smartIndexEnabled,
                        onToggle = viewModel::updateSmartIndexEnabled,
                    )
                    if (preferences.smartIndexEnabled) {
                        SettingsItem(
                            icon = Icons.Outlined.Face,
                            iconBgColor = Color(0xFF673AB7),
                            title = "People grouping",
                            // When the recognition model is absent the toggle explains why
                            // rather than switching on a feature that cannot work.
                            subtitle = uiState.faceModelStatus
                                ?: "Group photos by face on-device; nothing is uploaded and no identity is inferred",
                            isToggle = true,
                            isToggled = preferences.faceGroupingEnabled && uiState.isFaceGroupingAvailable,
                            onToggle = viewModel::updateFaceGroupingEnabled,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.AutoAwesome,
                            iconBgColor = Color(0xFF009688),
                            title = "Build smart index",
                            subtitle = uiState.smartIndexStatus
                                ?: "Scan photos for searchable text, objects, and optional face groups",
                            onClick = viewModel::buildSmartIndex,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.DeleteOutline,
                            iconBgColor = Color(0xFF8D6E63),
                            title = "Clear on-device index",
                            subtitle = "Delete recognised text, labels and face groups from this device",
                            onClick = viewModel::clearSmartIndex,
                        )
                    }
                }

                // ============================================
                // Gallery behaviour
                // ============================================
                SettingsSection(title = "GALLERY") {
                    SettingsItem(
                        icon = Icons.Outlined.Image,
                        iconBgColor = Color(0xFFE91E63),
                        title = "Show Pictures tab",
                        subtitle = if (preferences.showPicturesTab) {
                            "Pictures timeline tab is shown in the bottom bar"
                        } else {
                            "Off · Albums is the default starting tab"
                        },
                        isToggle = true,
                        isToggled = preferences.showPicturesTab,
                        onToggle = viewModel::updateShowPicturesTab,
                    )
                    SettingsItem(
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        iconBgColor = Color(0xFF3F51B5),
                        title = "Photo sort order",
                        subtitle = if (preferences.perFolderSortEnabled) {
                            "${preferences.mediaSortOrder.label} — for folders you haven't sorted yourself"
                        } else {
                            "${preferences.mediaSortOrder.label} — everywhere in the app"
                        },
                        onClick = { showMediaSortDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.FolderOpen,
                        iconBgColor = Color(0xFF00ACC1),
                        title = "Sort folders separately",
                        subtitle = if (preferences.perFolderSortEnabled) {
                            "Sorting inside a folder changes that folder only"
                        } else {
                            "Sorting anywhere changes every photo grid at once"
                        },
                        isToggle = true,
                        isToggled = preferences.perFolderSortEnabled,
                        onToggle = viewModel::updatePerFolderSortEnabled,
                    )
                    // Only worth showing once folders actually differ from the default — and it is
                    // how a user undoes a sort they no longer remember setting.
                    if (preferences.folderSortOverrides.isNotEmpty()) {
                        SettingsItem(
                            icon = Icons.Outlined.RestartAlt,
                            iconBgColor = Color(0xFF8D6E63),
                            title = "Reset folder sort orders",
                            subtitle = pluralFolders(preferences.folderSortOverrides.size) +
                                " sorted differently from the default",
                            onClick = { showResetFolderSortDialog = true },
                        )
                    }
                    SettingsItem(
                        icon = Icons.Outlined.Folder,
                        iconBgColor = Color(0xFF7CB342),
                        title = "Album sort order",
                        subtitle = "${preferences.albumSortOrder.label} — pinned albums stay first",
                        onClick = { showAlbumSortDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.DataUsage,
                        iconBgColor = Color(0xFF00897B),
                        title = "Show folder size",
                        subtitle = if (preferences.showAlbumSize) {
                            "Each folder shows its item count and size"
                        } else {
                            "Each folder shows its item count only"
                        },
                        isToggle = true,
                        isToggled = preferences.showAlbumSize,
                        onToggle = viewModel::updateShowAlbumSize,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.DeleteSweep,
                        iconBgColor = Color(0xFF795548),
                        title = "Keep trashed items for",
                        subtitle = "${preferences.trashRetentionDays} days before permanent deletion",
                        onClick = { showTrashRetentionDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Slideshow,
                        iconBgColor = Color(0xFF5C6BC0),
                        title = "Slideshow speed",
                        subtitle = "${preferences.slideshowIntervalSeconds} seconds per photo",
                        onClick = { showSlideshowDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.ContentCopy,
                        iconBgColor = Color(0xFF7E57C2),
                        title = "Auto-resolve duplicates",
                        subtitle = if (preferences.duplicateAutoResolve) {
                            "Weekly while charging · keeps the ${preferences.duplicateKeepRule.label.lowercase()}"
                        } else {
                            "Move byte-identical copies to Trash automatically"
                        },
                        isToggle = true,
                        isToggled = preferences.duplicateAutoResolve,
                        onToggle = viewModel::updateDuplicateAutoResolve,
                    )
                    if (preferences.duplicateAutoResolve) {
                        SettingsItem(
                            icon = Icons.Outlined.Checklist,
                            iconBgColor = Color(0xFF546E7A),
                            title = "Copy to keep",
                            subtitle = "${preferences.duplicateKeepRule.label} — ${preferences.duplicateKeepRule.description}",
                            onClick = { showDuplicateRuleDialog = true },
                        )
                    }
                    SettingsItem(
                        icon = Icons.Outlined.PlayCircleOutline,
                        iconBgColor = Color(0xFFE91E63),
                        title = "Autoplay videos",
                        subtitle = if (preferences.autoPlayVideos) {
                            "Videos start playing as soon as you open them"
                        } else {
                            "Videos open paused on the first frame"
                        },
                        isToggle = true,
                        isToggled = preferences.autoPlayVideos,
                        onToggle = viewModel::updateAutoPlayVideos,
                    )
                }

                // ============================================
                // Display & Customization
                // ============================================
                SettingsSection(title = "DISPLAY & CUSTOMIZATION") {
                    SettingsItem(
                        icon = Icons.Outlined.ScreenshotMonitor,
                        iconBgColor = Color(0xFF00897B),
                        title = "Photo screensaver",
                        subtitle = "Show a rotating selection of recent photos while your device is docked or charging",
                        onClick = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }
                        },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.DarkMode,
                        iconBgColor = Color(0xFF607D8B), // Slate/Gray
                        title = "Dark/Light Theme",
                        subtitle = when (preferences.themeMode) {
                            ThemeMode.SYSTEM -> "System default"
                            ThemeMode.LIGHT -> "Light theme"
                            ThemeMode.DARK -> "Dark theme"
                        },
                        onClick = { showThemeDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Palette,
                        iconBgColor = Color(0xFF9E9E9E), // Cool Gray
                        title = "Dynamic Color Theme",
                        subtitle = "Use color scheme from wallpaper (Android 12+)",
                        isToggle = true,
                        isToggled = preferences.useDynamicColors,
                        onToggle = { viewModel.updateUseDynamicColors(it) },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Collections,
                        iconBgColor = Color(0xFFFF5722), // Deep Orange
                        title = "App Icon",
                        subtitle = when (preferences.appIcon) {
                            "icon_1" -> "Camera Lens Integration"
                            "icon_2" -> "Artistic Polaroid (Default)"
                            "icon_3" -> "Abstract Low-Poly Shutter"
                            "icon_4" -> "Minimalist Blueprint"
                            "icon_5" -> "Overlapping Photo Cards"
                            "icon_6" -> "Neon Synthwave"
                            "icon_7" -> "Watercolor Splatter"
                            "icon_8" -> "Clay Panda"
                            "icon_9" -> "Retro Pixel"
                            "icon_10" -> "Luxury Gold Line"
                            "icon_11" -> "Doodle Sketch"
                            "icon_12" -> "Cyber Robotic"
                            "icon_13" -> "Papercut Silhouette"
                            "icon_14" -> "Panda Garage (Car Collector)"
                            "icon_15" -> "Panda Garage (Speed Shop)"
                            "icon_16" -> "Panda Garage (Workshop)"
                            else -> "Artistic Polaroid (Default)"
                        },
                        onClick = { showIconDialog = true },
                    )
                }

                // ============================================
                // Advanced & Media Conversions (Samsung Gallery 15.9 Parity)
                // ============================================
                SettingsSection(title = "ADVANCED & MEDIA CONVERSIONS") {
                    SettingsItem(
                        icon = Icons.Outlined.PlayCircleOutline,
                        iconBgColor = Color(0xFF00ACC1),
                        title = "Auto-play motion photos",
                        subtitle = if (preferences.autoPlayMotionPhotos) {
                            "Plays the motion video clip preview while browsing photos"
                        } else {
                            "Motion photos remain static until tapped"
                        },
                        isToggle = true,
                        isToggled = preferences.autoPlayMotionPhotos,
                        onToggle = viewModel::setAutoPlayMotionPhotos,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.HdrOn,
                        iconBgColor = Color(0xFFFF9800),
                        title = "Super HDR gainmap display",
                        subtitle = if (preferences.superHdrGainmapEnabled) {
                            "Display Ultra HDR gainmaps at peak display luminance"
                        } else {
                            "Standard dynamic range (SDR) fallback"
                        },
                        isToggle = true,
                        isToggled = preferences.superHdrGainmapEnabled,
                        onToggle = viewModel::setSuperHdrGainmapEnabled,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Transform,
                        iconBgColor = Color(0xFF4CAF50),
                        title = "Convert HEIF images when sharing",
                        subtitle = if (preferences.convertHeifWhenSharing) {
                            "Converts HEIC/HEIF images to JPEG before sending for universal compatibility"
                        } else {
                            "Share original HEIF files"
                        },
                        isToggle = true,
                        isToggled = preferences.convertHeifWhenSharing,
                        onToggle = viewModel::setConvertHeifWhenSharing,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.PhotoCamera,
                        iconBgColor = Color(0xFF9C27B0),
                        title = "Convert RAW images when sharing",
                        subtitle = if (preferences.convertRawWhenSharing) {
                            "Converts RAW (DNG) files to JPEG for universal compatibility"
                        } else {
                            "Share original RAW DNG files"
                        },
                        isToggle = true,
                        isToggled = preferences.convertRawWhenSharing,
                        onToggle = viewModel::setConvertRawWhenSharing,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.LocationOff,
                        iconBgColor = Color(0xFF2C6CF5),
                        title = "Remove location data when sharing",
                        subtitle = if (preferences.removeLocationWhenSharing) {
                            "Strips GPS coordinates from photos and videos before sharing to protect privacy"
                        } else {
                            "Location coordinates remain embedded in shared files"
                        },
                        isToggle = true,
                        isToggled = preferences.removeLocationWhenSharing,
                        onToggle = viewModel::setRemoveLocationWhenSharing,
                    )
                }

                SettingsSection(title = "COMPRESSION SAFETY VAULT") {
                    SettingsItem(
                        icon = Icons.Outlined.Shield,
                        iconBgColor = Color(0xFF2E7D32),
                        title = "Keep originals before compressing",
                        subtitle = if (preferences.safetyVaultEnabled) {
                            "Stores the untouched original so a compression can be undone — uses extra space until it expires"
                        } else {
                            "Off · Compressing in place is permanent and cannot be undone"
                        },
                        isToggle = true,
                        isToggled = preferences.safetyVaultEnabled,
                        onToggle = viewModel::setSafetyVaultEnabled,
                    )
                    SettingsItem(
                        icon = Icons.Outlined.HistoryToggleOff,
                        iconBgColor = Color(0xFF00897B),
                        title = "Keep originals for",
                        subtitle = when {
                            !preferences.safetyVaultEnabled ->
                                "Not in use while originals are not being kept"
                            preferences.safetyVaultRetentionDays == 0 ->
                                "Forever · Cleared only when you clear it by hand"
                            else ->
                                "${preferences.safetyVaultRetentionDays} days · Undo window before the copy is deleted"
                        },
                        onClick = { showSafetyVaultRetentionDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.DeleteSweep,
                        iconBgColor = Color(0xFFD32F2F),
                        title = "Safety Vault storage",
                        subtitle = safetyVaultStatus ?: when {
                            safetyVaultBytes <= 0L -> "Nothing kept · Tap to re-check"
                            safetyVaultCount > 0 ->
                                "${formatFileSize(safetyVaultBytes)} across $safetyVaultCount " +
                                    "${if (safetyVaultCount == 1) "original" else "originals"} · Tap to clear"
                            else -> "${formatFileSize(safetyVaultBytes)} kept · Tap to clear"
                        },
                        onClick = {
                            if (safetyVaultBytes > 0L) {
                                showClearSafetyVaultDialog = true
                            } else {
                                viewModel.refreshSafetyVaultStorage()
                            }
                        },
                    )
                }

                if (preferences.galleryLabsUnlocked) {
                    SettingsSection(title = "GALLERY LABS · COMPRESSION ENGINE") {
                        SettingsItem(
                            icon = Icons.Outlined.Thermostat,
                            iconBgColor = Color(0xFFE65100),
                            title = "Thermal Throttling Profile",
                            subtitle = "${preferences.thermalThrottleMode.label} · ${preferences.thermalThrottleMode.description}",
                            onClick = { showThermalThrottleDialog = true },
                        )
                        SettingsItem(
                            icon = Icons.Outlined.MotionPhotosOn,
                            iconBgColor = Color(0xFF8E24AA),
                            title = "Motion Photo Batch Compression",
                            subtitle = "${preferences.motionPhotoCompressionMode.label} · ${preferences.motionPhotoCompressionMode.description}",
                            onClick = { showMotionPhotoCompressionDialog = true },
                        )
                        SettingsItem(
                            icon = Icons.Outlined.AutoFixHigh,
                            iconBgColor = Color(0xFF3F51B5),
                            title = "Adaptive Perceptual Rate Control",
                            subtitle = if (preferences.adaptivePerceptualRateControl) {
                                "Active · Picks a quality per photo to preserve texture and edges"
                            } else {
                                "Disabled · Uses static linear quality quantization"
                            },
                            isToggle = true,
                            isToggled = preferences.adaptivePerceptualRateControl,
                            onToggle = viewModel::setAdaptivePerceptualRateControl,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Speed,
                            iconBgColor = Color(0xFF00897B),
                            title = "Hardware MediaCodec Acceleration",
                            subtitle = if (preferences.hardwareCodecAcceleration) {
                                "Active · Direct GPU/NPU-accelerated AVIF & WebP encoding"
                            } else {
                                "Disabled · Software CPU encoder fallback"
                            },
                            isToggle = true,
                            isToggled = preferences.hardwareCodecAcceleration,
                            onToggle = viewModel::setHardwareCodecAcceleration,
                        )
                    }

                    SettingsSection(title = "GALLERY LABS · DISPLAY & VIEWER") {
                        SettingsItem(
                            icon = Icons.Outlined.PlayCircleOutline,
                            iconBgColor = Color(0xFF1976D2),
                            title = "Dynamic Video Preview in Grid",
                            subtitle = if (preferences.dynamicVideoPreviewInGrid) {
                                "Active · Autoplays silent video thumbnails in media grid"
                            } else {
                                "Disabled · Static poster frame thumbnails"
                            },
                            isToggle = true,
                            isToggled = preferences.dynamicVideoPreviewInGrid,
                            onToggle = viewModel::setDynamicVideoPreviewInGrid,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.HdrOn,
                            iconBgColor = Color(0xFFE91E63),
                            title = "Super HDR Gainmap Display Boost",
                            subtitle = if (preferences.superHdrGainmapEnabled) {
                                "Active · Displays Ultra HDR gainmaps at peak luminance on AMOLED panels"
                            } else {
                                "Disabled · Standard dynamic range SDR fallback"
                            },
                            isToggle = true,
                            isToggled = preferences.superHdrGainmapEnabled,
                            onToggle = viewModel::setSuperHdrGainmapEnabled,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Memory,
                            iconBgColor = Color(0xFF009688),
                            title = "120Hz Hardware Bitmaps (Direct GPU VRAM)",
                            subtitle = if (preferences.hardwareBitmapsDirectVram) {
                                "Active · GraphicBuffer GPU allocation bypassing JVM GC pauses"
                            } else {
                                "Disabled · Software ARGB_8888 bitmap heap allocation"
                            },
                            isToggle = true,
                            isToggled = preferences.hardwareBitmapsDirectVram,
                            onToggle = viewModel::setHardwareBitmapsDirectVram,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.BurstMode,
                            iconBgColor = Color(0xFF5C6BC0),
                            title = "Filmstrip Pre-cache Pipeline",
                            subtitle = if (preferences.filmstripPrecache) {
                                "Active · Pre-renders adjacent slides in L1 memory cache"
                            } else {
                                "Disabled · On-demand lazy rendering"
                            },
                            isToggle = true,
                            isToggled = preferences.filmstripPrecache,
                            onToggle = viewModel::setFilmstripPrecache,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Vibration,
                            iconBgColor = Color(0xFFFF9800),
                            title = "Rotary Horizon Dial Mechanical Haptics",
                            subtitle = if (preferences.rotaryDialHaptics) {
                                "Active · Tactile clicks on each degree step with magnetic 0° detent"
                            } else {
                                "Disabled · Silent rotation dial"
                            },
                            isToggle = true,
                            isToggled = preferences.rotaryDialHaptics,
                            onToggle = viewModel::setRotaryDialHaptics,
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Smartphone,
                            iconBgColor = Color(0xFF673AB7),
                            title = "Cover Screen Viewer (Foldables)",
                            subtitle = "Preview streamlined high-contrast viewer for foldable cover screens",
                            onClick = onCoverViewerClick,
                        )
                    }

                    SettingsSection(title = "GALLERY LABS · ACTIONS") {
                        SettingsItem(
                            icon = Icons.Outlined.RestartAlt,
                            iconBgColor = Color(0xFF607D8B),
                            title = "Reset Gallery Labs Defaults",
                            subtitle = "Restore all experimental settings to factory One UI defaults",
                            onClick = {
                                viewModel.resetGalleryLabsDefaults()
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                android.widget.Toast.makeText(context, "Gallery Labs settings reset to defaults", android.widget.Toast.LENGTH_SHORT).show()
                            },
                        )
                        SettingsItem(
                            icon = Icons.Outlined.Lock,
                            iconBgColor = Color(0xFF795548),
                            title = "Lock Gallery Labs",
                            subtitle = "Hide experimental settings and lock Gallery Labs",
                            onClick = {
                                viewModel.setGalleryLabsUnlocked(false)
                                versionTapCount = 0
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                android.widget.Toast.makeText(context, "Gallery Labs locked", android.widget.Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }

                DiagnosticsSection()

                // ============================================
                // About
                // ============================================
                SettingsSection(title = "ABOUT") {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        iconBgColor = Color(0xFF009688), // Teal/Cyan
                        title = "Version",
                        subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (preferences.galleryLabsUnlocked) " · [Labs Unlocked]" else ""}",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            if (!preferences.galleryLabsUnlocked) {
                                versionTapCount++
                                if (versionTapCount in 3..6) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "You are ${7 - versionTapCount} steps away from unlocking Gallery Labs",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else if (versionTapCount >= 7) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    viewModel.setGalleryLabsUnlocked(true)
                                    android.widget.Toast.makeText(
                                        context,
                                        "🧪 Gallery Labs unlocked! Experimental settings revealed.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Gallery Labs is enabled · Scroll up to configure experimental features",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                    )
                    SettingsItem(
                        icon = Icons.Outlined.SystemUpdate,
                        iconBgColor = Color(0xFF3F51B5), // Indigo
                        title = "Check for updates",
                        subtitle = "Look for a newer build on Panda App Store when the app opens",
                        isToggle = true,
                        isToggled = updateCheckEnabled,
                        onToggle = { enabled ->
                            AppUpdateChecker.setEnabled(context, enabled)
                            updateCheckEnabled = enabled
                        },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ============================================
    // Dialog Selection Overlays
    // ============================================

    // 1. Preset Quality Dialog
    if (showPresetDialog) {
        SingleSelectDialog(
            title = "Compression Quality Preset",
            options = CompressionPreset.entries.map { preset ->
                preset.name to when (preset) {
                    CompressionPreset.SMART_AUTO -> "Smart Auto (Quality chosen per photo — saves least)"
                    CompressionPreset.NEAR_LOSSLESS -> "Near-Lossless (Archival — minimal visible change)"
                    CompressionPreset.LOW -> "Low (Max space savings)"
                    CompressionPreset.MEDIUM -> "Medium (Recommended balance)"
                    CompressionPreset.HIGH -> "High (Near-original quality)"
                    CompressionPreset.LOSSLESS -> "Lossless (No quality loss, largest files)"
                }
            },
            // Empty when hand-tuned, so no preset is shown as chosen while the values differ.
            selectedOption = preferences.matchedCompressionPreset?.name.orEmpty(),
            // Predicted result for a typical photo in this library, so the choice is
            // concrete rather than three adjectives.
            optionDescriptions = uiState.presetEstimates
                .filterValues { it.isMeaningful }
                .mapKeys { (preset, _) -> preset.name }
                .mapValues { (_, estimate) -> "Typical photo: ${estimate.summaryWithSavings}" },
            onDismiss = { showPresetDialog = false },
            onSelect = {
                viewModel.updateCompressionPreset(CompressionPreset.valueOf(it))
                showPresetDialog = false
            }
        )
    }

    folderPinAction?.let { action ->
        PinManagementFlow(
            action = action,
            setupTitle = "Set folder lock PIN",
            verifyTitle = "Enter folder lock PIN",
            manageMessage = "This PIN opens every locked folder. It is separate from your " +
                "Private folder PIN and from your phone's screen lock.",
            removeMessage = "Locked folders will ask you to choose a new PIN next time you open one.",
            onAdvance = { folderPinAction = it },
            onVerify = viewModel::verifyFolderLockPin,
            lockoutRemaining = viewModel::folderLockoutRemainingMillis,
            onPinChosen = viewModel::setFolderLockPin,
            onRemove = viewModel::clearFolderLockPin,
            scope = scope,
        )
    }

    vaultPinAction?.let { action ->
        PinManagementFlow(
            action = action,
            setupTitle = "Set Private folder PIN",
            verifyTitle = "Enter Private folder PIN",
            manageMessage = "This PIN opens the Private folder only. Your media stays encrypted " +
                "either way — the PIN controls who can see it in the app.",
            removeMessage = "The Private folder will ask you to choose a new PIN next time you open it.",
            onAdvance = { vaultPinAction = it },
            onVerify = viewModel::verifyPrivateVaultPin,
            lockoutRemaining = viewModel::privateVaultLockoutRemainingMillis,
            onPinChosen = viewModel::setPrivateVaultPin,
            onRemove = viewModel::clearPrivateVaultPin,
            scope = scope,
        )
    }

    if (showMediaSortDialog) {
        SingleSelectDialog(
            title = "Photo sort order",
            options = SortOrder.entries.map { it.name to it.label },
            selectedOption = preferences.mediaSortOrder.name,
            onDismiss = { showMediaSortDialog = false },
            onSelect = {
                viewModel.updateMediaSortOrder(SortOrder.valueOf(it))
                showMediaSortDialog = false
            },
        )
    }

    if (showAlbumSortDialog) {
        SingleSelectDialog(
            title = "Album sort order",
            options = AlbumSortOrder.entries.map { it.name to it.label },
            selectedOption = preferences.albumSortOrder.name,
            onDismiss = { showAlbumSortDialog = false },
            onSelect = {
                viewModel.updateAlbumSortOrder(AlbumSortOrder.valueOf(it))
                showAlbumSortDialog = false
            },
        )
    }

    if (showResetFolderSortDialog) {
        PremiumAlertDialog(
            onDismissRequest = { showResetFolderSortDialog = false },
            icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
            title = { Text("Reset folder sort orders?") },
            text = {
                Text(
                    "${pluralFolders(preferences.folderSortOverrides.size)} will go back to " +
                        "${preferences.mediaSortOrder.label.lowercase()}. No photos are moved or changed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetFolderSortDialog = false
                        viewModel.clearFolderSortOverrides()
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetFolderSortDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showDuplicateRuleDialog) {
        SingleSelectDialog(
            title = "Copy to keep",
            options = DuplicateKeepRule.entries.map { it.name to it.label },
            optionDescriptions = DuplicateKeepRule.entries.associate { it.name to it.description },
            selectedOption = preferences.duplicateKeepRule.name,
            onDismiss = { showDuplicateRuleDialog = false },
            onSelect = {
                viewModel.updateDuplicateKeepRule(DuplicateKeepRule.valueOf(it))
                showDuplicateRuleDialog = false
            },
        )
    }

    if (showBackupScheduleDialog) {
        SingleSelectDialog(
            title = "Backup schedule",
            options = BackupSchedule.entries.map { it.name to it.readableName() },
            selectedOption = preferences.backupSchedule.name,
            onDismiss = { showBackupScheduleDialog = false },
            onSelect = {
                viewModel.updateBackupSchedule(BackupSchedule.valueOf(it))
                showBackupScheduleDialog = false
            },
        )
    }

    if (showBackupAlbumsDialog) {
        BackupAlbumsDialog(
            albums = uiState.albums,
            selectedPaths = preferences.backupSelectedAlbumPaths,
            onDismiss = { showBackupAlbumsDialog = false },
            onSave = {
                viewModel.updateBackupSelectedAlbumPaths(it)
                showBackupAlbumsDialog = false
            },
        )
    }

    // 2. Image Format Dialog
    if (showFormatDialog) {
        SingleSelectDialog(
            title = "Default Image Format",
            options = ImageFormat.entries.map { format ->
                format.name to when {
                    format == ImageFormat.JPEG -> "JPEG (.jpg) — Universal compatibility"
                    format == ImageFormat.WEBP -> "WebP (.webp) — Modern web format"
                    format == ImageFormat.AVIF && !uiState.isAvifEncodingSupported ->
                        "AVIF (Not supported on this device)"
                    format == ImageFormat.AVIF -> "AVIF (.avif) — High efficiency"
                    else -> format.name
                }
            },
            selectedOption = preferences.imageFormat.name,
            disabledOptions = if (uiState.isAvifEncodingSupported) emptySet() else setOf(ImageFormat.AVIF.name),
            onDismiss = { showFormatDialog = false },
            onSelect = {
                viewModel.updateImageFormat(ImageFormat.valueOf(it))
                showFormatDialog = false
            }
        )
    }

    // 3. Video Quality Dialog
    if (showVideoQualityDialog) {
        VideoQualityDialog(
            currentResolution = preferences.videoResolution,
            currentCodec = preferences.videoCodec,
            onDismiss = { showVideoQualityDialog = false },
            onSave = { res, codec ->
                viewModel.updateVideoResolution(res)
                viewModel.updateVideoCodec(codec)
                showVideoQualityDialog = false
            }
        )
    }

    if (showImageQualityDialog) {
        ImageQualityDialog(
            currentQuality = preferences.imageQuality,
            onDismiss = { showImageQualityDialog = false },
            onSave = {
                viewModel.updateImageQuality(it)
                showImageQualityDialog = false
            },
        )
    }

    if (showCompressedFolderDialog) {
        FolderPathDialog(
            currentPath = preferences.compressedFolderPath,
            onDismiss = { showCompressedFolderDialog = false },
            onSave = {
                viewModel.updateCompressedFolderPath(it)
                showCompressedFolderDialog = false
            },
        )
    }

    if (showConcurrencyDialog) {
        ParallelCompressionDialog(
            currentConcurrency = preferences.compressionConcurrency,
            onDismiss = { showConcurrencyDialog = false },
            onSave = {
                viewModel.updateCompressionConcurrency(it)
                showConcurrencyDialog = false
            },
        )
    }


    // 5. Theme Dialog
    if (showThemeDialog) {
        SingleSelectDialog(
            title = "App Theme",
            options = ThemeMode.entries.map { mode ->
                mode.name to when (mode) {
                    ThemeMode.SYSTEM -> "System default"
                    ThemeMode.LIGHT -> "Light Theme"
                    ThemeMode.DARK -> "Dark Theme"
                }
            },
            selectedOption = preferences.themeMode.name,
            onDismiss = { showThemeDialog = false },
            onSelect = {
                viewModel.updateThemeMode(ThemeMode.valueOf(it))
                showThemeDialog = false
            }
        )
    }

    if (showTrashRetentionDialog) {
        SingleSelectDialog(
            title = "Keep trashed items for",
            options = listOf(7, 14, 30, 60, 90).map { it.toString() to "$it days" },
            selectedOption = preferences.trashRetentionDays.toString(),
            onDismiss = { showTrashRetentionDialog = false },
            onSelect = {
                viewModel.updateTrashRetentionDays(it.toInt())
                showTrashRetentionDialog = false
            },
        )
    }

    if (showThermalThrottleDialog) {
        SingleSelectDialog(
            title = "Thermal Throttling Profile",
            options = ThermalThrottleMode.entries.map { it.name to "${it.label} — ${it.description}" },
            selectedOption = preferences.thermalThrottleMode.name,
            onDismiss = { showThermalThrottleDialog = false },
            onSelect = {
                viewModel.setThermalThrottleMode(ThermalThrottleMode.valueOf(it))
                showThermalThrottleDialog = false
            },
        )
    }

    if (showClearSafetyVaultDialog) {
        PremiumAlertDialog(
            onDismissRequest = { showClearSafetyVaultDialog = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text("Clear the Safety Vault?") },
            text = {
                Text(
                    "Deletes ${formatFileSize(safetyVaultBytes)} of kept originals. Photos you have " +
                        "already compressed stay as they are, but they can no longer be reverted to " +
                        "the uncompressed version.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSafetyVaultDialog = false
                        viewModel.clearSafetyVault()
                    },
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearSafetyVaultDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showSafetyVaultRetentionDialog) {
        SingleSelectDialog(
            title = "Keep originals for",
            options = listOf(
                "7" to "7 Days — Fast storage reclamation",
                "14" to "14 Days (Default) — Balanced protection",
                "30" to "30 Days — Extended safety",
                "60" to "60 Days — Maximum rollback window",
                "0" to "Forever — Manual cleanup only",
            ),
            selectedOption = preferences.safetyVaultRetentionDays.toString(),
            onDismiss = { showSafetyVaultRetentionDialog = false },
            onSelect = {
                viewModel.setSafetyVaultRetentionDays(it.toInt())
                showSafetyVaultRetentionDialog = false
            },
        )
    }

    if (showMotionPhotoCompressionDialog) {
        SingleSelectDialog(
            title = "Motion Photo Batch Compression",
            options = MotionPhotoCompressionMode.entries.map { it.name to "${it.label} — ${it.description}" },
            selectedOption = preferences.motionPhotoCompressionMode.name,
            onDismiss = { showMotionPhotoCompressionDialog = false },
            onSelect = {
                viewModel.setMotionPhotoCompressionMode(MotionPhotoCompressionMode.valueOf(it))
                showMotionPhotoCompressionDialog = false
            },
        )
    }

    if (showSlideshowDialog) {
        SingleSelectDialog(
            title = "Slideshow speed",
            options = listOf(2, 3, 5, 8, 15).map { it.toString() to "$it seconds per photo" },
            selectedOption = preferences.slideshowIntervalSeconds.toString(),
            onDismiss = { showSlideshowDialog = false },
            onSelect = {
                viewModel.updateSlideshowIntervalSeconds(it.toInt())
                showSlideshowDialog = false
            },
        )
    }

    if (showOriginalActionDialog) {
        SingleSelectDialog(
            title = "After compressing",
            options = CompressionOriginalAction.entries.map { action ->
                action.name to when (action) {
                    CompressionOriginalAction.ASK -> "Ask each time"
                    CompressionOriginalAction.COPY -> "Keep the originals"
                    CompressionOriginalAction.MOVE -> "Delete originals once compressed"
                }
            },
            selectedOption = preferences.compressionOriginalAction.name,
            onDismiss = { showOriginalActionDialog = false },
            onSelect = {
                viewModel.updateCompressionOriginalAction(CompressionOriginalAction.valueOf(it))
                showOriginalActionDialog = false
            },
        )
    }

    if (showStatusPlacementDialog) {
        SingleSelectDialog(
            title = "Progress Bar Placement",
            options = CompressionStatusPlacement.entries.map { placement ->
                placement.name to "${placement.label} — ${placement.description}"
            },
            selectedOption = preferences.compressionStatusPlacement.name,
            onDismiss = { showStatusPlacementDialog = false },
            onSelect = {
                viewModel.updateCompressionStatusPlacement(CompressionStatusPlacement.valueOf(it))
                showStatusPlacementDialog = false
            },
        )
    }

    if (showDisconnectBackupDialog) {
        PremiumAlertDialog(
            onDismissRequest = { showDisconnectBackupDialog = false },
            icon = { Icon(Icons.Outlined.LinkOff, contentDescription = null) },
            title = { Text("Disconnect backup folder?") },
            text = { Text("Scheduled backups stop. Files already copied to the folder are left untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectBackupDialog = false
                        viewModel.disconnectBackupFolder()
                    },
                ) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectBackupDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showIconDialog) {
        AppIconDialog(
            selectedIcon = preferences.appIcon,
            onDismiss = { showIconDialog = false },
            onSelect = {
                viewModel.updateAppIcon(it)
                showIconDialog = false
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    isToggle: Boolean = false,
    isToggled: Boolean = false,
    onToggle: (Boolean) -> Unit = {},
) {
    // The row hues are authored as single fixed values; adapt each to the active theme so it stays
    // legible on both the near-black and the near-white card.
    val accent = rememberAdaptiveAccent(iconBgColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (!isToggle) onClick else { { onToggle(!isToggled) } })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isToggle) {
            SeslSwitch(
                checked = isToggled,
                onCheckedChange = onToggle,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SingleSelectDialog(
    title: String,
    options: List<Pair<String, String>>, // id to label
    selectedOption: String,
    disabledOptions: Set<String> = emptySet(),
    /** Optional secondary line per option id, e.g. a predicted before/after size. */
    optionDescriptions: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { (id, label) ->
                    PandaSelectableOption(
                        title = label,
                        selected = id == selectedOption,
                        enabled = id !in disabledOptions,
                        description = optionDescriptions[id],
                        onClick = { onSelect(id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BackupAlbumsDialog(
    albums: List<Album>,
    selectedPaths: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selection by remember(selectedPaths) { mutableStateOf(selectedPaths) }
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Albums to back up") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Leave every album unchecked to back up all albums.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                albums.forEach { album ->
                    val path = album.relativePath?.trim('/') ?: return@forEach
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selection = if (path in selection) selection - path else selection + path
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = path in selection,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(album.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${album.mediaCount} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selection) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { selection = emptySet() }) { Text("All albums") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Which step of setting, changing or removing a PIN is on screen.
 *
 * Changing and removing both start by proving the current PIN. Without that, anyone holding an
 * already-unlocked phone could strip the lock off a folder without ever knowing its PIN — the one
 * thing the lock exists to prevent.
 */
private sealed interface PinAction {
    /** No PIN yet: choose one. */
    data object Create : PinAction

    /** A PIN exists: change it or remove it. */
    data object Manage : PinAction

    data object VerifyToChange : PinAction

    data object VerifyToRemove : PinAction

    companion object {
        fun forExisting(hasPin: Boolean): PinAction = if (hasPin) Manage else Create
    }
}

/**
 * The set / change / remove flow for one lock's PIN, shared by both locks so neither can drift
 * into a weaker sequence than the other.
 */
@Composable
private fun PinManagementFlow(
    action: PinAction,
    setupTitle: String,
    verifyTitle: String,
    manageMessage: String,
    removeMessage: String,
    onAdvance: (PinAction?) -> Unit,
    onVerify: suspend (String) -> PinVerification,
    lockoutRemaining: suspend () -> Long,
    onPinChosen: (String) -> Unit,
    onRemove: () -> Unit,
    scope: CoroutineScope,
) {
    var error by remember(action) { mutableStateOf<String?>(null) }
    val verifying = action == PinAction.VerifyToChange || action == PinAction.VerifyToRemove
    val lockoutMessage = rememberPinLockoutMessage(active = verifying, remainingMillis = lockoutRemaining)

    when (action) {
        PinAction.Create -> PinSetupDialog(
            title = setupTitle,
            onPinChosen = { pin ->
                onPinChosen(pin)
                onAdvance(null)
            },
            onDismiss = { onAdvance(null) },
        )

        PinAction.Manage -> PremiumAlertDialog(
            onDismissRequest = { onAdvance(null) },
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            title = { Text("PIN is set") },
            text = { Text(manageMessage) },
            confirmButton = {
                TextButton(onClick = { onAdvance(PinAction.VerifyToChange) }) { Text("Change PIN") }
            },
            dismissButton = {
                TextButton(onClick = { onAdvance(PinAction.VerifyToRemove) }) {
                    Text("Remove PIN", color = MaterialTheme.colorScheme.error)
                }
            },
        )

        PinAction.VerifyToChange, PinAction.VerifyToRemove -> PinVerifyDialog(
            title = verifyTitle,
            message = if (action == PinAction.VerifyToRemove) removeMessage else "Enter your current PIN to change it.",
            error = error,
            lockoutMessage = lockoutMessage,
            onSubmit = { pin ->
                scope.launch {
                    when (val result = onVerify(pin)) {
                        PinVerification.Success -> {
                            if (action == PinAction.VerifyToRemove) {
                                onRemove()
                                onAdvance(null)
                            } else {
                                onAdvance(PinAction.Create)
                            }
                        }
                        // The lockout line is owned by the poller above, which also clears it
                        // when the wait expires; this only reports the guess itself.
                        is PinVerification.Wrong -> error = wrongPinMessage(result)
                        is PinVerification.LockedOut -> error = null
                    }
                }
            },
            onDismiss = { onAdvance(null) },
        )
    }
}

/** Explains a fingerprint toggle, including the case where the phone has none enrolled. */
private fun biometricShortcutSubtitle(enabled: Boolean, available: Boolean): String = when {
    !available -> "No fingerprint enrolled on this device"
    enabled -> "A fingerprint can be used instead of the PIN"
    else -> "Always ask for the PIN"
}

/** "1 folder" / "4 folders" — the count is the point of the sentence, so it leads. */
private fun pluralFolders(count: Int): String =
    if (count == 1) "1 folder" else "$count folders"

private fun BackupSchedule.readableName(): String = when (this) {
    BackupSchedule.MANUAL -> "Manual only"
    BackupSchedule.DAILY -> "Daily"
    BackupSchedule.WEEKLY -> "Weekly"
}

@Composable
fun ImageQualityDialog(
    currentQuality: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    // Smart Auto is not a point on the slider, so it is tracked separately. Seeding the slider
    // with the raw stored value instead would put "-1" under the heading and, on Save, write a
    // quality of 1 for anyone who opened this dialog while Smart Auto was selected.
    var isAdaptive by remember { mutableStateOf(currentQuality == ADAPTIVE_IMAGE_QUALITY) }
    var quality by remember {
        mutableFloatStateOf(
            if (currentQuality == ADAPTIVE_IMAGE_QUALITY) 75f else currentQuality.toFloat().coerceIn(30f, 100f),
        )
    }

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image Quality") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (isAdaptive) "Auto" else "${quality.toInt()}",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!isAdaptive) {
                        Text(
                            text = "%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (isAdaptive) "Chosen per photo" else qualityLabel(quality.toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Slider(
                    value = quality,
                    onValueChange = {
                        quality = it
                        isAdaptive = false
                    },
                    valueRange = 30f..100f,
                    steps = 13,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Smaller files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Better detail",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isAdaptive) {
                    TextButton(
                        onClick = { isAdaptive = true },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text("Let Smart Auto decide per photo")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(if (isAdaptive) ADAPTIVE_IMAGE_QUALITY else quality.toInt()) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** Turns the raw percentage into the trade-off it actually represents. */
private fun qualityLabel(quality: Int): String = when {
    quality < 45 -> "Maximum savings"
    quality < 65 -> "Balanced"
    quality < 85 -> "High detail"
    else -> "Near-original"
}

@Composable
fun ParallelCompressionDialog(
    currentConcurrency: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val context = LocalContext.current
    val cpuCores = remember { Runtime.getRuntime().availableProcessors() }
    val recommendedConcurrency = remember { defaultCompressionConcurrency() }
    val ramText = remember(context) {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        if (totalGb > 0.0) "${kotlin.math.ceil(totalGb).toInt()} GB RAM" else null
    }

    var concurrency by remember { mutableFloatStateOf(currentConcurrency.toFloat().coerceIn(1f, 10f)) }

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parallel Compression") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Configure how many files are compressed simultaneously in the background queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Device hardware capacity & recommendation banner
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = buildString {
                                    append("$cpuCores CPU Cores")
                                    if (ramText != null) append(" • $ramText")
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Recommended: $recommendedConcurrency parallel tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            if (concurrency.toInt() != recommendedConcurrency) {
                                Text(
                                    text = "Use recommended",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { concurrency = recommendedConcurrency.toFloat() }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "${concurrency.toInt()}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (concurrency.toInt() == 1) " file at a time" else " files at a time",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                    )
                    if (concurrency.toInt() == recommendedConcurrency) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                        ) {
                            Text(
                                text = "Recommended",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = concurrencyLabel(concurrency.toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Slider(
                    value = concurrency,
                    onValueChange = { concurrency = it },
                    valueRange = 1f..10f,
                    steps = 8,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "1 (Sequential)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "10 (Maximum)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = when {
                        concurrency.toInt() == 1 ->
                            "Sequential compression uses minimal memory and battery (Best for background battery saving)."
                        concurrency.toInt() == recommendedConcurrency ->
                            "Optimal setting for your $cpuCores-core device. Maximizes throughput while preserving UI smoothness."
                        concurrency.toInt() > recommendedConcurrency ->
                            "Faster batch throughput, but uses more RAM, CPU, and generates more heat during large jobs."
                        else ->
                            "Moderate concurrency. Good balance of speed and device resource consumption."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(concurrency.toInt()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun concurrencyLabel(concurrency: Int): String = when (concurrency) {
    1 -> "Default"
    in 2..3 -> "Moderate"
    in 4..6 -> "Fast"
    else -> "Maximum"
}

@Composable
fun FolderPathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var path by remember { mutableStateOf(currentPath) }

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compressed Folder") },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                singleLine = true,
                label = { Text("Folder path") },
                supportingText = { Text("Example: Pictures/PandaGallery Compressed") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(path) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun VideoQualityDialog(
    currentResolution: VideoResolution,
    currentCodec: VideoCodec,
    onDismiss: () -> Unit,
    onSave: (VideoResolution, VideoCodec) -> Unit,
) {
    var selectedResolution by remember { mutableStateOf(currentResolution) }
    var selectedCodec by remember { mutableStateOf(currentCodec) }

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Compression Settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Resolution Selector
                Column {
                    Text(
                        text = "Resolution ceiling",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        // "Target" read as a size the encoder would aim for in both directions,
                        // which is what the engine used to do — it enlarged anything smaller.
                        text = "Videos above this are scaled down. Smaller ones are left as they are.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    VideoResolution.entries.forEach { res ->
                        PandaSelectableOption(
                            title = res.label,
                            selected = res == selectedResolution,
                            onClick = { selectedResolution = res },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Codec Selector
                Column {
                    Text(
                        text = "Codec",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        // Measured, not assumed: Media3's DefaultEncoderFactory derives the
                        // requested bitrate from width, height and frame rate alone, so all
                        // three codecs are handed the same budget. The old labels ("Higher
                        // compression", "~30% smaller") promised a size difference that the
                        // encoder is never asked to produce.
                        text = "Affects picture quality at the same file size, not the size itself. " +
                            "Resolution is what changes how much space a video takes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    VideoCodec.entries.forEach { codec ->
                        PandaSelectableOption(
                            title = when (codec) {
                                VideoCodec.H264 -> "H.264 / AVC — plays everywhere"
                                VideoCodec.H265 -> "H.265 / HEVC — better quality per MB (recommended)"
                                VideoCodec.AV1 -> "AV1 — best quality per MB, slowest to encode"
                            },
                            selected = codec == selectedCodec,
                            onClick = { selectedCodec = codec },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedResolution, selectedCodec) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AppIconDialog(
    selectedIcon: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val icons = listOf(
        "icon_1" to Triple("Camera Lens", R.drawable.ic_launcher_icon_1, "Option 1: Camera Lens Integration (Deep blue/minimalist)"),
        "icon_2" to Triple("Artistic Polaroid", R.drawable.ic_launcher_icon_2, "Option 2: Artistic Panda & Polaroid (Cute, glassmorphism sunset)"),
        "icon_3" to Triple("Low-Poly Shutter", R.drawable.ic_launcher_icon_3, "Option 3: Abstract Shutter & Low-Poly (Vibrant gradients, geometric)"),
        "icon_4" to Triple("Grid Blueprint", R.drawable.ic_launcher_icon_4, "Option 4: Minimalist Blueprint & Aperture (Sleek layout, camera eye)"),
        "icon_5" to Triple("Photo Cards", R.drawable.ic_launcher_icon_5, "Option 5: Overlapping Photo Cards (Extremely creative, polaroid-themed)"),
        "icon_6" to Triple("Neon Synthwave", R.drawable.ic_launcher_icon_6, "Option 6: Synthwave / Retro Neon"),
        "icon_7" to Triple("Watercolor Splatter", R.drawable.ic_launcher_icon_7, "Option 7: Artistic Watercolor Splatter"),
        "icon_8" to Triple("Clay Panda", R.drawable.ic_launcher_icon_8, "Option 8: 3D Claymation / Clay"),
        "icon_9" to Triple("Retro Pixel", R.drawable.ic_launcher_icon_9, "Option 9: Retro Pixel Art"),
        "icon_10" to Triple("Luxury Gold", R.drawable.ic_launcher_icon_10, "Option 10: Elegant Gold & Marble Line-Art"),
        "icon_11" to Triple("Doodle Sketch", R.drawable.ic_launcher_icon_11, "Option 11: Hand-Drawn Doodle"),
        "icon_12" to Triple("Cyber Robotic", R.drawable.ic_launcher_icon_12, "Option 12: Cyberpunk / Robotic"),
        "icon_13" to Triple("Papercut Forest", R.drawable.ic_launcher_icon_13, "Option 13: Papercut Silhouette"),
        "icon_14" to Triple("Panda Garage", R.drawable.ic_launcher_icon_14, "Option 14: Panda Garage Car Collector (Smiling Panda & Red Sports Car)"),
        "icon_15" to Triple("Speed Shop", R.drawable.ic_launcher_icon_15, "Option 15: Panda Garage Speed Shop (Carbon & Turbo)"),
        "icon_16" to Triple("Workshop", R.drawable.ic_launcher_icon_16, "Option 16: Panda Garage Workshop (Full Studio)")
    )

    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose App Icon",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Selecting a new icon will update your app launcher icon. Your active launcher might close briefly to reload.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(icons) { (id, details) ->
                        val (name, drawableRes, desc) = details
                        val isSelected = (id == selectedIcon)
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                                .clickable { onSelect(id) }
                                .padding(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = drawableRes),
                                contentDescription = desc,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = 2.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
