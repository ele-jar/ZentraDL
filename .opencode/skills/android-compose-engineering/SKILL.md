---
name: android-compose-engineering
description: Load BEFORE creating or editing ANY Kotlin file in android/ (app, engine, data, domain, services, tests, Gradle). MVVM, coroutines, Hilt, Room, DataStore, navigation, logging, build and testing rules for ZentraDL.
license: GPL-3.0-or-later
compatibility: opencode
---

# Android + Compose Engineering — ZentraDL

Stack: Kotlin, Compose + Material3, Hilt, Coroutines/Flow, Room, DataStore,
Navigation-Compose (type-safe), Coil, Media3, WorkManager, androidx.webkit,
kotlinx.serialization, Timber, AboutLibraries, Glance. minSdk 26.

## 1. Architecture

- MVVM + unidirectional data flow: immutable `UiState` data class,
  one-shot events via `Channel<UiEvent>`/`SharedFlow`, state hoisting.
  ViewModel owns logic; composables are stateless where possible.
- Package-by-feature: `feature/downloads/`, `feature/browser/`,
  `feature/torrents/`, `feature/settings/` each with
  `ui/`, `domain/`, `data/` inside; shared in `core/`.
  Modules: `:app`, `:engine` (Go core AAR wrapper + EngineClient + models),
  `:designsystem` (theme/tokens/components incl. PiecesMap).
- Core owns transfer state. Room stores app metadata (categories, tags,
  rules, history, stats, bookmarks) keyed by taskId; reconcile on startup.
  Never duplicate progress bookkeeping.

```kotlin
// GOOD: immutable UiState + one-shot events
data class DownloadsUiState(val items: List<TaskUi> = emptyList(), val isLoading: Boolean = false)
sealed interface DownloadsEvent { data class ShowUndo(val msg: String) : DownloadsEvent }
@HiltViewModel class DownloadsVm @Inject constructor(repo: TaskRepo) : ViewModel() {
  val state: StateFlow<DownloadsUiState> = repo.tasks.map { DownloadsUiState(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState(isLoading = true))
  private val _events = Channel<DownloadsEvent>(Channel.BUFFERED); val events = _events.receiveAsFlow()
}
```

## 2. Coroutines / Flow (blocking)

- No `GlobalScope`. Inject dispatchers (`@IoDispatcher`). Structured
  concurrency (`viewModelScope`, `lifecycleScope`).
- Progress flows: `conflate() + sample(250)` → ≤4 Hz to UI.
- `SharingStarted.WhileSubscribed(5_000)` for UI StateFlows.
- Catch with typed results: `sealed interface Result<T>` / domain errors,
  never silent `catch {}`. Log with Timber.

## 3. DI, persistence, nav

- Hilt everywhere (`@HiltAndroidApp`, `@AndroidEntryPoint`,
  `@HiltViewModel`, `@Module @InstallIn(SingletonComponent::class)`).
- Room: entities + DAO + migrations for EVERY schema change
  (`autoMigrations` or manual; destructive fallback FORBIDDEN in release).
- DataStore (Preferences/Proto) for settings; no SharedPreferences in new code.
- Navigation: type-safe routes (`@Serializable` destinations), no raw string
  deep-links; intent filters validated before use.

## 4. Gradle conventions

- Version catalog `gradle/libs.versions.toml` — all versions there, nowhere else.
- KSP for Hilt/Room. R8 + resource shrinking on release. ABI splits
  (`arm64-v8a`, `armeabi-v7a`, `x86_64`) + universal APK. Baseline profile.
- `org.gradle.jvmargs` tuned to machine RAM; `workers.max` = nproc-1.
- 16 KB page-size alignment for native libs (verify NDK/AGP guidance).
- Lint: Android lint + ktlint (or detekt). Zero new warnings policy.

## 5. Logging / errors / security

- Timber (no `Log.*`, no `println` in prod). Redact URLs/tokens in shared logs.
- Sanitize filenames/paths (block `..`, absolute paths, reserved names);
  zip-slip protection on extract. Validate all incoming intents.
- Keystore-encrypted vault for credentials; cleartext HTTP only for
  user-supplied download URLs; loopback-only core endpoint + auth token.

## 6. Testing strategy

- Unit: categorizer, auto-sort templates, rules engine, duplicates, storage
  strategy, ETA smoother, RLE aggregation (JUnit + Truth/Turbine for Flows).
- Compose UI tests (Add flow, list, selection) + screenshot tests
  (Roborazzi/Paparazzi: light/dark/large-font/tablet).
- Go tests for every core patch. Offline harness: local HTTP server
  (ranges/slow/flaky/redirects/expiry) + local torrent swarm.
- Only ONE process runs Gradle at a time. Lead runs full builds after merging.

## 7. Naming / style

- Files: `DownloadsScreen.kt`, `DownloadsViewModel.kt`, `TaskRepo.kt`.
- Functions `lowerCamel`, composables `UpperCamel`, test `subject_when_then`.
- ktlint format, max 120 cols. No Hungarian notation, no `Util*` dumping
  ground — put helpers next to their feature.

## 8. Definition of Done (every feature)

- [ ] Builds (`assembleDebug`), unit + UI tests pass, lint clean.
- [ ] UiState immutable, events via Channel, no GlobalScope.
- [ ] Room migration if schema changed; DataStore for prefs.
- [ ] Skills `android-ui-ux-design` + `download-manager-ux-patterns`
      checklists run (for UI); noted in PROGRESS.md.
- [ ] No secrets, no demo data in prod paths, Timber only.
- [ ] docs + PROGRESS.md updated, committed (Conventional Commits).
