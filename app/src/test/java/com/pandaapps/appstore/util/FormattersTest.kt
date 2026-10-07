package com.pandaapps.appstore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FormattersTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val sampleInstant = Instant.parse("2026-10-04T08:19:02Z").toEpochMilli()

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    // ---- bytes ----

    @Test
    fun bytes_underOneKilobyte() {
        assertEquals("0 B", Formatters.bytes(0))
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("999 B", Formatters.bytes(999))
    }

    @Test
    fun bytes_negativeIsZero() {
        assertEquals("0 B", Formatters.bytes(-5))
    }

    @Test
    fun bytes_kilobytesUseSiUnits() {
        assertEquals("1 KB", Formatters.bytes(1_000))
        assertEquals("1.5 KB", Formatters.bytes(1_500))
        assertEquals("8.4 KB", Formatters.bytes(8_400))
        assertEquals("250 KB", Formatters.bytes(250_000))
    }

    @Test
    fun bytes_megabytes() {
        assertEquals("1 MB", Formatters.bytes(1_000_000))
        assertEquals("12.3 MB", Formatters.bytes(12_345_678))
        assertEquals("177 MB", Formatters.bytes(176_745_929))
    }

    @Test
    fun bytes_gigabytesHaveTwoDecimals() {
        assertEquals("1.25 GB", Formatters.bytes(1_250_000_000))
        assertEquals("1.00 GB", Formatters.bytes(1_000_000_000))
    }

    // ---- progress / percent ----

    @Test
    fun progressBytes_withKnownTotal() {
        assertEquals("12 MB / 177 MB", Formatters.progressBytes(12_000_000, 176_745_929))
    }

    @Test
    fun progressBytes_withUnknownTotal_showsDoneOnly() {
        assertEquals("12 MB", Formatters.progressBytes(12_000_000, 0))
        assertEquals("12 MB", Formatters.progressBytes(12_000_000, -1))
    }

    @Test
    fun percent_isWholeAndClamped() {
        assertEquals("0%", Formatters.percent(0f))
        assertEquals("7%", Formatters.percent(0.07f))
        assertEquals("50%", Formatters.percent(0.5f))
        assertEquals("99%", Formatters.percent(0.999f))
        assertEquals("100%", Formatters.percent(1f))
        assertEquals("100%", Formatters.percent(1.5f))
        assertEquals("0%", Formatters.percent(-0.3f))
    }

    // ---- speed / timeLeft ----

    @Test
    fun speed_reusesBytesFormat() {
        assertEquals("4.2 MB/s", Formatters.speed(4_200_000.0))
        assertEquals("850 KB/s", Formatters.speed(850_000.0))
        assertEquals("1 B/s", Formatters.speed(1.0))
    }

    @Test
    fun speed_unknownOrZero_isNull() {
        assertNull(Formatters.speed(0.0))
        assertNull(Formatters.speed(0.4))
        assertNull(Formatters.speed(-5.0))
        assertNull(Formatters.speed(Double.NaN))
        assertNull(Formatters.speed(Double.POSITIVE_INFINITY))
    }

    @Test
    fun timeLeft_seconds() {
        assertEquals("12 s left", Formatters.timeLeft(0, 12_000_000, 1_000_000.0))
        // Partial seconds round up so it never reads "0 s left" before the end.
        assertEquals("1 s left", Formatters.timeLeft(99, 100, 1_000.0))
        assertEquals("59 s left", Formatters.timeLeft(0, 59_000_000, 1_000_000.0))
    }

    @Test
    fun timeLeft_minutesAndHours() {
        assertEquals("about 1 min left", Formatters.timeLeft(0, 60_000_000, 1_000_000.0))
        // 177 MB at 1.5 MB/s = 118 s.
        assertEquals("about 2 min left", Formatters.timeLeft(0, 177_000_000, 1_500_000.0))
        assertEquals("about 89 min left", Formatters.timeLeft(0, 89 * 60_000L, 1_000.0))
        assertEquals("about 2 h left", Formatters.timeLeft(0, 2 * 3_600_000L, 1_000.0))
    }

    @Test
    fun timeLeft_hiddenWhenUnknownOrDone() {
        assertNull(Formatters.timeLeft(5_000_000, 0, 1_000_000.0))
        assertNull(Formatters.timeLeft(5_000_000, -1, 1_000_000.0))
        assertNull(Formatters.timeLeft(0, 177_000_000, 0.0))
        assertNull(Formatters.timeLeft(0, 177_000_000, Double.NaN))
        assertNull(Formatters.timeLeft(177_000_000, 177_000_000, 1_000_000.0))
        assertNull(Formatters.timeLeft(180_000_000, 177_000_000, 1_000_000.0))
    }

    // ---- versionLine ----

    @Test
    fun versionLine_withSize() {
        assertEquals("v1.0.6 · build 7 · 177 MB", Formatters.versionLine("1.0.6", 7, 176_745_929))
    }

    @Test
    fun versionLine_omitsUnknownOrZeroSize() {
        assertEquals("v1.0.6 · build 7", Formatters.versionLine("1.0.6", 7, null))
        assertEquals("v1.0.6 · build 7", Formatters.versionLine("1.0.6", 7, 0))
    }

    // ---- relativeTime ----

    @Test
    fun relativeTime_buckets() {
        val now = sampleInstant
        assertEquals("just now", Formatters.relativeTime(now, now, utc))
        assertEquals("just now", Formatters.relativeTime(now - 59_000, now, utc))
        assertEquals("1 min ago", Formatters.relativeTime(now - minute, now, utc))
        assertEquals("5 min ago", Formatters.relativeTime(now - 5 * minute, now, utc))
        assertEquals("59 min ago", Formatters.relativeTime(now - 59 * minute, now, utc))
        assertEquals("1 h ago", Formatters.relativeTime(now - hour, now, utc))
        assertEquals("3 h ago", Formatters.relativeTime(now - 3 * hour - 10 * minute, now, utc))
        assertEquals("23 h ago", Formatters.relativeTime(now - 23 * hour, now, utc))
        assertEquals("yesterday", Formatters.relativeTime(now - day, now, utc))
        assertEquals("yesterday", Formatters.relativeTime(now - 47 * hour, now, utc))
        assertEquals("2 days ago", Formatters.relativeTime(now - 2 * day, now, utc))
        assertEquals("6 days ago", Formatters.relativeTime(now - 6 * day, now, utc))
    }

    @Test
    fun relativeTime_aWeekOrMore_showsDate() {
        val now = sampleInstant
        assertEquals("27 Sep 2026", Formatters.relativeTime(now - 7 * day, now, utc))
        assertEquals("4 Sep 2026", Formatters.relativeTime(now - 30 * day, now, utc))
    }

    @Test
    fun relativeTime_futureTimestamp_isJustNow() {
        assertEquals("just now", Formatters.relativeTime(sampleInstant + hour, sampleInstant, utc))
    }

    // ---- date / dateTime ----

    @Test
    fun date_formatsDayMonthYear() {
        assertEquals("4 Oct 2026", Formatters.date(sampleInstant, utc))
    }

    @Test
    fun date_respectsZone() {
        // 2026-10-04T08:19:02Z is still 3 Oct in Honolulu (UTC-10).
        assertEquals("3 Oct 2026", Formatters.date(sampleInstant, ZoneId.of("Pacific/Honolulu")))
    }

    @Test
    fun dateTime_formatsWith24HourClock() {
        assertEquals("4 Oct 2026, 08:19", Formatters.dateTime(sampleInstant, utc))
        assertEquals("4 Oct 2026, 13:49", Formatters.dateTime(sampleInstant, ZoneId.of("Asia/Kolkata")))
    }

    // ---- parseIsoInstant ----

    @Test
    fun parseIsoInstant_parsesCatalogTimestamps() {
        assertEquals(sampleInstant, Formatters.parseIsoInstant("2026-10-04T08:19:02Z"))
        assertEquals(0L, Formatters.parseIsoInstant("1970-01-01T00:00:00Z"))
    }

    @Test
    fun parseIsoInstant_nullBlankOrMalformed_isNull() {
        assertNull(Formatters.parseIsoInstant(null))
        assertNull(Formatters.parseIsoInstant(""))
        assertNull(Formatters.parseIsoInstant("   "))
        assertNull(Formatters.parseIsoInstant("yesterday"))
        assertNull(Formatters.parseIsoInstant("2026-10-04"))
    }
}
