# PRIVACY.md — ZentraDL

No ads, analytics or telemetry. Network calls only when the user triggers them
(downloads, torrent trackers/DHT/routers, browser pages) plus the optional
user-triggered update check (off by default, hits api.github.com once).
All smart features run on-device; no cloud/AI. Logs stay local.
Backups are plaintext JSON by user choice (warned in-app); the app stores no
passwords or tokens anywhere, so there is nothing to encrypt or wipe.
Private vault = app-private files + `.nomedia` (hidden from galleries, not
encrypted — documented gap). App lock uses the device credential.
No built-in torrent search or indexers; HLS capture is for non-DRM streams the
user can already play.
