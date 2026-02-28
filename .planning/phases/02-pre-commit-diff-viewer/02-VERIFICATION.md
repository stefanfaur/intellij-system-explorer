---
phase: 02-pre-commit-diff-viewer
verified: 2026-02-28T13:00:00Z
status: gaps_found
score: 13/15 must-haves verified
re_verification: false
gaps:
  - truth: "History mode: Show Diff opens before-commit vs after-commit diff (COMMIT_HASH^ vs COMMIT_HASH)"
    status: failed
    reason: "doShowFullDiff() in GitPanelComponent uses HEAD vs working-tree bytes for BOTH staging and history mode. selectedCommitHash is tracked but never used in doShowFullDiff(). COMMIT^:path and COMMIT:path are never called. This was explicitly deferred in 02-02-SUMMARY.md as requiring getFileAtRevision() on GitBackend, which does not yet exist."
    artifacts:
      - path: "src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt"
        issue: "doShowFullDiff() (lines 584-605) has a single code path for both staging and history modes — uses backend.getHeadContent(file.path) (HEAD content) and File(backend.repoPath, file.path).readBytes() (working-tree). selectedCommitHash is never read inside doShowFullDiff()."
    missing:
      - "getFileAtRevision(hash: String, path: String): ByteArray? method on GitBackend interface"
      - "LocalGitBackend and RemoteGitBackend implementations calling git show HASH:path"
      - "History-mode branch in doShowFullDiff() that calls getFileAtRevision(hash) for HEAD side and getFileAtRevision(hash^) for base side"
  - truth: "Staging mode and history mode both toggle visibility of the inline diff panel vs CommitDetailsPanel"
    status: partial
    reason: "Slot toggling works correctly for staging-to-history and history-to-staging transitions via showDiffSlot()/showCommitDetailsSlot(). However in history mode, selecting a file in ChangedFilesPanel fires onFileSelected which calls showDiffSlot() + loadInlineDiff(), producing an inline HEAD-vs-working-tree diff in history mode — not the commit-range diff. The mode guard is absent; history-mode file selection should either be disabled or show correct commit-range inline diff. This is a minor behavioral gap: the toggle mechanism is correct but history-mode file selection triggers incorrect diff content."
    artifacts:
      - path: "src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt"
        issue: "onFileSelected lambda (lines 194-206) does not check selectedCommitHash before calling loadInlineDiff(). In history mode, clicking a file in ChangedFilesPanel shows an inline diff using HEAD vs working-tree instead of COMMIT^:path vs COMMIT:path, or alternatively stays in CommitDetailsPanel slot."
    missing:
      - "Guard in onFileSelected: if selectedCommitHash != null, either skip inline diff or show correct commit-range diff"
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
  - test: "Select two files in System Explorer (non-git files), right-click, choose Compare With..."
    expected: "Action is visible and enabled only when exactly 2 files are selected; clicking opens side-by-side diff popup"
    why_human: "Context menu population and action enable/disable requires IDE runtime"
  - test: "Close Git panel tool window while a diff is loaded"
    expected: "No memory leaks; editor documents released; no exceptions in IDE log"
    why_human: "Memory leak detection requires runtime profiling or log inspection"
---

# Phase 2: Pre-Commit Diff Viewer Verification Report

**Phase Goal:** Users can inspect what changed in a file before committing, both inline in the Git panel and in a full-window frame
**Verified:** 2026-02-28T13:00:00Z
**Status:** gaps_found — 2 gaps found (1 blocking, 1 partial)
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

The must-haves are drawn from the three plan frontmatter blocks (02-01, 02-02, 02-03) plus the ROADMAP Phase 2 Success Criteria.

