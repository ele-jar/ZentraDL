# ROLE & MISSION

You are a senior Android engineer (Kotlin / Jetpack Compose), Go engineer and product designer in one. Working autonomously in this repository through opencode, build a production-quality, open-source Android download manager that combines:

1. The power and convenience of 1DM+: fast multi-part HTTP downloads, built-in browser with video/audio sniffer, queues, scheduler, speed limiter, SD-card support, clipboard "smart download".
2. The torrent capabilities of LibreTorrent: magnet / .torrent, file selection, sequential download, trackers, peers, seeding rules, RSS auto-download, torrent creation, streaming. PLUS a live piece-by-piece "Pieces map" that shows exactly how many pieces are downloaded.
3. The engine and architecture of Gopeed (github.com/GopeedLab/gopeed: Go core + REST API + extension system). REUSE Gopeed's Go core as our download engine and extend it. Do NOT re-implement HTTP segmenting or the BitTorrent protocol from scratch.
4. Smart, optional, user-controllable automation (smart categories, auto-sort, rules, duplicate detection and more) and an exceptionally polished, modern, easy-to-use UI/UX.

Fill-in values (use everywhere, never ask me about them):
  APP_NAME: ZentraDL
  APP_ID:   com.elejar.ZentraDL
  LICENSE:  GPL-3.0-or-later
  TARGET:   Android 8.0+ (minSdk 26); compile/target = latest stable SDK; ABIs arm64-v8a, armeabi-v7a, x86_64

# 0. HOW YOU MUST WORK

