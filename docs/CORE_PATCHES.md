# CORE_PATCHES.md — engine deltas (D010: Go core dropped, history below)

> The Gopeed Go core (`core/` submodule) was removed per user instruction
> (D010, 2026-09-20). The CP1–CP10 Go-patch table is void; this file now tracks
> the equivalent Kotlin-engine deltas in `:engine`.

| # | Delta | Why (§5) | Status |
|---|---|---|---|
| K1 | OkHttp multi-part HTTP (1–32 conns, resume, 3x retry, 4 Hz, seg trackers) | H1–H4 | DONE (P1/P2) |
| K2 | Global token-bucket speed cap (`SpeedLimiter`) | Q2 | DONE (P3d; HTTP only — bt has no caps API) |
| K3 | Free-space preflight before first byte | H11 | DONE (P6c; HTTP only) |
| K4 | HLS variant picker + AES-128 TS fetcher | H13 | DONE (P5b; DASH merge = gap) |
| K5 | bt 1.10 wrapper: isolated runtimes, file selection, sequential, RLE pieces | T1–T7/PM1 | DONE (P4; see `docs/BT_API.md`) |
| K6 | Refresh-link in place (re-queue, partial kept) | H5 | DONE (P5c) |
| K7 | SHA-256 verify card for completed files | H9-lite | DONE (P3c; no sibling auto-detect) |

Deferred core work (see `docs/GAPS.md`): mirrors/fallback URLs, ETag validators,
per-host backoff memory, DASH merge, per-torrent rate caps, FTP/SFTP.
