# PROGRESS.md — ZentraDL (one row per feature; TODO/DOING/DONE/PARTIAL/BLOCKED)

> Updated by lead after every slice. Full catalogue: `docs/FEATURES.md`.
> UI rows: reviewer initials = skill checklists run.

| ID | Name | Status | Notes |
|---|---|---|---|
| P0 | Foundations (skills, agents, env, AGENTS.md, Gradle skeleton, CI) | DOING | D010 pivot: Go dropped, Kotlin-only. AGP9 needs kotlin-android removed (build error msg is explicit) — applied; awaiting CI run |
| P1 | Engine bridge, Kotlin edition (OkHttp multi-part engine in :engine, FGS, Room/DataStore, 1 HTTP download E2E; torrent client deferred to P4) | TODO | Starts after CI green |
| P1 | Engine bridge / walking skeleton (AAR, EngineClient, FGS, 1 HTTP + 1 magnet E2E) | TODO | Needs ENGINE_API.md contract first |
| P2 | Design system + HTTP MVP | TODO | — |
| P3 | Organization & control | TODO | — |
| P4 | Torrent parity + Pieces map | TODO | — |
| P5 | Browser & streams (HLS/DASH, sniffer, extensions) | TODO | — |
| P6 | Smart & automation (S1-S14 + rules + FEATURE_IDEAS best) | TODO | — |
| P7 | Polish & release (widgets, backup, onboarding, a11y/i18n/perf, docs) | TODO | — |
