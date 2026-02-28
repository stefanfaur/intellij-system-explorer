# Phase 7: Polish, improve quick open / smart search - Context

**Gathered:** 2026-02-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Polish the existing Quick Open popup (Cmd+Shift+P): fix Nucleo native bundling so the best fuzzy ranker actually works, improve rg content search reliability and UX, add search transparency (active ranker + root directory), improve ranking quality and result display, and fix known code quality issues. Adding entirely new search domains (e.g., IntelliJ actions beyond the existing `>` prefix, remote SFTP search) is out of scope.

</domain>

<decisions>
## Implementation Decisions

### Search Transparency
- Add a status bar line **below the result list** (replacing or extending the current hint bar area)
- Format: `Fuzzy: Nucleo · Root: ~/projects/myapp · 14,203 files`
- The root directory path in the status bar is **clickable** — clicking opens a directory picker so users can change the search root without reopening the popup
- When Nucleo is unavailable: **do not surface a warning** — the correct fix is bundling the native lib so it always works. Once bundled, the status bar just shows "Fuzzy: Nucleo" always.

### Nucleo Native Bundling (Root Fix)
- Add a Gradle `copyNatives` task that copies `rust-fuzzy/target/release/libfuzzyjni.*` into `src/main/resources/natives/<platform>/` before `processResources`
- Platform detection required: macOS (arm64/x86_64), Linux (x86_64), Windows (x86_64)
- This is the **highest priority fix** — without it the best ranker is silently unavailable for all users

### rg Content Search
- Fix the **coroutine scope leak**: `performContentSearch()` currently creates an unparented `CoroutineScope + CountDownLatch`. Replace with structured concurrency tied to the panel's lifecycle
- Result display: each rg match shows **filename (bold) + line number + matching line with the matched portion highlighted** — like VS Code search results
- Selecting a rg content match: **Enter opens the file in IntelliJ's editor at the exact matched line number** (not just navigate explorer)
- Keep separate debounce for rg: 300ms (vs 50ms for fuzzy) to avoid spawning too many rg processes on fast typing

### Ranking Quality
- **Frecency boost**: strong boost for frequently/recently visited paths, but fuzzy score quality can still overtake — balanced, not frecency-always-wins
- **Result grouping order**: Recent/bookmarks first → files → directories (within each group, sorted by combined fuzzy+frecency score)
- **Match character highlighting**: matched characters are highlighted/bolded in both the filename and the path segment, like IntelliJ's Cmd+E popup
- Nucleo bundling is prerequisite to meaningful ranking quality improvement

### UI/UX
- **Search debounce**: reduce to **50ms for fuzzy search**, keep **300ms for rg content search**
- **Hint bar**: condense to 4-5 essential shortcuts only: `↩ navigate · Tab preview · ⌘D bookmark · Ctrl+R recent · Esc close`
- The newly-added status bar replaces the space freed up by the condensed hint bar

### Speed Dial (empty query panel)
- Show **top frecent paths** (most recently visited via Quick Open), not just bookmarks
- Include **both files and directories** — whatever the user actually visited most recently
- Drop the bookmark-priority logic; pure recency from `FrecencyStore`

### Code Quality (all four fixes required)
1. **Coroutine scope leak** in `performContentSearch()` — fix to structured concurrency
2. **`lateinit popup` crash risk** — change to `var popup: JBPopup? = null` with null-safe calls, or pass via constructor
3. **Bundle Nucleo native libs** via Gradle `copyNatives` task (see Nucleo section above)
4. **`ripgrepExtraFlags` allowlist validation** — validate against a safe allowlist (e.g. `--hidden`, `--no-ignore`, `--follow`, `--type`, `--glob`); reject flags starting with `--exec` or `--`-prefixed flags not on the allowlist

### Claude's Discretion
- Exact Gradle platform detection logic and how to handle cross-compilation scenarios
- Whether to add a `?` key for a full shortcut help overlay (user said condense, not remove — Claude can decide if a help key is worth adding)
- Exact frecency score formula tuning (weights for recency vs frequency)
- How to handle the picker UX when clicking the root path (dialog vs inline field vs dropdown of recent roots)

</decisions>

<specifics>
## Specific Ideas

- rg results should feel like VS Code's search panel: file header + line + highlighted match snippet
- Status bar should feel lightweight — same small gray text as the current hint bar, just different content
- Speed dial should reflect actual usage: "whatever I visited most recently, files or dirs"

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `QuickOpenPanel.kt`: Main panel class — status bar addition goes in `init` layout (currently `statusPanel` at SOUTH of centerPanel)
- `SpeedDialPanel.kt`: Speed dial component — change its data source from bookmark-priority to pure frecency from `FrecencyStore`
- `FrecencyStore.kt`: Application service with `getSignals()` and `recordVisit()` — drives both ranking boosts and the new speed dial
- `SearchResultRenderer.kt`: Cell renderer for result rows — add match character highlighting here
- `RankerSelector.kt`: Picks the active ranker backend — expose the selected backend name for the status bar
- `CandidatePool.kt`: Manages the index — exposes `isTruncated` and pool size, both needed for status bar
- `RipgrepContentSearch.kt`: Handles rg execution — fix the coroutine scope here
- `NucleoNative.kt` + `NucleoRanker.kt`: JNI binding and ranker — the native libs need to be bundled for these to activate
- `QueryParser.kt`: Query parsing and `QueryMode` enum — existing mode chip already reads from this
- `build.gradle.kts`: Where the `copyNatives` Gradle task needs to be added

### Established Patterns
- Status bar area: currently a `JPanel(BorderLayout())` with `truncationLabel` (WEST), `countLabel` (EAST), `hintLabel` (CENTER) — the new ranker/root info replaces or extends this
- All settings persist via `QuickOpenSettings.State` — any new tuning parameters go there
- Debounce via `ScheduledFuture` on `searchScheduler` — same mechanism, just change the delay constant
- `DumbAwareAction.create {}` pattern for keyboard shortcuts — use for any new keyboard bindings

### Integration Points
- `QuickOpenPanel` receives `currentPath: String` at construction — this is the initial search root; clicking the root picker would update a mutable field and trigger re-index
- `FrecencyStore.getInstance()` is the single source of truth for visit history — speed dial and ranking both draw from it
- File open action: `FileEditorManager.getInstance(project).openFile(vf, true)` already exists in `showOpenWithMenu()` — reuse for rg match activation
- Line navigation after opening: use `OpenFileDescriptor(project, vf, lineNumber - 1, 0).navigate(true)` pattern (standard IntelliJ API)

</code_context>

<deferred>
## Deferred Ideas

- Scope presets (project root / current dir / home shortcuts) — user was interested but chose not to discuss further; add to backlog
- Remote SFTP content search — searching remote file contents via rg over SSH
- IntelliJ action search improvements beyond the existing `>` prefix

</deferred>

---

*Phase: 07-polish-quickopen-smart-search*
*Context gathered: 2026-02-28*
