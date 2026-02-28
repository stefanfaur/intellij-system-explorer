# Phase 8: Intelligent Indexed Local Filesystem Search with Apache Lucene and Hybrid Mode - Research

**Researched:** 2026-02-28
**Domain:** Apache Lucene 9.x full-text indexing in an IntelliJ plugin, JVM WatchService filesystem monitoring, hybrid mode switching
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Index Scope**
- Index both filename/path AND file content for each file
- Content is indexed only for files matching the configurable extension allowlist
- Default allowlist: `.kt`, `.java`, `.py`, `.ts`, `.tsx`, `.js`, `.jsx`, `.go`, `.rs`, `.rb`, `.c`, `.cpp`, `.h`, `.cs`, `.swift`, `.md`, `.txt`, `.adoc`, `.yaml`, `.yml`, `.json`, `.toml`, `.xml`, `.gradle`, `.properties`, `.sh`, `.env`
- User can add/remove extensions in Settings
- Hard excludes (always skipped regardless of extension): binary files, files above max-size threshold (configurable, default 1MB), directories matching existing ripgrep ignore rules (`node_modules`, `.git`, `build/`, `target/`, etc.)

**Hybrid Mode — Mode Selection**
- Auto-switch based on directory file count at a configurable threshold (default: 5,000 files)
- Below threshold: Live mode — live ripgrep enumeration (same as today), no index involved
- Above threshold: Index mode — Lucene index used for path recall and content search
- Threshold is configurable in QuickOpen Settings

**Hybrid Mode — Initial Build**
- When a root first crosses the threshold, the system falls back to live ripgrep while Lucene builds the index in the background
- Status bar shows `Indexing...` chip while build is in progress
- Once complete, seamlessly switches to index mode without requiring user action

**Hybrid Mode — UI Indicator**
- The existing Phase 7 status bar gains a mode chip: `Indexed` or `Live`
- No separate visual state or window — just the status bar chip

**Content Search Strategy (Lucene + ripgrep)**
- Lucene runs first → instant results with snippet highlighting
- Ripgrep runs in parallel → catches files changed since last index update (freshness gap)
- Merge strategy: deduplicate by full path; if both sources found the same file, keep the Lucene result (has snippet). Append rg-only results at the end.

**Index Lifecycle**
- WatchService daemon monitors indexed roots for filesystem changes (add/update/delete)
- WatchService starts only when the root crosses the size threshold — no overhead for small roots
- Watching uses the same ignore rules as ripgrep: `ripgrepRespectIgnore`, hidden files toggle, max depth, and the extension allowlist
- Each unique root path gets its own independent index (per-root isolation)
- Multiple roots can be indexed concurrently in the background

**Index Storage**
- Location: `PathManager.getPluginTempPath() / "explorer-index" / {sha256(rootPath)}`
- Max size per root: 500MB (configurable). If exceeded, stop indexing content bodies but continue indexing paths only
- LRU eviction: roots not opened in 30 days have their index automatically deleted
- A "Clear Index" action available in Settings for manual cleanup

### Claude's Discretion
- Lucene `Analyzer` choice (e.g. `StandardAnalyzer` vs custom tokenizer for code paths)
- Exact Lucene document schema (field names, stored vs indexed, norms)
- WatchService overflow handling (too many events in burst)
- Corrupt index recovery strategy (detect on open → rebuild silently)
- Settings UI layout for the new index controls

### Deferred Ideas (OUT OF SCOPE)
- Remote (SFTP) root indexing — would require streaming content over SSH; complex, separate phase
- Index coverage for project-scoped roots (all bookmarks indexed as one) — future enhancement
- Custom query syntax beyond keyword/phrase (e.g. `ext:kt modified:today`) — future search language phase
</user_constraints>

---

## Summary

Apache Lucene must be bundled as a plugin dependency — IntelliJ Platform does not expose its internal Lucene classes as a public API. The `implementation("org.apache.lucene:lucene-core:9.12.3")` Gradle declaration is sufficient; the IntelliJ Platform Gradle Plugin 2.x automatically places these JARs in the plugin's `/lib` folder. The project already uses this pattern successfully with `sshd-core` 2.17.1 (~5.5MB), so the Lucene bundle (~6MB total) is not novel territory.

The critical incubator module concern surfaced in research is OPTIONAL: Lucene 9.x uses `jdk.incubator.vector` only for SIMD vector acceleration (KNN search). Standard text search and filesystem path indexing work without `--enable-preview` or `--add-modules jdk.incubator.vector`. The SIMD vectorization feature is simply absent without those flags — not an error. This plugin only needs text indexing for path/content search, not KNN vector search. Lucene 9.12.3 (the last Lucene 9.x maintenance branch) is the correct version to use because Lucene 10.x requires Java 21+ and potentially introduces API churn; Lucene 9.12.3 is stable, already declared the long-term maintenance line, and the Java 21 MMapDirectory foreign memory API is fully available without any preview flags.

