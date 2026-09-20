---
name: download-manager-ux-patterns
description: Load BEFORE creating or editing ANY download-related UI or logic (Downloads list, Add sheet, task details, torrent screens, Pieces map, browser sniffer, queues, scheduler, errors, notifications). Download-manager UX rules for ZentraDL.
license: GPL-3.0-or-later
compatibility: opencode
---

# Download-Manager UX Patterns — ZentraDL

## 1. Speed / ETA display

- Smooth speed with exponential moving average (α=0.3 over 1s samples).
  Show "Calculating…" until ≥3 samples or 3s. Never show wildly jumping ETA.
- Unknown total: show "12.4 MB downloaded · 3.1 MB/s" with indeterminate
  trailing segment, never a fake %.
- States vocabulary (exact labels): Downloading, Queued (n of m),
  Waiting for network, Waiting for Wi-Fi, Scheduled (starts 22:00),
  Paused, Seeding, Checking, Completed, Failed — with reason.
- Resumable badge: "Resumable" / "Not resumable" on Add sheet + details.
- Multi-source: "from 3 mirrors". Queued vs waiting-for-network must be
  visually distinct (chip + banner, not just color).
- Update speeds/ETA at ≤4 Hz; sparkline = 60 samples @1 Hz.

```kotlin
// GOOD: EMA + stable state
val speedEma = remember { SpeedEma(alpha = 0.3f) }
Text(if (!speedEma.stable) "Calculating…" else "${speedEma.value}/s")
```

## 2. Error taxonomy (exact message + one-tap fix)

| Cause | Message | Fix button |
|---|---|---|
| Network lost | "Paused — no connection. Resumes automatically." | — (auto) |
| DNS | "Couldn't reach server (DNS). Check the URL or network." | Retry |
| Timeout | "Server stopped responding." | Retry |
| HTTP 403/404/410 | "Link expired or removed (404). Refresh the link." | Refresh link |
| HTTP 429/503 | "Server is limiting us — slowed to N connections." | Details |
| Disk full | "Not enough space — need 1.2 GB, have 300 MB free." | Free space |
| Storage revoked | "Lost access to the folder. Pick it again." | Pick folder |
| Checksum mismatch | "File failed checksum — may be corrupt." | Re-download |
| Path too long | "Filename too long — shortened, extension kept." | Rename |
| Torrent no peers | "No peers yet (health: Stalled). Try more trackers." | Add trackers |
| Metadata timeout | "Couldn't fetch torrent info in 60s." | Retry |

- Every error card: what + why + fix. "Fix it" card on details screen.
- Auto-heal safe fixes silently (backoff, fewer connections, mirror switch)
  and log them; ask for the rest.

## 3. Torrents explained simply (help sheets)

- Seeds = people sharing complete file; Peers = everyone connected;
  Ratio = uploaded ÷ downloaded; Availability = copies of rarest piece.
- Health indicator (exact): Excellent (seeds≥10, avail≥5) / Good (seeds≥3) /
  Poor (1–2 seeds) / Stalled (0 seeds, peers>0) / Dead (0 seeds, 0 peers).
  Each shows a suggestion ("Add trackers", "Check port", "Try Wi-Fi").
- Never show raw flags (`uTP`, `PEX`) without a tap-to-explain tooltip.

## 4. Pieces map (showcase — full spec)

- Location: Torrent details → "Pieces" tab; compact down-sampled piece bar
  on torrent list card + per-file in Files tab.
- States (color AND pattern — never color alone):
  Missing = outlined empty; Downloading = pulsing/partial fill;
  Downloaded = solid primary; Verifying = amber hatch;
  Skipped = dimmed diagonal hatch; High priority = accent border.
  Availability mode = heat-map (rare pieces highlighted).
- Header: "1,234 / 5,000 pieces (24.7%) · 2 MiB each · 6 downloading ·
  3 verifying · 812 skipped", legend, mode switch (State|Availability),
  "contiguous from start %" (streaming relevance).
- Rendering: ONE `Canvas` composable, adaptive grid. pieces>cells →
  aggregate N pieces/cell, fill = completed fraction. Pinch-zoom + pan down
  to 1 cell = 1 piece. Must stay smooth at 200,000 pieces (no per-cell
  composables, RLE input, down-sample in ViewModel).
- Data contract: `{ total, pieceLength, runs: [[stateIdx,count]…], inFlight,
  verifying, availability? }` at ~1 Hz ONLY while tab visible. RLE or diffs,
  never full 100k+ arrays per tick.
- Interaction: tap cell → sheet (index/range, state, size, availability,
  files); tap file → highlight its pieces; long-press → "Prioritise range"
  (if engine allows).
- Loading: "Getting metadata…" for magnets. Accessibility: semantic summary
  + text-mode alternative (list of ranges).

## 5. HTTP Segments map

- Per-connection rows: `#3  41–82 MB  ▓▓▓▓░░ 62%  1.1 MB/s  2 retries`.
- On 429/503 show "backed off to N connections" inline.

## 6. Add-download flow (≤2 taps share/paste → running)

1. Paste/clipboard/share → auto-resolve with skeleton (filename, size, type
   icon, resumable badge, server/host).
2. Confirm sheet: editable name, folder (free space shown), category chip
   (auto, confidence shown), connections stepper (1–32, default 8),
   Advanced collapsed. Primary "Download", secondary "Queue".
3. Multiple URLs → checklist of resolved items with per-item errors inline.
4. Remember defaults (folder per category, connections per host).
5. Inline errors, never a blocking dialog for a fixable field.

## 7. Completed-item quick actions

By type: video → Play/Share; audio → Play; image → View; apk → Install
(opt-in); archive → Extract; document → Open. Always: Open, Share file,
Copy link, Move, Rename, Delete (task vs task+file), Details.

## 8. Selection mode, density, grouping

- Long-press → multi-select (count + Pause/Resume/Delete/Move/Category/Tag/
  Copy links/Share). Drag handle reorders queued items.
- Density: Compact/Comfortable/Detailed (persisted). Grouping: Active first,
  then Completed grouped Today/Yesterday/This week/Earlier (flat-list toggle).

## 9. REVIEW CHECKLIST

- [ ] Speed EMA + "Calculating…", ETA stable, unknown-size handled?
- [ ] Errors use taxonomy table (what+why+fix, one-tap button)?
- [ ] Torrent help sheets + Health indicator with suggestion?
- [ ] Pieces map = single Canvas, RLE, 1 Hz only when visible, legend +
      summary + text alternative?
- [ ] Add flow ≤2 taps, skeleton resolve, Advanced collapsed, remembered
      defaults, inline errors?
- [ ] Swipe actions + Undo, selection toolbar complete?
