---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: unknown
last_updated: "2026-03-01T13:01:41.676Z"
progress:
  total_phases: 7
  completed_phases: 6
  total_plans: 23
  completed_plans: 22
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-28)

**Core value:** A single explorer pane where local and remote (SSH) files look and behave identically — browse, edit, and manage them without leaving IntelliJ.
**Current focus:** Phase 1 — GitBackend Extensions + Pull

## Current Position

Phase: 3 of 8 (Drag-and-Drop Polish)
Plan: 2 of 3 in current phase
Status: Plan 03-02 complete — ghost drag image, conflict pre-check dialogs, balloon notifications for DnD errors delivered
Last activity: 2026-02-28 — Completed plan 03-02 (buildGhostImage in FileTreeTransferHandler, conflict pre-check in both DnD paths, Explorer.DnD notification group)

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
| Phase 02-pre-commit-diff-viewer P03 | 3 | 2 tasks | 3 files |
| Phase 07-polish-quickopen-smart-search P03 | 3 | 4 tasks | 3 files |
| Phase 02-pre-commit-diff-viewer P02 | 11 | 3 tasks | 3 files |
| Phase 02-pre-commit-diff-viewer P04 | 2 | 2 tasks | 4 files |
| Phase 08 P02 | 2 | 2 tasks | 2 files |
| Phase 08-01 P01 | 15 | 2 tasks | 3 files |
| Phase 08 P04 | 7 | 2 tasks | 3 files |
| Phase 08 P06 | 2 | 1 tasks | 1 files |
| Phase 03 P01 | 4 | 2 tasks | 2 files |
| Phase 03 P02 | 8 | 2 tasks | 3 files |
| Phase 04 P01 | 9 | 2 tasks | 1 files |
| Phase 04 P02 | 5 | 1 tasks | 1 files |
| Phase 04 P02 | 5 | 2 tasks | 1 files |
| Phase 05-branch-management P01 | 6 | 3 tasks | 8 files |
| Phase 05-branch-management P03 | 3m | 2 tasks | 2 files |

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
- [Phase 02-03]: CompareWithAction uses isEnabledAndVisible (hides action when count != 2, not just disables); tree row order = left/right assignment
- [Phase 07-03]: Status bar row added using GridLayout(2,1) to avoid touching existing truncation/count/hint layout
- [Phase 07-03]: Speed dial uses FrecencyStore.recencyScore() exclusively — bookmark-first fallback removed
- [Phase 07-03]: typeGroup comparator is post-scoring display ordering only — frecency blend in scoring unchanged
- [Phase 02-02]: CardLayout used for rightSlot to exclusively show commitDetailsPanel or inlineDiffPanel
- [Phase 02-02]: DiffContentFactory.createFromBytes is the correct API (not createDocumentFromBytes which does not exist in IJ 2024.3)
- [Phase 02-02]: History-mode Show Diff deferred to HEAD vs working-tree; exact commit-range diff needs getFileAtRevision() on GitBackend
- [Phase 02-pre-commit-diff-viewer]: 02-04: Used explicit lambda label (fileSelectedLambda@) for early return in onFileSelected — Kotlin property-name labels are invalid for assigned lambdas
- [Phase 02-pre-commit-diff-viewer]: 02-04: buildDiffRequest extended with leftLabel/rightLabel default params — all existing call-sites unchanged
- [Phase 08-02]: Four Lucene fields appended at end of State data class; PersistentStateComponent serializes with defaults for existing users, no migration needed
- [Phase 08-02]: Clear Index button uses PathManager.getSystemPath() + caches/explorer-index path — consistent with where index plans will write data
- [Phase 08]: NIOFSDirectory used instead of FSDirectory.open() to prevent MMapDirectory file handle leaks on plugin unload
- [Phase 08]: indexDirForRoot uses PathManager.getSystemPath() so Lucene index survives IDE restarts (not getPluginTempPath())
- [Phase 08]: searchPaths blank query uses MatchAllDocsQuery — WildcardQuery on empty string returns no results
- [Phase 08-04]: candidatePool changed to var to allow runtime replacement when switching to LuceneEnumerator on hybrid threshold crossing
- [Phase 08-04]: IndexRegistry is a Kotlin object singleton with independent registryScope not cancelled on panel dispose
- [Phase 08-06]: indexModeChipLabel is separate from modeChipLabel — placed in bottomRow EAST for index state; three states Indexed/Indexing.../Live from IndexRegistry
- [Phase 03]: RenderingUtil is at com.intellij.ui.render.RenderingUtil (not com.intellij.ui.RenderingUtil) in IJ 2024.3+
- [Phase 03]: Source-parent refresh only runs when isMove=true — copy operations do not need to invalidate the source directory
- [Phase 03]: AllIcons.Nodes.MultipleFiles does not exist in IJ 2024.3 — AllIcons.FileTypes.Any_type used as multi-file ghost icon fallback
- [Phase 03]: Messages.showYesNoDialog in FileTreeTransferHandler uses null Project (no project reference available in TransferHandler)
- [Phase 04-01]: FileStatusManager.addFileStatusListener used instead of FILE_STATUS_TOPIC messageBus (no such constant in IJ 2024.3)
- [Phase 04-01]: DumbService.DUMB_MODE is correct topic constant (not DUMB_MODE_LISTENER)
- [Phase 04-01]: directoryStatusCache uses Optional<Color> as ConcurrentHashMap cannot store null values
- [Phase 04]: All menu items always added to menu; isEnabled set explicitly — no conditional item add/remove (per CTX-04)
- [Phase 04]: All menu items always added to menu; isEnabled set explicitly — no conditional item add/remove (per CTX-04)
- [Phase 04]: createLocalShellWidget deprecation warning accepted — API still functional in IJ 2024.3
- [Phase 05-branch-management]: BranchNameValidator checks: blank, leading/trailing dots, slashes, double slash, double dot, invalid chars, @{, lone @, .lock suffix — first match wins
- [Phase 05-branch-management]: resetHard() uses one-liner override pattern with args reset --hard HEAD, same as checkoutBranch pattern
- [Phase 05-branch-management]: ActionUpdateThread.EDT used for Create Branch/Delete Branch actions — they read cachedBranches which is EDT-only state
- [Phase 05-branch-management]: Delete Branch attempt-and-detect: call deleteBranch(force=false) first, check stderr for 'not fully merged', show separate force-delete dialog

### Roadmap Evolution

- Phase 7 added: Polish, improve quick open / smart search
- Phase 8 added: Intelligent Indexed Local Filesystem Search with Apache Lucene and Hybrid Mode

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 5 planning: Dirty-tree smart checkout UX needs validation against IntelliJ's own checkout flow before designing the dialog
- Phase 6 planning: git stash --include-untracked behavior on SSH/remote repos needs live validation before planning
- General: 2025.3 threading model (Write-Intent lock removal from AWT input events) may affect existing LocalGitBackend callsites — validate against 2025.3 EAP before release

## Session Continuity

Last session: 2026-03-01
Stopped at: Completed 04-02-PLAN.md
Resume file: None
