# Project Research Summary

**Project:** IntelliJ Explorer — Unified File Explorer + Git Panel Plugin
**Domain:** IntelliJ Platform plugin — file explorer, SSH/SFTP remote browser, Git operations, diff viewer
**Researched:** 2026-02-28
**Confidence:** MEDIUM-HIGH (core APIs verified against source; architecture derived from live codebase)

## Executive Summary

This is a milestone release on an existing IntelliJ Platform plugin that provides a unified local + SSH/SFTP file explorer with an integrated Git panel. The plugin already has a working foundation — multi-panel layout, staging/commit/push, remote browsing, fuzzy search — and the current work focuses on four targeted improvements: reliable drag-and-drop, pre-commit diff viewer, deeper Git operations (branches, stash, pull), and file tree rendering polish. Experts building IntelliJ plugins in this domain consistently follow three patterns: a dual-backend interface that abstracts local vs. SSH git execution, strict EDT/background thread separation for all I/O, and the `com.intellij.diff.*` API (not the deprecated `com.intellij.openapi.diff.*` namespace) for all diff surfaces.

The recommended approach is to build new Git operations by extending the existing `GitBackend` interface first, then layer UI panels on top. Every new Git operation must run on a pooled background thread with an EDT marshal back via `invokeLater` and a stale-callback guard. The diff viewer should use `DiffManager.createRequestPanel()` for inline embedded diffs and `DiffManager.showDiff()` with `DiffDialogHints.FRAME` for full-window diffs; both use `DiffContentFactory` + `SimpleDiffRequest` from `com.intellij.diff.*`. DnD polish is purely behavioral — the architecture split between Swing `TransferHandler` (drag-out) and IntelliJ `DnDNativeTarget` (drop-in) is already correct and must not be changed.

The primary risks are: (1) calling git CLI on the EDT, which freezes the IDE and triggers IntelliJ's slow-operation detector on 2024.1+; (2) leaking `DiffRequestPanel` instances by passing `project` as the parent disposable instead of a component-scoped disposable; and (3) the 2025.1 threading model change that removed the implicit Write-Intent lock from `SwingUtilities.invokeLater`, which breaks any VFS/PSI read that was not wrapped in an explicit `ReadAction`. All three are preventable with patterns already established in the codebase.

## Key Findings

### Recommended Stack

The existing stack needs no new dependencies for this milestone. The diff viewer uses `com.intellij.diff.DiffManager`, `DiffContentFactory`, and `SimpleDiffRequest`, all stable since IDEA 14.1 with no breaking changes through 2025.3. All new Git operations (branches, stash, pull) continue the existing CLI pattern via `LocalGitCommandExecutor`/`RemoteGitCommandExecutor` — adding `git4idea` as a bundled plugin dependency is explicitly rejected because it locks the plugin to IDEA-only, breaks SSH-managed repos, and introduces significant API churn risk. The `TreeUIHelper.installTreeSpeedSearch()` pattern replaces the soft-deprecated `TreeSpeedSearch` constructor for tree navigation.

**Core technologies:**
- `com.intellij.diff.DiffManager` / `DiffContentFactory` / `SimpleDiffRequest`: diff display (embedded and popup) — verified against intellij-community source, stable API
- `GitBackend` interface (existing) extended with `getDiff`, `listBranches`, `checkoutBranch`, `createBranch`, `deleteBranch`, `stash`, `stashList`, `stashPop`, `pull`: all new Git ops — keeps local/remote abstraction intact
- Swing `TransferHandler` + `tree.dragEnabled=true` (drag-out) + IntelliJ `DnDNativeTarget` (drop-in): DnD split pattern — validated in live code and MEMORY.md
- `ApplicationManager.getApplication().executeOnPooledThread` + `invokeLater` + stale-guard: threading model — required for all git and SFTP I/O
- `TreeUIHelper.getInstance().installTreeSpeedSearch()`: speed search — replaces deprecated constructor

### Expected Features

**Must have (table stakes):**
- File tree icons + VCS color coding (modified=blue, untracked=red) — sets the visual quality bar vs. built-in project view
- Context menu completeness: copy, paste, delete, rename, new file, open in editor, reveal in OS — broken right-click = 1-star reviews
- Keyboard navigation: arrow keys, Enter to open, speed search, back/forward/up — keyboard-first IDE users notice any gap
- Pre-commit diff viewer in Git panel — most-requested Git panel feature; without it users stay on IntelliJ's built-in commit window
- Pull from remote — Git workflow is incomplete without pull; users currently terminal-out
- Reliable DnD between explorer panels and IDE project view — currently reported as broken; undermines trust in the whole plugin

