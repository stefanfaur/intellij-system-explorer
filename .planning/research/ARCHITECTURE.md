# Architecture Research

**Domain:** IntelliJ plugin — file explorer + Git panel
**Researched:** 2026-02-28
**Confidence:** HIGH (verified against live source code + IntelliJ Platform SDK docs)

---

## Standard Architecture

### System Overview

```
┌───────────────────────────────────────────────────────────────┐
│                    Tool Window Layer (Swing)                   │
│  ┌──────────────────────┐   ┌──────────────────────────────┐  │
│  │  System Explorer TW  │   │   System Explorer Git TW     │  │
│  │  (ExplorerPanel)     │   │   (GitPanelComponent)        │  │
│  └──────────┬───────────┘   └──────────────┬───────────────┘  │
│             │                              │                   │
│  ┌──────────▼───────────┐   ┌──────────────▼───────────────┐  │
│  │  LocalBrowserPanel   │   │  CommitLogPanel (JBTable)    │  │
│  │  RemoteBrowserPanel  │   │  ChangedFilesPanel (JBList)  │  │
│  │  BookmarksPanel      │   │  CommitDetailsPanel (Cards)  │  │
│  └──────────┬───────────┘   └──────────────┬───────────────┘  │
└─────────────┼──────────────────────────────┼───────────────────┘
              │                              │
┌─────────────▼──────────────────────────────▼───────────────────┐
│                     Backend / Service Layer                     │
│  ┌───────────────┐  ┌──────────────────┐  ┌─────────────────┐  │
│  │ FileTreeModel │  │ GitRepositoryReg │  │ SftpConnection  │  │
│  │ VirtualFile   │  │ (PROJECT service)│  │ Manager         │  │
│  │ LocalFileSystem│  │                 │  │                 │  │
│  └───────┬───────┘  └────────┬─────────┘  └────────┬────────┘  │
└──────────┼──────────────────┼───────────────────────┼───────────┘
           │                  │                       │
┌──────────▼──────────────────▼───────────────────────▼───────────┐
│                       Execution Layer                            │
│  ┌──────────────────┐   ┌────────────────────────────────────┐  │
│  │GitCommandExecutor│   │  RemoteGitCommandExecutor (SSH)    │  │
│  │(interface)       │   │  LocalGitCommandExecutor (Process) │  │
│  │                  │   │                                    │  │
│  │ GitBackend       │   │  ContentRevision / DiffProvider    │  │
│  │ (interface)      │   │  (IntelliJ VCS bridge)             │  │
│  └──────────────────┘   └────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Communicates With |
|-----------|----------------|-------------------|
| `GitPanelComponent` | Orchestrator for the Git tool window. Owns `repoCombo`, `branchLabel`, wires the three sub-panels together, runs background data loads. | `GitRepositoryRegistry`, `ActiveBrowserTracker`, `CommitLogPanel`, `ChangedFilesPanel`, `CommitDetailsPanel` |
| `CommitLogPanel` | Renders git log as a `JBTable` with graph dot, subject, author, date columns. Exposes `onCommitSelected` callback. | `GitPanelComponent` (callback), `CommitLogTableModel` |
| `ChangedFilesPanel` | Dual-mode list (staging with checkboxes OR read-only diff file list). Exposes `getCheckedPaths()`. | `GitPanelComponent` |
| `CommitDetailsPanel` | `CardLayout` switcher between DETAILS (commit HTML) and EDITOR (commit message + buttons). | `GitPanelComponent` (callback wired by parent) |
| `GitBackend` (interface) | Contract for all Git operations: log, status, stage, commit, push, branch, diff content. | `LocalGitBackend`, `RemoteGitBackend` |
| `LocalGitBackend` | Shells out to local git CLI via `LocalGitCommandExecutor`. | `GitCommandExecutor`, `GitLogParser`, `GitStatusParser` |
| `RemoteGitBackend` | Executes git commands over SSH via `RemoteGitCommandExecutor`. | SSH channel, same parsers |
| `GitRepositoryRegistry` | Project-level service. Maps backend keys to `GitBackend` instances, fires change events. | `GitPanelComponent` (listener), `RemoteGitVcsManager` |
| `ActiveBrowserTracker` | Project-level service. Broadcasts current browser path so Git panel auto-selects matching repo. | `GitPanelComponent` (listener), `LocalBrowserPanel`, `RemoteBrowserPanel` |
| `FileTreeComponent` | `JTree` wrapper for local filesystem. Owns DnD (Swing `TransferHandler` for drag-out, `DnDNativeTarget` for drop-in). | `BrowserPanel`, `DragDropHandler`, `FileActions` |
| `RemoteBrowserPanel` | Remote SFTP tree. Owns `RemoteTreeDropTarget` (DnD drop-in), `RemoteTreeTransferHandler` (drag-out). | `SftpConnectionManager`, `RemoteTreeDropTarget` |
| `DragDropHandler` | `DnDNativeTarget` implementation. Handles drops from IntelliJ Project View (`TransferableWrapper`) and native apps. | `FileTreeComponent`, `FileActions` |
| `RemoteGitDiffProvider` | `DiffProvider` bridge. Answers getCurrentRevision / getLastRevision / createFileContent for IntelliJ's diff engine. | `RemoteGitCommandExecutor`, `RemoteGitContentRevision` |
| `RemoteGitContentRevision` | `ContentRevision` impl. Fetches file at a given git revision via `git show`. | `RemoteGitCommandExecutor` |

---

## Recommended Project Structure

The existing structure is already sound. New components fit into existing packages:

```
src/main/kotlin/ro/faur/explorer/
├── actions/                  # DnD, file ops, navigation, quick open
│   ├── DragDropHandler.kt    # DnDNativeTarget for local tree drops
│   └── FileTreeTransferHandler.kt  # Swing TransferHandler for local drag-out
├── gitpanel/
│   ├── exec/                 # GitCommandExecutor interface + LocalGitCommandExecutor
│   ├── ui/
│   │   ├── GitPanelComponent.kt    # Orchestrator (existing)
│   │   ├── CommitLogPanel.kt       # Log table (existing)
│   │   ├── ChangedFilesPanel.kt    # File list + staging (existing)
│   │   ├── CommitDetailsPanel.kt   # CardLayout: details + editor (existing)
│   │   ├── DiffPanel.kt            # NEW: embedded DiffRequestPanel wrapper
│   │   └── BranchManagementPanel.kt  # NEW: branch list, checkout, create, delete
│   ├── GitBackend.kt         # Interface (existing, needs: getDiff, branch ops, stash)
│   ├── LocalGitBackend.kt    # Local impl (existing, extend)
│   └── RemoteGitBackend.kt   # Remote impl (existing, extend)
├── remote/
│   ├── git/
│   │   ├── RemoteGitDiffProvider.kt      # DiffProvider (existing)
│   │   ├── RemoteGitContentRevision.kt   # ContentRevision (existing)
│   │   └── RemoteGitCommandExecutor.kt   # SSH git exec (existing)
│   └── ui/
│       ├── RemoteBrowserPanel.kt         # Remote tree (existing)
│       ├── RemoteTreeDropTarget.kt       # DnD target for remote (existing)
│       └── RemoteTreeTransferHandler.kt  # DnD source for remote (existing)
├── ui/
│   ├── FileTreeComponent.kt   # Local tree (existing, DnD polish)
│   └── LocalBrowserPanel.kt   # Local browser container (existing)
└── ...
```

### Structure Rationale

- **gitpanel/ui/**: All Git panel UI components live together. New `DiffPanel` and `BranchManagementPanel` drop in as siblings alongside existing panels — no package restructuring needed.
- **gitpanel/GitBackend.kt**: Adding new methods (`getDiff`, `listBranches`, `checkoutBranch`, `stash`, `stashPop`, `pull`) to the interface and both implementations keeps the dual-backend (local/remote) pattern intact.
- **remote/git/**: The `RemoteGitDiffProvider` + `RemoteGitContentRevision` pair is the bridge to IntelliJ's diff engine. This stays in the remote package because those classes bridge IntelliJ VCS APIs to the SFTP/SSH backend; local diff uses VirtualFile directly.

---

## Architectural Patterns

### Pattern 1: Dual-Backend Interface + Two Implementations

**What:** `GitBackend` interface encapsulates all git operations. `LocalGitBackend` shells to local `git` CLI. `RemoteGitBackend` executes over SSH.

**When to use:** Every new Git operation (branches, stash, pull, diff content) must be added here first.

**Trade-offs:** Doubles the implementation work for each new operation. The payoff is that the UI never knows whether it's talking to a local or remote repo.

**Example:**
```kotlin
interface GitBackend : Disposable {
    // Existing
    fun getCurrentBranch(): String?
    fun getLog(maxCount: Int = 100): List<GitLogEntry>
    fun getWorkingTreeStatus(): List<CommitFile>
    fun stageFiles(paths: List<String>): GitCommandResult
    fun commit(message: String): GitCommandResult
    fun push(): GitCommandResult