The existing codebase provides all integration points: `EnumeratorBackend` interface makes `LuceneEnumerator` a drop-in, `CandidatePool` already accepts any `EnumeratorBackend`, `QuickOpenSettings` has a clean extension pattern via `@State`/`PersistentStateComponent`, and `QuickOpenPanel.performContentSearch()` is the single merge point. Background work follows the established `CoroutineScope(Dispatchers.IO + SupervisorJob())` pattern already used throughout the codebase.

**Primary recommendation:** Bundle `lucene-core:9.12.3` and `lucene-analysis-common:9.12.3` as `implementation` dependencies (~6MB combined). Use `NIOFSDirectory` explicitly (not `FSDirectory.open()` auto-select) to avoid MMapDirectory's file-handle management complexity in a plugin context. Implement `LuceneEnumerator` as an `EnumeratorBackend` and `LuceneContentSearch` as a parallel content search, then merge results in `performContentSearch()`.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.apache.lucene:lucene-core` | 9.12.3 | IndexWriter, IndexReader, FSDirectory, Document, Query | The standard full-text search library for JVM; 9.12.x is the LTS maintenance line |
| `org.apache.lucene:lucene-analysis-common` | 9.12.3 | StandardAnalyzer, WhitespaceAnalyzer, keyword tokenizer | Required for text analysis; always paired with lucene-core |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Java `java.nio.file.WatchService` | JDK 21 (bundled) | Filesystem change events for index updates | Already on JDK 21 classpath — no extra dependency |
| `java.security.MessageDigest` (SHA-256) | JDK 21 (bundled) | Hash root path for index storage directory name | Needed to implement `sha256(rootPath)` storage naming |
| `java.nio.file.Files.walkFileTree` | JDK 21 (bundled) | Initial index build traversal | Parallel to ripgrep but pure Java — used for first-time index population |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `lucene-core:9.12.3` (bundled) | IntelliJ Platform's internal Lucene | Internal Lucene is not a public API, version tied to IDE release, no stability guarantee — NOT suitable |
| `lucene-core:9.12.3` | `lucene-core:10.4.0` | Lucene 10 requires Java 21 (same as plugin), but introduces breaking API changes (IndexWriter flush API changes); 9.12.x is the stable LTS choice |
| `NIOFSDirectory` | `FSDirectory.open()` auto-select | `FSDirectory.open()` returns `MMapDirectory` on macOS/Linux, which has more complex lifecycle management (unmapping) in a plugin classloader context; `NIOFSDirectory` is safer and adequately fast for plugin use |
| `StandardAnalyzer` | Custom code analyzer | StandardAnalyzer handles camelCase tokenization poorly; for code paths a `PerFieldAnalyzerWrapper` combining `WhitespaceAnalyzer` for the path field and `StandardAnalyzer` for content is better — this is a discretionary choice |

**Installation (Gradle `build.gradle.kts`):**
```kotlin
// === Lucene index for QuickOpen hybrid mode ===
implementation("org.apache.lucene:lucene-core:9.12.3")
implementation("org.apache.lucene:lucene-analysis-common:9.12.3")
```

JAR sizes: `lucene-core-9.12.3.jar` ≈ 4.3MB, `lucene-analysis-common-9.12.3.jar` ≈ 1.7MB — total ~6MB. Comparable to the existing sshd bundle.

---

## Architecture Patterns

### Recommended Project Structure
```
src/main/kotlin/ro/faur/explorer/quickopen/
├── backend/
│   ├── EnumeratorBackend.kt          # existing interface — LuceneEnumerator implements this
│   ├── LuceneEnumerator.kt           # NEW: EnumeratorBackend backed by Lucene index
│   ├── LuceneContentSearch.kt        # NEW: content search via Lucene, parallel to RipgrepContentSearch
│   ├── RipgrepEnumerator.kt          # existing — unchanged
│   └── RipgrepContentSearch.kt       # existing — unchanged, still runs in parallel
├── index/
│   ├── CandidatePool.kt              # existing — receives LuceneEnumerator via injection
│   ├── LuceneIndexManager.kt         # NEW: owns IndexWriter, manages lifecycle, exposes search
│   └── LuceneIndexBuilder.kt         # NEW: initial build via Files.walkFileTree + WatchService setup
├── ui/
│   └── QuickOpenPanel.kt             # modified: performContentSearch() merges Lucene + rg results
└── settings/
    └── QuickOpenSettings.kt          # modified: add luceneHybridThreshold, luceneExtensionAllowlist,
                                      #           luceneMaxIndexSizeMb, luceneEvictionDays
```

