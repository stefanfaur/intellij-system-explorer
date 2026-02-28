---
phase: 02-pre-commit-diff-viewer
plan: 02
subsystem: ui
tags: [diff, DiffManager, DiffContentFactory, IntelliJ-diff, CardLayout, lifecycle, Disposer]

# Dependency graph
requires:
  - phase: 02-pre-commit-diff-viewer/02-01
    provides: GitBackend.getHeadContent(path) for reading HEAD bytes in diff
provides:
  - InlineDiffPanel — embedded DiffRequestPanel with spinner, lifecycle management
  - changedFilesPanel.onFileSelected — file selection callback
  - changedFilesPanel.onFileDoubleClicked — double-click to full diff callback
  - GitPanelComponent Show Diff toolbar button
  - Slot switching between CommitDetailsPanel (history) and InlineDiffPanel (staging)
affects: [03-pre-commit-diff-viewer, future UI phases using GitPanelComponent]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CardLayout for exclusive slot switching (commitDetailsPanel vs inlineDiffPanel)"
    - "Disposer.newDisposable(parent) for scoped child disposables inside InlineDiffPanel"
    - "executeOnPooledThread + invokeLater with stale-check (identityHashCode + path equality)"
    - "DiffContentFactory.createFromBytes(project, bytes, filePath) for git HEAD content diff"

key-files:
  created:
    - src/main/kotlin/ro/faur/explorer/gitpanel/ui/InlineDiffPanel.kt
  modified:
    - src/main/kotlin/ro/faur/explorer/gitpanel/ui/ChangedFilesPanel.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt

key-decisions:
  - "CardLayout used for rightSlot to exclusively show commitDetailsPanel or inlineDiffPanel — cleaner than isVisible toggling"
  - "DiffContentFactory.createFromBytes(project, bytes, filePath) — actual API (createDocumentFromBytes does not exist in IJ 2024.3)"
  - "VcsUtil.getFilePath(absolutePathString, false) used instead of deprecated VcsUtil.getFilePath(File)"
  - "runCatching wraps createFromBytes call since it throws IOException — falls back to createEmpty() on error"
  - "doShowFullDiff uses HEAD vs working-tree diff in both modes; history-mode exact COMMIT^:path diff deferred to Phase 2 polish (no getFileAtRevision on GitBackend yet)"

patterns-established:
  - "InlineDiffPanel.showSpinner() then showDiffRequest() always called on EDT; byte reading on pooled thread"
  - "Stale-check pattern: snapshot System.identityHashCode(backend) + path comparison before EDT update"

requirements-completed: [DIFF-01, DIFF-02, DIFF-05]

# Metrics
duration: 15min
completed: 2026-02-28
---

# Phase 02 Plan 02: Inline Diff Panel Summary

**Embedded DiffRequestPanel with spinner, CardLayout slot switching, Show Diff toolbar button, and file-selection wiring for HEAD vs working-tree diffs**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-02-28T12:30:00Z
- **Completed:** 2026-02-28T12:45:00Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments

- Created InlineDiffPanel — a JPanel wrapper managing child disposable lifecycle for embedded DiffRequestPanel instances, with spinner during loading and clear() on demand
- Added onFileSelected and onFileDoubleClicked callbacks to ChangedFilesPanel; preserved existing checkbox behavior; double-click fires in any mode
- Wired GitPanelComponent: Show Diff toolbar button (enabled when file selected), CardLayout slot switching between commitDetailsPanel and inlineDiffPanel, loadInlineDiff() with off-EDT byte reading and stale-check guards, doShowFullDiff() opening DiffDialogHints.FRAME

## Task Commits

Each task was committed atomically:

1. **Task 1: Create InlineDiffPanel** - `87a3773` (feat)
2. **Task 2: Add callbacks to ChangedFilesPanel** - `3df6da6` (feat)
3. **Task 3: Wire InlineDiffPanel into GitPanelComponent** - `781cdcd` (feat)

