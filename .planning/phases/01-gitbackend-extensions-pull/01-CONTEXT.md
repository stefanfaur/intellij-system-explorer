# Phase 1: GitBackend Extensions + Pull - Context

**Gathered:** 2026-02-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Extend the `GitBackend` interface with all new Git operations needed by downstream phases (listBranches, checkoutBranch, createBranch, deleteBranch, stash, stashList, stashPop, pull) and implement them in both `LocalGitBackend` and `RemoteGitBackend`. Wire a Pull button into the Git panel toolbar. Commit/push UI, diff viewer, branch management UI, and stash UI are separate phases.

</domain>

<decisions>
## Implementation Decisions

### Pull progress
- Use IntelliJ background task (IDE status bar progress indicator) — consistent with how IntelliJ's own Git pull works
- Pull button is disabled while the operation is running to prevent double-pulls
- Git panel data (log + changed files) auto-reloads after pull completes — same as after commit
- Pull works for both LOCAL and REMOTE backends (RemoteGitBackend uses existing SSH executor pattern)

### Pull result display
- Success: silent + auto-reload — no notification; the updated commit log speaks for itself
- Error: error balloon notification with last 300 chars of stderr — consistent with existing push error handling
- Error balloon title: "Pull Failed"

### Conflict surfacing
- Detect conflicts by parsing git output for the `CONFLICT` keyword (git always prints "CONFLICT (content): Merge conflict in path/to/file")
- On conflict: show a warning balloon ("Pull resulted in conflicts — resolve the marked files") + auto-reload Changed Files panel so conflict files appear with their UU status
- No in-panel conflict resolution actions for Phase 1 — surface only, let user resolve in IDE/terminal

### Toolbar placement
- New order: Refresh | Pull | Push | Add Local Repo | Remove Repo
- Pull and Push are adjacent (sync operations grouped); pull-before-push matches git workflow
- Pull button enabled only when a backend is selected (same condition as Push)
- No confirmation dialog for remote Pull (unlike remote Push) — pull doesn't modify the remote

### Claude's Discretion
- `pull()` return type and `GitCommandResult` usage (existing pattern)
- Exact signatures for `listBranches`, `checkoutBranch`, `createBranch`, `deleteBranch`, `stash`, `stashList`, `stashPop` — use whatever data structures make sense for downstream phases
- IntelliJ `ProgressManager` / `Task.Backgroundable` implementation details for background task
- Unit test structure for new interface methods

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LocalGitCommandExecutor` / `RemoteGitCommandExecutor`: existing blocking git command runners — new methods follow the same `executeBlocking(repoPath, args)` pattern
- `GitCommandResult`: existing sealed result type — pull, branch, and stash methods return this
- `GitPanelComponent.doPush()`: template for background operation + error balloon — pull follows the same structure
- `GitPanelComponent.reloadData()`: already refreshes log + changed files — call after pull completes
- `ActionManager.createActionToolbar`: existing toolbar construction in `GitPanelComponent.buildToolbar()` — pull action added to the same `DefaultActionGroup`
- `NotificationGroupManager` / `Notifications.Bus.notify`: existing balloon notification pattern used for push errors

### Established Patterns
- Background ops: `ApplicationManager.getApplication().executeOnPooledThread { ... invokeLater { ... } }` — pull follows this pattern; wrap with IntelliJ `Task.Backgroundable` for progress indicator
- Action enable/disable: `override fun update(e: AnActionEvent) { e.presentation.isEnabled = selectedBackend != null }` — same guard for Pull
- Git output parsing: stdout/stderr line parsing with `GitLogParser`, `GitStatusParser` — conflict detection parses stdout for "CONFLICT"
- Error notifications: `Notification("SystemExplorer", title, message, NotificationType.ERROR)` — same group for pull errors
- Interface extensibility: `GitBackend` is a simple interface with no default methods — add all new operations as abstract methods; both `LocalGitBackend` and `RemoteGitBackend` must implement

### Integration Points
- `GitBackend.kt`: interface file where new method signatures are added
- `LocalGitBackend.kt` + `RemoteGitBackend.kt`: both need full implementations of all new methods
- `GitPanelComponent.buildToolbar()`: Pull `AnAction` inserted after Refresh action in the `DefaultActionGroup`
- `GitPanelComponent`: new `doPull()` private method following `doPush()` structure

</code_context>

<specifics>
## Specific Ideas

No specific references — open to standard approaches for method signatures and task implementation.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 01-gitbackend-extensions-pull*
*Context gathered: 2026-02-28*
