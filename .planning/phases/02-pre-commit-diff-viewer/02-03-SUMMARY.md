---
phase: 02-pre-commit-diff-viewer
plan: 03
subsystem: ui
tags: [diff, diffmanager, context-menu, action, anaction, virtualfile]

# Dependency graph
requires:
  - phase: 02-pre-commit-diff-viewer
    provides: ExplorerActionUtil, FileTreeComponent.getSelectedFiles(), SystemExplorer.ActionGroup
provides:
  - CompareWithAction: AnAction that compares two explorer-selected files via IntelliJ DiffManager
  - plugin.xml SystemExplorer.CompareWith registration
affects: [future-actions, context-menu-actions]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AnAction with ActionUpdateThread.BGT, update() guards isEnabledAndVisible on exact selection count"
    - "DiffContentFactory.create(project, virtualFile) + vf.refresh(false, false) before diff"
    - "DiffManager.showDiff with DiffDialogHints.FRAME for full-window diff popup"

key-files:
  created:
    - src/main/kotlin/ro/faur/explorer/actions/CompareWithAction.kt
  modified:
    - src/main/resources/META-INF/plugin.xml
    - src/test/kotlin/ro/faur/explorer/light/GitRepositoryRegistryTest.kt

key-decisions:
  - "Use com.intellij.diff.* exclusively (not deprecated com.intellij.openapi.diff.*) — existing locked decision"
  - "Tree row order determines left/right: selected[0] = top row = left, selected[1] = bottom row = right"
  - "vf.refresh(false, false) called before DiffContentFactory to avoid stale VFS content"

patterns-established:
  - "Context-menu-only actions: no keyboard-shortcut element needed, action group membership is sufficient"

requirements-completed: [DIFF-04]

# Metrics
duration: 3min
completed: 2026-02-28
---

# Phase 02 Plan 03: Compare With Action Summary

**CompareWithAction added to System Explorer context menu — compares exactly two selected files side-by-side via IntelliJ's DiffManager FRAME popup, no git required**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-28T12:26:05Z
- **Completed:** 2026-02-28T12:29:58Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Created `CompareWithAction.kt` with `update()` guard that enables action only when exactly 2 files are selected
- Registered `SystemExplorer.CompareWith` in `plugin.xml` inside `SystemExplorer.ActionGroup` for context-menu availability
- Auto-fixed pre-existing test compilation error caused by `getHeadContent` interface addition from plan 02-01

## Task Commits

Each task was committed atomically:

1. **Task 1: Create CompareWithAction** - `0d4ad61` (feat)
2. **Task 2: Register CompareWithAction in plugin.xml** - `ddd4ce9` (feat, includes auto-fix)

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/actions/CompareWithAction.kt` - New AnAction; update() guards on exactly 2 files, actionPerformed() builds SimpleDiffRequest and calls DiffManager.showDiff(FRAME)
- `src/main/resources/META-INF/plugin.xml` - Added SystemExplorer.CompareWith action registration inside SystemExplorer.ActionGroup
- `src/test/kotlin/ro/faur/explorer/light/GitRepositoryRegistryTest.kt` - Added missing `getHeadContent` stub to makeBackend anonymous object (auto-fix Rule 1)

## Decisions Made
- Used `isEnabledAndVisible` (not just `isEnabled`) so the action is hidden rather than grayed out when selection count != 2 — consistent with plan spec
- Left/right assignment follows tree row order (top row = left), not click order — documented known limitation from RESEARCH.md Pitfall 5

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed test compilation error — missing getHeadContent stub in GitRepositoryRegistryTest**
- **Found during:** Task 2 (plugin.xml registration + test run)
- **Issue:** `GitRepositoryRegistryTest.makeBackend` anonymous object did not implement `getHeadContent`, which was added to the `GitBackend` interface in plan 02-01. This caused `./gradlew test` to fail with "Class is not abstract and does not implement abstract member 'getHeadContent'".
- **Fix:** Added `override fun getHeadContent(path: String): ByteArray? = null` to the anonymous object stub
- **Files modified:** `src/test/kotlin/ro/faur/explorer/light/GitRepositoryRegistryTest.kt`
- **Verification:** `./gradlew test` BUILD SUCCESSFUL
- **Committed in:** `ddd4ce9` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — pre-existing bug from prior plan)
**Impact on plan:** Required fix to make tests pass. No scope creep.

## Issues Encountered
- `RemoteGitBackend.kt` had working-tree changes not yet committed (from a prior working session) — these were already correctly implementing `getHeadContent` and were included in the Task 2 commit. Build succeeded with these changes present.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- CompareWithAction is complete and ready for use
- The full diff viewer infrastructure (plans 01-03) is now in place: GitBackend interface with getHeadContent, diff viewer panel, and context-menu compare action

---
*Phase: 02-pre-commit-diff-viewer*
*Completed: 2026-02-28*
