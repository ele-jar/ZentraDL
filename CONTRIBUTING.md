# CONTRIBUTING.md — ZentraDL

- Kotlin + Compose + Material3; Conventional Commits (`feat/fix/docs/test/refactor:`).
- One Room migration per schema change (never destructive); DataStore for prefs.
- Skills live in `.opencode/skills/` — load `android-ui-ux-design` +
  `download-manager-ux-patterns` before any UI work, `android-compose-engineering`
  before any Kotlin work; run the review checklists, note them in `docs/PROGRESS.md`.
- Tests with every slice (JUnit + Truth + Turbine; MockWebServer/loopback swarm
  for transfers); `gradle testDebugUnitTest lint` must be green.
- No stubs as done: mark PARTIAL/BLOCKED with reason + alternative in PROGRESS.md.
- No demo data in prod paths; Timber only; sanitize paths/intents; GPL headers kept.
- APKs come from CI — never commit `dist/`, keys, or `local.properties`.