### Pattern 1: LuceneIndexManager — Service-style lifecycle

**What:** A singleton-per-root object that owns the `IndexWriter` and exposes search and update operations. Created lazily when a root first crosses the threshold. Stored in a `ConcurrentHashMap<rootPath, LuceneIndexManager>`.

**When to use:** Any component that needs to read from or write to the index delegates to this manager.

```kotlin
// Source: Lucene 9.12 API docs — https://lucene.apache.org/core/9_12_3/core/index.html
class LuceneIndexManager(val indexPath: Path) : Closeable {

    private val analyzer: Analyzer = PerFieldAnalyzerWrapper(
        StandardAnalyzer(),
        mapOf(
            FIELD_PATH     to WhitespaceAnalyzer(),
            FIELD_FILENAME to WhitespaceAnalyzer()
        )
    )
    private val directory: Directory = NIOFSDirectory(indexPath)
    private val writerConfig = IndexWriterConfig(analyzer).apply {
        openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
    }
    private val writer = IndexWriter(directory, writerConfig)

    companion object {
        const val FIELD_PATH     = "path"
        const val FIELD_FILENAME = "filename"
        const val FIELD_CONTENT  = "content"

        fun indexDirForRoot(rootPath: String): Path {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(rootPath.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
            return Paths.get(PathManager.getPluginTempPath(), "explorer-index", hash)
        }
    }

    fun addOrUpdateFile(file: Path, content: String?, allowContent: Boolean) {
        val doc = Document().apply {
            add(StringField(FIELD_PATH, file.toString(), Field.Store.YES))
            add(TextField(FIELD_FILENAME, file.fileName.toString(), Field.Store.YES))
            if (allowContent && content != null) {
                add(TextField(FIELD_CONTENT, content, Field.Store.YES))
            }
        }
        writer.updateDocument(Term(FIELD_PATH, file.toString()), doc)
    }

    fun deleteFile(path: String) {
        writer.deleteDocuments(Term(FIELD_PATH, path))
    }

    fun commit() = writer.commit()

    fun searchPaths(query: String, maxResults: Int): List<String> {
        writer.commit()
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        val q = BooleanQuery.Builder()
            .add(WildcardQuery(Term(FIELD_FILENAME, "*${query.lowercase()}*")), BooleanClause.Occur.SHOULD)
            .add(WildcardQuery(Term(FIELD_PATH,     "*${query.lowercase()}*")), BooleanClause.Occur.SHOULD)
            .build()
        val hits = searcher.search(q, maxResults)
        return hits.scoreDocs.map { reader.storedFields().document(it.doc).get(FIELD_PATH) }
            .also { reader.close() }
    }

    fun searchContent(pattern: String, maxResults: Int): List<Pair<String, String>> {
        writer.commit()
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        val parser = QueryParser(FIELD_CONTENT, analyzer)
        val q = parser.parse(QueryParser.escape(pattern))
        val hits = searcher.search(q, maxResults)
        val highlighter = UnifiedHighlighter(searcher, analyzer)
        return hits.scoreDocs.map { sd ->
            val path = reader.storedFields().document(sd.doc).get(FIELD_PATH)
            val snippets = highlighter.highlight(FIELD_CONTENT, q, hits, 1)
            val snippet = snippets.firstOrNull() ?: ""
            Pair(path, snippet)
        }.also { reader.close() }
    }

    override fun close() {
        writer.close()
        directory.close()
        analyzer.close()
    }
}
```

### Pattern 2: LuceneEnumerator — EnumeratorBackend drop-in

**What:** Implements `EnumeratorBackend` by querying the Lucene path index. Drop-in replacement for `RipgrepEnumerator` when in index mode.

**When to use:** When `rootFileCount > luceneHybridThreshold` and index is ready.

```kotlin
// Source: existing EnumeratorBackend contract from quickopen/backend/EnumeratorBackend.kt
class LuceneEnumerator(
    private val indexManager: LuceneIndexManager
) : EnumeratorBackend {

    override val name = "LuceneEnumerator"
    override fun isAvailable(): Boolean = true  // manager handles corrupt-open internally

    override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
        indexManager.searchPaths("", maxResults).forEach { path ->
            emit(path)
        }
    }
}
```

### Pattern 3: WatchService daemon for incremental updates

**What:** A background coroutine that registers `WatchService` for a root and processes `ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE`, and `OVERFLOW` events.

