package com.pandaapps.appstore.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandaapps.appstore.BuildConfig
import com.pandaapps.appstore.appContainer
import com.pandaapps.appstore.data.Settings
import com.pandaapps.appstore.data.SettingsRepository
import com.pandaapps.appstore.install.InstallManager
import com.pandaapps.appstore.ui.StoreUiState
import com.pandaapps.appstore.ui.StoreViewModel
import com.pandaapps.appstore.ui.components.PandaButton
import com.pandaapps.appstore.ui.components.PandaButtonStyle
import com.pandaapps.appstore.ui.components.SectionHeader
import com.pandaapps.appstore.ui.theme.statusColors
import com.pandaapps.appstore.util.Formatters
import kotlinx.coroutines.launch

/**
 * Settings: background update checks (interval, auto-update, Wi-Fi only), permission status with
 * fix buttons, ntfy instructions, downloads cache, version info and the Debug console entry.
 * Setting changes are saved and the update worker is rescheduled by [StoreViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: StoreViewModel, onBack: () -> Unit, onOpenDebug: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val fixNotifications = rememberNotificationFixer(onResult = viewModel::refreshPermissions)

    // ntfy may be installed while we are away (step 1 of the card), so re-check on resume.
    val installedApps = remember(context) { context.appContainer.installedAppsRepository }
    var ntfyInstalled by remember { mutableStateOf(installedApps.isInstalled(SystemIntents.NTFY_PACKAGE)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ntfyInstalled = installedApps.isInstalled(SystemIntents.NTFY_PACKAGE)
    }

    // Downloads cache: measured on entry, after clearing and whenever installs settle.
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var cacheGeneration by remember { mutableIntStateOf(0) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(cacheGeneration, state.anyBusy) {
        cacheBytes = viewModel.downloadsCacheSize()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "updates") {
                UpdatesSection(
                    settings = state.settings,
                    onIntervalChange = viewModel::setCheckIntervalHours,
                    onAutoUpdateChange = viewModel::setAutoUpdate,
                    onWifiOnlyChange = viewModel::setWifiOnly,
                )
            }
            item(key = "permissions") {
                PermissionsSection(
                    state = state,
                    onFixInstall = {
                        context.startFirstAvailable(
                            viewModel.unknownSourcesSettingsIntent(),
                            SystemIntents.appDetailsSettings(context),
                        )
                    },
                    onFixNotifications = fixNotifications,
                )
            }
            item(key = "ntfy") {
                SectionHeader(title = "Instant notifications", modifier = Modifier.padding(top = 8.dp))
                NtfyCard(
                    ntfyInstalled = ntfyInstalled,
                    onGetNtfy = {
                        context.startFirstAvailable(SystemIntents.ntfyStoreListing(), SystemIntents.ntfyWebListing())
                    },
                    onOpenNtfy = {
                        installedApps.launchIntent(SystemIntents.NTFY_PACKAGE)?.let { context.startFirstAvailable(it) }
                    },
                )
            }
            item(key = "storage") {
                StorageSection(
                    cacheBytes = cacheBytes,
                    busy = state.anyBusy || clearing,
                    onClear = {
                        scope.launch {
                            clearing = true
                            try {
                                viewModel.clearDownloads()
                            } finally {
                                clearing = false
                                cacheGeneration++
                            }
                        }
                    },
                )
            }
            item(key = "about") {
                AboutSection(silentEligibleCount = state.silentUpdatable.size)
            }
            item(key = "debug") {
                Spacer(Modifier.height(4.dp))
                NavigationRow(
                    icon = Icons.Filled.Terminal,
                    title = "Debug console",
                    subtitle = "Live log of catalog checks, downloads and installs",
                    onClick = onOpenDebug,
                )
            }
        }
    }
}

// --- Sections ---------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesSection(
    settings: Settings,
    onIntervalChange: (Int) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    val checksOn = settings.checkIntervalHours > 0
    SettingsSection("Updates") {
        SettingsRow(
            icon = Icons.Filled.Schedule,
            title = "Check for updates",
            subtitle = intervalDescription(settings.checkIntervalHours),
            below = {
                val options = SettingsRepository.CHECK_INTERVAL_OPTIONS
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    options.forEachIndexed { index, hours ->
                        SegmentedButton(
                            selected = hours == settings.checkIntervalHours,
                            onClick = { if (hours != settings.checkIntervalHours) onIntervalChange(hours) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            icon = {},
                        ) {
                            Text(intervalLabel(hours), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
        )
        RowDivider()
        SwitchRow(
            icon = Icons.Filled.Autorenew,
            title = "Auto-update",
            subtitle = when {
                !checksOn -> "Background checks are off, so nothing updates on its own."
                else -> "Download and install updates during background checks. Apps Panda App Store " +
                    "installed update without a prompt on Android 12+."
            },
            checked = settings.autoUpdate,
            onCheckedChange = onAutoUpdateChange,
        )
        RowDivider()
        SwitchRow(
            icon = Icons.Filled.Wifi,
            title = "Wi-Fi only",
            subtitle = if (settings.autoUpdate) {
                "Only download automatic updates on Wi-Fi or other unmetered networks."
            } else {
                "Applies to automatic updates. Turn on auto-update to use it."
            },
            checked = settings.wifiOnly,
            onCheckedChange = onWifiOnlyChange,
            enabled = settings.autoUpdate,
        )
    }
}

@Composable
private fun PermissionsSection(
    state: StoreUiState,
    onFixInstall: () -> Unit,
    onFixNotifications: () -> Unit,
) {
    val status = MaterialTheme.statusColors
    SettingsSection("Permissions") {
        SettingsRow(
            icon = Icons.Filled.InstallMobile,
            iconTint = if (state.canInstallPackages) status.success else status.error,
            title = "Install unknown apps",
            subtitle = if (state.canInstallPackages) {
                "Panda App Store can install and update your apps."
            } else {
                "Needed to install and update apps."
            },
            below = { PermissionStatus(state.canInstallPackages, status.success, status.error) },
            trailing = if (state.canInstallPackages) null else {
                { PandaButton("Allow", onFixInstall, compact = true) }
            },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Notifications,
            iconTint = if (state.notificationsAllowed) status.success else status.warning,
            title = "Notifications",
            subtitle = if (state.notificationsAllowed) {
                "You'll hear about new versions and finished installs."
            } else {
                "Without them you won't hear about updates or installs that need a tap."
            },
            below = { PermissionStatus(state.notificationsAllowed, status.success, status.warning) },
            trailing = if (state.notificationsAllowed) null else {
                { PandaButton("Allow", onFixNotifications, compact = true) }
            },
        )
    }
}

@Composable
private fun StorageSection(cacheBytes: Long?, busy: Boolean, onClear: () -> Unit) {
    SettingsSection("Storage") {
        SettingsRow(
            icon = Icons.Filled.DeleteSweep,
            title = "Downloads cache",
            subtitle = null,
            below = {
                AnimatedContent(
                    targetState = cacheBytes,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "cacheSize",
                ) { bytes ->
                    Text(
                        when {
                            bytes == null -> "Measuring…"
                            bytes == 0L -> "Empty"
                            else -> "${Formatters.bytes(bytes)} of downloaded APKs"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (busy) {
                    Text(
                        "Available once the running install finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailing = {
                PandaButton(
                    "Clear",
                    onClear,
                    style = PandaButtonStyle.Outlined,
                    compact = true,
                    enabled = !busy && (cacheBytes ?: 0L) > 0L,
                )
            },
        )
    }
}

@Composable
private fun AboutSection(silentEligibleCount: Int) {
    val sdk = Build.VERSION.SDK_INT
    val minTarget = InstallManager.silentUpdateMinTargetSdk(sdk)
    SettingsSection("About") {
        SettingsRow(
            icon = Icons.Filled.Info,
            title = "Panda App Store",
            subtitle = buildString {
                append("Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}")
                if (BuildConfig.DEBUG) append(" · debug")
            },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Android,
            title = "Android ${Build.VERSION.RELEASE} (API $sdk)",
            subtitle = if (minTarget == null) {
                "Updates without a prompt need Android 12 or newer. Each update asks you to confirm."
            } else {
                "Apps installed by Panda App Store that target API $minTarget+ update without a prompt. " +
                    when (silentEligibleCount) {
                        0 -> "None of yours qualify yet."
                        1 -> "1 of yours qualifies."
                        else -> "$silentEligibleCount of yours qualify."
                    }
            },
        )
    }
}

// --- Notifications permission ---------------------------------------------------------------------

/**
 * Returns a "fix notifications" action. On API 33+ it shows the system permission prompt while
 * Android still allows one; once the user has refused for good (or notifications are blocked in
 * system settings) it opens this app's notification settings instead.
 */
