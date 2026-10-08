# Enhancement Roadmap

Tracks the feature/improvement backlog identified from a competitive review (Google Photos,
Samsung Gallery, Ente, Immich, Simple/Fossify Gallery) against Panda Gallery's current feature
set, dated 2026-08-19.

Status legend: ✅ Done · 🚧 In progress · ⬜ Not started

## Improvements to what already exists

| Status | Item | Notes |
|---|---|---|
| ✅ | **Compression**: lossless/near-lossless quality tier | `CompressionPreset.NEAR_LOSSLESS` (WebP q92) and `LOSSLESS` (true lossless WebP). 2026-10: Lossless no longer downscales large photos (task fails, original kept), skips JPEG/HEIC/HEIF sources and videos (kept unchanged — no lossless path helps them), and lossless jobs stay out of estimate calibration |
| ✅ | **Compression**: resumable batch jobs | Tasks are Room rows; a killed/rebooted batch resumes per file (only the in-flight file restarts — Media3 can't resume an export). 2026-10: each task snapshots its encode settings at enqueue, no duplicate copies when a batch is stopped; a failed completion write removes the copy it just published, and only process death between publish and completion can still duplicate (see ponytail in CompressionQueueProcessor), stale encode files are swept at batch start, and a boot-time resume that Android 15 won't let run as a foreground service posts "Tap to resume" instead of running unprotected |
| ⬜ | **Panda Sweep**: near-duplicate detection across original ↔ already-compressed/backed-up copies | Avoid flagging a compressed copy of a photo as an unrelated "similar" match, or missing the link entirely |
| ⬜ | **Editor**: curves / white-balance / HSL adjustments | Current editor has crop, rotate, exposure, filters, markup |
| ⬜ | **Large-library performance**: verify/improve scroll + background indexing at 100k+ photos | Confirm paging strategy and embedding batch size scale |
| ⬜ | **Foldable/tablet layouts**: multi-pane grid+viewer, adaptive column count | Currently single fixed-width layout |
| ⬜ | **Localization breadth**: expand beyond English | Simple/Fossify Gallery ships 32 languages |
| ⬜ | **Predictive back gesture** support | Android 16 target requirement (mandatory by 2026-08-31) |

## New functionality — AI-powered editing

| Status | Item | Notes |
|---|---|---|
| ⬜ | On-device object removal / generative fill (Magic Eraser equivalent) | ONNX segmentation + inpainting, no cloud |
| ⬜ | One-tap "Enhance" auto-adjustment | |
| ⬜ | Portrait light / background blur adjustment | |

## New functionality — Backup & sync

| Status | Item | Notes |
|---|---|---|
| ⬜ | End-to-end encrypted backup | Current SAF backup copies files unencrypted |
| ⬜ | Immich/WebDAV-compatible sync client | For self-hosted-server users |

## New functionality — Discovery & organization

| Status | Item | Notes |
|---|---|---|
| ⬜ | Natural-language / semantic search (on-device embeddings) | Beyond current OCR + object-label FTS index |
| ⬜ | Places/map clustering tab | Group photos by GPS cluster, not just per-photo GPS in viewer |
| ⬜ | Auto-generated highlight-reel videos from Memories | Reuse existing video editor pipeline |
| ⬜ | Automatic People-group merge suggestions | Currently manual merge only |

## New functionality — Platform integrations

| Status | Item | Notes |
|---|---|---|
| ⬜ | Chromecast/DLNA casting (viewer + slideshow) | |
| ⬜ | Android 16 embedded Photo Picker support | Pinch-resize grid, drag multi-select |
| ⬜ | Quick Share / Nearby Share integration | |
| ⬜ | Material You dynamic color on home-screen widget | |

## Implementation order

1. **Improvements to what already exists** (this batch) — in progress
2. AI-powered editing
3. Backup & sync
4. Discovery & organization
5. Platform integrations
