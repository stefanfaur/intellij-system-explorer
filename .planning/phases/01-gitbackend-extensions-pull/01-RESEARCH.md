# Phase 1: GitBackend Extensions + Pull - Research

**Researched:** 2026-02-28
**Domain:** IntelliJ Plugin — GitBackend interface extension, AnAction toolbar wiring, background task execution
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Pull progress**
- Use IntelliJ background task (IDE status bar progress indicator) — consistent with how IntelliJ's own Git pull works
- Pull button is disabled while the operation is running to prevent double-pulls
- Git panel data (log + changed files) auto-reloads after pull completes — same as after commit
- Pull works for both LOCAL and REMOTE backends (RemoteGitBackend uses existing SSH executor pattern)

**Pull result display**
- Success: silent + auto-reload — no notification; the updated commit log speaks for itself
- Error: error balloon notification with last 300 chars of stderr — consistent with existing push error handling
- Error balloon title: "Pull Failed"

**Conflict surfacing**
- Detect conflicts by parsing git output for the `CONFLICT` keyword (git always prints "CONFLICT (content): Merge conflict in path/to/file")
- On conflict: show a warning balloon ("Pull resulted in conflicts — resolve the marked files") + auto-reload Changed Files panel so conflict files appear with their UU status
- No in-panel conflict resolution actions for Phase 1 — surface only, let user resolve in IDE/terminal

**Toolbar placement**
- New order: Refresh | Pull | Push | Add Local Repo | Remove Repo
- Pull and Push are adjacent (sync operations grouped); pull-before-push matches git workflow
- Pull button enabled only when a backend is selected (same condition as Push)
- No confirmation dialog for remote Pull (unlike remote Push) — pull doesn't modify the remote

### Claude's Discretion
- `pull()` return type and `GitCommandResult` usage (existing pattern)
- Exact signatures for `listBranches`, `checkoutBranch`, `createBranch`, `deleteBranch`, `stash`, `stashList`, `stashPop` — use whatever data structures make sense for downstream phases
- IntelliJ `ProgressManager` / `Task.Backgroundable` implementation details for background task
- Unit test structure for new interface methods

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| GIT-01 | User can trigger a "Pull" action from the Git panel toolbar that pulls the current branch from remote | AnAction in DefaultActionGroup, icon AllIcons.Actions.CheckOut or AllIcons.Vcs.Fetch; enable guard identical to Push |
| GIT-02 | Pull operation runs in the background and shows progress; does not freeze the IDE | Task.Backgroundable + ProgressManager.getInstance().run() for status bar indicator; disable action during run via presentation flag |
| GIT-03 | Pull result (success, conflicts, error) is surfaced to the user in the Git panel status area | Conflict detection via stdout.contains("CONFLICT"); warning balloon for conflicts; error balloon for non-zero exit; silent reload on clean success |
</phase_requirements>

---

## Summary

Phase 1 has two distinct deliverables: (1) extend the `GitBackend` interface with all new method signatures needed by downstream phases (branch ops and stash ops), and (2) implement and wire a functioning Pull toolbar action. The interface extension is additive and low-risk — both `LocalGitBackend` and `RemoteGitBackend` use the same `executeBlocking(repoPath, args)` pattern for all existing methods; new methods follow the identical pattern.

The Pull implementation follows the established `doPush()` pattern exactly: background execution via `ApplicationManager.getApplication().executeOnPooledThread`, result inspection on EDT via `invokeLater`, error balloon via the existing `"SystemExplorer"` notification group. The one difference from Push is that Pull requires conflict detection (stdout scan for `"CONFLICT"`) and a warning balloon path in addition to the error path. The user decision to use `Task.Backgroundable` (for the status bar progress indicator) is a small addition on top of the existing pattern.

The project targets IntelliJ platform 2025.1–2025.3 (IC, Kotlin 2.1.0, Java 21). The `GitCommandResult` sealed data class (`exitCode`, `stdout`, `stderr`) is the uniform return type for all git operations. No new dependencies are required for this phase — everything needed is already in the IntelliJ Platform SDK bundled with the project.

