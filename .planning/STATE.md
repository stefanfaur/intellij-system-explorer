# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-28)

**Core value:** A single explorer pane where local and remote (SSH) files look and behave identically — browse, edit, and manage them without leaving IntelliJ.
**Current focus:** Phase 1 — GitBackend Extensions + Pull

## Current Position

Phase: 1 of 6 (GitBackend Extensions + Pull)
Plan: 2 of 2 in current phase
Status: Phase complete
Last activity: 2026-02-28 — Completed plan 01-02 (Pull toolbar button with Task.Backgroundable)

Progress: [██░░░░░░░░] 17%

## Performance Metrics

**Velocity:**
- Total plans completed: 2
- Average duration: 3 min
- Total execution time: 6 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-gitbackend-extensions-pull | 2 | 6 min | 3 min |

**Recent Trend:**
- Last 5 plans: 01-01 (2 min), 01-02 (4 min)
- Trend: -

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Architecture: TransferHandler for drag-out, DnDNativeTarget for drops — do not mix the two DnD systems
- Architecture: Use com.intellij.diff.* exclusively (not deprecated com.intellij.openapi.diff.*)
- Architecture: All Git CLI calls must run off EDT via executeOnPooledThread or Task.Backgroundable
- 01-01: parseBranchLine/parseStashLine are package-internal functions in LocalGitBackend.kt, visible to RemoteGitBackend via same-package access
- 01-01: RemoteGitBackend uses LOG.warn for list methods on failure, returns result directly for command methods (matching existing getLog() pattern)
- 01-02: Pull uses Task.Backgroundable (not bare executeOnPooledThread) for IDE status bar progress during network git ops
- 01-02: pullInProgress = false placed as first invokeLater statement (before disposed check) to prevent permanent button disable on panel disposal mid-pull
- 01-02: canBeCancelled=false in Task.Backgroundable — git CLI has no cooperative cancellation, suppress fake Cancel UI

### Roadmap Evolution

- Phase 7 added: Polish, improve quick open / smart search

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 5 planning: Dirty-tree smart checkout UX needs validation against IntelliJ's own checkout flow before designing the dialog
- Phase 6 planning: git stash --include-untracked behavior on SSH/remote repos needs live validation before planning
- General: 2025.3 threading model (Write-Intent lock removal from AWT input events) may affect existing LocalGitBackend callsites — validate against 2025.3 EAP before release

## Session Continuity

Last session: 2026-02-28
Stopped at: Completed 01-02-PLAN.md — Pull toolbar button with Task.Backgroundable and three result paths
Resume file: None
