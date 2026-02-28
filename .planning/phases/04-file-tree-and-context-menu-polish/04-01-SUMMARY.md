---
phase: 04-file-tree-and-context-menu-polish
plan: 01
subsystem: ui
tags: [vcs-colors, file-tree, dumb-mode, tree-speed-search]
dependency_graph:
  requires: []
  provides: [vcs-color-rendering, dumb-mode-icon-cache-invalidation, collapse-placeholder-fix, speed-search-migration]
  affects: [FileTreeComponent]
tech_stack:
  added: []
  patterns: [FileStatusManager.addFileStatusListener, DumbService.DUMB_MODE messageBus, TreeUIHelper.installTreeSpeedSearch, Optional-as-nullable-cache-value]
key_files:
  created: []
  modified:
    - src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt
decisions:
  - "FileStatusManager.addFileStatusListener(listener, disposable) used instead of FILE_STATUS_TOPIC messageBus (topic constant does not exist in IJ 2024.3 API; addFileStatusListener is the correct method)"
  - "DumbService.DUMB_MODE is the correct topic constant for dumb-mode listener (not DUMB_MODE_LISTENER)"
  - "TreeUIHelper.installTreeSpeedSearch uses 3-argument overload with explicit Convertor<TreePath, String> type — 2-argument lambda-only overload not available in IJ 2024.3"
  - "directoryStatusCache uses ConcurrentHashMap<String, Optional<Color>> — Optional.empty() as sentinel for computed-but-no-color entries (ConcurrentHashMap cannot store null values)"
  - "FileStatusManager.getRecursiveStatus(vf) exists in the API but 1-level manual propagation chosen for explicit control over caching"
metrics:
  duration: 9 min
  completed: 2026-03-01
  tasks_completed: 2
  files_modified: 1
---

# Phase 4 Plan 01: File Tree VCS Colors and Polish Summary

VCS color coding in FileTreeComponent using FileStatusManager with real-time FileStatusListener, DumbModeListener for icon cache invalidation, collapse-placeholder restore fix, and TreeUIHelper speed search migration.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | VCS color coding + FileStatusListener + DumbModeListener + directoryStatusCache | 70c4d89 |
| 2 | treeWillCollapse placeholder restore + TreeSpeedSearch migration | 36a9686 |

## What Was Built

### Task 1: VCS Color Coding and Real-Time Listeners

**VCS color rendering:** `VirtualFileCellRenderer.customizeCellRenderer()` now calls `getEffectiveVcsColor(vf)` after the icon assignment. The returned color (or null for `NOT_CHANGED`) is wrapped into a `SimpleTextAttributes` and applied to all `append()` calls for the filename. Files outside any VCS repo get `FileStatus.NOT_CHANGED` → null → `REGULAR_ATTRIBUTES`, so no color glitches.

**Directory status propagation:** `getEffectiveVcsColor()` scans 1-level of directory children for the first non-`NOT_CHANGED` status and returns that status's color. Results are cached in `directoryStatusCache: ConcurrentHashMap<String, Optional<Color>>`. The `Optional` sentinel pattern handles "computed but no color" vs. "not yet computed".

**FileStatusListener:** `FileStatusManager.getInstance(project).addFileStatusListener(listener, this)` subscribes to VCS change notifications. On any change, `directoryStatusCache.clear()` plus `tree.repaint()` on the EDT. Auto-disconnects when `FileTreeComponent` (the Disposable `this`) is disposed.

**DumbModeListener:** `project.messageBus.connect(this).subscribe(DumbService.DUMB_MODE, ...)` subscribes to dumb-mode transitions. On `exitDumbMode()`, `iconCache.clear()` plus `tree.repaint()`. Auto-disconnects via the connection's `Disposable`.

**Cache invalidation:** `directoryStatusCache.clear()` added to both `setRoot()` and `refresh()` alongside the existing `iconCache.clear()` calls.

### Task 2: Collapse Placeholder Fix and Speed Search Migration

**treeWillCollapse fix:** Previously empty. Now restores the "loading..." placeholder via `invokeLater` after collapse completes. This ensures the `treeWillExpand` guard (`childCount != 1 || userObject !is String`) correctly triggers lazy re-population on subsequent expansion, preventing duplicate children.

**TreeSpeedSearch migration:** Replaced `@Suppress("DEPRECATION") TreeSpeedSearch(tree) { ... }` with `TreeUIHelper.getInstance().installTreeSpeedSearch(tree, Convertor<TreePath, String> { ... }, true)`. Removed `import com.intellij.ui.TreeSpeedSearch`.

## Deviations from Plan

### API Corrections (Rule 1 - Bug Prevention)

**1. [Rule 1 - API] FileStatusManager has no FILE_STATUS_TOPIC static field in IJ 2024.3**
- Found during: Task 1
- Issue: Plan spec referenced `FileStatusManager.FILE_STATUS_TOPIC` messageBus topic; this constant does not exist in IJ 2024.3's `FileStatusManager` API
- Fix: Used `FileStatusManager.addFileStatusListener(listener, disposable)` which is the correct method-based API (verified via `javap` decompile of app-client.jar)
- Files modified: FileTreeComponent.kt
- Commit: 70c4d89

**2. [Rule 1 - API] DumbService topic is DUMB_MODE not DUMB_MODE_LISTENER**
- Found during: Task 1
- Issue: Plan spec referenced `DumbService.DUMB_MODE_LISTENER`; the actual constant is `DumbService.DUMB_MODE` (verified via `javap` decompile of util-8.jar)
- Fix: Changed to `DumbService.DUMB_MODE`
- Files modified: FileTreeComponent.kt
- Commit: 70c4d89

**3. [Rule 1 - API] TreeUIHelper.installTreeSpeedSearch has no 2-argument (tree, lambda) overload**
- Found during: Task 2
- Issue: Plan spec showed 2-argument lambda form; actual API has `(JTree)` and `(JTree, Convertor<in TreePath, String>, Boolean)` overloads
- Fix: Used 3-argument overload with explicit `Convertor<TreePath, String> { ... }` type annotation
- Files modified: FileTreeComponent.kt
- Commit: 36a9686

## Self-Check: PASSED

- FileTreeComponent.kt: FOUND
- 04-01-SUMMARY.md: FOUND
- Commit 70c4d89: FOUND
- Commit 36a9686: FOUND
