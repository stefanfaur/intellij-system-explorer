# Feature Research

**Domain:** IntelliJ plugin — unified file explorer + Git panel (local + SSH/SFTP)
**Researched:** 2026-02-28
**Confidence:** MEDIUM — built-in IDE features HIGH (official JetBrains docs), plugin ecosystem MEDIUM (marketplace observation + community), anti-feature rationale LOW-MEDIUM (inferred from scope + ecosystem norms)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume a file-explorer-plus-Git plugin has. Missing any of these triggers bad reviews or immediate uninstall.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| File tree with icons matching IDE theme | IntelliJ's built-in project view sets the bar — every tree node has a meaningful icon | LOW | Use `ColoredTreeCellRenderer` + `FileTypeManager.getIcon()`; icon mismatches feel amateur |
| Right-click context menu with standard file ops (copy, paste, delete, rename, new file/folder) | Every OS file manager and the built-in project view has this | MEDIUM | Actions must be wired through `AnAction`; "new file" must trigger in-place rename field |
| VCS color coding on tree nodes (modified=blue, untracked=red, ignored=gray) | IntelliJ's own project view does this; users expect it everywhere | MEDIUM | Use `FileStatusManager.getStatus()` and its `FileStatus` color; must refresh on VCS events |
| Keyboard navigation (arrow keys, Enter to open, speed search) | Standard IDE UX contract; keyboard-first users will notice if missing | LOW | `JTree` speed-search built-in; Enter action must be wired |
| Back / Forward / Up navigation | File managers have this; without it users feel trapped | LOW | Already implemented; must be reliable with keyboard shortcuts |
| Drag files into editor tabs | Users drag files between tool windows constantly | MEDIUM | Current milestone work; use `TransferHandler` + `DnDNativeTarget` per project memory |
| Drag files between explorer panels | Multi-panel SSH setup requires cross-panel copy/move | MEDIUM | Requires distinguishing local vs remote targets; conflict-free DnD is the active challenge |
| Context menu: Open in Editor, Reveal in Finder/Explorer | Universal expectation from any file tool | LOW | `OpenFileDescriptor` for editor; `RevealFileAction` or native OS call for reveal |
| Staged/unstaged file list in Git panel | Any Git UI plugin shows this; the built-in commit window shows it | LOW | Already implemented |
| Commit message editor with multi-line support | Every Git client has a commit message field | LOW | Already implemented via CardLayout editor |
| Show file diff before commit (pre-commit diff) | IntelliJ's own commit window does this; users expect to review changes inline | MEDIUM | Active milestone work; must use `DiffManager.getInstance().showDiff()` with before/after content |
| Push to remote | Bare minimum Git workflow: stage, commit, push | LOW | Already implemented |
| Pull from remote | Users need to sync before working; without pull they fall back to terminal | MEDIUM | Active milestone work; requires branch tracking awareness |
| Create / checkout branch | Core Git workflow; no Git UI is complete without it | MEDIUM | Active milestone work; must handle dirty working tree gracefully (offer stash/smart checkout) |
| Refresh / reload tree on file system changes | Files created externally must appear; stale trees frustrate users | LOW | Already has glitches per PROJECT.md — this is a quality bar issue |
| Fuzzy file open (Cmd+Shift+P or equivalent) | VS Code popularized this; IntelliJ users expect quick navigation | LOW | Already implemented with frecency |

### Differentiators (Competitive Advantage)

