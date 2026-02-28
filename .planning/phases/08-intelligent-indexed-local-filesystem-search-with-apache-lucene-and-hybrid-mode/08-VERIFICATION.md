---
phase: 08-intelligent-indexed-local-filesystem-search-with-apache-lucene-and-hybrid-mode
verified: 2026-02-28T00:00:00Z
status: human_needed
score: 28/28 must-haves verified
human_verification:
  - test: "Open QuickOpen Settings (Settings -> Tools -> Quick Open) and confirm 'Lucene Index' group with 4 controls (threshold spinner, extension text field, max size spinner, eviction spinner) and 'Clear All Index Caches' button is visible"
    expected: "Settings panel renders the Lucene Index group after all existing groups"
    why_human: "Swing UI panel layout cannot be verified programmatically without running the IDE"
  - test: "Open QuickOpen for a small root (fewer than 5,000 files) and observe the bottom status bar"
    expected: "Status bar shows '[Live]' chip in gray color to the right of the ranker/root/count label"
    why_human: "Label rendering and color values require visual inspection in a running IDE"
  - test: "Open QuickOpen for a root with more than 5,000 files and watch the status bar over 10-60 seconds"
    expected: "Chip transitions from '[Live]' to '[Indexing...]' (amber) then to '[Indexed]' (green) as the background build completes; candidate results switch to Lucene-backed results automatically"
    why_human: "Time-based state transition and automatic pool switch require real IDE interaction"
  - test: "After the index is built for a large root, type a query and observe results"
    expected: "Results are returned from the Lucene index (LuceneEnumerator) rather than live VfsEnumerator; the '[Indexed]' chip remains green"
    why_human: "Cannot distinguish which enumerator backend produced results without IDE instrumentation"
  - test: "Use content search mode (/: prefix) on a root that has a Lucene index"
    expected: "Results appear from both Lucene (score 2.0, has snippet) and ripgrep (score 1.0, appended); no duplicate paths; Lucene-found paths appear first"
    why_human: "Result ordering and source attribution require visual inspection of running search output"
  - test: "Click 'Clear All Index Caches' button in Settings"
    expected: "A dialog confirms 'Index caches cleared. They will be rebuilt on next QuickOpen.' and the explorer-index directory under IDE system caches is deleted"
    why_human: "Button action and dialog are UI behaviors requiring manual IDE interaction"
  - test: "Open QuickOpen for a large root that previously had an index; wait for '[Indexed]'; close the IDE; reopen the IDE and open QuickOpen for the same root"
    expected: "The '[Indexed]' chip reappears quickly (within seconds) because the index was persisted in PathManager.getSystemPath()/caches/explorer-index/ across the restart"
    why_human: "IDE restart persistence requires manual verification across a full IDE restart cycle"
---

# Phase 08: Intelligent Indexed Local Filesystem Search Verification Report

**Phase Goal:** Intelligent indexed local filesystem search with Apache Lucene and hybrid mode — when a root exceeds the threshold, automatically build and maintain a Lucene index, use it for file enumeration and content search, and display index status in the UI.
**Verified:** 2026-02-28
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