**Primary recommendation:** Add all new interface methods to `GitBackend.kt` first (stubs returning empty/error results), implement them in both backends using `executor.executeBlocking(repoPath, args)`, then wire the Pull `AnAction` following the `doPush()` template exactly with the conflict-detection branch added.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| IntelliJ Platform SDK | 2025.1 (IC) | AnAction, ProgressManager, Task.Backgroundable, Notifications | Project targets IC 2025.1–2025.3 |
| `ApplicationManager.getApplication()` | Platform | `executeOnPooledThread` + `invokeLater` — background/EDT dispatch | Already used in `doPush()` and `reloadData()`; project-established pattern |
| `Task.Backgroundable` | Platform | Status bar progress indicator for long-running ops | Locked decision; wraps the pooled-thread execution for IDE progress |
| `ProgressManager.getInstance().run()` | Platform | Launches `Task.Backgroundable` with status bar indicator | Standard IntelliJ API for cancellable background tasks |
| `Notification` / `Notifications.Bus.notify` | Platform | Balloon notifications (error, warning) | Already used for push errors; same group `"SystemExplorer"` |
| `AllIcons` | Platform | Standard icons for toolbar actions | Already used for Refresh, Push, Add, Remove actions |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `LocalGitCommandExecutor` | Project | Blocking git process runner for local repos | All new `LocalGitBackend` methods |
| `RemoteGitCommandExecutor` | Project | SSH exec channel for remote repos | All new `RemoteGitBackend` methods; takes `repoPath` + `args` identical to local |
| `GitCommandResult` | Project | Uniform result type (`exitCode`, `stdout`, `stderr`, `isSuccess`) | Return type for all new backend methods |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `Task.Backgroundable` + `ProgressManager` | `executeOnPooledThread` (existing pattern) | Existing pattern has no status bar indicator; `Task.Backgroundable` adds progress without restructuring the logic |
| stdout scan for `"CONFLICT"` | Exit code + heuristics | Git exit code 1 for both merge conflicts and network errors — stdout keyword is authoritative for conflict detection |

**Installation:** No new dependencies — all required APIs are in the bundled IntelliJ Platform SDK.

---

## Architecture Patterns

### Recommended Project Structure

No structural changes required. New code goes into existing files:

```
src/main/kotlin/ro/faur/explorer/gitpanel/
├── GitBackend.kt              # Add: pull(), listBranches(), checkoutBranch(),
│                              #   createBranch(), deleteBranch(), stash(),
│                              #   stashList(), stashPop()
├── LocalGitBackend.kt         # Implement all new methods
├── RemoteGitBackend.kt        # Implement all new methods
└── ui/
    └── GitPanelComponent.kt   # Add Pull AnAction to toolbar + doPull() method
```

### Pattern 1: New GitBackend Interface Method (abstract, no default)

**What:** Add each new operation as an abstract method returning `GitCommandResult` or a typed data class. No default implementations — both backends must implement.
**When to use:** Every new git operation.

```kotlin
// GitBackend.kt — add to existing interface GitBackend : Disposable
fun pull(): GitCommandResult
fun listBranches(): List<BranchInfo>         // BranchInfo: name + isCurrent
fun checkoutBranch(name: String): GitCommandResult
fun createBranch(name: String): GitCommandResult
fun deleteBranch(name: String, force: Boolean = false): GitCommandResult
fun stash(message: String?, includeUntracked: Boolean): GitCommandResult
fun stashList(): List<StashEntry>            // StashEntry: index + message
fun stashPop(index: Int): GitCommandResult
```

### Pattern 2: LocalGitBackend Implementation

**What:** Delegate to `executor.executeBlocking(repoPath, args)` exactly like existing methods.
**When to use:** All new `LocalGitBackend` methods.

```kotlin
// LocalGitBackend.kt
override fun pull(): GitCommandResult {
    return executor.executeBlocking(repoPath, args = arrayOf("pull"))
}

override fun listBranches(): List<BranchInfo> {
    val result = executor.executeBlocking(
        repoPath,
        args = arrayOf("branch", "--format=%(refname:short)\t%(HEAD)")
    )
    if (!result.isSuccess) return emptyList()
    return result.stdoutLines.mapNotNull { parseBranchLine(it) }
}
```

### Pattern 3: RemoteGitBackend Implementation

**What:** Identical to Local — delegate to `executor.executeBlocking(repoPath, args)`. The `RemoteGitCommandExecutor` handles SSH channel setup transparently. Log warnings on failure (consistent with existing `getLog()` impl).
**When to use:** All new `RemoteGitBackend` methods.

```kotlin
// RemoteGitBackend.kt
override fun pull(): GitCommandResult {
    val result = executor.executeBlocking(repoPath, args = arrayOf("pull"))
    if (!result.isSuccess && result.stderr.isNotBlank()) {
        LOG.warn("git pull failed for $displayName (exit ${result.exitCode}): ${result.stderr.take(300)}")
    }
    return result
}
```

### Pattern 4: Pull AnAction in Toolbar (with Task.Backgroundable)