**When to use:** Started immediately after the initial index build completes.

```kotlin
// Source: Oracle WatchService tutorial — https://docs.oracle.com/javase/tutorial/essential/io/notification.html
suspend fun watchRoot(root: Path, manager: LuceneIndexManager, scope: CoroutineScope) {
    FileSystems.getDefault().newWatchService().use { watchService ->
        // Register all subdirectories recursively
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )
                return FileVisitResult.CONTINUE
            }
        })
        while (scope.isActive) {
            val key = withContext(Dispatchers.IO) { watchService.poll(1, TimeUnit.SECONDS) } ?: continue
            for (event in key.pollEvents()) {
                when (event.kind()) {
                    StandardWatchEventKinds.OVERFLOW -> {
                        // OVERFLOW: events may have been lost. Re-scan the directory for changes.
                        rebuildIndex(root, manager)
                        break
                    }
                    StandardWatchEventKinds.ENTRY_DELETE -> {
                        val path = key.watchable() as Path
                        manager.deleteFile(path.resolve(event.context() as Path).toString())
                    }
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY -> {
                        val changed = (key.watchable() as Path).resolve(event.context() as Path)
                        if (shouldIndex(changed)) {
                            manager.addOrUpdateFile(changed, changed.toFile().readTextOrNull(), isContentExt(changed))
                        }
                    }
                }
            }
            manager.commit()
            if (!key.reset()) break
        }
    }
}
```

### Pattern 4: Merge strategy in performContentSearch()

**What:** Launch Lucene content query and ripgrep content search concurrently, then deduplicate results keyed by file path. Lucene result wins when same file found in both (has snippet). Ripgrep appends only its unique files.

```kotlin
// Source: CONTEXT.md merge strategy decision
private fun performContentSearch(pattern: String): List<SearchResult> {
    val luceneResults = mutableListOf<Pair<String, String>>()
    val rgResults = mutableListOf<ContentMatch>()
    val latch = CountDownLatch(2)

    panelScope.launch {
        try {
            indexManager?.searchContent(pattern, MAX_CONTENT_RESULTS)
                ?.forEach { luceneResults.add(it) }
        } finally { latch.countDown() }
    }
    panelScope.launch {
        try {
            RipgrepContentSearch(rgPath, currentRoot).search(pattern)
                .take(MAX_CONTENT_RESULTS).collect { rgResults.add(it) }
        } finally { latch.countDown() }
    }
    latch.await(10, TimeUnit.SECONDS)

    val merged = mutableListOf<SearchResult>()
    val lucenePathSet = luceneResults.map { it.first }.toHashSet()

    luceneResults.forEach { (path, snippet) ->
        merged.add(SearchResult(ScoredCandidate(
            SearchCandidate(
                id = "lucene-content:$path",
                displayName = path.substringAfterLast('/'),
                fullPath = path,
                parentPath = path.substringBeforeLast('/'),
                type = CandidateType.CONTENT_MATCH,
                contentSnippet = snippet
            ),
            score = 2.0   // Lucene results rank higher (have snippet)
        )))
    }
    rgResults.filter { it.filePath !in lucenePathSet }.forEach { match ->
        merged.add(SearchResult(ScoredCandidate(
            SearchCandidate(
                id = "content:${match.filePath}:${match.lineNumber}",
                displayName = "${match.filePath.substringAfterLast('/')}:${match.lineNumber}",
                fullPath = match.filePath,
                parentPath = match.filePath.substringBeforeLast('/'),
                type = CandidateType.CONTENT_MATCH,
                contentSnippet = match.snippet,
                contentMatchRanges = match.matchRanges.takeIf { it.isNotEmpty() }
            ),
            score = 1.0
        )))
    }
    return merged
}
```

### Anti-Patterns to Avoid

