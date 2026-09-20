# FEATURES.md — ZentraDL feature catalogue (ID'd rows; status in PROGRESS.md)

Spec: `docs/BRIEF.md` §5–§7. Every row is DONE or PARTIAL/BLOCKED with reason
before release (§11). Status lives in `docs/PROGRESS.md`.

## H — HTTP engine (§5.1, Phase 1/2/5; core: Gopeed `internal/protocol/http`)

| ID | Name | Notes |
|---|---|---|
| H1 | Multi-part 1–32 conns, default 8, adaptive + slow-segment re-split | Upstream exists (default 16 → set 8 from app); 429-backoff = patch |
| H2 | Resume across restarts/crashes/reboots (bbolt) + ETag/Last-Modified/size validation | Resume exists; validators = core patch |
| H3 | Retry policy none/N/unlimited + exp-backoff/jitter, auto-resume on connectivity | Fixed policy upstream → core patch + app ConnectivityManager |
| H4 | Link pre-check (name/size/type/resumable/redirects/server; HTML-instead-of-file + 403/410 warn) | `Resolve()` exists; warnings = app-side |
| H5 | Refresh expired link in same task (browser re-capture → Patch URL) | `Patch()` exists; flow = Phase 5 |
| H6 | Request customization (headers/cookies/referer/UA presets/auth/POST, per-host vault opt-in) | Manual headers exist; presets+vault = app-side |
| H7 | Mirrors/fallback URLs multi-source + checksum validation | Core patch |
| H8 | Proxy system/HTTP/SOCKS5 global + per-task | Exists |
| H9 | Checksums MD5/SHA-1/256/512 + sibling-file auto-detect + verify badge | Core verify patch + UI |
| H10 | Filename sanitize/conflict policy (rename/overwrite/ask/skip) + ext-preserving truncation | Partial upstream; policy UI app-side |
| H11 | Free-space preflight + optional preallocation | Prealloc exists; preflight app-side |
| H12 | Batch (multi-URL paste, txt import/export, `[001-100]` wildcards, grab-from-page w/ filters) | App-side |
| H13 | HLS (m3u8 AES-128 non-DRM) + DASH/CMAF picker, parallel segments, merge .ts/.mp4 | New core fetcher; remux lib TBD (no ffmpeg-kit) |
| H14 | Gopeed JS extensions (install git URL/file, enable/disable/update/settings, trust warning) | Exists in core; manager UI Phase 5 |
| H15 | Stretch: FTP/SFTP | Only if lib found; else GAPS.md |

## Q — Queues/scheduling/limits (§5.2, Phase 3)

| ID | Name | Notes |
|---|---|---|
| Q1 | 1–30 simultaneous (default 3), named queues w/ concurrency/limit/networks/window, drag order, priority H/N/L | App-side over `maxRunning` |
| Q2 | Global + per-task speed limits (separate torrent upload), timed schedule, alt-speed toggle | Core patch (limiter) + header UI |
| Q3 | Conditions (Wi-Fi/metered/roaming, battery %, charging, VPN, Data Saver, monthly cap) | App-side |
| Q4 | Scheduler (start/stop task/queue, repeat days, survives reboot via WorkManager) | No FGS from BOOT (target 35+ rule) |
| Q5 | Pause/Resume/Remove-all + confirm + undo; on-finish action (nothing/notify/close) | App-side |

## ST — Storage & files (§5.3, Phase 2/3)

| ID | Name | Notes |
|---|---|---|
| ST1 | Default + per-category folders, SAF picker (SD/USB), free-space, move-on-complete, auto-clear history | StorageStrategy A/B (direct vs stage+move) |
| ST2 | Private vault (app-private, .nomedia, biometric, secure delete) | App-side |
| ST3 | File actions (open/FileProvider, open-with, share, rename, move, copy link, delete task vs task+file, show-in-folder, details, checksum, APK prompt opt-in, zip extract) | App-side; other formats only via compatible lib |

## T — Torrent (§5.4, Phase 4; core: anacrolix wrapper)

