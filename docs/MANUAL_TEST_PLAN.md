# MANUAL_TEST_PLAN.md — acceptance runs (BRIEF §11; qa-tester owns)

1. 1 GB file, 8 conns, kill app mid-way → resume completes, checksum OK.
2. Magnet → metadata → pick files → Pieces map fills live → sequential preview plays.
3. Auto-sort with preview + undo restores originals.
4. Browser sniffs video on test page → downloads it.
5. Schedule + Wi-Fi-only respected (airplane/Wi-Fi toggles).
6. Rotation / fold / tablet layouts; multi-window.
7. TalkBack pass (labels + progress semantics); 200% font; RTL.
8. 10,000-task synthetic list scrolls smoothly while 20 update.
