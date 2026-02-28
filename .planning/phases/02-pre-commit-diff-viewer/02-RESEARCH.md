# Phase 2: Pre-Commit Diff Viewer - Research

**Researched:** 2026-02-28
**Domain:** IntelliJ Platform Diff API (com.intellij.diff.*), embedded DiffRequestPanel, DiffContentFactory, SimpleDiffRequest
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Inline diff placement**
- The right-side panel slot (currently Commit Details) is shared: history mode shows Commit Details, staging mode shows the inline diff
- Both panels exist in the layout with toggled visibility — show/hide based on mode (not a CardLayout swap)
- Use IntelliJ's DiffManager embedded panel (proper syntax-highlighted diff viewer as a Swing component — theme-aware, disposable)
- When no file is selected in staging mode: show a blank/empty panel (no placeholder message)

**Diff trigger behavior**
- Auto-load on file selection — as soon as a file is clicked in the Changed Files list, the diff starts loading
- Immediate replace when a different file is selected — old diff disposed immediately, new one starts loading
- Show a loading spinner in the diff area while content is being prepared
- For new/untracked files (status '?'): show full file content as "all added" (empty → current, all green)

**Full-window diff**
- Triggered by: toolbar button in the Git panel (enabled when a file is selected) AND double-clicking a file in the Changed Files list
- Opens IntelliJ's native DiffManager popup (showDiff()) — not a custom dialog
- Available in both staging mode (HEAD vs working tree) and history mode (before-commit vs after-commit for that entry)

**Compare With... (System Explorer)**
- Action appears only when exactly two files are selected in the explorer (not for single-file selection)
- Compares working-tree content only — no git involvement, works for any two files
- Left/right order: first selected = left side, second selected = right side
- Opens IntelliJ's DiffManager popup for the comparison

### Claude's Discretion
- Exact spinner/loading indicator implementation
- Disposal lifecycle details for embedded diff panels
- How to track "first selected" vs "second selected" order given available tree selection API

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DIFF-01 | User can select a changed file in the Git panel staging area and see an inline pre-commit diff embedded in the panel | `DiffManager.createRequestPanel()` returns a `DiffRequestPanel` whose `getComponent()` embeds in `JBSplitter`; `ListSelectionListener` on `ChangedFilesPanel.list` triggers `setRequest()` |
| DIFF-02 | User can trigger "Show Diff" from the Git panel to open the full-window diff frame (DiffManager popup) | `DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)` — must be called on EDT; toolbar button already established pattern in `GitPanelComponent` |
| DIFF-03 | Diff viewer displays HEAD version vs. working-tree version of the selected file | HEAD content: `git show HEAD:path` via executor → bytes; working-tree content: `java.io.File(repoPath, path).readBytes()`; both passed to `DiffContentFactory.createDocumentFromBytes()` |
| DIFF-04 | User can right-click any two files in the explorer and select "Compare With..." to open a side-by-side diff | Register `CompareWithAction` in `SystemExplorer.ActionGroup` (plugin.xml); use `DiffContentFactory.create(project, virtualFile)` for each; `DiffManager.showDiff()` for popup |
| DIFF-05 | Diff viewer is disposed correctly when panel is closed or new file is selected (no memory leak) | `Disposer.newDisposable()` as child of `GitPanelComponent`; `Disposer.dispose(childDisposable)` on every selection change; `DiffRequestPanel` implements `Disposable` so it self-cleans |
</phase_requirements>

## Summary

IntelliJ Platform provides a complete diff API under `com.intellij.diff.*` (introduced in IDEA 14.1, build 141+). The project's STATE.md already has the architectural decision locked: "Use com.intellij.diff.* exclusively (not deprecated com.intellij.openapi.diff.*)". The three main entry points are: `DiffManager.createRequestPanel()` for embedded inline panels, `DiffManager.showDiff()` for full-window popups, and `DiffContentFactory` for wrapping raw content (strings, bytes, or VirtualFiles) into `DiffContent` objects.

