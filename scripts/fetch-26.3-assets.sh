#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-${ROOT}/work/eag26.3-assets.epk}"
COMMIT='bc586558628e2d7ef8ea1cebf198495625619eb2'
URL="https://raw.githubusercontent.com/Enchantment-Niko/mcjs/${COMMIT}/wasm-loader/26.3/eag26.3-assets.epk"
mkdir -p "$(dirname "$OUT")"
curl -fL "$URL" -o "$OUT"
test -s "$OUT"
echo "26.3 assets: $OUT"
sha256sum "$OUT"
