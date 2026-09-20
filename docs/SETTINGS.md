# SETTINGS.md — ZentraDL (all settings documented here; groups per BRIEF §6.9)

> Groups: General, Downloads, Network, Storage & Organization, Smart features, Torrent, Browser, Notifications,
> Appearance, Privacy & Security, Backup & Restore, Advanced, About.
> Each: title, one-line description, default, section-reset support.

| Key | Group | Default | Notes |
|---|---|---|---|
| `theme_mode` | Appearance | System | System/Light/Dark/AMOLED |
| `accent` | Appearance | Blue | 6 accents; active only when dynamic color OFF |
| `dynamic_color` | Appearance | on | Wallpaper-matched schemes (Android 12+) |
| `connections` | Downloads | 8 | Segments per download (1–32) |
| `max_running` | Downloads | 3 | Simultaneous downloads (1–30) |
| `density` | Downloads (list) | comfortable | compact/comfortable card density |
| `sort` | Downloads (list) | date | date/name/size/progress/speed |
| `wifi_only` | Network & schedule | off | Hold queue on metered connections |
| `charging_only` | Network & schedule | off | Hold queue while on battery |
| `sched_enabled` | Network & schedule | off | Daily time-window gate |
| `sched_start_min` | Network & schedule | 1320 (22:00) | Window start, minutes-of-day |
| `sched_end_min` | Network & schedule | 360 (06:00) | Window end; wraps midnight |
| `speed_limit_kbps` | Network & schedule | 0 (unlimited) | Global cap, max 100 MB/s |
| `app_lock` | Privacy & security | off | Device credential on start + resume |
| (categories) | Storage & organization | 7 built-ins | Room table; per-category folders; add/delete in Settings |

> Reset-per-section, Torrent/Browser/Notifications/Smart groups, backup/restore: later phases.