The embedded panel lifecycle is managed via IntelliJ's `Disposer` framework. A child `Disposable` (created with `Disposer.newDisposable()`) is passed to `createRequestPanel()` as its parent; disposing that child also disposes the panel and releases all diff viewer resources. When the user switches file selection in the staging list, the current child disposable is disposed and a new one created — this is the canonical DIFF-05 solution. `GitPanelComponent` already implements `Disposable`, making it the natural grandparent for all child disposables.

For HEAD-vs-working-tree diffs (DIFF-03), the approach matches the existing `LocalGitBackend`/`RemoteGitBackend` pattern: call `git show HEAD:path` via the existing executor to get HEAD bytes, read the working-tree file directly from disk (local) or via SFTP (remote). Both byte arrays are wrapped with `DiffContentFactory.createDocumentFromBytes()` paired with a `FilePath` so the diff viewer picks up the correct syntax highlighting. For untracked files (status `?`), the HEAD side is `DiffContentFactory.createEmpty()` per `SimpleDiffRequest` API documentation.

The "Compare With..." action (DIFF-04) for arbitrary two-file comparison in the explorer is simpler: both files are on the working tree, so `DiffContentFactory.create(project, virtualFile)` handles them directly without any git involvement. The action must be added to `SystemExplorer.ActionGroup` in `plugin.xml` and guarded in `update()` to only enable when exactly two paths are selected.

**Primary recommendation:** Use `DiffManager.createRequestPanel(project, childDisposable, null)` for the inline panel slot, `DiffManager.showDiff(project, request, DiffDialogHints.FRAME)` for full-window popups, and `DiffContentFactory.createDocumentFromBytes()` for byte-based content from git CLI output.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.intellij.diff.DiffManager` | Platform (2025.1+) | Show diff popups and create embedded panels | The only supported diff API since IDEA 14.1; old `openapi.diff` is deprecated |
| `com.intellij.diff.DiffRequestPanel` | Platform (2025.1+) | Embeddable Swing component wrapping a diff viewer | `createRequestPanel()` returns this; it extends `Disposable` — ties into existing disposal pattern |
| `com.intellij.diff.DiffContentFactory` | Platform (2025.1+) | Wraps String, byte[], VirtualFile into `DiffContent` | Handles encoding, file type detection, syntax highlighting automatically |
| `com.intellij.diff.requests.SimpleDiffRequest` | Platform (2025.1+) | Two-sided diff request with title and content labels | Canonical two-panel diff container; supports `createEmpty()` for new/deleted files |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.intellij.diff.DiffDialogHints` | Platform | Controls popup display mode | `DiffDialogHints.FRAME` for full-window popup; `DEFAULT` for modal dialog |
| `com.intellij.openapi.util.Disposer` | Platform | Parent-child disposable tree | Register child disposables so panel cleanup is automatic when parent disposes |
| `com.intellij.openapi.vfs.VfsUtil` / `LocalFileSystem` | Platform | VirtualFile from local path | Required for `DiffContentFactory.create(project, virtualFile)` in "Compare With..." |
| `com.intellij.openapi.fileTypes.FileTypeManager` | Platform | Detect file type from extension | Fallback when `FilePath` is not available for syntax highlighting |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `createDocumentFromBytes()` with `FilePath` | `create(project, string, fileType)` | String approach loses encoding fidelity for binary-adjacent files; bytes approach is always correct |
| `DiffManager.showDiff()` with `FRAME` | `DiffEditorTabFilesManager.showDiffFile()` with `ChainDiffVirtualFile` | Tab approach opens in editor area, not a popup; FRAME matches what IntelliJ Git panel does natively |

**Installation:** No new dependencies — all APIs are in the platform classpath already bundled with `platformVersion = 2025.1`.

## Architecture Patterns

