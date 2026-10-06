package com.pandaapps.appstore.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pandaapps.appstore.ui.components.PandaButton
import com.pandaapps.appstore.ui.components.PandaButtonStyle
import com.pandaapps.appstore.ui.components.PandaCard
import com.pandaapps.appstore.ui.theme.MonoTextStyle
import com.pandaapps.appstore.ui.theme.statusColors

/**
 * "How to get instant notifications": `panda-publish` pushes to an ntfy topic whose name lives only
 * in the Mac's `~/.config/panda-store/config.json`, so the app explains how to subscribe to it.
 *
 * @param ntfyInstalled switches the button between "Get ntfy" and "Open ntfy".
 */
@Composable
internal fun NtfyCard(
    ntfyInstalled: Boolean,
    onGetNtfy: () -> Unit,
    onOpenNtfy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.statusColors.downloading
    PandaCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        accent = accent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RowIcon(Icons.Filled.NotificationsActive, tint = accent)
            Column(Modifier.weight(1f)) {
                Text("Get updates instantly", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Background checks run every few hours. For a push the moment you publish, " +
                        "subscribe to your store's topic in the free ntfy app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Step(1) {
                Text(
                    if (ntfyInstalled) "ntfy is installed." else "Install ntfy from the Play Store.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Step(2) {
                Text("On your Mac, find your topic name:", style = MaterialTheme.typography.bodyMedium)
                CodeBlock("cat ~/.config/panda-store/config.json")
                Text(
                    buildAnnotatedString {
                        append("It's the value of ")
                        mono("ntfyTopic")
                        append(". The server is ")
                        mono("ntfy.sh")
                        append(" unless ")
                        mono("ntfyServer")
                        append(" says otherwise.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Step(3) {
                Text(
                    "In ntfy, tap + and subscribe to that topic.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Step(4) {
                Text(
                    "Each publish now pings your phone. Tap the notification to open that app here, " +
                        "ready to update.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            if (ntfyInstalled) {
                PandaButton(
                    "Open ntfy",
                    onOpenNtfy,
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    style = PandaButtonStyle.Tonal,
                    compact = true,
                )
            } else {
                PandaButton("Get ntfy", onGetNtfy, icon = Icons.Filled.Download, compact = true)
            }
        }
    }
}

@Composable
private fun Step(number: Int, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Text(
        code,
        style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

private fun AnnotatedString.Builder.mono(text: String) {
    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)) { append(text) }
}
