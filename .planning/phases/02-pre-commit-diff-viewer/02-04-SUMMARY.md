---
phase: 02-pre-commit-diff-viewer
plan: 04
subsystem: ui
tags: [kotlin, git, diff, intellij-plugin, history-mode]

# Dependency graph
requires:
  - phase: 02-pre-commit-diff-viewer
    provides: InlineDiffPanel, Show Diff button, CardLayout slot wiring (02-02)
provides:
  - getFileAtRevision(hash, path) primitive on GitBackend interface and both implementations
  - History-mode Show Diff: COMMIT^:path vs COMMIT:path using getFileAtRevision
  - onFileSelected guard: no inline diff load when selectedCommitHash != null (history mode)
affects:
  - Future phases using GitBackend — interface is now extended with getFileAtRevision

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "getFileAtRevision uses git show HASH:path — same executor pattern as getHeadContent"
    - "buildDiffRequest accepts leftLabel/rightLabel with defaults to keep all existing call-sites unchanged"
    - "Lambda labeled return in Kotlin: assign explicit label (fileSelectedLambda@) when property-name label would be invalid"

key-files:
  created: []
  modified:
    - src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt

key-decisions:
  - "Used explicit lambda label (fileSelectedLambda@) for early return in onFileSelected — property-name labels are invalid in Kotlin"
  - "buildDiffRequest extended with leftLabel/rightLabel default params — all existing staging-mode call-sites unchanged"
  - "History-mode uses COMMIT^ (first parent) as before-state — standard convention, returns null for added files correctly"

patterns-established:
  - "GitBackend interface: additive extension with getFileAtRevision for commit-range diff support"

requirements-completed: [DIFF-01, DIFF-02, DIFF-03, DIFF-05]

# Metrics
duration: 2min
completed: 2026-02-28
---

# Phase 2 Plan 04: Gap Closure — getFileAtRevision and History-mode Diff Summary

**getFileAtRevision(hash, path) on GitBackend layer + history-mode Show Diff (COMMIT^:path vs COMMIT:path) + onFileSelected guard that blocks inline diff load in history mode**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-28T12:54:37Z
- **Completed:** 2026-02-28T12:56:17Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Added `getFileAtRevision(hash: String, path: String): ByteArray?` to GitBackend interface with implementations in LocalGitBackend (git show HASH:path) and RemoteGitBackend (same pattern)
- Split `doShowFullDiff()` into history-mode branch (getFileAtRevision with COMMIT^ vs COMMIT labels) and staging-mode branch (existing HEAD vs working-tree behaviour)
- Added `onFileSelected` guard that returns immediately when `selectedCommitHash != null`, keeping CommitDetailsPanel visible in history mode

## Task Commits

Each task was committed atomically:

1. **Task 1: Add getFileAtRevision to GitBackend interface + both implementations** - `5e540d5` (feat)
2. **Task 2: History-mode branch in doShowFullDiff() + onFileSelected guard** - `d3ee347` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt` - Added `getFileAtRevision` to interface after `getHeadContent`
- `src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt` - Override `getFileAtRevision` using `git show $hash:$path` via local executor
- `src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt` - Override `getFileAtRevision` using `git show $hash:$path` via remote executor
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt` - onFileSelected guard + doShowFullDiff split + buildDiffRequest label params

## Decisions Made
- Used explicit lambda label `fileSelectedLambda@` for early return in `onFileSelected` because Kotlin does not create automatic labels from property names — `return@onFileSelected` was an unresolved label at compile time
- `buildDiffRequest` extended with `leftLabel`/`rightLabel` default parameters (`"HEAD"` and `"Working Tree"`) so the existing `loadInlineDiff` call-site needs no changes
- History-mode uses `$commitHash^` (first parent) as the before-state — this is the same convention IntelliJ's built-in Git viewer uses; for ADDED files, `getFileAtRevision` returns null (file did not exist in parent), which correctly renders as an all-added diff via `createEmpty()` on the left side

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Replaced invalid `return@onFileSelected` label with explicit lambda label**
- **Found during:** Task 2 (History-mode branch in doShowFullDiff + onFileSelected guard)
- **Issue:** Plan specified `return@onFileSelected` but Kotlin does not generate labels from property names for lambda assignments — compiler error: "Unresolved label"
- **Fix:** Labeled the lambda assignment as `fileSelectedLambda@{ ... }` and used `return@fileSelectedLambda`
- **Files modified:** `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt`
- **Verification:** `./gradlew compileKotlin` passes, behaviour identical to plan intent
- **Committed in:** d3ee347 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug in plan's Kotlin syntax)
**Impact on plan:** Functionally identical to plan specification — only the label name differs.

## Issues Encountered
- `return@onFileSelected` is not a valid Kotlin label for property-assigned lambdas. Resolved with explicit label annotation on the lambda.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All gaps from 02-VERIFICATION.md are now closed: `getFileAtRevision` exists on the backend, history-mode diff shows correct commit range, file selection in history mode no longer triggers inline diff
- Phase 02 pre-commit diff viewer feature set is complete
- Ready for Phase 3+ (branch operations, stash management, etc.)

---
*Phase: 02-pre-commit-diff-viewer*
*Completed: 2026-02-28*
