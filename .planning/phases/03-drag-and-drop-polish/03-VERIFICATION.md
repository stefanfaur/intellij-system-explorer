---
phase: 03-drag-and-drop-polish
verified: 2026-02-28T00:00:00Z
status: human_needed
score: 5/5 automated must-haves verified
re_verification: false
human_verification:
  - test: "DND-02 — Drop row highlight on directories only"
    expected: "Directory rows glow with selection-background color during drag; file rows do NOT highlight; highlight clears immediately on drop or cursor leave"
    why_human: "Visual rendering behavior requires a live drag gesture to observe; cannot be verified by reading code alone"
  - test: "DND-04 — Ghost drag image follows cursor"
    expected: "A translucent image (file icon + filename, ~70% opacity) follows the cursor. Multi-file drag shows 'filename (+N more)' badge."
    why_human: "Ghost image is set via TransferHandler.setDragImage(); actual rendering is platform-native and requires a running IDE to observe"
  - test: "DND-03 — Auto-scroll during drag"
    expected: "Holding the cursor at the top/bottom edge of the tree while dragging causes the tree to scroll in that direction"
    why_human: "Auto-scroll is enabled implicitly by tree.dragEnabled=true and Swing's DnD infrastructure; functional verification requires a live drag gesture"
  - test: "DND-01 — Drag to IntelliJ project view + modifier-key COPY/MOVE"
    expected: "Files dropped into IntelliJ's Project View panel appear there; holding Option/Ctrl copies instead of moves"
    why_human: "Cross-panel drag-and-drop interaction between Swing TransferHandler and IntelliJ DnDManager requires a running IDE"
  - test: "DND-05 — Drop on file targets parent directory"
    expected: "Dropping a dragged file onto another file (not a directory) places the dragged file in the target file's parent directory"
    why_human: "Drop target resolution for file-vs-directory nodes requires a live drag gesture to confirm the routing is correct"
  - test: "DND-06 — VFS source refresh after move"
    expected: "After a drag-move, the source directory immediately no longer shows the moved file in the explorer tree"
    why_human: "VFS refresh visibility requires a live move operation; cannot confirm the tree actually updates without running the plugin"
  - test: "Conflict dialog — Replace/Skip on filename collision"
    expected: "Dragging onto a directory that already contains a file with the same name shows 'Confirm Replace' dialog; Skip leaves file unchanged; Replace overwrites it"
    why_human: "Dialog invocation path (Messages.showYesNoDialog) requires a real collision scenario in a running IDE"
  - test: "Balloon notification — I/O error surface"
    expected: "When a move/copy operation fails (e.g., permissions error), an IDE balloon notification appears instead of a silent failure"
    why_human: "Error path requires triggering a real I/O failure in a running IDE"
  - test: "No regression in copy/paste/context menu"
    expected: "Right-click Copy, Cut, Paste all continue to work normally after DnD changes"
    why_human: "Context menu interaction requires live IDE testing"
---

# Phase 3: Drag and Drop Polish — Verification Report

