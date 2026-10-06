package com.pandaapps.appstore.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.security.MessageDigest

/** What an APK file on disk says about itself. */
data class ArchiveInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    /** Lowercase hex SHA-256 of signing certs; empty when the platform could not read them. */
    val signers: Set<String>,
)

/**
 * Reads installed-package facts from PackageManager and notices package changes.
 * [changes] bumps on PACKAGE_ADDED/REPLACED/REMOVED/FULLY_REMOVED (dynamic receiver, registered
 * for the lifetime of the process) and whenever [refresh] is called.
 */
class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        // Package broadcasts are protected system broadcasts, so a non-exported receiver still gets them.
        ContextCompat.registerReceiver(appContext, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Bump [changes] so observers recompute statuses. */
    fun refresh() {
        _changes.update { it + 1 }
    }

    fun isInstalled(packageName: String): Boolean = packageInfo(packageName, 0) != null

    fun get(packageName: String): InstalledInfo? {
        val info = packageInfo(packageName, signingFlags()) ?: return null
        return InstalledInfo(
            versionCode = info.longVersionCodeCompat(),
            versionName = info.versionName,
            signerSha256 = signersOf(info),
            installerPackage = installerOf(packageName),
        )
    }

    fun launchIntent(packageName: String): Intent? =
        pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Installed app's own launcher icon (fallback when the catalog has no iconUrl). */
    fun appIcon(packageName: String): Drawable? = try {
        pm.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** targetSdkVersion of the installed app, or null when not installed. */
    fun targetSdk(packageName: String): Int? = packageInfo(packageName, 0)?.applicationInfo?.targetSdkVersion

    /** Installer of record (package that installed / owns updates), or null. */
    fun installerOf(packageName: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Lowercase hex SHA-256 of the APK's signing certs; empty if unreadable. */
    fun archiveSigners(apk: File): Set<String> = archiveInfo(apk)?.signers.orEmpty()

    /** Parses an APK on disk, or null if PackageManager cannot read it. */
    fun archiveInfo(apk: File): ArchiveInfo? {
        val info = archivePackageInfo(apk.absolutePath, archiveSigningFlags()) ?: return null
        return ArchiveInfo(
            packageName = info.packageName,
            versionCode = info.longVersionCodeCompat(),
            versionName = info.versionName,
            signers = signersOf(info),
        )
    }

    private fun packageInfo(packageName: String, flags: Int): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun archivePackageInfo(path: String, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(path, flags)
        }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    /**
     * Flags for parsing an APK file. Android 9's `getPackageArchiveInfo` only collects certificates
     * when GET_SIGNATURES is set (Android 10 also accepts GET_SIGNING_CERTIFICATES), so ask for both:
     * without it every archive on API 28 would come back with no signers at all.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures") // only used on a downloaded file we then verify
    private fun archiveSigningFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }

    /**
     * API 28+: current signers when signed by multiple keys, otherwise the whole rotation history so
     * an app whose key was rotated still matches the original. Falls back to the legacy signatures
     * when the platform filled only those (API 26–27, or an API 28 archive parse).
     */
    private fun signersOf(info: PackageInfo): Set<String> {
        val signatures: Array<out Signature>? =
            signingCertificates(info) ?: @Suppress("DEPRECATION") info.signatures
        return signatures.orEmpty().mapTo(mutableSetOf()) { sha256Hex(it.toByteArray()) }
    }

    /** API 28+ signing certificates, or null when below API 28 or the platform didn't fill them. */
    private fun signingCertificates(info: PackageInfo): Array<out Signature>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val signing = info.signingInfo ?: return null
        return if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode
        else @Suppress("DEPRECATION") versionCode.toLong()

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
