# PERMISSIONS.md — ZentraDL (minimal; all justified, JIT + rationale)

| Permission | Why | When asked |
|---|---|---|
| INTERNET | Downloads, trackers, update-check (opt-in) | Install (normal) |
| ACCESS_NETWORK_STATE | Wi-Fi-only/metered rules, auto-resume | Install (normal) |
| POST_NOTIFICATIONS (33+) | Progress/completion/failure actions | First download (rationale first) |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC (34+) | Transfers while backgrounded | With first backgrounded download |
| WAKE_LOCK / Wi-Fi lock | Only during active transfers | Implicit; released when idle |
| RECEIVE_BOOT_COMPLETED | Reschedule WorkManager/scheduler only (never FGS from BOOT) | Install |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Optional; OEM auto-kill guidance screen | Only if user opens guidance |
| MANAGE_EXTERNAL_STORAGE | `full` flavor only (direct SD/USB paths); store-safe flavor uses SAF | Only in full flavor, with rationale |
| REQUEST_INSTALL_PACKAGES | Optional APK-install prompt | Only if user enables it |
| USE_BIOMETRIC | Vault + app lock | Only if user enables lock |
| CAMERA | Optional QR scan (magnets/links) | Only when scanning |
