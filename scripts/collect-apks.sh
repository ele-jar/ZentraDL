#!/usr/bin/env bash
# scripts/collect-apks.sh — copy Gradle APKs to dist/ as <APP>-<ver>-<abi>-<type>.apk + SHA256SUMS.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="ZentraDL"; VER="${VERSION_NAME:-0.1.0}"
mkdir -p "$ROOT/dist"
shopt -s nullglob
for f in "$ROOT"/android/app/build/outputs/apk/*/*.apk; do
  type="$(basename "$(dirname "$f")")"; abi="$(basename "$f" .apk | rev | cut -d- -f1 | rev)"
  cp "$f" "$ROOT/dist/${APP}-${VER}-${abi}-${type}.apk"
done
(cd "$ROOT/dist" && sha256sum ./*.apk > SHA256SUMS)
ls "$ROOT/dist"
