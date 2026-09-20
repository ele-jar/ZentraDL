# CORE_PATCHES.md — our Go changes on Gopeed v1.9.3 (keep upstream-mergeable)

> Every patch: new files only + Go tests + this entry (why). Status in PROGRESS.md.

| # | Patch | Why (§5) | Status |
|---|---|---|---|
| CP1 | HTTP `StatsConnection` += ranges + per-conn speed | PM3 Segments map | TODO (Phase 2) |
| CP2 | Resume validators (ETag/Last-Modified/size) + checksum verify | H2/H9 | TODO (Phase 3) |
| CP3 | Retry modes + exp-backoff/jitter; per-host caps + memory | H3/Q2/S7 | TODO (Phase 3) |
| CP4 | Piece-detail RLE endpoint (`pieces=` flags, down-sample) | PM1 | TODO (Phase 4) |
| CP5 | Tracker CRUD + re-announce + per-tracker stats; peer ban hook | T3/T4 | TODO (Phase 4) |
| CP6 | Per-file priority post-create via PATCH | T2 | TODO (Phase 4) |
| CP7 | Sequential / first-last-piece streaming mode | T7 | TODO (Phase 4) |
| CP8 | Per-task speed limits; per-torrent seed-goal override; net toggles + port | Q2/T5 | TODO (Phase 4) |
| CP9 | HLS (AES-128 non-DRM) + DASH fetcher + merge | H13 | TODO (Phase 5) |
| CP10 | Mirrors/fallback + checksum validation | H7 | TODO (Phase 3) |

Go: `cd core/upstream && go vet ./... && go test ./...`. Build: `scripts/build-core.sh`.
