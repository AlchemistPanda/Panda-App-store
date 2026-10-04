# Panda App Store

A personal app store for my Android apps. Builds are published from the IDE
with `tools/panda-publish` and installed/updated on the phone by the Panda App
Store app.

- **APKs** live in this repo's GitHub Releases (newest two per app).
- **Catalog** (`catalog.json`) and app icons live on the `store` branch.
- **Publish:** `tools/panda-publish path/to/App-v1.0.7-b8-2026-10-04.apk --notes "what changed"`
  — reads everything from the APK, refuses if the build number did not go up
  or the signing key changed, uploads, updates the catalog, prunes old
  releases and sends an ntfy push. Config: `~/.config/panda-store/config.json`.
