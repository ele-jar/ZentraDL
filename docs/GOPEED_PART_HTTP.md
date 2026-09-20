# Gopeed HTTP Engine + Task Lifecycle — Upstream Analysis for ZentraDL

> Scope: Gopeed's HTTP download engine and task lifecycle only (BRIEF §4.2 / §5.1).
> BT engine, REST/WebSocket surface, mobile bind and extensions are covered in sibling docs.
> **Pinned upstream: `v1.9.3`.** Every path, name and version below was verified against the
> real repo (`github.com/GopeedLab/gopeed`) via GitHub API listings + raw file fetches on
> 2026-09-20. Nothing is inferred from memory.
> Blob permalink pattern: `https://github.com/GopeedLab/gopeed/blob/v1.9.3/<path>`
> Line numbers are exact for files ≤ ~5 KB (retrieved in full); for the two large files
> (`internal/protocol/http/fetcher.go` ~44 KB, `pkg/download/downloader.go` ~48 KB)
> references are function-anchored (grep-able in one step) instead of line numbers.

---

## 0. Pin: release tag + Go version

| Item | Value | Verified via |
|---|---|---|
| Latest release tag | **`v1.9.3`** (shows `Latest` badge; released 2026-03-18 by `github-actions`, commit `a5cd53f`) | `github.com/GopeedLab/gopeed/releases/tag/v1.9.3` page fetch |
| `go.mod` **at `v1.9.3`** | **`go 1.24.9`** + `toolchain go1.24.11` | raw `go.mod` @ tag `v1.9.3` (full text retrieved) |
| `go.mod` on `main` (drift!) | `go 1.25.4` (plus new deps: `go-ffmpreg`, `webview_go`, `mcp/go-sdk`, `wazero`, …) | raw `go.mod` @ `main` |
| pkg.go.dev module data | latest `v1.9.3`, Go `1.24.9` | websearch result |
| Commits since release | ~71 commits to `main` after `v1.9.3` (per release page) | release page fetch |

**Decision for ZentraDL:** pin `core/` to tag **`v1.9.3`** (needs **Go ≥ 1.24.9** toolchain).
`main` has already moved (Go 1.25.4, new webview/MCP deps) — do not float on `main`.

Notable `go.mod` deps at `v1.9.3` (HTTP/BT/storage relevant):
`github.com/anacrolix/torrent v1.60.1-0.2025…` (confirms BT wraps anacrolix/torrent),
`github.com/imroc/req/v3 v3.52.2`, `github.com/gorilla/mux`, `github.com/gorilla/websocket`,
`github.com/mattn/go-ieproxy` (system proxy), `go.etcd.io/bbolt v1.4.3` (task persistence),
`github.com/armon/go-socks5`, `github.com/rs/zerolog`.

---

## 1. Real directory map (HTTP-relevant, at `v1.9.3`)

Sizes are from the GitHub API (bytes) to help judge file weight.

### 1.1 Downloader package — `pkg/download/`

| Path | Size | Role |
|---|---|---|
| `pkg/download/downloader.go` | 48276 | `Downloader`: task lifecycle, `MaxRunning` queue (`waitTasks`), bbolt persistence, progress ticker, webhooks/scripts/auto-torrent/auto-extract hooks |
| `pkg/download/model.go` | 4173 | `Task`, `NewTask()`, `TaskFilter`, `DownloaderConfig` (+ defaults) |
| `pkg/download/event.go` | 313 | `EventKey` constants + `Event` struct |
| `pkg/download/storage.go` | 5049 | Pluggable `Storage` backend (buckets named in `downloader.go`) |
| `pkg/download/engine/` | dir | JS extension engine (`engine.go` + `inject/` + `polyfill/` + `util/`) |
| `pkg/download/extension.go`, `script.go`, `webhook.go` | — | Extension triggers, post-download scripts, webhooks |
| `pkg/download/extract*.go`, `extract_queue.go` | — | Archive auto-extract (zip/rar/7z), single-flight queue |

### 1.2 Shared models — `pkg/base/`

| Path | Role |
|---|---|
| `pkg/base/model.go` | `Request`, `RequestProxy`, `Resource`, `FileInfo`, `Options`, `DownloaderStoreConfig`, `DownloaderProxyConfig` |
| `pkg/base/constants.go` (857 B) | `Status` enum + HTTP code/header constants (notably **no ETag / If-Range constants**) |
| `pkg/base/info.go` | Build/app info |

