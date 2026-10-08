package com.pandagallery.app.domain.security

/**
 * How long the lock refuses further guesses after repeated wrong PINs.
 *
 * A 4-digit PIN is 10,000 possibilities, which is nothing to a program typing them in. Since the
 * PIN is the only way in — there is no recovery path by design — the cost per guess has to come
 * from somewhere, and slowing the guesser down is what makes a short PIN defensible at all. The
 * delay grows in steps rather than locking out permanently, so a genuine user who mistyped a few
 * times waits rather than losing their photos.
 *
 * Deliberately not a wipe-after-N-attempts rule: the media in a locked folder is the user's only
 * copy, and someone else's failed guesses must never be able to destroy it.
 */
private val BACKOFF_STEPS = listOf(
    5 to 30_000L,        // 30 seconds
    8 to 2 * 60_000L,    // 2 minutes
    11 to 10 * 60_000L,  // 10 minutes
    14 to 30 * 60_000L,  // 30 minutes
)

/** Wrong attempts allowed before the first wait. */
const val PIN_ATTEMPTS_BEFORE_BACKOFF = 5

/** How long a lockout lasts once [failedAttempts] wrong guesses have been made. */
fun pinLockoutMillis(failedAttempts: Int): Long =
    BACKOFF_STEPS.lastOrNull { (threshold, _) -> failedAttempts >= threshold }?.second ?: 0L

/**
 * Milliseconds still to wait, or 0 when a guess is allowed now.
 *
 * A clock that moved backwards (timezone change, manual clock edit) must not hand out a free pass,
 * so a negative elapsed time is treated as no time having passed at all.
 */
fun pinLockoutRemainingMillis(failedAttempts: Int, lastFailureAt: Long, now: Long): Long {
    val lockout = pinLockoutMillis(failedAttempts)
    if (lockout == 0L || lastFailureAt <= 0L) return 0L
    val elapsed = (now - lastFailureAt).coerceAtLeast(0L)
    return (lockout - elapsed).coerceAtLeast(0L)
}

/** How many wrong guesses remain before the next wait — for warning the user before it bites. */
fun pinAttemptsRemainingBeforeLockout(failedAttempts: Int): Int {
    val nextThreshold = BACKOFF_STEPS.firstOrNull { (threshold, _) -> failedAttempts < threshold }?.first
        ?: return 0
    return nextThreshold - failedAttempts
}

/** "30 seconds" / "2 minutes" — for the message shown while a lockout is counting down. */
fun formatPinLockout(remainingMillis: Long): String {
    val seconds = ((remainingMillis + 999) / 1000).coerceAtLeast(1)
    if (seconds < 60) return if (seconds == 1L) "1 second" else "$seconds seconds"
    val minutes = (seconds + 59) / 60
    return if (minutes == 1L) "1 minute" else "$minutes minutes"
}
