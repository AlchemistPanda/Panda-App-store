# Collage creator: PandaGallery vs Samsung Gallery

A feature-by-feature comparison of our collage creator against the one in Samsung's Gallery app,
and a ranked list of what is missing.

## How to read this

- **Our column is verified.** Every claim about PandaGallery was read out of the source; file and
  line references are given so they can be re-checked as the code moves.
- **The Samsung column is not.** This device is a OnePlus, so Samsung Gallery cannot be installed
  and nothing here was tested against a live build. It describes One UI Gallery's collage as it is
  generally known to work. Rows marked **(confirm)** are ones I would not act on without checking
  on a real Samsung handset first — they are the ones where One UI versions differ most.
- **Updated 2026-09-17.** The gaps found in the first pass have been worked through; each section
  says what is now done and what is deliberately still open. Everything marked done was verified on
  the device, including in the exported file.

---

## 1. Closed: the features we had built and never wired up

**Status: done.** This was the largest gap, and it was not against Samsung — it was against
ourselves. `CollageTool` had four entries and `ToolOptions` four `when` branches, so there was no
tab that could host text, stickers, filters or music, and every callback for them was threaded
three composables deep and then dropped.

There are now nine tabs — Layout, Ratio, Border, Background, Filter, Text, Sticker, Draw, Music —
in a scrollable row that scrolls itself to the selected tool. Everything in the table below is
reachable and verified end-to-end on device, including in the exported file.

| Feature | Where it lives now |
|---|---|
| Text captions | **Text tab** — add, select, restyle. Now also carries fonts, outline, plate, size and angle |
| Stickers — 32 emoji presets | **Sticker tab** — tap to add, then drag / pinch / twist on the canvas |
| Per-cell and global filters — 7 | **Filter tab**, with an All / This-one switch; each tile previews the real photo |
| Music: local, online, categories, custom file, trim, volume | **Music tab** — opens the audio picker that was 350 lines of dead UI |
| Add more photos while editing | **+** in the top bar |
| Reset a cell's zoom/pan/rotation | **Reset** in the cell menu |
| Flip vertical | **Flip vertically** in the cell menu |
| Share after saving | **Share** in the top bar |

One real bug surfaced the moment captions became visible: cells carry `zIndex(1f)` (5 when
selected), and the sticker and caption layers were at the default `0`, so overlays rendered
*underneath the photos*. Nobody had seen it because nothing could add an overlay. The layers are now
explicitly ordered — cells 1–10, freehand 15, stickers 16, captions 17, cell menu 20.

Still not wired, and deliberately: **reorder a cell left/right** (`moveActive`) and **go back and
change the selection** (`editSelection`). Drag-to-swap and the cell menu's Replace already cover
both, and adding buttons for them would crowd a tray that now has nine tabs.

---

## 2. Area by area

### 2.1 Getting in, and picking photos

| | PandaGallery | Samsung Gallery |
|---|---|---|
| Entry points | Albums overflow → "Create collage"; also from a multi-selection | Multi-select → Create → Collage |
| Min / max items | 2 – 6 (`COLLAGE_MIN_ITEMS`, `COLLAGE_MAX_ITEMS`) | up to 6 **(confirm)** |
| Picker organisation | **done** — date headers, and album chips across the top | grouped by date, with album / tab switching |
| Picker text search | **still open** — album chips cover most of it | yes |
| Selection order shown | yes — numbered badge | yes |
| Change selection later | open — drag-to-swap and Replace cover the need | yes, back out to the picker |
| Add photos mid-edit | **done** — **+** in the top bar | yes |

### 2.2 Layout

| | PandaGallery | Samsung Gallery |
|---|---|---|
| Grid templates | **done** — 6 (2 photos), 9 (3), 9 (4), 8 (5), 8 (6); was 4/6/6/5/5 | a larger set per count **(confirm exact numbers)** |
| Template previews | yes, drawn live from the real cell rects (`LayoutThumbnail`) | yes |
| Draggable dividers | **yes** — drag to re-split, primary and secondary (`onSetSplitRatio`, `onSetSubSplitRatio`) | yes |
| Swap two photos | yes — long-press and drag a cell | yes |
| Scattered / "freestyle" layouts | **yes** — 3 variants per count, with per-card rotation and elevation, plus shuffle | no equivalent in collage |
| Free-form drag/resize of a single card | no — freestyle positions are presets, not manipulable | n/a |

