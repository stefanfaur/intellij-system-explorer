---
phase: 06-stash-ui
plan: "03"
subsystem: ui
tags: [kotlin, intellij-platform, jblist, toolbar, context-menu, swing]

requires:
  - phase: 06-01
    provides: StashEntry data class and stashApply/stashDrop/stashPop/stashList backend methods
provides:
  - StashListPanel: self-contained panel with JBList<StashEntry>, Apply/Pop/Drop toolbar, and right-click context menu
affects: [06-04-GitPanelComponent-stash-wiring]

tech-stack:
  added: []
  patterns:
    - "Secondary ActionToolbar on NORTH + JBScrollPane on CENTER in BorderLayout for list panels"
    - "ActionUpdateThread.EDT for toolbar actions that read Swing list selection state"
    - "mouseReleased (not mousePressed) for right-click popup trigger — matches project convention"
    - "ColoredListCellRenderer<T> with REGULAR_ATTRIBUTES for platform-themed list rows"
    - "Nullable lambda callbacks (var onX: ((T) -> Unit)? = null) for decoupled event wiring"

key-files:
  created:
    - src/main/kotlin/ro/faur/explorer/gitpanel/ui/StashListPanel.kt
  modified: []

key-decisions:
  - "Row format: StashEntry.message only — no stash@{N}: prefix (message already cleaned by parseStashLine)"
  - "setEntries() always rebuilds from fresh data — stale index prevention by convention"
  - "Toolbar and context menu both show Apply/Pop/Drop — menu items call same callbacks as toolbar actions"
  - "Icons: Apply=AllIcons.Actions.Download, Pop=AllIcons.Vcs.Merge, Drop=AllIcons.General.Remove"

patterns-established:
  - "StashListPanel pattern: toolbar actions + matching context menu both fire nullable lambda callbacks"

requirements-completed: [STASH-03, STASH-04, STASH-05]

duration: 1min
completed: 2026-03-01
---

# Phase 6 Plan 03: StashListPanel Summary

**Self-contained stash list panel with JBList<StashEntry>, Apply/Pop/Drop secondary toolbar, and mirrored right-click context menu using nullable callback properties**

## Performance

- **Duration:** 1 min
- **Started:** 2026-03-01T14:24:08Z
- **Completed:** 2026-03-01T14:25:11Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- StashListPanel.kt created with JBList rendering stash messages (no index prefix)
- Secondary toolbar with Apply/Pop/Drop — all enabled only when a row is selected, using ActionUpdateThread.EDT
- Right-click context menu mirrors toolbar exactly: Apply, Pop, Drop
- Empty text "No stashes" shown when list is empty
- onApply/onPop/onDrop/onStashSelected nullable lambda callbacks ready for wiring by GitPanelComponent
- setEntries() rebuilds list model from fresh data on each call

## Task Commits

Each task was committed atomically:

1. **Task 1: Create StashListPanel.kt with JBList, secondary toolbar, and context menu** - `dd982d8` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/StashListPanel.kt` - StashListPanel with JBList, toolbar, context menu, and callback properties

## Decisions Made
- Row format shows StashEntry.message only — no stash@{N}: prefix since parseStashLine already produces clean messages
- Icons chosen: Apply=AllIcons.Actions.Download, Pop=AllIcons.Vcs.Merge, Drop=AllIcons.General.Remove
- Context menu items built fresh on each right-click (buildContextMenu()) for simplicity

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- StashListPanel is ready to be embedded into GitPanelComponent's CardLayout in plan 04
- Callbacks (onApply, onPop, onDrop, onStashSelected) are public properties awaiting wiring
- setEntries() public method ready for GitPanelComponent to call after backend stashList() results

---
*Phase: 06-stash-ui*
*Completed: 2026-03-01*