| ID | Name | Notes |
|---|---|---|
| T1 | Add via magnet/.torrent picker/URL/info-hash/clipboard/share/deep-link/mime-handler (+QR) | URL/info-hash resolve app-side |
| T2 | Add-torrent sheet (metadata progress, file tree tri-state, filters/search, per-file priority, space check, dest/category/tags, start modes, sequential, first/last, skip-hash, trackers, private flag) | Per-file priority + sequential = core patch |
| T3 | Details tabs Info/Files/Pieces/Peers/Trackers/Stats/Settings | Peers basic exists; trackers tab needs patch |
| T4 | Actions (pause/resume/recheck/re-announce/move-storage/rename/copy-magnet/export/delete±data) | Thin endpoints over lib calls |
| T5 | Global torrent settings (DHT/PeX/LSD/UPnP/NAT-PMP/uTP/encryption/port/limits/slots/IP-filter/proxy/anon/seed-rules/Wi-Fi+charging) | New config fields (client-restart note) |
| T6 | Auto-move finished (keep seeding via move-storage) | Patch + app-side |
| T7 | Sequential + streaming to Media3/external player | Core patch + player |
| T8 | RSS/Atom manager (interval, include/exclude/regex → auto-download) | App-layer |
| T9 | Create torrent (piece-size, trackers, webseeds, private, comment, seed, share) | New builder |
| T10 | v2/hybrid support (or GAPS.md entry) | Inherited; surfacing TBD |
| T11 | Health indicator + stalled detection + suggestions; seeding notification | App-layer over stats |
| T12 | LibreTorrent gaps → docs/GAPS.md + alternative (never silent) | Process row |

## PM — Pieces map (§5.5 showcase, Phase 4)

| ID | Name | Notes |
|---|---|---|
| PM1 | Core: RLE pieceDetail endpoint (states/avail/in-flight/verifying/fileRanges, down-sample, flags) | §6 ENGINE_API; Go tests |
| PM2 | UI: single-Canvas renderer (aggregate cells, pinch-zoom→1:1, 200k smooth, legend/mode switch/contiguous-%, tap sheet, file-highlight, a11y text mode) | Screenshot tests 10/5k/200k |
| PM3 | HTTP Segments map (per-conn range/progress/speed/retries) | Core stats patch + UI |

## B — Browser & sniffer (§5.6, Phase 5)

| ID | Name | Notes |
|---|---|---|
| B1 | WebView multi-tab + previews, incognito, bookmarks/history, engines, desktop toggle, find-in-page, shortcuts, long-press download | App-side |
| B2 | Download interception → Add sheet (URL/name/size/MIME/cookies/UA/referer) | App-side |
| B3 | Media sniffer (intercept patterns + document-start JS hooks, verify, ad/tiny filter, badge → sheet w/ multi-select) | App-side |
| B4 | Ad-block (host lists, custom URLs, allow-list) + popup blocker + indicators | App-side |
| B5 | Saved credentials vault opt-in; per-site JS/cookie/perms; clear-on-exit | App-side |
| B6 | Share/Open-with/intent integration from other browsers | App-side |

## S — Smart features (§5.7, Phase 6; ALL: toggle + 1-line explanation + Auto chip + log + undo; moves/renames/deletes OFF by default; on-device only; master switch + reset)

