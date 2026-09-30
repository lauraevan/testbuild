#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="${ROOT}/work/26.2-base"
RID='rad:z2BWVCwcwTyoQ2veMJLb1eFpMtJDj'
HEAD='016a49a92ab4f43db18b892ab7929b62c0e96dba'
RADICLE_URL="https://radicle.jarg.io/${RID}.git"
MIRROR_URL='https://github.com/lauraevan/scode.git'
mkdir -p "${ROOT}/work"
if [[ ! -d "${DEST}/.git" ]]; then
  rm -rf "${DEST}"
  if ! git clone "${RADICLE_URL}" "${DEST}"; then
    echo "Radicle clone failed; trying the exact GitHub mirror: ${MIRROR_URL}" >&2
    git clone "${MIRROR_URL}" "${DEST}"
  fi
fi
git -C "${DEST}" fetch --all --tags --prune || true
git -C "${DEST}" checkout --detach "${HEAD}"
ACTUAL="$(git -C "${DEST}" rev-parse HEAD)"
[[ "${ACTUAL}" == "${HEAD}" ]] || { echo "Unexpected 26.2 base commit: ${ACTUAL}" >&2; exit 1; }
echo "26.2 base ready at ${DEST} (${ACTUAL})"
