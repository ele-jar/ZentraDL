# AGENTS.md — ZentraDL (read at session start)

Project: **ZentraDL** (`com.elejar.ZentraDL`, GPL-3.0-or-later) — open-source
Android download manager (1DM+ convenience + LibreTorrent torrent power +
Gopeed Go-core engine + optional on-device smart automation + polished M3 UI).
Target: Android 8.0+ (minSdk 26), latest stable compile/target, ABIs
arm64-v8a, armeabi-v7a, x86_64. Full spec: `docs/BRIEF.md`.
Status: `docs/PROGRESS.md` (one row per feature, TODO/DOING/DONE/PARTIAL/BLOCKED).
Decisions: `docs/DECISIONS.md`. Setup: `SETUP.md`.

## Architecture (brief 4.x)

- `core/` — Gopeed fork pinned to a release tag (submodule; stay
  upstream-mergeable; our patches in separate files, listed in
  `docs/CORE_PATCHES.md`). Go core + REST/WebSocket API + extensions.
  Built to AAR via `gomobile bind` (`scripts/build-core.sh`).
- `android/` — Gradle project: `:app` (features, package-by-feature),
  `:engine` (AAR wrapper + `EngineClient` OkHttp/serialization + models),
  `:designsystem` (theme/tokens/components incl. PiecesMap Canvas).
- Core owns transfer state (loopback only + auth token). Room stores app
  metadata (categories/tags/rules/history/stats) keyed by taskId.
- Contract first: `docs/ENGINE_API.md` (endpoints, JSON, piece-map RLE)
  before splitting Go/Kotlin/UI work.

## Working folder rules (brief 13 — binding)

- Project root is `/root/work/ANDROID` (capital). All relative paths in the
  brief are relative to it. Current host is Termux/proot Debian aarch64 —
  **code only here, NO local APK builds**; APKs/AARs build in GitHub Actions.
- Stay inside: create/edit/delete ONLY inside project root. Never
  `rm -rf`/chmod/chown with wildcards or expandable variables. Check absolute
  path before any recursive delete. Never hard-code the root in source/Gradle/CI.
- Toolchains (if ever installed locally) go under `.toolchain/` (gitignored).
  Every build script sources `scripts/env.sh` (JAVA_HOME, ANDROID_HOME,
  GRADLE_USER_HOME, GOPATH/GOCACHE, PATH). `local.properties` never committed.
- Outputs: local builds copy APKs to `dist/` as
  `<APP_NAME>-<version>-<abi>-<debug|release>.apk` + `SHA256SUMS`; never commit
  `dist/` or keys. Here: fetch APKs from CI artifacts.

## Session start

1. Read this file + `docs/PROGRESS.md`, continue where it stopped.
2. Use todo tool for current phase. One `in_progress` at a time.
3. Never fake it: no stubs as done, no demo data in prod paths, no silent drops
   → mark PARTIAL/BLOCKED with reason + alternative.

## UI rule (blocking)

**Always load `android-ui-ux-design` and `download-manager-ux-patterns`
before ANY UI work** (create/edit of screens, components, theme, navigation,
widgets). After each screen run the REVIEW CHECKLIST and note it in PROGRESS.md.
Engineering rules: `android-compose-engineering`. Engine facts: `gopeed-core-notes`.

## Subagents (brief 14 — lead = build agent)

- Built-in: `explore`/`scout` (read-only research), `general` (edits).
- Custom (`.opencode/agents/`): `go-core-engineer` (core/),
  `kotlin-engineer` (non-UI Kotlin), `compose-ui-designer` (UI),
  `qa-tester` (tests/harness), `code-reviewer` (read-only audits).
- Only the lead delegates; subagents never spawn subagents. Max 3 parallel,
  disjoint paths. Only ONE process runs Gradle at a time (the lead runs full
  builds/tests after integrating). Only the lead commits (Conventional Commits).
- Delegation brief must be self-contained: goal+why; docs/skills/files to read
  first; paths OWNED vs MUST-NOT-touch; acceptance criteria; commands may/may-not
  run; report ≤300 words (changed, files, tests+results, BLOCKED+options;
  long findings → docs/*.md link).
- Trust but verify: read the diff, run build+tests, run UI checklist yourself
  before marking DONE. Record implementer + reviewer in PROGRESS.md.

## Build / test commands

```bash
source scripts/env.sh
gradle assembleDebug          # full APK (CI; NOT on termux host; no wrapper jar yet — CI's setup-gradle provides gradle)
gradle testDebugUnitTest lint # unit + lint
(cd core && go vet ./... && go test ./...)  # Go patches
```

## Coding rules (short)

Conventional Commits; ktlint/detekt + Android lint clean; Room migrations for
every schema change; DataStore (no new SharedPreferences); Hilt; no GlobalScope;
Timber (redact URLs/tokens); sanitize paths/intents; GPL headers + NOTICE kept;
16 KB page alignment for native libs; R8 + ABI splits + baseline profile.