**What:** Add Pull as an `AnAction` in `buildToolbar()`, with `doPull()` implementing background execution using `Task.Backgroundable`.
**When to use:** Pull toolbar button.

```kotlin
// GitPanelComponent.kt — inside buildToolbar(), replacing group.add(Refresh), re-ordered

group.add(object : AnAction("Refresh", "Reload git data", AllIcons.Actions.Refresh) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) { reloadData() }
})
group.add(object : AnAction("Pull", "Pull current branch from remote", AllIcons.Vcs.Fetch) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) { doPull() }
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = selectedBackend != null && !pullInProgress
    }
})
group.add(object : AnAction("Push", "Push current branch to remote", AllIcons.Actions.Upload) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) { doPush() }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = selectedBackend != null }
})
group.add(object : AnAction("Add Local Repo", "Add a local git repository", AllIcons.General.Add) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) { addLocalRepo() }
})
group.add(object : AnAction("Remove Repo", "Remove selected repository", AllIcons.General.Remove) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) { removeSelectedRepo() }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = selectedBackend != null }
})
```

### Pattern 5: doPull() — Background Execution with Conflict Detection

**What:** Background pull with status bar progress indicator, conflict detection, and auto-reload.
**When to use:** Pull action.

```kotlin
// GitPanelComponent.kt
@Volatile private var pullInProgress = false

private fun doPull() {
    val backend = selectedBackend ?: return
    if (pullInProgress) return
    pullInProgress = true

    // Task.Backgroundable shows IDE status bar progress indicator (locked decision)
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Pulling…", false) {
        override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
            val result = backend.pull()
            ApplicationManager.getApplication().invokeLater {
                pullInProgress = false
                if (disposed) return@invokeLater
                when {
                    !result.isSuccess -> {
                        Notifications.Bus.notify(
                            Notification(
                                "SystemExplorer",
                                "Pull Failed",
                                result.stderr.takeLast(300).ifBlank { "Unknown error" },
                                NotificationType.ERROR
                            ), project
                        )
                    }
                    result.stdout.contains("CONFLICT") -> {
                        Notifications.Bus.notify(
                            Notification(
                                "SystemExplorer",
                                "Pull Conflicts",
                                "Pull resulted in conflicts — resolve the marked files",
                                NotificationType.WARNING
                            ), project
                        )
                        reloadData()  // shows UU-status files in Changed Files panel
                    }
                    else -> reloadData()  // success: silent, auto-reload
                }
            }
        }
    })
}
```

### Anti-Patterns to Avoid

- **Calling `backend.pull()` on the EDT:** Git operations block and will freeze the IDE. Always dispatch to `executeOnPooledThread` or `Task.Backgroundable.run()`.
- **Checking only `exitCode` for conflicts:** Git exits 1 for both merge conflicts and non-conflict failures (network errors, auth failures). Stdout keyword `"CONFLICT"` is the authoritative signal.
- **Using default interface methods for new GitBackend methods:** The interface has no default methods — adding defaults would allow one backend to silently miss an implementation. Keep all methods abstract.
- **Forgetting to reset `pullInProgress` on error paths:** If the `invokeLater` block is not reached (e.g., `disposed` early return), the Pull button stays disabled forever. Reset `pullInProgress = false` before checking `disposed`.
- **Using `AllIcons.Actions.Download` for Pull:** Visually confusing. Use `AllIcons.Vcs.Fetch` which is what IntelliJ's own Git plugin uses for fetch/pull operations.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Status bar progress for background ops | Custom overlay / spinning label | `Task.Backgroundable` + `ProgressManager` | Platform manages cancellation, thread safety, and status bar lifecycle |
| Balloon notification display | Custom Swing dialog/popup | `Notifications.Bus.notify` with `Notification` | Consistent with IDE conventions, already established in project |
| EDT dispatch after background work | Raw `SwingUtilities.invokeLater` | `ApplicationManager.getApplication().invokeLater` | IntelliJ-aware; respects disposal and modal state |
| Git conflict detection | Exit code alone | stdout scan for `"CONFLICT"` | git exit code 1 is ambiguous (conflicts and errors both exit 1) |

**Key insight:** Every new operation is a thin delegation to `executeBlocking`. The platform handles all cross-cutting concerns (threading, UI, notifications). Do not add abstraction layers.

---

## Common Pitfalls

### Pitfall 1: Reordering toolbar actions breaks existing tests

