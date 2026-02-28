# Stack Research

**Domain:** IntelliJ Plugin — Diff Viewer, Deeper Git Ops, DnD Polish, File Tree UI
**Researched:** 2026-02-28
**Confidence:** MEDIUM (core Diff/DnD APIs verified via source; Git4Idea integration patterns from community; some 2025.x specifics LOW)

---

## Current Stack Baseline

The plugin already uses these — confirmed from `build.gradle.kts` and source:

| Technology | Version | Role |
|------------|---------|------|
| Kotlin | 2.1.0 | Primary language |
| IntelliJ Platform Gradle Plugin | 2.x | Build toolchain |
| platformVersion | 2025.1 (sinceBuild 251) | Target platform |
| Apache MINA SSHD | 2.17.1 | SSH/SFTP remote browser |
| JUnit 5 + JUnit 4 bridge | 5.11.4 / 4.13.2 | Tests |
| Mockito-Kotlin | 5.4.0 | Test mocking |
| Remote Robot | 0.11.23 | UI tests |
| Nucleo (Rust JNI) | custom | Fuzzy ranking |

---

## Recommended Stack Additions

### Diff Viewer

| Technology | Package / Since | Purpose | Why Recommended |
|------------|-----------------|---------|-----------------|
| `DiffManager` | `com.intellij.diff.DiffManager` / IDEA 14.1+ | Orchestrates diff display | Singleton service; the single entry point for all diff operations. Use `createRequestPanel()` to embed, `showDiff()` for dialog. |
| `DiffContentFactory` | `com.intellij.diff.DiffContentFactory` / IDEA 14.1+ | Builds diff content objects | Creates `DocumentContent` from `String`, `VirtualFile`, or `byte[]`. Use `createFromBytes()` for git blob content (e.g., HEAD version). |
| `SimpleDiffRequest` | `com.intellij.diff.requests.SimpleDiffRequest` | Wraps two/three content sides | Simplest diff request — two content sides + titles. No need to extend `DiffRequest` for standard 2-way diffs. |
| `DiffRequestPanel` | `com.intellij.diff.DiffRequestPanel` | Embeddable JComponent for diffs | Returned by `DiffManager.createRequestPanel()`; add `component` to any Swing container to embed diff inside the Git panel tool window. |
| `DiffDialogHints` | `com.intellij.diff.DiffDialogHints` | Controls dialog presentation | Use `DiffDialogHints.FRAME` for non-modal side-by-side window; `DiffDialogHints.DEFAULT` for modal dialog. |

**How to show a pre-commit diff (working tree vs HEAD):**

```kotlin
// On background thread: fetch HEAD blob bytes via `git show HEAD:path/to/file`
// then on EDT:
val factory = DiffContentFactory.getInstance()
val headContent = factory.createFromBytes(project, headBytes, filePath)  // FilePath from VFS
val diskContent = factory.create(project, virtualFile)
val request = SimpleDiffRequest("Diff: ${virtualFile.name}", headContent, diskContent, "HEAD", "Working Tree")
DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)
```

**How to embed diff in the Git panel tool window:**

```kotlin
val panel: DiffRequestPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)
panel.setRequest(request)
toolWindowContent.add(panel.component, BorderLayout.CENTER)
```

**Confidence:** HIGH for `DiffManager`/`DiffContentFactory`/`SimpleDiffRequest` (verified from intellij-community source). MEDIUM for embedded `DiffRequestPanel` pattern (community discussions, no 2025-specific breakage found).

---

### Deeper Git Operations

The plugin currently runs raw git CLI via `LocalGitCommandExecutor`. Two strategies exist:

**Strategy A — Continue with raw CLI (recommended for this plugin)**

| Operation | Git command | Notes |
|-----------|-------------|-------|
| List branches | `git branch -a --format=%(refname:short)` | Works locally and remote |
| Switch branch | `git checkout <branch>` or `git switch <branch>` | `switch` preferred (git 2.23+) |
| Create branch | `git checkout -b <name>` | Simple |
| Stash list | `git stash list --format=%gd\|%s` | Parseable |
| Stash apply | `git stash apply stash@{N}` | |
| Stash drop | `git stash drop stash@{N}` | |
| Pull | `git pull --ff-only` | Safer than bare pull; fail visibly on diverged |
| Fetch | `git fetch --prune` | Background refresh |
| Full log (graph) | `git log --format=%H\|%an\|%at\|%s --graph -n 100` | Graph rendering is custom |

**Why continue with CLI:** The plugin already has `LocalGitCommandExecutor` + `RemoteGitBackend` that execute git over SSH. Introducing `git4idea` bundled plugin dependency locks the plugin to IDEA only (not PyCharm, GoLand, etc. if desired) and adds significant API surface churn risk.

**Strategy B — git4idea bundled plugin (NOT recommended for this plugin)**

