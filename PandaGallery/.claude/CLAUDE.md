# Panda Gallery Instructions

## Mandatory Build & Release Protocol

Whenever generating a new build of the application:

### 1. Increment Version / Build Number in `app/build.gradle.kts`
Before building:
- **`versionCode`**: Always increment by 1 (e.g., `1` → `2`).
- **`versionName`**: Update to reflect the build / release version (e.g., `1.0.0` → `1.0.1` or `1.0.0-b2`).

```kotlin
// In app/build.gradle.kts (under android.defaultConfig)
defaultConfig {
    ...
    versionCode = <NEW_VERSION_CODE>
    versionName = "<NEW_VERSION_NAME>"
}
```

### 2. Assemble the APK
```bash
# Release APK:
./gradlew assembleRelease
```

### 3. Rename and Copy Release APK to Project Root Folder
Always copy the release APK to the root workspace directory matching the version name in the filename:

```bash
cp app/build/outputs/apk/release/app-release.apk ./PandaGallery-<versionName>.apk
```
*(e.g., `cp app/build/outputs/apk/release/app-release.apk ./PandaGallery-1.0.1.apk`)*

### 4. Instruction Files Location
Keep agent instruction documentation organized inside `.claude/CLAUDE.md` and `docs/` rather than adding extra documentation files in the root folder.