### 1.3 HTTP protocol — public surface `pkg/protocol/http/`, impl `internal/protocol/http/`

| Path | Size | Role |
|---|---|---|
| `pkg/protocol/http/model.go` (full text retrieved, 36 lines) | 1441 | `ReqExtra` (`:3-7`), `OptsExtra` (`:9-24`), `Stats`/`StatsConnection` (`:27-36`) |
| `internal/protocol/http/config.go` (full text retrieved, 7 lines) | 170 | `config` struct (`:3-7`): `userAgent`, `connections`, `useServerCtime` |
| `internal/protocol/http/fetcher.go` | 44272 | `Fetcher`: resolve/start/pause/stats, slow-start, chunk split, work stealing, retry |
| `internal/protocol/http/fetcher_manager.go` | 2537 | `FetcherManager`: scheme filters, defaults, `Store`/`Restore` |
| `internal/protocol/http/helper.go` | 13302 | Request building, redirect fallback, filename parsing (RFC 5987/2047/GBK/HTML-entities) |
| `internal/protocol/http/timeout_reader.go` | 652 | 15 s read-timeout wrapper (`NewTimeoutReader`) |
| `internal/fetcher/fetcher.go` (full text retrieved) | 4798 | `Fetcher` / `FetcherManager` interfaces, `FetcherMeta`, `SchemeFilter`, `Progress` |
| `internal/controller/controller.go` (full text retrieved) | 2182 | `Controller` (`GetConfig`/`GetProxy`), `DefaultFileController.Touch()` (mkdir + `os.Truncate` prealloc) |

### 1.4 Adjacent (context for §4.2 bridge work, details in sibling docs)

| Path | Role |
|---|---|
| `internal/protocol/` | Contains **only** `bt/`, `ed2k/`, `http/` (verified listing) — **no HLS/DASH/m3u8/mpd fetcher exists** |
| `pkg/protocol/` | Contains only `bt/`, `ed2k/`, `http/` (verified listing) |
| `bind/mobile/main.go` (full text retrieved) | `package libgopeed`: `Start(cfg JSON) (int, error)` → `rest.Start`, `Stop()` — the gomobile entry point |
| `pkg/rest/` | `api.go`, `server.go`, `config.go`, `model/` (incl. `StartConfig`), `server_test.go` — REST layer |

---

## 2. Task state machine

### 2.1 Downloader-level statuses — `pkg/base/constants.go:6-11`

```go
DownloadStatusReady   Status = "ready"   // task created but not started
DownloadStatusRunning Status = "running"
DownloadStatusPause   Status = "pause"
DownloadStatusWait    Status = "wait"     // queued, waiting for a running slot
DownloadStatusError   Status = "error"
DownloadStatusDone    Status = "done"
```

Six states, no separate `checking`/`seeding`/`metadata` states at downloader level.

### 2.2 Downloader-level transitions — `pkg/download/downloader.go` (→ function anchors)

| Transition | Function | Notes |
|---|---|---|
| `∅ → ready` | `NewTask()` (in `pkg/download/model.go`) | nanoid ID, `CreatedAt/UpdatedAt`, `IsCreated=false` |
| `ready → running` **or** `ready → wait` | `doCreate()` | If `remainRunningCount() == 0` (`MaxRunning` slots full) → `wait`, appended to `waitTasks`; else `doStart()` immediately. `watch(task)` goroutine starts in both cases |
| `wait → running` | `notifyRunning()` | Dequeues head of `waitTasks` when a slot frees (on done/error/delete/pause) |
| `any → running` (guard) | `doStart()` | No-op if already `running`/`done` (under `statusLock`). `restoreTask()` → `Resolve()` if `Meta.Res == nil` → auto-rename dup check (`AutoRename`, `util.CheckDuplicateAndRename`) → `timer.Start()` → `fetcher.Start()` → `saveTask()` → emit `start` |
| `running → pause` | `doPause()` via `Pause(filter)` / `pauseAll()` | Sets `pause`, `timer.Pause()`, `fetcher.Pause()`, `saveTask()` (persists chunk state), emits `pause` |
| `pause/error/wait → running` (+ slot juggling) | `Continue(filter)` / `continueAll()` / `ContinueBatch()` | `Continue` may pause running tasks to `wait` to make room; `continueAll` only fills free slots |
| `running → done` | `watch()` | `fetcher.Wait()` returns nil → finalize size/speed → `updateStatus(done)` → persist → emit `done` + `finally` → `notifyRunning()` → extension/webhook/script/auto-torrent/auto-extract hooks |
| `running → error` | `watch()` → `doOnError()` | Persists `error`, emits `error` + `finally`, `notifyRunning()`. **No automatic retry at downloader level** — stays `error` until user `Continue`s |
| `any → ∅` | `Delete(filter, force)` / `deleteAll()` | Removes from `tasks`+`waitTasks`, deletes bbolt rows (`task`+`save` buckets), `fetcher.Close()`; `force=true` also deletes files. Emits `delete` |
| `pause/error/* → pause` (restart) | `Setup()` | On boot, every non-`done`/non-`error` task is forced to `pause`; fetcher state rehydrates lazily via `Restore()` on next start → **resume across restarts/crashes/reboots** |
| in-place mutation | `Patch(id, req, opts)` | HTTP: replaces URL (clears saved redirect), merges headers/body/method/labels/proxy → `saveTask()` → emits `progress`. Basis for "refresh expired link" |

