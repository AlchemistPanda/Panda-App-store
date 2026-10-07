package com.pandaapps.appstore.data

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CatalogModelsTest {

    private fun parse(json: String): Catalog = CatalogJson.decodeFromString(Catalog.serializer(), json)

    private val liveSample = """
        {
          "schema": 1,
          "apps": [
            {
              "packageName": "com.pandacollection.pandagarage",
              "name": "Panda Garage",
              "iconUrl": "https://raw.githubusercontent.com/AlchemistPanda/Panda-App-store/store/icons/com.pandacollection.pandagarage.webp",
              "updatedAt": "2026-10-04T08:19:02Z",
              "releases": [
                {
                  "versionName": "1.0.6",
                  "versionCode": 7,
                  "tag": "com.pandacollection.pandagarage-v1.0.6-b7",
                  "apkUrl": "https://github.com/AlchemistPanda/Panda-App-store/releases/download/com.pandacollection.pandagarage-v1.0.6-b7/PandaGarage-v1.0.6-2026-10-02.apk",
                  "size": 176745929,
                  "sha256": "549c38…0dd8",
                  "signerSha256": "fac617…3b9c",
                  "minSdk": 24,
                  "targetSdk": 36,
                  "releasedAt": "2026-10-04T08:19:02Z",
                  "notes": "First release in Panda App Store."
                }
              ]
            }
          ],
          "updatedAt": "2026-10-04T08:19:02Z"
        }
    """.trimIndent()

    @Test
    fun parsesLiveSample() {
        val catalog = parse(liveSample)

        assertEquals(1, catalog.schema)
        assertEquals("2026-10-04T08:19:02Z", catalog.updatedAt)
        assertEquals(1, catalog.apps.size)

        val app = catalog.apps.single()
        assertEquals("com.pandacollection.pandagarage", app.packageName)
        assertEquals("Panda Garage", app.name)
        assertEquals(
            "https://raw.githubusercontent.com/AlchemistPanda/Panda-App-store/store/icons/com.pandacollection.pandagarage.webp",
            app.iconUrl,
        )
        assertEquals("2026-10-04T08:19:02Z", app.updatedAt)

        val release = app.releases.single()
        assertEquals(
            CatalogRelease(
                versionName = "1.0.6",
                versionCode = 7,
                apkUrl = "https://github.com/AlchemistPanda/Panda-App-store/releases/download/" +
                    "com.pandacollection.pandagarage-v1.0.6-b7/PandaGarage-v1.0.6-2026-10-02.apk",
                tag = "com.pandacollection.pandagarage-v1.0.6-b7",
                size = 176745929,
                sha256 = "549c38…0dd8",
                signerSha256 = "fac617…3b9c",
                minSdk = 24,
                targetSdk = 36,
                releasedAt = "2026-10-04T08:19:02Z",
                notes = "First release in Panda App Store.",
            ),
            release,
        )
        assertEquals(release, app.latest)
        assertTrue(app.previous.isEmpty())
    }

    @Test
    fun missingOptionalFields_defaultToNull() {
        val catalog = parse(
            """
            {"apps":[{"packageName":"com.example.a","name":"A",
              "releases":[{"versionName":"1.0","versionCode":1,"apkUrl":"https://x/a.apk"}]}]}
            """.trimIndent(),
        )
        assertEquals(1, catalog.schema)
        assertNull(catalog.updatedAt)

        val app = catalog.apps.single()
        assertNull(app.iconUrl)
        assertNull(app.updatedAt)

        val r = app.releases.single()
        assertEquals("1.0", r.versionName)
        assertEquals(1L, r.versionCode)
        assertEquals("https://x/a.apk", r.apkUrl)
        assertNull(r.tag)
        assertNull(r.size)
        assertNull(r.sha256)
        assertNull(r.signerSha256)
        assertNull(r.minSdk)
        assertNull(r.targetSdk)
        assertNull(r.releasedAt)
        assertNull(r.notes)
    }

    @Test
    fun explicitNullOptionalFields_parseAsNull() {
        val catalog = parse(
            """
            {"schema":1,"updatedAt":null,"apps":[{"packageName":"com.example.a","name":"A","iconUrl":null,
              "releases":[{"versionName":"1.0","versionCode":1,"apkUrl":"https://x/a.apk",
                "minSdk":null,"targetSdk":null,"notes":null,"size":null,"sha256":null,"signerSha256":null}]}]}
            """.trimIndent(),
        )
        val app = catalog.apps.single()
        assertNull(catalog.updatedAt)
        assertNull(app.iconUrl)
        val r = app.releases.single()
        assertNull(r.minSdk)
        assertNull(r.targetSdk)
        assertNull(r.notes)
        assertNull(r.size)
        assertNull(r.signerSha256)
    }

    @Test
    fun nullForDefaultedCollections_isCoercedToEmpty() {
        val catalog = parse("""{"schema":1,"apps":null}""")
        assertTrue(catalog.apps.isEmpty())

        val withApp = parse("""{"apps":[{"packageName":"com.example.a","name":"A","releases":null}]}""")
        assertTrue(withApp.apps.single().releases.isEmpty())
    }

    @Test
    fun appWithoutReleasesKey_hasNoLatest() {
        val app = parse("""{"apps":[{"packageName":"com.example.a","name":"A"}]}""").apps.single()
        assertTrue(app.releases.isEmpty())
        assertNull(app.latest)
        assertTrue(app.previous.isEmpty())
    }

    @Test
    fun unknownFieldsAtEveryLevel_areIgnored() {
        val catalog = parse(
            """
            {
              "schema": 2,
              "generator": {"name": "panda-publish", "version": [1, 2]},
              "apps": [
                {
                  "packageName": "com.example.a",
                  "name": "A",
                  "category": "tools",
                  "screenshots": ["a.png", "b.png"],
                  "releases": [
                    {"versionName": "1.0", "versionCode": 1, "apkUrl": "https://x/a.apk",
                     "abi": ["arm64-v8a"], "extra": {"nested": true}, "downloads": 12}
                  ]
                }
              ],
              "updatedAt": "2026-10-04T08:19:02Z",
              "futureFlag": true
            }
            """.trimIndent(),
        )
        assertEquals(2, catalog.schema)
        assertEquals("com.example.a", catalog.apps.single().packageName)
        assertEquals(1L, catalog.apps.single().releases.single().versionCode)
    }

    @Test
    fun emptyAppsList_parses() {
        val catalog = parse("""{"schema":1,"apps":[]}""")
        assertTrue(catalog.apps.isEmpty())
        assertEquals(1, catalog.schema)
    }

    @Test
    fun emptyObject_usesDefaults() {
        assertEquals(Catalog(), parse("{}"))
    }

    @Test
    fun missingRequiredReleaseField_throws() {
        assertThrowsSerialization(
            """{"apps":[{"packageName":"com.example.a","name":"A","releases":[{"versionName":"1.0","versionCode":1}]}]}""",
        )
    }

    @Test
    fun missingPackageName_throws() {
        assertThrowsSerialization("""{"apps":[{"name":"A"}]}""")
    }

    @Test
    fun missingAppName_throws() {
        assertThrowsSerialization("""{"apps":[{"packageName":"com.example.a"}]}""")
    }

    @Test
    fun largeVersionCode_parsesAsLong() {
        val r = parse(
            """{"apps":[{"packageName":"p.q","name":"Q","releases":[{"versionName":"9","versionCode":3000000000,"apkUrl":"u"}]}]}""",
        ).apps.single().releases.single()
        assertEquals(3_000_000_000L, r.versionCode)
    }

    @Test
    fun releases_newestFirstAsPublished() {
        val app = parse(
            """
            {"apps":[{"packageName":"com.example.a","name":"A","releases":[
              {"versionName":"1.0.6","versionCode":7,"apkUrl":"https://x/7.apk"},
              {"versionName":"1.0.5","versionCode":6,"apkUrl":"https://x/6.apk"}
            ]}]}
            """.trimIndent(),
        ).apps.single()

        assertEquals(listOf(7L, 6L), app.releases.map { it.versionCode })
        assertEquals(7L, app.latest?.versionCode)
        assertEquals(listOf(6L), app.previous.map { it.versionCode })
    }

    @Test
    fun latestAndPrevious_doNotDependOnListOrder() {
        val app = parse(
            """
            {"apps":[{"packageName":"com.example.a","name":"A","releases":[
              {"versionName":"1.0.4","versionCode":5,"apkUrl":"https://x/5.apk"},
              {"versionName":"1.0.6","versionCode":7,"apkUrl":"https://x/7.apk"},
              {"versionName":"1.0.5","versionCode":6,"apkUrl":"https://x/6.apk"}
            ]}]}
            """.trimIndent(),
        ).apps.single()

        assertEquals("1.0.6", app.latest?.versionName)
        assertEquals(listOf(6L, 5L), app.previous.map { it.versionCode })
    }

    @Test
    fun previous_excludesOnlyTheLatestInstance() {
        val a = CatalogRelease(versionName = "1", versionCode = 1, apkUrl = "u")
        val app = CatalogApp(packageName = "p.q", name = "Q", releases = listOf(a))
        assertEquals(a, app.latest)
        assertTrue(app.previous.isEmpty())
    }

    @Test
    fun history_isEmptyWhenAbsent() {
        assertTrue(parse(liveSample).apps.single().history.isEmpty())
    }

    @Test
    fun history_parsesNewestFirst() {
        val json = """
            {"apps":[{"packageName":"p.q","name":"Q",
              "releases":[{"versionName":"1.0.6","versionCode":7,"apkUrl":"u"}],
              "history":[
                {"versionName":"1.0.6","versionCode":7,"releasedAt":"2026-10-04T08:19:02Z","notes":"Seven"},
                {"versionName":"1.0.5","versionCode":6,"notes":null,"future":"ignored"},
                {"versionName":"1.0.4","versionCode":5}
              ]}]}
        """.trimIndent()
        val history = parse(json).apps.single().history
        assertEquals(
            listOf(
                CatalogNote("1.0.6", 7, "2026-10-04T08:19:02Z", "Seven"),
                CatalogNote("1.0.5", 6),
                CatalogNote("1.0.4", 5),
            ),
            history,
        )
    }

    @Test
    fun encodeThenDecode_roundTrips() {
        val original = parse(liveSample)
        val encoded = CatalogJson.encodeToString(Catalog.serializer(), original)
        assertEquals(original, parse(encoded))
    }

    @Test
    fun encoding_omitsNullFields() {
        val encoded = CatalogJson.encodeToString(
            CatalogRelease.serializer(),
            CatalogRelease(versionName = "1", versionCode = 1, apkUrl = "u"),
        )
        assertTrue(encoded, !encoded.contains("null"))
    }

    private fun assertThrowsSerialization(json: String) {
        try {
            parse(json)
            fail("Expected SerializationException for $json")
        } catch (_: SerializationException) {
            // expected
        }
    }
}
