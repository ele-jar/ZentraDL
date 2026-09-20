# ARCHITECTURE.md — ZentraDL (condensed; full spec docs/BRIEF.md §4)

Modules: `:app` (features package-by-feature: `feature/downloads|browser|torrents|settings/*/ui|domain|data`),
`:engine` (pure-Kotlin transfer engine: OkHttp multi-part HTTP downloader, later a JVM
BitTorrent client; models per `docs/ENGINE_API.md`), `:designsystem`
(theme/tokens/components incl. PiecesMap Canvas). D010: the Gopeed Go core
(`core/` submodule, gomobile AAR) was dropped per user instruction — Android-only,
Kotlin-only. `docs/GOPEED_*.md` remain as research history only.

State: `:engine` owns transfers (foreground service + WorkManager/UIDT jobs, §4.5).
Room = app metadata keyed by taskId (categories/tags/rules/
history/stats), reconciled on startup; never duplicate progress. Settings in DataStore.

Background: foreground service ONLY while active (dataSync type + `FOREGROUND_SERVICE_DATA_SYNC`
on 34+; handle API35 `onTimeout`; 6h/24h cap); UIDT JobScheduler on 34+ else WorkManager;
boot receiver reschedules only (no FGS from BOOT on target 35+). Storage: `StorageStrategy`
A direct-path / B stage-in-app-private + move-to-SAF on completion; `full` flavor holds
MANAGE_EXTERNAL_STORAGE. Contract-first: ENGINE_API.md before Go/Kotlin/UI splits.