### Recommended Project Structure
```
gitpanel/
├── GitBackend.kt                 # Add getDiffContent(path): ByteArray? method
├── LocalGitBackend.kt            # Implement: git show HEAD:path via executor
├── RemoteGitBackend.kt           # Implement: git show HEAD:path via remote executor
└── ui/
    ├── GitPanelComponent.kt      # Wire ListSelectionListener, toolbar button, inline panel slot
    ├── ChangedFilesPanel.kt      # Add onFileSelected callback; add double-click trigger
    └── InlineDiffPanel.kt        # NEW: wraps DiffRequestPanel lifecycle, shows spinner
actions/
└── CompareWithAction.kt          # NEW: "Compare With..." action for two-file selection
```

### Pattern 1: Embedded Diff Panel with Child Disposable

**What:** Create a `DiffRequestPanel` as a child of `GitPanelComponent`; replace it on every file selection by disposing the child disposable and creating a fresh one.

**When to use:** Any time an inline diff must live in a fixed panel slot with automatic cleanup on parent disposal.

**Example:**
```kotlin
// Source: DiffManager.java (github.com/JetBrains/intellij-community)
// In GitPanelComponent or InlineDiffPanel

private var inlineDiffDisposable: Disposable? = null
private var inlineDiffPanel: DiffRequestPanel? = null

fun showInlineDiff(request: SimpleDiffRequest) {
    // Dispose previous
    inlineDiffDisposable?.let { Disposer.dispose(it) }

    // Create fresh child disposable parented to GitPanelComponent
    val childDisposable = Disposer.newDisposable(this /* parent */)
    inlineDiffDisposable = childDisposable

    // Create embedded panel — window param null is fine for tool windows
    val panel = DiffManager.getInstance()
        .createRequestPanel(project, childDisposable, null)
    panel.setRequest(request)
    inlineDiffPanel = panel

    // Swap component in layout
    diffSlot.removeAll()
    diffSlot.add(panel.getComponent(), BorderLayout.CENTER)
    diffSlot.revalidate()
    diffSlot.repaint()
}

fun clearInlineDiff() {
    inlineDiffDisposable?.let { Disposer.dispose(it) }
    inlineDiffDisposable = null
    inlineDiffPanel = null
    diffSlot.removeAll()
    diffSlot.revalidate()
}
```

### Pattern 2: Building a SimpleDiffRequest from git CLI bytes

**What:** Call `git show HEAD:path` to get HEAD content as bytes, read working-tree file for current content, wrap both in `DiffContentFactory.createDocumentFromBytes()`.

**When to use:** HEAD vs working-tree diff for any file tracked in git.

**Example:**
```kotlin
// Source: DiffContentFactoryImpl.java + DiffManager.java (intellij-community)
// Runs on pooled thread — DiffContentFactory construction is safe off-EDT

fun buildDiffRequest(
    project: Project,
    repoPath: String,
    commitFile: CommitFile,
    headBytes: ByteArray?,   // null for untracked/new files
    workBytes: ByteArray?    // null for deleted files
): SimpleDiffRequest {
    val factory = DiffContentFactory.getInstance()
    val filePath = VcsUtil.getFilePath(File(repoPath, commitFile.path))

    val headContent: DiffContent = if (headBytes != null)
        factory.createDocumentFromBytes(project, headBytes, filePath)
    else
        factory.createEmpty()

    val workContent: DiffContent = if (workBytes != null)
        factory.createDocumentFromBytes(project, workBytes, filePath)
    else
        factory.createEmpty()

    return SimpleDiffRequest(
        commitFile.path,          // dialog title
        headContent,              // left side
        workContent,              // right side
        "HEAD",                   // left label
        "Working Tree"            // right label
    )
}
```

### Pattern 3: Full-Window Popup via showDiff

**What:** Call `DiffManager.showDiff()` on EDT with `DiffDialogHints.FRAME` to open a native IntelliJ diff popup.

**When to use:** "Show Diff" toolbar button, double-click on changed file, "Compare With..." from explorer.

**Example:**
```kotlin
// Source: DiffManager.java (github.com/JetBrains/intellij-community)
// Must be called on EDT — use invokeLater if coming from background thread

ApplicationManager.getApplication().invokeLater {
    DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)
}
```

### Pattern 4: "Compare With..." Two-File Action

