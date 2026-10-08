package com.pandagallery.app.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckLogicTest {

    private val catalog = """
        {
          "schema": 1,
          "apps": [
            {
              "packageName": "com.pandaapps.appstore",
              "releases": [{ "versionName": "9.0.0", "versionCode": 99, "apkUrl": "https://example.com/store.apk" }]
            },
            {
              "packageName": "com.pandagallery.app",
              "releases": [
                { "versionName": "1.3.6", "versionCode": 23, "apkUrl": "https://example.com/gallery-b23.apk",
                  "size": 52428800, "notes": "  Faster compression.  " },
                { "versionName": "1.3.5", "versionCode": 22, "apkUrl": "https://example.com/gallery-b22.apk" },
                { "versionName": "bad", "apkUrl": "https://example.com/no-code.apk" },
                { "versionName": "1.3.4", "versionCode": "twenty", "apkUrl": "https://example.com/string-code.apk" }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun newerBuildOfThisPackageIsFound() {
        val update = findUpdate(catalog, "com.pandagallery.app", installedCode = 22)

        assertEquals("1.3.6", update?.versionName)
        assertEquals(23, update?.versionCode)
        assertEquals("https://example.com/gallery-b23.apk", update?.apkUrl)
        assertEquals("Faster compression.", update?.notes)
        assertEquals(52428800L, update?.size)
    }

    @Test
    fun sameOrOlderBuildIsNoUpdate() {
        assertNull(findUpdate(catalog, "com.pandagallery.app", installedCode = 23))
        assertNull(findUpdate(catalog, "com.pandagallery.app", installedCode = 24))
    }

    @Test
    fun otherPackagesAreIgnored() {
        assertNull(findUpdate(catalog, "com.unknown.app", installedCode = 1))
    }

    @Test
    fun malformedCatalogIsNoUpdateNotAnError() {
        assertNull(findUpdate("not json", "com.pandagallery.app", installedCode = 1))
        assertNull(findUpdate("{}", "com.pandagallery.app", installedCode = 1))
        assertNull(findUpdate("""{"apps": [{"packageName": "com.pandagallery.app"}]}""", "com.pandagallery.app", 1))
    }

    @Test
    fun missingSizeDefaultsToZero() {
        val withoutSize = findUpdate(
            """{"apps":[{"packageName":"p","releases":[{"versionName":"2","versionCode":2,"apkUrl":"u"}]}]}""",
            "p",
            installedCode = 1,
        )

        assertEquals(0L, withoutSize?.size)
    }

    @Test
    fun startupCheckRunsWhenEnabledAndNeverChecked() {
        assertTrue(shouldCheckOnStartup(enabled = true, lastCheckedAt = null, now = 1_000L))
    }

    @Test
    fun startupCheckIsSkippedWhenDisabled() {
        assertFalse(shouldCheckOnStartup(enabled = false, lastCheckedAt = null, now = 1_000L))
    }

    @Test
    fun startupCheckIsThrottledToTheInterval() {
        val last = 1_000_000L

        assertFalse(shouldCheckOnStartup(true, last, last + STARTUP_CHECK_INTERVAL_MS - 1))
        assertTrue(shouldCheckOnStartup(true, last, last + STARTUP_CHECK_INTERVAL_MS))
    }

    @Test
    fun clockMovedBackwardsDoesNotBlockTheCheckForever() {
        assertTrue(shouldCheckOnStartup(true, lastCheckedAt = 5_000L, now = 1_000L))
    }

    @Test
    fun skippedVersionIsNotAnnouncedAgainButANewerOneIs() {
        val update = AvailableUpdate("1.3.6", 23, "", "u", 0L)

        assertTrue(shouldAnnounce(update, skippedCode = null))
        assertFalse(shouldAnnounce(update, skippedCode = 23))
        assertTrue(shouldAnnounce(update, skippedCode = 22))
    }
}
