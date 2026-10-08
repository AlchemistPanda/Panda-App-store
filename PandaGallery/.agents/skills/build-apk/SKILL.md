---
name: build-apk
description: Build the latest release and debug APKs for PandaGallery and automatically copy them to the project root folder and builds directory. Also installs directly to any connected Android device.
---

# Build APK

This skill compiles the latest release (signed & minified) and debug APKs using Gradle, copies the resulting files directly into the project root directory and `builds/` directory, and installs to a connected physical device if available.

## Command

Run:
```bash
./scripts/build_apk.sh
```

Or manually:
```bash
./gradlew assembleRelease assembleDebug
cp app/build/outputs/apk/release/app-release.apk PandaGallery-1.0.0.apk
cp app/build/outputs/apk/release/app-release.apk app-release.apk
cp app/build/outputs/apk/debug/app-debug.apk app-debug.apk

mkdir -p builds
cp app/build/outputs/apk/release/app-release.apk builds/PandaGallery-1.0.0.apk
cp app/build/outputs/apk/release/app-release.apk builds/app-release.apk
cp app/build/outputs/apk/debug/app-debug.apk builds/app-debug.apk
```