    // New additions — add here, then implement in both backends
    fun getDiff(path: String, ref: String = "HEAD"): String    // for pre-commit diff
    fun listBranches(): List<BranchInfo>                        // local + remote branches
    fun checkoutBranch(name: String): GitCommandResult
    fun createBranch(name: String, startPoint: String = "HEAD"): GitCommandResult
    fun deleteBranch(name: String, force: Boolean = false): GitCommandResult
    fun stash(message: String = ""): GitCommandResult
    fun stashList(): List<StashEntry>
    fun stashPop(index: Int = 0): GitCommandResult
    fun pull(): GitCommandResult
}
```

### Pattern 2: Background Executor + EDT Marshal

**What:** All git operations run on `ApplicationManager.getApplication().executeOnPooledThread`. Results are dispatched back to the EDT via `invokeLater`. A `snapshotKey = System.identityHashCode(backend)` guards against stale callbacks.

**When to use:** Every interaction with `GitBackend` — including new diff and branch ops. Never block the EDT on git commands.

**Trade-offs:** Slightly more boilerplate per operation. Required by IntelliJ's threading model (SlowOperation violations otherwise).

**Example:**
```kotlin
ApplicationManager.getApplication().executeOnPooledThread {
    val diff = runCatching { backend.getDiff(path) }.getOrElse { "" }
    ApplicationManager.getApplication().invokeLater {
        if (disposed) return@invokeLater
        if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater
        diffPanel.setContent(diff)
    }
}
```

### Pattern 3: DiffRequestPanel for Embedded Diff (new)

**What:** Use `DiffManager.getInstance().createRequestPanel(project, disposableParent, null)` to obtain a `DiffRequestPanel`. Set its content via `setRequest(SimpleDiffRequest(...))`. The returned panel is a `JComponent` that embeds IntelliJ's standard diff viewer inside any `JPanel`.

**When to use:** Pre-commit diff (show working tree vs HEAD for a selected file), and general file comparison from the file tree context menu.

**Trade-offs:** The embedded panel is a full diff viewer with IntelliJ's syntax highlighting, gutter actions, and theme-aware rendering — effectively free. The only cost is that `createRequestPanel` requires a `Disposable` parent, which the hosting panel (`CommitDetailsPanel` or a new `DiffPanel`) must implement.

**Example:**
```kotlin
class DiffPanel(project: Project, parentDisposable: Disposable) : JPanel(BorderLayout()), Disposable {
    private val diffRequestPanel = DiffManager.getInstance()
        .createRequestPanel(project, this, null)

