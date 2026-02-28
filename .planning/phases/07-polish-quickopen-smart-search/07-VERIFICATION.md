---
phase: 07-polish-quickopen-smart-search
verified: 2026-02-28T12:45:00Z
status: passed
score: 10/10 must-haves verified
gaps: []
human_verification:
  - test: "Open QuickOpen popup and observe status bar"
    expected: "Status bar shows 'Fuzzy: Nucleo · Root: ~/path · N files' after enumeration"
    why_human: "candidatePool.getCandidates().size is runtime data; can't verify count without running plugin"
  - test: "Click on status bar root label"
    expected: "Directory picker dialog opens; selecting a new directory re-indexes and updates status bar"
    why_human: "FileChooserFactory dialog behavior requires live IntelliJ instance"
  - test: "Type query with empty search field (speed dial mode)"
    expected: "Items shown are sorted by recent FrecencyStore visits, not bookmark-first order"
    why_human: "Requires FrecencyStore state populated by prior visits; not testable statically"
  - test: "Type '/:pattern' and press Enter on a CONTENT_MATCH result"
    expected: "Editor opens the matched file scrolled to the exact matched line"
    why_human: "Requires rg binary, live file system, and IntelliJ editor integration at runtime"
  - test: "Observe result list with mixed types (bookmark, file, directory)"
    expected: "Bookmarks/recent appear at top, then files, then directories — within groups sorted by score"
    why_human: "Visual result ordering requires populated candidates and runtime scoring"
---

# Phase 7: Polish QuickOpen Smart Search — Verification Report

**Phase Goal:** Polish the QuickOpen smart search feature — fix code quality/security issues, improve async behavior, and deliver UI polish for a production-ready experience.
**Verified:** 2026-02-28T12:45:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | NucleoNative loads on darwin-aarch64 — libfuzzyjni.dylib committed at correct classpath path | VERIFIED | `src/main/resources/natives/darwin-aarch64/libfuzzyjni.dylib` exists, 713K, Mach-O 64-bit arm64 dylib |
| 2 | ripgrepExtraFlags with --exec or unknown flags are silently dropped (allowlist validation) | VERIFIED | `validateExtraFlags()` in `RipgrepContentSearch.kt` with `ALLOWED_FLAGS`/`ALLOWED_PREFIXES`; `cmd.addAll(validateExtraFlags(extra))` replaces unsafe direct split |
| 3 | popup is null-safe — no UninitializedPropertyAccessException possible | VERIFIED | `var popup: JBPopup? = null` at line 171; all 5 callsites (lines 153, 695, 715, 878, 886) use `popup?.closeOk(null)` |
| 4 | CoroutineScope in performContentSearch() is parented to panelScope with SupervisorJob, cancelled in dispose() | VERIFIED | `panelScope` declared line 80 with `CoroutineScope(Dispatchers.IO + SupervisorJob())`; `panelScope.launch` at line 576; `panelScope.cancel()` in `dispose()` at line 912 |
| 5 | Fuzzy search debounce is 50ms; rg content search debounce is 300ms | VERIFIED | `scheduleSearch()` lines 516-523: `val delayMs = if (isContentSearch) 300L else 50L`; old hardcoded 150ms removed |
| 6 | Pressing Enter on a CONTENT_MATCH result opens file at exact matched line | VERIFIED | `openContentMatchAtLine()` at line 690 uses `OpenFileDescriptor(project, vf, lineNumber - 1, 0).navigate(true)`; `activateSelected()` branches on `CandidateType.CONTENT_MATCH` at line 708 |
| 7 | Status bar shows "Fuzzy: ranker · Root: path · N files"; root is clickable | VERIFIED | `rankerStatusLabel` with MouseAdapter calling `openRootPicker()`; `updateStatusBar()` builds correct format string at line 432; called in `refreshAsync` callback (line 409) and `init` (line 422) |
| 8 | Speed dial shows top frecent paths from FrecencyStore, not bookmark-first logic | VERIFIED | `speedDialItems` at lines 138-145 uses `FrecencyStore.getInstance().recencyScore()` exclusively; old bookmark-first fallback removed |
| 9 | Hint bar condensed to 5 shortcuts: navigate, preview, bookmark, recent, close | VERIFIED | `hintLabel` text at lines 129-131: `"↩ navigate · Tab preview · ⌘D bookmark · Ctrl+R recent · Esc close"` |
| 10 | Matched characters in parent path segment are highlighted/bolded in result rows | VERIFIED | `SearchResultRenderer.kt` has `currentQuery` field (line 12); conditional literal-substring highlighting in parent path block (lines 69-85); `resultRenderer.currentQuery = query` set in `updateList()` line 643 |

