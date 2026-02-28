# Phase 3: Drag and Drop Polish - Research

**Researched:** 2026-02-28
**Domain:** IntelliJ Platform DnD, Swing TransferHandler, JTree row highlighting, ghost drag image, VFS conflict resolution
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Drop highlight style
- Full row background color (not border/outline)
- Only directory rows get highlighted — file nodes do not highlight (dropping on a file targets its parent dir anyway)
- Use IntelliJ theme colors (JBColor/UIManager) so the highlight adapts to light and dark themes
- Highlight clears immediately on drop or when the cursor exits the tree — no post-drop linger

#### Ghost image appearance
- Single file: file type icon + filename text, ~70% opacity (translucent)
- Multiple files: first filename + "(+N more)" badge, same ~70% opacity
- Translucent throughout drag to keep the drop target visible beneath

#### Conflict resolution
- When a file with the same name already exists at the destination: show a confirmation dialog ("Replace existing file?")
- When dragging multiple files with several conflicts: one dialog per conflicting file (not a single bulk dialog)
- Non-conflict errors (permissions, I/O): show an IntelliJ balloon notification — replace the current silent LOG.warn behavior
- The existing per-file try-catch loop in performDrop() is the right place to add this

#### Multi-file drag
- All selected files and directories drag as a batch (not just the clicked item)
- Mix of files and directories in a single drag is allowed
- Default action = move; holding a modifier key (Option/Alt on macOS, Ctrl on Windows/Linux) = copy
- DragDropHandler already checks DnDAction.MOVE — modifier key wiring is the missing piece

### Claude's Discretion
- Auto-scroll speed and threshold distance (pixels from edge to trigger scroll)
- Exact ghost image layout/font/padding
- Which IntelliJ notification API to use for error balloons (NotificationGroupManager vs legacy)
- Confirmation dialog button labels and wording

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DND-01 | User can drag a file from the explorer tree and drop it into IntelliJ's project view without conflict or silent failure | Modifier key wiring for COPY/MOVE; conflict pre-check before VirtualFile.copy(); balloon notifications for I/O errors |
| DND-02 | User sees a drop-target highlight row when dragging over a valid directory in the explorer | DragDropHandler.update() + repaint hovered row; pass hoveredRow through to VirtualFileCellRenderer via component property |
| DND-03 | Tree auto-scrolls when user drags near the top or bottom edge | SmoothAutoScroller.installDropTargetAsNecessary() called automatically by Tree.setTransferHandler(); already active once TransferHandler is set |
| DND-04 | User sees a ghost drag image representing the dragged file(s) during the drag gesture | Override FileTreeTransferHandler.getSourceActions() to call setDragImage()/setDragImageOffset() with a custom BufferedImage at ~70% opacity |
| DND-05 | Dropping a file onto another file targets the parent directory (not the file itself) | resolveTargetDirectory() already implements this; verify DnDManager path and FileTreeTransferHandler both call the same logic |
| DND-06 | VFS is refreshed in both source and destination directories after a drag-move completes | fileTreeComponent.refresh() already called in drop() and exportDone(); add refresh of source parent on move |
</phase_requirements>

## Summary

Phase 3 polishes six drag-and-drop requirements on an existing two-system DnD architecture: Swing `TransferHandler` handles drag-out from the explorer, and IntelliJ's `DnDNativeTarget` (via `DragDropHandler`) receives drops into the explorer. Both systems are already wired in `FileTreeComponent`; no new integration is needed. This phase adds the visual polish layer on top: row highlighting, ghost image, and auto-scroll (DND-02/03/04), fixes reliability issues around modifier-key COPY action and conflict handling (DND-01), and ensures VFS refresh covers source as well as destination (DND-06).

Auto-scroll is the easiest win: IntelliJ's `Tree` class calls `SmoothAutoScroller.installDropTargetAsNecessary(this)` inside `setTransferHandler()`. Because `FileTreeComponent` already calls `tree.transferHandler = FileTreeTransferHandler(this)`, the auto-scroller is already installed for free — DND-03 may need no new code beyond verification.

