---
phase: 03-drag-and-drop-polish
plan: 01
subsystem: ui
tags: [dnd, drag-and-drop, jtree, vfs, swing, intellij-platform]

# Dependency graph
requires: []
provides:
  - "DragDropHandler tracks hovered row via dnd.hoveredRow JTree client property"
  - "VirtualFileCellRenderer paints theme-aware selection background on hovered directory rows during drag"
  - "DragDropHandler clears hover highlight on drop"
  - "DragDropHandler refreshes source parent directories after move (DND-06)"
affects: [03-02]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JTree client property (dnd.hoveredRow) as side-channel from DnDTarget to cell renderer — avoids direct coupling"
    - "Repaint guard: only call putClientProperty + repaint() when row actually changes to avoid flicker"
    - "com.intellij.ui.render.RenderingUtil.getSelectionBackground(tree) for theme-aware highlight color"

key-files:
  created: []
  modified:
    - src/main/kotlin/ro/faur/explorer/actions/DragDropHandler.kt
    - src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt

key-decisions:
  - "RenderingUtil is at com.intellij.ui.render.RenderingUtil (not com.intellij.ui.RenderingUtil) in IJ 2024.3+"
  - "Source-parent refresh only runs when isMove=true — copy operations do not need to invalidate the source"
  - "Hover highlight cleared in drop() only (cleanUpOnLeave not needed for current platform version)"

patterns-established:
  - "JTree client property side-channel: DnDTarget writes, cell renderer reads — zero coupling between the two classes"

requirements-completed: [DND-02, DND-06]

# Metrics
duration: 4min
completed: 2026-02-28
---

# Phase 3 Plan 01: Drag-and-Drop Polish — Drop-Row Highlighting and Source-Dir VFS Refresh Summary

**Drop-row hover highlight (theme-aware, directories only) via JTree client property + VirtualFileCellRenderer, and VFS source-parent refresh on move**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-28T19:15:31Z
- **Completed:** 2026-02-28T19:20:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- DragDropHandler.update() writes `dnd.hoveredRow` client property to the JTree and only calls repaint() when the row actually changes (avoids per-mousemove flicker)
- DragDropHandler.drop() clears the hover highlight before processing the drop
- DragDropHandler.drop() refreshes source parent directories after a move using `it?.refresh(false, false)` (DND-06)
- VirtualFileCellRenderer reads `dnd.hoveredRow` from the tree and paints `RenderingUtil.getSelectionBackground(tree)` on unselected directory rows matching the hovered row (DND-02)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add hovered-row tracking to DragDropHandler** - `2c07470` (feat)
2. **Task 2: Paint drop-highlight in VirtualFileCellRenderer** - `1f131e3` (feat)

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/actions/DragDropHandler.kt` - update() tracks hovered row; drop() clears highlight and refreshes source parents
- `src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt` - added `com.intellij.ui.render.RenderingUtil` import; VirtualFileCellRenderer paints selection background on hovered directory rows

## Decisions Made
- `RenderingUtil` is at `com.intellij.ui.render.RenderingUtil` (not `com.intellij.ui.RenderingUtil`) in IJ 2024.3 — the RESEARCH.md reference was incomplete on the package path
- Source-parent refresh only runs when `isMove=true` — copy operations do not need to invalidate source directory VFS state
- No `cleanUpOnLeave()` override needed; `DnDNativeTarget` in 2024.3 exposes it but clearing in `drop()` alone is sufficient per plan specification

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed incorrect RenderingUtil package path**
- **Found during:** Task 2 (VirtualFileCellRenderer highlight implementation)
- **Issue:** Plan specified `com.intellij.ui.RenderingUtil` but the actual class in IJ 2024.3 is at `com.intellij.ui.render.RenderingUtil` — compilation failed with "Unresolved reference"
- **Fix:** Discovered correct path by scanning `app-client.jar` via `jar tf`, updated import to `com.intellij.ui.render.RenderingUtil`
- **Files modified:** `src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt`
- **Verification:** `./gradlew compileKotlin` exits 0 after fix
- **Committed in:** `1f131e3` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary correction to compile. No scope creep.

## Issues Encountered
- RenderingUtil package path mismatch between RESEARCH.md and actual IJ 2024.3 JAR structure — resolved by inspecting `app-client.jar` contents directly

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- DND-02 and DND-06 complete; plan 03-02 can now read the final state of both files
- `dnd.hoveredRow` client property contract is established and stable for plan 03-02 to extend if needed

---
*Phase: 03-drag-and-drop-polish*
*Completed: 2026-02-28*
