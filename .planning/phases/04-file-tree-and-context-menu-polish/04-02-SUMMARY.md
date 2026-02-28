---
phase: 04-file-tree-and-context-menu-polish
plan: 02
subsystem: ui
tags: [context-menu, terminal, file-manager, intellij-plugin, kotlin]

# Dependency graph
requires:
  - phase: 04-01
    provides: VCS color rendering and dumb-mode icon caching in FileTreeComponent
provides:
  - Reorganized createPopupMenu() with 5 separator-delimited groups
  - Reveal in Finder/Explorer/File Manager action using Desktop.browseFileDirectory()
  - Open Terminal Here action using TerminalToolWindowManager.createLocalShellWidget()
  - All menu items always visible with explicit isEnabled based on selection state
affects:
  - future ui polish phases

# Tech tracking
tech-stack:
  added: [TerminalToolWindowManager (org.jetbrains.plugins.terminal)]
  patterns:
    - OS-adaptive menu labels via SystemInfo.isMac / SystemInfo.isWindows
    - Off-EDT Desktop API calls via AppExecutorUtil.getAppExecutorService().execute
    - Always-visible menu items with explicit isEnabled (no conditional add/remove per CTX-04)

key-files:
  created: []
  modified:
    - src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt

key-decisions:
  - "All menu items always added to menu; isEnabled set explicitly — no conditional item add/remove (per CTX-04)"
  - "Open and Add to Bookmarks remain always-visible but disabled when semantically invalid (file vs directory)"
  - "createLocalShellWidget deprecation warning accepted — API still functional in IJ 2024.3"

patterns-established:
  - "Context menu groups: Open/System/Reveal/Terminal | Copy/Cut/Paste/CopyPath | Rename/Delete | NewFile/NewFolder | Bookmarks/Refresh"

requirements-completed: [CTX-01, CTX-02, CTX-03, CTX-04]

# Metrics
duration: 5min
completed: 2026-03-01
---

# Phase 4 Plan 02: Context Menu Polish Summary

**Reorganized context menu with 5 ordered groups, OS-adaptive Reveal in Finder/Explorer action, and Open Terminal Here via TerminalToolWindowManager**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-02-28T22:17:49Z
- **Completed:** 2026-02-28T22:22:00Z
- **Tasks:** 1 of 2 (checkpoint at Task 2 — awaiting human verify)
- **Files modified:** 1

## Accomplishments
- Rewrote createPopupMenu() with 5 explicit groups separated by menu separators
- Added Reveal in Finder (macOS) / Reveal in Explorer (Windows) / Open in File Manager (Linux) using `Desktop.browseFileDirectory()` off-EDT
- Added Open Terminal Here using `TerminalToolWindowManager.getInstance(project).createLocalShellWidget()`
- All 12 menu items always present in the menu with explicit `isEnabled` based on selection state (CTX-04)

## Task Commits

Each task was committed atomically:

1. **Task 1: Reorganize createPopupMenu with new actions and enable/disable logic** - `283a772` (feat)

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt` - Added TerminalToolWindowManager import, rewrote createPopupMenu() with 5 groups, Reveal and Terminal actions, explicit isEnabled on all items

## Decisions Made
- All menu items always added to menu — isEnabled set based on selection (CTX-04 compliance). Only distinction: items disabled when selection doesn't match semantic requirements.
- `createLocalShellWidget` deprecation warning accepted — the API is still functional in IJ 2024.3 and is the documented pattern in SshTerminalAction.kt.
- Off-EDT execution via `AppExecutorUtil.getAppExecutorService().execute` for both Desktop.open() and Desktop.browseFileDirectory() calls.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Context menu polish complete, pending human verification in running IDE
- Phase 4 verification covers: VCS colors, icons after dumb-mode, expand/collapse stability, context menu behavior, speed search

---
*Phase: 04-file-tree-and-context-menu-polish*
*Completed: 2026-03-01*