**Should have (competitive):**
- Branch create/checkout with smart checkout (stash on dirty tree) — differentiates from minimal branch support
- Stash UI (list, create, apply, drop) — IntelliJ's built-in stash UI is minimal; a first-class panel adds real value
- Side-by-side diff for arbitrary files (not just VCS diffs) — builds on DiffManager infrastructure already used for pre-commit diff; low marginal cost
- Git log with linear branch labels — full DAG graph is v2+; linear log with labels is achievable and useful now

**Defer (v2+):**
- Git log with full branch graph DAG — HIGH complexity; Git Machete plugin shows the difficulty
- Inline blame on explorer tree nodes — editor blame via GitToolBox covers most users; low urgency
- Conventional commit message assistance — nice-to-have, not a core value driver

**Anti-features to explicitly reject:**
- Built-in terminal emulator: IntelliJ already has a best-in-class terminal; wire "Open Terminal Here" to it instead
- Interactive rebase UI: pseudo-terminal requirement makes it impractical in Swing; shell out and open the terminal
- File permission editor: OS/symlink edge cases generate endless support tickets; show permissions read-only

### Architecture Approach

The plugin follows a clean three-layer architecture: Swing tool windows (ExplorerPanel, GitPanelComponent) at the top, a backend/service layer (GitBackend interface, SftpConnectionManager, GitRepositoryRegistry) in the middle, and an execution layer (LocalGitCommandExecutor, RemoteGitCommandExecutor over SSH) at the bottom. All UI panels are dumb — they display what is pushed to them via callbacks; the orchestrators (GitPanelComponent, BranchManagementPanel) own all write operations. New components (`DiffPanel`, `BranchManagementPanel`, `StashListPanel`) drop into existing packages as siblings without restructuring.

**Major components:**
1. `GitBackend` interface (extended) — single contract for all Git operations, implemented by both `LocalGitBackend` and `RemoteGitBackend`; all new ops added here first
2. `DiffPanel` (new) — wraps `DiffManager.createRequestPanel()` as an embeddable `JComponent`; implements `Disposable` with component-scoped lifetime
3. `BranchManagementPanel` (new) — `JBList` of branches with checkout/create/delete toolbar; wired into `GitPanelComponent` to avoid bloating the orchestrator
4. `StashListPanel` (new) — stash list with apply/drop actions; avoids adding stash logic directly to `GitPanelComponent`
5. `DragDropHandler` / `FileTreeTransferHandler` (polish) — existing split-DnD architecture; behavioral fixes only, no structural change

### Critical Pitfalls

1. **Mixing IntelliJ DnDManager and Swing TransferHandler on the same component** — use the established split: `TransferHandler + dragEnabled=true` for drag-out only; `DnDNativeTarget` registered with `DnDManager` for drop-in only; never register `DnDSource` on a component that also has `dragEnabled=true`
2. **Calling git CLI (any blocking I/O) on the EDT** — wrap every `executeBlocking` call in `executeOnPooledThread`; use `Task.Backgroundable` for long ops (push, pull); audit existing callsites in `GitPanelComponent` before adding new ones
3. **Embedded DiffRequestPanel not disposed** — always pass a component-scoped `Disposable` (e.g., `toolWindow.disposable` or `Disposer.newDisposable(parentDisposable)`), never `project` or `application`; dispose old diff disposable before creating a new one on selection change
4. **Using the deprecated `com.intellij.openapi.diff.*` API** — exclusively use `com.intellij.diff.*`; old API packages are stubs that will be removed; many Stack Overflow answers still show old imports
5. **2025.1 threading model: VFS/PSI reads inside `invokeLater` without `ReadAction`** — wrap all VFS/PSI access in `ReadAction.compute {}` regardless of thread; `SwingUtilities.invokeLater` no longer holds the implicit Write-Intent lock on 2025.1+

## Implications for Roadmap

Based on research, the dependency graph is clear: the `GitBackend` interface must be extended before any Git UI work can start; DnD polish and file tree polish are independent of each other and of Git work. The suggested phase structure below respects these dependencies and groups work by the component boundary each phase touches.

### Phase 1: GitBackend Interface Extensions + Threading Audit

**Rationale:** Every planned Git UI feature (diff, branches, stash, pull) calls methods that don't exist yet on `GitBackend`. This is the prerequisite that unblocks all other Git work. The threading audit is included here because any new method added to the interface will be called from UI — auditing existing callsites now prevents introducing new EDT violations.
**Delivers:** Extended `GitBackend.kt` interface + both implementations (`LocalGitBackend`, `RemoteGitBackend`) with `getDiff`, `listBranches`, `checkoutBranch`, `createBranch`, `deleteBranch`, `stash`, `stashList`, `stashPop`, `pull`; all existing git callsites confirmed off-EDT
**Addresses:** Pull from remote (table stakes), prerequisites for diff/branch/stash
**Avoids:** Git CLI blocking EDT (Pitfall 3); stale-callback accumulation on rapid selection changes

