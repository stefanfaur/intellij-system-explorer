---
phase: 08-intelligent-indexed-local-filesystem-search-with-apache-lucene-and-hybrid-mode
plan: "04"
subsystem: quickopen/hybrid-index
tags: [lucene, hybrid-mode, enumerator, index-registry, candidate-pool]
dependency_graph:
  requires: [08-01, 08-02, 08-03]
  provides: [LuceneEnumerator, IndexRegistry, hybrid-mode-selection]
  affects: [QuickOpenPanel, CandidatePool]
tech_stack:
  added: []
  patterns: [Kotlin object singleton, ConcurrentHashMap, background coroutine, onIndexReady callback]
key_files:
  created:
    - src/main/kotlin/ro/faur/explorer/quickopen/backend/LuceneEnumerator.kt
    - src/main/kotlin/ro/faur/explorer/quickopen/index/IndexRegistry.kt
  modified:
    - src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt
decisions:
  - "IndexRegistry is a Kotlin object (JVM singleton) with ConcurrentHashMap for managers and lastAccessed timestamps"
  - "candidatePool changed to var to allow replacement when hybrid switch occurs"
  - "selectEnumeratorAndRefresh counts files on IO thread to avoid blocking EDT before deciding mode"
  - "onIndexReady callback allows IndexRegistry to notify QuickOpenPanel to switch to LuceneEnumerator without coupling to panel lifecycle"
metrics:
  duration: 7min
  completed: "2026-02-28"
  tasks: 2
  files: 3
---

# Phase 8 Plan 4: LuceneEnumerator + IndexRegistry + Hybrid Mode Wiring Summary

**One-liner:** EnumeratorBackend backed by Lucene path index with singleton IndexRegistry managing background build lifecycle and hybrid mode switching wired into QuickOpenPanel.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Implement LuceneEnumerator and IndexRegistry | 3886420 | LuceneEnumerator.kt, IndexRegistry.kt (created) |
| 2 | Wire hybrid mode selection in QuickOpenPanel | 8c28eeb | QuickOpenPanel.kt (modified) |

## What Was Built

### LuceneEnumerator (`backend/LuceneEnumerator.kt`)
A drop-in `EnumeratorBackend` implementation that wraps `LuceneIndexManager`. Calls `searchPaths("", maxResults)` which triggers `MatchAllDocsQuery` internally (implemented in plan 08-01), emitting all indexed paths as a Flow.

### IndexRegistry (`index/IndexRegistry.kt`)
A Kotlin object (JVM singleton) with:
- `ConcurrentHashMap<String, LuceneIndexManager>` for ready managers
- `ConcurrentHashMap<String, Long>` for last-accessed timestamps (eviction)
- `ConcurrentHashMap<String>.newKeySet()` to track roots currently building
- `registryScope` (independent of panel lifecycle) for background build + watcher coroutines
- `getOrBuild(root, settings, onIndexReady)`: returns manager if ready, null if building; triggers background build as side effect
- `evictStaleIndexes`: removes managers and deletes index directories for roots not accessed within `luceneEvictionDays`

### QuickOpenPanel Hybrid Mode Wiring
- `candidatePool` changed from `val` to `var` to allow runtime replacement
- `selectEnumeratorAndRefresh(root)` added: checks for existing ready manager first, then counts files on IO thread, selects VfsEnumerator (below threshold) or triggers background build with live fallback (above threshold)
- Both `init` and `openRootPicker()` now call `selectEnumeratorAndRefresh` instead of directly calling `candidatePool.refreshAsync`

## Deviations from Plan

None - plan executed exactly as written.

## Self-Check

- [x] `LuceneEnumerator.kt` created at correct path
- [x] `IndexRegistry.kt` created at correct path
- [x] `QuickOpenPanel.kt` modified with `selectEnumeratorAndRefresh`
- [x] Both tasks committed individually
- [x] `./gradlew compileKotlin` returns BUILD SUCCESSFUL
- [x] `grep "selectEnumeratorAndRefresh"` shows 3 occurrences (definition + 2 call sites)
- [x] `grep "IndexRegistry"` shows 4 occurrences in QuickOpenPanel

## Self-Check: PASSED