Events — `pkg/download/event.go:5-13`: `start`, `pause`, `progress`, `error`, `delete`, `done`, `finally`.
Progress ticker: every `RefreshInterval` (default **350 ms**, `DownloaderConfig.Init()` in `pkg/download/model.go`);
speed = moving average over last ~5 s (`calcSpeed()` in `pkg/download/model.go`).
`Progress` struct (`downloader.go` → `type Progress struct`): `used`, `speed`, `downloaded`,
`uploadSpeed`, `uploaded`, `extractStatus/extractProgress`, multipart-archive fields.

### 2.3 Fetcher-internal states — `internal/protocol/http/fetcher.go` (→ type/function anchors)

```go
// → type fetcherState
stateIdle → stateResolving → stateResolved → stateSlowStart → stateSteady → stateDone
statePaused / stateError  (side states; Start() re-enters doStart() from both)
```

- `Resolve()`: plain GET (no `Range`), probes `Accept-Ranges`/`Content-Range`, reads
  `Content-Length`, stores `Last-Modified` → `FileInfo.Ctime`, parses filename
  (Content-Disposition → URL basename → hostname), keeps response body open, starts
  `asyncPrefetch()` to temp file (range-capable only), honours pending early-`Start()`.
- `Start()`: `resolved/paused → doStart()`; `resolving → startPending=true` (auto-starts when
  resolve finishes); `slowStart/steady → doStart()` (resume-from-pause path);
  `error → doStart()` (drains `doneCh`, resets retryable connections).
- `doStart()`: blocks on `resolvedCh`; opens/creates target via `ctl.Touch()` (**preallocates**
  with `os.Truncate(size)`); copies prefetched bytes; builds `slowStartController(maxConns)`;
  launches `downloadLoop()` goroutine.
- `downloadLoop()`: fresh → `startResolveDownload()` (non-range: single conn reusing resolve
  response — one-time-URL safe; range: `expandConnections()`); resume → `resumeConnections()` +
  `waitForCompletion()`; expands on `expansionCh` until `checkCompletion()`/`stateSteady`.
- `Pause()`/`Close()`: cancel ctx, stop prefetch, wait `downloadLoopDone` + `wg`, close file,
  `→ statePaused`. `Wait()` blocks on `doneCh`. `Stats()` / `Progress()` / `Meta()` as below.

Connection model (→ `type connection`, `connectionState`, `connectionRole`, `chunk`):

| Item | Values |
|---|---|
| conn states | `connNotStarted → connConnecting → connDownloading → connCompleted`, or `connFailed` |
| roles | `roleResolve` (probe+prefetch), `rolePrimary` (first conn), `roleWorker` |
| chunk | `{Begin, End, Downloaded}`, `remain() = End-Begin+1-Downloaded`; persisted per conn |

---

## 3. Multi-part / segmenting behaviour

### 3.1 Connections config

- Per-task `OptsExtra.Connections` (`pkg/protocol/http/model.go:9-10`); `≤0` → global http
  `config.Connections`; **global default `16`** (`FetcherManager.DefaultConfig()` in
  `internal/protocol/http/fetcher_manager.go`); hard floor `1`.
- Global concurrent-task cap `MaxRunning`, **default `5`** (`DownloaderStoreConfig.Init()`,
  `pkg/base/model.go`). No per-host connection cap anywhere.
- BRIEF default is 8/task: Gopeed default is 16 — ZentraDL should set per-task default 8
  via `OptsExtra` (no core change needed).

