---
name: android-ui-ux-design
description: Load BEFORE creating or editing ANY UI file in android/ (screens, components, theme, navigation, widgets, notifications UI). Governs Material 3 visual design, layout, motion, states, accessibility and microcopy for ZentraDL.
license: GPL-3.0-or-later
compatibility: opencode
---

# Android UI/UX Design — ZentraDL

Visual tone: modern, clean, calm, slightly playful. Rounded shapes, generous
whitespace, progress is the hero. "Google Files clarity + pro-tool density
controls + polished Material You". Original identity — never clone 1DM+,
Gopeed or LibreTorrent.

## 1. Principles

1. Clarity at a glance: status, progress, speed visible in <2s on any row.
2. Calm density: user picks Compact / Comfortable / Detailed, default Comfortable.
3. One primary action per screen (Download / Pause / Retry / Open).
4. Progressive disclosure: basics first, Advanced collapsed in an expandable.
5. Never surprise: moves/renames/deletes are opt-in, previewed, undoable.
6. Honest status: never fake progress. Show "Waiting for network",
   "Calculating…", "Queued (2 of 5)" instead of a frozen bar.
7. Share/paste → download running in at most 2 taps.

## 2. Theme tokens (exact)

- Material 3 color roles only: primary, onPrimary, primaryContainer,
  secondary, tertiary, surface, surfaceVariant, surfaceContainer*,
  error, outline. NEVER hardcode `Color(0xFF…)`.
- Dynamic color on Android 12+ (`dynamicLightColorScheme` /
  `dynamicDarkColorScheme`); designed fallback otherwise:
  - Light fallback seed `0xFF2E6BE6`, Dark fallback seed `0xFF90B4FF`.
  - AMOLED theme = dark scheme with `surface/surfaceContainer* = Black (0xFF000000)`.
- Theme options: System / Light / Dark / AMOLED. Accent picker (6 accents)
  active only when dynamic color OFF.
- Semantic status colors (harmonized, ALWAYS icon+label, never color alone):
  - Downloading = primary, Queued = outline/secondary, Paused = tertiary,
    Completed = green `0xFF2E7D32` (dark `0xFF81C784`),
    Failed = error, Seeding = teal `0xFF00796B`, Checking = amber `0xFFF9A825`.
- Type: M3 type scale only (`displayLarge … labelSmall`). Body ≥14sp.
  Speeds/sizes/% use tabular figures:
  `FontFamily.Monospace` or `fontFeatureSettings = "tnum"` so numbers don't jitter.
- Spacing grid 4dp: use 4/8/12/16/24/32 only. Screen padding 16 (compact),
  24 (medium+). Card corner 16dp (compact rows 12dp), sheets 28dp top.
- Shape scale: `extraSmall=4, small=8, medium=12, large=16, extraLarge=28`.
- Elevation: tonal only (`surfaceContainerLow/High`), no shadow abuse.
- Icons: Material Symbols Rounded, one weight (400 filled-off for outline
  actions, filled-on for selected). Size 24dp actions, 20dp chips, 48dp tiles.
  NEVER emoji as icons.

## 3. Layout

- Window size classes: compact <600dp, medium 600–839dp, expanded ≥840dp.
- Nav: bottom bar (compact) → NavigationRail (medium) → permanent drawer
  (expanded). Destinations: Downloads | Browser | Activity | Settings.
- List-detail on medium+ for Details screens (route + pane widths 360/720).
- Edge-to-edge: `enableEdgeToEdge()`, consume
  `WindowInsets.systemBars + displayCutout + ime` via `windowInsetsPadding`.
- Primary actions in thumb zone (bottom 1/3, FAB 16dp above bottom bar).
- Max content width 840dp centered on expanded screens.
- Support portrait, landscape, foldables (posture/tabletop), multi-window.
- RTL: no `left/right` paddings — use `start/end`; test with Force RTL.

## 4. Components (all states: enabled/disabled/pressed/focused/loading/error)

- Download card:
  - Compact 64dp: tile + title(1 line) + progress bar + trailing action.
  - Comfortable 88dp (default): 48dp rounded tile (thumbnail or type icon on
    category color), title 2 lines middle-ellipsis, meta line, 6dp rounded
    animated progress, trailing action + overflow.
  - Detailed 120dp: + speed graph sparkline, chips (queue/category/type),
    segmented piece bar for torrents.
- Progress: linear 6dp rounded (`LinearProgressIndicator`, gap + rounded
  stroke); ring for header; segmented piece bar for torrents (Canvas, NOT
  per-cell composables). Animate progress toward target (`animateFloatAsState`,
  300ms), never jump. Show % + "312 MB of 1.2 GB".
