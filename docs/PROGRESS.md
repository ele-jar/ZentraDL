# PROGRESS.md — ZentraDL (one row per feature; TODO/DOING/DONE/PARTIAL/BLOCKED)

> Updated by lead after every slice. Full catalogue: `docs/FEATURES.md`.
> UI rows: reviewer initials = skill checklists run.

| ID | Name | Status | Notes |
|---|---|---|---|
| P0 | Foundations (skills A-C, agents, env, AGENTS.md, Gradle skeleton, CI, Gopeed pin+analysis, FEATURES catalogue) | DOING | Skeleton+docs+pin committed 2026-09-20; first CI run FAILED at setup-android (`tools` pkg obsolete) → fixed (manual SDK step, java v5, gradle 9.6.0 pin) + versions re-verified (Kotlin 2.3.21/KSP 2.3.12/AGP 9.4.1, D005) + real MainActivity; awaiting 2nd CI run |
| P1 | Engine bridge / walking skeleton (AAR, EngineClient, FGS, 1 HTTP + 1 magnet E2E) | TODO | Needs ENGINE_API.md contract first |
| P2 | Design system + HTTP MVP | TODO | — |
| P3 | Organization & control | TODO | — |
| P4 | Torrent parity + Pieces map | TODO | — |
| P5 | Browser & streams (HLS/DASH, sniffer, extensions) | TODO | — |
| P6 | Smart & automation (S1-S14 + rules + FEATURE_IDEAS best) | TODO | — |
| P7 | Polish & release (widgets, backup, onboarding, a11y/i18n/perf, docs) | TODO | — |
