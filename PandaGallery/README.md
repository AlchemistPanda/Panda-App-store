# Panda Gallery 🐼

A privacy-first Android gallery built with Kotlin and Jetpack Compose. Albums remain the primary tab; chronological grouping is available inside media grids rather than as a separate primary timeline tab.

## Features

### 📸 Gallery and editing
- **Album-first navigation** — Auto-detected device folders and custom albums
- **Chronological grouping** — Date headers inside media grids
- **Albums** — Auto-detected device folders + custom albums, with a pickable cover
  (album long-press → Cover, or a photo's viewer menu → Use as album cover)
- **Full-screen Viewer** — Pinch-to-zoom, swipe navigation, metadata, GPS removal, maps, slideshow, and external-screen handoff
- **Video Player** — Built-in player with Media3/ExoPlayer and an autoplay on/off toggle
- **Image editor** — Crop, rotate, exposure, filters, markup, undo, and save-copy
- **Video editor** — Trim, rotate, mute, progress, cancellation, and save-copy
- **Format support** — GIF, RAW, SVG, TIFF, motion photos, panoramas, and 360° metadata classification
- **Multi-select** — Long-press batch operations (delete, share, favorite, move)
- **Live library** — MediaStore changes refresh the grid automatically, plus pull-to-refresh
- **Fast scroll** — Draggable date scrubber for long timelines
- **Favorites** — Heart toggle with dedicated album
- **Trash Bin** — 30-day auto-delete with restore
- **Advanced search** — Name, date, album, media type, size, favorites, OCR text, and on-device object
  labels, backed by an FTS4 index with stemming, synonyms and relevance ranking ("receipts" finds
  "receipt", "puppy" finds photos labelled "Dog")

### ✨ Smart organization
- **Panda Sweep** — Exact duplicates, similar photos, **burst sequences (keeps the sharpest frame)**,
  blurry candidates, screenshots, documents, and large files, with one-tap "keep the best copy"
  selection and optional weekly auto-resolve for byte-identical duplicates
- **Memories** — On This Day and monthly recaps
- **Photo stacks** — Groups shots captured close together
- **People groups** — Optional on-device face recognition (MobileFaceNet embeddings + landmark
  alignment, clustered by cosine similarity) with persistent names, manual merging, and a
  "not a person" dismissal for posters and passers-by
- **Privacy controls** — OCR, labeling, and people grouping are opt-in and the local index can be cleared
- **Automatic smart index** — Media changes enqueue an incremental refresh; unchanged photos are skipped and a charging-periodic job provides fallback maintenance

### 🗜️ Smart Compression (Memories Vault)
- **Image compression** — Convert to WebP/AVIF with configurable quality
- **Video compression** — H.265/HEVC with GPU-accelerated MediaCodec
- **Quality presets** — Low (85% savings) / Medium (70%) / High (50%), with predicted
  before/after sizes that self-calibrate against your completed compressions
- **Space savings dashboard** — Track total storage saved

### 🔒 Privacy
- **Private Album** — Hidden, locked with biometric or PIN/pattern; re-locks when the app leaves the foreground and is excluded from screenshots and cloud backups
- **Hide Albums** — Exclude specific albums from the main view, with a temporary
  "Show hidden albums" reveal that resets when you leave the screen
- **Lock Albums** — Require device authentication to open an album (in-app gate; files stay in place)
- **Album export** — Bundle an album's originals into a single shareable zip

### ☁️ Folder backup
- **Storage Access Framework** — Back up to device storage or a compatible document provider such as Google Drive
- **Incremental backup and restore** — Preserve albums, skip unchanged media, and restore recorded items
- **Scheduling controls** — Selected albums, videos, Wi-Fi-only, and charging-only options

### 📱 System integrations
- **System photo viewer** — Handles `ACTION_VIEW` and `ACTION_EDIT` from other apps
- **Home-screen widget** — Open Memories, Search, or Panda Sweep
- **Launcher shortcuts** — Search, Private folder, and Panda Sweep
- **Photo screensaver** — Rotating recent photos through Android's screensaver settings

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Database | Room (metadata cache) |
| Image Loading | Coil 3 |
| Video | Media3/ExoPlayer |
| Async | Coroutines + Flow |
| Navigation | Compose Navigation (type-safe) |

## Preferences that persist
Sort order, content filter, grid density, album sort and album grid density are remembered
between sessions, alongside trash retention (1–90 days) and slideshow speed.

## Requirements
- Android 12+ (API 31)
- Android Studio Hedgehog or newer

## Setup

1. Clone this repository
2. Open in Android Studio
3. Download fonts: `bash scripts/download_fonts.sh`
4. Download the face recognition model: `bash scripts/download_face_model.sh`
   (optional — without it the app builds and runs, but People grouping stays disabled
   and says so in Settings rather than falling back to a weaker signal.
   **Read [docs/LICENSING.md](docs/LICENSING.md) before shipping commercially** — the
   model weights are free but not cleared for commercial distribution)
5. Sync Gradle and run on device/emulator

## License
MIT
