---
phase: 08-intelligent-indexed-local-filesystem-search-with-apache-lucene-and-hybrid-mode
plan: "06"
subsystem: quickopen-ui
tags: [lucene, status-bar, mode-chip, hybrid-mode, ux]
dependency_graph:
  requires: [08-04, 08-05]
  provides: [index-mode-status-chip]
  affects: [QuickOpenPanel]
tech_stack:
  added: []
  patterns: [status-bar-chip, IndexRegistry-read]
key_files:
  created: []
  modified:
    - src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt
decisions:
  - "indexModeChipLabel is a separate label from modeChipLabel — modeChipLabel stays in north panel for query mode chips; indexModeChipLabel is placed in bottomRow EAST for index state visibility"
  - "Three states — Indexed (green), Indexing... (amber), Live (gray) — derived from IndexRegistry.getManager and IndexRegistry.isBuilding"
metrics:
  duration: "2 min"
  completed: "2026-02-28"
  tasks_completed: 1
  files_modified: 1
---

# Phase 08 Plan 06: Index Mode Status Chip Summary

**One-liner:** Status bar gains an Indexed/Indexing.../Live chip driven by IndexRegistry state, making hybrid mode visible to users without any new files.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add index mode chip to the status bar | d61eeca | QuickOpenPanel.kt (modified) |

## What Was Built

### QuickOpenPanel.kt changes

**New field `indexModeChipLabel`:** Bold 11pt JBLabel with 2/6px border padding, placed alongside `rankerStatusLabel`.

**Layout update:** `bottomRow` in the `statusPanel` init block now adds `indexModeChipLabel` to `BorderLayout.EAST`, so it appears to the right of the existing ranker/root/count text without displacing it.

**`updateStatusBar()` extension:** Three-state logic appended after the existing ranker label update:
- `IndexRegistry.getManager(currentRoot) != null` → `"Indexed"` (green `0x59A869`)
- `IndexRegistry.isBuilding(currentRoot)` → `"Indexing..."` (amber `0xF0A30A`)
- otherwise → `"Live"` (gray)

The chip updates automatically at every existing `updateStatusBar()` call site (after enumeration, after root change, after scheduled search completes). No new call sites needed.

**Import verified:** `ro.faur.explorer.quickopen.index.IndexRegistry` was already present from plan 08-04.

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- QuickOpenPanel.kt: FOUND
- Commit d61eeca: FOUND
