package com.pandaapps.appstore.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Request
import java.security.MessageDigest

/**
 * Owner mode: private apps live in a private GitHub repo that only a read-only token can read.
 * The owner unlocks it once per phone (hidden passcode in Settings, then the token); the token is
 * kept in app-private storage and [interceptor] adds it to requests for that repo and nothing else.
 * The passcode only hides the door; the token is what actually guards the private apps.
 *
 * SharedPreferences rather than DataStore so the interceptor can read the token synchronously,
 * including from a cold-started update worker.
 */
class OwnerAccess(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, null))

    /** The saved token, kept while owner mode is off so turning it back on needs only the passcode. */
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false) && _token.value != null)

    /** Private apps are shown (a token is saved and owner mode is on). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** The token to send, or null while owner mode is off. */
    val activeToken: String? get() = _token.value?.takeIf { _enabled.value }

    fun enable(token: String) {
        prefs.edit { putString(KEY_TOKEN, token).putBoolean(KEY_ENABLED, true) }
        _token.value = token
        _enabled.value = true
    }

    /** Hides private apps again; the token stays saved for next time. */
    fun disable() {
        prefs.edit { putBoolean(KEY_ENABLED, false) }
        _enabled.value = false
    }

    val interceptor = Interceptor { chain -> chain.proceed(authorize(chain.request(), activeToken)) }

    companion object {
        const val PRIVATE_REPO = "AlchemistPanda/Panda-App-store-private"
        const val PRIVATE_CATALOG_URL = "https://api.github.com/repos/$PRIVATE_REPO/contents/catalog.json?ref=store"
        private const val API_PREFIX = "https://api.github.com/repos/$PRIVATE_REPO/"

        private const val PREFS = "owner"
        private const val KEY_TOKEN = "github_token"
        private const val KEY_ENABLED = "enabled"

        /** SHA-256 of the passcode, so it isn't sitting in the APK as plain text. */
        private const val PASSCODE_SHA256 = "c4876de490dcf38b74d6c0d4f120cf01126c3d6a3a49b93ec81caae38ea1497e"

        fun isPasscode(input: String): Boolean = sha256(input.trim()) == PASSCODE_SHA256

        /**
         * Adds [token] to requests for the private repo (catalog, icons via the contents API, APKs
         * via the release-asset API, which answers with a redirect to a signed CDN link; OkHttp drops
         * the Authorization header when it follows a redirect to another host). Every other request,
         * and requests that already carry credentials, pass through unchanged.
         */
        internal fun authorize(request: Request, token: String?): Request {
            val url = request.url.toString()
            if (token == null || !url.startsWith(API_PREFIX) || request.header("Authorization") != null) return request
            val builder = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("X-GitHub-Api-Version", "2022-11-28")
            when {
                "/releases/assets/" in url -> builder.header("Accept", "application/octet-stream")
                "/contents/" in url -> builder.header("Accept", "application/vnd.github.raw")
            }
            return builder.build()
        }

        internal fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