**Phase Goal:** Dragging files within the explorer and into IntelliJ's project view is reliable, visually clear, and leaves both source and destination in a consistent state
**Verified:** 2026-02-28
**Status:** human_needed — all automated checks passed; 9 items require human testing in a running IDE
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | Dragging a file from the explorer tree and dropping it into IntelliJ's project view succeeds without conflict or silent failure | ? HUMAN | `FileTreeTransferHandler` creates `javaFileListFlavor` transferable (line 68-83); `DragDropHandler` receives IntelliJ DnDManager drops; conflict pre-check + notifyError wired. Functional verification requires live IDE. |
| 2 | A highlighted row appears on the drop-target directory as the user drags over it | ? HUMAN | `DragDropHandler.update()` writes `dnd.hoveredRow` client property (lines 69-73); `VirtualFileCellRenderer.customizeCellRenderer()` reads it and applies `RenderingUtil.getSelectionBackground(tree)` on directory rows (FileTreeComponent.kt lines 598-602). Visual behavior requires live IDE. |
| 3 | The tree scrolls automatically when the cursor is held near the top or bottom edge during a drag | ? HUMAN | `tree.dragEnabled = true` set in FileTreeComponent.kt (line 207). Swing's built-in auto-scroll activates when `dragEnabled=true` and a TransferHandler is installed. No code verification possible — requires live drag. |
| 4 | A ghost image representing the dragged file(s) follows the cursor throughout the drag gesture | ? HUMAN | `FileTreeTransferHandler.getSourceActions()` calls `buildGhostImage()` then `setDragImage()` + `setDragImageOffset()` (lines 40-48). `buildGhostImage()` creates `TYPE_INT_ARGB` BufferedImage at `AlphaComposite.SRC_OVER` 0.7f (lines 50-66). Visual rendering requires live IDE. |
| 5 | Dropping a file onto another file moves it into the parent directory; after any drag-move, both source and destination directories refresh in the VFS | ? HUMAN | `resolveDropTarget()` in `FileTreeTransferHandler` (lines 172-184) returns `vf.parent` for file nodes. `DragDropHandler.drop()` refreshes sourceParents after isMove (lines 103-106). `FileTreeTransferHandler.exportDone()` also refreshes sourceParents on MOVE (lines 158-165). Functional verification requires live IDE. |

