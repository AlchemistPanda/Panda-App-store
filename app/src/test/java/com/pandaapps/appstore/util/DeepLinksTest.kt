package com.pandaapps.appstore.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Exercises the pure overload of [DeepLinks.parse] (the Uri overload needs the Android framework). */
class DeepLinksTest {

    @Test
    fun home() {
        assertEquals(DeepLink.Home, DeepLinks.parse("pandastore", "home", emptyList()))
    }

    @Test
    fun app() {
        assertEquals(
            DeepLink.App("com.pandacollection.pandagarage"),
            DeepLinks.parse("pandastore", "app", listOf("com.pandacollection.pandagarage")),
        )
    }

    @Test
    fun schemeAndHost_areCaseInsensitive() {
        assertEquals(DeepLink.Home, DeepLinks.parse("PandaStore", "HOME", emptyList()))
        assertEquals(DeepLink.App("com.pandaapps.appstore"), DeepLinks.parse("PANDASTORE", "App", listOf("com.pandaapps.appstore")))
    }

    @Test
    fun app_usesFirstPathSegmentOnly() {
        assertEquals(DeepLink.App("com.example.a"), DeepLinks.parse("pandastore", "app", listOf("com.example.a", "extra")))
    }

    @Test
    fun app_withoutPackage_isNull() {
        assertNull(DeepLinks.parse("pandastore", "app", emptyList()))
    }

    @Test
    fun app_withInvalidPackageName_isNull() {
        assertNull(DeepLinks.parse("pandastore", "app", listOf("nodots")))
        assertNull(DeepLinks.parse("pandastore", "app", listOf("1com.example")))
        assertNull(DeepLinks.parse("pandastore", "app", listOf("com..example")))
        assertNull(DeepLinks.parse("pandastore", "app", listOf("com.example/../x")))
        assertNull(DeepLinks.parse("pandastore", "app", listOf("")))
    }

    @Test
    fun wrongSchemeOrHost_isNull() {
        assertNull(DeepLinks.parse("https", "app", listOf("com.example.a")))
        assertNull(DeepLinks.parse(null, "home", emptyList()))
        assertNull(DeepLinks.parse("pandastore", "settings", emptyList()))
        assertNull(DeepLinks.parse("pandastore", null, emptyList()))
    }

    @Test
    fun constants_matchManifestAndNtfyClickUrl() {
        assertEquals("pandastore", DeepLinks.SCHEME)
        assertEquals("app", DeepLinks.HOST_APP)
        assertEquals("home", DeepLinks.HOST_HOME)
    }
}