    init {
        add(diffRequestPanel.component, BorderLayout.CENTER)
        Disposer.register(parentDisposable, this)
    }

    fun showDiff(title: String, beforeText: String, afterText: String, beforeTitle: String, afterTitle: String) {
        val factory = DiffContentFactory.getInstance()
        val before = factory.create(beforeText)
        val after = factory.create(afterText)
        val request = SimpleDiffRequest(title, before, after, beforeTitle, afterTitle)
        diffRequestPanel.setRequest(request)
    }

    override fun dispose() {}
}
```

For `showDiff` (popup window, not embedded), use:
```kotlin
DiffManager.getInstance().showDiff(project, request)
// or with FRAME hints:
DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)
```

### Pattern 4: CardLayout for Mode Switching

**What:** `CommitDetailsPanel` already uses `CardLayout` to switch between DETAILS (read-only commit HTML) and EDITOR (commit message textarea + buttons). The same pattern applies for the Git panel bottom region when adding a diff view: extend CardLayout to a third card DIFF, or replace CommitDetailsPanel with a tabbed pane.

**When to use:** When the same screen region must display different UIs depending on context (commit selected = details, working tree file selected = diff, no selection = empty).

**Trade-offs:** Simple and lightweight. The card is invisible but still in memory. Fine for three modes; if it grows beyond three consider a tab strip.

### Pattern 5: DnD Split System (Existing, Polish Required)

**What:** Two DnD systems coexist intentionally. Swing `TransferHandler` + `dragEnabled=true` handles drag-out. IntelliJ `DnDNativeTarget` (registered with `DnDManager`) handles drop-in. They must never overlap on the same component or they compete for mouse-drag gestures.

**When to use:** This is the established pattern in the codebase. Do not change the split. Polish involves fixing edge cases (ghost drag image, auto-scroll during drag, drop indicator rendering).

---

## Data Flow

### Pre-Commit Diff Flow (new)

```
User clicks file in ChangedFilesPanel (staging mode)
    ↓
