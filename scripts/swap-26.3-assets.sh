#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "usage: $0 <built-26.2-project> <26.3-assets.epk> [output.html]" >&2
  exit 2
fi
PROJECT="$(cd "$1" && pwd)"
ASSETS="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
OUTPUT="${3:-${PROJECT}/eaglercraft-26.3-asset-swap.html}"
WEB="${PROJECT}/target_teavm_wasm_gc/build/web"
ORIGINAL="${WEB}/assets.epk"
[[ -s "$ORIGINAL" ]] || { echo "Missing built 26.2 asset archive: $ORIGINAL" >&2; exit 1; }
[[ -s "$ASSETS" ]] || { echo "Missing 26.3 asset archive: $ASSETS" >&2; exit 1; }
BACKUP="${WEB}/assets.26.2-original.epk"
if [[ ! -e "$BACKUP" ]]; then cp "$ORIGINAL" "$BACKUP"; fi
cp "$ASSETS" "$ORIGINAL"
echo "26.2 original: $(sha256sum "$BACKUP" | cut -d' ' -f1)"
echo "26.3 swapped:  $(sha256sum "$ORIGINAL" | cut -d' ' -f1)"
cd "$PROJECT"
node wasm-toolchain/build-single-html.js --skip-build --output "$OUTPUT"
echo "Hybrid packaged at: $OUTPUT"
