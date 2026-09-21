# RELEASE.md — signing & outputs

Debug builds use the default debug key. Release signing: keystore NEVER
committed. Provide via CI secrets (`ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`)
consumed by a future `release` workflow job. Ask the maintainer before
generating a release keystore. Outputs: `dist/<APP>-<version>-<abi>-<type>.apk`
+ `SHA256SUMS` (see `scripts/collect-apks.sh`). On this termux host: no local
builds — fetch APKs from GitHub Actions artifacts.

Current: version `0.1.0` (versionCode 1), minSdk 26, target 36, compile 37.
Release APKs are currently **unsigned** (`app-release-unsigned.apk` + splits);
R8 + resource shrinking on, keeps for bt/Guice in `proguard-rules.pro`
(device behavior of minified Guice paths needs on-device confirmation —
debug builds don't minify and are the test path).
Pre-release gate: `docs/MANUAL_TEST_PLAN.md` all green + `docs/GAPS.md`
reviewed + `docs/PROGRESS.md` has no TODO row.
