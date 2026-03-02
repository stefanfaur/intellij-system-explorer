---
phase: 02-pre-commit-diff-viewer
verified: 2026-02-28T13:30:00Z
status: human_needed
score: 15/15 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 13/15
  gaps_closed:
    - "History mode: Show Diff opens before-commit vs after-commit diff (COMMIT_HASH^ vs COMMIT_HASH)"
    - "Staging mode and history mode both toggle visibility of the inline diff panel vs CommitDetailsPanel"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Select a modified file in staging area, observe inline diff loads"
    expected: "Spinner appears briefly, then IntelliJ diff viewer shows HEAD content on left and working-tree content on right with syntax highlighting"
    why_human: "Requires running plugin in IntelliJ IDE; DiffRequestPanel rendering cannot be verified statically"
  - test: "Click Show Diff toolbar button with a file selected in staging mode"
    expected: "Full-window DiffManager FRAME popup opens showing the same HEAD vs working-tree comparison"
    why_human: "Requires IDE runtime to verify DiffDialogHints.FRAME window appears"
  - test: "Switch from one file selection to another in staging area"
    expected: "Previous diff panel disappears immediately (no lingering view), spinner appears, new diff loads"
    why_human: "Disposal and re-creation of DiffRequestPanel child requires runtime observation"
  - test: "Select a commit row in history mode, then click a file in the Changed Files panel"
    expected: "CommitDetailsPanel remains visible; inline diff slot does NOT switch to CARD_DIFF; no diff loads"
    why_human: "Lambda early-return guard behaviour requires runtime confirmation that slot state does not flicker"
  - test: "Select a commit row in history mode, then click Show Diff toolbar button"
    expected: "Full-window popup opens showing COMMIT^:path content on left and COMMIT:path content on right; abbreviated commit hash appears as right-pane title"
    why_human: "getFileAtRevision return value and diff label correctness require IDE runtime"
  - test: "Select two files in System Explorer (non-git files), right-click, choose Compare With..."
    expected: "Action is visible and enabled only when exactly 2 files are selected; clicking opens side-by-side diff popup"
    why_human: "Context menu population and action enable/disable requires IDE runtime"
  - test: "Close Git panel tool window while a diff is loaded"
    expected: "No memory leaks; editor documents released; no exceptions in IDE log"
    why_human: "Memory leak detection requires runtime profiling or log inspection"
---

# Phase 2: Pre-Commit Diff Viewer Verification Report

**Phase Goal:** Users can inspect what changed in a file before committing, both inline in the Git panel and in a full-window frame
**Verified:** 2026-02-28T13:30:00Z
**Status:** human_needed — all 15/15 automated checks pass; 7 items remain for human testing
**Re-verification:** Yes — after gap closure (commits 5e540d5 and d3ee347)

## Gap Closure Summary

| Gap | Previous Status | Current Status | Evidence |
|-----|----------------|----------------|----------|
| History mode Show Diff uses wrong diff (HEAD vs working-tree) | FAILED | CLOSED | `doShowFullDiff()` now reads `selectedCommitHash` and branches: `commitHash != null` path calls `getFileAtRevision("$commitHash^", path)` and `getFileAtRevision(commitHash, path)` at GitPanelComponent lines 596-599 |
| onFileSelected fires loadInlineDiff in history mode without guard | PARTIAL | CLOSED | `changedFilesPanel.onFileSelected = fileSelectedLambda@{ file -> ... if (selectedCommitHash != null) return@fileSelectedLambda ... }` at GitPanelComponent line 196 |

---

## Goal Achievement

### Observable Truths