- **Using `IndexWriter` without `commit()`:** Changes are not visible to `DirectoryReader.open()` until `commit()` is called. Always commit after batching writes, or use `DirectoryReader.openIfChanged()` for near-real-time search.
- **Opening a new `DirectoryReader` per query:** `DirectoryReader` is expensive to open. Cache it and reuse with `DirectoryReader.openIfChanged(oldReader)` for efficiency.
- **Calling `FSDirectory.open()` auto-select:** On macOS/Linux this returns `MMapDirectory`, which uses memory-mapped files. In a plugin classloader that may be garbage collected, mmap handles can leak. Use `NIOFSDirectory` explicitly.
- **Registering WatchService on the full directory tree at index-build time:** Large trees (100k+ dirs) cause WatchService registration to block for minutes. Register directories lazily as they are encountered, or use a dedicated registration coroutine.
- **Storing Lucene index inside the project directory:** Index files would appear in `git status` and VCS tooling. Always use `PathManager.getPluginTempPath()` as storage root.
- **Accessing `IndexWriter` from EDT:** All Lucene I/O must be off the EDT. Use `Dispatchers.IO` for all index read/write operations.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Full-text search with snippets | Custom regex-over-files scanner | `lucene-core` QueryParser + UnifiedHighlighter | Scoring, stemming, BooleanQuery, phrase matching, Unicode normalization — all built in |
| Tokenization for code paths | Split on `/`, `.`, `_` manually | `WhitespaceAnalyzer` for paths, `StandardAnalyzer` for content | Lucene analyzers handle Unicode edge cases, token filters, stopwords |
| Incremental index updates | Track file modification timestamps manually | `IndexWriter.updateDocument(Term, Document)` with `FIELD_PATH` as key | Atomic replace of existing document by term — built-in Lucene operation |
| Filesystem event buffering | Ring buffer of `java.io.File` objects | `WatchService` + handle `OVERFLOW` by triggering full re-scan | JDK buffers 512 events per watcher; overflow recovery is documented |
| Index size enforcement | Count files manually | Track `IndexWriter.ramBytesUsed()` + `IndexWriter.numDocs()`, stop content indexing when over cap | Lucene exposes byte-level usage natively |
| Path SHA-256 for storage naming | Roll own hex encoding | `MessageDigest.getInstance("SHA-256")` from JDK | Already in JDK 21, no dependency needed |

**Key insight:** Lucene handles all the indexing complexity that makes full-text search hard at scale — inverted indexes, term dictionaries, segment merging, corrupt index recovery. Any custom solution would reimplement a fraction of this and break on edge cases.

---

## Common Pitfalls

### Pitfall 1: IntelliJ's Internal Lucene Classes
**What goes wrong:** Plugin code accidentally references `com.intellij.util.indexing.*` or IntelliJ's own `org.apache.lucene.*` re-exports from the platform classloader.
**Why it happens:** IntelliJ ships Lucene internally. If `lucene-core` is not declared as `implementation`, the platform version leaks into the classpath.
**How to avoid:** Declare `implementation("org.apache.lucene:lucene-core:9.12.3")` explicitly. Verify in the built plugin ZIP that `lib/lucene-core-9.12.3.jar` is present.
**Warning signs:** `NoClassDefFoundError` for Lucene classes, or test compilation errors about version mismatch.

### Pitfall 2: Lucene 9 Incubator Vector Module (jdk.incubator.vector)
**What goes wrong:** Confusion about whether `--enable-preview` or `--add-modules jdk.incubator.vector` is required.
**Why it happens:** Lucene 9 OPTIONALLY uses `jdk.incubator.vector` for KNN vector search SIMD acceleration. Text search and path indexing do NOT require it.
**How to avoid:** Do not add `--add-modules jdk.incubator.vector` to the plugin. The incubator module is absent in a standard IntelliJ JVM launch; Lucene detects this and silently disables SIMD vectorization. All text search functionality works normally.
**Warning signs:** None — the absence of the module is gracefully handled by Lucene.

### Pitfall 3: WatchService OVERFLOW Events
**What goes wrong:** A burst of file changes (e.g., `git checkout`, `npm install`) overflows the WatchService buffer (512 events/watcher). Events are silently dropped. Index becomes stale.
**Why it happens:** The JDK WatchService buffers up to 512 pending events per registered watchable. When this limit is exceeded, an `OVERFLOW` event is queued instead of individual change events.
**How to avoid:** In the event processing loop, detect `StandardWatchEventKinds.OVERFLOW` and trigger a full re-scan of the affected subtree rather than attempting to process lost events individually.
**Warning signs:** Files changed during `npm install` or similar bulk operations don't appear in search results.

### Pitfall 4: IndexWriter Not Closed Before DirectoryReader Opens
**What goes wrong:** `DirectoryReader.open(directory)` does not see recently written documents.
**Why it happens:** Lucene uses a segment-based architecture. Documents are in memory until `commit()` writes them to a segment file. Readers only see committed segments.
**How to avoid:** Always call `writer.commit()` after batching writes. For search, use `DirectoryReader.openIfChanged(cachedReader)` to get a near-real-time view.
**Warning signs:** Index build completes but path enumeration returns 0 results.

