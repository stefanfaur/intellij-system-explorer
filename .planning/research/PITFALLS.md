# Pitfalls Research

**Domain:** IntelliJ Platform Plugin — File Explorer + Git Panel + Diff Viewer
**Researched:** 2026-02-28
**Confidence:** HIGH (DnD: HIGH from codebase + official docs; Threading: HIGH from official docs + real incidents; Diff API: MEDIUM from official docs + community reports; Git ops: MEDIUM from code + patterns)

---

## Critical Pitfalls

### Pitfall 1: Mixing IntelliJ DnDManager and Swing TransferHandler on the Same Component

**What goes wrong:**
Both IntelliJ's `DnDManager` and Swing's `TransferHandler`/`dragEnabled=true` listen for mouse-drag gestures on the same component. They fight over mouse events, producing ghost drags, missed drops, and intermittent failures that are nearly impossible to reproduce reliably.

**Why it happens:**
Developers see IntelliJ's `DnDManager` in sample code and also know Swing has a `TransferHandler`, then use both for "completeness." The two systems are architecturally separate: `DnDManagerImpl` uses AWT native DnD under the hood, while `TransferHandler` uses Swing's gesture recognizer. Both consume mouse-pressed/dragged events.

**How to avoid:**
Follow the split pattern already established in this codebase:
- Drag OUT from the explorer tree: Swing `TransferHandler` + `tree.dragEnabled = true` only.
- Receive drops FROM IntelliJ panels (Project View, etc.): implement `DnDNativeTarget` (not just `DnDTarget`) registered with `DnDManager` only.
- Never register both a `DnDSource` and `dragEnabled=true` on the same tree.

**Warning signs:**
- Drag from explorer sometimes starts, sometimes doesn't.
- Drops from IntelliJ Project View land in wrong target or silently do nothing.
- Mouse cursor shows wrong DnD feedback (copy vs. move mismatch).

**Phase to address:** DnD polish phase — enforce the split in code review before merging any DnD change.

---

### Pitfall 2: TransferableWrapper Is Not a Transferable

**What goes wrong:**
IntelliJ's Project View sends a `TransferableWrapper` as the `attachedObject` in a `DnDEvent`. Code that calls `attachedObject is Transferable` returns `false` even though there are files present. The drop silently produces zero files.

**Why it happens:**
`TransferableWrapper` extends `FileFlavorProvider`, not `Transferable`. Developers assume that because the object carries file data it must implement `Transferable`. The IntelliJ DnD system bypasses standard Java data flavors for internal panel drags.

**How to avoid:**
Always check `TransferableWrapper` first in the extraction chain, then `FileFlavorProvider`, then `Transferable` as a fallback. Access data via `wrapper.psiElements` and `wrapper.asFileList()` directly. The current `DragDropHandler.extractFiles()` already does this correctly — do not simplify it.

**Warning signs:**
- Drops from IntelliJ's Project View produce no files extracted.
- `extractFiles` logging shows "attachedObject type = com.intellij.ide.dnd.TransferableWrapper" followed by "could not extract any files."

**Phase to address:** DnD polish phase — covered by existing implementation; new contributors must not "simplify" the extraction chain.

---

### Pitfall 3: Calling git CLI (or Any Blocking I/O) on the EDT

**What goes wrong:**
`LocalGitCommandExecutor.executeBlocking()` calls `ProcessBuilder`, waits on the process, and reads stdout. If this is invoked from a UI event handler (button click, tree selection listener, etc.) running on the EDT, the entire IDE freezes until git finishes. IntelliJ 2024.1+ reports "Slow operations are prohibited on EDT" and can log a freeze report blaming the plugin.

**Why it happens:**
The executor API name `executeBlocking` implies it blocks — it is easy to call it from a convenient UI callback without thinking about thread context. The 30-second default timeout means a stuck git process (auth prompt, lock file, large repo) will freeze the IDE for 30 seconds.

