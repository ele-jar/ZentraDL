# ARCHITECTURE.md — ZentraDL (condensed; full spec docs/BRIEF.md §4)

Modules: `:app` (features package-by-feature: `feature/downloads|browser|torrents|settings/*/ui|domain|data`),
`:engine` (AAR wrapper + `EngineClient` per `docs/ENGINE_API.md`), `:designsystem`
(theme/tokens/components incl. PiecesMap Canvas). `core/` = Gopeed @v1.9.3 submodule.

State: core owns transfers (in-process Dispatch + SubscribeTaskEvents; TCP+token
fallback loopback-only). Room = app metadata keyed by taskId (categories/tags/rules/
history/stats), reconciled on startup; never duplicate progress. Settings in DataStore.

Background: foreground service ONLY while active (dataSync type + `FOREGROUND_SERVICE_DATA_SYNC`
on 34+; handle API35 `onTimeout`; 6h/24h cap); UIDT JobScheduler on 34+ else WorkManager;
boot receiver reschedules only (no FGS from BOOT on target 35+). Storage: `StorageStrategy`
A direct-path / B stage-in-app-private + move-to-SAF on completion; `full` flavor holds
MANAGE_EXTERNAL_STORAGE. Contract-first: ENGINE_API.md before Go/Kotlin/UI splits.
