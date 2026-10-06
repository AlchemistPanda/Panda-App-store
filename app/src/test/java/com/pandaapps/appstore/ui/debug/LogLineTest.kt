package com.pandaapps.appstore.ui.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class LogLineTest {

    @Test
    fun parsesAppLogFormat() {
        assertEquals(
            LogLine("14:03:22.481", 'I', "Install", "Downloading Panda Garage"),
            LogLine.parse("14:03:22.481 I/Install: Downloading Panda Garage"),
        )
    }

    @Test
    fun keepsColonsAndNewlinesInMessage() {
        val line = LogLine.parse("09:00:00.000 E/Catalog: failed: HTTP 500\njava.io.IOException: boom")
        assertEquals('E', line.level)
        assertEquals("Catalog", line.tag)
        assertEquals("failed: HTTP 500\njava.io.IOException: boom", line.message)
    }

    @Test
    fun unknownFormat_isMessageOnly() {
        assertEquals(LogLine(null, null, null, "--- header ---"), LogLine.parse("--- header ---"))
        assertEquals(LogLine(null, null, null, "12:00 X/Tag: bad level"), LogLine.parse("12:00 X/Tag: bad level"))
    }
}