**How to avoid:**
Wrap every call to `executeBlocking` in a background task:
- For short operations (status, current branch): `ReadAction.nonBlocking { ... }.submit(AppExecutorUtil.getAppExecutorService())` with a UI callback via `invokeLater`.
- For long operations (push, pull, log): `ProgressManager.getInstance().run(object : Task.Backgroundable(...) { ... })` so the user sees progress and can cancel.
- For new Git ops (stash, branch create): use Kotlin coroutines with `Dispatchers.IO` if targeting 2024.1+.

Never add new callsites of `executeBlocking` from EDT. Audit existing callsites in `GitPanelComponent` and similar UI classes.

**Warning signs:**
- IDE freezes for 1-30 seconds after a Git panel button click.
- Thread dump shows `AWT-EventQueue-0` blocked in `process.waitFor(...)`.
- IntelliJ logs "Plugin 'System Explorer' might be slowing things down."

**Phase to address:** Git ops phase (branch, stash, pull, log) — every new operation must be dispatched to background. Existing stage/commit/push should be audited too.

---

### Pitfall 4: Embedded DiffViewer Panel Not Disposed

**What goes wrong:**
`DiffManager.getInstance().createRequestPanel(project, disposable, ...)` creates a diff viewer that owns editor instances and document listeners. If the `disposable` passed is the project or application rather than the containing tool window or panel, the diff viewer lives for the entire IDE session. Each time the diff panel is rebuilt (new commit selected, new file selected), the old viewer leaks.

**Why it happens:**
`project` is always available and convenient to use as a parent disposable. Developers pass it without checking whether it represents the correct lifetime. The diff panel may appear to work correctly while silently accumulating leaked editors.

**How to avoid:**
- Pass the tool window's `toolWindow.disposable` as the parent when constructing `createRequestPanel`.
- For embedded panels inside `GitPanelComponent`: use `Disposer.newDisposable(parentDisposable)` scoped to the panel's lifetime.
- When switching diffs (user selects a different file/commit): call `Disposer.dispose(oldDiffDisposable)` before creating the new one.
- Never use `project` or `application` as the direct parent for a diff panel.

**Warning signs:**
- Memory grows monotonically as users click through commits in the log.
- IntelliJ's memory profiler shows many `EditorImpl` instances referencing a single project.
- `Disposer.isDisposed(panel)` returns false for panels that should be gone.

**Phase to address:** Diff viewer phase — enforce via code review. Verify with a simple test: open Git log, click 20 commits, check memory delta.

---

### Pitfall 5: Threading Model Changes Breaking Old Invokation Patterns (2025.1+)

**What goes wrong:**
In IntelliJ Platform 2025.1, `SwingUtilities.invokeLater` and `SwingUtilities.invokeAndWait` no longer hold the Write-Intent lock. Code that relied on this implicit lock — performing VFS reads or PSI access inside `invokeLater` without an explicit `ReadAction.compute()` — will throw `IllegalStateException: Read access is allowed from inside read-action only`.

In 2025.3, Write-Intent lock acquisition was also removed from AWT input event processing, and several lock-acquisition methods (`acquireReadActionLock`, `acquireWriteActionLock`, `WriteAction.start()`) were removed entirely.

**Why it happens:**
Historically, `invokeLater` on EDT implicitly held the Write-Intent lock as a side effect. Plugin code written before 2024 could do VFS reads inside `invokeLater` without explicit `ReadAction`. The platform is progressively removing this implicit lock for performance reasons.

**How to avoid:**
- Replace `SwingUtilities.invokeLater { vfsRead() }` with `ApplicationManager.getApplication().invokeLater { ReadAction.compute { vfsRead() } }`.
- Use `ApplicationManager.getApplication().invokeLater` (not `SwingUtilities`) to ensure correct ModalityState handling.
- Wrap all VFS/PSI access in explicit `ReadAction.compute()` regardless of which thread you're on.
- Target build range: if supporting 2023.x+, test on a 2025.x nightly before each release.

**Warning signs:**
- `IllegalStateException: Read access is allowed from inside read-action only` in plugin logs.
- Works fine on 2023.x but crashes on 2025.x.
- Occurs after a UI action that triggers a tree refresh or file list update.

**Phase to address:** Every phase — but file tree refresh and git status polling are the highest-risk callsites. Add explicit read-action wrappers as new code is written.

---

### Pitfall 6: Using the Old Diff API (`com.intellij.openapi.diff.*`) Instead of the New One

