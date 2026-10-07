package com.pandaapps.appstore.ui.detail

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.data.CatalogRelease
import com.pandaapps.appstore.data.StoreApp
import com.pandaapps.appstore.install.InstallState
import com.pandaapps.appstore.install.isBusy
import com.pandaapps.appstore.ui.StoreUiState
import com.pandaapps.appstore.ui.StoreViewModel
import com.pandaapps.appstore.ui.components.AppIcon
import com.pandaapps.appstore.ui.components.DownloadProgress
import com.pandaapps.appstore.ui.components.EmptyState
import com.pandaapps.appstore.ui.components.InfoBanner
import com.pandaapps.appstore.ui.components.InstallAction
import com.pandaapps.appstore.ui.components.PandaButton
import com.pandaapps.appstore.ui.components.PandaButtonStyle
import com.pandaapps.appstore.ui.components.PandaCard
import com.pandaapps.appstore.ui.components.SectionHeader
import com.pandaapps.appstore.ui.components.StatusChip
import com.pandaapps.appstore.ui.components.color
import com.pandaapps.appstore.ui.components.installActionFor
import com.pandaapps.appstore.ui.components.rememberArmedClick
import com.pandaapps.appstore.ui.settings.SwitchRow
import com.pandaapps.appstore.ui.theme.MonoTextStyle
import com.pandaapps.appstore.ui.theme.statusColors
import com.pandaapps.appstore.util.Formatters

/**
 * Details of one catalog app: hero header, status explanation with the primary action,
 * install progress, version facts, the per-app auto-update switch, every catalog release (with the
 * rollback flow for the previous one) and the notes of older versions whose APKs are gone. Also handles a deep link to a package the catalog doesn't contain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(packageName: String, viewModel: StoreViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selfPackage = LocalContext.current.packageName
    val app = state.app(packageName)

    val listState = rememberLazyListState()
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var confirmUninstall by rememberSaveable(packageName) { mutableStateOf(false) }
    var confirmRollback by rememberSaveable(packageName) { mutableStateOf(false) }

    // A deep link (e.g. an ntfy push) can arrive before our cached catalog knows the app:
    // fetch once before declaring it missing.
    var refreshedForMissing by rememberSaveable(packageName) { mutableStateOf(false) }
    val missing = app == null && !state.isLoading
    LaunchedEffect(missing) {
        if (missing && !refreshedForMissing) {
            refreshedForMissing = true
            viewModel.refresh()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = {
                    AnimatedVisibility(
                        visible = showTitle && app != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                    ) {
                        Text(app?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (app != null) {
                        val progress = state.sharing[app.packageName]
                        if (progress != null) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                if (progress >= 0f) {
                                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                }
                            }
                        } else {
                            IconButton(onClick = { viewModel.share(app.packageName) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share APK")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            app != null -> DetailContent(
                app = app,
                state = state,
                isSelf = app.packageName == selfPackage,
                selfPackage = selfPackage,
                contentPadding = padding,
                listState = listState,
                actions = DetailActions(
                    onInstall = { viewModel.install(app.packageName) },
                    onRetry = { viewModel.retry(app.packageName) },
                    onInstallRelease = { versionCode -> viewModel.installRelease(app.packageName, versionCode) },
                    onOpen = { viewModel.open(app.packageName) },
                    onCancel = { viewModel.cancel(app.packageName) },
                    onConfirm = { viewModel.resumePendingInstall(app.packageName) },
                    onUninstall = { confirmUninstall = true },
                    onRollback = { confirmRollback = true },
                    onDismissError = { viewModel.dismissInstallResult(app.packageName) },
                    onAutoUpdateChange = { on -> viewModel.setAutoUpdatePaused(app.packageName, paused = !on) },
                ),
            )

            state.isLoading || state.isRefreshing || !refreshedForMissing ->
                LoadingBox(Modifier.padding(padding))

            else -> NotFound(
                packageName = packageName,
                error = state.error.takeIf { state.apps.isEmpty() },
                isOffline = state.isOffline,
                onRetry = viewModel::refresh,
                onBack = onBack,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (app != null && confirmUninstall) {
        UninstallDialog(
            app = app,
            onConfirm = {
                confirmUninstall = false
                viewModel.uninstall(app.packageName)
            },
            onDismiss = { confirmUninstall = false },
        )
    }
    val previous = app?.app?.previous?.firstOrNull()
    if (app != null && previous != null && confirmRollback) {
        RollbackDialog(
            app = app,
            release = previous,
            onConfirm = {
                confirmRollback = false
                viewModel.rollback(app.packageName)
            },
            onDismiss = { confirmRollback = false },
        )
    }
}

/** Callbacks of the detail screen's buttons. */
private class DetailActions(
    val onInstall: () -> Unit,
    /** Repeats the failed install's release (which may be an older one, after a rollback). */
    val onRetry: () -> Unit,
    /** Installs a specific catalog release (by versionCode) of an app that isn't installed. */
    val onInstallRelease: (Long) -> Unit,
    val onOpen: () -> Unit,
    val onCancel: () -> Unit,
    val onConfirm: () -> Unit,
    val onUninstall: () -> Unit,
    val onRollback: () -> Unit,
    val onDismissError: () -> Unit,
    /** The per-app "Auto-update" switch was flipped (true = auto-update on, i.e. not paused). */
    val onAutoUpdateChange: (Boolean) -> Unit,
)

