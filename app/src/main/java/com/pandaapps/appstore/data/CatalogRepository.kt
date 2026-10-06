package com.pandaapps.appstore.data

import android.content.Context
import com.pandaapps.appstore.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * @property catalog last good catalog (from network or the on-disk cache), null until one is available.
 * @property lastUpdated epoch millis of the last successful network fetch, null if never fetched.
 * @property isRefreshing a network refresh is in flight.
 * @property error human message of the last failed refresh; cleared on success.
 * @property isOffline the shown catalog came from the cache because the last refresh failed.
 */
data class CatalogState(
    val catalog: Catalog? = null,
    val lastUpdated: Long? = null,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
)

/**
 * Fetches `catalog.json`: GitHub contents API first (fresh, rate-limited 60/h/IP), then the
 * raw.githubusercontent.com CDN (may be ~5 min stale). The last good catalog JSON is cached in
 * `filesDir/catalog.json` and loaded at construction so the UI has something offline.
 */
class CatalogRepository(
    context: Context,
    private val okHttp: OkHttpClient,
    private val json: Json,
    private val appLog: AppLog,
    scope: CoroutineScope,
) {
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { loadCache() }
    }

    /**
     * Refreshes from the network. Concurrent callers share one fetch (the second waits and returns
     * the fresh result). Never throws; failures land in [CatalogState.error].
     */
    suspend fun refresh(): Result<Catalog> {
        if (refreshMutex.isLocked) {
            refreshMutex.withLock { }
            return _state.value.catalog?.takeIf { _state.value.error == null }?.let { Result.success(it) }
                ?: Result.failure(IOException(_state.value.error ?: "Catalog unavailable"))
        }
        return refreshMutex.withLock {
            _state.update { it.copy(isRefreshing = true) }
            val result = try {
                withContext(Dispatchers.IO) { fetch() }
            } catch (e: CancellationException) {
                // The caller went away (screen closed, worker stopped): don't leave the spinner on.
                _state.update { it.copy(isRefreshing = false) }
                throw e
            }
            result.fold(
                onSuccess = { (catalog, raw) ->
                    val now = System.currentTimeMillis()
                    withContext(Dispatchers.IO) { writeCache(raw, now) }
                    _state.value = CatalogState(catalog = catalog, lastUpdated = now)
                    appLog.i(TAG, "Catalog refreshed: ${catalog.apps.size} apps")
                    Result.success(catalog)
                },
                onFailure = { error ->
                    val message = humanMessage(error)
                    appLog.w(TAG, "Catalog refresh failed: $message", error)
                    _state.update {
                        it.copy(isRefreshing = false, error = message, isOffline = it.catalog != null)
                    }
                    Result.failure(error)
                },
            )
        }
    }

    /** Looks up an app in the current catalog. */
    fun findApp(packageName: String): CatalogApp? =
        _state.value.catalog?.apps?.firstOrNull { it.packageName == packageName }

    private fun fetch(): Result<Pair<Catalog, String>> {
        val fresh = runCatching { download(freshRequest()) }
        fresh.onSuccess { return Result.success(it) }
        appLog.w(TAG, "Fresh catalog URL failed (${fresh.exceptionOrNull()?.message}); trying CDN")
        return runCatching { download(fallbackRequest()) }
    }

    private fun download(request: Request): Pair<Catalog, String> {
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val raw = response.body?.string() ?: throw IOException("Empty catalog response")
            return parse(raw) to raw
        }
    }

    private fun parse(raw: String): Catalog = json.decodeFromString(Catalog.serializer(), raw)

    private fun loadCache() {
        if (!cacheFile.exists()) return
        try {
            val catalog = parse(cacheFile.readText())
            val stamp = cacheFile.lastModified().takeIf { it > 0 }
            _state.update { current ->
                // A network refresh may already have completed; never overwrite it with the cache.
                if (current.catalog != null) current else current.copy(catalog = catalog, lastUpdated = stamp)
            }
            appLog.d(TAG, "Loaded cached catalog (${catalog.apps.size} apps)")
        } catch (e: Exception) {
            appLog.w(TAG, "Ignoring unreadable catalog cache", e)
            cacheFile.delete()
        }
    }

    private fun writeCache(raw: String, timestamp: Long) {
        try {
            val tmp = File(cacheFile.parentFile, "$CACHE_FILE_NAME.tmp")
            tmp.writeText(raw)
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.writeText(raw)
                tmp.delete()
            }
            cacheFile.setLastModified(timestamp)
        } catch (e: IOException) {
            appLog.w(TAG, "Could not write catalog cache", e)
        }
    }

    private fun freshRequest() = Request.Builder()
        .url(FRESH_URL)
        .header("Accept", "application/vnd.github.raw")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", USER_AGENT)
        .header("Cache-Control", "no-cache")
        .build()

    private fun fallbackRequest() = Request.Builder()
        .url(FALLBACK_URL)
        .header("User-Agent", USER_AGENT)
        .header("Cache-Control", "no-cache")
        .build()

    private fun humanMessage(error: Throwable): String = when (error) {
        is HttpStatusException -> when (error.code) {
            403, 429 -> "GitHub rate limit reached. Try again in a few minutes."
            404 -> "Catalog not found on GitHub."
            in 500..599 -> "GitHub is having trouble (HTTP ${error.code})."
            else -> "Could not load the catalog (HTTP ${error.code})."
        }
        is SerializationException, is IllegalArgumentException -> "The catalog is malformed."
        is IOException -> "No internet connection."
        else -> error.message ?: "Could not load the catalog."
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    companion object {
        private const val TAG = "Catalog"
        private const val CACHE_FILE_NAME = "catalog.json"
        private const val USER_AGENT = "PandaAppStore-Android"
        const val FRESH_URL =
            "https://api.github.com/repos/AlchemistPanda/Panda-App-store/contents/catalog.json?ref=store"
        const val FALLBACK_URL =
            "https://raw.githubusercontent.com/AlchemistPanda/Panda-App-store/store/catalog.json"
    }
}