All automated checks pass. The implementation matches every must-have from all six plans. Human verification is required for UI appearance, state transitions, and IDE restart persistence.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Lucene 9.12.3 JARs are bundled (lucene-core + lucene-analysis-common + lucene-highlighter + lucene-queryparser) | VERIFIED | `build.gradle.kts` lines 51-54: four `implementation("org.apache.lucene:...")` declarations at 9.12.3 |
| 2 | LuceneIndexManager can open/create an index and write/read documents | VERIFIED | `LuceneIndexManager.kt` lines 92-107: two constructors (production NIOFSDirectory, test ByteBuffersDirectory); full write/search/delete API present |
| 3 | Corrupt or old index is detected and silently rebuilt | VERIFIED | `LuceneIndexManager.kt` lines 109-137: `openOrRebuildDirectory` catches `CorruptIndexException`, `IndexFormatTooOldException`, and generic exceptions; calls `rebuildDirectory` which wipes and recreates with `OpenMode.CREATE` |
| 4 | searchPaths("") returns all indexed paths via MatchAllDocsQuery | VERIFIED | `LuceneIndexManager.kt` lines 198-199: `if (query.isBlank()) { MatchAllDocsQuery() }` branch; confirmed by LuceneIndexManagerTest test 7 |
| 5 | QuickOpenSettings.State has all four new Lucene fields with correct defaults | VERIFIED | `QuickOpenSettings.kt` lines 38-41: `luceneHybridThreshold=5_000`, `luceneExtensionAllowlist` with full list, `luceneMaxIndexSizeMb=500`, `luceneEvictionDays=30` |
| 6 | Settings UI renders a "Lucene Index" group with all four controls and Clear button | VERIFIED (code) | `QuickOpenConfigurable.kt` lines 51-54 (spinners/field declared), 133-152 (group rendered with 4 controls + button), 181-184 (isModified), 207-210 (apply), 233-236 (reset) — visual rendering requires human check |
| 7 | All four settings fields survive serialization via apply()/reset()/isModified() | VERIFIED | All three lifecycle methods handle `luceneHybridThreshold`, `luceneExtensionAllowlist`, `luceneMaxIndexSizeMb`, `luceneEvictionDays` |
| 8 | buildIndex walks the filesystem on Dispatchers.IO, never blocking EDT | VERIFIED | `LuceneIndexBuilder.kt` lines 108-109: `suspend fun buildIndex` wrapped in `withContext(Dispatchers.IO)`; uses `Files.walkFileTree` |
| 9 | Files excluded by extension, size, binary detection, and hard-exclude dirs | VERIFIED | `LuceneIndexBuilder.kt` lines 33-101: `HARD_EXCLUDE_DIRS` set; `shouldIndex` checks extension, size >1MB, null byte detection; `preVisitDirectory` returns `SKIP_SUBTREE` for hard-exclude and hidden dirs |
| 10 | WatchService handles ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE, and OVERFLOW | VERIFIED | `LuceneIndexBuilder.kt` lines 196-240: event loop processes all four event kinds |
| 11 | OVERFLOW triggers a full re-scan | VERIFIED | `LuceneIndexBuilder.kt` lines 201-207: `OVERFLOW` sets `overflow = true`; lines 233-236: `if (overflow)` launches `buildIndex` in new coroutine |
| 12 | New subdirectories registered with WatchService during watch | VERIFIED | `LuceneIndexBuilder.kt` lines 215-220: `Files.isDirectory(changed)` branch registers new dir via `changed.register(watchService, ...)` |
| 13 | When root exceeds threshold, QuickOpenPanel uses LuceneEnumerator instead of VfsEnumerator | VERIFIED | `QuickOpenPanel.kt` lines 426-483: `selectEnumeratorAndRefresh` counts files, compares to `luceneHybridThreshold`, switches `candidatePool` to `CandidatePool(LuceneEnumerator(mgr))` via `onIndexReady` callback |
| 14 | LuceneEnumerator is a valid EnumeratorBackend drop-in | VERIFIED | `LuceneEnumerator.kt` lines 7-16: `class LuceneEnumerator(manager) : EnumeratorBackend`; implements `name`, `isAvailable()`, `enumerate()` returning `Flow<String>` |
| 15 | IndexRegistry is a per-JVM singleton ConcurrentHashMap from root to LuceneIndexManager | VERIFIED | `IndexRegistry.kt` lines 13-17: `object IndexRegistry` with `ConcurrentHashMap<String, LuceneIndexManager>` and `ConcurrentHashMap<String, Long>` |
| 16 | While building, live candidatePool is used; after build, pool switches to LuceneEnumerator | VERIFIED | `QuickOpenPanel.kt` lines 458-480: `candidatePool.refreshAsync(root)` called immediately (live), then `IndexRegistry.getOrBuild` triggers build; `onIndexReady` callback (line 467) switches pool and refreshes |
| 17 | IndexRegistry evicts stale roots by deleting their index directory | VERIFIED | `IndexRegistry.kt` lines 62-77: `evictStaleIndexes` computes `evictAfterMs`, filters `lastAccessed`, calls `manager.close()` and `deleteRecursively()` |
| 18 | LuceneEnumerator.enumerate calls searchPaths("", maxResults) triggering MatchAllDocsQuery | VERIFIED | `LuceneEnumerator.kt` line 14: `manager.searchPaths("", maxResults).forEach { emit(it) }` |
| 19 | performContentSearch launches Lucene and ripgrep in parallel | VERIFIED | `QuickOpenPanel.kt` lines 646-666: `val sourceCount = if (luceneManager != null) 2 else 1`; `CountDownLatch(sourceCount)`; two coroutines launched |
| 20 | Lucene results deduplicated; Lucene wins; rg-only results appended | VERIFIED | `QuickOpenPanel.kt` lines 671-701: `lucenePathSet` HashSet built from Lucene paths; `rgResults.filter { it.filePath !in lucenePathSet }` |
| 21 | Lucene results scored 2.0, ripgrep results scored 1.0 | VERIFIED | `QuickOpenPanel.kt` line 684: `score = 2.0` for Lucene; line 699: `score = 1.0` for rg |
| 22 | If no Lucene index, only ripgrep runs | VERIFIED | `QuickOpenPanel.kt` lines 645-647: `sourceCount = if (luceneManager != null) 2 else 1`; Lucene coroutine only launched if `luceneManager != null` |
| 23 | openContentMatchAtLine handles lucene-content IDs (no line number) | VERIFIED | `QuickOpenPanel.kt` lines 795-802: `id.substringAfterLast(':').toIntOrNull() ?: 0` — null coalesces to 0 (open at top) |
| 24 | Status bar shows Indexed/Indexing.../Live chip based on IndexRegistry state | VERIFIED | `QuickOpenPanel.kt` lines 493-504: three-state `when` on `IndexRegistry.getManager` and `IndexRegistry.isBuilding`; chip text and color set accordingly |
| 25 | indexModeChipLabel placed in bottomRow EAST alongside rankerStatusLabel | VERIFIED | `QuickOpenPanel.kt` lines 190-193: `bottomRow` BorderLayout with rankerStatusLabel CENTER and indexModeChipLabel EAST |
| 26 | LuceneIndexManagerTest: all 7 tests cover core lifecycle | VERIFIED | `LuceneIndexManagerTest.kt` lines 13-116: 7 tests — add/search, delete, indexDirForRoot, fresh index, corrupt recovery, searchContent, blank query MatchAllDocs |
| 27 | LuceneIndexBuilderTest: 6 tests cover buildIndex filtering | VERIFIED | `LuceneIndexBuilderTest.kt` lines 26-146: 6 tests — binary exclusion, extension allowlist, size limit, hard-exclude dirs, countFiles, binary detection |
| 28 | NIOFSDirectory used explicitly (not FSDirectory.open) | VERIFIED | `LuceneIndexManager.kt` lines 29 and 112: `import org.apache.lucene.store.NIOFSDirectory`; `NIOFSDirectory(path)` called directly |

**Score:** 28/28 truths verified (automated)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `build.gradle.kts` | Lucene 9.12.3 deps declared | VERIFIED | Lines 50-54: lucene-core, lucene-analysis-common, lucene-highlighter, lucene-queryparser all at 9.12.3 |
| `src/main/kotlin/ro/faur/explorer/quickopen/index/LuceneIndexManager.kt` | Core index lifecycle | VERIFIED | 264 lines; full API: addOrUpdateFile, deleteFile, commit, numDocs, searchPaths, searchContent, close; two constructors |
| `src/main/kotlin/ro/faur/explorer/quickopen/index/LuceneIndexBuilder.kt` | buildIndex + startWatcher + countFiles | VERIFIED | 245 lines; all three functions present as `object LuceneIndexBuilder` methods |
| `src/main/kotlin/ro/faur/explorer/quickopen/index/IndexRegistry.kt` | Singleton registry | VERIFIED | 78 lines; Kotlin `object`; getOrBuild, isBuilding, getManager, evictStaleIndexes |
| `src/main/kotlin/ro/faur/explorer/quickopen/backend/LuceneEnumerator.kt` | EnumeratorBackend drop-in | VERIFIED | 16 lines; implements EnumeratorBackend; enumerate delegates to searchPaths("", maxResults) |
| `src/main/kotlin/ro/faur/explorer/quickopen/backend/LuceneContentSearch.kt` | search() wrapper | VERIFIED | 20 lines; delegates to manager.searchContent; guards blank pattern and exceptions |
| `src/main/kotlin/ro/faur/explorer/settings/QuickOpenSettings.kt` | 4 new Lucene State fields | VERIFIED | Lines 37-41: all four fields with correct defaults |
| `src/main/kotlin/ro/faur/explorer/settings/QuickOpenConfigurable.kt` | Lucene Index settings group | VERIFIED | Lines 51-54 (spinners), 133-152 (group), 181-184 (isModified), 207-210 (apply), 233-236 (reset) |
| `src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt` | selectEnumeratorAndRefresh, indexModeChipLabel, performContentSearch | VERIFIED | Lines 426-483 (hybrid selection), 134-137 (chip label), 190-193 (layout), 487-504 (updateStatusBar), 634-704 (performContentSearch) |
| `src/test/kotlin/ro/faur/explorer/unit/LuceneIndexManagerTest.kt` | 7 unit tests | VERIFIED | All 7 tests present; uses ByteBuffersDirectory (no IDE required) |
| `src/test/kotlin/ro/faur/explorer/unit/LuceneIndexBuilderTest.kt` | 6 unit tests | VERIFIED | All 6 tests present; uses TempDir and ByteBuffersDirectory |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| LuceneIndexManager | NIOFSDirectory | `NIOFSDirectory(path)` constructor | WIRED | `LuceneIndexManager.kt` line 112: `NIOFSDirectory(path)` explicit call |
| LuceneIndexManager.indexDirForRoot | PathManager.getSystemPath | `PathManager.getSystemPath()` | WIRED | `LuceneIndexManager.kt` line 64: `Paths.get(PathManager.getSystemPath(), "caches", "explorer-index", hash)` |
| LuceneIndexBuilder.buildIndex | LuceneIndexManager.addOrUpdateFile | `manager.addOrUpdateFile` per file | WIRED | `LuceneIndexBuilder.kt` line 143: `manager.addOrUpdateFile(file, content, !contentCapReached)` |
| LuceneIndexBuilder.startWatcher | LuceneIndexManager.deleteFile / addOrUpdateFile | WatchService event loop | WIRED | `LuceneIndexBuilder.kt` lines 210-227: `manager.deleteFile` on DELETE; `manager.addOrUpdateFile` on CREATE/MODIFY |
| QuickOpenSettings.State | QuickOpenConfigurable | apply()/reset()/isModified() all four fields | WIRED | `QuickOpenConfigurable.kt` lines 181-184, 207-210, 233-236 |
| QuickOpenPanel | IndexRegistry | `IndexRegistry.getOrBuild(root, settings)` in init | WIRED | `QuickOpenPanel.kt` lines 431 and 467: `IndexRegistry.getManager(root)` and `IndexRegistry.getOrBuild(root, settings)` |
| IndexRegistry.getOrBuild | LuceneIndexBuilder.buildIndex + startWatcher | background coroutine | WIRED | `IndexRegistry.kt` lines 44-49: `LuceneIndexBuilder.buildIndex(rootPath, manager, settings)` then `LuceneIndexBuilder.startWatcher(...)` |
| LuceneEnumerator | LuceneIndexManager.searchPaths | `manager.searchPaths("", maxResults)` | WIRED | `LuceneEnumerator.kt` line 14: `manager.searchPaths("", maxResults).forEach { emit(it) }` |
| QuickOpenPanel.performContentSearch | LuceneContentSearch.search | parallel coroutine | WIRED | `QuickOpenPanel.kt` line 653: `LuceneContentSearch(luceneManager).search(pattern, MAX_CONTENT_RESULTS)` |
| LuceneContentSearch.search | LuceneIndexManager.searchContent | delegates directly | WIRED | `LuceneContentSearch.kt` line 15: `manager.searchContent(pattern, maxResults)` |
| QuickOpenPanel.updateStatusBar | IndexRegistry.getManager + isBuilding | reads both to determine chip state | WIRED | `QuickOpenPanel.kt` lines 495-496: `IndexRegistry.getManager(currentRoot)` and `IndexRegistry.isBuilding(currentRoot)` |

### Anti-Patterns Found

No anti-patterns found. Scanned all Phase 8 new files:

- No `TODO`, `FIXME`, `XXX`, `HACK`, or `PLACEHOLDER` comments in implementation files
- No empty return stubs (`return null`, `return {}`, `return []`) in business logic paths (the two `return null` in IndexRegistry are intentional design: "index not ready yet, caller falls back")
- No `console.log`-only handlers
- No deprecated Lucene 9 APIs used (executor confirmed in SUMMARY: `reader.storedFields().document()` and `UnifiedHighlighter.builder().build()` patterns adopted)

### Human Verification Required

#### 1. Lucene Index Settings Group Rendering

**Test:** Open Settings -> Tools -> (System Explorer) -> Quick Open in a running IDE
**Expected:** A "Lucene Index" group appears at the bottom of the settings panel containing: "Hybrid mode threshold (files)" spinner (default 5000), "Content-indexed extensions" text field (default full extension list), "Max index size per root (MB)" spinner (default 500), "Evict unused index after (days)" spinner (default 30), and a "Clear All Index Caches" button
**Why human:** Swing DSL `panel { group(...) }` layout cannot be verified without rendering the component

#### 2. Live Mode Status Chip

**Test:** Open QuickOpen for a directory with fewer than 5,000 files; inspect the bottom status bar
**Expected:** The bottom-right of the status bar shows `[Live]` in gray, to the right of the "Fuzzy: ... Root: ... N files" label
**Why human:** Label foreground color and layout position require visual confirmation in running IDE

#### 3. Indexing Transition (Indexed/Indexing.../Live)

**Test:** Open QuickOpen for a directory with more than 5,000 files; watch the chip over 10-60 seconds
**Expected:** Chip shows `[Live]` initially (VfsEnumerator in use), transitions to `[Indexing...]` in amber when build starts, then to `[Indexed]` in green when complete; candidate list automatically refreshes with Lucene results
**Why human:** Background build timing and automatic pool switch require real-time observation in a running IDE

#### 4. Content Search with Lucene Results

**Test:** After index is built for a large root, use `/: queryterm` content search
**Expected:** Results appear from both Lucene (with highlighted snippet, higher rank) and ripgrep (appended after); no path appears twice; Lucene-sourced paths come first
**Why human:** Result source attribution and ordering require visual inspection of live search output

#### 5. Clear Index Caches Button Action

**Test:** Click "Clear All Index Caches" in settings
**Expected:** Dialog shows "Index caches cleared. They will be rebuilt on next QuickOpen." (or "No index caches found." if none exist); the `caches/explorer-index/` directory under the IDE system path is deleted
**Why human:** Button click action and file system side effect require manual IDE interaction

#### 6. Index Persistence Across IDE Restart

**Test:** Open QuickOpen for a large root, wait for `[Indexed]`, close IDE, reopen IDE, open QuickOpen for the same root
**Expected:** Index is rebuilt quickly because the index was persisted at `PathManager.getSystemPath()/caches/explorer-index/<sha256hash>/`; `[Indexed]` chip appears after a shorter build time (or immediately if IndexRegistry is pre-populated — note: IndexRegistry is an in-memory Kotlin object and is NOT persisted, so the index directory exists on disk but the manager must be reloaded on demand)
**Why human:** IDE restart cycle and disk persistence require manual testing across a full restart

#### 7. WatchService Incremental Updates

**Test:** With QuickOpen open and `[Indexed]` chip visible, create a new `.kt` file in the indexed root; wait a few seconds; search for the new filename
**Expected:** The new file appears in QuickOpen search results within a few seconds (WatchService ENTRY_CREATE event triggers re-indexing)
**Why human:** Real-time filesystem event handling requires live IDE + filesystem manipulation

---

## Summary

Phase 8 implementation is complete and correct at the code level. All 28 observable truths are verified against the actual codebase:

- Apache Lucene 9.12.3 (4 JARs) is declared as `implementation` dependency in `build.gradle.kts`
- `LuceneIndexManager` provides the full write/search/delete/corrupt-recovery API using `NIOFSDirectory` and `PerFieldAnalyzerWrapper`
- `LuceneIndexBuilder` implements `buildIndex` (walkFileTree with extension/size/binary/hard-exclude filtering) and `startWatcher` (WatchService daemon with OVERFLOW re-scan)
- `IndexRegistry` is a Kotlin `object` singleton managing root-to-manager lifecycle with background build orchestration and stale eviction
- `LuceneEnumerator` is a correct `EnumeratorBackend` drop-in backed by `searchPaths("", maxResults)`
- `LuceneContentSearch` wraps `searchContent` with blank-guard and exception safety
- `QuickOpenPanel.selectEnumeratorAndRefresh` correctly routes small roots to VfsEnumerator and large roots through the IndexRegistry build pipeline, switching to LuceneEnumerator when ready
- `performContentSearch` runs Lucene and ripgrep in parallel (CountDownLatch(sourceCount)) with Lucene-wins deduplication and score-based ordering
- `updateStatusBar` renders the three-state index chip (Indexed/Indexing.../Live) with correct colors
- All four Lucene settings fields are present in `QuickOpenSettings.State` with correct defaults and fully wired in `QuickOpenConfigurable` (isModified/apply/reset)
- 13 unit tests (7 for LuceneIndexManager + 6 for LuceneIndexBuilder) cover all functional behaviors including blank-query MatchAllDocs, corrupt recovery, HARD_EXCLUDE_DIRS, binary detection, and extension filtering

7 items require human verification: settings UI rendering, status chip visual states, live indexing transition, content search result ordering, Clear button action, IDE restart persistence, and WatchService incremental update.

---

_Verified: 2026-02-28_
_Verifier: Claude (gsd-verifier)_
