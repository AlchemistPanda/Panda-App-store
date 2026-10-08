package com.pandagallery.app.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Storage and checking of the app's own lock PINs — one for locked folders, one for the Private
 * folder.
 *
 * The PIN is never stored. What gets written is a PBKDF2-SHA256 hash over a per-PIN random salt,
 * so reading the preference file back reveals nothing that can be replayed, and the same PIN used
 * for both locks produces two unrelated records. Verification is a constant-time comparison, which
 * matters more than it looks: a four-digit space is small enough that a timing side channel would
 * shorten it materially.
 *
 * The iteration count is deliberately high for a value this short — the whole defence of a 4-digit
 * PIN is that each guess is expensive — and is stored inside the record so it can be raised later
 * without invalidating PINs already set.
 */
private const val ALGORITHM = "PBKDF2WithHmacSHA256"
private const val ITERATIONS = 200_000
private const val KEY_LENGTH_BITS = 256
private const val SALT_BYTES = 16
private const val RECORD_PREFIX = "pbkdf2-sha256"

/** The shortest and longest PIN the setup screen accepts. */
const val MIN_PIN_LENGTH = 4
const val MAX_PIN_LENGTH = 8

/** Digits only, within [MIN_PIN_LENGTH]..[MAX_PIN_LENGTH]. */
fun isValidPin(pin: String): Boolean =
    pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all(Char::isDigit)

/**
 * Hashes [pin] for storage, generating a fresh salt.
 *
 * Returns `pbkdf2-sha256$<iterations>$<salt>$<hash>`, self-describing so [verifyPin] never has to
 * assume the parameters a stored record was written with.
 */
fun hashPin(pin: String, random: SecureRandom = SecureRandom()): String {
    val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
    return encodeRecord(salt, derive(pin, salt, ITERATIONS))
}

/**
 * Whether [pin] matches [record] as written by [hashPin].
 *
 * A record this version cannot parse counts as no match rather than as an error: the caller is a
 * lock screen, and the safe answer to "is this the right PIN" when the stored value is unreadable
 * is no.
 */
fun verifyPin(pin: String, record: String?): Boolean {
    val parsed = parseRecord(record ?: return false) ?: return false
    val candidate = derive(pin, parsed.salt, parsed.iterations)
    // MessageDigest.isEqual is the constant-time comparison on the platform.
    return MessageDigest.isEqual(candidate, parsed.hash)
}

private class PinRecord(val iterations: Int, val salt: ByteArray, val hash: ByteArray)

private fun encodeRecord(salt: ByteArray, hash: ByteArray): String {
    val encoder = Base64.getEncoder().withoutPadding()
    return "$RECORD_PREFIX$$ITERATIONS$${encoder.encodeToString(salt)}$${encoder.encodeToString(hash)}"
}

private fun parseRecord(record: String): PinRecord? {
    val parts = record.split('$')
    if (parts.size != 4 || parts[0] != RECORD_PREFIX) return null
    val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
    return runCatching {
        val decoder = Base64.getDecoder()
        PinRecord(iterations, decoder.decode(parts[2]), decoder.decode(parts[3]))
    }.getOrNull()?.takeIf { it.salt.isNotEmpty() && it.hash.isNotEmpty() }
}

private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
    val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
    return try {
        SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    } finally {
        // Drops the PIN from the spec's internal copy; the caller's String is out of our hands.
        spec.clearPassword()
    }
}
