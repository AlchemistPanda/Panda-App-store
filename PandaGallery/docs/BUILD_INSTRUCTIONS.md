# Build & Release Protocol

This document specifies the standard build and release procedure for Panda Gallery.

---

## Mandatory Build Procedure

### 1. Update Version in `app/build.gradle.kts`
Before building:
1. Open [`app/build.gradle.kts`](../app/build.gradle.kts).
2. Increment `versionCode` by 1.
3. Update `versionName` to the new version (e.g. `1.0.1`).

### 2. Assemble Release APK
```bash
./gradlew assembleRelease
```

### 3. Copy Release APK to Project Root Folder
Always copy the compiled release APK to the project root directory using the version name:

```bash
cp app/build/outputs/apk/release/app-release.apk ./PandaGallery-<versionName>.apk
```
*(e.g., `cp app/build/outputs/apk/release/app-release.apk ./PandaGallery-1.0.1.apk`)*
