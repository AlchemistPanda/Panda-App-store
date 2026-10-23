package com.pandaapps.appstore.data

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerAccessTest {

    private val api = "https://api.github.com/repos/${OwnerAccess.PRIVATE_REPO}"

    private fun request(url: String) = Request.Builder().url(url).build()

    @Test
    fun passcode() {
        assertTrue(OwnerAccess.isPasscode("9090"))
        assertTrue(OwnerAccess.isPasscode(" 9090 "))
        assertFalse(OwnerAccess.isPasscode("1234"))
        assertFalse(OwnerAccess.isPasscode(""))
    }

    @Test
    fun noTokenLeavesRequestsAlone() {
        val r = request(OwnerAccess.PRIVATE_CATALOG_URL)
        assertSame(r, OwnerAccess.authorize(r, null))
    }

    @Test
    fun tokenNeverGoesToOtherHostsOrRepos() {
        for (url in listOf(
            "https://api.github.com/repos/AlchemistPanda/Panda-App-store/contents/catalog.json?ref=store",
            "https://raw.githubusercontent.com/AlchemistPanda/Panda-App-store-private/store/catalog.json",
            "https://objects.githubusercontent.com/github-production-release-asset/1",
            "https://api.github.com/repos/AlchemistPanda/Panda-App-store-private-evil/contents/x",
        )) {
            assertNull(url, OwnerAccess.authorize(request(url), "tok").header("Authorization"))
        }
    }

    @Test
    fun catalogAndIconsGetRawContents() {
        val r = OwnerAccess.authorize(request("$api/contents/icons/a.b.png?ref=store&v=3"), "tok")
        assertEquals("Bearer tok", r.header("Authorization"))
        assertEquals("application/vnd.github.raw", r.header("Accept"))
    }

    @Test
    fun releaseAssetsGetBinary() {
        val r = OwnerAccess.authorize(request("$api/releases/assets/123"), "tok")
        assertEquals("Bearer tok", r.header("Authorization"))
        assertEquals("application/octet-stream", r.header("Accept"))
    }

    @Test
    fun explicitCredentialsWin() {
        val r = Request.Builder().url(OwnerAccess.PRIVATE_CATALOG_URL).header("Authorization", "Bearer new").build()
        assertEquals("Bearer new", OwnerAccess.authorize(r, "old").header("Authorization"))
    }

    private fun app(pkg: String, name: String, code: Long?, isPrivate: Boolean = false) = CatalogApp(
        packageName = pkg,
        name = name,
        releases = listOfNotNull(code?.let { CatalogRelease(versionName = "1.0.$it", versionCode = it, apkUrl = "u") }),
        isPrivate = isPrivate,
    )

    @Test
    fun mergeMarksPrivateAndSortsByName() {
        val merged = mergeCatalogs(Catalog(apps = listOf(app("b", "Bravo", 1))), listOf(app("a", "alpha", 1)))
        assertEquals(listOf("a", "b"), merged.apps.map { it.packageName })
        assertTrue(merged.apps[0].isPrivate)
        assertFalse(merged.apps[1].isPrivate)
    }

    @Test
    fun mergeKeepsNewerBuildWhenAppIsInBoth() {
        val public = Catalog(apps = listOf(app("x", "X", 5)))
        assertEquals(6L, mergeCatalogs(public, listOf(app("x", "X", 6))).apps.single().latest?.versionCode)
        assertFalse(mergeCatalogs(public, listOf(app("x", "X", 4))).apps.single().isPrivate)
        assertTrue(mergeCatalogs(public, listOf(app("x", "X", 5))).apps.single().isPrivate)
    }

    @Test
    fun privateFlagRoundTrips() {
        val json = """{"apps":[{"packageName":"p","name":"P","private":true}]}"""
        val catalog = CatalogJson.decodeFromString(Catalog.serializer(), json)
        assertTrue(catalog.apps.single().isPrivate)
        val again = CatalogJson.decodeFromString(Catalog.serializer(), CatalogJson.encodeToString(Catalog.serializer(), catalog))
        assertTrue(again.apps.single().isPrivate)
    }
}
