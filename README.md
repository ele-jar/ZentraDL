# ZentraDL — fast, calm, trustworthy open-source Android download manager

`com.elejar.ZentraDL` · GPL-3.0-or-later · minSdk 26 · ABIs arm64-v8a/armeabi-v7a/x86_64.

1DM+-style multi-part HTTP + sniffer browser, LibreTorrent-style torrents with a live
Pieces map, Gopeed Go-core engine (pinned `v1.9.3` in `core/`), optional on-device smart
automation, polished Material You UI. No ads, no tracking.

## Build (CI; local builds need JDK17 + SDK — see SETUP.md)

```bash
source scripts/env.sh
gradle testDebugUnitTest lint                        # (CI uses gradle, no wrapper jar yet)
gradle assembleDebug assembleRelease
bash scripts/collect-apks.sh                         # dist/*.apk + SHA256SUMS
```

APKs: GitHub Actions artifacts (`build` workflow). Docs: `docs/BRIEF.md`,
`docs/FEATURES.md` + `docs/PROGRESS.md`, `docs/ARCHITECTURE.md`,
`docs/ENGINE_API.md`, `docs/GOPEED_ANALYSIS.md`, `SETUP.md`, `AGENTS.md`.