**What goes wrong:**
IntelliJ has two diff API namespaces:
- Old (deprecated): `com.intellij.openapi.diff.*` — `DiffContent`, `DiffManager.getDiffTool()`, etc.
- New (current): `com.intellij.diff.*` — `SimpleDiffRequest`, `DiffContentFactory`, `DiffManager.getInstance().showDiff()`

Mixing the two causes compile errors or ClassCastExceptions at runtime. The old API produces warnings and may be removed in a future platform version.

**Why it happens:**
Search results, Stack Overflow answers, and old blog posts frequently use the old API. When copying example code, it is easy to import from the wrong package.

**How to avoid:**
Use exclusively `com.intellij.diff.*`:
```kotlin
val left = DiffContentFactory.getInstance().create(project, leftText)
val right = DiffContentFactory.getInstance().create(project, rightText)
val request = SimpleDiffRequest("Title", left, right, "Before", "After")
DiffManager.getInstance().showDiff(project, request)
```
For embedded panels: `DiffManager.getInstance().createRequestPanel(project, disposable, request)`.
Set `idea.log.slow.operations.in.edt=true` in `idea.properties` to catch any EDT violations during development.

**Warning signs:**
- Import statements containing `com.intellij.openapi.diff` in diff-related files.
- ClassCastException at runtime when showing a diff.
- Deprecation warnings on `DiffContent` or `DiffManager.getDiffTool()`.

**Phase to address:** Diff viewer phase — establish the correct import at the start, add a checkstyle/lint rule if possible.

---

### Pitfall 7: DnD Drop Visual Feedback Not Implemented

**What goes wrong:**
The tree shows no visual indicator of the drop target while a file is being dragged over it. Users cannot tell whether they are dropping onto a directory or in empty space. Drop operations complete, but users distrust them because the feedback is absent.

**Why it happens:**
Swing's `JTree.DropLocation` painting is not automatic when using a custom `TransferHandler`. `JTree.setDropMode(DropMode.ON_OR_INSERT)` must be set, and the tree renderer must check `tree.dropLocation` to highlight the target row. This step is easy to overlook when focusing on getting the file copy to work.

**How to avoid:**
- Call `tree.dropMode = DropMode.ON_OR_INSERT` when setting up the tree.
- In `ColoredTreeCellRenderer.customizeCellRenderer()`, check if `tree.dropLocation?.path == currentPath` and apply a visual highlight if so.
- For `DnDNativeTarget`-registered drops: implement `update()` to call `event.setDropPossible(true)` with a descriptive hint string, which IntelliJ uses to paint the drop indicator.

**Warning signs:**
- No row highlights during drag-over in the file tree.
- `update()` in `DragDropHandler` returns true but no cursor change is visible.