| Concern | Detail |
|---------|--------|
| Plugin ID | `Git4Idea` (capital G, capital I) |
| Gradle | `bundledPlugins("Git4Idea")` in `intellijPlatform {}` block |
| plugin.xml | `<depends>Git4Idea</depends>` |
| Key API | `GitRepositoryManager.getInstance(project).repositories` → `GitRepository` → `GitBranch`, `GitBranchesCollection` |
| Why to avoid | Tight coupling to JetBrains' Git plugin internals, frequent deprecation churn (confirmed by community reports), makes remote/SSH git ops impossible through the API, restricts IDE compatibility |

**Confidence:** HIGH for CLI approach (already in use, battle-tested). LOW for git4idea API surface stability (community reports of breakage on updates).

---

### DnD Polish

The existing DnD architecture (documented in MEMORY.md and implemented in `FileTreeComponent`) is already correct:

- **Drag-out:** Swing `TransferHandler` + `tree.dragEnabled = true` — correct pattern
- **Drop-in from IntelliJ panels:** `DnDManager.registerTarget(handler, tree)` with `DnDTarget` (or `DnDNativeTarget`)
- **Drop-in from external apps:** `DnDNativeTarget` (extends `DnDTarget`) to also receive AWT native drops

The remaining work is polish, not architecture change:

| Issue | Fix | API |
|-------|-----|-----|
| Drop feedback (highlight target row) | Override `DnDTarget.update()`, call `DnDEvent.setDropPossible(true)` and set highlighting | `com.intellij.ide.dnd.DnDEvent` |
| Autoscroll during drag | Swing `JTree.setAutoscrolls(true)` | Already in Swing, no IntelliJ API needed |
| Multi-file drag | `FileTreeTransferHandler.createTransferable()` — return `DataFlavor.javaFileListFlavor` with all selected files | Already partially done |
| Drop onto directory vs file | `DnDEvent.point` → `tree.getPathForLocation()` → check if node is directory | Standard pattern |

**Key constraint:** Do NOT register the same component as both `DnDSource` (via `DnDManager.registerSource`) AND use `tree.dragEnabled = true`. These two systems conflict. Current setup is correct — Swing handles drag-out, IntelliJ's `DnDManager` handles drop-in only.

**Confidence:** HIGH (verified from project MEMORY.md and existing working code in `FileTreeComponent.kt`).

---

### File Tree UI Polish

| Component | Current | Recommended | Rationale |
|-----------|---------|-------------|-----------|
| Tree speed search | `TreeSpeedSearch(tree)` (deprecated constructor) | `TreeUIHelper.getInstance().installTreeSpeedSearch(tree, converter)` | `TreeUIHelper` is in the platform-api module; direct `TreeSpeedSearch` instantiation was soft-deprecated |
| Tree renderer | `ColoredTreeCellRenderer` | Keep — correct choice | `ColoredTreeCellRenderer` is IntelliJ's standard, integrates with selection/focus painting |
| Context menu | `JPopupMenu` | Keep for now, consider `ActionManager` + `DefaultActionGroup` later | `ActionManager`-based menus integrate with IntelliJ's keymap, shortcuts, and "Find Action" — but requires action registration in plugin.xml |
| Icons | `AllIcons.*` + `vf.fileType.icon` | Keep — correct | Standard approach; icon cache already implemented |
| VFS refresh | `vf.refresh(async=false, recursive=false)` | Keep shallow refresh approach | Recursive refresh on large trees causes performance problems |

**Confidence:** MEDIUM (`TreeUIHelper` pattern from official SDK docs list-and-trees page; other items from existing codebase review).

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `com.intellij.openapi.diff.*` (old diff API) | Deprecated since IDEA 14.1; classes like `DiffManager` in the old package are stubs that delegate or removed | `com.intellij.diff.*` (new API in `platform/diff-api`) |
| `DnDManager.registerSource()` on the file tree | Conflicts with Swing's `DragGestureRecognizer` when `tree.dragEnabled = true` is also set — both compete for mouse-drag events | Swing `TransferHandler` + `dragEnabled=true` for drag-out only |
| `TransferableWrapper` via `Transferable` flavor iteration | `TransferableWrapper` does NOT implement `Transferable` directly — it implements `FileFlavorProvider`; calling `getTransferData()` on it fails for Project View drops | Use `wrapper.psiElements` and `wrapper.asFileList()` directly |
| `git4idea` bundled API for remote repos | API is unavailable for SSH-managed repos; git4idea's `GitRepository` only tracks VFS-visible local repos | Continue with `GitCommandExecutor` + SSH-forwarded commands |
| `SimpleDiffRequest` in the deprecated `com.intellij.openapi.diff` package | This is the old API; the new one is `com.intellij.diff.requests.SimpleDiffRequest` | `com.intellij.diff.requests.SimpleDiffRequest` |
| Calling `VirtualFile.children` on every cell render | Triggers VFS access on EDT for every visible cell; causes jank on large trees | Pre-populate child counts in background and cache |

