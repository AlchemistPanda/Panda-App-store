package com.pandagallery.app.ui.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pandagallery.app.domain.security.formatPinLockout
import kotlinx.coroutines.delay

/**
 * The "try again in …" line for a PIN prompt, kept current while [active].
 *
 * Polled rather than set once when a lockout starts, because a stored lockout has two properties a
 * one-shot message gets wrong: it may already be running when the prompt opens, and it expires
 * while the prompt is still on screen. Both were live bugs — a stale message left the keypad
 * disabled long after the wait was over, and a prompt reopened during a lockout accepted digits it
 * was always going to refuse.
 *
 * @param remainingMillis reads the lockout straight from storage, so force-stopping the app cannot
 *   shake it off.
 */
@Composable
internal fun rememberPinLockoutMessage(
    active: Boolean,
    remainingMillis: suspend () -> Long,
): String? {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active) {
        if (!active) {
            message = null
            return@LaunchedEffect
        }
        while (true) {
            val remaining = remainingMillis()
            message = if (remaining > 0L) {
                "Too many wrong attempts. Try again in ${formatPinLockout(remaining)}."
            } else {
                null
            }
            // Half a second: fast enough that the countdown never looks stuck, slow enough to be
            // free next to a PBKDF2 verification.
            delay(500)
        }
    }
    return message
}