@Composable
private fun DetailContent(
    app: StoreApp,
    state: StoreUiState,
    isSelf: Boolean,
    selfPackage: String,
    contentPadding: PaddingValues,
    listState: LazyListState,
    actions: DetailActions,
) {
    val installState = state.installState(app.packageName)
    val silent = state.canSilentlyUpdate(app.packageName)
    val canOpen = state.canOpen(app.packageName)
    val deviceSdk = Build.VERSION.SDK_INT
    val releases = remember(app.app) { (listOf(app.latest) + app.app.previous).distinctBy { it.versionCode } }
    val rollbackTarget = app.app.previous.firstOrNull()
    // Notes only: their APKs were pruned, so they render as plain release cards without actions.
    // ponytail: dummy apkUrl to reuse ReleaseCard; give it a notes-only overload if cards diverge.
    val older = remember(app.app) {
        olderVersions(app.app).map {
            CatalogRelease(
                versionName = it.versionName,
                versionCode = it.versionCode,
                apkUrl = "",
                releasedAt = it.releasedAt,
                notes = it.notes,
            )
        }
    }
    val layoutDirection = LocalLayoutDirection.current

    LazyColumn(
        state = listState,
        // Edge-to-edge: keep content clear of side system bars / cutouts too (landscape 3-button nav).
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero") {
            Hero(app = app, installState = installState, silent = silent)
        }
        item(key = "status") {
            StatusCard(
                app = app,
                installState = installState,
                silent = silent,
                canOpen = canOpen,
                isSelf = isSelf,
                deviceSdk = deviceSdk,
                actions = actions,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "facts") {
            FactsCard(
                app = app,
                selfPackage = selfPackage,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (app.installed != null) {
            item(key = "autoUpdate") {
                val globalOn = state.settings.autoUpdate
                val paused = state.isAutoUpdatePaused(app.packageName)
                PandaCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    SwitchRow(
                        icon = Icons.Filled.Bolt,
                        title = "Auto-update",
                        subtitle = autoUpdateRowSubtitle(globalOn, paused),
                        checked = globalOn && !paused,
                        onCheckedChange = actions.onAutoUpdateChange,
                        enabled = globalOn,
                    )
                }
            }
        }
        item(key = "releasesHeader") {
            SectionHeader(title = "Releases", count = releases.size)
        }
        items(releases, key = { "release-${it.versionCode}" }) { release ->
            val isPrevious = release != app.latest
            val offerRollback = isPrevious && release == rollbackTarget && canRollBack(app, release, isSelf)
            // Not installed (e.g. the rollback's uninstall happened but its install didn't finish):
            // the previous release can simply be installed.
            val offerInstall = isPrevious && app.installed == null && !isSelf
            ReleaseCard(
                appName = app.name,
                release = release,
                isLatest = release == app.latest,
                isPrevious = isPrevious,
                isInstalled = app.installed?.versionCode == release.versionCode,
                offerRollback = offerRollback,
                rollbackBlockedReason = when {
                    !offerRollback -> null
                    installState.isBusy || installState == InstallState.PendingUserAction ->
                        "Wait for the current install to finish."
                    else -> releaseIncompatibility(release, deviceSdk)
                },
                onRollback = actions.onRollback,
                offerInstall = offerInstall,
                installBlockedReason = when {
                    !offerInstall -> null
                    installState.isBusy -> "Wait for the current install to finish."
                    else -> releaseIncompatibility(release, deviceSdk)
                },
                onInstall = { actions.onInstallRelease(release.versionCode) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (older.isNotEmpty()) {
            item(key = "olderHeader") {
                SectionHeader(title = "Older versions", count = older.size)
            }
            items(older, key = { "older-${it.versionCode}" }) { release ->
                ReleaseCard(
                    appName = app.name,
                    release = release,
                    isLatest = false,
                    isPrevious = false,
                    isInstalled = app.installed?.versionCode == release.versionCode,
                    offerRollback = false,
                    rollbackBlockedReason = null,
                    onRollback = {},
                    offerInstall = false,
                    installBlockedReason = null,
                    onInstall = {},
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/**
 * Rolling back means uninstalling first, so offer it only when the installed build is newer
 * than [release] (or can't be updated in place at all) — and never for Panda App Store itself.
 */
private fun canRollBack(app: StoreApp, release: CatalogRelease, isSelf: Boolean): Boolean {
    val installed = app.installed ?: return false
    if (isSelf) return false
    return installed.versionCode > release.versionCode || app.status == AppStatus.SignerMismatch
}

// --- Hero ---------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(app: StoreApp, installState: InstallState, silent: Boolean) {
    val accent = app.status.color()
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val iconScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "heroIconScale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent)))
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon(
            app = app,
            size = 96.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            app.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            app.packageName,
            style = MonoTextStyle.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            heroVersionLine(app),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            StatusChip(status = app.status, installState = installState)
            AnimatedVisibility(visible = silent, enter = fadeIn(), exit = fadeOut()) {
                AutoUpdateBadge()
            }
        }
    }
}

/** "v1.0.5 → v1.0.6" while an update waits, otherwise the latest "v1.0.6 · build 7 · 177 MB". */
private fun heroVersionLine(app: StoreApp): String {
    val installed = app.installed
    val latest = app.latest
    return if (app.status == AppStatus.UpdateAvailable && installed != null) {
        val from = installed.versionName?.let { "v$it" } ?: "build ${installed.versionCode}"
        "$from → v${latest.versionName}"
    } else {
        Formatters.versionLine(latest.versionName, latest.versionCode, latest.size)
    }
}

@Composable
private fun AutoUpdateBadge() {
    val color = MaterialTheme.statusColors.success
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Updates install automatically", style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// --- Status + primary action --------------------------------------------------------------------

@Composable
private fun StatusCard(
    app: StoreApp,
    installState: InstallState,
    silent: Boolean,
    canOpen: Boolean,
    isSelf: Boolean,
    deviceSdk: Int,
    actions: DetailActions,
    modifier: Modifier = Modifier,
) {
    val accent = app.status.color()
    val explanation = statusExplanation(app, deviceSdk, silent)

    // Keep the last in-progress state so the progress block can animate out with content.
    var lastActive by remember { mutableStateOf<InstallState>(InstallState.Idle) }
    val showsProgress = installState.showsProgress
    LaunchedEffect(installState) { if (installState.showsProgress) lastActive = installState }

    PandaCard(modifier = modifier.fillMaxWidth(), accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(app.status.icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(explanation.title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            explanation.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.animateContentSize(),
        )

        val failure = installState as? InstallState.Failed
        var lastFailure by remember { mutableStateOf("") }
        LaunchedEffect(failure) { failure?.let { lastFailure = it.message } }
        AnimatedVisibility(
            visible = failure != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            InfoBanner(
                icon = Icons.Outlined.ErrorOutline,
                text = failure?.message ?: lastFailure,
                color = MaterialTheme.colorScheme.error,
                actionLabel = "Dismiss",
                onAction = actions.onDismissError,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        AnimatedVisibility(
            visible = showsProgress,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DownloadProgress(
                state = if (showsProgress) installState else lastActive,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        ActionRow(
            app = app,
            installState = installState,
            canOpen = canOpen,
            isSelf = isSelf,
            actions = actions,
        )
    }
}

/** States that [DownloadProgress] draws something for. */
private val InstallState.showsProgress: Boolean
    get() = isBusy || this == InstallState.PendingUserAction

/** What the action row shows; drives its enter/exit animation. */
private enum class RowKind { Install, Update, Retry, Confirm, Progress, Done, Open, UninstallFirst, Idle, Unavailable }

@Composable
private fun ActionRow(
    app: StoreApp,
    installState: InstallState,
    canOpen: Boolean,
    isSelf: Boolean,
    actions: DetailActions,
) {
    val canUninstall = app.installed != null && !isSelf
    val action = installActionFor(app.status, installState, canOpen)
    val kind = when (action) {
        InstallAction.Install -> RowKind.Install
        InstallAction.Update -> RowKind.Update
        InstallAction.Retry -> RowKind.Retry
        InstallAction.Confirm -> RowKind.Confirm
        InstallAction.Progress -> RowKind.Progress
        InstallAction.Done -> RowKind.Done
        InstallAction.Unavailable -> RowKind.Unavailable
        InstallAction.Open, InstallAction.None -> when {
            app.status == AppStatus.SignerMismatch && canUninstall -> RowKind.UninstallFirst
            action == InstallAction.Open -> RowKind.Open
            else -> RowKind.Idle
        }
    }

    AnimatedContent(
        targetState = kind,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 })
                .togetherWith(fadeOut(tween(120)))
        },
        contentAlignment = Alignment.Center,
        label = "detailActionRow",
    ) { current ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val grow = Modifier.weight(1f)
            when (current) {
                RowKind.Install ->
                    PandaButton("Install", actions.onInstall, grow, icon = Icons.Filled.Download)

                RowKind.Update ->
                    PandaButton("Update", actions.onInstall, grow, icon = Icons.Filled.Upgrade)

                RowKind.Retry ->
                    PandaButton("Retry", actions.onRetry, grow, icon = Icons.Filled.Refresh, style = PandaButtonStyle.Danger)

                RowKind.Confirm -> {
                    PandaButton(
                        "Finish installing", actions.onConfirm, grow,
                        icon = Icons.Filled.TouchApp, style = PandaButtonStyle.Tonal,
                    )
                    PandaButton("Cancel", actions.onCancel, icon = Icons.Filled.Close, style = PandaButtonStyle.Outlined)
                }

                RowKind.Progress ->
                    // Appears where Install/Update was: ignore the tail of a double tap on that button.
                    PandaButton(
                        "Cancel", rememberArmedClick(actions.onCancel), grow,
                        icon = Icons.Filled.Close, style = PandaButtonStyle.Outlined,
                    )

                RowKind.Done -> {
                    InstalledPill(grow)
                    if (canOpen) {
                        PandaButton(
                            "Open", actions.onOpen,
                            icon = Icons.AutoMirrored.Filled.OpenInNew, style = PandaButtonStyle.Tonal,
                        )
                    }
                }

                RowKind.Open ->
                    PandaButton(
                        "Open", actions.onOpen, grow,
                        icon = Icons.AutoMirrored.Filled.OpenInNew, style = PandaButtonStyle.Tonal,
                    )

                RowKind.UninstallFirst -> {
                    PandaButton(
                        "Uninstall", actions.onUninstall, grow,
                        icon = Icons.Outlined.Delete, style = PandaButtonStyle.Danger,
                    )
                    if (canOpen) {
                        PandaButton(
                            "Open", actions.onOpen,
                            icon = Icons.AutoMirrored.Filled.OpenInNew, style = PandaButtonStyle.Tonal,
                        )
                    }
                }

                RowKind.Unavailable ->
                    PandaButton("Not compatible", {}, grow, style = PandaButtonStyle.Outlined, enabled = false)

                RowKind.Idle -> when {
                    canUninstall -> PandaButton(
                        "Uninstall", actions.onUninstall, grow,
                        icon = Icons.Outlined.Delete, style = PandaButtonStyle.Outlined,
                    )
                    isSelf -> Text(
                        "This is the store you're using right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = grow,
                    )
                    else -> Spacer(grow)
                }
            }

            // Secondary Uninstall next to the primary action when the app is installed.
            val secondaryUninstall = canUninstall &&
                current in setOf(RowKind.Update, RowKind.Retry, RowKind.Open, RowKind.Unavailable)
            if (secondaryUninstall) {
                PandaButton(
                    "Uninstall", actions.onUninstall,
                    icon = Icons.Outlined.Delete, style = PandaButtonStyle.Outlined,
                )
            }
        }
    }
}

@Composable
private fun InstalledPill(modifier: Modifier = Modifier) {
    val color = MaterialTheme.statusColors.success
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Installed", style = MaterialTheme.typography.labelLarge, color = color)
    }
}

private val AppStatus.icon: ImageVector
    get() = when (this) {
        AppStatus.NotInstalled -> Icons.Outlined.Info
        AppStatus.UpToDate -> Icons.Outlined.CheckCircle
        AppStatus.UpdateAvailable -> Icons.Outlined.NewReleases
        AppStatus.InstalledNewer -> Icons.Outlined.Science
        AppStatus.SignerMismatch -> Icons.Outlined.GppBad
        AppStatus.Incompatible -> Icons.Outlined.Block
    }

// --- Facts --------------------------------------------------------------------------------------

@Composable
private fun FactsCard(app: StoreApp, selfPackage: String, modifier: Modifier = Modifier) {
    val latest = app.latest
    val installed = app.installed
    val releasedAt = Formatters.parseIsoInstant(latest.releasedAt)

    PandaCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        FactRow(
            label = "Installed",
            value = installed?.let { versionWithBuild(it.versionName, it.versionCode) } ?: "Not installed",
            valueColor = if (installed == null) MaterialTheme.colorScheme.onSurfaceVariant else null,
        )
        FactDivider()
        FactRow(
            label = "Latest",
            value = versionWithBuild(latest.versionName, latest.versionCode),
            valueColor = if (app.status == AppStatus.UpdateAvailable) MaterialTheme.statusColors.downloading else null,
        )
        latest.size?.takeIf { it > 0 }?.let {
            FactDivider()
            FactRow(label = "Download size", value = Formatters.bytes(it))
        }
        releasedAt?.let {
            FactDivider()
            FactRow(label = "Released", value = "${Formatters.date(it)} · ${Formatters.relativeTime(it)}")
        }
        requirementText(latest.minSdk)?.let {
            FactDivider()
            FactRow(label = "Requires", value = it)
        }
        if (installed != null) {
            FactDivider()
            FactRow(label = "Installed by", value = installerLabel(installed.installerPackage, selfPackage))
        }
    }
}

/** Friendly name of the installer of record. */
private fun installerLabel(installer: String?, selfPackage: String): String = when (installer) {
    null -> "Unknown (adb or file manager)"
    selfPackage -> "Panda App Store"
    "com.android.vending" -> "Google Play"
    "com.google.android.packageinstaller", "com.android.packageinstaller" -> "Android package installer"
    else -> installer
}

@Composable
private fun FactRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FactDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

// --- Releases -----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseCard(
    appName: String,
    release: CatalogRelease,
    isLatest: Boolean,
    isPrevious: Boolean,
    isInstalled: Boolean,
    offerRollback: Boolean,
    rollbackBlockedReason: String?,
    onRollback: () -> Unit,
    offerInstall: Boolean,
    installBlockedReason: String?,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.statusColors
    val releasedAt = Formatters.parseIsoInstant(release.releasedAt)
    var expanded by rememberSaveable(release.versionCode) { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }

    PandaCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        accent = if (isLatest) MaterialTheme.colorScheme.primary else null,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "v${release.versionName}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Text(
                "build ${release.versionCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (isLatest) ReleaseBadge("Latest", MaterialTheme.colorScheme.primary)
            if (isPrevious) ReleaseBadge("Previous", colors.neutral)
            if (isInstalled) ReleaseBadge("Installed", colors.success)
        }

        val meta = listOfNotNull(
            releasedAt?.let { Formatters.date(it) },
            release.size?.takeIf { it > 0 }?.let { Formatters.bytes(it) },
            requirementText(release.minSdk),
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(10.dp))
        val notes = release.notes?.trim()?.takeIf { it.isNotEmpty() }
        Text(
            notes ?: "No release notes.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (notes == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else NOTES_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }

        if (offerRollback) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text(
                "Android can't install an older version over a newer one. To go back to " +
                    "v${release.versionName}, $appName is uninstalled first (its data on this device " +
                    "is deleted), then this version installs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rollbackBlockedReason?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.warning)
            }
            Spacer(Modifier.height(12.dp))
            PandaButton(
                "Uninstall & install this version",
                onRollback,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.History,
                style = PandaButtonStyle.Outlined,
                enabled = rollbackBlockedReason == null,
            )
        }

        if (offerInstall) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text(
                "$appName isn't installed, so you can install this older version instead of the latest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            installBlockedReason?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.warning)
            }
            Spacer(Modifier.height(12.dp))
            PandaButton(
                "Install v${release.versionName}",
                onInstall,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Download,
                style = PandaButtonStyle.Outlined,
                enabled = installBlockedReason == null,
            )
        }
    }
}

@Composable
private fun ReleaseBadge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private const val NOTES_COLLAPSED_LINES = 6

// --- Dialogs ------------------------------------------------------------------------------------

@Composable
private fun UninstallDialog(app: StoreApp, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val reinstallHint = if (app.status == AppStatus.SignerMismatch) {
        " Once it's gone, come back here and tap Install to get the store's build."
    } else {
        " You can install it again from Panda App Store at any time."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text("Uninstall ${app.name}?") },
        text = { Text("This removes ${app.name} and its data from this device.$reinstallHint") },
        confirmButton = { PandaButton("Uninstall", onConfirm, style = PandaButtonStyle.Danger) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}

@Composable
private fun RollbackDialog(app: StoreApp, release: CatalogRelease, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val target = versionWithBuild(release.versionName, release.versionCode)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.History, contentDescription = null) },
        title = { Text("Go back to v${release.versionName}?") },
        text = {
            Text(
                "Android can't downgrade an app in place, so this happens in two steps:\n\n" +
                    "1. ${app.name} is uninstalled — its data on this device is deleted.\n" +
                    "2. As soon as it's gone, $target downloads and installs.\n\n" +
                    "Android will ask you to confirm both steps.",
            )
        },
        confirmButton = { PandaButton("Uninstall & install", onConfirm, style = PandaButtonStyle.Danger) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- Loading / not found ------------------------------------------------------------------------

@Composable
private fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotFound(
    packageName: String,
    error: String?,
    isOffline: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (error != null) {
            EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = "Couldn't load the catalog",
                message = error,
                tint = MaterialTheme.colorScheme.error,
                actionLabel = "Try again",
                onAction = onRetry,
            )
        } else {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = "App not found",
                message = if (packageName.isBlank()) {
                    "This link doesn't name an app."
                } else {
                    "$packageName isn't in your store's catalog. It may have been removed, or a new " +
                        "release hasn't reached the catalog yet — that can take a few minutes." +
                        if (isOffline) " You're offline, so the catalog may be out of date." else ""
                },
                actionLabel = "Check again",
                onAction = onRetry,
            )
        }
        PandaButton("Back to store", onBack, style = PandaButtonStyle.Text)
    }
}