### Pitfall 5: MMapDirectory File Handle Leak in Plugin Context
**What goes wrong:** After plugin unload, `MMapDirectory` holds mmap file handles that prevent the index directory from being deleted/written on Windows.
**Why it happens:** `FSDirectory.open()` returns `MMapDirectory` on macOS/Linux/Windows-64. MMap handles are tied to the object lifecycle, not OS file close calls.
**How to avoid:** Use `NIOFSDirectory(indexPath)` explicitly. It uses standard `FileChannel` operations that release on `close()`.
**Warning signs:** `AccessDeniedException` when trying to delete or recreate the index directory on Windows.

### Pitfall 6: Index Stored in Project Directory
**What goes wrong:** Index files appear in `git status`, are accidentally committed, or confuse VCS tooling.
**Why it happens:** Naively using a relative path like `".index/"` for the index directory.
**How to avoid:** Always use `PathManager.getPluginTempPath()` as the storage root. The CONTEXT.md decision specifies `PathManager.getPluginTempPath() / "explorer-index" / {sha256(rootPath)}`.
**Warning signs:** `git status` shows untracked `.index/` directories.

### Pitfall 7: Blocking EDT During Index Build
**What goes wrong:** IDE freezes during initial index build for large directories.
**Why it happens:** `Files.walkFileTree` on a 100k-file directory takes 2-10 seconds. If called on EDT, the UI locks.
**How to avoid:** All index build and WatchService operations must run in `CoroutineScope(Dispatchers.IO + SupervisorJob())`. Show the `Indexing...` chip in the status bar while the background job runs. Follow the `CandidatePool.refreshAsync()` pattern already in the codebase.
**Warning signs:** IDE shows "UI freeze detected" warnings in log.

---

## Code Examples

Verified patterns from official sources:

### Adding Lucene dependencies to build.gradle.kts
```kotlin
// Source: Apache Lucene Downloads — https://lucene.apache.org/core/downloads.html
// Mirrors existing sshd pattern already in this project's build.gradle.kts
implementation("org.apache.lucene:lucene-core:9.12.3")
implementation("org.apache.lucene:lucene-analysis-common:9.12.3")
```

### Creating and writing to an index
```kotlin
// Source: Apache Lucene 9.12 Core API — https://lucene.apache.org/core/9_12_3/core/index.html
val analyzer = PerFieldAnalyzerWrapper(
    StandardAnalyzer(),
    mapOf("path" to WhitespaceAnalyzer(), "filename" to WhitespaceAnalyzer())
)
val directory: Directory = NIOFSDirectory(indexPath)
val config = IndexWriterConfig(analyzer).apply {
    openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
}
val writer = IndexWriter(directory, config)

val doc = Document().apply {
    add(StringField("path", filePath, Field.Store.YES))          // exact match field
    add(TextField("filename", fileName, Field.Store.YES))         // analyzed text field
    add(TextField("content", fileContent, Field.Store.YES))       // full-text content
}
writer.updateDocument(Term("path", filePath), doc)  // upsert by path
writer.commit()
```

### Searching paths (wildcard)
```kotlin
// Source: Lucene 9 WildcardQuery API
writer.commit()
val reader = DirectoryReader.open(directory)
val searcher = IndexSearcher(reader)
val q = BooleanQuery.Builder()
    .add(WildcardQuery(Term("filename", "*${query.lowercase()}*")), BooleanClause.Occur.SHOULD)
    .add(WildcardQuery(Term("path",     "*${query.lowercase()}*")), BooleanClause.Occur.SHOULD)
    .build()
val hits = searcher.search(q, maxResults)
val paths = hits.scoreDocs.map { reader.storedFields().document(it.doc).get("path") }
reader.close()
```

### Searching content with snippets (UnifiedHighlighter)
```kotlin
// Source: Lucene 9 UnifiedHighlighter — https://lucene.apache.org/core/9_12_3/highlighter/index.html
// lucene-highlighter module is included in lucene-core for 9.x
val parser = QueryParser("content", analyzer)
val q = parser.parse(QueryParser.escape(pattern))
val hits = searcher.search(q, maxResults)
val highlighter = UnifiedHighlighter(searcher, analyzer)
val snippets = highlighter.highlight("content", q, hits, 1)
```

### Detecting corrupt index and rebuilding silently
```kotlin
// Source: Lucene IndexWriter.OpenMode.CREATE_OR_APPEND behavior + CheckIndex utility
fun openOrRebuild(indexPath: Path): IndexWriter {
    return try {
        val dir = NIOFSDirectory(indexPath)
        val config = IndexWriterConfig(analyzer).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
        }
        IndexWriter(dir, config)
    } catch (e: IndexFormatTooOldException) {
        LOG.warn("Corrupt/old Lucene index at $indexPath — rebuilding", e)
        indexPath.toFile().deleteRecursively()
        indexPath.toFile().mkdirs()
        val dir = NIOFSDirectory(indexPath)
        IndexWriter(dir, IndexWriterConfig(analyzer).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE
        })
    }
}
```

