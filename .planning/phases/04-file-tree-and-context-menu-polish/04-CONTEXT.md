# Phase 4: File Tree and Context Menu Polish - Context

**Gathered:** 2026-02-28
**Status:** Ready for planning

<domain>
## Phase Boundary

The file tree renders icons and VCS colors correctly at all times and the right-click context menu exposes all expected file actions. Covers requirements TREE-01 through TREE-04 and CTX-01 through CTX-04. Scope is limited to `FileTreeComponent` (local browser) — remote panel VCS coloring is out of scope.

</domain>

<decisions>
## Implementation Decisions

### VCS color coding
- Use IntelliJ's `FileStatusManager` to source VCS status — not our own `GitBackend`
- All visible file nodes get VCS coloring, including children revealed on expand
- Directories propagate status: a directory shows color if any child is modified/untracked/etc.
- Full IntelliJ color set: modified (blue), untracked (green/brown), ignored (grey), added (green), deleted (red), conflict (red) — exact parity with Project View
- Real-time updates via `FileStatusListener` — colors repaint automatically on save, stage, etc.
- Ignored files (.gitignore'd) shown but dimmed with grey text
- Color only — no icon overlay badges
- Graceful fallback: outside any git repo, everything renders in default color with no errors

### Context menu actions and organization
- Keep existing "Open in System" (opens with OS default app) AND add "Reveal in Finder/Explorer" (shows in OS file manager) — both actions serve different use cases
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

</decisions>

<specifics>
## Specific Ideas

- VCS colors should match IntelliJ Project View exactly — users expect visual consistency between the explorer and the native project tree
- Remote/SSH panel VCS coloring is explicitly out of scope for this phase

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `FileTreeComponent.VirtualFileCellRenderer` (`FileTreeComponent.kt:589`): Already uses `ColoredTreeCellRenderer` — VCS color integration adds `FileStatusManager.getStatus(vf)` color lookup in `customizeCellRenderer()`
- `FileTreeComponent.iconCache` (`FileTreeComponent.kt:92`): Existing `ConcurrentHashMap<String, Icon>` cache — needs dumb-mode invalidation listener
- `ExplorerActions.kt`: Existing AnAction subclasses for Copy, Cut, Paste, Rename, Delete — pattern to follow for new Reveal/Terminal actions
- `ExplorerActionUtil.kt`: Central helper for action → panel lookup — new actions should use this

### Established Patterns
- Context menu built via `JPopupMenu` with `JMenuItem` in `createPopupMenu()` (`FileTreeComponent.kt:328`)
- Background VFS reads via `ApplicationManager.getApplication().executeOnPooledThread` + `ReadAction.compute` + `invokeLater` (used throughout `FileTreeComponent`)
- Lazy directory expansion via `TreeWillExpandListener` with placeholder "loading..." nodes
- `@Suppress("DEPRECATION")` already used for `TreeSpeedSearch` at line 123 — migration target is `TreeUIHelper.installTreeSpeedSearch()`

### Integration Points
- `FileTreeComponent` is created by `LocalBrowserPanel` and registered as child disposable — any new listeners must be cleaned up in `dispose()`
- New AnActions registered in `plugin.xml` under `SystemExplorer.ActionGroup`
- `IntelliJ TerminalView` API for "Open Terminal Here" functionality
- `RevealFileAction` from IntelliJ platform may provide reference implementation for Reveal in Finder

</code_context>

<deferred>
## Deferred Ideas

- Remote/SSH panel VCS coloring using `RemoteGitBackend` — separate phase
- VCS status icon badges/overlays — decided against for now, may revisit

</deferred>

---

*Phase: 04-file-tree-and-context-menu-polish*
*Context gathered: 2026-02-28*
