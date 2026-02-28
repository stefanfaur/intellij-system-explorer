# Phase 3: Drag and Drop Polish - Context

**Gathered:** 2026-02-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Dragging files within the explorer and into IntelliJ's project view is reliable, visually clear, and leaves both source and destination in a consistent state. Covers DND-01 through DND-06: drop highlighting, auto-scroll, ghost image, VFS refresh, and reliability fixes. File creation, deletion, VCS integration, and context menus are separate phases.

</domain>

<decisions>
## Implementation Decisions

### Drop highlight style
- Full row background color (not border/outline)
- Only directory rows get highlighted — file nodes do not highlight (dropping on a file targets its parent dir anyway)
- Use IntelliJ theme colors (JBColor/UIManager) so the highlight adapts to light and dark themes
- Highlight clears immediately on drop or when the cursor exits the tree — no post-drop linger

### Ghost image appearance
- Single file: file type icon + filename text, ~70% opacity (translucent)
- Multiple files: first filename + "(+N more)" badge, same ~70% opacity
- Translucent throughout drag to keep the drop target visible beneath

### Conflict resolution
- When a file with the same name already exists at the destination: show a confirmation dialog ("Replace existing file?")
- When dragging multiple files with several conflicts: one dialog per conflicting file (not a single bulk dialog)
- Non-conflict errors (permissions, I/O): show an IntelliJ balloon notification — replace the current silent LOG.warn behavior
- The existing per-file try-catch loop in performDrop() is the right place to add this

### Multi-file drag
- All selected files and directories drag as a batch (not just the clicked item)
- Mix of files and directories in a single drag is allowed
- Default action = move; holding a modifier key (Option/Alt on macOS, Ctrl on Windows/Linux) = copy
- DragDropHandler already checks DnDAction.MOVE — modifier key wiring is the missing piece

### Claude's Discretion
- Auto-scroll speed and threshold distance (pixels from edge to trigger scroll)
- Exact ghost image layout/font/padding
- Which IntelliJ notification API to use for error balloons (NotificationGroupManager vs legacy)
- Confirmation dialog button labels and wording

</decisions>

<specifics>
## Specific Ideas

No specific UI references given — open to standard IntelliJ conventions throughout.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DragDropHandler` (actions/DragDropHandler.kt): handles drops INTO explorer from IntelliJ DnDManager and native sources. Has `performDrop()`, `resolveDropTarget()`, `extractFiles()` — conflict logic and error notifications go here.
- `FileTreeTransferHandler` (actions/FileTreeTransferHandler.kt): Swing TransferHandler for drag OUT from explorer. `createTransferable()` already packs all selected files. Ghost image customization via `DragSource.setDragImage()` or overriding `getVisualRepresentation()` goes here.
- `FileTreeComponent` (ui/FileTreeComponent.kt): registers both Swing DnD (`tree.dragEnabled = true`) and IntelliJ `DnDManager.registerTarget()`. Drop highlight painting and auto-scroll wiring go here.

### Established Patterns
- Two-system DnD architecture is intentional: Swing TransferHandler handles drag-OUT, DnDNativeTarget handles drops-IN. Don't collapse them — they conflict over mouse events.
- `fileTreeComponent.refresh()` is the VFS refresh call. Already called in both `DragDropHandler.drop()` and `FileTreeTransferHandler.exportDone()`.
- No existing highlight, ghost image, or auto-scroll code — all three need to be added.

### Integration Points
- Row highlight: override `FileTreeComponent`'s tree cell renderer or use a custom `DropTargetListener` to repaint the hovered row
- Auto-scroll: install `Autoscroll` behavior on the JTree (Swing's built-in `setAutoscrolls(true)`) or a custom `javax.swing.Timer`-based scroller
- Ghost image: override `TransferHandler.getVisualRepresentation()` or use `DragGestureEvent.startDrag()` with a custom `DragSourceContext` image

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 03-drag-and-drop-polish*
*Context gathered: 2026-02-28*
