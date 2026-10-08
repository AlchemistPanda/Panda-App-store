package com.pandagallery.app.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pandagallery.app.data.update.AppUpdateChecker
import com.pandagallery.app.domain.update.AvailableUpdate

/**
 * Looks for a newer build once when the app opens, and offers it in a dialog if there is one.
 * A failed or throttled check shows nothing.
 */
@Composable
fun StartupUpdatePrompt() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }

    LaunchedEffect(Unit) {
        update = AppUpdateChecker.checkOnStartup(context)
    }

    update?.let { available ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Update available") },
            text = {
                Text(updateMessage(available))
            },
            confirmButton = {
                TextButton(onClick = {
                    AppUpdateChecker.openUpdate(context, available)
                    update = null
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    AppUpdateChecker.skipVersion(context, available.versionCode)
                    update = null
                }) {
                    Text("Skip this version")
                }
            },
        )
    }
}

private fun updateMessage(update: AvailableUpdate): String {
    val size = if (update.size > 0) " · ${update.size / (1024 * 1024)} MB" else ""
    val notes = if (update.notes.isNotEmpty()) "\n\n${update.notes}" else ""
    return "Version ${update.versionName} (build ${update.versionCode})$size is ready.$notes" +
        "\n\nUpdating opens Panda App Store, which installs it over this one. Your photos are kept."
}
