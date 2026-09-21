# MANUAL_TEST_PLAN.md — acceptance runs (BRIEF §11; qa-tester owns)

HTTP:
1. 1 GB file, 8 conns, kill app mid-way → resume completes; checksum card matches.
2. Expired link → Fix-it card → Refresh link with browser-captured URL → completes.
3. Add 30 mixed links via batch → duplicates skipped with count → export list.
4. Wi-Fi-only + schedule window respected (airplane/Wi-Fi toggles, banner shows reason).

Torrents:
5. Magnet (with DHT only, no x.pe) → metadata ≤60 s → file tree → deselect one file → completes subset.
6. Pieces tab fills live; pinch-zoom + tap cell; peers list non-empty; health card sensible.
7. Pause → resume completes from existing data (no re-download); recheck re-verifies.
8. Seed to goal ratio → parks as paused; move storage to another category → resume seeds from new path.
9. Kill app mid-torrent → relaunch parks it paused (no stuck "downloading").

Browser/rules/backup:
10. Test page with video → badge → quality picker → .ts downloads and plays.
11. Auto-sort rule preview → complete a download → file moves + undo in Activity restores.
12. Backup → reinstall → import → tasks/rules/settings back (records paused).
13. Onboarding shows once; QS tile pauses/resumes; shortcut opens add sheet.

Device:
14. Rotation/fold/tablet; 200% font; RTL forced; TalkBack on list + details.
15. Vault file invisible to gallery; app lock prompts on resume; deny fails closed.
16. Release APK on low-RAM device: 5 torrents + 3 HTTP run; no ANR in logcat.