**Score:** 10/10 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/natives/darwin-aarch64/libfuzzyjni.dylib` | Committed darwin-aarch64 dylib for JAR distribution | VERIFIED | 713K, Mach-O 64-bit arm64, exists in VCS |
| `src/main/kotlin/ro/faur/explorer/quickopen/backend/RipgrepContentSearch.kt` | validateExtraFlags() with allowlist; no direct flag pass-through | VERIFIED | ALLOWED_FLAGS, ALLOWED_PREFIXES in companion object; validateExtraFlags() method in class body; cmd.addAll(validateExtraFlags(extra)) at line 92 |
| `src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt` | panelScope, dispose() cancellation, dual-debounce, openContentMatchAtLine(), status bar, condensed hint, frecency speedDial, resultRenderer field | VERIFIED | All features implemented and wired — 914 lines total |
| `src/main/kotlin/ro/faur/explorer/quickopen/ui/SearchResultRenderer.kt` | currentQuery field; parent path substring highlighting | VERIFIED | currentQuery: String = "" at line 12; full conditional highlighting block lines 69-85 |
| `src/main/kotlin/ro/faur/explorer/quickopen/ranking/Ranker.kt` | typeGroup() helper; compareBy(typeGroup) primary sort | VERIFIED | typeGroup() at line 67; sortedWith uses compareBy { typeGroup(it.candidate) }.thenByDescending { it.score } |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| QuickOpenPanel.init | updateStatusBar() | candidatePool.refreshAsync callback | WIRED | Line 409: updateStatusBar() called inside invokeLater after scheduleSearch() |
| QuickOpenPanel.init | updateStatusBar() | end of init block | WIRED | Line 422: updateStatusBar() called unconditionally after refreshAsync registration |
| rankerStatusLabel | openRootPicker() | MouseAdapter.mouseClicked | WIRED | Lines 114-118: anonymous MouseAdapter calls openRootPicker() |
| scheduleSearch() | 300ms or 50ms delay | QueryParser.parse(raw).mode == CONTENT_SEARCH | WIRED | Lines 516-523: isContentSearch check drives delayMs |
| activateSelected() | openContentMatchAtLine() | CandidateType.CONTENT_MATCH branch | WIRED | Lines 708-712: contentMatches filter with early return |
| openContentMatchAtLine() | OpenFileDescriptor.navigate(true) | lineNumber - 1 (0-based) | WIRED | Line 694: OpenFileDescriptor(project, vf, lineNumber - 1, 0).navigate(true) |
| panelScope | panelScope.cancel() | dispose() | WIRED | Line 912: panelScope.cancel() is last cleanup call in dispose() |
| updateList() | resultRenderer.currentQuery | before listModel.replaceAll() | WIRED | Line 643: resultRenderer.currentQuery = query before list replacement |
| NucleoNative.tryLoad() classpath | libfuzzyjni.dylib | src/main/resources/natives/darwin-aarch64/ path | WIRED | File at correct classpath-rooted path; packaged by processResources |
| Ranker.rank() sortedWith | typeGroup comparator | compareBy { typeGroup(it.candidate) } | WIRED | Lines 57-63: type-group primary, score secondary |

### Requirements Coverage

| Requirement | Source Plan | Description (inferred from ROADMAP) | Status | Evidence |
|-------------|------------|--------------------------------------|--------|----------|
| QO-CODE-01 | 07-01 | Nucleo native library bundled in JAR | SATISFIED | libfuzzyjni.dylib committed, 713K arm64 dylib |
| QO-CODE-02 | 07-01 | ripgrepExtraFlags allowlist validation | SATISFIED | validateExtraFlags() with ALLOWED_FLAGS/ALLOWED_PREFIXES |
| QO-CODE-03 | 07-01 | popup null-safety (no lateinit crash) | SATISFIED | var popup: JBPopup? = null; all callsites use ?. |
| QO-CODE-04 | 07-02 | Coroutine scope parented to panel lifecycle | SATISFIED | panelScope with SupervisorJob(); cancelled in dispose() |
| QO-RG-01 | 07-02 | Dual debounce: 50ms fuzzy / 300ms rg | SATISFIED | scheduleSearch() lines 516-523 |
| QO-RG-02 | 07-02 | Enter on CONTENT_MATCH opens at matched line | SATISFIED | openContentMatchAtLine() + activateSelected() branching |
| QO-RG-03 | 07-02 | No unparented CoroutineScope in QuickOpenPanel | SATISFIED | Zero hits for `CoroutineScope(Dispatchers.IO + Job())` in file |
| QO-UI-01 | 07-03 | Status bar shows ranker/root/count; root clickable | SATISFIED | rankerStatusLabel, updateStatusBar(), openRootPicker() |
| QO-UI-02 | 07-03 | Speed dial uses pure frecency (FrecencyStore) | SATISFIED | speedDialItems uses recencyScore(), no bookmark-first fallback |
| QO-UI-03 | 07-03 | Hint bar condensed to 5 shortcuts | SATISFIED | "↩ navigate · Tab preview · ⌘D bookmark · Ctrl+R recent · Esc close" |
| QO-UI-04 | 07-03 | Parent path highlighting in result renderer | SATISFIED | SearchResultRenderer currentQuery field + conditional highlight block |
| QO-RANK-01 | 07-03 | Result-type grouping: bookmarks/recent → files → dirs | SATISFIED | Ranker.typeGroup() + compareBy { typeGroup } comparator |

**Note:** QO-* requirements are defined in ROADMAP.md Success Criteria for Phase 7. They are not present in REQUIREMENTS.md (which covers DND, TREE, CTX, DIFF, GIT, BRANCH, STASH — v1 functional requirements from a different roadmap layer). All 12 QO-* requirements are fully accounted for across the three plans.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None found | — | — | — | — |

Scanned: `RipgrepContentSearch.kt`, `QuickOpenPanel.kt`, `SearchResultRenderer.kt`, `Ranker.kt` — no TODO, FIXME, placeholder, empty implementations, or console.log patterns detected.

### Human Verification Required

The following behaviors require a running IntelliJ instance to verify:

#### 1. Status Bar Runtime Display

**Test:** Open the Quick Open popup (invoke the action) and wait for candidate enumeration to complete.
**Expected:** Status bar shows "Fuzzy: Nucleo · Root: ~/your-project · 12,345 files" (or similar) with hand cursor.
**Why human:** candidatePool.getCandidates().size is runtime data populated after async VFS enumeration; can't verify count statically.

#### 2. Clickable Root Picker

**Test:** Click on the status bar label in the Quick Open popup.
**Expected:** A directory picker dialog opens. Selecting a new directory re-enumerates and updates the status bar root path.
**Why human:** FileChooserFactory dialog flow requires live IntelliJ platform context.

#### 3. Speed Dial Frecency Order

**Test:** Navigate to several files to populate FrecencyStore, then open Quick Open with an empty query.
**Expected:** Items appear sorted by most-recently-visited, regardless of type — no bookmark-first preference.
**Why human:** Requires FrecencyStore state populated by real usage visits.

#### 4. Content Match Line Navigation

**Test:** Type `/: some-pattern` in Quick Open, wait for rg results, press Enter on a result.
**Expected:** Editor opens the matched file and scrolls to the exact matched line (not line 1).
**Why human:** Requires rg binary on PATH, matching files on disk, and live IntelliJ editor.

#### 5. Result Type Grouping Visual

**Test:** Type a query that matches bookmarks, regular files, and directories.
**Expected:** Bookmarked/recent items appear at top of list, then files, then directories — with score-ordering within each group.
**Why human:** Requires populated candidate pool with mixed types at runtime.

### Gaps Summary

No gaps found. All 10 observable truths are verified. All 12 requirements are satisfied. All 5 artifacts exist and are substantive (not stubs). All 10 key links are wired.

The five items flagged for human verification are runtime behavior checks (UI rendering, dialog interactions, live rg integration) that cannot be confirmed statically — they are not gaps, they are functional behaviors that require a running plugin.

---

_Verified: 2026-02-28T12:45:00Z_
_Verifier: Claude (gsd-verifier)_
