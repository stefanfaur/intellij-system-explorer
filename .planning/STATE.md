---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: unknown
last_updated: "2026-02-28T12:29:38.813Z"
progress:
  total_phases: 3
  completed_phases: 1
  total_plans: 8
  completed_plans: 5
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-28)

**Core value:** A single explorer pane where local and remote (SSH) files look and behave identically — browse, edit, and manage them without leaving IntelliJ.
**Current focus:** Phase 1 — GitBackend Extensions + Pull

## Current Position

Phase: 7 of 7 (Polish QuickOpen Smart Search)
Plan: 2 of ? in current phase
Status: Plan 07-02 complete
Last activity: 2026-02-28 — Completed plan 07-02 (panelScope coroutine scope fix, dual-debounce, content match line navigation)

Progress: [███░░░░░░░] 25%

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
| Phase 07-polish-quickopen-smart-search P01 | 2 | 3 tasks | 3 files |
| Phase 02-pre-commit-diff-viewer P01 | 5 | 2 tasks | 3 files |
| Phase 07 P02 | 4 | 3 tasks | 1 files |

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
- [Phase 07-01]: validateExtraFlags allowlist: --hidden, --no-ignore, --follow exact + --type, --glob prefix; unknown flags silently dropped
- [Phase 07-01]: popup changed from lateinit to JBPopup? = null with ?. safe-call on all 4 callsites
- [Phase 07-01]: Nucleo native library committed to src/main/resources/natives/darwin-aarch64/ for JAR packaging without requiring Rust toolchain
- [Phase 02-01]: LocalGitBackend uses git show HEAD:path; RemoteGitBackend uses git show :path (index proxy); null return is the contract for untracked/new files
- [Phase 07-02]: panelScope uses SupervisorJob so child coroutine failures do not cancel sibling coroutines
- [Phase 07-02]: 50ms debounce for fuzzy search; 300ms for rg content search
- [Phase 07-02]: OpenFileDescriptor with lineNumber - 1 (0-based) for line-precise content match navigation

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
Stopped at: Completed 07-02-PLAN.md — coroutine scope leak fix, 50ms/300ms dual-debounce, content match line-precise navigation
Resume file: None
