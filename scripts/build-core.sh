#!/usr/bin/env bash
# scripts/build-core.sh — build Gopeed AAR via gomobile (verified command, upstream README+CI).
# Usage: bash scripts/build-core.sh [--out <dir>]  (default out: <root>/android/app/libs)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/env.sh"
OUT="$ROOT/android/app/libs"
while [ $# -gt 0 ]; do case "$1" in --out) OUT="$2"; shift 2;; *) echo "unknown arg $1"; exit 1;; esac; done
mkdir -p "$OUT"
if ! command -v gomobile >/dev/null 2>&1; then
  go install golang.org/x/mobile/cmd/gomobile@latest
  go get golang.org/x/mobile/bind 2>/dev/null || true
  gomobile init
fi
# NDK r28+ required for 16 KB page-size alignment (Play-enforced for Android 15+ targets).
gomobile bind -tags nosqlite -ldflags="-w -s -checklinkname=0" \
  -o "$OUT/libgopeed.aar" -target=android -androidapi 21 -javapkg="com.gopeed" \
  github.com/GopeedLab/gopeed/bind/mobile
echo "AAR -> $OUT/libgopeed.aar"
