#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"${ROOT}/scripts/setup-base.sh"
cd "${ROOT}/work/26.2-base/patcher/java-cli"
./build.sh
echo "Built: $PWD/build/eaglercraft-26.2-java-cli.jar"
