# Phase 4: File Tree and Context Menu Polish - Research

**Researched:** 2026-02-28
**Domain:** IntelliJ Platform — VCS coloring, DumbService, TreeUIHelper, TerminalToolWindowManager, JPopupMenu context menu
**Confidence:** HIGH for most areas; MEDIUM for FileStatusListener lifecycle; noted where.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### VCS color coding
- Use IntelliJ's `FileStatusManager` to source VCS status — not our own `GitBackend`
- All visible file nodes get VCS coloring, including children revealed on expand
- Directories propagate status: a directory shows color if any child is modified/untracked/etc.
- Full IntelliJ color set: modified (blue), untracked (green/brown), ignored (grey), added (green), deleted (red), conflict (red) — exact parity with Project View
- Real-time updates via `FileStatusListener` — colors repaint automatically on save, stage, etc.
- Ignored files (.gitignore'd) shown but dimmed with grey text
- Color only — no icon overlay badges
- Graceful fallback: outside any git repo, everything renders in default color with no errors

#### Context menu actions and organization
- Keep existing "Open in System" (opens with OS default app) AND add "Reveal in Finder/Explorer" (shows in OS file manager)
- Add "Open Terminal Here" — opens IntelliJ's built-in terminal at the selected directory; if a file is selected, opens terminal at the file's parent directory
- Reorganized menu groups with separators:
  1. Open | Open in System | Reveal in Finder | Open Terminal Here
  2. Copy | Cut | Paste | Copy Path
  3. Rename | Delete
  4. New File | New Folder
  5. Add to Bookmarks | Refresh
- OS-adaptive labels: "Reveal in Finder" (macOS), "Reveal in Explorer" (Windows), "Open in File Manager" (Linux)
- Actions correctly enabled/disabled based on current selection state

### Claude's Discretion
- Icon cache invalidation strategy for dumb-mode transitions (TREE-01)
- Expand/collapse duplicate placeholder fix approach (TREE-02)
- `TreeUIHelper.installTreeSpeedSearch()` migration details (TREE-04)
- FileStatusListener registration and disposal lifecycle
- Directory status propagation implementation (recursive vs cached)

### Deferred Ideas (OUT OF SCOPE)
- Remote/SSH panel VCS coloring using `RemoteGitBackend` — separate phase
- VCS status icon badges/overlays — decided against for now, may revisit
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| TREE-01 | File tree icons are correct after dumb-mode transitions (no stale icon cache) | DumbService.runWhenSmart() callback clears iconCache and triggers tree repaint; listener registered/disposed in FileTreeComponent lifecycle |
| TREE-02 | Expand/collapse does not produce duplicate placeholder children on repeated toggling | Existing guard in treeWillExpand() already checks `childCount != 1 && userObject !is String`, but treeWillCollapse needs to restore the placeholder child; confirmed fix: add `treeWillCollapse` to remove children and re-add single "loading..." placeholder |
| TREE-03 | Modified files show VCS color coding (blue), untracked files show different color (matches IntelliJ conventions) | FileStatusManager.getInstance(project) + FileStatus color mapping in VirtualFileCellRenderer; FileStatusListener triggers tree.repaint() on VCS changes |
| TREE-04 | File tree uses `TreeUIHelper.installTreeSpeedSearch()` (replaces deprecated `TreeSpeedSearch` constructor) | `TreeUIHelper.getInstance().installTreeSpeedSearch(tree) { path -> ... }` replaces `@Suppress("DEPRECATION") TreeSpeedSearch(tree) { ... }` |
| CTX-01 | Right-clicking a file shows: Open in Editor, Copy, Paste, Delete, Rename, New File, New Directory | createPopupMenu() already has most items; "Open in Editor" wording update needed; groups and separators need reorganization per locked decision |
| CTX-02 | Right-clicking a file shows "Reveal in Finder/Explorer" | `java.awt.Desktop.getDesktop().browseFileDirectory(File)` (Java 9+) is the cross-platform API; OS-adaptive label via SystemInfo |
| CTX-03 | Right-clicking a file shows "Open Terminal Here" | `TerminalToolWindowManager.getInstance(project).createLocalShellWidget(workDir, tabName, true, true)` — already used in SshTerminalAction.kt pattern |
| CTX-04 | Context menu actions are correctly enabled/disabled based on selection state (no stale enable/disable) | createPopupMenu() builds menu fresh on every right-click so enabled state is always current; review each item to ensure consistent enable condition logic |
</phase_requirements>

## Summary

Phase 4 is a targeted polish phase with no new architecture — all changes are surgical modifications to `FileTreeComponent.kt` and its inner classes. The existing `VirtualFileCellRenderer` already uses `ColoredTreeCellRenderer` (the correct base class) with an `iconCache`; VCS coloring adds a `FileStatusManager` lookup in `customizeCellRenderer()` to select the foreground color, and a `FileStatusListener` triggers `tree.repaint()` on VCS changes. Directory color propagation can be implemented as a recursive scan with short-circuit on first colored child.

The duplicate-placeholder bug (TREE-02) comes from `treeWillCollapse` not resetting collapsed directory nodes back to the "loading..." state — when a directory collapses and re-expands, the population guard in `treeWillExpand` sees `childCount != 1` (it kept populated children) and skips the lazy load. The fix adds a `treeWillCollapse` handler that removes all children and inserts a fresh placeholder, mirroring the initial `populateNode()` behavior. The icon cache dumb-mode problem (TREE-01) is fixed by registering a `DumbService.runWhenSmart()` callback that clears `iconCache` and calls `tree.repaint()` after each dumb-mode exit.

Context menu additions are straightforward: "Reveal in Finder/Explorer" uses `java.awt.Desktop.browseFileDirectory()` (Java 9+, already available in the JDK 21 runtime), and "Open Terminal Here" uses the same `TerminalToolWindowManager.createLocalShellWidget()` pattern already implemented in `SshTerminalAction.kt`. The `TreeUIHelper.installTreeSpeedSearch()` migration is a single-line change replacing the `@Suppress("DEPRECATION") TreeSpeedSearch(tree)` constructor call.

**Primary recommendation:** All eight requirements fit in a single plan for `FileTreeComponent.kt`. No new files needed for TREE-01/02/03/04. CTX-02/03 may add a small helper or keep inline — either works. CTX-01 and CTX-04 are refactors of `createPopupMenu()`.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.intellij.openapi.vcs.FileStatusManager` | Platform 2025.1 (bundled) | Maps VirtualFile → FileStatus (MODIFIED, UNKNOWN, etc.) | The authoritative source for IntelliJ VCS status; used by Project View itself |
| `com.intellij.openapi.vcs.FileStatus` | Platform 2025.1 (bundled) | Enum-like constants (MODIFIED, UNKNOWN, ADDED, etc.) | Each constant has a `.color` property returning the theme-aware Color used by IntelliJ UI |
| `com.intellij.openapi.vcs.FileStatusListener` | Platform 2025.1 (bundled) | Callback when VCS status changes | Registered via `project.messageBus.connect(disposable).subscribe(FileStatusManager.FILE_STATUS_TOPIC, listener)` |
| `com.intellij.openapi.project.DumbService` | Platform 2025.1 (bundled) | Dumb-mode lifecycle hook | `DumbService.getInstance(project).runWhenSmart(runnable)` — queues runnable for each smart-mode entry |
| `com.intellij.ui.TreeUIHelper` | Platform 2025.1 (bundled) | Speed search installation | `TreeUIHelper.getInstance().installTreeSpeedSearch(tree, converter)` replaces deprecated constructor |
| `org.jetbrains.plugins.terminal.TerminalToolWindowManager` | Terminal plugin (bundled) | Open local terminal at path | `createLocalShellWidget(workDir, tabName, true, true)` — already depended on in plugin.xml |
| `java.awt.Desktop` | JDK 21 | Reveal in Finder/Explorer | `Desktop.getDesktop().browseFileDirectory(File)` for "reveal" action (Java 9+) |
| `com.intellij.openapi.util.SystemInfo` | Platform 2025.1 (bundled) | OS detection for menu labels | `SystemInfo.isMac`, `SystemInfo.isWindows`, `SystemInfo.isLinux` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.intellij.ui.SimpleTextAttributes` | Platform 2025.1 | Style-aware text color overrides in ColoredTreeCellRenderer | Use `SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color)` to apply VCS color to filename |
| `com.intellij.ui.render.RenderingUtil` | Platform 2025.1 | Theme-aware selection background | Already used in DnD highlight; no new usage needed in phase 4 |
| `com.intellij.openapi.project.ProjectLevelVcsManager` | Platform 2025.1 | Check whether a file is under VCS before querying FileStatusManager | Guards graceful fallback — files outside any VCS repo return `null` from `FileStatusManager` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `FileStatusManager` | `ChangeListManager.getStatus()` | `ChangeListManager` is higher level and already used in `GitStatusProvider`; `FileStatusManager` is lower level, faster, and gives the same `FileStatus` colors used by Project View |
| `FileStatusListener` via messageBus | `VirtualFileListener` / polling | Polling is wasteful; `VirtualFileListener` sees file content changes but not VCS status changes; `FileStatusListener` is the correct subscription topic |
| `Desktop.browseFileDirectory()` | `RevealFileAction` from IntelliJ | `RevealFileAction` is an internal platform action not intended for direct invocation; `Desktop.browseFileDirectory()` is the standard Java API for this purpose |
| `TerminalToolWindowManager.createLocalShellWidget(workDir, ...)` | Opening a new terminal and sending a `cd` command | The `workDir` parameter directly sets the working directory — cleaner than a post-creation `cd` command |

**Installation:** No new build.gradle.kts dependencies. `TerminalToolWindowManager` comes from `org.jetbrains.plugins.terminal` which is already in `platformBundledPlugins`. All other APIs are platform-bundled.

## Architecture Patterns

### Recommended Project Structure
```
src/main/kotlin/ro/faur/explorer/
├── ui/
│   └── FileTreeComponent.kt    # All TREE-* changes live here
└── actions/
    └── ExplorerActions.kt      # No changes needed — context menu stays in createPopupMenu()
```
No new files needed for any of the eight requirements.

### Pattern 1: VCS Color Lookup in ColoredTreeCellRenderer
**What:** `FileStatusManager.getInstance(project)` maps a `VirtualFile` to a `FileStatus`. `FileStatus.color` returns the theme-aware `Color` used by IntelliJ's Project View. Apply it via `SimpleTextAttributes` in `customizeCellRenderer()`.
**When to use:** In `VirtualFileCellRenderer.customizeCellRenderer()`, after setting the icon and before appending the filename text.
**Example:**
```kotlin
// In VirtualFileCellRenderer.customizeCellRenderer():
val vcsColor: Color? = try {
    val status = FileStatusManager.getInstance(project).getStatus(vf)
    if (status != FileStatus.NOT_CHANGED && status != FileStatus.UNKNOWN) status.color else null
} catch (_: Exception) { null }

val textStyle = if (vcsColor != null)
    SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, vcsColor)
else
    SimpleTextAttributes.REGULAR_ATTRIBUTES

append(vf.name, textStyle)
```
**Note on directories:** To propagate VCS color to directories, add a `getEffectiveVcsColor(vf: VirtualFile): Color?` helper that:
1. If `vf` is a file: returns `FileStatusManager.getStatus(vf).color` (null if NOT_CHANGED).
2. If `vf` is a directory: iterates `vf.children`, returning the first non-null color found (short-circuit). Returns null if none.
This is called from `customizeCellRenderer()` and replaces the direct file lookup.

### Pattern 2: FileStatusListener for Real-Time Repaint
**What:** Subscribe to `FileStatusManager.FILE_STATUS_TOPIC` on the project message bus. When any file status changes, call `tree.repaint()` on the EDT to refresh VCS colors.
**When to use:** In `FileTreeComponent.init {}` block; connection must be disposed in `dispose()`.
**Example:**
```kotlin
// In FileTreeComponent constructor — store for disposal:
private val messageBusConnection: MessageBusConnection

// In init {}:
messageBusConnection = project.messageBus.connect()
messageBusConnection.subscribe(FileStatusManager.FILE_STATUS_TOPIC, object : FileStatusListener {
    override fun fileStatusChanged(virtualFile: VirtualFile) {
        ApplicationManager.getApplication().invokeLater {
            if (!isDisposed) tree.repaint()
        }
    }
    override fun fileStatusesChanged() {
        ApplicationManager.getApplication().invokeLater {
            if (!isDisposed) tree.repaint()
        }
    }
})

// In dispose():
messageBusConnection.disconnect()
```
**Confidence:** MEDIUM — `FileStatusManager.FILE_STATUS_TOPIC` is the standard topic but needs verification that it fires on `git add` / `git checkout` (not just on save). The `fileStatusesChanged()` bulk override covers all cases.

### Pattern 3: DumbService Hook for Icon Cache Invalidation
**What:** `DumbService.getInstance(project).runWhenSmart(runnable)` queues a runnable to execute each time IntelliJ transitions from dumb-mode back to smart-mode. Use it to clear `iconCache` and repaint the tree.
**When to use:** Registered once in `FileTreeComponent.init {}` via a `DumbModeListener` subscription (not `runWhenSmart` which fires only once).
**Example:**
```kotlin
// Use DumbModeListener for repeated transitions (not runWhenSmart which is one-shot):
project.messageBus.connect(messageBusConnection) // reuse existing connection
    .subscribe(DumbService.DUMB_MODE_LISTENER, object : DumbService.DumbModeListener {
        override fun exitDumbMode() {
            iconCache.clear()
            ApplicationManager.getApplication().invokeLater {
                if (!isDisposed) tree.repaint()
            }
        }
    })
```
**Note:** `DumbService.DUMB_MODE_LISTENER` is the topic constant. `exitDumbMode()` is the callback method. Alternative: subscribe via `messageBusConnection.subscribe(DumbService.DUMB_MODE_LISTENER, ...)` to reuse the existing connection being created for FileStatusListener.

### Pattern 4: Expand/Collapse Placeholder Fix
**What:** The `treeWillCollapse` handler must restore the "loading..." placeholder to collapsed directory nodes so that the `treeWillExpand` population guard works correctly on re-expansion.
**When to use:** Add to the existing `expandListener` anonymous object in `FileTreeComponent.init {}`.
**Example:**
```kotlin
// In the existing expandListener object:
override fun treeWillCollapse(event: TreeExpansionEvent) {
    val node = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
    val file = node.userObject as? VirtualFile ?: return
    if (!file.isDirectory) return
    // Restore placeholder so treeWillExpand guard fires correctly on re-expansion
    ApplicationManager.getApplication().invokeLater {
        if (isDisposed) return@invokeLater
        node.removeAllChildren()
        node.add(DefaultMutableTreeNode("loading..."))
        treeModel.nodeStructureChanged(node)
    }
}
```
**Why invokeLater:** `treeWillCollapse` is called before the collapse animation begins. Modifying the model inside the callback can interfere with Swing's tree collapse sequence. `invokeLater` defers the reset until after collapse completes.

### Pattern 5: TreeUIHelper Speed Search Migration
**What:** Replace the deprecated `TreeSpeedSearch` constructor with `TreeUIHelper.getInstance().installTreeSpeedSearch()`.
**When to use:** In `FileTreeComponent.init {}`, replacing lines 123–128.
**Example:**
```kotlin
// Replace:
// @Suppress("DEPRECATION")
// TreeSpeedSearch(tree) { path -> ... }

// With:
TreeUIHelper.getInstance().installTreeSpeedSearch(tree) { path ->
    val node = path.lastPathComponent as? DefaultMutableTreeNode
    val vf = node?.userObject as? VirtualFile
    vf?.name ?: node?.userObject?.toString() ?: ""
}
```
**Import:** `import com.intellij.ui.TreeUIHelper`

### Pattern 6: Reveal in Finder/Explorer
**What:** `java.awt.Desktop.getDesktop().browseFileDirectory(File)` opens the OS file manager and selects the file. OS-adaptive menu label via `SystemInfo`.
**When to use:** New menu item in `createPopupMenu()`, group 1.
**Example:**
```kotlin
val revealLabel = when {
    SystemInfo.isMac     -> "Reveal in Finder"
    SystemInfo.isWindows -> "Reveal in Explorer"
    else                 -> "Open in File Manager"
}
menu.add(JMenuItem(revealLabel).apply {
    isEnabled = java.awt.Desktop.isDesktopSupported() &&
        java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR)
    addActionListener {
        AppExecutorUtil.getAppExecutorService().execute {
            runCatching {
                val target = if (singleFile!!.isDirectory) java.io.File(singleFile.path)
                             else java.io.File(singleFile.path).parentFile
                java.awt.Desktop.getDesktop().browseFileDirectory(java.io.File(singleFile.path))
            }.onFailure { LOG.warn("Failed to reveal in file manager: ${it.message}") }
        }
    }
})
```
**Note:** `Desktop.Action.BROWSE_FILE_DIR` (Java 9+) is the correct action constant for reveal-in-manager. `Desktop.Action.OPEN` (already used) opens with the default app.

### Pattern 7: Open Terminal Here
**What:** `TerminalToolWindowManager.getInstance(project).createLocalShellWidget(workDir, tabName, true, true)` opens a new terminal tab at the specified working directory. Already used in `SshTerminalAction.kt`.
**When to use:** New menu item in `createPopupMenu()`, group 1.
**Example:**
```kotlin
menu.add(JMenuItem("Open Terminal Here").apply {
    addActionListener {
        val dir = if (singleFile != null && singleFile.isDirectory) singleFile.path
                  else (singleFile?.parent?.path ?: contextDir?.path) ?: return@addActionListener
        TerminalToolWindowManager.getInstance(project).createLocalShellWidget(
            dir,
            "Terminal: ${java.io.File(dir).name}",
            true,
            true
        )
    }
})
```
**Import:** `import org.jetbrains.plugins.terminal.TerminalToolWindowManager`
**Note:** `singleFile` may be null if nothing is selected; `contextDir` is the fallback. Guard against null before calling.

### Anti-Patterns to Avoid
- **Using `ChangeListManager` for VCS colors:** `ChangeListManager.getStatus()` is correct for the Quick Open git indicator, but for cell rendering use `FileStatusManager.getStatus()` which returns the same `FileStatus` that Project View uses to color entries.
- **Calling `vf.children` in `customizeCellRenderer()`:** Triggers VFS access on every paint call. For directory VCS propagation, use `FileStatusManager.getRecursiveStatus(vf)` if available, or limit the depth of child iteration (max 1 level is sufficient for visual propagation).
- **Clearing iconCache in `treeWillExpand`:** The cache should only be cleared on dumb-mode exit, not on every expand (which defeats the purpose of caching).
- **Registering `DumbService.runWhenSmart()` for repeated transitions:** `runWhenSmart()` fires only once for the next smart-mode entry. Use `DumbService.DUMB_MODE_LISTENER` via messageBus for repeated transitions.
- **Directly calling `treeModel.reload()` from `treeWillCollapse`:** Full model reload collapses all tree nodes and loses expansion state. Use `treeModel.nodeStructureChanged(node)` instead.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| VCS color lookup | Custom git parsing to infer modified/untracked state | `FileStatusManager.getStatus(vf).color` | FileStatusManager reads IntelliJ's internal VCS cache; custom git parsing is stale and doesn't account for staged/unstaged distinction |
| VCS change notification | VirtualFileListener or polling | `FileStatusManager.FILE_STATUS_TOPIC` via messageBus | FILE_STATUS_TOPIC fires specifically when VCS status changes; VirtualFileListener fires on content changes, not VCS transitions |
| Speed search | Custom key listener on JTree | `TreeUIHelper.installTreeSpeedSearch()` | Platform implementation handles popup positioning, international characters, case-sensitivity, and focus management |
| Dumb-mode tracking | Boolean flag toggled by custom listener | `DumbService.DUMB_MODE_LISTENER` | Platform listener is authoritative; boolean flag can race with background indexing tasks |

**Key insight:** All four problems have authoritative platform APIs. Custom solutions will miss edge cases (staged files, merge conflicts, multi-byte characters in speed search) that the platform APIs handle correctly.

## Common Pitfalls

### Pitfall 1: FileStatusManager Returns NOT_CHANGED Outside VCS
**What goes wrong:** `FileStatusManager.getStatus(vf)` returns `FileStatus.NOT_CHANGED` (not null) for files that are not under any VCS. Applying `NOT_CHANGED.color` may return null or a specific color that clashes with the default renderer color.
**Why it happens:** FileStatusManager always returns a non-null status; the "no VCS" case maps to `NOT_CHANGED`.
**How to avoid:** Check `FileStatus.NOT_CHANGED` explicitly and skip color application. Optionally guard with `ProjectLevelVcsManager.getInstance(project).getVcsFor(vf) != null` before calling FileStatusManager.
**Warning signs:** All files turn a single color in a non-git directory.

### Pitfall 2: Directory VCS Propagation Performance
**What goes wrong:** Iterating `vf.children` recursively in `customizeCellRenderer()` causes VFS I/O on the EDT for every visible directory node on every paint call.
**Why it happens:** `vf.children` triggers a VFS read if the directory isn't cached. This is a slow operation.
**How to avoid:** Limit directory propagation to 1 level of children only (not recursive). Add a `directoryStatusCache: ConcurrentHashMap<String, Color?>` similar to the existing `iconCache` to amortize the cost. Invalidate the directory cache in the `FileStatusListener` callback alongside `tree.repaint()`.
**Warning signs:** UI lag when scrolling a tree with many visible directories.

### Pitfall 3: treeWillCollapse Concurrent Model Modification
**What goes wrong:** Modifying `DefaultTreeModel` inside `treeWillCollapse` while Swing is processing the collapse event can cause `ConcurrentModificationException` or visual artifacts (nodes appearing/disappearing during animation).
**Why it happens:** `treeWillCollapse` fires before Swing removes child nodes from the view. Model changes inside the callback race with Swing's own tree state updates.
**How to avoid:** Always use `ApplicationManager.getApplication().invokeLater {}` to defer model changes to the next EDT cycle after collapse completes.
**Warning signs:** Intermittent `IllegalStateException` or visual glitch where a collapsed node briefly shows expanded children.

### Pitfall 4: messageBus Connection Leak
**What goes wrong:** If `MessageBusConnection` is not disconnected in `dispose()`, the FileStatusListener and DumbModeListener continue to hold a reference to `FileTreeComponent` after the panel is closed.
**Why it happens:** Message bus subscriptions are reference-counted by IntelliJ's project bus and not garbage-collected until explicitly disconnected.
**How to avoid:** Store the `MessageBusConnection` as a field and call `messageBusConnection.disconnect()` in `dispose()`. Alternatively, pass the `Disposable` (FileTreeComponent itself) to `project.messageBus.connect(this)` and IntelliJ will auto-disconnect on disposal.
**Warning signs:** Memory leak warnings in the IDE; FileTreeComponent not garbage-collected after panel close.

### Pitfall 5: Desktop.browseFileDirectory Availability
**What goes wrong:** `Desktop.Action.BROWSE_FILE_DIR` is not supported on all Linux window managers (GNOME supports it; some minimal WMs do not). Calling without checking causes a silent failure or crash.
**Why it happens:** AWT Desktop support is OS/DE dependent on Linux.
**How to avoid:** Always guard with `Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)`. When not supported on Linux, disable the menu item (set `isEnabled = false`).
**Warning signs:** Menu item does nothing on certain Linux configurations.

## Code Examples

Verified patterns from existing codebase and platform APIs:

### FileStatus Color in ColoredTreeCellRenderer
```kotlin
// Source: IntelliJ Platform SDK — FileStatusManager, FileStatus
// (consistent with GitStatusProvider.kt pattern already in this plugin)
private fun getVcsColor(vf: VirtualFile): Color? {
    return try {
        val status = FileStatusManager.getInstance(project).getStatus(vf)
        when {
            status == FileStatus.NOT_CHANGED -> null
            status == FileStatus.UNKNOWN     -> null  // untracked — use explicit color
            else -> status.color
        }
    } catch (_: Exception) { null }
}
```

### FileStatusListener Subscription (reusing existing messageBus connection)
```kotlin
// In FileTreeComponent init {}:
messageBusConnection = project.messageBus.connect()
messageBusConnection.subscribe(FileStatusManager.FILE_STATUS_TOPIC, object : FileStatusListener {
    override fun fileStatusChanged(virtualFile: VirtualFile) {
        directoryStatusCache.clear()
        ApplicationManager.getApplication().invokeLater { if (!isDisposed) tree.repaint() }
    }
    override fun fileStatusesChanged() {
        directoryStatusCache.clear()
        ApplicationManager.getApplication().invokeLater { if (!isDisposed) tree.repaint() }
    }
})
// In dispose():
messageBusConnection.disconnect()
```

### DumbModeListener (reusing messageBus connection)
```kotlin
messageBusConnection.subscribe(DumbService.DUMB_MODE_LISTENER, object : DumbService.DumbModeListener {
    override fun exitDumbMode() {
        iconCache.clear()
        ApplicationManager.getApplication().invokeLater { if (!isDisposed) tree.repaint() }
    }
})
```

### TreeUIHelper Speed Search (replaces deprecated constructor)
```kotlin
// Replaces @Suppress("DEPRECATION") TreeSpeedSearch(tree) { ... }
TreeUIHelper.getInstance().installTreeSpeedSearch(tree) { path ->
    val node = path.lastPathComponent as? DefaultMutableTreeNode
    val vf = node?.userObject as? VirtualFile
    vf?.name ?: node?.userObject?.toString() ?: ""
}
```

### Open Terminal Here (from SshTerminalAction.kt pattern)
```kotlin
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

val dirPath = if (singleFile?.isDirectory == true) singleFile.path
              else singleFile?.parent?.path ?: return@addActionListener
TerminalToolWindowManager.getInstance(project).createLocalShellWidget(
    dirPath,
    "Terminal: ${java.io.File(dirPath).name}",
    true,
    true
)
```

### Reveal in Finder/Explorer
```kotlin
// Guard first, then run off EDT
AppExecutorUtil.getAppExecutorService().execute {
    runCatching {
        java.awt.Desktop.getDesktop().browseFileDirectory(java.io.File(singleFile.path))
    }.onFailure { LOG.warn("Reveal in file manager failed: ${it.message}") }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `TreeSpeedSearch(tree)` constructor | `TreeUIHelper.getInstance().installTreeSpeedSearch()` | IJ 2024.1 soft-deprecated | Remove `@Suppress("DEPRECATION")` suppression; behavior identical |
| `DumbService.runWhenSmart()` for repeated hooks | `DumbService.DUMB_MODE_LISTENER` messageBus subscription | IJ 2022+ (listener always existed) | `runWhenSmart()` is one-shot per call; DUMB_MODE_LISTENER fires on every transition |
| Hand-rolled VCS color mapping (as done in RemoteGitTreeDecorator) | `FileStatusManager.getStatus(vf).color` | N/A — local VFS has always had FileStatusManager | Avoids maintaining parallel color constants; inherits IntelliJ theme updates automatically |

**Deprecated/outdated:**
- `TreeSpeedSearch(tree)` constructor: soft-deprecated in IJ 2024.1; no removal timeline but suppression should be removed
- `DumbService.smartInvokeLater()`: slightly different semantics; prefer `DumbService.DUMB_MODE_LISTENER` for component-level hooks

## Open Questions

1. **`FileStatus.UNKNOWN` color for untracked files**
   - What we know: `FileStatus.UNKNOWN` is the status returned by FileStatusManager for untracked files. `FileStatus.UNKNOWN.color` may be null or may be the brown/olive untracked color depending on the IJ version.
   - What's unclear: Whether `FileStatus.UNKNOWN.color` returns the expected untracked color or null on IJ 2025.1.
   - Recommendation: In implementation, log the color value in a test and add an explicit fallback: `status.color ?: JBColor(Color(0x80, 0x60, 0x00), Color(0xC4, 0xA0, 0x00))` (olive/brown, matching RemoteGitTreeDecorator constants).

2. **`FileStatusManager.getRecursiveStatus()` availability**
   - What we know: There may be a `getRecursiveStatus(VirtualFile)` or similar method that does directory propagation at the platform level — cannot confirm without live IDE inspection.
   - What's unclear: If it exists, it would eliminate the need for manual child iteration.
   - Recommendation: In implementation, check `FileStatusManager.getInstance(project)` methods in the IDE. If `getRecursiveStatus()` exists and returns a meaningful status for directories, use it. Otherwise, implement 1-level manual propagation with caching.

3. **`MessageBusConnection` vs. `Disposable` auto-disconnect**
   - What we know: `project.messageBus.connect(disposable)` returns a connection that auto-disconnects when the disposable is disposed. `FileTreeComponent` is itself `Disposable`.
   - What's unclear: Whether passing `this` (FileTreeComponent) to `connect()` works correctly given that FileTreeComponent is registered as a child disposable of LocalBrowserPanel via `Disposer.register(this, fileTreeComponent)`.
   - Recommendation: Use `project.messageBus.connect(this)` — passing `this` (the Disposable FileTreeComponent) is the cleanest pattern and eliminates the need to call `disconnect()` manually in `dispose()`. If this causes any ordering issues, fall back to explicit `messageBusConnection.disconnect()` in `dispose()`.

## Sources

### Primary (HIGH confidence)
- IntelliJ Platform source — `FileStatusManager`, `FileStatus`, `FileStatusListener` — verified via project's existing `GitStatusProvider.kt` which already uses `FileStatus` constants from `com.intellij.openapi.vcs`
- Project codebase — `SshTerminalAction.kt` — verified `TerminalToolWindowManager.createLocalShellWidget()` API signature already in use
- Project codebase — `FileTreeComponent.kt` — full understanding of existing `iconCache`, `expandListener`, `VirtualFileCellRenderer` patterns
- Project codebase — `RemoteGitTreeDecorator.kt` — color constants verified and can be reused as fallbacks

### Secondary (MEDIUM confidence)
- `.planning/research/STACK.md` — `TreeUIHelper.installTreeSpeedSearch()` pattern documented; from IntelliJ Platform SDK docs (List and Tree Controls page)
- `.planning/phases/03-drag-and-drop-polish/03-RESEARCH.md` — `DumbService.DUMB_MODE_LISTENER` and `MessageBusConnection` lifecycle patterns cited
- IntelliJ Platform API Changes 2025 — confirms no breaking changes to `FileStatusManager` or `FileStatusListener` in 2025.1-2025.3

### Tertiary (LOW confidence)
- Community knowledge about `FileStatus.UNKNOWN.color` returning null vs. the untracked color — needs live verification in implementation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all APIs are bundled platform APIs already partially used in this plugin
- Architecture: HIGH — all changes are surgical modifications to existing classes; patterns are well-established
- Pitfalls: MEDIUM-HIGH — directory propagation performance and messageBus leak are well-known IntelliJ plugin pitfalls; BROWSE_FILE_DIR support level is LOW (Linux variability)

**Research date:** 2026-02-28
**Valid until:** 2026-09-28 (stable platform APIs; 6 months)
