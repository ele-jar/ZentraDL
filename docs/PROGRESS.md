# PROGRESS.md — ZentraDL (one row per feature; TODO/DOING/DONE/PARTIAL/BLOCKED)

> Updated by lead after every slice. Full catalogue: `docs/FEATURES.md`.
> UI rows: reviewer initials = skill checklists run.

| ID | Name | Status | Notes |
|---|---|---|---|
| P0 | Foundations (skills, agents, env, AGENTS.md, Gradle skeleton, CI) | DONE | CI green 2026-09-20 (D010 Kotlin-only pivot; D011 sdk37.2) |
| P1 | Engine bridge, Kotlin edition (OkHttp engine, Room/DataStore/Hilt, FGS, skeleton screen, E2E) | DONE | CI green 2026-09-20: 17 tests (MockWebServer range/resume/fallback, repo lifecycle), debug+release APKs |
| P2a | Design system (theme, status colors, cards, chips, states + Previews) | DONE | CI green 2026-09-20. UI checklist: M3 roles only ✓, 4dp grid/48dp/middle-ellipsis/monospace-tabular ✓, progress animated 300ms ✓, TalkBack labels+stateDescription ✓, strings via params ✓, sample data in @Preview only ✓. Gaps → P2b/c: full 6-state screens, reduce-motion gate, 200%-font/RTL device pass, screenshot tests → P7 |
| P1-old | (superseded by D010; was: AAR/EngineClient) | DONE | Replaced by Kotlin-edition P1 above |
| P2b | Downloads list (search/filter/density, speed header, FAB) + queue basics (maxRunning, pause/resume all) | DONE | CI green 2026-09-20. List checklist: search/sort(5)/density/filter-chips/speed-header+pause-resume-all ✓, stable keys+animateItem ✓, section headers ✓, undo-delete+confirm-file-delete ✓, AddSheet (clipboard suggestion/resolve skeleton/rename/conns/Download+Queue) ✓, FileProvider open ✓. Deferred: sparkline+alt-speed (P2c), sticky header, swipe actions, multi-select (P3), tnum+i18n (P7) |
| P2c | Add sheet + task details + notifications upgrade + settings v1 | DONE | CI green 2026-09-20. Download-patterns checklist: EMA+Calculating ✓, ETA/unknown-size ✓, error taxonomy + Fix-it card ✓, segments map ✓, add ≤2 taps ✓. UI checklist (details): roles/grid/strings/edge-to-edge ✓, loading+error states ✓, TalkBack back-label ✓. Notifications: 3 channels, per-task + summary (1 Hz), Open/Share/Retry actions ✓. Gaps → later: offline banner + denied/first-run states, swipe/selection (P3), sparkline/alt-speed, sticky header, details @Preview, quiet hours (P6), full i18n (P7) |
| P3 | Organization & control | TODO | — |
| P4 | Torrent parity + Pieces map | TODO | — |
| P5 | Browser & streams (HLS/DASH, sniffer, extensions) | TODO | — |
| P6 | Smart & automation (S1-S14 + rules + FEATURE_IDEAS best) | TODO | — |
| P7 | Polish & release (widgets, backup, onboarding, a11y/i18n/perf, docs) | TODO | — |
