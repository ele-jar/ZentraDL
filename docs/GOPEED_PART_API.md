# Gopeed Core — REST / Bind / Extension / Flutter API (upstream research)

> Source: `github.com/GopeedLab/gopeed`, branch `main` (2.0-beta era), read 2026-09-20
> via raw file fetches + GitHub API listings. No endpoint invented: every path below
> comes from `pkg/api/service.go` (`NewService` route table) or `pkg/rest/server.go`.
> Pin a release tag before forking into `core/` (BRIEF 4.1); re-verify paths then —
> v1.x and 2.0-beta differ (see §2).

## 1. Mobile bind — package, command, tags

- **Bind package:** `github.com/GopeedLab/gopeed/bind/mobile` — single file
  `bind/mobile/main.go`, Go package name `libgopeed`.
  Sibling adapters: `bind/desktop/main.go` (C-shared FFI: `.so`/`.dll`/`.dylib`)
  and `bind/native/invoke.go` (transport-neutral in-process executor shared by both).
- **gomobile setup + Android bind (README.md “Mobile”, exact):**

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
go get golang.org/x/mobile/bind
gomobile init
gomobile bind -tags nosqlite -ldflags="-w -s -checklinkname=0" \
  -o ui/flutter/android/app/libs/libgopeed.aar \
  -target=android -androidapi 21 -javapkg="com.gopeed" \
  github.com/GopeedLab/gopeed/bind/mobile
```

- **iOS (same section):** `gomobile bind -tags nosqlite -ldflags="-w -s" -o ui/flutter/ios/Frameworks/Libgopeed.xcframework -target=ios github.com/GopeedLab/gopeed/bind/mobile`
- **Desktop (same section, for reference):** `go build -tags nosqlite -ldflags="-w -s" -buildmode=c-shared -o ui/flutter/linux/bundle/lib/libgopeed.so github.com/GopeedLab/gopeed/bind/desktop` (windows → `libgopeed.dll`, macOS → `libgopeed.dylib`).
- **CI reference:** `.github/workflows/build.yml` (android + ios jobs) runs the same
  `gomobile bind` with `-tags nosqlite` plus version stamping
  `-ldflags "-w -s [-checklinkname=0] -X github.com/GopeedLab/gopeed/pkg/base.Version=$VERSION"`,
  AAR output `ui/flutter/android/app/libs/libgopeed.aar` (ABIs armeabi-v7a, arm64-v8a, x86, x86_64).
- **Build tags:** `-tags nosqlite` (drops the SQLite driver; `go.mod` still lists
  `modernc.org/sqlite`-family as indirect deps). `-checklinkname=0` is required on
  current main (absent in old v1.5.6 README, which also used `-androidapi 19`).
- **Toolchain:** README Environment = Go 1.25+, Flutter 3.41+; `go.mod` says `go 1.25.4`.
- **Reproduce in:** `scripts/build-core.sh` / `scripts/build-core.ps1` (BRIEF 4.2).
- **BT backend (confirmed, not assumed):** `go.mod` direct dep
  `github.com/anacrolix/torrent v1.60.1-0.20251217…` (+ `anacrolix/dht`, `go-libutp`, …);
  ed2k via `github.com/monkeyWie/goed2k`. Fetchers registered in
  `pkg/download/model.go` `DownloaderConfig.Init()`: `hls, http, bt, ed2k`.

## 2. Transport on mobile — start/stop, socket vs in-process, port + auth

Two generations exist. **Current `main` (2.0-beta): native in-process, no socket on mobile.**

- **Mobile call path (no HTTP):** Flutter `MethodChannel('gopeed.com/libgopeed')`
  (`ui/flutter/lib/core/common/libgopeed_channel.dart`: methods `start`, `stop`,
  `invoke`, `subscribeTaskEvents`, `getApiServerState`, …; `taskEvent` handler →
  `Stream<TaskEvent>`) → gomobile-exposed funcs in `bind/mobile/main.go`:
  `Start(cfg JSON) (port, err)`, `Stop()`, `InvokeAsync(method,path,query,body,requestID,listener)`,
  `SubscribeTaskEvents(mask,listener)` → shared executor `bind/native/invoke.go`
  (per-request goroutines, bounded 3 s drain `StopTimeout`) → `pkg/rest/invoke.go`
  `Dispatch()` → `pkg/api.Service.Dispatch/JSON` (same route table as HTTP).
  Desktop differs only in adapter: `LibgopeedFFi`/`LibgopeedWorker` over C FFI
  (`bind/desktop`, `ui/flutter/lib/core/ffi/`); web uses Dio HTTP
  (`entry/gopeed_transport_web.dart`, debug default `http://127.0.0.1:9999/`).