Drop highlight requires coordinating two touch points: `DragDropHandler.update()` (already called on every drag-over event by DnDManager) must record the hovered row index on the component, and `VirtualFileCellRenderer.customizeCellRenderer()` must paint that row with a theme-aware background. Ghost image requires overriding `getSourceActions()` in `FileTreeTransferHandler` to call `setDragImage()` with a custom `BufferedImage` rendered from selected file metadata at ~70% AlphaComposite opacity. Conflict detection must happen before `VirtualFile.copy()`/`move()` by checking `destDir.findChild(file.name) != null`.

**Primary recommendation:** Implement each requirement as a targeted, minimal change on the existing classes — no new files needed except possibly a helper method. Prioritize DND-05 verification, DND-03 auto-scroll verification, then add highlight, ghost image, and conflict handling in that order.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.intellij.ui.treeStructure.Tree` | Platform 2025.1 (bundled) | JTree subclass used by this plugin | Already in use; provides SmoothAutoScroller auto-install and wide-selection painting |
| `com.intellij.ide.dnd.SmoothAutoScroller` | Platform 2025.1 (bundled) | Auto-scroll during drag | Installed automatically via Tree.setTransferHandler(); marked @ApiStatus.Experimental but stable since 2022 |
| `com.intellij.ui.JBColor` / `UIManager` | Platform 2025.1 (bundled) | Theme-aware drop highlight color | `JBColor.namedColor()` or `UIManager.getColor("Tree.selectionBackground")` adapts to Darcula |
| `com.intellij.notification.NotificationGroupManager` | Platform 2025.1 (bundled) | Error balloon for I/O failures | Modern replacement for legacy `NotificationGroup` constructor; requires plugin.xml registration |
| `javax.swing.TransferHandler` | JDK 21 | Drag-out and ghost image | `setDragImage()` + `setDragImageOffset()` built into TransferHandler since Java 7 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.awt.AlphaComposite` | JDK 21 | ~70% opacity on ghost image | Use `AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f)` when painting BufferedImage |
| `com.intellij.openapi.ui.Messages` | Platform 2025.1 | Conflict "Replace?" dialog | Already used in FileTreeComponent for rename/delete confirmations — consistent UX |
| `com.intellij.ui.RenderingUtil` | Platform 2025.1 | Theme-aware selection background | `RenderingUtil.getSelectionBackground(tree)` is the authoritative API used by ColoredTreeCellRenderer itself |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `SmoothAutoScroller` (auto-installed) | Custom `javax.swing.Timer`-based edge scroller | Custom timer is ~50 lines and fragile; SmoothAutoScroller is free via Tree.setTransferHandler() |
| `NotificationGroupManager` | `LOG.warn` → IDE balloon via `Notifications.Bus.notify` | `Notifications.Bus.notify` without a registered group works but doesn't appear in Settings > Notifications; NotificationGroupManager is the correct modern API |
| `DragDropHandler.update()` repaint coordination | Full custom `DropTargetListener` overlay | Overlay glass-pane approach requires more boilerplate; coordinating via existing update() is simpler |

**Installation:** No new dependencies — all APIs are bundled with the IntelliJ Platform already declared in `build.gradle.kts`.

## Architecture Patterns

### Recommended Project Structure
No new files needed. All changes are surgical modifications to existing classes:

```
src/main/kotlin/ro/faur/explorer/
├── actions/
│   ├── DragDropHandler.kt       # Add: hoveredRow tracking, highlight coordination, conflict pre-check, balloon on I/O error
│   ├── FileTreeTransferHandler.kt # Add: getSourceActions() override with ghost image, modifier key COPY detection
│   └── FileActions.kt           # No change needed
├── ui/
│   └── FileTreeComponent.kt     # Add: hoveredRow property on tree (client property or field), VirtualFileCellRenderer highlight branch
└── plugin.xml                   # Add: <notificationGroup> registration
```

