package com.pandagallery.app.data.security

import com.pandagallery.app.data.local.PreferencesDataSource
import com.pandagallery.app.data.local.PreferencesDataSource.PinLock
import com.pandagallery.app.domain.security.hashPin
import com.pandagallery.app.domain.security.pinAttemptsRemainingBeforeLockout
import com.pandagallery.app.domain.security.pinLockoutRemainingMillis
import com.pandagallery.app.domain.security.verifyPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that checks a lock PIN.
 *
 * Holds the two rules that have to apply identically wherever a PIN is asked for: the guess is
 * checked against a hash, and repeated wrong guesses cost time. Keeping both here is what stops a
 * new caller from accidentally introducing a route in that counts no attempts.
 *
 * Hashing runs on [Dispatchers.Default] — 200k PBKDF2 iterations is deliberately slow, and it must
 * not be slow on the main thread.
 */
@Singleton
class PinLockManager @Inject constructor(
    private val preferences: PreferencesDataSource,
) {
    /**
     * Checks [pin] against the stored PIN for [lock].
     *
     * The lockout is enforced before the hash is even computed, so attempts made during a lockout
     * cost the attacker their wait and gain them nothing.
     */
    suspend fun verify(
        lock: PinLock,
        pin: String,
        now: Long = System.currentTimeMillis(),
    ): PinVerification {
        val attempts = preferences.pinAttemptsFlow(lock).first()
        val waiting = pinLockoutRemainingMillis(attempts.failedAttempts, attempts.lastFailureAt, now)
        if (waiting > 0L) return PinVerification.LockedOut(waiting)

        val stored = storedHash(lock)
            // No PIN set means nothing to verify against. Treated as a wrong guess rather than as
            // a free pass, so a cleared preference can never become a way in.
            ?: return PinVerification.Wrong(attemptsRemaining = 0, lockoutMillis = 0L)

        val matches = withContext(Dispatchers.Default) { verifyPin(pin, stored) }
        if (matches) {
            preferences.clearPinFailures(lock)
            return PinVerification.Success
        }

        preferences.recordPinFailure(lock, now)
        val after = preferences.pinAttemptsFlow(lock).first()
        return PinVerification.Wrong(
            attemptsRemaining = pinAttemptsRemainingBeforeLockout(after.failedAttempts),
            lockoutMillis = pinLockoutRemainingMillis(after.failedAttempts, after.lastFailureAt, now),
        )
    }

    /** Milliseconds left on a lockout for [lock], or 0 when a guess is allowed. */
    suspend fun lockoutRemainingMillis(lock: PinLock, now: Long = System.currentTimeMillis()): Long {
        val attempts = preferences.pinAttemptsFlow(lock).first()
        return pinLockoutRemainingMillis(attempts.failedAttempts, attempts.lastFailureAt, now)
    }

    /** Stores [pin] for [lock], replacing any PIN already set. */
    suspend fun setPin(lock: PinLock, pin: String) {
        val hash = withContext(Dispatchers.Default) { hashPin(pin) }
        when (lock) {
            PinLock.FOLDERS -> preferences.setFolderLockPinHash(hash)
            PinLock.PRIVATE_VAULT -> preferences.setPrivateVaultPinHash(hash)
        }
    }

    /** Removes the PIN for [lock] along with its biometric shortcut and attempt history. */
    suspend fun clearPin(lock: PinLock) {
        when (lock) {
            PinLock.FOLDERS -> preferences.clearFolderLockPin()
            PinLock.PRIVATE_VAULT -> preferences.clearPrivateVaultPin()
        }
    }

    suspend fun hasPin(lock: PinLock): Boolean = storedHash(lock) != null

    private suspend fun storedHash(lock: PinLock): String? = when (lock) {
        PinLock.FOLDERS -> preferences.folderLockPinHash()
        PinLock.PRIVATE_VAULT -> preferences.privateVaultPinHash()
    }
}

sealed interface PinVerification {
    data object Success : PinVerification

    /**
     * @param attemptsRemaining wrong guesses left before the next wait, for warning the user.
     * @param lockoutMillis non-zero when this guess was the one that started a lockout.
     */
    data class Wrong(val attemptsRemaining: Int, val lockoutMillis: Long) : PinVerification

    data class LockedOut(val remainingMillis: Long) : PinVerification
}
