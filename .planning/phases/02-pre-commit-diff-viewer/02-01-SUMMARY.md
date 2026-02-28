---
phase: 02-pre-commit-diff-viewer
plan: 01
subsystem: api
tags: [git, kotlin, gitbackend, diff]

# Dependency graph
requires: []
provides:
  - getHeadContent(path: String): ByteArray? on GitBackend interface
  - LocalGitBackend implementation using git show HEAD:path
  - RemoteGitBackend implementation using git show :path (index proxy)
affects:
  - 02-pre-commit-diff-viewer plans that build inline diff viewer (Plans 02+)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Return null on git failure (non-zero exit) — callers use createEmpty() for HEAD side"
    - "RemoteGitBackend uses index (git show :path) as working-tree proxy for remote repos"

key-files:
  created: []
  modified:
    - src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt
    - src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt

key-decisions:
  - "LocalGitBackend uses git show HEAD:path for committed-file content; null returned for untracked/new files (non-zero exit)"
  - "RemoteGitBackend uses git show :path (staging area/index) as proxy since SFTP working-tree reads are not available; unstaged remote edits won't show in diff — known limitation"
  - "ByteArray return encoded as UTF-8 from stdout; null is the contract for failure — callers handle via DiffContentFactory.createEmpty()"

patterns-established:
  - "getHeadContent null-return pattern: callers must treat null as signal to use empty content on HEAD side of diff"

requirements-completed: [DIFF-03]

# Metrics
duration: 5min
completed: 2026-02-28
---

# Phase 2 Plan 01: GitBackend getHeadContent Summary

**`getHeadContent(path: String): ByteArray?` added to GitBackend interface with local (HEAD) and remote (index) implementations, providing HEAD-side bytes for diff viewer**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-28T12:30:00Z
- **Completed:** 2026-02-28T12:35:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Added `fun getHeadContent(path: String): ByteArray?` to the GitBackend interface
- Implemented in LocalGitBackend using `git show HEAD:<path>` to fetch committed file bytes
- Implemented in RemoteGitBackend using `git show :<path>` (index/staging area as proxy for working-tree)
- All existing unit tests pass, no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Add getHeadContent to GitBackend interface + LocalGitBackend** - `833f4ab` (feat)
2. **Task 2: Implement getHeadContent in RemoteGitBackend** - `27a26fe` (feat)

**Plan metadata:** (docs commit — pending)

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt` - Added `fun getHeadContent(path: String): ByteArray?` to interface after `getCommitInfo`
- `src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt` - Added override using `git show HEAD:$path`, returns UTF-8 bytes or null on failure
- `src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt` - Added override using `git show :$path` (index), returns UTF-8 bytes or null, with comment documenting the limitation

## Decisions Made
- LocalGitBackend reads from HEAD (`git show HEAD:path`) — correct for local repos where committed content is desired
- RemoteGitBackend reads from git index (`git show :path`) — avoids requiring SFTP file read of working-tree; unstaged remote edits won't appear in diff (documented as known limitation)
- Null return on failure is the contract — callers (Plan 02 diff viewer) will call `DiffContentFactory.createEmpty()` for the HEAD side when null is received

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- A linter/formatter reverted the first edit to RemoteGitBackend.kt; re-applied the change and compilation succeeded on second attempt. No impact on final outcome.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- GitBackend interface now provides HEAD-side bytes needed to construct `SimpleDiffRequest` in the diff viewer
- Plan 02 can proceed: build the inline diff viewer component using `getHeadContent` on the HEAD side and working-tree content on the other side
- No blockers

---
*Phase: 02-pre-commit-diff-viewer*
*Completed: 2026-02-28*