---

## 2025.x Compatibility Notes

| Change | Version | Impact |
|--------|---------|--------|
| `com.intellij.diff.util.ThreeSide.map(Function)` parameter type changed from `com.intellij.util.Function` to `kotlin.jvm.functions.Function1` | 2025.2 | Low — only affects 3-way merge; plugin uses 2-way diffs |
| VCS modules `intellij.platform.vcs.dvcs` and `intellij.platform.vcs.log` extracted; need explicit `bundledModule()` | 2025.3 | Low — plugin does not depend on these modules |
| `SwingUtilities.invokeLater` no longer holds write-intent lock | 2025.1 | Medium — any EDT code that writes to the PSI or VFS must use explicit `WriteAction.run {}` |
| Kotlin 2.x required for plugins targeting 2025.1+ | 2025.1 | Done — plugin already uses Kotlin 2.1.0 |
| `platformBundledPlugins = org.jetbrains.plugins.terminal` already declared | existing | No change needed for diff/git additions |

**Confidence for 2025.x notes:** MEDIUM (from official API changes page).

---

## Version Compatibility

| Library | Compatible with platformVersion | Notes |
|---------|---------------------------------|-------|
| `com.intellij.diff.*` | 2023.x+ (sinceBuild 231) | Stable; no breaking changes in 2025.1-2025.3 affecting 2-way diff |
| `com.intellij.ide.dnd.DnDManager` | 2023.x+ | Stable API; `DnDNativeTarget` unchanged |
| Apache MINA SSHD 2.17.1 | All | No IntelliJ dependency |
| Kotlin 2.1.0 | Required for 2025.1+, compatible with 2024.x via toolchain config | Already correct |

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| CLI git for branch/stash/pull | git4idea bundled plugin API | Only if the plugin targets IDEA-only and never needs SSH git — and only if willing to track internal API churn |
| `DiffManager.createRequestPanel()` for embedded diff | Custom diff renderer (JTextPane side-by-side) | Never — implementing a quality diff renderer from scratch is months of work; IntelliJ's is already excellent |
| `DiffManager.showDiff()` with `DiffDialogHints.FRAME` for full-screen diff | Embedding inside Git panel | Use showDiff() for "show full diff" action; embedded panel for preview-on-select |
| Swing `TransferHandler` for drag-out | IntelliJ `DnDSource` | If the plugin ever needs to drag to IntelliJ-internal panels only (not external apps). For cross-app drag, Swing is required. |

---

## Sources

- [DiffManager source — JetBrains/intellij-community](https://github.com/JetBrains/intellij-community/blob/master/platform/diff-api/src/com/intellij/diff/DiffManager.java) — HIGH confidence; verified createRequestPanel() and showDiff() signatures
- [DiffContentFactoryImpl source — JetBrains/intellij-community](https://github.com/JetBrains/intellij-community/blob/master/platform/diff-impl/src/com/intellij/diff/DiffContentFactoryImpl.java) — HIGH confidence; verified createFromBytes(), create(project, VirtualFile), create(project, String)
- [IntelliJ Platform API Changes 2025](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2025.html) — MEDIUM confidence; ThreeSide.map type change, VCS module extraction in 2025.3
- [IntelliJ Platform Gradle Plugin — Dependencies Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html) — HIGH confidence; bundledPlugin() usage confirmed
- [GitRepositoryManager source — JetBrains/intellij-community](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/src/git4idea/repo/GitRepositoryManager.java) — MEDIUM confidence; API shape verified, stability not guaranteed
- [IntelliJ Platform — List and Tree Controls docs](https://plugins.jetbrains.com/docs/intellij/lists-and-trees.html) — MEDIUM confidence; TreeUIHelper.installTreeSpeedSearch pattern
- [DnD article — Pieces.app blog](https://dev.to/getpieces/how-to-develop-an-intellij-plugin-a-diy-guide-to-adding-drag-and-drop-with-custom-dataflavors-52o8) — LOW confidence (community blog); supports existing architecture choice
- [Git4Idea bundledPlugin issue #483 — JetBrains/intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template/issues/483) — LOW confidence; confirms Git4Idea plugin ID and dependency approach
- [JetBrains Platform — DiffRequestPanel embedding discussion](https://intellij-support.jetbrains.com/hc/en-us/community/posts/360000112364-Trying-to-reuse-the-diff-view-at-a-different-place) — LOW confidence (community support); supports embedded panel approach
- Project `MEMORY.md` — HIGH confidence; DnD architecture already validated through implementation

---

*Stack research for: IntelliJ Plugin — System Explorer diff viewer and deeper Git ops*
*Researched: 2026-02-28*