### Pattern 1: Drop Row Highlight via DnDManager + CellRenderer

**What:** Track the currently-hovered row index as a JTree client property. The existing `DragDropHandler.update()` call sets it and calls `tree.repaint()`; `VirtualFileCellRenderer` reads it to paint the background.

**When to use:** Any time a drag enters a row over a directory node.

**Example:**
```kotlin
// In DragDropHandler.update() — sets hovered row:
override fun update(event: DnDEvent): Boolean {
    val point = event.point
    val newRow = if (point != null) tree.getRowForLocation(point.x, point.y) else -1
    val targetDir = resolveDropTarget(point)
    val highlightRow = if (targetDir != null) newRow else -1
    val currentRow = tree.getClientProperty("dnd.hoveredRow") as? Int ?: -1
    if (highlightRow != currentRow) {
        tree.putClientProperty("dnd.hoveredRow", highlightRow)
        tree.repaint()
    }
    event.setDropPossible(targetDir != null)
    return targetDir != null
}

// Clear on cleanUpOnLeave / after drop:
override fun drop(event: DnDEvent) {
    tree.putClientProperty("dnd.hoveredRow", -1)
    tree.repaint()
    // ... existing drop logic
}

// In VirtualFileCellRenderer.customizeCellRenderer():
val hoveredRow = tree.getClientProperty("dnd.hoveredRow") as? Int ?: -1
if (!selected && row == hoveredRow && vf?.isDirectory == true) {
    background = RenderingUtil.getSelectionBackground(tree)
    isOpaque = true
}
```

### Pattern 2: Ghost Drag Image via TransferHandler.getSourceActions()

**What:** Override `getSourceActions()` in `FileTreeTransferHandler` to build a `BufferedImage` from file metadata and set it as the drag image at 70% opacity.

**When to use:** Every drag initiation from the explorer tree.

**Example:**
```kotlin
// Source: Java Swing TransferHandler API (docs.oracle.com/javase/8/docs/api/javax/swing/TransferHandler.html)
override fun getSourceActions(c: JComponent): Int {
    val selected = fileTreeComponent.getSelectedFiles()
    if (selected.isNotEmpty()) {
        val img = buildGhostImage(selected, c)
        setDragImage(img)
        // Offset so image appears near cursor, not offset by component origin
        setDragImageOffset(java.awt.Point(img.width / 2, img.height / 2))
    }
    return COPY_OR_MOVE
}

private fun buildGhostImage(files: List<VirtualFile>, component: JComponent): BufferedImage {
    val label = if (files.size == 1) files[0].name else "${files[0].name} (+${files.size - 1} more)"
    val icon: Icon = if (files.size == 1) (files[0].fileType.icon ?: AllIcons.FileTypes.Any_type)
                     else AllIcons.Nodes.MultipleFiles
    // Create ARGB image with icon + text
    val fm = component.getFontMetrics(component.font)
    val w = (icon.iconWidth + 6 + fm.stringWidth(label)).coerceAtLeast(60)
    val h = (icon.iconHeight + 4).coerceAtLeast(20)
    val img = UIUtil.createImage(component, w, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f)
    icon.paintIcon(component, g, 2, (h - icon.iconHeight) / 2)
    g.color = component.foreground
    g.font = component.font
    g.drawString(label, icon.iconWidth + 6, h / 2 + fm.ascent / 2)
    g.dispose()
    return img
}
```

### Pattern 3: Modifier Key COPY/MOVE in FileTreeTransferHandler

**What:** Swing's `TransferSupport.getDropAction()` returns `COPY` when the user holds Ctrl (Windows/Linux) or Option (macOS). `canImport()` and `importData()` already query `support.dropAction == MOVE`; no additional modifier-key polling is needed in Swing DnD. For IntelliJ's `DnDManager` path, `DnDEvent.action == DnDAction.MOVE` already handles it.

