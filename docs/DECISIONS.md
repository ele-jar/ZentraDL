# DECISIONS.md — ZentraDL (3-line notes, newest last)

## D001 (2026-09-20) — docs/BRIEF.md canonical; root BRIEF.MD kept as received
Root `BRIEF.MD` was the attached file; copied verbatim to `docs/BRIEF.md`.
`docs/BRIEF.md` is canonical going forward; root file left untouched.

## D002 (2026-09-20) — Code-only on termux/proot host; CI builds APKs
Host is aarch64 + 3.7G free + <1G RAM + no KVM/JDK/Go/SDK. Per BRIEF 13.6 and
user instruction, no local toolchain/APK builds; GitHub Actions is the build
path. Verified via uname/nproc/free/df + missing java/go/adb.

## D003 (2026-09-20) — core/upstream shallow-pinned to v1.9.3 (a5cd53f)
`git clone --depth 1 --branch v1.9.3` (8.5 MB) registered via .gitmodules;
`go.mod` confirms go 1.24.9. Shallow keeps the 3.7G-free disk safe; CI clones
recursive. `--branch` on `submodule add` failed (tag vs branch), plain clone +
manual .gitmodules worked.

## D004 (2026-09-20) — CI calls `gradle`, not `./gradlew`
No wrapper jar in repo (binary, would need download); `gradle/actions/setup-gradle`
provides Gradle 9.6.0 in CI. AGENTS.md + README updated; wrapper may come in Phase 1.

## D005 (2026-09-20) — Kotlin 2.3.21 + KSP 2.3.12 (not 2.4.20), AGP 9.4.1
First CI run failed before compiling: `android-actions/setup-android` installs
obsolete `tools` package (exit 1) → replaced with manual sdkmanager step.
While fixing, verified every version against Maven metadata: KSP has NO 2.4 line
(latest 2.3.12, new scheme), so Kotlin 2.4.20 would have broken KSP/Hilt/Room.
Downgraded Kotlin → 2.3.21; AGP → 9.4.1; added activity-compose 1.13.0; real
minimal MainActivity + themes.xml so the skeleton compiles. Re-upgrade Kotlin
when KSP 2.4.x ships.

## D006 (2026-09-20) — compileSdk 36, not 37
CI: `platforms;android-37` not found. Checked Google's repository2-1.xml
directly: stable platforms stop at android-36, `android-37` appears 0 times
(only build-tools 37 exists). NDK 29.0.14206865 confirmed present. If a future
dep demands minCompileSdk 37, revisit the BOM instead.

## D007 (2026-09-20) — pin x/mobile to v0.0.0-20260209203831-923679eb55af
CI: `go install .../gomobile@latest` pulled Sep-2026 mobile (needs go>=1.26,
toolchain auto-switched) and new gomobile demands x/mobile in the target
module's graph (go.dev/issue/77183). Fix: pin pre-1.25-bump mobile (go 1.24.0,
proxy-verified), install from it, add ephemeral `-tool gobind` dep inside
core/upstream at build time (never committed; submodule pin stays pristine).
Re-pin when our Go toolchain moves to 1.25/1.26.

## D008 (2026-09-20) — `go get -tool` MUST carry @version
CI: bare `go get -tool <pkg>` re-resolved x/mobile to latest mid-build
(go 1.26.0 module, toolchain switch) while the gomobile binary stayed pinned —
skewed build, bind died with a bogus ENOENT on the AAR path. Single pinned
`go get -tool <pkg>@<ver>` command; script now also re-mkdirs out dir and
fails loudly with `ls` if the AAR is missing.

## D009 (2026-09-20) — resolve script --out to absolute (bind cwd differs)
CI trace showed `-o android/app/libs/...` relative: workflow passes a relative
--out, bind cd's into core/upstream, AAR landed in the submodule. Force absolute.

## D011 (2026-09-20) — compileSdk 37 via minor-versioned platform (supersedes D006)
D006 was wrong: stable channel DOES ship API 37 as `platforms;android-37.0` /
37.1 / **37.2** (+betas) — my repository2-1.xml read missed them (separate
addon index). AGP's "for example 37.2" hint was literal. Install 37.2 +
build-tools 37.0.0; targetSdk stays 36 (Play floor).

## D010 (2026-09-20) — DROP Go/gomobile: Android-only, Kotlin-only (user instruction)
gomobile cost 5 failed CI rounds; user ordered pure Kotlin. Removed: `core/`
Gopeed submodule, `core-aar` CI job, Go setup/NDK from workflow, build-core
scripts. `:engine` is now a pure-Kotlin transfer engine (OkHttp multi-part
HTTP now; JVM BitTorrent client in Phase 4 — replaces anacrolix; candidates
TBD). `docs/GOPEED_*.md` = history only. This supersedes BRIEF §4.1/§4.2 (Go core).