**Phase to address:** DnD polish phase — treat visual feedback as a required deliverable, not a nice-to-have.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Call `executeBlocking` from a listener on EDT | Simple code, works for fast repos | IDE freeze on large repos or network git | Never for push/pull; only for sub-10ms local ops like reading current branch if cached |
| Use `project` as parent disposable for diff panels | No need to track disposable lifetime | Memory leak per commit click | Never |
| Skip `update()` in `DnDNativeTarget` (always return true) | No need to compute drop target on hover | Misleading cursor when hovering over non-directories | Only acceptable in a prototype |
| Parse git output with simple string splits | No dependency on git4idea | Breaks on filenames with tabs, quotes, or unusual characters | Only for paths known to be ASCII-safe |
| Block all git ops with a single mutex | Simple synchronization | Starvation when push blocks status poll | Only in MVP; replace with per-repo locking before marketplace release |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| IntelliJ Project View DnD | Treat `TransferableWrapper` as a `Transferable` | Check `is TransferableWrapper` first; use `.psiElements` and `.asFileList()` directly |
| VFS refresh after file copy/move | Call `LocalFileSystem.getInstance().refresh(false)` on EDT | Call `refresh(true)` (async) or wrap in `WriteAction` if synchronous refresh is needed |
| Git push over SSH | Assume push completes in < 30s default timeout | Use a longer timeout or async task; push can take minutes on slow networks |
| `DiffContentFactory.create(project, file)` | Pass a file that hasn't been refreshed in VFS | Refresh the VirtualFile before creating diff content; stale VF content = stale diff |
| `ActionUpdateThread` for toolbar actions | Default to `OLD_EDT` (deprecated in 2024.1+) | Override `getActionUpdateThread()` to return `BGT` and do PSI/VFS access in `update()` with `ReadAction` |
| git4idea plugin dependency | Add `git4idea.jar` to project classpath manually | Declare `<depends>Git4Idea</depends>` in `plugin.xml`; do not bundle the jar |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| VFS `findFileByPath()` called in tree cell renderer for every row | Tree scroll is janky; CPU spike on expand | Cache `VirtualFile` in tree node `userObject` at load time, not at render time | Directories with 500+ files |
| `git status --porcelain` polled on a timer on EDT | IDE stutter every N seconds | Run status poll on background coroutine; update UI via `invokeLater` | Repos with thousands of tracked files |
| Loading full git log (unlimited) into `CommitLogTableModel` | Log panel hangs on repos with 10k+ commits | Always pass `-n maxCount` to `git log`; implement lazy/paginated loading | Repos with > 1000 commits |
| Blocking SFTP operations called from Swing listeners | Remote panel freezes on slow connections | Move all SFTP I/O to background thread with `Task.Backgroundable` | Any connection with > 100ms RTT |
| Creating a new `DiffViewer` for every keystroke in commit message | Diff panel flickers; layout thrashes | Debounce the diff update trigger (300ms minimum); reuse viewer, only update content | Any typing speed |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Storing SSH passwords in plain text in settings state | Credentials exposed in `workspace.xml` checked into source control | Use `PasswordSafe.getInstance()` for credential storage; never serialize credentials in `PersistentStateComponent` |
| Not validating file paths from DnD before copy | Directory traversal if a crafted drag source provides `../../sensitive/path` | Validate that the resolved `VirtualFile` is a real local file before operating on it; already safe if using VFS |
| Running `git commit -m` with unsanitized user input as a shell argument | Command injection if message contains shell metacharacters | Pass commit message as a separate argument to `ProcessBuilder`, never via shell string interpolation — the current `LocalGitCommandExecutor` does this correctly |
| Logging full git output at DEBUG level in production | Sensitive file contents, tokens in commit messages appear in IDE logs | Log only exit codes and error summaries; gate verbose output behind `LOG.isDebugEnabled` |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No progress indicator for push/pull | User clicks push, nothing visible for 10s, clicks again, double-push | Use `Task.Backgroundable` with cancellable progress; show "Pushing..." in status bar |
| Commit panel resets (clears message, unstages files) after a failed push | User loses their commit message context | Separate commit from push; only reset on successful commit |
| Git log table has no loading state | Empty table flashes briefly on panel open | Show a "Loading..." placeholder row while the background task runs |
| Diff viewer opens in a separate window instead of embedded panel | Breaks spatial context; user loses place in commit log | Use `createRequestPanel()` for embedded diff; `showDiff()` only for explicit "Open in Window" action |
| File tree does not refresh after a DnD move | File appears in both source and target, or disappears from source only | Call `VirtualFileManager.getInstance().asyncRefresh()` covering both source and destination after a move |

---

## "Looks Done But Isn't" Checklist

