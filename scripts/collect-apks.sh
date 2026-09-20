#!/usr/bin/env bash
# scripts/collect-apks.sh — copy Gradle APKs to dist/ as <APP>-<ver>-<abi>-<type>.apk + SHA256SUMS.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="ZentraDL"; VER="${VERSION_NAME:-0.1.0}"
mkdir -p "$ROOT/dist"
shopt -s nullglob
# Split APKs look like app-arm64-v8a-debug.apk (abi may itself contain dashes).
for f in "$ROOT"/android/app/build/outputs/apk/*/*.apk; do
  base="$(basename "$f" .apk)"   # app-<abi>-<type>
  rest="${base#app-}"            # <abi>-<type>
  type="${rest##*-}"             # <type>
  abi="${rest%-"$type"}"         # <abi> (universal for the universal APK)
  cp "$f" "$ROOT/dist/${APP}-${VER}-${abi}-${type}.apk"
done
(cd "$ROOT/dist" && sha256sum ./*.apk > SHA256SUMS)
ls "$ROOT/dist"
