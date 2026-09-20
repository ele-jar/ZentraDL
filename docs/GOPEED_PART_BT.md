# Gopeed BitTorrent engine + piece-state API — upstream analysis (ZentraDL)

> Sources: live upstream `github.com/GopeedLab/gopeed` `main` branch, fetched 2026-09-20.
> Do NOT treat this as a pinned release: re-check `go.mod` when `core/` is pinned.
> Related BRIEF sections: 4.2 (engine bridge), 5.4 (torrent parity), 5.5 (Pieces map).

## 1. BT implementation: CONFIRMED — wraps `anacrolix/torrent`

**Verdict: yes, Gopeed's BT fetcher is a thin wrapper around `github.com/anacrolix/torrent`.**

- `go.mod` (main, `go 1.25.4`):
  `github.com/anacrolix/torrent v1.60.1-0.20251217073903-486bcbe758e0` (pseudo-version on top of v1.60.x line; latest tagged anacrolix at time of writing is v1.61.0).
- Supporting anacrolix deps in the same `go.mod`: `anacrolix/dht/v2 v2.23.0`, `anacrolix/go-libutp v1.3.2`, `anacrolix/upnp v0.1.4`, `anacrolix/utp v0.2.0`, plus `RoaringBitmap/roaring v1.9.4` (used by Gopeed's peer-relevance code).

### Key files (upstream paths)

| Path | Role |
|---|---|
| `internal/protocol/bt/fetcher.go` (~21 KB, the whole BT engine glue) | `Fetcher`: `Resolve/Start/Pause/Close/Patch/Progress/Wait/Upload`, client singleton, `Stats()` builder (`buildBTPieceMap`, `buildBTPeers`), tracker injection, seeding loop |
| `internal/protocol/bt/config.go` | Global BT config struct (only 5 fields — see §2) |
| `internal/protocol/bt/dns_cache_resolver.go` | DNS-cache dialer wired as `cfg.TrackerDialContext` (5-min refresh) |
| `internal/protocol/bt/fetcher_test.go`, `selection_test.go`, `stats_test.go` | Selection/patch + snapshot-vs-runtime + peer-normalisation tests |
| `pkg/protocol/bt/model.go` | `ReqExtra{Trackers}`, `StatsSnapshot{SeedBytes,SeedRatio,SeedTime,PieceMap}`, `StatsRuntime{TotalPeers,ActivePeers,ConnectedSeeders,ConnectedLeechers,Peers}` |
| `pkg/base/piece_map.go` | `PieceMap` bitset-v1 type (NEW on main — see §3) |
| `pkg/base/stats.go` | `PeerStats{Address,Client,DownloadSpeed,UploadSpeed,PieceCount,Completion?,Relevance?,Source,Transport}` |
| `pkg/base/model.go` | `Request/Resource/FileInfo/Options{SelectFiles}/DownloaderStoreConfig{ProtocolConfig}` |
| `internal/fetcher/fetcher.go` | `Fetcher` + `Stats{Snapshot any, Runtime any}` split contract; `Progress []int64` is per-**file** bytes, not per-piece |
| `pkg/download/model.go`, `pkg/download/downloader.go` | `Task`, `TaskRuntimeStatus{Files[] FileRuntimeStatus}`, `Stats(taskID)`, `Patch(taskID,…)` passthrough |
| `pkg/api/service.go`, `pkg/api/routes.go` | REST surface: `GET /api/v1/tasks/{id}/stats`, `GET .../status`, `PATCH .../tasks/{id}` (see §2) |
| `pkg/rest/server.go` | HTTP transport (routes delegate to `pkg/api.Service`) |

### Client setup (`initClient`, `addTorrent` in `fetcher.go`)

- One shared `*torrent.Client` process-wide (`var client`), guarded by `lock`; closed when last torrent drops.
- `torrent.NewDefaultClientConfig()` then overrides **only**: `Seed=true`, `Bep20="-GPxxxx-"`, `ExtendedHandshakeClientVersion="Gopeed <ver>"`, `ListenPort` (from BT config), `HTTPProxy` (from per-request proxy), `TrackerDialContext` (DNS cache). **Everything else (DHT, PEX, uTP, UPnP, encryption, connection limits) = anacrolix defaults, no Gopeed toggle.**
- Storage: `storage.NewFileOpts` with custom `FilePathMaker`/`TorrentDirMaker` rooted at task `Opts.Path`; includes v2-aware layout helper (`torrentFileLayout`, handles `NoName` rootless + v2 named tree root).
- Add paths: `MAGNET:` scheme → `TorrentSpecFromMagnetUri`; `FILE:`/plain path/`DATA:` URI → `metainfo.Load` (with hotfix ignoring anacrolix issue #992 `"expected EOF"`), `TorrentSpecFromMetaInfoErr`; then `<-t.GotInfo()` (magnet metadata fetch blocks here), private-torrent detection (`info.Private`), external trackers skipped for private torrents.
- BEP-20 peer id `-GP…-`, i.e. identifiable as Gopeed on the wire.

## 2. What exists today (main branch)

| BRIEF 5.4 item | Upstream status | Evidence |
|---|---|---|
| Magnet add | ✅ | `SchemeFilter{FilterTypeUrl, "MAGNET"}`, `TorrentSpecFromMagnetUri`, `<-GotInfo()` |
| `.torrent` file add | ✅ | `SchemeFilter{FilterTypeFile, "TORRENT"}` + `metainfo.Load` from `FILE:` path |
| `.torrent` via base64/data-URI | ✅ | `SchemeFilter{FilterTypeBase64, "APPLICATION/X-BITTORRENT"}` + `DATA:` branch |
| `.torrent` URL / info-hash paste / QR | ❌ (core) | No URL-fetch-to-torrent or raw-infohash path in `addTorrent`; UI-layer concern |
| File selection (skip vs download) | ✅ | `Options.SelectFiles`, `Start()` (`DownloadAll` vs per-file `Download()`), `Patch()` re-select via `CancelPieces(0,NumPieces)` + re-`Download()`; invalid indices silently ignored; unselected files deleted on completion (`removeUnselectedFile`, traversal-safe) |
| Per-file priority (skip/low/normal/high) | ❌ | Only binary wanted/unwanted. anacrolix HAS `File.SetPriority(PiecePriorityNone/Normal/High/…)` and `Piece.SetPriority`, but Gopeed never calls them |
| Sequential / first-last-piece mode | ❌ | No `Reader`/`SetReadahead`/request-strategy wiring. anacrolix supports streaming via `Torrent.NewReader()` + readahead/Now/Next priorities — unused by Gopeed |
| Trackers: add at creation | ✅ (add-only) | `bt.ReqExtra.Trackers` (per-task) + global `config.Trackers`, merged + deduped, `AddTrackers()`; auto-refreshed public list is a docs claim ("update tracker list every day"), not a core API |
| Trackers: list / remove / re-announce / per-tracker stats | ❌ | No `ModifyTrackers`, no announce-state exposure, no `GET trackers` endpoint. anacrolix HAS `ModifyTrackers()` + per-announcer status (`writeStatus`) — unwired |
| Peers list | ✅ (basic) | `StatsRuntime.Peers[]`: address, client name, down/up payload B/s, remote piece count, `completion?` (0–1, nil pre-metadata), `relevance?` (share of local wanted-missing set the peer has, nil when empty/unknown), `source` (tracker/dht/pex/incoming/direct/holepunch/unknown), `transport` (tcp/utp/webrtc/unknown). Magnet-pre-metadata peers kept with nil completion/relevance |
| Peer ban / flags / progress / encryption state / GeoIP | ❌ | No ban API, no choke/interest/seed-flag fields, no `headerEncrypted` exposure (anacrolix `KnownSwarm()` carries `SupportsEncryption` — unused), no GeoIP |
| Seeding goals (ratio / time / keep) | ✅ (global only) | `config{SeedKeep, SeedRatio (default 1.0), SeedTime (default 7200 s)}`; `doUpload()` 1-s loop stops via `Close()` when goal hit. No per-torrent override, no "seed on Wi-Fi/charging only" (app-layer) |
| DHT / PEX / LSD / UPnP / uTP / encryption settings | ❌ toggles; ✅ inherited defaults | anacrolix defaults active (docs advertise "DHT, PEX, uTP, Web Seeds, UPnP"); `ListenPort` is the ONLY network knob. No enable/disable, no port-random, no NAT-PMP/LSD/anonymous-mode flags in Gopeed config |
| Proxy | ✅ (partial) | Per-request `HTTPProxy` for client + tracker dial path; no "proxy for peers vs trackers separately" split |
| Per-task / global speed limits (incl. upload) | ❌ for BT | No rate-limiter wiring in BT fetcher (HTTP fetcher has connection counts; BT has none) |
| Max connections / upload slots / active torrent caps | ❌ | Not in `config`; anacrolix `ClientConfig` fields untouched |
| IP filter (eMule/PeerGuardian) | ❌ | Nothing in core |
| Availability / health figures | ✅ (partial) | `TotalPeers/ActivePeers/ConnectedSeeders/ConnectedLeechers` + per-peer relevance; NO per-piece availability array (see §3) |
| Piece completion map | ✅ (verified-complete only) | `PieceMap` bitset-v1 in `StatsSnapshot` (see §3) |
| Move storage / rename / recheck / export .torrent / copy magnet | ❌ (core) | No such endpoints; `PATCH` only touches `SelectFiles` |
| Create torrent | ❌ | No builder endpoint |
| RSS | ❌ | No feeds in core |
| v2 / hybrid torrents | ✅ inherited (with caveat) | anacrolix ≥v1.56 implements v2 (piece layers, Merkle hashing, dual-swarm announce, `MagnetV2`); Gopeed handles v2 layout + `HasV2()` branches, but exposes only ONE `Resource.Hash` string (v1 hex) — no v2 hash field, no `MagnetV2` parse path verified in `addTorrent` |
| Web seeds (BEP 19) | ✅ inherited, ❌ surfaced | anacrolix `UrlList`/webseed peers work; no Gopeed API to view/add them |

### REST endpoints touching BT (via `pkg/api.Service`)

- `POST /api/v1/resolve` → magnet metadata + file list (powers the add-sheet).
- `POST /api/v1/tasks` (+ `/batch`) → create.
- `PATCH /api/v1/tasks/{id}` (`{req, opts}`) → BT: only `opts.selectFiles` honoured.
- `GET /api/v1/tasks/{id}` → full task (meta+resource+opts).
- `GET /api/v1/tasks/{id}/status` → lightweight progress (per-file bytes).
- `GET /api/v1/tasks/{id}/stats` → `{snapshot: StatsSnapshot, runtime: StatsRuntime}` — **the Pieces/Peers source**.
- `GET/PUT /api/v1/config` → `DownloaderStoreConfig.ProtocolConfig["bt"]` carries the 5-field BT config.
- `PUT .../pause|continue`, `DELETE ...` → lifecycle. No BT-specific verbs (no re-announce/recheck/move).

## 3. Piece-state feasibility

### 3.1 What already landed (main has MORE than older releases)

`pkg/base/piece_map.go` — `PieceMap{pieceCount, pieceSize, completedPieces, data}`:

- Wire format `encoding: "bitset-v1"`: 1 bit/piece, LSB-first; JSON `data` is Base64.
- `Snapshot.PieceMap` is `nil` while metadata missing OR any `PieceStateRun.Ok==false` (storage still verifying/restoring) — *"nil Snapshot tells Downloader to retain the last trusted bitset"*; `Stats()` clones before returning, safe for concurrent poll.
- `Downloader` persists `Snapshot`, keeps `Runtime` in memory (`internal/fetcher/fetcher.go` contract; `stats_test.go` pins the split).

**Payload (do NOT send full arrays per tick — already compact):**
100 000 pieces → 12 500 B raw (~16.7 KB Base64 JSON); 200 000 → 25 KB (~33 KB b64).
At 1 Hz while the Pieces tab is visible this is acceptable on loopback; for paranoia add RLE/diff or down-sampled runs (BRIEF 5.5 already mandates RLE-or-diff + ~1 Hz + visible-tab-only).

### 3.2 What is MISSING for BRIEF 5.5

| Needed | anacrolix source | Gopeed today |
|---|---|---|
| Availability (peers-having-piece) | ✅ `piece(i).availability()`, `pieceAvailabilityRuns/Frequencies`, `connsWithAllPieces` | ❌ not exported (only swarm totals + per-peer relevance) |
| In-flight / partial pieces | ✅ `PieceState.Partial`, `piecePartiallyDownloaded`, dirty-chunk bitmap | ❌ collapsed to "not complete" (`applyBTPieceRuns`: `completed = Ok && Complete`) |
| Verifying / hashing pieces | ✅ `PieceState.Hashing/QueuedForHash/Checking/Marking` | ❌ same collapse (test explicitly asserts partial+hashing → `false`) |
| Skipped pieces (deselected files) | ✅ `Priority==PiecePriorityNone` runs (already used for `Relevance` denominator) | ❌ not exported per piece |
| File→piece-range mapping | ✅ `File.BeginPieceIndex/EndPieceIndex`, `File.State() []FilePieceState` | ❌ no mapping endpoint |
| Live deltas / subscriptions | ✅ `SubscribePieceStateChanges()`, `Torrent.PieceState(i)` | ❌ poll-only via `Stats()` |
| Per-piece bytes (last-piece tail) | ✅ `Piece.Info().Length()` / `metainfo.Piece.Length()` | ⚠️ derivable: `pieceSize` + total length, last piece shorter (client must compute) |

### 3.3 Patch sketch (minimal, upstream-mergeable)

1. New optional struct in `pkg/protocol/bt/model.go`, e.g. `PieceDetail{runs|availability|inflight|verifying|fileRanges}` — keep `PieceMap` bitset as the always-on field; put the rest behind query flags (`?pieces=map+avail+flight&downsample=N`) so the default `/stats` payload doesn't grow.
2. In `internal/protocol/bt/fetcher.go`: build from `t.PieceStateRuns()` (already imported) — map `Priority/Partial/Hashing|Checking` → states; aggregate `availability` via per-piece availability accessor or `PeerPieces` unions; map files via `Files()[i].BeginPieceIndex/EndPieceIndex`.
3. Compress: runs MUST be RLE (`[{state,len}]` or base64 bitsets per state); cap `downsample` server-side for >50k pieces (cell = majority/frac-complete); document exact JSON in `docs/ENGINE_API.md` before Kotlin/UI work (per BRIEF 14.6).
4. Push vs poll: `SubscribePieceStateChanges` → WebSocket/SSE event is the lazy-efficient path; 1 Hz poll is the fallback. Either way gate on tab visibility.
5. Tests: extend `internal/protocol/bt/stats_test.go` (runs→states mapping, RLE round-trip, nil-while-verifying preserved) + `pkg/base/piece_map_test.go`.

**Feasibility: HIGH.** All raw signals exist one call below Gopeed's wrapper; no protocol re-implementation, no new dependency (roaring already vendored). ~1 small endpoint/flag-set + model types.

## 4. Gaps vs BRIEF 5.4 / 5.5 (explicit list — nothing silently dropped)

**5.4 Torrent gaps (Gopeed core lacks; ZentraDL must patch or app-layer):**
1. Per-file priority levels (only skip/download) — needs `File.SetPriority` wiring + `PATCH` extension.
2. Sequential + first/last-piece streaming mode — needs Reader/readahead or request-strategy priority preset.
3. Tracker list/remove/edit/re-announce + per-tracker status/counters — needs `ModifyTrackers` + announcer-state exposure + new endpoints.
4. Peer actions + detail (ban, flags/choke/interest, progress %, encryption, TCP/uTP already present as `transport`, GeoIP) — partial patch (ban = app-maintained blocklist → `AddPeers` filter or client blocklist).
5. DHT/PEX/LSD/UPnP/NAT-PMP/uTP/encryption toggles, listen-port random/custom, max connections, upload slots, active-torrent caps — new `config` fields mapped to `torrent.ClientConfig` (note: client is a singleton → changing these likely requires client restart; document it).
6. Per-torrent speed limits + per-torrent seeding-goal override — new fields + limiter wiring.
7. IP filter (dat/PeerGuardian, file/URL, auto-update) — new subsystem.
8. Proxy split (trackers vs peers) + anonymous mode — config + client wiring.
9. Torrent lifecycle ops: force recheck (`VerifyData` exists in lib), move storage, rename, export `.torrent` (`Metainfo()` exists in lib), copy-magnet (trivial from hash+trackers) — thin endpoints over existing lib calls.
10. Create-torrent (piece-size auto/manual, trackers, webseeds, private flag, comment, start-seeding) — new builder (`metainfo` + storage walk); lib has no high-level creator, moderate work.
11. RSS/Atom manager — entirely app-layer (polling + auto-`resolve`/create); no core work.
12. v2/hybrid full surfacing (v2 hash field, v2-magnet ingest proof, per-file v2 run) — verify + expose; lib support is real (v1.56+ changelog), Gopeed just under-exposes it.
13. Health/stalled logic, seeding notifications, Wi-Fi/charging-gated seeding — app-layer over existing stats; no core work except maybe configurable announce hooks.

**5.5 Pieces-map gaps (core patch required, all feasible — §3.3):**
14. No per-piece availability array/heat-map source.
15. No in-flight (downloading/partial) or verifying (hashing/checking) states — only verified-complete bitset.
16. No skipped-piece сильной signal per piece (only implicit via file selection).
17. No file→piece-range mapping endpoint.
18. No RLE/diff/down-sample wire option and no push channel (poll-only today).
19. No contiguous-from-start % (trivially computed from map; decide core vs client — prefer client to keep core small).

---
*Method: `go.mod`, `internal/protocol/bt/{fetcher,config,dns_cache_resolver}.go` (full read), `pkg/{protocol/bt/model,base/{piece_map,stats,model}}.go` (full read), `pkg/{api/{service,routes},rest/server,download/model}.go` + `internal/fetcher/fetcher.go` (full read), anacrolix `torrent.go` (`pieceState`, `PieceStateRuns`, v2/`isPrivate`/tracker/reader paths), anacrolix CHANGELOG (v2 in v1.56.0), gopeed.com docs (DHT/PEX/uTP/WebSeeds/UPnP claims). Flutter UI strings NOT audited (no code-search auth) — settings-screen parity unchecked.*