**What goes wrong:** `ToolbarIconsTest` or `ToolbarLayoutTest` may assert action order or count and fail after reordering the `DefaultActionGroup`.
**Why it happens:** Existing tests snapshot the toolbar state.
**How to avoid:** Check `ToolbarLayoutTest` and `ToolbarIconsTest` before finalizing group order; update test expectations to match the new order.
**Warning signs:** Compilation passes but unit tests fail with index-out-of-bounds or wrong-icon assertions.

### Pitfall 2: pullInProgress flag not reset on early returns

**What goes wrong:** `pullInProgress` stays `true` after the panel is disposed mid-pull, permanently disabling the Pull button if the panel is recreated.
**Why it happens:** `disposed` check returns early before `pullInProgress = false`.
**How to avoid:** Reset `pullInProgress = false` as the first line inside `invokeLater`, before the `disposed` guard.
**Warning signs:** Pull button stays greyed out after pulling once and reopening the panel.

### Pitfall 3: Task.Backgroundable modal vs non-modal

**What goes wrong:** Passing `canBeCancelled = true` to `Task.Backgroundable` adds a Cancel button that does not actually cancel the blocking SSH/process call.
**Why it happens:** `Task.Backgroundable` cancellation is cooperative — the `run()` body must check `indicator.isCanceled`. The existing executor has no cancellation hook.
**How to avoid:** Pass `canBeCancelled = false` (as shown in the doPull() example). Do not add fake cancellation UI.
**Warning signs:** Cancel button appears but clicking it does nothing — the operation continues in background.

### Pitfall 4: Conflict detection false positive on rebase/cherry-pick messages

**What goes wrong:** `stdout.contains("CONFLICT")` triggers the conflict warning balloon on messages like "No CONFLICT detected — everything is fine" (hypothetical).
**Why it happens:** Substring match is too broad.
**How to avoid:** Git's actual conflict lines are always `"CONFLICT (..."` — check for `"CONFLICT ("` or just `"CONFLICT"` (git has no "CONFLICT" line that indicates no conflict). In practice, git only prints lines starting with `CONFLICT` when actual conflicts occur, so simple `contains("CONFLICT")` is safe based on verified git output format.
**Warning signs:** Warning balloon appears on clean pulls — investigate stdout content.

### Pitfall 5: New interface methods break compilation before both backends implement them

**What goes wrong:** Adding abstract methods to `GitBackend` causes a compilation error until both `LocalGitBackend` and `RemoteGitBackend` implement them.
**Why it happens:** Kotlin interface abstract methods without defaults require all implementing classes to compile.
**How to avoid:** Add all new interface methods and both backend implementations in a single commit. Use stub implementations (`return GitCommandResult(0, "", "")` / `return emptyList()`) to unblock compilation while building out the full implementation iteratively within the same wave.
**Warning signs:** `Class 'LocalGitBackend' is not abstract and does not implement abstract member` compile error.

---

## Code Examples

Verified patterns from existing project source:

### Existing push background pattern (source for doPull template)

```kotlin
// Source: src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt
private fun doPush() {
    val backend = selectedBackend ?: return
    ApplicationManager.getApplication().executeOnPooledThread {
        val result = backend.push()
        ApplicationManager.getApplication().invokeLater {
            if (disposed) return@invokeLater
            if (!result.isSuccess) {
                Notifications.Bus.notify(
                    Notification(
                        "SystemExplorer",
                        "Push Failed",
                        result.stderr.takeLast(300).ifBlank { "Unknown error" },
                        NotificationType.ERROR
                    ), project
                )
            } else {
                reloadData()
            }
        }
    }
}
```

### Existing AnAction with enable guard

```kotlin
// Source: src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt
group.add(object : AnAction("Push", "Push current branch to remote", AllIcons.Actions.Upload) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun actionPerformed(e: AnActionEvent) { doPush() }
    override fun update(e: AnActionEvent) { e.presentation.isEnabled = selectedBackend != null }
})
```

### Existing executor pattern (source for all new backend methods)

```kotlin
// Source: src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt
override fun push(): GitCommandResult {
    return executor.executeBlocking(
        repoPath,
        args = arrayOf("push")
    )
}

// Source: src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt
override fun push(): GitCommandResult {
    return executor.executeBlocking(
        repoPath,
        args = arrayOf("push")
    )
}
```

### Recommended data models for downstream-facing new methods

```kotlin
// Add to GitBackend.kt alongside existing data classes

data class BranchInfo(
    val name: String,
    val isCurrent: Boolean,
)

data class StashEntry(
    val index: Int,        // 0-based, matches stash@{N}
    val message: String,   // "WIP on branch: <hash> <subject>" or custom message
)
```