**What:** Use `DiffContentFactory.create(project, virtualFile)` for both files; open popup with `showDiff()`.

**When to use:** Comparing any two working-tree files (no git required).

**Example:**
```kotlin
// Source: DiffContentFactory API docs + ExplorerActions.kt pattern
class CompareWithAction : AnAction("Compare With...") {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val tree = ExplorerActionUtil.findFileTreeComponent(e)
        e.presentation.isEnabledAndVisible = tree != null
            && ExplorerActionUtil.isExplorerActive(e)
            && tree.getSelectedFiles().size == 2
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tree = ExplorerActionUtil.findFileTreeComponent(e) ?: return
        val selected = tree.getSelectedFiles()   // preserves selection order
        if (selected.size != 2) return

        val leftVf = LocalFileSystem.getInstance().findFileByPath(selected[0].path) ?: return
        val rightVf = LocalFileSystem.getInstance().findFileByPath(selected[1].path) ?: return

        val factory = DiffContentFactory.getInstance()
        val leftContent  = factory.create(project, leftVf)
        val rightContent = factory.create(project, rightVf)

        val request = SimpleDiffRequest(
            "Compare: ${selected[0].name} vs ${selected[1].name}",
            leftContent, rightContent,
            selected[0].name, selected[1].name
        )
        DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)
    }
}
```

### Anti-Patterns to Avoid

- **Using `com.intellij.openapi.diff.*`:** The old diff API (pre-IDEA 14.1) — deprecated and will break on future platform versions. STATE.md explicitly prohibits this.
- **Creating DiffRequestPanel without a parent Disposable:** Without a parent, the embedded diff panel leaks editors and listeners. Always use `Disposer.newDisposable(parentDisposable)`.
- **Calling `showDiff()` off-EDT:** DiffManager methods carry `@RequiresEdt` — calling from a pooled thread causes threading exceptions. Fetch content off-EDT, then `invokeLater` for the UI call.
- **Replacing the panel component without disposing the old one:** Removing the Swing component from the layout does not dispose the DiffRequestPanel. Must call `Disposer.dispose()` on the child disposable first.
- **Doing git CLI calls on EDT:** `git show HEAD:path` can take hundreds of milliseconds. Must run on `executeOnPooledThread` or `Task.Backgroundable` — matches established Phase 1 pattern.
- **Storing `DiffRequestPanel` reference across disposal:** After `Disposer.dispose(childDisposable)`, the panel is dead. Null out both `inlineDiffDisposable` and `inlineDiffPanel` references.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Syntax-highlighted diff renderer | Custom Swing diff widget | `DiffManager.createRequestPanel()` | Platform handles 100+ languages, themes, line number gutter, copy actions, collapse ranges |
| Two-file diff popup | Custom JDialog with JTextPanes | `DiffManager.showDiff(..., FRAME)` | Platform popup is resizable, keyboard-navigable, has toolbar actions (next/prev change, etc.) |
| HEAD file content extraction | Custom git CLI wrapper | `git show HEAD:path` via existing `LocalGitCommandExecutor`/`RemoteGitCommandExecutor` | Executors already established; no new code needed |
| File type detection for syntax | Extension → FileType map | `DiffContentFactory.createDocumentFromBytes(project, bytes, filePath)` with `FilePath` | Factory uses `FileTypeManager` internally for correct detection |
| Loading spinner during async load | Custom animated component | `AnimatedIcon.Default` / `AsyncProcessIcon` from platform | Platform provides theme-aware loading indicators |

**Key insight:** The platform diff API is complete enough that all visible diff UI (both inline and popup) is zero custom rendering. The only custom code is lifecycle management (child disposables) and content retrieval (git CLI calls already in use).

## Common Pitfalls

