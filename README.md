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

## Public and private apps

There are two stores behind the one app:

- **Public** (`AlchemistPanda/Panda-App-store`, this repo): what everyone sees.
- **Private** (`AlchemistPanda/Panda-App-store-private`, a private repo): owner-only
  builds (betas, personal apps, apps with Firebase config). Same layout: APKs
  in its Releases, `catalog.json` and icons on its `store` branch.

`panda-publish` keeps each app in the store it was last published to, so
shipping needs no extra flags. A **new app goes to the private store** unless
you pass `--public`. To move one: `--public` / `--private` on the next publish,
or without a new build `panda-publish --move <package> --to public|private`
(moving out of the public store deletes its public releases).

On the phone, private apps appear only in owner mode: in Settings, tap the
version row 7 times, enter the passcode, and (once per phone) paste a
fine-grained GitHub token with read-only **Contents** access to the private
repo. The token is the real lock; the passcode only hides the door. Owner
mode can be turned off again under Settings → Owner.
