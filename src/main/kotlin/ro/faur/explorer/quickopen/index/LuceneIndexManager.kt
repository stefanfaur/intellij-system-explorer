package ro.faur.explorer.quickopen.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.PathManager
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.TextField
import org.apache.lucene.document.StringField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexFormatTooOldException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.WildcardQuery
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.uhighlight.UnifiedHighlighter
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.store.Directory
import org.apache.lucene.store.NIOFSDirectory
import org.apache.lucene.index.CorruptIndexException
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

data class IndexedDocument(
    val path: String,
    val ext: String,
    val hasContent: Boolean,
    val content: String?,
)

/**
 * Single owner of an IndexWriter and search operations for one root path.
 *
 * Two constructors:
 *  - Primary: `LuceneIndexManager(indexPath: Path)` — production use with NIOFSDirectory
 *  - Internal: `LuceneIndexManager(directory: Directory)` — for tests using ByteBuffersDirectory
 */
class LuceneIndexManager : Closeable {

    companion object {
        const val FIELD_PATH = "path"
        const val FIELD_FILENAME = "filename"
        const val FIELD_CONTENT = "content"

        private val LOG = Logger.getInstance(LuceneIndexManager::class.java)

        /**
         * Compute the index directory path for a given root path.
         * Uses SHA-256 of the root string, takes first 16 hex chars.
         * Stored under PathManager.getSystemPath()/caches/explorer-index/<hash>
         * so it survives IDE restarts (unlike getPluginTempPath()).
         */
        fun indexDirForRoot(rootPath: String): Path {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(rootPath.toByteArray(Charsets.UTF_8))
            val hex = bytes.joinToString("") { "%02x".format(it) }
            val hash = hex.substring(0, 16)
            return Paths.get(PathManager.getSystemPath(), "caches", "explorer-index", hash)
        }

        private fun buildAnalyzer(): Analyzer {
            val perField = mapOf(
                FIELD_PATH to WhitespaceAnalyzer(),
                FIELD_FILENAME to WhitespaceAnalyzer()
            )
            return PerFieldAnalyzerWrapper(StandardAnalyzer(), perField)
        }

        private fun deleteDirectoryRecursively(path: Path) {
            if (!Files.exists(path)) return
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    val indexPath: Path?
    private val directory: Directory
    private val analyzer: Analyzer
    private val writer: IndexWriter

    /**
     * Production constructor: opens NIOFSDirectory at the given path.
     * If the index is corrupt or too old, it is silently rebuilt.
     */
    constructor(indexPath: Path) {
        this.indexPath = indexPath
        this.analyzer = buildAnalyzer()
        this.directory = openOrRebuildDirectory(indexPath, analyzer)
        this.writer = openWriter(directory, analyzer, OpenMode.CREATE_OR_APPEND)
    }

    /**
     * Test constructor: accepts a pre-created Directory (e.g. ByteBuffersDirectory).
     */
    internal constructor(directory: Directory) {
        this.indexPath = null
        this.analyzer = buildAnalyzer()
        this.directory = directory
        this.writer = openWriter(directory, analyzer, OpenMode.CREATE_OR_APPEND)
    }

    private fun openOrRebuildDirectory(path: Path, analyzer: Analyzer): Directory {
        Files.createDirectories(path)
        return try {
            val dir = NIOFSDirectory(path)
            // Validate by attempting to open a reader (if index exists)
            if (DirectoryReader.indexExists(dir)) {
                DirectoryReader.open(dir).close()
            }
            dir
        } catch (e: CorruptIndexException) {
            LOG.warn("Corrupt Lucene index at $path — rebuilding", e)
            rebuildDirectory(path, analyzer)
        } catch (e: IndexFormatTooOldException) {
            LOG.warn("Lucene index too old at $path — rebuilding", e)
            rebuildDirectory(path, analyzer)
        } catch (e: Exception) {
            LOG.warn("Failed to open Lucene index at $path — rebuilding", e)
            rebuildDirectory(path, analyzer)
        }
    }

    private fun rebuildDirectory(path: Path, analyzer: Analyzer): NIOFSDirectory {
        deleteDirectoryRecursively(path)
        Files.createDirectories(path)
        val dir = NIOFSDirectory(path)
        // Create fresh empty index
        IndexWriter(dir, IndexWriterConfig(analyzer).apply { openMode = OpenMode.CREATE }).close()
        return dir
    }

    private fun openWriter(dir: Directory, analyzer: Analyzer, openMode: OpenMode): IndexWriter {
        val config = IndexWriterConfig(analyzer).apply {
            this.openMode = openMode
        }
        return IndexWriter(dir, config)
    }

    /**
     * Add or update a file in the index.
     * Uses updateDocument so re-indexing a file replaces the old document.
     */
    fun addOrUpdateFile(file: Path, content: String?, allowContent: Boolean) {
        val pathStr = file.toString()
        val filename = file.fileName?.toString() ?: pathStr

        val doc = Document()
        doc.add(StringField(FIELD_PATH, pathStr, Field.Store.YES))
        doc.add(TextField(FIELD_FILENAME, filename, Field.Store.YES))

        if (allowContent && content != null) {
            doc.add(TextField(FIELD_CONTENT, content, Field.Store.YES))
        }

        writer.updateDocument(Term(FIELD_PATH, pathStr), doc)
    }

    /**
     * Remove a file from the index by its path string.
     */
    fun deleteFile(path: String) {
        writer.deleteDocuments(Term(FIELD_PATH, path))
    }

    /**
     * Commit pending writes to the index.
     */
    fun commit() {
        writer.commit()
    }

    /**
     * Returns the number of documents in the index.
     */
    fun numDocs(): Int {
        writer.commit()
        return DirectoryReader.open(directory).use { it.numDocs() }
    }

    /**
     * Search for files by filename/path pattern.
     *
     * When query is blank, uses MatchAllDocsQuery to return all indexed paths.
     * When query is non-blank, uses WildcardQuery on FIELD_FILENAME and FIELD_PATH.
     */
    fun searchPaths(query: String, maxResults: Int): List<String> {
        writer.commit()
        val reader = DirectoryReader.open(directory)
        return reader.use {
            val searcher = IndexSearcher(it)
            val luceneQuery = if (query.isBlank()) {
                MatchAllDocsQuery()
            } else {
                val lowerQuery = query.lowercase()
                val wildcardPattern = "*${lowerQuery}*"
                val boolQuery = BooleanQuery.Builder()
                boolQuery.add(
                    WildcardQuery(Term(FIELD_FILENAME, wildcardPattern)),
                    BooleanClause.Occur.SHOULD
                )
                boolQuery.add(
                    WildcardQuery(Term(FIELD_PATH, wildcardPattern)),
                    BooleanClause.Occur.SHOULD
                )
                boolQuery.build()
            }
            val topDocs = searcher.search(luceneQuery, maxResults)
            val storedFields = it.storedFields()
            topDocs.scoreDocs.map { scoreDoc ->
                storedFields.document(scoreDoc.doc).get(FIELD_PATH)
            }
        }
    }

    /**
     * Search file content using the given pattern.
     * Returns a list of (path, snippet) pairs.
     */
    fun searchContent(pattern: String, maxResults: Int): List<Pair<String, String>> {
        writer.commit()
        val reader = DirectoryReader.open(directory)
        return reader.use {
            val searcher = IndexSearcher(it)
            val parser = QueryParser(FIELD_CONTENT, analyzer)
            val luceneQuery = parser.parse(QueryParser.escape(pattern))
            val topDocs = searcher.search(luceneQuery, maxResults)

            if (topDocs.totalHits.value == 0L) {
                return@use emptyList()
            }

            val highlighter = UnifiedHighlighter.builder(searcher, analyzer).build()
            val snippets = try {
                highlighter.highlight(FIELD_CONTENT, luceneQuery, topDocs, maxResults)
            } catch (e: Exception) {
                LOG.warn("Highlighting failed for pattern '$pattern'", e)
                null
            }

            val storedFields = it.storedFields()
            topDocs.scoreDocs.mapIndexed { idx, scoreDoc ->
                val path = storedFields.document(scoreDoc.doc).get(FIELD_PATH) ?: ""
                val snippet = snippets?.getOrNull(idx) ?: ""
                path to snippet
            }
        }
    }

    /**
     * Fetch a single document by exact path. Returns null if not found.
     */
    fun getDocument(path: String): IndexedDocument? {
        writer.commit()
        val reader = DirectoryReader.open(directory)
        return reader.use {
            val searcher = IndexSearcher(it)
            val topDocs = searcher.search(
                org.apache.lucene.search.TermQuery(Term(FIELD_PATH, path)), 1
            )
            if (topDocs.scoreDocs.isEmpty()) return@use null
            val stored = it.storedFields().document(topDocs.scoreDocs[0].doc)
            val storedPath = stored.get(FIELD_PATH) ?: return@use null
            val content = stored.get(FIELD_CONTENT)
            val ext = storedPath.substringAfterLast('.', "").lowercase()
            IndexedDocument(
                path = storedPath,
                ext = ext,
                hasContent = content != null,
                content = content,
            )
        }
    }

    /**
     * Return distinct file extensions present in the index (lowercase, no dot).
     */
    fun listExtensions(): List<String> {
        writer.commit()
        val reader = DirectoryReader.open(directory)
        return reader.use {
            val searcher = IndexSearcher(it)
            val topDocs = searcher.search(MatchAllDocsQuery(), Int.MAX_VALUE)
            val storedFields = it.storedFields()
            topDocs.scoreDocs
                .mapNotNull { sd -> storedFields.document(sd.doc).get(FIELD_PATH) }
                .map { p -> p.substringAfterLast('.', "").lowercase() }
                .filter { e -> e.isNotBlank() }
                .distinct()
                .sorted()
        }
    }

    /**
     * Close the writer, directory, and analyzer in order.
     */
    override fun close() {
        try { writer.close() } catch (e: Exception) { LOG.warn("Error closing IndexWriter", e) }
        try { directory.close() } catch (e: Exception) { LOG.warn("Error closing Directory", e) }
        try { analyzer.close() } catch (e: Exception) { LOG.warn("Error closing Analyzer", e) }
    }
}