#### Plan 02-01 Truths (DIFF-03: getHeadContent)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | getHeadContent(path) returns raw bytes of a file at HEAD revision | VERIFIED | LocalGitBackend line 62-65: `executor.executeBlocking(repoPath, args = arrayOf("show", "HEAD:$path"))` returns `result.stdout.toByteArray(Charsets.UTF_8)` on success |
| 2 | For untracked/new files the caller can detect failure (null return) | VERIFIED | Both implementations return `null` when `!result.isSuccess` — caller in GitPanelComponent checks `commitFile.status == UNTRACKED/ADDED` and passes `null` headBytes to `buildDiffRequest()` |
| 3 | RemoteGitBackend.getHeadContent uses git show :path (staging-area proxy) | VERIFIED | RemoteGitBackend line 71: `args = arrayOf("show", ":$path")` with documented limitation comment |
| 4 | Both implementations compile and existing unit tests still pass | VERIFIED | Commits 833f4ab and 27a26fe; test stub fix committed in ddd4ce9 (GitRepositoryRegistryTest) |

#### Plan 02-02 Truths (DIFF-01, DIFF-02, DIFF-05: Inline Diff Panel)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 5 | Clicking a file in the Changed Files list (staging mode) starts loading an inline diff | VERIFIED | GitPanelComponent lines 194-201: `changedFilesPanel.onFileSelected` lambda calls `showDiffSlot()` + `loadInlineDiff(file)` when file != null |
| 6 | A spinner is visible in the diff slot while content is being fetched off-EDT | VERIFIED | GitPanelComponent line 531: `inlineDiffPanel.showSpinner()` called before `executeOnPooledThread`. InlineDiffPanel.showSpinner() (lines 24-37) adds `AsyncProcessIcon("diff-loading")` |
| 7 | The inline diff displays HEAD vs working-tree content via DiffRequestPanel | VERIFIED | InlineDiffPanel.showDiffRequest() (lines 43-58): creates child disposable via `Disposer.newDisposable(parentDisposable)`, calls `DiffManager.getInstance().createRequestPanel(project, childDisposable, null)`, sets request, adds panel.component |
| 8 | Selecting a different file immediately disposes the old DiffRequestPanel | VERIFIED | InlineDiffPanel.showDiffRequest() disposes `currentChildDisposable` before creating new one. `loadInlineDiff` stale-check at line 550 guards against out-of-order EDT callbacks |
| 9 | Closing the Git panel disposes all child diff disposables | VERIFIED | `InlineDiffPanel` parentDisposable is `GitPanelComponent` (this). `Disposer.newDisposable(parentDisposable)` ensures child disposables are cleaned up when GitPanelComponent.dispose() fires |
| 10 | Show Diff toolbar button (enabled when file selected) opens full-window diff via DiffManager.showDiff() | VERIFIED | GitPanelComponent lines 141-147: AnAction "Show Diff" with update() guard `selectedDiffFile != null && selectedBackend != null`. doShowFullDiff() line 602: `DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)` |
| 11 | Double-clicking a file in the Changed Files list opens the full-window diff | VERIFIED | ChangedFilesPanel lines 75-78: `if (e.clickCount == 2)` invokes `onFileDoubleClicked`. GitPanelComponent lines 203-206: `changedFilesPanel.onFileDoubleClicked = { file -> selectedDiffFile = file; doShowFullDiff() }` |
| 12 | History mode: Show Diff opens before-commit vs after-commit diff | FAILED | doShowFullDiff() (lines 584-605) has ONE code path. `selectedCommitHash` is never read inside the function. Both staging and history mode use `backend.getHeadContent(file.path)` (HEAD) vs `File(backend.repoPath, file.path).readBytes()` (working tree). COMMIT^:path and COMMIT:path git show calls are absent. Explicitly deferred in 02-02-SUMMARY.md. |
| 13 | Staging mode and history mode both toggle visibility of the inline diff panel vs CommitDetailsPanel | PARTIAL | showDiffSlot()/showCommitDetailsSlot() work correctly for mode switching (staging shows CARD_DIFF, history shows CARD_DETAILS). Gap: in history mode, file selection in ChangedFilesPanel still triggers loadInlineDiff() which produces a HEAD-vs-working-tree diff instead of commit-range diff or no-op. No guard on selectedCommitHash in the onFileSelected lambda. |