- [ ] **DnD to IntelliJ Project View**: Drop from the explorer INTO the Project View — not just FROM the Project View into the explorer. Verify the Project View tree updates after the drop.
- [ ] **Diff for deleted files**: Ensure diff viewer handles `DELETED` status — left side shows last committed content, right side shows empty. A common mistake is crashing when trying to `DiffContentFactory.create()` a null/missing file.
- [ ] **Git stash with untracked files**: `git stash` by default does not stash untracked files. If the UI implies "stash everything," pass `--include-untracked` or warn the user. Test with untracked files present.
- [ ] **Branch switch with uncommitted changes**: Switching branches when the working tree is dirty requires either stashing or a hard reset. The UI must handle the dirty-tree case explicitly, not assume the branch switch will succeed.
- [ ] **DnD cursor feedback on macOS vs. Windows**: The Swing DnD cursor (copy vs. move) behaves differently on macOS. On macOS, `Option` key switches copy/move; on Windows, it is `Ctrl`. Test on both platforms.
- [ ] **Embedded diff viewer theme consistency**: The diff viewer uses its own color scheme. Verify it respects the IDE's dark/light theme switch without requiring a restart.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| DnD system conflict (both Swing + IntelliJ DnD registered) | MEDIUM | Remove one system; refactor to split pattern; retest all DnD paths |
| Leaked diff viewers | MEDIUM | Identify root parent disposable; add `Disposer.dispose()` at panel teardown; verify with heap profiler |
| EDT blocking from git CLI calls | HIGH (requires async refactor) | Wrap all `executeBlocking` callsites in `Task.Backgroundable`; add coroutine dispatcher layer |
| Wrong diff API namespace | LOW | Find-and-replace old imports; update to `com.intellij.diff.*`; recompile |
| Git stash/branch leaving dirty state on error | MEDIUM | Add explicit error handling that notifies user; do not silently ignore git stderr |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| DnD system conflict (Swing + DnDManager) | DnD polish phase | Manual test: drag from explorer, drag to explorer, drag from Project View; all three must work |
| TransferableWrapper not a Transferable | DnD polish phase | Integration test: drop from IntelliJ Project View, assert files received |
| Git CLI blocking EDT | Git ops phase (branch/stash/pull/log) | Run IntelliJ freeze detector; check thread dump after each git button click |
| Embedded diff panel not disposed | Diff viewer phase | Heap snapshot before/after 20 commit-selections; editor count must not grow |
| Old diff API import | Diff viewer phase | Code review checklist; build with `-Werror` on deprecations |
| DnD drop visual feedback absent | DnD polish phase | UX review: drag a file over a directory, assert row highlights |
| Threading model changes (2025.1+) | All phases | Test each release against latest IntelliJ EAP before publishing |
| Credential storage plain text | Settings/security phase | Grep codebase for `password` fields in `@State` data classes |

---

## Sources

- [IntelliJ Platform Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html) — HIGH confidence
- [Incompatible API Changes 2025.*](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2025.html) — HIGH confidence
- [Incompatible API Changes 2024.*](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2024.html) — HIGH confidence
- [Disposer and Disposable](https://plugins.jetbrains.com/docs/intellij/disposers.html) — HIGH confidence
- [Background Processes](https://plugins.jetbrains.com/docs/intellij/background-processes.html) — HIGH confidence
- [Changes in threading model 2025.1](https://platform.jetbrains.com/t/changes-in-threading-model-in-intellij-platform-2025-1/721) — HIGH confidence
- [Changes in threading model 2025.3](https://platform.jetbrains.com/t/changes-in-threading-model-in-the-intellij-platform-2025-3/2934) — HIGH confidence
- [Investigating IntelliJ Platform UI Freezes (2025)](https://blog.jetbrains.com/platform/2025/09/investigating-intellij-platform-ui-freezes/) — HIGH confidence
- [git4idea dependency setup — JetBrains support](https://intellij-support.jetbrains.com/hc/en-us/community/posts/12131507393810-How-to-add-Git4Idea-dependency) — MEDIUM confidence
- [DiffManager usage examples — JetBrains support](https://intellij-support.jetbrains.com/hc/en-us/community/posts/115000508990-how-to-invoke-the-built-in-diff-action) — MEDIUM confidence
- [DnD implementation guide (Pieces blog)](https://dev.to/getpieces/how-to-develop-an-intellij-plugin-a-diy-guide-to-adding-drag-and-drop-with-custom-dataflavors-52o8) — MEDIUM confidence
- Codebase analysis: `DragDropHandler.kt`, `FileTreeTransferHandler.kt`, `LocalGitCommandExecutor.kt`, `LocalGitBackend.kt`, `RemoteGitDiffProvider.kt` — HIGH confidence (first-hand)
- Project MEMORY.md — IntelliJ DnD architecture notes from prior development — HIGH confidence

---
*Pitfalls research for: IntelliJ Platform Plugin (File Explorer + Git Panel + Diff Viewer)*
*Researched: 2026-02-28*
