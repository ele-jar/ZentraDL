#!/usr/bin/env bash
# scripts/build-core.sh — build Gopeed AAR via gomobile.
# Upstream command (README+CI) uses @latest; we PIN x/mobile (see MOBILE_VERSION):
# @latest (Sep 2026) needs go>=1.26 and our toolchain is go1.24.9 per core/upstream/go.mod.
# Usage: bash scripts/build-core.sh [--out <dir>]  (default out: <root>/android/app/libs)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/env.sh"
# x/mobile @ 2026-02-09 (go directive 1.24.0; last line before the 1.25.0 bump).
# Verified via proxy: v0.0.0-20260209203831-923679eb55af.
MOBILE_VERSION="${MOBILE_VERSION:-v0.0.0-20260209203831-923679eb55af}"
OUT="$ROOT/android/app/libs"
while [ $# -gt 0 ]; do case "$1" in --out) OUT="$2"; shift 2;; *) echo "unknown arg $1"; exit 1;; esac; done
mkdir -p "$OUT"
# Install (pinned) gomobile binary.
go install "golang.org/x/mobile/cmd/gomobile@$MOBILE_VERSION"
# Current gomobile requires x/mobile in the target module's graph (go.dev/issue/77183).
# This adds a LOCAL-ONLY tool dep to core/upstream/go.mod+go.sum (ephemeral in CI;
# never commit it — the submodule pin stays pristine).
(
  cd "$ROOT/core/upstream"
  # Single pinned command: a bare `go get -tool <pkg>` (no @version) re-resolves
  # to latest and drags the whole module to go1.26 — do NOT split this line.
  go get -tool "golang.org/x/mobile/cmd/gobind@$MOBILE_VERSION"
)
gomobile init
echo "gomobile on PATH: $(command -v gomobile)"
(
  cd "$ROOT/core/upstream"
mkdir -p "$OUT"
# The bind runs from core/upstream: a relative --out would land inside the
# submodule. Force absolute (D009).
case "$OUT" in /*) ;; *) OUT="$ROOT/$OUT";; esac
mkdir -p "$OUT"
  set -x
  gomobile bind -tags nosqlite -ldflags="-w -s -checklinkname=0" \
    -o "$OUT/libgopeed.aar" -target=android -androidapi 21 -javapkg="com.gopeed" \
    github.com/GopeedLab/gopeed/bind/mobile
  set +x
)
echo "bind exit: $?"
find "$ROOT" -maxdepth 6 -name "*.aar" 2>/dev/null
test -f "$OUT/libgopeed.aar" || { echo "FATAL: AAR not produced"; ls -la "$OUT"; exit 1; }
echo "AAR -> $OUT/libgopeed.aar"
