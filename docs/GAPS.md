# GAPS.md — known engine gaps (never silent; mapped to alternative)

| Gap | Status | Alternative |
|---|---|---|
| BitTorrent v2 full surfacing (v2 hash field, v2-magnet ingest proof) | TODO verify in Phase 4 | Inherited anacrolix ≥v1.56 v2 support; expose or document here |
| HLS/DASH remux library (ffmpeg-kit retired upstream) | TODO research Phase 5 | Default: concatenate to .ts/.mp4; remux only via maintained compatible lib |
| FTP/SFTP fetcher | Stretch (§5.1) | Document here if skipped |
