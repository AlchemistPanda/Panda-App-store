package com.pandaapps.appstore.install

import com.pandaapps.appstore.data.CatalogApp
import com.pandaapps.appstore.data.CatalogRelease
import com.pandaapps.appstore.data.InstalledAppsRepository
import com.pandaapps.appstore.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

sealed interface VerifyResult {
    data object Ok : VerifyResult
    data class Rejected(val message: String) : VerifyResult
}

/**
 * Checks a downloaded APK against the catalog before it is handed to PackageInstaller:
 * size, sha256, package name, versionCode, signer vs the pinned [TRUSTED_SIGNERS], signer vs
 * catalog, signer vs the installed app.
 */
class ApkVerifier(
    private val installedApps: InstalledAppsRepository,
    private val appLog: AppLog,
) {

    suspend fun verify(apk: File, app: CatalogApp, release: CatalogRelease): VerifyResult = withContext(Dispatchers.IO) {
        release.size?.let { expected ->
            if (apk.length() != expected) {
                return@withContext reject("Download size mismatch (${apk.length()} bytes, expected $expected). Try again.")
            }
        }

        release.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = sha256Of(apk)
            if (!actual.equals(expected.trim(), ignoreCase = true)) {
                return@withContext reject("Download is corrupted (SHA-256 does not match the catalog). Try again.")
            }
        }

        val archive = installedApps.archiveInfo(apk)
            ?: return@withContext reject("The downloaded file is not a valid APK.")

        if (archive.packageName != app.packageName) {
            return@withContext reject("This APK is for ${archive.packageName}, not ${app.packageName}.")
        }
        if (archive.versionCode != release.versionCode) {
            return@withContext reject(
                "This APK is build ${archive.versionCode}, but the catalog says build ${release.versionCode}."
            )
        }

        val installed = installedApps.get(app.packageName)
        signerProblem(
            appName = app.name,
            archiveSigners = archive.signers,
            catalogSigner = release.signerSha256,
            installedSigners = installed?.signerSha256,
        )?.let { return@withContext reject(it) }
        VerifyResult.Ok
    }

    private fun reject(message: String): VerifyResult {
        appLog.w(TAG, "Verification failed: $message")
        return VerifyResult.Rejected(message)
    }

    private suspend fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "Verify"

        const val UNREADABLE_SIGNER_MESSAGE = "Could not read the APK's signature. Not installing it."
        const val CATALOG_SIGNER_MESSAGE = "This APK is not signed with the key the catalog expects. Not installing it."
        const val UNTRUSTED_SIGNER_MESSAGE = "This APK is signed with a key Panda App Store doesn't trust. Not installing it."

        /**
         * SHA-256 digests (lowercase hex) of the owner's signing certs. Pinned in the app so a
         * compromised catalog can't get a fresh install signed by some other key past [signerProblem].
         * A new signing key means adding it here and shipping a store build first.
         */
        val TRUSTED_SIGNERS: Set<String> = setOf(
            // ~/.android/debug.keystore: the store, Casio Hunt, Panda Grab.
            "ae234c8a18366995e0f656128c383dc7f6464f42332c913552d12bb9b3abd509",
            // Panda Garage (and its friends edition) own keystore.
            "fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c",
            // Panda Gallery's own release keystore (panda-gallery-release.jks).
            "76005928c7be52235cdb6c4fa2ff16acfe782f40ea8e7d4a4345320550c427b9",
        )

        /**
         * The signer checks of SPEC #4, as a pure function. Returns the rejection message, or null
         * when the APK may be installed. Fails closed: an APK whose signers can't be read is never
         * installed, since neither the catalog nor the installed-app check could be done.
         *
         * @param archiveSigners lowercase hex SHA-256 of the APK's signing certs.
         * @param catalogSigner the release's `signerSha256` (any case; blank = not published).
         * @param installedSigners signers of the installed app, or null when it isn't installed.
         * @param trustedSigners at least one archive signer must be in here (lowercase hex).
         */
        fun signerProblem(
            appName: String,
            archiveSigners: Set<String>,
            catalogSigner: String?,
            installedSigners: Set<String>?,
            // ponytail: no user-facing override for untrusted keys; add a setting if a third-party signer is ever wanted.
            trustedSigners: Set<String> = TRUSTED_SIGNERS,
        ): String? {
            if (archiveSigners.isEmpty()) return UNREADABLE_SIGNER_MESSAGE
            if (archiveSigners.none { it in trustedSigners }) return UNTRUSTED_SIGNER_MESSAGE
            val expected = catalogSigner?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (expected != null && expected !in archiveSigners) return CATALOG_SIGNER_MESSAGE
            if (!installedSigners.isNullOrEmpty() && archiveSigners.none { it in installedSigners }) {
                return signerMismatchMessage(appName)
            }
            return null
        }

        fun signerMismatchMessage(appName: String): String =
            "This build is signed with a different key than the installed app. " +
                "Uninstall $appName first, then install."
    }
}
