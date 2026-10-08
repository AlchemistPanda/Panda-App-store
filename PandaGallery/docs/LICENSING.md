# Face recognition model licensing

Everything Panda Gallery depends on is **free of charge**. There are no API keys, quotas,
subscriptions or per-device fees anywhere in the smart search or people grouping pipeline —
it all runs on the phone.

There is, however, a licensing question around the face recognition **model weights**, and
the answer depends entirely on whether you ship commercially.

## What each component costs and permits

| Component | Cost | Commercial use |
|---|---|---|
| ML Kit text recognition / image labeling / face detection | Free | Yes — on-device APIs, no billing, no key |
| TensorFlow Lite runtime | Free (Apache-2.0) | Yes |
| Room, FTS4, AndroidX | Free (Apache-2.0) | Yes |
| **MobileFaceNet weights** (`app/src/main/assets/mobilefacenet.tflite`) | Free | **Unclear — see below** |

Only the last row is a problem, and only for commercial distribution.

## Why the weights are the awkward part

Face recognition models are trained on large scraped face datasets, and essentially all of
the public ones are research-licensed. That restriction flows through to the weights trained
on them. Surveyed October 2025:

| Source | Code licence | Weights licence | Commercial? |
|---|---|---|---|
| [shubham0204/FaceRecognition_With_FaceNet_Android](https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android) (what `download_face_model.sh` defaults to) | Apache-2.0 | **Undocumented** | Unclear — assume no |
| [InsightFace](https://github.com/deepinsight/insightface) (ArcFace, buffalo_*) | MIT | "for non-commercial research purposes only" | **No** |
| [EdgeFace](https://huggingface.co/Idiap/EdgeFace-S-GAMMA) (Idiap) | BSD-3-Clause | CC BY-NC-SA 4.0 | **No** |

The default source's Apache-2.0 licence covers that repository's **code**. It does not
document where the `.tflite` weights came from, and published MobileFaceNet weights derive
from refined MS-Celeb-1M — a dataset Microsoft retired in 2019 that was released for
research. There is no explicit commercial grant attached.

This gap is structural, not an oversight on anyone's part. It is a large part of why Google
Photos and Apple Photos use in-house models for exactly this feature.

## What to do

**Personal use, side project, internal tool, or not distributing the APK**
Run `scripts/download_face_model.sh` and carry on. Nothing to pay, nothing to worry about.

**Publishing commercially (Play Store, paid app, business use)**
Do not ship the default weights. Options, roughly by effort:

1. **Ship without people grouping.** The feature already degrades cleanly — with no model
   present the toggle stays off and explains why, and smart search is entirely unaffected
   because it uses ML Kit, which is unrestricted. This costs nothing and is the default
   state of the repository.
2. **License a commercial face SDK.** Paravision, Neurotechnology, Luxand and similar sell
   per-app or per-device licences with clear commercial terms. This is the one path that
   genuinely does cost money.
3. **Train your own MobileFaceNet** on a dataset you have rights to. The architecture is
   published and unencumbered; only the weights are the issue. Highest effort, but the
   result is unambiguously yours.

Options 2 and 3 both produce a 112×112×3 float32 model with a 128- or 192-dimension
embedding, which is what `FaceEmbedder` already reads shapes for — so either drops in
without code changes. Re-tune `SAME_PERSON_THRESHOLD` in `FaceClustering.kt` afterwards;
similarity thresholds are not transferable between embedding models.

## Note

This is an engineering summary of publicly stated licence terms, not legal advice. If you
are shipping commercially, have counsel confirm before release.
