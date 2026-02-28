# Phase 2: Pre-Commit Diff Viewer - Context

**Gathered:** 2026-02-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Let users inspect what changed in a file before committing: inline diff embedded in the Git panel, full-window diff popup from the Git panel, and a two-file "Compare With..." from the System Explorer context menu. Staging, committing, and conflict resolution are separate phases.

</domain>

<decisions>
## Implementation Decisions

### Inline diff placement
- The right-side panel slot (currently Commit Details) is shared: history mode shows Commit Details, staging mode shows the inline diff
- Both panels exist in the layout with toggled visibility — show/hide based on mode (not a CardLayout swap)
- Use IntelliJ's DiffManager embedded panel (proper syntax-highlighted diff viewer as a Swing component — theme-aware, disposable)
- When no file is selected in staging mode: show a blank/empty panel (no placeholder message)

### Diff trigger behavior
- Auto-load on file selection — as soon as a file is clicked in the Changed Files list, the diff starts loading
- Immediate replace when a different file is selected — old diff disposed immediately, new one starts loading
- Show a loading spinner in the diff area while content is being prepared
- For new/untracked files (status '?'): show full file content as "all added" (empty → current, all green)

### Full-window diff
- Triggered by: toolbar button in the Git panel (enabled when a file is selected) AND double-clicking a file in the Changed Files list
- Opens IntelliJ's native DiffManager popup (showDiff()) — not a custom dialog
- Available in both staging mode (HEAD vs working tree) and history mode (before-commit vs after-commit for that entry)

### Compare With... (System Explorer)
- Action appears only when exactly two files are selected in the explorer (not for single-file selection)
- Compares working-tree content only — no git involvement, works for any two files
- Left/right order: first selected = left side, second selected = right side
- Opens IntelliJ's DiffManager popup for the comparison

### Claude's Discretion
- Exact spinner/loading indicator implementation
- Disposal lifecycle details for embedded diff panels
- How to track "first selected" vs "second selected" order given available tree selection API

</decisions>

<specifics>
## Specific Ideas

- No specific UI references mentioned — standard IntelliJ diff conventions apply

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ChangedFilesPanel`: existing list with `JBList`, staging/history dual-mode renderer, `getCheckedPaths()` — add `ListSelectionListener` here to trigger diff loading
- `GitPanelComponent.buildCenter()`: existing `JBSplitter` layout — the `commitDetailsPanel` slot is where the diff panel gets added alongside (toggled visibility)
- `CommitFile` data class: already has `path`, `oldPath`, `status` (GitFileStatus) — sufficient to build a diff request
- `GitBackend` interface: already extended in Phase 1 — diff content retrieval method goes here
- `LocalGitBackend` / `RemoteGitBackend`: both will need `getDiffContent(path)` implementation
- `ExplorerPanel` / `FileTreeComponent`: existing tree with multi-selection — add "Compare With..." to context menu action group

### Established Patterns
- Background operations use `Task.Backgroundable` (established in Phase 1 Pull button) — diff loading follows same pattern
- `Disposable` pattern: `GitPanelComponent` already implements `Disposable`; embedded diff viewers must be registered as child disposables
- Actions registered in `plugin.xml` under `SystemExplorer.ActionGroup` (see `ExplorerActions.kt` for reference)
- `ExplorerActionUtil` for action → active panel lookup (needed for "Compare With..." action)

### Integration Points
- `ChangedFilesPanel.list` needs a `ListSelectionListener` → triggers inline diff load in `GitPanelComponent`
- `GitPanelComponent` toolbar (existing `DefaultActionGroup`) gets the "Show Diff" button added
- `GitPanelComponent.commitDetailsPanel` visibility toggled based on staging vs history mode
- `plugin.xml` → `SystemExplorer.ActionGroup`: register `CompareWithAction`
- `FileTreeComponent` (or `LocalBrowserPanel`) context menu: wire `CompareWithAction` for two-file selection

</code_context>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-pre-commit-diff-viewer*
*Context gathered: 2026-02-28*