### Phase 2: Pre-Commit Diff Viewer

**Rationale:** The most-requested Git panel feature. Directly depends on Phase 1 (`getDiff`). The `DiffManager` API is well-documented and stable — this is low-risk, high-value work.
**Delivers:** `DiffPanel` wrapping `DiffManager.createRequestPanel()`; `ChangedFilesPanel.onFileSelected` callback; CardLayout extended to show diff when staging file is selected; context menu "Show Diff" using `DiffManager.showDiff()`
**Uses:** `com.intellij.diff.DiffManager`, `DiffContentFactory`, `SimpleDiffRequest` from STACK.md
**Implements:** Pattern 3 (DiffRequestPanel embedded) and Pattern 2 (background executor) from ARCHITECTURE.md
**Avoids:** Leaked diff viewers (Pitfall 4); old diff API namespace (Pitfall 6); diff viewer theme inconsistency

### Phase 3: DnD Polish

**Rationale:** DnD is currently reported broken and undermines user trust in the whole plugin. It is independent of Git work (no new backend methods needed) so can run in parallel with Phase 2 if capacity allows, or follow it. The architecture is already correct — this is purely behavioral polish.
**Delivers:** Drop target row highlighting; auto-scroll during drag; ghost drag image fix; `RemoteTreeDropTarget` edge case fixes (drop onto file vs. directory); VFS refresh covering both source and destination after move
**Addresses:** DnD reliability (highest-priority table stake per FEATURES.md)
**Avoids:** DnD system conflict (Pitfall 1); TransferableWrapper extraction error (Pitfall 2); missing drop visual feedback (Pitfall 7)

### Phase 4: File Tree Rendering Polish

**Rationale:** Table-stakes visual quality work that is independent of Git and DnD. Groups all tree rendering fixes together to avoid scattered patches.
**Delivers:** Icon cache invalidation on dumb mode transitions; expand/collapse placeholder child deduplication; context menu additions (Reveal in Finder/Explorer, Open Terminal Here wired to IntelliJ terminal); narrowed VFS refresh scope; `TreeUIHelper.installTreeSpeedSearch()` migration
**Addresses:** File tree icon + VCS color polish, context menu completeness (both table stakes)
**Avoids:** VFS `findFileByPath()` in cell renderer (performance trap); `ActionUpdateThread.OLD_EDT` deprecation

### Phase 5: Branch Management

**Rationale:** Depends on Phase 1 (listBranches, checkoutBranch, createBranch, deleteBranch) and Phase 1's threading patterns. Builds `BranchManagementPanel` as a separate class, not inline in `GitPanelComponent`, to avoid the anti-pattern of bloating the orchestrator.
**Delivers:** `BranchManagementPanel` with JBList of branches (current highlighted), checkout/create/delete toolbar, dirty-tree smart checkout (stash or hard reset prompt); pull wired into Git panel toolbar
**Addresses:** Branch create/checkout (P2 feature), pull from remote completeness
**Avoids:** Anti-pattern 5 (hardcoding branch ops into GitPanelComponent); branch switch on dirty working tree silently failing

### Phase 6: Stash UI

**Rationale:** Depends on Phase 1 (stash, stashList, stashPop) and Phase 5 (smart checkout uses stash). Grouped after branch work because the branch panel's dirty-tree handling references stash.
**Delivers:** Stash toolbar button + input dialog; `StashListPanel` with JBList of entries, apply/drop actions; git stash with `--include-untracked` option surfaced in UI
**Addresses:** Stash UI (P2 feature)
**Avoids:** `git stash` silently ignoring untracked files ("looks done but isn't" checklist item)

### Phase 7: Side-by-Side Arbitrary File Diff (v1.x)

**Rationale:** Low marginal cost — reuses the DiffManager infrastructure from Phase 2. Triggered by user feedback requesting "compare two files." Placed after core Git work to avoid scope creep in the milestone.
**Delivers:** "Compare With..." context menu action on any two selected files in the explorer; reuses `DiffPanel` from Phase 2
**Addresses:** Side-by-side arbitrary file diff (P2 differentiator feature)
**Uses:** Same `DiffManager` / `SimpleDiffRequest` stack as Phase 2

### Phase Ordering Rationale