**When to use:** The default Swing DnD behavior already wires modifier keys to `getDropAction()`. The missing piece is ensuring `getSourceActions()` returns `COPY_OR_MOVE` (already returning that value) and that `importData()` respects `support.dropAction` (already checking `support.dropAction == MOVE`). This requirement is already satisfied — verify it works end-to-end.

### Pattern 4: Conflict Pre-Check Before Copy/Move

**What:** Before calling `VirtualFile.copy()` or `VirtualFile.move()`, check whether a file with the same name already exists in the target directory. If so, prompt the user.

**When to use:** Inside the `performDrop()` per-file loop in `DragDropHandler`, and the parallel loop in `FileTreeTransferHandler.importData()`.

**Example:**
```kotlin
// Source: IntelliJ VirtualFile API (plugins.jetbrains.com/docs/intellij/virtual-file.html)
fun performDrop(files: List<VirtualFile>, targetDir: VirtualFile, isMove: Boolean) {
    for (file in files) {
        if (file.parent == targetDir) continue
        try {
            val existing = targetDir.findChild(file.name)
            if (existing != null) {
                // Must be called on EDT — performDrop is typically called from drop() which is on EDT
                val result = Messages.showYesNoDialog(
                    project,
                    "'${file.name}' already exists in '${targetDir.name}'. Replace it?",
                    "Confirm Replace",
                    "Replace",
                    "Skip",
                    Messages.getWarningIcon()
                )
                if (result != Messages.YES) continue
                // Delete existing before copy/move (VirtualFile.copy fails if name exists)
                runWriteAction { existing.delete(this) }
            }
            if (isMove) FileActions.moveTo(file, targetDir)
            else FileActions.copyTo(file, targetDir)
        } catch (e: Exception) {
            val action = if (isMove) "move" else "copy"
            notifyError("Failed to $action '${file.name}': ${e.message}")
        }
    }
}
```

### Pattern 5: Balloon Notification for I/O Errors

**What:** Replace `LOG.warn` in the catch block with an IntelliJ balloon notification using `NotificationGroupManager`.

**When to use:** On non-conflict exceptions in the catch block of `performDrop()` and `FileTreeTransferHandler.importData()`.

**Example:**
```kotlin
// plugin.xml addition:
// <notificationGroup id="Explorer.DnD" displayType="BALLOON" key="notification.group.dnd"/>

// Kotlin:
private fun notifyError(message: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Explorer.DnD")
        .createNotification(message, NotificationType.WARNING)
        .notify(project)
}
```

### Pattern 6: VFS Refresh — Source AND Destination

**What:** After a move, refresh the source directory in addition to the destination. Currently `fileTreeComponent.refresh()` only re-reads the current root. For moves, the source file's parent also needs `VirtualFile.refresh()`.

**When to use:** In `DragDropHandler.drop()` and `FileTreeTransferHandler.exportDone()` after a successful move.

**Example:**
```kotlin
// After move loop completes, refresh source parents:
val sourceParents = files.map { it.parent }.toSet()
sourceParents.forEach { it?.refresh(false, false) }
fileTreeComponent.refresh()
```

### Anti-Patterns to Avoid

