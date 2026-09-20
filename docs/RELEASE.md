# RELEASE.md — signing & outputs

Debug builds use the default debug key. Release signing: keystore NEVER
committed. Provide via CI secrets (`ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`)
consumed by a future `release` workflow job. Ask the maintainer before
generating a release keystore. Outputs: `dist/<APP>-<version>-<abi>-<type>.apk`
+ `SHA256SUMS` (see `scripts/collect-apks.sh`). On this termux host: no local
builds — fetch APKs from GitHub Actions artifacts.