**Score:** 0/5 truths can be confirmed by automated checks alone — all require human IDE testing. All automated evidence (artifact existence, substantive implementation, wiring) is present and verified.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/ro/faur/explorer/actions/DragDropHandler.kt` | Hovered-row tracking + source parent refresh + conflict dialog + notifyError | VERIFIED | `update()` puts `dnd.hoveredRow` (line 71); `drop()` clears it (line 85) and refreshes sourceParents (lines 103-106); `performDrop()` has conflict pre-check with `findChild` (lines 151-163) and `notifyError()` in catch (line 173); `notifyError()` uses `NotificationGroupManager` with `Explorer.DnD` group (lines 178-183). Committed: 2c07470, 4491bbc. |
| `src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt` | VirtualFileCellRenderer paints highlight on hovered directory rows | VERIFIED | Lines 598-602: reads `dnd.hoveredRow`, applies `RenderingUtil.getSelectionBackground(tree)` on directory rows when `!selected && row == hoveredRow && vf.isDirectory`. Import `com.intellij.ui.render.RenderingUtil` present (line 21). Committed: 1f131e3. |
| `src/main/kotlin/ro/faur/explorer/actions/FileTreeTransferHandler.kt` | Ghost image in getSourceActions() + conflict pre-check + balloon notification in importData() | VERIFIED | `getSourceActions()` calls `buildGhostImage()` and `setDragImage()` (lines 40-48). `buildGhostImage()` paints at 0.7f opacity (lines 50-66). `importData()` has `findChild` conflict pre-check (lines 120-132) and balloon notification in catch block (lines 140-148). `exportDone()` refreshes source parents (lines 158-165). Committed: c7fdea4. |
| `src/main/resources/META-INF/plugin.xml` | Explorer.DnD notificationGroup BALLOON registration | VERIFIED | Line 82: `<notificationGroup id="Explorer.DnD" displayType="BALLOON"/>`. Committed: 4491bbc. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `DragDropHandler.update()` | `tree.getClientProperty("dnd.hoveredRow")` | `tree.putClientProperty("dnd.hoveredRow", highlightRow); tree.repaint()` | WIRED | DragDropHandler.kt lines 69-73: reads current, writes only on change, calls repaint. Guard pattern implemented correctly. |
| `VirtualFileCellRenderer.customizeCellRenderer()` | `tree.getClientProperty("dnd.hoveredRow")` | reads hoveredRow, paints background when row matches and node is directory | WIRED | FileTreeComponent.kt lines 598-602: `val hoveredRow = tree.getClientProperty("dnd.hoveredRow") as? Int ?: -1` then condition check with `!selected && row == hoveredRow && vf.isDirectory`. |
| `DragDropHandler.drop()` | source parent `VirtualFile.refresh()` | `filesToDrop.map { it.parent }.toSet().forEach { it?.refresh(false, false) }` | WIRED | DragDropHandler.kt lines 103-106: guard `if (isMove)` around sourceParents refresh. Pattern matches plan spec. |
| `FileTreeTransferHandler.getSourceActions()` | `buildGhostImage()` | `setDragImage(img); setDragImageOffset(Point(img.width/2, img.height/2))` | WIRED | FileTreeTransferHandler.kt lines 40-48: exact pattern present. |
| `DragDropHandler.performDrop()` | `targetDir.findChild(file.name)` | conflict pre-check before copy/move; `Messages.showYesNoDialog` on conflict | WIRED | FileTreeTransferHandler.kt lines 119-132 and DragDropHandler.kt lines 151-163: both code paths have `findChild` pre-check and `showYesNoDialog`. |
| `FileTreeTransferHandler.importData()` catch block | `notifyError()` | `NotificationGroupManager` balloon replaces silent `LOG.warn` | WIRED | FileTreeTransferHandler.kt lines 140-148: `LOG.warn` kept AND `NotificationGroupManager.getInstance().getNotificationGroup("Explorer.DnD")` balloon added. DragDropHandler.kt line 173: `notifyError()` called in catch. |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| DND-01 | 03-02-PLAN.md | Drag to IntelliJ project view without conflict or silent failure | SATISFIED (human verify) | `FileTreeTransferHandler` creates `javaFileListFlavor` transferable; modifier-key COPY/MOVE handled by `support.dropAction == MOVE` in `importData()` (line 102); `resolveDropTarget()` resolves target directory from both DnDManager and Swing paths. |
| DND-02 | 03-01-PLAN.md | Drop-target highlight row when dragging over valid directory | SATISFIED (human verify) | `dnd.hoveredRow` client property tracked in `DragDropHandler.update()` (lines 69-73) and painted in `VirtualFileCellRenderer` (lines 598-602). Guard ensures only directories highlight. |
| DND-03 | 03-02-PLAN.md | Auto-scroll when dragging near top/bottom edge | SATISFIED (human verify) | `tree.dragEnabled = true` (FileTreeComponent.kt line 207) enables Swing's built-in auto-scroll. No additional code needed per plan RESEARCH.md. |
| DND-04 | 03-02-PLAN.md | Ghost drag image during drag gesture | SATISFIED (human verify) | `buildGhostImage()` in `FileTreeTransferHandler` (lines 50-66); `setDragImage()` called in `getSourceActions()` (line 44). `TYPE_INT_ARGB` at 0.7f opacity. |
| DND-05 | 03-02-PLAN.md | Dropping on a file targets parent directory | SATISFIED (human verify) | `resolveDropTarget()` in `FileTreeTransferHandler` (line 179): `if (vf.isDirectory) vf else vf.parent`. `resolveTargetDirectory()` in `DragDropHandler` (line 138): same pattern. Both code paths handle file-to-parent. |
| DND-06 | 03-01-PLAN.md | VFS refresh in source and destination after drag-move | SATISFIED (human verify) | `DragDropHandler.drop()` refreshes sourceParents on isMove (lines 103-106); `FileTreeTransferHandler.exportDone()` refreshes sourceParents on MOVE action (lines 158-164); both also call `fileTreeComponent.refresh()` for destination. |

### Anti-Patterns Found

No blockers or warnings found.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `DragDropHandler.kt` | 125, 137, 208 | `return null` | Info | Valid early-exit guard clauses, not stubs |
| `FileTreeTransferHandler.kt` | 70, 203 | `return null` | Info | Valid early-exit guard clauses, not stubs |

All `return null` occurrences are null-safety guards on optional values (`?: return null`), not placeholder implementations. No TODO/FIXME/HACK/placeholder comments found in any modified file.

### Human Verification Required

All DnD behaviors are visual and interactive. The automated checks confirm all code artifacts exist, are substantive, and are correctly wired. The following must be confirmed in a running IntelliJ IDE instance via `./gradlew runIde`:

#### 1. DND-02 — Drop Row Highlight

**Test:** Open System Explorer tool window. Drag any file slowly over the tree.
**Expected:** Directory rows show a blue/selection-color background as cursor passes over them. File rows do NOT highlight. Highlight clears immediately when cursor leaves or drop completes.
**Why human:** Visual rendering requires a live drag gesture.

#### 2. DND-04 — Ghost Drag Image

**Test:** Start dragging a single file. Then drag multiple files.
**Expected:** Single file: translucent image showing file icon + filename. Multiple files: image shows "filename (+N more)" badge. Both visibly translucent (not fully opaque).
**Why human:** Ghost image platform rendering requires a running IDE to observe.

#### 3. DND-03 — Auto-Scroll

**Test:** Open a directory with many files so the tree overflows. Start dragging a file, hold cursor at the top edge, then the bottom edge.
**Expected:** Tree scrolls upward at top edge, downward at bottom edge.
**Why human:** Swing auto-scroll via `dragEnabled=true` cannot be confirmed without a live drag.

#### 4. DND-01 — Drag to IntelliJ Project View + Modifier-Key COPY/MOVE

**Test:** Drag a file from System Explorer and drop onto IntelliJ's Project View. Then repeat holding Option (macOS) / Ctrl (Windows/Linux).
**Expected:** Plain drop moves the file; modifier-key drop copies it. No error or silent failure.
**Why human:** Cross-panel DnD interaction requires a running IDE.

#### 5. DND-05 — Drop on File Targets Parent Directory

**Test:** Drag a file and drop it directly onto another file node (not a directory).
**Expected:** The dragged file moves into the target file's parent directory.
**Why human:** Requires a live drop gesture to confirm routing.

#### 6. DND-06 — VFS Source Refresh After Move

**Test:** Drag a file from one directory to another (move, no modifier key).
**Expected:** The source directory immediately no longer shows the moved file (no stale entry).
**Why human:** Requires observing tree refresh behavior after a live move.

#### 7. Conflict Dialog — Replace/Skip

**Test:** Drag a file into a directory that already contains a file with the same name.
**Expected:** "Confirm Replace" dialog appears with "Replace" and "Skip" buttons. Skip leaves file unchanged. Replace overwrites.
**Why human:** Requires triggering a real filename collision in a live drop.

#### 8. Balloon Notification — I/O Error Surface

**Test:** Trigger a move/copy that fails (e.g., permission-restricted destination).
**Expected:** An IDE balloon notification appears with the error message instead of silent failure.
**Why human:** Requires triggering a real I/O failure in a running IDE.

#### 9. No Regression in Copy/Paste/Context Menu

**Test:** Right-click a file and use Copy, Cut, Paste from the context menu.
**Expected:** All operations work normally; no interference from DnD changes.
**Why human:** Context menu interaction requires a running IDE.

### Commits Verified

All 4 commits referenced in summaries exist in git history:
- `2c07470` — feat(03-01): add hovered-row tracking and source-parent refresh to DragDropHandler
- `1f131e3` — feat(03-01): paint drop-hover highlight on directory rows in VirtualFileCellRenderer
- `c7fdea4` — feat(03-02): add ghost drag image, conflict pre-check, and balloon notification to FileTreeTransferHandler
- `4491bbc` — feat(03-02): add conflict pre-check and notifyError to DragDropHandler; register Explorer.DnD notification group

### Gaps Summary

No gaps found in automated verification. All artifacts exist, are substantive implementations (not stubs), and all key links are wired. Plan 03-03 is explicitly a human-verify checkpoint — the phase design requires manual testing in a running IDE before phase completion can be declared.

The one deviation from the plans that affects verification: `AllIcons.Nodes.MultipleFiles` does not exist in IJ 2024.3 and was replaced by `AllIcons.FileTypes.Any_type` for the multi-file ghost icon. This is a functionally equivalent substitution — the ghost image still renders; it just uses a generic file-type icon instead of a multi-file icon for batches. This should be confirmed as acceptable during human testing.

---

_Verified: 2026-02-28_
_Verifier: Claude (gsd-verifier)_
