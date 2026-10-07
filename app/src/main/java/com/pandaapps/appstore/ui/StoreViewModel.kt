package com.pandaapps.appstore.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pandaapps.appstore.AppContainer
import com.pandaapps.appstore.PandaStoreApp
import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.data.Catalog
import com.pandaapps.appstore.data.CatalogState
import com.pandaapps.appstore.data.Settings
import com.pandaapps.appstore.data.SettingsRepository
import com.pandaapps.appstore.data.StoreApp
import com.pandaapps.appstore.data.toStoreApp
import com.pandaapps.appstore.install.InstallState
import com.pandaapps.appstore.install.isBusy
import com.pandaapps.appstore.notify.Notifications
import com.pandaapps.appstore.util.Formatters
import com.pandaapps.appstore.work.UpdateScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Everything the store screens render, in one immutable snapshot.
 *
 * @property apps every catalog app that has a release, sorted by name.
 * @property installStates per-package pipeline state; a missing key means [InstallState.Idle].
 * @property silentUpdatable packages whose updates install without a prompt (SPEC #7).
 * @property launchable installed packages that have a launcher activity ("Open" is possible).
 * @property isLoading no catalog yet (first launch, cache still loading or first fetch in flight).
 * @property isRefreshing a network refresh is running.
 * @property error message of the last failed refresh, null after a success.
 * @property isOffline the catalog shown is the cached copy because the last refresh failed.
 * @property lastChecked epoch millis of the last successful catalog fetch.
 * @property canInstallPackages "Install unknown apps" is granted to Panda App Store.
 * @property notificationsAllowed notifications can be posted (permission + not blocked).
 */
@Immutable
data class StoreUiState(
    val apps: List<StoreApp> = emptyList(),
    val installStates: Map<String, InstallState> = emptyMap(),
    val silentUpdatable: Set<String> = emptySet(),
    val launchable: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
    val lastChecked: Long? = null,
    val settings: Settings = Settings(),
    /** Package -> share download progress (0..1, or -1 while unknown) for apps being prepared for sharing. */
    val sharing: Map<String, Float> = emptyMap(),
    val canInstallPackages: Boolean = true,
    val notificationsAllowed: Boolean = true,
) {
    /** Apps with [AppStatus.UpdateAvailable], in [apps] order. */
    val updates: List<StoreApp> = apps.filter { it.status == AppStatus.UpdateAvailable }

    val updatesCount: Int get() = updates.size

    /** True when any install is queued or running. */
    val anyBusy: Boolean get() = installStates.values.any { it.isBusy }

    fun app(packageName: String): StoreApp? = apps.firstOrNull { it.packageName == packageName }

    fun installState(packageName: String): InstallState = installStates[packageName] ?: InstallState.Idle

    fun canOpen(packageName: String): Boolean = packageName in launchable

    fun canSilentlyUpdate(packageName: String): Boolean = packageName in silentUpdatable
}

/** One-shot UI events (collected once, by the nav graph). */
sealed interface StoreEvent {
    /** "Install unknown apps" is off; explain and offer [StoreViewModel.unknownSourcesSettingsIntent]. */
    data object NeedInstallPermission : StoreEvent

    /** A short message for a snackbar. */
    data class Message(val text: String) : StoreEvent

    /** An APK is ready to hand to the system share sheet. */
    data class ShareApk(val file: java.io.File, val appName: String, val versionName: String) : StoreEvent
}

/**
 * Shared by every screen (scoped to the activity). Combines the catalog, installed packages,
 * install pipeline states and settings into [state], and exposes the store actions.
 */
class StoreViewModel(
    private val appContext: Context,
    private val container: AppContainer,
) : ViewModel() {

    private val catalogRepository = container.catalogRepository
    private val installedApps = container.installedAppsRepository
    private val installManager = container.installManager
    private val settingsRepository = container.settingsRepository
    private val appLog = container.appLog

    private val permissions = MutableStateFlow(readPermissions())

    /** Serialises settings writes + worker rescheduling. */
    private val settingsMutex = Mutex()

    /**
     * Work requested while "Install unknown apps" was off. It survives exactly one trip to the
     * settings page opened from the permission dialog ([onInstallPermissionSettingsOpened]): resumed
     * on return if the permission was granted, dropped otherwise — never replayed later.
     */
    private var pendingAfterPermission: PendingAction? = null

    /** The user left for the "Install unknown apps" page from the dialog and hasn't come back yet. */
    private var awaitingPermissionReturn = false

    private val sharing = MutableStateFlow<Map<String, Float>>(emptyMap())
    private var shareJob: kotlinx.coroutines.Job? = null

    private val _events = Channel<StoreEvent>(Channel.BUFFERED)
    val events: Flow<StoreEvent> = _events.receiveAsFlow()

    /** Package-manager-derived data; recomputed only when the catalog or installed packages change. */
    private val snapshot: Flow<AppsSnapshot> = combine(
        catalogRepository.state.map { it.catalog }.distinctUntilChanged(),
        installedApps.changes,
    ) { catalog, _ -> buildSnapshot(catalog) }
        .flowOn(Dispatchers.IO)

    val state: StateFlow<StoreUiState> = combine(
        catalogRepository.state,
        snapshot,
        installManager.states,
        settingsRepository.settings,
        permissions,
        sharing,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        toUiState(
            values[0] as CatalogState,
            values[1] as AppsSnapshot,
            values[2] as Map<String, InstallState>,
            values[3] as Settings,
            values[4] as Permissions,
            values[5] as Map<String, Float>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoreUiState())

    init {
        refresh()
    }

    // --- Catalog ----------------------------------------------------------------------------------

    /** Re-fetches the catalog; failures surface through [StoreUiState.error] / [StoreUiState.isOffline]. */
    fun refresh() {
        viewModelScope.launch {
            installedApps.refresh()
            catalogRepository.refresh()
        }
    }

    // --- Install actions --------------------------------------------------------------------------

    /** Installs (or updates to) the latest release of [packageName]. */
    fun install(packageName: String) {
        if (!ensureInstallPermission(PendingAction.Install(packageName))) return
        val app = catalogRepository.findApp(packageName)
        val latest = app?.latest ?: return message("That app is not in the catalog")
        appLog.i(TAG, "Install requested: ${app.name} v${latest.versionName}")
        installManager.install(app, latest)
    }

    /**
     * Installs the catalog release [versionCode] of [packageName] — e.g. the previous release of an
     * app that isn't installed (after a rollback whose second step didn't complete).
     */
    fun installRelease(packageName: String, versionCode: Long) {
        if (!ensureInstallPermission(PendingAction.InstallRelease(packageName, versionCode))) return
        val app = catalogRepository.findApp(packageName) ?: return message("That app is not in the catalog")
        val release = app.releases.firstOrNull { it.versionCode == versionCode }
            ?: return message("That version is no longer in the catalog")
        appLog.i(TAG, "Install requested: ${app.name} v${release.versionName}")
        installManager.install(app, release)
    }

    /**
     * Retry after a failure: repeats the release the failed install was for (a rollback target
     * stays a rollback target), not necessarily the latest one.
     */
    fun retry(packageName: String) {
        val requested = installManager.lastRequestedRelease(packageName) ?: return install(packageName)
        val app = catalogRepository.findApp(packageName) ?: return message("That app is not in the catalog")
        if (app.releases.none { it.versionCode == requested.versionCode }) {
            // Superseded since: back to the normal Install / Update button for what the catalog has now.
            installManager.clearState(packageName)
            return message("v${requested.versionName} is no longer in the catalog")
        }
        installRelease(packageName, requested.versionCode)
    }

    /** Starts every available update that is not already running. Panda App Store itself goes last. */
    fun updateAll() {
        if (!ensureInstallPermission(PendingAction.UpdateAll)) return
        val updates = state.value.updates
            .filterNot { installManager.stateOf(it.packageName).isBusy }
            .sortedBy { it.packageName == appContext.packageName }
        if (updates.isEmpty()) return
        appLog.i(TAG, "Update all: ${updates.joinToString { it.name }}")
        updates.forEach { installManager.install(it.app, it.latest) }
    }

    fun cancel(packageName: String) = installManager.cancel(packageName)

    /** Downloads the latest APK of [packageName] and emits [StoreEvent.ShareApk] for the share sheet. */
    fun share(packageName: String) {
        if (packageName in sharing.value) return
        val app = catalogRepository.findApp(packageName)
        val release = app?.latest ?: return message("Nothing to share yet")
        shareJob = viewModelScope.launch {
            sharing.value = sharing.value + (packageName to -1f)
            try {
                val file = installManager.downloadForShare(app, release) { bytes, total ->
                    val progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else -1f
                    sharing.value = sharing.value + (packageName to progress)
                }
                _events.trySend(StoreEvent.ShareApk(file, app.name, release.versionName))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                appLog.w(TAG, "Could not prepare ${app.name} for sharing", e)
                message("Couldn't download the APK to share")
            } finally {
                sharing.value = sharing.value - packageName
            }
        }
    }

    /** Opens the system uninstall dialog. */
    fun uninstall(packageName: String) = installManager.uninstall(packageName)

    /** Launches the installed app. */
    fun open(packageName: String) {
        if (packageName == appContext.packageName) return
        val intent = installedApps.launchIntent(packageName) ?: return message("This app can't be opened")
        try {
            appContext.startActivity(intent)
        } catch (e: Exception) {
            appLog.w(TAG, "Could not open $packageName", e)
            message("This app can't be opened")
        }
    }

    /**
     * "Uninstall & install this version": uninstalls [packageName], then installs its previous
     * release once the package is gone (Android cannot downgrade in place).
     */
    fun rollback(packageName: String) {
        if (!ensureInstallPermission(PendingAction.Rollback(packageName))) return
        val app = catalogRepository.findApp(packageName)
        val previous = app?.previous?.firstOrNull() ?: return message("No previous version to roll back to")
        installManager.uninstallAndInstall(app, previous)
    }

    /** Reopens the system install confirmation of an install waiting on the user. */
    fun resumePendingInstall(packageName: String) {
        if (!installManager.resumePendingUserAction(packageName)) {
            message("Check the notification shade to finish installing")
        }
    }

    /** Clears a Failed (or Success) state back to Idle. */
    fun dismissInstallResult(packageName: String) = installManager.clearState(packageName)

    // --- Permissions ------------------------------------------------------------------------------

    /** Intent for the "Install unknown apps" page of this app (launch from an Activity). */
    fun unknownSourcesSettingsIntent(): Intent = installManager.unknownSourcesSettingsIntent()

    /** The user declined to grant "Install unknown apps"; forget the deferred action. */
    fun dismissInstallPermission() {
        pendingAfterPermission = null
        awaitingPermissionReturn = false
    }

    /** The permission dialog opened the "Install unknown apps" page; [onResume] settles the deferred action. */
    fun onInstallPermissionSettingsOpened() {
        awaitingPermissionReturn = pendingAfterPermission != null
    }

    /**
     * Re-checks permissions (call on every resume). Back from the settings page opened by the
     * permission dialog, resumes the deferred action if the permission was granted, and drops it
     * if not — so it can't start much later when the permission is granted for another reason.
     */
    fun onResume() {
        installManager.resumePendingConfirmations()
        val perms = readPermissions()
        permissions.value = perms
        if (!awaitingPermissionReturn) return
        awaitingPermissionReturn = false
        val pending = pendingAfterPermission ?: return
        pendingAfterPermission = null
        if (!perms.canInstallPackages) {
            appLog.i(TAG, "Install permission not granted; dropping $pending")
            return
        }
        appLog.i(TAG, "Install permission granted; resuming $pending")
        when (pending) {
            is PendingAction.Install -> install(pending.packageName)
            is PendingAction.InstallRelease -> installRelease(pending.packageName, pending.versionCode)
            PendingAction.UpdateAll -> updateAll()
            is PendingAction.Rollback -> rollback(pending.packageName)
        }
    }

    // --- Settings ---------------------------------------------------------------------------------

    /** Saves the background check interval (0 = off) and reschedules the update worker. */
    fun setCheckIntervalHours(hours: Int) = updateSettings("check interval = ${hours}h") {
        it.setCheckIntervalHours(hours)
    }

    /** Saves the auto-update toggle and reschedules the update worker. */
    fun setAutoUpdate(enabled: Boolean) = updateSettings("auto-update = $enabled") { it.setAutoUpdate(enabled) }

    /** Saves the Wi-Fi-only toggle and reschedules the update worker. */
    fun setWifiOnly(enabled: Boolean) = updateSettings("Wi-Fi only = $enabled") { it.setWifiOnly(enabled) }

    /**
     * Writes a setting, then re-enqueues the periodic update check from the stored values.
     * Runs on the app scope (a quick toggle-and-leave must still reschedule) and serialised, so
     * the last write always wins.
     */
    private fun updateSettings(description: String, write: suspend (SettingsRepository) -> Unit) {
        container.appScope.launch {
            settingsMutex.withLock {
                write(settingsRepository)
                val settings = settingsRepository.snapshot()
                UpdateScheduler.schedule(
                    context = appContext,
                    intervalHours = settings.checkIntervalHours,
                    autoUpdate = settings.autoUpdate,
                    wifiOnly = settings.wifiOnly,
                )
                appLog.i(TAG, "Settings: $description")
            }
        }
    }

    /** Re-reads the install / notification permission state (e.g. after a permission dialog). */
    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    /** Bytes used by downloaded APKs (walks the cache on IO). */
    suspend fun downloadsCacheSize(): Long = withContext(Dispatchers.IO) { installManager.downloadsCacheSize() }

    /** Deletes downloaded APKs and reports how much was freed. Returns the bytes freed. */
    suspend fun clearDownloads(): Long {
        if (state.value.anyBusy) {
            message("Wait for the running install to finish")
            return 0L
        }
        val freed = withContext(Dispatchers.IO) { installManager.clearDownloads() }
        message(if (freed > 0) "Freed ${Formatters.bytes(freed)}" else "Nothing to clear")
        return freed
    }

    // --- Internals --------------------------------------------------------------------------------

    private fun ensureInstallPermission(action: PendingAction): Boolean {
        if (installManager.canRequestPackageInstalls()) return true
        permissions.value = readPermissions()
        pendingAfterPermission = action
        awaitingPermissionReturn = false
        _events.trySend(StoreEvent.NeedInstallPermission)
        return false
    }

    private fun message(text: String) {
        _events.trySend(StoreEvent.Message(text))
    }

    private fun readPermissions() = Permissions(
        canInstallPackages = installManager.canRequestPackageInstalls(),
        notificationsAllowed = Notifications.canPost(appContext),
    )

    private fun buildSnapshot(catalog: Catalog?): AppsSnapshot {
        if (catalog == null) return AppsSnapshot()
        val sdk = Build.VERSION.SDK_INT
        val apps = catalog.apps
            .mapNotNull { it.toStoreApp(installedApps.get(it.packageName), sdk) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val installed = apps.filter { it.installed != null }
        return AppsSnapshot(
            apps = apps,
            silentUpdatable = installed.map { it.packageName }.filterTo(HashSet()) { installManager.canSilentlyUpdate(it) },
            launchable = installed.map { it.packageName }
                .filterTo(HashSet()) { it != appContext.packageName && installedApps.launchIntent(it) != null },
        )
    }

    private fun toUiState(
        catalogState: CatalogState,
        apps: AppsSnapshot,
        installStates: Map<String, InstallState>,
        settings: Settings,
        perms: Permissions,
        sharing: Map<String, Float>,
    ): StoreUiState {
        val hasCatalog = catalogState.catalog != null
        return StoreUiState(
            apps = apps.apps,
            installStates = installStates,
            silentUpdatable = apps.silentUpdatable,
            launchable = apps.launchable,
            isLoading = !hasCatalog && (catalogState.isRefreshing || catalogState.error == null),
            isRefreshing = catalogState.isRefreshing,
            error = catalogState.error,
            isOffline = catalogState.isOffline,
            lastChecked = catalogState.lastUpdated,
            settings = settings,
            sharing = sharing,
            canInstallPackages = perms.canInstallPackages,
            notificationsAllowed = perms.notificationsAllowed,
        )
    }

    private data class AppsSnapshot(
        val apps: List<StoreApp> = emptyList(),
        val silentUpdatable: Set<String> = emptySet(),
        val launchable: Set<String> = emptySet(),
    )

    private data class Permissions(val canInstallPackages: Boolean, val notificationsAllowed: Boolean)

    private sealed interface PendingAction {
        data class Install(val packageName: String) : PendingAction
        data class InstallRelease(val packageName: String, val versionCode: Long) : PendingAction
        data object UpdateAll : PendingAction
        data class Rollback(val packageName: String) : PendingAction
    }

    companion object {
        private const val TAG = "Store"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** `viewModel(factory = StoreViewModel.Factory)` — reads [AppContainer] from the Application. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as PandaStoreApp
                StoreViewModel(app, app.container)
            }
        }
    }
}