- **Calling Messages.showYesNoDialog off EDT:** `DragDropHandler.drop()` is called from DnDManager on the EDT. `FileTreeTransferHandler.importData()` is also EDT. Both are safe for `Messages.showYesNoDialog()`. Do not move conflict dialogs to a background thread.
- **Collapsing the two DnD systems:** Do not merge DragDropHandler and FileTreeTransferHandler. The CONTEXT.md explicitly locks this as the established pattern — mixing them causes mouse event conflicts.
- **Storing hovered row in a field instead of JTree client property:** Using a field on FileTreeComponent introduces concurrency concerns. Using `tree.putClientProperty("dnd.hoveredRow", ...)` keeps the state co-located with the JTree and is cleared automatically if the component is re-created.
- **Using `paintImmediately()` instead of `repaint()`:** For a cell renderer background, `tree.repaint()` is correct. `paintImmediately()` is only needed when painting a separate overlay glass-pane layer that must stay ahead of normal repaint queuing — not applicable here.
- **Registering a `DnDSource` via DnDManager:** Already locked out in CONTEXT.md. Swing's TransferHandler is the correct drag-out mechanism.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Auto-scroll during drag | Custom Timer polling cursor position | `SmoothAutoScroller` (auto-installed by `Tree.setTransferHandler()`) | Already installed for free; handles edge detection and acceleration with system DPI scaling |
| Theme-aware highlight color | Hard-coded `Color(r, g, b)` | `RenderingUtil.getSelectionBackground(tree)` or `UIManager.getColor("Tree.selectionBackground")` | Adapts to all IntelliJ themes including custom themes; avoids hard-coded Darcula magic numbers |
| Ghost image transparency | Manually compositing pixel arrays | `AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f)` on a `BufferedImage.TYPE_INT_ARGB` | Standard Java2D pattern; ARGB buffer handles pre-multiplied alpha correctly |
| Error notification | Custom dialog or status bar message | `NotificationGroupManager` balloon | Balloons appear in IntelliJ Notifications tool window, respect user settings, and are dismissible |
| Conflict detection | Catching `IOException` from `VirtualFile.copy()` | `targetDir.findChild(file.name) != null` pre-check | VirtualFile.copy() throws IOException for many reasons; pre-checking name gives deterministic, user-friendly conflict detection separate from real I/O errors |

**Key insight:** The hardest parts (auto-scroll, theme color, transparency) are solved by platform APIs that are either already active or one-line calls. The bulk of implementation work is the conflict pre-check and balloon notification wiring, which are straightforward try-catch upgrades.

## Common Pitfalls

### Pitfall 1: DnDManager.update() Called Frequently — Avoid Repaint Thrash
**What goes wrong:** `DragDropHandler.update()` is called on every mouse move during drag. Calling `tree.repaint()` unconditionally on every update causes visible flicker.
**Why it happens:** DnDManager fires update() at mouse-move rate, not only on row change.
**How to avoid:** Compare the new hovered row to the previous value stored in the client property. Only call `tree.repaint()` when the row actually changes.
**Warning signs:** Flickering or jitter in the tree while dragging.

### Pitfall 2: Ghost Image Not Appearing on macOS
**What goes wrong:** `setDragImage()` has no visible effect on some macOS JVM configurations.
**Why it happens:** macOS native DnD pipeline sometimes ignores `setDragImage()` in `getSourceActions()` if it is called before the Transferable is created.
**How to avoid:** Call `setDragImage()` inside `getSourceActions()` (the correct hook), not in `createTransferable()`. On macOS, the drag image from `getSourceActions()` is picked up by the native pipeline.
**Warning signs:** Ghost image appears on Linux/Windows but not macOS. Verify with manual testing.

### Pitfall 3: `Messages.showYesNoDialog` Blocks the EDT During Drag
**What goes wrong:** The modal dialog blocks the EDT while the DnD mouse capture is still active, causing the drop to behave unpredictably.
**Why it happens:** DnD drop processing and dialog display both require the EDT. On most platforms this is fine because the drop event is already complete when `drop()` is called, but the drag system may not release the mouse until `drop()` returns.
**How to avoid:** Call dialog immediately in `drop()` before any file operations. Do NOT defer to `invokeLater` from within drop() for the conflict dialog — it will not run until drop() returns. Test on all three platforms.
**Warning signs:** Dialog appears but the UI hangs after clicking Yes/No.