- **Start (mobile):** `ui/flutter/lib/core/entry/libgopeed_boot_native.dart` sets
  `storage='bolt'`, `storageDir=<app dir>`, `tempDir=<cache>`, `refreshInterval=0`
  → Go `Start()`: `ProductionMode=true`, `NativeMode=true`, `rest.Start()` →
  `initializeCore` (Bolt or mem storage, `Downloader.Setup()`), `ContinueOnStartup()`,
  returns bound port (0 when API server disabled). `ResumeInvokes()` re-arms the bridge.
- **Stop:** Flutter unsubscribes events then `stop` → `nativebridge.Stop()` →
  `pauseAndWait(3s)` + `rest.Stop()` (HTTP shutdown if running + `Downloader.Close()`).
- **Optional external REST server** (off by default on mobile: Flutter
  `StartConfig.apiEnable=false`): `pkg/rest/api_server_manager.go` serves the persisted
  `base.APIServerConfig{enable,mcpEnable,network,address,token}` (defaults `tcp`,
  `127.0.0.1:9999`); `NativeMode` in `pkg/rest/server.go` `Start()` adopts stored
  config. Manage via `Get/Start/Stop/RestartAPIServer` (also exposed through the bind).

- **Legacy v1.x transport (still documented in README “Development”):**
  Flutter ↔ Go over HTTP — unix socket on unix-like, TCP on Windows.
  `pkg/rest/model/server.go` `StartConfig{network,address}` defaults `tcp`/`127.0.0.1:0`
  (random loopback port, returned to caller); `network=="unix"` unlinks a stale socket file.
- **Auth:** `StartConfig.apiToken` → `X-Api-Token` header **or** `Authorization: Bearer <token>`
  (constant-time compare, `validateAPITokenHeaders`, `pkg/rest/server.go`); web UI adds
  cookie session (`gopeed-session`, 7-day TTL) + `POST /api/web/login`. CORS `*`.
  External API docs: `gopeed.com/docs/openapi`, `gopeed.com/docs/dev-api`
  (external callers must flip Settings → Advanced → Communication Protocol to TCP + set token).
- **Push: there is NO WebSocket endpoint on current main** (`gorilla/websocket` is only an
  indirect dep; no `ws://` route in `service.go`/`server.go`). Live updates are:
  (a) native `SubscribeTaskEvents(mask)` — bitmask in `pkg/api/events.go`
  (`done=bit0, error=bit1, start, progress, pause, delete`; JSON
  `{"type":"task.done|task.progress|…","taskId","name?","error?"}`, progress ticks at
  `refreshInterval`, default 350 ms); (b) HTTP polling — `GET /api/v1/tasks/{id}/status`
  (lightweight) or `GET /api/v1/tasks`; (c) `DOWNLOAD_DONE`/`DOWNLOAD_ERROR` webhooks
  (POST JSON) and MCP at `/mcp` (`pkg/mcpserver`, tools resolve/create/list/get tasks).

## 3. REST endpoints (26 API routes) + JSON models

Route table source: `pkg/api/service.go` `NewService()`; HTTP wiring
(`RouteSpecs()` → gorilla/mux) in `pkg/rest/server.go` `buildServer()`.
Envelope everywhere: `Result{code,msg,data}` (`pkg/rest/model/result.go`;
codes `0=ok, 1000=error, 1001=unauthorized, 1002=invalidParam, 2001=taskNotFound`).
Filters: `?id=…&status=…&notStatus=…` (`TaskFilter`); deletes take `?force=true`.

