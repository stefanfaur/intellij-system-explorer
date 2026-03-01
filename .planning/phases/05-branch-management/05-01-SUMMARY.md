---
phase: 05-branch-management
plan: 01
subsystem: git
tags: [kotlin, git, branch-management, validation, tdd, junit5]

# Dependency graph
requires: []
provides:
  - "BranchNameValidator pure-Kotlin object with 22+ test cases covering all git ref-name rules"
  - "resetHard() method on GitBackend interface + LocalGitBackend + RemoteGitBackend"
affects:
  - "05-branch-management (CreateBranchDialog uses BranchNameValidator.validate())"
  - "05-branch-management (GitPanelComponent.doDiscardAndCheckout() calls backend.resetHard())"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "BranchNameValidator as a Kotlin object (singleton) with no IntelliJ dependencies — pure JVM"
    - "GitBackend interface extension: one-liner override pattern for simple git commands"
    - "TDD: RED commit (failing test) then GREEN commit (implementation + compilation fixes)"

key-files:
  created:
    - src/main/kotlin/ro/faur/explorer/gitpanel/BranchNameValidator.kt
    - src/test/kotlin/ro/faur/explorer/unit/BranchNameValidatorTest.kt
  modified:
    - src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt
    - src/test/kotlin/ro/faur/explorer/light/GitRepositoryRegistryTest.kt
    - src/test/kotlin/ro/faur/explorer/unit/LuceneContentSearchTest.kt
    - src/test/kotlin/ro/faur/explorer/unit/LuceneIndexManagerPersistenceTest.kt

key-decisions:
  - "BranchNameValidator checks in order: blank, leading/trailing dots, leading/trailing slashes, double slash, double dot, invalid chars, @{, lone @, .lock suffix — first match wins"
  - "INVALID_CHARS regex covers: whitespace, tilde, caret, colon, question mark, asterisk, open bracket, backslash — exactly the git check-ref-format forbidden set"
  - "resetHard() uses one-liner override pattern (same as checkoutBranch) with args reset --hard HEAD"
  - "Pre-existing assertDoesNotThrow(message, lambda) misuse in LuceneContentSearchTest and LuceneIndexManagerPersistenceTest fixed as Rule 3 (blocked compileTestKotlin)"

patterns-established:
  - "Pure-Kotlin validators: no IntelliJ imports, single object, validate() returns null|String"

requirements-completed: [BRANCH-03, BRANCH-04, BRANCH-05]

# Metrics
duration: 6min
completed: 2026-03-01
---

# Phase 05 Plan 01: BranchNameValidator + resetHard() Summary

**Pure-Kotlin BranchNameValidator with 24 JUnit5 tests covering all git ref-name rules, plus resetHard() added to GitBackend interface and both implementations**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-01T12:50:03Z
- **Completed:** 2026-03-01T12:55:45Z
- **Tasks:** 3 (RED, GREEN, REFACTOR)
- **Files modified:** 8

## Accomplishments
- Created `BranchNameValidator` as a pure-Kotlin object with zero IntelliJ dependencies, implementing all `git check-ref-format` rules
- All 24 BranchNameValidatorTest cases pass (empty/blank, invalid chars, positional rules, .lock suffix, valid names)
- Added `resetHard()` to `GitBackend` interface and both `LocalGitBackend`/`RemoteGitBackend` using the project's one-liner override pattern

## Task Commits

Each task was committed atomically:

1. **Task RED: Failing BranchNameValidatorTest** - `ec589e0` (test)
2. **Task GREEN: BranchNameValidator + resetHard() implementations** - `aa463d0` (feat)

_Note: REFACTOR phase required no code changes — messages were already user-readable._

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/gitpanel/BranchNameValidator.kt` - Pure-Kotlin validator; no IntelliJ imports
- `src/test/kotlin/ro/faur/explorer/unit/BranchNameValidatorTest.kt` - 24 JUnit5 test cases
- `src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt` - Added `fun resetHard(): GitCommandResult`
- `src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt` - `override fun resetHard()` (one-liner)
- `src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt` - `override fun resetHard()` (one-liner)
- `src/test/kotlin/ro/faur/explorer/light/GitRepositoryRegistryTest.kt` - Added missing `getFileAtRevision` and `resetHard` to anonymous GitBackend stub
- `src/test/kotlin/ro/faur/explorer/unit/LuceneContentSearchTest.kt` - Fixed `assertDoesNotThrow` call signature
- `src/test/kotlin/ro/faur/explorer/unit/LuceneIndexManagerPersistenceTest.kt` - Fixed `assertDoesNotThrow` call signature

## Decisions Made
- BranchNameValidator checks in order: blank, leading/trailing dots, leading/trailing slashes, double slash, double dot, invalid chars, @{, lone @, .lock suffix — first match wins
- INVALID_CHARS regex covers: whitespace, tilde, caret, colon, question mark, asterisk, open bracket, backslash
- `resetHard()` uses the one-liner override pattern with `args = arrayOf("reset", "--hard", "HEAD")`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added `resetHard()` and `getFileAtRevision()` to anonymous GitBackend stub in GitRepositoryRegistryTest**
- **Found during:** GREEN phase (compileTestKotlin)
- **Issue:** Adding `resetHard()` to the interface caused `GitRepositoryRegistryTest.kt`'s anonymous stub to fail compilation (missing abstract member). The stub was also already missing `getFileAtRevision` (pre-existing gap).
- **Fix:** Added both `override fun getFileAtRevision(...): ByteArray? = null` and `override fun resetHard(): GitCommandResult = GitCommandResult(0, "", "")` to the anonymous class.
- **Files modified:** `src/test/kotlin/ro/faur/explorer/light/GitRepositoryRegistryTest.kt`
- **Committed in:** `aa463d0` (GREEN phase commit)

**2. [Rule 3 - Blocking] Fixed `assertDoesNotThrow` call signature in two pre-existing untracked test files**
- **Found during:** GREEN phase (compileTestKotlin)
- **Issue:** `LuceneContentSearchTest.kt` and `LuceneIndexManagerPersistenceTest.kt` used `assertDoesNotThrow("message") { ... }` — an invalid JUnit5 Kotlin call order that caused compile errors, blocking `compileTestKotlin` entirely and preventing BranchNameValidatorTest from running.
- **Fix:** Changed `assertDoesNotThrow("message") { ... }` to `assertDoesNotThrow { ... }` (no message) in LuceneContentSearchTest; changed to `assertDoesNotThrow<LuceneIndexManager> { ... }` in LuceneIndexManagerPersistenceTest.
- **Files modified:** `src/test/kotlin/ro/faur/explorer/unit/LuceneContentSearchTest.kt`, `src/test/kotlin/ro/faur/explorer/unit/LuceneIndexManagerPersistenceTest.kt`
- **Committed in:** `aa463d0` (GREEN phase commit)

---

**Total deviations:** 2 auto-fixed (1 Rule 1 bug, 1 Rule 3 blocking)
**Impact on plan:** Both auto-fixes required for test compilation and correctness. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviations above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `BranchNameValidator.validate()` is ready for use in `CreateBranchDialog` (plan 05-02)
- `backend.resetHard()` is ready for use in `GitPanelComponent.doDiscardAndCheckout()` (plan 05-02 or 05-03)
- Both implementations compile cleanly; 24 unit tests pass

---
*Phase: 05-branch-management*
*Completed: 2026-03-01*
