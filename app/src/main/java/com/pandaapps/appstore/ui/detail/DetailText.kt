package com.pandaapps.appstore.ui.detail

import com.pandaapps.appstore.data.AppStatus
import com.pandaapps.appstore.data.CatalogRelease
import com.pandaapps.appstore.data.StoreApp

/*
 * Pure text helpers for the app detail screen (no Android or Compose types, so they are
 * JVM-testable).
 */

/** Headline + body explaining an app's [AppStatus] on the detail screen. */
data class StatusExplanation(val title: String, val body: String)

/** Marketing name of an Android API level: 24 → "7.0", 33 → "13". Unknown levels → "API n". */
fun androidVersionName(sdk: Int): String = when (sdk) {
    21 -> "5.0"
    22 -> "5.1"
    23 -> "6"
    24 -> "7.0"
    25 -> "7.1"
    26 -> "8.0"
    27 -> "8.1"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31 -> "12"
    32 -> "12L"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    36 -> "16"
    else -> "API $sdk"
}

/** "Android 7.0 (API 24)", or "API 40" for levels without a known name. */
fun androidLabel(sdk: Int): String {
    val name = androidVersionName(sdk)
    return if (name.startsWith("API")) name else "Android $name (API $sdk)"
}

/** "Android 7.0 or newer", or null when the release doesn't declare a minimum. */
fun requirementText(minSdk: Int?): String? = minSdk?.let {
    val name = androidVersionName(it)
    if (name.startsWith("API")) "$name or newer" else "Android $name or newer"
}

/** "v1.0.6 · build 7" — version without the size. */
fun versionWithBuild(versionName: String?, versionCode: Long): String =
    if (versionName.isNullOrBlank()) "build $versionCode" else "v$versionName · build $versionCode"

/**
 * Explains [app]'s status in plain words.
 *
 * @param deviceSdk `Build.VERSION.SDK_INT`.
 * @param silent updates of this app complete without a prompt.
 */
fun statusExplanation(app: StoreApp, deviceSdk: Int, silent: Boolean): StatusExplanation {
    val latest = app.latest
    val latestLabel = versionWithBuild(latest.versionName, latest.versionCode)
    val installed = app.installed
    val installedLabel = installed?.let { versionWithBuild(it.versionName, it.versionCode) }
    return when (app.status) {
        AppStatus.NotInstalled -> StatusExplanation(
            title = "Not installed",
            body = "${app.name} isn't on this device yet. Install $latestLabel from your store.",
        )

        AppStatus.UpToDate -> StatusExplanation(
            title = "Up to date",
            body = "You have the newest version ($latestLabel)." +
                if (silent) " Future updates install without asking." else "",
        )

        AppStatus.UpdateAvailable -> StatusExplanation(
            title = "Update available",
            body = "$latestLabel is ready. You have $installedLabel." +
                if (silent) {
                    " It will install without a confirmation prompt."
                } else {
                    " Android will ask you to confirm the update."
                },
        )

        AppStatus.InstalledNewer -> StatusExplanation(
            title = "Newer build installed",
            body = "The installed $installedLabel is newer than anything in the store ($latestLabel) — " +
                "probably a development build. Nothing to do until a newer release is published.",
        )

        AppStatus.SignerMismatch -> StatusExplanation(
            title = "Signed with a different key",
            body = "The installed ${app.name} was signed with a different key than the store's builds, " +
                "so Android won't update it in place. Uninstall ${app.name} first, then install it from " +
                "here. Uninstalling deletes the app's data on this device.",
        )

        AppStatus.Incompatible -> {
            val needs = latest.minSdk?.let { androidLabel(it) } ?: "a newer Android version"
            StatusExplanation(
                title = "Not compatible with this device",
                body = "$latestLabel needs $needs or newer. This device runs ${androidLabel(deviceSdk)}." +
                    if (installed != null) " You can keep using the installed $installedLabel." else "",
            )
        }
    }
}

/** Why the previous [release] can't be installed on this device, or null when it can. */
fun releaseIncompatibility(release: CatalogRelease, deviceSdk: Int): String? {
    val min = release.minSdk ?: return null
    return if (deviceSdk < min) "Needs ${androidLabel(min)} or newer." else null
}
