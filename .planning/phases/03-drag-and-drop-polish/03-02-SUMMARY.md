---
phase: 03-drag-and-drop-polish
plan: 02
subsystem: ui
tags: [drag-and-drop, swing, transferhandler, notifications, conflict-dialog]

# Dependency graph
requires:
  - phase: 03-01
    provides: hoveredRow tracking, DragDropHandler, source-parent VFS refresh on move
provides:
  - Ghost drag image (file icon + filename at 70% opacity) during drag-out from explorer
  - Conflict pre-check dialog before overwriting existing files (both DragDropHandler and FileTreeTransferHandler paths)
  - Balloon error notifications for I/O failures via Explorer.DnD notification group
  - exportDone source-parent refresh for Swing drag-out path
affects: [03-drag-and-drop-polish, dnd-requirements]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Ghost image: buildGhostImage() creates a TYPE_INT_ARGB BufferedImage with AlphaComposite.SRC_OVER 0.7f"
    - "Conflict pre-check: targetDir.findChild(file.name) before copy/move; runWriteAction to delete existing on YES"
    - "Error notification: NotificationGroupManager.getInstance().getNotificationGroup(Explorer.DnD) balloon"

key-files:
  created: []
  modified:
    - src/main/kotlin/ro/faur/explorer/actions/FileTreeTransferHandler.kt
    - src/main/kotlin/ro/faur/explorer/actions/DragDropHandler.kt
    - src/main/resources/META-INF/plugin.xml

key-decisions:
  - "AllIcons.Nodes.MultipleFiles does not exist in IJ 2024.3 — AllIcons.FileTypes.Any_type used as multi-file ghost icon fallback"
  - "Messages.showYesNoDialog in FileTreeTransferHandler uses null Project (no project reference available in TransferHandler)"
  - "Balloon notification in FileTreeTransferHandler.importData() uses null project (application-level notification)"
  - "LOG.warn kept in addition to balloon notification so errors appear in IDE logs as well as UI"

patterns-established:
  - "Conflict pre-check pattern: findChild check + YesNoDialog + runWriteAction delete + continue on skip"
  - "Error balloon pattern: NotificationGroupManager + Explorer.DnD group + WARNING type + notify(project)"

requirements-completed: [DND-01, DND-03, DND-04, DND-05]

# Metrics
duration: 8min
completed: 2026-02-28
---

# Phase 3 Plan 02: Drag-and-Drop Polish Summary

**Ghost drag image at 70% opacity, conflict replace dialogs, and balloon error notifications for all DnD I/O failures**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-28T19:10:00Z
- **Completed:** 2026-02-28T19:18:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- FileTreeTransferHandler.getSourceActions() now builds and sets a translucent ghost drag image (file icon + name, 0.7f opacity) using UIUtil.createImage
- Conflict pre-check added to both DragDropHandler.performDrop() and FileTreeTransferHandler.importData() — shows YesNoDialog before overwriting, skips file on cancel
- DragDropHandler.notifyError() added using NotificationGroupManager with Explorer.DnD group; replaces silent LOG.warn in catch block
- plugin.xml gains Explorer.DnD notificationGroup BALLOON registration
- exportDone() in FileTreeTransferHandler now refreshes source parent directories before full tree refresh on MOVE

## Task Commits

Each task was committed atomically:

1. **Task 1: Ghost image + conflict pre-check + balloon in FileTreeTransferHandler** - `c7fdea4` (feat)
2. **Task 2: Conflict pre-check + notifyError in DragDropHandler + plugin.xml** - `4491bbc` (feat)

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/actions/FileTreeTransferHandler.kt` - Added buildGhostImage(), getSourceActions() override, conflict pre-check in importData(), balloon notification in catch, exportDone source-parent refresh
- `src/main/kotlin/ro/faur/explorer/actions/DragDropHandler.kt` - Added conflict pre-check in performDrop(), notifyError() helper, required imports
- `src/main/resources/META-INF/plugin.xml` - Added Explorer.DnD notificationGroup BALLOON registration

## Decisions Made
- `AllIcons.Nodes.MultipleFiles` does not exist in IJ 2024.3; used `AllIcons.FileTypes.Any_type` as the multi-file ghost icon fallback
- `Messages.showYesNoDialog` in `FileTreeTransferHandler` passes `null` for Project parameter since TransferHandler has no project reference — dialog still appears as application-modal
- `notify(null)` used for balloon in `FileTreeTransferHandler.importData()` (application-level, no project) vs `notify(project)` in `DragDropHandler.notifyError()` (has project reference)
- Kept `LOG.warn` alongside balloon notification so errors remain visible in IDE logs

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] AllIcons.Nodes.MultipleFiles unresolved reference**
- **Found during:** Task 1 (ghost image implementation)
- **Issue:** `AllIcons.Nodes.MultipleFiles` does not exist in IntelliJ 2024.3 API — compilation error
- **Fix:** Replaced with `AllIcons.FileTypes.Any_type` as the fallback icon for multi-file ghost
- **Files modified:** `src/main/kotlin/ro/faur/explorer/actions/FileTreeTransferHandler.kt`
- **Verification:** `./gradlew compileKotlin` exits 0
- **Committed in:** c7fdea4 (Task 1 commit)

**2. [Rule 1 - Bug] Messages.showYesNoDialog component-parent overload does not exist**
- **Found during:** Task 1 (conflict pre-check in FileTreeTransferHandler)
- **Issue:** Plan specified `fileTreeComponent.tree` (a JComponent) as first arg but no such overload exists; valid overloads use Project? or String
- **Fix:** Changed first argument to `null as Project?` (application-modal dialog, no project needed)
- **Files modified:** `src/main/kotlin/ro/faur/explorer/actions/FileTreeTransferHandler.kt`
- **Verification:** `./gradlew compileKotlin` exits 0
- **Committed in:** c7fdea4 (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - API mismatch bugs caught at compile time)
**Impact on plan:** Both fixes resolved API incompatibilities between plan research and actual IJ 2024.3 API. No functional scope change.

## Issues Encountered
- Research.md pattern used `AllIcons.Nodes.MultipleFiles` which does not exist in IJ 2024.3 — substituted with `AllIcons.FileTypes.Any_type`
- Research.md conflict pattern used `Messages.showYesNoDialog(component, ...)` which has no matching overload — used `Messages.showYesNoDialog(project?, ...)` instead

## Next Phase Readiness
- DND-01 (modifier COPY/MOVE), DND-03 (auto-scroll), DND-04 (ghost image), DND-05 (file→parent target) all complete
- Phase 03 drag-and-drop polish is fully complete (plans 01 and 02 delivered all DND requirements)
- Ready for next phase

---
*Phase: 03-drag-and-drop-polish*
*Completed: 2026-02-28*