0.1 Be autonomous. Do not stop after planning. Work phase by phase (Section 10): implement, build, test, commit. Ask me a question ONLY if (a) you need a secret or signing key, (b) I must install something or test on a real device/emulator, or (c) two requirements truly conflict. Otherwise decide, write a 3-line note in docs/DECISIONS.md, and continue.
0.2 Persist your memory (context can reset). Your first three actions, in order: (1) make sure this brief lives at docs/BRIEF.md (if I attached it as BRIEF.md, move it there; if it was pasted, save it verbatim); (2) create the skills in Section 1; (3) run the environment check (0.7). Then create AGENTS.md at the repo root (opencode reads it automatically) containing: project summary, architecture summary, build/test commands, coding rules, "always load skills android-ui-ux-design and download-manager-ux-patterns before ANY UI work", and pointers to docs/BRIEF.md and docs/PROGRESS.md. Keep docs/PROGRESS.md current: one row per feature (ID, name, status TODO/DOING/DONE/PARTIAL/BLOCKED, notes). At the start of every new session, read AGENTS.md and docs/PROGRESS.md, then continue where you stopped.
0.3 Use your todo tool for the current phase.
0.4 Never fake it. No stubs/mocks presented as done, no demo data in production code paths, no silent feature drops. If something is infeasible, mark PARTIAL/BLOCKED in PROGRESS.md with the reason and the best alternative.
0.5 Verify, don't guess. Library versions, Android API behaviour, Gopeed's real file layout and APIs: check the actual source, official docs (use web access if you have it) or Maven/Gradle metadata. Never invent dependency versions, class names or endpoints.
0.6 Keep the build green. After each meaningful change compile (Gradle assembleDebug; Go build/test for core changes) and fix errors before moving on. Commit after each finished feature (Conventional Commits). Never commit secrets or keystores.
0.7 Environment check: detect OS; verify JDK 17+, Android SDK (platform-tools, build-tools, latest platform, NDK), Go (version required by Gopeed's go.mod), gomobile, git, adb. If something is missing, write SETUP.md with exact commands for my OS, tell me, and continue with work that doesn't need it.
0.8 After each phase print a short report: done / next / blockers / how I can test it.

# 1. STEP ZERO: CREATE UI/UX AND ENGINEERING SKILLS BEFORE WRITING ANY APP CODE

AI-generated UI is mediocre by default, so quality must be enforced by skills. Create these project skills first, then actually USE them.

Format: `.opencode/skills/<name>/SKILL.md` with YAML frontmatter: `name` (must equal the folder name; lowercase letters, digits, single hyphens), `description` (1-1024 chars; say exactly WHEN to load it), optional `license`, `compatibility: opencode`. Bodies must be concrete: numbers, rules, do/don't lists, checklists, small code examples. No vague essays. If you have web access, first read official Material 3 and Android design guidance (m3.material.io, developer.android.com) and any reputable open-source design/engineering skills; read them critically, merge only what is good, and never run scripts from third-party skills without reviewing them. If opencode's `skill` tool does not list the new skills, read the SKILL.md files directly and tell me to restart opencode.

USAGE RULE: before creating or editing ANY UI file, load `android-ui-ux-design` and `download-manager-ux-patterns`. After finishing each screen, run the REVIEW CHECKLIST from the skill, fix violations, and note it in docs/PROGRESS.md.

## Skill A: `android-ui-ux-design` must contain
- Visual tone: modern, clean, calm, slightly playful; rounded shapes, generous whitespace, progress is the hero. Think "Google Files clarity + pro-tool density controls + polished Material You". Original identity, not a clone of 1DM+, Gopeed or LibreTorrent.
- Principles: clarity at a glance; calm density; one primary action per screen; progressive disclosure (basics first, advanced expandable); never surprise the user (moves/renames/deletes are opt-in, previewed, undoable); honest status (never fake progress; show "Waiting for network" instead of a frozen bar); any share/paste -> download running in at most 2 taps.
- Foundations with exact tokens: Material 3 color roles + dynamic color (Android 12+) with a designed fallback palette; Light, Dark and true-black AMOLED; semantic status colors (Downloading, Queued, Paused, Completed, Failed, Seeding, Checking) harmonized with the theme and ALWAYS paired with an icon/label (never color alone); M3 type scale; tabular figures for speeds/sizes/percentages so numbers don't jitter; 4dp spacing grid (4/8/12/16/24/32); shape scale; tonal elevation; one icon family (Material Symbols) with consistent weight.
- Layout: window size classes; bottom bar (compact) -> rail (medium) -> drawer (expanded); list-detail on tablets/foldables; edge-to-edge with correct insets; primary actions in the thumb zone; max content width on large screens.
- Component specs with dimensions and all states: download card (compact/comfortable/detailed), progress indicators (linear, segmented "piece bar", ring), status chips, filter chips, speed header, FAB menu, bottom sheets, dialogs vs snackbars (rule: reversible -> snackbar with Undo; irreversible -> confirm dialog), swipe actions, multi-select toolbar, settings rows (switch/slider/choice + one-line explanation), empty states, error banners.
- Motion: 150-300 ms, M3 easing; container transform / shared axis between list and details; animate progress toward the target value (never jump); animateItem in lists; predictive back; honor "reduce motion". Use M3 Expressive components (e.g. wavy progress) only if they are in a stable Compose Material3 release.
- State design for EVERY screen: loading (skeleton), empty (short text + illustration + CTA), error (what happened + why + what to do + button), offline, permission denied, first run.
- Accessibility: contrast >= 4.5:1 (3:1 for large text/graphics), touch targets >= 48dp, TalkBack labels and progress semantics, layouts survive 200% font scale, RTL, keyboard/D-pad focus order.
- Microcopy: friendly, short, specific; error formula "what + why + fix"; formats like "1.4 GB", "3.2 MB/s", "12 min left", "Yesterday 21:40".
- Compose list performance: stable keys, immutable UI models, derivedStateOf, no recomposition storms, throttle progress updates (max ~4 Hz to the UI).
- ANTI-PATTERNS ("generic AI UI"): cramped rows, inconsistent padding, hardcoded colors, tiny text, rainbow palettes, heavy gradients, emoji as icons, dialog-for-everything, infinite spinners, truncating important info (use middle-ellipsis so the file extension stays visible), non-tabular numbers, lorem/demo data.
- REVIEW CHECKLIST per screen + screenshot loop: @Preview + JVM screenshot tests (Roborazzi or Paparazzi) in light, dark, large-font and tablet. If you can view images, critique them against this skill and iterate; otherwise ask me for device screenshots.

## Skill B: `download-manager-ux-patterns` must contain
- Speed/ETA display: exponential-moving-average smoothing, "Calculating..." until stable; unknown total size; multi-source; resumable vs not; queued vs waiting-for-network vs scheduled.
- Error taxonomy with human messages and one-tap fixes: network lost, DNS, timeout, HTTP 403/404/410 (expired link -> "Refresh link"), 429/503 (server limiting -> auto-reduce connections), disk full, storage permission revoked, checksum mismatch, path too long, torrent no peers / metadata timeout.
- Torrent explained simply: help sheets for Seeds, Peers, Ratio, Availability, DHT; a "Health" indicator (Excellent / Good / Poor / Stalled / Dead) with suggestions.
- The complete Pieces map spec (Section 5.5) and the HTTP "Segments map".
- Add-download flow rules (paste -> auto-resolve -> confirm in <= 2 taps; remembered defaults; advanced collapsed), completed-item quick actions, selection mode, list density, grouping.

## Skill C: `android-compose-engineering` must contain
MVVM + unidirectional state (immutable UiState, one-shot events via Channel/SharedFlow, state hoisting), package-by-feature, Hilt, coroutines/Flow rules (no GlobalScope, injected dispatchers, structured concurrency), Room migrations, DataStore, type-safe navigation, typed error handling, Timber logging, Gradle conventions (version catalog libs.versions.toml, KSP, R8, ABI splits, baseline profile), lint/format (ktlint or detekt + Android lint), testing strategy (unit, Turbine, Compose UI tests, screenshot tests), naming, and a definition-of-done for a feature.

## Skill D: `gopeed-core-notes` (create at the end of Phase 0, after analysing the repo; keep updating)
Real directory map, key packages (downloader, HTTP protocol, BT protocol, REST layer, mobile bind, extension engine), REST/WebSocket API cheat-sheet, task state machine, config schema, gomobile build commands, known limitations, list of planned core patches.

# 2. PRODUCT PRINCIPLES

Fast, calm, trustworthy, ad-free, no tracking. Beginner-friendly by default, power-user deep on demand. Every automatic behaviour is optional and explainable. Works fully offline except for what the user downloads.

# 3. LEGAL & LICENSING GUARDRAILS

- Repo is GPL-3.0-or-later. Keep upstream Gopeed copyright headers and license files; add NOTICE with attributions; in-app "Open source licenses" screen (AboutLibraries).
- Take feature ideas and UX behaviour from 1DM+ but never its code, name, icons, strings or artwork. LibreTorrent (GPL) may be studied and ported with attribution, but prefer clean re-implementation.
- No DRM circumvention (Widevine/PlayReady/FairPlay). AES-128 HLS without DRM is fine. No built-in torrent search/indexers and no curated links to copyrighted content; the browser homepage contains no piracy sites; show a short "use responsibly" notice at first run. Site-specific extensions are user-installed only, with a trust warning.

# 4. TECH STACK & ARCHITECTURE

## 4.1 Repo layout
  core/      Gopeed fork pinned to a release tag (git subtree or submodule; stay upstream-mergeable). Our additions live in clearly separated files/packages; every change listed in docs/CORE_PATCHES.md (why + tests).
  android/   Gradle project: :app (features, package-by-feature), :engine (Gopeed AAR wrapper, Kotlin API client, models), :designsystem (theme, tokens, reusable components incl. PiecesMap). Extract more modules only if it clearly helps.
  docs/  .opencode/skills/  scripts/  AGENTS.md  SETUP.md  README.md  LICENSE  NOTICE

## 4.2 Engine bridge (verify everything in the real repo first)
- Upstream is a Go core + Flutter UI (ui/flutter) talking over HTTP; Android packages the core with `gomobile bind` of the `bind/mobile` package into an AAR (build tags such as nosqlite, javapkg com.gopeed; read its README/CI for the exact command). Reproduce this in scripts/build-core.sh and scripts/build-core.ps1.
- Write docs/GOPEED_ANALYSIS.md: repo map; how the core starts/stops on mobile; transport (TCP loopback vs unix socket); REST + WebSocket endpoints; task and config models; extension engine; BT implementation (I believe it wraps anacrolix/torrent, confirm); what already exists for seeding config, proxy, speed limits, per-file selection. Use ui/flutter's Dart API client and i18n files as reference for models and translations.
- Kotlin `EngineClient` (OkHttp + kotlinx.serialization) mirrors the API. If the transport is a unix socket, use a LocalSocket-based SocketFactory, or configure loopback TCP with a random port + auth token. Prefer push (WebSocket/SSE) for progress; otherwise adaptive polling (1 s foreground, slower or none in background). Bind ONLY to loopback; never expose the port to other apps.
- Extend the Go core with small, tested patches for what the UI needs and Gopeed lacks: piece-state map + availability, peers list, trackers list/add/remove/re-announce, per-file priorities, sequential and first/last-piece mode, per-task speed limits, seeding goals (ratio/time), IP filter, HTTP segment/connection map, HLS/DASH fetcher, per-host connection caps, move-storage, and anything else Section 5 needs. Return compact payloads (run-length-encoded piece runs, down-sampled when huge).
- All native libraries must be 16 KB page-size aligned (check current NDK/AGP guidance and verify).

## 4.3 App stack
Kotlin, Jetpack Compose + Material 3, Hilt, Coroutines/Flow, Room, DataStore, type-safe Navigation, Coil, Media3 (preview/streaming player), WorkManager, androidx.webkit, kotlinx.serialization, Timber, AboutLibraries, Glance (widgets). R8 + resource shrinking, ABI splits + universal APK, baseline profile.

## 4.4 State ownership
The core owns transfer state. Room stores app-level metadata (categories, tags, rules, history, stats, bookmarks) keyed by task ID and reconciles on startup. Never duplicate progress bookkeeping.

## 4.5 Background execution
Foreground service that exists ONLY while there is active work (stops itself when idle; no idle background service); correct foregroundServiceType + permissions; handle the Android 15+ dataSync timeout (Service.onTimeout) and consider user-initiated data-transfer jobs (check current Android docs); WorkManager for scheduled starts (exact alarms only if the user grants them); boot receiver restores schedules; wake/Wi-Fi locks only during transfers; ConnectivityManager callbacks; battery-optimisation guidance screen including OEM auto-kill tips.

## 4.6 Storage strategy
The Go core writes to real file paths, not SAF URIs. Implement a `StorageStrategy` abstraction: (A) direct path when the target is writable by path (app-specific dir, or all-files access granted); (B) stage in app-specific external storage, then move/copy into the chosen SAF/MediaStore location on completion, with progress and failure recovery. Choose at runtime; declare MANAGE_EXTERNAL_STORAGE only in a `full` build flavor so a store-safe flavor can exist. Record the decision and test on API 29, 30, 33, 34+.

## 4.7 Budgets
Cold start < 1 s on a mid-range phone; 10,000-task list scrolls smoothly while 20 tasks update; idle = no service, no wakelock, ~0% CPU; APK <= ~40 MB per ABI (report actual).

# 5. FEATURE SPECIFICATION
(In Phase 0 expand EVERY bullet below into an ID'd row in docs/FEATURES.md and track it in PROGRESS.md.)

## 5.1 HTTP/HTTPS engine (Gopeed core)
- Multi-part downloads: 1-32 connections per task (default 8), adaptive; re-split slow segments; per-host caps; automatic fallback to a single connection when ranges are unsupported.
- Resume across app restarts, crashes and reboots, validated by ETag/Last-Modified/size; partial-file integrity.
- Retry policy: none / N / unlimited; exponential backoff with jitter or fixed custom delay; auto-resume when connectivity returns.
- Link pre-check (HEAD / Range probe) before starting: filename, size, type, resumable, redirect chain, server; warn on HTML-instead-of-file and expired/403/410 links.
- Refresh expired link: re-open the source page in the built-in browser, capture a fresh URL, replace it inside the same task.
- Request customization: headers, cookies, referer, user-agent presets, HTTP auth, POST body, saved per-host credentials (encrypted, opt-in), mirrors/fallback URLs (multi-source with checksum validation).
- Proxy: system / HTTP / SOCKS5, global and per task.
- Checksums MD5/SHA-1/SHA-256/SHA-512: manual entry or auto-detect from a sibling checksum file; verify button + badge.
- Filenames: Content-Disposition, URL-decoding, sanitizing, conflict policy (auto-rename / overwrite / ask / skip), long-name-safe truncation that keeps the extension.
- Free-space preflight and optional preallocation with clear messages.
- Batch: multi-URL paste, import/export link lists (txt), wildcard patterns like https://x.com/img[001-100].jpg, "grab links from a web page" with type filters.
- Streaming formats: HLS (m3u8, incl. AES-128 non-DRM) and DASH/CMAF with quality/audio/subtitle picker, parallel segments, merge to .ts/.mp4 (concatenate by default; optional remux with a maintained, license-compatible library: verify, note that ffmpeg-kit was retired upstream). Implement as a Gopeed-style fetcher in the core if not present.
- Gopeed extensions (JS): Extensions screen (install from Git URL/file, enable/disable, update, per-extension settings, trust warning).
- Stretch: FTP/SFTP.

## 5.2 Queues, scheduling, limits
- 1-30 simultaneous downloads (default 3); multiple named queues (Default, Night, Wi-Fi only, custom), each with concurrency, speed limit, allowed networks, time window and days; drag-and-drop order; priority High/Normal/Low.
- Global and per-task speed limits (separate upload limit for torrents); time-based limit schedule; "alt-speed" quick toggle in the header.
- Conditions: Wi-Fi only, metered/roaming toggles, pause below N% battery / when not charging, VPN change handling, Data Saver awareness, monthly mobile-data cap with warning.
- Scheduler: start/stop a download or queue at a time, repeat on days; survives reboot.
- Pause all / Resume all / Remove all (confirmation + undo); "when queue finishes": nothing / notify / close app.

## 5.3 Storage & files
- Default folder + per-category folders; folder picker with SD card/USB; free-space display; move on completion; auto-clear completed history after N days (optional).
- Private vault ("hide downloads"): app-private storage, .nomedia, biometric-locked, secure delete.
- File actions: open (FileProvider, correct MIME), open with, share file/link, rename, move, copy link, delete (task only vs task + file), show in folder, details, checksum, install APK prompt (opt-in), extract archives (zip built-in; other formats only via a license-compatible library).

## 5.4 Torrent (LibreTorrent parity and more)
- Add via magnet link, .torrent file (picker), .torrent URL, info-hash paste, clipboard, share intent, `magnet:` deep link, `application/x-bittorrent` handler; optional QR scan.
- Add-torrent sheet: magnet metadata fetch with progress and peers found; file tree with tri-state checkboxes, sizes, select all/none/invert, filter by type (Video/Audio/Docs/Subtitles), search, per-file priority (skip/low/normal/high); selected size vs free space; destination, category, tags; start now/paused/queued; sequential toggle; first/last-piece priority; skip hash check; add trackers; private-torrent indicator.
- Details screen tabs: Info (name, v1/v2 hashes, size, pieces count x piece size, creator/date/comment, save path, private flag, added/completed, ratio, totals, availability, active/seeding time) | Files (tree, per-file progress and priority, open/preview, rename) | Pieces (Section 5.5) | Peers (IP, client, flags, progress, speeds, TCP/uTP, encrypted, optional offline GeoIP flag, ban) | Trackers (URL, status, seeds/peers/leechers, last/next announce, errors; add/edit/remove; force re-announce) | Stats (speed sparklines, swarm size) | Settings (per-torrent limits, priority, seeding goal, sequential).
- Actions: pause/resume, force recheck, force announce, move storage while downloading, rename, copy magnet, export .torrent, delete with/without data.
- Global torrent settings: DHT, PeX, LSD, UPnP, NAT-PMP, uTP, encryption (off/allowed/forced), listen port (random/custom), max connections, upload slots, max active downloads/seeds, speed limits, IP filter (eMule dat / PeerGuardian from file or URL, auto-update), proxy for trackers and peers, anonymous mode, seeding rules (stop at ratio / time / never), "seed only on Wi-Fi and charging".
- Auto-move finished torrents to another folder or external storage (keep seeding via move-storage).
- Sequential download + streaming of selected media files to Media3 player or an external player.
- RSS/Atom manager with per-feed interval and include/exclude/regex filters -> auto-download into a category.
- Create torrent from file/folder: piece size auto/manual, trackers, web seeds, private flag, comment, start seeding, share .torrent/magnet.
- BitTorrent v2/hybrid support if the engine supports it; otherwise document the gap in docs/GAPS.md (do not silently drop).
- Seeding notification, health indicator and "stalled" detection with suggestions (add trackers, check port, switch network).
- If the Gopeed engine cannot reliably deliver a listed LibreTorrent feature, document it in docs/GAPS.md and propose an alternative rather than skipping it.

## 5.5 PIECES MAP (must-have showcase feature)
Purpose: show at a glance which pieces are downloaded, downloading, missing or skipped.
- Location: Torrent details -> "Pieces" tab; a compact down-sampled "piece bar" on the torrent's list card and per file in the Files tab.
- Data (core patch): total pieces, piece length, per-piece state as run-length-encoded runs, in-flight pieces, verifying pieces, availability (peers having each piece), file-to-piece-range mapping. Push/poll at ~1 Hz ONLY while the tab is visible; send RLE or diffs, never a full 100k+ array per tick.
- States (color AND pattern): Missing = outlined empty cell; Downloading = pulsing/partially filled; Downloaded = solid primary; Verifying = amber hatch; Skipped (only in deselected files) = dimmed diagonal hatch; High priority = accent border. Optional Availability heat-map mode (rare pieces highlighted).
- Header: "1,234 / 5,000 pieces (24.7%) - 2 MiB each - 6 downloading - 3 verifying - 812 skipped", legend, mode switch (State | Availability), and "contiguous from start" % (matters for streaming).
- Rendering: ONE Canvas composable (no per-cell composables). Adaptive grid; when pieces > cells, each cell aggregates N pieces and its fill = completed fraction; pinch-zoom and pan down to 1 cell = 1 piece. Smooth with 200,000 pieces.
- Interaction: tap a cell -> sheet (piece index/range, state, size, availability, files it belongs to); tap a file in Files tab -> highlight its pieces; long-press -> "Prioritise this range" if the engine allows.
- Loading/empty: "Getting metadata..." for magnets; map appears after metadata.
- Accessibility: semantic summary string + alternative text mode (list of ranges).
- Tests: unit tests for RLE->cells aggregation; screenshot tests with 10 / 5,000 / 200,000 pieces; Go tests for the endpoint.
- HTTP equivalent: a "Segments map" showing each connection's byte range, progress, speed and retries.

## 5.6 Built-in browser & media sniffer
- WebView browser: multi-tab with previews, incognito (no persisted history/cookies), bookmarks, history, search-engine choice, address bar with suggestions, desktop-site toggle, find in page, share, user-defined homepage shortcuts, long-press link/image/video -> Download / Copy link.
- Download interception (WebView DownloadListener) -> Add sheet with URL, name, size, MIME, cookies, UA, referer.
- Sniffer: shouldInterceptRequest URL/MIME patterns + early-injected JS (androidx.webkit document-start script) hooking fetch/XHR/video/source to catch mp4/webm/mp3/m4a/m3u8/mpd; verify content type with a lightweight request using the page's cookies; filter ads/trackers/tiny files; badge with count -> "Sniffed media" sheet (thumbnail, title, type, quality, size, multi-select, Download / Queue / Copy link).
- Ad blocker (host/filter-list based, user-supplied list URLs, per-site allow-list) and pop-up blocker with indicators; block auto-download prompts and intent:// redirects.
- Optional saved credentials for auto-login (encrypted vault, opt-in); per-site JS/cookie/permission controls; clear-on-exit option.
- Also integrate with other browsers via Share / Open-with / intent filters.

## 5.7 SMART FEATURES
Every smart feature MUST: have its own toggle in Settings -> Smart features (plus a master switch and "Reset to defaults"); include a one-line explanation with an example; show an "Auto" chip on affected items; be logged in the Activity log; be undoable when it changes files. Safe defaults: labels/suggestions ON; anything that MOVES, RENAMES or DELETES files OFF until the user opts in (offer it in onboarding with a live preview). All logic is on-device: no cloud or AI services.
 S1 Smart Categories: classify by extension, MIME, filename patterns/regex, domain, size, source app, torrent content. Defaults: Videos, Music, Documents, Archives, Apps, Images, Subtitles, eBooks, Torrents, Other. User can add/edit/reorder/delete categories (name, icon, color, default folder, rules). Shows confidence; user overrides teach the engine ("You moved 3 .mkv files to TV Shows -> create a rule?"). Modes: Off / Suggest / Auto.
 S2 Auto-sort on completion: destination template with tokens {category} {ext} {domain} {year} {month} {day} {source} {tag} {torrent_name} {series} {season}; per-category overrides; live path preview; conflict policy; "Organize existing files" tool (scan folder -> proposed moves -> apply selected); Undo toast (10 s) + log; torrents keep seeding after moves. Global on/off + per-category on/off.
 S3 Smart rename: strips junk tags/site names/underscores/tracking query strings; optional title-case and date prefix; preview; editable rules.
 S4 Duplicate detection: by URL, name+size, torrent info-hash, and content hash after completion; "Already downloaded (date) -> Open / Download again / Skip"; optional auto-skip.
 S5 Smart clipboard: detect links/magnets when the app comes to foreground (Android blocks background clipboard reads); non-intrusive chip; batches; remember ignored links; plus a Quick Settings tile and notification action.
 S6 Smart conditions: Wi-Fi-only, charging-only, battery threshold, night window, metered/roaming rules, mobile-data cap; one-tap "Download later on Wi-Fi".
 S7 Adaptive connections & speed: auto-tune segments per server, back off on 429/503, per-host memory of what worked.
 S8 Link health + auto-heal: probe before start; classify errors; auto-apply safe fixes (retry with backoff, fewer connections, re-resolve, switch mirror); "Fix it" card for the rest.
 S9 Storage manager: free-space guard, storage insights (by category/largest/oldest), cleanup suggestions (manual approval), auto-extract archives (+ optional delete archive), delete .torrent after adding, APK install prompt (opt-in).
 S10 Smart notifications: quiet hours, batching, hide tiny files, failures only, per-category sound/vibration.
 S11 Torrent helpers: skip junk files (nfo/txt/sample/promo), auto-add public trackers from a user-supplied list URL (refreshable), auto-sequential for single-video torrents, seed goals + auto-remove after goal, low-seed warning.
 S12 Smart search: instant search across name/URL/tag/category + saved filters ("Large videos this week").
 S13 Insights: weekly summary, best time to download from history, average speed by network type (on-device only).
 S14 Adaptive quick actions after completion (Open / Share / Move / Rename / Delete) by file type.

## 5.8 Automation rules ("If this then that")
- Triggers: added, started, completed, failed, network changed, time. Conditions: domain/URL regex, category, size, type, source, network type, battery, time window. Actions: set category/folder/tags/queue/priority/speed limit/connections, rename by template, move, notify, pause/resume, extract, ask me.
- Sentence-style visual rule builder, template gallery, dry-run tester ("would this rule match X?"), enable/disable, ordering + conflict resolution, JSON import/export. S1-S3 run on top of this engine.

## 5.9 Extras
- Glance widgets (active downloads + speed + pause all; quick add), Quick Settings tiles, static + dynamic shortcuts, share targets.
- Backup/restore (settings, categories, rules, task list, bookmarks; optional password encryption), app lock (biometric/PIN), local activity log with share/redact.
- Statistics: downloaded per day/week, speed over time, storage by category, top domains.
- User-triggered update check (off by default).
- Stretch: Android TV/leanback, Firefox-for-Android extension via the local API (off by default).

## 5.10 YOU MUST ADD MORE
Think like a power user AND a beginner. In Phase 0 write docs/FEATURE_IDEAS.md with at least 20 additional ideas beyond this brief (value / effort / risk). Examples only (invent better ones): watch-folder for .torrent files, QR share/scan of links and magnets, send-to-another-device on LAN, remembered per-domain settings, auto-pause when the device is hot, battery-friendly torrent mode, play-while-downloading preview, link history and re-download, speed test, "download later" inbox, keyboard shortcuts for tablets/desktop mode. Implement the best ones in Phase 6; each optional, with a toggle, and none may compromise core quality.

# 6. UI/UX SPECIFICATION (follow Skills A and B)

6.1 Global: Material 3; dynamic color + fallback palette; Theme = System / Light / Dark / AMOLED; accent picker when dynamic color is off; edge-to-edge; predictive back; adaptive navigation; portrait, landscape, foldable; haptics on key actions (respect system setting); all strings localized (no hard-coded text); RTL-safe.
6.2 Navigation (compact): Downloads | Browser | Activity | Settings. Torrents appear in the Downloads list (type chip + badge); details screens differ by type.
6.3 Downloads (home):
 - Top bar with search (name, URL, tag, category), sort (date, name, size, progress, speed, status), density (Compact / Comfortable / Detailed).
 - Sticky speed header: total down/up speed, active/queued counts, 60-second sparkline, alt-speed toggle, Pause all / Resume all.
 - Filter chips: All, Active, Queued, Paused, Completed, Failed, Seeding + category chips + type (HTTP / Torrent / Stream) + saved filters.
 - List: sections Active, then Completed grouped Today / Yesterday / This week / Earlier (toggle to flat list). Card: 48dp rounded tile (thumbnail or type icon on category color), title (2 lines, middle-ellipsis), meta line ("312 MB of 1.2 GB - 4.1 MB/s - 3 min left" / "1.2 GB - example.com - Yesterday" / error line in error color), 6dp rounded animated progress (segmented piece bar for torrents), trailing primary action (pause/resume/retry/open) + overflow. Configurable swipe actions with Undo; long-press = multi-select (pause, resume, delete, move, category, tag, copy links, share); drag handle to reorder queued items.
 - FAB "Add" -> FAB menu: Paste link, Torrent file, Magnet, Batch/Import, From clipboard; long-press = paste-and-start.
 - Empty state: illustration + "Paste a link" + "Open browser".
6.4 Add-download sheet (full-height, keyboard aware): multi-line URL field with clipboard suggestion; auto-resolve (skeleton -> filename, size, type icon, resumable badge, server); editable name; folder chooser (last used / per-category / SD) with free space; auto-detected category chip; connections stepper; collapsible Advanced (headers, cookies, referer, UA, auth, checksum, speed limit, queue, schedule, start paused, mirrors, proxy); primary "Download", secondary "Queue"; multiple URLs -> checklist of resolved items; inline errors.
6.5 HTTP task details: hero progress + speed graph; facts grid (size, downloaded, speed, ETA, connections, resumable, added, category, path, URL with copy); Segments map; action bar; logs/headers tab; "Fix it" card on error.
6.6 Torrent details and add sheet: as in 5.4 and 5.5.
6.7 Browser: as in 5.6; address bar, tabs sheet, sniffed-media badge, blocker indicators.
6.8 Activity: charts, auto-action log with Undo, insights cards (only when Insights is on).
6.9 Settings: searchable, grouped (General, Downloads, Network, Storage & Organization, Smart features, Torrent, Browser, Notifications, Appearance, Privacy & Security, Backup & Restore, Advanced, About). Each setting: clear title, one-line description, sensible default; destructive ones confirmed; "Reset section to defaults". Document all in docs/SETTINGS.md.
6.10 Onboarding (<= 3 skippable screens): value; download folder + theme; optional smart features with examples and live preview. Permissions asked just-in-time with a rationale, never all at once.

# 7. NOTIFICATIONS & SYSTEM INTEGRATION

- Channels: Active downloads (low, silent, ongoing), Completed (default), Failed/Action needed (high), Seeding (low), Scheduled/queue (low). Android 13+ POST_NOTIFICATIONS flow.
- Per-task ongoing notifications with progress + Pause/Resume/Cancel; group summary ("3 downloading - 8.2 MB/s"); completion with Open/Share/Delete; failure with Retry/Fix. Update at most ~1/s; quiet hours; per-category sound/vibration; hide for files under X MB.
- Intent filters: http/https VIEW for downloadable types, `magnet:`, `application/x-bittorrent` / .torrent, ACTION_SEND and SEND_MULTIPLE (text/URLs), PROCESS_TEXT ("Download with APP_NAME"). Never auto-start from an external intent unless the user enabled it; otherwise open the Add sheet.
- Permissions minimal and documented in docs/PERMISSIONS.md: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE (+ data-sync type), WAKE_LOCK, RECEIVE_BOOT_COMPLETED; optional: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, MANAGE_EXTERNAL_STORAGE (full flavor only), REQUEST_INSTALL_PACKAGES, USE_BIOMETRIC.

# 8. SECURITY & PRIVACY

- No ads, analytics or telemetry. Network calls only when the user triggers them (plus optional user-enabled tracker-list / IP-filter / update checks). Logs stay local; "share logs" redacts URLs and tokens.
- Core endpoint: loopback only, random port, auth token; components not exported unless required; validate all incoming intents.
- Sanitize filenames/paths from servers and torrents (block path traversal, absolute paths, reserved names); zip-slip protection when extracting.
- Credentials/cookies vault encrypted with Android Keystore; opt-in; wipeable.
- Network security config: cleartext allowed only for user-supplied download URLs; TLS validation never silently disabled ("allow invalid certificates" = explicit per-task advanced opt-in with warning).
- App lock, private vault, optional screen-capture blocking; backups exclude secrets unless password-encrypted.

# 9. QUALITY BAR

- Tests: Go tests for every core patch; Kotlin unit tests for categorizer, auto-sort templating, rules engine, duplicate detection, storage strategy, ETA smoother, RLE piece aggregation; Turbine flow tests; Compose UI tests (Add flow, list, selection); screenshot tests for key screens (light/dark/large font/tablet); an offline integration harness with a local HTTP server (range support, slow, flaky, redirects, expiring links) and a local torrent seeder/swarm to test magnet -> metadata -> pieces -> completion without internet.
- CI (GitHub Actions): build the AAR, run Go + Kotlin tests, lint (Android lint + ktlint/detekt), assemble APKs; release workflow with signing via secrets.
- Accessibility pass (TalkBack, 200% font, contrast), i18n pass (English complete, RTL check, per-app language via locales_config, reuse Gopeed translations where useful and license-compatible).
- Performance: baseline profile, R8, verify Section 4.7 budgets with a 10,000-task synthetic dataset.
- Docs: README (features, build, screenshot placeholders), ARCHITECTURE, CORE_PATCHES, GAPS, FEATURES/PROGRESS, SMART_FEATURES (every toggle explained), SETTINGS, PERMISSIONS, PRIVACY, MANUAL_TEST_PLAN, CONTRIBUTING, third-party licenses.

# 10. PHASED PLAN
Definition of Done for EVERY phase: builds, tests pass, docs + PROGRESS.md updated, skill review checklist run for UI, committed.

Phase 0 Foundations: skills A-C; environment check; repo layout, AGENTS.md, docs/FEATURES.md (all Section 5 bullets as ID'd rows), docs/FEATURE_IDEAS.md; pin Gopeed into core/; docs/GOPEED_ANALYSIS.md; skill D; scripts/build-core; Gradle skeleton + CI.
Phase 1 Engine bridge / walking skeleton: AAR builds; EngineClient; foreground service; one HTTP file and one magnet torrent download end-to-end on a minimal screen; Room + DataStore wired; smoke tests.
Phase 2 Design system + HTTP MVP: theme/tokens/components; Downloads list; Add sheet; task details; notifications; settings v1; queue basics; storage strategies A/B; retry/resume; pause/resume all.
Phase 3 Organization & control: categories, auto-sort (preview + undo), file actions, queues + scheduler, speed limits, network/battery policies, batch import/export, checksums, duplicates, clipboard, share/deep links, private vault, app lock.
Phase 4 Torrent parity: add flow, file selection, details tabs, Pieces map (core patches + Canvas renderer), peers/trackers, seeding rules, full torrent settings, sequential/streaming, RSS, create torrent, health/stalled logic.
Phase 5 Browser & streams: WebView browser, tabs/bookmarks/history/incognito, ad/pop-up blocking, media sniffer, HLS/DASH + quality picker + merge, refresh-expired-link flow, extension manager.
Phase 6 Smart & automation: rules engine + UI, smart rename, smart notifications, insights, storage cleaner, adaptive connections, auto-heal, torrent helpers, top items from FEATURE_IDEAS.md.
Phase 7 Polish & release: widgets/tiles/shortcuts, backup/restore, onboarding, accessibility + i18n + performance passes, baseline profile, docs, release build, MANUAL_TEST_PLAN.

# 11. FINAL ACCEPTANCE (all true, or explicitly PARTIAL with reasons)

- Every row in docs/FEATURES.md is DONE, or PARTIAL/BLOCKED with an explanation.
- README build instructions work from a clean clone; APKs (ABI splits + universal) build.
- Manual test plan passes: (1) 1 GB file, 8 connections, kill the app mid-way, resume; (2) magnet -> metadata -> pick files -> Pieces map fills live -> sequential preview plays; (3) auto-sort with preview + undo; (4) browser sniffs a video on a test page and downloads it; (5) schedule + Wi-Fi-only respected; (6) rotation/fold/tablet layouts; (7) TalkBack quick pass; (8) 10,000 tasks stay smooth.
- Every smart feature has an on/off switch, explanation and safe default; master switch and "Reset to defaults" work.

# 12. START NOW

Begin immediately: (1) docs/BRIEF.md, (2) create the skills from Section 1, (3) environment check, (4) Phase 0, then continue through the phases without waiting for me, printing a short report at the end of each phase.

# 13. WORKING FOLDER (applies to EVERYTHING above)

13.1 Project root = /root/work/ANDROID (capital letters; Linux paths are case-sensitive, so never use /root/work/android). Every relative path in this brief (docs/, core/, android/, scripts/, .opencode/, AGENTS.md ...) is relative to it. Examples: /root/work/ANDROID/docs/BRIEF.md, /root/work/ANDROID/core/ (Gopeed fork), /root/work/ANDROID/android/ (the Gradle project), /root/work/ANDROID/.opencode/skills/, /root/work/ANDROID/.opencode/agents/, /root/work/ANDROID/AGENTS.md.
13.2 First commands: `mkdir -p /root/work/ANDROID && cd /root/work/ANDROID`. If this folder is not itself a git repository root, run `git init` (opencode finds project skills, agents and AGENTS.md from the working directory up to the git root). If no git identity is set, configure one repo-locally only; never change global git config. Create .gitignore for Android/Gradle/Go/IDE files plus: .toolchain/, .logs/, dist/, local.properties, *.jks, *.keystore, .cxx/, .kotlin/, build/. Do NOT ignore .opencode/ (skills and agents are part of the project).
13.3 Stay inside. Create, edit and delete files ONLY inside /root/work/ANDROID; never touch other folders in /root/work/ or anywhere else. You are probably running as root: never run recursive deletes/chmod/chown with wildcards or variables that could expand outside the project, and double-check the absolute path before any recursive delete.
13.4 Toolchains live inside too (fewer out-of-workspace permission prompts, clean machine, cleanup = delete one folder). Anything not already installed system-wide goes under /root/work/ANDROID/.toolchain/ (gitignored, so search tools skip it): Android SDK (cmdline-tools, platform-tools, build-tools, latest platform, NDK) in .toolchain/android-sdk; GRADLE_USER_HOME in .toolchain/gradle-home; GOPATH/GOCACHE in .toolchain/go; JDK 17+ and Go tarballs from official sources if missing. Write scripts/env.sh that exports JAVA_HOME, ANDROID_HOME/ANDROID_SDK_ROOT, GRADLE_USER_HOME, GOPATH, GOCACHE and PATH; every build script sources it; document it in SETUP.md. local.properties (sdk.dir) is generated per machine and never committed. If a suitable system-wide JDK or Go exists, just use it.
13.5 Never hard-code /root/work/ANDROID in source, Gradle or CI files. Scripts resolve paths relative to their own location so the repo builds on any machine or CI. The absolute path appears only in SETUP.md as "current working folder".
13.6 Machine checks (extends 0.7): assume a headless Linux server, no display, probably no emulator. Run `uname -m`, `nproc`, `free -h`, `df -h /root/work` and check for /dev/kvm. Need roughly 15 GB free disk and >= 6 GB RAM; tune org.gradle.jvmargs and org.gradle.workers.max to the machine. Without /dev/kvm do NOT try to run an emulator: rely on JVM unit tests, Robolectric (also for Compose UI tests) and JVM screenshot tests (Roborazzi/Paparazzi), and give me APKs to install by hand. If the CPU is aarch64/arm64 (e.g. Termux/proot on a phone), warn me BEFORE investing time: Google's Android build tools have historically been x86_64-only on Linux (verify), so APK builds there may need workarounds. In that case do code, Go and JVM tests locally and rely on the GitHub Actions workflow (Section 9) for APKs.
13.7 Installing: everything from official sources may be installed without asking; log each install in SETUP.md (what, version, source URL, path). ONE exception: accepting the Android SDK licences is a legal agreement, so ask me once, then run the acceptance yourself. Where 13.4-13.7 differ from 0.7, follow 13.
13.8 Outputs: after each successful build copy the APKs (debug and release; ABI splits + universal) to /root/work/ANDROID/dist/ as <APP_NAME>-<version>-<abi>-<debug|release>.apk plus a SHA256SUMS file, and tell me the exact paths so I can copy them to my phone (scp/adb). Debug builds use the default debug key. Never commit dist/ or keys; document release signing in docs/RELEASE.md and ask me before generating a release keystore.
13.9 All commands are non-interactive and run from /root/work/ANDROID (or a subfolder). Long jobs (first Gradle build, gomobile, NDK downloads) run in the background with output logged to /root/work/ANDROID/.logs/ and are polled, so tool timeouts don't kill them.

# 14. SUB-AGENTS (you MUST use them: they keep your context clean and let independent work run in parallel)

14.1 Roles. You (the main agent, staying in the `build` agent) are the LEAD: you plan, split work, write delegation briefs, integrate results, resolve conflicts, keep docs/PROGRESS.md and the todo list, run full builds/tests, and make all git commits. Subagents work in their own child sessions and return a short report. Only the lead delegates; subagents never spawn subagents.
14.2 Built-in subagents (available in every opencode version): `explore` (fast, read-only codebase/web exploration) and `general` (multi-step research and edits; it has no todo tool). If your version also has `scout` (read-only upstream/dependency source research), use it for Gopeed and anacrolix/torrent. Use these for one-off tasks that don't need a custom role.
14.3 Custom subagents: create them in Phase 0 right after the skills, as Markdown files in `.opencode/agents/<name>.md` (older opencode versions used `.opencode/agent/`; check the docs for the installed version at opencode.ai/docs/agents and use the documented folder and frontmatter syntax). Minimum frontmatter: `description` (say exactly WHEN to use it) and `mode: subagent`. Add tool/permission limits with the syntax your version documents. Do NOT hard-code a `model` (inherit mine). If the agents don't show up, tell me to restart opencode; if custom agents are unsupported, use `general` and paste the role text into each brief. Each file's body is a focused system prompt: skills to load first, paths it owns, report format.
  - `go-core-engineer`: owns core/ and scripts/build-core*. Go patches to Gopeed (piece map, peers, trackers, seeding goals, HLS/DASH fetcher ...), gomobile/AAR build, Go tests, docs/CORE_PATCHES.md. Loads gopeed-core-notes.
  - `kotlin-engineer`: owns non-UI Kotlin under android/ (:engine, data, domain, services, storage strategy, smart + rules engines, notifications, browser/sniffer logic). Loads android-compose-engineering and gopeed-core-notes.
  - `compose-ui-designer`: owns :designsystem and all screen/UI code. MUST load android-ui-ux-design and download-manager-ux-patterns before any UI change, builds @Preview + screenshot tests, runs the review checklist, and reports any violation it could not fix.
  - `qa-tester`: owns test source sets, the offline integration harness (local HTTP server, local torrent swarm) and docs/MANUAL_TEST_PLAN.md. Writes/runs tests and reports failures with reproduction steps; touches production code only to add test hooks, and says so.
  - `code-reviewer` (read-only, no edits): reviews each finished slice against this brief, the skills' checklists, Section 8 (security), Section 3 (licensing) and Section 4.7 (budgets); returns findings by severity with file:line and a suggested fix.
14.4 When to delegate: (a) research/analysis that would flood your context (Gopeed analysis, API and version checks, log or stack-trace triage); (b) independent implementation slices touching DIFFERENT files; (c) an independent review + tests after EVERY feature slice (fresh eyes). Do NOT delegate tiny edits or tightly coupled changes; subagents cost tokens too.
14.5 Delegation brief (must be self-contained; a subagent does not see this conversation): goal and why; docs/skills/files to read first; the exact paths it OWNS (may edit) and must NOT touch; acceptance criteria; commands it may and may not run; report format (<= 300 words: what changed, files, tests run + results, open issues or BLOCKED + options). Long findings go into a docs/*.md file; the report links to it instead of pasting it.
14.6 Parallel rules: at most 3 subagents at once; parallel subagents own disjoint paths. Contract first: write docs/ENGINE_API.md (endpoints, JSON models, the piece-map RLE format) BEFORE splitting Go and Kotlin/UI work so both sides build against it. Only ONE process runs Gradle at a time (the lead runs full builds/tests after integrating). Only the lead runs git commit/merge. Sample data is allowed only in @Preview and test source sets.
14.7 Trust but verify: never mark a feature DONE because a subagent said so. Read the diff, run the build + relevant tests, and run the UI review checklist yourself for UI work. If a subagent returns BLOCKED or partial work: re-brief it with more context, split the task smaller, or do it yourself. Record in PROGRESS.md which subagent implemented and which reviewed each feature.
14.8 Suggested parallel plan (adapt as needed):
  - Phase 0: up to three `explore`/`scout` runs at once to analyse Gopeed (HTTP engine + task lifecycle | BT engine + piece-API feasibility | REST/mobile bind/extensions + Flutter API client + i18n), each writing its part of docs/GOPEED_ANALYSIS.md. When one finishes, start `general` to verify the latest stable dependency versions (AGP, Kotlin, Compose BOM, Hilt, Room, Media3, WorkManager, androidx.webkit, Glance) and current Android rules (foreground-service types/timeouts, storage access, 16 KB pages) -> docs/DEPENDENCIES.md.
  - Phase 1: `go-core-engineer` (AAR + build scripts) | `kotlin-engineer` (Gradle skeleton, EngineClient against ENGINE_API.md, service) | `qa-tester` (offline harness).
  - Phases 2-3: `compose-ui-designer` (design system + screens against agreed UiState contracts) | `kotlin-engineer` (repositories, ViewModels, storage, queues, categories, auto-sort) | `qa-tester`; then `code-reviewer`.
  - Phase 4: `go-core-engineer` (piece/peer/tracker endpoints) | `compose-ui-designer` (PiecesMap Canvas renderer + torrent screens against the RLE contract) | `qa-tester` (local swarm tests).
  - Phase 5: `go-core-engineer` (HLS/DASH fetcher) | `kotlin-engineer` (browser + sniffer logic) | `compose-ui-designer` (browser + sniffed-media UI).
  - Phase 6: `kotlin-engineer` (rules + smart engines) | `compose-ui-designer` (rules builder, Activity, Insights) | `qa-tester`.
  - Phase 7: parallel passes for accessibility review, i18n extraction, performance profiling and docs, then a final `code-reviewer` audit of the whole repo.

# 15. FINAL START (supersedes Section 12)

Do this now, in this order: (1) 13.2: create/enter /root/work/ANDROID, git init, .gitignore; (2) make sure this brief is at docs/BRIEF.md; (3) create skills A-C (Section 1); (4) create the custom subagents (14.3); (5) machine checks and toolchain setup (0.7 + 13.4-13.7; the only question you may ask me is the SDK licence acceptance); (6) create AGENTS.md so it repeats the key rules of Sections 13 and 14 (working folder, stay-inside rule, scripts/env.sh, who runs Gradle and git, delegation brief format) and survives context resets; (7) run Phase 0 with parallel subagents (14.8). Then continue through the phases without waiting for me. After every phase print a short report that includes the paths of any new APKs in /root/work/ANDROID/dist/.


also important things do not build apk your tasks is only to write code in /root/work/ANDROID i will use GitHub action to create android application (i am running opencode in termux proot debain)