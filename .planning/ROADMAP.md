# Roadmap: System Explorer — IntelliJ Plugin

## Overview

This roadmap delivers the current milestone on an existing IntelliJ Platform plugin: reliable drag-and-drop, pre-commit diff viewer, deeper Git operations (pull, branches, stash), and file tree rendering polish. The foundation (multi-panel layout, SSH browsing, staging/commit/push, fuzzy search) already exists. The six phases extend it in dependency order — GitBackend interface first to unblock all Git UI work, then diff, DnD, tree polish, branch management, and stash — ending with a plugin that passes the JetBrains Marketplace quality bar.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: GitBackend Extensions + Pull** - Extend GitBackend interface with all new Git operations and wire pull into the Git panel toolbar
- [ ] **Phase 2: Pre-Commit Diff Viewer** - Embed inline diff in Git panel for selected staged files and provide full-window diff popup
- [ ] **Phase 3: Drag and Drop Polish** - Fix DnD reliability with drop highlighting, auto-scroll, ghost image, and VFS refresh
- [ ] **Phase 4: File Tree and Context Menu Polish** - Correct icon rendering, VCS color coding, expand/collapse glitches, and complete context menu actions
- [ ] **Phase 5: Branch Management** - Add branch list panel with checkout, create, and delete; handle dirty-tree smart checkout
- [ ] **Phase 6: Stash UI** - Add stash creation dialog and stash list panel with apply and drop actions
- [ ] **Phase 7: Polish, improve quick open / smart search** - Polish, improve quick open / smart search

## Phase Details

### Phase 1: GitBackend Extensions + Pull
**Goal**: All new Git operations exist on the GitBackend interface and both implementations; pull runs in the background and surfaces its result in the Git panel
**Depends on**: Nothing (first phase)
**Requirements**: GIT-01, GIT-02, GIT-03
**Success Criteria** (what must be TRUE):
  1. User can click a Pull button in the Git panel toolbar and the pull executes without freezing the IDE
  2. Pull progress is visible (background indicator) while the operation runs
  3. Pull result — success, conflicts, or error — appears in the Git panel status area after completion
  4. All new GitBackend methods (listBranches, checkoutBranch, createBranch, deleteBranch, stash, stashList, stashPop, pull) exist on the interface and both implementations compile and pass unit tests
**Plans**: 2 plans

Plans:
- [ ] 01-01-PLAN.md — Extend GitBackend interface + implement all new methods in both backends + parsing helpers with unit tests
- [ ] 01-02-PLAN.md — Wire Pull AnAction into GitPanelComponent toolbar + implement doPull() with Task.Backgroundable and result handling

### Phase 2: Pre-Commit Diff Viewer
**Goal**: Users can inspect what changed in a file before committing, both inline in the Git panel and in a full-window frame
**Depends on**: Phase 1
**Requirements**: DIFF-01, DIFF-02, DIFF-03, DIFF-04, DIFF-05
**Success Criteria** (what must be TRUE):
  1. Selecting a changed file in the Git panel staging area displays an inline diff (HEAD vs. working tree) embedded in the panel without opening a new window
  2. Clicking "Show Diff" in the Git panel opens the full-window DiffManager popup showing the same HEAD vs. working-tree comparison
  3. Right-clicking two files in the explorer and selecting "Compare With..." opens a side-by-side diff of those two files
  4. Switching file selection in the staging area or closing the panel disposes the previous diff viewer with no memory leak
**Plans**: TBD

### Phase 3: Drag and Drop Polish
**Goal**: Dragging files within the explorer and into IntelliJ's project view is reliable, visually clear, and leaves both source and destination in a consistent state
**Depends on**: Nothing (independent of Phase 1 and 2)
**Requirements**: DND-01, DND-02, DND-03, DND-04, DND-05, DND-06
**Success Criteria** (what must be TRUE):
  1. Dragging a file from the explorer tree and dropping it into IntelliJ's project view succeeds without conflict or silent failure
  2. A highlighted row appears on the drop-target directory as the user drags over it
  3. The tree scrolls automatically when the cursor is held near the top or bottom edge during a drag
  4. A ghost image representing the dragged file(s) follows the cursor throughout the drag gesture
  5. Dropping a file onto another file moves it into the parent directory; after any drag-move, both source and destination directories refresh in the VFS
**Plans**: TBD

### Phase 4: File Tree and Context Menu Polish
**Goal**: The file tree renders icons and VCS colors correctly at all times and the right-click context menu exposes all expected file actions
**Depends on**: Nothing (independent)
**Requirements**: TREE-01, TREE-02, TREE-03, TREE-04, CTX-01, CTX-02, CTX-03, CTX-04
**Success Criteria** (what must be TRUE):
  1. File icons are correct immediately after dumb-mode transitions; no stale icons appear in the tree
  2. Repeatedly expanding and collapsing a directory node never produces duplicate placeholder children
  3. Modified files show blue VCS color and untracked files show the correct untracked color, matching IntelliJ project view conventions
  4. Right-clicking a file shows all seven actions (Open in Editor, Copy, Paste, Delete, Rename, New File, New Directory) and each action executes correctly; Reveal in Finder/Explorer and Open Terminal Here are also present and functional
  5. Context menu actions are enabled only when the current selection makes them valid; no stale enabled/disabled states
**Plans**: TBD

### Phase 5: Branch Management
**Goal**: Users can view, switch, create, and delete local branches from inside the Git panel, with safe handling of dirty working trees
**Depends on**: Phase 1
**Requirements**: BRANCH-01, BRANCH-02, BRANCH-03, BRANCH-04, BRANCH-05
**Success Criteria** (what must be TRUE):
  1. The Git panel shows a list of all local branches with the current branch visually highlighted
  2. User can select a branch and check it out; the file tree and Git panel status reflect the new branch
  3. User can create a new branch by entering a name in a dialog; the new branch appears in the list and becomes current
  4. User can delete a local branch with a confirmation prompt; the branch disappears from the list
  5. Attempting to checkout a branch with a dirty working tree presents a dialog offering to stash, hard-reset, or cancel — no silent failure or data loss
**Plans**: TBD

### Phase 6: Stash UI
**Goal**: Users can create, view, apply, and delete stash entries from the Git panel without using a terminal
**Depends on**: Phase 5
**Requirements**: STASH-01, STASH-02, STASH-03, STASH-04, STASH-05
**Success Criteria** (what must be TRUE):
  1. User can open a stash creation dialog from the Git panel toolbar, optionally enter a message, and choose whether to include untracked files
  2. After stash creation, the new entry appears in the stash list in the Git panel
  3. User can select a stash entry and apply it; the working tree reflects the stashed changes
  4. User can select a stash entry and delete it after a confirmation prompt; the entry disappears from the list
**Plans**: TBD

### Phase 7: Polish, improve quick open / smart search
**Goal**: Polish, improve quick open / smart search
**Depends on**: TBD
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7
(Phase 3 and Phase 4 are independent and may run in parallel with Phase 2 if capacity allows)

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. GitBackend Extensions + Pull | 1/2 | In Progress|  |
| 2. Pre-Commit Diff Viewer | 0/? | Not started | - |
| 3. Drag and Drop Polish | 0/? | Not started | - |
| 4. File Tree and Context Menu Polish | 0/? | Not started | - |
| 5. Branch Management | 0/? | Not started | - |
| 6. Stash UI | 0/? | Not started | - |
| 7. Polish, improve quick open / smart search | 0/? | Not started | - |
