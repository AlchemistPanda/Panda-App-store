package com.pandaapps.appstore.ui.home

import android.content.ActivityNotFoundException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.data.StoreApp
import com.pandaapps.appstore.install.isBusy
import com.pandaapps.appstore.ui.StoreUiState
import com.pandaapps.appstore.ui.StoreViewModel
import com.pandaapps.appstore.ui.components.EmptyState
import com.pandaapps.appstore.ui.components.InfoBanner
import com.pandaapps.appstore.ui.components.PandaButton
import com.pandaapps.appstore.ui.components.SectionHeader
import com.pandaapps.appstore.ui.theme.statusColors
import com.pandaapps.appstore.util.Formatters
import kotlinx.coroutines.delay

/** Home list filters. */
enum class HomeFilter(val label: String) {
    All("All"),
    Updates("Updates"),
    Installed("Installed"),
    NotInstalled("Not installed");

    fun matches(app: StoreApp): Boolean = when (this) {
        All -> true
        Updates -> app.status == AppStatus.UpdateAvailable
        Installed -> app.installed != null
        NotInstalled -> app.installed == null
    }
}

/**
 * Store home: updates section with "Update all", search, filters, pull-to-refresh and the app list.
 * Long-press the title to open the debug console.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: StoreViewModel,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(HomeFilter.All) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val actions = remember(viewModel, onOpenApp) {
        AppRowActions(
            onClick = onOpenApp,
            onInstall = viewModel::install,
            onRetry = viewModel::retry,
            onOpen = viewModel::open,
            onCancel = viewModel::cancel,
            onConfirm = viewModel::resumePendingInstall,
            onDismissError = viewModel::dismissInstallResult,
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HomeTopBar(
                state = state,
                onRefresh = viewModel::refresh,
                onOpenSettings = onOpenSettings,
                onOpenDebug = onOpenDebug,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        // While the keyboard is up it covers the navigation bar; imePadding() already lifts the list.
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val listBottomPadding = (padding.calculateBottomPadding() - imeBottom).coerceAtLeast(0.dp)
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                // Edge-to-edge: stay clear of the top bar and of side system bars / cutouts
                // (landscape 3-button nav); the bottom inset is list content padding instead.
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                )
                // The keyboard (search) is not resized for under edge-to-edge: shrink the list
                // above it, minus the insets the Scaffold padding already accounts for.
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            HomeList(
                state = state,
                query = query,
                onQueryChange = { query = it },
                filter = filter,
                onFilterChange = { filter = it },
                actions = actions,
                onUpdateAll = viewModel::updateAll,
                onRetry = viewModel::refresh,
                onAllowInstalls = {
                    try {
                        context.startActivity(viewModel.unknownSourcesSettingsIntent())
                    } catch (_: ActivityNotFoundException) {
                        // Nothing to open on this device; the dialog path explains the permission.
                    }
                },
                bottomPadding = listBottomPadding,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeTopBar(
    state: StoreUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    TopAppBar(
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        title = {
            Column(
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onOpenDebug,
                    onLongClickLabel = "Open debug console",
                ),
            ) {
                Text("Panda App Store", style = MaterialTheme.typography.titleLarge)
                val subtitle = when {
                    state.isRefreshing -> "Checking for updates…"
                    state.lastChecked != null -> "Checked ${Formatters.relativeTime(state.lastChecked, now)}"
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                RefreshIcon(spinning = state.isRefreshing)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        },
    )
}

@Composable
private fun RefreshIcon(spinning: Boolean) {
    if (spinning) {
        val transition = rememberInfiniteTransition(label = "refreshSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "refreshAngle",
        )
        Icon(Icons.Filled.Refresh, contentDescription = "Refreshing", modifier = Modifier.rotate(angle))
    } else {
        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
    }
}

@Composable
private fun HomeList(
    state: StoreUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: HomeFilter,
    onFilterChange: (HomeFilter) -> Unit,
    actions: AppRowActions,
    onUpdateAll: () -> Unit,
    onRetry: () -> Unit,
    onAllowInstalls: () -> Unit,
    bottomPadding: Dp,
) {
    val trimmed = query.trim()
    val visible = remember(state.apps, trimmed, filter) {
        state.apps.filter { app ->
            filter.matches(app) && (
                trimmed.isEmpty() ||
                    app.name.contains(trimmed, ignoreCase = true) ||
                    app.packageName.contains(trimmed, ignoreCase = true)
                )
        }
    }
    val showUpdatesSection = filter == HomeFilter.All && trimmed.isEmpty() && state.updates.isNotEmpty()
    val noCatalog = state.apps.isEmpty() && !state.isLoading

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "banners", contentType = "banners") {
            Banners(state = state, onRetry = onRetry, onAllowInstalls = onAllowInstalls)
        }

        if (noCatalog && state.error != null) {
            item(key = "error", contentType = "empty") {
                EmptyState(
                    icon = Icons.Outlined.WifiOff,
                    title = "Couldn't load the store",
                    message = state.error,
                    tint = MaterialTheme.statusColors.error,
                    actionLabel = "Try again",
                    onAction = onRetry,
                    modifier = Modifier.animateItem(),
                )
            }
            return@LazyColumn
        }

        item(key = "search", contentType = "search") {
            SearchAndFilters(
                query = query,
                onQueryChange = onQueryChange,
                filter = filter,
                onFilterChange = onFilterChange,
                counts = HomeFilter.entries.associateWith { f -> state.apps.count(f::matches) },
            )
        }

        if (state.isLoading) {
            items(count = 4, key = { "skeleton$it" }, contentType = { "skeleton" }) {
                SkeletonRow(Modifier.padding(horizontal = 16.dp))
            }
            return@LazyColumn
        }

        if (showUpdatesSection) {
            val allBusy = state.updates.all { state.installState(it.packageName).isBusy }
            item(key = "updatesHeader", contentType = "header") {
                SectionHeader(
                    title = "Updates available",
                    count = state.updatesCount,
                    modifier = Modifier.animateItem(),
                ) {
                    PandaButton(
                        text = if (allBusy) "Updating…" else "Update all",
                        onClick = onUpdateAll,
                        icon = Icons.Filled.SystemUpdate,
                        enabled = !allBusy,
                        compact = true,
                    )
                }
            }
            items(state.updates, key = { "u:${it.packageName}" }, contentType = { "app" }) { app ->
                AppListItem(
                    app = app,
                    installState = state.installState(app.packageName),
                    canOpen = state.canOpen(app.packageName),
                    actions = actions,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
        }

        item(key = "allHeader", contentType = "header") {
            SectionHeader(
                title = if (filter == HomeFilter.All) "All apps" else filter.label,
                count = visible.size,
                modifier = Modifier
                    .padding(top = if (showUpdatesSection) 8.dp else 0.dp)
                    .animateItem(),
            )
        }

        if (visible.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                val (title, message) = when {
                    state.apps.isEmpty() -> "No apps yet" to "Publish an app with panda-publish and it will show up here."
                    trimmed.isNotEmpty() -> "No matches" to "Nothing matches \"$trimmed\"."
                    filter == HomeFilter.Updates -> "All up to date" to "Every installed app is on its latest version."
                    filter == HomeFilter.Installed -> "Nothing installed yet" to "Install an app from the All tab."
                    else -> "Everything's installed" to "Every app in the store is already on this device."
                }
                EmptyState(
                    icon = when {
                        trimmed.isNotEmpty() -> Icons.Filled.SearchOff
                        filter == HomeFilter.Updates -> Icons.Filled.SystemUpdate
                        else -> Icons.Outlined.Inventory2
                    },
                    title = title,
                    message = message,
                    modifier = Modifier.animateItem(),
                )
            }
        } else {
            items(visible, key = { "a:${it.packageName}" }, contentType = { "app" }) { app ->
                AppListItem(
                    app = app,
                    installState = state.installState(app.packageName),
                    canOpen = state.canOpen(app.packageName),
                    actions = actions,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
        }
    }
}

@Composable
private fun Banners(state: StoreUiState, onRetry: () -> Unit, onAllowInstalls: () -> Unit) {
    val hasApps = state.apps.isNotEmpty()
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = state.isOffline && hasApps,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val since = state.lastChecked?.let { " from ${Formatters.relativeTime(it)}" }.orEmpty()
            InfoBanner(
                icon = Icons.Filled.CloudOff,
                text = "Offline — showing saved catalog$since. ${state.error.orEmpty()}".trim(),
                color = MaterialTheme.statusColors.warning,
                actionLabel = "Retry",
                onAction = onRetry,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        AnimatedVisibility(
            visible = !state.canInstallPackages && hasApps,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            InfoBanner(
                icon = Icons.Filled.Security,
                text = "Allow \"Install unknown apps\" so Panda App Store can install and update your apps.",
                color = MaterialTheme.statusColors.downloading,
                actionLabel = "Allow",
                onAction = onAllowInstalls,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SearchAndFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: HomeFilter,
    onFilterChange: (HomeFilter) -> Unit,
    counts: Map<HomeFilter, Int>,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(HomeFilter.entries, key = { it.name }) { f ->
                val count = counts[f] ?: 0
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilterChange(f) },
                    label = { Text(if (f == HomeFilter.All) f.label else "${f.label} · $count") },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == f,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        selectedBorderWidth = 1.2.dp,
                    ),
                )
            }
        }
    }
}

/** Pulsing placeholder row shown while the first catalog loads. */
@Composable
private fun SkeletonRow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).background(block, MaterialTheme.shapes.medium))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).background(block, CircleShape))
            Box(Modifier.fillMaxWidth(0.8f).height(10.dp).background(block, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(width = 76.dp, height = 36.dp).background(block, CircleShape))
    }
}