- Status chip: icon + label, 32dp height, tonal container.
- Filter chips: single-select row (All/Active/Queued/Paused/Completed/Failed/
  Seeding) + category/type chips; horizontal scroll, 8dp gap.
- Speed header (sticky): total down/up, active/queued counts, 60s sparkline,
  alt-speed toggle, Pause-all/Resume-all. Updates throttled to ~4 Hz.
- FAB menu: Add → Paste link, Torrent file, Magnet, Batch/Import,
  From clipboard. Long-press = paste-and-start.
- Bottom sheets for Add/confirm flows (full-height, keyboard-aware,
  `imePadding()`); dialogs ONLY for irreversible confirms; reversible →
  Snackbar with Undo (10s).
- Swipe actions configurable (default: start-swipe Delete, end-swipe
  Pause/Resume); always Undo.
- Multi-select toolbar: count + Pause/Resume/Delete/Move/Category/Tag/Copy/Share.
- Settings rows: title + one-line description + control
  (switch/slider/segmented/dropdown); destructive = confirm; per-section Reset.
- Empty: illustration + ≤12-word text + CTA button.
  Error: what + why + fix + button. Loading: skeleton (never bare spinner for
  lists). Offline banner. Permission-denied with rationale + "Open settings".
  First-run: ≤3 skippable screens.

## 5. Motion

- Durations 150–300ms, M3 easing
  (`fastOutSlowIn` / `emphasized`). Container transform list→detail,
  shared-axis for tabs. `animateItem()` in LazyColumn. Predictive back
  supported. Honor `reduce motion` (disable non-essential animation when
  AccessibilityManager requests it).
- Use M3 Expressive (e.g. wavy progress) ONLY if in a stable
  `androidx.compose.material3` release — verify version first.

## 6. State design (EVERY screen)

Loading skeleton → Content | Empty (text+illustration+CTA) |
Error (what/why/fix/button) | Offline | Permission-denied | First-run.
No screen ships without all six handled.

## 7. Accessibility (blocking)

- Contrast ≥4.5:1 (3:1 large text/graphics). Touch targets ≥48dp.
- TalkBack: contentDescription on every icon button; progress exposed via
  `stateDescription = "45 percent, 312 MB of 1.2 GB, 4 MB per second"`.
- Survive 200% font scale (no fixed-height text clipping). Keyboard/D-pad
  focus order logical, visible focus indicator.

## 8. Microcopy

- Friendly, short, specific. Errors: "what + why + fix".
  BAD: "Error 404". GOOD: "File not found — the link expired. Refresh the link."
- Formats: "1.4 GB" (1024-base, 1 decimal), "3.2 MB/s", "12 min left",
  "Yesterday 21:40". Middle-ellipsis for filenames so extension stays visible:
  `very-long-fil…nal.mp4`.
- No hard-coded strings: all in `strings.xml`, no lorem/demo data in prod code.

## 9. Compose performance (blocking)

- LazyColumn with stable `key = task.id`, immutable UiState data classes.
- `derivedStateOf` for filtered/sorted lists. No recomposition storms:
  progress Flow throttled (`conflate()` + `sample(250ms)` → ≤4 Hz UI).
- `remember` formatters; avoid allocations in composition.
- 10,000-task list must scroll smoothly while 20 tasks update.

## 10. ANTI-PATTERNS (reject in review)

Cramped rows, inconsistent padding, hardcoded colors, text <12sp,
rainbow palettes, heavy gradients, emoji icons, dialog-for-everything,
infinite spinners, truncating extensions, non-tabular numbers, demo data.

## 11. REVIEW CHECKLIST (run after EVERY screen, note in PROGRESS.md)

- [ ] M3 roles only, light/dark/AMOLED correct, status = icon+label?
- [ ] 4dp grid, 48dp targets, 2-line title middle-ellipsis, tabular numbers?
- [ ] All 6 states (loading/empty/error/offline/denied/first-run)?
- [ ] Motion 150–300ms, progress animated, reduce-motion honored?
- [ ] TalkBack labels + progress semantics, 200% font, RTL?
- [ ] No hard-coded strings/colors, edge-to-edge insets correct?
- [ ] @Preview + screenshot tests (light, dark, large-font, tablet)?
- [ ] Throttled progress (≤4 Hz), stable keys, no recomposition storm?

```kotlin
// GOOD: tonal, tabular, semantic, accessible
ListItem(
  leadingContent = { CategoryTile(type, categoryColor) },
  headlineContent = {
    Text(item.title, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
  },
  supportingContent = {
    Text(
      "${item.downloaded} of ${item.total} · ${item.speed}/s",
      fontFeatureSettings = "tnum",
      style = MaterialTheme.typography.bodySmall
    )
  },
  trailingContent = { PauseResumeButton(item.status) }
)
```
