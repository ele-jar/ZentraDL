# PROGRESS.md — ZentraDL (one row per feature; TODO/DOING/DONE/PARTIAL/BLOCKED)

> Updated by lead after every slice. Full catalogue: `docs/FEATURES.md`.
> UI rows: reviewer initials = skill checklists run.

| ID | Name | Status | Notes |
|---|---|---|---|
| P0 | Foundations (skills, agents, env, AGENTS.md, Gradle skeleton, CI) | DONE | CI green 2026-09-20 (D010 Kotlin-only pivot; D011 sdk37.2) |
| P1 | Engine bridge, Kotlin edition (OkHttp engine, Room/DataStore/Hilt, FGS, skeleton screen, E2E) | DONE | CI green 2026-09-20: 17 tests (MockWebServer range/resume/fallback, repo lifecycle), debug+release APKs |
| P2a | Design system (theme, status colors, cards, chips, states + Previews) | DONE | CI green 2026-09-20. UI checklist: M3 roles only ✓, 4dp grid/48dp/middle-ellipsis/monospace-tabular ✓, progress animated 300ms ✓, TalkBack labels+stateDescription ✓, strings via params ✓, sample data in @Preview only ✓. Gaps → P2b/c: full 6-state screens, reduce-motion gate, 200%-font/RTL device pass, screenshot tests → P7 |
| P1-old | (superseded by D010; was: AAR/EngineClient) | DONE | Replaced by Kotlin-edition P1 above |
| P2b | Downloads list (search/filter/density, speed header, FAB) + queue basics (maxRunning, pause/resume all) | DOING | Queue engine green 2026-09-20 (FIFO gate, pause/resume-all, delete, Format helpers, 9 new tests). Real bug found by hang: cancel() ignored queued waiters → rewritten cancel/pauseAll paths. List UI next |
| P2c | Add sheet + task details + notifications upgrade + settings v1 | TODO | After P2b |
| P3 | Organization & control | TODO | — |
| P4 | Torrent parity + Pieces map | TODO | — |
| P5 | Browser & streams (HLS/DASH, sniffer, extensions) | TODO | — |
| P6 | Smart & automation (S1-S14 + rules + FEATURE_IDEAS best) | TODO | — |
| P7 | Polish & release (widgets, backup, onboarding, a11y/i18n/perf, docs) | TODO | — |
