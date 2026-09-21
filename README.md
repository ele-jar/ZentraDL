# ZentraDL — fast, calm, trustworthy open-source Android download manager

`com.elejar.ZentraDL` · GPL-3.0-or-later · minSdk 26 · ABIs arm64-v8a/armeabi-v7a/x86_64.

Multi-part HTTP with resume, BitTorrent with a live Pieces map (via
[atomashpolskiy/bt](https://github.com/atomashpolskiy/bt) 1.10, Apache-2.0),
HLS stream capture, a WebView browser with ad-block and a media sniffer,
categories, queues, scheduler gates, and on-device automation rules — all in
Kotlin, Material You UI. No ads, no tracking.

## Features (see `docs/FEATURES.md`, status in `docs/PROGRESS.md`)

- **HTTP**: 1–32 connections, resume, retry, refresh-expired-link, checksums, batch import/export.
- **Torrents**: magnet/.torrent/URL, metadata fetch, file selection, sequential mode, seeding with goals, DHT/trackers, recheck, move storage, export.
- **Browser**: tabs, bookmarks/history, download interception, media sniffer with HLS quality picker, AES-128 HLS capture, host ad-block.
- **Control**: categories + auto-sort rules, queues, Wi-Fi/charging/schedule gates, global speed cap, private vault, app lock, duplicates, share intents, QS tile, shortcuts.
- **Smart** (all on-device, logged, undoable): automation rules with preview, smart rename, quiet hours, activity insights, storage cleaner, plaintext backup/restore.

## Build (CI; local builds need JDK 17 + SDK — see SETUP.md)

```bash
source scripts/env.sh
gradle assembleDebug          # APKs (CI; NOT on termux host)
gradle testDebugUnitTest lint # unit tests + lint
```

APKs come from GitHub Actions artifacts (`build` workflow: debug + release
ABI splits + universal). See `docs/RELEASE.md` for signing/outputs.

## Docs

`docs/BRIEF.md` (spec) · `ARCHITECTURE.md` · `FEATURES.md`/`PROGRESS.md` ·
`SETTINGS.md` · `SMART_FEATURES.md` · `BT_API.md` (torrent lib contract) ·
`PERMISSIONS.md` · `PRIVACY.md` · `MANUAL_TEST_PLAN.md` · `RELEASE.md` ·
`GAPS.md` (deferred items, never silent) · `DECISIONS.md` · `DEPENDENCIES.md`.

Key third-party: atomashpolskiy/bt (Apache-2.0), OkHttp, Room, DataStore,
Hilt, Media3, Coil, Guava (via bt), slf4j (NOP on device).
