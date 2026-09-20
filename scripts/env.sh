#!/usr/bin/env bash
# scripts/env.sh — toolchain env for ZentraDL.
# Resolves repo root relative to this script; never hard-codes the absolute path.
# Safe on machines without local toolchains (e.g. Termux/proot): warns, continues.
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Gradle / Go homes inside repo (gitignored)
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.toolchain/gradle-home}"
export GOPATH="${GOPATH:-$ROOT/.toolchain/go}"
export GOCACHE="${GOCACHE:-$ROOT/.toolchain/go-cache}"
mkdir -p "$GRADLE_USER_HOME" "$GOPATH" "$GOCACHE" 2>/dev/null || true

# JDK
if [ -z "${JAVA_HOME:-}" ]; then
  for c in "$ROOT/.toolchain/jdk-"* /usr/lib/jvm/java-17-openjdk-* /usr/lib/jvm/java-21-openjdk-*; do
    if [ -x "$c/bin/java" ]; then export JAVA_HOME="$c"; break; fi
  done
fi

# Android SDK: prefer env, then repo-local, then $HOME/Android/Sdk
if [ -z "${ANDROID_HOME:-}" ]; then
  for c in "$ROOT/.toolchain/android-sdk" "$HOME/Android/Sdk" "$HOME/Android/sdk" /opt/android-sdk /usr/lib/android-sdk; do
    if [ -d "$c/platform-tools" ] || [ -d "$c/cmdline-tools" ]; then export ANDROID_HOME="$c"; break; fi
  done
fi
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [ -n "${ANDROID_HOME:-}" ]; then
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
else
  echo "[env.sh] No Android SDK found (ANDROID_HOME unset). CI builds APKs; see SETUP.md." >&2
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "[env.sh] No JDK found (JAVA_HOME unset). Code-only mode; see SETUP.md." >&2
else
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if command -v go >/dev/null 2>&1; then export PATH="$PATH:$(go env GOPATH 2>/dev/null)/bin"; fi
export PATH="$ROOT/scripts:$PATH"
