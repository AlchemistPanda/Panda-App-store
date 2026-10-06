package com.pandaapps.appstore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * android.util.Log is stubbed on the JVM (unitTests.isReturnDefaultValues = true), so only the
 * in-memory ring buffer is checked here.
 */
class AppLogTest {

    private val timestamp = "\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"

    @Test
    fun lines_haveTimeLevelTagAndMessage() {
        val log = AppLog()
        log.d("Catalog", "debug")
        log.i("Install", "Downloading Panda Garage v1.0.6")
        log.w("Verify", "warn")
        log.e("Worker", "error")

        val lines = log.lines.value
        assertEquals(4, lines.size)
        assertTrue(lines[0], Regex("^$timestamp D/Catalog: debug$").matches(lines[0]))
        assertTrue(lines[1], Regex("^$timestamp I/Install: Downloading Panda Garage v1\\.0\\.6$").matches(lines[1]))
        assertTrue(lines[2], Regex("^$timestamp W/Verify: warn$").matches(lines[2]))
        assertTrue(lines[3], Regex("^$timestamp E/Worker: error$").matches(lines[3]))
    }

    @Test
    fun throwable_isSummarisedOnTheLine() {
        val log = AppLog()
        log.e("Install", "failed", IllegalStateException("disk full"))
        val line = log.lines.value.single()
        assertTrue(line, line.endsWith("E/Install: failed (IllegalStateException: disk full)"))
    }

    @Test
    fun ringBuffer_dropsOldestBeyondCapacity() {
        val log = AppLog(capacity = 3)
        (1..5).forEach { log.i("T", "m$it") }
        val messages = log.lines.value.map { it.substringAfter("I/T: ") }
        assertEquals(listOf("m3", "m4", "m5"), messages)
    }

    @Test
    fun clear_emptiesBuffer() {
        val log = AppLog()
        log.i("T", "a")
        log.clear()
        assertTrue(log.lines.value.isEmpty())
        assertEquals("", log.dump())
        log.i("T", "b")
        assertEquals(1, log.lines.value.size)
    }

    @Test
    fun dump_joinsLinesOldestFirst() {
        val log = AppLog()
        log.i("T", "first")
        log.i("T", "second")
        val dump = log.dump().lines()
        assertEquals(2, dump.size)
        assertTrue(dump[0].endsWith("first"))
        assertTrue(dump[1].endsWith("second"))
    }

    @Test
    fun defaultCapacity_isOneThousand() {
        assertEquals(1000, AppLog.DEFAULT_CAPACITY)
        val log = AppLog()
        repeat(1005) { log.d("T", "$it") }
        assertEquals(1000, log.lines.value.size)
        assertTrue(log.lines.value.first().endsWith("D/T: 5"))
    }
}