### git branch output parsing (for listBranches implementation)

```kotlin
// git branch --format=%(refname:short)\t%(HEAD)
// Output: "main\t*" for current, "feature-x\t " for others
internal fun parseBranchLine(line: String): BranchInfo? {
    val parts = line.split("\t")
    if (parts.size < 2) return null
    return BranchInfo(name = parts[0].trim(), isCurrent = parts[1].trim() == "*")
}
```

### git stash list output parsing (for stashList implementation)

```kotlin
// git stash list --format=%gd\t%s
// Output: "stash@{0}\tWIP on main: abc1234 fix bug"
internal fun parseStashLine(line: String): StashEntry? {
    val tabIdx = line.indexOf('\t')
    if (tabIdx < 0) return null
    val ref = line.substring(0, tabIdx)  // "stash@{0}"
    val msg = line.substring(tabIdx + 1)
    val index = ref.removePrefix("stash@{").removeSuffix("}").toIntOrNull() ?: return null
    return StashEntry(index = index, message = msg)
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `executeOnPooledThread` (fire-and-forget) | `Task.Backgroundable` + `ProgressManager` for long ops | IntelliJ 2020+ (platform matured) | Status bar progress visible to user; IDE knows a task is running |
| `javax.swing.SwingUtilities.invokeLater` | `ApplicationManager.getApplication().invokeLater` | Early IntelliJ Platform | IntelliJ-aware dispatch; respects read/write action model |

**Deprecated/outdated:**
- `com.intellij.openapi.progress.ProgressManager.runProcessWithProgressSynchronously`: blocks EDT — never use for background git ops.
- Anonymous `Thread` objects for background work: use pooled thread or `Task.Backgroundable` instead.

---

## Open Questions

1. **Pull timeout for RemoteGitBackend**
   - What we know: `GitCommandExecutor.executeBlocking` defaults to `Duration.ofSeconds(30)`. Remote `git pull` over SSH can take longer for large repos or slow networks.
   - What's unclear: Should pull use a longer timeout (e.g., 120s)?
   - Recommendation: Use the default 30s for Phase 1 (consistent with all other backend methods). Log a warning on timeout. Add configurable timeout to a later phase if users report issues.

2. **Pull icon choice**
   - What we know: `AllIcons.Vcs.Fetch` exists in the platform and is used by IntelliJ Git plugin for fetch/pull. `AllIcons.Actions.CheckOut` also exists.
   - What's unclear: Which icon is the best semantic fit.
   - Recommendation: Use `AllIcons.Vcs.Fetch` — it clearly conveys "get from remote". Verify it exists in SDK 2025.1 at implementation time.

3. **Tracking branch for pull**
   - What we know: `git pull` with no arguments uses the configured tracking branch. If no tracking branch is configured, git returns non-zero exit with a helpful error message.
   - What's unclear: Whether users will encounter "no tracking branch" errors frequently.
   - Recommendation: Use bare `git pull` — the error stderr will surface in the "Pull Failed" balloon with sufficient detail for the user to act.

---

## Sources

### Primary (HIGH confidence)
- Project source code — `GitPanelComponent.kt`, `GitBackend.kt`, `LocalGitBackend.kt`, `RemoteGitBackend.kt`, `GitCommandResult.kt`, `LocalGitCommandExecutor.kt`, `RemoteGitCommandExecutor.kt` — direct codebase inspection
- `build.gradle.kts` + `gradle.properties` — IntelliJ Platform 2025.1 (IC), Kotlin 2.1.0, Java 21 verified from project config

### Secondary (MEDIUM confidence)
- IntelliJ Platform SDK documentation on `Task.Backgroundable` and `ProgressManager` — API is stable and well-documented across 2020+ platforms
- Git documentation on `git pull` stdout format — CONFLICT lines always appear as `CONFLICT (type): path` in merge output

### Tertiary (LOW confidence)
- `AllIcons.Vcs.Fetch` icon existence in SDK 2025.1 — assumed from training data; verify at implementation time by checking AllIcons in the IDE

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries are already in use in the project; no new dependencies
- Architecture: HIGH — patterns are direct extrapolations from existing `doPush()` template with verified code
- Pitfalls: HIGH — all pitfalls derived from direct codebase inspection and known IntelliJ Platform threading model
- Interface method signatures (discretion area): MEDIUM — `BranchInfo`/`StashEntry` shapes are reasonable but may need adjustment based on downstream phase needs

**Research date:** 2026-02-28
**Valid until:** 2026-03-28 (stable IntelliJ Platform SDK; no fast-moving dependencies)
