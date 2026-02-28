# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-28)

**Core value:** A single explorer pane where local and remote (SSH) files look and behave identically — browse, edit, and manage them without leaving IntelliJ.
**Current focus:** Phase 1 — GitBackend Extensions + Pull

## Current Position

Phase: 1 of 6 (GitBackend Extensions + Pull)
Plan: 1 of 2 in current phase
Status: In progress
Last activity: 2026-02-28 — Completed plan 01-01 (GitBackend interface extensions + implementations)

Progress: [█░░░░░░░░░] 8%

## Performance Metrics

**Velocity:**
- Total plans completed: 1
- Average duration: 2 min
- Total execution time: 2 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-gitbackend-extensions-pull | 1 | 2 min | 2 min |

**Recent Trend:**
- Last 5 plans: 01-01 (2 min)
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
Stopped at: Completed 01-01-PLAN.md — GitBackend interface extensions and implementations
Resume file: None