| # | Method | Path | Handler → Downloader call |
|---|--------|------|---------------------------|
| 1 | GET | `/api/v1/info` | version/runtime/os/arch/inDocker |
| 2 | POST | `/api/v1/resolve` | `Resolve(req, opts)` → `{id,res}` (pre-create probe) |
| 3 | POST | `/api/v1/tasks` | `CreateWithOptions(rid,opts)` or `CreateDirect(req,opts)` → taskId |
| 4 | POST | `/api/v1/tasks/batch` | `CreateDirectBatch` → [taskId] |
| 5 | PATCH | `/api/v1/tasks/{id}` | `Patch(id, req, opts)` (e.g. refresh expired URL) |
| 6 | PUT | `/api/v1/tasks/{id}/pause` | `Pause({ids:[id]})` |
| 7 | PUT | `/api/v1/tasks/pause` | `Pause(filter)` |
| 8 | PUT | `/api/v1/tasks/{id}/continue` | `Continue({ids:[id]})` |
| 9 | PUT | `/api/v1/tasks/continue` | `Continue(filter)` |
| 10 | DELETE | `/api/v1/tasks/{id}?force=` | `Delete(filter, force)` (force=delete files) |
| 11 | DELETE | `/api/v1/tasks?force=` | `Delete(filter, force)` |
| 12 | GET | `/api/v1/tasks/{id}` | full `Task` |
| 13 | GET | `/api/v1/tasks[?id&status&notStatus]` | `GetTasksByFilter` |
| 14 | GET | `/api/v1/tasks/{id}/status` | `RuntimeStatus` (lightweight poll target) |
| 15 | GET | `/api/v1/tasks/{id}/stats` | `Stats(id)` (protocol stats incl. `PeerStats[]`) |
| 16 | GET | `/api/v1/config` | `GetConfig` |
| 17 | PUT | `/api/v1/config` | `PutConfig` |
| 18 | POST | `/api/v1/extensions` | install by git URL (`{devMode,url}`) → identity |
| 19 | GET | `/api/v1/extensions` | list |
| 20 | GET | `/api/v1/extensions/{identity}` | one |
| 21 | PUT | `/api/v1/extensions/{identity}/settings` | `{settings:{…}}` |
| 22 | PUT | `/api/v1/extensions/{identity}/switch` | `{status:bool}` |
| 23 | DELETE | `/api/v1/extensions/{identity}` | uninstall (+storage wipe) |
| 24 | GET | `/api/v1/extensions/{identity}/update` | version check → `{newVersion}` |
| 25 | POST | `/api/v1/extensions/{identity}/update` | upgrade |
| 26 | POST | `/api/v1/webhook/test` | `{url}` must return HTTP 200 |
| + | POST | `/api/web/login` | web-auth only (cookie session) |
| + | ANY | `/api/web/proxy` | passthrough via `X-Target-Uri` header |
| + | — | `/mcp`, `/fs/tasks/…`, `/fs/extensions/…` | MCP handler; task/extension file serving (web build) |

