# DEPENDENCIES.md — ZentraDL verified stable versions & platform rules

Verified 2026-09-20 via official sources (developer.android.com, Maven Central/Google Maven, release notes).
Do NOT upgrade past these without re-verifying. minSdk 26 throughout.

## Toolchain / SDK

| Library | Verified stable version | Source URL | Notes |
|---|---|---|---|
| AGP | 9.4.1 (Sep 2026) | https://developer.android.com/build/releases/agp-9-4-0-release-notes | Requires Gradle 9.6.0, JDK 17; max API 37. Minimum for Compose 1.12 stack is 9.2.0 |
| Gradle | 9.6.0 (pinned via `gradle-version` in CI; no wrapper jar in repo) | https://services.gradle.org/distributions/ | setup-gradle installs it; 9.7.1 is current but 9.6.0 is AGP 9.4's floor |
| Kotlin | 2.3.21 (Sep 2026) | https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml | DOWNGRADED from 2.4.20: KSP has no 2.4 line (latest 2.3.12). Re-upgrade when KSP 2.4.x ships |
| Compose compiler plugin | 2.3.21 (`org.jetbrains.kotlin.plugin.compose`) | https://mvnrepository.com/artifact/org.jetbrains.kotlin.plugin.compose/org.jetbrains.kotlin.plugin.compose.gradle.plugin | Ships with Kotlin; version == Kotlin version |
| KSP | 2.3.12 (new versioning scheme; latest 2026-09-09) | https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml | No 2.4.x exists — this pins Kotlin to 2.3.x (D005) |
| compileSdk | 36 (max stable platform published; verified 2026-09-20 in repository2-1.xml — NO android-37 exists, only build-tools 37) | https://dl.google.com/android/repository/repository2-1.xml | D006: was 37 (wrong). If a dep errors minCompileSdk 37, revisit BOM choice |
| targetSdk | 36 (Android 16, Baklava) | https://developer.android.com/about/versions/16/setup-sdk + https://targetsdk.com/ | Play requires target ≥36 for new apps/updates since Aug 31, 2026; target 37 optional |
| build-tools | 36.0.0 | https://developer.android.com/build/releases/agp-9-4-0-release-notes | AGP 9.4 default |
| NDK | r29 stable (29.0.14206865); AGP default 28.2.13676358; LTS r27d | https://developer.android.com/ndk/downloads | Pin `ndkVersion`; r28+ emits 16 KB-aligned ELF by default; ≤r27 needs `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` |

## Libraries

| Library | Verified stable version | Source URL | Notes |
|---|---|---|---|
| Compose BOM | 2026.08.00 (Compose 1.12, Aug 12, 2026) | https://developer.android.com/develop/ui/compose/bom + https://mvnrepository.com/artifact/androidx.compose/compose-bom/2026.08.00 | Requires compileSdk 37 + AGP ≥9.2.0; compiled with Kotlin 2.3.21 plugin (D005) |
| activity-compose | 1.13.0 stable (Sep 2026) | https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml | For ComponentActivity.setContent |
| Material3 (Compose) | 1.4.0 stable (1.5.0-alpha27 latest alpha) | https://developer.android.com/jetpack/androidx/releases/compose-material3 | Use 1.4.0; M3 Expressive APIs partly experimental |
| Hilt (Dagger) | 2.60.1 (Jul 2026) | https://dagger.dev/ + https://github.com/google/dagger/releases | First AGP-9-compatible line; skip 2.59 (ComponentTreeDeps bug); minSdk now 23 — OK for our 26 |
| androidx.hilt | 1.4.0 (Jul 1, 2026) | https://developer.android.com/jetpack/androidx/releases/hilt | navigation-compose + work extensions; KGP ≥2.2.0 |
| Room | 2.8.4 (Nov 2025, maintenance) — RECOMMENDED; Room3 3.0.2 stable (Aug 26, 2026) exists | https://developer.android.com/jetpack/androidx/releases/room + .../room3 | Room3 = new `androidx.room3` pkg, KMP, KSP-only, coroutines-only, no SupportSQLite. Stay on 2.8.4 (matches WorkManager transitive dep); migrate later deliberately |
| DataStore | 1.2.1 (Mar 11, 2026) | https://developer.android.com/jetpack/androidx/releases/datastore | 1.3.x still alpha |
| Navigation-Compose | 2.10.0 (Aug 26, 2026) | https://mvnrepository.com/artifact/androidx.navigation/navigation-compose/2.10.0 | Note: Navigation3 1.1.7 is the new Compose-first lib; stay on 2.10.0 unless adopting Navigation3 |
| Coil | 3.6.0 (Aug 26, 2026, Maven Central) | https://mvnrepository.com/artifact/io.coil-kt.coil3/coil-compose-android/versions | Group `io.coil-kt.coil3`, artifacts `coil-compose` + `coil-network-okhttp`; trunk README cites 3.6.2 — re-check at setup, take highest stable |
| Media3 | 1.11.0 (Aug 5, 2026) | https://github.com/androidx/media/blob/release/RELEASENOTES.md | exoplayer/hls/dash/session/ui-compose-material3 (MiniController, PlayerPool new) |
| WorkManager | 2.11.2 (Mar 25, 2026) | https://developer.android.com/jetpack/androidx/releases/work | 2.12.0 only rc01 — not stable |
| androidx.webkit | 1.17.0 (Aug 12, 2026) | https://developer.android.com/jetpack/androidx/releases/webkit | Covers document-start JS injection / sniffer needs |
| Glance | 1.2.0 (Aug 26, 2026) | https://mvnrepository.com/artifact/androidx.glance/glance-appwidget/1.2.0 | `androidx.glance:glance-appwidget`; 1.3.x alpha only |
| kotlinx.serialization | 1.11.0 library (Apr 10, 2026); compiler plugin = Kotlin 2.4.20 | https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md | Plugin ships with Kotlin; runtime via `org.jetbrains.kotlinx:kotlinx-serialization-json` |
| Timber | 5.0.1 | https://github.com/JakeWharton/timber/blob/trunk/README.md | `com.jakewharton.timber:timber` |
| AboutLibraries | 15.1.1 (Aug 22, 2026) | https://github.com/mikepenz/AboutLibraries + https://mvnrepository.com/artifact/com.mikepenz/aboutlibraries-compose-core | 15.x line = Compose 1.11.x / AGP 9 / Kotlin 2.4 / compile 37; use `aboutlibraries-compose-m3` |
| Screenshot | Roborazzi 1.73.0 (Aug 25, 2026) — PICK | https://github.com/takahirom/roborazzi/releases + https://mvnrepository.com/artifact/io.github.takahirom.roborazzi/roborazzi | Robolectric-compatible (Hilt works); Paparazzi latest is 2.0.0-alpha05 (alpha only) and incompatible with Robolectric |
| Robolectric | 4.16.1 stable (Jan 21, 2026) | https://mvnrepository.com/artifact/org.robolectric/robolectric/versions | 4.17.x is beta-only (beta-4, Aug 2026) — do NOT use |
| Turbine | 1.2.1 | https://github.com/cashapp/turbine | `app.cash.turbine:turbine` |
| Truth / JUnit | Truth 1.4.4; JUnit4 4.13.2 (final) | https://android.googlesource.com/platform/external/truth + https://junit.org/junit4/ | JUnit4 in maintenance; consider JUnit5 only if Paparazzi path chosen (it isn't) |