### 3.2 Slow-start + splitting (exists, adaptive-ish)

- `slowStartController`: batch sizes **1, 2, 4, 8…** (`commitBatch` doubles `nextBatchSize`);
  next batch launches when current batch fully responded (`onConnectSuccess`/`onConnectFailed`
  → `expansionCh`); stops at `maxConnections` → `stateSteady`.
- New workers split the largest remaining chunk in half (only if `remain > 2×512 KB`,
  `stealMinChunkSize = 512*1024`); first connection owns `[prefetchedBytes, size-1]`.
- **Work stealing** (`helpOtherConnection`): a finished connection steals half the remaining
  work of the slowest connection when it needs `> stealThresholdSeconds (3 s)` to finish
  (speed tracked per conn on a 500 ms window). This is Gopeed's equivalent of
  "re-split slow segments" — **exists**.
- Fast-fail adaptive connect timeout (`buildFastFailClient` in `helper.go`): tracks max
  successful conn time (`maxConnTime`); timeout = `max(3 s, 1.5× maxConnTime)`.
  Base timeouts: connect 15 s, read 15 s (`connectTimeout`/`readTimeout`, `TimeoutReader`).
- Single-connection fallback: non-range or unknown-size → one conn reusing the resolve
  response body (`runConnectionWithResolveResp`) — **exists** (also covers one-time URLs).
- `slowStartController.paused` field exists but **nothing ever sets it** — the "pause
  expansion on 429" path is a stub comment, not behaviour.

### 3.3 Resume (offsets yes, validators no)

- Persisted per task: `fetcherData{Connections []*connection, RedirectURL string}`
  (`fetcher_manager.go` → `Store`/`Restore`), saved to bbolt `save` bucket on every progress
  tick and on pause (`saveTask()`); restored on next `Start()` → byte-range resume.
- Saved redirect URL is reused; on 401/403/404/410 the fetcher retries once against the
  **original** URL and re-saves the fresh redirect (`tryFallbackToOriginalURL`,
  `isRedirectExpiredError`) — covers expiring signed links.
- Non-range resume restarts the file from byte 0 (`resetConnectionForRestart`).
- **No `ETag` handling at all** (no `ETag`/`If-Range`/`If-Match`/`If-Modified-Since` in
  `pkg/base/constants.go`, `fetcher.go`, or `helper.go` — full texts searched).
  `Last-Modified` is read only to stamp file mtime when `useServerCtime=true`; it is
  **never validated** on resume. No size-change validation, no checksum/integrity pass.

### 3.4 Retry policy (fixed, not configurable)

- `isFailureExemptHTTPCode` / `shouldCountHTTPFailure` (`helper.go`): **5xx, 429, 408, 440, 499
  are exempt** → retried **indefinitely** with linear backoff `1s×n` capped at 5 s.
- Counted failures (e.g. 404 and other 4xx, body read errors after counting): `retryTimes++`,
  connection dies at **`retryTimes >= 3`**; task fails (`onDownloadComplete`) if any chunk
  has remaining bytes and total < size.
- **403 = instant permanent connection failure** (interpreted as "server connection limit");
  ignored at completion only if the bytes still arrived via other connections.
- Network errors / timeouts (non-HTTP errors): retried indefinitely, same linear backoff.
- Backoff is **linear (1 s, 2 s, … cap 5 s), no jitter, no exponential**, and there is
  **no user-facing retry setting** (no none/N/unlimited modes) at any config level.

### 3.5 What `Stats()` exposes (basis for a Segments map — partial)

`pkg/protocol/http/model.go:27-36` — per connection only:

```go
StatsConnection{ Downloaded int64; Completed bool; Failed bool; RetryTimes int }
```

`Progress()` returns a **single total** (`fetcher.Progress.TotalDownloaded()`).
Chunk `Begin/End` ranges and per-conn speed exist internally but are **not exposed** —
a "Segments map" (range + progress + speed + retries per connection) needs a **small core
patch** extending `StatsConnection` (wire-compatible addition).

---

## 4. Config schema (HTTP-relevant fields)

### 4.1 Global store config — `base.DownloaderStoreConfig` (`pkg/base/model.go` → type + `Init`/`Merge`)