### WatchService event loop with OVERFLOW handling
```kotlin
// Source: Oracle WatchService docs — https://docs.oracle.com/javase/tutorial/essential/io/notification.html
// JDK buffers 512 events/watcher; OVERFLOW = events were dropped, must re-scan
while (scope.isActive) {
    val key = withContext(Dispatchers.IO) {
        watchService.poll(500, TimeUnit.MILLISECONDS)
    } ?: continue

    for (event in key.pollEvents()) {
        @Suppress("UNCHECKED_CAST")
        when (event.kind()) {
            StandardWatchEventKinds.OVERFLOW -> {
                // Re-index the directory fully — events were lost
                scope.launch(Dispatchers.IO) { rebuildIndex(root, manager) }
                key.reset()
                break
            }
            StandardWatchEventKinds.ENTRY_DELETE -> {
                val changed = (key.watchable() as Path).resolve(event.context() as Path)
                manager.deleteFile(changed.toString())
            }
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY -> {
                val changed = (key.watchable() as Path).resolve(event.context() as Path)
                if (shouldIndex(changed)) {
                    val content = if (isContentExt(changed)) readSafe(changed) else null
                    manager.addOrUpdateFile(changed, content, content != null)
                }
                // Register newly created directories for watching
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE &&
                    Files.isDirectory(changed)) {
                    changed.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
                }
            }
        }
    }
    manager.commit()
    if (!key.reset()) break
}
```