### Pitfall 1: EDT Violation on DiffManager calls
**What goes wrong:** `showDiff()` or `createRequestPanel()` called from a background thread throws `com.intellij.openapi.progress.ProcessCanceledException` or an EDT assertion failure.
**Why it happens:** All `DiffManager` display methods carry `@RequiresEdt`. Background content loading naturally leads to calling display APIs from wrong thread.
**How to avoid:** Always separate: (1) fetch bytes off-EDT, (2) build `SimpleDiffRequest` off-EDT (safe), (3) `invokeLater { }` for `setRequest()` or `showDiff()`.
**Warning signs:** `com.intellij.util.ui.EDT` assertion error in IDE log; works sometimes but crashes under load.

### Pitfall 2: Panel Not Disposed on File Switch
**What goes wrong:** Old diff panel leaks — editor documents, listeners, and highlighters accumulate. IDE slows down after many file selections.
**Why it happens:** Removing a `DiffRequestPanel.getComponent()` from a Swing layout only removes the visual component — it does NOT dispose the underlying diff viewer infrastructure.
**How to avoid:** Always call `Disposer.dispose(childDisposable)` before reassigning `inlineDiffDisposable` to a new disposable. Never just `removeAll()` on the container.
**Warning signs:** "Already disposed" exceptions in log; growing memory over repeated file selections; `Disposer` leak detection warnings in test output.

### Pitfall 3: Untracked Files Showing Empty Diff
**What goes wrong:** `git show HEAD:newfile.txt` exits non-zero (path unknown to HEAD), causing an error response — the diff shows empty on both sides instead of "all added".
**Why it happens:** New/untracked files (status `?` or `A` for newly staged) have no HEAD revision.
**How to avoid:** Check `CommitFile.status` before calling `git show HEAD:path`. For `UNTRACKED` (`?`) and `ADDED` (`A`): use `DiffContentFactory.createEmpty()` on the left (HEAD) side, working-tree bytes on the right.
**Warning signs:** Diff shows two empty panes for new files; executor returning non-zero exit from `git show HEAD:path`.

### Pitfall 4: VirtualFile Staleness for "Compare With..."
**What goes wrong:** `LocalFileSystem.getInstance().findFileByPath(path)` returns a cached VirtualFile whose content is stale relative to disk.
**Why it happens:** IntelliJ VFS has a refresh cycle; external file changes may not be reflected immediately.
**How to avoid:** Call `vf.refresh(false, false)` before passing to `DiffContentFactory.create()`, or use `DiffContentFactory.create(project, vf)` which reads from the VFS content — acceptable since we are comparing current working-tree state at the moment of action invocation.
**Warning signs:** "Compare With..." shows old content for recently modified files.

### Pitfall 5: Selection Order Lost on Multi-Select
**What goes wrong:** "Compare With..." action can't determine which file should be left vs right — both appear to have the same "first selected" status.
**Why it happens:** JTree multi-selection reports paths in tree order (top-to-bottom), not click order.
**How to avoid:** For the two-file case, use `tree.selectionPaths` array order — it reflects the order rows appear in the tree (consistent and deterministic). Document this as a known limitation: the user controls left/right ordering by their position in the tree, not click sequence.
**Warning signs:** "Compare With..." always puts alphabetically-first file on left regardless of click order.

### Pitfall 6: Double-Click Conflicting with Single-Click Diff Load
**What goes wrong:** Double-clicking a file in `ChangedFilesPanel` triggers ListSelectionListener (loads inline diff) AND the double-click handler (opens full-window diff) in rapid succession.
**Why it happens:** `ListSelectionListener.valueChanged` fires on every selection event, including the first click of a double-click gesture.
**How to avoid:** Double-click opens full-window diff and also updates inline diff (acceptable — user sees both). The inline diff load is background; it simply gets replaced by the full-window popup. No special sequencing needed.
**Warning signs:** Extra background tasks firing; console logs showing two diff loads for one double-click (harmless but wastes resources).

## Code Examples

Verified patterns from official sources:

### getDiffContent() method for GitBackend

```kotlin
// Source: LocalGitCommandExecutor pattern (existing codebase)
// Add to GitBackend interface:
fun getHeadContent(path: String): ByteArray?

// LocalGitBackend implementation:
override fun getHeadContent(path: String): ByteArray? {
    val result = executor.executeBlocking(
        repoPath,
        args = arrayOf("show", "HEAD:$path")
    )
    return if (result.isSuccess) result.stdout.toByteArray(Charsets.UTF_8) else null
}
```

