---
phase: 08-intelligent-indexed-local-filesystem-search-with-apache-lucene-and-hybrid-mode
plan: "05"
subsystem: quickopen-content-search
tags: [lucene, ripgrep, content-search, parallel, deduplication]
dependency_graph:
  requires: [08-01, 08-03, 08-04]
  provides: [hybrid-content-search]
  affects: [QuickOpenPanel, LuceneContentSearch]
tech_stack:
  added: []
  patterns: [parallel-coroutine-search, lucene-wins-deduplication, CountDownLatch-merge]
key_files:
  created:
    - src/main/kotlin/ro/faur/explorer/quickopen/backend/LuceneContentSearch.kt
  modified:
    - src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt
decisions:
  - "LuceneContentSearch is a thin wrapper matching RipgrepContentSearch pattern — all Lucene machinery stays in LuceneIndexManager"
  - "openContentMatchAtLine uses coerceAtLeast(0) to handle lucene-content IDs with no line number suffix gracefully"
  - "Lucene wins deduplication by path: Lucene result (score=2.0) suppresses rg result for same file; rg-only results appended at score=1.0"
metrics:
  duration: "2 min"
  completed: "2026-02-28"
  tasks_completed: 2
  files_modified: 2
---

# Phase 08 Plan 05: Hybrid Content Search (Lucene + Ripgrep Parallel Merge) Summary

**One-liner:** Parallel Lucene + ripgrep content search with path-based Lucene-wins deduplication and graceful fallback to ripgrep-only when no index exists.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Implement LuceneContentSearch | d740eb2 | LuceneContentSearch.kt (created) |
| 2 | Update performContentSearch to merge Lucene + ripgrep results | de2c24d | QuickOpenPanel.kt (modified) |

## What Was Built

### LuceneContentSearch.kt
Thin wrapper around `LuceneIndexManager.searchContent`. Mirrors the pattern of `RipgrepContentSearch` to provide a clean injection point. Returns `emptyList()` on blank input or any exception — never propagates.

### QuickOpenPanel.kt — performContentSearch() rewrite
- Checks `IndexRegistry.getManager(currentRoot)` to determine if Lucene index is available
- If index available: `CountDownLatch(2)` — Lucene and ripgrep coroutines run in parallel
- If no index: `CountDownLatch(1)` — ripgrep only (unchanged behavior from before plan 08-05)
- Merge: Lucene results first (score=2.0, have snippets), then rg-only results filtered by `lucenePathSet` (score=1.0)
- `openContentMatchAtLine` updated: `lucene-content:$path` IDs have no line number, `toIntOrNull()` returns null, defaulting to line 0; `coerceAtLeast(0)` prevents negative offset

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- LuceneContentSearch.kt: FOUND
- QuickOpenPanel.kt: FOUND
- Commit d740eb2: FOUND
- Commit de2c24d: FOUND
