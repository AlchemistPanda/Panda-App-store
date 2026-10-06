package com.pandaapps.appstore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure helpers and defaults exposed by the data-layer repositories. */
class RepositoryHelpersTest {

    @Test
    fun sha256Hex_knownVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            InstalledAppsRepository.sha256Hex(ByteArray(0)),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            InstalledAppsRepository.sha256Hex("abc".toByteArray(Charsets.US_ASCII)),
        )
    }

    @Test
    fun sha256Hex_isLowercaseWithoutSeparators() {
        val hex = InstalledAppsRepository.sha256Hex(byteArrayOf(-1, 0, 127, -128))
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun settings_defaultsMatchSpec() {
        val s = Settings()
        assertEquals(6, s.checkIntervalHours)
        assertFalse(s.autoUpdate)
        assertTrue(s.wifiOnly)
        assertTrue(s.notifiedVersions.isEmpty())
        assertTrue(s.deferredVersions.isEmpty())
    }

    @Test
    fun checkIntervalOptions_matchSpecAndContainDefault() {
        assertEquals(listOf(0, 1, 3, 6, 12, 24), SettingsRepository.CHECK_INTERVAL_OPTIONS)
        assertEquals(6, SettingsRepository.DEFAULT_CHECK_INTERVAL_HOURS)
        assertTrue(SettingsRepository.DEFAULT_CHECK_INTERVAL_HOURS in SettingsRepository.CHECK_INTERVAL_OPTIONS)
    }
}