ChangedFilesPanel fires onFileSelected(commitFile)
    ↓
GitPanelComponent (on pooled thread)
    → backend.getDiff(path, "HEAD")  [git diff HEAD -- <path>]
    ↓ returns unified diff string
GitPanelComponent (back on EDT)
    → DiffPanel.showDiff(title, beforeContent, afterContent, ...)
    [DiffContentFactory.create() wraps strings as DiffContent]
    [SimpleDiffRequest passed to DiffRequestPanel.setRequest()]
    ↓
DiffRequestPanel renders side-by-side diff inside the Git tool window
```

### Branch Management Flow (new)

```
User opens branch panel or toolbar branch button
    ↓
BranchManagementPanel.refresh() (pooled thread)
    → backend.listBranches()  [git branch -a --format=...]
    ↓ returns List<BranchInfo>
BranchManagementPanel (EDT)
    → renders JBList of branches with current branch highlighted
    ↓
User selects branch → "Checkout" button
    ↓
GitPanelComponent (pooled thread)
    → backend.checkoutBranch(name)  [git checkout <name>]
    ↓ result back on EDT
    → GitPanelComponent.reloadData()  [refresh log + status]
    → branchLabel updated
```

### Stash Flow (new)

```
User clicks "Stash" in Git panel toolbar (new action)
    ↓
StashDialog.show() — simple input dialog for stash message
    ↓
GitPanelComponent (pooled thread)
    → backend.stash(message)  [git stash push -m <message>]
    ↓
GitPanelComponent (EDT)
    → reloadData()
```

### DnD Drop Flow (existing, polish)

```
User drags file from IntelliJ Project View
    ↓
DnDManager fires DnDTarget.update() on DragDropHandler
    → DragDropHandler resolves drop target dir from tree point
    → event.setDropPossible(true / false)
    ↓
User releases drop
    ↓
DragDropHandler.drop()
    → extractFiles(event)  [handles TransferableWrapper, FileFlavorProvider, Transferable, native]
    → performDrop(files, targetDir, isMove)
    → FileActions.copyTo() / moveTo()
    → FileTreeComponent.refresh()
