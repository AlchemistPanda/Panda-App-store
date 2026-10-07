package com.pandaapps.appstore.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `catalog.json` from the `store` branch, as written by `tools/panda-publish`.
 * Everything is optional except packageName/name on apps and
 * versionCode/versionName/apkUrl on releases.
 */
@Serializable
data class Catalog(
    val schema: Int = 1,
    val apps: List<CatalogApp> = emptyList(),
    val updatedAt: String? = null,
)

@Serializable
data class CatalogApp(
    val packageName: String,
    val name: String,
    val iconUrl: String? = null,
    val updatedAt: String? = null,
    /** Newest first (the publisher keeps at most two). */
    val releases: List<CatalogRelease> = emptyList(),
    /**
     * Release notes of past versions, newest first, kept after their APKs are pruned (the publisher
     * caps the list). May overlap [releases]. Absent in catalogs written before it existed.
     */
    val history: List<CatalogNote> = emptyList(),
) {
    /** Highest versionCode, independent of list order. */
    val latest: CatalogRelease? get() = releases.maxByOrNull { it.versionCode }

    /** Every release except [latest], newest first. */
    val previous: List<CatalogRelease>
        get() {
            val newest = latest ?: return emptyList()
            return releases.filter { it !== newest }.sortedByDescending { it.versionCode }
        }
}

@Serializable
data class CatalogRelease(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val tag: String? = null,
    val size: Long? = null,
    /** Lowercase hex, no colons. */
    val sha256: String? = null,
    /** SHA-256 of the signing certificate (DER), lowercase hex, no colons. */
    val signerSha256: String? = null,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val releasedAt: String? = null,
    val notes: String? = null,
)

/** Notes of one published version; outlives the APK (see [CatalogApp.history]). */
@Serializable
data class CatalogNote(
    val versionName: String,
    val versionCode: Long,
    val releasedAt: String? = null,
    val notes: String? = null,
)

/** The JSON configuration used for the catalog everywhere (tolerant of unknown keys and nulls). */
val CatalogJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}
