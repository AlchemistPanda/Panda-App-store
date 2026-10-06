package com.pandaapps.appstore.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToLong

/** Pure, JVM-testable formatting helpers. Sizes use SI units (1 MB = 1,000,000 bytes) like the catalog tool. */
object Formatters {

    private const val KB = 1_000.0
    private const val MB = 1_000_000.0
    private const val GB = 1_000_000_000.0

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** `512 B`, `8.4 KB`, `12.3 MB`, `177 MB`, `1.25 GB`. Negative input → `0 B`. */
    fun bytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0)
        return when {
            b < KB -> "$b B"
            b < MB -> "${scaled(b / KB)} KB"
            b < GB -> "${scaled(b / MB)} MB"
            else -> String.format(Locale.US, "%.2f GB", b / GB)
        }
    }

    /** `12.3 MB / 177 MB`, or just the downloaded amount when the total is unknown (≤ 0). */
    fun progressBytes(done: Long, total: Long): String =
        if (total > 0) "${bytes(done)} / ${bytes(total)}" else bytes(done)

    /** Whole percent 0..100 for a 0..1 fraction. */
    fun percent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%"

    /** `v1.0.6 · build 7 · 177 MB` (size omitted when unknown). */
    fun versionLine(versionName: String, versionCode: Long, sizeBytes: Long?): String = buildString {
        append("v").append(versionName).append(" · build ").append(versionCode)
        if (sizeBytes != null && sizeBytes > 0) append(" · ").append(bytes(sizeBytes))
    }

    /**
     * `just now`, `5 min ago`, `3 h ago`, `yesterday`, `4 days ago`, then a date like `4 Oct 2026`.
     * Times in the future (clock skew) read as `just now`.
     */
    fun relativeTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val diff = nowMillis - epochMillis
        return when {
            diff < MINUTE -> "just now"
            diff < HOUR -> "${diff / MINUTE} min ago"
            diff < DAY -> "${diff / HOUR} h ago"
            diff < 2 * DAY -> "yesterday"
            diff < 7 * DAY -> "${diff / DAY} days ago"
            else -> date(epochMillis, zone)
        }
    }

    /** `4 Oct 2026` in the given zone. */
    fun date(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** `4 Oct 2026, 08:19` in the given zone. */
    fun dateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** Parses catalog timestamps such as `2026-10-04T08:19:02Z`; null when absent or malformed. */
    fun parseIsoInstant(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** One decimal below 100, none at or above (`12.3`, `177`). */
    private fun scaled(value: Double): String =
        if (value < 100) String.format(Locale.US, "%.1f", value).removeSuffix(".0")
        else value.roundToLong().toString()

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US)
}
