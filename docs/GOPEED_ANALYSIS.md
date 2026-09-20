# GOPEED_ANALYSIS.md — Gopeed upstream analysis (ZentraDL)

Pinned upstream: **`v1.9.3`** (2026-03-18, commit `a5cd53f`; `go.mod` = **go 1.24.9**).
`main` has drifted (go 1.25.4, new webview/MCP deps) — do NOT float on main.
Depth lives in three verified part files (all read 2026-09-20 against the real
repo, nothing invented); this file is the synthesis + decisions.

- `docs/GOPEED_PART_HTTP.md` — HTTP engine + task lifecycle (§5.1 gaps)
- `docs/GOPEED_PART_BT.md` — BitTorrent engine + piece-API feasibility (§5.4/5.5)
- `docs/GOPEED_PART_API.md` — REST/bind/extensions/Flutter client (§4.2 bridge)
- Versions/toolchain: `docs/DEPENDENCIES.md` · Contract: `docs/ENGINE_API.md`

## 1. Repo map (v1.9.3, verified)

| Path | Role |
|---|---|
| `pkg/download/` | `Downloader` lifecycle, `MaxRunning` queue, bbolt persist, extension/webhook/script/auto-extract hooks |
| `pkg/base/` | `Request/Resource/Options`, proxy/store config, 6-status enum (`ready/running/pause/wait/error/done`), `PieceMap` bitset-v1, `PeerStats` |
| `pkg/protocol/http/` + `internal/protocol/http/` | HTTP fetcher: resolve, slow-start 1/2/4/8, chunk-halving, work-stealing, retry, redirect-fallback |
| `internal/protocol/bt/` | BT glue: thin wrapper over `anacrolix/torrent v1.60.1-0.2025…` (+dht, go-libutp, upnp, roaring) |
| `internal/protocol/` | Only `bt/ ed2k/ http/` — **no HLS/DASH fetcher** |
| `pkg/api/service.go` + `pkg/rest/` | 26 REST routes, `Result{code,msg,data}` envelope, token auth |
| `bind/mobile/main.go` (`libgopeed`) | gomobile entry: `Start(cfg JSON)`, `Stop()`, `InvokeAsync`, `SubscribeTaskEvents` |
| `pkg/download/engine/` | JS extension engine (goja + fetch/xhr/file/stream/ffmpeg polyfills, manifest identity) |
| `ui/flutter/lib/api/` | Dart API client + models (mirror as Kotlin `EngineClient`); `l10n/` ~23 locales (GPL, reusable) |

## 2. Mobile start/stop + transport (2.0-beta native mode)

No socket on mobile: Flutter `MethodChannel('gopeed.com/libgopeed')` →
in-process `Dispatch()` into shared `pkg/api` service. `Start()` inits Bolt
storage + `Downloader.Setup()` + `ContinueOnStartup()`; `Stop()` drains ≤3s.
Optional TCP server (`127.0.0.1:9999`, token `X-Api-Token`/Bearer) OFF by default.
**No WebSocket exists** — push = `SubscribeTaskEvents(mask)` bitmask
(done/error/start/progress/pause/delete, 350 ms ticks); else poll
`GET /tasks/{id}/status` (light) / `GET /tasks`.

**ZentraDL decision:** prefer in-process `InvokeAsync`/`Dispatch` (no port, no
token in RAM, §8 loopback trivially satisfied); keep TCP+token only for external
consumers (browser-extension bridge stretch). No WebSocket to implement.

## 3. Task + config models (see ENGINE_API.md for full contract)

Task: `{id, name, protocol: http|bt|ed2k|hls, meta:{req,res,opts}, status,
progress:{used, speed (5s mean), downloaded, uploadSpeed, uploaded}, createdAt,
updatedAt}`. Runtime poll adds per-file bytes (`selectFiles` order).
Config: `{downloadDir, maxRunning=5, protocolConfig:{http:{userAgent,connections=16},
bt:{listenPort,trackers,seedKeep,seedRatio=1.0,seedTime=7200s},…}, proxy,
webhook, script, autoTorrent, archive, api:{…127.0.0.1:9999,token}}`.

## 4. What exists vs gaps (condensed)

HTTP: per-task connections (set 8 from app, no patch), slow-start + work-steal
re-split, single-conn fallback, bbolt resume, redirect-fallback on
401/403/404/410, `Resolve()` pre-check, `Patch()` URL refresh, proxy
system/HTTP/SOCKS5, truncate prealloc, JS extensions — EXIST. Missing: ETag/size
resume validation, retry modes (fixed infinite-linear/3×), per-host caps,
speed limits, checksums, mirrors, HTML-file warning, free-space preflight,
HLS/DASH fetcher (new), segment ranges+speed in Stats (small patch).
BT: magnet/file/base64 add, metadata resolve, file skip/select + Patch re-select,
add-only trackers, basic peers, global seed ratio/time, verified-complete PieceMap
bitset (nil while verifying), inherited v2/DHT/PEX/uTP/UPnP — EXIST. Missing:
per-file priority, sequential, tracker list/remove/re-announce, peer ban/detail,
network toggles, per-torrent limits/goals, IP filter, recheck/move/rename/export/
create/RSS, availability/in-flight/verifying/file→piece signals (all feasible,
one call below the wrapper; see §5).

## 5. Planned core patches (detail: docs/CORE_PATCHES.md)

1. HTTP `StatsConnection` += ranges + per-conn speed (wire-compatible).
2. Resume validators (ETag/Last-Modified/size) + checksum verify on completion.
3. Retry modes (none/N/unlimited) + exp-backoff/jitter; per-host caps + memory.
4. Piece-detail endpoint/flags: RLE runs (state/avail/in-flight/verifying),
   file→piece ranges, down-sample cap, behind query flags (default payload unchanged).
5. Tracker CRUD + re-announce + per-tracker stats; peer ban hook.
6. Per-file priority post-create (`File.SetPriority` wiring + PATCH ext).
7. Sequential / first-last-piece streaming mode (Reader/readahead preset).
8. Per-task speed limits; per-torrent seeding-goal override; listen-port + toggles.
9. HLS (m3u8 AES-128 non-DRM) + DASH fetcher (Gopeed-style `FetcherManager`).
   Merge strategy TBD (ffmpeg-kit retired — research license-compatible remux).
10. Mirrors/fallback URLs + checksum validation (with #2).

All in NEW files under `core/`, Go tests per patch, upstream-mergeable.

## 6. Build

```bash
go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init
gomobile bind -tags nosqlite -ldflags="-w -s -checklinkname=0" \
  -o <out>/libgopeed.aar -target=android -androidapi 21 -javapkg="com.gopeed" \
  github.com/GopeedLab/gopeed/bind/mobile
```

NDK r28+ required (16 KB page alignment, Play-enforced for 15+ targets).
Reproduce in `scripts/build-core.sh` / `.ps1`. CI builds the AAR (no local
toolchain on termux host — see SETUP.md).