## Current platform rules (affect BRIEF §§4.5, 4.6, 7)

- **FGS dataSync/mediaProcessing timeout (Android 15+)**: max 6 h per 24 h per type (tracked separately, shared across all services of that type); on expiry system calls `Service.onTimeout(int,int)` (API 35+) → must `stopSelf()` within seconds or crash (`RemoteServiceException`); user foregrounding the app resets timer. Test via `adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS <pkg>`. Sources: https://developer.android.com/develop/background-work/services/fgs/timeout, https://developer.android.com/about/versions/15/changes/foreground-service-types
- **BOOT_COMPLETED → FGS banned (target 35+)**: `dataSync` and `camera` FGS may NOT launch from `BOOT_COMPLETED` receiver. Boot receiver must only (re)schedule WorkManager/jobs. Source: same as above.
- **User-initiated data transfer (UIDT) jobs (API 34+, recommended for long downloads)**: `JobScheduler` with UIDT params, requires notification, runs immediately, exempt from job quotas, user-stoppable via Task Manager (kill = process death, no `onStopJob`). **No Jetpack/WorkManager wrapper exists** — call `JobScheduler` directly, gate on API 34, fall back to WorkManager foreground worker below 34. Source: https://developer.android.com/develop/background-work/background-tasks/uidt
- **FGS permissions**: `dataSync` type additionally needs `FOREGROUND_SERVICE_DATA_SYNC` manifest permission on API 34+ (on top of `FOREGROUND_SERVICE`).
- **POST_NOTIFICATIONS**: runtime permission on API 33+; UIDT/FGS notifications still require it for user visibility — request just-in-time.
- **MANAGE_EXTERNAL_STORAGE**: Play treats as high-risk; allowed only with essential core file-management functionality + approved Play Console Permissions Declaration. Prefer SAF/MediaStore; keep BRIEF's `full` vs store-safe flavor split. Sources: https://support.google.com/googleplay/android-developer/answer/10467955, .../answer/16558241
- **16 KB page-size alignment: MANDATORY and enforced**. New apps/updates targeting Android 15+ must ship 16 KB-aligned native `.so` on 64-bit (Play blocks otherwise; current docs page updated Sep 2026 phrases cutoff as Feb 1, 2027 for updates — treat as already-blocking). Pure Kotlin = free; **Go core via gomobile MUST be built with NDK r28+** and verified (`check_elf_alignment.sh` / APK Analyzer / lint). Sources: https://developer.android.com/guide/practices/page-sizes, https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html
- **AndroidX minSdk floor**: many AndroidX libs (incl. Hilt 2.60) now require minSdk 23 — fine for our minSdk 26, no action.