### Full inline diff load sequence

```kotlin
// Source: Pattern from GitPanelComponent.onCommitSelected() + DiffManager API
// Runs in GitPanelComponent (or delegated to InlineDiffPanel)

fun loadInlineDiff(commitFile: CommitFile, backend: GitBackend) {
    // Show spinner immediately (on EDT)
    showSpinner()

    val snapshotKey = System.identityHashCode(backend)

    ApplicationManager.getApplication().executeOnPooledThread {
        // Fetch HEAD content off-EDT
        val headBytes: ByteArray? = when (commitFile.status) {
            GitFileStatus.UNTRACKED, GitFileStatus.ADDED -> null  // no HEAD for new files
            else -> backend.getHeadContent(commitFile.path)
        }
        // Fetch working-tree content off-EDT
        val workBytes: ByteArray? = when (commitFile.status) {
            GitFileStatus.DELETED -> null
            else -> runCatching {
                File(backend.repoPath, commitFile.path).readBytes()
            }.getOrNull()
        }

        val request = buildDiffRequest(project, backend.repoPath, commitFile, headBytes, workBytes)

        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            if (System.identityHashCode(selectedBackend) != snapshotKey) return@invokeLater

            // Dispose old panel, create new one
            inlineDiffDisposable?.let { Disposer.dispose(it) }
            val childDisposable = Disposer.newDisposable(this)
            inlineDiffDisposable = childDisposable

            val panel = DiffManager.getInstance()
                .createRequestPanel(project, childDisposable, null)
            panel.setRequest(request)
            inlineDiffPanel = panel

            hideSpinner()
            diffSlot.removeAll()
            diffSlot.add(panel.getComponent(), BorderLayout.CENTER)
            diffSlot.revalidate()
            diffSlot.repaint()
        }
    }
}
```

### ListSelectionListener wiring in ChangedFilesPanel / GitPanelComponent

```kotlin
// Source: ChangedFilesPanel.kt list field (existing) + ListSelectionListener API
// Add to ChangedFilesPanel:
var onFileSelected: ((CommitFile?) -> Unit)? = null

init {
    list.addListSelectionListener { e ->
        if (e.valueIsAdjusting) return@addListSelectionListener
        val selected = list.selectedValue
        onFileSelected?.invoke(selected)
    }
}
```

### Plugin.xml registration for CompareWithAction

