package com.pandaapps.appstore.install

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareFileNameTest {
    @Test
    fun stripsSpacesAndUnsafeCharacters() {
        assertEquals("PandaGarage-v1.0.7.apk", shareFileName("Panda Garage", "1.0.7"))
        assertEquals("ab-v2.apk", shareFileName("a/b:", "2"))
    }

    @Test
    fun fallsBackWhenNameHasNothingSafe() {
        assertEquals("app-v1.apk", shareFileName("熊猫", "1"))
    }
}
