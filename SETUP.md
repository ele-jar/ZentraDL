# SETUP — ZentraDL (com.elejar.ZentraDL)

Current working folder: `/root/work/ANDROID` (project root; never hard-code
this path in source/Gradle/CI — scripts resolve relative to their own location).

## 1. Machine check (2026-09-20, this Termux/proot Debian host)

- `uname -m`: **aarch64** (phone running Termux proot Debian)
- `nproc`: 6 · `free -h`: 5.5G total / ~852M available (swap heavily used)
- `df -h /root/work`: 111G total, **only ~3.7G free** (97% used)
- `/dev/kvm`: not accessible → **no emulator possible**
- Installed: git 2.47.3, python3 3.13, curl, unzip
- Missing: JDK, Go, gomobile, Gradle, Android SDK, adb

### Decision (per BRIEF 13.6 + user instruction)

> "Do not build apk, only write code in /root/work/ANDROID. I will use
> GitHub Action to create the android application (running in termux proot debian)."

Plus: aarch64 Linux + 3.7G free disk + <1G free RAM means a local Android
SDK + NDK + Gradle build is infeasible here (needs ~15G free, ≥6G RAM,
and Google's build tools are x86_64-first on Linux). So:

- **No toolchain installed locally.** Nothing under `.toolchain/` on this host.
- All code is written here; **APKs/AARs build in GitHub Actions**
  (`.github/workflows/`). That is the supported build path on this machine.
- Verification here = `python3` checks, file-level review, and (once a full
  machine is available) Gradle/Go builds. No emulator: JVM unit tests +
  Robolectric + Roborazzi/Paparazzi screenshot tests only.

## 2. scripts/env.sh

Every build script sources it:

```bash
source "$(dirname "$0")/env.sh"
```

It exports (when present, otherwise warns and continues):
`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `GRADLE_USER_HOME`
(`<root>/.toolchain/gradle-home`), `GOPATH`/`GOCACHE`
(`<root>/.toolchain/go`), and prepends SDK `cmdline-tools`, `platform-tools`
and Go binaries to `PATH`. `local.properties` (`sdk.dir`) is generated per
machine and never committed.

## 3. Full dev machine setup (Ubuntu 22.04+ x86_64, 15G+ free, 8G+ RAM)

Exact commands (official sources only):

```bash
# JDK 17 (Temurin, official Adoptium API)
sudo apt update && sudo apt install -y wget unzip curl git
wget https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.11_9.tar.gz -O /tmp/jdk17.tar.gz
mkdir -p ~/.toolchain && tar xzf /tmp/jdk17.tar.gz -C ~/.toolchain
export JAVA_HOME=~/.toolchain/jdk-17.0.11+9

# Go (official go.dev, check go.mod for required version — Gopeed needs Go >=1.22)
wget https://go.dev/dl/go1.23.0.linux-amd64.tar.gz -O /tmp/go.tar.gz
sudo tar -C /usr/local -xzf /tmp/go.tar.gz
export PATH=$PATH:/usr/local/go/bin
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init

# Android SDK cmdline-tools (official dl.google.com)
mkdir -p ~/Android/Sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip
unzip /tmp/cmdtools.zip -d ~/Android/Sdk/cmdline-tools
mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
export ANDROID_HOME=~/Android/Sdk ANDROID_SDK_ROOT=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
yes | sdkmanager --licenses   # <-- LEGAL AGREEMENT: run yourself, then tell opencode
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125"
```

Then in repo root: `source scripts/env.sh && ./gradlew assembleDebug`.

## 4. Android SDK licence

Accepting SDK licences is a legal agreement — **you** must accept once:

```bash
yes | sdkmanager --licenses
```

After that, opencode runs all further commands non-interactively.

## 5. Install log

| Date | What | Version | Source | Path | By |
|---|---|---|---|---|---|
| 2026-09-20 | (none — intentionally skipped local toolchain; CI builds) | — | — | `.toolchain/` (empty) | opencode lead |

## 6. Outputs

Successful CI builds publish APKs as workflow artifacts AND copy to
`dist/` when built locally as
`<APP_NAME>-<version>-<abi>-<debug\|release>.apk` + `SHA256SUMS`.
On this host: no local APKs — fetch them from GitHub Actions.
Debug builds use the default debug key; release signing documented in
`docs/RELEASE.md` (keystore never committed; ask before generating).
