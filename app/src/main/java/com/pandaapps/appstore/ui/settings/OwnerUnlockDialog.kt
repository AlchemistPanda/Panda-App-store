package com.pandaapps.appstore.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pandaapps.appstore.data.OwnerAccess
import com.pandaapps.appstore.ui.components.PandaButton
import kotlinx.coroutines.launch

/**
 * The hidden owner unlock, opened by tapping the version row 7 times. Step 1 asks for the passcode;
 * with a token already saved that is all it takes. Otherwise (or with [askTokenFirst], to replace a
 * token) step 2 takes the GitHub token, which [onEnable] checks against the private catalog.
 *
 * @param onEnable returns null on success or a message to show.
 */
@Composable
internal fun OwnerUnlockDialog(
    savedToken: String?,
    askTokenFirst: Boolean,
    onEnable: suspend (token: String) -> String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var askToken by remember { mutableStateOf(askTokenFirst) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun tryToken(token: String) {
        busy = true
        error = null
        scope.launch {
            val failure = onEnable(token)
            busy = false
            if (failure == null) {
                onDismiss()
            } else {
                askToken = true
                input = ""
                error = failure
            }
        }
    }

    fun submit() {
        if (busy) return
        when {
            askToken -> input.trim().takeIf { it.isNotEmpty() }?.let(::tryToken)
            !OwnerAccess.isPasscode(input) -> {
                input = ""
                error = "Wrong passcode"
            }
            savedToken != null -> tryToken(savedToken)
            else -> {
                askToken = true
                input = ""
                error = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text(if (askToken) "GitHub token" else "Passcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (askToken) {
                    Text(
                        "Paste a fine-grained GitHub token with read-only Contents access to " +
                            "${OwnerAccess.PRIVATE_REPO}. It is saved on this phone only.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    placeholder = { Text(if (askToken) "github_pat_…" else "••••") },
                    textStyle = if (askToken) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (askToken) KeyboardType.Password else KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            PandaButton(if (askToken) "Unlock" else "OK", ::submit, enabled = !busy && input.isNotBlank())
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