### Pitfall 4: VirtualFile.copy() Throws IOException When Destination Exists
**What goes wrong:** Without a pre-check, `VirtualFile.copy()` throws `IOException` with message "already exists". The catch block then shows a balloon notification saying "Failed to copy 'foo.txt': already exists" — confusing to the user.
**Why it happens:** VirtualFile.copy() does not pre-delete and overwrite; it fails if a file with that name exists.
**How to avoid:** Pre-check with `targetDir.findChild(file.name) != null`. Show confirmation dialog. If user confirms, delete existing with `runWriteAction { existing.delete(this) }` BEFORE calling `copyTo()`.
**Warning signs:** Balloon notifications with "already exists" messages instead of "Replace?" dialogs.

### Pitfall 5: SmoothAutoScroller is `@ApiStatus.Experimental`
**What goes wrong:** The API may change in a future platform version.
**Why it happens:** JetBrains marks it experimental to reserve the right to change its behavior.
**How to avoid:** The auto-scroller is installed implicitly via `Tree.setTransferHandler()` — we never call `SmoothAutoScroller` directly. If it changes, `Tree.setTransferHandler()` will be updated by JetBrains. No direct dependency to worry about.
**Warning signs:** Compilation error on `SmoothAutoScroller` class — don't reference it directly.

### Pitfall 6: DnDManager.update() `event.point` is in Component Coordinates
**What goes wrong:** Using `event.point` to call `tree.getRowForLocation()` returns wrong row because the point is in the wrong coordinate space.
**Why it happens:** DnDManager converts the drag point to the registered component's local coordinate system, but if the tree is inside a scroll pane, the viewport offset must be accounted for.
**How to avoid:** Use `tree.getRowForLocation(event.point.x, event.point.y)` directly — the DnDManager already translates to the tree's coordinate system (the component registered with `registerTarget`). Test scrolled positions.
**Warning signs:** Highlight appears on the wrong row when tree is scrolled down.

## Code Examples

### Auto-scroll: Verify It Is Already Active
```kotlin
// Source: Tree.java (github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/treeStructure/Tree.java)
// Tree.setTransferHandler() calls:
//   SmoothAutoScroller.installDropTargetAsNecessary(this)
// This is already triggered by FileTreeComponent.init():
tree.transferHandler = FileTreeTransferHandler(this)
// No additional code needed for DND-03. Verify in manual test only.
```

### NotificationGroup Registration in plugin.xml
```xml
<!-- Source: plugins.jetbrains.com/docs/intellij/notifications.html -->
<extensions defaultExtensionNs="com.intellij">
  <notificationGroup id="Explorer.DnD"
                     displayType="BALLOON"
                     key="notification.group.dnd"/>
</extensions>
```

### Theme-Aware Highlight Color
```kotlin
// Source: RenderingUtil, UIManager — platform-api (HIGH confidence)
// Option A — uses same color as tree selection (recommended):
val highlightColor = RenderingUtil.getSelectionBackground(tree)

// Option B — explicit UIManager lookup with JBColor fallback:
val highlightColor: Color = UIManager.getColor("Tree.selectionBackground")
    ?: JBColor(0xc5dffc, 0x113a5c)
```