**Task** (`pkg/download/model.go` + `pkg/base/constants.go` + `ui/flutter/lib/api/model/task.dart`):
`{id, name(derived), protocol: http|bt|ed2k|hls, meta:{req,res,opts} (FetcherMeta),
status: ready|running|pause|wait|error|done, uploading, progress:{used(ns), speed(B/s),
downloaded, uploadSpeed, uploaded[, extractStatus, extractProgress]}, createdAt, updatedAt}`.
Speed = 5 s rolling mean, tick = `refreshInterval`.
**TaskRuntimeStatus** (same file): `{status,used,speed,downloaded,total,uploadSpeed,uploaded,
extractStatus,extractProgress,files:[{index,size,downloaded}]}` — `files` is in
`selectFiles` order, `index` = original resource index.
**Request/Resource/Options** (`pkg/base/model.go`): `Request{url,extra,labels,proxy:{mode:none|follow|custom,scheme,host,usr,pwd},skipVerifyCert}`;
`Resource{name,size,range,files:[{name,path,size,ctime,req}],hash}`;
`Options{name,path,selectFiles[],extra}`.
**REST DTOs** (`pkg/rest/model/task.go|extension.go|server.go`):
`ResolveTask{req,opts}`, `CreateTask{rid,req,opts}`,
`InstallExtension{devMode,url}`, `UpdateExtensionSettings{settings}`,
`SwitchExtension{status}`, `UpdateCheckExtensionResp{newVersion}`,
`StartConfig{network,address,apiEnable,mcpEnable,refreshInterval,storage:mem|bolt,
storageDir,tempDir,whiteDownloadDirs,apiToken,downloadConfig,webViewRpcConfig}`.
**Config** (`pkg/base/model.go` `DownloaderStoreConfig`, `maxRunning` default 5):
`{downloadDir,maxRunning,protocolConfig:{http:{userAgent,connections,useServerCtime},
bt:{listenPort,trackers[],seedKeep,seedRatio,seedTime},ed2k:{listenPort,udpPort,serverAddr,…},
hls:{segmentConnections,maxRetries,timeoutSeconds,…}},proxy,webhook:{enable,urls[]},
script:{enable,paths[]},autoTorrent,archive:{autoExtract,deleteAfterExtract},
api:{enable,mcpEnable,network,address,token},autoStartTasks,autoDeleteMissingFileTasks}`.
**Stats:** `GET …/stats` → protocol stats; common peer rows `PeerStats{address,client,
downloadSpeed,uploadSpeed,pieceCount,completion?,relevance?,source,transport}`
(`pkg/base/stats.go`).
**Piece map:** `pkg/base/piece_map.go` — `bitset-v1`, 1 bit/piece LSB-first, JSON
`{encoding,pieceCount,pieceSize,completedPieces,data(base64)}`; tracks **verified-complete
only** (no downloading/hashing/availability states). Flutter decoder:
`ui/flutter/lib/api/model/piece_map_codec.dart`.
**Gaps vs BRIEF 5.4/5.5 (need core patches):** no per-piece in-flight/availability endpoint,
no peers/trackers list-management or re-announce endpoint, no per-file priority change
(`selectFiles` is create-time only), no per-task speed limit, no sequential/magnet-metadata
progress endpoint — all confirmed absent from the route table.

## 4. Extension engine — JS, install/enable/settings, trust

- **Language/runtime: JavaScript on goja.** `go.mod` direct deps `github.com/dop251/goja`
  (+ `goja_nodejs/eventloop`); `pkg/download/engine/engine.go` `NewEngine()` runs each
  script on an event loop with promise resolution and polyfills
  (`global`/`window`/`location`, `fetch`, `xhr`, `file`, `stream`, `ffmpeg`, `vm`, `url`).
  Per-trigger session: `pkg/download/extension_engine*.go`.
- **Injected `gopeed` object** (`pkg/download/extension.go`): `events.{onResolve,onStart,
  onError,onDone}`, `info{identity,name,author,title,version}`, `logger`, `settings`
  (type-coerced map), `storage` (per-extension KV persisted in Bolt bucket
  `bucketExtensionStorage`: get/set/remove/keys/clear), `runtime.webview`.
  Hooks fire in `doTrigger()` for enabled extensions whose `scripts[].match` hits:
  `onResolve` may supply/rewrite `Resource` (names sanitized via `SafeFilename`),
  `onStart` may rewrite `Request` (validated), `onError` exposes `Continue()`.
- **Manifest** (`manifest.json`, required; `Extension` struct + `validate()` in
  `pkg/download/extension.go`): `{name, author → identity "author@name", title,
  description, icon, version (semver), homepage, repository:{url,directory},
  scripts:[{event, match:{urls[] (Chrome match-pattern syntax), labels[]}, entry}],
  settings:[{name,title,description,required,type: string|number|boolean, value, options[]}]}`.
- **Lifecycle** (same file): install = `InstallExtensionByGit(url)` (depth-1 clone,
  `repo.git[#subdir]` split, proxy-aware) or `InstallExtensionByFolder(path, devMode)`
  (devMode = no copy, absolute `DevPath`); enable = `Disabled=!status`
  (`SwitchExtension`); settings = `UpdateExtensionSettings` (values coerced to declared
  type); update = re-clone + semver compare + `update()` merges settings (drops removed,
  appends new, preserves values); uninstall deletes disk dir + both storage buckets +
  webview profile. Persisted bucket: `bucketExtension`.
- **Trust — no sandbox, no permissions, no signatures.** A script runs with the full
  injected API (network fetch/XHR, file, stream/ffmpeg, webview, KV storage) and can
  rewrite tasks' URLs/resources; install source is any git URL. Core auto-enables on
  install. **ZentraDL consequences (BRIEF §3/§8):** user-installed-only (never bundled
  site scripts), install sheet must show identity/version/source URL + requested
  `event`s + settings with an explicit trust warning; consider default-off for fresh
  installs, ask-before-update, and an allow-list for `webview`/network use. Dev docs:
  `gopeed.com/docs/dev-extension`.

