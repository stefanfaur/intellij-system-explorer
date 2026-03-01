---
phase: 05-branch-management
plan: 02
subsystem: git
tags: [kotlin, git, branch-management, popup, checkout, dirty-tree]

# Dependency graph
requires:
  - phase: 05-01
    provides: "resetHard() on GitBackend interface + both backends; BranchNameValidator"
provides:
  - "cachedBranches field populated in reloadData() invokeLater block"
  - "showBranchPopup() wired to branchLabel MouseAdapter with HAND_CURSOR"
  - "doCheckoutBranch() with pooled-thread working-tree status check and stale-result guard"
  - "showDirtyTreeDialog() 3-option Messages.showDialog (Stash & Switch / Discard & Switch / Cancel)"
  - "doStashAndCheckout() with Auto-stash message and includeUntracked=true"
  - "doDiscardAndCheckout() calling backend.resetHard()"
  - "performCheckout() with checkout + invokeLater reloadData() or notifyError()"
  - "notifyError() helper using SystemExplorer notification group"
affects:
  - "05-branch-management (all branch checkout paths use these methods)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JBPopupFactory.createPopupChooserBuilder for branch list popup with ColoredListCellRenderer"
    - "MouseAdapter on JBLabel with HAND_CURSOR for clickable label UX"
    - "3-option Messages.showDialog for destructive-action UX (defaultOptionIndex = Cancel)"
    - "Stash-then-checkout and reset-then-checkout as two separate dirty-tree resolution paths"

key-files:
  created: []
  modified:
    - src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt

key-decisions:
  - "cachedBranches populated inside invokeLater stale-result guard — always consistent with displayed branchLabel"
  - "showDirtyTreeDialog message references 'Discard & Switch' with explicit WARNING text about permanent loss"
  - "Auto-stash message is exactly 'Auto-stash before checkout to $targetBranch' with includeUntracked=true"
  - "doDiscardAndCheckout calls backend.resetHard() (not inline git command) — uses interface method from 05-01"
  - "All checkout paths use stale-result guard (System.identityHashCode snapshotKey pattern)"

patterns-established:
  - "Branch popup: snapshot cachedBranches to local val before building popup to avoid race conditions"

requirements-completed: [BRANCH-01, BRANCH-02, BRANCH-05]

# Metrics
duration: 5min
completed: 2026-03-01
---

# Phase 05 Plan 02: Branch Popup + Checkout Flow Summary

**Branch popup wired to branchLabel with JBPopupFactory, full checkout flow including 3-option dirty-tree dialog (Stash & Switch / Discard & Switch / Cancel), all using pooled-thread + stale-result guard pattern**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-01T13:14:25Z
- **Completed:** 2026-03-01T13:19:00Z
- **Tasks:** 2 (Task 1: cachedBranches + popup wiring; Task 2: checkout flow methods)
- **Files modified:** 1

## Accomplishments
- Wired `branchLabel` with a `MouseAdapter` (HAND_CURSOR) that opens a `JBPopupFactory` chooser listing all branches; current branch is bold-prefixed with "* "
- `doCheckoutBranch()` checks working tree status on a pooled thread; if dirty, shows a 3-option `Messages.showDialog` before proceeding
- `doStashAndCheckout()` auto-stashes with message "Auto-stash before checkout to $targetBranch" and `includeUntracked=true`, then checks out
- `doDiscardAndCheckout()` calls `backend.resetHard()` (from plan 05-01), then checks out
- All paths use stale-result guard; `notifyError()` provides unified error notification via `SystemExplorer` notification group

## Task Commits

The implementation was committed as part of commit `51f9891` (bundled with plan 05-03 in a prior session):

1. **Task 1 + Task 2: Full branch popup + checkout flow** - `51f9891` (feat)

_Note: This plan's implementation was pre-committed in the 05-03 commit by a prior execution session. All success criteria verified against HEAD._

## Files Created/Modified
- `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt` - Added cachedBranches field, listBranches() in reloadData(), branchLabel mouse listener, showBranchPopup(), doCheckoutBranch(), showDirtyTreeDialog(), doStashAndCheckout(), doDiscardAndCheckout(), performCheckout(), notifyError()

## Decisions Made
- `cachedBranches` is populated inside the `invokeLater` stale-result guard to keep it always consistent with the displayed branch label text
- Dialog uses `defaultOptionIndex = 2` (Cancel) so pressing Enter does not accidentally trigger checkout
- "Discard & Switch" option text includes "WARNING: all uncommitted changes will be permanently lost" to make destructive consequence clear
- `doDiscardAndCheckout` delegates to `backend.resetHard()` rather than calling git CLI directly — consistent with interface-based git command pattern

## Deviations from Plan

### Pre-existing Implementation Found

**1. [Informational] Implementation already committed in prior session (05-03 commit)**
- **Found during:** Task 1 execution start (git status check)
- **Issue:** All 05-02 and 05-03 implementations were already committed in `51f9891` by a prior session that bundled plans 05-02 and 05-03 together
- **Fix:** Verified all success criteria against HEAD; confirmed all symbols present and compile succeeds
- **Files modified:** None additional (code already in HEAD)
- **Verification:** `./gradlew compileKotlin --rerun-tasks` exits 0; all 8 required symbols confirmed via grep

---

**Total deviations:** 1 informational (prior session pre-committed work)
**Impact on plan:** No impact — all deliverables present and verified. Code quality meets plan requirements.

## Issues Encountered
- The prior session committed all 05-02 and 05-03 work in a single commit labeled `feat(05-03)`. This plan creates the SUMMARY.md and updates STATE.md/ROADMAP.md to formally close 05-02.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Branch popup, checkout flow, and dirty-tree dialog are fully functional
- `doCreateBranch()` and `showDeleteBranchSelectPopup()` are also present (from the bundled 05-03 commit)
- Plan 05-03 SUMMARY.md already exists — phase 05 is effectively complete

---
*Phase: 05-branch-management*
*Completed: 2026-03-01*

## Self-Check: PASSED

- FOUND: src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt
- FOUND commit: 51f9891 (contains all 05-02 implementations)
- FOUND: cachedBranches field (line 73)
- FOUND: showBranchPopup() (line 200)
- FOUND: doCheckoutBranch() (line 276)
- FOUND: showDirtyTreeDialog() (line 293)
- FOUND: doStashAndCheckout() (line 313)
- FOUND: doDiscardAndCheckout() (line 327)
- FOUND: performCheckout() (line 341)
- FOUND: notifyError() (line 438)
- FOUND: listBranches() in reloadData() (line 503)
- FOUND: cachedBranches = branches in invokeLater (line 509)
- BUILD: compileKotlin --rerun-tasks exits 0
