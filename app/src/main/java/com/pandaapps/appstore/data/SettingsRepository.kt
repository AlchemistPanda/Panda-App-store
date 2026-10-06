package com.pandaapps.appstore.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class Settings(
    /** 0 = off; otherwise one of [SettingsRepository.CHECK_INTERVAL_OPTIONS]. */
    val checkIntervalHours: Int = SettingsRepository.DEFAULT_CHECK_INTERVAL_HOURS,
    val autoUpdate: Boolean = false,
    val wifiOnly: Boolean = true,
    /** `pkg:versionCode` keys already announced in a notification. */
    val notifiedVersions: Set<String> = emptySet(),
    /**
     * `pkg:versionCode` keys whose background download could not finish within the update worker's
     * time limit. The worker no longer tries them (it would restart from zero every period); they
     * are announced instead, and the user installs them from the app.
     */
    val deferredVersions: Set<String> = emptySet(),
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<Settings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** Current settings, read once. */
    suspend fun snapshot(): Settings = settings.first()

    suspend fun setCheckIntervalHours(hours: Int) {
        require(hours in CHECK_INTERVAL_OPTIONS) { "Unsupported interval: $hours" }
        dataStore.edit { it[Keys.CHECK_INTERVAL] = hours }
    }

    suspend fun setAutoUpdate(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_UPDATE] = enabled }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    /** Records `pkg:code` keys (see [notifiedKey]) as announced. */
    suspend fun markNotified(keys: Collection<String>) {
        if (keys.isEmpty()) return
        dataStore.edit { it[Keys.NOTIFIED] = it[Keys.NOTIFIED].orEmpty() + keys }
    }

    /** Records `pkg:code` keys whose background download ran out of time (see [Settings.deferredVersions]). */
    suspend fun markDeferred(keys: Collection<String>) {
        if (keys.isEmpty()) return
        dataStore.edit { it[Keys.DEFERRED] = it[Keys.DEFERRED].orEmpty() + keys }
    }

    /**
     * Drops notified and deferred keys that are no longer pending (installed, superseded or not in
     * the catalog any more).
     */
    suspend fun retainPending(keep: Set<String>) {
        dataStore.edit { prefs ->
            for (key in listOf(Keys.NOTIFIED, Keys.DEFERRED)) {
                val current = prefs[key].orEmpty()
                val retained = current intersect keep
                if (retained.size != current.size) prefs[key] = retained
            }
        }
    }

    private fun Preferences.toSettings() = Settings(
        checkIntervalHours = this[Keys.CHECK_INTERVAL]?.takeIf { it in CHECK_INTERVAL_OPTIONS }
            ?: DEFAULT_CHECK_INTERVAL_HOURS,
        autoUpdate = this[Keys.AUTO_UPDATE] ?: false,
        wifiOnly = this[Keys.WIFI_ONLY] ?: true,
        notifiedVersions = this[Keys.NOTIFIED].orEmpty(),
        deferredVersions = this[Keys.DEFERRED].orEmpty(),
    )

    private object Keys {
        val CHECK_INTERVAL = intPreferencesKey("check_interval_hours")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val NOTIFIED = stringSetPreferencesKey("notified_versions")
        val DEFERRED = stringSetPreferencesKey("deferred_versions")
    }

    companion object {
        const val DEFAULT_CHECK_INTERVAL_HOURS = 6
        val CHECK_INTERVAL_OPTIONS: List<Int> = listOf(0, 1, 3, 6, 12, 24)
    }
}