```

### Key Data Flows Summary

1. **GitBackend → GitPanelComponent → UI panels:** All data flows from backend through the orchestrator. Sub-panels are dumb — they only display what is pushed to them via setData/setFiles/setRequest calls.
2. **User action → Callback → GitPanelComponent:** User interactions in sub-panels fire callbacks (`onCommitSelected`, `onFileSelected`, `onAction`). The orchestrator owns all write operations.
3. **VCS bridge (RemoteGitDiffProvider):** Flows from IntelliJ diff engine requesting revisions → `RemoteGitDiffProvider` → `RemoteGitCommandExecutor` (SSH git show) → returns content string → IntelliJ renders diff. This bridge is only used when IntelliJ itself initiates a diff (e.g., "Show Diff" from the VCS panel for remote files). The Git panel's own diff flow (Pattern 3 above) is independent.

---

## Suggested Build Order

Dependencies determine this order. Each item depends on the items above it.

### Phase A: GitBackend Interface Extensions (prerequisite for everything else)

Add new method signatures to `GitBackend.kt`, implement in both `LocalGitBackend` and `RemoteGitBackend`. Methods needed:
- `getDiff(path: String, ref: String): String`
- `listBranches(): List<BranchInfo>`
- `checkoutBranch(name: String): GitCommandResult`
- `createBranch(name: String, startPoint: String): GitCommandResult`
- `deleteBranch(name: String, force: Boolean): GitCommandResult`
- `stash(message: String): GitCommandResult`
- `stashList(): List<StashEntry>`
- `stashPop(index: Int): GitCommandResult`
- `pull(): GitCommandResult`

This must ship first. All UI work depends on these methods being callable.

### Phase B: Pre-Commit Diff Viewer (depends on: getDiff)

1. Create `DiffPanel` wrapping `DiffManager.createRequestPanel()` — MEDIUM effort.
2. Add `onFileSelected` callback to `ChangedFilesPanel`.
3. Wire in `GitPanelComponent`: when staging mode file selected, load diff and show `DiffPanel`.
4. Extend `CommitDetailsPanel`'s CardLayout with a third DIFF card, OR replace the bottom splitter layout to show diff as a fourth region.
5. Add context menu item "Show Diff" to `ChangedFilesPanel` for popup diff (uses `DiffManager.showDiff()`).

### Phase C: Branch Management Panel (depends on: listBranches, checkoutBranch, createBranch, deleteBranch)

1. Create `BranchManagementPanel` (JBList with branch names, current highlighted, toolbar with checkout/create/delete).
2. Integrate into `GitPanelComponent` toolbar: branch label becomes a clickable dropdown or the panel appears as a sidebar/popup.
3. Wire pull operation into the toolbar alongside push.

### Phase D: Stash Panel (depends on: stash, stashList, stashPop)

1. Add stash toolbar button → simple dialog for message.
2. Add `StashListPanel` (JBList of stash entries with pop/apply actions).
3. Integrate as a tab or collapsible section in the Git panel layout.

### Phase E: DnD Polish (depends on: existing DnD infrastructure)

This is independent — no new Backend methods needed. Work involves:
1. Fix ghost drag image (provide custom `DragSourceAdapter`).
2. Add auto-scroll during drag in `FileTreeComponent`.
3. Improve drop target highlight rendering in `DragDropHandler.update()`.
4. Fix `RemoteTreeDropTarget` edge cases (drop onto file vs directory).

### Phase F: File Tree UI Polish (independent)

1. Fix icon cache invalidation on dumb mode transitions.
2. Fix expand/collapse glitch (duplicate placeholder children).
3. Widen context menu coverage (missing actions: reveal in Finder/Explorer, open terminal here).
4. Improve refresh behavior: narrow VFS scope so refresh only touches visible subtree.

---

## Integration Points

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `GitPanelComponent` ↔ `CommitLogPanel` | Direct field reference + `onCommitSelected` lambda | Panel is internal, not a service |
| `GitPanelComponent` ↔ `GitBackend` | Direct interface call on pooled thread | Always background; never EDT |
| `GitPanelComponent` ↔ `GitRepositoryRegistry` | Listener pattern (registry fires `() -> Unit`) | Registry is a PROJECT service |
| `LocalBrowserPanel` ↔ `ActiveBrowserTracker` | `ActiveBrowserTracker.publish(connName, path)` | PROJECT service, observer |
| `FileTreeComponent` ↔ `DragDropHandler` | Direct constructor injection | DragDropHandler holds reference to FileTreeComponent |
| `DiffPanel` ↔ `DiffManager` | `DiffManager.getInstance().createRequestPanel()` | IntelliJ platform API, HIGH confidence |
| `RemoteGitDiffProvider` ↔ IntelliJ VCS diff engine | `DiffProvider` interface extension point | Registered via `RemoteGitVcs.getDiffProvider()` |

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| IntelliJ `DiffManager` | `DiffManager.getInstance().createRequestPanel(project, disposable, null)` returns embedded `JComponent` | MEDIUM confidence — API stable since IDEA 14.1; current as of 2026 |
| IntelliJ `DiffContentFactory` | `DiffContentFactory.getInstance().create(text)` wraps strings | Used to build `SimpleDiffRequest` content |
| `git` CLI (local) | `ProcessBuilder` in `LocalGitCommandExecutor` | Existing pattern — extend with new commands |
| `git` CLI (remote) | SSH channel exec in `RemoteGitCommandExecutor` | Existing pattern — extend with new commands |
| IntelliJ `DnDManager` | `DnDManager.getInstance().registerTarget(handler, component)` | Already wired; polish is behavioral, not structural |

---

## Anti-Patterns

### Anti-Pattern 1: Registering Both DnDSource and Swing dragEnabled

**What people do:** Register the same component as a `DnDSource` via `DnDManager` AND set `dragEnabled=true` (Swing).
**Why it's wrong:** Both systems install mouse-drag gesture recognizers. They compete for the same mouse events, causing drag to either not start or start and cancel immediately.
**Do this instead:** Use Swing `TransferHandler` + `dragEnabled=true` for drag-out only. Register `DnDNativeTarget` only (not `DnDSource`) for drop-in.

### Anti-Pattern 2: Using Transferable Flavor Iteration on TransferableWrapper

**What people do:** Call `transferable.isDataFlavorSupported(...)` and `transferable.getTransferData(...)` on IntelliJ's `TransferableWrapper`.
**Why it's wrong:** `TransferableWrapper` extends `FileFlavorProvider`, not `Transferable`. It does not implement flavor-based retrieval. The cast succeeds but the flavor lookup returns nothing.
**Do this instead:** Check `if (attached is TransferableWrapper)` and call `wrapper.psiElements` and `wrapper.asFileList()` directly.

### Anti-Pattern 3: Calling GitBackend on the EDT

**What people do:** Call `backend.getLog()`, `backend.commit()`, etc. directly in an action handler or `invokeLater`.
**Why it's wrong:** Git CLI execution blocks the thread. On the EDT this freezes the UI and triggers IntelliJ's SlowOperation detection.
**Do this instead:** Always use `executeOnPooledThread` with EDT marshal back via `invokeLater` and a stale-check guard.

### Anti-Pattern 4: Embedding DiffManager via the Old Legacy API

**What people do:** Use `com.intellij.openapi.diff.DiffManager` (legacy package).
**Why it's wrong:** The legacy `com.intellij.openapi.diff.*` API is deprecated. The modern API is `com.intellij.diff.*` (available since IDEA 14.1).
**Do this instead:** Use `com.intellij.diff.DiffManager`, `com.intellij.diff.DiffContentFactory`, `com.intellij.diff.requests.SimpleDiffRequest`.

### Anti-Pattern 5: Hardcoding Branch Operations into GitPanelComponent

**What people do:** Add branch management logic directly into the existing `GitPanelComponent` class.
**Why it's wrong:** `GitPanelComponent` is already 420+ lines and growing. Adding branch UI and stash UI makes it unmaintainable.
**Do this instead:** Extract each major feature into its own panel class (`BranchManagementPanel`, `StashListPanel`), then wire them into `GitPanelComponent` the same way `CommitLogPanel` and `ChangedFilesPanel` are wired today.

---

## Scaling Considerations

This plugin is not a web service, so "scale" means IDE performance under large repos / many files.

| Concern | Small repos (<1k files) | Large repos (>50k files) | Notes |
|---------|------------------------|--------------------------|-------|
| Git log loading | Instant | Acceptable with `maxCount=200` limit | Already capped at 200; consider pagination |
| getDiff() content | Fast | Fast — only one file at a time | No bulk loading needed |
| listBranches() | Fast | Fast — linear in branch count, not file count | Even repos with 500 branches render quickly in a JBList |
| File tree render | Fast | May be slow if opened at root | Already uses lazy expansion; keep it |
| DnD drop extraction | Fast | Fast | Already handles batch file lists |

---

## Sources

- Live codebase inspection: `/src/main/kotlin/ro/faur/explorer/` — HIGH confidence (source of truth)
- IntelliJ Platform SDK VCS Integration: https://plugins.jetbrains.com/docs/intellij/vcs-integration-for-plugins.html — MEDIUM confidence
- `DiffManager.java` source: https://github.com/JetBrains/intellij-community/blob/master/platform/diff-api/src/com/intellij/diff/DiffManager.java — HIGH confidence
- IntelliJ support forums on DiffManager / DiffRequestPanel — MEDIUM confidence (corroborates API shape)
- git4idea source: https://github.com/JetBrains/intellij-community/tree/master/plugins/git4idea — MEDIUM confidence (not directly used by this plugin, but confirms git4idea is a separate optional dependency that this plugin intentionally avoids)
- MEMORY.md: DnD architecture notes — HIGH confidence (validated against live code)

---

*Architecture research for: IntelliJ Plugin — System Explorer + Git Panel*
*Researched: 2026-02-28*
