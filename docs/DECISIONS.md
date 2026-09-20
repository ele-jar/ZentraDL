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