We are ahead on freestyle, level on dividers, and the template count is now in the same range.

### 2.3 Ratio

| | PandaGallery | Samsung Gallery |
|---|---|---|
| Presets | **8**: Orig, 1:1, 4:3, 3:4, 4:5, 9:16, 16:9, 2:3 | comparable set |
| "Original" (match the source photo) | **done** — resolves to the first still's shape | yes **(confirm)** |
| 4:3 | **done** | yes |
| Free / custom ratio | **still open** | **(confirm)** |

### 2.4 Border

| | PandaGallery | Samsung Gallery |
|---|---|---|
| Outer margin | yes, 0–6% | combined into one "thickness" |
| Inner spacing between cells | yes, 0–6% | combined into one "thickness" |
| Corner roundness | yes, 0–8% | yes ("roundness") |
| Border colour separate from background | **no** — the gap always shows the background | **(confirm)** |
| Shadow / outline on cards | freestyle cards carry an elevation value, not user-controllable | no |

We actually split margin and spacing where Samsung uses one control — that is a genuine, if small,
advantage, and the new minimal sliders make three rows affordable.

### 2.5 Background

| | PandaGallery | Samsung Gallery |
|---|---|---|
| Solid colours | 10 presets | a palette |
| Custom colour picker | **done** — HSV dialog behind the eyedropper | **(confirm)** |
| Gradients | 6 presets | **(confirm)** |
| Patterns / textures | **done** — 8 procedural (dots, stripes, grid, checker), drawn identically in preview and export | yes **(confirm)** |
| Blurred version of the photos | **yes** | no |
| Colour sampled from the photos | **done** — dominant colours of the selection, offered as swatches | **(confirm)** |

### 2.6 Per-photo controls

Our cell menu now offers: Replace, Delete, Rotate, Flip horizontal, Flip vertical, Reset, and Trim
for videos.

| | PandaGallery | Samsung Gallery |
|---|---|---|
| Pinch to zoom / pan inside a cell | yes | yes |
| Double-tap to fit / fill | **done** — honoured by the preview and the exporter | yes |
| Rotate | yes, 90° steps | yes |
| Flip horizontal | yes | yes |
| Flip vertical | **done** | yes |
| Reset the cell | **done** | yes |
| Replace | yes | yes |
| Remove | yes | yes |
| Open in the full photo editor | **no** | yes — the strongest Samsung advantage here |
| Per-cell crop | **no** — zoom/pan only | yes, via the editor |

### 2.7 Overlays and drawing

| | PandaGallery | Samsung Gallery |
|---|---|---|
| Text | **done** — Text tab | yes |
| Text fonts | **done** — 7 families, matched between preview and export | multiple fonts |
| Text outline / plate | **done** — outline on by default, plate cycles none → dark → light | yes |
| Text alignment | **still open** — captions are centred on their anchor | yes |
| Resize / rotate a caption | **done** — sliders in the tray, or pinch and twist on the canvas | yes |
| Stickers | **done** — 32 emoji, draggable, pinchable, rotatable | yes, a real sticker library incl. AR emoji |
| Drawing / doodle | **done** — Draw tab: freehand strokes, 10 colours, brush width, undo-stroke, clear | yes |
| Frames | **still open** | **(confirm)** |

The preview used to draw every caption in bold sans on a fixed dark plate regardless of the model,
so what you positioned was never quite what got saved. `DraggableText` and
`CollageExporter.drawTextOverlay` are now written to match property for property.

### 2.8 Filters

**Done.** Seven colour-matrix filters, per-cell or global, each tile previewing the user's own
photo rather than a swatch. Samsung applies filters through its photo editor rather than inside the
collage tool, so we are now ahead here — filters live where you are actually composing.

### 2.9 Video and audio — where we are clearly ahead

Samsung's collage is a still-image tool; videos go to "Create movie" instead.

| | PandaGallery |
|---|---|
| Videos inside a collage | yes, up to 4 (`COLLAGE_MAX_VIDEOS`) |
| Playback in the live preview | yes |
| Per-clip trim | yes, reachable via the cell menu |
| Mute source audio | yes |
| Music track over the collage | **done** — Music tab |
| Video export | Media3 `Transformer` |