- Phase 1 must come first: no Git UI phase can be implemented without the backend methods
- Phase 2 and Phase 3 can run in parallel given separate implementers; they share no dependencies
- Phase 4 is independent and can be interleaved with any other phase but is grouped to batch tree-rendering fixes
- Phase 5 must follow Phase 1; Phase 6 must follow Phase 5 (branch-stash dependency)
- Phase 7 is deliberately last — it delivers the most value when the Git workflow is already solid

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 5 (Branch Management):** Dirty-tree smart checkout UX (stash vs. hard reset vs. cancel prompt) has platform-specific edge cases; verify IntelliJ's own checkout flow for UX precedent before designing the dialog
- **Phase 6 (Stash UI):** `git stash --include-untracked` behavior on remote SSH repos needs validation; test whether `RemoteGitCommandExecutor` handles the extra stdin interaction correctly

Phases with standard patterns (skip research-phase):
- **Phase 1 (GitBackend extensions):** Adding methods to an existing interface with two known implementations — no novel patterns
- **Phase 2 (Pre-Commit Diff):** DiffManager API is fully documented and verified; `DiffRequestPanel` pattern is well-established
- **Phase 3 (DnD Polish):** Architecture is already defined in MEMORY.md; work is behavioral correction, not design
- **Phase 4 (File Tree Polish):** All fixes use established IntelliJ Platform patterns (TreeUIHelper, ColoredTreeCellRenderer, ActionUpdateThread.BGT)
- **Phase 7 (Arbitrary Diff):** Purely reuses Phase 2 infrastructure

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Core diff API verified against intellij-community source; CLI git strategy validated by existing working code; only 2025.x-specific edge cases are MEDIUM |
| Features | MEDIUM | Table stakes and anti-features are HIGH (official docs + competitor observation); differentiator prioritization is inference from marketplace gaps |
| Architecture | HIGH | Derived from live codebase inspection; component responsibilities and data flows verified against existing working implementation |
| Pitfalls | HIGH | Threading pitfalls from official JetBrains platform docs; DnD pitfalls from project MEMORY.md validated against live code; diff disposal from official Disposer docs |

**Overall confidence:** HIGH for the milestone scope (phases 1-4); MEDIUM for v1.x scope (phases 5-7) due to dirty-tree and SSH stash edge cases.

### Gaps to Address

- **Embedded DiffRequestPanel vs. CardLayout layout decision:** Whether to extend `CommitDetailsPanel`'s existing CardLayout with a third DIFF card or restructure the Git panel bottom area as a 4-region splitter is a UX decision not resolved in research. Validate with a prototype before committing to either layout in Phase 2.
- **SSH stash with `--include-untracked`:** Whether `RemoteGitCommandExecutor` handles this correctly needs a live test against a real SSH target before Phase 6 planning.
- **`ActionUpdateThread.BGT` migration scope:** The extent to which existing toolbar actions need to be migrated from `OLD_EDT` (deprecated 2024.1+) is not fully quantified. Needs a codebase scan during Phase 4 planning.
- **2025.x EAP compatibility:** The 2025.3 threading model changes (Write-Intent lock removal from AWT input events) may affect the existing `LocalGitBackend` callsites. Should be validated against a 2025.3 EAP build before the milestone release.

## Sources

### Primary (HIGH confidence)
- `DiffManager.java` source — JetBrains/intellij-community — `createRequestPanel()`, `showDiff()` signatures verified
- `DiffContentFactoryImpl.java` source — JetBrains/intellij-community — `createFromBytes()`, `create(project, VirtualFile)` verified
- IntelliJ Platform API Changes 2025 — official docs — threading model changes, VCS module extraction
- IntelliJ Platform Threading Model — official docs — EDT rules, `executeOnPooledThread` patterns
- Disposer and Disposable — official docs — diff viewer lifetime management
- Changes in threading model 2025.1 and 2025.3 — JetBrains Platform Forum — confirmed Write-Intent lock removal
- Project MEMORY.md — DnD architecture notes validated against live code — DnD split pattern, TransferableWrapper behavior
- Live codebase `/src/main/kotlin/ro/faur/explorer/` — component responsibilities, existing patterns

### Secondary (MEDIUM confidence)
- IntelliJ Platform SDK — Lists and Trees docs — `TreeUIHelper.installTreeSpeedSearch()` pattern
- IntelliJ Platform SDK — VCS Integration for Plugins — `DiffProvider`/`ContentRevision` bridge pattern
- IntelliJ support forums on DiffManager / DiffRequestPanel — corroborates embedded panel approach
- JetBrains Marketplace (GitToolBox, XFTP, Git Machete) — competitor feature baseline

### Tertiary (LOW confidence)
- Pieces.app blog — DnD implementation guide — supports existing architecture choice; not authoritative
- Git4Idea plugin template issue — confirms plugin ID and dependency declaration format
- JetBrains Platform Blog Q4 2025 — current marketplace context

---
*Research completed: 2026-02-28*
*Ready for roadmap: yes*
