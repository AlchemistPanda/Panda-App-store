package com.pandagallery.app.ui.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandagallery.app.domain.security.MAX_PIN_LENGTH
import com.pandagallery.app.domain.security.MIN_PIN_LENGTH
import com.pandagallery.app.domain.security.isValidPin
import com.pandagallery.app.ui.components.PremiumAlertDialog

/**
 * Asks for a PIN that already exists.
 *
 * The keypad is the app's own rather than a text field with a numeric keyboard: a password field
 * routes every digit through the system IME, where autofill, clipboard suggestions and third-party
 * keyboards all get a look at it. Here the digits never leave this composable except through
 * [onSubmit].
 *
 * @param lockoutMessage set while a wrong-attempt lockout is counting down, which disables entry.
 */
@Composable
internal fun PinVerifyDialog(
    title: String,
    message: String,
    error: String?,
    lockoutMessage: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    onUseBiometrics: (() -> Unit)? = null,
) {
    var entry by remember { mutableStateOf("") }
    val locked = lockoutMessage != null

    PinScaffold(
        icon = Icons.Outlined.Lock,
        title = title,
        message = message,
        entryLength = entry.length,
        error = lockoutMessage ?: error,
        enabled = !locked,
        confirmLabel = "Unlock",
        onDigit = { entry += it },
        onBackspace = { entry = entry.dropLast(1) },
        onConfirm = {
            val submitted = entry
            entry = ""
            onSubmit(submitted)
        },
        onDismiss = onDismiss,
        footer = if (onUseBiometrics != null && !locked) {
            {
                TextButton(onClick = onUseBiometrics) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Use fingerprint",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            null
        },
    )
}

/**
 * Chooses a new PIN, asking for it twice.
 *
 * The second entry is not politeness: a mistyped PIN that is never recoverable would lock the user
 * out of their own folder on the first attempt, and the warning text says as much before they
 * commit. [onPinChosen] only fires once both entries agree.
 */
@Composable
internal fun PinSetupDialog(
    title: String,
    onPinChosen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val confirming = firstEntry != null

    PinScaffold(
        icon = Icons.Outlined.LockReset,
        title = title,
        message = if (confirming) "Enter the same PIN again to confirm." else PIN_SETUP_MESSAGE,
        entryLength = entry.length,
        error = error,
        enabled = true,
        confirmLabel = if (confirming) "Save PIN" else "Next",
        onDigit = { entry += it },
        onBackspace = { entry = entry.dropLast(1) },
        onConfirm = {
            val submitted = entry
            entry = ""
            val first = firstEntry
            when {
                first == null -> {
                    firstEntry = submitted
                    error = null
                }
                first == submitted -> onPinChosen(submitted)
                else -> {
                    // Back to the first step: with no way to see what was typed, continuing to
                    // confirm an unknown first entry would just fail again.
                    firstEntry = null
                    error = "Those PINs did not match. Start again."
                }
            }
        },
        onDismiss = onDismiss,
        footer = null,
    )
}

/** Stated before a PIN is chosen, because after this point nothing in the app can undo it. */
internal const val PIN_SETUP_MESSAGE =
    "Choose $MIN_PIN_LENGTH–$MAX_PIN_LENGTH digits. Nothing can recover it — if you forget this " +
        "PIN, the app cannot open this content again."

@Composable
private fun PinScaffold(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    entryLength: Int,
    error: String?,
    enabled: Boolean,
    confirmLabel: String,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)?,
) {
    PremiumAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                PinDots(length = entryLength)
                AnimatedVisibility(visible = error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                PinKeypad(
                    enabled = enabled && entryLength < MAX_PIN_LENGTH,
                    backspaceEnabled = enabled && entryLength > 0,
                    onDigit = onDigit,
                    onBackspace = onBackspace,
                )
                footer?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                // Anything shorter than the minimum cannot be a stored PIN, so it is refused here
                // rather than spent as one of the attempts before a lockout.
                enabled = enabled && isValidPin(digitsPlaceholder(entryLength)),
                onClick = onConfirm,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Length-only stand-in for the entry, so the enabled/disabled rule can reuse [isValidPin] without
 * this layout ever being handed the digits themselves.
 */
private fun digitsPlaceholder(length: Int) = "0".repeat(length)

@Composable
private fun PinDots(length: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Shows how much has been typed without revealing the length of the stored PIN: the run of
        // placeholders is always the maximum, whatever the real PIN is.
        repeat(MAX_PIN_LENGTH) { index ->
            val filled = index < length
            Box(
                modifier = Modifier
                    .size(if (filled) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    ),
            )
        }
    }
}

@Composable
private fun PinKeypad(
    enabled: Boolean,
    backspaceEnabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { digit -> PinKey(digit.toString(), enabled) { onDigit(digit) } }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(56.dp))
            PinKey("0", enabled) { onDigit('0') }
            IconButton(
                onClick = onBackspace,
                enabled = backspaceEnabled,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete last digit")
            }
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * What to say after a wrong PIN.
 *
 * Counts down out loud once the next wait is close, so the first lockout is never a surprise, and
 * says nothing about the stored PIN itself — not its length, not how close the guess was.
 */
internal fun wrongPinMessage(result: com.pandagallery.app.data.security.PinVerification.Wrong): String = when {
    result.lockoutMillis > 0L ->
        "Too many wrong attempts. Try again in " +
            com.pandagallery.app.domain.security.formatPinLockout(result.lockoutMillis) + "."
    result.attemptsRemaining in 1..2 ->
        "Wrong PIN. ${result.attemptsRemaining} more attempt" +
            (if (result.attemptsRemaining == 1) "" else "s") + " before a wait."
    else -> "Wrong PIN."
}