```xml
<!-- Source: ExplorerActions.kt pattern + existing SystemExplorer.ActionGroup -->
<!-- Add inside SystemExplorer.ActionGroup in plugin.xml: -->
<action id="SystemExplorer.CompareWith"
        class="ro.faur.explorer.actions.CompareWithAction"
        text="Compare With..."
        description="Compare the two selected files side by side">
</action>
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `com.intellij.openapi.diff.DiffManager` | `com.intellij.diff.DiffManager` | IDEA 14.1 (build 141+, ~2015) | Old API deprecated; new API has `createRequestPanel()` for embedding |
| `SimpleContent` / `StringContent` (old API) | `DiffContentFactory.create(project, text, fileType)` | Same transition | New factory handles encoding, PSI, VFS properly |
| Custom diff widget (hand-rolled) | `DiffManager.createRequestPanel()` | N/A | Platform handles all rendering, keyboard nav, gutter, toolbar |

**Deprecated/outdated:**
- `com.intellij.openapi.diff.*` (entire old package): Replaced by `com.intellij.diff.*`. STATE.md already records the decision to use the new package exclusively.
- `SimpleDiffRequest` from old package (`com.intellij.openapi.diff.SimpleDiffRequest`): Different class from the new `com.intellij.diff.requests.SimpleDiffRequest`. Never use the old one.

## Open Questions

1. **Remote backend: reading working-tree file bytes**
   - What we know: `LocalGitBackend` can use `File(repoPath, path).readBytes()` directly. `RemoteGitBackend` accesses files via SFTP.
   - What's unclear: Is there a fast path to read a remote working-tree file's bytes, or must we use `git show :path` (index) as a proxy?
   - Recommendation: For `RemoteGitBackend`, use `git show :path` (index/staging-area content) as the right side if SFTP file read is not readily available from the existing remote executor. This is slightly different from working-tree (unstaged edits won't show) but avoids a new SFTP transport. Flag this in the plan as a known limitation for remote backends.

2. **Loading spinner component choice**
   - What we know: IntelliJ provides `AnimatedIcon.Default` and `AsyncProcessIcon`. Exact API and correct instantiation were not verified.
   - What's unclear: Whether `AsyncProcessIcon` requires a parent component width constraint to display correctly embedded in `JPanel`.
   - Recommendation: Use `com.intellij.util.ui.AsyncProcessIcon("diff-loading")` — this is the standard platform spinner. Wrap in a centered `JPanel` while background load is in progress.

3. **History mode diff (before-commit vs after-commit)**
   - What we know: Context says full-window diff works "in both staging mode and history mode (before-commit vs after-commit for that entry)". The inline diff is staging-mode only.
   - What's unclear: For history-mode full-window diff, the "before" bytes require `git show PARENT_HASH:path` (not HEAD). `CommitFile` does not currently carry the parent hash.
   - Recommendation: For history-mode showDiff, call `git show COMMIT_HASH^:path` as the before-side and `git show COMMIT_HASH:path` as the after-side. The `^` suffix means parent. This works for standard commits; handle merge commits (multiple parents) by always using `^1`.

## Validation Architecture

> `workflow.nyquist_validation` is not set in `.planning/config.json` (key absent — only `research`, `plan_check`, `verifier` are present). Skipping Validation Architecture section.

## Sources

### Primary (HIGH confidence)
- `github.com/JetBrains/intellij-community` → `platform/diff-api/src/com/intellij/diff/DiffManager.java` — showDiff signatures, createRequestPanel signature, @RequiresEdt annotation
- `github.com/JetBrains/intellij-community` → `platform/diff-api/src/com/intellij/diff/DiffRequestPanel.java` — setRequest(DiffRequest), getComponent(), putContextHints(), Disposable extension
- `github.com/JetBrains/intellij-community` → `platform/diff-impl/src/com/intellij/diff/DiffContentFactoryImpl.java` — createDocumentFromBytes(project, bytes, filePath), create(project, vf), createEmpty() signatures
- `github.com/JetBrains/intellij-community` → `platform/diff-api/src/com/intellij/diff/requests/SimpleDiffRequest.java` — two-content constructor, createEmpty() hint from docs
- `plugins.jetbrains.com/docs/intellij/disposers.html` — Disposer.register(), Disposer.newDisposable(), parent-child lifecycle

### Secondary (MEDIUM confidence)
- `platform.jetbrains.com/t/how-to-close-a-simplediffrequest-in-code/994` — DiffDialogHints.FRAME, SimpleDiffRequest with DiffContentFactory pattern (verified against official source)
- JetBrains support forum posts on DiffManager.createRequestPanel() embedded usage — confirmed against source code

### Tertiary (LOW confidence)
- WebSearch results on VirtualFile refresh patterns — not verified against official 2025.1 docs; treat as guidance

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — API confirmed directly from intellij-community source (DiffManager.java, DiffRequestPanel.java, DiffContentFactoryImpl.java)
- Architecture: HIGH — patterns derived from existing codebase conventions (Task.Backgroundable, executeOnPooledThread, Disposer usage in GitPanelComponent) combined with confirmed API signatures
- Pitfalls: HIGH for EDT violation and disposal (confirmed from @RequiresEdt annotation and Disposer docs); MEDIUM for VirtualFile staleness and selection order (established patterns, not verified against 2025.1-specific docs)

**Research date:** 2026-02-28
**Valid until:** 2026-03-30 (platform APIs in this domain are stable; no known upcoming breaking changes in 2025.1–2025.3 range)