| Field (JSON) | Default | Notes |
|---|---|---|
| `downloadDir` | `""` (must be set by embedder; `initOptions` falls back to it) | Per-task `Options.path` overrides; `%year%/%month%/%day%/%date%` placeholders expanded |
| `maxRunning` | `5` | Max concurrent running tasks; overflow tasks wait (`waitTasks`) |
| `protocolConfig` | `{http: DefaultConfig(), …}` | Per-protocol opaque map; http entry → §4.2 |
| `proxy` | disabled | §4.4 |
| `webhook {enable, urls}` / `script {enable, paths}` | disabled | Post-event hooks |
| `autoTorrent {enable, deleteAfterDownload}` | disabled | Auto-create BT task for finished `.torrent` files |
| `archive {autoExtract, deleteAfterExtract}` | disabled | Auto-extract zip/rar/7z incl. multi-part |
| `autoDeleteMissingFileTasks` | false | Startup cleanup of tasks whose files vanished |
| `extra` | — | Opaque embedder map |

No scheduler, no named queues, no per-task priority in schema.

### 4.2 HTTP protocol config — `internal/protocol/http/config.go:3-7`

```go
config{ UserAgent string; Connections int; UseServerCtime bool }
```

Defaults (`FetcherManager.DefaultConfig()`): Chrome-116 UA string, `Connections: 16`.
`UseServerCtime=false` unless set (stamps mtime from `Last-Modified` via `setft`).

### 4.3 Per-request — `base.Request` + `http.ReqExtra` (`pkg/protocol/http/model.go:3-7`)

| Field | Notes |
|---|---|
| `url` | http/https (scheme filter `HTTP`/`HTTPS`, `FetcherManager.Filters()`); magnet/torrent/file URLs route to other managers |
| `extra.method / extra.header / extra.body` | Custom method, headers (**cookies, referer, auth all manual via headers**), POST body. `Host` header overrides TLS SNI host; global UA injected only if absent |
| `extra` absent | Plain GET |
| `proxy {mode: follow\|none\|custom, scheme, host, usr, pwd}` | Per-task proxy; `follow` (default) uses global |
| `skipVerifyCert` | Per-task TLS-verification bypass |
| `labels` | Opaque string map (app-level tagging), mergeable via `Patch()` |

### 4.4 Proxy + preallocation (exists)

- Global `DownloaderProxyConfig{enable, system, scheme, host, usr, pwd}` (`pkg/base/model.go`):
  `system=true` → OS proxy via `go-ieproxy`; else `scheme://[usr:pwd@]host` (HTTP/SOCKS5-style).
  Resolution order in `setupFetcher` (`downloader.go`): task `none`/`custom` wins, else global.
- `DefaultFileController.Touch(name, size)` (`internal/controller/controller.go`): `MkdirAll` +
  `os.Create` + **`os.Truncate(size)` preallocation**. No free-space preflight check anywhere.

### 4.5 Explicitly absent from schema (verified by full-text retrieval)

**Speed limits** (global or per-task), **retry count/mode**, **per-host caps**, **checksum fields**,
**mirror/fallback URLs**, **per-host credential vault** — none exist in
`DownloaderStoreConfig`, `DownloaderConfig`, http `config`, `ReqExtra`, or `OptsExtra`
(`Resource.Hash` exists but is BT-oriented; nothing consumes it on the HTTP path).

---

## 5. BRIEF §5.1 gap analysis (HTTP engine)

