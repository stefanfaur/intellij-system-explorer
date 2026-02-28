# Requirements: System Explorer — IntelliJ Plugin

**Defined:** 2026-02-28
**Core Value:** A single explorer pane where local and remote (SSH) files look and behave identically — browse, edit, and manage them without leaving IntelliJ.

## v1 Requirements

### Drag & Drop

- [ ] **DND-01**: User can drag a file from the explorer tree and drop it into IntelliJ's project view without conflict or silent failure
- [ ] **DND-02**: User sees a drop-target highlight row when dragging over a valid directory in the explorer
- [ ] **DND-03**: Tree auto-scrolls when user drags near the top or bottom edge
- [ ] **DND-04**: User sees a ghost drag image representing the dragged file(s) during the drag gesture
- [ ] **DND-05**: Dropping a file onto another file targets the parent directory (not the file itself)
- [ ] **DND-06**: VFS is refreshed in both source and destination directories after a drag-move completes

### File Tree Rendering

- [ ] **TREE-01**: File tree icons are correct after dumb-mode transitions (no stale icon cache)
- [ ] **TREE-02**: Expand/collapse does not produce duplicate placeholder children on repeated toggling
- [ ] **TREE-03**: Modified files show VCS color coding (blue), untracked files show different color (matches IntelliJ conventions)
- [ ] **TREE-04**: File tree uses `TreeUIHelper.installTreeSpeedSearch()` (replaces deprecated `TreeSpeedSearch` constructor)

### Context Menus

- [ ] **CTX-01**: Right-clicking a file shows: Open in Editor, Copy, Paste, Delete, Rename, New File, New Directory
- [ ] **CTX-02**: Right-clicking a file shows "Reveal in Finder/Explorer" that opens the OS file manager at that path
- [ ] **CTX-03**: Right-clicking a file shows "Open Terminal Here" that opens IntelliJ's built-in terminal at that directory
- [ ] **CTX-04**: Context menu actions are correctly enabled/disabled based on selection state (no stale enable/disable)

### Diff Viewer

- [ ] **DIFF-01**: User can select a changed file in the Git panel staging area and see an inline pre-commit diff embedded in the panel
- [ ] **DIFF-02**: User can trigger "Show Diff" from the Git panel to open the full-window diff frame (DiffManager popup)
- [ ] **DIFF-03**: Diff viewer displays HEAD version vs. working-tree version of the selected file
- [ ] **DIFF-04**: User can right-click any two files in the explorer and select "Compare With..." to open a side-by-side diff
- [ ] **DIFF-05**: Diff viewer is disposed correctly when the panel is closed or a new file is selected (no memory leak)

### Git Core

- [ ] **GIT-01**: User can trigger a "Pull" action from the Git panel toolbar that pulls the current branch from remote
- [ ] **GIT-02**: Pull operation runs in the background and shows progress; does not freeze the IDE
- [ ] **GIT-03**: Pull result (success, conflicts, error) is surfaced to the user in the Git panel status area

### Branch Management

- [ ] **BRANCH-01**: User can view a list of all local branches in the Git panel, with the current branch highlighted
- [ ] **BRANCH-02**: User can checkout an existing branch from the branch list
- [ ] **BRANCH-03**: User can create a new branch from the current HEAD via a name-input dialog
- [ ] **BRANCH-04**: User can delete a local branch (with confirmation) from the branch list
- [ ] **BRANCH-05**: Checking out a branch with a dirty working tree prompts the user to stash, hard-reset, or cancel (no silent failure)

### Stash

- [ ] **STASH-01**: User can create a stash with an optional message from the Git panel toolbar
- [ ] **STASH-02**: Stash creation includes untracked files (--include-untracked option surfaced in dialog)
- [ ] **STASH-03**: User can view a list of all stash entries in the Git panel
- [ ] **STASH-04**: User can apply a selected stash entry
- [ ] **STASH-05**: User can drop (delete) a selected stash entry (with confirmation)

## v2 Requirements

### Git Log

- **LOG-01**: User can view a linear git log with commit hash, author, date, and message
- **LOG-02**: User can see branch labels annotated on relevant commits in the log
- **LOG-03**: Full branch DAG visualization (defer — high complexity)

### Notifications

- **NOTIF-01**: User receives IDE notification balloon on push/pull success or failure
- **NOTIF-02**: User can configure notification verbosity in plugin settings

### Performance

- **PERF-01**: Explorer tree renders up to 10,000 files without visible lag
- **PERF-02**: Quick Open results appear within 200ms for local repos up to 50,000 files

## Out of Scope

| Feature | Reason |
|---------|--------|
| Built-in terminal emulator | IntelliJ already has best-in-class terminal; wire "Open Terminal Here" to it |
| Interactive rebase UI | Requires pseudo-terminal; impractical in Swing; shell out to terminal |
| File permission editor | OS/symlink edge cases cause endless support tickets; show read-only |
| Conflict resolution merge editor | IntelliJ's built-in merge editor is best-in-class; don't duplicate |
| Full branch DAG graph | HIGH complexity (see Git Machete plugin); linear log is sufficient for v1 |
| Mobile / non-JetBrains IDEs | JetBrains Platform only |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DND-01 | Phase 3 | Pending |
| DND-02 | Phase 3 | Pending |
| DND-03 | Phase 3 | Pending |
| DND-04 | Phase 3 | Pending |
| DND-05 | Phase 3 | Pending |
| DND-06 | Phase 3 | Pending |
| TREE-01 | Phase 4 | Pending |
| TREE-02 | Phase 4 | Pending |
| TREE-03 | Phase 4 | Pending |
| TREE-04 | Phase 4 | Pending |
| CTX-01 | Phase 4 | Pending |
| CTX-02 | Phase 4 | Pending |
| CTX-03 | Phase 4 | Pending |
| CTX-04 | Phase 4 | Pending |
| DIFF-01 | Phase 2 | Pending |
| DIFF-02 | Phase 2 | Pending |
| DIFF-03 | Phase 2 | Pending |
| DIFF-04 | Phase 2 | Pending |
| DIFF-05 | Phase 2 | Pending |
| GIT-01 | Phase 1 | Pending |
| GIT-02 | Phase 1 | Pending |
| GIT-03 | Phase 1 | Pending |
| BRANCH-01 | Phase 5 | Pending |
| BRANCH-02 | Phase 5 | Pending |
| BRANCH-03 | Phase 5 | Pending |
| BRANCH-04 | Phase 5 | Pending |
| BRANCH-05 | Phase 5 | Pending |
| STASH-01 | Phase 6 | Pending |
| STASH-02 | Phase 6 | Pending |
| STASH-03 | Phase 6 | Pending |
| STASH-04 | Phase 6 | Pending |
| STASH-05 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0 (complete coverage)

---
*Requirements defined: 2026-02-28*
*Last updated: 2026-02-28 after roadmap creation*