#### Plan 02-01 Truths (DIFF-03: getHeadContent)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | getHeadContent(path) returns raw bytes of a file at HEAD revision | VERIFIED | LocalGitBackend line 63: `arrayOf("show", "HEAD:$path")` returns bytes on success |
| 2 | For untracked/new files the caller can detect failure (null return) | VERIFIED | Both implementations return `null` when `!result.isSuccess` |
| 3 | RemoteGitBackend.getHeadContent uses git show :path (staging-area proxy) | VERIFIED | RemoteGitBackend line 71: `arrayOf("show", ":$path")` |
| 4 | Both implementations compile and existing unit tests still pass | VERIFIED | Commits 5e540d5 and d3ee347 in git log; compileKotlin verified in SUMMARY |

#### Plan 02-02 Truths (DIFF-01, DIFF-02, DIFF-05: Inline Diff Panel)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 5 | Clicking a file in the Changed Files list (staging mode) starts loading an inline diff | VERIFIED | GitPanelComponent lines 197-199: `showDiffSlot(); loadInlineDiff(file)` called when `selectedCommitHash == null` |
| 6 | A spinner is visible in the diff slot while content is being fetched off-EDT | VERIFIED | InlineDiffPanel `showSpinner()` called before `executeOnPooledThread` |
| 7 | The inline diff displays HEAD vs working-tree content via DiffRequestPanel | VERIFIED | InlineDiffPanel.showDiffRequest() calls `DiffManager.getInstance().createRequestPanel(project, childDisposable, null)` |
| 8 | Selecting a different file immediately disposes the old DiffRequestPanel | VERIFIED | InlineDiffPanel.showDiffRequest() disposes `currentChildDisposable` before creating new one; stale-check at loadInlineDiff guards out-of-order EDT callbacks |
| 9 | Closing the Git panel disposes all child diff disposables | VERIFIED | `InlineDiffPanel` parentDisposable is `GitPanelComponent`; `Disposer.newDisposable(parentDisposable)` ensures cleanup on dispose() |
| 10 | Show Diff toolbar button opens full-window diff via DiffManager.showDiff() | VERIFIED | GitPanelComponent line 616: `DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)` |
| 11 | Double-clicking a file in the Changed Files list opens the full-window diff | VERIFIED | changedFilesPanel.onFileDoubleClicked (lines 204-207) invokes `doShowFullDiff()` |
| 12 | History mode: Show Diff opens before-commit vs after-commit diff (COMMIT^ vs COMMIT) | VERIFIED | doShowFullDiff() lines 594-599: `commitHash != null` branch calls `getFileAtRevision("$commitHash^", file.path)` (before) and `getFileAtRevision(commitHash, file.path)` (after); labels set to `"$commitHash^"` and `commitHash.take(8)` |
| 13 | History mode file selection stays in CommitDetailsPanel; staging mode shows inline diff | VERIFIED | fileSelectedLambda guard at line 196: `if (selectedCommitHash != null) return@fileSelectedLambda` prevents slot switch in history mode; showDiffSlot()/showCommitDetailsSlot() handle mode transitions |

#### Plan 02-03 Truths (DIFF-04: CompareWithAction)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 14 | Right-clicking exactly two files shows 'Compare With...' menu item; disabled otherwise | VERIFIED | CompareWithAction.update() guards on `tree.getSelectedFiles().size == 2`; plugin.xml lines 212-213 register `SystemExplorer.CompareWith` |
| 15 | Triggering the action opens DiffManager full-window popup comparing the two files | VERIFIED | CompareWithAction.actionPerformed() builds SimpleDiffRequest and calls `DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)` |

