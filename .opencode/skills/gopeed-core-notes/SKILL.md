---
name: gopeed-core-notes
description: Load BEFORE touching core/ Go code, scripts/build-core*, EngineClient models, or docs/ENGINE_API.md and docs/CORE_PATCHES.md. Verified Gopeed upstream facts, API cheat-sheet and patch rules for ZentraDL.
license: GPL-3.0-or-later
compatibility: opencode
---

# Gopeed Core Notes — ZentraDL (verified 2026-09-20, upstream v1.9.3)

Full depth: `docs/GOPEED_ANALYSIS.md` + `docs/GOPEED_PART_{HTTP,BT,API}.md`.
Contract: `docs/ENGINE_API.md`. Patches log: `docs/CORE_PATCHES.md`.

## 1. Pin

- Upstream `github.com/GopeedLab/gopeed`, pin **`v1.9.3`** (go 1.24.9).
  `main` drifted (go 1.25.4) — never float. `core/` = submodule, mergeable.
- BT = `anacrolix/torrent v1.60.1-0.2025…` (+dht/go-libutp/upnp/roaring).

## 2. Directory map

- `pkg/download/` lifecycle + bbolt; `pkg/base/` models + status enum
  (`ready/running/pause/wait/error/done`) + `PieceMap` bitset-v1 + `PeerStats`.
- `pkg/protocol/http/` + `internal/protocol/http/` fetcher (slow-start,
  chunk-halving, work-steal, fixed retry, redirect-fallback). No HLS/DASH.
- `internal/protocol/bt/` thin anacrolix wrapper. `pkg/api/service.go` 26 routes.
- `bind/mobile/main.go` (`libgopeed`): `Start/Stop/InvokeAsync/SubscribeTaskEvents`.
- `pkg/download/engine/` JS/goja extensions. `ui/flutter/lib/api/` Dart client to mirror.

## 3. REST cheat-sheet (envelope `{code,msg,data}`; 0 ok / 1000 err / 1001 auth / 1002 param / 2001 notfound)

- `POST /api/v1/resolve` probe → `{id,res}`. `POST /api/v1/tasks` (+`/batch`) create.
- `PATCH /api/v1/tasks/{id}` (HTTP: swap URL; BT: only `selectFiles`).
- `PUT …/pause|continue` (single + batch). `DELETE …?force=` (force = delete files).
- `GET /api/v1/tasks[?id&status&notStatus]`, `GET /tasks/{id}[/status|/stats]`.
- `GET|PUT /api/v1/config`. 8× `/extensions…` (git-URL install, switch, settings, update).
- Auth `X-Api-Token`/Bearer. NO WebSocket — push = `SubscribeTaskEvents` mask;
  poll `…/status` ≤1 Hz foreground.
- Speed = 5 s rolling mean, tick 350 ms. Boot forces non-done/error → pause (lazy restore).
- PieceMap: `{encoding:"bitset-v1",pieceCount,pieceSize,completedPieces,data:b64}`,
  verified-complete only, nil while verifying. 100k pieces ≈ 16.7 KB b64 @1 Hz OK.

## 4. Task state machine

`∅→ready→(running|wait)→…→done|error`; pause/resume via doPause/Continue;
delete removes bbolt rows; Patch mutates in place. Fetcher-internal:
`idle→resolving→resolved→slowStart→steady→done` (+paused/error sides).

## 5. Config schema (keys that exist)

`downloadDir, maxRunning=5, protocolConfig{http:{userAgent,connections=16},
bt:{listenPort,trackers,seedKeep,seedRatio=1.0,seedTime=7200},…}, proxy,
webhook, script, autoTorrent, archive, api`. Absent: speed limits, retry modes,
per-host caps, checksums, mirrors, per-file priority, sequential, tracker mgmt.

## 6. Gomobile build

```bash
gomobile bind -tags nosqlite -ldflags="-w -s -checklinkname=0" \
  -o <out>/libgopeed.aar -target=android -androidapi 21 -javapkg="com.gopeed" \
  github.com/GopeedLab/gopeed/bind/mobile
```

NDK r28+ (16 KB alignment). See `scripts/build-core.sh`.

## 7. Patch rules (blocking)

- New files/packages only; never rewrite upstream files in place.
- Every patch: entry in `docs/CORE_PATCHES.md` (why + files + tests) + Go tests.
- `go vet` + `go test ./...` clean. Default `/stats` payload never grows
  (new detail behind query flags). RLE/down-sample piece data; ~1 Hz safe.
- Known limits: singleton BT client (network-toggle changes need restart note);
  single `Resource.Hash` (v1 only — v2 surfacing TBD); 403 = fatal per-conn;
  backoff linear ≤5 s (until retry patch lands).

## 8. Planned patches (Phase 1/4/5)

HTTP stats ranges+speed · resume validators + checksum verify · retry modes +
per-host caps · piece-detail RLE endpoint · tracker CRUD/re-announce · per-file
priority · sequential mode · per-task limits + seed-goal override · HLS/DASH
fetcher · mirrors. GAPS.md for anything infeasible (never silent).
