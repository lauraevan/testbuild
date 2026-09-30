#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${MC_26_2_JAR:?Set MC_26_2_JAR to the official 26.2 client JAR}"
: "${VINEFLOWER_JAR:?Set VINEFLOWER_JAR to Vineflower 1.12.0}"
: "${JAVA17:?Set JAVA17 to a Java 17 executable}"
: "${PATCH_BUNDLE:?Set PATCH_BUNDLE to the authorized source-patch bundle}"
OUT="${OUT:-${ROOT}/work/26.2-project}"
PATCH_SHA='df3af583c06aa22748d21f039980cdd3923dbc7ab0bc21accbbb28b1cd1e7389'
"${ROOT}/scripts/build-patcher.sh"
CLI="${ROOT}/work/26.2-base/patcher/java-cli/build/eaglercraft-26.2-java-cli.jar"
args=(create-dev --jar "$MC_26_2_JAR" --output "$OUT" --vineflower "$VINEFLOWER_JAR" --java17 "$JAVA17" --patch-bundle "$PATCH_BUNDLE" --expected-bundle-sha256 "$PATCH_SHA")
if [[ -n "${PROJECT_SKELETON:-}" ]]; then
  args+=(--project-skeleton "$PROJECT_SKELETON" --expected-skeleton-sha256 'e76f606630ce6596061e7ac5a76d01a541846cac7d8d1424ec38a942ab00c071')
fi
if [[ -n "${RESOURCE_OVERLAY:-}" || -n "${EXTERNAL_RESOURCE_ROOT:-}" ]]; then
  : "${RESOURCE_OVERLAY:?Set both RESOURCE_OVERLAY and EXTERNAL_RESOURCE_ROOT}"
  : "${EXTERNAL_RESOURCE_ROOT:?Set both RESOURCE_OVERLAY and EXTERNAL_RESOURCE_ROOT}"
  args+=(--resource-overlay "$RESOURCE_OVERLAY" --expected-resource-overlay-sha256 '2ba7e3376891c64f8bf57f3687e05b8dbe1971a75475b6825449e5e5f96d71f3' --external-resource-root "$EXTERNAL_RESOURCE_ROOT")
fi
"$JAVA17" -jar "$CLI" "${args[@]}"
echo "Reconstructed 26.2 source project: $OUT"