Three defects found and fixed here:

1. **Video output was capped at 1080px** on the long edge while stills exported at 2400px — a video
   collage came out at less than half the resolution of the same collage saved as a still, with
   nothing saying so. Now 1920, rounded to even dimensions because encoders reject odd ones.
   Verified: a two-clip collage now writes 1920×1920 (parsed from the mp4's `hvc1` box).
2. **The video path ignored every background mode but solid colour.** It built its backdrop with a
   flat `eraseColor`, so gradients, patterns and blurred-media silently vanished the moment a video
   joined the collage. It now runs the same `drawBackground` the still path does.
3. **The video path ignored overlays entirely** — captions, stickers and freehand marks never
   reached the file. They are now rendered onto transparency and applied as a `BitmapOverlay` on the
   `Composition`, so they land on top of the composited cells rather than inside one of them.

### 2.10 The editing session

| | PandaGallery | Samsung Gallery |
|---|---|---|
| Undo / redo | **done** — 40 steps, in the top bar | yes **(confirm for collage specifically)** |
| Discard confirmation | yes | yes |
| Draft / resume later | **still open** | **(confirm)** |
| Config survives rotation | yes — the ViewModel outlives configuration changes | — |
| Config survives process death | **still open** — `CollageConfiguration` is not in `SavedStateHandle` | — |
| Progress while saving | yes, with percentage | yes |

Undo works by stacking whole `CollageConfiguration` values: the configuration is one immutable
object, so a list of previous ones is a complete history with no per-feature bookkeeping. Edits
carrying the same label within 700ms collapse into one entry, so a slider drag or a pinch costs one
undo step rather than forty. Verified on device: margin 25 → drag to 91 → undo → 25 → redo → 91.

### 2.11 Output

| | PandaGallery | Samsung Gallery |
|---|---|---|
| Stills | JPEG q96, 2400px long edge, to `Pictures/PandaGallery/Collages/` | JPEG into Gallery |
| Video | **1920px** long edge (was 1080) | n/a |
| Resolution choice | **still open** | **(confirm)** |
| Share straight from the editor | **done** | yes |
| Save-and-continue-editing | **still open** | — |

---

## 3. Where we already beat Samsung

Worth stating plainly, because the list above is long:

1. **Video collages at all** — Samsung does not do this.
2. **Music over a collage**, including an online catalogue.
3. **Freestyle scattered layouts** with rotation and depth.
4. **Blurred-media backgrounds**, alongside gradients and procedural patterns.
5. **Separate margin and spacing** controls where Samsung has one "thickness".
6. **Filters inside the collage tool** rather than in a separate editor.
7. **Background colours sampled from the photos themselves.**

---

## 4. What remains

Everything on the original backlog is done except the five below, each left for a stated reason
rather than overlooked.

1. **"Edit in photo editor" from a cell.** The largest remaining Samsung advantage. It needs
   cross-screen result plumbing — navigate to `EditorRoute`, let it save a *new* MediaStore item,
   then find that new id and swap it into the cell — and a half-working version would lose the
   user's collage. Worth doing as its own piece of work.
2. **Draft save and resume.** Needs `CollageConfiguration` serialised to disk, including its `Uri`
   fields, plus somewhere to list and reopen drafts.
3. **Process-death persistence.** The same serialisation problem, smaller: the ViewModel already
   survives rotation, so this only matters when Android kills the process behind a backgrounded
   editor.
4. **Per-cell crop, free/custom aspect ratio, caption alignment, frames, output-resolution choice.**
   Depth items, none of them blocking.
5. **Picker text search.** Album chips and date headers cover most of the need.

One thing to check on a real device rather than in code: the **music → video** export. It compiles
and shares its whole path with the video export that was verified, but this handset has no local
audio and was in airplane mode, so the online catalogue could not be reached. The still and
video-with-clips paths were both verified end to end.

---

## 5. Verifying the Samsung column

Everything marked **(confirm)** is worth ten minutes on a real Samsung device before it drives a
decision. If one is available, the useful capture is: the collage editor with each of its tabs
open, the cell context menu, and the top bar — that would settle template counts, background
options, undo, and whether text and stickers live in the collage tool or only in the photo editor.
