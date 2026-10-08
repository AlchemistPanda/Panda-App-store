package com.pandagallery.app.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandagallery.app.data.diagnostics.CrashLog

/** Settings › Diagnostics: the crash & error log, with enable, view, share and clear. */
@Composable
internal fun DiagnosticsSection() {
    val context = LocalContext.current
    val enabled by CrashLog.enabled.collectAsStateWithLifecycle()
    val revision by CrashLog.revision.collectAsStateWithLifecycle()
    val logSize = remember(revision) { CrashLog.read().length }
    var showLog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    SettingsSection(title = "DIAGNOSTICS") {
        SettingsItem(
            icon = Icons.Outlined.BugReport,
            iconBgColor = Color(0xFFE53935),
            title = "Crash & error log",
            subtitle = if (enabled) {
                "On · Records crashes and failed operations on this phone"
            } else {
                "Off · Nothing is being recorded"
            },
            isToggle = true,
            isToggled = enabled,
            onToggle = CrashLog::setEnabled,
        )
        SettingsItem(
            icon = Icons.Outlined.Description,
            iconBgColor = Color(0xFF546E7A),
            title = "View log",
            subtitle = if (logSize == 0) "Empty" else "${formatLogSize(logSize)} recorded",
            onClick = { showLog = true },
        )
        SettingsItem(
            icon = Icons.Outlined.Share,
            iconBgColor = Color(0xFF1E88E5),
            title = "Share log",
            subtitle = "Send the log as a text file (Quick Share, email, chat…)",
            onClick = { shareCrashLog(context) },
        )
        SettingsItem(
            icon = Icons.Outlined.DeleteSweep,
            iconBgColor = Color(0xFF8D6E63),
            title = "Clear log",
            subtitle = "Delete every recorded entry",
            onClick = { confirmClear = true },
        )
    }

    if (showLog) {
        CrashLogDialog(
            revision = revision,
            onDismiss = { showLog = false },
            onShare = { shareCrashLog(context) },
            onClear = { confirmClear = true },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear log?") },
            text = { Text("Every recorded crash and error will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    CrashLog.clear()
                    confirmClear = false
                    Toast.makeText(context, "Log cleared", Toast.LENGTH_SHORT).show()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashLogDialog(
    revision: Int,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    val text = remember(revision) { CrashLog.read() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Crash & error log") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share log")
                        }
                        IconButton(onClick = onClear, enabled = text.isNotEmpty()) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear log")
                        }
                    },
                )
            },
        ) { padding ->
            if (text.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        "No crashes or errors recorded",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SelectionContainer {
                    Text(
                        text = text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun shareCrashLog(context: Context) {
    runCatching {
        val file = CrashLog.exportForSharing()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "Couldn't share the log: ${it.message}", Toast.LENGTH_LONG).show()
    }
}

private fun formatLogSize(chars: Int): String =
    if (chars < 1024) "$chars B" else "${chars / 1024} KB"