Features that set this plugin apart from both IntelliJ's built-in project view and competing marketplace plugins.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Unified local + SSH/SFTP tree in one panel | No built-in IntelliJ feature or marketplace plugin does this without Ultimate subscription; fills a real gap for devops/backend devs | HIGH | Core value proposition; already implemented; robustness (DnD, refresh, icons) is what differentiates quality |
| Multi-panel layout (up to 5 panels) for remote files | Power users running multi-server workflows have no IDE-native alternative | HIGH | Already implemented; polish (DnD between panels, session persistence) elevates it |
| Side-by-side file diff viewer (arbitrary files, not just VCS) | IntelliJ has diff but it's buried; a plugin that surfaces "compare any two files" quickly is genuinely useful | MEDIUM | Active milestone; use `DiffManager` API; surface via context menu "Compare With..." on any two selected files |
| Git stash with named entries and apply/drop UI | IntelliJ's stash UI is minimal; a first-class stash panel (list, preview, apply, drop, name) adds real value | MEDIUM | Active milestone; stash list from `git stash list`, preview via diff |
| Git log with branch graph visualization | GitToolBox and IntelliJ log tab are powerful but not integrated into a file-explorer panel workflow | HIGH | Active milestone; branch graph is the hardest part — consider showing linear log with branch labels first |
| Inline blame in the explorer panel (not just editor) | GitToolBox provides inline blame in the editor; blame visible directly on file nodes in the explorer panel is novel | MEDIUM | Low-hanging differentiation; use `git blame --porcelain` on selected file |
| ripgrep content search with results linked to file tree | Many plugins search, few link results back to a navigable tree | MEDIUM | Already implemented; polish (highlight, filter) is the differentiator |
| Log tail viewer for remote files | No built-in equivalent for SSH files; genuinely unique for remote debugging workflows | MEDIUM | Already implemented |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem desirable but should be deliberately excluded.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Built-in terminal emulator | Users want to run commands in context | IntelliJ already has a first-class terminal (and redesigned it in 2025.2); duplicating it creates maintenance burden and will always be inferior — IntelliJ's terminal handles shell profiles, hyperlinks, multiplexing | Wire "Open Terminal Here" action that focuses IntelliJ's own terminal in the current directory |
| Real-time collaborative editing | Power-user request for pair programming | Out of scope architecturally (requires CRDT/OT), conflicts with SSH model, and JetBrains Code With Me already exists | Non-goal; document it explicitly in plugin description |
| Full Git rebase interactive UI | Some users want rebase -i | Interactive rebase requires a pseudo-terminal session; building a GUI for it in Swing is a multi-month effort with high breakage risk | Add "Rebase onto..." action that shells out and opens the terminal; let the terminal handle the interactive session |
| File permission editor (chmod) | Useful for remote files | OS-specific, SSH-path-specific, error-prone; every edge case (symlinks, ACLs, macOS vs Linux) is a support ticket | Show permissions as read-only in file details; for changing, open terminal in directory |
| Conflict resolution merge editor | Users in merge conflicts want a three-way merge UI | IntelliJ already has a best-in-class three-way merge editor built in; replacing it is impossible and unnecessary | Detect conflicts and open IntelliJ's own merge tool via `MergeRequest` API |
| Plugin settings with dozens of toggles | Feature-complete plugins accumulate settings | Settings screens become unmaintainable and confuse users; JetBrains Marketplace reviewers flag plugins with settings that don't work | Ship opinionated defaults; only expose settings that power users genuinely need (SSH profiles, shortcut overrides) |

---

## Feature Dependencies

```
[Pre-commit Diff Viewer]
    └──requires──> [Staged/Unstaged File List]  (need to know which file to diff)
    └──requires──> [DiffManager API integration]

[Git Log with Branch Graph]
    └──requires──> [Branch List / Checkout]  (branch list must be populated)
    └──enhances──> [Stash UI]  (log shows when stash was made)

[Branch Create / Checkout]
    └──requires──> [Pull from Remote]  (need remote tracking info for checkout -b)

[Side-by-Side File Diff]
    └──enhances──> [Pre-commit Diff Viewer]  (same DiffManager infrastructure)

[Drag-and-Drop Polish]
    └──requires──> [File Tree Icons + VCS Coloring]  (visual feedback during drag depends on node rendering)
    └──conflicts──> [Swing dragEnabled + IntelliJ DnDManager on same component]  (per project memory: use TransferHandler for drag-out, DnDNativeTarget for drops — never mix)

[Stash UI]
    └──requires──> [Branch Create / Checkout]  (smart checkout needs stash)

[Git Blame in Explorer]
    └──enhances──> [File Tree with VCS Coloring]  (augments same tree node)

[Reveal in OS Explorer context menu action]
    └──enhances──> [Right-click Context Menu]
```

### Dependency Notes

- **Pre-commit diff requires staged file list:** The diff is always "staged revision vs HEAD" — the file selection context from the staging panel is the trigger.
- **Branch checkout requires remote tracking info:** Creating a branch that tracks origin requires knowing remote refs; pull/fetch must succeed first.
- **DnD conflicts with mixed DnD systems:** Per MEMORY.md, `TransferHandler + dragEnabled` and `DnDManager` cannot both be registered on the same component; DnD polish depends on getting this architecture right first.
- **Git log enhances stash UI:** Showing stash entries in timeline context makes "when did I stash this?" immediately clear.

---

## MVP Definition

This is a milestone release on top of an existing plugin, not a greenfield MVP. The milestone goal is polish + depth in four areas.

### Launch With (milestone v-next)

- [ ] Reliable DnD between explorer panels and IDE project view — the DnD architecture fix is blocking; users report drag as broken, which undermines trust in the whole plugin
- [ ] File tree rendering polish (icons, VCS colors, expand/collapse glitches, refresh on external changes) — table stakes; without this the plugin feels unfinished next to built-in project view
- [ ] Context menu completeness (all standard file ops wired, "open in editor", "reveal in OS") — table stakes; broken right-click actions are immediate 1-star reviews
- [ ] Pre-commit diff viewer in Git panel — most-requested Git panel feature; without it users stay on IntelliJ's built-in commit window
- [ ] Pull from remote — Git workflow is incomplete without pull; currently users must terminal-out to pull

### Add After Validation (v1.x)