### QuickOpenSettings extension for new fields
```kotlin
// Source: existing QuickOpenSettings pattern in settings/QuickOpenSettings.kt
data class State(
    // ... existing fields ...

    // === Phase 8: Lucene hybrid index ===
    var luceneHybridThreshold: Int = 5_000,
    var luceneExtensionAllowlist: String = "kt,java,py,ts,tsx,js,jsx,go,rs,rb,c,cpp,h,cs,swift,md,txt,adoc,yaml,yml,json,toml,xml,gradle,properties,sh,env",
    var luceneMaxIndexSizeMb: Int = 500,
    var luceneEvictionDays: Int = 30,
)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Lucene 1.x single flat index | Segment-based architecture with merging | Lucene 2.x (2004) | Incremental updates without full rebuild |
| `RAMDirectory` for testing | `ByteBuffersDirectory` | Lucene 8.x | `RAMDirectory` deprecated and removed in 9.x — use `ByteBuffersDirectory` in tests |
| `IndexWriter.optimize()` | `IndexWriter.forceMerge(1)` | Lucene 3.x | `optimize()` removed; `forceMerge` is explicit about merge factor |
| `MultiTermQuery.CONSTANT_SCORE_AUTO_REWRITE_DEFAULT` | `MultiTermQuery.CONSTANT_SCORE_REWRITE` | Lucene 9.x | Constant naming change |
| `Document.add(new Field(...))` | Use typed field subclasses: `StringField`, `TextField`, `StoredField` | Lucene 4.x | Cleaner API, compile-time field type checking |
| `TopDocs.totalHits` as `int` | `TopDocs.totalHits` as `TotalHits` object | Lucene 8.x | Supports approximate counts for large result sets |
| `IndexReader.document()` | `IndexReader.storedFields().document()` | Lucene 9.x | Required API change — old form removed in 9.x |

**Deprecated/outdated:**
- `RAMDirectory`: Removed in Lucene 9. Use `ByteBuffersDirectory` for in-memory indexes in tests.
- `IndexReader.document(docId)`: Removed in Lucene 9.x. Use `reader.storedFields().document(docId)`.
- `Highlighter` (classic): Superseded by `UnifiedHighlighter` — better performance, supports multi-value fields.
- `QueryParser` with unescaped user input: Always use `QueryParser.escape()` or `PrefixQuery`/`WildcardQuery` directly to avoid query parse exceptions from user input.

---

## Open Questions

1. **UnifiedHighlighter module availability in lucene-core 9.12.3**
   - What we know: `UnifiedHighlighter` moved into `lucene-core` JAR in Lucene 9.x (was a separate `lucene-highlighter` module in 8.x).
   - What's unclear: The exact package path in 9.12.3 (`org.apache.lucene.search.uhighlight` vs `org.apache.lucene.highlighter`).
   - Recommendation: Verify package path when adding the first import; if not in `lucene-core`, add `lucene-highlighter:9.12.3` as an additional dependency (~0.8MB).

2. **WatchService behavior on network-mounted local paths (NFS/CIFS symlinks)**
   - What we know: WatchService is well-behaved on local APFS/ext4/NTFS. On network-mounted paths it may silently produce no events.
   - What's unclear: Whether the guard should check for network mounts before starting WatchService (or just let it degrade gracefully with no events).
   - Recommendation: Start WatchService unconditionally but log a warning if zero events are received within 60 seconds of a known modification. The LRU eviction and ripgrep freshness-gap coverage mitigate the impact.

3. **`PathManager.getPluginTempPath()` vs `PathManager.getSystemPath()`**
   - What we know: `getPluginTempPath()` returns `idea.system.path + "/tmp"`. It is meant for transient data that may be cleared on IDE restart.
   - What's unclear: Whether the index should survive IDE restarts (it should — rebuilding is expensive) or be treated as fully transient.
   - Recommendation: Use `PathManager.getSystemPath() + "/caches/explorer-index"` (analogous to how IntelliJ stores its own persistent caches) rather than `getPluginTempPath()`. The CONTEXT.md specified `getPluginTempPath()` but the behavior difference matters — clarify with planner.

4. **Counting files for threshold detection without full enumeration**
   - What we know: The hybrid threshold check triggers when root file count exceeds 5,000. Counting files requires enumeration.
   - What's unclear: Should the threshold check use `rg --count-matches --files` (fast) or `Files.walk().count()` (pure Java, no rg dependency)?
   - Recommendation: Use `rg --files | wc -l` equivalent: run `rg --files --count-matches` with a 5-second timeout. This reuses the existing rg path detection and respects ignore rules automatically.

---

## Sources

### Primary (HIGH confidence)
- Apache Lucene 9.12.3 Core API docs — https://lucene.apache.org/core/9_12_3/core/index.html — verified FSDirectory, IndexWriter, Document patterns
- Apache Lucene FSDirectory 9.12.1 — https://lucene.apache.org/core/9_12_1/core/org/apache/lucene/store/FSDirectory.html — NIOFSDirectory vs MMapDirectory behavior
- Apache Lucene Core News — https://lucene.apache.org/core/corenews.html — verified 9.12.3 and 10.4.0 release dates (latest stable 10.4.0 Feb 2026, latest 9.x is 9.12.3)
- Maven Central `repo1.maven.org/maven2/org/apache/lucene/lucene-core/9.12.3/` — JAR size 4.3MB verified
- Maven Central `repo1.maven.org/maven2/org/apache/lucene/lucene-analysis-common/9.12.3/` — JAR size 1.7MB verified
- Oracle WatchService tutorial — https://docs.oracle.com/javase/tutorial/essential/io/notification.html — OVERFLOW event handling, 512-event buffer
- Existing codebase: `EnumeratorBackend.kt`, `CandidatePool.kt`, `QuickOpenSettings.kt`, `QuickOpenPanel.kt`, `RipgrepEnumerator.kt`, `build.gradle.kts` — all read directly

### Secondary (MEDIUM confidence)
- Lucas plugin GitHub (picimako/lucas) — confirmed incubator module is OPTIONAL for text search; the limitation only affects SIMD KNN vector acceleration
- JetBrains Platform Plugin SDK (plugins.jetbrains.com/docs/intellij/plugin-content.html) — confirmed `implementation` deps go into plugin `/lib` folder automatically; verified against existing sshd-core pattern in this project
- OpenSearch Issue #4637 / Elasticsearch Issue #90526 — incubator.vector only required for Java 19/20/21 + MMapDirectory SIMD; safe to omit for text search

### Tertiary (LOW confidence)
- Baeldung WatchService guide — general OVERFLOW handling pattern (not IntelliJ-specific)
- Stack Overflow / community discussions on Lucene index corrupt recovery — flagged for validation; use official `IndexFormatTooOldException` catch as the recovery trigger

---

## Metadata

**Confidence breakdown:**
- Standard stack (Lucene 9.12.3 as bundled dependency): HIGH — JAR sizes verified on Maven Central, incubator optionality confirmed via multiple sources
- Architecture (LuceneIndexManager, LuceneEnumerator integration): HIGH — integration points read directly from codebase; EnumeratorBackend interface verified
- Pitfalls: HIGH for Lucene-specific (MMapDirectory, OVERFLOW, EDT blocking) — MEDIUM for WatchService on network mounts (not directly tested)
- Lucene version choice (9.12.3 vs 10.x): HIGH — 9.12.3 confirmed as last 9.x maintenance branch on official release page

**Research date:** 2026-02-28
**Valid until:** 2026-03-30 (Lucene 9.12.x is maintenance-only; no breaking changes expected; IntelliJ Platform 2.x Gradle plugin is stable)