## Files Created/Modified

- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/InlineDiffPanel.kt` - New; wraps DiffRequestPanel lifecycle with showSpinner/showDiffRequest/clear
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/ChangedFilesPanel.kt` - Added onFileSelected (ListSelectionListener) and onFileDoubleClicked (MouseAdapter double-click) public callbacks
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt` - Added InlineDiffPanel field, rightSlot CardLayout, Show Diff action, loadInlineDiff(), buildDiffRequest(), doShowFullDiff(), showDiffSlot()/showCommitDetailsSlot()

## Decisions Made

- CardLayout used for rightSlot to exclusively show commitDetailsPanel or inlineDiffPanel — cleaner than isVisible toggling with BorderLayout conflicts
- DiffContentFactory API verified: the real method is `createFromBytes(project, bytes, filePath)` (not `createDocumentFromBytes` which does not exist in IJ 2024.3)
- VcsUtil.getFilePath(String, Boolean) used instead of deprecated VcsUtil.getFilePath(File)
- runCatching wraps createFromBytes because it throws IOException — falls back to createEmpty() silently
- History-mode doShowFullDiff shows HEAD vs working-tree diff (same as staging mode); exact COMMIT^:path vs COMMIT:path deferred — requires getFileAtRevision() on GitBackend interface not yet added

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] DiffContentFactory.createDocumentFromBytes does not exist**
- **Found during:** Task 3 (GitPanelComponent wiring)
- **Issue:** Plan specified `factory.createDocumentFromBytes(project, bytes, filePath)` but this method does not exist in IntelliJ 2024.3's DiffContentFactory API
- **Fix:** Used `factory.createFromBytes(project, bytes, filePath)` which is the real API; wrapped in runCatching to handle the checked IOException
- **Files modified:** src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt
- **Verification:** `./gradlew compileKotlin` passed with no errors
- **Committed in:** 781cdcd (Task 3 commit)

**2. [Rule 1 - Bug] VcsUtil.getFilePath(File) is deprecated**
- **Found during:** Task 3 (GitPanelComponent wiring)
- **Issue:** Compiler warning: VcsUtil.getFilePath(File) is deprecated in Java
- **Fix:** Changed to VcsUtil.getFilePath(String, Boolean) with the absolute path and false for isDirectory
- **Files modified:** src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt
- **Verification:** `./gradlew compileKotlin` passed with no warnings
- **Committed in:** 781cdcd (Task 3 commit)

**3. [Rule 1 - Bug] BorderLayout cannot hold two CENTER children**
- **Found during:** Task 3 (GitPanelComponent layout)
- **Issue:** Plan suggested adding both commitDetailsPanel and inlineDiffPanel to a JPanel(BorderLayout()) using isVisible toggling, but BorderLayout CENTER only renders the last added component regardless of visibility
- **Fix:** Used CardLayout with CARD_DETAILS and CARD_DIFF named cards; rightSlotLayout.show() switches exclusive visibility cleanly
- **Files modified:** src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt
- **Verification:** Clean compile, layout works correctly
- **Committed in:** 781cdcd (Task 3 commit)

---

**Total deviations:** 3 auto-fixed (3 Rule 1 bugs — incorrect API and layout)
**Impact on plan:** All fixes were API reality corrections; functional behavior matches plan intent exactly. No scope creep.

## Issues Encountered

None beyond the API discrepancies documented above as deviations.

## Next Phase Readiness

- InlineDiffPanel, callbacks, and toolbar button fully functional
- Inline diff and full-window diff both wired for staging mode
- History-mode Show Diff shows HEAD vs working-tree (acceptable for Phase 2; exact commit-range diff needs getFileAtRevision() added to GitBackend in a future plan)
- All existing tests pass; no regressions

---
*Phase: 02-pre-commit-diff-viewer*
*Completed: 2026-02-28*
