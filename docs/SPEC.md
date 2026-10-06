# Panda App Store — Android app spec

Personal app store for the owner's own Android apps. Reads a catalog from GitHub,
shows install/update status of each app on the device, downloads, verifies and
installs APKs, and updates them like the Play Store (silently on Android 12+
once Panda App Store is the installer of record).

## Backend (already built and live — do not change)

- Repo: `AlchemistPanda/Panda-App-store` (public).
- Catalog: `catalog.json` on branch `store`.
  - Fresh URL (preferred, no CDN cache, 60 req/h/IP):
    `https://api.github.com/repos/AlchemistPanda/Panda-App-store/contents/catalog.json?ref=store`
    with header `Accept: application/vnd.github.raw`.
  - Fallback URL (CDN, up to ~5 min stale):
    `https://raw.githubusercontent.com/AlchemistPanda/Panda-App-store/store/catalog.json`
- Publisher: `tools/panda-publish` (Python). Read it to see exactly how the
  catalog is produced. Live example of the catalog:

```json
{
  "schema": 1,
  "apps": [
    {
      "packageName": "com.pandacollection.pandagarage",
      "name": "Panda Garage",
      "iconUrl": "https://raw.githubusercontent.com/AlchemistPanda/Panda-App-store/store/icons/com.pandacollection.pandagarage.webp",
      "updatedAt": "2026-10-04T08:19:02Z",
      "releases": [
        {
          "versionName": "1.0.6",
          "versionCode": 7,
          "tag": "com.pandacollection.pandagarage-v1.0.6-b7",
          "apkUrl": "https://github.com/AlchemistPanda/Panda-App-store/releases/download/com.pandacollection.pandagarage-v1.0.6-b7/PandaGarage-v1.0.6-2026-10-02.apk",
          "size": 176745929,
          "sha256": "549c38…0dd8",
          "signerSha256": "fac617…3b9c",
          "minSdk": 24,
          "targetSdk": 36,
          "releasedAt": "2026-10-04T08:19:02Z",
          "notes": "First release in Panda App Store."
        }
      ]
    }
  ],
  "updatedAt": "2026-10-04T08:19:02Z"
}
```

  `releases` is newest first (max 2). `iconUrl`, `minSdk`, `targetSdk`, `notes`
  may be null/absent. Parse with `ignoreUnknownKeys = true`, everything
  optional except packageName/name/versionCode/versionName/apkUrl.
  `signerSha256` and `sha256` are lowercase hex without colons.