### Conflict Pre-Check Pattern
```kotlin
// Source: VirtualFile API docs (plugins.jetbrains.com/docs/intellij/virtual-file.html)
val existing = targetDir.findChild(file.name)
if (existing != null && existing.exists()) {
    val result = Messages.showYesNoDialog(
        project,
        "'${file.name}' already exists in '${targetDir.name}'. Replace it?",
        "Confirm Replace",
        "Replace", "Skip",
        Messages.getWarningIcon()
    )
    if (result != Messages.YES) continue
    runWriteAction { existing.delete(this) }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `NotificationGroup(id, displayType, true)` constructor | `NotificationGroupManager.getInstance().getNotificationGroup(id)` + plugin.xml EP | IntelliJ 2020.3 | Constructor is deprecated; new approach allows user to configure notification display |
| Manual `javax.swing.Timer` edge auto-scroller | `SmoothAutoScroller.installDropTargetAsNecessary()` via `Tree.setTransferHandler()` | IntelliJ ~2022 | Platform handles it; plugin needs zero scroll code |
| `TreeSpeedSearch(tree)` constructor | `TreeUIHelper.installTreeSpeedSearch()` | IntelliJ 2024.1 | Already noted in REQUIREMENTS.md (TREE-04); out of scope for Phase 3 |

**Deprecated/outdated:**
- `NotificationGroup(displayId, displayType, logByDefault)` constructor: deprecated, use NotificationGroupManager + plugin.xml EP
- Manual DropTargetListener for auto-scroll: replaced by SmoothAutoScroller

## Open Questions

1. **macOS ghost image rendering**
   - What we know: `setDragImage()` in `getSourceActions()` is the correct hook; works on Linux/Windows
   - What's unclear: macOS native DnD pipeline compatibility with `TransferHandler.setDragImage()` for non-system component drags
   - Recommendation: Implement and manually test on macOS. If ghost image does not appear, fall back to a DragSourceMotionListener that paints to a JWindow glass overlay (significantly more complex; defer until confirmed broken).

2. **DND-01: Drag INTO IntelliJ project view success criteria**
   - What we know: `FileTreeTransferHandler.createTransferable()` produces `javaFileListFlavor` which IntelliJ project view accepts
   - What's unclear: Whether IntelliJ project view treats the drop as a copy (LINK) action or a move, and whether it fires a corresponding VFS event the plugin can observe
   - Recommendation: Manual test in a real IDE instance. The drag-out path is read-only from the plugin's side — IntelliJ's project view handles what happens on its end.

3. **Conflict dialog on EDT during active drag**
   - What we know: `drop()` is called after the OS drag is complete; showing a modal dialog in `drop()` is safe on all platforms in typical usage
   - What's unclear: Whether some platforms fire `drop()` before the mouse button-up event, which could cause UI hangs
   - Recommendation: Implement and test. If hang occurs, use `ApplicationManager.getApplication().invokeLater { showDialog() }` to defer post-drop.

## Sources

### Primary (HIGH confidence)
- `plugins.jetbrains.com/docs/intellij/notifications.html` — NotificationGroupManager API, plugin.xml EP format
- `plugins.jetbrains.com/docs/intellij/lists-and-trees.html` — Tree class, SmoothAutoScroller mention
- `github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/treeStructure/Tree.java` — SmoothAutoScroller.installDropTargetAsNecessary() in setTransferHandler()
- `github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/ColoredTreeCellRenderer.java` — setBackground/RenderingUtil patterns
- `docs.oracle.com/javase/tutorial/uiswing/dnd/dropaction.html` — Modifier key COPY/MOVE rules
- `docs.oracle.com/javase/8/docs/api/javax/swing/TransferHandler.html` — setDragImage/setDragImageOffset API

### Secondary (MEDIUM confidence)
- `java-swing-tips.blogspot.com/2015/08/create-custom-drag-ghost-image.html` — Ghost image BufferedImage/AlphaComposite pattern (verified against TransferHandler Java docs)
- `intellij-support.jetbrains.com` — Confirmation that `DnDNativeTarget.update()` is called per mouse-move; row highlight behavior discussed in forum posts

### Tertiary (LOW confidence — flag for validation)
- macOS ghost image rendering compatibility: training-data knowledge only; needs manual test
- SmoothAutoScroller scroll threshold of 5*sysScale px: from source read, marked @ApiStatus.Experimental — subject to change

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all APIs are bundled platform APIs, verified in official docs and source
- Architecture: HIGH — existing code read directly; patterns verified against platform source
- Pitfalls: MEDIUM — conflict dialog EDT behavior and macOS ghost image are based on general Swing knowledge, not IntelliJ-specific docs

**Research date:** 2026-02-28
**Valid until:** 2026-03-30 (stable platform APIs; SmoothAutoScroller experimental flag warrants re-check if upgrading beyond 2025.3)