#### Plan 02-03 Truths (DIFF-04: CompareWithAction)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 14 | Right-clicking exactly two files shows 'Compare With...' menu item; disabled otherwise | VERIFIED | CompareWithAction.update() (lines 22-27): `isEnabledAndVisible = tree != null && isExplorerActive(e) && tree.getSelectedFiles().size == 2`. plugin.xml lines 212-216: `SystemExplorer.CompareWith` registered in `SystemExplorer.ActionGroup` |
| 15 | Triggering the action opens DiffManager full-window popup comparing the two files | VERIFIED | CompareWithAction.actionPerformed() (lines 29-51): fetches VirtualFiles, calls `vf.refresh(false, false)`, builds SimpleDiffRequest, calls `DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)` |

**Score: 13/15 truths verified** (1 failed, 1 partial)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/ro/faur/explorer/gitpanel/GitBackend.kt` | getHeadContent(path: String): ByteArray? on interface | VERIFIED | Line 55: `fun getHeadContent(path: String): ByteArray?` present after getCommitInfo |
| `src/main/kotlin/ro/faur/explorer/gitpanel/LocalGitBackend.kt` | LocalGitBackend implementation of getHeadContent | VERIFIED | Lines 62-65: override using `git show HEAD:$path` |
| `src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt` | RemoteGitBackend implementation of getHeadContent | VERIFIED | Lines 68-73: override using `git show :$path` (index) with limitation comment |
| `src/main/kotlin/ro/faur/explorer/gitpanel/ui/InlineDiffPanel.kt` | Wraps DiffRequestPanel lifecycle; showSpinner(), showDiffRequest(), clear() | VERIFIED | 70 lines; all three methods substantively implemented; uses Disposer.newDisposable and DiffManager.createRequestPanel |
| `src/main/kotlin/ro/faur/explorer/gitpanel/ui/ChangedFilesPanel.kt` | onFileSelected callback + onFileDoubleClicked + double-click trigger | VERIFIED | Lines 33-36: both public callback vars declared. Lines 63-67: ListSelectionListener. Lines 70-88: MouseAdapter with clickCount==2 guard |
| `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt` | InlineDiffPanel wired, Show Diff button, CardLayout slot, loadInlineDiff | VERIFIED | Lines 73-75: inlineDiffPanel and rightSlot(CardLayout) fields. Lines 141-147: Show Diff AnAction. Lines 527-553: loadInlineDiff(). Lines 211-218: showDiffSlot()/showCommitDetailsSlot(). |
| `src/main/kotlin/ro/faur/explorer/actions/CompareWithAction.kt` | AnAction comparing two explorer-selected files via DiffManager | VERIFIED | 52 lines; update() guards on size==2; actionPerformed builds SimpleDiffRequest and calls showDiff FRAME |
| `src/main/resources/META-INF/plugin.xml` | SystemExplorer.CompareWith registration in SystemExplorer.ActionGroup | VERIFIED | Lines 212-216: action registration present with correct class and id |

---

### Key Link Verification

#### Plan 02-01 Links

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| LocalGitBackend.getHeadContent | executor.executeBlocking | git show HEAD:path | WIRED | Line 63: `args = arrayOf("show", "HEAD:$path")` — pattern `show.*HEAD:` present |
| RemoteGitBackend.getHeadContent | executor.executeBlocking | git show :path (index) | WIRED | Line 71: `args = arrayOf("show", ":$path")` — pattern `show.*:path` matches |

#### Plan 02-02 Links

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| ChangedFilesPanel.list (ListSelectionListener) | GitPanelComponent.onFileSelected | onFileSelected callback | WIRED | ChangedFilesPanel lines 63-67 fires `onFileSelected?.invoke(selected)`. GitPanelComponent lines 194-206 assigns the callback |
| GitPanelComponent.onFileSelected | InlineDiffPanel.loadDiff | background thread + invokeLater | WIRED | Lines 197-201: calls `showDiffSlot()` then `loadInlineDiff(file)` which uses `executeOnPooledThread` + `invokeLater` |
| InlineDiffPanel.loadDiff | DiffManager.createRequestPanel | Disposer.newDisposable | WIRED | InlineDiffPanel lines 48-51: `Disposer.newDisposable(parentDisposable)` then `DiffManager.getInstance().createRequestPanel(project, childDisposable, null)` |
| Show Diff AnAction | DiffManager.showDiff | invokeLater with DiffDialogHints.FRAME | WIRED | Line 602: `DiffManager.getInstance().showDiff(project, request, DiffDialogHints.FRAME)` inside invokeLater |

#### Plan 02-03 Links

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| CompareWithAction.update | ExplorerActionUtil.findFileTreeComponent + size==2 | AnActionEvent | WIRED | Lines 23-27: `tree.getSelectedFiles().size == 2` controls isEnabledAndVisible |
| CompareWithAction.actionPerformed | DiffManager.showDiff | DiffContentFactory.create(project, virtualFile) | WIRED | Lines 40-50: factory.create() for both VirtualFiles, then showDiff FRAME |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DIFF-01 | 02-02-PLAN.md | Inline pre-commit diff embedded in Git panel staging area | SATISFIED | InlineDiffPanel + changedFilesPanel.onFileSelected wiring present and substantive |
| DIFF-02 | 02-02-PLAN.md | Show Diff toolbar button opens full-window DiffManager popup | SATISFIED | Show Diff AnAction in toolbar; doShowFullDiff() calls showDiff FRAME |
| DIFF-03 | 02-01-PLAN.md | Diff viewer displays HEAD vs working-tree version | SATISFIED | getHeadContent() on GitBackend interface + both implementations; buildDiffRequest uses HEAD bytes vs File.readBytes() |
| DIFF-04 | 02-03-PLAN.md | Compare With... context-menu action for two selected explorer files | SATISFIED | CompareWithAction.kt exists and is registered in plugin.xml |
| DIFF-05 | 02-02-PLAN.md | Diff viewer disposed correctly when panel closed or new file selected | SATISFIED | InlineDiffPanel uses Disposer.newDisposable(parentDisposable); clear() and showDiffRequest() both call Disposer.dispose(currentChildDisposable) before replacing |

All 5 requirements (DIFF-01 through DIFF-05) are marked Complete in REQUIREMENTS.md traceability table. Code evidence confirms this at the implementation level for all 5, with the caveat that DIFF-02 is technically satisfied (Show Diff opens a popup) but the popup shows HEAD vs working-tree even in history mode rather than the commit-specific diff that the Success Criteria implies.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `GitPanelComponent.kt` | 584-605 | selectedCommitHash declared but never used in doShowFullDiff() | Warning | History-mode Show Diff shows wrong diff (HEAD vs working-tree instead of COMMIT^:path vs COMMIT:path). Documented deferral — not a crash, but functionally incomplete for history mode. |
| `GitPanelComponent.kt` | 194-206 | onFileSelected fires loadInlineDiff in history mode without guard | Warning | When user selects a file in history mode (after clicking a commit row), inline diff slot switches to CARD_DIFF and shows HEAD vs working-tree diff rather than remaining in CommitDetailsPanel or showing the correct commit-range diff. |

No TODO/FIXME/HACK/PLACEHOLDER comments found in any phase 02 source files. No empty return null / return {} stub implementations found.

---

### Human Verification Required

#### 1. Inline diff renders with syntax highlighting

**Test:** In staging mode, select a modified .kt or .py file in the Changed Files list
**Expected:** After a brief spinner, the IntelliJ diff viewer renders with proper syntax coloring in both panes; left pane shows HEAD content, right pane shows working-tree content
**Why human:** DiffRequestPanel syntax highlighting requires IntelliJ's editor infrastructure at runtime; cannot verify statically

#### 2. Show Diff opens full-window frame popup

**Test:** With a file selected in staging mode, click the "Show Diff" toolbar button in the Git panel
**Expected:** A new floating window or frame opens (DiffDialogHints.FRAME behavior) showing the HEAD vs working-tree diff
**Why human:** DiffDialogHints.FRAME window presentation requires IDE runtime

#### 3. File switch disposes previous diff

**Test:** Select file A in staging area, wait for diff to load, then select file B
**Expected:** File A's diff disappears immediately (spinner replaces it), then file B's diff appears; no stale editors from file A remain
**Why human:** Child disposable lifecycle and Swing component removal require runtime observation

#### 4. Compare With... context menu behavior

**Test:** In System Explorer, select exactly one file and right-click; then select exactly two files and right-click
**Expected:** With one file selected, "Compare With..." is absent or grayed out. With two files selected, "Compare With..." is visible and enabled; clicking it opens a side-by-side popup.
**Why human:** Context menu population from SystemExplorer.ActionGroup requires IDE runtime

#### 5. Panel close memory leak check

**Test:** Open the Git panel, load a diff, then close the Git panel tool window; check IDE log for leaked editors
**Expected:** No "Already disposed" or "Editor was not properly disposed" messages in the IDE log
**Why human:** Memory leak detection requires runtime; IDE may print disposal warnings

---

### Gaps Summary

Two gaps block full goal achievement:

**Gap 1 (Blocking — DIFF-02 partial / history-mode correctness):** The Show Diff toolbar button in history mode opens a diff of HEAD vs working-tree, not COMMIT^:path vs COMMIT:path. The `selectedCommitHash` field is correctly set when a history row is selected, but `doShowFullDiff()` never reads it. This requires adding `getFileAtRevision(hash: String, path: String): ByteArray?` to the GitBackend interface and both implementations, then adding a history-mode branch inside `doShowFullDiff()` that calls `getFileAtRevision(hash)` for the after-commit side and `getFileAtRevision("$hash^")` for the before-commit side. This gap was explicitly deferred in 02-02-SUMMARY.md.

**Gap 2 (Warning — onFileSelected history-mode guard):** When a commit row is selected (history mode) and then the user clicks a file in the ChangedFilesPanel, `onFileSelected` calls `showDiffSlot()` + `loadInlineDiff()` unconditionally. This switches the right slot from CommitDetailsPanel to InlineDiffPanel and shows a HEAD-vs-working-tree diff in a context where the user is viewing a historical commit. The fix requires checking `selectedCommitHash` inside the `onFileSelected` lambda before deciding whether to call `loadInlineDiff()`. This is a usability/correctness gap, not a crash.

Both gaps are rooted in the same missing primitive: `getFileAtRevision(hash, path)` on GitBackend. The SUMMARY correctly identifies this as the blocking dependency. Resolving Gap 1 automatically enables the correct fix for Gap 2.

**What is working correctly:**
- All GitBackend.getHeadContent() implementations are complete and correct
- InlineDiffPanel lifecycle (spinner, showDiffRequest, clear, disposal) is fully implemented
- Show Diff button in staging mode opens correct HEAD-vs-working-tree diff popup
- Double-click on a file in staging mode opens correct diff popup
- CardLayout slot switching between CommitDetailsPanel and InlineDiffPanel is correct
- CompareWithAction is fully implemented and registered
- All 5 DIFF requirements have implementation evidence
- No TODO/stub/placeholder anti-patterns in any phase 02 files

---

_Verified: 2026-02-28T13:00:00Z_
_Verifier: Claude (gsd-verifier)_