@Composable
private fun rememberNotificationFixer(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        context.appContainer.appLog.i("Settings", "Notification permission ${if (granted) "granted" else "denied"}")
        onResult()
    }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canPromptForNotifications(context)) {
                markNotificationsAsked(context)
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startFirstAvailable(
                    SystemIntents.appNotificationSettings(context),
                    SystemIntents.appDetailsSettings(context),
                )
            }
        }
    }
}

/**
 * True when POST_NOTIFICATIONS is not granted and the system will still show its prompt: either it
 * was never asked, or the user declined once (rationale state). [android.app.Activity]-less callers
 * fall back to settings.
 */
private fun canPromptForNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val permission = Manifest.permission.POST_NOTIFICATIONS
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) return false
    val askedBefore = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ASKED_NOTIFICATIONS, false)
    if (!askedBefore) return true
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

private fun markNotificationsAsked(context: Context) {
    context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
}

// Shared with MainActivity, which asks for POST_NOTIFICATIONS once on first launch.
private const val UI_PREFS = "ui_state"
private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

// --- Labels (pure) ----------------------------------------------------------------------------------

/** Segment label: "Off", "1h", "24h". */
internal fun intervalLabel(hours: Int): String = if (hours <= 0) "Off" else "${hours}h"

/** Subtitle under "Check for updates". */
internal fun intervalDescription(hours: Int): String = when {
    hours <= 0 -> "Off. Pull to refresh on the home screen to check."
    hours == 1 -> "Every hour in the background, with a notification for new versions."
    else -> "Every $hours hours in the background, with a notification for new versions."
}