- ntfy push: `panda-publish` posts to ntfy.sh with header
  `Click: pandastore://app/<packageName>`. The user taps the notification in
  the ntfy app, which opens that deep link. The app must handle
  `pandastore://app/<packageName>` (open that app's detail screen) and
  `pandastore://home`.
- The catalog will also contain Panda App Store itself
  (`com.pandaapps.appstore`) — self-update must work.

## Project setup

- Location: project root `Panda App Store/` (already a git repo; `tools/`,
  `docs/`, `README.md`, `.gitignore` exist — keep them). Single module `app/`.
- Gradle 8.11.1 wrapper (copy `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
  from `../Casio Hunt/`), AGP 8.7.3, Kotlin 2.1.0 with the compose compiler
  plugin and kotlin serialization plugin, version catalog
  `gradle/libs.versions.toml` (mirror `../Casio Hunt/gradle/libs.versions.toml`
  versions). JDK 21 is installed; use jvmTarget 17.
- `local.properties`: `sdk.dir=/Users/manuraj/Library/Android/sdk` (git-ignored).
- applicationId / namespace: `com.pandaapps.appstore`.
- minSdk 26, targetSdk 35, compileSdk 35.
- versionName `1.0.0`, versionCode `1`.
- Release build: `signingConfig = signingConfigs.getByName("debug")` (uses
  `~/.android/debug.keystore` — same key as the owner's other native apps),
  `isMinifyEnabled = false`. `buildConfig = true`.
- Libraries: core-ktx, activity-compose, lifecycle (runtime-compose,
  viewmodel-compose, process), compose BOM 2024.12.01 + material3 +
  material-icons-extended, navigation-compose, kotlinx-serialization-json,
  kotlinx-coroutines-android, okhttp 4.12.0, coil 3 (`io.coil-kt.coil3:coil-compose`
  + `coil-network-okhttp`), work-runtime-ktx, datastore-preferences.
  Tests: junit 4, kotlinx-coroutines-test. **No Hilt, no Room** — manual DI
  through `AppContainer` keeps the build simple.

## Permissions (AndroidManifest)

INTERNET, ACCESS_NETWORK_STATE, REQUEST_INSTALL_PACKAGES,
REQUEST_DELETE_PACKAGES, QUERY_ALL_PACKAGES (needed: the set of packages is
dynamic; this is a sideloaded personal app), POST_NOTIFICATIONS,
UPDATE_PACKAGES_WITHOUT_USER_ACTION (API 31+ silent updates),
ENFORCE_UPDATE_OWNERSHIP (API 34+), RECEIVE_BOOT_COMPLETED is NOT needed
(WorkManager handles reboot), FOREGROUND_SERVICE not needed.

## Package layout (`com.pandaapps.appstore`) — the file contract

```
PandaStoreApp.kt                Application: creates AppContainer, notification channels, schedules worker
AppContainer.kt                 holds singletons: okHttp, json, catalogRepository, installedAppsRepository,
                                settingsRepository, installManager, appLog
MainActivity.kt                 single activity, edge-to-edge, handles deep links (onCreate + onNewIntent)
                                exposes deep link to nav via a StateFlow/Channel

data/CatalogModels.kt           @Serializable Catalog, CatalogApp, CatalogRelease
data/CatalogRepository.kt       fetch (fresh URL → fallback URL), cache last good catalog JSON in filesDir,
                                StateFlow<CatalogState> (catalog, lastUpdated, isRefreshing, error)
data/InstalledAppsRepository.kt query PackageManager: installed versionCode/versionName, signer SHA-256 set,
                                installer-of-record package, launch intent; signer of an APK file
data/SettingsRepository.kt      DataStore: checkIntervalHours (0=off, 1,3,6,12,24; default 6),
                                autoUpdate (default false), wifiOnly (default true),
                                notifiedVersions (Set<String> "pkg:code" already notified)
data/AppStatus.kt               enum/sealed status + pure function computeStatus(...)
                                NotInstalled, UpToDate, UpdateAvailable, InstalledNewer, SignerMismatch,
                                Incompatible (device SDK < minSdk)
                                + StoreApp(ui model: catalog app + latest release + installed info + status)

install/InstallManager.kt       per-package StateFlow<Map<String, InstallState>>; install(app, release, background)
                                → download → verify → PackageInstaller session; cancel(pkg); uninstall(pkg)
install/InstallState.kt         Idle, Queued, Downloading(progress 0..1, bytes, total), Verifying, Installing,
                                PendingUserAction, Success, Failed(message)
install/ApkDownloader.kt        OkHttp streaming download to cacheDir/apks/<pkg>-<code>.apk.part → rename,
                                progress callback, resume not required, cancellable
install/ApkVerifier.kt          size check, sha256 check, archive packageName/versionCode check,
                                archive signer vs catalog signerSha256 and vs installed signer
install/PackageInstallerHelper.kt  session create/write/commit, PendingIntent to InstallResultReceiver
install/InstallResultReceiver.kt   BroadcastReceiver for session status

work/UpdateCheckWorker.kt       CoroutineWorker: refresh catalog, compute updates, notify new ones,
                                if autoUpdate (+wifi constraint) install updates in background
work/UpdateScheduler.kt         enqueue/replace unique periodic work from settings
notify/Notifications.kt         channels ("updates", "installs"), post update-available, install-result,
                                "tap to finish installing" notifications; PendingIntents deep-link
                                into MainActivity

ui/theme/Color.kt, Theme.kt, Type.kt   Panda brand theme
ui/navigation/PandaNavGraph.kt  routes: home, app/{packageName}, settings, debug; slide/fade transitions
ui/StoreViewModel.kt            combines catalog + installed + install states → List<StoreApp>; actions
ui/home/HomeScreen.kt
ui/detail/AppDetailScreen.kt
ui/settings/SettingsScreen.kt
ui/debug/DebugConsoleScreen.kt
ui/components/*.kt              AppIcon, StatusChip, PandaButton (bouncy), DownloadProgress, etc.
util/AppLog.kt                  in-memory ring buffer (1000 lines) + Logcat, StateFlow<List<String>>
util/Formatters.kt              bytes, relative time
```

Shared signatures other files rely on (keep these exact):

```kotlin
// data/AppStatus.kt
enum class AppStatus { NotInstalled, UpToDate, UpdateAvailable, InstalledNewer, SignerMismatch, Incompatible }
data class InstalledInfo(val versionCode: Long, val versionName: String?, val signerSha256: Set<String>,
                         val installerPackage: String?)
data class StoreApp(val app: CatalogApp, val latest: CatalogRelease, val installed: InstalledInfo?,
                    val status: AppStatus)
fun computeStatus(latest: CatalogRelease, installed: InstalledInfo?, deviceSdk: Int): AppStatus

// install/InstallManager.kt
class InstallManager(...) {
  val states: StateFlow<Map<String, InstallState>>
  fun install(app: CatalogApp, release: CatalogRelease, background: Boolean = false)
  fun cancel(packageName: String)
  fun uninstall(packageName: String)   // launches ACTION_DELETE with FLAG_ACTIVITY_NEW_TASK
  fun canSilentlyUpdate(packageName: String): Boolean
}

// data/InstalledAppsRepository.kt
class InstalledAppsRepository(context: Context) {
  val changes: StateFlow<Int>                     // bumps on PACKAGE_ADDED/REPLACED/REMOVED (dynamic receiver)
  fun get(packageName: String): InstalledInfo?
  fun launchIntent(packageName: String): Intent?
  fun archiveSigners(apk: File): Set<String>      // lowercase hex SHA-256 of signing certs
  fun refresh()                                   // bump changes
}
```

## Behaviour details that matter

1. **Status** (`computeStatus`): not installed → `Incompatible` if
   `deviceSdk < minSdk` else `NotInstalled`. Installed: if catalog
   signerSha256 is non-null and not in installed signers → `SignerMismatch`;
   else compare versionCode: lower → `UpdateAvailable`, equal → `UpToDate`,
   higher → `InstalledNewer`. Device SDK below minSdk on an update →
   `Incompatible`.
2. **Installed signers**: API 28+ `GET_SIGNING_CERTIFICATES`
   (`signingInfo.apkContentsSigners`, or `signingCertificateHistory` when
   not multiple signers — include the whole history so a rotated key still
   matches); API 26–27 `GET_SIGNATURES`. SHA-256 of `Signature.toByteArray()`.
   Use `PackageManager.PackageInfoFlags` on API 33+.
3. **Download**: OkHttp follows GitHub's redirect to
   objects.githubusercontent.com. Write to `.part`, then rename. Report
   progress at most ~10×/s. Delete partial file on failure/cancel. Delete
   finished APKs after install success and on app start for anything older
   than a day.
4. **Verify before installing** — refuse with a clear message on: size
   mismatch, sha256 mismatch, archive packageName ≠ catalog, archive
   versionCode ≠ catalog, archive signer ≠ catalog signerSha256, archive
   signer not in installed signers (when installed): "This build is signed
   with a different key than the installed app. Uninstall <name> first, then
   install." (offer an Uninstall button on the detail screen for
   SignerMismatch).
5. **Install** via `PackageInstaller`:
   `SessionParams(MODE_FULL_INSTALL)`, `setAppPackageName`, `setSize`,
   API 31+ `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`, API 34+
   `setRequestUpdateOwnership(true)`, API 26+ `setInstallReason(INSTALL_REASON_USER)`.
   Copy the APK into the session on `Dispatchers.IO`. Commit with a
   `PendingIntent.getBroadcast` to `InstallResultReceiver` (explicit intent,
   unique requestCode per session, `FLAG_UPDATE_CURRENT or FLAG_MUTABLE`
   on API 31+ — the system must fill in extras). Receiver:
   - `STATUS_PENDING_USER_ACTION` → if the app is in the foreground
     (ProcessLifecycleOwner STARTED) start `EXTRA_INTENT` with
     `FLAG_ACTIVITY_NEW_TASK`; otherwise post a "Tap to finish installing
     <name>" notification whose PendingIntent launches that intent.
     State → `PendingUserAction`. If the user cancels the system dialog the
     session reports `STATUS_FAILURE_ABORTED` → back to Idle (not an error).
   - `STATUS_SUCCESS` → `Success`, delete APK, refresh installed repo, post
     "<name> updated to vX" notification if installed in background.
   - Other failures → `Failed(human message)` mapped from status +
     `EXTRA_STATUS_MESSAGE` (CONFLICT → likely signature/downgrade,
     INCOMPATIBLE, STORAGE → not enough space, INVALID, BLOCKED).
   - Self-update (`packageName == context.packageName`): process will be
     killed on success; that's fine.
6. **Unknown sources**: before installing, if
   `!packageManager.canRequestPackageInstalls()` show a dialog explaining
   and open `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with
   `package:` URI; re-check on resume.
7. **Silent update eligibility** (`canSilentlyUpdate`): API 31+, app installed,
   installer of record (`getInstallSourceInfo(pkg).installingPackageName` on
   API 30+) == our package, and the target app's targetSdk is recent enough
   (API 31: target ≥ 29; API 33: ≥ 30; API 34: ≥ 31; API 35+: ≥ 33). Purely
   informational for UI ("Updates install automatically") and for
   deciding whether background auto-update can complete without a prompt.
8. **Background worker**: unique periodic work `update-check`,
   `ExistingPeriodicWorkPolicy.UPDATE`, interval from settings (0 → cancel),
   network constraint CONNECTED (UNMETERED when wifiOnly && autoUpdate).
   Notifies only versions not already in `notifiedVersions`. One summary
   notification "N updates available" (or the single app name) that opens
   home (or the app). If autoUpdate is on, start installs with
   `background = true` and await them (bounded) so the worker doesn't die
   mid-download — or use `setForeground` is NOT allowed (no FGS); instead
   do the download inside the worker coroutine and only hand off the
   session commit.
9. **Package change receiver**: dynamically register for
   PACKAGE_ADDED/REPLACED/REMOVED/FULLY_REMOVED (data scheme `package`) in
   `InstalledAppsRepository`; bump `changes` so the UI recomputes. Also
   refresh on activity resume.
10. **Deep links**: intent-filter on MainActivity: `VIEW`, `DEFAULT`,
    `BROWSABLE`, scheme `pandastore` (hosts `app` and `home`).
    `launchMode="singleTop"`; handle in `onNewIntent`.

## UI

Brand (from `../PandaApps_Brand_Kit/PANDA_DESIGN_GUIDELINES.md` and
`../UI_DESIGN.md` — read both): PandaGreen `#4CAF50` primary, dark by default
(surface `#121212`, surface high `#1E1E1E`), success `#4CAF50`, error
`#F44336`, downloading `#2196F3`; Material 3; follow system light/dark but
design dark first; smooth micro-animations; bouncy primary button.
Launcher icon from `../PandaApps_Brand_Kit/panda_logo.png` (1024²): adaptive
icon with the logo as foreground (scaled into the 66% safe zone) on white or
PandaGreen-tinted background, plus legacy mipmaps (use `sips` to resize).
App label "Panda App Store".

- **Home**: top bar with title + refresh + settings; "Updates available (N)"
  section with **Update all** button at top when N>0; then all apps. Each
  row: icon (Coil from iconUrl; if installed and no iconUrl, use
  PackageManager icon), name, "v1.0.6 · build 7 · 177 MB", status chip, and
  a primary action button (Install / Update / Open / progress ring while
  working). Search field and filter chips (All, Updates, Installed, Not
  installed). Pull-to-refresh. Last-checked time. Empty/error/offline states
  (show cached catalog with an "offline" banner).
- **App detail**: big icon, name, package, installed vs latest version +
  build, size, release date, status explanation, primary action
  (Install/Update/Open/Retry/Cancel), secondary Uninstall, progress with
  bytes, "Updates install automatically" badge when silent-eligible,
  release notes for every release in the catalog (previous one labelled
  "Previous" — Android cannot downgrade, so explain that rolling back means
  uninstalling first; offer "Uninstall & install this version" for the
  previous release only via a confirmation dialog: uninstall, then when
  removed install that release).
- **Settings**: check-for-updates interval, auto-update toggle, Wi-Fi only
  toggle, permission status rows with fix buttons (install unknown apps,
  notifications), "How to get instant notifications" card (install ntfy,
  subscribe to the topic from the Mac's `~/.config/panda-store/config.json`),
  clear downloads cache (shows size), app version/build, Debug console entry.
- **Debug console** (brand-mandated): live AppLog lines, auto-scroll, copy
  all, share, clear. Reachable from Settings and by long-pressing the home
  title.

All user-facing strings in `res/values/strings.xml` is NOT required (inline
strings are fine for this personal app).

## Tests

Unit tests (JVM, `app/src/test`): `computeStatus` truth table, catalog JSON
parsing (the sample above + missing optional fields + unknown fields),
formatters, install status message mapping (pure function), interval→work
request mapping if pure.

## Done means

- `./gradlew testDebugUnitTest assembleDebug --no-daemon -q` passes.
- No warnings about missing permissions in lint for the install path
  (`./gradlew lintDebug` should not report errors).
