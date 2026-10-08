#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "🔨 Building release and debug APKs..."
./gradlew assembleRelease assembleDebug

echo "📦 Copying APKs to project root and builds/ folder..."
mkdir -p builds
cp app/build/outputs/apk/release/app-release.apk PandaGallery-1.0.0.apk
cp app/build/outputs/apk/release/app-release.apk PandaGallery-release.apk
cp app/build/outputs/apk/release/app-release.apk app-release.apk
cp app/build/outputs/apk/debug/app-debug.apk PandaGallery-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk app-debug.apk

cp app/build/outputs/apk/release/app-release.apk builds/PandaGallery-1.0.0.apk
cp app/build/outputs/apk/release/app-release.apk builds/PandaGallery-release.apk
cp app/build/outputs/apk/release/app-release.apk builds/app-release.apk
cp app/build/outputs/apk/debug/app-debug.apk builds/PandaGallery-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk builds/app-debug.apk

echo "✅ APKs successfully copied to project root:"
ls -lh PandaGallery-*.apk app-*.apk


# Check for connected adb device
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [ -x "$ADB" ]; then
    DEVICE=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')
    if [ -n "$DEVICE" ]; then
        echo "📲 Installing release APK directly to connected device: $DEVICE..."
        "$ADB" install -r PandaGallery-1.0.0.apk || true
    fi
fi
