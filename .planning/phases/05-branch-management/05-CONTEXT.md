# Phase 5: Branch Management - Context

**Gathered:** 2026-03-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can view, switch, create, and delete local branches from inside the Git panel, with safe handling of dirty working trees. Remote branch management and stash integration are separate phases.

</domain>

<decisions>
## Implementation Decisions

### Branch List UI
- Branch list is a **popup triggered by clicking the existing `branchLabel` in the toolbar** (no new always-visible panel section)
- Popup is a quick-switcher: clicking a branch checks it out — no other actions in the popup
- Create and Delete are separate **toolbar buttons** — not in the popup
- Toolbar button is the only entry point for Create Branch

### Branch Checkout
- After successful checkout: full `reloadData()` — branch label, commit log, and working tree all refresh
- No partial refresh — always reload everything

### Dirty Working Tree Dialog
- When checkout is attempted with uncommitted changes, show a dialog with three options:
  - **Stash & Switch** — auto-stash with message `"Auto-stash before checkout to <target-branch>"`, then checkout
  - **Discard & Switch** — hard-reset working tree, then checkout (requires explicit confirmation within the dialog or strong wording)
  - **Cancel** — abort, do nothing
- No user-editable stash message — auto-generated only

### Create Branch
- Simple dialog: single text input for branch name + OK/Cancel
- **Client-side validation**: invalid chars (spaces, `..`, `~`, `^`, `:`, `?`, `*`, `[`, `\`) and reserved names rejected inline; OK button disabled until name is valid
- Always branches from current HEAD (no commit selector)
- **Auto-checkout** the new branch immediately after creation

### Delete Branch
- Requires selection of a branch in the branch popup (or a toolbar button on the selected branch)
- Standard confirmation dialog for normal case
- **Detect unmerged commits**: if the branch has commits not merged into the current branch, show a **stronger warning dialog** explaining commits would be lost, with a `Force Delete` button and Cancel — no silent data loss
- Force-delete uses `git branch -D`; normal delete uses `git branch -d`

### Claude's Discretion
- Exact popup/list component choice (JBPopup, JBList, etc.)
- Icon choices for branch toolbar buttons
- Exact wording of confirmation dialogs beyond the key behaviors above
- Whether to show branch count in the toolbar label

</decisions>

<specifics>
## Specific Ideas

- The `branchLabel` in the toolbar is the natural anchor for the branch popup — it already shows the current branch with a ⎇ prefix
- The dirty-tree dialog must make "Discard & Switch" clearly destructive (wording or icon)

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GitBackend.listBranches()` → returns `List<BranchInfo>` (name, isCurrent) — already implemented in Phase 1
- `GitBackend.checkoutBranch(name)`, `createBranch(name)`, `deleteBranch(name, force)` — all exist on interface and both backends
- `GitBackend.stash(message, includeUntracked)` — exists; Phase 5 will call it with a generated message during Stash & Switch
- `branchLabel: JBLabel` in `GitPanelComponent` — existing toolbar widget, natural popup anchor
- `JOptionPane.showConfirmDialog` — used for existing push confirmations; same pattern for delete confirmation
- `ApplicationManager.getApplication().executeOnPooledThread` + `invokeLater` — established threading pattern for all git ops

### Established Patterns
- Toolbar actions: `AnAction` subclasses in `DefaultActionGroup`, added to `buildToolbar()`
- Confirmation dialogs: `JOptionPane` with OK_CANCEL_OPTION and WARNING_MESSAGE
- Background git operations: pooled thread → invokeLater result handling
- Stale-result guard: `System.identityHashCode(selectedBackend) != snapshotKey` check before applying results
- Error reporting: `Notifications.Bus.notify` with `NotificationType.ERROR/WARNING`

### Integration Points
- `GitPanelComponent.buildToolbar()` — add branch management toolbar buttons here
- `GitPanelComponent.reloadData()` — call after checkout/create/delete to refresh all panel state
- `branchLabel` mouse listener — attach popup trigger here
- `GitBackend.stash()` — called by dirty-tree Stash & Switch before `checkoutBranch()`

</code_context>

<deferred>
## Deferred Ideas

- Remote branch tracking / push -u — future phase
- Branch rename — could be a backlog item
- Visual graph of branch divergence — separate scope

</deferred>

---

*Phase: 05-branch-management*
*Context gathered: 2026-03-01*
