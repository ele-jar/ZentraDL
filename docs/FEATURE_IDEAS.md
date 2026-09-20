# FEATURE_IDEAS.md — 20+ extra ideas (§5.10; implement best in Phase 6, all optional + toggled)

| # | Idea | Value | Effort | Risk | Notes |
|---|---|---|---|---|---|
| 1 | Watch-folder for .torrent/.txt link files | H | S | L | Auto-import + optional delete source |
| 2 | QR share/scan for links + magnets | H | S | L | Camera opt-in; complements T1 |
| 3 | Send-to-device on LAN (QR-paired, token auth) | H | M | M | Reuses TCP+token path; loopback default stays |
| 4 | Per-domain remembered settings (conns/UA/headers/queue) | H | S | L | Extends S7 memory |
| 5 | Auto-pause when device hot (thermal API) | M | S | L | ThermalManager; toggle |
| 6 | Battery-friendly torrent mode (pause seeding <X%, unmetered-only) | H | S | L | Extends S6/S11 |
| 7 | Play-while-downloading preview (sequential + Media3 progressive) | H | M | M | Needs T7 first |
| 8 | Link history + one-tap re-download | H | S | L | Room table keyed by URL hash |
| 9 | Built-in speed test (single/multi-conn vs cache-busted endpoint) | M | S | L | User-triggered, no tracking |
| 10 | "Download later" inbox (parked links w/ conditions) | H | S | L | Feeds S6/Q4 |
| 11 | Keyboard shortcuts (tablets/desktop mode, Ctrl+F etc.) | M | S | L | Focus-order dependent |
| 12 | Subtitle auto-grab for downloaded videos (OpenSubtitles opt-in) | M | M | M | External call only when enabled |
| 13 | Checksum-file (.sfv/.md5) import + batch verify | M | S | L | Extends H9 |
| 14 | Torrent tracker health cache (which trackers worked per info-hash) | M | S | L | App-side stats |
| 15 | "Merge duplicate mirrors" suggestion (same file, different URLs) | M | M | L | Extends S4 + H7 |
| 16 | Scheduled speed profiles (e.g. unlimited night, 1 MB/s day) | M | S | L | Extends Q2 |
| 17 | Per-download notes field (searchable) | L | XS | L | Room column; cheap win |
| 18 | Sleep timer ("pause all in 30 min") | M | XS | L | Simple alarm; no exact-alarm need |
| 19 | Export task list as HTML/CSV report | L | XS | L | Share sheet; redaction toggle |
| 20 | Auto-rename TV episodes (S01E02 from common patterns) | M | M | M | Extends S3; preview mandatory |
| 21 | Folder sync ("mirror finished Sports/ to USB when attached") | M | M | M | SAF volume watcher; preview + undo |
| 22 | Quick-compare completed vs source size/date before overwrite | M | S | L | Safety for H10 overwrite mode |
| 23 | Widget-configurable "one-tap retry failed" button | M | S | L | Extends X1 |
| 24 | "What slowed me?" per-task diagnosis (server limit vs network vs disk) | H | M | L | Speed-vs-conn evidence card; great trust builder |

Phase 6 picks top value/effort (1,2,4,6,7,8,10 recommended first). None may
compromise core quality; each ships toggled + explained + logged.
