---
phase: 01-gitbackend-extensions-pull
plan: 01
subsystem: api
tags: [kotlin, git, interface, backend, branch-management, stash]

# Dependency graph
requires: []
provides:
  - GitBackend interface extended with pull(), listBranches(), checkoutBranch(), createBranch(), deleteBranch(), stash(), stashList(), stashPop()
  - BranchInfo and StashEntry data classes in GitBackend.kt
  - Full implementations in LocalGitBackend and RemoteGitBackend
  - parseBranchLine and parseStashLine internal helpers with unit tests
affects:
  - 01-02 (Pull toolbar depends on pull() on the interface)
  - Phase 5 (Branch Management depends on listBranches/checkoutBranch/createBranch/deleteBranch)
  - Phase 6 (Stash UI depends on stash/stashList/stashPop)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "executor.executeBlocking(repoPath, args = arrayOf(...)) for all Git CLI calls in LocalGitBackend"
    - "LOG.warn on failure for list methods in RemoteGitBackend; plain delegation for command methods"
    - "Internal package-level parsing functions for git output parsing, unit tested separately from IntelliJ platform"

key-files:
  created:
    - src/test/kotlin/ro/faur/explorer/unit/GitBackendExtensionsTest.kt
  modified:
    - src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt
    - src/test/kotlin/ro/faur/explorer/light/GitRepositoryRegistryTest.kt

key-decisions:
  - "parseBranchLine and parseStashLine are package-internal functions in LocalGitBackend.kt, reused by RemoteGitBackend via same-package visibility without imports"
  - "RemoteGitBackend uses LOG.warn for list methods (listBranches, stashList) on failure but not for command methods (pull, checkoutBranch, etc.) to match existing remote pattern"
  - "stash() uses 'stash push' subcommand with --include-untracked flag and optional -m for message"

patterns-established:
  - "New git commands: add abstract method to GitBackend, implement in both backends following existing executor.executeBlocking pattern"
  - "Parsing helpers: package-internal functions at file bottom, unit tested with JUnit5 (no IntelliJ platform needed)"

requirements-completed: [GIT-01, GIT-03]

# Metrics
duration: 2min
completed: 2026-02-28
---

# Phase 1 Plan 01: GitBackend Extensions Summary

**GitBackend interface extended with 8 new abstract git operations (pull, branch CRUD, stash CRUD) plus BranchInfo/StashEntry data classes, implemented in both LocalGitBackend and RemoteGitBackend with parsing helpers and unit tests**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-28T10:49:11Z
- **Completed:** 2026-02-28T10:52:07Z
- **Tasks:** 2
- **Files modified:** 4 (+ 1 created)

## Accomplishments
- Extended GitBackend interface with 8 new abstract methods and 2 new data classes (BranchInfo, StashEntry)
- Implemented all 8 methods in LocalGitBackend using executor.executeBlocking pattern
- Implemented all 8 methods in RemoteGitBackend with LOG.warn on list method failures
- Added parseBranchLine and parseStashLine internal helpers with 10 unit tests covering all edge cases

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend GitBackend interface with new method signatures and data classes** - `e8f09c9` (feat)
2. **Task 2: Implement all new methods in both backends, add parsing helpers with tests** - `29fda1d` (feat)

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt` - Added BranchInfo, StashEntry data classes and 8 new abstract method signatures
- `src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt` - Implemented all 8 new methods + parseBranchLine/parseStashLine helpers
- `src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt` - Implemented all 8 new methods with LOG.warn on list failures
- `src/test/kotlin/ro/faur/explorer/unit/GitBackendExtensionsTest.kt` - 10 unit tests for parseBranchLine and parseStashLine
- `src/test/kotlin/ro/faur/explorer/light/GitRepositoryRegistryTest.kt` - Added missing stub implementations for new interface methods

## Decisions Made
- parseBranchLine and parseStashLine are package-internal functions in LocalGitBackend.kt, visible to RemoteGitBackend via same-package access (no import needed)
- RemoteGitBackend uses LOG.warn for list methods (listBranches, stashList) on failure, matching existing getLog() pattern; command methods (pull, checkoutBranch, etc.) return result directly
- stash() uses `git stash push` with `--include-untracked` and optional `-m` flags

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed GitRepositoryRegistryTest anonymous GitBackend object missing new abstract methods**
- **Found during:** Task 1 (after extending the interface)
- **Issue:** GitRepositoryRegistryTest.kt creates an anonymous `object : GitBackend` that didn't implement the 8 new abstract methods, causing compile failure
- **Fix:** Added stub implementations for all 8 new methods returning empty results
- **Files modified:** src/test/kotlin/ro/faur/explorer/light/GitRepositoryRegistryTest.kt
- **Verification:** ./gradlew compileKotlin exits 0
- **Committed in:** e8f09c9 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug)
**Impact on plan:** Necessary fix for compilation. No scope creep.

## Issues Encountered
None beyond the auto-fixed compile error in the test stub.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- GitBackend interface is fully extended — plan 01-02 (Pull toolbar) can now reference pull() on the interface
- Phase 5 (Branch Management) and Phase 6 (Stash UI) are unblocked: all required interface methods exist
- parseBranchLine and parseStashLine helpers are tested and ready for use by UI layers

---
*Phase: 01-gitbackend-extensions-pull*
*Completed: 2026-02-28*