- [ ] Branch create / checkout with smart checkout (stash on dirty tree) — trigger: user feedback that they still terminal-out to branch
- [ ] Stash UI (list, create, apply, drop) — trigger: user feedback; depends on branch operations being solid first
- [ ] Side-by-side diff for arbitrary files (not just Git diffs) — trigger: user requests "compare two files"; builds on DiffManager infrastructure already used for pre-commit diff

### Future Consideration (v2+)

- [ ] Git log with branch graph — HIGH complexity; linear log with branch labels is v1.x; full DAG graph is v2+
- [ ] Inline blame on explorer tree nodes — MEDIUM complexity but low urgency; editor blame (via GitToolBox) already covers most users
- [ ] Conventional commit message assistance (branch-name autocomplete, commit type picker) — nice-to-have; not a core value driver for this plugin's audience

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| DnD reliability fix | HIGH | MEDIUM | P1 |
| File tree icon + VCS color polish | HIGH | LOW | P1 |
| Context menu completeness | HIGH | LOW | P1 |
| Pre-commit diff viewer | HIGH | MEDIUM | P1 |
| Pull from remote | HIGH | LOW | P1 |
| Branch create / checkout | HIGH | MEDIUM | P2 |
| Stash UI | MEDIUM | MEDIUM | P2 |
| Side-by-side arbitrary file diff | MEDIUM | LOW (reuses DiffManager) | P2 |
| Git log with linear branch labels | MEDIUM | MEDIUM | P2 |
| Git log with full branch graph DAG | MEDIUM | HIGH | P3 |
| Inline blame on explorer nodes | LOW | MEDIUM | P3 |
| Conventional commit assistance | LOW | LOW | P3 |

---

## Competitor Feature Analysis

| Feature | IntelliJ Built-in Project View | GitToolBox | XFTP / FTP-SFTP Plugin | Our Plugin |
|---------|-------------------------------|------------|------------------------|-----------|
| Local file tree | Yes, excellent | No | No | Yes, needs icon/refresh polish |
| SSH/SFTP remote tree | Ultimate only | No | Yes (SFTP only, no Git) | Yes, unique combination |
| Multi-panel layout | No | No | No | Yes (up to 5 panels) |
| VCS color coding in tree | Yes | Augments (ahead/behind counts) | No | Needs polish |
| Drag-and-drop | Yes (within project) | No | Yes (upload/download) | In progress, conflict-free arch |
| Pre-commit diff | Yes (built-in commit window) | No | No | In progress |
| Branch operations | Yes (widget popup) | Enhances (recent branches) | No | In progress |
| Stash UI | Minimal (menu items) | No | No | In progress |
| Git log | Yes (Log tab, powerful) | Enhances (status indicators) | No | In progress (linear first) |
| Fuzzy quick open | Yes (Shift+Shift) | No | No | Yes, with frecency ranking |
| Content search | Yes (Find in Files) | No | No | Yes, ripgrep-powered |
| Log tail for remote files | No | No | No | Yes, unique |
| Bookmarks panel | Yes (built-in) | No | No | Yes |
| Git blame | Yes (editor gutter) | Yes (inline editor blame) | No | Yes (existing) |

---

## Sources

- [JetBrains Marketplace](https://plugins.jetbrains.com/) — plugin discovery
- [GitToolBox documentation](https://gittoolbox.lukasz-zielinski.com/docs/) — feature list, freemium model (MEDIUM confidence, official plugin site)
- [IntelliJ IDEA Project Tool Window docs](https://www.jetbrains.com/help/idea/project-tool-window.html) — built-in feature baseline (HIGH confidence, official docs)
- [IntelliJ IDEA Git Log Tab docs](https://www.jetbrains.com/help/idea/log-tab.html) — log feature set (HIGH confidence, official docs)
- [IntelliJ IDEA Shelve/Stash docs](https://www.jetbrains.com/help/idea/shelving-and-unshelving-changes.html) — stash UI patterns (HIGH confidence, official docs)
- [IntelliJ IDEA Diff Viewer docs](https://www.jetbrains.com/help/idea/differences-viewer.html) — diff API patterns (HIGH confidence, official docs)
- [XFTP plugin](https://plugins.jetbrains.com/plugin/16590-xftp) — SSH/SFTP competitor (MEDIUM confidence, marketplace listing)
- [Git Machete plugin](https://plugins.jetbrains.com/plugin/14221-git-machete) — branch visualization competitor (MEDIUM confidence, marketplace listing)
- [JetBrains Platform Blog Q4 2025](https://blog.jetbrains.com/platform/2026/01/busy-plugin-developers-newsletter-q4-2025/) — current marketplace context (MEDIUM confidence)
- Project MEMORY.md — DnD architecture constraints (HIGH confidence, first-hand project knowledge)

---

*Feature research for: IntelliJ plugin — unified file explorer + Git panel*
*Researched: 2026-02-28*
