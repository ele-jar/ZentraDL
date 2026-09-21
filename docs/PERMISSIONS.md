# PERMISSIONS.md — ZentraDL (minimal; all justified, JIT + rationale)

| Permission | Why | When asked |
|---|---|---|
| INTERNET | Downloads, trackers/DHT, opt-in update check | Install (normal) |
| ACCESS_NETWORK_STATE | Wi-Fi-only/metered gates, waiting banners | Install (normal) |
| POST_NOTIFICATIONS (33+) | Progress/completion/failure actions | First download (rationale first) |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC (34+) | Transfers while backgrounded | With first backgrounded download |
| WAKE_LOCK | Held by the FGS while active only | Implicit; released when idle |
| RECEIVE_BOOT_COMPLETED | Reserved for scheduler reschedule (no FGS from BOOT) | Install |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Declared, no guidance screen yet (gap) | — |
| VIBRATE | Notification vibration channel default | Install (normal) |

Not requested: location, contacts, camera (QR gap), microphone, MANAGE_EXTERNAL_STORAGE
(app-private storage + SAF-free design; no full flavor), REQUEST_INSTALL_PACKAGES
(APK prompt gap), USE_BIOMETRIC (app lock uses device credential, not biometric API).
