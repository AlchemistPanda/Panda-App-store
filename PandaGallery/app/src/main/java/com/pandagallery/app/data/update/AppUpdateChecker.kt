package com.pandagallery.app.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.pandagallery.app.BuildConfig
import com.pandagallery.app.domain.update.AvailableUpdate
import com.pandagallery.app.domain.update.findUpdate
import com.pandagallery.app.domain.update.shouldAnnounce
import com.pandagallery.app.domain.update.shouldCheckOnStartup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks Panda App Store for a newer build of this app and hands over to it when the owner says so.
 *
 * The catalog is the same public catalog.json the store app reads, so there is nothing to
 * configure: this app's package picks its own entry. "Updating" opens Panda App Store, which
 * downloads and installs the build and keeps the signing check. If the store is not installed,
 * the APK link opens in the browser instead. The app never installs anything itself.
 *
 * A failed check (offline, GitHub down, odd catalog) is silent. It never blocks the app opening.
 */
object AppUpdateChecker {
    // The same public catalog the Panda App Store app reads.
    private const val CATALOG_URL = "https://raw.githubusercontent.com/AlchemistPanda/Panda-App-store/store/catalog.json"

    // The release package. Debug builds carry a ".debug" suffix, but they look up this entry too.
    private const val PACKAGE_NAME = "com.pandagallery.app"
    private const val TIMEOUT_MS = 10_000

    private const val PREFS = "update_check"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_SKIPPED_CODE = "skipped_code"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** On unless the owner turned it off. */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun skipVersion(context: Context, versionCode: Int) {
        prefs(context).edit { putInt(KEY_SKIPPED_CODE, versionCode) }
    }

    /** Runs once when the app opens: respects the setting, the throttle and "skip this version". */
    suspend fun checkOnStartup(context: Context): AvailableUpdate? {
        val prefs = prefs(context)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L).takeIf { it > 0L }
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        if (!shouldCheckOnStartup(enabled, lastCheck, System.currentTimeMillis())) return null

        val update = fetchUpdate(context) ?: return null
        val skipped = prefs.getInt(KEY_SKIPPED_CODE, 0).takeIf { it > 0 }
        return update.takeIf { shouldAnnounce(it, skipped) }
    }

    /** The newer build when one is published, else null. Records the check time only when the catalog was read. */
    private suspend fun fetchUpdate(context: Context): AvailableUpdate? = withContext(Dispatchers.IO) {
        val catalog = fetchCatalog() ?: return@withContext null
        prefs(context).edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
        findUpdate(catalog, PACKAGE_NAME, BuildConfig.VERSION_CODE)
    }

    private fun fetchCatalog(): String? {
        val connection = URL(CATALOG_URL).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (error: IOException) {
            null
        } catch (error: SecurityException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Opens Panda App Store on this build's entry, or the APK link in the browser when the store is missing. */
    fun openUpdate(context: Context, update: AvailableUpdate) {
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("pandastore://app/$PACKAGE_NAME"))
        try {
            context.startActivity(store)
            return
        } catch (error: ActivityNotFoundException) {
            // Panda App Store is not installed; fall through to the download link.
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl)))
        } catch (error: ActivityNotFoundException) {
            // No browser either; nothing more the app can offer.
        }
    }
}
