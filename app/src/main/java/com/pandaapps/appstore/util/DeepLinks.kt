package com.pandaapps.appstore.util

import android.net.Uri

/** A parsed `pandastore://` link (sent by ntfy pushes and our own notifications). */
sealed interface DeepLink {
    /** `pandastore://home` */
    data object Home : DeepLink

    /** `pandastore://app/<packageName>` */
    data class App(val packageName: String) : DeepLink
}

object DeepLinks {
    const val SCHEME = "pandastore"
    const val HOST_APP = "app"
    const val HOST_HOME = "home"

    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun homeUri(): Uri = Uri.parse("$SCHEME://$HOST_HOME")

    fun appUri(packageName: String): Uri = Uri.parse("$SCHEME://$HOST_APP/$packageName")

    fun parse(uri: Uri?): DeepLink? {
        if (uri == null) return null
        return parse(uri.scheme, uri.host, uri.pathSegments.orEmpty())
    }

    /** Pure overload (no android.net.Uri) so it can be unit tested on the JVM. */
    fun parse(scheme: String?, host: String?, pathSegments: List<String>): DeepLink? {
        if (!scheme.equals(SCHEME, ignoreCase = true)) return null
        return when (host?.lowercase()) {
            HOST_HOME -> DeepLink.Home
            HOST_APP -> pathSegments.firstOrNull()
                ?.takeIf { PACKAGE_NAME.matches(it) }
                ?.let { DeepLink.App(it) }
            else -> null
        }
    }
}
