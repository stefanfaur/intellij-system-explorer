---
phase: 07-polish-quickopen-smart-search
plan: 02
subsystem: quickopen-ui
tags: [coroutine-scope, debounce, content-search, ripgrep, editor-navigation]
dependency_graph:
  requires: [07-01]
  provides: [panelScope-lifecycle, dual-debounce, content-match-line-navigation]
  affects: [QuickOpenPanel]
tech_stack:
  added: [SupervisorJob, OpenFileDescriptor]
  patterns: [parented-coroutine-scope, dual-debounce-timing, line-precise-navigation]
key_files:
  modified:
    - src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt
decisions:
  - panelScope uses SupervisorJob so child coroutine failures do not cancel sibling coroutines or the panel itself
  - FrecencyStore.recordVisit still called for CONTENT_MATCH candidates before routing to openContentMatchAtLine
  - Return early after openContentMatchAtLine — popup is closed inside helper, not in activateSelected
metrics:
  duration: "4 min"
  completed_date: "2026-02-28"
  tasks_completed: 3
  files_modified: 1
---

# Phase 7 Plan 02: Coroutine Scope Fix, Dual-Debounce, and Content Match Navigation Summary

**One-liner:** Parented panelScope with SupervisorJob eliminates rg process leaks; 50ms/300ms dual-debounce optimises responsiveness; OpenFileDescriptor with 0-based line offset delivers line-precise content match navigation.

## What Was Built

Three targeted changes to `QuickOpenPanel.kt`:

1. **panelScope field** added after `candidatePool` declaration. Uses `CoroutineScope(Dispatchers.IO + SupervisorJob())` so the scope is tied to panel lifecycle rather than floating unparented.

2. **Coroutine scope leak fixed** in `performContentSearch()`: replaced `CoroutineScope(Dispatchers.IO + Job()).launch` with `panelScope.launch`. The latch-based await pattern is unchanged.

3. **dispose() updated** to call `panelScope.cancel()` as the last cleanup step, ensuring all in-flight rg processes are cancelled when the panel closes.

4. **Dual-debounce** in `scheduleSearch()`: replaced hardcoded 150ms with `QueryParser.parse(raw).mode == QueryMode.CONTENT_SEARCH` check — 300ms for rg content searches (network/disk bound), 50ms for fuzzy path matching (in-memory, fast).

5. **openContentMatchAtLine() helper** added: parses 1-based line number from `candidate.id` (`content:<path>:<line>`), looks up `VirtualFile`, calls `OpenFileDescriptor(project, vf, lineNumber - 1, 0).navigate(true)` with the required 0-based offset, then closes the popup.

6. **activateSelected() branching**: CONTENT_MATCH candidates are routed to `openContentMatchAtLine(contentMatches.first())` with an early return. Non-content-match candidates continue through the existing `onSelected()` callback path.

7. **Import added**: `com.intellij.openapi.fileEditor.OpenFileDescriptor`, `kotlinx.coroutines.SupervisorJob`, `kotlinx.coroutines.cancel`.

## Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 | ed549f6 | fix(07-02): add panelScope with SupervisorJob and fix coroutine scope leak in performContentSearch() |
| 2 | c5de5b4 | feat(07-02): implement dual-debounce 50ms fuzzy / 300ms rg content search in scheduleSearch() |
| 3 | e1cfdb0 | feat(07-02): wire rg content match activation to line-precise editor navigation via OpenFileDescriptor |

## Verification Results

- `grep -c "CoroutineScope(Dispatchers.IO + Job())"` → 0 (no unparented scopes)
- `grep -n "panelScope"` → 3 hits (line 76 declaration, line 521 launch, line 856 cancel)
- `grep -n "150, TimeUnit"` → 0 hits (hardcoded delay eliminated)
- `grep -n "openContentMatchAtLine"` → 2 hits (definition + call)
- `./gradlew compileKotlin -PskipCargo=true` → BUILD SUCCESSFUL

## Deviations from Plan

None - plan executed exactly as written.

## Self-Check: PASSED

- `/Users/stefanfaur/Desktop/work/ai-tools/intellij-explorer/src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt` - FOUND
- Commits ed549f6, c5de5b4, e1cfdb0 - all present in git log
