package com.pandaapps.appstore.ui.debug

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandaapps.appstore.BuildConfig
import com.pandaapps.appstore.appContainer
import com.pandaapps.appstore.ui.components.EmptyState
import com.pandaapps.appstore.ui.components.PandaButton
import com.pandaapps.appstore.ui.components.PandaButtonStyle
import com.pandaapps.appstore.ui.theme.MonoTextStyle
import com.pandaapps.appstore.ui.theme.StatusColors
import com.pandaapps.appstore.ui.theme.statusColors
import com.pandaapps.appstore.util.AppLog
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Brand-mandated Debug console: the live [AppLog] buffer in monospace, following new lines while
 * scrolled to the bottom, with copy-all, share and clear. Reachable from Settings and by
 * long-pressing the home title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appLog = remember(context) { context.appContainer.appLog }
    val lines by appLog.lines.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Follow the tail until the user scrolls up; resume once they are back at the bottom.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { inProgress -> !inProgress }
            .collect { follow = !listState.canScrollForward }
    }
    // Keyed on the list itself: once the ring buffer is full its size stays the same.
    LaunchedEffect(lines) {
        if (follow && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Debug console", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (lines.size == 1) "1 line" else "${lines.size} lines",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val hasLines = lines.isNotEmpty()
                    IconButton(
                        enabled = hasLines,
                        onClick = {
                            copyToClipboard(context, logReport(appLog))
                            // Android 13+ shows its own clipboard confirmation.
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                scope.launch { snackbarHostState.showSnackbar("Copied ${lines.size} lines") }
                            }
                        },
                    ) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy all") }
                    IconButton(
                        enabled = hasLines,
                        onClick = {
                            if (!shareLog(context, logReport(appLog))) {
                                scope.launch { snackbarHostState.showSnackbar("No app can share text") }
                            }
                        },
                    ) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                    IconButton(enabled = hasLines, onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !follow && lines.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        follow = true
                        scope.launch { listState.animateScrollToItem(lines.lastIndex.coerceAtLeast(0)) }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Jump to latest") }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (lines.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Terminal,
                    title = "No log lines yet",
                    message = "Catalog checks, downloads and installs are logged here as they happen.",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                val colors = MaterialTheme.colorScheme
                val status = MaterialTheme.statusColors
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .background(colors.surfaceContainerLowest, MaterialTheme.shapes.medium),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        items(lines, contentType = { "line" }) { line ->
                            Text(
                                text = remember(line, colors, status) {
                                    styledLine(LogLine.parse(line), colors.onSurface, colors.onSurfaceVariant, status)
                                },
                                style = MonoTextStyle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
            title = { Text("Clear the log?") },
            text = { Text("This removes all ${lines.size} lines. Copy or share them first if you need them.") },
            confirmButton = {
                PandaButton(
                    "Clear",
                    onClick = {
                        appLog.clear()
                        follow = true
                        confirmClear = false
                    },
                    style = PandaButtonStyle.Danger,
                )
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** Time dimmed, `L/Tag:` coloured by level, warnings and errors tinted through the message. */
private fun styledLine(line: LogLine, text: Color, dim: Color, status: StatusColors): AnnotatedString {
    val levelColor = when (line.level) {
        'E', 'A' -> status.error
        'W' -> status.warning
        'I' -> status.success
        else -> dim
    }
    val messageColor = when (line.level) {
        'E', 'A' -> status.error
        'W' -> status.warning
        'D', 'V' -> dim
        else -> text
    }
    return buildAnnotatedString {
        if (line.time != null) {
            withStyle(SpanStyle(color = dim)) { append(line.time) }
            append(' ')
        }
        if (line.level != null) {
            withStyle(SpanStyle(color = levelColor, fontWeight = FontWeight.Bold)) {
                append(line.level)
                append('/')
                append(line.tag.orEmpty())
                append(':')
            }
            append(' ')
        }
        withStyle(SpanStyle(color = messageColor)) { append(line.message) }
    }
}

/** The log with a one-line device/app header, for copy and share. */
private fun logReport(appLog: AppLog): String = buildString {
    append("Panda App Store ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    append(" · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    append(" · ${Build.MANUFACTURER} ${Build.MODEL}\n\n")
    append(appLog.dump())
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Panda App Store log", text))
}

/** Opens the system share sheet. Returns false when nothing can handle it. */
private fun shareLog(context: Context, text: String): Boolean {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Panda App Store log")
        .putExtra(Intent.EXTRA_TEXT, text)
    return try {
        context.startActivity(Intent.createChooser(send, "Share log"))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