**Score: 15/15 truths verified**

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt` | getHeadContent + getFileAtRevision on interface | VERIFIED | Line 55: `fun getHeadContent(path: String): ByteArray?`; Line 56: `fun getFileAtRevision(hash: String, path: String): ByteArray?` |
| `src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt` | Both overrides implemented | VERIFIED | Lines 62-65: getHeadContent (HEAD:path); Lines 67-70: getFileAtRevision ($hash:path) |
| `src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt` | Both overrides implemented | VERIFIED | Lines 68-73: getHeadContent (:path index); Lines 75-78: getFileAtRevision ($hash:path) |
| `src/main/kotlin/ro/faur/explorer/gitpanel/ui/InlineDiffPanel.kt` | showSpinner(), showDiffRequest(), clear() | VERIFIED | All three methods substantively implemented; uses Disposer.newDisposable and DiffManager.createRequestPanel |
| `src/main/kotlin/ro/faur/explorer/gitpanel/ui/ChangedFilesPanel.kt` | onFileSelected + onFileDoubleClicked callbacks + double-click trigger | VERIFIED | Both callbacks declared; ListSelectionListener and MouseAdapter with clickCount==2 guard present |
| `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt` | InlineDiffPanel wired, Show Diff button, CardLayout slot, loadInlineDiff, history-mode guard, doShowFullDiff split | VERIFIED | All fields/methods present; history-mode branch in doShowFullDiff() at lines 594-611; fileSelectedLambda guard at line 196 |
| `src/main/kotlin/ro/faur/explorer/actions/CompareWithAction.kt` | AnAction comparing two explorer-selected files via DiffManager | VERIFIED | update() guards on size==2; actionPerformed opens DiffManager FRAME popup |
| `src/main/resources/META-INF/plugin.xml` | SystemExplorer.CompareWith registration | VERIFIED | Lines 212-213: registration with correct class `ro.faur.explorer.actions.CompareWithAction` |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| LocalGitBackend.getHeadContent | executor.executeBlocking | git show HEAD:path | WIRED | Line 63: `arrayOf("show", "HEAD:$path")` |
| RemoteGitBackend.getHeadContent | executor.executeBlocking | git show :path (index) | WIRED | Line 71: `arrayOf("show", ":$path")` |
| LocalGitBackend.getFileAtRevision | executor.executeBlocking | git show HASH:path | WIRED | Line 68: `arrayOf("show", "$hash:$path")` |
| RemoteGitBackend.getFileAtRevision | executor.executeBlocking | git show HASH:path | WIRED | Line 76: `arrayOf("show", "$hash:$path")` |
| changedFilesPanel.onFileSelected (staging) | loadInlineDiff | guard passes when selectedCommitHash == null | WIRED | Line 196 guard absent for null; lines 198-199 fire |
| changedFilesPanel.onFileSelected (history) | early return | return@fileSelectedLambda when selectedCommitHash != null | WIRED | Line 196: explicit early return guard |
| doShowFullDiff() history branch | getFileAtRevision | commitHash != null branch | WIRED | Lines 596-599: two getFileAtRevision calls with COMMIT^ and COMMIT |
| doShowFullDiff() staging branch | getHeadContent + File.readBytes() | commitHash == null branch | WIRED | Lines 602-610: existing HEAD vs working-tree path unchanged |
| InlineDiffPanel.showDiffRequest | DiffManager.createRequestPanel | Disposer.newDisposable | WIRED | `DiffManager.getInstance().createRequestPanel(project, childDisposable, null)` |
| Show Diff AnAction | DiffManager.showDiff | DiffDialogHints.FRAME | WIRED | Line 616: `showDiff(project, request, DiffDialogHints.FRAME)` inside invokeLater |
| CompareWithAction.actionPerformed | DiffManager.showDiff | DiffContentFactory.create(project, virtualFile) | WIRED | factory.create() for both VirtualFiles then showDiff FRAME |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DIFF-01 | 02-02-PLAN.md | Inline pre-commit diff embedded in Git panel staging area | SATISFIED | InlineDiffPanel + changedFilesPanel.onFileSelected wiring present; history-mode guard prevents spurious inline diffs |
| DIFF-02 | 02-02-PLAN.md | Show Diff toolbar button opens full-window DiffManager popup | SATISFIED | doShowFullDiff() calls showDiff FRAME for both staging and history modes with correct content |
| DIFF-03 | 02-01-PLAN.md | Diff viewer displays HEAD vs working-tree (staging) / COMMIT^ vs COMMIT (history) | SATISFIED | getHeadContent() for staging; getFileAtRevision() for history; buildDiffRequest with label params builds correct content |
| DIFF-04 | 02-03-PLAN.md | Compare With... context-menu action for two selected explorer files | SATISFIED | CompareWithAction.kt exists, registered in plugin.xml, guards on size==2, opens DiffManager FRAME popup |
| DIFF-05 | 02-02-PLAN.md | Diff viewer disposed correctly when panel closed or new file selected | SATISFIED | InlineDiffPanel uses Disposer.newDisposable(parentDisposable); clear() and showDiffRequest() both call Disposer.dispose(currentChildDisposable) before replacing |

All 5 requirements (DIFF-01 through DIFF-05) are fully satisfied with implementation evidence.

---

### Anti-Patterns Found

None. No TODO/FIXME/HACK/PLACEHOLDER comments found in any phase 02 source files. No empty stubs or return-null implementations found. The explicit lambda label `fileSelectedLambda@` (instead of the invalid `return@onFileSelected`) is a correct Kotlin compiler workaround documented in 02-04-SUMMARY.md — not an anti-pattern.

---

### Human Verification Required

#### 1. Inline diff renders with syntax highlighting (staging mode)

**Test:** In staging mode, select a modified .kt file in the Changed Files list
**Expected:** After a brief spinner, the IntelliJ diff viewer renders with proper syntax coloring in both panes; left pane shows HEAD content, right pane shows working-tree content
**Why human:** DiffRequestPanel syntax highlighting requires IntelliJ's editor infrastructure at runtime; cannot verify statically

#### 2. Show Diff opens full-window frame popup (staging mode)

**Test:** With a file selected in staging mode, click the "Show Diff" toolbar button in the Git panel
**Expected:** A floating window opens (DiffDialogHints.FRAME) showing the HEAD vs working-tree diff; window title includes the file path
**Why human:** DiffDialogHints.FRAME window presentation requires IDE runtime

#### 3. File switch disposes previous diff

**Test:** Select file A in staging area, wait for diff to load, then select file B
**Expected:** File A's diff disappears immediately (spinner replaces it), then file B's diff appears; no stale editors from file A remain
**Why human:** Child disposable lifecycle and Swing component removal require runtime observation

#### 4. History-mode file selection stays in CommitDetailsPanel

**Test:** Select a commit row in the history log; observe CommitDetailsPanel is visible; then click a file in the Changed Files panel below
**Expected:** CommitDetailsPanel remains visible — the right slot does NOT switch to the InlineDiffPanel; no spinner appears
**Why human:** CardLayout slot visibility (CARD_DETAILS vs CARD_DIFF) requires runtime confirmation; static guard is correct but slot flicker cannot be ruled out without running the plugin

#### 5. Show Diff in history mode shows correct commit range

**Test:** Select a commit row in the history log; ensure a file is selected in Changed Files; click the Show Diff toolbar button
**Expected:** Full-window popup opens; left pane shows the file as it existed before the commit (COMMIT^); right pane shows the file after the commit; abbreviated commit hash appears as the right-pane title; for files added in that commit, left pane is empty (all-green diff)
**Why human:** getFileAtRevision return value and correct diff label rendering require IDE runtime

#### 6. Compare With... context menu behavior

**Test:** In System Explorer, select one file and right-click; then select two files and right-click
**Expected:** With one file selected, "Compare With..." is absent or grayed out. With two files selected, it is visible and enabled; clicking opens a side-by-side popup with the two files compared
**Why human:** Context menu population from SystemExplorer.ActionGroup requires IDE runtime

#### 7. Panel close memory leak check

**Test:** Open the Git panel, load a diff, then close the Git panel tool window; inspect IDE log
**Expected:** No "Already disposed" or "Editor was not properly disposed" messages appear in the IDE log
**Why human:** Memory leak detection requires runtime; IDE may print disposal warnings in the log

---

_Verified: 2026-02-28T13:30:00Z_
_Verifier: Claude (gsd-verifier)_
