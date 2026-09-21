# GAPS.md — known gaps (never silent; mapped to alternative or phase)

| Gap | Status | Alternative / note |
|---|---|---|
| BitTorrent v2/hybrid surfacing | Open | bt 1.10 is v1-focused; v2 torrents ingest where bt accepts them, no v2-specific UI |
| DASH fragmented-mp4 merge | Open | Progressive direct files work; DASH picker refused with message |
| HLS resume / parallel segments | Open | Sequential restart; segments are small, resume rarely matters |
| Torrent per-file priority mid-run | Open | bt supports NORMAL↔HIGH only, no re-skip; set selection before start |
| Torrent re-announce button | Open | Restarting the session re-announces effectively (recheck) |
| Torrent rename | Open | Torrent names are storage identity; use HTTP downloads for renamable files |
| RSS manager / torrent creator UI | Open | bt supports creation (`TorrentBuilder`); UI deferred |
| .torrent mime-handler from other apps | Open | In-app picker + URL + magnet covered |
| Saved credentials vault / per-site settings | Open | No credential storage at all (see PRIVACY.md) |
| Tags | Open | Categories + search cover organization |
| Named queues | Open | Single FIFO gate + maxRunning covers phone use |
| Per-task speed limits / battery-% gate | Open | Global cap + charging-only toggle cover the common cases |
| Glance widget | Open | QS tile + shortcuts + share targets ship; Glance 1.2 API drifted, revisit with docs |
| Password-encrypted backup | Open | Plaintext JSON with user warning |
| Baseline profile / 10k scroll proof | Open | 10k-row list logic unit-tested; on-device perf pass still needed |
| TV/leanback, Firefox extension | Stretch | Only if core quality allows |
| FTP/SFTP | Stretch | No compatible lib found |
| Gopeed JS extensions (H14) | Dropped | No Go core since D010; no JS runtime on device |
| LibreTorrent gaps (T12) | N/A | No LibreTorrent dependency; own bt client instead |

Resolved this phase: ST1 auto-clear history, H11 preflight, X5 update check,
X2 backup, X1 tile+shortcuts, U10 onboarding, locales config.