## 5. Flutter client + i18n — what to mirror

- **API client (1:1 with §3):** `ui/flutter/lib/api/api.dart` — `resolve`,
  `createTask`, `createTaskBatch`, `patchTask`, `getTasks(statuses)`, `getTaskStatus`,
  `getTaskStats`, `pause/continue/deleteTask` + batch `pauseAllTasks/continueAllTasks/
  deleteTasks`, `get/putConfig`, `install/get/updateSettings/switch/delete/upgradeCheck/
  updateExtension`, `testWebhook`, `login`, `proxyRequest`, `forward`, `join`;
  envelope parsed in `api/model/result.dart` (`code==0` ⇒ data), `ApiTimeoutException`
  on Dio timeouts. **Mirror this file as Kotlin `EngineClient`** (OkHttp +
  kotlinx.serialization; same paths/methods/query names).
- **Transport switch:** `ui/flutter/lib/core/network/gopeed/gopeed_transport.dart`
  + `entry/gopeed_transport_native.dart` (current: in-process `invoke`, ignores
  network/address; Dio kept only for external `proxyRequest`) vs
  `entry/gopeed_transport_web.dart` (plain HTTP). Boot/events/config lived in
  `ui/flutter/lib/core/{libgopeed_boot.dart, entry/libgopeed_boot_native.dart,
  common/{start_config,task_event,api_server_state,libgopeed_channel,libgopeed_ffi,
  libgopeed_interface}.dart}` — reuse `StartConfig` field list and the
  `TaskEvent{type,taskId,name?,error?}` shape for our push layer.
- **Dart models to port** (`ui/flutter/lib/api/model/`): `task.dart`
  (Status/Protocol/Progress/FileRuntimeStatus/TaskRuntimeStatus/ExtractStatus),
  `downloader_config.dart` (DownloaderConfig incl. `ExtraConfig`: theme/locale/
  bookmarks/createHistory/downloadCategories, BT tracker subscriptions, GitHub mirrors;
  `ApiServerConfig`; `Proxy/Webhook/Script/AutoTorrent/ArchiveConfig`),
  `request/resource/options/meta/resolve_task/create_task(+batch)/result/extension/
  install_extension/switch_extension/update_*/task_stats/piece_map_codec/store_extension`.
- **i18n (GPL-3.0, reusable with attribution):** `ui/flutter/lib/l10n/` —
  `app_en.arb` master + ~22 locales (`ar az ca de es fa fr hu id it ja ko pl pt ru ta
  tr uk vi zh zh_TW`), wired by `l10n.dart`. Mirror status/category/settings strings
  and error phrasing; add RTL check per BRIEF §9.
- **Reference UI flows** (same tree): task list/detail/settings/extensions pages show
  which config keys are actually surfaced (e.g. BT `seedKeep/seedRatio/seedTime`,
  tracker subscribe URLs, webhook/script lists) — use as completeness checklist for
  our Settings screens (`docs/SETTINGS.md`).

## 6. Implications for ZentraDL (`EngineClient` + `docs/ENGINE_API.md`)

1. Prefer **in-process `InvokeAsync`/`Dispatch`** over loopback TCP (matches 2.0 native
   mode; no port, no token in RAM, no exposure surface — BRIEF §8 “loopback only” is
   then trivially satisfied). Keep TCP+token path only for external consumers
   (browser extension bridge, §5.9 stretch).
2. **No WebSocket to implement** — push = `SubscribeTaskEvents` mask
   (done|error|start|progress|pause|delete); poll `…/status` ≤1 Hz foreground for
   speeds, full `GET /tasks` on `taskEvent` pings or screen entry.
3. Write `docs/ENGINE_API.md` from §3 tables verbatim (paths/query names/envelope codes),
   then port `api.dart` + `api/model/*` to Kotlin data classes.
4. Plan core patches (§3 gaps + BRIEF 4.2): RLE piece states + availability, peers/
   trackers CRUD + re-announce, per-file priority post-create, sequential mode,
   per-task limits, seeding goals — all in new files under `core/`, listed in
   `docs/CORE_PATCHES.md`; Go tests per patch.
