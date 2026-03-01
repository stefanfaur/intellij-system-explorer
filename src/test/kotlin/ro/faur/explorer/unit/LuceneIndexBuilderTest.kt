package ro.faur.explorer.unit

import kotlinx.coroutines.runBlocking
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ro.faur.explorer.quickopen.index.LuceneIndexBuilder
import ro.faur.explorer.quickopen.index.LuceneIndexManager
import ro.faur.explorer.settings.QuickOpenSettings
import java.nio.file.Files
import java.nio.file.Path

class LuceneIndexBuilderTest {

    private fun defaultSettings(
        allowlist: String = "kt,java,py,ts,js,md,txt",
        searchHidden: Boolean = false,
        maxSizeMb: Int = 500
    ) = QuickOpenSettings.State(
        luceneExtensionAllowlist = allowlist,
        ripgrepSearchHidden = searchHidden,
        luceneMaxIndexSizeMb = maxSizeMb
    )

    @Test
    fun `test 1 - buildIndex indexes kt files but not exe binaries`(@TempDir tempDir: Path) = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)

        // Create 3 .kt files and 1 .exe binary
        Files.writeString(tempDir.resolve("Main.kt"), "fun main() {}")
        Files.writeString(tempDir.resolve("Util.kt"), "object Util {}")
        Files.writeString(tempDir.resolve("Model.kt"), "data class Model(val id: Int)")
        // Write a binary file (has null byte = binary marker)
        Files.write(tempDir.resolve("app.exe"), byteArrayOf(0x4D, 0x5A, 0x00, 0x00, 0x50, 0x45, 0x00, 0x00))

        LuceneIndexBuilder.buildIndex(tempDir, manager, defaultSettings())

        val results = manager.searchPaths("", 100)
        assertEquals(3, results.size, "Should index 3 kt files, got: $results")
        assertTrue(results.any { it.endsWith("Main.kt") }, "Main.kt missing from: $results")
        assertTrue(results.any { it.endsWith("Util.kt") }, "Util.kt missing from: $results")
        assertTrue(results.any { it.endsWith("Model.kt") }, "Model.kt missing from: $results")
        assertTrue(results.none { it.endsWith("app.exe") }, "app.exe should be excluded from: $results")

        manager.close()
    }

    @Test
    fun `test 2 - buildIndex respects extension allowlist`(@TempDir tempDir: Path) = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)

        Files.writeString(tempDir.resolve("server.log"), "2024-01-01 INFO started")
        Files.writeString(tempDir.resolve("app.kt"), "fun main() {}")

        // allowlist does NOT include "log"
        LuceneIndexBuilder.buildIndex(tempDir, manager, defaultSettings(allowlist = "kt,java"))

        val results = manager.searchPaths("", 100)
        assertEquals(1, results.size, "Only .kt should be indexed, got: $results")
        assertTrue(results.any { it.endsWith("app.kt") }, "app.kt should be indexed")
        assertTrue(results.none { it.endsWith("server.log") }, "server.log should be excluded")

        manager.close()
    }

    @Test
    fun `test 3 - buildIndex skips files larger than maxFileSizeBytes`(@TempDir tempDir: Path) = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)

        // Create a small file (should be indexed)
        Files.writeString(tempDir.resolve("small.kt"), "fun small() {}")

        // Create a file larger than 1MB (default limit) — write > 1_048_576 bytes
        val largeContent = "A".repeat(1_048_577)
        Files.writeString(tempDir.resolve("large.kt"), largeContent)

        LuceneIndexBuilder.buildIndex(tempDir, manager, defaultSettings())

        val results = manager.searchPaths("", 100)
        assertEquals(1, results.size, "Only small.kt should be indexed, got: $results")
        assertTrue(results.any { it.endsWith("small.kt") }, "small.kt should be indexed")
        assertTrue(results.none { it.endsWith("large.kt") }, "large.kt exceeds size limit and should be excluded")

        manager.close()
    }

    @Test
    fun `test 4 - buildIndex skips hard-exclude directories`(@TempDir tempDir: Path) = runBlocking {
        val dir = ByteBuffersDirectory()
        val manager = LuceneIndexManager(dir)

        // Create files in excluded directories
        val nodeModules = tempDir.resolve("node_modules")
        val gitDir = tempDir.resolve(".git")
        val buildDir = tempDir.resolve("build")
        val targetDir = tempDir.resolve("target")
        val srcDir = tempDir.resolve("src")
        Files.createDirectories(nodeModules)
        Files.createDirectories(gitDir)
        Files.createDirectories(buildDir)
        Files.createDirectories(targetDir)
        Files.createDirectories(srcDir)

        Files.writeString(nodeModules.resolve("package.kt"), "// node dep")
        Files.writeString(gitDir.resolve("config.kt"), "// git config")
        Files.writeString(buildDir.resolve("output.kt"), "// build output")
        Files.writeString(targetDir.resolve("classes.kt"), "// compiled")
        Files.writeString(srcDir.resolve("Main.kt"), "fun main() {}")

        LuceneIndexBuilder.buildIndex(tempDir, manager, defaultSettings())

        val results = manager.searchPaths("", 100)
        assertEquals(1, results.size, "Only Main.kt in src/ should be indexed, got: $results")
        assertTrue(results.any { it.endsWith("Main.kt") }, "Main.kt should be indexed")

        manager.close()
    }

    @Test
    fun `test 5 - countFiles returns correct count within timeout`(@TempDir tempDir: Path) {
        // Create 5 files
        for (i in 1..5) {
            Files.writeString(tempDir.resolve("file$i.txt"), "content $i")
        }

        val count = LuceneIndexBuilder.countFiles(tempDir, timeoutMs = 5_000)
        assertEquals(5, count, "countFiles should return 5, got: $count")
    }

    @Test
    fun `test 5b - countFiles skips HARD_EXCLUDE_DIRS`(@TempDir tempDir: Path) {
        Files.createDirectories(tempDir.resolve("node_modules"))
        for (i in 1..5) Files.writeString(tempDir.resolve("node_modules/dep$i.kt"), "dep")
        for (i in 1..3) Files.writeString(tempDir.resolve("file$i.kt"), "content")

        val count = LuceneIndexBuilder.countFiles(tempDir)
        assertEquals(3, count, "countFiles should skip node_modules, got: $count")
    }

    @Test
    fun `test 5c - countFiles stops after stopAt threshold`(@TempDir tempDir: Path) {
        for (i in 1..10) Files.writeString(tempDir.resolve("file$i.kt"), "content")

        val count = LuceneIndexBuilder.countFiles(tempDir, stopAt = 5)
        assertTrue(count > 5, "countFiles with stopAt=5 should return > 5 when 10 files exist, got: $count")
    }

    @Test
    fun `test 6 - shouldIndex returns false for a class file with binary content`(@TempDir tempDir: Path) {
        // Java .class files start with 0xCAFEBABE magic bytes
        val classFile = tempDir.resolve("Main.class")
        Files.write(classFile, byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0x00, 0x00, 0x00, 0x3D))

        // Even if we add "class" to the allowlist, binary detection should reject it
        val allowedExts = setOf("kt", "java", "class")
        val result = LuceneIndexBuilder.shouldIndex(classFile, allowedExts, maxSizeBytes = 1_048_576)

        assertFalse(result, "shouldIndex should return false for binary .class file")
    }
}
