# Phase 6: Stash UI - Context

**Gathered:** 2026-03-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can create, view, apply, and delete stash entries from the Git panel without using a terminal. Covers: stash creation dialog, stash list panel, apply/pop/drop actions. Stash diff preview is part of this phase. No new backend operations beyond what's needed to support these UI actions.

</domain>

<decisions>
## Implementation Decisions

### Stash list placement
- New dedicated card in the existing CardLayout — same pattern as CommitLogPanel/ChangedFilesPanel
- Navigation: toolbar button "Stash List" toggles between commit log view and stash card
- Layout when on stash card: stash list in left slot, stash diff preview (vs parent commit) in right slot
- After apply or drop: auto-return to commit log view
- No count badge on the toolbar button — icon only

### Apply vs Pop backend
- Keep existing `stashPop(index)` (used by auto-stash-on-checkout flow)
- Add two new methods to `GitBackend`: `stashApply(index)` (apply, keep stash) and `stashDrop(index)` (delete only)
- UI exposes three actions: **Apply** (keep stash), **Pop** (apply + drop), **Drop** (delete only)
- Errors shown as notification balloons — consistent with Pull/Push error handling
- After successful apply: stash entry stays selected (so user can easily drop it next)

### Stash creation dialog
- Follow `CreateBranchDialog` pattern: `DialogWrapper` + `FormBuilder`
- Fields: optional message (`JBTextField`) with placeholder showing git default (e.g. "WIP on main: abc1234 last commit msg"), include-untracked `JCheckBox`
- OK button enabled immediately (empty message = git default)
- Include-untracked: unchecked by default; state is NOT persisted across sessions
- After successful stash creation: auto-navigate to stash card so user sees the new entry

### Stash list item display & actions
- Row format: message only — no `stash@{N}:` prefix (cleaner; index is an implementation detail)
- Secondary toolbar scoped to stash card (only rendered/visible when stash card is active): Apply, Pop, Drop buttons enabled when a row is selected
- Right-click context menu on a row mirrors toolbar exactly: Apply, Pop, Drop
- Right slot shows diff of stash against its parent commit (what changes are stashed)

### Claude's Discretion
- Exact icon choices for Apply/Pop/Drop toolbar buttons
- Stash diff panel implementation details (reuse InlineDiffPanel or similar)
- Empty state UI when stash list is empty

</decisions>

<specifics>
## Specific Ideas

- "Stash List" toolbar button should feel like a view toggle, not a one-off action — similar to how switching between log and staging views works
- Keep-stash Apply is the primary action users want; Pop is a convenience; Drop is destructive (confirm prompt already required by STASH-05)

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CreateBranchDialog` (`ui/CreateBranchDialog.kt`): `DialogWrapper` + `FormBuilder` pattern — stash dialog should follow this exactly
- `GitPanelComponent.CardLayout` (`rightSlotLayout`): already manages multiple content panels; adding a stash card is straightforward
- `CommitLogPanel` / `ChangedFilesPanel`: reference for how content cards are structured
- `InlineDiffPanel`: candidate for reuse in the stash diff right slot
- Existing `AnAction` inline pattern in `GitPanelComponent` for toolbar actions
- `stash()`, `stashList()`, `stashPop()` already on `GitBackend` interface and both implementations

### Established Patterns
- Toolbar actions are inline `AnAction` objects added to a `DefaultActionGroup` in `GitPanelComponent`
- Error reporting: notification balloons via `notifyError()` helper (Pull/Push precedent)
- Confirmation dialogs: `JOptionPane.showConfirmDialog` (Delete Branch, Push force precedent)
- Background operations: `Task.Backgroundable` for git ops that may block

### Integration Points
- `GitBackend` interface needs two new methods: `stashApply(index: Int)` and `stashDrop(index: Int)`
- Both `LocalGitBackend` and `RemoteGitBackend` must implement new methods
- `GitPanelComponent` toolbar group gets "Stash" (create) and "Stash List" (toggle view) actions
- Stash card registered in `rightSlotLayout` CardLayout alongside existing cards

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 06-stash-ui*
*Context gathered: 2026-03-01*
