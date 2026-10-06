package com.pandaapps.appstore.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.data.StoreApp
import com.pandaapps.appstore.install.InstallState
import com.pandaapps.appstore.install.isBusy
import com.pandaapps.appstore.ui.components.AppIcon
import com.pandaapps.appstore.ui.components.DownloadProgress
import com.pandaapps.appstore.ui.components.InstallActionButton
import com.pandaapps.appstore.ui.components.PandaCard
import com.pandaapps.appstore.ui.components.StatusChip
import com.pandaapps.appstore.util.Formatters

/** Callbacks for a row's actions, keyed by package name. */
internal class AppRowActions(
    val onClick: (String) -> Unit,
    val onInstall: (String) -> Unit,
    /** Retry a failed install (repeats the release it was for, e.g. a rollback target). */
    val onRetry: (String) -> Unit,
    val onOpen: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onConfirm: (String) -> Unit,
    val onDismissError: (String) -> Unit,
)

/** One app in the home list: icon, name, version line, status chip, primary action, live progress. */
@Composable
internal fun AppListItem(
    app: StoreApp,
    installState: InstallState,
    canOpen: Boolean,
    actions: AppRowActions,
    modifier: Modifier = Modifier,
) {
    val pkg = app.packageName
    PandaCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
        onClick = { actions.onClick(pkg) },
        accent = if (app.status == AppStatus.UpdateAvailable) MaterialTheme.colorScheme.primary else null,
        contentPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app = app, size = 52.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    versionText(app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                StatusChip(status = app.status, installState = installState)
            }
            InstallActionButton(
                status = app.status,
                installState = installState,
                onInstall = { actions.onInstall(pkg) },
                onRetry = { actions.onRetry(pkg) },
                onOpen = { actions.onOpen(pkg) },
                onCancel = { actions.onCancel(pkg) },
                onConfirm = { actions.onConfirm(pkg) },
                canOpen = canOpen,
            )
        }

        AnimatedVisibility(
            visible = installState.isBusy,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            DownloadProgress(state = installState, modifier = Modifier.padding(top = 12.dp))
        }

        AnimatedVisibility(
            visible = installState is InstallState.Failed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val message = (installState as? InstallState.Failed)?.message.orEmpty()
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                IconButton(onClick = { actions.onDismissError(pkg) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** "v1.0.6 · build 7 · 177 MB", prefixed with the installed version when an update is waiting. */
private fun versionText(app: StoreApp): String {
    val latest = Formatters.versionLine(app.latest.versionName, app.latest.versionCode, app.latest.size)
    val installedName = app.installed?.versionName
    return if (app.status == AppStatus.UpdateAvailable && installedName != null) "v$installedName → $latest" else latest
}
