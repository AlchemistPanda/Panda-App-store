package com.pandaapps.appstore.data

enum class AppStatus { NotInstalled, UpToDate, UpdateAvailable, InstalledNewer, SignerMismatch, Incompatible }

/** What PackageManager knows about an installed app. Signers are lowercase hex SHA-256 of each certificate. */
data class InstalledInfo(
    val versionCode: Long,
    val versionName: String?,
    val signerSha256: Set<String>,
    val installerPackage: String?,
)

/** UI model: a catalog app with its newest release, what is installed, and the resulting status. */
data class StoreApp(
    val app: CatalogApp,
    val latest: CatalogRelease,
    val installed: InstalledInfo?,
    val status: AppStatus,
) {
    val packageName: String get() = app.packageName
    val name: String get() = app.name
}

/**
 * Status truth table (pure; see SPEC "Behaviour details" #1):
 * - not installed → [AppStatus.Incompatible] if the device is below minSdk, else [AppStatus.NotInstalled]
 * - installed, catalog signer known and not among installed signers → [AppStatus.SignerMismatch]
 * - installed versionCode lower → [AppStatus.UpdateAvailable] ([AppStatus.Incompatible] if below minSdk)
 * - equal → [AppStatus.UpToDate]; higher → [AppStatus.InstalledNewer]
 */
fun computeStatus(latest: CatalogRelease, installed: InstalledInfo?, deviceSdk: Int): AppStatus {
    val tooOld = latest.minSdk != null && deviceSdk < latest.minSdk
    if (installed == null) return if (tooOld) AppStatus.Incompatible else AppStatus.NotInstalled

    val catalogSigner = latest.signerSha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    if (catalogSigner != null && installed.signerSha256.none { it.equals(catalogSigner, ignoreCase = true) }) {
        return AppStatus.SignerMismatch
    }
    return when {
        installed.versionCode < latest.versionCode -> if (tooOld) AppStatus.Incompatible else AppStatus.UpdateAvailable
        installed.versionCode == latest.versionCode -> AppStatus.UpToDate
        else -> AppStatus.InstalledNewer
    }
}

/** Builds the UI model, or null when the catalog app has no releases. */
fun CatalogApp.toStoreApp(installed: InstalledInfo?, deviceSdk: Int): StoreApp? {
    val newest = latest ?: return null
    return StoreApp(this, newest, installed, computeStatus(newest, installed, deviceSdk))
}

/** Stable key for "already notified about this version" bookkeeping: `pkg:versionCode`. */
fun notifiedKey(packageName: String, versionCode: Long): String = "$packageName:$versionCode"
