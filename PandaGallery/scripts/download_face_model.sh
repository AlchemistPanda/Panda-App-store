#!/usr/bin/env bash
#
# Fetches the MobileFaceNet TFLite model that powers people grouping.
#
# The model is NOT committed to this repository. It is a third-party binary with its own
# licence, and vendoring it would silently make that licence part of Panda Gallery's
# distribution. Run this once locally (and in CI, before assembling a release) to place it
# at app/src/main/assets/mobilefacenet.tflite.
#
# Without the model, the app still builds and runs — people grouping reports that the
# recognition model is unavailable and stays switched off, rather than falling back to a
# weaker signal that would quietly mix different people together.
#
# Usage:
#   scripts/download_face_model.sh                 # download from the default source
#   scripts/download_face_model.sh /path/to.tflite # install a model you already have
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSET_DIR="$REPO_ROOT/app/src/main/assets"
TARGET="$ASSET_DIR/mobilefacenet.tflite"

# Android sample that redistributes MobileFaceNet in TFLite form (112x112 input).
#
# IMPORTANT: the hosting repository's licence covers its CODE. The repository does not
# document where the model weights themselves came from. Published MobileFaceNet weights
# were trained on refined MS-Celeb-1M, a dataset Microsoft retired in 2019 and which was
# released for research. There is no explicit commercial-use grant on these weights.
# See LICENSING.md before shipping this in a commercial build.
#
# Override with FACE_MODEL_URL if your organisation mirrors its own vetted copy.
DEFAULT_URL="https://github.com/estebanuri/face_recognition/raw/master/android/app/src/main/assets/mobile_face_net.tflite"
MODEL_URL="${FACE_MODEL_URL:-$DEFAULT_URL}"

mkdir -p "$ASSET_DIR"

if [[ $# -ge 1 ]]; then
  echo "Installing model from $1"
  cp "$1" "$TARGET"
else
  echo "Downloading MobileFaceNet from:"
  echo "  $MODEL_URL"
  echo
  curl -fL --progress-bar "$MODEL_URL" -o "$TARGET"
fi

# A TFLite flatbuffer carries the identifier "TFL3" at byte offset 4. Checking it catches
# the common failure where a redirect or an error page lands in the file instead.
if [[ "$(dd if="$TARGET" bs=1 skip=4 count=4 2>/dev/null)" != "TFL3" ]]; then
  echo "ERROR: $TARGET is not a TFLite model (missing TFL3 identifier)." >&2
  echo "       The download probably returned an HTML error page. File left in place for inspection." >&2
  exit 1
fi

SIZE_BYTES=$(wc -c < "$TARGET" | tr -d ' ')
if command -v shasum >/dev/null 2>&1; then
  CHECKSUM=$(shasum -a 256 "$TARGET" | cut -d' ' -f1)
else
  CHECKSUM=$(sha256sum "$TARGET" | cut -d' ' -f1)
fi

cat <<EOF

Installed: $TARGET
Size:      $SIZE_BYTES bytes
SHA-256:   $CHECKSUM

Record that checksum and pin it in CI so the model cannot change underneath you.

*** LICENCE WARNING ***
These weights are fine for personal and non-commercial use. They are NOT cleared for
commercial distribution -- their provenance is undocumented and the published
MobileFaceNet weights derive from a research-only dataset. If you intend to publish
Panda Gallery commercially, read LICENSING.md first.

The app reads the model's input and output shapes at load time, so 112x112x3 float32
variants with either a 128- or 192-dimension embedding both work. If you swap models,
re-tune SAME_PERSON_THRESHOLD in FaceClustering.kt — thresholds are not transferable
between embedding models.
EOF
