# System Explorer — IntelliJ Plugin

## What This Is

A file browser plugin for IntelliJ-based IDEs that treats local and SSH/SFTP remote files as first-class citizens in a unified explorer view. It combines a file tree, fuzzy Quick Open, a Git panel with stage/commit/push flow, and a log tail viewer — all inside the IDE without switching to a terminal. Target audience: developers who frequently work across local and remote environments and want a polished, marketplace-ready alternative to the built-in project view.

## Core Value

A single explorer pane where local and remote (SSH) files look and behave identically — browse, edit, and manage them without leaving IntelliJ.

## Requirements

### Validated

- ✓ Local file browser with back/forward/up navigation — existing
- ✓ SSH/SFTP remote file browser with multi-panel support (up to 5 panels) — existing
- ✓ Fuzzy Quick Open popup (`Cmd+Shift+P`) with frecency ranking — existing
- ✓ Git panel with staged/unstaged file listing, commit editor, push — existing
- ✓ Glob filter presets for file tree — existing
- ✓ Log tail viewer for remote files — existing
- ✓ Bookmarks panel — existing
- ✓ Content search (ripgrep integration) — existing
- ✓ Git blame display — existing
- ✓ Keyboard shortcuts (Alt+E, Option+Shift+1–5, etc.) — existing

### Active

- [ ] File explorer DnD (drag and drop) — reliable, conflict-free
- [ ] File tree rendering polish (icons, refresh, expand/collapse glitches)
- [ ] Context menu completeness (right-click actions wired up correctly)
- [ ] Diff viewer — pre-commit diffs in Git panel + general side-by-side file comparison
- [ ] Deeper Git operations (branching, stash, pull, log)

### Out of Scope

- Mobile / non-IntelliJ IDE support — JetBrains platform only
- Built-in terminal emulator — use IntelliJ's existing terminal
- Real-time collaborative editing — out of scope for v1 marketplace release

## Context

- Kotlin + IntelliJ Platform SDK; plugin published to JetBrains Marketplace
- DnD architecture: TransferHandler + dragEnabled for drag-out; DnDNativeTarget for receiving drops (IntelliJ and Swing DnD systems are separate — mixing them causes conflicts)
- Git operations use local git CLI execution and SSH forwarding
- UI: Swing/IntelliJ components (ColoredTreeCellRenderer, CardLayout commit editor, CheckboxTree for staging)
- CI/CD: GitHub Actions for build and publish workflows

## Constraints

- **Tech stack**: IntelliJ Platform SDK (Kotlin) — no framework changes
- **Compatibility**: Must support IntelliJ IDEA 2023.x+ and recent JetBrains IDE versions
- **Marketplace**: Plugin must meet JetBrains Marketplace quality bar (no crashes, proper error handling, icons, descriptions)
- **Performance**: File tree and Quick Open must remain responsive on large directories

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| TransferHandler for drag-out, DnDNativeTarget for drops | IntelliJ and Swing DnD systems conflict when mixed | — Pending |
| CardLayout for commit editor | Allows switching between collapsed/expanded without re-rendering | — Pending |
| ripgrep for content search | Much faster than pure-Java grep on large codebases | — Pending |

---
*Last updated: 2026-02-28 after initialization*
