# Phase 8: Intelligent Indexed Local Filesystem Search with Apache Lucene and Hybrid Mode - Context

**Gathered:** 2026-02-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace the in-memory `CandidatePool` (50k cap, re-enumerates on root change) with a persistent, disk-backed Lucene index for local filesystem paths and file content. A "hybrid mode" automatically selects between index-backed recall and live ripgrep enumeration based on directory size, so large directories get fast indexed results while small/fresh roots continue using the live path.

Remote (SFTP) roots are out of scope — index is local-only.

</domain>

<decisions>
## Implementation Decisions

### Index Scope
- Index both **filename/path AND file content** for each file
- Content is indexed only for files matching the configurable **extension allowlist**
- Default allowlist: `.kt`, `.java`, `.py`, `.ts`, `.tsx`, `.js`, `.jsx`, `.go`, `.rs`, `.rb`, `.c`, `.cpp`, `.h`, `.cs`, `.swift`, `.md`, `.txt`, `.adoc`, `.yaml`, `.yml`, `.json`, `.toml`, `.xml`, `.gradle`, `.properties`, `.sh`, `.env`
- User can add/remove extensions in Settings
- **Hard excludes** (always skipped regardless of extension): binary files, files above a max-size threshold (configurable, default 1MB), and directories matching the existing ripgrep ignore rules (`node_modules`, `.git`, `build/`, `target/`, etc.)

### Hybrid Mode — Mode Selection
- Auto-switch based on **directory file count** at a configurable threshold (default: 5,000 files)
- Below threshold: **Live mode** — live ripgrep enumeration (same as today), no index involved
- Above threshold: **Index mode** — Lucene index used for path recall and content search
- Threshold is configurable in QuickOpen Settings

### Hybrid Mode — Initial Build
- When a root first crosses the threshold, the system **falls back to live ripgrep** while Lucene builds the index in the background
- Status bar shows `Indexing...` chip while build is in progress
- Once complete, seamlessly switches to index mode without requiring user action

### Hybrid Mode — UI Indicator
- The existing Phase 7 status bar gains a **mode chip**: `Indexed` or `Live`
- No separate visual state or window — just the status bar chip

### Content Search Strategy (Lucene + ripgrep)
- **Lucene runs first** → instant results with snippet highlighting
- **Ripgrep runs in parallel** → catches files changed since last index update (freshness gap)
- **Merge strategy**: deduplicate by full path; if both sources found the same file, keep the Lucene result (has snippet). Append rg-only results at the end.

### Index Lifecycle
- **WatchService daemon** monitors indexed roots for filesystem changes (add/update/delete)
- WatchService **starts only when the root crosses the size threshold** — no overhead for small roots
- Watching uses the same ignore rules as ripgrep: `ripgrepRespectIgnore`, hidden files toggle, max depth, and the extension allowlist
- Each unique root path gets its **own independent index** (per-root isolation)
- Multiple roots can be indexed concurrently in the background

### Index Storage
- Location: `PathManager.getSystemPath() / "caches" / "explorer-index" / {sha256(rootPath)}`
- **Note:** The original discussion specified `PathManager.getPluginTempPath()`, but planning research determined that `getPluginTempPath()` is cleared on IDE restart, which defeats the purpose of a persistent index. `getSystemPath()/caches/` is the correct IntelliJ convention for plugin-owned persistent caches (same location used by the IDE's own Lucene indexes). This override is intentional and documented here.
- **Max size per root**: 500MB (configurable). If exceeded, stop indexing content bodies but continue indexing paths only
- **LRU eviction**: roots not opened in 30 days have their index automatically deleted
- A "Clear Index" action available in Settings for manual cleanup

### Claude's Discretion
- Lucene `Analyzer` choice (e.g. `StandardAnalyzer` vs custom tokenizer for code paths)
- Exact Lucene document schema (field names, stored vs indexed, norms)
- WatchService overflow handling (too many events in burst)
- Corrupt index recovery strategy (detect on open → rebuild silently)
- Settings UI layout for the new index controls

</decisions>

<specifics>
## Specific Ideas

- The "Lucene first, rg fills gaps" pattern must be transparent to the user — they should never see two separate result lists or flickering as rg results arrive
- Indexing should feel like a background maintenance task, not a blocking operation

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `EnumeratorBackend` interface (`quickopen/backend/EnumeratorBackend.kt`): `enumerate(root, maxResults): Flow<String>` — Lucene index can implement this interface as a `LuceneEnumerator`, making it a drop-in for `CandidatePool`
- `CandidatePool` (`quickopen/index/CandidatePool.kt`): Takes an `EnumeratorBackend` — injection point already exists; a `LuceneEnumerator` can be injected here
- `RipgrepEnumerator` (`quickopen/backend/RipgrepEnumerator.kt`): Existing ignore rule config reading from `QuickOpenSettings` — same logic should drive index exclusions
- `RipgrepContentSearch` (`quickopen/backend/RipgrepContentSearch.kt`): Will continue to run in parallel with Lucene content queries for freshness; merge happens in `QuickOpenPanel`
- `QuickOpenSettings` (`settings/QuickOpenSettings.kt`): Already has `maxIndexSize`, `indexEnumerationTimeoutSec`, `ripgrepRespectIgnore`, `ripgrepSearchHidden`, `ripgrepMaxDepth` — new settings for threshold, extension allowlist, and max content index size extend this class
- Status bar from Phase 7 in `QuickOpenPanel.kt` — add mode chip alongside existing "Fuzzy / Root / N files" display

### Established Patterns
- Settings persistence: `@State`/`@Service` with `PersistentStateComponent` — follow existing `QuickOpenSettings` pattern for new index settings
- Background work: `CoroutineScope(Dispatchers.IO + SupervisorJob())` with `Task.Backgroundable` for UI-visible progress — follow `CandidatePool.refreshAsync()` pattern
- Coroutine scope tied to panel lifecycle with `dispose()` cancellation — critical for index service too

### Integration Points
- `QuickOpenPanel.performContentSearch()`: Currently calls `RipgrepContentSearch` only — needs to also launch a Lucene content query and merge results
- `CandidatePool.refreshAsync()`: Mode-selection logic (live vs index) can live here or in a new `IndexManager` service that `CandidatePool` delegates to
- `QuickOpenSettings`: New fields needed — `luceneHybridThreshold`, `luceneExtensionAllowlist`, `luceneMaxIndexSizeMb`, `luceneEvictionDays`

### Research Required — CRITICAL
- **Lucene bundling vs platform Lucene**: IntelliJ Platform uses Lucene internally (version tied to IDE release, not a public API). Must research: (a) whether IntelliJ exposes Lucene classes in a plugin-accessible module, (b) version compatibility with our target IC 2025.1, (c) risk of breakage on IDE upgrades. Alternative: bundle `lucene-core` + `lucene-analysis-common` as plugin dependencies (~5MB JAR weight). Researcher must investigate this before any Lucene integration work begins.

</code_context>

<deferred>
## Deferred Ideas

- Remote (SFTP) root indexing — would require streaming content over SSH; complex, separate phase
- Index coverage for project-scoped roots (all bookmarks indexed as one) — future enhancement
- Custom query syntax beyond keyword/phrase (e.g. `ext:kt modified:today`) — future search language phase

</deferred>

---

*Phase: 08-intelligent-indexed-local-filesystem-search-with-apache-lucene-and-hybrid-mode*
*Context gathered: 2026-02-28*