| §5.1 requirement | Upstream status | Evidence / note |
|---|---|---|
| 1–32 conns/task, default 8 | **EXISTS** (default differs) | `OptsExtra.Connections`; default 16 — set 8 per task from ZentraDL, no patch |
| Adaptive; re-split slow segments | **EXISTS** (partially) | Slow-start + chunk-halving + work stealing exist; **no** 429/503 back-off, no per-host memory (S7/S8 need app-side logic) |
| Per-host caps | **MISSING** | No per-host state anywhere; needs core patch + app-side host tracker |
| Single-conn fallback (no Range) | **EXISTS** | `runConnectionWithResolveResp`; non-range resume restarts at 0 |
| Resume across restarts/crashes | **EXISTS** | bbolt `task`+`save` buckets; boot forces `pause`, lazy `Restore()` |
| ETag/Last-Modified/size validation | **MISSING** | Only mtime stamping; no validator, no size check → **core patch** (store validator at resolve, verify on resume) |
| Partial-file integrity | **MISSING** | Offset bookkeeping only; no checksums |
| Retry none/N/unlimited + exp-backoff/jitter | **MISSING** (fixed policy) | Fixed: 3× for counted 4xx, infinite linear ≤5 s otherwise, 403 fatal; **no settings** → core patch + settings UI |
| Auto-resume on connectivity return | **MISSING** (app-side) | Core retries network errors forever *while running*, but Android kill/sleep needs app-side `ConnectivityManager` → `Continue` |
| Link pre-check (name/size/type/resumable/redirects) | **PARTIAL** | `Resolve()` returns name/size/range before `Create()`; **no** HEAD probe (uses GET), no MIME/HTML-instead-of-file warning, no redirect-chain/server info → surface `Res` + extend |
| Refresh expired link in same task | **PARTIAL** | `Patch()` swaps URL + redirect-fallback covers 401/403/404/410; browser re-capture flow is app-side |
| Headers/cookies/referer/UA/auth/POST | **PARTIAL** | All possible manually via `ReqExtra`; no UA presets, no cookie jar export, no saved per-host creds → app-side vault + presets |
| Mirrors / multi-source + checksum | **MISSING** | No mirror fields; needs core patch (and checksum verify with it) |
| Proxy system/HTTP/SOCKS5 global+per-task | **EXISTS** | `DownloaderProxyConfig` + `RequestProxy{follow,none,custom}` |
| Checksums MD5/SHA-* verify | **MISSING** | No fields, no verify pass → core patch (verify on completion) + UI |
| Filename sanitizing/conflict policy | **PARTIAL** | `SafeFilename` + auto-rename dup check; Content-Disposition parsing incl. RFC 5987/GBK; no overwrite/ask/skip policy, no ext-preserving truncation → app-side |
| Free-space preflight + prealloc | **PARTIAL** | Prealloc via `Truncate` exists; **no free-space check** → app-side preflight |
| Batch/wildcards/grab-from-page | **MISSING** (app-side) | `CreateDirectBatch` takes an explicit list only |
| HLS (m3u8, AES-128) + DASH + picker + merge | **MISSING** | No fetcher (`internal/protocol/` = bt/ed2k/http only); `.m3u8` downloads as a plain file → **new core fetcher** (Gopeed-style `FetcherManager`) + merge strategy TBD (ffmpeg-kit retired — needs research) |
| Extensions (JS) | **EXISTS** | `pkg/download/engine/` + extension screens data; install/enable/settings via REST (sibling doc) |
| Segment/connection map for UI (§5.5 HTTP equiv.) | **PARTIAL** | Per-conn downloaded/completed/failed/retryTimes via `Stats()`; **ranges+speed missing** → extend `StatsConnection` (wire-compatible) |
| FTP/SFTP stretch | **MISSING** | No fetcher |

**Checksum for this section:** 6 EXISTS · 7 PARTIAL · 9 MISSING. All MISSING/PARTIAL items
except HLS/DASH-fetcher, ETag validation, retry config, checksum verify, mirrors and
per-host caps are app-side (Kotlin) work; those six need small, isolated Go core patches
(new files only, keep `core/` upstream-mergeable, list in `docs/CORE_PATCHES.md`).

---

## Appendix A. Verification log (all reads 2026-09-20, all `ref=v1.9.3` unless noted)

GitHub API listings: `pkg/`, `internal/`, `pkg/base`, `pkg/download`, `pkg/download/engine`,
`pkg/protocol`, `pkg/protocol/http`, `internal/protocol`, `internal/protocol/http`,
`internal/fetcher`, `internal/controller`, `bind/`, `bind/mobile`, `pkg/rest`.
Full raw texts: `go.mod` (@tag and @main), `pkg/base/model.go`, `pkg/base/constants.go`,
`pkg/protocol/http/model.go`, `internal/protocol/http/{config, fetcher, fetcher_manager,
helper}.go`, `internal/fetcher/fetcher.go`, `internal/controller/controller.go`,
`pkg/download/{downloader, model, event}.go`, `bind/mobile/main.go`.
Pages: release `v1.9.3` (incl. `Latest` badge + asset list incl. Android APKs
arm64-v8a/armeabi-v7a/x86_64/universal), pkg.go.dev module page (v1.9.3 / Go 1.24.9).
`grep.app` repo-scoped search was attempted for `ETag`/`m3u8` but ignored the repo filter
(global results) — absence claims above rest instead on full-text retrieval of
`fetcher.go` (44 KB), `helper.go` (13 KB), `constants.go`, and the verified
`internal/protocol/` listing (bt/ed2k/http only).
