---
phase: 07-polish-quickopen-smart-search
plan: 03
subsystem: ui
tags: [quickopen, frecency, ranking, search, kotlin, intellij-plugin]

# Dependency graph
requires:
  - phase: 07-01
    provides: QuickOpenPanel, CandidatePool, FrecencyStore, RankerSelector, NucleoNative
  - phase: 07-02
    provides: panelScope, dual-debounce, content match navigation
provides:
  - rankerStatusLabel with clickable root picker (FileChooserFactory)
  - updateStatusBar() showing active ranker, current root, file count
  - condensed 5-shortcut hint bar
  - pure-frecency speed dial (FrecencyStore.recencyScore based)
  - parent path substring highlighting in SearchResultRenderer
  - result-type grouping in Ranker (BOOKMARK/RECENT before FILE before DIRECTORY)
affects: [08-any-future-phase using QuickOpenPanel or Ranker]

# Tech tracking
tech-stack:
  added: [FileChooserDescriptor, FileChooserFactory (root picker)]
  patterns: [post-scoring display ordering via typeGroup comparator, renderer field pattern for shared renderer state]

key-files:
  created: []
  modified:
    - src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt
    - src/main/kotlin/ro/faur/explorer/quickopen/ui/SearchResultRenderer.kt
    - src/main/kotlin/ro/faur/explorer/quickopen/ranking/Ranker.kt

key-decisions:
  - "Status bar row added below hint row using GridLayout(2,1) with a bottomRow BorderLayout panel"
  - "currentRoot mutable field tracks effective search root independently of constructor-param currentPath"
  - "Speed dial uses FrecencyStore.recencyScore() exclusively — no bookmark-first fallback"
  - "Path highlighting uses case-insensitive literal indexOf, not fuzzy positions — more accurate for parent paths"
  - "typeGroup comparator is post-scoring display layer only — frecency blend in scoring formula unchanged"

patterns-established:
  - "Renderer field pattern: extract renderer to a named field so QuickOpenPanel can set state (currentQuery) before list updates"
  - "typeGroup helper: private fun in Ranker maps CandidateType to Int ordinal for clean comparator composition"

requirements-completed: [QO-UI-01, QO-UI-02, QO-UI-03, QO-UI-04, QO-RANK-01]

# Metrics
duration: 3min
completed: 2026-02-28
---

# Phase 7 Plan 03: UI Polish — Status Bar, Speed Dial Frecency, Path Highlighting, Result Grouping Summary

**Ranker/root/count status bar with clickable root picker, frecency-only speed dial, condensed 5-shortcut hint bar, parent path substring highlighting, and type-group result ordering added to QuickOpen popup**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-28T12:30:41Z
- **Completed:** 2026-02-28T12:33:21Z
- **Tasks:** 4
- **Files modified:** 3

## Accomplishments
- Status bar row showing "Fuzzy: Nucleo · Root: ~/project · 12,345 files" with hand-cursor click to open directory picker
- Speed dial now sorted purely by FrecencyStore.recencyScore() — bookmark-first fallback removed
- Hint bar condensed from 10 shortcuts to 5: navigate, preview, bookmark, recent, close
- Parent path in result rows highlights matched substring in bold (case-insensitive literal search)
- Ranker sort changed from pure-score to type-group primary (BOOKMARK/RECENT=0, FILE=1, DIRECTORY=2) then score within group

## Task Commits

Each task was committed atomically:

1. **Task 1: Status bar + clickable root picker** - `7781142` (feat) — also contains Tasks 2 changes (both in QuickOpenPanel.kt)
2. **Task 3: Parent path highlighting** - `cf8ef72` (feat)
3. **Task 4: Result-type grouping in Ranker** - `7ffe6ea` (feat)

Note: Tasks 1 and 2 (hint bar + speed dial) are combined in commit `7781142` since both modify QuickOpenPanel.kt and were implemented together.

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt` - Added rankerStatusLabel, currentRoot, updateStatusBar(), openRootPicker(), condensed hintLabel, frecency speedDialItems, resultRenderer field, resultRenderer.currentQuery wiring
- `src/main/kotlin/ro/faur/explorer/quickopen/ui/SearchResultRenderer.kt` - Added currentQuery field and conditional path highlighting
- `src/main/kotlin/ro/faur/explorer/quickopen/ranking/Ranker.kt` - Added typeGroup() helper and updated sortedWith comparator

## Decisions Made
- GridLayout(2,1) chosen for statusPanel to add second row without touching the existing truncation/count/hint row layout
- currentRoot mutable field added separately from constructor-param currentPath so the panel can track root changes without recreating itself
- Path highlighting guards against "/" and ":" prefixes to avoid noise when query is a path or mode prefix

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None - build succeeded cleanly on first attempt for all changes.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 5 QO-UI and QO-RANK-01 requirements completed
- QuickOpen UI polish pass is complete; no further planned polish tasks in this phase
- Ranker type-grouping is a post-scoring display layer — scoring formula unchanged, safe to extend

---
*Phase: 07-polish-quickopen-smart-search*
*Completed: 2026-02-28*
