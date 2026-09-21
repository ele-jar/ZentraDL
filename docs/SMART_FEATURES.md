# SMART_FEATURES.md — ZentraDL (every toggle explained; §9 docs)

> All smart behavior is on-device, opt-in per feature, logged, and undoable.
> Moves/renames/deletes never happen unless a rule you added says so.
> Torrent sessions are never moved or renamed (they keep seeding in place).

| Toggle | Default | What it does |
|---|---|---|
| Smart automation (master) | on | Lets rules run. With an empty rule list (ship state) nothing happens. |
| Rules (list) | empty | Each rule: trigger (on add / on complete) + conditions (extension, min size, host, name regex) + actions (move to category, rename). Templates: Sort videos, Sort music, Clean filenames. |
| Rule preview | — | Dry-run against finished downloads; lists would-change rows, changes nothing. |
| Rules export / import | — | Plain-text blocks (`trigger:`/`when:`/`do:`), shareable; import parses the same format. |
| Auto-action log (Activity) | — | Every automatic move/rename with time; Undo restores file + record. Keeps last 200. |
| Quiet hours | off | Completed/failed notifications go silent inside the window (default 22:00–07:00). |
| Failures only | off | Skips “download complete” notifications; failures still notify. |
| Hide tiny files | off | Skips “complete” notifications for files under 1 MB. |
| Wi-Fi only | off | Holds the queue on metered connections (“Waiting for Wi-Fi”). |
| Charging only | off | Holds the queue while on battery. |
| Schedule window | off | Downloads run only inside the daily window (“Scheduled · starts …”). |
| Speed limit | unlimited | Global download cap (HTTP; torrents manage their own peers). |
| Auto-sort + rename | via rules | Covered above. Name templates: `{name}` `{date}` `{ext}` + junk cleanup. |
| Storage cleanups | manual | Approved actions only: clear completed history (records), delete failed leftovers (files). Free-space preflight fails fast with “Not enough space”. |

Deferred with reasons: adaptive per-server connections (needs core caps patch), silent
auto-heal (Fix-it card covers the UX; auto-fix stays manual), torrent trackers refresh,
saved search filters, weekly insights beyond counts, per-type quick actions, sentence
builder gallery, RSS, torrent creation UI.
