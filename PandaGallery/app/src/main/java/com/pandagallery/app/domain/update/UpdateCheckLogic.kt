package com.pandagallery.app.domain.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Decides whether a newer build of this app has been published to Panda App Store, from the
 * store's catalog.json. Pure: fetching, the stored settings and the dialog live in
 * data/update and ui/update.
 *
 * Builds are compared by build number (Android's versionCode), never by the version string:
 * the build number is the one that always goes up, and the one Android refuses to install
 * backwards.
 */
data class AvailableUpdate(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    /** Bytes, or 0 when the catalog does not say. */
    val size: Long,
)

/** A startup check at most this often, so opening the app a few times a day is not a request each time. */
const val STARTUP_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

/**
 * The newest published release of [packageName] when it is newer than [installedCode], else null.
 * Anything malformed in the catalog is treated as "no update" rather than thrown: a bad catalog
 * must never bother the owner.
 */
fun findUpdate(catalogJson: String, packageName: String, installedCode: Int): AvailableUpdate? {
    val apps = runCatching { Json.parseToJsonElement(catalogJson).jsonObject["apps"] as? JsonArray }
        .getOrNull() ?: return null
    val app = apps.firstOrNull { (it as? JsonObject)?.stringField("packageName") == packageName } as? JsonObject
        ?: return null
    val releases = app["releases"] as? JsonArray ?: return null

    var best: AvailableUpdate? = null
    for (raw in releases) {
        val release = raw as? JsonObject ?: continue
        val code = release.intField("versionCode") ?: continue
        val name = release.stringField("versionName") ?: continue
        val apkUrl = release.stringField("apkUrl") ?: continue
        if (best != null && code <= best.versionCode) continue
        best = AvailableUpdate(
            versionName = name,
            versionCode = code,
            notes = release.stringField("notes")?.trim().orEmpty(),
            apkUrl = apkUrl,
            size = release.longField("size") ?: 0L,
        )
    }
    return best?.takeIf { it.versionCode > installedCode }
}

/** Whether the startup check should run now. */
fun shouldCheckOnStartup(enabled: Boolean, lastCheckedAt: Long?, now: Long): Boolean {
    if (!enabled) return false
    if (lastCheckedAt == null || lastCheckedAt > now) return true
    return now - lastCheckedAt >= STARTUP_CHECK_INTERVAL_MS
}

/** Whether to announce [update] on startup: not when the owner already skipped that build or a newer one. */
fun shouldAnnounce(update: AvailableUpdate, skippedCode: Int?): Boolean =
    skippedCode == null || update.versionCode > skippedCode

private fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intField(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.longField(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull
