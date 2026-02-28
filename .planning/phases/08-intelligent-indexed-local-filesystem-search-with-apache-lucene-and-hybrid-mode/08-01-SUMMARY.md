---
phase: 08-intelligent-indexed-local-filesystem-search-with-apache-lucene-and-hybrid-mode
plan: "01"
subsystem: search
tags: [lucene, index, search, quickopen, kotlin, java]

# Dependency graph
requires: []
provides:
  - LuceneIndexManager class with IndexWriter lifecycle (addOrUpdateFile, deleteFile, commit, close)
  - searchPaths (WildcardQuery + MatchAllDocsQuery for blank), searchContent (QueryParser + UnifiedHighlighter)
  - indexDirForRoot factory using SHA-256 hash + PathManager.getSystemPath() for persistence across IDE restarts
  - Corrupt/old index silent recovery (deleteDirectoryRecursively + OpenMode.CREATE)
  - Lucene 9.12.3 bundled: lucene-core, lucene-analysis-common, lucene-highlighter, lucene-queryparser
affects: [08-02, 08-03, 08-04, 08-05, 08-06]

# Tech tracking
tech-stack:
  added:
    - org.apache.lucene:lucene-core:9.12.3
    - org.apache.lucene:lucene-analysis-common:9.12.3
    - org.apache.lucene:lucene-highlighter:9.12.3
    - org.apache.lucene:lucene-queryparser:9.12.3
  patterns:
    - PerFieldAnalyzerWrapper with WhitespaceAnalyzer for FIELD_PATH/FIELD_FILENAME and StandardAnalyzer for FIELD_CONTENT
    - NIOFSDirectory (not FSDirectory.open/MMapDirectory) to avoid file handle leaks on plugin unload
    - ByteBuffersDirectory for in-memory tests (RAMDirectory removed in Lucene 9)
    - Internal constructor for test injection — package-private secondary constructor accepts Directory
    - Corrupt index recovery: try DirectoryReader.open(), catch CorruptIndexException/IndexFormatTooOldException, wipe and recreate

key-files:
  created:
    - src/main/kotlin/ro/faur/explorer/quickopen/index/LuceneIndexManager.kt
    - src/test/kotlin/ro/faur/explorer/unit/LuceneIndexManagerTest.kt
  modified:
    - build.gradle.kts

key-decisions:
  - "NIOFSDirectory used instead of FSDirectory.open() to prevent MMapDirectory auto-selection which leaks file handles on plugin unload"
  - "indexDirForRoot uses PathManager.getSystemPath() (not getPluginTempPath()) so index survives IDE restarts"
  - "lucene-highlighter and lucene-queryparser added alongside lucene-core — required for UnifiedHighlighter and QueryParser"
  - "searchPaths blank query uses MatchAllDocsQuery — WildcardQuery on empty string returns no results"
  - "PerFieldAnalyzerWrapper chosen for correct camelCase tokenization of code paths"

patterns-established:
  - "Pattern 1: ByteBuffersDirectory for Lucene unit tests — no disk I/O, fast, clean"
  - "Pattern 2: Internal (package-private) secondary constructor accepting Directory for test injection"
  - "Pattern 3: writer.commit() before every DirectoryReader.open() to ensure latest data is visible"

requirements-completed: []

# Metrics
duration: 15min
completed: 2026-02-28
---

# Phase 08 Plan 01: Lucene Foundation Summary

**Apache Lucene 9.12.3 bundled as plugin dependency with LuceneIndexManager providing write/search/delete/corrupt-recovery lifecycle via NIOFSDirectory and PerFieldAnalyzerWrapper**

## Performance

- **Duration:** 15 min
- **Started:** 2026-02-28T13:00:00Z
- **Completed:** 2026-02-28T13:15:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Lucene 9.12.3 JARs (lucene-core, lucene-analysis-common, lucene-highlighter, lucene-queryparser) declared as implementation dependencies in build.gradle.kts and verified in runtimeClasspath
- LuceneIndexManager implemented with full lifecycle: addOrUpdateFile, deleteFile, commit, numDocs, searchPaths (WildcardQuery + MatchAllDocsQuery), searchContent (QueryParser + UnifiedHighlighter), close
- Silent corrupt index recovery: CorruptIndexException and IndexFormatTooOldException both trigger recursive delete + recreate with OpenMode.CREATE
- All 7 unit tests pass using ByteBuffersDirectory (no IntelliJ platform required) confirming blank-query MatchAllDocsQuery, content search, path search, deletion, recovery, and deterministic indexDirForRoot

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Lucene dependencies to build.gradle.kts** - `b0b84cc` (chore)
2. **Task 2 RED: Failing tests for LuceneIndexManager** - `6e43e45` (test)
3. **Task 2 GREEN: Implement LuceneIndexManager** - `c2e0c00` (feat)

_Note: TDD task split into test commit (RED) and implementation commit (GREEN)_

## Files Created/Modified
- `build.gradle.kts` - Added 4 Lucene 9.12.3 implementation dependencies
- `src/main/kotlin/ro/faur/explorer/quickopen/index/LuceneIndexManager.kt` - Core index lifecycle class
- `src/test/kotlin/ro/faur/explorer/unit/LuceneIndexManagerTest.kt` - 7 TDD unit tests (ByteBuffersDirectory)

## Decisions Made
- Used `NIOFSDirectory(indexPath)` explicitly (not `FSDirectory.open()`) to avoid MMapDirectory which leaks file handles on plugin unload
- `indexDirForRoot` uses `PathManager.getSystemPath()` so index persists across IDE restarts (overriding CONTEXT.md's initial `getPluginTempPath()` choice)
- `lucene-highlighter` and `lucene-queryparser` added as additional dependencies because `searchContent` requires both `UnifiedHighlighter` and `QueryParser`
- Used `UnifiedHighlighter.builder(searcher, analyzer).build()` and `reader.storedFields().document()` to avoid deprecated Lucene 9 APIs

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added lucene-highlighter and lucene-queryparser dependencies**
- **Found during:** Task 2 (Implement LuceneIndexManager)
- **Issue:** Plan mentioned adding `lucene-highlighter:9.12.3` if UnifiedHighlighter import fails; QueryParser also requires `lucene-queryparser:9.12.3` — both unresolved at compile time
- **Fix:** Added both `lucene-highlighter:9.12.3` and `lucene-queryparser:9.12.3` to build.gradle.kts implementation dependencies
- **Files modified:** build.gradle.kts
- **Verification:** ./gradlew compileKotlin passes with no errors; tests pass
- **Committed in:** c2e0c00 (Task 2 GREEN commit)

**2. [Rule 1 - Bug] Fixed deprecated Lucene 9 API usage**
- **Found during:** Task 2 (Implement LuceneIndexManager)
- **Issue:** `searcher.doc(scoreDoc.doc)` and `UnifiedHighlighter(searcher, analyzer)` constructor are deprecated in Lucene 9.12.3 — plan requires no deprecated API warnings
- **Fix:** Changed to `reader.storedFields().document(scoreDoc.doc)` and `UnifiedHighlighter.builder(searcher, analyzer).build()`
- **Files modified:** src/main/kotlin/ro/faur/explorer/quickopen/index/LuceneIndexManager.kt
- **Verification:** ./gradlew compileKotlin produces zero warnings; all 7 tests pass
- **Committed in:** c2e0c00 (Task 2 GREEN commit)

---

**Total deviations:** 2 auto-fixed (1 blocking dependency, 1 deprecated API bug)
**Impact on plan:** Both auto-fixes necessary for compilation and plan compliance. No scope creep.

## Issues Encountered
None beyond the auto-fixed items above.

## Next Phase Readiness
- LuceneIndexManager is the foundation for all subsequent Phase 08 plans (02-06)
- Public API shape exactly matches the contract specified in 08-01-PLAN.md interfaces section
- searchPaths("") correctly uses MatchAllDocsQuery — required by LuceneEnumerator.enumerate in 08-02

---
*Phase: 08-intelligent-indexed-local-filesystem-search-with-apache-lucene-and-hybrid-mode*
*Completed: 2026-02-28*