| ID | Name | Notes |
|---|---|---|
| S1 | Smart categories (rules by ext/MIME/regex/domain/size/app/content; 10 defaults; teach-by-override; Off/Suggest/Auto) | — |
| S2 | Auto-sort on completion (token templates, per-category override, preview, conflicts, organize-existing, 10s undo; torrents keep seeding) | — |
| S3 | Smart rename (strip junk, title-case, date prefix, preview, editable rules) | — |
| S4 | Duplicate detection (URL/name+size/info-hash/content-hash; open/again/skip; auto-skip opt) | — |
| S5 | Smart clipboard (foreground detect, chip, batches, ignore-list; QS tile + notif action) | No bg reads (platform) |
| S6 | Smart conditions (Wi-Fi/charging/battery/night/metered/cap; "later on Wi-Fi") | — |
| S7 | Adaptive connections & speed (per-server tune, 429 backoff, per-host memory) | + core caps patch |
| S8 | Link health + auto-heal (probe, classify, safe auto-fix, "Fix it" card) | — |
| S9 | Storage manager (guard, insights, approved cleanups, auto-extract opt, .torrent delete, APK prompt) | — |
| S10 | Smart notifications (quiet hours, batching, hide-tiny, failures-only, per-category sound) | — |
| S11 | Torrent helpers (skip junk, tracker-list URL refresh, auto-sequential single-video, seed goals + auto-remove, low-seed warn) | — |
| S12 | Smart search (name/URL/tag/category + saved filters) | — |
| S13 | Insights (weekly summary, best-time, speed-by-network; on-device) | — |
| S14 | Adaptive post-completion quick actions by file type | — |

## R — Automation rules (§5.8, Phase 6)

| ID | Name | Notes |
|---|---|---|
| R1 | Rules engine (triggers/conditions/actions incl. ask-me; S1–S3 run on it) | Unit-tested |
| R2 | Sentence-style builder + gallery + dry-run tester + ordering/conflicts + JSON import/export | UI on R1 |

## X — Extras (§5.9, Phase 7)

| ID | Name | Notes |
|---|---|---|
| X1 | Glance widgets (active + speed + pause-all; quick add) + QS tiles + shortcuts + share targets | — |
| X2 | Backup/restore (settings/rules/tasks/bookmarks; password-encrypted; secrets excluded unless encrypted) | — |
| X3 | App lock (biometric/PIN) + activity log (share/redact) | — |
| X4 | Statistics (per-day/week, speed history, storage by category, top domains) | — |
| X5 | User-triggered update check (off by default) | — |
| X6 | Stretch: TV/leanback, Firefox extension via local API (off) | Only if core quality safe |

## U — UI/UX (§6, Phases 2–7; skills A+B mandatory; checklist per screen)

| ID | Name | Notes |
|---|---|---|
| U1 | Global (M3 + dynamic/fallback, System/Light/Dark/AMOLED, accent picker, edge-to-edge, predictive back, adaptive nav, haptics, i18n, RTL) | — |
| U2 | Navigation compact Downloads/Browser/Activity/Settings; torrents inline w/ chip+badge | — |
| U3 | Downloads home (search/sort/density, speed header+sparkline, filter chips, grouped sections, 3-density cards, swipes+Undo, multi-select, FAB menu, empty state) | — |
| U4 | Add-download sheet (clipboard suggest, auto-resolve skeleton, editable name, folder+space, category chip, conn stepper, Advanced, Download/Queue, batch checklist, inline errors) | — |
| U5 | HTTP details (hero+graph, facts grid, Segments map, actions, logs, Fix-it card) | — |
| U6 | Torrent add-sheet + details tabs (per §5.4/5.5) | — |
| U7 | Browser screens (bar, tabs sheet, sniffer badge, blocker indicators) | — |
| U8 | Activity (charts, auto-action log+Undo, insights cards gated) | — |
| U9 | Settings (searchable 13 groups, one-line descriptions, reset-per-section; mirror in SETTINGS.md) | — |
| U10 | Onboarding ≤3 skippable (value; folder+theme; smart preview) + JIT permissions w/ rationale | — |

## N — Notifications & intents (§7, Phase 2/3)

| ID | Name | Notes |
|---|---|---|
| N1 | Channels (Active low-ongoing/Completed/Failed-high/Seeding/Scheduled) + API33 POST_NOTIFICATIONS flow | + `FOREGROUND_SERVICE_DATA_SYNC` (API34+) |
| N2 | Per-task progress + Pause/Resume/Cancel; group summary; completion/failure actions; ≤1 Hz; quiet hours; per-category sound; hide-tiny | — |
| N3 | Intent filters (VIEW dl-types, magnet:, .torrent, SEND/SEND_MULTIPLE, PROCESS_TEXT); never auto-start unless enabled | Validate all intents |
